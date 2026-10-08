# UI 2.0 validation

## Current integration status

This UI candidate is being integrated on the latest inspected `origin/main`,
`fa75ced697c45ba091b552200c40f78e9ad92392`. The source work preserves that main's
camera, PCM and fluid repairs. See [main integration](MAIN_INTEGRATION.md).

**Current-candidate build, test, lint and Android runtime results are pending.**
No local compilation, tests or lint were executed for this integration. The
repository requires these checks in GitHub Actions. A passing earlier UI build
does not validate this new base or any subsequent screen/material changes.

## Required current-commit Actions gates

The single [Android workflow](../../.github/workflows/android.yml) checks every
PR and main push. Review all results on the exact candidate commit; for a PR,
`commit.txt` records the merge commit that Actions actually checked out.

| Job | Current-candidate evidence required | Status |
| --- | --- | --- |
| `debug-apk` | `assembleDebug` and `assembleDebugAndroidTest`; both packaged ABIs; verified debug signature; native ELF/APK ZIP 16 KB alignment; APK and instrumentation SHA-256; dependency provenance and exact commit | Pending |
| `quality` | All-module `detekt`, `ktlintCheck`, `testDebugUnitTest`, audio-core JVM tests and app `lintDebug`; reports with actual suite counts and failures | Pending |
| `native-regressions` | Motion wrap, frame/MilkDrop PCM consumption, bounded camera rig, independent scene audio response, microphone PCM, fluid allocation and audio-drive C++ regressions; release and Android-tool Python suites | Pending |
| `ui-world` | Current production GLSL on Mesa software GLES in portrait and landscape; declared/uploaded uniform audit; non-black output; zero GL errors; reduced-motion time invariance; same-time audio response; current shader/scenery hashes | Pending |
| `emulator` | Exact uploaded app/instrumentation APKs; all instrumentation suites including spatial/fluid render-recreation tests; Living Lake component/orbit/destination/search/settings/playback smoke and adaptive profiles; screenshots, UI trees, recordings and logcat | Pending |

The software GLES job installs only runtime Mesa EGL/GL and Python NumPy/Pillow
dependencies. Its captures are uploaded as `ui-world-<run number>` and include
the checked-out commit. It does not substitute for the emulator or claim phone
GPU performance. The signed bundle requires all five verification jobs.

Existing UI regression suites cover destination history/save restoration, theme
migration, renderer visibility, unsaved Studio edits and readable materials.
Their results must be regenerated on this candidate. Tool tests similarly check
orbit selectors and callback/state evidence; their presence is not a pass claim.

The new `WorldAudioMailboxTest` suite contains nine source-level regression
cases for 48 ms held accents surviving slower frames, independent beat/transient
rearming, repeat suppression, expiry, bounded amplitudes, reduced motion and
yield/replay resets. These cases have not run locally and belong to the current
`quality` gate. Current production fragment source SHA-256 is
`ec41b830df61dca10e529ecfa7ed9cbbc6f4865c801c2f5a49e2c2eeefd044e5`;
this identifies source only and is not a shader compilation or render result.

## Historical evidence

The [previous-base Android report](validation/previous-base/android-build.json)
is retained unchanged for source commit
`b4ed0d027356c2833aaa7a11932a03475917c658`. Its 74 JVM tests, lint counts and APK
checksum describe that earlier UI source only. That APK is not the new-main UI
build and must not be offered as current-candidate verification.

The checked-in [portrait](validation/world-gles-report.json) and
[landscape](validation/landscape/world-gles-report.json) capture reports likewise
describe their recorded shader hash
`1421451f1631da93b278fb08502991f351c497771d07f2725791010ab08ff599`. They are
historical scene-only evidence from Mesa, not captured Android screens. Current
Actions evidence must record the candidate's actual shader and asset hashes.
Captured values have not been rewritten to imply a fresh run.

The wireframe and original environment images remain design/source artifacts;
they establish composition and asset provenance, not current runtime acceptance.

## Remaining device evidence

Review the Actions emulator artifacts for real SurfaceView/Compose ordering
during Visuals/Live/Studio/orbit handoff, restored state and recorded navigation.
Then inspect representative physical Android devices for TalkBack/focus,
large-font/inset behavior, context recreation, live audio response, sustained
frames, memory, battery and thermal stability. CI success does not establish
production visual quality or physical-device performance.

Requested world quality limits are provisional: normal 540-pixel short edge/60
FPS, quiet or constrained 360/30, severe thermal 360/15. Reduced motion stops
ambient pacing. No shader preview proves these frame rates on a phone.
