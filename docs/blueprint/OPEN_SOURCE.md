# Open-source and documentation decisions

Research checked 7 October 2026. No new third-party source is copied in this
blueprint. Existing provenance in `docs/visualizer-v2/provenance.json` remains the
adoption ledger. Before importing code, pin a commit, check the exact file and
transitive assets, record modifications and ship the required notices.

## Repository shortlist

| Source | Licence evidence inspected | Role and decision |
|---|---|---|
| [androidx/media](https://github.com/androidx/media) | [LICENSE](https://github.com/androidx/media/blob/release/LICENSE), Apache-2.0; licence blob `d645695673349e3947e8e5ae42332d0ac3164cd7` | **Use.** ExoPlayer, sessions, library service, Transformer and CompositionPlayer. Study official `demos` and `docsamples`; keep all Media3 artifacts on one version. |
| [google/oboe](https://github.com/google/oboe) | [LICENSE](https://github.com/google/oboe/blob/main/LICENSE), Apache-2.0; blob `d645695673349e3947e8e5ae42332d0ac3164cd7` | **Removed** (owner decision, 7 October 2026; PR #10). The microphone uses AAudio from the NDK directly; Oboe samples remain a reference for stream lifecycle and disconnect handling. |
| [projectM](https://github.com/projectM-visualizer/projectm) | [LICENSE.txt](https://github.com/projectM-visualizer/projectm/blob/master/LICENSE.txt), LGPL 2.1 text; blob `125c022f19a416ce7ffdfc7c89f94a1ed56d291d` | **Retain boundary.** Existing `e0b0a967f0ffd7d332106c366668ed271718472b` plus in-tree FBO patch. Preserve dynamic library, source/patch/build materials and applicable replacement/relinking obligations; dynamic linking alone is not the complete compliance review. Preset packs need their own asset licences. |
| [KISS FFT](https://github.com/mborgerding/kissfft) | [COPYING](https://github.com/mborgerding/kissfft/blob/master/COPYING) and [BSD-3-Clause text](https://github.com/mborgerding/kissfft/blob/master/LICENSES/BSD-3-Clause) | **Retain.** Bounded float FFT. Current submodule `7bce4153c6bc8aba2db0e889e576f9d00505cbe1`; benchmark before replacing it. |
| [WebGL Fluid Simulation](https://github.com/PavelDoGreat/WebGL-Fluid-Simulation) | [LICENSE](https://github.com/PavelDoGreat/WebGL-Fluid-Simulation/blob/master/LICENSE), MIT | **Adapt algorithms within existing provenance.** Advection, divergence, pressure projection, vorticity, splats and bloom. Port GLSL/math to native GLES; do not embed a browser runtime. Existing Geode fluid implementation already cites it. |
| [ShaderEditor](https://github.com/markusfisch/ShaderEditor) | [LICENSE](https://github.com/markusfisch/ShaderEditor/blob/master/LICENSE), MIT; blob `d8d1df2472908229862c2510152442180bb4799a` | **Study/adapt selectively.** Android shader/wallpaper lifecycle, user-facing shader errors and quality controls; not an application shell to copy. |
| [SwissGL](https://github.com/paradigms-of-intelligence/swissgl) | [LICENSE](https://github.com/paradigms-of-intelligence/swissgl/blob/main/LICENSE), Apache-2.0; blob `261eeb9e9f8b2b4b0d119366dda99c6fd7d35c64` | **Simulation reference.** Ping-pong texture state and instanced particles. The old `google/swissgl` reference has moved; verify redirects when updating provenance. No JavaScript runtime in Geode. |
| [Diligent Engine](https://github.com/DiligentGraphics/DiligentEngine) | [License.txt](https://github.com/DiligentGraphics/DiligentEngine/blob/master/License.txt), Apache-2.0; blob `d9a10c0d8e868ebf8da0b3dc95bb0be634c34bfe` | **Prototype only.** Cross-platform rendering candidate, not approved production replacement. Android support does not prove safe shared-context interop with our projectM/export path. |

Licence blobs identify the text inspected, not the commit of every source file.
No candidate is approved for import solely from this table. Existing TagLib and
GL Transitions obligations must also be reconciled against the packaged binary.

## Engine selection gate

Keep the current GLES renderer as the production baseline. A Diligent prototype
must prove, on arm64 and x86_64: one scene in the app, context loss recovery,
projectM state isolation, offscreen encoder-surface output, 16 KB compatibility,
frame/thermal performance and acceptable binary/build-time growth. Compare the
same scene in the existing renderer. Keep Vulkan out of the release-critical path
unless measured benefits justify the interop and driver matrix. This is an
engineering risk decision, not a finding that Diligent cannot work.

## Primary documentation and how it changes the build

| Documentation | Concrete requirement |
|---|---|
| [Media3 background playback](https://developer.android.com/media/media3/session/background-playback) | Service owns player and session; UI sends controller commands; release owned resources at service teardown |
| [Media3 multi-asset editing](https://developer.android.com/media/media3/transformer/multi-asset) | Represent media composition explicitly; prototype CompositionPlayer/Transformer parity with native visual frames |
| [Oboe low latency](https://developer.android.com/games/sdk/oboe/low-latency-audio) (applies to AAudio) | Request low-latency/exclusive where supported, accept negotiated format, use callbacks and avoid blocking work |
| [Audio playback capture](https://developer.android.com/media/platform/av-capture) | Android 10+ consent-based capture; respect eligibility/capture policies; do not promise all-app or DRM capture |
| [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) | Separate playback, projection and processing service responsibilities and satisfy launch/permission restrictions |
| [16 KB page sizes](https://developer.android.com/guide/practices/page-sizes) | Check every native ELF load segment, generated APK ZIP alignment and runtime on a 16 KB device |
| [Target API requirement](https://developer.android.com/google/play/requirements/target-sdk) | As checked: new apps/updates require API 36 from 31 August 2026; verify again before upload |
| [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview) | Capture real startup/library/player/preset journeys, benchmark a release build and ship the profile |
| [Billing security](https://developer.android.com/google/play/billing/security) | Verify purchases before granting; acknowledge purchases; handle pending, refunds, revocation and restore |
| [Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg) | Optional Google sign-in; cancellation and no-account are normal states |
| [Drive app data](https://developers.google.com/workspace/drive/api/guides/appdata) | Separate Drive authorization; least-privilege appDataFolder scope; versioned backup manifests |
| [Account deletion](https://support.google.com/googleplay/android-developer/answer/13327111) | If account creation is supported, implement the applicable in-app and external deletion paths |
| [Personal-account testing](https://support.google.com/googleplay/android-developer/answer/14151465) | Confirm account-specific testing eligibility; qualifying new personal accounts need the prescribed closed test before production access |
| [Detekt compatibility](https://detekt.dev/docs/introduction/compatibility/) and [Java 25 issue](https://github.com/detekt/detekt/issues/8714) | Detekt 1.23.8's embedded compiler cannot run on Java 25; changing bytecode target alone does not fix the runtime |

## Adoption rules

1. Prefer official Android samples for lifecycle/API behavior.
2. Use permissive code only after exact-file licence review. Carry MIT/BSD
   notices and Apache licence/NOTICE/change notices as applicable.
3. Keep the existing LGPL projectM exception explicit. Do not treat arbitrary
   MilkDrop packs, shader sites or texture downloads as freely redistributable.
4. GPL/AGPL/noncommercial/no-licence examples are not copied into this product.
   Study mathematical papers or independent permissive implementations instead.
5. Record URL, commit, files, licence text hash, modifications and tests before
   adoption. Reconcile native dependencies as well as Kotlin/Gradle libraries.
6. Do not add another engine/library because it is popular; require a demonstrated
   capability or maintenance benefit and a migration/rollback path.
