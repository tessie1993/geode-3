# Implementation and verification status

Updated 7 October 2026. Baseline inspected: `9848b28`.

## Changes implemented in this branch

| Area | Change | Evidence / limit |
|---|---|---|
| Research | Eight reference apps, all nine supplied screenshots, licensed source review, native camera/art contract and build/release specification | Gallery images and supplied screenshots inspected; no competitor hands-on session, viewed trailer or benchmark claimed |
| Playback preferences | Saved preferences initialize at playback-session creation; one repository/subscription survives the UI | Three focused instrumentation regressions added; route/noisy/focus device tests still required |
| Native analysis | Serial native ownership across canceled loops/restart; teardown waits without blocking caller; tuning applied on worker | Three deterministic coroutine regressions added; JNI/device stress still required |
| Native motion | Separate drift angle from angular velocity, avoiding spin acceleration and wrap reversal | Host regression source covers 30/60/120 fps, scaling/reset/transition behavior |
| Release validation | Every ELF PT_LOAD and RELRO checked; malformed input fails; APK ZIP offsets checked; missing artifacts fail | Seven Python regression cases added; AAB bundletool/16 KB device validation remains required |
| Signing | Optional fail-closed signing flag, private temp key file, jarsigner verification, checksums and native symbols | No signing secrets supplied; no signed AAB claimed |
| CI | Detekt runtime separated from Java 25 compiler; all module unit tests and native/release checks wired | Workflow outcomes must be read for the pushed commit |
| Native dependency | Rebuild pinned AndroidX graphics-path native sources while preserving its official Java API and resources | Both ABIs compiled and passed ELF/RELRO/export checks in run 37552611481; archive rewrite then failed on mutated ZIP metadata. Follow-up fix and regression pushed; full APK gate still pending |
| Account foundation | Optional profile/commerce separation, entitlement policy, premium UI state/screen and debug-only preview | No live Credential Manager/Billing adapter; preview defaults to Unconfigured and cannot simulate a real purchase |
| Backend foundation | Purchase-domain parsing and atomic ledger/verifier interfaces with test fixtures | No HTTP auth, real Google Play adapter, durable database, deployed service or signing verifier; not a functioning production billing backend |

GitHub Actions run `37550743637` executed 22 Kotlin/JVM tests with zero failures,
plus passing Detekt, ktlint, Android lint and native/release regressions. App and
instrumentation APK compilation passed. Packaging failed on the official AndroidX
path library's RELRO alignment, so that run produced no downloadable APK and did
not run emulator tests. This evidence applies to that run's head, not to subsequent
unverified feature commits. Consult [WORK_QUEUE.md](WORK_QUEUE.md) for active owners.

No local compile, test or lint commands were run, following the existing rebuild
plan's Actions-only rule. Source review is not equivalent to executed tests.

## Still blocking a Play-ready product

- Complete service ownership of history, A-B repeat, resume, errors and fades;
  resolve native-player focus/lifetime or migrate it out of release.
- Oboe microphone integration and route/device latency proof.
- Beat/stale-PCM/presentation-clock, MilkDrop persistence, renderer recreation and
  complete live/export parity.
- Additive native 3D tunnels/objects, generative camera, universal customization
  and versioned upgrades preserving every existing C++ style and MilkDrop.
- Full Studio preview and Visual/Overlay lane export with correct timeline gaps.
- Production auth, premium UI/Billing integration, backend verification, account
  deletion, Drive integration and the security test matrix.
- Owner-controlled OAuth, Play products, backend deployment, signing and public
  privacy/deletion/support URLs.
- Real-device performance, accessibility, native compatibility and release testing.

See `DELIVERY_PLAN.md` for ordered work and `SECURITY_AUTH_PREMIUM.md` for the
account/payment boundary. A blueprint or green unit test is not a release approval.
