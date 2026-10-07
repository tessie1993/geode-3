# Billing security domain foundation

This directory contains **Python standard-library domain code and test fixtures**.
It is not a deployed backend, HTTP API, payment system or production verifier.
No credentials, Google calls, database adapter, account authentication, Integrity
verification, acknowledgement worker, RTDN receiver or signed lease issuer are
implemented. It must not be wired directly to untrusted Android request JSON.

Implementation contract: [Security, auth and premium blueprint](../docs/blueprint/SECURITY_AUTH_PREMIUM.md).

## Implemented

- Strict reducers for a trusted Play SubscriptionPurchaseV2 or ProductPurchaseV2
  observation: active, pending, grace, hold, pause, cancellation/expiry and lifetime
  cancellation; acknowledgement eligibility and deadline.
- Explicit package/product/base-plan/purchase-option catalog. No production product
  configuration is invented; the caller supplies actual Console identifiers.
- Obfuscated commerce-owner matching, rejection of unauthorized test purchases,
  timestamp/type validation, source freshness and token-to-response binding.
- Mandatory `PlayVerifier` and `PurchaseLedger` protocols. Failure cannot synthesize
  premium. The service never accepts a client `isPremium`, price or expiry field.
- Keyed token fingerprints, subject-scoped idempotency, one global token owner,
  optimistic revision checks and atomic acknowledgement-outbox intent.
- Regression fixtures covering lifecycle, malformed input, concurrency, stale
  observations, duplicate ownership and rollback. Fakes exist only in tests.

Capability wire identifiers match Android's `PremiumCapability` enum exactly:
`PREMIUM_VISUALS`, `HIGH_RESOLUTION_EXPORT`, `LONG_EXPORT`,
`WATERMARK_FREE_EXPORT`. Catalog fixtures use those names; unknown identifiers
must not implicitly grant a capability in the eventual HTTP/Android adapter.

`EntitlementDecision.allows_access(now)` evaluates the short-lived observation;
the `state` string or nonempty capability set alone is not an authorization check.
This is **not** the blueprint's 72-hour/30-day signed offline lease. A refund amount
alone is not treated as access revocation; the verified purchase state controls
the decision. No lifetime purchase is consumed.

## Required adapter guarantees

1. An authenticated server layer resolves `PurchaseOwner` from the stored commerce
   subject and verified intent. Neither `subject_id`, `allow_test_purchases` nor
   `obfuscated_account_id` comes directly from caller claims. Profile identity and
   commerce ownership remain independent. New-install Restore needs a separate
   authorized access-grant flow; changing the owner passed here is not Restore.
2. `PlayVerifier.fetch` calls Google's API over validated TLS with backend-only
   credentials, using the requested package/type/token. It timestamps the actual
   successful read on the server and binds the returned snapshot to that token.
   Payload `verified`, timestamp or premium flags cannot attest their own trust.
   Never construct paid snapshots on timeout or accept mobile-supplied Play JSON.
3. `PurchaseLedger.transaction` must atomically lock the global token key and
   subject+idempotency key, including keys that do not exist yet. Enforce SQL unique
   constraints. Purchase, request and outbox writes either all commit or all roll
   back. A network fetch occurs outside these transactions; a changed revision
   requires a new Play fetch, never blind retry of the old response.
4. Persist acknowledgement jobs uniquely by action key and encrypt their raw
   token. `repr` redaction is not storage encryption. The worker reads the current
   ledger before acknowledging, uses the correct Google acknowledgement endpoint,
   retries durably, records completion and alerts well before its deadline. Jobs
   for subsequently revoked purchases must not blindly acknowledge obsolete work.
5. Resolve each idempotent replay against the **current** purchase record, not a
   cached earlier premium decision. An expired observation must be reverified.
   The eventual HTTP layer may replay an operation receipt, but must not issue a
   new lease from an obsolete cached response after revocation.
6. Inject a secret HMAC key from the secret service and a trusted server clock.
   Token fingerprint key rotation requires an atomic migration/dual lookup that
   preserves token uniqueness; replacing the key alone would create new keys for
   old purchases. Never log tokens, provider bodies or credential exceptions.

The initial reducer intentionally rejects linked replacements, out-of-app claim
contexts, multiple line items, prepaid/installment plans, rentals, preorders,
consumed lifetime products and multi-quantity purchases. Missing required owner or
catalog fields fail closed. Supporting them needs verified fixtures and, for
replacement tokens, a transaction spanning the old/new lineages. Rejection leaves
an existing record untouched; it does not fabricate a new entitlement or erase an
independent valid purchase. Aggregate access across multiple valid products,
RTDN/voided-purchase reconciliation, lease signing and production lifecycle support
are still required before billing can ship.

## Verification

From the repository root, CI should run:

```bash
PYTHONPATH=server python -m unittest discover -s server/tests -v
```

Use Python 3.11 or newer. No third-party dependencies are required. No local tests,
compilation or lint were run for this task; GitHub Actions is the required gate.
Production adapters additionally need live Play sandbox, SQL concurrency/fault
injection, authorization and security tests described in the blueprint.

Official response contracts:
[SubscriptionPurchaseV2](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2),
[ProductPurchaseV2](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.productsv2),
[purchase security](https://developer.android.com/google/play/billing/security).
