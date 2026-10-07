# Account and premium foundation

This package contains pure policy/state models and an unwired Compose screen.
It does **not** implement Google sign-in, Play purchases, cryptographic lease
verification, a backend, network requests or secure credential storage. It is
not production authentication or proof that premium is ready to sell.

## Current entry points

| API | Behavior | Integration responsibility |
|---|---|---|
| `AccountState.signedOut()` | Returns a new state with profile identity cleared and the same commerce principal | Revoke/clear real profile credentials, cancel profile jobs and clear Credential Manager state separately |
| `CommercePrincipal` | Separates installation/key/package/environment/commerce ownership from Google profile identity | Populate from authenticated installation/commerce registration; do not infer ownership from Google email |
| `PremiumAccessPolicy.evaluate(capability, entitlement, context)` | Returns a typed allow/deny decision for one supplied grant | Supply authenticated metadata, principal, revision floor and trusted time; enforce the result at feature/job entry |
| `VerifiedTimeAnchor` | Derives time from an authenticated server baseline plus monotonic elapsed time during the same boot | Establish/store the baseline securely; supply real elapsed time/boot identity; refresh after reboot or uncertain history |
| `PremiumUnlockUiState` | Holds display catalog, selection, operation and ownership independently | Publish new immutable state from repositories; payment callbacks do not set ownership directly |
| `PremiumUnlockScreen(...)` | Shows offers/status and emits selection, purchase, restore, retry, privacy, terms, dismiss and optional manage callbacks | Resolve fresh BillingClient offer data at launch; never trust a displayed price as purchase authority |

The default screen state is `PremiumCatalogState.Unconfigured`. It has no
fabricated prices, cannot purchase/restore and explains that these operations
are unavailable in this build. A debug preview can exercise this state without
creating an account or contacting a service. `Loading` should only be published
when a real product lookup is underway.

The policy checks verified-service source/status, principal/key/environment and
audience binding, product allowlist, revision floor, eligible purchase state,
requested capability, positive/ordered timestamps, exclusive expiry, overflow
and proposed 72-hour subscription / 30-day lifetime offline limits. Subscription
access is additionally capped by authoritative product expiry. Lifetime has no
scheduled product expiry but still needs a bounded cached verification lease.
Free playback, safety and access to user data are not premium capabilities.

## Critical trust boundary

`VerifiedEntitlement` is a typed **input to policy**, not a signature verifier.
Its `LeaseVerification.VERIFIED` enum value is not proof by itself. Never map a
JSON Boolean/status, imported settings, purchase callback, profile login or a
debug toggle directly into that value. Only a real verification adapter may
construct trusted input after checking the server's signed claims and response
context. None exists in this package yet. A modified client can also patch local
checks; this foundation does not promise tamper-proof offline access.

Likewise, `PremiumOwnershipUiState.Owned` is display state only. It must come from
a still-valid access decision and must never be consulted as an authorization
source. `PremiumOperation.RestoreFinished` does not set ownership. The policy
evaluates one grant; aggregation of several purchase lineages, reconciliation,
known revocation and revision persistence belong in EntitlementRepository.

## Required implementation before production wiring

1. **Credential Manager adapter and IdentityRepository:** explicit Google
   sign-in/cancellation, server challenge/token exchange, signed-in session,
   refresh/revocation, sign-out, reauthentication and deletion. Keep profile and
   installation billing sessions independent. `signedOut()` alone performs no
   network or credential-store operation.
2. **BillingClient adapter and BillingRepository:** connection/reconnection,
   subscription and one-time product discovery, eligible offer selection,
   pending transactions, payment launch, owned-purchase query and restore.
   Populate localized price/recurrence/full disclosure from actual product
   details; map current offers by key when launching. Do not consume lifetime.
3. **Verification backend:** authenticated installation principals and purchase
   intents, Play API verification, unique commerce ownership, lineage reducer,
   durable acknowledgment, RTDN/reconciliation, revisioned capability snapshots
   and signed leases. Restore grants the new installation access to the existing
   owner; it never silently transfers ownership to a selected Google profile.
4. **Cryptographic lease adapter:** use a reviewed maintained implementation to
   validate signature, pinned algorithm/key ID, key rotation, issuer, audience,
   environment, installation-key binding and the exact signed capability/time/
   revision claims. Reject malformed or altered bytes before policy input.
   No custom signing/verifying implementation or embedded private key is supplied.
5. **Secure store and trusted clock integration:** protect installation keys and
   sessions with Android Keystore-backed storage; exclude keys, credentials,
   commerce tokens and leases from Auto Backup/Drive backup. Persist the highest
   authenticated aggregate revision per principal and trusted time anchor.
   Reinstall/key loss/uncertain clock history requires registration and Restore.
6. **EntitlementRepository and execution gates:** aggregate eligible independent
   lineages without letting an expired subscription erase valid lifetime access;
   reconcile revocations and replayed revisions. Refresh/re-evaluate on time,
   account/installation context, foreground and connectivity changes. Validate
   new exports/scene actions at execution, not only button display; accepted
   immutable jobs must not corrupt user data if a lease later expires.
7. **Production UI and disclosure:** wire real product/pricing and privacy/terms/
   management destinations, retry/recovery, entitlement refresh, optional
   identity/Drive, deletion and support. Align existing offline-only app copy,
   manifest/network configuration and actual data disclosures together.

See `docs/blueprint/SECURITY_AUTH_PREMIUM.md` for the full contract, proposed
backend/API design, tradeoffs and launch blockers. These interfaces do not
configure Console products, OAuth clients, prices, keys, infrastructure or URLs.

## Verification status

Tests are supplied in `app/src/test/java/dev/geode/account/` for policy attack
cases, profile/commerce separation, immutable catalog snapshots and unavailable/
pending/owned/restore UI decisions. They exercise model/policy behavior without
network, Play or signatures. Execute them in GitHub Actions; no local compile,
tests or lint were run while creating this package. Passing these tests cannot
certify real authentication, billing or signature validation. Play sandbox,
staging API/security tests and physical-device journeys remain required.
