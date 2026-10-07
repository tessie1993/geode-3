# Implementation and verification status

Updated 7 October 2026. Baseline inspected: `9848b28`.

## Changes implemented in this branch

| Area | Change | Evidence / limit |
|---|---|---|
| Research | Seven reference apps, open-source licence-file review, spec/architecture/design/build/release documents | Public listing/document inspection; no competitor installation or device benchmark |
| Playback preferences | Saved preferences initialize at playback-session creation; one repository/subscription survives the UI | Three focused instrumentation regressions added; route/noisy/focus device tests still required |
| Native analysis | Serial native ownership across canceled loops/restart; teardown waits without blocking caller; tuning applied on worker | Three deterministic coroutine regressions added; JNI/device stress still required |
| Native motion | Separate drift angle from angular velocity, avoiding spin acceleration and wrap reversal | Host regression source covers 30/60/120 fps, scaling/reset/transition behavior |
| Release validation | Every ELF PT_LOAD and RELRO checked; malformed input fails; APK ZIP offsets checked; missing artifacts fail | Seven Python regression cases added; AAB bundletool/16 KB device validation remains required |
| Signing | Optional fail-closed signing flag, private temp key file, jarsigner verification, checksums and native symbols | No signing secrets supplied; no signed AAB claimed |
| CI | Detekt runtime separated from Java 25 compiler; all module unit tests and native/release checks wired | Workflow outcomes must be read for the pushed commit |

No local compile, test or lint commands were run, following the existing rebuild
plan's Actions-only rule. Source review is not equivalent to executed tests.

## Still blocking a Play-ready product

- Complete service ownership of history, A-B repeat, resume, errors and fades;
  resolve native-player focus/lifetime or migrate it out of release.
- AAudio microphone integration and route/device latency proof.
- Beat/stale-PCM/presentation-clock, MilkDrop persistence, renderer recreation and
  complete live/export parity.
- New signature visual designs, universal customization and saved-style migrations.
- Full Studio preview and Visual/Overlay lane export with correct timeline gaps.
- Production auth, premium UI/Billing integration, backend verification, account
  deletion, Drive integration and the security test matrix.
- Owner-controlled OAuth, Play products, backend deployment, signing and public
  privacy/deletion/support URLs.
- Real-device performance, accessibility, native compatibility and release testing.

See `DELIVERY_PLAN.md` for ordered work and `SECURITY_AUTH_PREMIUM.md` for the
account/payment boundary. A blueprint or green unit test is not a release approval.
