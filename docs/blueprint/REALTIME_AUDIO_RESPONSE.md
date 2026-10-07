# Real-time audio response: evidence, fixes and measurement

Reviewed 2026-10-07. This is a source audit and implementation plan, not a device-performance certificate. No local build, test or lint was executed. The existing C++ styles and MilkDrop stay. New tunnels, 3D objects and camera movement belong in the native C++ engine. Live motion must have fresh generative variation and react to the music and touch; repeatable fixtures are for testing only.

## What Geode actually does

The app already has real music analysis and continuous visual response. The important work is making the whole path fresh, correctly timed and consistently applied. It would be inaccurate to call all existing visuals fake or nonreactive.

| Stage | Source inspected | Confirmed behavior | Gap |
| --- | --- | --- | --- |
| Playback | `app/.../playback/PlaybackEngine.kt` | A service-owned `PlaybackSession` creates Media3 with `TapRenderersFactory`. A separate native playback option exists. | The two playback paths need the same format, source-generation and presentation-time contract. |
| PCM tap | `app/.../audio/dsp/MvzAudioProcessorChain.kt`, `engine/audio-android/.../PcmTap.kt` | Decoded PCM is converted from signed 16-bit/float into reusable float staging and written to both app and analysis rings. | The order is tap → silence skipping → Sonic → DSP. Visual analysis therefore sees source audio before the transformations heard by the listener. |
| Analysis scheduling | `engine/scenes/.../analysis/AnalysisEngine.kt`, `engine/audio-core/.../MidSideWindow.kt`, `SampleRing.kt` | A coroutine reads the most recent 2048-frame window nominally every 16 ms. JNI invokes native analysis. | Refresh checks whether a full window exists, not whether its sample range changed. Paused audio can be analyzed repeatedly. Polling jitter changes the real audio-hop spacing while native tempo algorithms are told it is constant. |
| Native DSP | `core/analysis/ReactiveAnalyzer.cpp`, `Spectrum.cpp`, `LogBands.cpp`, `AdaptiveRange.cpp` | Hann-windowed KissFFT, frequency bands, per-band adaptive range, attack/release envelopes, flux/onset, tempo, drum hints, structure, stereo and chroma are implemented. | Several wiring and numerical-contract defects remain; detailed below. |
| Feature delivery | `AudioFeatures.kt`, `EnginePlumbing.kt`, `VisualizerRenderer.kt`, `RendererFrame.cpp` | Kotlin publishes features and the GL thread sends a native frame through JNI. Renderer applies modulation, continuous motion and visual safety. | Feature frames do not carry source sample position/generation/presentation timestamp into this live path. `StateFlow` conflates events; three-hop holds partly compensate. |
| Shader response | `core/viz/scenes/ShaderScene.cpp`, shader assets | Bass/mid/treble/RMS, audio texture, smoothed bands and musical motion uniforms genuinely vary with analysis. Several shaders consume them. | `uBeat` and `uBeatResponse` are hardcoded to zero. Restoring these indiscriminately would also re-enable old flash/shake paths. |
| Motion | `core/viz/MotionField.cpp` | Relative energy, confidence-weighted beat/bar oscillation, novelty-driven orbit, drift and harmonic color influence native parameters. | Fixed reset seed creates repeatable wander. This contradicts the latest live-session direction. Ambient drift continues without music, so a moving screenshot is not proof of audio response. |
| MilkDrop | `core/viz/scenes/MilkdropScene.cpp`, pinned projectM source | projectM independently analyzes PCM and renders the selected preset. | Native/app handoff freshness, mono downmix, silent input handling and presentation timing must be verified. |
| Presentation clock | `AudioPresentationClock.kt`, `SinkClockDriver.kt`, `FeatureRing.kt` | Clock and timestamped-feature primitives exist. | The live renderer does not consume them. The current clock driver is not proof that visuals are synchronized to heard output. |

Repository paths with `...` above are abbreviated package prefixes; the class/file names are exact. Findings describe the audited baseline, before the dedicated fix agents' changes.

## Ranked corrections

### P0 — consume new samples and stop replaying old music

The native PCM handoff and Kotlin analysis-ring reader need independent freshness fixes. Clearing the native renderer's pending PCM count does not stop `MidSideWindow` rereading an unchanged full analysis window.

Use a source generation plus monotonically increasing sample cursor. Process each scheduled analysis hop once. On underrun, pause or ended input, invalidate musical events and decay/clear music-driven envelopes according to an explicit quiet-state policy. Ambient scene motion may continue. Reset normalization/tempo history across discontinuities; do not let an old source's event be tagged as a new source's event. Export must process all required hops; live overload may drop bounded history but must report the discontinuity instead of pretending none occurred.

This should be implemented in the native/analysis lane, preserving the already repaired worker teardown serialization. A format reset must not race native resource destruction.

### P0 — align the analysis domain with the listening domain

Official Media3 documentation explicitly distinguishes tap placement before and after transformations. Geode's pre-processing tap is a legitimate source-analysis tap, but it does not demonstrate post-EQ, pitch-adjusted, silence-skipped listening response. The early tap can also produce future source features while audio is buffered for presentation.

Keep two purposes explicit: source-domain data for offline musical analysis, and output-domain PCM/features for live response. Prefer a post-processing live tap with timestamped feature history and a tested mapping to the audio sink's playback position. Moving the current tap to the end without revising `SinkClockDriver` would break its assumptions about pre-Sonic input frames and skipped samples. Do not make that one-line reorder in isolation.

Audit float-output/offload paths against the pinned Media3 source: if the configured custom processor chain is bypassed, the app needs a supported PCM path while visualization is active or an explicit unavailable state. Do not silently show simulated audio response. This bypass is an investigation item, not a confirmed failure of the current configuration.

### P1 — correct stereo energy and spectral-band semantics

1. `AnalysisSession::analyze()` passes **mid only** to `ReactiveAnalyzer`; side is used later for stereo descriptors. With a synthetic stereo tone `L = -R`, mid is zero even though each channel contains audible energy. The current spectrum and RMS consequently classify that signal as silent. The app PCM ring also averages channels before MilkDrop. Preserve channel-aware energy: an appropriate energy spectrum can combine left/right or mid/side powers before log-band reduction. Keep a signed waveform separately. Do not rectify the waveform to manufacture energy; rectification alters its spectrum.
2. `LogBands::rebuild()` forces each successive band to a new FFT bin with `cursor`, but `bandForHz()` uses ideal logarithmic edges. At 48 kHz with a 2048 FFT the bin spacing is 23.4375 Hz; the low bands cannot all have distinct bins while maintaining their declared log edges. `DrumChannels` mappings that use ideal band indices can therefore refer to different actual frequencies. Use shared weighted/overlapping log filters or an explicitly published actual-bin map. Add tone/sweep tests comparing energy placement with the same map consumed by drum detectors. Do not merely change the labels.
3. Offline `AnalysisSession::pull()` clamps hop samples to the FFT window while still advancing DSP time by `1 / hopRateHz`. At high sample rates this can disagree with the actual number of samples consumed. Use sample-derived hop duration and correct decimation/resampling or a window/hop design that supports the declared rates. Live and offline outputs must agree on time semantics.

These are narrow numerical fixes, not a reason to replace the functioning C++ FFT pipeline with a new large dependency.

### P1 — restore musical articulation through explicit scene contracts

Maintain three distinct native inputs: continuous levels, timestamped musical events and ambient generative motion. Map them to bounded scene changes. For example, bass can change tunnel radius, mid energy can deform objects, treble can affect fine emissive detail, and a sufficiently confident onset can add a short camera acceleration impulse. Beat/bar phase should influence smooth motion only when confidence supports it.

Replace the contradictory zeroed uniforms and stale comments by an intentional contract. Audit every consumer of `uBeat`, `uSpike`, flash, shake and beat response before reconnecting events. Use scene capability metadata so a control is only advertised where the scene implements it. A master audio influence control must scale all audio-derived geometry/camera/material changes consistently; it must not just turn down one shader's bands while leaving another modulation path active.

The camera should use fresh session entropy for target variation, music-conditioned target selection, continuous orientation/velocity and tunable acceleration limits. Never sample unrelated randomness every rendered frame. Track silence separately from low confidence: silence removes music impulses; low confidence still permits level-driven movement. Record realized transforms/events when recording a live performance.

### P2 — reduce duplicated bridges after proving parity

The app ring, analysis ring, native pending PCM, scene-specific PCM and feature bridge currently each have different freshness semantics. Consolidate ownership and metadata gradually after the regression tests protect each consumer. Do not delete all old styles or the working continuous response because a new camera is being added. Remove only an obsolete path whose replacement is wired, tested in Actions and compared on a device.

## External code and documentation checked

| Source | Verified behavior / useful lesson | Adoption decision |
| --- | --- | --- |
| [projectM pinned PCM](https://github.com/projectM-visualizer/projectm/blob/e0b0a967f0ffd7d332106c366668ed271718472b/src/libprojectM/Audio/PCM.cpp), [audio constants](https://github.com/projectM-visualizer/projectm/blob/e0b0a967f0ffd7d332106c366668ed271718472b/src/libprojectM/Audio/AudioConstants.hpp) | PCM is appended to a ring; per-render processing extracts waveform/spectrum data. The pinned input history is **576 frames**, with 480 waveform samples exposed for drawing. Sending only the most recent 576 frames per render is therefore not, by itself, proof of a truncation defect: feeding a larger chunk to this upstream ring also retains its tail. | Keep existing integration. Fix repeated/stale submission and stereo/presentation semantics. Do not inflate its buffer blindly or claim that forwarding every historical sample makes the per-frame analyzer sample-accurate. |
| [projectM Loudness](https://github.com/projectM-visualizer/projectm/blob/e0b0a967f0ffd7d332106c366668ed271718472b/src/libprojectM/Audio/Loudness.cpp), [license](https://github.com/projectM-visualizer/projectm/blob/e0b0a967f0ffd7d332106c366668ed271718472b/LICENSE.txt) | It exposes current and smoothed loudness relative to longer averages, with smoothing adjusted using elapsed frame time. This supports large dynamic-range music without prescribing one fixed gain. License file is LGPL-2.1. | Preserve projectM's separately governed dependency and its notices/source obligations; do not copy its code into unrelated application modules under a permissive label. Geode already has related normalization primitives. |
| [CAVA core](https://github.com/karlstav/cava/blob/master/cavacore.c), [core design](https://github.com/karlstav/cava/blob/master/CAVACORE.md), [license](https://github.com/karlstav/cava/blob/master/LICENSE) | Separates visualization processing from capture/output; logarithmic bands, auto sensitivity and controlled falloff improve legibility. Source takes a new-sample count and uses it to estimate cadence. | Core license is MIT, verified in LICENSE. Its normal FFTW dependency is GPL, explicitly called out in CAVACORE.md. Study techniques or adapt a reviewed isolated permissive part over existing KissFFT; do not link its complete default build. Geode's time-based envelopes are already preferable to copying frame constants. |
| [Flux AudioVisualizer](https://github.com/SRE-0/Audio-Visualizer), [native source](https://github.com/SRE-0/Audio-Visualizer/blob/main/AudioVisualizer/src/main/cpp/audio_processor.cpp), [license](https://github.com/SRE-0/Audio-Visualizer/blob/main/LICENSE) | A small Android input/processor/view separation. Actual native source has per-call smoothing constants and global mutable gain/history; its README describes a Kotlin FFT in microphone mode. | MIT verified. Not a replacement for Geode's C++ analyzer/render architecture. Do not copy claims of universal real-time behavior or its global state pattern. Useful mainly as a contrasting simple integration. |
| [audioFlux](https://github.com/libAudioFlux/audioFlux), [license](https://github.com/libAudioFlux/audioFlux/blob/master/LICENSE.md), [Android build](https://github.com/libAudioFlux/audioFlux/blob/master/docs/installing.md#android-build) | C analysis library with spectral/onset/chroma methods and Android build documentation. | Top-level MIT verified, but documented Android build uses FFTW. Technique reference or carefully isolated algorithm review only until the entire selected dependency chain is audited. No Python runtime is proposed for the app. |
| [Gist](https://github.com/adamstark/Gist), [license](https://github.com/adamstark/Gist/blob/master/LICENSE.txt) | Catalog of RMS, spectral and onset methods is useful as a feature checklist. | GPL-3.0 file verified. No implementation copied or integrated. |
| [KissFFT pinned license](https://github.com/mborgerding/kissfft/blob/7bce4153c6bc8aba2db0e889e576f9d00505cbe1/LICENSES/BSD-3-Clause) | Existing native FFT dependency. | Retain the BSD-3-Clause implementation and notices; correcting windowing, frequency mapping and timestamps is more valuable than replacing the FFT primitive. |
| [Media3 TeeAudioProcessor](https://developer.android.com/reference/androidx/media3/exoplayer/audio/TeeAudioProcessor), [DefaultAudioSink](https://developer.android.com/reference/androidx/media3/exoplayer/audio/DefaultAudioSink) | Tap position defines which processing has already occurred. Sink playback position is different from the fact that input has been queued. | Use these APIs as the integration contract. Verify against the project's pinned version when implementing. |
| [Android AudioTimestamp](https://developer.android.com/reference/android/media/AudioTimestamp), [Oboe guide at pinned revision](https://github.com/google/oboe/blob/b115f47593969fd67a21e9f63640ffef749b5067/docs/FullGuide.md), [Oboe license](https://github.com/google/oboe/blob/b115f47593969fd67a21e9f63640ffef749b5067/LICENSE) | Timestamp pairs represent audio frame position and an estimated presentation time. Oboe callbacks run on the latency-sensitive audio path and must not block on UI/render work. Oboe license is Apache-2.0. | Preserve C++/Oboe input/native-audio capability. Transport samples through a preallocated producer/consumer boundary; analysis and scene rendering stay outside the callback. Hardware timestamps are estimates and cannot measure unknown route delays. |

No external source code was copied into the app for this report or the instrumentation test. Default-branch sources above were read on the review date; pin an exact revision and record full dependency notices before any later code adoption.

## Tests that distinguish real response from ambient animation

### Authored now, awaiting GitHub Actions

Worktree: `geode-audio-response-tests`; branch: `codex/audio-response-tests`.

File: `app/src/androidTest/java/dev/geode/audio/RealtimeAudioResponseTest.kt`.

It creates PCM WAV fixtures in the instrumented app's cache and plays them through the actual `PlaybackSession`, WAV extractor, Media3 sink/tap, rings and JNI C++ analyzer. Tests cover:

1. A low tone followed by a high tone must produce nonzero energy and a changed spectral distribution, then a separate silent source must clear energy/events in a later source epoch.
2. Seeking from a tone into a silent section must advance the source epoch and clear the preceding signal.
3. Pausing a continuing tone must stop producer advancement and clear stale energy/events. This is an intentional regression requirement; it is expected to expose the baseline freshness defect until that production fix is included.

Preferences are isolated; player operations run on the main thread; native teardown is awaited; fixtures are removed. CSV observations are retained in the debug app's external files `audio-response/` directory. On the normal debug emulator this is `/sdcard/Android/data/dev.geode.debug/files/audio-response/`. CI should collect it before clearing app data, including when instrumentation fails. Each observation includes elapsed time, source epoch, written frames, RMS, centroid, maximum band and event hints.

The broad test deadlines tolerate emulator scheduling. They are liveness/behavior bounds, **not** a real-time latency specification. The current feature data lacks a matching sample timestamp, so polling ring state alongside feature state cannot prove exact per-hop synchronization. These tests do not exercise GPU rendering, physical speaker output, Bluetooth delay, microphone capture, or sustained thermal performance.

### Next native and instrumentation seams

| Test | Required assertion | Appropriate execution |
| --- | --- | --- |
| Sample ownership | Unchanged cursor never generates another analysis hop; epoch change invalidates old events; overrun reports dropped history | C++/Kotlin fixture tests in Actions |
| Hop clock | Irregular producer block sizes and 44.1/48/96/192 kHz inputs yield correct sample-derived hop timestamps; chunking does not alter event order | Native fixtures in Actions |
| Stereo | In-phase, left-only, right-only and anti-phase tones have consistent channel-energy response and correct pan/correlation | Native fixtures, then decoded-WAV instrumentation |
| Frequency mapping | Sweep/tone energy agrees with declared filter edges and drum-band selection | Native fixtures in Actions |
| Events at different display rates | 20/30/60/120 Hz consumers receive each event once; sustained event holds do not retrigger camera impulses | Timestamped feature/event fixture tests |
| Transformation parity | EQ gain, pitch, speed and silence skipping change the live output-domain features consistently with actual processed PCM | Real Media3-chain instrumentation |
| GPU response | Two offscreen renders share scene state, time, camera, touch, random stream and history; only audio input differs. Measure scene-specific geometry/material response and inspect captures | GLES instrumentation/emulator, followed by physical GPU devices |
| Music-driven camera | Inject features while keeping ambient variation fixed only in the test seam; audio influence zero removes musical acceleration/deformation, while generative ambient motion remains | Native camera fixtures + paired frame capture |
| End-to-end timing | Record source sample range → analysis completion → selected presentation feature → submitted/presented frame using one clock domain | Instrumented device trace; fixture has isolated impulses/tones |
| Physical sync | Compare known impulse audio and visible response with externally measured speaker/display timing; repeat on speaker/wired/Bluetooth routes | Device lab; emulator cannot certify this |

Proposed performance targets must be ratified after traces: report p50/p95/p99 hop-to-render delay, audio/visual offset and dropped-hop/frame counts, not a single best-case FPS number. Separately budget FFT window delay, producer buffering, processing, render queue and audio-route latency. A 2048-frame window spans about 42.7 ms at 48 kHz, already material to that budget.

For paired GPU tests, a controlled test seed/time is a measurement tool. It does not change the requirement that normal live sessions evolve differently. Random camera paths and time-driven shader animation otherwise make pixel-difference tests pass even when audio wiring is disconnected.

## Implementation sequence

1. Finish the current debug-APK Actions gate.
2. Land the native PCM consume-once/fanout fix and Kotlin analysis freshness/quiet reset together with the decoded-WAV regression tests. Preserve failure CSV/logs in Actions.
3. Correct stereo-energy and spectral-filter mapping; verify sample-rate/hop semantics.
4. Introduce output-domain analysis metadata and presentation-time selection; explicitly test speed/pitch/EQ/silence-skip paths.
5. Restore supported musical event mappings through scene capabilities and safety limits; remove zeroed/inert wiring and misleading comments after each replacement is verified.
6. Add the generative C++ 3D camera/tunnel/object layers while preserving all current styles and MilkDrop. Verify their response with paired scene-state captures and real tracks.
7. Run physical-device sync, long-session and thermal checks. Only then publish measured responsiveness and quality claims.
