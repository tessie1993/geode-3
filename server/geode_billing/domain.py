"""Strict reducers for responses fetched by a trusted Google Play API adapter.

These functions do not authenticate JSON. Never pass a mobile request body as a
PlaySnapshot. Source metadata is supplied by the server's PlayVerifier adapter.
"""

from collections.abc import Mapping
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from enum import Enum
import re
from typing import Any


class BillingError(Exception):
    """Public-safe errors: never include token, claims, or provider payloads."""

    code = "BILLING_ERROR"

    def __init__(self) -> None:
        super().__init__(self.code)


class InvalidPurchase(BillingError):
    code = "INVALID_PLAY_PURCHASE"


class UnsupportedPurchase(BillingError):
    code = "UNSUPPORTED_PURCHASE"


class OwnershipConflict(BillingError):
    code = "PURCHASE_OWNERSHIP_CONFLICT"


class StaleSnapshot(BillingError):
    code = "STALE_PLAY_SNAPSHOT"


class VerificationUnavailable(BillingError):
    code = "VERIFICATION_UNAVAILABLE"


class LedgerUnavailable(BillingError):
    code = "LEDGER_UNAVAILABLE"


class IdempotencyConflict(BillingError):
    code = "IDEMPOTENCY_CONFLICT"


class ConcurrentUpdate(BillingError):
    code = "REVERIFY_CONCURRENT_UPDATE"


class ProductKind(str, Enum):
    SUBSCRIPTION = "SUBS"
    LIFETIME = "INAPP"


class EntitlementState(str, Enum):
    PENDING = "PENDING"
    PENDING_CANCELED = "PENDING_CANCELED"
    ACTIVE = "ACTIVE"
    GRACE = "GRACE"
    CANCELED_ACTIVE = "CANCELED_ACTIVE"
    ON_HOLD = "ON_HOLD"
    PAUSED = "PAUSED"
    EXPIRED = "EXPIRED"
    LIFETIME = "LIFETIME"
    REVOKED = "REVOKED"


ACCESS_STATES = frozenset(
    {
        EntitlementState.ACTIVE,
        EntitlementState.GRACE,
        EntitlementState.CANCELED_ACTIVE,
        EntitlementState.LIFETIME,
    }
)
ACK_PENDING = "ACKNOWLEDGEMENT_STATE_PENDING"
ACKNOWLEDGED = "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
ACK_DEADLINE = timedelta(days=3)  # Only auto-renewing and non-consumable catalog.


def utc(value: datetime) -> datetime:
    if not isinstance(value, datetime) or value.tzinfo is None or value.utcoffset() is None:
        raise InvalidPurchase()
    return value.astimezone(timezone.utc)


@dataclass(frozen=True)
class Product:
    product_id: str
    kind: ProductKind
    capabilities: frozenset[str]
    base_plan_ids: frozenset[str] = frozenset()
    purchase_option_ids: frozenset[str] = frozenset()

    def __post_init__(self) -> None:
        if not self.product_id or not isinstance(self.kind, ProductKind) or not self.capabilities:
            raise ValueError("Explicit product and capabilities required")
        if self.kind == ProductKind.SUBSCRIPTION and not self.base_plan_ids:
            raise ValueError("Subscription base-plan allowlist required")
        if self.kind == ProductKind.LIFETIME and not self.purchase_option_ids:
            raise ValueError("Lifetime purchase-option allowlist required")
        for values in (self.capabilities, self.base_plan_ids, self.purchase_option_ids):
            if not isinstance(values, frozenset) or any(not isinstance(v, str) or not v for v in values):
                raise ValueError("Configuration values must be immutable nonempty strings")


@dataclass(frozen=True)
class Catalog:
    package_name: str
    products: tuple[Product, ...]
    max_snapshot_age: timedelta = timedelta(minutes=5)
    future_clock_tolerance: timedelta = timedelta(seconds=5)

    def __post_init__(self) -> None:
        if not self.package_name or not isinstance(self.products, tuple) or not self.products:
            raise ValueError("Explicit package and immutable catalog required")
        if len({product.product_id for product in self.products}) != len(self.products):
            raise ValueError("Duplicate product configuration")
        if self.max_snapshot_age <= timedelta(0) or self.future_clock_tolerance < timedelta(0):
            raise ValueError("Invalid freshness policy")

    def product(self, product_id: str, kind: ProductKind) -> Product:
        for product in self.products:
            if product.product_id == product_id and product.kind == kind:
                return product
        raise UnsupportedPurchase()


@dataclass(frozen=True)
class PurchaseOwner:
    """Server-resolved commerce subject/intent, never a client identity claim."""

    subject_id: str
    obfuscated_account_id: str = field(repr=False)
    allow_test_purchases: bool = False


@dataclass(frozen=True)
class PlaySnapshot:
    """Adapter attests the request package/type, token fingerprint and read time."""

    package_name: str
    kind: ProductKind
    token_fingerprint: str
    retrieved_at: datetime
    payload: Mapping[str, Any] = field(repr=False)


@dataclass(frozen=True)
class EntitlementDecision:
    state: EntitlementState
    product_id: str
    kind: ProductKind
    capabilities: frozenset[str]
    verified_at: datetime
    fresh_until: datetime
    access_until: datetime | None
    acknowledgement_required: bool
    acknowledgement_due_at: datetime | None

    def allows_access(self, now: datetime) -> bool:
        """Short-lived domain observation, not a signed offline entitlement lease."""
        current = utc(now)
        return (
            self.state in ACCESS_STATES
            and current < self.fresh_until
            and (self.access_until is None or current < self.access_until)
        )


_RFC3339 = re.compile(
    r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})"
)


def _object(value: Any) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise InvalidPurchase()
    return value


def _string(value: Any) -> str:
    if not isinstance(value, str) or not value or len(value) > 4096:
        raise InvalidPurchase()
    return value


def _timestamp(value: Any) -> datetime:
    if not isinstance(value, str) or not _RFC3339.fullmatch(value):
        raise InvalidPurchase()
    try:
        return utc(datetime.fromisoformat(value.replace("Z", "+00:00")))
    except (ValueError, OverflowError):
        raise InvalidPurchase() from None


def _one_item(payload: Mapping[str, Any], key: str) -> Mapping[str, Any]:
    items = payload.get(key)
    if not isinstance(items, list) or len(items) != 1:
        raise UnsupportedPurchase()
    return _object(items[0])


def _acknowledgement(payload: Mapping[str, Any]) -> str:
    value = payload.get("acknowledgementState")
    if value not in (ACK_PENDING, ACKNOWLEDGED):
        raise InvalidPurchase()
    return value


def _owner_identifier(payload: Mapping[str, Any], owner: PurchaseOwner) -> None:
    actual = _string(payload.get("obfuscatedExternalAccountId"))
    if actual != owner.obfuscated_account_id:
        raise OwnershipConflict()


def _decision(
    *,
    state: EntitlementState,
    product: Product,
    snapshot: PlaySnapshot,
    catalog: Catalog,
    now: datetime,
    access_until: datetime | None,
    purchased_at: datetime | None,
    acknowledgement: str,
) -> EntitlementDecision:
    grants_access = state in ACCESS_STATES and (access_until is None or now < access_until)
    acknowledgement_required = grants_access and acknowledgement == ACK_PENDING
    if acknowledgement_required and purchased_at is None:
        raise InvalidPurchase()
    if purchased_at is not None and purchased_at > now + catalog.future_clock_tolerance:
        raise InvalidPurchase()
    verified_at = utc(snapshot.retrieved_at)
    return EntitlementDecision(
        state=state,
        product_id=product.product_id,
        kind=product.kind,
        capabilities=product.capabilities if grants_access else frozenset(),
        verified_at=verified_at,
        fresh_until=verified_at + catalog.max_snapshot_age,
        access_until=access_until,
        acknowledgement_required=acknowledgement_required,
        acknowledgement_due_at=purchased_at + ACK_DEADLINE if acknowledgement_required else None,
    )


def evaluate_purchase(
    snapshot: PlaySnapshot,
    catalog: Catalog,
    owner: PurchaseOwner,
    now: datetime,
) -> EntitlementDecision:
    """No fallback to client purchase flags, cached UI state or optimistic grants."""
    if not isinstance(snapshot, PlaySnapshot) or not isinstance(snapshot.kind, ProductKind):
        raise InvalidPurchase()
    current = utc(now)
    observed = utc(snapshot.retrieved_at)
    if observed > current + catalog.future_clock_tolerance or current - observed >= catalog.max_snapshot_age:
        raise StaleSnapshot()
    if snapshot.package_name != catalog.package_name:
        raise UnsupportedPurchase()
    payload = _object(snapshot.payload)
    if snapshot.kind == ProductKind.SUBSCRIPTION:
        return _subscription(payload, snapshot, catalog, owner, current)
    return _lifetime(payload, snapshot, catalog, owner, current)


def _subscription(
    payload: Mapping[str, Any],
    snapshot: PlaySnapshot,
    catalog: Catalog,
    owner: PurchaseOwner,
    now: datetime,
) -> EntitlementDecision:
    # A safe replacement needs a transaction spanning both token lineages. This
    # foundation deliberately cannot grant an unsupported replacement in isolation.
    if "linkedPurchaseToken" in payload or "outOfAppPurchaseContext" in payload:
        raise UnsupportedPurchase()
    if "testPurchase" in payload:
        _object(payload["testPurchase"])
        if not owner.allow_test_purchases:
            raise UnsupportedPurchase()
    _owner_identifier(_object(payload.get("externalAccountIdentifiers")), owner)
    item = _one_item(payload, "lineItems")
    product = catalog.product(_string(item.get("productId")), ProductKind.SUBSCRIPTION)
    if "prepaidPlan" in item or "deferredItemReplacement" in item:
        raise UnsupportedPurchase()
    offer = _object(item.get("offerDetails"))
    if _string(offer.get("basePlanId")) not in product.base_plan_ids:
        raise UnsupportedPurchase()
    plan = _object(item.get("autoRenewingPlan"))
    if "installmentDetails" in plan:
        raise UnsupportedPurchase()
    if type(plan.get("autoRenewEnabled")) is not bool:
        raise InvalidPurchase()
    states = {
        "SUBSCRIPTION_STATE_PENDING": EntitlementState.PENDING,
        "SUBSCRIPTION_STATE_ACTIVE": EntitlementState.ACTIVE,
        "SUBSCRIPTION_STATE_IN_GRACE_PERIOD": EntitlementState.GRACE,
        "SUBSCRIPTION_STATE_ON_HOLD": EntitlementState.ON_HOLD,
        "SUBSCRIPTION_STATE_PAUSED": EntitlementState.PAUSED,
        "SUBSCRIPTION_STATE_CANCELED": EntitlementState.CANCELED_ACTIVE,
        "SUBSCRIPTION_STATE_EXPIRED": EntitlementState.EXPIRED,
        "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED": EntitlementState.PENDING_CANCELED,
    }
    raw_state = _string(payload.get("subscriptionState"))
    if raw_state not in states:
        raise InvalidPurchase()
    state = states[raw_state]
    pending = state in (EntitlementState.PENDING, EntitlementState.PENDING_CANCELED)
    expiry = _timestamp(item["expiryTime"]) if "expiryTime" in item else None
    if not pending and expiry is None:
        raise InvalidPurchase()
    if state in ACCESS_STATES and expiry is not None and expiry <= now:
        state = EntitlementState.EXPIRED
    if state == EntitlementState.EXPIRED and expiry is not None and expiry > now:
        raise InvalidPurchase()
    started = _timestamp(payload["startTime"]) if "startTime" in payload else None
    if not pending and started is None:
        raise InvalidPurchase()
    if started is not None and expiry is not None and started >= expiry:
        raise InvalidPurchase()
    return _decision(
        state=state,
        product=product,
        snapshot=snapshot,
        catalog=catalog,
        now=now,
        access_until=expiry,
        purchased_at=started,
        acknowledgement=_acknowledgement(payload),
    )


def _lifetime(
    payload: Mapping[str, Any],
    snapshot: PlaySnapshot,
    catalog: Catalog,
    owner: PurchaseOwner,
    now: datetime,
) -> EntitlementDecision:
    if "testPurchaseContext" in payload:
        context = _object(payload["testPurchaseContext"])
        if context.get("fopType") != "TEST" or not owner.allow_test_purchases:
            raise UnsupportedPurchase()
    _owner_identifier(payload, owner)
    item = _one_item(payload, "productLineItem")
    product = catalog.product(_string(item.get("productId")), ProductKind.LIFETIME)
    offer = _object(item.get("productOfferDetails"))
    if _string(offer.get("purchaseOptionId")) not in product.purchase_option_ids:
        raise UnsupportedPurchase()
    if "rentOfferDetails" in offer or "preorderOfferDetails" in offer:
        raise UnsupportedPurchase()
    if type(offer.get("quantity")) is not int or offer["quantity"] != 1:
        raise UnsupportedPurchase()
    if "refundableQuantity" in offer:
        quantity = offer["refundableQuantity"]
        if type(quantity) is not int or quantity not in (0, 1):
            raise InvalidPurchase()
    # A refund amount alone does not prove access was revoked.
    if offer.get("consumptionState") != "CONSUMPTION_STATE_YET_TO_BE_CONSUMED":
        raise UnsupportedPurchase()
    state = _string(_object(payload.get("purchaseStateContext")).get("purchaseState"))
    states = {
        "PENDING": EntitlementState.PENDING,
        "PURCHASED": EntitlementState.LIFETIME,
        "CANCELLED": EntitlementState.REVOKED,
    }
    if state not in states:
        raise InvalidPurchase()
    completed = _timestamp(payload["purchaseCompletionTime"]) if "purchaseCompletionTime" in payload else None
    if state == "PURCHASED" and completed is None:
        raise InvalidPurchase()
    return _decision(
        state=states[state],
        product=product,
        snapshot=snapshot,
        catalog=catalog,
        now=now,
        access_until=None,
        purchased_at=completed,
        acknowledgement=_acknowledgement(payload),
    )
