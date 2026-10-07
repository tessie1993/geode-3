# Native cluster audit and next correction packages

Status: read-only source audit, 2026-10-07. No local build, test or lint was run.
This supplements `VISUAL_ENGINE_DEEP_SCAN.md` and `VISUAL_STYLE_CAMERA.md`.
Source locations below refer to the inspected working tree; line numbers are
navigation aids, not a promise that later edits retain those positions.

The first delivery gate remains a downloadable APK built and checked by GitHub
Actions. The following changes are proposed, not implemented. Preserve every
existing native style, stable style ID, saved preset and MilkDrop preset. Native
C++ owns visual/camera/simulation logic; the Android shell owns platform APIs.

## Clusters and boundaries

| Cluster | Active owners | Required boundary |
|---|---|---|
| Audio capture and freshness | `engine/audio-core/.../SampleRing.kt`, `MidSideWindow.kt`, `engine/scenes/.../AnalysisEngine.kt` | An input epoch and monotonic sample position identify actual new audio. Polling again cannot create another observation of the same samples. Silence/source suspension and discontinuity are explicit states. |
| Native visual ingress | `core/api/geode_viz_api.cpp`, `core/viz/Params.cpp`, `Renderer.cpp` | Validate complete typed parameter/feature frames before publication. No NaN, infinity or conversion overflow reaches integer conversion, allocation or GL. |
| Frame coordination | `core/viz/RendererFrame.cpp` | One frame captures one coherent feature/PCM/event state, then fans it out to all active transition/layer consumers. A subsequent producer write belongs to a later frame. |
| Scene families | `core/viz/Scene.hpp`, `scenes/ShaderScene.cpp`, `scenes/MilkdropScene.cpp`, `scenes/*Scene.cpp`, `fluid/` | Capability metadata distinguishes planar transform, true spatial camera, simulation and preset-owned movement. Every current family remains supported. |
| Generative spatial state | proposed C++ `CameraDirector`, `TunnelPath`, `SpatialSceneState` | Fresh live intent within composition/velocity/acceleration bounds; scene geometry and camera use the same spatial snapshot. Test entropy is injectable, never the default live path. |
| Quality and safety | `ThermalGovernor.*`, `fluid/FluidQuality.hpp`, `VisualSafety.*`, final composition | Device capability and sustained cost determine quality. Invalid inputs are rejected before this cluster; effect caps are not a general numeric validator. Reduced motion has explicit stationary-camera semantics. |
| Recorded performance | existing offscreen/export path, proposed realized-performance recorder | Preserve the actual composed performance. New offline generation is a new take unless sufficient simulation outcomes have been captured and fidelity proven. |

## Confirmed defects and contract gaps

| Priority | Evidence | Consequence and bounded correction |
|---|---|---|
| P0 numeric ingress | `geode_viz_set_params` checks only pointer/count (`core/api/geode_viz_api.cpp:67–72`). `SceneParams::set` directly assigns floats and calls `lround` for integers (`Params.cpp:288–295`). `Renderer::setFeatures` copies a frame directly (`Renderer.cpp:45–48`). | Malformed imported values can propagate into persistent state and math. Add finite/range/schema validation before any integer conversion or state publication. Reject an invalid full frame atomically and retain the last valid one; return a diagnosable result. Validate named setters, feature frames and other scalar ingress consistently. Kotlin import normalization is a separate cooperating layer, not a substitute. |
| P0 repeated PCM | `RendererFrame.cpp:138–149` reads `pcmCount_` without consuming it. Each active/layer/outgoing scene reads it independently. `Renderer.cpp:73–78` overwrites the pending buffer. | Paused/stalled audio is delivered repeatedly; a producer update between draws can give two scenes different audio within the same frame. Consume once at begin-frame into immutable frame scratch, fan out once per scene, and define bounded overrun/drop reporting. Do not clear it after the first scene and starve other consumers. |
| P0 stale analysis | `SampleRing.kt:69–83` returns the latest window whenever enough samples exist. `MidSideWindow.kt:18–32` has no consumed position. `AnalysisEngine.kt:119–121,183–195` polls that window on a 16 ms schedule. | Unchanged audio is analyzed again as though time advanced through new samples. Add epoch/sample identity, coherent snapshot admission and source-state handling. Reset analyzer/event state at discontinuity and publish bounded silence decay without replaying the old window. Worker lifecycle serialization already exists and must be preserved. |
| P1 projectM feed contract | `MilkdropScene.cpp:171–178` truncates pending PCM to its engine sample limit and otherwise submits `features.waveform` on every update. | Removing renderer duplication alone still leaves stale waveform fallback. Define fresh PCM, explicit source-silence and no-new-data cases; feed supported contiguous chunks and apply a deliberate silence transition rather than resubmitting a cached waveform. Verify against pinned projectM PCM behavior. |
| P1 inactive controls | `ShaderScene.cpp:163,176` uploads zero beat/response; `RendererFrame.cpp:110,242,244` feeds zero composite pulse/hit/flash impulse. Comments say this was intentional in the previous motion change. | UI can expose controls that have no effect. Do not blindly reconnect every shader expression: inventory each consumer, introduce fresh event IDs/envelopes, and restore bounded shape/material response per capability. Flash/shake must not be accidentally re-enabled by a beat fix. Preserve original saved look variants. |
| P1 master control semantics | `MotionField.cpp:170–208`, specifically speed at line 191, modulates speed from energy independently of `motionAmount`; breath/drift/hue have separate amounts. | Motion amount currently cannot truthfully mean all movement off. Publish separate clear controls or implement the new master contract with migration; zero should have an acceptance test for every affected contribution. Do not rename the current slider to a master without changing its contract. |
| P1 repeated live variation | `MotionField::reset` assigns `seedState_ = 0x51ed270b` (`MotionField.cpp:42`); `nextRandom` advances a fixed LCG (`75–78`). | Repeated reset plus equivalent inputs repeats the pseudo-random intent sequence. New native director should receive fresh live-session entropy away from realtime audio callbacks; retain injected fixed entropy only in QA. Never re-seed every frame. Existing fixtures and saved look compatibility remain separate from fresh-session variation. |
| P1 no shared camera | `core/viz/Scene.hpp:18–49` has no camera/spatial snapshot hook; current spatial shader rigs are documented in the camera brief. | A UI orbit knob alone cannot unify ray generation, geometry, collision clearance and transitions. Add an optional capability-based camera contract and upgrade styles additively. MilkDrop retains its own preset movement. |
| P1 reduced motion | `VisualSafety.cpp:34–44` scales selected rates by 0.4; it does not make camera motion stationary or disable every motion mechanism. | The new camera needs a stationary automatic rig under reduced motion; touch still offers deliberate bounded positioning. Prove effects and scene-specific movement separately instead of claiming the existing multiplier fulfils the new contract. |

Numeric validation must distinguish malformed values from valid sentinels such
as `kUnsetOverride = -1` and `kNoPaletteLut = -1`. Stable wire indices cannot be
reordered casually. A versioned shared parameter schema should own names, units,
type, sentinel policy, supported range, family capability and migration; C++ and
Kotlin consume generated or verified matching descriptors. Do not invent a
single generic 0–1 clamp for parameters with different units.

## Constants: remove hidden behavior, retain explicit invariants

“Nothing hardcoded” means eliminate hidden product decisions and duplicated
configuration. It cannot mean replacing all mathematical values, bounded
capacities or platform constants with arbitrary user input.

| Kind | Current example | Treatment |
|---|---|---|
| Adjustable art choice | MotionField orbit radius/ease, drift rate, breath weights (`MotionField.hpp:79–100`); palette hues (`Params.cpp:8–17`) | Move intended artist-facing choices into versioned style profiles with units, defaults and allowed ranges. Expose only meaningful controls; record actual values in a take. Preserve old profiles. |
| Quality budget | Fluid five-tier resolution/particle/iteration table (`fluid/FluidQuality.hpp:16–21`); thermal hysteresis (`ThermalGovernor.hpp:64–69`) | Keep centrally named budgets, chosen from measured capabilities and sustained performance. Test memory/cost per tier on physical devices. Do not let a preset bypass device or thermal limits. |
| Numeric safety | Running-average denominator floor (`MotionField.cpp:14`), bounded `dt`, normalization tolerances | Keep documented, unit-aware algorithm guards. Validate finite input before comparisons; `std::clamp` alone does not sanitize NaN. |
| Compatibility/schema | `SceneParams::kFieldCount`, saved stable IDs, unset sentinels, native API version | Keep explicit and tested. Prefer generated/count-derived tables where feasible so declarations cannot silently drift. Migration is required for changes. |
| Mathematics and protocol | Two-pi, matrix dimensions, ABI ordering, GL enumerants | Keep constants. These are not product configurability defects. |
| User comfort and output constraints | Motion/acceleration caps, final luminance/effect budget | Keep enforced native ceilings. An advanced slider can reduce them, not disable them. Numerical caps alone do not establish full accessibility compliance. |
| Runtime identity | Fixed reset RNG state | Fresh entropy for live sessions; explicit fixed fixture state only in tests. Preserve the realized output for recordings. |

## Ordered correction packages after the APK gate

1. **Admission and schema:** native finite/range guards, atomic frame rejection,
   named-setter parity and Kotlin import normalization; add malformed numeric and
   sentinel regression sources. Preserve every current field and valid preset.
2. **Freshness and PCM:** coherent epoch/sample snapshots; stop reanalyzing stale
   samples; one native frame PCM snapshot for active/layer/outgoing; explicit
   source state; projectM fallback correction. Regression cases: pause, resume,
   seek, rate change, source switch, ring wrap and render/producer cadence skew.
3. **Event and control truth:** publish unique musical-event identity independent
   of held feature levels; duration-based bounded envelopes; per-family response
   capability matrix. Prove a held event cannot fire repeatedly and two nearby
   valid events remain distinct. Keep restored shape response separate from any
   flash/shake decision.
4. **Native generative camera:** `CameraDirector`/`TunnelPath`/`SpatialSceneState`,
   art-profile constraints and fresh live entropy. Upgrade a retained tunnel ID
   through an explicit opt-in look version; add the owner-reference bead/fractal
   corridor as an additive native variant. Preserve all existing styles.
5. **Visual and performance evidence:** GitHub Actions host/Android checks, then
   physical-device music/touch recordings, contact sheets, motion traces,
   sustained GPU/thermal/memory and 16 KB runtime checks. A green compile alone
   cannot establish visual quality or device performance.

Test requirements are proposals. No test source or runtime code was changed by
this read-only audit, and no test outcome is claimed here.
