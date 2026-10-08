# UI 2.0 integration on latest main

## Baseline and scope

The latest inspected main is
`fa75ced697c45ba091b552200c40f78e9ad92392`. This candidate applies Living Lake
presentation to that source rather than treating the earlier UI branch or its
APK as the new baseline.

Main's visualizer repairs remain authoritative: bounded spatial camera motion,
fresh PCM batches consumed once, independent audio reactions, recoverable fluid
allocation and repeated fluid-style rendering. The UI integration preserves
the native core and engine implementation, the existing Media3/audio feature
pipeline, export ownership and the pinned build/dependency setup. Its world
subscribes to the existing audio feature stream; it does not open another audio
device or replace native visualizer analysis.

## Presentation changes

- One original dawn lake environment with a separately rendered mineral geode,
  water, optical navigation lenses, bounded filaments and touch/audio effects.
- Native shared pale material components and readable typography replace the
  old black dock/capsule presentation for the built-in theme.
- Common GEODE headings, framed real album/clip artwork, native search input
  and quieter tabs/transport give all five screens the same reading hierarchy.
- Typed five-destination navigation, compact playback entry, fixed semantic
  orbit targets and adaptive route-list fallback replace stacked docks.
- Orbit chrome reveals using the theme's selected-duration profile and snaps
  under reduced motion. Its animation does not transform the GL surface or
  measured target geometry; closing releases the overlay immediately.
- Player, Library, Visuals, Studio and Settings bind to the latest main's
  existing state/actions while gaining the common hierarchy and surfaces.
- Visibility/lifecycle ownership suspends the UI world for the existing
  Visuals, expanded Live, Studio preview and export owners. Destination state
  survives temporary removal for orbit/search/full-screen presentation.

The mineral anchor has a chipped leaning shell and three faceted quartz volumes.
Navigation lenses use matching entry/exit refraction indices. Brief audio accents
are latched from independent beat/transient edges in a constant-size mailbox,
then consumed once by the world frame. This preserves the existing 48 ms event
hold even at the 15 FPS requested tier. Pause, surface detach/reattach, source
changes and reduced motion clear pending accents and baseline replayed state.
The native audio analyser and the visualizer's own event handling are unchanged.

The [blueprint](BLUEPRINT.md) maps feature entries and ownership. The
[wireframes](WIREFRAMES.html) explain intended layout. Neither proves runtime
parity, Android surface ordering or phone visual quality.

## Verification on the current candidate

Repository policy places compilation, tests and lint in GitHub Actions only.
No local run establishes a result for this integration. The existing workflow
retains debug/instrumentation packaging, 16 KB checks, full Kotlin gates, native
camera/PCM/fluid regressions and emulator render/UI smoke checks. A small
`ui-world` job adds current production-shader verification on Mesa software GLES
with portrait/landscape captures and exact hashes. Every verification job also
gates the optional signed bundle.

All current-candidate gates are pending until the reviewed commit runs in
Actions. Downloadable APKs must come from that run with its `commit.txt` and
`SHA256SUMS`. Record actual test totals and lint findings after completion;
do not reuse the earlier 74-test or APK report as a new-main result.

Nine new `WorldAudioMailboxTest` cases cover held events, independent rearming,
slower drains, expiry, bounded strengths and reset/replay behavior. They are
source additions awaiting the `quality` job, not executed results.

The previous Android report is archived in
[validation/previous-base](validation/previous-base/README.md). Existing scene
captures retain their original values/hash and are labelled historical in
[validation status](VALIDATION.md). Newly generated CI artifacts remain separate.

Physical-device accessibility, visual acceptance and sustained performance are
still required after Actions passes. Quality settings are requested limits,
not measured frame-rate claims.
