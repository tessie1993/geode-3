# Geode visual style and camera direction

**Status: proposed design and implementation contract, 7 October 2026.** This
extends `docs/blueprint/DESIGN.md` and the visual lane of
`docs/rebuild/PLAN.md`. It is not a completed renderer or a device review.
The research pass ran no local build, test or lint. Implementation and runtime
evidence are tracked separately in the work queue and implementation status.

**Latest owner scope: preserve ALL existing C++ styles and MilkDrop.** New
psychedelic 3D tunnels, objects and music-driven camera motion are additive.
This supersedes the historical plan's approximately 82-style cull and any
replacement language in the earlier blueprint. Visuals, camera, simulation and
scene logic belong in native C++ with GPU shaders; Kotlin remains the Android
shell for UI, Media3, permissions and platform account/billing integration.

## Owner decision: live art must be generative

The owner's latest instruction, **“No not deterministic!”**, supersedes older
language about seeded, repeatable live camera routes. A new live session should
produce fresh, organic visual choices. Music, touch, the current image and
bounded spontaneous variation shape what happens next. Do not derive the same
camera journey from a track hash or saved preset seed.

The shared C++ `CameraDirector` still owns camera quality. It improvises rather
than plays a predetermined route. Continuity, deliberate composition and motion
limits remain requirements; unpredictability must not become jitter. Saved
looks preserve artistic preferences, not a mandatory replay of a previous trip.
Resuming an existing session preserves its current state without restarting it.

Recording captures the performance that actually occurred. A recording is not
a promise that generating the same song again produces the same images. Keep
fixed entropy and repeatable camera fixtures inside the QA harness only. This
contract supersedes older seed-only capture or repeatable-live-path assumptions.

## Research evidence and its limits

Official Android listings and upstream developer pages were read on 7 October
2026. Eight public promotional images were actually opened for visual
inspection: Astral images 1, 2 and 4, and one image each from Magic Fluids,
Fraksl, Avee, Vythm and projectM. These images establish appearance in those
particular images. They do not establish the underlying geometry, measured
camera movement, latency, frame rate or quality across the app. No trailer was
played and no reference app was installed in this research pass.

The table deliberately separates **observed image**, **developer-described
behavior**, and **our design inference**. Marketing claims about relaxation or
health are not adopted. Competitor artwork, presets, icons and copy are not
assets for Geode.

| Primary evidence | What was observed or described | Design inference for Geode |
|---|---|---|
| [Astral Android listing](https://play.google.com/store/apps/details?id=astral.teffexf&hl=en), images 1/2/4 | Viewed: saturated radial/fractal contours; a dark cosmic field with scattered lights; a longitudinal mirrored lattice receding toward a central opening. Listing describes swipe-based distance, speed adjustment, space/tunnel journeys and gyroscope interaction. A still image does not reveal its camera implementation. | Preserve a strong sense of passage and accessible navigation. Make our tunnel depth legible through parallax, occlusion and restrained near/mid/far colour; maintain dark breathing space. Use one shared camera rather than unrelated hardcoded rotations per shader. |
| [Magic Fluids Android listing](https://play.google.com/store/apps/details?id=com.magicfluids&hl=en), image 1 | Viewed: orange and blue curling filaments on a dark field with a very bright merged centre and fine particle specks. Listing describes touch drawing, configurable smoke/water, particles, glow and textures. | Retain the fluid family. Give dye a recognisable material and intentional scale hierarchy. Preserve coloured detail through tone mapping instead of making the centre uniformly white. Treat fluid flow and camera movement as separate controls. |
| [Fluids Particle Simulation LWP listing](https://play.google.com/store/apps/details?id=com.MKGames.FluidsSounds&hl=en) | Text reviewed: touch/swipe fluid art, flow/intensity/colour controls, particles, wallpaper and ambient sound. This pass did not inspect one of its image files. | A calm direct-touch scene is an essential baseline, with immediate local response and an optional shallow layer-depth presentation. Do not infer true volumetric fluid simulation from the listing. |
| [Fraksl Android listing](https://play.google.com/store/apps/details?id=com.workSPACE.Fraksl&hl=en), image 1 | Viewed: broad, mirrored colour bands curling into nested spirals. Listing describes live video feedback, filters/mirrors, layer transforms, modulators and sequences with hold/transition times. | Borrow the principle of continuously evolving form and coherent transformations. Our interpretation uses constrained live variation and explicit modulation, not a screenshot-shaped clone or a claim that feedback is a true 3D camera. |
| [Avee Android listing](https://play.google.com/store/apps/details?id=com.daaw.avee&hl=en), image 1 | Viewed: several branded central compositions with spectrum accents and image backdrops. Listing describes editable templates, layers, audio response and device-dependent resolution/frame-rate export. | Preserve focal space and stable framing for music-video creators. A user logo/caption must stay separate from camera travel and remain readable. A recorded visual take must survive editing and export. |
| [Vythm Android listing](https://play.google.com/store/apps/details?id=com.MKGames.Vythm&hl=en), image 1, plus [developer page](https://mkgames.org/apps/vythm/) | Viewed Android image: a centred neon circular/equalizer motif inside the app. Android listing describes modes, backgrounds and performance effects. The developer site describes framing and beat-aware evolving looks, but presents its Apple-platform product; do not assume Android feature parity. | Keep performance controls immediate and explain which operation they affect. Colour, material, scene geometry and camera must each have a distinct role. Effects should accent the composition, with a clear way back to a restrained look. |
| [Official projectM Android listing](https://play.google.com/store/apps/details?id=com.psperl.projectM&hl=en), image 1 | Viewed: irregular bright streaks and layered feedback contours against a dark purple field. Listing describes MilkDrop presets, touch interaction and quality controls. Appearance varies by preset. | Keep MilkDrop as a distinct generative preset family. Preserve its authored motion; do not force all presets through a fictitious world-space camera. Provide optional, clearly labelled canvas framing and capture the actual rendered performance. |

Public image references used for the visual observations:
[Astral radial](https://play-lh.googleusercontent.com/E3U32pmvVxpuzwDe_6b9hTIJgzSaeiz_VwWPqHpd2O4dYbWsSVkqTXZQwbLthJboY60bJrU4uO8ZiLpB_n7B41A=w526-h296),
[Astral space](https://play-lh.googleusercontent.com/uIVv8eg3A_aDlnqWubCDDWgdOLFqEqU_ybKbvfeHcoD65Ve3j4ljKRz-jo8quHshOhVivtsBwWdld27Td4hC=w526-h296),
[Astral passage](https://play-lh.googleusercontent.com/BD1kK7pJpBn21zbbYsHc-m5JxtSlQcnB_vROvacL0Q33_J3mIddaqT7cZbrUqt4f5EeoqicXOee_8VPdI8FI=w526-h296),
[Magic Fluids](https://play-lh.googleusercontent.com/ND2rwXusA6UIiVWYjUKoXUe8Rft4vsxJXgij0xsRTAGErGWIji07EelpPNHw1thS9wI0y8icvdw2YCoiT79hWg=w526-h296),
[Fraksl](https://play-lh.googleusercontent.com/PGwr3AW5BKFc_IxJKND2s26K-aq83lZ0MQv7lh3X9qwssLoYwWbEtEYhyyoFAXqA3UfDtPiUyR3n59baLqk0Iw=w526-h296),
[Avee](https://play-lh.googleusercontent.com/yEiqHDUa0EvIJjc8asyRkAWeFcNuvdMvNRle0vA75FODLXFVx682WHKrX2fZOJNxi3op3cRDFTNdhIRJ9iEa=w526-h296),
[Vythm](https://play-lh.googleusercontent.com/LvLePrhQHczpv9Jdd5vC_QJLRL-JBxhCKRJZC-Ug1jXy-3VJJGBc_r_M7WLgYsGthMLlFtIXkGXgoL4eid26=w526-h296),
[projectM](https://play-lh.googleusercontent.com/5stiW5dEK4WXAwoMLyxs3DCj4nNvZ9jXOXHN7Cks-MVqS6pQcelEme6-J8mkYS2kcKuGif2GBFla1ljDLzdDLd4=w526-h296).
These remain external research references, not bundled artwork.

### Owner-supplied visual references

All nine attached screenshots were inspected. Keep the original images private
to the working session; do not commit them to the public repository. Their
filename time fragments below identify the exact evidence without assuming an
app identity from appearance alone. A still cannot prove a true 3D renderer or
the path its camera took.

| Screenshot time fragment | Observed visual form | Concrete implementation target |
|---|---|---|
| `00-54-11`, `00-54-14` | Fine blue fluid ribbons, bright particles along curls, soft yellow/blue dye lobes inside a phone mockup | Tidal Silk materials; layered dye and particles with controlled luminous edges, stable touch anchors and optional shallow parallax |
| `00-56-00` | Rows of coloured ring/bead points curve around a dark aperture; larger near rings establish scale | Upgrade existing bead/rod corridor looks with spatially placed instances, genuine depth/occlusion and a camera sharing the corridor path |
| `00-57-27` | Saturated nested contours narrow toward a curved, distant passage | A bounded fractal tunnel material with scale hierarchy, texture filtering and a true path-following ray origin, not only radial screen zoom |
| `00-57-59`, `00-58-05` | Dense particle corridors with many depth layers and large near polygonal points | C++-owned instance/particle fields distributed around a navigable path; distance-aware size/LOD and near clipping without billboard pops |
| `00-58-54` | Mirrored saturated corridor and visible speed/settings controls | User-controlled travel with an observable Motion range; coherent forward reveal and no speed-change teleport |
| `00-58-56` | Fractal organic radial petals and a central chamber; visible “37 Tunnels” choice list | Multiple geometric motifs and independently selectable look presets; Glass Bloom/Prismatic Current variants with different shapes, not identical geometry recoloured |
| `00-58-59` | Curved stellar rows around a luminous aperture; visible “10 Backgrounds” list | Background layer independent of tunnel geometry, palette and camera; preserve central detail through tone mapping |

The user's DMT-like direction means richly structured psychedelic forms,
receding nested geometry, bead/particle passages and morphing objects. It does
not justify copying the references' images or assigning health effects to the
experience. Prioritise the bead/fractal tunnel prototype alongside the six
signature scene briefs below; do not spend the first visual pass on UI polish
while these requested visual forms remain absent.

## The visual language

The six signatures from DESIGN remain the launch art direction: **Facet
Cathedral, Prismatic Current, Tidal Silk, Orbital Weave, Glass Bloom and Contour
Sea**. They should differ in silhouette, spatial organisation, material and
movement before colour is applied.

Use mineral darkness, translucent colour and fine light as the common visual
vocabulary. Every scene needs a dominant large-scale form, a secondary moving
structure, and a small amount of high-frequency detail. Keep one clear focal
region and at least one quiet region; a screen filled uniformly with glowing
detail has no depth hierarchy. Materials should remain readable with bloom
disabled. Texture should support geometry rather than mask a weak silhouette.

Palette follows material: a warm seam against a cool body, or opaque dark mass
against fine coloured fibres. Offer saturated palettes, but avoid coupling
Colour to uncontrolled brightness. Audio energy changes geometry, tension,
flow and local highlights. Full-frame flashes, rapid random cuts and continuous
camera shake are not the default vocabulary.

Freshness comes from local choices: which arch opens, where a ribbon bends,
which vortex receives dye, which orbit reveals a knot, how petals unfold and
which valley draws the camera. Keep those choices related to the scene and the
music. A random full-screen palette change every few seconds is insufficient.

## Shared C++ CameraDirector

### Required boundary

One camera contract serves the retained spatial shaders and new C++ scenes.
It does not require adopting a second renderer. Keep the GLES production path
while the Diligent prototype proves the go/no-go requirements from the rebuild
plan. Engine choice must not change camera-control semantics.

| Contract element | Proposed contents and responsibility |
|---|---|
| `CameraIntent` | Requested rig, travel amount, framing distance, orbit limits, target/focal anchor, look bias, interaction mode and explicit user commands. Angles use radians, rates radians/second, positions scene units. |
| `SceneCameraConstraints` | Scene scale, navigable volume/corridor, subject bounds, clearance, look-ahead range, projection limits, permissible pitch/roll, composition target and supported rigs. A scene adapter supplies these rather than exposing arbitrary free flight. |
| `DirectorSignals` | Fresh timestamped band/energy/novelty values, confidence-gated musical events with unique sequence IDs, source state, touch/gyro intent and monotonic elapsed time. Never treat an old beat flag as a new event. |
| `CameraState` | Position, orientation quaternion, linear/angular velocity, target, current generated path segment, framing, recent gesture history and transition state. Owned by the visual session; independent of GL resource lifetime. |
| `CameraFrame` | Realized pose, lens/projection, view and inverse view/projection, previous realized transform, target and timestamp. Produced once per scene per frame and used consistently by geometry, ray generation, picking, depth and recording. |
| `PerformanceRecorder` | Receives realized frame state and transitions after bounds are applied, along with encoded composited frames/audio and capability metadata. It never rerolls the live director to reconstruct a recording. |

Each scene in a transition has its own state. Outgoing and incoming scenes may
share a musical time sample but must not overwrite each other's camera. GL
recreation recreates resources and rebinds the current state; it does not restart
the performance. The current native `Scene` interface has no shared camera
frame, so this is new foundation work, not a cosmetic label on existing zoom.

### Improvisation without jitter

1. Generate a short horizon of candidate framing/travel intentions from fresh
   session entropy, the current scene, recent movements and music. Acquire
   entropy away from the realtime audio callback and avoid blocking work on the
   render thread. Do not use a permanent preset seed as the product default.
2. Score candidates for visible subject area, clearance, depth reveal and
   continuity. Down-weight recently used gestures/directions. If no candidate
   is valid, hold or coast safely; do not force a new movement for novelty.
3. Plan only the next local passage, generally 4–12 seconds. New sections,
   touch or meaningful spectral change can reshape the future part. Preserve
   current position, velocity and acceleration when joining paths.
4. Use constrained cubic splines for corridors, evolving elliptical arcs for
   orbits, and damped spring targets for gentle framing changes. Sample by
   arc length rather than raw spline parameter. Check the curve between
   control points: smooth interpolation alone does not ensure clearance.
5. Low-pass spontaneous variation and combine it with purposeful targets.
   Independent random orientation samples on every frame are forbidden.
   Ordinary orbit reversals slow through zero; do not flip an angular sign.
6. Bound velocity, acceleration and jerk, then apply composition/collision
   constraints and reduced-motion rules. A spring alone is not a speed limiter.
   Keep internal integration stable after a stalled frame, without advancing a
   large missed interval as one camera jump.

For a target spring, use the critically damped response family with damping
ratio 1 and explicitly clamped output motion. Orientation error is handled in
quaternion/tangent space with hemisphere continuity; never spring Euler angles
across a 360-degree wrap. Parallel-transport a path frame and stabilise its up
vector so straight sections and low curvature do not create roll flips.

### Initial movement budgets

These are proposed design starting points for device review, not physiological
safety limits. Let **U** be a scene's declared characteristic size (subject
radius or corridor half-width), not an assumed physical metre. Scene-specific
limits below may be stricter.

| Quantity | Normal autonomous motion | Reduced motion |
|---|---|---|
| Translation | Per-scene cap; global ceiling 1.5 U/s | Stationary framing by default; explicit user framing remains available |
| Translation acceleration / jerk | At most 0.6 U/s² / 2 U/s³ | No autonomous travel; eased recenter at most 0.15 U/s |
| Angular speed / acceleration / jerk | At most 12°/s / 20°/s² / 80°/s³ | No autonomous orbit, roll or beat camera displacement |
| Roll | Default 0°; optional scene bank limited to ±3°, slew at most 1°/s | Exactly 0° |
| Lens | Fixed 45–65° vertical FOV chosen per scene; dolly changes distance | Fixed FOV; no beat zoom |
| Camera beat accent | Optional displacement target at most 0.02 U, eased over at least 350 ms; no accumulation | Disabled; use bounded object/material deformation instead |
| Major reframing | Usually at least 6 seconds between autonomous changes; confidence and valid composition required | User requested only, eased opacity/recenter |

No camera snap on a kick, seek, source switch, parameter reset or beat-detector
reacquisition. Recenter restores a good composition smoothly; it does not reset
music time or fluid state. A user drag can interrupt autonomous intention, but
its rate/clearance constraints remain active. Do not turn the display refresh
rate into a different travel speed.

## Six signature scene contracts

### S1 — Facet Cathedral

**Composition and material.** Interlocking mineral arches frame a dark
off-centre vanishing point. Broad matte facets define each arch; thin warm seams
trace load-bearing ribs. Keep a foreground arch cropped at one edge, two or
three readable middle arches and a fog-softened distant opening. Translucency
is selective, never a pile of uniformly additive transparent surfaces.

**Camera.** A local spline dolly follows a corridor, looking 0.6–1.2 U ahead.
Initial travel is 0.25 U/s, cap 0.8 U/s; yaw cap 8°/s; fixed 52° vertical FOV.
Lateral offset stays within 0.15 U and the camera keeps at least 0.2 U clearance
from the arch surface. Each newly revealed junction offers several valid local
paths; the director chooses from current music, composition and fresh variation.
There is no stored repeating tour. Keep horizon roll at zero.

**Music and touch.** Bass opens ribs by at most 8% of their rest span; mids
adjust curvature more slowly; treble animates sparse seam detail. A phrase can
invite a new passage; a beat sends a deformation down nearby arches rather than
jerking the lens. One-finger interaction pulls a focal/rib anchor; Camera mode
uses drag to bias the next look-ahead. Pinch dollies within the corridor without
crossing a wall. Reduced motion presents a fixed arch composition with restrained
local deformation and no camera beat displacement.

**Feasibility and quality.** Prefer instanced mesh arches for predictable
occlusion and material variation. A raymarched prototype is acceptable if it
exposes the same world-space camera and clearance volume. Low/medium/high
initial ceilings: 12/24/40 visible arches; low uses opaque glass approximation,
medium adds half-resolution bloom, high may add bounded refraction. Failing the
high tier must leave architectural depth and silhouette intact.

### S2 — Prismatic Current

**Composition and material.** Broad asymmetrical ribbons braid through a tunnel
with a quiet dark core. Large near ribbons pass the frame edge; thinner distant
ribbons reveal the depth. Use one primary spectral gradient and a few secondary
seam colours rather than equally bright rainbow rings at every distance.

**Camera.** Forward dolly with an evolving look-ahead spline. Initial travel
0.4 U/s, cap 1.2 U/s; lateral drift at most 0.18 U; yaw cap 10°/s; 60° FOV.
Generate gentle upcoming bends with fresh phase, amplitude and cross-section
choices, subject to minimum curvature/clearance limits. Slow before a tight
bend using a curvature-based speed cap. Optional bank follows the curve within
±3°; default is level. Do not multiply elapsed time by a changing audio rate.

**Music and touch.** Bass widens the tunnel, mids twist ribbons and treble adds
travelling edge texture. Confidence-gated beat phase can guide moving highlights;
it does not have to drive a literal repeating camera bob. Touch bends geometry
ahead of the camera, so the user's gesture reads as shaping the journey. Reduced
motion uses a stationary opening and moving local ribbon deformation; no forward
optical flow or bank.

**Feasibility and quality.** Instanced ribbon strips or a bounded SDF tunnel
share the same path definition with the camera so neither cuts through the wall.
Initial low/medium/high: 8×24, 12×40, 18×64 ribbon segments. Cull behind the eye,
limit transparent overlap and add refraction only after the baseline passes.

### S3 — Tidal Silk

**Composition and material.** Existing fluid motion becomes shallow suspended
pigment sheets: broad dense dye, fine iridescent edges and occasional sparse
wisps. A large calm negative region offsets the active vortex. Keep the palette
mix readable under the strongest injection; luminous edges do not bleach the
entire dye volume.

**Camera.** A shallow orbit/parallax rig explores a central dye volume. Default
orbit drift about 1.5°/s, cap 4°/s; yaw excursion initially ±18°, pitch ±8°;
framing distance 3–4 U, 48° FOV. Live choices vary which side of the flow is
revealed and how long the image rests, rather than following a closed loop.
Layer separation is capped at 0.3 U until view-dependent overlap is convincing.

**Music and touch.** Bass feeds broad vortices, mids bias curl over seconds and
treble excites a small particle budget. One finger injects dye on a stable
world plane using the current inverse camera; moving the camera does not move
an already established contact point. Two-finger Camera mode adjusts the view.
Reduced motion uses a fixed frontal composition and slows automatic forcing;
touch injection and locally evolving dye remain available.

**Feasibility and quality.** Retain the current 2D fluid solver initially, shown
on 2/3/4 depth-separated layers with 128²/192²/256² velocity grids. This is
explicitly **layer depth**, not a claim of a full volumetric Navier–Stokes solver.
Parallax must not expose empty card edges. Solver iterations, dye resolution and
wisps have independent budgets. A genuine volume is a later measured capability.

### S4 — Orbital Weave

**Composition and material.** Fine fibres wrap around dark forms with visible
occlusion and occasional bright knots. A foreground strand reveals scale; the
main scaffold occupies roughly the central two-thirds; the background remains
sparse. Prevent additive fibre crossings from becoming featureless white discs.

**Camera.** An elliptical orbit dolly keeps a focal knot visible while gradually
revealing the scaffold. Radius 2.8–4 U; ellipticity at most 0.25; nominal orbit
2°/s, cap 6°/s; pitch −10° to +25°; 50° FOV. The director evolves radius, focal
knot and dwell length across musical phrases. It can reverse after an eased
rest but does not endlessly replay the same ellipse or lock to one bar period.

**Music and touch.** Bass tightens loops, treble sends brief waves along chosen
fibres and confident chroma biases harmonic groupings. Low chroma confidence
holds a stable palette rather than hunting. Drag attracts a knot; Camera mode
orbits the scaffold; pinch changes framing distance. Reduced motion fixes the
view and retains low-amplitude fibre waves with no camera bob.

**Feasibility and quality.** True geometry using instanced ribbons or tube strips,
with mesh depth testing. Low/medium/high start at 48×32, 96×48, 160×64 fibre
samples. Choose cross-section detail by projected size; avoid per-fibre frame
allocations. Bloom is half-resolution and optional; occlusion is mandatory.

### S5 — Glass Bloom

**Composition and material.** Petals open around a visible empty centre, viewed
from an oblique angle. Polished rims contrast with softly coloured mineral cores.
Keep the centre and outer silhouette legible at every opening amount. Use
stylised reflective glass, not an unsupported claim of physically exact optics.

**Camera.** A three-quarter subject rig uses a slow boom and restrained orbit.
Framing distance 2.5–3.8 U; pitch 20–40°; nominal orbit 1°/s, cap 4°/s;
boom cap 0.1 U/s; 45° FOV. The director chooses small reveal arcs around the
currently opening petal group, returning to useful framing without restarting
the gesture. Lens FOV stays constant; opening movement belongs to the specimen.

**Music and touch.** Bass opens petals, mids shape curvature and slow harmonic
change adjusts layer spacing. A strong drop can invite one unfolding gesture
with at least an 8-second refractory interval, not repeated opening/closing on
every transient. Drag rotates the specimen with bounded decaying inertia; it
does not alter perpetual camera spin. Reduced motion holds the camera and
eliminates rotational inertia; petal deformation remains restrained.

**Feasibility and quality.** Instanced petal meshes, opaque-glass low tier and a
bounded refractive layer at higher tiers. Start with 12/24/36 petals; high may
add a second shell only after overdraw measurements. Depth sorting, backface
handling and silhouette are acceptance requirements even without refraction.

### S6 — Contour Sea

**Composition and material.** Broad terrain waves carry topographic light lines,
with mist revealing distance. The horizon stays low and stable, initially
55–65% down the image; large near contours and a restrained sky define scale.
Hills are coherent in two axes, not a row of extruded FFT bars.

**Camera.** A low valley glide follows an improvised terrain-aware path. Initial
speed 0.18 U/s, cap 0.6 U/s; yaw cap 5°/s; 55° FOV. Maintain clearance above
the highest local animated surface and slow on strong curvature. The target
region evolves across several seconds; autonomous look-ahead never dives into
a transient peak. Terrain, camera and fog share one coordinate system.

**Music and touch.** Low/mid energy drives broad spatial bands; spectral history
leaves travelling ridges with bounded height. Novelty can draw attention toward
a new valley. Touch makes a local ripple/height attractor; Camera mode shifts
the overlook target. Reduced motion is a stationary overlook with modest local
wave deformation and no horizon drift.

**Feasibility and quality.** Heightfield mesh 64²/96²/128² plus a distant
impostor ring, distance culling and analytic fog. Reuse the same height function
for surface rendering and camera-clearance queries. Optional shadows wait until
frame/thermal gates pass; contour readability cannot depend on them.

## Universal controls and interaction mapping

The five macro meanings in DESIGN stay consistent. A preset stores requested
0–1 values, and each style has a documented adapter plus effective capability
state. A quality tier may cap Detail but must not silently change camera intent.

| Style | Motion | Response | Depth | Detail | Colour |
|---|---|---|---|---|---|
| Facet Cathedral | Corridor travel | Rib/arch deformation | Arch separation and reveal | Rib/facet population | Seam/body distribution |
| Prismatic Current | Tunnel travel | Ribbon deformation | Corridor length/parallax | Ribbon subdivisions | Spectral spacing |
| Tidal Silk | Intentional advection and optional view travel | Audio forcing | Layer spacing/parallax | Solver/particle budget | Dye separation/mixing |
| Orbital Weave | Orbit and fibre travel | Fibre displacement | Scaffold/orbital separation | Fibre population | Group separation |
| Glass Bloom | Specimen/view travel | Petal unfolding | Petal layers | Petal mesh budget | Core/rim balance |
| Contour Sea | Valley/ridge travel | Height deformation | Valley spacing/fog | Mesh/contour density | Height/palette distribution |

**Motion zero holds automatic travel.** No hidden `uTime * baselineRate` may
continue moving the camera or specimen after that. Local Response-driven
deformation remains independently controlled. In a fluid adapter, zero automatic
advection holds the moving state; direct touch still has an explicit local
interaction contract. A separate Freeze action holds the entire visual state.
**Response zero removes audio modulation**, including camera accents and slow
energy effects, while allowing generative ambient motion at the chosen Motion.

Default one-finger input interacts with the scene; a visible Camera mode enables
drag-to-orbit/look and pinch-to-dolly. On planar families it is called Frame and
provides canvas scale/pan, not a misleading 3D dolly. Offer accessible buttons
for orbit left/right, closer/farther, recenter and freeze; no essential action
requires multitouch or gyro. Gyro is opt-in, adds a small relative look offset
and uses the same bounds; disabling it recenters smoothly.

Separate **orientation angle**, **turn rate**, **framing scale**, **dolly distance**
and **lens FOV** in the model and advanced UI. Current
`TouchTransform.rotation()` adds `degrees * 0.012` to `SceneParams.rotation`,
which native rendering integrates as a rate: a twist can alter continuing spin
instead of orientation. Migrate this behavior intentionally; do not relabel that
field as a camera angle. Preserve old parameter values in a legacy adapter until
users can preview and save a new version.

## Existing families and additive integration

The owner's latest scope keeps every existing C++ style and working MilkDrop,
including the Fluid family and eight raymarched styles. The six signatures and
new DMT-like tunnel/object variants extend that catalogue. Existing IDs, saved
looks, favourites, packs and Studio references remain valid. There is no style
cull or asset-removal package. Improvements to an existing style need an
explicit versioned adapter that preserves its saved behavior and identity.

| Family | Camera integration | Migration requirement |
|---|---|---|
| Retained raymarch styles: `noneuclid`, `kifs`, `orb_lattice`, `rod_tunnel`, `neon_tiles`, `chroma_orb`, `mandala_dome`, `bead_vortex` | Audit every ray origin/direction and look target. Supply shared camera matrices/basis and world-space picking where the field supports it. A fullscreen fragment shader can perform true spatial ray rendering; a mesh is not required. Geometry-relative constraints remain per style. | Preserve the old framing as an explicit legacy camera mode until the new adapter is visually approved. `noneuclid` currently assumes an eye-relative field and touch anchor, so its coordinate transform requires a careful port. |
| Fluid family | Default fixed plane; optional bounded layer-depth camera. Transform touches through the actual camera/plane intersection. | Preserve solver state through camera changes, recenter and tier resize. Do not portray parallax cards as volumetric fluid. |
| MilkDrop/projectM | Preserve preset-owned generative movement. Optional outer canvas framing can scale/pan/tilt a bounded presentation surface; capability labels remain planar. | Keep `.milk` compatibility, texture packs and authored motion. Do not rewrite preset camera parameters to pretend all presets share Geode world-space geometry. |
| All other existing C++ styles | Preserve their current renderer and expose only camera capabilities they actually support. Add optional spatial upgrades without forcing a planar scene into a misleading 3D control contract. | Inventory stable IDs and fixtures; preserve every scene and saved project. Introduce versioned opt-in upgrades, with the original saved behavior available. No replacement mapping may silently discard an existing style. |

Current source evidence makes the foundation need concrete:
`kifs_frag.glsl:521–535` creates yaw/pitch from its own `uTime`;
`rod_tunnel_frag.glsl:129–141` builds fly/roll from time and flow phase;
`noneuclid_frag.glsl:97–109` assumes an eye-relative frame. None is automatically
controlled by adding an Orbit slider to the app. Port one at a time and remove
the replaced local camera calculation so it cannot double-apply motion.

Further confirmed active-path evidence:

- `lib_dmt.glsl:493–551` already defines `dmtTunnelPath`, a warp, flight basis
  and `dmtFlightRay`. Its curve is presently a fixed combination of sine terms
  plus `uMoveDir * z * 0.12`. Retain the useful camera/geometry alignment idea,
  but replace the fixed global route with bounded C++-generated local path
  segments shared by ray generation and tunnel evaluation.
- `ShaderScene.cpp` uploads zero spike/form/spawn values and constant move
  direction. Consequently, the intended rod-to-bead form progression and
  heading variation are not live generative controls today. Restore a designed
  native scene-state contract rather than guessing random shader uniforms.
- `fractal_temple_frag.glsl:14–24` explicitly describes a 2D perspective
  construction, with a projected dome and scaled repeats. It is a valid existing
  style and stays. A true spatial temple variant needs world-space geometry or
  an SDF and shared camera; its existing appearance cannot acquire parallax just
  by renaming the zoom control.

### First native tunnel/object implementation slice

Add a C++ `SpatialSceneState` and `TunnelPath` alongside `CameraDirector`.
`TunnelPath` owns a bounded local segment window, arc-length lookup, tangent/
transported frame, corridor radius and distance offset. The camera and material/
instance generator read the same immutable frame snapshot. Upload only the
needed coefficient/instance data once per frame; do not regenerate path choices
per fragment. Rebase far-travelled coordinates coherently so float precision
does not degrade or reset the visible journey.

Use that foundation for three reviewed looks under existing-ID-compatible
versioned adapters and/or new additive IDs:

1. **Bead corridor:** true 3D ring/bead instances with sparse connector strands,
   a curved flight path, open central clearance and distance LOD. Music adjusts
   row spacing, bead radius and local wave displacement within bounds; the
   camera improvises bends ahead. Start at 2k/5k/10k visible instances for
   low/medium/high as prototype ceilings, then revise from device evidence.
2. **Fractal passage:** a bounded repeated SDF corridor or mesh extrusion with
   fractal surface detail. Shared path coordinates define the deformation;
   conservatively account for the warp derivative when sphere tracing so
   artistic warps cannot skip surfaces. Use distance-scaled hit epsilon, a
   finite far range and quality-tier step limits. No claim of an exact signed
   distance survives an arbitrary unbounded domain warp.
3. **Morphing 3D object:** a radial bead/petal/folded form with a true orbit
   camera, depth occlusion and a stable focal void. Audio drives the object's
   local shape and material, while the camera independently chooses reveal
   arcs. Reuse Glass Bloom/Orbital Weave contracts rather than faking orbit with
   a rotating completed image.

Render a debug depth view, normal view, path centreline and camera frustum in
the prototype. Use off-axis camera views to prove spatial geometry and parallax;
a convincing straight-ahead screenshot is insufficient. Keep debug overlays
out of production user flows.

## Transition language and post-processing

Use a small set of transitions that respect spatial continuity:

- **Material dissolve:** 600–1000 ms eased opacity between independently valid
  compositions, with exposure held stable. Default for incompatible spaces.
- **Shared-direction reveal:** continue a similar travel/look direction while
  revealing the next scene; only compatible spatial adapters may use it.
- **Portal passage:** a visible doorway/opening leads into the next scene; both
  sides must have valid camera placement and enough GPU budget during overlap.
- **Rest and reveal:** slow, dwell, then reveal a new focal form within one scene.
  This is the preferred large generative change instead of repeated hard cuts.

Music may suggest a transition opportunity; an explicit user selection takes
effect promptly rather than waiting indefinitely for a bar. Never hard-cut on
every detected beat. Reduced motion uses a short opacity transition with stable
framing. Scene errors hold the last valid frame, then name the fallback.

Common pass order is scene geometry/material → optional bounded trails/bloom/
depth/chroma → tone mapping → final safety-aware composition. The existing
parameter clamps are not proof of final-frame luminance limits. Integrate
overlays/transitions into the final output evaluation; do not leave a later
bright overlay outside the claimed safety boundary. DOF and motion blur are
optional high-tier art tools, default off until they improve captured motion
without smear, halos, discomfort or budget failure.

## Recording and export preserve the realized performance

Use two explicit product concepts:

| Concept | Behavior |
|---|---|
| **Live session / Generate a take** | Fresh generative visuals and camera. Playing the same song again may create a different journey. The session is continuous across UI changes. |
| **Recorded take** | The actual composited video and synchronized audio captured during that performance, plus editing metadata. Exporting that take preserves its recorded imagery and timing. |

Record realized camera poses, lens/framing, parameter changes, selected gestures,
scene/asset versions, source discontinuities and transition outcomes with media
timestamps. These aid editing and diagnostics. **Transforms/events alone do not
guarantee exact reconstruction of stochastic GPU fluid or projectM feedback.**
The encoded composited recording is the authoritative appearance. If later
editable rerendering is offered, it must capture sufficient simulation state,
resources and evaluated outcomes, measure storage/bandwidth, and prove that
specific mode. Do not quietly present a freshly generated replacement as the
recorded performance.

High-resolution rerender without captured state is a new generated take, with
clear preview and user choice. Never promise pixel-identical cross-device
rerendering from a random seed. Export failure/cancellation preserves the take;
aspect-ratio changes use crop/reframe controls around recorded pixels unless a
supported editable state representation is available.

For QA only, the director accepts a controlled entropy source and a timestamped
input fixture. This allows bounds and integration regressions to be reproduced.
Such a fixture does not become the default live product experience.

## Maintained implementation references

Primary upstream code was read on 7 October 2026. No code was copied. Inspect
exact-file provenance and transitive dependencies before a port; keep notices.
The following references inform a native implementation, not a WebView layer.

| Source and inspected revision | What to use as a reference |
|---|---|
| [bgfx C++ raymarch example](https://github.com/bkaradzic/bgfx/blob/master/examples/03-raymarch/raymarch.cpp), blob `96c8972476d73fe5785cf35898e779e4d71e3a8e`; [fragment shader](https://github.com/bkaradzic/bgfx/blob/master/examples/03-raymarch/fs_raymarching.sc), blob `9a55e40d4a8923a3f943946c3f2b513b40d4268a` | Native C++ view/projection setup and inverse-matrix-based spatial ray generation. Study the engine/shader boundary; do not adopt its simple time rotation as our director. The included `iq_sdf.sh` references third-party distance-function material: use independently implemented mathematics or separately verify exact-file provenance before copying it. No bgfx renderer replacement is approved by this reference. |
| [FastNoiseLite C++ header](https://github.com/Auburn/FastNoiseLite/blob/master/Cpp/FastNoiseLite.h), blob `c67f2e5cec4653ebf85e9f19eec2d6ddfd8ca697` | A small native candidate for smooth 3D noise/domain-warp targets, with an in-file MIT notice. Use fresh live state/offsets and user/music influence; never a fixed track-derived route. Bound amplitude/derivatives and sample off the realtime audio callback. Benchmark before adding the dependency. |
| [Filament C++ Manipulator](https://github.com/google/filament/blob/main/libs/camutils/include/camutils/Manipulator.h), blob `5ea49eaf5aee776d0b78fd56775153648cd51557`; [OrbitManipulator](https://github.com/google/filament/blob/main/libs/camutils/src/OrbitManipulator.h), blob `f89b7ada03339904e3c89ad728de29d92c2010ac` | Native orbit/map/free-flight separation, explicit look-at output, perspective-correct ray picking, input lifecycle and camera bookmarks. Adapt the contract; Geode adds tighter bounds and improvisation. Do not inherit unrestricted target crossing. |
| [Three.js spline extrusion example](https://github.com/mrdoob/three.js/blob/dev/examples/webgl_geometry_extrude_splines.html), blob `446ff1135db93897e47c92b334f2217bbd08c527` | Concrete tube geometry and camera travel using path points/tangents, a transported basis and arc-length look-ahead. This is a behavior/math reference only. Its fixed 20-second wall-clock loop is explicitly rejected for Geode. Implement our own bounded C++ path, without shipping JavaScript or a browser renderer. |
| [Three.js OrbitControls](https://github.com/mrdoob/three.js/blob/dev/examples/jsm/controls/OrbitControls.js), blob `016838dd1111765b03df64243000a81631481f60` | Clear distinction between orbit, dolly and pan, fixed up direction, distance/polar-angle limits and elapsed-time-aware automatic rotation. Study behavior rather than transplant browser code. |
| [Three.js CatmullRomCurve3](https://github.com/mrdoob/three.js/blob/dev/src/extras/curves/CatmullRomCurve3.js), blob `a18985c3e16ee71e973dac94ac77abadaa457a2a` | Centripetal/chordal curve construction and handling repeated control points. Independently implement the small native geometry needed; a spline reference does not supply collision clearance, curvature budgeting or an organic director. |
| [Filament material documentation](https://github.com/google/filament/blob/main/docs/Materials.md.html), blob `3a82cd3f1de05a3234199c50c6235637cbadffe4` | Roughness, emission and refraction as separate material dimensions. Apply these ideas to the six art briefs without adding an unapproved second production renderer. |
| [Diligent post-processing tutorial](https://github.com/DiligentGraphics/DiligentSamples/blob/master/Tutorials/Tutorial27_PostProcessing/readme.md), blob `0e6e80698235dd9b139b2bd66298f1dbed7dd626` | Explicit material/depth/motion data, temporal-history ownership, lighting and tone mapping. Its deferred/SSR stack is a research sample, not proof that the whole stack is appropriate for Geode's Android GPUs. |

Licences inspected: [Filament Apache-2.0](https://github.com/google/filament/blob/main/LICENSE),
[Three.js MIT](https://github.com/mrdoob/three.js/blob/dev/LICENSE) (blob
`8ada2a5f982916b0ba4b7a0aa7de347587e745d7`), and
[DiligentSamples Apache-2.0](https://github.com/DiligentGraphics/DiligentSamples/blob/master/License.txt).
Maintenance evidence checked: Filament commit
`79bfde1c94e591ade7321f344b72a35c48c0b6ca` on 6 Oct 2026; Three.js
`f27bb5ae1ede1309841e6fc9586cce768a99f9ad` on 6 Oct 2026; DiligentSamples
`03a10e15e69c5ae1738a52175ae029f3f4275a90` on 5 Oct 2026. These show active
upstreams, not approval to consume moving branch heads.

Additional licence evidence inspected:
[bgfx BSD-2-Clause](https://github.com/bkaradzic/bgfx/blob/master/LICENSE), blob
`efd9f778e6e43205c4092a327286379e0de7bbac`, and
[FastNoiseLite MIT](https://github.com/Auburn/FastNoiseLite/blob/master/LICENSE),
blob `dd6df2c160a31fff4e781e9589d56bc29dabb5c0`. Latest commits observed:
bgfx `cca91681c953d2de9531197b0f580c866ffaa775`, 5 Oct 2026; FastNoiseLite
`785f37a9ad76e283586a379675085f2063ae03f7`, 21 June 2026. Prefer the native
C++ references for implementation; web examples are technique references only.

## Delivery and visual acceptance

Implement only after the parent-owned APK gate. Keep two workers at most, with
disjoint files, and run validation in GitHub Actions. An attractive still image
does not close a camera or performance gate.

1. **Inventory and baseline:** classify every existing scene's camera/space type;
   record current preset IDs, framing, controls and sample recordings. Prepare
   saved-look compatibility fixtures before adding camera adapters.
2. **Camera foundation:** C++ state/constraints/director, bounded organic intent,
   picking, reduced motion and recorder boundary. Add host regression sources
   for continuity, limits, degenerate look-at, curve joins, angle wrap, stale
   events and stalled frames. Fixed entropy exists only in tests.
3. **One complete spatial prototype:** the owner-reference bead/fractal corridor
   and Prismatic Current camera foundation through current GLES or the approved
   Diligent prototype. Prove genuine spatial parallax, live motion, touch,
   context recovery and actual performance recording before multiplying styles.
4. **Retained adapters:** port one or two raymarch styles per review, then fluid
   layer depth. Keep projectM's planar/preset contract honest. Coordinate the
   UI capability schema and remove every exposed inert camera control.
5. **Signature art passes:** implement the remaining five scenes, one or two per
   review, with explicit camera/material/audio/quality mappings. Review them in
   monochrome and bloom-off as well as their intended colour treatment.
6. **Recorded take and Studio:** capture the composed output, synchronized audio
   and metadata; retain actual camera/transition outcomes; support cancellation,
   reframe/crop and export without regenerating the recording.
7. **Additive catalogue integration:** expose accepted new styles alongside all
   existing styles. Preserve IDs and saved behavior; offer previewed, versioned
   upgrades to current scenes without deleting originals, assets or presets.

For every style, attach three evidence sheets and a short motion review:

| Evidence | Required contents |
|---|---|
| **Art sheet, 3×3** | Rows: restrained default, strongest intended look, reduced motion. Columns: opening composition, active musical passage, touch interaction. Include a bloom-off inset and silhouette view. |
| **Capability sheet** | Five macros at minimum/middle/maximum; portrait, landscape and square framing; low/high tier; no cropped subject, exposed card edge or ineffective control. |
| **Continuity strip** | 12 sampled frames across a realized move/transition, plus position/speed/acceleration/roll traces. Show an unexpected source stop and user interruption. |
| **Recorded motion clips** | 20-second silence, sparse rhythm and dense music clips; 60-second free exploration; a recorded take exported through Studio. Use rights-cleared fixtures. |

Required rejection checks: near-wall clipping, sudden horizon roll, repeated
random jumps, camera motion after Motion zero, audio modulation after Response
zero, hidden rate changes from an orientation gesture, loss of focus after
aspect-ratio change, white featureless bloom, sparkle aliasing, obvious repeated
live camera loops and a replacement image masquerading as a recorded take.

Collect actual frame/GPU timing, peak memory and a 20-minute thermal run on named
devices. Retain the blueprint's provisional p95 targets of 16.7 ms at 60 fps or
33.3 ms at 30 fps, with graceful quality reduction and no audio interruption.
Test 30/60/120 Hz displays, background/resume, context recreation, source changes
and gesture cancellation. The same completed performance should keep its camera
state across those lifecycle events, while a newly generated session remains
free to evolve differently.

Final art approval requires the user to recognise each scene from its shape and
movement without seeing its name. Final camera approval requires organic
variation that stays composed, controllable and comfortable in the reviewed
settings. Neither approval is established by this document alone.
