# UI 2.0 reference board

These are the visual and technical study sources collected for the redesign plan. The attached lake/geode video is the primary art-direction reference. Shipped lake imagery is original, and the runtime geometry/effects are implemented in this repository.

| Reference | Applied study |
| --- | --- |
| [Outpost: Explore Primland](https://outpost.design/work/primland-explore/) | Natural environmental depth and camera-led presentation |
| [Dribbble: Immersive scroll](https://dribbble.com/shots/26679711-Immersive-scroll) | Restrained scene transitions and spatial hierarchy |
| [Dribbble: Spatial music exploration](https://dribbble.com/shots/22729566-Imagine-Spotify-on-VisionOS-Spatial-Design-Exploration) | Music metadata layered above an environment |
| [Dribbble: Liquid glass navigation](https://dribbble.com/shots/26155046-Liquid-glass-navigation) | Optical targets, readable selection and material edges |
| [Framer: AquaticLens](https://www.framer.com/marketplace/components/aquaticlens/) | Lens refraction and water-facing material response |
| [Framer: Liquid Glass Shader](https://www.framer.com/marketplace/components/liquid-glass-shader/) | Coherent light, rim and optical displacement |
| [Framer: Liquid Lens](https://www.framer.com/marketplace/components/liquid-lens/) | Local optical volume with stable native input |
| [Framer: 3D Particle Engine](https://www.framer.com/marketplace/components/3d-particle-engine/) | Bounded atmospheric points and connected scene motion |
| [OriginKit: Liquid Sphere](https://www.originkit.dev/components/liquid-sphere) | Volumetric material research |

## High-end material resources collected for study

- [Poly Haven mossy forest](https://polyhaven.com/a/mossy_forest), [rock/moss set](https://polyhaven.com/a/rock_moss_set_01), [mossy rock](https://polyhaven.com/a/mossy_rock) and [license](https://polyhaven.com/license).
- [ambientCG material library/license](https://docs.ambientcg.com/license/).
- [Filament material reference](https://google.github.io/filament/Materials.html).
- [Procedural noise reference](https://github.com/stegu/webgl-noise).

The asset manifest identifies the originals and existing vectors/sound cues actually shipped. These third-party material/reference assets are not included in the app.

## Native implementation references

- [Android predictive Back](https://developer.android.com/develop/ui/compose/system/predictive-back-setup).
- [Compose performance phases](https://developer.android.com/develop/ui/compose/performance/phases).
- [Compose value-based animation](https://developer.android.com/develop/ui/compose/animation/value-based).
- [Native accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).
- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations).

The active implementation uses the existing Android/Compose shell and an isolated GLES3 world. See [implementation](IMPLEMENTATION.md), [blueprint](BLUEPRINT.md), and [asset manifest](ASSET_MANIFEST.json) for the final code boundaries and resource provenance.
