# Spatial Jelly UI replacement

## Scope and delivery order

Replace the earlier stone visual skins and the first Tidal Glass prototype with a newly authored, living spatial interface. Keep the app's functions, saved theme identities, accessibility semantics and native rendering ownership. This document supersedes the visual direction in `TIDAL_GLASS_UI_PLAN.md`; its feature-preservation and playback-service regression requirements still apply.

The delivery order is reference study, motion plan, new asset and component kit, component motion preview, then app integration. Make the kit and its motion concrete before attaching it to live app screens. Continue within the user's authorized scope; this order does not introduce an additional approval requirement.

The target is an interface situated in a coherent environment: distant atmosphere, a projected water plane, suspended glass volumes, grounded reflections, and effects whose origins belong to those volumes. Typography and ordinary controls must remain easy to read and operate.

## Evidence from the five supplied clips

The reference review inspected 20 extracted frames per clip, sampled every 0.5 seconds from 0.0 through 9.5 seconds, and individual frames where needed. These observations describe sampled visual behavior, not verified continuous playback or the author's rendering implementation. The file stems identify the user-supplied references; do not bundle the reference videos into the app.

| Reference stem | Observed environment and motion | Design consequence |
| --- | --- | --- |
| `484844259_1790423669733611` | Forest creek, ferns, mist and directional sunbeams. At 0–1.5 s a blue volume emerges through the water; at 2–3 s it rises with connected liquid strands and falling drops. At 3.5–5.5 s it changes between round, elongated and upright forms. At 6–7.5 s it settles onto the water. At 8–9.5 s a bell appears and curved luminous paths grow from the same object. | Give the hero a submerged/resting, rising, floating and settling lifecycle. Tie dripping, impact rings, reflection and notification-like accents to its position and phase. Preserve the impression of a thick liquid volume as its silhouette changes. |
| `211752439_1790423660555633` | Seven warm translucent ovals hover over a misty lake. The broad faces turn toward end-on views in a staggered sequence around 1.5–4.5 s. Around 6.5–7.5 s the column rises above the water; the familiar arrangement returns by 8 s. Warm internal streaks, glancing highlights and the lower reflection provide thickness. The landscape framing changes little. | Components should share material and lighting, but have individually staggered elevation and orientation. A contact ripple and reflection belong below their projected positions. Do not copy the old stone textures simply because the reference forms are oval. |
| `352263071_1790423617832188` | A lake becomes a component stage: large near glass domes, smaller middle controls and distant capsule rows. From about 2.5 s, a central core emits curved trails and light points; tall panels appear at different depths. Foreground forms grow and shift relative to distant forms, and panel overlap changes through 9.5 s. Water reflections remain visibly distorted. | Build depth ordering, coordinated component families, grouped reveals and orbital trails. Differential scale, movement and occlusion are observed; a forward/right camera move is an interpretation of those changes, not a verified camera rig. |
| `25717296_1791401788134479` | A lake object begins dark, glows blue around 0.5–1.5 s and sends branching paths across the water around 2–2.5 s. A suspended blue core appears around 3 s; a ring of small glass nodes forms around 3.5–4.5 s. Connections brighten around 5–6.5 s, and the core changes apparent orientation and shape afterward. | Use an awakening sequence that connects environment, core and surrounding controls. Project energetic paths onto the water; use an organized orbit with meaningful nodes rather than an unrelated confetti field. The sequence is visual inspiration, not a requirement to replace standard navigation with a twelve-item radial menu. |
| `639273179_1790526264108374` | A player layout remains visible over a lake. A suspended glass sphere and distant glass controls establish depth. Around 2.5–3 s album artwork becomes visible inside the sphere; artwork orientation changes while droplets and light points follow curved orbits. The water reflection and surface rings continue beneath it. | Connect the hero to real album art and the existing playback state. Keep artwork, shell, orbit and reflection as separate layers. Keep readable native text and transport controls; do not reproduce distorted reference text as artwork. |

Refraction, thickness, water distortion and trails are visible material cues. Specific shader techniques, physical simulation, spring constants, camera paths and true optical correctness are not established by these frames. The Android implementation must describe its authored art and procedural projection honestly; it must not claim ray-traced glass or a fluid simulation merely because the result looks liquid.

## New asset manifest

All production assets are new local resources. They contain no interface text, labels, album artwork, app icons or complete screen layouts. Runtime content supplies those elements. Keep transparent assets transparent, with clean alpha edges and no baked rectangular background. Record creation source, dimensions, alpha status and compressed size with the delivery.

| Production filename | Required content | Runtime responsibility |
| --- | --- | --- |
| `spatial_lake_atmosphere.webp` | A neutral blue-grey natural environment with a usable distant horizon, soft atmosphere and a quiet central area. No UI, floating object or baked light trail. | Theme lighting, slow atmospheric movement, projected water and scene depth. One shared authored scene supports eleven fresh lighting treatments. |
| `spatial_glass_orb_shell.webp` | Transparent thick clear glass shell, coherent rim highlights, visible inner depth and a quiet area for live artwork. | Awakening, elevation, liquid silhouette treatment, inner album-art presentation, rim light, orbital depth and reflection. |
| `spatial_glass_capsule.webp` | Transparent rounded glass volume with a believable thick lower edge and controlled top reflection. No label or icon. | New chips, tabs, row accents and button material; theme tint, interaction light, compression and stable text matte. |
| `spatial_glass_pebble.webp` | Transparent rounded lens/control volume with curved highlights and depth. This is a new glass control resource, not reuse of the old mineral texture. | Circular transport, navigation and knob/thumb material. Existing icons and semantics remain native. |
| `spatial_foreground_ferns.webp` (optional) | Transparent separated near foliage with unobstructed central content area. | A low-amplitude foreground parallax plane. Omit if it obscures controls or exceeds the decoded-memory budget. |

Water, mist, caustic accents, droplets, orbital paths, contact rings, shadows and text mattes are procedural layers. A still environment cannot be the entire depth system. Where a turn exposes another face, use an authored view or a shaded projected form that retains volume; stretching a front-facing picture alone is not sufficient evidence of a volumetric turn.

## Scene and component kit

Use one scene state and one bounded clock for the decorative Compose stage. Separate the far atmosphere, middle water, rear orbit, glass objects, front orbit, near scenery and readable UI planes. Project world-like positions consistently: near forms move and scale more than distant forms; reflections use the object's water-plane position, height and depth. Sort orbital samples by depth so their paths can pass behind and in front of the decorative hero. Do not draw front trails across reading areas or action labels.

The authored lake's source horizon is approximately 51% down the image. The runtime projects that distance into the upper 58% of the viewport and places the world water horizon near 29%, so the hero's contact rings land on water. A separate crop of the actual source water fills the near plane; a bounded overlap fade joins the two planes. The near water, distant image and foreground ferns move at different amplitudes. These are authored/procedural 2.5D layers, without a new renderer or a physical fluid simulation.

| Kit element | Material/motion requirements | Existing app use |
| --- | --- | --- |
| Spatial environment | Horizon stays coherent; water has perspective and moving highlights; mist and near scenery use different depth factors. | Shared scenic background in Player, Library, Visuals, Studio and Settings where native render surfaces do not occupy that space. |
| Hero volume | Resting/submerged start, rise, optional connected drops, suspended idle, coherent settle and reflection. Album art is a separate layer inside the shell. | Player artwork and decorative live energy display. Observe the existing signal supplier; do not create another analyzer or player. |
| Lens control | Thick rim, curved highlight, inner depth, localized touch light, compression and a restrained spring return. | Play/pause, previous/next, shuffle/repeat, navigation and small actions. |
| Capsule control | A shared physical material with less reflection behind text; stable label, selected edge light and explicit disabled/focus states. | Tabs, chips, quick actions, search and filtering controls. |
| Floating panel | Readable matte content area with an authored glass perimeter, contact shadow and small settle on appearance. | Cards, list rows, sheets, dialogs, mini-player and appearance controls. |
| Orbital connections | Curved paths anchored to the hero/active group; particles follow those paths with depth sorting and finite lifetimes. | Hero awakening and selected visual accents. Use few paths, rather than a separate competing animation on every row. |
| Projected water response | Elliptical rings belong to the water plane; droplets hit at the corresponding point; reflection changes with object height. | Hero rise/settle and bounded interaction accents. Standard control ripple remains clipped to its own material. |

Author a component preview showing default, focused, pressed, selected and disabled states at normal and large font sizes. Show an awakening/rise/settle sequence, an orbital depth pass, one panel reveal, and a control press with its linked ripple. Review this kit before integrating it into all five destinations. The preview is evidence of the new components; screen integration must use those components rather than a flattened preview image.

The debug-only component activity uses the production background, hero, capsule and lens controls, with real native labels and callbacks. Its Awaken action recreates the same bounded scene clock to record the opening rise, then the review presses both control families and changes selection. CI records each theme at a stated ordinary phone viewport and restores the original device configuration before reviewing MainActivity. Capture metadata records action times and whether they fall inside the video; it never presents an action after the recording ends as visible motion evidence.

## Motion lifecycle

Awakening is a short, coherent sequence: quiet water responds, the hero rises, its contact reflection separates, drops finish and orbital paths assemble around it. Idle motion then uses gentle elevation, changing reflections and controlled orbital travel. A changed track updates real artwork through a short material reveal without blocking playback. Destination changes settle the incoming Compose content; do not retain a second outgoing native scene for the sake of a transition.

Use the existing control `InteractionSource` for pressure, focus and ripple origins. Press light, deformation, a few droplets and release settle are part of one response. Do not add a gesture interceptor over seek bars, lists, editor lanes, scene touch or accessibility actions. Keep ordinary action execution immediate; decorative animation never delays a callback, permission request, navigation or export cancellation.

Particles belong to specific paths or impact events. Preserve their relationship to the emitting component during scene movement. Ambient mist/light points may use a deterministic seed; avoid regenerating random positions every frame. Do not attach a perpetual frame loop to every card, row or button.

## Eleven fresh theme versions

Keep the exact saved slugs, display names and catalog order. All eleven built-in choices use the newly authored glass kit and scene. Each receives a fresh palette, material light and environmental treatment; none uses an old stone tile as its active visual skin. The treatments below are art direction, not measured physical material properties.

| Existing slug / name | New glass and lighting | Environment treatment |
| --- | --- | --- |
| `tidal-glass` / Tidal Glass | Clear aqua depth, pearl rim and a small warm reflected highlight. | Blue-grey creek/lake atmosphere, cool mist and cyan water accents. |
| `lapis-lazuli` / Lapis Lazuli | Deep blue clear glass, ice-blue edges and restrained gold light. | Dusk water with a darker blue distance and warm horizon reflection. |
| `sugilite` / Sugilite | Plum transparent glass, lilac rim and soft rose secondary light. | Violet evening haze with quiet pink water reflections. |
| `amethyst` / Amethyst | Clear lavender depth, pale violet edges and cool white light. | Lavender-blue dawn mist; brighter distance than Sugilite. |
| `clear-quartz` / Clear Quartz | Nearly colorless glass, pearl rim, dark readable labels and a light matte. | Soft daylight, silver water and low-saturation atmosphere. Retain the light-theme identity. |
| `azurite` / Azurite | Saturated azure depth, turquoise rim and cool silver secondary light. | Blue hour with crisp cyan contact reflections. |
| `firestone` / Firestone | Warm amber/red transparent glass with pale gold edge light. | Sunset warmth, dark calm water and controlled copper reflection. |
| `kyanite` / Kyanite | Cool steel-blue glass, icy rim and restrained white highlights. | Cold morning haze and desaturated blue water. |
| `malachite` / Malachite | Transparent jade depth, pale mint rim and warm natural highlights. | Green woodland lighting and quiet teal water. |
| `mookaite` / Mookaite | Honey/cream glass, muted rose secondary light and warm pearl edges. | Sand-coloured dawn haze with amber reflections. |
| `onyx` / Onyx | Smoke-clear dark glass, pearl rim and a small champagne accent. | Night water with strong silhouette separation and restrained warm light. |

Use theme palette values to drive the new scene lighting, mattes, rim intensity and effects. Preserve user dim, opacity, font, text-scale, corner and reduced-motion settings. Do not recolor real album artwork or native visualizer output. Typography contrast and selected/disabled recognition take priority over transparency.

## Function and tooling contracts

Keep Player, Library, Visuals, Studio and Settings, all existing sections/tabs and user-intent filtering. Keep transport, queue, lyrics, favourites, seek, shuffle/repeat, A/B loop, automatic visuals, capture sources, microphone/projection permissions, sleep timer and listening history. Keep search, notices, onboarding, crash recovery, fullscreen/PiP, second-screen presentation, saved destination, adaptive rail/two-pane layout and compact/top/bottom mini-player modes.

Keep Studio editing, clip/take workflows, undo, import/export, export progress/cancel, destination selection, SAF and sharing. Keep Visuals presets/templates/imports, locks, modulation, GLSL and renderer error handling. Invoke existing callbacks through the new material components; do not replace functional content with decorative stand-ins.

Preserve `ThemePack`, `StoneComponent`, `StoneState`, palette/motion preferences and resource lookup contracts even though the display material changes. The eighteen component keys continue to provide all five states. Names containing `Stone` are compatibility identifiers, not a requirement to retain the stone appearance.

Keep `ThemePackCatalog.all` in this order: `tidalGlass`, `lapisLazuli`, `sugilite`, `amethyst`, `clearQuartz`, `azurite`, `firestone`, `kyanite`, `malachite`, `mookaite`, `onyx`. Preserve multiline catalog recognition in `tools/check_style_collection.py` and the `tools/import-theme-pack.sh` contract: native Tidal registration, the required Kyanite source pack and reserved built-in slug protection. Do not remove old compatibility resources or imported-pack support simply to rename the visual layer. Replacing built-in rendering must not force imported third-party packs into an incomplete built-in kit.

## Native surface boundary

The native visualizer remains in its existing `VisualizerView`/`GLSurfaceView` host with its current renderer, capture source, feature bus and lifecycle ownership. Do not wrap that view, its `AndroidView`, or an ancestor containing it in a perspective transform, rotation, animated scale, blur, offscreen compositing layer or screen-transition effect. Do not instantiate a second GL host to render a spatial UI background.

Apply spatial transforms to decorative Compose material, artwork and controls only. Preserve the native surface's bounds, input coordinates, underlay/overlay ordering, fullscreen and PiP behavior. During fullscreen, search and native scene occupation, pause or suppress scenic decorations that would compete with that content. UI effects must not enter exported frames or change DSP, audio-rate/capture ownership, native visualizer frame pacing or project geometry.

## Bounded runtime budget and accessibility

The following are implementation targets, not performance results:

- One resumed decorative clock, capped at 30 Hz; read phase in drawing/layer lambdas rather than recomposing whole screens. Interaction springs may use normal Compose animation pacing while active.
- Cap ambient points at 18, active impact drops at 8, ripple fronts at 3 per current interaction, and orbital paths at 3. Bound each trail's samples and particle lifetimes; do not accumulate arrays or launch unbounded jobs on repeated taps.
- Keep the new compressed asset set below 12 MiB and the active decoded decorative bitmap cache below 24 MiB. Decode only the active scene/kit at the required display size; do not preload eleven separate environments. Record actual sizes and adjust quality if these targets are exceeded.
- Cache stable paths, brushes and bitmap handles. No full-screen render-to-bitmap or blur allocation per frame; no frame-time file, network or resource decoding.
- Activity backgrounding, hidden content and fullscreen native scene occupation stop decorative clocks and particle updates. Return from the background resumes a bounded phase without emitting a backlog of drops or replaying a long awakening unexpectedly.
- App Reduced Motion and disabled system animations produce a readable, composed still state. Skip awakening, camera/parallax travel, morphing, orbit particles and animated ripples; retain static focus, selected and pressed recognition. Never hide content pending an animation that will not run.
- Labels, live track data and editing content sit on stable theme-appropriate mattes. Large fonts can grow/wrap controls. Preserve at least 48 dp touch targets, role/selected/disabled semantics, screen-reader labels and visible keyboard focus.

## Implementation and verification stages

1. **Reference and plan:** retain the sampled-motion distinctions above, the functional inventory and the native surface boundary. Confirm that every planned effect has a scene/component owner and an off/reduced state.
2. **Assets and component kit:** create and inspect the new manifest assets. Build material components and procedural depth/water/orbit layers in isolation. Produce actual component-state and motion previews before screen integration.
3. **Theme treatment:** give all eleven built-in themes their new lighting/matte palettes through the same kit. Verify the light Clear Quartz version separately. Retain slug persistence, imported-pack behavior and all state-art contracts.
4. **App integration:** replace visual skins behind existing controls and callbacks across every destination. Connect hero artwork and existing live feature data; leave native rendering and functional ownership intact. Preserve content while animations settle.
5. **CI validation and review:** use the existing GitHub Actions Android workflow for builds, Kotlin/native tests, lint, style checks, instrumentation and emulator smoke checks. Repository policy keeps builds, tests, lint and formatting checks in CI; do not run local Gradle/CMake builds or substitute local test results.

CI evidence must cover all five destinations, Library and Visuals tabs, real indexed audio playback beyond the foreground-service startup deadline, transport/seek/shuffle/repeat, search, saved theme switching across all eleven choices, reduced motion including process restart, large fonts and landscape. Exercise Studio and native visualizer reachability without changing their export/capture contracts. Review the existing foreground-service regression test rather than removing it to make the UI suite pass.

Inspect actual emulator PNGs, UI hierarchies, motion recordings, crash logs and available resource snapshots. For the new spatial kit, review emergence/settle, layered orbital occlusion, projected water/contact reflection, press response and steady text in motion. Review native scene/fullscreen/PiP transitions for surface clipping, coordinate changes, duplicated owners and black frames. Only report smoothness or resource improvements when traces actually support those claims; emulator screenshots alone do not establish physical-device frame rate.

## Primary design references

These pages informed motion/material principles. They are references, not app dependencies or asset/code sources to copy. Marketplace descriptions were inspected; external videos were not verified through playback. The supplied five clips are the stronger evidence for the requested environment and choreography.

- [Glass bubbles — KLSG.Design, Dribbble](https://dribbble.com/shots/27320341-Glass-bubbles): suspended translucent volumes with delicate reflections/refractions; useful for material weight and depth, not for its grey background.
- [Spatial Music Player — Ali Asadi, Dribbble](https://dribbble.com/shots/25905556--Spatial-Music-Player-UI-Designed-for-Apple-Vision-Pro): layered floating media panels and a clear now-playing hierarchy.
- [VisionOS Liquid Glass interactive UI — Kiarash Amalivand for Dmazing, Dribbble](https://dribbble.com/shots/26294686-VisionOS-Liquid-Glass-interactive-UI-Apple-inspired-3D-Motion): fluid micro-interactions, depth and physics-informed behavior.
- [Orbit Cards — Michael Slotta, Framer](https://www.framer.com/marketplace/components/orbit-cards/): coordinated centered arrangements, idle floating and perspective/parallax shifts.
- [Animated Particle — Sobakhul Munir Siroj, Framer](https://www.framer.com/marketplace/components/animated-particle/): drift, wave and swirl treatments with reduced-motion controls; informs deterministic ambient points and organized orbital travel.
- [Edge Glow — Fabian Albert, Framer](https://www.framer.com/marketplace/components/edge-glow/): localized edge light and controlled shine; adapted to native press, focus and selected states rather than introducing web hover tracking.
- [Layer Stack — Marco Ponce de León, Framer](https://www.framer.com/marketplace/components/layer-stack/): assembled glass layers, staggered float and tunable tilt/spacing.
- [Liquid Glass Refract — Artem Kireev, Framer](https://www.framer.com/marketplace/components/liquid-glass-refract/): flowing distortion, thick edge cues, dispersion, sheen and tap rings; includes reduced-motion/offscreen considerations.
- [Liquid Glass Buttons — Matt, Framer](https://www.framer.com/marketplace/components/liquid-glass-buttons/): coordinated reflection, tilt/lift and press compression with preserved interaction states.
- [Depth Card Flow — Can Girgin, Framer](https://www.framer.com/community/marketplace/components/depth-card-flow/): independent material/content/shadow depth and controlled transition choices.

Translate those principles into existing Android/Compose capabilities. Do not install a Framer/WebGL SDK, import a web component into the app, purchase a component as a substitute for implementation, or add another native GL layer.
