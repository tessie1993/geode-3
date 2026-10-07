# Geode — Android music player, visualizer and video suite

The current implementation is under a release-readiness rebuild. Start with the
[product blueprint, feature spec, architecture and delivery plan](docs/blueprint/README.md)
and [implementation status](docs/blueprint/IMPLEMENTATION_STATUS.md). Features
described below are not all release-verified; the blueprint records the gaps.

Native Android music player, real-time GPU music visualizer and a small video
suite. Kotlin and Jetpack Compose on top; a C++20 engine built by CMake and
the NDK underneath (audio analysis, the GLES 3.x renderer, the DSP chain, a
native player and tag access). Everything runs on-device: the app holds no
network permission, and nothing it hears or renders leaves the phone.

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
  modified). An optional native audio engine adds gapless joins,
  crossfades and, on Android 14+, a bit-perfect USB output toggle. Android
  Auto browsing (tracks, albums, artists, playlists, favourites, recently
  played) and a home-screen now-playing widget.
- **Visual scenes** — particle, simulation and fragment-shader scenes with an
  in-app GLSL editor; the GPU fluid family (nine Fluid looks over one solver,
  plus Curl Flow and Water);
  Cymatics; MilkDrop via projectM 4 built in-tree from a git submodule. Every
  scene is rendered by the native core. Customize includes parameter locks,
  randomization, LFO/ADSR mapping, palettes and presets (JSON + `.milk`). The
  audit identifies inert controls and incomplete saved-state coverage; see the
  blueprint's bug audit and active work queue for fixes and verification.
- **Studio** — a multi-lane timeline (visual, media, text, overlay, audio
  lanes) with trim, split, ripple, snapping, markers, tap-in, auto-cut from the
  analysed track, keyframe curves for scene and clip parameters, GL
  Transitions between clips, speed ramps, `.cube` LUTs and per-channel gamma,
  captions from lyrics or SRT (import and export). The visualizer export uses an
  analysis timeline; cut export uses Media3 Transformer. Full Visual/Overlay lane
  composition and live-performance fidelity remain incomplete. Codec support
  depends on the device and must be verified on the release candidate.
- **Live wallpaper** — the visualizer as a home-screen wallpaper, with an idle
  drive so it keeps moving without audio.
- **Other apps' audio** — eligible Android 10+ playback capture feeds live
  visualization. Current export reads the selected local track; it does not
  record or mux captured other-app audio. Actual audiovisual performance
  recording is a separate implementation package.

## Build

Use the **Android Build** GitHub Actions workflow on a pull request, main push
or manual run. It checks out native submodules and installs SDK 37.0, NDK
`30.0.16248370`, CMake `4.1.2`, JDK 21 for Gradle/analysis and JDK 25 for the
compiler. A controlled source rebuild prepares the AndroidX native dependency
before Gradle resolves it.

After compilation and packaging validation, the run uploads `Geode-debug.apk`,
instrumentation APK, checksums and native-dependency provenance. Emulator and
quality jobs verify that build. A signed Play AAB is an opt-in job gated on all
checks and signing configuration; the workflow does not publish to Play. Check
the exact run result before treating an artifact as verified. See
[CI instructions](docs/blueprint/CI.md).

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
| `core/viz` | GL capability probing, program cache, scene/trails/composite rendering, continuous MotionField inputs, transitions, safety controls and every native scene family; legacy FormDrive controls still require repair |
| `core/audio/dsp` | Biquad equalizer, gain, crossfeed, lookahead limiter |
| `core/audio/player` | AMediaCodec decode, resampling, a lock-free mixer with gapless and crossfade, Oboe output |
| `core/library` | TagLib tag reading over a file descriptor |
| `app/src/main/cpp` | The JNI files: no logic, only marshalling into `core/api` |
| `third_party/` | Git submodules: projectm, kissfft, oboe, taglib |

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
