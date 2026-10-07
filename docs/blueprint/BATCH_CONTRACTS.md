# Implementation batch contracts — 7 October 2026

## Delivery rule

Implement, integrate, review, and commit locally before remote delivery. No worker
pushes or polls GitHub. Compilation, tests and lint still run only in Actions after
the completed batch is pushed. Authored regression tests are unexecuted until then.
No automatic merge or Play publication is authorized by this batch.

All workers share the checkout based on `2d1f3f2`, with disjoint file ownership.
Scope expansion requires coordination. Each handoff includes changed files,
acceptance criteria addressed, regression coverage, and unresolved limits. Root
owns integration, documentation, workflow test registration and final delivery.

Preserve every existing C++ style and MilkDrop. Visual simulation and camera belong
in C++/GPU shaders; live variation is fresh. Test seeds do not dictate live routes.
Kotlin owns Android integration. No fake prices, purchases or account verification.

## Assigned contracts

| Owner | Exclusive scope | Acceptance |
|---|---|---|
| audio_restart | AnalysisEngine, SampleRing, MidSideWindow and targeted tests | Restart waits for fresh PCM; one sample-rate/config snapshot per hop; obsolete work cannot publish; preserve stereo energy |
| playlist_import | MusicLibraryController, MusicPlaylistStore, PlaylistFormats and tests | Bounded provider reads and entries; unique name and durable write are one transaction; failure reaches UI; preserve existing bytes |
| transition_memory | GlTransitionEffect, TransitionFrameStore, ProjectComposition and tests | Consume each CPU frame once; handle late arrival; checked allocation; release at export teardown including cancellation before consumer creation |
| milkdrop_import | MilkImportController, MilkPackImporter, TextureStore, shared admission and tests | Common byte/dimension/footprint/count limits; staging and rollback; preserve existing assets and supported formats |
| native_camera | CameraDirector, Scene/RendererFrame flag routing, ShaderScene, rod_tunnel_frag.glsl and native tests | True raymarched camera integration; smooth bounded music response; fresh session variation; Motion zero and Reduced Motion freeze automatic camera travel; preserve catalog |
| android_export_service | ExportRun, ExportService, ExportController and admission helper/tests | Await actual foreground promotion before analysis; fail visibly on rejected service start; cancellation/timeout stops coroutine; stale callbacks cannot own next run |
| bug_android_review | Independent source review; bounded CaptureController permission cleanup fix | Trace callers and lifecycle; return concrete blockers to owners; distinguish source review from device proof |
| root | RenderScratch, GeodeContainer, scratch allocation sites, docs and CI registration | Delayed sweep never selects active-process scratch or new pending media; integrate all patches and review final diff |

The audio task finishes before the dedicated Android export task starts, keeping
at most six workers active. This ledger records task ownership, not test success.

## Review and delivery

1. Owners finish bounded implementations and regression sources.
2. Reviewer traces actual callers and returns concrete blockers.
3. Owners resolve findings; root checks integration and registers new tests.
4. Record remaining gaps honestly; commit the complete batch.
5. Read remote delivery state once after commit, push with conflict protection,
   and let the existing Actions workflow build the APK and execute checks.

## Remaining release packages

- Bounded off-main LUT/subtitle imports and truthful validation feedback.
- Presentation-clock synchronization, audible processing tap, Oboe microphone adapter.
- Additional spatial scenes/objects and complete customization coverage.
- Studio timeline gaps, Visual/Overlay lanes, preview parity, service-owned Studio lifetime.
- Actual live-performance recording preserving the performed organic movement.
- Production Credential Manager/Billing adapters, verified backend entitlements,
  restoration, deletion, account routing and security review.
- Owner-controlled Play products, OAuth/backend/signing configuration and public URLs.
- Real-device performance, accessibility, playback, export and purchase tests.

This batch is not a claim of AAA visual quality, complete bug removal or Play readiness.

## Integration review outcome

Source review covered audio publication invalidation, playlist durability, common
MilkDrop admission, camera controls, transition lifetime and Android service
admission. Review returned and owners repaired: cancellation before a coroutine
starts leaking the export reservation; cancellation before a transition consumer
exists retaining its frame; missing permission leaving capture projection alive;
and incomplete reduced-motion flag routing.

Root added process-owned export scratch and a fixed startup cutoff for old pending
MediaStore rows. A queued cleanup task cannot age new exports into its selection.
Native camera tests are registered in the existing Actions job; module unit tests
and instrumentation discovery include the new regressions automatically.

No compile, unit test, instrumentation, lint, benchmark or visual device validation
ran locally. A clean whitespace diff is only a source hygiene check. In particular,
Rod Tunnel quality, actual GLES transition ordering, foreground-service denial,
provider failures, and 4K memory require Actions/device evidence after delivery.
