# Visual engine deep scan and next implementation gates

Checked 7 October 2026 against local HEAD
`97dc5d4a379c9e231fa3e90eafa0c269643a40ec` and the working tree. This is a
source inspection and primary-source research report. No local build, test,
lint, emulator or physical-device run was performed for this report. Line
references describe the inspected tree and may move as the active CI repair
lands. Only this document was written during this scan.

**The next implementation gate is a successful GitHub Actions debug APK job
with a downloadable APK.** The packages below are proposed work after that
gate, not completed changes. The owner subsequently requested additional fix
agents and independent bug/feature reviewers. Disjoint worktrees/file ownership
and Actions-only validation remain required.

Owner update: preserve every existing C++ style and MilkDrop. Follow
[VISUAL_STYLE_CAMERA.md](VISUAL_STYLE_CAMERA.md) for fresh generative live
movement and captured-performance fidelity; a fixed seed alone does not record
the realized live session. Later native findings are in
[NATIVE_BUG_CONFIG_AUDIT.md](NATIVE_BUG_CONFIG_AUDIT.md).

Read this with [the architecture](ARCHITECTURE.md),
[feature acceptance criteria](FEATURE_SPEC.md), [design](DESIGN.md),
[delivery plan](DELIVERY_PLAN.md), [seven-app comparison](REFERENCE_APPS.md),
and [source/licence decisions](OPEN_SOURCE.md). The historical
[audit](../AUDIT_2026-10.md) is a lead for inspection; this report distinguishes
findings reconfirmed in the current active path from old observations.

## Active path, including two separate PCM rings

| Stage | Active source evidence | Consequence |
|---|---|---|
| Media3 PCM tap and capture sink | `app/.../playback/PlaybackEngine.kt:35–65` writes both `PcmRingBuffer` and `SampleRing`; tap boundaries begin a sample-ring epoch | The renderer PCM stream and analysis windows have different consumers and cursors. A correction in one does not automatically correct the other. |
| Analysis worker | `engine/audio-core/.../MidSideWindow.kt:18–32`; `engine/scenes/.../analysis/AnalysisEngine.kt:119–150` | The worker analyzes the latest mid/side window and publishes an `AudioFeatures` snapshot. |
| Shared audio features | `PlaybackEngine.kt:140–151`; `app/.../audio/AudioBus.kt:12–21` | Analysis runs while consumers exist. Bus freshness currently means publication age, not age of the underlying audio samples. |
| Live renderer bridge | `engine/scenes/.../render/VisualizerRenderer.kt:172–209` | Every GL draw forwards the current feature object, asks for PCM, and supplies elapsed real time to native rendering. |
| Renderer PCM provider | `app/.../ui/PlayerSession.kt:373–380`; `app/.../audio/PcmRingBuffer.kt:56–72` | `copyNewSince` already advances a cursor and returns no chunk when there is no new PCM. It deliberately takes the latest bounded tail if the consumer falls behind. |
| Native frame | `core/viz/RendererFrame.cpp:25–48,138–198` | Features are latched once, but PCM is separately copied for each visible scene and is never marked consumed. |
| Shader-backed scenes | `core/viz/SceneRegistry.cpp:23–27,77`; `core/viz/scenes/ShaderScene.cpp:84–119,155–246` | Registered scope, tunnel, orb, mandala and other shader styles reach the upload path with disconnected beat inputs. |
| projectM scene | `core/viz/scenes/MilkdropScene.cpp:164–179,222–238` | Receives native PCM or feature-waveform fallback and advances projectM independently of the timestamp supplied to `draw`. |

The path prefixes above are `app/src/main/java/dev/geode/`,
`engine/audio-core/src/main/kotlin/dev/geode/engine/audio/`, and
`engine/scenes/src/main/kotlin/dev/geode/`. These are current production
sources, not the legacy prototype renderer.

## Findings ordered by correction priority

### V01 — Native PCM is replayed after the producer stops

**Confirmed, open.** `core/viz/Renderer.cpp:73–79` copies the newest bounded
block and assigns `pcmCount_`. `RendererFrame.cpp:138–149` copies that block
without clearing the pending count. Subsequent frames therefore deliver the
same samples again. Layer and outgoing-scene calls at lines 164 and 177 read
under separate locks, so a producer write between them can also make one
composite frame observe different PCM blocks.

Do not reset the count inside the first `deliverPcm` call: that would starve
the other visible scene. Consume once at frame start into a preallocated,
immutable frame buffer, then fan that same buffer out to every scene rendered
in that frame. A producer write after the snapshot belongs to the next frame.
Keep producer lock duration bounded to the copy; do not call scene code while
holding the lock. Preserve the existing live latest-tail overflow policy.

**Correction to an early hypothesis:** `PlayerSession.latestPcm()` is already
cursor-based. It is not the source of this repeated-block defect.

### V02 — Paused audio can be analyzed indefinitely as fresh input

**Confirmed, open.** `SampleRing.kt:69–83` snapshots the latest full window
whenever enough frames have ever been written. `MidSideWindow.refresh()` does
not compare `writtenFrames` or `epoch`. `AnalysisEngine.Pass.tick()` then calls
native analysis with a fixed 16 ms delta and constructs new feature arrays.
Stopping the PCM producer does not itself make this path return false.

Consequently, analysis can continue to derive energy and events from the last
nonzero window; publication refreshes `AudioBus.latestAtMs`. The bus's 1.5-second
stale timeout cannot distinguish these reanalyses from new audio. Merely adding
a timestamp at `NativeViz.setFeatures` would have the same flaw.

Track a coherent `(epoch, written-frame end)` acquisition stamp. Analyze only
when a fresh window is available; reject a copy crossing an epoch boundary.
Derive audio-analysis delta from new sample time and negotiated sample rate,
with an explicit policy for skipped live windows. Separately run a bounded
quiet-state transition when input stops. Reset held beat/drum/structure events
at expiry and on epoch changes. Continue ambient scene time and touch response;
Silent Explore is an explicit source mode, not stale music mistaken for input.

The native analyzer lifetime/start-stop serialization repair already in the
tree addresses ownership. It does not supply this freshness policy.

### V03 — Beat response is disconnected in active shader scenes

**Confirmed, open.** `ShaderScene.cpp:163,176,242–246` supplies `uBeat = 0`,
`uBeatResponse = 0`, `uSpike = 0`, a constant spawn seed/age, constant form phase
and constant move direction. Yet retained shaders read these values:

| Example | Current reader | Disabled behavior |
|---|---|---|
| Scope | `app/src/main/assets/shaders/scope_frag.glsl:91,108–115,156` | Beat shake, pulse/zoom contribution and flash contribution |
| Chroma orb | `chroma_orb_frag.glsl:109–110` | Smoothed bass response and transient fringe response |
| Mandala dome | `mandala_dome_frag.glsl:134–135` | Transient response and bass breathing |
| Rod tunnel | `rod_tunnel_frag.glsl:121,160` | Transient-dependent hue response |

`uBeatPhase` and continuous motion uniforms are still supplied, so this is not
a claim that all audio reactivity is absent. Also, projectM separately reads
`p.beatResponse` in `MilkdropScene.cpp:234`; its response control is not subject
to the shader-uniform zeroing.

First define a normalized, finite event envelope with explicit attack/release,
held-event edge handling, reset and NaN/Inf rejection. Repeated draws of one
held analysis event must not retrigger indefinitely. Use elapsed time for
decay so 30/60/120 Hz rendering has the same musical response. Restore only
mapped, reviewed inputs; do not resurrect the removed FormDrive system by
guessing values for every legacy uniform.

**Safety dependency:** restoring `uBeat` also activates existing shader flash
and shake expressions. Do not globally enable it as a one-line patch. Separate
geometric response from luminance/flash response, keep currently inactive flash
paths inactive until their own gate passes, and coordinate any shader-file
changes with the app/UI owner. Restoring a control is not a medical-safety claim.

### V04 — projectM truncates and then synthesizes input without freshness

**Confirmed, open.** `MilkdropScene.cpp:61–66` accumulates up to 8192 samples,
but update at lines 171–175 retains at most the final 576 samples and discards
the rest. Its `else` branch feeds the feature waveform on every update without
knowing whether that waveform is fresh, live or generated for an export.

The inspected upstream projectM PCM implementation accepts the complete sample
count and advances its own bounded rolling buffer. The local 576-sample cap is
not justified by that API implementation. Its frame-analysis code also retains
the latest rolling waveform when nothing new is submitted. Therefore fixing
V01 alone is insufficient to guarantee a quiet projectM image after pause.

Define live PCM, feature-only/offline input and no-input states explicitly.
Feed every retained sample once; document deliberate live overflow. On live
input expiry, provide an explicit bounded silence policy that flushes the
projectM rolling audio state without inventing new onsets. Test it with the
actual pinned projectM build. Do not remove the feature-waveform fallback until
offline rendering has an equivalent supported input path.

### V05 — Composite beat effects are also disconnected; reduced motion is partial

**Confirmed, open; separate from V03.** `RendererFrame.cpp:108–110` decays the
post beat pulse with a zero target. Composite inputs at lines 239–248 set
`hitImpulse` to zero and pass zero to the flash impulse calculation. Fixing
shader beat uniforms does not reconnect composite-backed pulse/shake/flash.

`core/viz/VisualSafety.cpp:25–45` bounds several parameters but only scales
shake/pulse/motion by 0.4 in reduced motion, and does not disable flash there.
This falls short of the target in `FEATURE_SPEC.md` that reduced motion disables
flash/shake. Parameter caps and a flash-edge budget do not establish a bound on
the luminance of the fully composed image, especially with user presets/layers.
Keep re-enabling composite effects out of the first correctness patch; give
them a mapping, reduced-motion and captured-output acceptance gate.

### V06 — Presentation clock exists but does not select visual features

**Confirmed integration gap, open.** `PlaybackEngine.kt:42–44,63–64` creates
`AudioPresentationClock`/`SinkClockDriver`; the driver records format, speed and
discontinuity mappings. Source search found no production caller of
`AudioPresentationClock.inputPositionAt` and no production instance of
`FeatureRing`; its current consumers are tests. Live rendering instead forwards
the latest feature snapshot on a wall-clock draw.

Complete the existing presentation mapping and indexed feature acquisition
before claiming calibrated audiovisual synchronization. Define unknown/stale
clock handling, seek/format/source epochs, speed and skip-silence behavior,
Bluetooth calibration, and the renderer's target presentation time. No actual
device latency value was measured in this scan. V02 expiry alone will not fix
features that arrive ahead of sound buffered by the audio sink.

### V07 — Offline analysis can drop samples and mislabel time at high sample rates

**Confirmed, open; separate export package.** Native
`core/analysis/AnalysisSession.cpp:19–21` clamps its hop to the FFT size.
`engine/scenes/.../analysis/OfflineAnalyzer.kt:168–169,201–214` advances its
timestamp counter by `sampleRate / 60` without that clamp. At 192 kHz and a
2048-sample FFT, native consumes 2048 samples while Kotlin advances 3200:
the timestamp advance is 1.5625 times the consumed sample interval. Native
`pull()` also analyzes with a nominal `1 / hopRateHz` delta at line 105.

Native `push()` at lines 65–98 intentionally drops old samples at its bounded
capacity. OfflineAnalyzer feeds a full decoder buffer before draining. A large
decoder output block can therefore drop offline audio while timestamps still
advance as if all analyzed windows were contiguous. Offline processing needs
bounded chunk-and-drain/backpressure and authoritative sample counters; live
latest-tail dropping is not a valid offline completeness policy.

`FeatureTimeline.kt:107–131` merges beat/onset/flux/strength/transient over an
output interval, but omits kick/snare/hat and structural events. Those events
can be missed between output frames. `FrameAccumulator.kt:56–73` preserves drum
maxima during compaction but not downbeat/section/drop/arrival events. Specify
continuous interpolation versus event aggregation and interval boundaries.

### V08 — projectM export time and lifecycle need separate parity validation

**Timestamp gap confirmed; visual impact unmeasured.** `MilkdropScene.draw`
at lines 222–238 explicitly ignores `timeSeconds` and renders through projectM's
normal clock. That boundary does not establish deterministic offscreen timing.
Verify the pinned project's supported time-control path before promising
frame-identical preview/export or migrating preset behavior.

**Historical-audit correction:** the current `ensureEngine()` at lines 118–151
creates projectM only when absent; a size change uses `projectm_set_window_size`.
This scan does not reconfirm an unconditional engine rebuild on thermal resize.
Full GL context recreation and view detach remain lifecycle test cases; do not
describe resize reset as a current proven defect without a reproduction.

### V09 — Motion controls require accurate capability labels

**Confirmed semantics, coordinate with customization work.** The angle-as-rate
drift bug has a prior source repair: `MotionField.cpp:187` applies `driftRate`,
while `state.drift` remains a wrapped shader angle. Do not undo that separation.
However, line 191 modulates speed independently of `motionAmount`. Thus a UI
label that describes Motion amount as a master motion-off switch would be
incorrect. Orbit is uploaded as shader state but needs demonstrated readers and
per-style mapping before exposure. FormDrive has no restored runtime contract.
Keep persisted fields for migration; gate visible controls by actual behavior.

## Primary-source research checked for the next packages

These sources were read from the upstream repositories through the GitHub
connector. Maintenance dates below come from their latest default-branch commit
responses checked on 7 October 2026; they are not claims of release stability.
No upstream code or artwork was copied. Existing dependency pins are unchanged.

| Official maintained source | Inspected evidence | Applicable decision |
|---|---|---|
| [AndroidX Media3 AudioSink](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioSink.java) | Blob `97fac2e91f813fbeac9b07530e517b808b219231`; playback-position/discontinuity contracts. Latest release-branch commit observed: `8c6678b657ede1e7883fc164ef73ed483c7796c3`, 8 Sep 2026. Apache-2.0 evidence in `OPEN_SOURCE.md`. | Use the sink's defined presentation domain and explicit discontinuities. Keep wall time, media time and input sample indices separate; finish our existing clock integration. |
| [Oboe full guide](https://github.com/google/oboe/blob/main/docs/FullGuide.md) | Blob `52d4fa461f9b418222162dfedbdd9b57e17d04e9`; callback, negotiated-format and stream lifecycle guidance. Latest commit observed `4468344c8fdfdd7df068d5abefe2d8615fd6b431`, 5 Oct 2026. Apache-2.0. | Retain the Media3-output/Oboe-input direction. Move no allocation, blocking lock, JNI or reopen into the realtime callback. Native microphone replacement and compatible Oboe updates remain a separate implementation/device gate; this scan does not claim the current microphone is Oboe-backed. |
| [projectM PCM](https://github.com/projectM-visualizer/projectm/blob/v4.1.7/src/libprojectM/Audio/PCM.cpp) and [Loudness](https://github.com/projectM-visualizer/projectm/blob/v4.1.7/src/libprojectM/Audio/Loudness.cpp) | PCM blob `56a7cc65d05abf68c073c5226b5334182af3f1c2`, Loudness blob `2139c92406f66d998926b7ec6e070f2e0988f551`. Current master commit observed `e98fca85e57802d27a6d11499642de2a1d5e994e`, 6 Oct 2026. Inspected behavior is the pinned-version source, not an assumed master backport. | PCM accepts all submitted samples into its rolling buffer. Loudness uses asymmetric smoothing and elapsed-time-adjusted decay. Study this separation of signal level and decay for our own event envelope; preserve the LGPL boundary and notices. Loudness smoothing itself is not a beat-event detector. |
| [Filament materials](https://github.com/google/filament/blob/main/docs/Materials.md.html) | Blob `3a82cd3f1de05a3234199c50c6235637cbadffe4`; material roughness, emissive and refraction models. Latest commit observed `79bfde1c94e591ade7321f344b72a35c48c0b6ca`, 6 Oct 2026. [Apache-2.0 licence](https://github.com/google/filament/blob/main/LICENSE), blob `73774b41cafc7ca9171fa50ea9fd95da343be320`. | Use coherent roughness, emission, lighting and material response as art-direction criteria. This is a design reference, not approval to add a second render engine. Budget optional bloom/refraction independently on mobile. |
| [Diligent Tutorial27 post-processing](https://github.com/DiligentGraphics/DiligentSamples/blob/master/Tutorials/Tutorial27_PostProcessing/readme.md) | Blob `0e6e80698235dd9b139b2bd66298f1dbed7dd626`; explicit G-buffer, temporal motion, lighting and tone-mapping stages. Latest commit observed `03a10e15e69c5ae1738a52175ae029f3f4275a90`, 5 Oct 2026. [Apache-2.0 licence](https://github.com/DiligentGraphics/DiligentSamples/blob/master/License.txt), blob `d9a10c0d8e868ebf8da0b3dc95bb0be634c34bfe`. | Study pass boundaries, temporal-history ownership and output tone mapping for spatial polish. A complete deferred/SSR pipeline is not an automatic mobile upgrade; retain the existing GLES baseline and require the architecture's isolated Diligent prototype gate. |

The GPU research supports a concrete art pass after correctness: strong depth
hierarchy, readable foreground silhouette, controlled reflective/emissive
materials, stable camera motion, temporally coherent transitions and a measured
quality ladder. For the six original scene directions in `DESIGN.md`, require
distinct geometry/camera/material behavior and a meaningful mapping of each
universal control. A different palette on the same tunnel is not a new spatial
scene. Benchmark optional passes before adding them to the baseline tier.

## What the seven reference apps change about the priorities

The app research remains public-listing research, not installed-app observation.
This scan does not assert superior graphics, responsiveness or battery life.

| Existing reference evidence | Concrete Geode priority and gate |
|---|---|
| Astral's spatial scenes and many controls | Repair effective beat/material/motion mappings before adding more sliders. Demonstrate six visually distinct scene directions with understandable macro controls. |
| Fluids Sounds and Magic Fluids touch/preset behavior | Keep touch responsive when music pauses; reset cancelled pointers; capture the same gestures across quality tiers. Preserve fluid state through non-destructive parameter changes. |
| projectM Pro's preset compatibility and configurable quality | Deliver PCM once, settle on silence, retain preset selection across lifecycle changes, and prove low-tier rendering without resetting a chosen look. |
| Vythm's performance effects | Make effects observable and reversible, with truthful availability, safe defaults and stable transitions. Do not expose currently zeroed composite effects as functional. |
| Avee's music-video/template export | Correct native/Kotlin sample-time agreement, complete offline analysis and event aggregation before promoting Studio output. Preview and export need one evaluator. |
| Fraksl's modulation sources | Separate continuous values, discrete events and clocks. Add modulation sources only after each mapping can state its range, smoothing, reset and ownership. |

## Proposed bounded implementation sequence

| Package | Scope and dependencies | Required acceptance before promotion |
|---|---|---|
| Gate A | Parent-owned Actions repair and debug APK artifact | Green debug job, downloadable/installable APK, recorded artifact/run SHA. A green APK job is not the full Play release gate. |
| VE1: PCM frame ownership | V01; native renderer mailbox and a host-testable bounded buffer helper | One push delivered once per visible scene in one frame; no replay on the next frame; no starvation of layer/outgoing scene; producer-after-snapshot reaches next frame; newest-tail overflow is explicit. |
| VE2: Fresh analysis and quiet state | V02; sample acquisition stamp, window/worker freshness, reset/expiry contract; retain the lifecycle fix | Duplicate cursors never invoke native analysis again; epoch changes cannot leak old events; pause reaches quiet audio-driven state within a documented bound; rapid restart survives; ambient time and touch continue. Initial target: settle within 500 ms, calibrated against real callback cadence. |
| VE3: Reviewed beat mapping | V03 after VE2, shader inventory and customization-owner agreement | A finite held event produces one envelope at 30/60/120 Hz; Beat response has a monotonic observable range where supported; stale frames do not retrigger. Restore geometric inputs only after checking every affected luminance/flash reader and reduced-motion behavior. |
| VE4: projectM input policy | V04 after VE1/VE2; explicit live/offline/no-input mode | All retained PCM supplied once; pause settles with the pinned engine; offline fallback remains functional; no preset resets across scene switches/resize. |
| VE5: Clock and export correctness | V06–V08; authoritative sample counters, FeatureRing consumer, timeline/event rules, projectM time adapter feasibility | Speed/seek/skip-silence/route matrix; matching live/export evaluator; large decoder chunks retain all offline data; no accumulating drift in 10-minute 44.1/48/96/192 kHz fixtures. Bump analysis-cache identity for changed timing/event semantics. |
| VE6: Spatial art and effect completion | V05/V09 and `DESIGN.md`; verified mapping/capability schema | Six reviewed art directions, consistent control meanings, reduced motion disables flash/shake, quality tiers preserve composition, stable context/preset restoration and sustained device performance. |

VE1 and VE2 can be separate small reviews with independent regression sources.
Do not bundle renderer replacement, dependency upgrades, preset deletion or a
new auth/billing integration into them. Re-run the relevant Actions gates after
each landed change. Final release still requires all applicable packages and
security/store/device gates in `DELIVERY_PLAN.md`.

## Regression and performance evidence to collect

All items below are **required future validation**, not reported passing tests.
Parent owns Actions workflow changes. Test source additions can be authored in
the relevant package and run only through Actions.

1. **Host C++ PCM test:** tagged sample sequences, empty frame, all visible
   consumers, overflow boundaries, producer-between-frames and reset. Exercise
   the real extracted mailbox behavior, not a test-only imitation.
2. **JVM sample/worker tests:** no new writes, same cursor after pause, fresh
   epoch with the same numeric cursor, mono/stereo, delayed producer, format
   transition, stop/start overlap and expiry using a controlled clock. Include
   `:engine:audio-core:test`; Android `testDebugUnitTest` alone does not execute
   tests in that pure JVM module.
3. **Host C++ envelope test:** held beat, two separated events, transient-only
   input, stale/invalid features, dt extremes, reset and frame-rate invariance.
   Verify bounded energy and finite release. Keep existing MotionField tests.
4. **Pinned projectM integration:** recognizable PCM fixture and silence,
   repeated pause/resume, crossfade/layer behavior, preset restore and context
   recreation. Capture per-frame diagnostic timestamps and presented imagery;
   static screenshots alone cannot prove temporal behavior.
5. **Offline regression:** identical samples supplied in tiny and oversized
   decoder chunks must yield equivalent feature timelines. Check sample-count
   duration, end padding, actual hop duration, cache invalidation and drum/
   structural pulses between render frames at 24/30/60 fps.
6. **Actions emulator smoke:** startup, local track, pause/resume, style switch,
   Customize, orientation and background/resume; retain UI tree, screenshots,
   crash logcat, frame and memory evidence. Emulator results do not substitute
   for mobile GPU/audio-route measurements.
7. **Named physical devices:** sustained 20-minute high/low-tier scene runs,
   100 scene switches, 20 capture/export cycles and route transitions. Use the
   FEATURE_SPEC targets: p95 frame time at most 16.7 ms for 60 fps or 33.3 ms
   for 30 fps; no monotonic memory growth; no audio interruption or preset loss
   on thermal fallback. Inspect native allocation/callback behavior with
   appropriate profiling, and distinguish GPU rendering from UI frame metrics.
8. **Synchronization and visual review:** calibrated local reaction within one
   30 fps frame; separately report Bluetooth. Review silence, sparse percussion,
   dense electronic music, speech, noisy microphone, denied/stopped capture and
   source switches. Compare matching settings with the seven reference apps
   only after recording their installed version, device and test conditions.

Preserve stable scene/preset identifiers, saved parameters and migration paths;
existing band gains, touch, parameter interpolation and continuous animation;
mono/stereo downmix; per-frame scene fanout; safety clamps; and bounded producer
work. No fix may turn lossless offline processing into live frame dropping, or
make the audio callback wait for scene upload/rendering. Store output captures
with device, build SHA, input fixture, settings and timestamps so visual quality
claims are reviewable and reproducible.
