# Geode experience and visual design

**Status: proposed design specification, 7 October 2026.** These screens, scene
briefs and tokens are implementation targets. They are not screenshots of a
completed app or evidence of a device test. Use this with [FEATURE_SPEC.md](FEATURE_SPEC.md)
and [ARCHITECTURE.md](ARCHITECTURE.md); record shipped coverage separately.

The latest owner direction preserves **all existing C++ styles and MilkDrop**.
New 3D tunnels, morphing objects and camera movement are native C++ additions.
Live motion is fresh and generative. The supplied screenshot mappings and more
detailed rigs in [VISUAL_STYLE_CAMERA.md](VISUAL_STYLE_CAMERA.md) refine this
initial art direction and govern any conflicting older wording.

## 1. Product direction

Geode should feel like a precise instrument surrounding a living image. Give the
visual canvas most of the space. Keep navigation, track metadata and editing
controls quiet and readable. The distinguishing material is mineral light:
translucent facets, fine luminous filaments, viscous colour and deep spatial
layers. This is an original art direction, not a reproduction of the reference
apps' layouts, artwork or preset packs.

The first minute must demonstrate three things: the image responds immediately,
playing music is straightforward, and changing a look is reversible. Advanced
mapping and timeline tools remain available without dominating that first use.
Avoid decorative dashboards, animated wallpaper behind text, fabricated audio
meters and decorative knobs that do not change the result.

## 2. Design system

### Colour and material tokens

These are initial token values. Verify rendered contrast in CI and on devices
before approval; a hex palette alone does not certify accessibility.

| Token | Dark | Light | Intended role |
|---|---|---|---|
| `canvas` | `#080B10` | `#F3F4F6` | Main app background |
| `surface` | `#121822` | `#FFFFFF` | Lists, sheets and panels |
| `surfaceRaised` | `#1D2633` | `#E6EAF0` | Selected groups and floating controls |
| `textPrimary` | `#F3F6FA` | `#141B27` | Track titles, headings, primary labels |
| `textSecondary` | `#BAC5D3` | `#465569` | Supporting text and metadata |
| `accent` | `#B7F4D8` | `#14563F` | Primary action, active parameter and selection |
| `onAccent` | `#10271D` | `#FFFFFF` | Text on accent-filled buttons |
| `focus` | `#B9C7FF` | `#3445AC` | Keyboard/switch focus outline |
| `warning` | `#FFD38D` | `#704500` | Interrupted permission, unavailable asset |
| `error` | `#FFB4AD` | `#A22429` | Failed operation with recovery action |
| `outline` | `#68788B` | `#748196` | Input bounds and meaningful separators |

Use solid surfaces beneath important text. A scrim may protect player controls
over a scene, but its minimum opacity is chosen from worst-case bright imagery.
Do not assume a blurred panel remains readable. Accent colour indicates state,
not the loudness of the music. Scene palettes and the interface palette are
independent; sampling album art never changes error, selection or focus semantics.

### Type, spacing and components

| Area | Specification |
|---|---|
| Typeface | Android system sans; no bundled display font needed. Use tabular figures for time and parameter values. |
| Type scale | Display 32/38 sp, title 24/30 sp, section 20/26 sp, body 16/24 sp, label 14/20 sp, metadata 12/16 sp. Respect font scaling; metadata never carries the only actionable instruction. |
| Weights | Normal 400, emphasis 500, key action 600. Avoid all-caps paragraphs and light-weight small text. |
| Spacing | 4 dp base; use 8, 12, 16, 24, 32 and 48 dp steps. Phone content gutters 20 dp; wider panes 24 dp. |
| Shape | Controls 12 dp, cards 16 dp, sheets 24 dp top corners; no nested rounded card around every row. |
| Targets | At least 48 × 48 dp; play/pause 64 dp. Timeline handles may look narrow but have separate accessible hit areas. |
| Icons | Consistent 24 dp stroke family, accompanied by text for unfamiliar actions. Selected navigation keeps a label. |
| Buttons | One filled primary action per dialog/sheet; tonal secondary; destructive actions explicitly named. Disable with a stated reason rather than silent no-op. |
| Sliders | Label, current value, reset, accessibility range and optional numeric entry. Do not require a circular drag to change a value. |
| Feedback | Inline field errors; persistent job/error card for long operations; brief snackbar with Undo for reversible edits. |
| Artwork | Square album art in lists/player; 16:10 preset thumbnails captured from representative frames and identified as previews; the live journey stays fresh. |

### Motion and haptics

Use 120 ms for press/selection feedback, 180 ms for panel content changes and
240 ms for sheet/navigation transitions. Prefer gentle ease-out with no spring
overshoot around precision controls. Scene transitions start at 600 ms and may
follow musical structure, but must not delay an explicit transport action.

Reduced-motion mode removes UI travel and camera roll, limits scene velocity,
uses short opacity transitions and suppresses flash/shake behavior. Also respect
the platform animation setting. Haptics are optional, brief and tied to discrete
events such as saving or snapping a clip; never vibrate continuously to the beat.

## 3. Navigation and adaptive layout

Primary destinations are **Listen**, **Explore** and **Studio**. Customize belongs
to the selected visual and opens from Explore or the immersive canvas. Account,
premium, safety, audio and help live in Settings. Show a persistent mini-player
above primary navigation when a playback queue exists.

| Window | Layout rule |
|---|---|
| Compact, below 600 dp width | Bottom navigation; one primary pane; now-playing and customization as full-height routes/sheets with saved state |
| Medium, 600–839 dp | Navigation rail; list plus detail where height permits; Explore canvas with collapsible inspector |
| Expanded, 840 dp and above | Navigation rail; library/catalog pane, main canvas/detail and optional 320 dp inspector; constrain reading lines rather than stretching text |
| Short landscape window | Prioritize canvas/transport; open controls as side panel; avoid stacked full-height bottom sheets |
| Foldable | Respect hinge/occlusion and current window bounds; keep important controls on one pane; no fixed device-orientation assumptions |

Window changes preserve the selected track, scene, editor selection and unsaved
edits. Back first exits transient editing/fullscreen, then returns through
navigation. It does not implicitly stop playback or erase a project. Predictive
Back must preview the destination, including a pending unsaved-change decision.

## 4. Screens and states

| Surface | Composition and main actions | Required states and recovery |
|---|---|---|
| Welcome | Short product statement, live silent scene, **Play my music**, **Explore visuals**, visible reduced-motion switch | No account/permission wall; device cannot run chosen demo → compatible scene; never pretend silent demo drive comes from a microphone |
| Listen library | Search, Tracks/Albums/Artists/Folders/Playlists filters, recent items, list with clear overflow actions | First scan with incremental results; empty → import; denied media permission → system picker; missing/revoked URI → relink/remove; failed artwork → neutral placeholder |
| Now playing | Large artwork or current visual, title/artist, seek and elapsed/remaining time, play/pause, previous/next, queue, repeat/shuffle | Loading, buffering, paused, ended, unplayable file, external focus loss; errors name the item and keep a recoverable queue |
| Queue | Current item pinned; reorder with drag and Move up/down actions; clear upcoming with Undo | Stale/missing entries identified; shuffle exposes effective play order; no duplicate stable keys |
| Explore | Hero canvas for selected look; source chip; curated scene collections; favourites/search; **Customize**, **Fullscreen**, **Save look** | Compiling/loading retains last valid frame; unsupported scene → named fallback; empty search; pack import report; no eligible audio → source help |
| Immersive canvas | Scene fills safe display area; transient transport, source and exit controls; optional performance strip | Touch interaction/gesture-cancel; visible mic/projection indicator; accessible Show controls action; quality reduction notice that does not interrupt music |
| Customize | Persistent canvas, five macro controls, palette strip, Advanced sections, Undo/Redo, Compare, Save copy | Dirty/clean state; unsupported advanced controls omitted; failed save retains edits; importing another preset asks how to handle the unsaved look |
| Preset detail | Larger stable preview, creator/licence when supplied, capability labels, Apply/Favourite/Duplicate | Missing textures → exact asset report; premium look preview is identified; unavailable engine never produces a blank preview |
| Studio projects | Recent projects with aspect/duration/modified date, New project, Import, Duplicate | Empty starter choices; missing media count; version-too-new preserves file and explains recovery; interrupted export remains visible |
| Studio editor | Preview, transport/time ruler, visual/video/overlay/audio lanes, selection inspector, undo/redo, Export | Timeline gaps visible; selected clip bounds clear; relink missing media; unsupported content blocks export by name; edits remain available if preview quality falls |
| Export sheet/job | Output destination, aspect/resolution/fps/codec, duration, estimated size, limits before Start; progress + Cancel afterward | Capability fallback before starting; low storage; background job; cancellation; failure reason + retry; success with Open/Share and actual file metadata |
| Premium | Free/premium comparison, live Play price and billing period, monthly/yearly/lifetime choices, Restore and Manage | Loading price, unavailable offer, pending, verification, active, expired, refunded, offline cached state; cancel always returns to prior task |
| Account/backup | Optional Google identity, Drive authorization status, last verified backup, Back up, Restore, Sign out, Delete | Signed out, authorization denied/revoked, offline, conflict preview, relink required, deletion pending/completed; local play always accessible |
| Settings/help | Audio, visuals/safety, accessibility, storage, account/premium, privacy, notices, diagnostics | Settings accurately reflect support; debug diagnostics are opt-in; copying support information excludes identifiers/secrets/audio |

Persist editing intent independently from transient screen loading. In particular,
refreshing Studio's project/clip list must never replace an export's Done, Failed
or Cancelling state with a generic Loading screen.

## 5. Journeys

### First run and first successful listen

1. Open directly into a restrained silent visual with its source labelled.
2. Choose **Play my music**; explain access at the action that needs it and use
   the appropriate system permission/picker. Denial retains Explore and import.
3. Show tracks as discovery progresses. Selecting one starts playback and opens
   the mini-player; a full-library scan is not a prerequisite.
4. Open Now playing, adjust queue, return to Explore, then lock the phone.
   Playback/preferences remain owned by the service.
5. A Bluetooth/headphone change follows the saved behavior. Reopening returns
   to the same queue and look. No sign-in or purchase prompt interrupts this path.

### Explore and make a personal look

1. Choose a source: Local music, Microphone, Device audio or Silent. Each has a
   distinct selected state; microphone/projection consent is requested on demand.
2. Browse six additive signature looks and the full retained C++/MilkDrop
   catalog. Tapping a card previews the selection without restarting music.
3. Drag the canvas to steer; cancel/palm interruption releases the interaction.
   An on-screen equivalent allows a user to steer without a gesture.
4. Open Customize; change macros, choose a palette and use Compare. Randomize
   respects locks and is one undoable configuration change. Live evolution stays
   fresh; undoing a setting does not force a previous camera journey to replay.
5. **Save copy** creates a unique stable preset. Export/share names the preset
   and required assets. The original and imported file are not overwritten.
6. Set as wallpaper through the system preview. Wallpaper has its own selected
   preset and power behavior; it does not silently activate microphone capture.

### Build and export a Studio project

1. Choose Track to video or Empty project, canvas aspect and source assets.
2. Place clips at explicit timeline positions. The preview displays intentional
   silence/empty gaps; moving a clip moves its clip-local keyframes with it.
3. Add a visual look, captions/image overlays and transitions. Inspector changes
   affect the same project evaluator used for the final file.
4. Scrub and preview; unavailable assets/effects show directly on their clips.
5. Open Export, resolve capability/entitlement/storage issues, then start. A
   background job and notification expose progress and cancellation.
6. A completed file offers Open/Share; a failed job retains the project and exact
   recovery action. Cancel cleans incomplete output without deleting the project.

### Premium without disrupting creation

1. Display a premium badge and exact restriction before choosing a gated action.
2. Offer a clear comparison and prices supplied by Play. No preselected trial,
   fake countdown or claim that subscribing is required for local playback.
3. Return from Play to Pending, Verifying or Active as appropriate. A purchase
   callback alone does not imply entitlement is ready.
4. Restore reconciles Play ownership and verification independently of optional
   Google profile sign-in. Manage opens the appropriate Play management surface.
5. If access expires, preserve projects and user data. Explain which new premium
   operations are unavailable; do not corrupt existing edits or interrupt music.

The historical 720p/three-minute free export and watermark are proposed product
limits, not finalized configuration. Approve the actual free tier before wiring
gates, then use the same definitions in the app, backend and store copy.

### Account, backup and deletion

1. Sign in only from an intentional account/backup action. Cancellation remains
   a normal signed-out state. Google profile identity is not a purchase receipt.
2. Ask separately for Drive access when the user first backs up. Preview the
   data included: presets, preferences and project manifests; music/recordings
   are excluded by default.
3. Show backup age and integrity result. Restore compares local/cloud versions,
   offers merge/copy/replace choices and previews missing file permissions.
4. Sign out clears local credentials and stops authorized cloud jobs; local
   tracks/projects remain. Deleting an account presents a separate explicit
   data-removal summary and completion status.
5. Explain the distinction between deleting app/cloud data and managing a Play
   subscription. Provide the applicable external deletion route as well.

## 6. Universal visual controls

Every scene implements the five macro meanings below through a documented
adapter. Advanced controls may vary. A macro is accepted only after minimum,
middle and maximum values visibly change its intended dimension without breaking
safety, timing or a quality budget. Values are normalized 0–1 in saved projects;
the UI may use meaningful labels such as Calm–Driven.

| Macro | Meaning | Required boundaries |
|---|---|---|
| Motion | Camera/object/simulation travel rate | Zero holds intentional travel; audio deformation remains separately controlled; reduced motion constrains the upper range |
| Response | Strength of audio deformation and accents | Zero ignores audio; no inherited hidden pulse; a quiet input never masquerades as a loud one |
| Depth | Spatial separation and parallax | True 3D scenes vary camera/geometry depth; planar families use a clearly named layer-depth compositor; no implication of reconstructed 3D geometry |
| Detail | Structural complexity/density | Requested complexity is capped by the active quality tier; moving it does not silently disable unrelated controls |
| Colour | Palette spread/blending | Palette editor chooses colours; macro varies their distribution rather than blindly increasing brightness |

Shared advanced sections: camera (orbit, rotation rate, zoom, recenter), motion
(flow, damping), audio mappings (source, sensitivity, attack/release), material
(roughness/glow where supported), post effects (trails/bloom/chroma), and palette.
Preset/scene capability metadata determines which advanced entries exist. Safety
is the final constraint and is never a premium control.

## 7. Six signature scene briefs

All six are planned original C++ scenes. Keep realized camera state, simulation time
and input feature timestamps explicit. Each ships with a neutral demo, restrained
default and performance preset. The quality values below are prototype ceilings,
to be revised from GPU/memory evidence rather than treated as measured budgets.

### S1 — Facet Cathedral

**Image:** interlocking translucent mineral arches with matte obsidian negative
space and thin warm emissive seams. The silhouette remains architectural even in
monochrome; bloom does not supply the geometry.

**Camera:** a locally generated spline through arches, with a stable horizon and
eased look-ahead. Music, touch and fresh variation shape the next corridor segment.
Reduced motion uses a stationary viewpoint and gentle local deformation.

**Audio:** bass changes arch opening, midrange bends ribs, treble reveals seam
detail. Beat accents push a bounded travelling deformation, not a full-screen
flash. Silence settles to a stable structure.

**Macros:** Motion = forward spline rate; Response = rib deformation; Depth =
arch separation; Detail = rib/facet count; Colour = seam-to-crystal distribution.

**Quality:** low 12 visible arch instances with opaque/translucency approximation;
medium 24 plus half-resolution bloom; high 40 with bounded refraction and an
optional depth-of-field pass. Never render arbitrary nested transparent layers.

### S2 — Prismatic Current

**Image:** a flowing tunnel of broad glass ribbons, with dark central breathing
space and an asymmetric cross-section. Avoid a uniform stack of neon rings.

**Camera:** forward travel with soft lateral drift. Musical phrases may change
the tunnel's cross-section; roll is optional and disabled by reduced motion.
Touch bends the next section ahead rather than whipping the camera immediately.

**Audio:** bass controls tunnel width, mids control ribbon torsion and beat phase
advances a travelling highlight. Transient energy changes edge texture within a
bounded range.

**Macros:** Motion = travel speed; Response = twist amplitude; Depth = visible
tunnel length/parallax; Detail = ribbon subdivisions; Colour = spectral spacing.

**Quality:** low 8 ribbons/24 longitudinal segments; medium 12/40; high 18/64
plus limited chromatic refraction. Cull behind the camera and cap transparent
overdraw; high detail must not reduce audio reliability.

### S3 — Tidal Silk

**Image:** fluid pigment sheets suspended through a shallow 3D volume, with
iridescent edges and a restrained scattering impression. Preserve recognisable
fluid movement from the existing family.

**Camera:** slow orbit around a central dye volume; touch injects on a stable
world plane. A camera recenter is smooth and does not reset the simulation.

**Audio:** bass expands vortices, midrange changes curl direction gradually, and
high-frequency energy creates sparse fine particles. Silence lets motion decay.

**Macros:** Motion = advection speed; Response = audio injection; Depth = layer
spacing and orbit parallax; Detail = solver/particle request within tier; Colour
= dye separation and mixing.

**Quality:** low 128² velocity grid with two depth layers; medium 192²/three;
high 256²/four plus capped particle wisps. Dye resolution may differ from solver
resolution. Pressure iteration counts and buffer memory are explicit per tier.

### S4 — Orbital Weave

**Image:** fine luminous fibres winding around dark celestial forms, with
intersections expressed as knots and depth occlusion, not additive white blobs.

**Camera:** elliptical dolly around a slowly rotating scaffold. Bar-confidence
changes the orbit radius; no hard cuts on every detected kick.

**Audio:** chroma selects a stable harmonic grouping, bass tightens loops and
treble excites short waves travelling along individual fibres. Invalid chroma
confidence holds the previous grouping rather than jumping between colours.

**Macros:** Motion = orbit/wave travel; Response = fibre displacement; Depth =
orbital spacing; Detail = fibre population; Colour = harmonic-group separation.

**Quality:** low 48 fibres × 32 samples; medium 96 × 48; high 160 × 64 with
half-resolution bloom and optional depth softening. Use bounded instancing and
level of detail; no per-fibre allocation during rendering.

### S5 — Glass Bloom

**Image:** radial glass petals that unfold around an empty centre, contrasting
polished surfaces with soft mineral cores. Reflection is stylized and bounded;
the scene is not a promise of physically exact real-time refraction.

**Camera:** an oblique three-quarter composition, slowly rising as petals open.
Touch rotates the specimen with inertia; reset preserves the current music time.

**Audio:** bass opens petals, mids alter curvature and harmonic energy changes
layer spacing. A drop can initiate one eased unfolding gesture with a refractory
interval, not rapid repeated opening and closing.

**Macros:** Motion = specimen rotation; Response = unfolding range; Depth = petal
layer separation; Detail = petal count/subdivision; Colour = core/rim balance.

**Quality:** low 12 petals with opaque glass shading; medium 24 with one sampled
scene-colour refraction; high 36 with a bounded second shell. Show correct depth
ordering and preserve the silhouette if high-tier effects are unavailable.

### S6 — Contour Sea

**Image:** broad sculpted terrain waves threaded by topographic light contours,
with a low horizon and soft fog. Distinct from a spectrum-bar landscape: hills
form coherent shapes across both spatial axes.

**Camera:** a calm glide along terrain valleys, with speed constrained by local
curvature. A stationary overlook is the default reduced-motion composition.

**Audio:** low/mid bands deform spatial scales, spectral history leaves slowly
travelling ridges and phrase changes move the focal region. No single FFT bin
creates an unbounded vertical spike.

**Macros:** Motion = glide/ridge travel; Response = terrain height modulation;
Depth = valley separation/fog falloff; Detail = terrain mesh/contour density;
Colour = height-to-palette distribution.

**Quality:** low 64² terrain patch, medium 96², high 128² plus a far impostor
ring. Use analytic fog and distance culling; high-quality shadows are optional
only after the common frame and thermal budgets are satisfied.

### Scene acceptance shared by all six

Record each scene with silence, sustained tones, transient pulses and at least
three licensed musical fixtures. Check macro extremes, touch cancellation,
source switch, live state restoration, GL context replacement, portrait/landscape,
30/60/120 Hz displays and preview/export checkpoints. The evidence includes
screenshots/video, frame timing, peak memory and a sustained thermal run.
Keep only looks that remain distinct without their palette.

## 8. Accessibility, safety and localization

- Give controls semantics for role, value, selected state and available actions.
  TalkBack traversal follows the visual task order; decorative scene content is
  excluded rather than announcing every animation frame.
- Offer seek, clip trim/move, reorder and parameter adjustments without drag,
  multitouch, gyro or long-press. Focus rings remain visible over scene content.
- Test 200% font scale, large display size, RTL, long translated strings, hardware
  keyboard and switch access. Avoid fixed-height text containers and colour-only
  lane/selection distinctions.
- Use WCAG AA contrast targets for text and meaningful controls, with measured
  scrim tests over bright scenes. Targets are design gates, not a claim that
  flashing visual art is medically safe.
- Reduced-motion and visual-safety controls are available before the first
  intense scene, persist across live/wallpaper/export, and are always free.
- Keep microphone/capture state visible. Silence and capture failure have
  different labels. No therapeutic, stress-treatment or all-audio-capture claims.

## 9. Design review outputs

For every implemented screen, attach compact/expanded layouts plus empty,
loading, populated, denied, offline and error states as applicable. For each
journey, record an uninterrupted device walkthrough, including recovery. Design
approval requires the interaction to work; a rendered mockup alone is insufficient.

Primary implementation references checked for this specification:
[Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults),
[adaptive window classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes),
and [Credential Manager / separate authorization](https://developer.android.com/identity/sign-in/credential-manager-siwg).
Competitor evidence and its limits remain in [REFERENCE_APPS.md](REFERENCE_APPS.md).
