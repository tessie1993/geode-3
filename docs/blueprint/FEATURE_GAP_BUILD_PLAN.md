# Geode feature gaps and native visual build packages

Checked 7 October 2026 against the source in `codex/fix-android-actions` at
`6b743d8`, existing blueprint/deep scans, and the current official sources below.
This is source inspection and primary-source research, not a hands-on review of
competitor apps or proof of device performance. No local build, test or lint was
run. Tests written during this task are intended for GitHub Actions.

The owner's latest direction controls the plan: keep **every C++ style and
MilkDrop**, add psychedelic spatial tunnels and objects, and make live journeys
fresh and music-driven. C++ owns simulation, geometry, camera and rendering;
GPU shaders perform parallel rendering work. Kotlin remains the Android and
Media3 shell. Existing 2D styles remain available as 2D styles. Do not label
perspective texture warps as a new world-space camera.

## What competing products actually establish

| Primary source inspected | Relevant advertised/documented behavior | Geode consequence |
|---|---|---|
| [Astral Android listing](https://play.google.com/store/apps/details?id=astral.teffexf&hl=en) | Tunnels/fractals/space, visual travel distance and speed, optional gyroscope interaction, silent visuals and separate input choices | Native corridor/object camera controls are a real gap. Preserve immediate exploration without music; add gyro only after bounded camera intent exists. |
| [Fraksl Android listing](https://play.google.com/store/apps/details?id=com.workSPACE.Fraksl&hl=en) | Sequences with per-bookmark hold/transition timing; setups include layers, mappings and modulators; image/video capture | A preset must represent the complete authored look. Geode has pieces of this already, but saved scalar params are not a full scene document or a captured live performance. |
| [Magic Music Visuals 2.5 guide](https://magicmusicvisuals.com/downloads/Magic_UsersGuide.html), playlist and audio-features chapters | Visual playlists, per-entry durations, disabled entries and no-repeat shuffle; normalized audio-to-parameter mappings | Useful desktop behavior, not Android/API parity. Repair our existing journey selection before inventing another playlist. Separate control ranges, mappings and actual feature freshness. |
| [Avee Android listing](https://play.google.com/store/apps/details?id=com.daaw.avee&hl=en) | Editable templates, layers, art assets, audio-response settings and device-dependent export frame rate/aspect/resolution | Geode already has a substantial media export/editor path. Close capture/state/preview gaps before adding more export buttons. |
| [projectM upstream](https://github.com/projectM-visualizer/projectm) | Native MilkDrop-compatible library, reusable independently of app frontends; preset packs maintained separately | Keep the current engine boundary and authored preset motion. Library compatibility does not grant asset-pack redistribution or prove this app's capture/playlist behavior. |

The owner's nine screenshots support bead/particle corridors, curved saturated
contours, nested radial objects, independent backgrounds and fluid ribbons.
They establish visual appearance, not camera velocity, topology, actual GPU
implementation or sustained frame rate. The screenshot-specific mapping is
already in `VISUAL_STYLE_CAMERA.md`; this report does not copy those images into
the public repository.

## Source-confirmed coverage and gaps

| Capability | Actual Geode source evidence | Status and next action |
|---|---|---|
| All retained scene families | `core/viz/SceneRegistry.cpp` owns available IDs; `VisualizerRenderer.availableSceneIds()` exposes them, and `EnginePlumbing.kt` publishes them through `LayersBus.availableScenes` | Present. Reuse the native list for orchestration; never maintain a separate hand-written subset of families. |
| Spatial bead corridor | `rod_tunnel_frag.glsl` raymarches a bent corridor using `lib_dmt.glsl` and shared material functions | Partial, not absent. Preserve geometry/material work; replace repeated shader-time camera travel with native generative state. |
| Distinct 3D objects | `SceneCapabilities.MARCHED_SCENES` lists eight marched styles. Chroma Orb, Bead Vortex and several other appealing names are explicitly 2D constructions | Partial. Improve existing 3D families and add independently silhouetted objects; retain honest capabilities for every 2D style. |
| Native camera director | `Scene.hpp` has no camera contract. `lib_dmt.glsl` computes the path/basis; Rod Tunnel adds fixed-rate roll and periodic offset. `ShaderScene` sends neutral legacy heading/form values | Missing shared native camera state. This is the major new visual package after the audio freshness fix. |
| Automatic visual journeys | `AutoVisualsController` has random, playlist and section staging; `AutoVisualsPrefsStore` persists selection/settings | Partial. Existing random pool omits Fluid, Cymatics, Water and CurlFlow. One retry can repeat. Playlist/section paths ignore Hold. Repair in the bounded slice below. |
| User-selected look pool | `VisualsHub` adds saved presets to a persistent visual playlist; random mode has separate style/preset/MilkDrop selectors | Present. Preserve selected categories and curated playlist rather than silently selecting from everything. A dedicated per-entry disable/duration editor remains absent. |
| Parameter locks and A/B | `CustomizeTabs`, `ParamRandomizer`, `VisualsHub` provide locks, randomization and scalar snapshots | Present but incomplete state semantics; do not create duplicate features. Existing customization deep scan owns complete-document/history work. |
| Modulation | Native LFO/ADSR configs are forwarded by `EnginePlumbing`; `LiveSignal` exposes features | Present. Accuracy/freshness and complete saved mappings remain work; MIDI/network input are optional later expansions. |
| Live look recording | `TakeController` records `PerformanceTake`; its event schema includes scene, scalar params and MilkDrop path | Partial. It does **not** record realized native camera transforms, simulation state, touch, complete shaders/layers or encoded live frames. Generative live video cannot be reconstructed by replaying only these events. |
| Video export | `VideoExporter`, `StudioExporter`, native offscreen scene factories and MediaCodec paths exist | Present with correctness gaps tracked separately. A render rerun is not automatically the captured live performance. |
| Gyroscope/recenter | No active Android rotation-vector camera input or shared recenter intent found in the production paths inspected | Absent. Add optional platform sensor input to the native camera intent after its constraints and lifecycle are verified. |
| Independent background layer | Texture/MilkDrop assets and Studio media layers exist, but native spatial scene background has no common capability/state contract | Partial. Add a background model after camera/scene contracts; do not upload arbitrary unsupported controls to every style. |

## Native-focused build order and acceptance gates

1. **Audio foundation (existing owner).** Consume each live PCM block once,
   expire stale windows/events, and maintain correct timestamp epochs. Confirm
   silence, pause, resume, source change and seek. Camera must not interpret a
   held beat flag or replayed audio as new musical events.
2. **Bounded journey reliability (this lane).** Use the native registry and
   selected categories, shuffle without repetition inside a cycle, retain pool
   changes, honor Hold in every automation path and preserve dwell/cursor.
   This is Android orchestration around the same native renderers, not a new
   rendering engine or a precomputed visual route.
3. **C++ CameraDirector integrated into Rod Tunnel.** Add native session camera
   state, intent/constraint structs and realized per-frame transform. Generate
   short-horizon travel choices from fresh session entropy, current music,
   touch and recent movement history. Integrate velocity with bounded
   acceleration; maintain path clearance and a stable up vector. Upload one
   camera frame to ray generation and depth/sky calculations. Existing 2D and
   MilkDrop paths stay unchanged. Acceptance: actual parallax/occlusion;
   explicit travel/framing controls; no seek/speed-change teleport; no fixed
   repeatable live route; reduced motion disables autonomous travel/roll;
   context recreation retains session state. A helper without an active scene
   consumer is not a finished feature.
4. **Spatial motifs and material quality.** Extend bead corridors into distinct
   pearl/rod/filament formations, then build one sculptural object family with
   recognizable silhouette and orbital reveal. Expose geometry, material,
   background and camera independently through capabilities. Keep shader
   budgets explicit and stable in portrait/landscape. Acceptance: reference
   captures with bloom off; depth readable without excessive brightness;
   original assets/math; frame/thermal evidence on physical hardware.
5. **Complete visual documents and direct manipulation.** Persist camera
   artistic settings, look/source identity, palette, mappings, layers and
   background together; keep runtime entropy separate. Recenter smoothly;
   user touch temporarily steers the native director within the corridor or
   subject bounds. Add opt-in gyroscope with lifecycle-aware Android delivery
   and C++ constraints. Acceptance: save/load/undo and process recreation agree;
   unavailable capabilities are not offered; no unbounded sensor accumulation.
6. **Capture the realized performance.** Encode the actual final live composite
   and aligned audio; store realized camera/transitions for editing where
   supported. Recorded video preserves the result seen by the user. Seekable
   offline regeneration requires explicit simulation snapshots/event coverage,
   not an assumption that the song creates the same journey twice. Acceptance:
   side-by-side captured/export evidence, orientation/cancellation/disk-full
   handling, and all retained scene families including MilkDrop.
7. **Per-entry journey editing.** Extend the existing playlist with stable
   entry IDs, reorder/disable, hold duration and transition overrides; keep
   global defaults and native scene capabilities. This follows complete state
   persistence so playlists do not become fragile pointers to incomplete looks.

Every package builds and tests in GitHub Actions. Native math tests cover
finite values, frame-rate changes, stalled frames, constraint bounds and camera
state transitions. Emulator evidence covers the actual controls and saved
state. Physical arm64 device evidence is required for sustained visual quality,
thermal behavior, audio/video alignment and export throughput. Do not infer
those from a passing host unit test or an emulator screenshot.

## Additional open-source code worth studying

| Primary repository/documentation | Decision |
|---|---|
| [Filament renderer documentation](https://google.github.io/filament/Filament.md.html) and [Apache-2.0 source](https://github.com/google/filament/blob/main/LICENSE) | Study material/linear-light/tone-mapping conventions and reference validation. Do not add a second full renderer during the current GLES/projectM repair. The scene identity and native camera contract should remain renderer-independent. |
| [FastNoiseLite](https://github.com/Auburn/FastNoiseLite), MIT | Possible compact C++/GLSL coherent-noise primitive for bounded organic variation. A fixed preset seed would still produce a repeatable route, so fresh session entropy and stateful constrained planning remain essential. Pin/review exact files before adoption; none imported in this slice. |
| [LearnOpenGL source](https://github.com/JoeyDeVries/LearnOpenGL) | Study camera transforms, instancing and material examples. Desktop samples are not Android lifecycle/extension guarantees. Exact file/asset licensing must be reviewed before any reuse; no files copied. |
| [projectM](https://github.com/projectM-visualizer/projectm) | Continue using the pinned existing native library. Keep its texture/preset provenance separate from the core library license and test transitions/context recovery through our host. |

No speculative social feed, generative-AI feature, radio catalog or network VJ
stack is needed to deliver the requested spatial art. Those are separate
product choices, not blockers for excellent native visuals.

## Allocated implementation slice

Root approved `codex/visual-journeys` in the isolated
`geode-visual-journeys` worktree. Owned source: `AutoVisualsController.kt`, new
`VisualJourneySelection.kt`, and dedicated unit/instrumentation tests. No shared
PlayerSession, shader, native renderer, preset storage, workflow or account
files changed. Build validation remains pending GitHub Actions; this report
must not be used to mark the app or new camera engine complete.
