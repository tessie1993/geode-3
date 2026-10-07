"""Verifier orchestration with mandatory trusted I/O and atomic ledger contracts."""

from collections.abc import Callable, Iterator
from contextlib import AbstractContextManager, contextmanager
from dataclasses import dataclass, field
from datetime import datetime
import hashlib
import hmac
import json
from typing import Protocol
from uuid import UUID

from .domain import (
    BillingError,
    Catalog,
    ConcurrentUpdate,
    EntitlementDecision,
    IdempotencyConflict,
    InvalidPurchase,
    LedgerUnavailable,
    OwnershipConflict,
    PlaySnapshot,
    ProductKind,
    PurchaseOwner,
    StaleSnapshot,
    UnsupportedPurchase,
    VerificationUnavailable,
    evaluate_purchase,
    utc,
)


@dataclass(frozen=True)
class VerifyCommand:
    purchase_token: str = field(repr=False)
    kind: ProductKind
    idempotency_key: str


@dataclass(frozen=True)
class PurchaseRecord:
    token_fingerprint: str
    subject_id: str
    package_name: str
    decision: EntitlementDecision
    revision: int


@dataclass(frozen=True)
class RequestRecord:
    request_digest: str
    token_fingerprint: str


@dataclass(frozen=True)
class AcknowledgementJob:
    action_key: str
    package_name: str
    product_id: str
    kind: ProductKind
    token_fingerprint: str
    purchase_token: str = field(repr=False)
    due_at: datetime


@dataclass(frozen=True)
class VerificationResult:
    purchase: PurchaseRecord
    replayed: bool


class PlayVerifier(Protocol):
    def fetch(
        self,
        *,
        package_name: str,
        kind: ProductKind,
        purchase_token: str,
        token_fingerprint: str,
    ) -> PlaySnapshot:
        """Call Google's authorized API, not a mobile payload or unsigned cache.

        Use the requested package/type/token; bind all to the returned snapshot.
        retrieved_at is the trusted server's actual successful read time. Bound
        response size, timeout, retries, TLS and credentials in this adapter.
        No network/configuration failure may produce a synthetic paid snapshot.
        """
        ...


class LedgerTransaction(Protocol):
    def get_purchase(self) -> PurchaseRecord | None:
        ...

    def get_request(self) -> RequestRecord | None:
        ...

    def put_purchase(self, purchase: PurchaseRecord) -> None:
        ...

    def put_request(self, request: RequestRecord) -> None:
        ...

    def enqueue_acknowledgement(self, job: AcknowledgementJob) -> None:
        """Unique action_key; encrypt purchase_token before durable storage."""
        ...


class PurchaseLedger(Protocol):
    def transaction(
        self,
        *,
        token_fingerprint: str,
        subject_id: str,
        idempotency_key: str,
    ) -> AbstractContextManager[LedgerTransaction]:
        """Lock purchase key AND (subject, idempotency key), including absent rows.

        The purchase token has one global owner, not one owner per user. Request
        keys are unique per subject. Commit purchase, request and ack outbox
        atomically on successful context exit; roll back all on any exception.
        Production must enforce these invariants with SQL constraints/locking,
        not just the service's read-before-write checks. No network read occurs
        inside this transaction. A key's revision is strictly increasing.
        """
        ...


class BillingService:
    """Existing-owner verification only; not an HTTP/auth/restore implementation."""

    def __init__(
        self,
        *,
        catalog: Catalog,
        verifier: PlayVerifier,
        ledger: PurchaseLedger,
        fingerprint_key: bytes,
        clock: Callable[[], datetime],
    ) -> None:
        if verifier is None or ledger is None or not callable(clock):
            raise ValueError("Trusted verifier, transactional ledger and clock required")
        if not isinstance(fingerprint_key, bytes) or len(fingerprint_key) < 32:
            raise ValueError("A server secret of at least 32 bytes is required")
        self._catalog = catalog
        self._verifier = verifier
        self._ledger = ledger
        self._fingerprint_key = fingerprint_key
        self._clock = clock

    def verify(self, owner: PurchaseOwner, command: VerifyCommand) -> VerificationResult:
        self._validate(owner, command)
        fingerprint = hmac.new(
            self._fingerprint_key, command.purchase_token.encode("utf-8"), hashlib.sha256
        ).hexdigest()
        digest = hashlib.sha256(
            json.dumps(
                [self._catalog.package_name, command.kind.value, fingerprint],
                separators=(",", ":"),
            ).encode("utf-8")
        ).hexdigest()
        request = RequestRecord(digest, fingerprint)
        key = str(UUID(command.idempotency_key))

        with self._transaction(fingerprint, owner, key) as tx:
            original = self._checked_purchase(tx.get_purchase(), owner, fingerprint, command.kind)
            replay = self._check_request(tx.get_request(), request)
            if replay:
                if original is None:
                    raise LedgerUnavailable()
                if self._fresh(original, utc(self._clock())):
                    return VerificationResult(original, replayed=True)
            expected_revision = original.revision if original is not None else 0

        try:
            snapshot = self._verifier.fetch(
                package_name=self._catalog.package_name,
                kind=command.kind,
                purchase_token=command.purchase_token,
                token_fingerprint=fingerprint,
            )
        except BillingError:
            raise
        except Exception:
            raise VerificationUnavailable() from None
        if not isinstance(snapshot, PlaySnapshot):
            raise InvalidPurchase()
        if snapshot.token_fingerprint != fingerprint or snapshot.kind != command.kind:
            raise InvalidPurchase()
        decision = evaluate_purchase(snapshot, self._catalog, owner, utc(self._clock()))

        with self._transaction(fingerprint, owner, key) as tx:
            current = self._checked_purchase(tx.get_purchase(), owner, fingerprint, command.kind)
            replay = self._check_request(tx.get_request(), request)
            if replay and current is not None and self._fresh(current, utc(self._clock())):
                # Another copy of this request already committed. Read the current
                # record, never a cached earlier grant that a refund has superseded.
                return VerificationResult(current, replayed=True)
            actual_revision = current.revision if current is not None else 0
            if actual_revision != expected_revision:
                # Discard the fetched response. A retry must fetch Google again;
                # an older concurrent response must not undo a revocation.
                raise ConcurrentUpdate()
            if current is not None and decision.verified_at < current.decision.verified_at:
                raise StaleSnapshot()
            if not self._fresh_decision(decision, utc(self._clock())):
                raise StaleSnapshot()
            record = PurchaseRecord(
                token_fingerprint=fingerprint,
                subject_id=owner.subject_id,
                package_name=self._catalog.package_name,
                decision=decision,
                revision=actual_revision + 1,
            )
            tx.put_purchase(record)
            tx.put_request(request)
            if decision.acknowledgement_required:
                if decision.acknowledgement_due_at is None:
                    raise InvalidPurchase()
                tx.enqueue_acknowledgement(
                    AcknowledgementJob(
                        action_key="ack:" + fingerprint,
                        package_name=record.package_name,
                        product_id=decision.product_id,
                        kind=decision.kind,
                        token_fingerprint=fingerprint,
                        purchase_token=command.purchase_token,
                        due_at=decision.acknowledgement_due_at,
                    )
                )
        return VerificationResult(record, replayed=False)

    @staticmethod
    def _validate(owner: PurchaseOwner, command: VerifyCommand) -> None:
        if not isinstance(owner, PurchaseOwner) or not isinstance(command, VerifyCommand):
            raise InvalidPurchase()
        values = ((owner.subject_id, 128), (owner.obfuscated_account_id, 64), (command.purchase_token, 4096))
        for value, maximum in values:
            if not isinstance(value, str) or not value or len(value) > maximum or value != value.strip():
                raise InvalidPurchase()
            try:
                value.encode("utf-8")
            except UnicodeError:
                raise InvalidPurchase() from None
        if type(owner.allow_test_purchases) is not bool or not isinstance(command.kind, ProductKind):
            raise InvalidPurchase()
        if not isinstance(command.idempotency_key, str):
            raise InvalidPurchase()
        try:
            UUID(command.idempotency_key)
        except ValueError:
            raise InvalidPurchase() from None

    def _checked_purchase(
        self,
        record: PurchaseRecord | None,
        owner: PurchaseOwner,
        fingerprint: str,
        kind: ProductKind,
    ) -> PurchaseRecord | None:
        if record is not None:
            if record.token_fingerprint != fingerprint or record.revision < 1:
                raise LedgerUnavailable()
            if record.subject_id != owner.subject_id:
                raise OwnershipConflict()
            if record.package_name != self._catalog.package_name or record.decision.kind != kind:
                raise UnsupportedPurchase()
            self._catalog.product(record.decision.product_id, kind)
        return record

    @staticmethod
    def _check_request(existing: RequestRecord | None, requested: RequestRecord) -> bool:
        if existing is not None and existing != requested:
            raise IdempotencyConflict()
        return existing is not None

    def _fresh(self, record: PurchaseRecord, now: datetime) -> bool:
        return self._fresh_decision(record.decision, now)

    def _fresh_decision(self, decision: EntitlementDecision, now: datetime) -> bool:
        return (
            decision.verified_at <= now + self._catalog.future_clock_tolerance
            and now < decision.fresh_until
            and now - decision.verified_at < self._catalog.max_snapshot_age
        )

    @contextmanager
    def _transaction(
        self, fingerprint: str, owner: PurchaseOwner, key: str
    ) -> Iterator[LedgerTransaction]:
        try:
            with self._ledger.transaction(
                token_fingerprint=fingerprint,
                subject_id=owner.subject_id,
                idempotency_key=key,
            ) as tx:
                yield tx
        except BillingError:
            raise
        except Exception:
            raise LedgerUnavailable() from None
