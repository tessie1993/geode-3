# Security, identity and premium implementation design

Research date: **7 October 2026**. This is a target design, not implemented
functionality or a security certification. Read with [Architecture](ARCHITECTURE.md),
[Feature specification](FEATURE_SPEC.md) and [Play release plan](PLAY_RELEASE.md).

## 1. Decision and current gap

Use a small **verification backend** for production premium. The latest request
for secure premium supersedes the historical no-backend assumption in
`docs/rebuild/PLAN.md`. An APK-only purchase flag is not the production authority.
Google recommends moving purchase verification and acknowledgement to a trusted
server; possession of a Google profile does not establish a paid entitlement.
See [Play billing security](https://developer.android.com/google/play/billing/security).

The inspected repository has no billing, Credential Manager or Google identity
dependencies in `gradle/libs.versions.toml`, no backend implementation, and no
`INTERNET` permission in `app/src/main/AndroidManifest.xml`. Its README, manifest
comments and several UI strings explicitly describe no network access. Existing
Auto Backup is enabled. These facts must change together with implementation;
do not deploy networking while retaining the old privacy claim.

Keep local playback, project editing, safety controls and access to user files
available without sign-in. Network registration begins only when a person uses
an account, backup or purchase feature. Buying and restoring premium must work
without creating a Geode Google profile. New purchase verification requires a
connection; previously verified access uses the bounded offline policy below.

The backend handles identity sessions, purchase verification and minimal account
metadata. It does not receive music, microphone PCM, rendered videos, media
library contents, projects or Drive access tokens. Drive backup remains a direct
Android-to-Google operation authorized separately by the user.

## 2. Boundaries and ownership

| Component | Owns | Must not own |
|---|---|---|
| `IdentityRepository` | Optional profile, sign-in state, identity session, sign-out/deletion | Paid status; Drive authorization |
| `BillingRepository` | One BillingClient connection, product details, purchase discovery and launch | Final entitlement decision; HTTP service credentials |
| `EntitlementRepository` | Verified capability snapshot, offline lease, verification status | Google account selection; user files |
| `DriveBackupRepository` | Explicit authorization, account-bound backup jobs, validation and restore | Purchase tokens or identity refresh tokens |
| Verification API | Auth checks, validated requests, installation-scoped grants | Trust in client prices, expiry, product ownership or `isPremium` |
| Purchase worker | Play API reads, lifecycle reducer, durable acknowledgement retries | Browser/UI lifecycle |
| RTDN ingress | Authenticated event receipt and durable inbox | Granting access from notification type alone |
| PostgreSQL ledger | Unique purchase ownership, event history, grant revisions and outbox | Raw secrets in logs or analytics |

Use Kotlin interfaces and StateFlow at Android boundaries. Keep platform dialogs
in foreground Activity launchers and long-lived repositories in application scope.
Hilt binds a fake billing/verifier implementation only in debug/test source sets.
Release code has no debug premium override, hidden unlock gesture or remote
arbitrary-feature grant endpoint.

### Initial deployment proposal

Use **Google Cloud Run + Cloud SQL for PostgreSQL + Secret Manager**, with
Pub/Sub for Play notifications, Cloud Tasks for retryable work, Cloud Scheduler
for reconciliation and Cloud KMS for lease signing/token encryption. This is a
practical proposed deployment, **not provisioned infrastructure**. Hosting
project, same-region EU placement, budget, domain and operator remain owner inputs.
Keep the HTTP implementation and migrations in a separate `backend/` build; reuse
Kotlin knowledge without coupling the service to Android/NDK builds.

| Deployment | Access and minimum responsibilities |
|---|---|
| Public API Cloud Run service | Public HTTPS ingress with app authentication; installation/profile/intent operations and scoped SQL access |
| Private verifier/worker Cloud Run service | IAM-authenticated invocation; Play verification/acknowledgement, grant transactions, outbox and reconciliation |
| Private RTDN Cloud Run service | Only the configured Pub/Sub push identity may invoke; durable inbox, no account UI routes |
| Cloud SQL PostgreSQL | Same region, private connectivity, bounded connection pools, runtime SQL roles separate from migration owner, automated backups/PITR and restore drill |
| Secret Manager / KMS | Individual-secret access grants and per-key signing/encryption permissions; no project-wide secret read |
| Cloud Tasks / Scheduler | Dedicated invocation identities; retry/DLQ monitoring; no in-process job expected to survive an HTTP response |

Assign user-managed service accounts per service. Give `roles/cloudsql.client`
only where database connectivity is needed, secret accessor on named secrets,
and KMS access only on the relevant keys. The Play-calling identity gets only
the required package-level Play Console purchase permissions, not app publishing
or account administration. Pub/Sub invoker gets `roles/run.invoker` only on RTDN;
task/scheduler identities get it only on their worker targets. A Cloud role
does not automatically confer Play Console access.

Use Cloud Run service identity/Application Default Credentials rather than
downloaded service-account JSON keys. Separate deployer from runtime accounts;
federate GitHub Actions through Workload Identity Federation restricted to this
repository, approved ref and deployment environment. SQL migrations run as a
controlled deployment job, not at every container startup. Bound maximum Run
instances against the SQL pool budget, and cap task concurrency and API retries
against Play quotas. Add billing alerts and staging load evidence before choosing
minimum warm instances or production database size.

Specify infrastructure as code and isolate staging from production data/keys.
References: [Cloud Run identity](https://cloud.google.com/run/docs/securing/service-identity),
[Cloud SQL connection](https://cloud.google.com/sql/docs/postgres/connect-run),
[Cloud Run secrets](https://cloud.google.com/run/docs/configuring/services/secrets),
[Pub/Sub to Cloud Run](https://cloud.google.com/run/docs/triggering/pubsub-push),
[Deployment federation](https://cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines).

### Three distinct identities

1. **Installation principal:** random backend ID bound to a device-generated
   signing key. Supports anonymous billing sessions. It is not a hardware ID,
   advertising ID, Google email or app account.
2. **Optional profile:** backend user UUID mapped to verified Google `sub`.
   Used for account settings and deletion. Profile login never grants premium.
3. **Commerce subject:** opaque owner of a purchase ledger lineage. It is separate
   from profile identity. One token belongs to exactly one commerce subject;
   several verified installations may receive access grants to that same purchase.

The Google profile, Drive account and Play purchasing account can differ. Label
them separately. Do not claim to know a Play account's email, or compare it with
the profile email as an ownership check. Google sign-out removes profile access
but does not revoke a valid device purchase grant.

## 3. Optional Google sign-in

Use Credential Manager with `credentials`, `credentials-play-services-auth` and
the `googleid` adapter. At research time, the official release table lists
Credential Manager **1.6.0 stable**; the integration page demonstrates an alpha.
Pin a compatible stable release and verify its actual API before implementation;
do not copy alpha dependencies just because a sample uses them.
Sources: [Credential Manager releases](https://developer.android.com/jetpack/androidx/releases/credentials),
[Sign in with Google](https://developer.android.com/identity/sign-in/credential-manager-siwg).

Flow:

1. Open sign-in from Account or an explicitly requested account feature; never
   block the first local playback behind an account sheet.
2. Obtain a short-lived, one-use server challenge bound to the installation and
   intended operation. Supply its random nonce and the configured **Web OAuth
   client ID** to Credential Manager. Use the explicit Google button as the
   persistent retry path; an absent credential or dismissal leaves local use open.
3. Accept only the expected Google credential type. Send the ID token over HTTPS
   to the backend. It is never a Drive access token or a premium receipt.
4. Exchange the verified token for an app session. Discard the Google ID token
   after exchange. Do not store it as a permanent login credential.
5. On sign-out revoke the identity session, clear local profile data and cancel
   its jobs, then call Credential Manager `clearCredentialState()`. Clearing this
   state does not itself revoke Google's previously granted permissions.

Implementation reference: [Credential Manager Google flow](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation).

Backend verification uses Google's maintained verifier or an audited OIDC library:
validate the Google signature against cached rotating keys, accepted issuer,
the exact environment's Web client audience and expiry. Apply bounded clock
skew, reject malformed/missing subject, and validate the challenge nonce before
atomically consuming the challenge. Respect authorized-party semantics if a
multi-audience token is supported; initially allow only the configured audience.
Never use decoded-but-unverified claims, email or a client-provided user ID as the
identity key. Do not automatically merge two profiles because their emails match.
See [Google ID token verification](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token).

| Identity state | Allowed transition / behavior |
|---|---|
| `LOCAL_ONLY` | Explicit sign-in starts `SELECTING`; all offline core features work |
| `SELECTING` | Success → `VERIFYING`; dismissal/no credential → `LOCAL_ONLY` |
| `VERIFYING` | Valid exchange → `SIGNED_IN`; invalid/expired challenge → retryable error |
| `SIGNED_IN` | Refresh session, switch profile after sign-out, or request deletion |
| `REAUTH_REQUIRED` | Reopen explicit sign-in; do not loop prompts during playback |
| `DELETION_PENDING` | No new profile writes; expose progress and support reference |
| `SIGNED_OUT` | Profile credentials gone; commerce grant evaluated independently |

App access tokens expire after a proposed 10 minutes. Rotate refresh tokens on
use, store only a server hash, and detect reuse of a spent refresh-token family.
Use an established sender-constrained session implementation: Android Keystore
P-256 key and DPoP-bound access/refresh tokens. Validate the proof signature,
method, target URI, time/nonce, unique proof ID, access-token hash and key binding;
DPoP does not replace authentication, TLS or request-body integrity.
Reference: [RFC 9449](https://www.rfc-editor.org/rfc/rfc9449).

Keep profile sessions and installation billing sessions separate. A revoked
profile session cannot be used for deletion or account reads even if its device
billing session remains valid. Key loss starts a new installation and Restore;
never recover by trusting a copied refresh-token file.

## 4. Product and entitlement contract

Retain the historical plan's identifiers until Play Console configuration proves
otherwise:

| Product ID | Play type | Grant |
|---|---|---|
| `premium_monthly` | Auto-renewing subscription | Premium while eligible |
| `premium_yearly` | Auto-renewing subscription | Same premium capabilities |
| `premium_lifetime` | Non-consumable one-time purchase | Premium with no scheduled expiry; subject to revocation |

Do not consume lifetime purchases. Do not add prepaid plans, installment plans,
rentals or multi-quantity products in the initial catalog. If the Console already
uses one subscription with monthly/yearly base plans, map that catalog explicitly
instead of creating duplicate sale products. Product IDs/base plans are not
prices: render localized price, billing period and eligible offer terms from
fresh Play ProductDetails. Never fabricate a trial or discount.

Use one active BillingClient, configure pending purchase handling, query owned
`INAPP` and `SUBS` purchases after reconnect/foreground and on Restore, and send
each discovered token to verification. A cancelled payment sheet is a normal
outcome. `ITEM_ALREADY_OWNED` triggers discovery/verification; connection failures
use bounded backoff. The current integration guide uses Billing **9.1.0**; pin
the supported stable version during implementation and recheck release policy.
Sources: [Billing integration](https://developer.android.com/google/play/billing/integrate),
[Billing errors](https://developer.android.com/google/play/billing/errors).

The entitlement snapshot contains `capabilities`, `source`, `state`,
`verifiedAt`, `accessUntil`, `offlineUntil`, `revision` and `verificationStatus`.
Separate payment state from network errors. An HTTP timeout is not `EXPIRED`.
Capabilities, rather than a scattered Boolean, drive style/export limits. The
server supplies the allowed product-to-capability mapping; Android also contains
the minimum free capabilities. Photosensitivity controls, basic playback,
viewing user projects, deleting data and accessing exports are always free.

Existing free/proposed premium boundaries in [Feature specification](FEATURE_SPEC.md)
are product inputs, not implemented gates. Resolve export resolution/duration,
watermark, free style inventory and subscription value before listing prices.
Do not charge for capabilities the binary cannot execute.

### Lifecycle reducer

Only a verified Play API response changes purchase truth. Keep one state per
purchase lineage; derive aggregate access from all eligible lineages. A pending
second purchase or an expired subscription does not remove a valid lifetime grant.

| Verified condition | Entitlement and UI |
|---|---|
| No eligible purchase | `FREE` |
| Payment pending | `PENDING`; no new grant or acknowledgement |
| Purchased, validation running | `VERIFYING`; preserve any existing valid grant |
| Active subscription | `ACTIVE` until verified eligible line-item expiry |
| Grace period | `GRACE`; retain access, show payment recovery action |
| Auto-renew disabled / cancelled, expiry still future | `CANCELED_ACTIVE`; access through expiry |
| Account hold | `ON_HOLD`; no subscription grant, payment recovery action |
| Effective pause | `PAUSED`; no subscription grant |
| Subscription expired | `EXPIRED`; free capabilities, preserve projects |
| Valid lifetime purchase | `LIFETIME`; no scheduled product expiry |
| Revoked/voided eligible purchase | `REVOKED`; remove its grant, preserve other valid grants |
| Refund request/review only | Keep verified current access; a request is not a revocation |
| Restore in progress | `RESTORING` operation over current access, not a grant |
| API failure / unknown new state | `VERIFICATION_UNAVAILABLE`; no new grant, bounded existing lease |

Subscription definitions and authoritative reads:
[Subscription lifecycle](https://developer.android.com/google/play/billing/lifecycle/subscriptions),
[SubscriptionPurchaseV2](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2).
One-time state and acknowledgement fields:
[One-time lifecycle](https://developer.android.com/google/play/billing/lifecycle/one-time),
[ProductPurchaseV2](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.productsv2).

Cancellation is not immediate revocation. Refund and revoke are also different
operations: reduce verified entitlement state rather than treating every refund
message as identical. A pending replacement does not invalidate the existing
eligible subscription. When a replacement becomes effective, atomically retire
the old lineage's overlapping grant according to verified replacement timing.
Retain token tombstones for deduplication; do not lose audit history by deleting
the old database row. An unknown state triggers reconciliation and an alert.

## 5. Purchase verification and durable acknowledgement

For a new payment, the backend first creates an expiring purchase intent and
returns an opaque obfuscated commerce-subject identifier. Set it as
`obfuscatedAccountId` in BillingFlowParams. It contains no email. The backend
checks the identifier returned by Play against the recorded intent/subject;
client-supplied fields are only routing hints.

The verifier follows this transaction:

1. Authenticate the installation, validate schema, catalog and rate limit, and
   validate any required fresh integrity challenge. Bound product/token lengths.
2. Fetch Play state for the server-selected production package. Use
   `purchases.subscriptionsv2.get` for subscriptions and
   `purchases.productsv2.getproductpurchasev2` for lifetime. Derive product,
   purchase state, completion/expiry, identifiers and test status from Google.
3. Verify the product/base plan against the allowlist and environment. Production
   users cannot receive real production grants from test fixtures. License-test
   purchases are supported only for authorized testing principals/configurations.
4. Deduplicate by the token fingerprint under a database unique constraint.
   Repeated submissions for the same authorized subject return the same result.
   A token cannot create another commerce owner or another independent grant.
5. Serialize updates for the purchase and linked lineage. In one SQL transaction,
   store verified state, replace obsolete grants, increase the entitlement revision
   and insert acknowledgement/reconciliation work into a transactional outbox.
6. A worker acknowledges an eligible, granted purchase if it is not already
   acknowledged. Retry transient failures; treat duplicate delivery idempotently.
   Record successful acknowledgement separately from the premium state.
7. Deliver a signed entitlement lease only from committed state. A lost HTTP
   response or app process death cannot lose the grant or the acknowledgement job.

Google's purchase verification guidance requires a verified purchased state and
unique token handling. Acknowledge promptly after durable grant; for the initial
auto-renewing/non-consumable catalog the unacknowledged refund deadline is three
days after completion, not time spent pending. Use a minutes-scale operational
target and an alarm on the oldest pending job, not the deadline as a queue budget.
See [Billing security](https://developer.android.com/google/play/billing/security).

Read API: [Product V2 verification](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.productsv2/getproductpurchasev2).
Write APIs: [Product acknowledgement](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.products/acknowledge),
[Subscription acknowledgement](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptions/acknowledge).
Do not invent a `subscriptionsv2.acknowledge` endpoint. Renewals do not create a
new acknowledgement obligation for an already acknowledged purchase token;
new purchase tokens are evaluated independently.

### Ledger constraints

| Record | Required fields / invariants |
|---|---|
| `installations` | Random ID, public-key thumbprint, environment, created/revoked time |
| `users` / `identity_sessions` | UUID, unique Google issuer+subject, optional display fields, revocation generation |
| `commerce_subjects` | Random immutable ID; no dependency on Google profile |
| `purchase_intents` | Subject, allowed product/plan, expiry, issuing installation, opaque account ID |
| `purchases` | Unique keyed token fingerprint, encrypted token, subject, package/product/type, Play state, acknowledgement state, last verification |
| `purchase_lineages` | Replacement links, effective intervals; cycle rejection and no overlapping duplicate grant |
| `installation_grants` | Installation, purchase/lineage, status, revision and verification evidence |
| `event_inbox` | Unique provider+message ID, payload reference, status, attempts and receive time |
| `outbox` | Unique action+purchase+revision, next retry, attempts, durable result |
| `deletion_jobs` | Scoped subject, requested time, erase stages, retention exceptions and completion |

Use HMAC-SHA-256 for token lookup fingerprints and authenticated encryption under
managed keys for tokens needed for later Play calls. Keep fingerprint/encryption
keys versioned and separate. Database uniqueness covers concurrent client, RTDN
and restore traffic; an application-level pre-check alone is insufficient.

## 6. Restore without mandatory profile login

Restore obtains current owned tokens from Play, verifies them online, then issues
installation-scoped access to the **existing** purchase. It does not transfer
the token into another user's profile. Existing obfuscated subject/lineage
mapping must remain unchanged, even after reinstall when the installation ID
has changed. Keep user-facing Restore visible from both Paywall and Settings.

This separation is deliberate: a guest who reinstalls can restore through Play,
and switching Google profiles cannot steal or erase a purchase. A Google login
by itself never returns all purchases associated with the email. The initial
product offers no cross-platform premium and no email-based entitlement transfer.

Unknown legacy/out-of-app tokens need a defined claim path: retrieve all available
Play context and previous-token lineage, reconcile to a known commerce subject
where possible, and quarantine ambiguous ownership for support. An order number,
email screenshot or caller-provided old user ID is not ownership proof. RTDN that
arrives before the app submits its purchase is matched through the server-issued
obfuscated subject; unmatched events remain durable until reconciled.

**Residual risk:** a purchase token is a sensitive bearer-like proof. Neither
Google profile login nor Play Integrity proves that a token came from a specific
BillingClient query. Anonymous restore cannot eliminate deliberate token sharing
on a compromised client. Protect tokens, require a fresh legitimate-app signal
for sensitive restore, rate-limit new-device grants and monitor abnormal fan-out.
Never describe this design as piracy-proof. It prevents duplicate ledger owners
and forged payment state; it does not make offline APK code unmodifiable.

Multiple genuine devices are allowed. Device limits require an explicit product
policy and recovery UI; do not silently lock out a paying user because a phone
was replaced. A successful empty Play query is not alone a verified refund.
On an explicit change of purchasing context, clear that installation's displayed
purchase association and run Restore; do not reveal another profile's metadata.

## 7. Backend API contract

These are proposed **Geode endpoints**, not existing routes or Google APIs.
Publish their OpenAPI schema before implementation. Examples contain placeholders,
not real credentials. API base URL is an unconfigured environment input.

All production routes require HTTPS. Authenticated routes use
`Authorization: DPoP <app-access-token>` plus a valid `DPoP` proof header. Profile
operations require a profile-scoped token; billing operations accept an
installation-scoped token. Enforce scopes and object ownership on the server.
No caller-selected user ID substitutes for the authenticated subject.

Every mutating client operation sends a UUID `Idempotency-Key`. Store request
digest plus response per principal+route+key; replay returns that result and a
changed body with the same key returns `409 IDEMPOTENCY_CONFLICT`. Sensitive
responses use `Cache-Control: no-store`; never put tokens in URLs. Proposed
limits: 64 KiB JSON bodies, 20 purchase tokens per restore batch, bounded string
fields, per-principal/IP throttles, and tighter challenge/new-device quotas.

| Method / route | Request | Response / authorization |
|---|---|---|
| `POST /v1/installations/challenge` | Public key thumbprint, package/version | One-use challenge; throttled bootstrap, no premium |
| `POST /v1/installations` | Challenge ID, public key, proof, integrity response | Installation-scoped session; no profile |
| `POST /v1/auth/google/challenge` | Installation session for `SIGN_IN`; profile session for `DELETE_ACCOUNT` | Random nonce, operation-bound challenge ID, expiry |
| `POST /v1/auth/google/exchange` | Challenge ID, Google ID token | Profile session for sign-in; limited reauthentication proof for deletion |
| `POST /v1/auth/refresh` | Rotating refresh token and device proof | Replacement access/refresh pair |
| `POST /v1/auth/logout` | Current profile session, `allDevices` flag | `204`; revokes selected profile session family |
| `POST /v1/billing/intents` | Product ID, base-plan/offer selection | Intent ID and obfuscated account ID, expiry |
| `POST /v1/billing/challenges` | Operation `VERIFY` or `RESTORE`, digest of planned business payload | One-use installation/operation-bound challenge ID and expiry |
| `POST /v1/billing/verify` | Intent ID if new, token, product-type hint, challenge evidence | Verified snapshot, pending state or durable `202` operation |
| `POST /v1/billing/restore` | Discovered token batch and fresh challenge evidence | Per-token status plus aggregate snapshot; no ownership transfer |
| `GET /v1/entitlements` | Installation session; optional revision | Current snapshot and signed offline lease |
| `GET /v1/operations/{id}` | Session that owns operation | Pending/completed outcome, no raw Play payload |
| `POST /v1/accounts/me/deletion` | Profile session, fresh `reauthToken`, confirmation | `202`, deletion ID, restricted status credential and retained-data explanation |
| `GET /v1/accounts/me/deletion/{id}` | Restricted deletion-status credential | Status only; remains usable after profile revocation |
| `POST /internal/play/rtdn` | Authenticated Pub/Sub envelope | `204` after durable inbox write; not accessible with app tokens |

Bootstrap integrity binds the server challenge and public-key thumbprint.
Purchase/restore integrity binds a canonical hash of challenge ID, installation,
method, route, idempotency key and business payload excluding the integrity token.
Validate the decoded request hash and permitted package/signing certificate/version
server-side. Discard evidence after its useful retention window. Integrity errors
produce a recovery path; they never disable free local use. Reference:
[Standard Integrity requests](https://developer.android.com/google/play/integrity/standard).

Challenges expire after a proposed five minutes and are consumed atomically.
Deletion reauthentication must verify the **same Google subject** as the existing
profile session; a different account is rejected instead of silently switching
the deletion target. Its resulting `reauthToken` is one-use, expires after five
minutes and authorizes only deletion of that profile from that installation.
The deletion-status credential authorizes only the named job's progress after
ordinary identity sessions have been revoked.

Google exchange example:

```json
{
  "challengeId": "challenge_uuid",
  "googleIdToken": "<short-lived-token>"
}
```

```json
{
  "user": {"id": "user_uuid", "displayName": "Optional name"},
  "accessToken": "<app-profile-session>",
  "tokenType": "DPoP",
  "expiresIn": 600,
  "refreshToken": "<rotating-token>"
}
```

Purchase verification example:

```json
{
  "intentId": "intent_uuid",
  "productType": "SUBS",
  "purchaseToken": "<play-purchase-token>",
  "challengeId": "challenge_uuid",
  "integrityToken": "<request-bound-evidence>"
}
```

```json
{
  "verificationStatus": "VERIFIED",
  "acknowledgement": "QUEUED",
  "entitlement": {
    "state": "ACTIVE",
    "source": "PLAY_SUBSCRIPTION",
    "capabilities": ["premium_styles", "premium_export"],
    "revision": 42,
    "verifiedAt": "2026-10-07T00:00:00Z",
    "accessUntil": "2026-11-07T00:00:00Z",
    "offlineUntil": "2026-10-10T00:00:00Z"
  },
  "offlineLease": "<server-signed-jws>"
}
```

`QUEUED` means a verified grant is durable and acknowledgement is being retried;
it is not the purchase's `PENDING` payment state. A deferred verification returns
`202` with `operationId`, `retryAfterSeconds` and the previously valid snapshot,
never a speculative new premium grant. `GET /operations` cannot enumerate jobs
belonging to other installations.

Stable errors contain `code`, `retryable`, `requestId` and an optional safe
`recoveryAction`, without Google payloads or account/token details:

```json
{
  "error": {
    "code": "VERIFICATION_UNAVAILABLE",
    "retryable": true,
    "requestId": "request_uuid",
    "recoveryAction": "RETRY"
  }
}
```

Use `400 INVALID_REQUEST`, `401 SESSION_EXPIRED`, `403 NOT_AUTHORIZED`,
`409 PURCHASE_OWNERSHIP_CONFLICT`, `422 PURCHASE_NOT_ELIGIBLE`,
`429 RATE_LIMITED` and `503 VERIFICATION_UNAVAILABLE` consistently. Return `202`
only after work is durable; clients honor Retry-After and exponential backoff with
jitter. Never retry a payment launch automatically after a timeout.

## 8. RTDN, reconciliation and failure recovery

RTDN is a change signal, not the purchase authority. Validate the Pub/Sub push
token's signature, issuer, audience and expected verified service-account email,
then validate the expected subscription, package and bounded envelope. Store the
message before acknowledging receipt. A worker fetches current Play state and
runs the same reducer as client verification. Sources:
[RTDN format](https://developer.android.com/google/play/billing/rtdn-reference),
[Pub/Sub push authentication](https://cloud.google.com/pubsub/docs/authenticate-push-subscriptions),
[Play backend integration](https://developer.android.com/google/play/billing/backend).

Expect duplicates, delays and out-of-order messages. Deduplicate message IDs, and
serialize/fence state updates per token lineage so an older concurrent fetch
cannot overwrite a newer commit. Do not impose ordering solely by event timestamp.
Unknown valid event types are persisted and trigger a safe current-state fetch.
Test notifications exercise routing but cannot create entitlements.

Run scheduled reconciliation for active, pending, recently expired and
acknowledgement-outstanding purchases. Prioritize purchases near expiry or with
failed webhooks, and use quota-aware batches. Track a durable overlapping cursor
for voided purchases and reconcile missed events after outages. Read
[Voided Purchases API](https://developers.google.com/android-publisher/voided-purchases).
Keep a dead-letter queue, replay command and audit trail. Do not resurrect
revoked access from a stale local purchase callback.

Operational targets are proposed: verify interactive purchases in seconds under
normal conditions, alert within 15 minutes of a stuck acknowledgement backlog,
and investigate RTDN/reconciliation failures before their oldest item is an hour
old. Monitor latency, retries, grant conflicts, rejected token checks and purchase
state drift using aggregate metrics. Alerting and an on-call owner are launch
requirements, not future polish.

## 9. Offline access and capability enforcement

The backend signs a compact lease with pinned algorithm and key ID, issuer,
audience (`dev.geode` plus environment), installation key thumbprint, capabilities,
entitlement revision, issued time, offline expiry and authoritative access expiry.
The app carries public verification keys only. It never signs its own premium
lease. Support overlapping key rotation and revoke future issuance by key ID.

Proposed policy: subscription offline access lasts up to **72 hours**, capped at
verified access expiry; lifetime offline access lasts up to **30 days** before a
refresh. These are product/security tradeoffs requiring clear customer disclosure,
not guarantees imposed by Play. A lifetime purchase has no recurring charge or
scheduled product expiry; its cached proof still needs occasional verification.
If indefinitely offline lifetime access is required, accept that prompt remote
revocation cannot be guaranteed and amend this policy before sale.

Use the last verified server time plus monotonic elapsed time during a boot.
After reboot reconcile wall time against the recorded server baseline; significant
rollback or an untrustworthy time history requires online refresh. Changing the
clock, importing settings or restoring Auto Backup cannot extend a lease. A fully
compromised client can patch checks; device binding and integrity reduce abuse
but do not create a tamper-proof local renderer.

Enforce gates when opening a premium feature and when validating a new export
job, not only on button appearance. Retain project contents and allow inspection,
editing into the free limits and data export when access lapses. A job accepted
under a valid lease may finish its immutable snapshot; loss of network or later
expiry must not corrupt the render. New premium work requires a valid grant.
Known revocation stops issuing leases immediately; already offline installations
can retain access until their issued lease expires. State this latency honestly.

## 10. Secrets, storage, transport and privacy

| Asset | Storage and access rule |
|---|---|
| Google ID token | Exchange in memory over HTTPS; redact and discard |
| Identity/installation refresh token | Keystore-protected private storage; server hashes; excluded from backups |
| Device signing key | Android Keystore; hardware-backed when available; no export or shared preference copy |
| Play purchase token | Transient Android discovery; backend authenticated encryption; keyed lookup fingerprint |
| Google Drive access token | AuthorizationClient flow; short-lived local use only; never billing API/logs |
| Lease signing / database encryption keys | Managed KMS/secret service, separate roles, audited rotation |
| Play Developer API credentials | Backend workload identity where supported; least privilege for this package |
| Upload/app-signing material | Separate protected release process; never app resources or repository |

Android Keystore protects key material but is not a guarantee that an attacker
controlling the running app cannot invoke key operations. Design recovery around
key invalidation and device replacement. See
[Android Keystore](https://developer.android.com/privacy-and-security/keystore).

Add `INTERNET` only with the implemented connected features. Require TLS,
platform hostname/certificate validation and a release Network Security Config
that rejects cleartext. Do not ship trust-all managers, debug CA acceptance or
HTTP fallback. Pinning is not an automatic improvement: use it only with an
operational rotation/recovery plan and tested backup pins. See
[Network Security Config](https://developer.android.com/privacy-and-security/security-config).

Update both `backup_rules.xml` and `data_extraction_rules.xml` to exclude sessions,
purchase tokens, installation IDs/keys and cached leases from cloud backup and
device transfer. Restored account state is signed-out and purchase access is
reverified. Preserve user-created presets/projects using their separate data
rules. Test both platform backup regimes, not only a manual JSON export.
See [Android backup controls](https://developer.android.com/identity/data/autobackup).

Redact Authorization/DPoP headers, tokens, Google claims, local media URIs, full
paths, filenames and raw purchase responses from RingLog, crash reports, HTTP
tracing, job arguments and support exports. Use random request IDs and short
rotating correlation identifiers; server audit access is restricted. Proposed
normal operational-log retention is 30 days, with IP-derived abuse data minimized
and retained only as long as the abuse investigation needs. Legal/accounting
retention is a separate approved schedule, never an unspecified permanent archive.

Use secret scanning, dependency verification/locking, an SBOM, protected production
deployments, least-privilege CI identities, encrypted database backups and tested
restore procedures. Human support has no raw-token export and cannot silently
grant premium; exceptional adjustments require authenticated operator actions,
a reason, expiry and an audit record. No production credentials in CI logs or
test fixtures. Review token revocation guidance in
[Google OAuth best practices](https://developers.google.com/identity/protocols/oauth2/resources/best-practices).

## 11. Drive authorization and account deletion

Drive permission is requested only after Backup/Restore is chosen. Use
AuthorizationClient separately from Credential Manager and request only
`https://www.googleapis.com/auth/drive.appdata` for hidden app data. It is a
non-sensitive Drive scope; it does not authorize browsing the user's whole Drive.
References: [AuthorizationClient](https://developer.android.com/identity/authorization),
[Drive app data](https://developers.google.com/workspace/drive/api/guides/appdata).

Bind every backup job to the selected Drive account and a unique work key. On
account change, cancel old work and clear its authorization state; never send a
pending snapshot to the newly selected account. WorkManager reacquires valid
authorization; if UI consent is required, return `NEEDS_AUTHORIZATION` and ask
from the foreground. No ID token substitution or embedded OAuth client secret.

Backup includes user-selected presets, project metadata and settings with schema,
checksum and size limits. It excludes audio files/recordings, auth tokens, paid
flags, purchase records and private keys. A checksum detects corruption, not a
malicious rewrite; validate content and migration before import. Preview conflicts
and write atomically. Ordinary appData backup is not advertised as end-to-end
encrypted. Such a claim would require a separate portable encryption/recovery-key
design; a device-only Keystore key cannot decrypt a backup on a replacement phone.

Provide distinct actions for Sign out, Disconnect Drive, Delete cloud backups,
Delete account and Clear local data. Account deletion requires a recent,
server-verified identity challenge, a concrete data summary and confirmation.
The deletion worker revokes sessions immediately, stops profile jobs and removes
profile data, links and unnecessary logs. Remove Drive appData before revoking
its authorization when the user requests cloud-backup deletion. If authorization
has already been revoked, disclose that the backend cannot erase Drive files it
cannot access and provide a reconnect or Google storage-management path.

Delete-account completion reports completed and blocked scopes separately.
Local music and existing exported videos are not silently deleted. Account
deletion does not itself cancel a Play subscription: expose Play's management
action and explain any remaining billing before confirmation. Do not require a
retained Geode profile merely to restore a valid independent Play purchase.
Keep only the minimal pseudonymous commerce/audit records justified for purchase
restore, fraud, refunds and legal obligations; document fields, purpose and exact
retention schedule before launch. These records can still be personal data.

Provide an in-app deletion path **and an externally reachable deletion-request
page**, including users who no longer have the app. The website authenticates
ownership; it does not delete by an unauthenticated email parameter. Privacy and
Data safety answers must cover actual SDK/backend behavior. See
[Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111).

## 12. Threat model and abuse cases

| Threat | Required control | Remaining limit / recovery |
|---|---|---|
| Patched APK sets premium flag | Server purchase truth, signed lease, capability checks | Local code can be patched; do not promise impossible DRM |
| Forged product/price/expiry | Fetch Play state; server catalog allowlist | API outage delays new verification |
| Token replay across users | Unique token owner, immutable lineage, scoped installation grants | Anonymous token theft needs rate limits/integrity; support route |
| Duplicate callback/RTDN/worker retry | Unique inbox/outbox keys, transaction, idempotency response | Reconcile after crash at every commit boundary |
| Old event overwrites a refund | Fetch current state, serialize/fence writes, grant revisions | Offline leases bound revocation delay |
| ID token for another app / replay | Audience/issuer/signature/expiry + one-use bound nonce | Compromised logged-in client remains a risk |
| Stolen app refresh token | Device proof, rotation/reuse detection, family revocation | Rooted runtime may use keys in place |
| Forged RTDN POST | Pub/Sub OIDC verification and expected service account | Valid duplicates remain normal input |
| Insecure direct object reference | Principal-scoped lookup for operation/account/purchase IDs | Security tests attempt cross-install/profile access |
| Clock rollback / backup cloning | Signed bounded lease, monotonic baseline, key binding, backup exclusions | Reverification on uncertain time/key loss |
| SQL/JSON/parser abuse | Parameterized queries, strict schemas, size/depth limits | Fuzz request and stored-provider payload boundaries |
| Billing verification quota exhaustion | Per-IP/principal quotas, token dedup, queue budget, retry jitter | New verification may be delayed, free playback stays usable |
| Secret leak through logs/builds | Structured redaction, protected secrets, scans and rotation drills | Incident playbook revokes affected sessions/credentials |
| Account deletion races with jobs | Deletion generation check, revoke sessions, cancel work, scoped erasure | Externally revoked Drive access needs user-assisted cleanup |
| Malicious backup/preset/media | Bounds/schema/path validation, safe extraction, native sanitizers | C++ decoders/shader drivers remain a separate release attack surface |
| Unauthorized capture/data export | Existing Android consent, least permissions, no backend media ingestion | New SDKs must be reviewed for additional collection |

Do not use root detection, Play Integrity or a device verdict as the sole proof
of payment. Choose graduated responses for unsupported devices and service
outages. A fail-closed decision for a **new** premium grant does not justify
blocking a user's local library, deleting work or repeated purchase prompts.
See [Integrity verdict meaning](https://developer.android.com/google/play/integrity/verdicts).

## 13. Implementation sequence and verification gate

1. Approve the catalog, feature gates, offline periods, anonymous-restore tradeoff,
   retention schedule and operating budget. Record an architecture decision.
2. Provision isolated environments, database migrations, secrets, keys, API schema,
   workload permissions, backup/restore and deployment rollback.
3. Implement installation sessions, Google challenge/exchange, profile sign-out,
   fresh reauthentication and deletion. Add adversarial auth contract tests first.
4. Implement the pure purchase reducer, token uniqueness and replacement-lineage
   transactions. Verify fixtures from the actual Play APIs.
5. Implement acknowledgement outbox, RTDN inbox, authenticated ingress,
   reconciliation, dead-letter replay and redacted observability.
6. Add BillingRepository, product discovery, purchase/restore journeys,
   EntitlementRepository and offline leases; keep a debug-only fake for UI tests.
7. Connect capability gates to every relevant screen, renderer selection and export
   job validator. Verify no data-destructive downgrade behavior.
8. Add optional Drive authorization, account-bound backup jobs and validated
   restore; exercise disconnect/deletion when authorization expires mid-job.
9. Update privacy copy, permissions, backup exclusions, notices, deletion website,
   product disclosures and Play Console forms together.
10. Pass automated, Play sandbox and physical-device scenarios below; independently
    review API authorization and release APK configuration before charging users.

| Test family | Required scenarios and evidence |
|---|---|
| Identity | Wrong signature/audience/issuer; expired token; missing/wrong/reused nonce; key rotation; email change without new identity; no credential/cancel; logout and revoked refresh reuse |
| Authorization | Every account/operation endpoint called with another user's or installation's ID; billing token rejected on profile routes; deletion token limited to status |
| Purchases | Monthly/yearly/lifetime; pending success/cancel; app killed after payment; duplicate callbacks; Play disconnected; already-owned recovery; acknowledgement transient failure/deadline alert |
| Subscription transitions | Renewal, grace, hold, recovery, scheduled cancellation, expiry, pause/resume, effective replacement, cancelled pending replacement, lifetime plus expired subscription |
| Refund/revoke | Refund without revoke, revoke/void/chargeback, out-of-order RTDN, duplicate messages, late old-token callback, missing RTDN followed by reconciliation |
| Restore | Reinstall/new key, second device, guest without profile, different profile/Play/Drive accounts, unknown token, copied token abuse, empty transient discovery, no ownership reassignment |
| Transaction safety | Crash before/after ledger commit, before/after ack, lost HTTP response, same idempotency key with changed body, concurrent verify/RTDN/restore, dead-letter replay |
| Offline | No first-use connection; valid lease; expired/modified/wrong-key lease; clock rollback/reboot; key invalidation; Auto Backup cloning; interrupted export near expiry |
| Drive/deletion | Denial/revocation, account switch while queued, corrupted/oversized/newer backup, secrets absent, successful/blocked deletion stages, website-only request |
| Release/security | Fake verifier unreachable; no debug grant; TLS/cleartext settings; secret/redaction scans; dependency review; backup exclusions; signing/certificate environment mismatch |

Run reducer/API/security tests in GitHub Actions and integration tests against the
isolated staging backend. Use Play license testers and Billing Lab for real
lifecycle behavior, including accelerated test time; mocks alone cannot certify
purchase correctness. Use physical devices for Credential Manager, Play account
switches, key loss and backup/restore. Reference:
[Play Billing testing](https://developer.android.com/google/play/billing/test).
This documentation task did not run local compilation, tests or lint.

### Configuration and release blockers

- No deployed backend, production URL, database, queue, signing key or operator
  exists in this checkout. None has been provisioned by this design task.
- Owner must configure Play products/base plans/offers, countries, pricing,
  license testers, API service-account access, RTDN topic and permissions.
- Google Cloud needs Android OAuth clients for correct package/signing
  fingerprints, a Web client ID, verified branding/consent configuration and Drive
  API. The production Play app-signing certificate differs from an upload key.
- Integrity project linkage, accepted package/certificate/version policy and
  staging exceptions need explicit configuration and device testing.
- Credentials, backend session/lease keys and release upload keys belong in
  protected secret systems; nothing in this document supplies them.
- Privacy/support/deletion URLs, retention decisions, product prices/limits and
  an incident/support owner are unresolved launch inputs.
- Existing offline-only copy and backup rules require reviewed implementation
  changes. A design document does not authorize claiming these protections ship.

Production premium is complete only when a real staged purchase survives process
death, restores without mandatory profile sign-in, revokes correctly, protects
account data, recovers from a backend outage and passes the release checks with
recorded evidence.
