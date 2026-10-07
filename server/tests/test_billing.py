"""Deterministic contract fixtures; fake adapters are tests only, never production."""

from contextlib import contextmanager
from copy import deepcopy
from dataclasses import replace
from datetime import datetime, timedelta, timezone
from threading import RLock
import unittest
from uuid import uuid4

from geode_billing.domain import (
    ACKNOWLEDGED,
    ACK_PENDING,
    Catalog,
    ConcurrentUpdate,
    EntitlementState,
    IdempotencyConflict,
    InvalidPurchase,
    LedgerUnavailable,
    OwnershipConflict,
    PlaySnapshot,
    Product,
    ProductKind,
    PurchaseOwner,
    StaleSnapshot,
    UnsupportedPurchase,
    VerificationUnavailable,
    evaluate_purchase,
)
from geode_billing.service import BillingService, VerifyCommand


NOW = datetime(2026, 10, 7, 12, tzinfo=timezone.utc)
OWNER = PurchaseOwner("commerce-one", "opaque-account-one")
CAPABILITIES = frozenset(
    {"PREMIUM_VISUALS", "HIGH_RESOLUTION_EXPORT", "LONG_EXPORT", "WATERMARK_FREE_EXPORT"}
)
CATALOG = Catalog(
    "dev.geode",
    (
        Product("premium_monthly", ProductKind.SUBSCRIPTION, CAPABILITIES, frozenset({"monthly-auto"})),
        Product("premium_yearly", ProductKind.SUBSCRIPTION, CAPABILITIES, frozenset({"yearly-auto"})),
        Product("premium_lifetime", ProductKind.LIFETIME, CAPABILITIES, purchase_option_ids=frozenset({"buy"})),
    ),
)


def timestamp(value):
    return value.isoformat().replace("+00:00", "Z")


def subscription(state="SUBSCRIPTION_STATE_ACTIVE", expiry=None):
    return {
        "subscriptionState": state,
        "startTime": timestamp(NOW - timedelta(days=1)),
        "acknowledgementState": ACK_PENDING,
        "externalAccountIdentifiers": {"obfuscatedExternalAccountId": OWNER.obfuscated_account_id},
        "lineItems": [
            {
                "productId": "premium_monthly",
                "expiryTime": timestamp(expiry if expiry is not None else NOW + timedelta(days=29)),
                "offerDetails": {"basePlanId": "monthly-auto"},
                "autoRenewingPlan": {"autoRenewEnabled": True},
            }
        ],
    }


def lifetime(state="PURCHASED"):
    return {
        "purchaseStateContext": {"purchaseState": state},
        "purchaseCompletionTime": timestamp(NOW - timedelta(hours=1)),
        "acknowledgementState": ACK_PENDING,
        "obfuscatedExternalAccountId": OWNER.obfuscated_account_id,
        "productLineItem": [
            {
                "productId": "premium_lifetime",
                "productOfferDetails": {
                    "purchaseOptionId": "buy",
                    "quantity": 1,
                    "refundableQuantity": 1,
                    "consumptionState": "CONSUMPTION_STATE_YET_TO_BE_CONSUMED",
                },
            }
        ],
    }


def decision(payload, kind=ProductKind.SUBSCRIPTION, owner=OWNER):
    return evaluate_purchase(PlaySnapshot("dev.geode", kind, "test-fingerprint", NOW, payload), CATALOG, owner, NOW)


class ReducerTests(unittest.TestCase):
    def test_subscription_lifecycle_and_expiry_boundaries(self):
        cases = (
            ("SUBSCRIPTION_STATE_ACTIVE", EntitlementState.ACTIVE, True),
            ("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", EntitlementState.GRACE, True),
            ("SUBSCRIPTION_STATE_CANCELED", EntitlementState.CANCELED_ACTIVE, True),
            ("SUBSCRIPTION_STATE_ON_HOLD", EntitlementState.ON_HOLD, False),
            ("SUBSCRIPTION_STATE_PAUSED", EntitlementState.PAUSED, False),
            ("SUBSCRIPTION_STATE_PENDING", EntitlementState.PENDING, False),
            ("SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED", EntitlementState.PENDING_CANCELED, False),
        )
        for raw, expected, allowed in cases:
            with self.subTest(state=raw):
                result = decision(subscription(raw))
                self.assertEqual(expected, result.state)
                self.assertEqual(allowed, result.allows_access(NOW))
                self.assertEqual(allowed, result.acknowledgement_required)
                if not allowed:
                    self.assertEqual(frozenset(), result.capabilities)
        for raw in ("SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_CANCELED", "SUBSCRIPTION_STATE_EXPIRED"):
            with self.subTest(expired=raw):
                result = decision(subscription(raw, expiry=NOW))
                self.assertEqual(EntitlementState.EXPIRED, result.state)
                self.assertFalse(result.allows_access(NOW))
                self.assertFalse(result.acknowledgement_required)

    def test_pending_does_not_need_completion_or_expiry_and_never_acknowledges(self):
        payload = subscription("SUBSCRIPTION_STATE_PENDING")
        del payload["startTime"]
        del payload["lineItems"][0]["expiryTime"]
        payload["isPremium"] = True  # An unrecognized caller-style flag is irrelevant.
        result = decision(payload)
        self.assertFalse(result.allows_access(NOW))
        self.assertIsNone(result.acknowledgement_due_at)
        item = lifetime("PENDING")
        del item["purchaseCompletionTime"]
        self.assertFalse(decision(item, ProductKind.LIFETIME).acknowledgement_required)

    def test_acknowledgement_is_only_needed_for_an_eligible_unacknowledged_purchase(self):
        payload = subscription()
        result = decision(payload)
        self.assertEqual(NOW + timedelta(days=2), result.acknowledgement_due_at)
        payload["acknowledgementState"] = ACKNOWLEDGED
        self.assertFalse(decision(payload).acknowledgement_required)

    def test_lifetime_refund_amount_does_not_imply_revocation(self):
        payload = lifetime()
        payload["productLineItem"][0]["productOfferDetails"]["refundableQuantity"] = 0
        self.assertTrue(decision(payload, ProductKind.LIFETIME).allows_access(NOW))
        payload["purchaseStateContext"]["purchaseState"] = "CANCELLED"
        result = decision(payload, ProductKind.LIFETIME)
        self.assertEqual(EntitlementState.REVOKED, result.state)
        self.assertFalse(result.allows_access(NOW))
        self.assertFalse(result.acknowledgement_required)

    def test_unknown_boolean_and_malformed_values_cannot_create_a_grant(self):
        for value in (None, True, 123, "", "2026-10-07", "2026-10-07T12:00:00", "2026-99-99T00:00:00Z"):
            payload = subscription()
            payload["lineItems"][0]["expiryTime"] = value
            with self.subTest(expiry=value), self.assertRaises(InvalidPurchase):
                decision(payload)
        for value in (True, None, "UNRECOGNIZED", {"active": True}):
            payload = subscription()
            payload["subscriptionState"] = value
            payload["isPremium"] = True
            with self.subTest(state=value), self.assertRaises(InvalidPurchase):
                decision(payload)
        with self.assertRaises(UnsupportedPurchase):
            decision({"isPremium": True, "externalAccountIdentifiers": {"obfuscatedExternalAccountId": OWNER.obfuscated_account_id}})
        with self.assertRaises(TypeError):
            VerifyCommand("secret", ProductKind.LIFETIME, str(uuid4()), isPremium=True)

    def test_non_boolean_auto_renew_and_missing_purchase_time_are_rejected(self):
        payload = subscription()
        payload["lineItems"][0]["autoRenewingPlan"]["autoRenewEnabled"] = "false"
        with self.assertRaises(InvalidPurchase):
            decision(payload)
        payload = lifetime()
        del payload["purchaseCompletionTime"]
        with self.assertRaises(InvalidPurchase):
            decision(payload, ProductKind.LIFETIME)

    def test_unknown_product_plan_and_owner_are_rejected(self):
        payload = subscription()
        payload["lineItems"][0]["productId"] = "unrelated_product"
        with self.assertRaises(UnsupportedPurchase):
            decision(payload)
        payload = subscription()
        payload["lineItems"][0]["offerDetails"]["basePlanId"] = "unconfigured-plan"
        with self.assertRaises(UnsupportedPurchase):
            decision(payload)
        with self.assertRaises(OwnershipConflict):
            decision(subscription(), owner=PurchaseOwner("other", "other-obfuscated-id"))

    def test_unsupported_replacements_prepaid_and_multi_quantity_fail_closed(self):
        payload = subscription()
        payload["linkedPurchaseToken"] = "old-secret-token"
        with self.assertRaises(UnsupportedPurchase):
            decision(payload)
        payload = subscription()
        payload["lineItems"][0]["prepaidPlan"] = {}
        with self.assertRaises(UnsupportedPurchase):
            decision(payload)
        payload = lifetime()
        payload["productLineItem"][0]["productOfferDetails"]["quantity"] = True
        with self.assertRaises(UnsupportedPurchase):
            decision(payload, ProductKind.LIFETIME)
        payload["productLineItem"][0]["productOfferDetails"]["quantity"] = 2
        with self.assertRaises(UnsupportedPurchase):
            decision(payload, ProductKind.LIFETIME)

    def test_license_test_state_requires_explicit_trusted_test_policy(self):
        payload = subscription()
        payload["testPurchase"] = {}
        with self.assertRaises(UnsupportedPurchase):
            decision(payload)
        self.assertTrue(decision(payload, owner=replace(OWNER, allow_test_purchases=True)).allows_access(NOW))

    def test_observation_has_a_bounded_lifetime_and_expiry_is_exclusive(self):
        result = decision(subscription(expiry=NOW + timedelta(minutes=1)))
        self.assertTrue(result.allows_access(NOW))
        self.assertFalse(result.allows_access(NOW + timedelta(minutes=1)))
        self.assertFalse(decision(lifetime(), ProductKind.LIFETIME).allows_access(NOW + timedelta(minutes=5)))


class FakePlayVerifier:
    def __init__(self, clock):
        self.clock = clock
        self.payload = subscription()
        self.calls = 0
        self.failure = False
        self.observed_at = None
        self.package_override = None
        self.fingerprint_override = None
        self.on_fetch = None

    def fetch(self, *, package_name, kind, purchase_token, token_fingerprint):
        self.calls += 1
        if self.failure:
            raise OSError("provider unavailable")
        if self.on_fetch is not None:
            callback, self.on_fetch = self.on_fetch, None
            callback()
        return PlaySnapshot(
            self.package_override or package_name,
            kind,
            self.fingerprint_override or token_fingerprint,
            self.observed_at or self.clock(),
            deepcopy(self.payload),
        )


class FakeLedger:
    """Serialized transactional fake with rollback, confined to test source."""

    def __init__(self):
        self.purchases = {}
        self.requests = {}
        self.jobs = {}
        self.lock = RLock()
        self.fail_enqueue = False
        self.unavailable = False

    @contextmanager
    def transaction(self, *, token_fingerprint, subject_id, idempotency_key):
        if self.unavailable:
            raise OSError("database unavailable")
        with self.lock:
            before = deepcopy((self.purchases, self.requests, self.jobs))
            try:
                yield FakeTransaction(self, token_fingerprint, (subject_id, idempotency_key))
            except Exception:
                self.purchases, self.requests, self.jobs = before
                raise


class FakeTransaction:
    def __init__(self, ledger, token_key, request_key):
        self.ledger, self.token_key, self.request_key = ledger, token_key, request_key

    def get_purchase(self):
        return self.ledger.purchases.get(self.token_key)

    def get_request(self):
        return self.ledger.requests.get(self.request_key)

    def put_purchase(self, purchase):
        self.ledger.purchases[self.token_key] = purchase

    def put_request(self, request):
        self.ledger.requests[self.request_key] = request

    def enqueue_acknowledgement(self, job):
        if self.ledger.fail_enqueue:
            raise OSError("outbox write failed")
        self.ledger.jobs.setdefault(job.action_key, job)


class ServiceTests(unittest.TestCase):
    def setUp(self):
        self.now = NOW
        self.verifier = FakePlayVerifier(lambda: self.now)
        self.ledger = FakeLedger()
        self.service = BillingService(
            catalog=CATALOG,
            verifier=self.verifier,
            ledger=self.ledger,
            fingerprint_key=b"unit-test-only-not-a-production-key",
            clock=lambda: self.now,
        )

    def command(self, token="test-secret-token", kind=ProductKind.SUBSCRIPTION):
        return VerifyCommand(token, kind, str(uuid4()))

    def test_duplicate_same_request_returns_current_record_and_one_ack_job(self):
        command = self.command()
        first = self.service.verify(OWNER, command)
        replay = self.service.verify(OWNER, command)
        self.assertTrue(first.purchase.decision.allows_access(self.now))
        self.assertTrue(replay.replayed)
        self.assertEqual(first.purchase, replay.purchase)
        self.assertEqual(1, self.verifier.calls)
        self.assertEqual(1, len(self.ledger.jobs))

    def test_duplicate_token_cannot_be_claimed_by_a_different_principal(self):
        self.service.verify(OWNER, self.command())
        with self.assertRaises(OwnershipConflict):
            self.service.verify(replace(OWNER, subject_id="another-principal"), self.command())
        self.assertEqual({OWNER.subject_id}, {p.subject_id for p in self.ledger.purchases.values()})
        self.assertEqual(1, self.verifier.calls)

    def test_same_idempotency_key_with_another_token_is_rejected(self):
        command = self.command()
        self.service.verify(OWNER, command)
        with self.assertRaises(IdempotencyConflict):
            self.service.verify(OWNER, replace(command, purchase_token="different-secret-token"))
        self.assertEqual(1, len(self.ledger.purchases))

    def test_package_and_source_token_binding_are_checked(self):
        for attribute, value in (("package_override", "com.other.app"), ("fingerprint_override", "wrong-token")):
            with self.subTest(attribute=attribute):
                setattr(self.verifier, attribute, value)
                with self.assertRaises((InvalidPurchase, UnsupportedPurchase)):
                    self.service.verify(OWNER, self.command())
                setattr(self.verifier, attribute, None)
        self.assertFalse(self.ledger.purchases)

    def test_provider_and_ledger_failure_never_create_a_grant(self):
        self.verifier.failure = True
        with self.assertRaises(VerificationUnavailable):
            self.service.verify(OWNER, self.command())
        self.verifier.failure = False
        self.ledger.unavailable = True
        with self.assertRaises(LedgerUnavailable):
            self.service.verify(OWNER, self.command())
        self.assertFalse(self.ledger.purchases)

    def test_outbox_failure_rolls_back_ownership_grant_and_idempotency(self):
        self.ledger.fail_enqueue = True
        with self.assertRaises(LedgerUnavailable):
            self.service.verify(OWNER, self.command())
        self.assertFalse(self.ledger.purchases)
        self.assertFalse(self.ledger.requests)
        self.assertFalse(self.ledger.jobs)

    def test_pending_grant_is_denied_and_no_acknowledgement_is_enqueued(self):
        self.verifier.payload = subscription("SUBSCRIPTION_STATE_PENDING")
        result = self.service.verify(OWNER, self.command())
        self.assertFalse(result.purchase.decision.allows_access(self.now))
        self.assertFalse(self.ledger.jobs)

    def test_source_freshness_rejects_old_and_future_observations(self):
        for offset in (timedelta(minutes=-5), timedelta(seconds=6)):
            with self.subTest(offset=offset):
                self.verifier.observed_at = NOW + offset
                with self.assertRaises(StaleSnapshot):
                    self.service.verify(OWNER, self.command())
        self.assertFalse(self.ledger.purchases)

    def test_an_older_observation_cannot_overwrite_a_newer_record(self):
        first = self.service.verify(OWNER, self.command())
        self.now += timedelta(seconds=20)
        self.verifier.observed_at = NOW - timedelta(seconds=1)
        with self.assertRaises(StaleSnapshot):
            self.service.verify(OWNER, self.command())
        self.assertEqual(first.purchase, next(iter(self.ledger.purchases.values())))

    def test_stale_idempotency_retry_requires_reverification(self):
        command = self.command()
        self.service.verify(OWNER, command)
        self.now += timedelta(minutes=6)
        self.verifier.failure = True
        with self.assertRaises(VerificationUnavailable):
            self.service.verify(OWNER, command)
        self.assertFalse(next(iter(self.ledger.purchases.values())).decision.allows_access(self.now))

    def test_old_request_retry_cannot_resurrect_a_revoked_purchase(self):
        self.verifier.payload = lifetime()
        command = self.command(kind=ProductKind.LIFETIME)
        self.service.verify(OWNER, command)
        self.now += timedelta(seconds=1)
        self.verifier.payload = lifetime("CANCELLED")
        self.service.verify(OWNER, self.command(kind=ProductKind.LIFETIME))
        replay = self.service.verify(OWNER, command)
        self.assertTrue(replay.replayed)
        self.assertEqual(EntitlementState.REVOKED, replay.purchase.decision.state)
        self.assertFalse(replay.purchase.decision.allows_access(self.now))

    def test_concurrent_revision_change_discards_the_fetched_response(self):
        first = self.service.verify(OWNER, self.command())

        def concurrent_revocation():
            old = self.ledger.purchases[first.purchase.token_fingerprint]
            revoked = replace(old.decision, state=EntitlementState.REVOKED, capabilities=frozenset(), acknowledgement_required=False)
            self.ledger.purchases[old.token_fingerprint] = replace(old, decision=revoked, revision=old.revision + 1)

        self.verifier.on_fetch = concurrent_revocation
        with self.assertRaises(ConcurrentUpdate):
            self.service.verify(OWNER, self.command())
        self.assertEqual(EntitlementState.REVOKED, next(iter(self.ledger.purchases.values())).decision.state)

    def test_tokens_are_omitted_from_domain_representations(self):
        command = self.command()
        result = self.service.verify(OWNER, command)
        self.assertNotIn(command.purchase_token, repr(command))
        self.assertNotIn(command.purchase_token, repr(result))
        self.assertNotIn(command.purchase_token, repr(next(iter(self.ledger.jobs.values()))))


if __name__ == "__main__":
    unittest.main()
