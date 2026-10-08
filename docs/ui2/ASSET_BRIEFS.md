# Geode UI 2.0 asset briefs

The dawn lake supplies the natural setting. Runtime water, wet mineral geometry, transparent navigation lenses and connected filaments supply depth and motion. Original environment mattes contain no interface shapes, so native controls remain sharp, reusable and accessible.

## Original environment assets

| Asset | Composition | Exact repository asset |
| --- | --- | --- |
| `ui2_lake_dawn.png` | Quiet portrait lake, pale open sky, misty forest depth, distant horizon near 37%, restrained wet stones and corner ferns. | 1024 × 1536 RGB PNG; 2,020,956 bytes |
| `ui2_lake_dawn_landscape.png` | Newly composed wide lake matching the portrait light and material palette; level distant shore near 44%, wide quiet water and natural shore framing. | 1536 × 1024 RGB PNG; 2,176,999 bytes |

Both are original built-in image-generation outputs copied exactly into `app/src/main/res/drawable-nodpi/`, without resizing, cropping, stretching or visual postprocessing. The landscape is a new composition using the portrait and video frame as visual references. Full prompts, provenance, file hashes and metadata are recorded in `ASSET_MANIFEST.json`.

Neither scene contains a geode, navigation lens, control, particle, light filament, interface text or watermark. Source reflections belong to the distant environment. The renderer separately intersects a real water plane, shades changing normals and reflects the mineral volume.

The renderer loads the appropriate original scene on the GL thread. Portrait remains the selected asset for tall and square surfaces; the matching landscape supports wide surfaces. The orientation-aware texture replacement is implemented. Checked-in software captures describe the earlier shader; current-candidate captures are pending GitHub Actions. CPU bitmap memory is recycled after upload. The current decode uses `inSampleSize=2`, yielding 512 × 768 portrait and 768 × 512 landscape GPU source textures. Native still fallbacks select the scene using their actual available bounds.

## Implemented runtime families

| Family | Actual representation | Source |
| --- | --- | --- |
| Lake water | Perspective ray-plane intersection; procedural normals; independent environment/geode reflection; projected near-shore colors and onset ripple. | `app/src/main/assets/ui2/world_frag.glsl` |
| Geode | Raymarched leaning chipped mineral shell, tall cavity, three faceted quartz crystals, restrained cyan seams, object-local grain/moss and water reflection. | `world_frag.glsl` |
| Navigation lenses | Finite analytical entry/exit refraction using one index, Fresnel and actual exit-point thickness; native measured anchors. Player aligns the real geode instead of drawing a duplicate glass sphere. | `world_frag.glsl`; `ui/lake/LakeNavigation.kt` |
| Connected effects | Four bounded curved world-space filaments from the geode to route nodes, with eight segments per path; soft orbit motes. | `world_frag.glsl` |
| Native glass components | Pale frost panels, controls, press/focus/selection states, sliders and a code-drawn seek thumb. | `ui/lake/LakeMaterials.kt`; `Crystal.kt`; `CrystalControls.kt`; `PlayerPanels.kt` |
| Semantic icons | Existing sharp vector symbols, with a small local frost backing inside live optical lenses to preserve contrast over dark refracted shore. | `ui/theme/StoneIcon.kt`; `ui/lake/LakeNavigation.kt` |
| Native geode handle/fallback | Deterministic six-facet Canvas mark for compact handles and renderer-unavailable navigation. | Private `LakeGeodeMark` in `LakeNavigation.kt` |
| Press effects | Event-driven pressure and bounded native ripple, with reduced-motion handling. | `ui/theme/StoneSurface.kt`; `TidalGlass.kt` |
| Audio accents | Independent beat/transient edge latch, constant-size pending maximum, consumed once by a world frame; reset and stale-event suppression. | `ui/world/WorldAudioMailbox.kt`; `LakeWorldRenderer.kt` |

The native bridge lives in `app/src/main/java/dev/geode/ui/world/`: `NativeWorldBackdrop.kt`, `LakeWorldView.kt`, `LakeWorldRenderer.kt`, `WorldShaderProgram.kt`, `WorldSceneSnapshot.kt` and `WorldAudioMailbox.kt`. The vertex shader is `app/src/main/assets/ui2/world_vert.glsl`.

The GL view owns the live world clock, its context resources and bounded render pacing. The Living Lake material theme supplies static zero scene time. Native text, hit targets, lists, input and screen state remain outside the shader. The renderer consumes small scalar snapshots from the existing audio feature source; it does not introduce audio callback work or upfront analysis.

The current quality profiles are provisional: Balanced requests a 540 px short edge at 60 fps; Low requests 360 px at 30 fps. These are configured budgets, not measured device performance claims.

## Reused source resources and clean switch audit

Living Lake is the only built-in catalog entry. Retired saved built-in names migrate to it. `StoneSurfaceArt` and `crystalPanel` route Living Lake directly to native material drawing, bypassing bitmap button skins. `WaveformSeekBar` now draws `drawLakeSeekThumb`; it no longer loads the old glass-pebble sprite. The debug showroom no longer uses the old sprite orb.

Legacy spatial bitmap definitions remain in the repository; they are not the active Living Lake control art. No new raster button, orb-shell or capsule sprite was created. No third-party shader, animation pack, particle sprite or bitmap was imported. Dribbble and Framer work is reference material only.

The existing soft click, confirmation and swoop WAVs are reused by `LakeThemePack.kt`. Their actual file hashes, sizes and audio metadata are recorded separately from generated imagery in the manifest.

## Evidence and remaining validation

The earlier UI shader was rendered under OpenGL ES 3.2 Mesa llvmpipe. These are historical reports for their recorded source hash; the latest-main candidate requires fresh GitHub Actions captures. Portrait captures are in `docs/ui2/validation/`; wide captures are in `docs/ui2/validation/landscape/`. Their JSON reports record the exact shader hash, checked uniforms, GL errors, image luminance, reduced-motion time invariance and output response to synthetic audio feature uniforms.

These historical captures record shader integration and observable procedural output for that earlier shader. They do not include native app text, controls or a real playback recording. They do not establish physical Android driver compatibility, accessibility, sustained frame rate or AAA visual quality.

The independent material audit triggered concrete improvements: richer mineral/cavity/core geometry, natural base grain, projected near shore, actual center-to-node filaments, responsive landscape artwork, and a local reading backing for icons inside lower refractive lenses. The earlier wide capture replaced the former stretched portrait with the original landscape and calibrated projection. Both orientation reports record shader SHA-256 `1421451f1631da93b278fb08502991f351c497771d07f2725791010ab08ff599`. The current candidate integrates main `fa75ced697c45ba091b552200c40f78e9ad92392` and refines mineral/crystal geometry, waterglass materials and the UI audio-event handoff. No local build, test, lint or shader preview was executed for this integration. Current Actions checks and device acceptance are pending. Remaining checks are:

- Inspect full native Player, Library, Visuals, Studio and Settings composition on Android, including landscape/tablet crops, large type, TalkBack and keyboard focus.
- Tune near-shore projection and refraction on real screens; verify that native icon backings keep contrast while the lens perimeter remains transparent.
- Record actual audio-driven water/core response and lifecycle handoffs through Live, Studio preview, export, pause and recreation.
- Measure sustained frame time, memory and thermal behavior on physical devices, then calibrate the quality profiles.
- Verify the still environment and native navigation fallback when GLES initialization fails.

Keep the source mattes available for future scene variants. New environments need their own filename, prompt and hash. Keep all control and interaction states in code.


