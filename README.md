# Geode — Android music player, visualizer and video suite

The current implementation is under a release-readiness rebuild. Start with the
[product blueprint, feature spec, architecture and delivery plan](docs/blueprint/README.md)
and [implementation status](docs/blueprint/IMPLEMENTATION_STATUS.md). Features
described below are not all release-verified; the blueprint records the gaps.

Native Android music player, real-time GPU music visualizer and a small video
suite. Kotlin and Jetpack Compose on top; a C++20 engine built by CMake and
the NDK underneath (audio analysis, the GLES 3.x renderer, the DSP chain and
tag reading). Everything runs on-device: the app holds no network permission,
and nothing it hears or renders leaves the phone.

Currently v1.8.0 (versionCode 32); minSdk 26, targetSdk 36, compileSdk 37,
ABIs arm64-v8a and x86_64. The full version history is in
[CHANGELOG.md](CHANGELOG.md).

## Features

- **Player** — MediaStore library plus SAF folder roots and imports, editable
  queue, favourites, playlists and smart playlists (rules over title, artist,
  album, folder, length, age, play count and favourite), multi-term search;
  timed lyrics (`.lrc`), A-B repeat, fades, sleep timer, a ten-band equalizer,
  ReplayGain, playback speed/pitch, skip silence, resume position for tracks
  over twenty minutes; track info edits kept in the app (audio files are never
  modified). Android Auto browsing (tracks, albums, artists, playlists,
  favourites, recently played) and a home-screen now-playing widget.
- **Visual scenes** — particle, simulation and fragment-shader scenes with an
  in-app GLSL editor; the GPU fluid family (nine Fluid looks over one solver,
  plus Curl Flow and Water);
  Cymatics; MilkDrop via projectM 4 built in-tree from a git submodule. Every
  scene is rendered by the native core; the Customize panel exposes every
  parameter with per-param locks, a randomizer, LFO and ADSR modulation, a
  palette maker, presets (JSON + `.milk`) and photosensitivity clamps.
- **Studio** — a multi-lane timeline (visual, media, text, overlay, audio
  lanes) with trim, split, ripple, snapping, markers, tap-in, auto-cut from the
  analysed track, keyframe curves for scene and clip parameters, GL
  Transitions between clips, speed ramps, `.cube` LUTs and per-channel gamma,
  captions from lyrics or SRT (import and export). The visualizer export is
  frame-exact from the analysis timeline; the cut export runs on Media3
  Transformer. Both encode H.264 or HEVC, with H.264 as the fallback when a
  device has no HEVC encoder.
- **Live wallpaper** — the visualizer as a home-screen wallpaper, with an idle
  drive so it keeps moving without audio.
- **Other apps' audio** — Android 10+ playback capture feeds the same PCM ring
  buffer the player's tap and the microphone share, so every scene, the
  exporter and the wallpaper work unchanged on foreign audio.

## Build

```bash
git submodule update --init --recursive   # projectM, kissfft, taglib
./gradlew assembleDebug                   # debug APK, native core included
./gradlew installDebug                    # install on a connected device
./gradlew ktlintCheck detekt              # style and static analysis
```

Requires JDK 25, the Android SDK with `platforms;android-37.0`, NDK
`30.0.16248370` and CMake 4.1.2; `local.properties` must point at the SDK.
`tools/setup-android-sdk.sh` installs those packages on a machine without
them. The workflows under `.github/workflows` build the debug APK, ship a
signed APK and cut a Play Store release; they check out the submodules.

## Architecture

Gradle modules, package `dev.geode`:

| Module | What it holds |
|---|---|
| `:engine:audio-core` | `GeodeNative`, the JNI binding to `libgeode.so`, and the Kotlin wrappers over the native analyzers |
| `:engine:audio-android` | The PCM tap, presentation clock driver and other Android-side audio plumbing |
| `:engine:scenes` | Scene ids and parameters, the GL-thread adapter over the native renderer, offscreen rendering for export, the analysis engine and its cache |
| `:app` | Compose UI, playback service and media session, library and playlist stores, the editor model, the export pipelines, the wallpaper and the widget |

The native core lives outside the modules and is built by the root
`CMakeLists.txt` into one `libgeode.so` (plus `libprojectM-4.so`, LGPL,
dynamically linked):

| Directory | What it holds |
|---|---|
| `core/api` | `geode_api.h`, the `extern "C"` ABI — the only thing JNI calls |
| `core/analysis` | FFT (kissfft), bands, onsets, tempo, beats, bars, key, structure, stereo field; the feature frame |
| `core/viz` | GL capability probing, program cache, the frame graph (scene → trails → composite), the superformula driver (`FormDrive`) that modulates every family's parameters with the music, transitions, safety clamps, every scene family |
| `core/audio/dsp` | Biquad equalizer, gain, crossfeed, lookahead limiter |
| `core/library` | TagLib tag reading over a file descriptor |
| `app/src/main/cpp` | The JNI files: no logic, only marshalling into `core/api` |
| `third_party/` | Git submodules: projectm, kissfft, taglib |

Shaders ship as assets under `app/src/main/assets/shaders/` and are loaded by
the native core. Inside `:app`, dependency flow is one-way: `ui` depends on
`playback`, `export`, `editor` and `data`, never the reverse.

## Tests

The original preset-link, build-variant and feature-ring tests are joined by
playback-preference instrumentation, analysis-lifecycle coroutine tests, native
motion regressions and release-artifact validation. CI runs unit tests across
modules. See [implementation status](docs/blueprint/IMPLEMENTATION_STATUS.md) for
executed evidence and remaining gaps. Checks that cannot run headless
(GL behaviour, capture, wallpaper) are listed in
[docs/DEVICE_CHECKS.md](docs/DEVICE_CHECKS.md), a partial reconstruction.
`tools/hostlink/hostlink.sh` compiles and links the native renderer on the
host with no undefined symbols allowed; it is a lower bound on portability,
not a device check.

## Maintainer notes

- `ENABLE_PLAYLIST` in the root `CMakeLists.txt` is now `OFF`: nothing calls
  `libprojectM-4-playlist.so`, so it is no longer packaged.
- `docs/visualizer-v2/` is what survives of the V2 planning set: the source
  archive and provenance registry (which the build's provenance check reads),
  the feature ABI and the safety model. The master plan they cite is not in
  the tree.

## Documentation

- [CHANGELOG.md](CHANGELOG.md) — version history, newest first.
- [docs/AUDIO_CHAIN.md](docs/AUDIO_CHAIN.md) — where playback audio goes and
  which stages the visuals see.
- [docs/PARAM_MATRIX.md](docs/PARAM_MATRIX.md) — param × scene-family matrix.
- [docs/VISUAL_STYLE_RESEARCH.md](docs/VISUAL_STYLE_RESEARCH.md) — design
  rationale and the style catalogue.
- [docs/DEVICE_CHECKS.md](docs/DEVICE_CHECKS.md) — on-device checklist.
- `tools/build-projectm.md` — how projectM is built from the submodule.
- `THIRD_PARTY_NOTICES` — licences and attributions; the in-app notices asset
  mirrors this file.
