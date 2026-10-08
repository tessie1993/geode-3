# Geode UI 2.0 implementation blueprint

Prepared 8 October 2026. This file describes the current UI 2.0 candidate and its remaining verification gates. The companion [wireframes](WIREFRAMES.html) and [vector sheet](WIREFRAMES.svg) are source-aligned structural diagrams of the implemented composition. Neither the diagrams nor this document establish final GPU appearance or device performance.

The current implementation is integrated against the latest inspected main,
`fa75ced697c45ba091b552200c40f78e9ad92392`. Main's native camera, PCM and fluid
repairs remain the functional baseline; UI 2.0 owns presentation and a separate
environment renderer. [Main integration](MAIN_INTEGRATION.md) and
[validation status](VALIDATION.md) distinguish this candidate's pending Actions
gates from earlier-base reports. Compilation, tests and lint run in GitHub
Actions only, as required by the repository plan.

## Product direction

Geode's default presentation uses a naturally illuminated lake with a mineral center. The geode anchors Player. Opening navigation presents the central Player target and labelled optical spheres for Library, Visuals, Studio and Settings; Studio visibility follows the existing listening/creating preference. Water, mineral geometry, sphere refraction and orbit-gated filaments are drawn by one scoped world renderer. Native controls supply touch and focus feedback. Existing audio playback, C++ visualizers and exported output retain their independent responsibilities.

Native text and normal Android controls sit on pale, quiet reading surfaces. Depth comes from coherent perspective, volume, illumination, reflection and occlusion. Large black capsules, stacked bottom docks and bitmap button skins are removed from the new default presentation.

## Wireframe contract

The sheet uses a 390 × 844 logical phone viewport, with explicit status and gesture insets. These dimensions explain composition; production layouts use window constraints, insets, typography metrics and content. The orbit uses fixed offsets within its constrained region and switches to a scrollable native route list on small windows or larger font scales. Navigation targets use a 48 dp minimum; the complete destination control and accessibility audit remains a verification gate.

| Destination | Primary hierarchy | Persistent anchor | Next detail or overlay |
| --- | --- | --- | --- |
| Player | Lake / tappable geode reserve → metadata → seek and transport → queue, lyrics, more | World geode plus a labelled navigation handle; no mini player on the single-pane Player page | Orbit navigation, expanded Live/playback, queue, lyrics, track actions, source selection |
| Library | Title → Tracks / Albums / Artists / Folders / Playlists → search → one accessible sort menu → collection or track list | Compact Player strip with a geode navigation handle | Collection drill-down, track menu, playlist editor, folder import |
| Visuals | Title → one 132 dp current visualizer preview → View live → Presets / Styles / Customize / Textures / Takes → contextual content | Compact Player strip with a geode navigation handle | Preset import/save/folders/templates, parameter tools, texture workflows and takes; clear-menu mode replaces the preview with one full backdrop host |
| Studio | Title → Timeline / Open video toolbar → existing clip library with real media thumbnails and clip actions | Compact Player strip with a geode navigation handle | Selecting a clip opens a separate editor with actual playback preview, trim/look/export controls; Timeline opens the separate timeline editor and its tool sheets |
| Settings | Title → seven category rows → nested settings page | Compact Player strip with a geode navigation handle | Look, Audio, Export, Folders, Behavior, Help, About |

Wireframe media names are example content. They are not filenames to ship or literal content to hardcode in the app. Lists, tabs and screen titles bind the existing state and resource strings. Some secondary controls appear as summary rows in the diagram; the parity table below determines their actual implementation scope.

## Navigation and overlay hierarchy

Keep the existing stable destination identities: `PLAYER`, `LIBRARY`, `VISUALS`, `STUDIO`, `SETTINGS`. Persist destination names, never ordinals. Destination definitions are the only source for labels, icons, active semantics and entry visibility. Respect the current listening/creating preference for Studio visibility; Studio remains explicitly reachable through the relevant settings preference.

The Player hero and footer navigation handle open an orbit overlay. Orbit entry targets stay stationary during selection; no precision gesture is required. Opening the orbit removes destination content and its compact anchor until the overlay closes. On content pages, the geode navigation control is attached to the compact Player strip. No second navigation dock is stacked beneath it. At widths of at least 900 dp, Library retains the existing two-pane layout with a Player pane; its compact anchor remains present, so this layout is not a strip-to-Player merge.

| Trigger | State change | Result |
| --- | --- | --- |
| Tap Library / Visuals / Studio / Settings orbit node | Set destination immediately; close orbit | Selected native destination is composed; its saveable state restores |
| Tap the Player page's hero geode | Open orbit | Destination content is replaced by the native navigation overlay; the world settles toward the measured orbit anchors |
| Tap the central Player target in orbit | Select Player; close orbit | Native Player content is composed immediately; playback continues |
| Tap compact strip geode handle | Open orbit navigation overlay | Four labelled destinations and a clear Player target become available |
| Tap compact strip metadata | Select Player | Player replaces the content page immediately; the strip is removed, without a merge animation |
| Tap compact strip play/pause | Existing playback intent only | Current destination stays visible |
| Tap Visuals View live or Player metadata | Set expanded presentation; close orbit | Existing full-screen VisualizerScreen owns the foreground; Back restores the destination and its saveable state |
| Tap queue / lyrics / more | Open the existing Player panel or dialog | Existing handlers and local panel state own the presentation |
| Open settings category | Set the saveable category index | Back clears the category and returns to the category list |
| Open collection or Studio clip/timeline | Set destination-local detail state | Existing local Back/close handlers return to the list or editor |

The current root's draw and input layers, ordered from bottom to top:

1. System window background and safe fallback color.
2. Scoped UI world renderer, when it owns the visible scene.
3. Native destination surface, title, lists, fields, tabs and controls.
4. One compact Player anchor on content destinations, outside scroll content and clear of system gestures.
5. Orbit overlay, replacing destination content and the anchor, with local reading surfaces, a dismiss target and a focused semantic destination group.
6. Global search or crash dialog when their explicit root state is active.
7. Expanded VisualizerScreen when expanded state is active; its existing panels own their native presentation.
8. Safety consent, first-run, tutorial or boot presentation according to the existing root conditions. Android pickers and permissions use their existing result APIs.

Search, expansion and route selection close orbit through GeodeAppState. Destination content is also removed during search and expanded Live, preventing an additional native preview host. Queue, lyrics, import, settings and export dialogs retain their existing local state and Android dialog windows; this candidate does not introduce one typed global modal owner. Their focus and renderer visibility are part of the remaining interaction audit.

Existing dialogs and detail screens retain their local Back handlers. The shell dismisses orbit or returns through saved destination history, with Player as the fallback. Root predictive Back sends progress to the world pose only when the world is active; native destination content stays in place until the gesture commits. Cancellation clears that progress without changing the destination. Some existing native detail headers have their own predictive dismiss feedback; there is no shared outgoing sheet/world animation. Selection commits immediately and never waits for animation.

The shell saves destination names, history, orbit/search/expanded state and each destination's saveable state. Existing Library, Visuals, Studio and Settings models remain their data owners. Native saveable controls retain tabs, supported query/detail state and remembered scroll state; this is not a claim that every transient preset selection, dialog or editor tool is persisted. GPU resources, textures and frame clocks are reconstructed after recreation, not serialized. Restoration flows still require exact-candidate Actions/device evidence.

Destination removal for orbit/search/full-screen presentation is paired with the shell's saveable state holder. Album/artist/folder drill-down and expanded playlist IDs are saveable. Studio's unsaved single-clip edit uses an explicit saver for all 19 `ClipEdit` fields, including enum names, nullable ratio/LUT URI, grading, gamma, trim and export choices. The timeline's selected clip/marker/key IDs and zoom are saveable; project history and playhead remain with its controller. Focused saver tests cover complete edit round-trip and malformed-state recovery. Transient menus and tool dialogs are not blanket-persisted.

## Android boundaries

The shell consumes immutable UI state and sends actions to existing ViewModels. Composables do not open audio devices, run analysis, allocate GL resources, encode media or save arbitrary files. ViewModels coordinate use cases; repositories preserve data and playback contracts. Existing Media3/Oboe/native responsibilities remain outside the environmental UI.

| Boundary | Owns | Must not own |
| --- | --- | --- |
| UI shell | Destination/history state, explicit orbit/search/expanded state, insets, shared layout, world visibility | Audio analysis, visualizer buffers, export EGL |
| Destination Composables | Semantic controls and binding to existing state/actions | Per-screen global clocks, duplicated navigation definitions |
| UI 2 component library | Material roles, typography, touch/focus states and native motion tokens | Playback business logic or route-specific persistence |
| UI world bridge | Small bounded frame snapshot, lifecycle, quality selection, visible-state ownership | Native visualizer framebuffers, audio callback mutation |
| UI world renderer | Lake, mineral geometry, optical spheres, light points and filaments with a single scene clock | Text layout, form input, accessibility tree |
| Existing visualizer renderer | Live visualizer output and its established feature ABI | UI world environment or app navigation state |
| Existing export controller | Export jobs, progress, result handling and offline rendering | UI world assets in exported media unless explicitly requested |

A dedicated GLES3 GLSurfaceView hosts the world through Compose interop, with native controls above it. There is no web UI runtime. LakeWorldVisibility yields the world for Visuals, Studio, expanded Live, search, setup/tutorial/crash presentation, a connected second-screen session and running export. Existing visualizer, video and export hosts keep their own owners. The world host also follows lifecycle, attachment and window visibility; Android composition and physical-device performance remain pending verification.

## Environment rendering contract

The production candidate casts a perspective ray for every world pixel. It intersects an actual water plane, raymarches the mineral signed-distance fields and intersects navigation spheres analytically. The spheres refract through entry and exit boundaries. Water is not a fullscreen animated photograph, and the mineral and optical spheres are not painted sprites. Original portrait/landscape mattes supply the far sky/shore and projected lake-bed/near-shore color.

| World element | Production representation | Common drivers |
| --- | --- | --- |
| Sky/distant lake landscape | Separate portrait/landscape matte textures sampled from view direction, with landscape projection calibration | Fixed authored light directions, camera and atmospheric fading |
| Lake | Ray-plane intersection, wave-derived normals, environment/mineral reflection and projected lake-bed color | Clock, bounded beat/transient ripples and mineral reflection |
| Mineral shell/core | 48-step raymarch of an asymmetrical clipped shell with a scooped chamber, ellipsoid base and three separate faceted quartz shard fields; procedural grain, moss, fissures and approximate quartz emission/refraction | Ambient orientation, route/orbit pose and bounded audio energy |
| Navigation lenses | Analytic spherical entry/exit intersections, two-boundary refraction, thickness attenuation and Fresnel environment reflection | Measured native anchors and selected flag; native controls own pressed/focused feedback |
| Contact and reflection | World-coordinate reflection/contact relationship | Exact geode position and same clock |
| Filaments/points | Bounded eight-segment world-space links while orbit is open, plus two procedural light-point/trail paths | Orbit visibility, scene clock and decaying audio impulse; no selection-event particle system |
| Native reading panels | Theme-backed pale surfaces with focus/active state and native text | Semantic material roles; contrast remains a complete-flow verification gate |

The shader approximates reflection/refraction for mobile; software/device captures must verify the result and its cost. The water reflection can trace mineral geometry, but its transmitted contribution is projected lake-bed color, with no ray refraction at the water plane. Quartz uses environment sampling, while the spheres trace the refracted world ray after their exit boundary. Orbit anchors use normalized top-origin centers and short-edge-relative radii measured from native target bounds. The central orbit anchor also sets the mineral's orbit position/size; outside orbit, the shader uses its authored Player/content pose. Shader positions never become the source of input hit testing.

The existing C++ visualizer implementation remains unchanged by UI 2.0. The environment renderer receives finite, clamped scalar copies of the available live feature snapshot through a read-only bridge; no upfront audio analysis is introduced. Receipt age controls freshness, stale envelopes decay, and visibility/lifecycle handoff resets audio state. This bridge does not add analysis or GPU work to an audio callback.

Environmental audio motion uses restrained envelopes: level affects core energy and slight mineral scale, bass affects water normals, and independent beat/transient rising edges feed a bounded decaying water/energy impulse. Continuous onset is retained in the copied snapshot but does not trigger mailbox accents. Treble affects the procedural light paths. Camera drift is ambient; audio does not move text or hit targets. One renderer clock drives the world; native orbit reveal and material interaction transitions use theme profile fields rather than a shared sheet/world clock.

## Renderer ownership and lifecycle

| App presentation | Current UI world behavior | Existing visualizer/video | Verification |
| --- | --- | --- | --- |
| Player spatial home, Library, Settings | Visible with provisional quality caps | No foreground visualizer host on the phone | World clock active only while host is resumed and region visible |
| Orbit overlay | Visible at the balanced cap; destination content is removed | Destination preview host is removed | No second scene or preview loop |
| Visuals owns live preview | Suspended behind foreground preview | Existing visualizer owns preview | Do not draw the world behind a continuously rendered preview |
| Expanded Live / playback visualizer | Suspended | Existing visualizer owns foreground | Handoff before show; restore world only on return |
| Studio clip library/editor/timeline | Suspended for the entire destination, except when orbit replaces it | Existing real thumbnail or preview surfaces | Do not overlap active world and preview GPU work |
| Running export workload | Suspended when ExportRun reports running | Existing export flow owns offline work | UI world does not compete with export; opening an options dialog alone is not a running-export signal |
| Search, setup/tutorial/crash presentation, second-screen session | Suspended through explicit shell flags | Controlled by each existing presentation owner | Verify each handoff in the exact-candidate flow |
| Not resumed, detached or hidden window | Suspended by the world host | Controlled by existing owner/lifecycle | No unseen continuous world rendering |
| Destination-local dialog or OS picker | Local dialogs have no general world-obscured flag; Activity/window lifecycle handles a picker when it changes lifecycle/visibility | Existing owner/lifecycle | Audit opaque-dialog and picker behavior; blanket modal suspension is not implemented |
| Reduced motion | World clock stops, audio envelopes are muted and changed scene snapshots request a static frame | Existing safety preferences still apply | No procedural orbit light/filament animation; native orbit reveal is immediate |
| Unsupported renderer / context failure | Pale static fallback | Existing app functions remain accessible | Navigation and playback survive world failure |

World suspend/resume is idempotent and derived from the explicit visibility flags plus host lifecycle. GL cleanup stays on the GL thread or context destruction; a lost context rebuilds resources. External display, wallpaper and dream services retain their existing lifecycle owners. Their integration behavior, local-dialog visibility and repeated surface restoration remain verification gates.

## Assets and component contracts

UI 2.0 uses original generated environment mattes and code-authored mineral, optics and native identity marks. Marketplace examples inform behavior and material research; they do not grant reuse rights. Generated screen concepts are art targets only. They are not live captures, preset thumbnails, functioning lenses, touch targets or replacements for native text.

| Asset | Current deliverable | Contract |
| --- | --- | --- |
| Dawn/lake matte | Separate Android-ready portrait and landscape environment images | No text/buttons; sky/shore sampling and projected lake/near-shore color only |
| Geode shape/material | Versioned shell/base/quartz signed-distance functions and shading in world_frag.glsl | Asymmetrical mineral silhouette, approximate reflection/refraction and bounded audio energy |
| Lens material | Analytic sphere optics in world_frag.glsl | Entry/exit refraction, attenuation and Fresnel reflection; no baked capsule skin |
| Wet rock/moss detail | Procedural low/high-frequency mineral grain, moss and fissure shading | Restrained surface detail; native reading panels protect text contrast |
| Icons | Existing vector semantics adapted to current destination set | Legible at native scale; selected state not communicated by color alone |
| Ripple/filament/light effects | Versioned shader functions, bounded audio mailbox and orbit-gated paths | Single world clock and explicit audio decay; no touch-event or destination-selection particle input |
| Fallback appearance | Native theme colors and deterministic vector/geode mark | No dependency on GPU/image download for basic navigation |
| Motion/quality configuration | Existing theme motion duration, renderer envelope rates, versioned shader rates and WorldQuality caps | Reduced-motion branch implemented; timing and quality calibration pending hardware evidence |

| Component | Inputs | Actions and states |
| --- | --- | --- |
| World host | Visible owner, scene snapshot, quality, reduced motion | Context ready/error; suspend/resume; no app input semantics |
| Orbit navigation | Visible destination entries, selected destination, world availability and reduced motion | Immediate select/dismiss; native reveal, focus/press/selected feedback; fixed targets or scrollable fallback list |
| Geode handle | Current destination | Open navigation; spoken current context; 48 dp target |
| Compact Player strip | Metadata, progress, playback state and media availability | Open Player, play/pause and open orbit; no artwork or strip-merge animation |
| Reading surface | Semantic material role and content | Contrast-safe background independent of image crop |
| Primary/secondary button | Label/icon, enabled state, interaction source | Native fill transitions use theme press/focus/selected/release duration fields; reduced motion uses immediate state |
| Search field | Query and search scope | Text input, clear, keyboard actions, empty/no-results state |
| Context tabs | Existing tab index, localized label and selection | Saveable selection where supported; existing horizontal overflow/focus |
| Track/preset/clip row | Domain item identity and state | Activate/context actions; stable lazy keys; native truncation |
| Seek/parameter slider | Value, valid range, formatter | Accessible increment/decrement; drag uses existing intent; no UI clock drives audio |
| Panels/dialogs | Existing local state, domain data and content | Existing dismiss/Back and result handlers; no new typed global overlay host |
| Export status surface | Existing phase/result state | Cancel/share/open/retry using existing authorized handlers |

LakeMaterials and the existing theme define shared native material roles and interaction transition durations. Destination screens still contain layout-specific spacing and corner sizes; world optics, settling and phase rates are versioned in the renderer/shader, separate from native reveal/material timing. The world and native controls do not share one calibrated motion clock. Product navigation labels use string resources; diagrams and blueprint samples do not become production content. Physical-device calibration remains pending.

## Feature parity mapping

This table is the migration checklist, based on existing screen/controller code. Moving a control is allowed; dropping its behavior is not part of the clean switch.

| Existing capability/source | UI 2.0 entry | Retained behavior |
| --- | --- | --- |
| `PlayerScreen`, `PlayerPanels`, `PlaybackRepository` | Player metadata/transport and playback expansion | Play/pause/previous/next, seek/waveform, favourite, artwork, source/error/empty states |
| `QueueController`, `QueuePanel` | Player Queue panel | Up next, reorder/remove, queue add/play-next, playlist naming and save |
| `LyricsPanel`, `PlaybackSettings`, `PlaybackFades` | Player Lyrics / More and Audio settings | Lyrics following/offset, shuffle/repeat, A–B loop, sleep timer, fades and playback preferences |
| `CaptureController`, `ExternalAudioSettings`, `AutoVisualsController` | Player source/more and Settings → Audio/Behavior | Microphone/external audio, consent and recovery/error states, automatic visuals |
| `LibraryScreen`, `LibraryViewModel`, `LibraryBrowse` | Library tabs and contextual rows | Tracks/albums/artists/folders/playlists, search/sort, collection play/shuffle, permission/empty states |
| `TrackInfoEditor`, `SmartPlaylistEditor`, `MusicLibraryController` | Track context menu / Playlists / folder controls | Edit metadata, play-next/queue/favourites, smart/manual lists, list import, order/remove/rename/delete |
| `VisualsHub`, `PresetLibraryController`, `BuiltInPresets` | Visuals → Presets | Built-in/user presets, apply, save/replace/share/delete, folders/move/rename, shared links/file import, templates |
| `VisualsHub.StylesTab`, MilkDrop import/link helpers | Visuals → Styles | Native scene families, shader scenes, MilkDrop file/folder import, engine-unavailable state, texture links |
| `CustomizePanel`, `ModulationController`, parameter widgets | Visuals → Customize | Applicable parameter groups, search across groups, randomize/reset, undo/redo, A/B and save as preset |
| `TexturesHubTab`, `TextureController` | Visuals → Textures | Texture import, assignment, missing/link/error state and clear/remove |
| `TakesTab`, `TakeController`, `TemplatesSheet` | Visuals → Takes / templates | Capture/takes, preview/rename/delete, use for export and template configuration |
| `VisualizerScreen`, `SecondScreen` | Visuals preview / Full screen Live | Existing native output, interaction, full-screen behavior, external display handoff |
| `StudioRoute`, `StudioScreen`, `StudioViewModel` | Studio clip-library root → separate clip editor | Video picker, actual thumbnail/preview, rename/delete/share, trim/speed/look/LUT/reframe/mute/caption, result handling |
| `studio/*`, `EditorController` | Studio Timeline action → separate TimelineEditor / tool sheets | Clip lanes, playhead, markers/keyframes/curves, transitions, auto-cut, undo/redo and editor state |
| `ExportHost`, `SettingsDialog`, `ExportController`, `LoopRenderSheet` | Studio/Player export modal | Quality/FPS/codec/aspect/range/loudness/loop options, folder target, progress/cancel/failure/result/share |
| `LookSettingsTab` | Settings → Look | Accent, typography/color/scale and intro remain actionable. Legacy dock opacity, Player position/compactness, corners, background dim and system appearance controls stay stored and visible only with imported packs; the living-lake shell owns its anchor and daylight material |
| `AudioSettingsTab` | Settings → Audio | Playback settings, analysis sensitivity, live input profiles, external audio and EQ |
| `ExportSettingsTab`, `FolderSettingsTab` | Settings → Export / Folders | Export defaults, preset mirror, music imports/rescan, cache clear and destination explanations |
| `BehaviorSettingsTab`, `AutoVisualsSettings` | Settings → Behavior | Listening/creating intent, touch preferences, second display, safety/reduced motion, PiP and wallpaper |
| `HelpSettingsTab`, `AboutSettingsTab` | Settings → Help / About | Replay tutorial, help topics, version/licenses/privacy and attribution |
| `BootIntro`, `FirstRun`, `SafetyConsent`, `TutorialOverlay` | Shell onboarding/overlay owners | Existing setup completion and consent persistence; navigation remains immediately usable after setup |

The implemented appearance migration preserves old preference data for imported packs and hides controls that no longer describe the living-lake shell. A localized explanation describes its single compact Player, geode navigation and scene lighting. `clearVisualsMenu`, intro, text and accent controls remain reachable. This is a deliberate UI change, not a claim that all old appearance controls were ported to the new world.

The lake material branch supplies effective daylight/readability treatment without rewriting the stored legacy preferences. Existing VisualizerScreen chrome also consumes `barOpacity`; its complete contrast behavior remains an audit item. The mapping table records current source entries and retained handlers; exact-candidate Actions and physical-device evidence remain required rather than completed claims.

## Implemented motion

| Event | World response | Native/control response | Constraint |
| --- | --- | --- | --- |
| Ambient idle | Slow water normals, restrained camera drift/mineral orientation, interior shading and two moving light paths; distance-based mist is static shading | Labels and target bounds stay fixed | World clock runs only while active and resumed |
| Tap Player hero or geode navigation handle | Orbit pose settles toward measured anchors; ambient camera drift continues | Orbit opens immediately and its native chrome fades in with the theme duration | No dedicated touch ripple or touch-triggered illumination event |
| Open orbit | Optical spheres and bounded connecting filaments become visible while measured anchors exist | Fixed labelled targets appear, or a scrollable native route list is used for small windows/large fonts | Filaments are orbit-gated; they are not destination-selection events |
| Select destination | World route pose settles when that destination permits the world; Visuals/Studio yield it | Orbit closes and selected native content is composed immediately | No reading-sheet transition; route commit does not wait for settling |
| Open Player from strip | Mineral returns toward the authored Player pose | Player replaces the destination and compact strip immediately | No strip expansion/merge animation; the existing wide Library Player pane remains a separate layout |
| Beat/transient rising edge | Bounded decaying water ring and mineral/light energy accents | No text or target displacement | Independent fresh edges only; held event levels and replayed baselines do not create repeated accents |
| Native press/focus/selection/release | No additional pointer event enters the world renderer | Reading/control fill transitions use the corresponding theme duration | Reduced motion uses immediate fill state; native target geometry stays fixed |
| Root predictive Back | Active world pose follows previous-destination progress; orbit dismissal has no world back destination | Native destination remains until commit; committed content changes immediately | Cancellation clears progress; no shared native-sheet or lens unwind animation |
| Reduced motion | Clock stops, audio-driven accents are muted, filaments/light paths are disabled and changed scene state draws a static frame | Orbit reveal snaps to its final opacity; existing controls retain their reduced-motion behavior | Route state remains immediately usable |

Native reveal uses the theme selected duration; material transitions use theme press/focus/selected/release durations. Renderer settling/envelopes and shader phase rates are authored constants, not device-calibrated shared transition profiles. Current world caps are balanced 540-pixel short edge/60 fps, low 360/30 and throttled 360/15, selected by route, power and thermal conditions. Exact-candidate Actions captures and sustained device recordings must validate visual timing, interruption, thermal behavior and material quality; that calibration is pending.

## Build order and clean switch

1. The five-screen wireframes were produced before implementation and regenerated to match the current source. This blueprint records that composition and remaining gates.
2. The original environment mattes, procedural mineral, analytic optical spheres, perspective water and read-only live audio bridge are implemented in the candidate.
3. The shared native material branch and five destination presentations are implemented. Contrast, 48 dp targets, typography growth, focus and TalkBack still require complete flow evidence.
4. Enum-based destination/history state, saveable destination holders, orbit and compact-anchor navigation are implemented. Existing local dialogs remain their own owners.
5. Existing destination models/actions are retained through the entries above. Function parity is a source mapping until the exact-candidate interaction flows pass.
6. Explicit world handoffs cover Visuals, Studio, expanded Live, search, setup/tutorial/crash, second-screen sessions, running export and host lifecycle. Local opaque dialogs and OS handoffs remain part of the renderer audit.
7. Submit the exact candidate commit to the repository's Actions workflow for all-module Kotlin/unit/static/lint checks, debug and instrumentation builds, native camera/PCM/fluid regressions, UI-tool Python suites and current-world software GLES checks. Do not execute these gates locally. Inspect the workflow's exact APK through its emulator instrumentation/smoke job and retain navigation/interaction evidence.
8. Keep the new default shell as the single candidate presentation path, preserving existing data and non-UI feature owners. Treat merge/release acceptance as pending until parity and renderer gates pass on this commit. Remove old presentation branches/assets only after their references are gone.

The task authorizes implementation and a clean switch; it does not require an additional approval round for routine reversible code changes. The gates ensure that the switch replaces a complete working presentation rather than a partial screenshot treatment.

## Verification gates

| Gate | Evidence required |
| --- | --- |
| Exact-commit integration | Actions debug/instrumentation build, all Kotlin checks, native camera/PCM/fluid and UI-tool regressions pass on the reviewed commit; earlier APK results do not qualify |
| Software world | Actions captures compile/link current production GLSL, audit uniforms and verify non-black/zero-error output, reduced motion and audio in portrait/landscape; report source/asset hashes |
| Spatial material | Device recording shows geode/lens volume, moving highlights, perspective water and matched reflection |
| Navigation | All five destinations reachable; fixed semantic targets; overlay dismissal; rapid switch and Back cancellation settle correctly |
| Restoration | Query/tab/sort/scroll and selected editor state survive switching and recreation where supported |
| Function parity | Each row above has an actual reachable entry and its existing handler; errors/empty/permission states inspected |
| Readability | Native text over all environment crops; large font and contrast mode; no clipped controls or icon-only navigation |
| Accessibility | TalkBack labels/current selection, logical focus order, keyboard/switch access and 48 dp targets |
| Renderer ownership | No continuous world frames during full Live, opaque overlay, Studio preview, export, hidden host or pause |
| Audio continuity | Playback survives navigation, shader/context recreation and overlays; no new audio-thread work |
| Sustained performance | Physical-device frame/memory/thermal evidence at declared quality; quality reduction preserves function |
| Safe fallback | Simulated world initialization failure leaves all native routes and playback usable |

Build success validates source integration. It does not establish AAA visual quality or sustained Android performance. Record any unavailable device evidence and pending material/render checks explicitly in the delivery status.
