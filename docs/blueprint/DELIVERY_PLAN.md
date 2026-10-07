# Android build and delivery plan

**Planning date: 7 October 2026. Status: ordered implementation plan, not a
completion report.** The user-facing scope is [FEATURE_SPEC.md](FEATURE_SPEC.md);
the technical contract is [ARCHITECTURE.md](ARCHITECTURE.md). Read actual branch
changes and evidence in [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md).

This plan expands `docs/rebuild/PLAN.md`. The owner's current C++/Media3/**Oboe**
instruction supersedes the earlier instruction to remove Oboe. Preserve Oboe for
native low-latency input; migrate ordinary music output to Media3. Keep the Fluid
family, eight raymarched styles and projectM compatibility. Style removal follows
replacement and saved-data migration, not an arbitrary count target.

The premium release architecture proposed here includes a small purchase
verification backend and optional identity. That changes the historical
no-backend decision. Record the hosting, operational ownership, data retention
and cost decision before production integration; a client-only premium flag is
not a substitute for this package. Google/Drive remains optional for local use.

## 1. Establish what exists and what is proved

| Evidence class | Meaning | Release implication |
|---|---|---|
| Existing code | A class, screen or native path is present | No correctness or product-completeness inference |
| Implemented patch | A scoped diff addresses a traced defect/contract | Needs review and applicable gates |
| CI verified | Named jobs pass on the exact commit SHA | Does not establish physical GPU/audio behavior |
| Device verified | Recorded journey on named hardware/OS and build | Applies to that scenario/device; expand the matrix deliberately |
| Release ready | All mandatory product, security, migration, device and Console gates pass | Candidate may enter the authorized release process |

At the inspected baseline, Media3 playback, native visual families, presets,
wallpaper and export paths exist. Audit findings still require fixes or verified
closure. Full Studio composition, six signature 3D scenes, Oboe microphone,
production purchases, optional identity/backup and Play readiness cannot be
marked complete from that code's presence. Branch patches are tracked separately;
this document does not claim that newly written tests have been executed.

## 2. Delivery discipline

1. Maintain **at most two active implementation workers**, one package each.
   Before starting, publish its allowed files, interfaces, acceptance criteria,
   dependencies and expected migration. No shared file ownership and no nested
   expansion into an uncontrolled worker fleet.
2. Use one branch/PR per independently reviewable package. Larger packages below
   become several ordered PRs under the same acceptance gate. Merge shared
   contracts first; regenerate the second worker's base before dependent work.
3. Keep compiling, tests and lint in **GitHub Actions only**, as the repository
   plan requires. Do not claim local validation or run local builds as a shortcut.
   Read diffs and trace call sites locally; use CI artifacts for device testing.
4. Review the entire diff, follow the user journey through ownership and failure
   paths, then send defects back to its worker. Graphify may supplement review
   when available; record its absence and perform code tracing without claiming
   a graph review occurred.
5. Require green applicable CI on the exact reviewed commit before merge.
   Native-only, build-logic, manifest and dependency changes must trigger their
   relevant gates. Device-required packages retain their gate after CI passes.
6. Keep unfinished integrations behind explicit internal flags. A placeholder
   button, silent fallback or fabricated entitlement is not completion. Preserve
   working user journeys until replacements pass their contracts.

## 3. Ordered packages and dependencies

| Package | Historical mapping | Depends on | Primary outcome |
|---|---|---|---|
| D00 Baseline and CI | A1, R4/R5 | None | Reproducible build/evidence pipeline, verified repository state |
| D01 Contracts, migration and security model | R1–R5 | D00 | Stable data/source/scene/project contracts and threat inventory |
| D02 Media3 output migration | A2, part A3 | D01 | One supported music output path; Oboe retained |
| D03 Engine correctness | B2 | D01 | Safe native lifetime, real beat response, clocks and pause behavior |
| D04 Service-owned player | A3 | D02 | Player rules survive UI loss and cold external launch |
| D05 projectM repair | B3 | D03 | Stable PCM, presets, resize and deterministic time |
| D06 Oboe input and source routing | A2b | D02–D04 | Low-latency mic and explicit single-source ownership |
| D07 Data and integration interfaces | A4/A5 foundation | D01, D04 | Durable repositories, migrations and integration boundaries |
| D08 3D route prototype | B4 | D03, D05 | Measured renderer go/no-go with live/export scene |
| D09 3D foundation | B5 | D08 | Camera, scene controls, post-processing and tier contracts |
| D10 Premium verification service | A4 expanded | D07, operational decision | Server-authoritative purchase state and test integration |
| D11 Signature scenes | B6 | D09 | Six original art-directed scenes, one or two per reviewed PR |
| D12 Optional identity and backup | A5 | D07, D10 interfaces | Google/Drive with safe restore and deletion |
| D13 Exporter core | C1 | D03, D05, D07, D09 | One deterministic export pipeline with accurate timing |
| D14 Full Studio | C2 | D13 | Preview and export agree across every advertised lane/effect |
| D15 New UI, controls and catalog migration | B1, B7 | D04–D09, initial D11, D14 | Design system and complete Listen/Explore/Customize/Studio journeys |
| D16 Premium gates and account polish | A4/A5, F1 | D10, D12, D14, D15 | Fair purchase/restore/gating in real user journeys |
| D17 Android polish, accessibility and performance | A6, F3 | D06, D11, D14–D16 | Adaptive, accessible and measured release candidate |
| D18 Security and release candidate audit | F2/F3 | D17 | No open required defect; accurate binary/privacy/licence evidence |
| D19 Play preparation, testing and rollout | F2 | D18 and owner credentials | Signed, verified AAB; closed testing; controlled publication |

### Scheduling with two slots

| Wave | Slot A | Slot B | Gate before next dependent wave |
|---|---|---|---|
| 0 | D00 | Read-only reference/device evidence | Establish base SHA; no competing infrastructure writes |
| 1 | D01 shared contracts | Read-only licence/buffer inventory | Merge contracts before implementation |
| 2 | D02 → D04 | D03 → D05 | Supported playback and stable renderer boundary |
| 3 | D06 → D07 | D08 → D09 | Source/data interfaces plus renderer go/no-go |
| 4 | D10 → D12 | D11 scene PRs | Billing/identity and scene files remain disjoint |
| 5 | D13 → D14 | Remaining D11 scene PRs | Shared scene/export contracts frozen first |
| 6 | D15, then D16 | Evidence review/docs or isolated tests | Shared UI/integration files have one owner |
| 7 | D17 → D18 | Physical-device verification and issue reproduction | Fix/retest failures on exact candidate SHA |
| 8 | D19 | Store evidence review | Required Console/device/CI gates complete |

Slots are a capacity limit, not a reason to manufacture parallel work. A task
touching the same files waits. Owner decisions and external services can be
prepared while code work continues; their absence remains an explicit gate.

## 4. Package contracts

### D00 — Baseline, dependency and CI reliability

- Fetch the current repository/PR state and compare with the old plan; do not
  assume PR #4 or any historical SHA is still the active baseline. Preserve
  unmerged work and document which removals already landed.
- Inventory app modules, native dependencies/submodules, manifests, build variants,
  repositories, secrets references and release jobs. Pin reviewed dependencies
  and record their exact licences; do not update every tool simply to be newest.
- Resolve the supported JDK/Gradle/AGP/Kotlin/Detekt combination through CI. Use
  a distinct compatible runtime for tools where necessary; a JVM bytecode target
  alone does not repair an incompatible tool runtime.
- Make CI compile Kotlin and native code for both packaged ABIs; run applicable
  unit/instrumentation/static checks, package APK/AAB artifacts, and retain logs,
  test reports, R8 mappings and native symbols. Fix the release-job permissions
  and tag/artifact conditions using least privilege.
- Add CI host regression targets for portable core code. Run sanitizer jobs on
  supported host tests and keep physical-GPU claims out of those reports.

**Acceptance:** exact build SHA, dependency inventory and named passing jobs;
deliberately test that native-only changes trigger native gates. Never use a
successful docs-only job as the application's verification result.

### D01 — Shared contracts, data preservation and threat model

- Record every PCM/feature/export buffer's units, producer, consumer, capacity,
  synchronization, overflow/underflow and stale-data behavior. Select a small
  documented baseline device/media matrix and provisional quality budgets.
- Define source leases/epochs, scene IDs/capabilities, universal controls,
  project time/clip semantics and repository interfaces before replacing code.
- Create upgrade fixtures from existing preferences, presets, playlists and
  Studio projects. Specify obsolete-control/style mapping, unknown fields,
  invalid/newer schemas, rollback and interrupted migration behavior.
- Model trust boundaries: incoming links, imported files/archives/shaders,
  native array lengths, URI permissions, billing tokens, identity, cloud backup,
  service entry points and logs. Assign each risk to an implementation gate.
- Record the backend scope decision and account/data deletion model. Specify
  what stays local, what leaves the device, retention and deletion behavior.

**Acceptance:** reviewed contracts plus migration/threat fixtures; feature IDs
from the specification map to packages and tests. No bulk style deletion yet.

### D02–D04 — Supported playback and engine correctness

**D02:** migrate saved NativePlayer/bit-perfect/crossfade choices to supported
Media3 behavior; remove unsupported promises and controls. Retain Oboe native
dependency for D06. Keep queue IDs, resume positions, EQ and user media intact.
Tap app-processed PCM at the chosen DSP position and document device/hardware
effects outside that tap. Never run duplicate audible output engines.

**D03:** fix native analysis/render teardown and stop/start overlap; make every
native handle's owner explicit. Separate angular rate from angle; restore beat,
transient and structure behavior actually read by scenes. Deliver each PCM block
once and decay/settle on missing audio. Repair offline hop accounting at high
sample rates and buffer backpressure. Align live features to presentation time
with route/seek/rate epochs. Preserve renderer CPU/preset state through surface
replacement and restore GPU resources after context loss.

**D04:** move preference application, noisy/focus behavior, queue persistence,
error skip, play counts, resume/bookmarks, A–B repeat, sleep and supported fades
under service lifetime. Replace avoidable UI polling with player events or
bounded service scheduling. UI/notification/widget/Auto use the same controller;
a second Activity must not release the shared player.

**Acceptance:** host lifecycle/math regressions; service instrumentation for
cold external launch, UI destruction and two activities; physical screen-off,
focus interruption, unplug and 20 route changes. Verify no stale beat/PCM after
pause/seek and record measured audio/visual offset. A rotation patch alone does
not close the full D03 contract.

### D05 — projectM compatibility

- Feed the full new PCM stream once, using frame/channel units and explicit
  sample-rate conversion where required; no fixed visual-frame sample budget.
- Preserve preset identity, seed and texture pack across screen/context/thermal
  changes. Resize targets without resetting the engine unnecessarily.
- Move texture linking and preset indexing out of repeated list retrieval;
  use lazy catalog loading and actionable missing/invalid asset errors.
- Make starter-pack installation atomic and retryable; mark success only after
  verification. Track licence/provenance for every bundled pack and texture.
- Replace wall-clock dependence in export with controlled timestamps, documented
  warm-up and seed behavior. Isolate projectM GL state from the main renderer.

**Acceptance:** device recordings of restore, resize and transitions without
black-frame/preset resets; deterministic export checkpoints; malformed pack and
interrupted-install tests; compliance materials for the actual linked version.

### D06 — Oboe microphone and source arbitration

- Implement a C++ Oboe input owner, control thread and preallocated callback queue.
  Negotiate actual rate/format/channels; try supported low-latency options and
  implement shared/backend fallback and disconnect/reopen recovery.
- Keep allocation, locks, FFT, JNI and storage/network work outside the callback.
  Measure callback timing and queue occupancy; size buffers from actual device
  burst/rate and worker jitter, then tune with evidence.
- Grant one producer lease to local music, microphone, eligible playback capture
  or silent drive. Reject callbacks from obsolete epochs after switching.
- Keep MediaProjection consent and AudioRecord for eligible other-app capture;
  Oboe does not replace platform consent. Handle permission denial/revocation,
  no input, route changes and screen/background service rules explicitly.

**Acceptance:** callback audit, source-switch tests and real-device input-latency
measurements with method and percentiles. The old 10–20 ms target is aspirational,
not a universal hardware claim. No background microphone activation by surprise.

### D07 — Data foundation and integration seams

- Introduce stable IDs, schema versions, atomic writes and command/undo semantics.
  Migrate to Room/DataStore only where justified, with fixtures for every supported
  previous format; retain recoverable originals until successful validation.
- Harden URI/deep-link/archive import: limits on compressed/expanded size and
  item count, path traversal/symlink rejection, schema/value validation, no
  overwrite on name collision, and per-item result reporting.
- Define EntitlementRepository, IdentityRepository and BackupRepository with
  debug/test implementations. Production code cannot select the billing fake.
- Store file references and relink state correctly; a backup does not recreate
  an Android URI grant on another device.

**Acceptance:** malformed/truncated/newer-schema inputs, interrupted writes and
upgrade fixtures pass CI; restored data matches expected identities and values.

### D08–D09 — 3D proof and shared foundation

**D08:** implement one tunnel scene in the current C++ GLES engine and an isolated
Diligent prototype. Compare identical content, quality, frame time, native size,
CI duration, projectM coexistence, context replacement and offscreen export on
arm64/x86_64. Document a go/no-go. Retaining GLES is the fallback and current
baseline; Vulkan/interop does not enter the critical path without evidence.

**D09:** implement seeded camera splines/springs, bounded velocity/acceleration,
timestamped audio uniform/history inputs, common scene lifecycle and per-scene
macro adapters. Establish the post stack and final visual-safety composite.
Budget render targets, transparent overdraw, simulation/geometry density and
optional effects per tier. Make tier changes preserve scene/preset state.

**Acceptance:** one complete scene in live view and offline export, no context
leaks over repeated surface changes, measured tier budgets, reproducible time and
seed behavior. Repeated palette variants do not satisfy the scene architecture.

### D10 — Premium service and verified entitlements

Production verification is a separate deployable package with staging fixtures,
owned infrastructure and monitoring. Do not commit service-account credentials,
signing keys or OAuth client secrets to Android resources or source control.

- Define server endpoints for purchase reconciliation and entitlement retrieval,
  an idempotent purchase ledger, authenticated request context, versioned response
  schema and explicit errors. Optional Google login must not become mandatory
  for buying/restoring; specify a secure guest/device-session recovery model.
- Verify purchase tokens with Play before access is granted; distinguish pending
  and purchased state. Deduplicate tokens, acknowledge valid purchases, reconcile
  linked replacements and process revocation/expiry updates. Use server-side
  purchase state rather than client-provided price, product or entitlement flags.
- Establish a least-privilege notification receiver, idempotent processing and
  reconciliation after missed notifications. Check notification authenticity
  before accepting its content and re-query authoritative purchase state.
- Design bounded cached/offline entitlement behavior, versioned signed responses,
  server time and retry/backoff. Define how subscription expiry, lifetime
  revocation, reinstall, Play-account switch and backend outage appear in UI.
- Limit request size/rate, redact tokens from logs, restrict secrets access and
  audit entitlement mutations. Define backup/restore and incident recovery for
  the ledger. Integrity signals are supplementary risk evidence, not ownership.
- Wire Play Billing client, live product details, subscription/lifetime offers,
  cancellation, reconnection and restore to EntitlementRepository. Keep mock
  purchases confined to tests/debug and prove they cannot unlock a release.

**Acceptance:** staging contract/security tests; Play licence-tester matrix for
pending, success, retry, acknowledgment, renewal, expiry, replacement, refund,
restore, account change and outage. A service stub or local Boolean fails this
gate. See [Play billing security](https://developer.android.com/google/play/billing/security)
for purchase verification and acknowledgment guidance; operational choices above
are the proposed Geode architecture, not a claim that a backend already exists.

### D11 — Six signature scenes

Implement the six briefs in [DESIGN.md](DESIGN.md), one or two per PR, retaining
the existing production catalog until replacements and migrations pass. Every
scene includes original assets/licence records, camera choreography, audio
mapping, universal-control extremes, quality tiers and deterministic export.

**Acceptance:** art review plus the common scene/device suite; silence, varied
music, source changes, interaction, context replacement and sustained thermal
behavior. Record each accepted scene's distinct silhouette and material behavior.

### D12 — Optional Google identity and Drive

- Implement Credential Manager sign-in, explicit cancel/no-credential/offline
  states and sign-out. Where backend sessions are used, verify the Google ID
  token's signature/audience/issuer/expiry and bind a replay-resistant sign-in
  request before issuing the app session; do not trust an email sent by a client.
- Keep Drive authorization separate, using the minimum appData scope. Build
  versioned/checksummed backup manifests and resumable/retryable WorkManager
  operations with clear cancellation and account ownership.
- Preview conflicts and missing media on restore. Restore into staging, validate,
  then commit atomically; preserve local data when cloud content is corrupt.
- Add in-app and applicable web deletion request/completion flows, session/token
  revocation, cloud-backup deletion and retention disclosures. Do not confuse
  account deletion with cancelling a Play subscription.
- Add In-App Review/Updates after successful user value; cancellations, quotas
  and update failures never block the core experience.

**Acceptance:** identity/authorization denial and revoke cases, wrong-account
backup, corrupted restore, interrupted jobs, relinking and deletion evidence.
See [Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg)
and the primary integration references in [OPEN_SOURCE.md](OPEN_SOURCE.md).

### D13 — Deterministic exporter

- Fix native/Kotlin hop-rate agreement and lossless offline audio draining before
  building on the analysis timeline. Separate offline backpressure from live
  overload behavior; include mono/high-rate and corrupt-input fixtures.
- Freeze an immutable project/preset snapshot with integer/rational timestamps,
  explicit seed and all dependency fingerprints. Evaluate frame time from index.
- Implement the native-visual/Media3 frame adapter or measured intermediate-file
  route; prove EGL/encoder ownership and texture lifetime. Shared API names do
  not establish that custom C++ visual layers are automatically composed.
- Validate codec/resolution, every requested effect, storage and entitlement
  before export. Handle service limits, cancellation, failure/process death,
  pending MediaStore output and final muxer close without losing the project.
- Implement captions/lyrics, loudness, human-readable file naming, progress/ETA,
  notification Cancel and durable completion/error state.

**Acceptance:** one-frame duration accuracy and no accumulating drift in ten-
minute fixtures at 44.1/48/96/192 kHz; repeated visual checkpoints; full disk,
permission loss, cancel and restart. Inspect the exported file, not only logs.

### D14 — Full Studio

- Define gaps, lane precedence, overlap, clip gain, trimming, transitions,
  keyframe origins, split/move/delete and speed mapping in the project evaluator.
- Build the composed preview from that evaluator, including native visuals and
  overlays. Export all advertised lanes and effects, including Ken Burns,
  captions and audio gain. Never discard an unsupported-content report.
- Build timeline interaction, precise numeric alternatives, snapping, undo/redo,
  missing-media relink, autosave, project duplication and import/version migration.
- Route all output through the export service/job model. UI recreation/list
  refresh must not erase progress, allow conflicting exports or lose Cancel.

**Acceptance:** fixture projects exercise gaps, overlapping lanes, trim/split,
nonzero clip starts, nested timing changes and keyframes. Compare preview and
export at specified timestamps and verify resulting audio/video streams.

### D15–D17 — Product experience and finish

**D15:** implement the design tokens and all loading/empty/denied/offline/error
states, Listen/Explore/Studio navigation, immersive transport, five universal
macros, advanced mappings, presets, undo and wallpaper. Migrate old style IDs
and removed settings only after replacement coverage is proven. Keep original
saved presets/projects recoverable; remove old screens/mirrors only after routing
and saved-state tests pass.

**D16:** connect actual premium capabilities and export limits to one entitlement
model in UI and execution paths. Finalize free limits/price configuration, fair
paywall, restore/manage, pending/error/offline states and account settings. Do
not gate safety, local playback, help or access to the user's existing data.

**D17:** complete Hilt ownership where appropriate, ViewModels/StateFlow,
edge-to-edge/predictive Back, accessibility, localization and adaptive layouts.
Capture a baseline profile for real startup/listen/explore/editor journeys and
measure release builds. Remove unreferenced bundled art/dead code only with a
reference/provenance check. Rewrite README/CHANGELOG to describe verified behavior.

**Acceptance:** uninterrupted design journeys on phone/tablet/foldable, TalkBack,
keyboard/switch access, 200% font scale and RTL; startup/frame/memory/thermal
budgets from FEATURE_SPEC; no dead controls or silently omitted output.

## 5. Migration and security gates across packages

| Boundary | Gate before enabling in a release |
|---|---|
| App update | Preserve package/signing identity, queue/playlists/presets/projects and media URI grants; unsupported styles/settings have explicit mapping or recovery |
| Data schema | Fixture upgrades, interrupted transaction recovery, unknown/newer-version preservation; successful validation before deleting backups |
| Imported content | Size/count/path/type limits, bounded shader/texture resources, native bounds checks, collision-safe writes and actionable rejection |
| IPC and links | Minimize exported components; validate commands/URIs; grant only needed URI access; prevent external intents from invoking privileged internal operations |
| Tokens and sessions | No secrets in APK/source/logs, token audience/expiry checks, session rotation/revocation, authenticated deletion and account separation |
| Purchases | Backend verification/reconciliation, replay/idempotency cases, fail-safe pending/error handling, debug fake excluded from production |
| Cloud backup | Least-privilege scope, user/account isolation, integrity/schema verification and staged restore; disclosed retention/deletion |
| Build supply chain | Pinned/reviewed native and Gradle dependencies, notices/SBOM, restricted CI permissions, secret scanning and retained source/patch materials |
| Release diagnostics | Crash symbols and useful local diagnostics without purchase tokens, credentials, full paths or recorded audio |

Security fixes receive regression evidence for the trust boundary they protect.
A checklist without exercised negative cases does not close the release audit.

## 6. Verification matrix

All automated build/test/lint execution below belongs in GitHub Actions. Physical
checks use its exact candidate artifacts and retain build SHA, device/OS, route,
fixture, parameters, thermal state, result and attached evidence.

| Layer | Required coverage |
|---|---|
| Portable native | DSP/hop/buffer units, rate/angle math, event delivery, epochs, deterministic camera/evaluator, parser bounds; supported sanitizer coverage |
| Kotlin/domain | Service rules with virtual time; analysis lifecycle; repository/undo/migrations; source arbitration; billing/account/backup/export state machines |
| Android instrumentation | Permission/picker denial, service cold launch/UI death, notification/widget/Auto commands, capture revocation, URI grants, foreground export and lifecycle restoration |
| Graphics | Low/mid/high GPU classes; GLES capability fallback; 100 scene switches; context replacement; each scene and projectM; device frame pacing |
| Audio | Mono/stereo and 44.1/48/96/192 kHz, VBR/corrupt/truncated media, speaker/wired/USB/Bluetooth, focus/calls, speed changes, silence and disconnect |
| Export | Every lane/effect/aspect, rational fps, gaps/trim/speed/keyframes, captions, loudness, cancel/full disk/process death, ten-minute sync fixture |
| Integration/security | Play tester lifecycle, staging outage/replay/authorization failures, Google/Drive revocation, account isolation/deletion, malicious imports/links |
| Release binary | Release R8 behavior, arm64/x86_64, every packaged ELF plus APK alignment for 16 KB, install/upgrade and supported runtime checks |

Select physical devices covering the minimum supported Android generation,
current target behavior, Adreno and Mali-class drivers, constrained memory and a
16 KB environment. Use emulator coverage for additional API/ABI combinations,
including x86_64; emulation does not establish microphone latency, Bluetooth
timing, GPU thermals or representative battery life. Keep representative phone,
tablet/foldable and display-refresh coverage in the release matrix.

## 7. D18–D19: release gate and publication

1. Freeze required scope and candidate SHA. Audit all R0/R1 feature IDs against
   completed journeys; R2 extensions are explicitly excluded from store claims.
   No unresolved reproducible crash, ANR, data loss or broken required journey.
2. Review manifests, foreground service declarations/launch behavior, permissions,
   data collection, backup rules and deletion against the shipped implementation.
   Recheck current Play requirements immediately before submission.
3. Produce a signed release AAB using the stable upload key in CI secrets and
   Play App Signing configuration. Retain mappings/native symbols and establish
   version-code/release-key continuity. Never generate a new sideload key per run.
4. Verify minified release, supported ABI/device installation, upgrade migration,
   native/ZIP 16 KB compatibility and runtime evidence. Review packaged assets,
   dependency notices, projectM compliance materials and privacy/data-safety copy.
5. Prepare honest screenshots/video from the real release build, listing text,
   content rating, support/privacy/deletion URLs, accessibility/capture notes and
   purchase descriptions. Confirm Cloud OAuth credentials and Play products.
6. Upload to internal testing and inspect automated/pre-launch reports. Complete
   the account-specific closed-testing/production-access requirements. Keep a
   tested path for genuine tester feedback and defect reproduction.
7. Close all required findings on a fresh candidate and rerun affected gates.
   The owner supplies credentials, Console declarations/answers and required
   production publication authorization; no code change substitutes for these.
8. Use a staged rollout with predefined pause criteria for crashes, ANRs,
   playback/capture failures, purchase errors and user-data reports. Reconcile
   backend purchase events and support outcomes during rollout.
9. Recover using a tested forward update with a higher version code when needed;
   do not assume an Android downgrade can reverse schema changes. Maintain
   backward-compatible server contracts for installed app versions.

See [PLAY_RELEASE.md](PLAY_RELEASE.md) for current policy sources and the concrete
Console checklist. "Ready for Play" is reported only after these gates have
evidence; a successful build or pushed branch is reported as exactly that.

## 8. Required handoff record for each package

Record: feature IDs; reviewed commit/PR; changed behavior; migration; exact CI
job links/results; physical-device evidence if required; dependency/licence
changes; remaining defects; and rollback/recovery behavior. Separate **written**,
**CI passed**, **device passed** and **released**. The next worker starts from the
reviewed contract and current repository state, not from a completion claim in
an old document.
