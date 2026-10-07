# R1 — Visual code sources and new 3D style concepts

Checked 7 October 2026. Read-only research; nothing in the repo was modified, compiled or run.

**How licences were verified.** Each LICENSE file below was fetched from `raw.githubusercontent.com/<owner>/<repo>/HEAD/<file>` on 2026-10-07 and read (first lines plus a keyword classification; sha256 prefix recorded so the exact text can be re-checked). HEAD commit SHAs were **not** resolved. Pin a commit and re-hash before importing anything (ledger rule in `docs/visualizer-v2/provenance.json`). Copies are under `scratchpad/dl/<owner>__<repo>/`. Anything I could not read from a primary source is marked **UNVERIFIED**.

**Repo context read** (branch `origin/codex/account-visual-foundation`, PR #9, via `git show`):
- `SpatialCameraDirector` takes bass/mid/treble/energy/section signals. Corridor geometry and camera share one equation, and `prismatic_passage_frag.glsl` contains rings, beads, helical filaments and folded sculptures.
- `ReactiveAnalyzer.hpp` already exposes `beat, beatStrength, beatPhase, barPhase, beatInBar, downbeat, downbeatConfidence, sectionBoundary, tempoStability, onset`.
- Shaders already shipped or in the PR: `rod_tunnel` (raymarched bead-chain corridor), `kifs` (crystal cathedral), `noneuclid` (box fold plus sphere inversion lattice), `vanishing` (volumetric Droste), `bead_vortex` (2D polar tunnel), `nebula`, `starfield`, `aurora`, `curl_bloom`, `fractal_temple`, `silk`, `water_ink`, and a PavelDo-derived fluid with `fluid_particle_*`.
- The provenance ledger already holds 39 sources, including glChAoS.P, threelab, vgalizer, acidcam-gpu, webgl-fluid, swissgl, colourful-attraction, ShaderEditor and fosfora. I reuse them below and do not re-research them.
- Uniform vocabulary used in the audio mappings: `uBass uMid uTreble uEnergy uBeat uBeatPhase uBloom uWarp uKaleido uTwist uSteps` and `Params.marchDetail`.
- Gap this report targets: the existing 3D scenes are mostly static-form raymarch scenes. There is no fractal flight-through, no 3D GPU particle system with a perspective camera, no spectrogram-as-geometry, and no depth-bearing fluid.

---

## A. Reference look → techniques

### A.1 What the owner's apps do (evidence level in brackets)

| Look | Evidence | Core techniques | Geode today | Gap |
|---|---|---|---|---|
| **Trance 5D** (Mobile Visuals): kaleidoscopic 3D trance, flying camera | Play listing text: "3 stunning music visualizers", live wallpaper; the store search summary says 29 colour themes, 6 backgrounds, 3D gyroscope [listing-level, not tested]. https://play.google.com/store/apps/details?id=mobilevisuals.trance.vj.musicvisualizer | Raymarched KIFS/Mandelbox/Kali folds; angular mirror-fold of ray directions; orbit-trap palette; depth fog plus glow; gyro-steered camera | `kifs`, `noneuclid`, 2D `kaleido` | No forward flight through a fold-fractal; no audio-modulated fold constants; no gyro or camera programs |
| **Tunnel app**, "37 Tunnels / 10 Backgrounds / 3D toggle / ± speed / 100+ options" | The exact app is **UNVERIFIED**. The Mobile Visuals tunnel family listings I saved describe: Morphing Tunnels (`tunnel.dimf`, "animated 3D tunnels … gyroscope … ± buttons"), Astral Tunnels (`tunnel.astral`, "15 tunnels for meditation", named tunnels such as Magnetic and Self-aware), Fractal Tunnels (`fractal.tunnels`, "tunnels inspired by rivers, lightning, crystals"), Astral 3D FX (`astral.teffexf`, "13 visualizers: tunnels, fractals, space journeys"). Package ids are play.google.com/store/apps/details?id=… The "37" and "10" counts were not confirmed. The meaning of "3D toggle" (parallax, stereo or gyro) is **UNVERIFIED**. | Polar/log-polar 2D tunnels; SDF tunnels with domain repetition; spline-path tubes; Truchet lattices; ring/torus gates; fractal/organic wall displacement | `rod_tunnel`, `bead_vortex`, `tunnel`, Prismatic Passage | Only bead-style walls. Missing: audio-as-geometry walls, branching route tunnels, discrete gate sequences, non-Euclidean throats, nature-inspired fractal walls |
| **De-Stress particle visuals** | The app itself is not identified. Category analogues found: Triple A (https://apps.apple.com/app/id967650266, "up to 30,000 particles … 60 FPS", store claim) and Atomus (12,000 particles, store claim) [UNVERIFIED claims] | Divergence-free (curl or bitangent) flow fields; GPU ping-pong particles; additive soft sprites; depth-of-field sprite scaling; long-persistence trails; touch wake | `curl_field`, `curl_bloom` (raymarched), `bead_vortex` | No 3D particle volume with real depth parallax or DOF |
| **Fluids Particle Simulation LWP** (MKGames) | https://play.google.com/store/apps/details?id=com.MKGames.FluidsSounds, summarised in `REFERENCE_APPS.md` | Stam stable fluids, PavelDo splats and vorticity, particles advected by the velocity grid; SPH/PBF/MPM for volumetric liquids | `fluid_*`, `fluid_particle_*` (2D) | Flat, with no perspective camera or depth ordering |

### A.2 Technique → best code source (permissive first)

| Look family | Technique | Port from (licence) | Technique-only references |
|---|---|---|---|
| Polar tunnel | angle/log-radius coordinates, 1/r depth; polar repeat | hg_sdf `pModPolar` (MIT elected), existing `lib_sdf3` | Shadertoy tunnels (CC BY-NC-SA default, see C) |
| SDF/raymarched tunnel | domain repetition, bounded displacement, smooth ops | hg_sdf (MIT elected); raylib `raymarching.fs` carrying iq's MIT header (read); SebLague/Ray-Marching (MIT) | iq articles (licence **UNVERIFIED**, see C) |
| Spline-path tunnel | Catmull-Rom plus rotation-minimising frame, banking | three.js `Curve.js` / `CatmullRomCurve3.js` (MIT); GLM `gtx/spline` (Happy Bunny OR MIT per `copying.txt`, read; the `spline.hpp` filename is from memory, not checked) | Wang et al. 2008 "Computation of rotation minimizing frames" (from memory, URL not checked) |
| Truchet | per-cell oriented tori/pipes; DDA cell stepping | hg_sdf `pMod3` for cell repeat; no permissive Truchet shader found | Carlson, "Multi-scale Truchet patterns" (Bridges 2018, from memory) |
| Ring/torus gates | instanced torus/polygon tubes on a path | three.js (MIT) path helpers; own instanced mesh | none needed |
| KIFS / Mandelbox / Mandelbulb | distance-estimated fold iteration, orbit traps | FractalView (MIT, formulas for Mandelbulb, Juliabulb, Mandelbox, KIFS per its README); mandelbulb-xr (MIT); hg_sdf folds | Fragmentarium and Mandelbulber2 (GPL), Hvidtfeldt series https://blog.hvidtfeldts.net/index.php/category/kaleidoscopic-ifs/ |
| Kali set / Star Nest | volumetric Kali iteration `p=abs(p)/dot(p,p)−c` | Star Nest header "MIT License" (mirror read, see B) | none |
| Apollonian / Kleinian | sphere inversion with fold | already approximated by `noneuclid` | iq's Apollonian write-ups |
| Strange attractors | ODE integration on GPU state texture | glChAoS.P (BSD-2, ledger, `license.txt` read), threelab (MIT, ledger), SoundVisualizer parameter sets (MIT) | Paul Bourke catalogue (not fetched) |
| Curl / flow particles | divergence-free noise advects ping-pong particles | bitangent_noise (MIT, GLSL included); Curl_Noise (MIT); three.js `GPUComputationRenderer` (MIT) | Bridson 2007 https://www.cs.ubc.ca/~rbridson/docs/bridson-siggraph2007-curlnoise.pdf; DeWolf 2005 |
| Flocking | boid rules plus grid hash on GPU | three.js `webgl_gpgpu_birds.html` (MIT); SebLague/Boids (MIT); unity3d-jp/BoidComputeShader (MIT) | — |
| Galaxy | log-spiral arms plus thickness plus dust | dgreenheck/webgpu-galaxy (MIT) | — |
| Stable-fluid particles | velocity grid advects particles | webgl-fluid (MIT, ledger); keijiro/StableFluids (Unlicense); touchFluid/Melange (MIT); GodotFluid 3D (MIT) | NVIDIA GPU Gems ch.38 (http://developer.download.nvidia.com/books/HTML/gpugems/gpugems_ch38.html) |
| Volumetric liquid | MLS-MPM or SPH plus screen-space fluid rendering | WebGPU-Ocean (MIT); SebLague/Fluid-Sim (MIT); taichi_mpm (MIT, reference) | Hu 2018 MLS-MPM https://yzhu.io/publication/mpmmls2018siggraph/paper.pdf; Müller 2003 SPH https://matthias-research.github.io/pages/publications/sca03.pdf; nialltl guide https://nialltl.neocities.org/articles/mpm_guide; Hoetzlein fast fixed-radius NN https://ramakarl.com/pdfs/2014_Hoetzlein_FastFixedRadius_Neighbors.pdf |
| Backgrounds | procedural nebula/stars; Rayleigh+Mie sky | space-3d (Unlicense); glsl-atmosphere (Unlicense); Star Nest (MIT); three.js `Sky.js` (MIT) | nimitz-style aurora (Shadertoy default licence) |
| Bloom | soft-knee prefilter then mip down/up chain | Godot `glow.glsl` (MIT, GLES3 path); Filament `bloomDownsample/Upsample.mat` (Apache-2.0); pmndrs `bloom.frag` (zlib) | Jimenez, COD:AW 2014; Bjørge dual filtering, SIGGRAPH 2015 (both from memory) |
| DOF | circle-of-confusion gather or sprite scaling | Godot `bokeh_dof.glsl` (MIT); Filament `dof.mat` (Apache-2.0); pmndrs `depth-of-field.frag` (zlib) | — |
| Chromatic aberration, trails, motion blur | edge-weighted RGB split; feedback decay; velocity blur | pmndrs `chromatic-aberration.frag` (zlib); three.js `RGBShiftShader`, `AfterimageShader` (MIT); KinoMotion (MIT, Unity HLSL) | — |

---

## B. Source repos table (verified licences)

Effort estimates are mine: S ≈ 1–3 dev-days, M ≈ 1–2 weeks, L ≥ 3 weeks for one engineer including GLES port and tests. "Allowed" means the licence is on the allow-list in the brief.

### B.1 Tunnels, SDF, fractals, backgrounds

| Name | URL | Licence (file read) | Language / API | Technique | What to port | Effort |
|---|---|---|---|---|---|---|
| hg_sdf (Mercury) | https://mercury.sexy/hg_sdf/ and https://mercury.sexy/hg_sdf/hg_sdf.glsl | `hg_sdf.glsl` header: "SPDX-License-Identifier: MIT OR CC-BY-NC-4.0", version 2021-07-28, © 2011–2021 Mercury Demogroup. sha256 prefix `bbab07b84d1c` (27,212 B). **Elect MIT, and record that election.** Not a git repo, so pin the file hash. | GLSL | polar/mirror/grid repetition, reflection folds, round booleans | `pModPolar`, `pMod1/2/3`, `pModMirror1/2`, `pR`, `pReflect`, `fOpUnionRound` (all confirmed present in the file) | S |
| FractalView | https://github.com/adamsol/FractalView | `LICENSE.txt`, MIT, © 2019 Adam Sołtysik, sha `a4358db088c1` | GLSL in Electron/three.js | DE ray-march of Mandelbulb, Juliabulb, Mandelbox, KIFS; orbit-trap colouring (README) | the DE formulas and orbit-trap colouring. Exact shader path not located (`src/renderers/`, UNVERIFIED) | S–M |
| SebLague/Ray-Marching | https://github.com/SebLague/Ray-Marching | `LICENSE`, MIT, © 2019 Sebastian Lague, sha `24f060a992cf` | Unity HLSL compute (`Assets/Scripts`, `Assets/Scenes`) | SDF blend/cut ops, Mandelbulb | op library only; project is "unoptimized and incomplete" per its page (WebFetch summary) | S |
| raylib `raymarching.fs` | https://github.com/raysan5/raylib/blob/master/examples/shaders/resources/shaders/glsl100/raymarching.fs | **File header**: "The MIT License, Copyright © 2013 Inigo Quilez" (read). Repo `LICENSE` is zlib, sha `882a5a819cf5`. The repo licence does not cover this file. | GLSL ES 100 | iq SDF primitives plus raymarch loop | reference for primitives and normals | S |
| Star Nest (Pablo Román Andrioli, "Kali") | https://www.shadertoy.com/view/XlfGRj | Header reads "This content is under the MIT License." I read this in a mirror, https://arcade3.readthedocs.io/en/latest/tutorials/shader_toy_glow/star_nest.html, and in a saved copy `dl/star_nest.glsl`. The Shadertoy page returned 403 to the proxy, so **UNVERIFIED at source**. The header has no copyright year or holder line, so ask for a fuller notice before shipping. | GLSL | volumetric Kali-set march, 17 fold iterations × 20 volume steps | whole shader is small; reduce steps for mobile | S |
| mandelbulb-xr | https://github.com/ibrews/mandelbulb-xr | `LICENSE`, MIT, © 2026 Alex Coulombe / Agile Lens, sha `522575974965`. README says it follows iq's Mandelbulb work. Young repo, 1 star. | GLSL | portable Mandelbulb core | secondary to FractalView | S |
| KaleidoscopeEnhanced | https://github.com/reneweller-coding/KaleidoscopeEnhanced | `LICENSE`, MIT, © 2026 reneweller, sha `78e69d45e429`. **Carve-out**: ChromeDreams, DiscoGodrays, FlowingWires, FractalBloom, InsideSystem, NeonTubes, PsychedelicPills, SphereGrid, TheCore, Vortex and Voyager are CC BY-NC-SA 4.0 (kishimisu). | GLSL 330 core, OpenGL 4.3, Qt6 | 865 scenes (README) or 890 (catalogue header), raymarched flights, audio-feature library | **Idea source** (`docs/Catalog/Katalog.md`, names and header descriptions). Port code only after reading each file header. 2026 repo with 5 stars, and per-file provenance is not independently verified. | M |
| space-3d | https://github.com/wwwtyro/space-3d | `LICENSE`, Unlicense, sha `60d2c28d19d2` | WebGL/GLSL | procedural nebula, stars, sun skybox | nebula and star layer functions | S |
| glsl-atmosphere | https://github.com/wwwtyro/glsl-atmosphere | `LICENSE`, Unlicense, sha `60d2c28d19d2` | GLSL | Rayleigh+Mie single-scatter sky | direction-to-colour sky function | S |
| three.js `Sky.js` | https://github.com/mrdoob/three.js (`examples/jsm/objects/Sky.js`, path confirmed 200) | repo `LICENSE`, MIT, sha `8b378ebe60e2` | GLSL | Preetham sky | cheap sky background | S |
| precomputed_atmospheric_scattering | https://github.com/ebruneton/precomputed_atmospheric_scattering | `LICENSE`, BSD-3-Clause, © 2017 Eric Bruneton, sha `530cf0bc2bf9` | GLSL, C++ | LUT sky | **ultra only**, optional | L |
| UnrealEngineSkyAtmosphere | https://github.com/sebh/UnrealEngineSkyAtmosphere | `LICENSE`, MIT, © 2020 Epic Games, sha `62891c551d48` | HLSL/D3D11 | LUT sky | skip for mobile | L |

### B.2 Particles, noise, flocking, camera

| Name | URL | Licence (file read) | Language / API | Technique | What to port | Effort |
|---|---|---|---|---|---|---|
| bitangent_noise | https://github.com/atyuwen/bitangent_noise | `LICENSE`, MIT, © 2021 Yuwen Wu, sha `201b9af79444` | **GLSL and HLSL provided** (README) | cheaper divergence-free noise (DeWolf 2005) | `BitangentNoise3D` for particle flow | S |
| Curl_Noise | https://github.com/kbladin/Curl_Noise | `LICENSE`, MIT, © 2019 Kalle Bladin, sha `35ac312b7d52` | C++/OpenGL | GPU curl-noise particles (Bridson 2007, ashima simplex) | pass structure and particle seeding | S |
| three.js | https://github.com/mrdoob/three.js | `LICENSE`, MIT, sha `8b378ebe60e2` | JS/GLSL/TSL | `GPUComputationRenderer.js`, `webgl_gpgpu_birds.html`, `Curve.js` (`computeFrenetFrames`), `CatmullRomCurve3.js`, `webgpu_tsl_galaxy.html`, `webgpu_compute_particles.html` (all paths returned 200) | ping-pong GPGPU pattern, flocking shaders, spline rails. **Caution:** some example shaders are third-party ports, e.g. `KaleidoShader.js` is "Ported from pixelshaders.com by Toby Schachman" and `BokehShader.js` is ported from a Martins Upitis blog post. Their original licences are not stated in the file, so treat those as technique-only. | S–M |
| SebLague/Boids | https://github.com/SebLague/Boids | `LICENSE`, MIT, © 2019 Sebastian Lague, sha `24f060a992cf` | Unity HLSL compute | boid rules on GPU | rules and constants | S |
| Unity-Boids-Behavior-on-GPGPU | https://github.com/chenjd/Unity-Boids-Behavior-on-GPGPU | `LICENSE`, MIT, © 2017 jiadong chen, sha `17fc557f9974` | Unity HLSL compute | GPU boids | reference | S |
| BoidComputeShader | https://github.com/unity3d-jp/BoidComputeShader | `LICENSE`, MIT, © 2022 Unity Technologies Japan, sha `62d7f5ac00db` | Unity HLSL compute | boids with grid | grid-neighbour approach | S |
| webgpu-galaxy | https://github.com/dgreenheck/webgpu-galaxy | `LICENSE`, MIT, © 2025 dgreenheck, sha `6acbfdfb1da7` | TSL/WGSL compute | spiral-arm generation, dust clouds, up to 750k particles, bloom (README) | arm/thickness/dust parameter model → GLSL ES 3.0 | M |
| SoundVisualizer (CAYADEV) | https://github.com/CaYatur/SoundVisualizer | `LICENSE`, MIT, © 2026 Çağan Turgut, sha `ef6f6a4ae949` | Electron/JS and GLSL | 27 strange attractors (Lorenz, Rössler, Chen, Halvorsen, Thomas, Aizawa, Chua, Dadras …), 13 3D solids, bounding-box probing to frame attractors, modulation matrix (README) | attractor parameter tables and framing idea. No THIRD_PARTY file found at root. | S–M |
| glChAoS.P | https://github.com/BrutPitt/glChAoS.P | `license.txt` (lowercase), "BSD 2-Clause License, © 2018-2024 Michele Morrone". It is already in the ledger. My script missed it because of the filename case, so the ledger filename should read `license.txt`. | C++/OpenGL | attractor point-sprite glow | already approved | — |
| three.js curves / GLM spline | https://github.com/g-truc/glm (`copying.txt`, 200) | `copying.txt`: "GLM is licensed under The Happy Bunny License or MIT License" | C++ | Catmull-Rom/Hermite | optional, since Catmull-Rom is ~20 lines | S |

### B.3 Fluids

| Name | URL | Licence (file read) | Language / API | Technique | What to port | Effort |
|---|---|---|---|---|---|---|
| WebGPU-Ocean | https://github.com/matsuoka-601/WebGPU-Ocean | `LICENSE`, MIT, © 2025 matsuoka-601, sha `7f265ec09ad9` | WGSL/WebGPU | MLS-MPM (~100k particles on iGPU per README), SPH with fixed-radius grid, screen-space fluid rendering | P2G/G2P pass structure, fixed-point atomics, SSFR smoothing → GLSL ES 3.1 compute | L |
| SebLague/Fluid-Sim | https://github.com/SebLague/Fluid-Sim | `LICENSE`, MIT, © 2023, sha `9e434653b48f` | Unity compute | particle SPH with spatial hashing; cites Müller and NVIDIA papers | spatial hash and sort, density/pressure kernels (2D vs 3D scope **UNVERIFIED**) | M |
| GodotFluid | https://github.com/efirdc/GodotFluid | `LICENSE`, MIT, © 2019 Cory Efird, sha `5d76f001c142` | GLSL (Godot 3.1) | 3D Eulerian stable fluid (GPU Gems ch.38) | 3D advect/project passes | M |
| StableFluids | https://github.com/keijiro/StableFluids | `LICENSE`, Unlicense, sha `6b0382b16279` | Unity HLSL | compact Stam solver | reference | S |
| touchFluid / Melange | https://github.com/kamindustries/touchFluid, https://github.com/kamindustries/Melange | `LICENSE`, MIT, © 2015-16 Kurt Kaminski, sha `b32214f8601c` | TouchDesigner + GLSL | audio/gesture-driven fluid instrument | audio→splat design | S |
| three-fluid-demo | https://github.com/Experience-Monks/three-fluid-demo | `LICENSE.md`, MIT, © 2016 Jam3, sha `4ebf74995ce5` | three.js/GLSL | grid fluid | reference | S |
| AminAliari/fluid-simulation | https://github.com/AminAliari/fluid-simulation | `LICENSE.md`, MIT, © 2022, sha `a77a677442ac` | GPU SPH (language **UNVERIFIED**) | SPH | reference | S |
| jeantimex/fluid | https://github.com/jeantimex/fluid | `LICENSE.txt`, MIT, © 2026, sha `b62b466a3809` | WebGPU (per search snippet, content not read) | SPH plus 3D PIC/FLIP | reference | S |
| taichi_mpm / taichi | https://github.com/yuanming-hu/taichi_mpm (MIT, sha `56a5e8e12587`), https://github.com/taichi-dev/taichi (Apache-2.0, sha `c71d239df917`) | `LICENSE` files read | C++/Python | MLS-MPM | study and test oracle | M |
| SPlisHSPlasH, fluid-engine-dev | https://github.com/InteractiveComputerGraphics/SPlisHSPlasH (MIT, sha `608181acd95c`), https://github.com/doyubkim/fluid-engine-dev (MIT, `LICENSE.md`, sha `ce03615c5dc7`) | read | CPU C++ | PBF, FLIP, SPH | **ORACLE tier**: offline reference values, never shipped | M |

### B.4 Post effects

| Name | URL | Licence (file read) | Language / API | Technique | What to port | Effort |
|---|---|---|---|---|---|---|
| Godot | https://github.com/godotengine/godot | `LICENSE.txt`, MIT, sha `b0435e3b3e4e`. Paths confirmed 200: `drivers/gles3/shaders/effects/glow.glsl`, `drivers/gles3/shaders/effects/post.glsl`, `servers/rendering/renderer_rd/shaders/effects/bokeh_dof.glsl`, `.../taa_resolve.glsl` | GLSL (GLES3 backend for glow) | glow chain, DOF, TAA resolve | GLES3 glow and bokeh DOF | S–M |
| Filament | https://github.com/google/filament | `LICENSE`, Apache-2.0, sha `02d70b593dd8`. No root `NOTICE` or `NOTICE.md` (404), so check subdirectories. Paths confirmed 200: `filament/src/materials/bloom/bloomDownsample.mat`, `bloomUpsample.mat`, `dof/dof.mat`, `colorGrading/colorGrading.mat` | GLSL inside `.mat` | mobile-oriented bloom and DOF | algorithms; Apache needs licence text plus a statement of changes | M |
| pmndrs/postprocessing | https://github.com/pmndrs/postprocessing | `LICENSE.md`, zlib, © 2015 Raoul van Rüschen, sha `b7650918449b`. Paths confirmed 200: `src/effects/glsl/chromatic-aberration.frag`, `bloom.frag`, `depth-of-field.frag` | GLSL | small, self-contained effects | CA, bloom, DOF fragments | S |
| KinoBloom, KinoMotion | https://github.com/keijiro/KinoBloom (MIT, `LICENSE.md`, sha `ee32f9c1de52`), https://github.com/keijiro/KinoMotion (MIT, `LICENSE.txt`, sha `aa7959134513`) | read | Unity HLSL | bloom, motion blur | translate HLSL→GLSL | S–M |
| Utilities | glsl-fast-gaussian-blur (MIT, sha `fb153e8eaf70`), glsl-fxaa (MIT, sha `91515553fda3`), stegu/webgl-noise (MIT, sha `bdafce1bb015`; psrdnoise already in repo) | read | GLSL | blur, AA, noise | as needed | S |
| bgfx | https://github.com/bkaradzic/bgfx | `LICENSE`, BSD-2-Clause, sha `ac2e0d253b15`. `examples/09-hdr/hdr.cpp` confirmed 200. | C++ plus bgfx shader language | HDR→bloom→tonemap pipeline | reference only | S |
| ARM OpenGL ES SDK for Android | https://github.com/ARM-software/opengl-es-sdk-for-android | `LICENSE`, "SPDX-License-Identifier: MIT", © 2012-2017 ARM, sha `a3ded46d7260`. README says "not maintained anymore". Sample list not enumerated (**UNVERIFIED**). | C++/GLES | Mali sample patterns | only if a specific sample is needed | — |

### B.5 Open-source music visualizers with strong 3D work

| Project | URL | Licence (file read) | Stack | 3D technique worth studying |
|---|---|---|---|---|
| CAYADEV Visualizer (SoundVisualizer) | https://github.com/CaYatur/SoundVisualizer | MIT (`LICENSE`) | Electron, WebGL | 27 strange attractors and 13 3D solids with own matrix maths, tunnels, MilkDrop engine, 42 GLSL shaders, modulation matrix from audio features, auto-VJ scene changes, clip deck. 10,347-preset MilkDrop test claim is README-level. |
| Kaleidoscope Enhanced | https://github.com/reneweller-coding/KaleidoscopeEnhanced | MIT with the 11-shader CC BY-NC-SA carve-out (above) | Qt6, OpenGL 4.3 | Raymarched flights and tunnels, tessellated terrain, 3D models as mirrors and shadow puppets, stereo-3D output, beat/key/mood/structure tracking driving scene choice. Catalogue names to mine for ideas: `SpectrogramTunnel`, `BinauralTunnel` (a tunnel whose cross-section is the stereo image), `HyperbolicPoincareTunnel`, `MoebiusTunnel`, `KleinBottleFlythrough`, `ApollonianGasketNebulaFlight`, `MandelboxHyperCubeMetamaterial`, `QuaternionicJulia4DFlight`, `StargateWormhole`, `HelicoidMinimalSurfaceTunnel`. Names and header text only. |
| phase-viz | https://github.com/7g3n/phase-viz | MIT (`LICENSE`, sha `6f9854e28421`) | Three.js / R3F, WebCodecs | 3D visualizer mode with particles; export pipeline; preset schema |
| modV | https://github.com/vcync/modV | MIT (`LICENSE`, sha `4ff9462e56a5`) | Electron, ISF, canvas | modular modules; ISF shaders carry their own licences, so check per file |
| ThreeAudio.js | https://github.com/blurskies/ThreeAudio.js | MIT (`LICENSE.txt`, © 2012 Steven Wittens, sha `464ab367a07f`) | three.js | audio exposed to GLSL as textures plus bass/mid/treble scalars |
| radiance | https://github.com/zbanks/radiance | `LICENSE`: MIT for source "unless indicated in the file", with a CC BY-NC-SA 4.0 clause for flagged data and model files (sha `f5f8c1d0042b`) | OpenGL VJ | shader-chain VJ architecture |
| Bonzomatic | https://github.com/Gargaj/Bonzomatic | `LICENSE`, Unlicense, except parts listed in the README acknowledgements (sha `e6a069dd6a4c`) | live-coding tool | FFT texture contract; STUDY only |
| Already in ledger | projectM (LGPL-2.1, RETAIN), butterchurn (MIT), fosfora (MIT OR Apache-2.0, `LICENSE-MIT` read, sha `1825a9612262`), vgalizer, karmaviz, clubber | — | — | do not re-research |
| Excluded here | glava (GPL-3.0 per search snippet, 2D), bradleybauer/music_visualizer (no LICENSE file), aenlow/Audio-Visualizer (no LICENSE file), BrokenSource/ShaderFlow (no LICENSE file at root) | — | — | technique-level only |

---

## C. Technique-only sources

| Source | Why technique-only | Evidence | Use for |
|---|---|---|---|
| Shadertoy shaders (default) | Default licence is CC BY-NC-SA 3.0 unless the header says otherwise | **UNVERIFIED at primary source** (`shadertoy.com/terms` returned 403). Consistent across search summaries. KaleidoscopeEnhanced's LICENSE independently confirms kishimisu uses CC BY-NC-SA 4.0. | Look targets for tunnels, Truchet flights, aurora (nimitz-style). **Rule: port a Shadertoy shader only if its own header says MIT, CC0 or Unlicense, and record the header text.** |
| iq (Inigo Quilez) articles and shaders | Reported policy: algorithm code MIT, artwork code copyrighted | Only the raylib `raymarching.fs` header is verified. iquilezles.org returned 404 through the proxy, and the policy came from a search summary, so it is **UNVERIFIED**. | Implement the well-known SDF/DE maths independently. Port a file only when its header says MIT. |
| Fragmentarium | No LICENSE file at repo HEAD; widely reported GPL-3 (**UNVERIFIED**) | https://github.com/Syntopia/Fragmentarium | KIFS/Mandelbox technique via the Hvidtfeldt blog |
| Mandelbulber2 | GPL-3.0 (`LICENSE` head: "GNU GENERAL PUBLIC LICENSE Version 3"; sha `589ed823e9a8`) | https://github.com/buddhi1980/mandelbulber2 | formulas as maths only |
| hypVR-Ray | No LICENSE file | https://github.com/mtwoodard/hypVR-Ray | hyperbolic raymarch idea |
| LearnOpenGL | CC BY-NC 4.0 (`LICENSE.md`, sha `b0e8f1c5e4dc`) | https://github.com/JoeyDeVries/LearnOpenGL | bloom/HDR explanations |
| The Book of Shaders | "All rights reserved" (`LICENSE`, sha `34082a07e6a4`) | https://github.com/patriciogonzalezvivo/thebookofshaders | **exclude** |
| spite/codevember-2016 | CC BY 4.0 (`LICENSE`, sha `c4f8f7a6715f`); CC BY is not on the allow-list | https://github.com/spite/codevember-2016 | visual ideas |
| lygia | Prosperity (ledger). shady-tunnel pulls it in as a submodule | https://github.com/creaktive/shady-tunnel (MIT itself, but avoid) | none |
| No-LICENSE-file repos | cabbibo/PhysicsRenderer, chiuhans111/fluidglass, XorDev/GM_Nebula, dodydharma/GPU-Compute-Shader-MPM-Fluid-Simulation, mattatz/unity-lbm-fluid-simulation, takah29/fractal-path-tracer, sagielevy/KIFS_Explorer, matthias-research/pages and tenMinutePhysics, torgarak/sph_vulkan, deni10000/Godot-3D-SPH-Fluid-Simulation | Raw fetch of 16 common licence filenames found nothing at HEAD (a renamed or nested licence file would be missed, so re-check by hand before dismissing a repo). Search snippets claimed MIT for the last two, which is **UNVERIFIED**. | ideas only |
| keijiro/Kino, KinoGlitch, KvantTunnel/Spray/Stream/Wall, NoiseBall, Reaktion | No LICENSE found at HEAD (repos may have moved) | https://github.com/keijiro | look ideas |
| Papers (technique, free to reimplement) | — | Bridson 2007; DeWolf 2005; Müller 2003; Hu 2018; Hoetzlein 2014; (from memory, URLs not checked) Macklin and Müller PBF 2013; van der Laan et al. screen-space fluid rendering 2009; James et al. 2015 "Visualizing Interstellar's Wormhole"; Carlson, Truchet, Bridges 2018; Wang et al. 2008 rotation-minimising frames; Jimenez COD:AW bloom; Bjørge dual filtering | algorithms |

**Provenance hazard.** A permissive repo licence does not cover shaders the repo copied from elsewhere. Examples: three.js `KaleidoShader` and `BokehShader` are ports. KaleidoscopeEnhanced openly carves out 11 NC shaders and may have others. Before any port, read the file header, grep it for `shadertoy|iq|nimitz|kali|ported from|based on`, and apply the original author's licence.

---

## D. Ranked style concepts

### D.0 Ground rules used below

- **Cost classes** are estimates from algorithmic op counts and `STYLE_CATALOG.md` §G budgets, not measurements.
  - **base**: 60 fps at 1080p on a Snapdragon 7-series. About ≤64 march steps (render scale 0.5–0.75 allowed), ≤65k GPU particles, ≤128² grids, ≤4 fullscreen post passes.
  - **high**: Adreno 750 class. About 96 steps, ≤262k particles, 256² grids, 6–8 post passes.
  - **ultra**: needs GLES 3.1 compute and atomics, 1M particles, or a 3D grid or MPM.
- **Camera programs** (shared by all styles): *Rail* (Catmull-Rom plus rotation-minimising frame, banking), *Orbit*, *Chase* (follows a tracer), *Drift* (Lissajous), *Cut* (a section-boundary jump hidden by a flash or feedback smear). `SpatialCameraDirector` today takes bass/mid/treble/energy/section. Beat-quantised swoops need `barPhase`, `downbeat` and `beatInBar` forwarded to it. They exist in `ReactiveAnalyzer.hpp`, but the plumbing to the director is not verified.
- **Reduced motion and flash safety:** roll off, cuts become crossfades, speed capped. Every beat flash goes through the FlashBudget pass (`STYLE_CATALOG.md` §F) and is gated by `tempoStability`.
- **Universal controls** applied to every concept unless a line says otherwise:
  - *palette* = `pal()` / LUT selection, with hue shifts only at `sectionBoundary`;
  - *intensity* = emission plus geometry-deformation amplitude (clamped);
  - *speed* = travel/sim time scale (0 holds);
  - *reactivity* = audio gain and attack/release;
  - *camera motion* = drift/roll/orbit/cut amplitude (0 = locked dolly);
  - *detail* = steps / iterations / particles / grid size, tied to quality tier;
  - *post* = bloom, CA, trails, DOF, grain.
- **Rank** weighs: owner bar (trippy, truly 3D, flying camera), permissive-source readiness, base-class feasibility, distinctness from shipped scenes, and effort.

### D.1 Ranking summary

| # | Concept | Family | Cost | Effort | Primary source (licence) | Owner reference |
|---|---|---|---|---|---|---|
| 1 | Mandelbox Voyage | kaleido-fractal | high (base at 0.5 scale) | M | FractalView (MIT) + hg_sdf (MIT) | Trance 5D |
| 2 | Attractor Chorus | particles | base 65k / high 262k | M | glChAoS.P (BSD-2) + SoundVisualizer (MIT) | Trance, De-Stress |
| 3 | Truchet Labyrinth | tunnel | base | M | hg_sdf (MIT) + Carlson (technique) | tunnel app |
| 4 | Spectral Gallery | tunnel | base | S–M | own + hg_sdf (MIT) | tunnel app |
| 5 | Lumen Eddies | fluid-particle | base | S–M | webgl-fluid (MIT) + StableFluids (Unlicense) | Fluids LWP |
| 6 | Galaxy Choir | particles | base (analytic) / high | M | webgpu-galaxy (MIT) + space-3d (Unlicense) | Trance, De-Stress |
| 7 | Murmuration | particles (flock) | base / high | M | three.js GPGPU birds (MIT) + bitangent_noise (MIT) | De-Stress |
| 8 | Beat Gates | tunnel | base | S–M | three.js Curve.js (MIT) | tunnel app |
| 9 | Kali Starfold Drift | kaleido-fractal | base at 0.5 scale / high | S | Star Nest (MIT, mirror-verified) | Trance 5D |
| 10 | Hyperbolic Throat | tunnel | base–high | M | hg_sdf (MIT); technique otherwise | tunnel app |
| 11 | Bokeh Tide | particles (calm) | base | S–M | bitangent_noise (MIT); Godot DOF (MIT) | De-Stress |
| 12 | Wormhole Transit | tunnel (lensed) | high | M | Star Nest (MIT) + space-3d (Unlicense) | Astral 3D FX |
| 13 | Mobius Ribbon Ride | surface | base | S–M | own maths | Trance 5D |
| 14 | Quaternion Slice Flight | kaleido-fractal | high | M | technique + FractalView (MIT) | Trance 5D |
| 15 | Hypercell Orbit | 4D wireframe | base | S | own maths | Trance 5D |
| 16 | Tide Prism | fluid (3D MPM) | ultra | L | WebGPU-Ocean (MIT) | Fluids LWP |

Counts against the brief: tunnels 5 (Truchet Labyrinth, Spectral Gallery, Beat Gates, Hyperbolic Throat, Wormhole Transit), particles 4, fluid 2, kaleidoscopic-fractal 3. None duplicates Prismatic Passage (beads/filaments/folded sculptures in one corridor) or `rod_tunnel`.

### D.2 Concepts

#### 1. Mandelbox Voyage (kaleido-fractal, high)
- **Look:** Flight inside a Mandelbox. Mirrored slabs and spherical bulges recede into luminous fog. Orbit-trap colouring gives neon-metal banding, and the scale slowly breathes so the chambers open and close.
- **Camera:** Rail steered by the distance estimate: the path samples DE ahead and bends toward the largest void, keeping clearance ≥ a set radius, with gentle roll. Beat gives a 40 ms FOV kick. Each downbeat bar gives a 1.5 s swoop (dolly-zoom plus yaw toward the biggest void). `sectionBoundary` cuts to a new fold-parameter set, hidden by a feedback smear.
- **Audio:** bass → fold scale (e.g. −1.5…−2.2, slewed) and glow. mid → box-fold limit and fold rotation. treble → orbit-trap sparkle and specular. beat → FOV kick and colour pulse. bar → swoop. energy → travel speed.
- **Technique:** Sphere-trace DE for box fold + sphere fold + scale/offset, 8–12 iterations, orbit trap, cone-relaxed steps. Base uses 0.5 render scale with bilateral upscale and 6 iterations.
- **Sources:** FractalView (MIT) for formulas and orbit trap; hg_sdf (MIT); existing `lib_sdf3`. Do not copy Fragmentarium (GPL).
- **Delta:** `kifs` is abs-fold only and `noneuclid` is a lattice. This adds sphere-fold plus scale, flight-through and DE-steered camera.
- **Controls:** palette = orbit-trap LUT. intensity = glow and fold-scale swing. speed = flight rate. reactivity = fold slew. camera = swoop and roll amount. detail = iterations 6–12, steps 48–96, render scale. post = bloom, edge CA, fog depth.
- **Risks:** per-pixel iteration cost on Adreno 6xx; fixed steps and early-out are mandatory (`lib_sdf3` already caps at 128).

#### 2. Attractor Chorus (particles, base/high)
- **Look:** Hundreds of thousands of glowing points trace a strange attractor (Lorenz, Aizawa, Thomas, Dadras, Halvorsen). Three voices (bass, mid, treble) each run their own copy, offset in space. The ribbons are velocity-stretched, tone-mapped, and coloured by speed and age.
- **Camera:** Chase: a tracer particle's position drives the camera with a look-ahead. Slow orbit → dolly through the lobes. Cut on `sectionBoundary` while the attractor parameters morph over one bar.
- **Audio:** bass → time step and one control parameter wobble, bounded to stay chaotic (e.g. Lorenz ρ in 24–30). mid → rotation/shear. treble → sprite size and sparkle. beat → radial impulse. bar → attractor swap.
- **Technique:** RGBA16F/32F position ping-pong (256² = 65k base, 512² = 262k high), RK2 step, instanced quads fetched by `gl_VertexID`, additive HDR, optional feedback decay. Frame by bounding-box probing, as SoundVisualizer does. Ultra is a compute SSBO at 1M.
- **Sources:** glChAoS.P (BSD-2, ledger), threelab (MIT, ledger), SoundVisualizer parameter sets (MIT), three.js `GPUComputationRenderer` pattern (MIT).
- **Controls:** palette = speed/age ramp. intensity = point brightness and trail length. speed = dt scale. reactivity = parameter wobble gain. camera = chase offset and orbit. detail = particle count tier. post = bloom plus trails.

#### 3. Truchet Labyrinth (tunnel, base)
- **Look:** A 3D lattice of cells, each with a randomly oriented quarter-torus or pipe elbow joining face midpoints. Together they form endless neon pipes. Light pulses travel along them with the beat. Everything else is fog and void.
- **Camera:** The camera follows a pipe. Each cell's arc continues the previous tangent, so the path is C1 by construction. At beat boundaries it picks a branch (beat-quantised turns). Roll follows the turn. Speed follows energy. Bars give a longer straight sprint.
- **Audio:** bass → pipe radius pulse and speed kick. mid → pulse wave speed along pipes. treble → bevel highlight and spark. beat → branch decision and flash. bar → palette step and lattice scale change.
- **Technique:** DDA through cells with an analytic torus-arc distance per cell (1–2 cells per step), ≤48 steps. A mesh variant instances quarter-torus pieces. Camera route is precomputed ~64 cells ahead from the hash.
- **Sources:** hg_sdf `pMod3` (MIT). Truchet itself is technique-only (Carlson 2018; Shadertoy versions are NC), so implement from the paper.
- **Delta:** `rod_tunnel` is one curved corridor. This branches and routes.
- **Controls:** palette = pulse colours. intensity = emission. speed = travel. reactivity = pulse gain. camera = roll and turn sharpness. detail = steps and cell neighbourhood. post = bloom, depth fog, mild CA.

#### 4. Spectral Gallery (tunnel, base)
- **Look:** The tunnel wall is the music's spectrogram. Frequency runs around the circumference, time runs along the axis, and amplitude pushes the wall into crystalline ridges. The last ~4 s of sound hangs around and behind you.
- **Camera:** Constant dolly with a light sway. Roll follows stereo balance (L/R). `downbeat` triggers a brief dolly-zoom. Sections change the tunnel radius.
- **Audio:** the spectrogram itself, with bass at the low-angle sector. beat → ridge-edge flash. bar → ring marker. treble → fine surface ripple.
- **Technique:** Cylindrical raymarch with bounded radial displacement read from a 2D audio-history texture (about 128–256 frequency bins × 256 frames, R16F). The displacement bound lets the step size stay safe at ~48 steps. This needs a new history texture; today `uAudioTex` is a single spectrum (UNVERIFIED beyond the uniform list). L/R halves need stereo bands (StereoAnalysis exists on the PR branch; uniform plumbing unverified).
- **Sources:** own implementation. Ideas from KaleidoscopeEnhanced `SpectrogramTunnel` and `BinauralTunnel` (names only). hg_sdf polar helpers (MIT).
- **Delta:** the only style where the geometry is literally the audio, in 3D.
- **Controls:** palette = height ramp. intensity = ridge amplitude. speed = scroll and travel. reactivity = history smoothing. camera = sway and roll. detail = history resolution and steps. post = bloom on ridges.

#### 5. Lumen Eddies (fluid-particle, base)
- **Look:** A stable-fluid grid advects tens of thousands of glowing particles that also carry height and depth. Ribbons curl in a shallow 3D volume under a perspective camera.
- **Camera:** Slow orbit ±20° plus parallax drift. Bar boundaries give a gentle dolly. Touch drags the fluid on a world plane, not the screen.
- **Audio:** bass → large low-frequency splats from orbiting emitters. treble → fine jets. beat → vorticity burst and colour step. bar → emitter pattern change.
- **Technique:** Reuse the shipped fluid solver and `fluid_particle_*`. Add z from `|v|`, curl and noise, instanced velocity-stretched sprites, perspective projection, existing sunrays and bloom.
- **Sources:** webgl-fluid (MIT, ledger), StableFluids (Unlicense), touchFluid/Melange (MIT) for audio→splat design, GodotFluid (MIT) if a true 3D grid is wanted later.
- **Delta:** adds depth, camera and ribbons to the existing 2D fluid. Lowest effort in this list.
- **Controls:** palette = velocity ramp. intensity = splat force and emission. speed = sim rate. reactivity = splat gain. camera = orbit and parallax. detail = grid 128²/256² and particle count. post = bloom, sunrays, trails.

#### 6. Galaxy Choir (particles, base analytic / high sim)
- **Look:** A spiral galaxy of 100–250k stars with a glowing core and dark dust lanes. Density waves travel along the arms.
- **Camera:** A 32-bar arc: fly in from afar, skim the disc, plunge toward the core, pull out. Horizon roll slowly. Cuts on `sectionBoundary`.
- **Audio:** bass → core pulse and differential rotation. mid → arm winding and dust opacity. treble → twinkle. beat → density-wave front along the arms. bar → arc waypoint.
- **Technique:** Base needs no state: star position is an analytic function of seed and time in the vertex shader (log-spiral arms, thickness falloff). Dust is 1–2k alpha sprites. Background from Star Nest or space-3d. High adds a GPU sim with a central potential and curl perturbation.
- **Sources:** webgpu-galaxy (MIT) for the parameter model, space-3d (Unlicense), Star Nest (MIT).
- **Controls:** palette = star temperature ramp. intensity = core and bloom. speed = rotation and travel. reactivity = wave strength. camera = arc amplitude. detail = star count. post = bloom, mild DOF.

#### 7. Murmuration (flock particles, base/high)
- **Look:** 30–60k agents form a smoke-like starling cloud that tears and reforms into ribbons, spheres and rings.
- **Camera:** Chase with look-ahead and slow orbit. On a drop, a fast pass through the flock, then a pull-back.
- **Audio:** bass → cohesion and speed pulse. mid → flow-field swirl strength. treble → shimmer and sprite size. beat → a radial shock that splits the flock. bar → target shape change.
- **Technique:** Texture ping-pong (256² = 65k). Alignment to a bitangent- or curl-noise field plus separation from a coarse 3D density grid (splat to an atlas), avoiding O(N²). Velocity-stretched billboards. Compute path for 262k+.
- **Sources:** three.js `webgl_gpgpu_birds.html` and `GPUComputationRenderer` (MIT), SebLague/Boids (MIT), BoidComputeShader (MIT), bitangent_noise (MIT).
- **Controls:** palette = depth or speed ramp. intensity = sprite brightness. speed = flock speed. reactivity = shock strength. camera = chase offset. detail = agent count and grid size. post = DOF, trails, bloom.

#### 8. Beat Gates (tunnel, base)
- **Look:** Large glass-tube portals (ring, hexagon, star) hang on a looping spline. Passing one flashes the screen and steps the colour. Earlier gates smear into echo trails.
- **Camera:** Rail with rotation-minimising frames and banking from curvature. A swoop between gates on each bar, and a Cut at `sectionBoundary`.
- **Audio:** bar (`downbeat`) → spawn a gate. beat → gate pulse. bass → gate scale. mid → tube twist. treble → edge glints. Gate shape encodes the section.
- **Technique:** 32–64 instanced tube meshes (GLES 3.0 instancing), additive depth-faded glow, feedback echo, radial blur and CA at crossing. Path via Catmull-Rom.
- **Sources:** three.js `Curve.js` / `CatmullRomCurve3.js` (MIT), GLM spline (MIT option), own mesh.
- **Delta:** Prismatic Passage has fine rings in one corridor. This has sparse, large, shape-changing gates, plus a banked spline with swoops and cuts.
- **Controls:** palette = per-gate step. intensity = glow. speed = route speed. reactivity = pulse gain. camera = bank and swoop amplitude. detail = gate count and tube sides. post = echo trails, radial blur, CA (flash-budgeted).

#### 9. Kali Starfold Drift (kaleido-fractal, base at 0.5 scale / high)
- **Look:** A volumetric Kali-set space of folded filaments and star clouds, with deep colour layers and long motion streaks as you fly through it.
- **Camera:** Slow Lissajous drift with a speed surge on the beat, a yaw swing per bar, and roll from mid energy.
- **Audio:** bass → the fold parameter (the shader's `formuparam`, around 0.53 ± 0.05) and brightness. mid → tilt and rotation. treble → dark-matter contrast and saturation. beat → warp-lurch. bar → fold-set change.
- **Technique:** Star Nest style loop (17 iterations × 20 volume steps = 340 fold evaluations per pixel as published). Mobile version: 10–12 steps × 10–12 iterations at half resolution, then upscale.
- **Sources:** Star Nest (MIT header, verified via mirror; confirm at source), space-3d (Unlicense).
- **Delta:** distinct from `starfield` and `nebula` (fbm): this is a fold fractal with real flight.
- **Controls:** palette = layer colours. intensity = star brightness. speed = travel. reactivity = fold swing. camera = drift and yaw. detail = steps × iterations. post = streak blur and bloom.

#### 10. Hyperbolic Throat (tunnel, base–high)
- **Look:** A tunnel whose cross-section is a hyperbolic tiling folded into a tube. The ribbed tile edges glow, and the tiling rotates so the geometry feels impossible.
- **Camera:** Axial dolly. The Möbius rotation of the tiling is the camera motion. A dolly-zoom on each bar.
- **Audio:** bass → inner ring and tile scale. mid → tiling rotation. treble → outer-ring edge glow. beat → tile flash. bar → change of {p,q} tiling.
- **Technique:** Iterated reflection and inversion folds into a fundamental domain (≤12 iterations); fold count gives depth and colour. Base is relief-shaded 2.5D. High extrudes the tiles into an SDF with bevels.
- **Sources:** hg_sdf polar and mirror folds (MIT); hyperbolic tiling itself is textbook maths (hypVR-Ray has no licence, so technique only).
- **Controls:** palette = fold-depth ramp. intensity = edge glow. speed = rotation and travel. reactivity = ring gains. camera = zoom and roll. detail = fold iterations, extrusion. post = bloom, CA.

#### 11. Bokeh Tide (calm particles, base)
- **Look:** 20–60k soft particles drift like plankton in layered depth. Near ones are large bokeh discs and far ones fine specks, rising and falling in slow tides.
- **Camera:** Very slow dolly with parallax drift. Rack-focus pulls on the beat. No hard flashes (reduced-motion friendly).
- **Audio:** bass → tide swell. mid → colour temperature. treble → sparkle on the far layer only. bar → focus sweep. beat → gentle brightness lift only.
- **Technique:** Curl or bitangent flow, circle-of-confusion computed in the sprite vertex shader (sprite size and alpha from depth versus focus), dual-filter bloom. Touch leaves a soft wake.
- **Sources:** bitangent_noise (MIT), Godot `bokeh_dof.glsl` (MIT) and Filament `dof.mat` (Apache-2.0) as CoC references.
- **Controls:** palette = warm/cool pair. intensity = sprite alpha. speed = drift. reactivity = tide gain. camera = parallax. detail = particle count and layers. post = bloom and bokeh softness.

#### 12. Wormhole Transit (lensed tunnel, high)
- **Look:** Through a rotating throat you see a second universe, with an Einstein-ring halo. At a bar boundary you fall through and emerge in a different sky.
- **Camera:** Fall toward the throat radius, pass it, emerge. Return on the next section.
- **Audio:** bass → throat radius. mid → twist of the far sky. treble → star twinkle. beat → ring pulse. bar → traversal.
- **Technique:** Analytic Ellis-wormhole null-geodesic integration (about 24–32 steps per pixel), mapping the exit direction to a procedural sky. Procedural sky evaluated twice, so base needs 0.5 scale.
- **Sources:** James et al. 2015 (technique); Star Nest (MIT); space-3d (Unlicense).
- **Controls:** palette = near vs far sky pair. intensity = lensing strength. speed = fall rate. reactivity = radius gain. camera = approach path. detail = integration steps. post = bloom, halo.

#### 13. Mobius Ribbon Ride (surface, base)
- **Look:** A thick iridescent ribbon (Möbius, trefoil, Klein bottle) with thin-film colour. You ride inside and along it, seeing the twist.
- **Camera:** Rail along the surface centreline, with the twist as roll. Morph to the next surface each bar.
- **Audio:** bass → thickness and pulse. mid → twist. treble → fine ripple. beat → phase-kick colour.
- **Technique:** Vertex-shader parametric mesh (512×64), thin-film LUT from the palettes, DOF.
- **Sources:** own maths. SoundVisualizer's 3D solids list (MIT) as a parameter reference.
- **Controls:** palette = thin-film LUT offset. intensity = ripple and emission. speed = ride rate. reactivity = twist gain. camera = roll. detail = mesh density. post = trails, DOF.

#### 14. Quaternion Slice Flight (kaleido-fractal, high)
- **Look:** A 4D quaternion Julia set sliced by a 3D hyperplane that sweeps through the fourth axis. The fractal blob mutates as you orbit and dive into hollows.
- **Camera:** Orbit and dolly into voids, bar-aligned. A Cut at section boundaries.
- **Audio:** bass → the constant `c` orbit on a small sphere (smooth, to avoid chaos). mid → slice sweep. treble → orbit-trap sparkle. beat → colour pulse.
- **Technique:** DE `0.5·|q|·log|q|/|q'|` over 8–10 iterations, AO from step count, orbit-trap shading.
- **Sources:** technique (Hvidtfeldt blog); FractalView Juliabulb (MIT) as a loose code reference only. Its README lists "Juliabulb", a Mandelbulb-style Julia, which may not be a true quaternion Julia (formula not inspected, UNVERIFIED), so implement the quaternion iteration independently.
- **Controls:** palette = orbit-trap LUT. intensity = glow. speed = sweep rate. reactivity = c-orbit gain. camera = orbit radius. detail = iterations and steps. post = bloom.

#### 15. Hypercell Orbit (4D wireframe, base)
- **Look:** Tesseract, 16-cell and 24-cell wireframes as glowing tubes, rotating through 4D. Their projected shadows swell and turn inside out.
- **Camera:** Orbit plus an occasional dive through the centre cell on a drop.
- **Audio:** bass → xw rotation speed. mid → yz. treble → zw plus edge shimmer. bar → switch polytope.
- **Technique:** 4D rotation matrices in the vertex shader, perspective 4D→3D projection, instanced capsules or thick lines, additive plus bloom.
- **Sources:** own maths. KaleidoscopeEnhanced `HyperDimensionalTesseractTunnel` (name only).
- **Controls:** palette = edge-depth ramp. intensity = glow. speed = rotation rate. reactivity = rotation gain. camera = orbit. detail = tube sides and polytope. post = bloom, trails.

#### 16. Tide Prism (3D fluid, ultra)
- **Look:** 20–40k particles of liquid in a volume, rendered as a refractive, light-absorbing surface with dye colour by velocity and age.
- **Camera:** Orbit and dive toward the surface. Slow-motion splash arcs on beats.
- **Audio:** bass → a piston/wave generator and gravity slosh. treble → surface sparkle and foam. beat → a splash impulse. bar → rotate gravity ~30°. Touch pushes the fluid on a plane.
- **Technique:** MLS-MPM in GLES 3.1 compute (P2G with fixed-point atomics, grid update, G2P), screen-space fluid rendering (depth splat, curvature smoothing, normals, Fresnel, refraction, thickness absorption). High-tier fallback of 10–20k particles at half-resolution rendering. Falls back to Lumen Eddies when compute is missing.
- **Sources:** WebGPU-Ocean (MIT), SebLague/Fluid-Sim (MIT) for an SPH fallback, taichi_mpm (MIT) as the reference, plus the technique papers in C.
- **Risks:** compute and atomics throughput on Adreno, thermal limits, and a 3+ week port. Highest wow and highest risk, so it is ranked last.
- **Controls:** palette = dye ramp. intensity = refraction and absorption. speed = sim rate. reactivity = piston gain. camera = orbit. detail = particles and render scale. post = bloom, DOF.

---

## E. Notes for THIRD_PARTY_NOTICES

**Process (matches the existing ledger rules).** For each import, add an entry to `docs/visualizer-v2/provenance.json` with: id, URL, tier, licence, files, **pinned commit**, licence-file sha256, read date, and a statement of modifications. Add SPDX and origin markers in each ported file and extend `app/src/main/assets/third_party_notices.txt` and `THIRD_PARTY_NOTICES`. HEAD SHAs were not captured in this research, so every row below needs a pin before work starts.

| Proposed ledger id | Tier | Licence and what must ship | Note |
|---|---|---|---|
| `hg-sdf` | ADAPT | Elect **MIT**. Ship the full MIT text with "Copyright (c) 2011-2021 Mercury Demogroup" and keep the `SPDX-License-Identifier: MIT OR CC-BY-NC-4.0` header, noting that MIT was elected. | Not a git repo. Pin by file sha256 `bbab07b84d1c…` (version 2021-07-28). |
| `fractalview` | ADAPT (formulas) | MIT. Copyright line "Adam Sołtysik, 2019" plus permission notice. | Locate the shader file first; confirm there is no third-party header inside. |
| `star-nest` | ADAPT | MIT, per header "Pablo Roman Andrioli". | Header lacks a copyright line and year. Obtain the original page's full header or contact the author before shipping. Verify at shadertoy.com, since I only read a mirror. |
| `raylib-raymarching-iq` | REIMPLEMENT (preferred) or ADAPT | File-level MIT, "Copyright © 2013 Inigo Quilez". The raylib repo zlib licence does not cover it. | Ship the file-level notice if anything is copied. |
| `bitangent-noise` | ADAPT | MIT, © 2021 Yuwen Wu. | GLSL is supplied. |
| `three-js-gpgpu-curves` | ADAPT, per file | MIT, "three.js authors". | Exclude `KaleidoShader`, `BokehShader` and any file carrying a third-party "ported from" note unless that origin is separately verified. |
| `godot-glow-dof` | ADAPT | MIT. Ship both copyright lines from `LICENSE.txt` (read): "Copyright (c) 2014-present Godot Engine contributors (see AUTHORS.md)" and "Copyright (c) 2007-2014 Juan Linietsky, Ariel Manzur". | Port algorithm to GLES 3.0. |
| `filament-bloom-dof` | ADAPT or REIMPLEMENT | Apache-2.0. Ship the licence text, keep copyright notices, **state changes**. No root NOTICE found, but check subfolders. | Re-read the exact `.mat` headers. |
| `pmndrs-postprocessing` | ADAPT | zlib. Mark altered versions, and do not misrepresent origin. | Small fragments. |
| `webgpu-galaxy` | REIMPLEMENT/ADAPT | MIT, © 2025 dgreenheck. | WGSL/TSL to GLSL, so mostly a re-expression. |
| `webgpu-ocean` | ADAPT | MIT, © 2025 matsuoka-601. | The project cites papers and nialltl's article. Credit them in docs. |
| `seblague-boids-fluid` | ADAPT | MIT, © Sebastian Lague 2019/2023. | HLSL to GLSL. |
| `unity3d-jp-boid` | REIMPLEMENT | MIT, © 2022 Unity Technologies Japan. | |
| `keijiro-stablefluids` | ADAPT | Unlicense (no notice legally required; list it anyway). | |
| `space-3d`, `glsl-atmosphere` | ADAPT | Unlicense. | List for transparency. |
| `soundvisualizer-attractors` | REIMPLEMENT | MIT, © 2026 Çağan Turgut. | Take parameter tables, not code. |
| `kaleidoscope-enhanced` | STUDY (ideas); ADAPT only after per-file review | MIT with the CC BY-NC-SA carve-out for 11 named shaders | Do not import any file in the carve-out list. |

**Do not import** (record as STUDY or EXCLUDE in the ledger so nobody rediscovers them): Fragmentarium and Mandelbulber2 (GPL), Shadertoy shaders without an MIT/CC0/Unlicense header, LearnOpenGL (CC BY-NC 4.0), The Book of Shaders (all rights reserved), radiance's CC BY-NC-SA data and model files, any repo with no LICENSE file (list in C), keijiro/Kino and Kvant repos (no licence found), and lygia-derived code (shady-tunnel).

**Unverified items to close before shipping:**
1. The Shadertoy default licence and iq's policy at their primary sites. Both were blocked or 404 through the proxy.
2. The Star Nest header at its source.
3. All HEAD commit SHAs.
4. Exact shader paths in FractalView and SebLague repos.
5. Whether `torgarak/sph_vulkan` or `deni10000/Godot-3D-SPH-Fluid-Simulation` actually grant MIT (no file found).
6. App facts: counts in the tunnel app listing and the "3D toggle" meaning.
7. All performance classes. They are estimates, and a device benchmark is still needed.
