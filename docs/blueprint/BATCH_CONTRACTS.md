# Implementation batch contracts — 7 October 2026

## Delivery rule

Implement, integrate, review, and commit locally before remote delivery. No worker
pushes or polls GitHub. Compilation, tests and lint still run only in Actions after
the completed batch is pushed. Authored regression tests are unexecuted until then.
No automatic merge or Play publication is authorized by this batch.

This batch uses isolated worktrees based on `2d1f3f2`; root integrates into
`codex/local-complete-batch` in `geode-local-batch`. Another active team was
observed changing `geode-3`; that checkout is left intact. Existing uncommitted
patches were snapshotted here for review, not assumed complete.
Scope expansion requires coordination. Each handoff includes changed files,
acceptance criteria addressed, regression coverage, and unresolved limits. Root
owns integration, documentation, workflow test registration and final delivery.

Preserve every existing C++ style and MilkDrop. Visual simulation and camera belong
in C++/GPU shaders; live variation is fresh. Test seeds do not dictate live routes.
Kotlin owns Android integration. No fake prices, purchases or account verification.

## Assigned contracts

| Owner | Exclusive scope | Acceptance |
|---|---|---|
| audio_restart (A1) | AnalysisInput, AnalysisEngine, SampleRing and tests in geode-batch-audio | Full new PCM window after restart/rate/reset; immutable rate per hop; reject stale completed work; preserve stereo |
| native_admission (N1) | Params, native API and Renderer setters plus tests in geode-batch-admission | Transactional finite/type/domain validation; preserve wire ABI and legal sentinels |
| spatial_visuals (V1) | SpatialCameraDirector, additive Prismatic Passage scene, shaders/catalog in geode-batch-visuals | Genuine spatial corridor/sculpture and fresh bounded music camera; stationary Motion zero/reduced motion; preserve full catalog |
| milk_assets (I1) | Direct/folder MilkDrop admission and truthful result UI in geode-batch-assets | Bounded bytes/dimensions/footprint/count; staging; preserve existing assets on failure |
| studio_inputs (E1) | LUT/SRT parser and async admission UI, configured LUT failures in geode-batch-studio | Bounded off-main parse; retain valid selection on rejected input; no false Loaded; scratch regression |
| audio_restart follow-up (E2) | ExportAdmission/ExportRun/ExportController and OfflineAnalyzer in integration | Pre-dispatch cancellation releases ownership; foreground failure stops actual nonsuspending render loops |
| native_admission follow-up (T1) | TakeStore/Repository/Controller, retry UI and source hook in integration | Durable typed save and serialized unique names; exact pending retry; source URI/offset captured together |
| independent_review (R1) | Read-only call-site and lifecycle review | Small reproducible findings; rereview owner fixes; tests not claimed executed |
| root | Integration, snapshot review, shared build registration, commit/remote delivery | Preserve prior work; no GitHub during development; all included patches reviewed before one final push |

Prior-team snapshot packages under review: playlist import transaction, transition
frame ownership, foreground-export admission, scratch run ownership, Rod Tunnel
camera adapter and permission cleanup. Overlapping implementations are reconciled
as a single coherent contract, never stacked blindly. Owners send changed paths,
regression sources, source evidence and limitations. No worker commits or pushes.

## Review and delivery

1. Owners finish bounded implementations and regression sources.
2. Reviewer traces actual callers and returns concrete blockers.
3. Owners resolve findings; root checks integration and registers new tests.
4. Record remaining gaps honestly; commit the complete batch.
5. Read remote delivery state once after commit, push with conflict protection,
   and let the existing Actions workflow build the APK and execute checks.

## Remaining release packages

- Presentation-clock synchronization, audible processing tap, Oboe microphone adapter.
- More spatial scenes/objects, GL-independent session lifetime and complete customization coverage.
- Studio timeline gaps, Visual/Overlay lanes, preview parity, service-owned Studio lifetime.
- Actual live-performance recording preserving the performed organic movement.
- Production Credential Manager/Billing adapters, verified backend entitlements,
  restoration, deletion, account routing and security review.
- Owner-controlled Play products, OAuth/backend/signing configuration and public URLs.
- Real-device performance, accessibility, playback, export and purchase tests.

This batch is not a claim of AAA visual quality, complete bug removal or Play readiness.
