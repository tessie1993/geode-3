# Reference application research

Checked 7 October 2026. Evidence is the public Android Google Play listing and
developer description, not an installed-app test. Counts, platform support and
premium gates can change by version or region. Review anecdotes are hypotheses
for our test plan, not proof of current defects. Screenshots and marketing words
do not prove frame rate, battery use, latency, capture compatibility or output
resolution. Do not copy artwork, presets, names, icons or store copy.

## Requested examples

| App / primary source | Advertised feature inventory | Implication for Geode |
|---|---|---|
| [Astral 3D FX Music Visualizer](https://play.google.com/store/apps/details?id=astral.teffexf) | 13 tunnel/fractal/space visualizers; local-file player; visualization with compatible external players; online radio and background radio; more than 100 settings for colour, patterns, movement and backgrounds; swipe distance control and speed buttons; silent visual mode; premium gyroscope interaction, microphone input and unrestricted settings | Build coherent spatial scenes, explicit audio-source selection, understandable controls and a silent Explore mode. Treat radio as a separate optional networking feature. |
| [Fluids Particle Simulation LWP](https://play.google.com/store/apps/details?id=com.MKGames.FluidsSounds) | Touch/swipe fluid interaction; configurable colours, flow and intensity; particles and visual effects; live wallpaper; ambient sound; advertised 4K wallpapers; saved animated looks; a Distort effect in release notes | Keep direct touch response, fluid materials and wallpaper. Show the actual export resolution supported by the device. Verify audio-input behavior ourselves; older reviews mention imported files and microphone, but do not establish current device-audio support. |
| [projectM Music Visualizer Pro](https://play.google.com/store/apps/details?id=com.psperl.projectM) | MilkDrop `.milk` compatibility; more than 200 effects; preset browsing/search; multitouch; microphone/music audio detection; live wallpaper and Daydream modes; configurable graphics quality; advertised 60 fps rendering; Chromecast and external player controls; recent notes mention track information and shuffle | Preserve compatibility through projectM, add excellent preset management and test preset restore, transitions and context loss. External-player and Cast claims need modern device verification before Geode promises parity. |

## Additional examples

| App / primary source | Advertised features worth studying | What to adopt as product behavior |
|---|---|---|
| [Vythm VJ](https://play.google.com/store/apps/details?id=com.MKGames.Vythm) | Dozens of visual modes, background combinations, performance effects, colour correction/bloom/chromatic aberration, file/microphone/device audio inputs, recording and sharing | A compact performance panel, quick A/B looks, deliberate visual transitions and a clear recording workflow |
| [Magic Fluids](https://play.google.com/store/apps/details?id=com.magicfluids) | Configurable fluid appearance/behavior, more than 30 presets, saved presets, multitouch, smoke/water, particles, colour modes, glow/textures, pause/screenshot, wallpaper and quality settings | Great first-touch experience; a curated preset set; separated motion/material controls; genuine low-power wallpaper mode |
| [Avee Music Player (Pro)](https://play.google.com/store/apps/details?id=com.daaw.avee) | Music library/player, spectrum visualizer templates, colour/shape/size/audio-response editing, image/GIF customization, template import/export, music-video export, albums/artists/genres/folders/playlists and premium export/customization options | A reliable music-to-video path, reusable templates, aspect-ratio presets and visible export progress/cancellation |
| [Fraksl](https://play.google.com/store/apps/details?id=com.workSPACE.Fraksl) | Interactive fractal art; parameter modulation from microphone, MIDI, beat clock, waveforms, motion and gamepad; saved setups; screenshot/video capture; NDI streaming; multiple languages | Treat modulation as a first-class mapping model. Keep MIDI/gamepad/network output as a separately tested performance expansion, rather than complicating first use. |

## Feature synthesis

| Capability | Baseline expected in Geode | Premium execution standard |
|---|---|---|
| Immediate visual experience | Works before importing a track | A carefully choreographed silent demo that clearly identifies itself as demo audio/drive |
| Audio reactivity | Bass, mids, highs and beat | Multi-band features, beat confidence, smooth transitions, calibrated presentation timing |
| Touch | Tap and drag affect the image | Stable pointer IDs, pressure where available, no stuck emitters on cancellation |
| Customization | Colour, motion, intensity | Universal macros with per-style mappings, undo, parameter locks and reset |
| Presets | Browse and save | Live thumbnails, search, favourites, import validation, no overwrites, version migration |
| Spatial visuals | Tunnels and fractals | Distinct geometry, depth, materials and camera behavior rather than palette swaps |
| Creation | Capture a look | Deterministic timeline rendering with audio, captions, layers and repeatable output |
| Background use | Music and wallpaper | Service-owned music state, visible capture state, offscreen rendering suspension |
| Performance | Configurable quality | Device capability probes, measured frame pacing, sustained thermal fallback |

## Gaps we should deliberately solve

1. **Too many controls:** give the user a curated look plus five understandable
   macros first; reveal advanced controls by section.
2. **Unclear audio capture:** label Local music / Microphone / Device audio /
   Silent. Explain that some apps prohibit playback capture and offer another
   source when no eligible audio is available.
3. **Mismatch between preview and saved video:** use one project evaluator and
   explicit timestamps for both preview and export.
4. **Interrupted listening:** keep preferences and transport behavior in the
   service, including headphone disconnect and errors, when every UI is gone.
5. **Preset and import friction:** batch operations, stable IDs, safe naming,
   atomic saves and a clear unsupported-file report.
6. **Trust:** premium limits are visible before rendering; no paywall on safety,
   no surprise microphone use and no therapeutic claims.

## Required hands-on comparison protocol

Before visual parity is signed off, install the reference apps from Play on a
test device using authorized accounts. Record app/version/device/date and:
launch-to-first-frame, permission denial, three songs with different rhythms,
silence, touch response, preset transition, background/resume, wallpaper battery,
landscape/tablet layout and exported audio/video synchronization. Compare matching
resolutions and refresh rates. Do not present listing claims as these measurements.
