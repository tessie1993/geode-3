# UI world preview

`world_preview.py` compiles and renders the app's exact GLES 3.0 shaders in a
surfaceless EGL pbuffer. It reads the same local lake drawable and uploads all
uniforms declared by the shader; an audit against `LakeWorldRenderer.kt` fails
if the uploader and shader diverge.

Requirements: Linux Mesa with `libEGL.so.1` and `libGL.so.1`, Python 3, NumPy and
Pillow. There is no network access, Android build, alternate shader, or copied
reference artwork in the harness.

The current repository policy runs build, test, lint and shader checks in GitHub Actions. The `ui-world` workflow job runs these commands from the repository root; they are documented for CI, not as local validation instructions:

```sh
/usr/bin/python3 tools/ui2/world_preview.py --width 360 --height 720 --out build/reports/ui-world/portrait
/usr/bin/python3 tools/ui2/world_preview.py --width 640 --height 360 --out build/reports/ui-world/landscape
```

The harness and Android host select the original landscape artwork when width
exceeds height; portrait and square surfaces retain the original portrait
artwork. Each orientation carries its inspected source horizon. Textures are
half-resolution decodes with the original aspect, matching the Android upload.

The harness default output is `docs/ui2/validation`; the workflow always overrides it to `build/reports/ui-world/<orientation>` so historical checked-in reports are preserved. Captures cover the closed Player,
open navigation with measured-style native control positions, closed Library,
reduced motion at two clock times, and live audio. Checks require successful
shader compilation/linking, zero GL errors, non-black output, identical reduced
motion pixels at different times, and a visible response to audio at the same
clock time. The JSON report records the exact fragment shader SHA-256.

These captures prove shader behavior on the reported software GLES driver.
They do **not** prove Android SurfaceView/Compose ordering, lifecycle handling,
phone driver acceptance, native route reachability, accessibility, sustained
frame rate, thermal stability, or physical-device material quality. Those are
separate Android and hardware validation gates.

The world quality budgets (540/60, 360/30, 360/15) are requested limits, not
measured performance claims. UI pacing never writes the visualizer's shared
`ThermalGovernor.pacedFps`.
