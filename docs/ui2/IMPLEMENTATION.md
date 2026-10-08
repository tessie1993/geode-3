# Living Lake implementation

UI 2.0 replaces the default flat, dark dock presentation with a bright natural lake, a wet mineral geode, measured optical orbit navigation and native pale reading surfaces. The reference video drives the environment and motion direction. Existing playback, visualizer, library and export owners remain responsible for their features.

This candidate integrates the UI onto main
`fa75ced697c45ba091b552200c40f78e9ad92392`, including its camera, PCM and fluid
repairs. [Integration boundaries and delivery gates](MAIN_INTEGRATION.md) record
the new base. Compilation, tests, lint and runtime verification for this
candidate are pending GitHub Actions; earlier UI evidence is historical.

## Review the design

- [Five-screen wireframe](WIREFRAMES.html), [portable SVG](WIREFRAMES.svg) and [PNG](WIREFRAMES.png).
- [App blueprint and feature mapping](BLUEPRINT.md).
- [Original asset provenance and runtime families](ASSET_MANIFEST.json), [art briefs](ASSET_BRIEFS.md).
- [Collected visual/material references](REFERENCE_BOARD.md).
- [Historical shader captures and previous-base build report](VALIDATION.md), with the current-source [GLES harness](../../tools/ui2/README.md).

## Active architecture

`AppShell.kt` owns one persistent `NativeWorldBackdrop` behind `LivingLakeShell`. Five typed `GeodeDestination` identities supply labels, icons, selection and Back history. Destination content is released while the orbit, Search or full-screen Live owns presentation; a `SaveableStateHolder` restores each screen. Unsaved Studio grades and timeline selections have explicit savers.

The closed Player shows the real geode in a transparent hero region. Tapping it or the navigation handle opens fixed orbit targets. Content destinations have one compact playback anchor: metadata opens Player, play/pause acts on the existing player, and the geode handle opens navigation. Small-height and large-font layouts use an accessible route list. Native measured bounds, rather than the shader, determine hit testing.

The GLES3 world computes perspective rays, intersects its water plane and mineral volumes, reflects the geode, refracts the lens scene and draws bounded filaments and light particles. The chipped mineral shell contains three faceted quartz volumes; the optical lenses share the same index at both sphere boundaries. Portrait and landscape distant scenery are separate original images. The renderer reads fresh scalar audio features directly from the existing stream, outside Compose recomposition; it neither acquires an audio device nor runs analysis. A constant-size `WorldAudioMailbox` retains independent beat/transient edges until a slower GL frame consumes them once. Lifecycle/source resets suppress held replayed events and clear animation state. Camera settling and predictive Back happen inside the persistent world surface.

Native screen headings, artwork frames, search input, tabs and transport share the pale material kit. Orbit chrome uses the theme's selected-duration reveal with a reduced-motion snap; the GL host and measured interaction geometry remain fixed during that native animation.

`LakeWorldVisibility` yields rendering to Visuals preview, Studio, full-screen Live, Search, setup/tutorial, crash presentation, an external display and active export. Opening orbit releases those destination previews before the world resumes. Lifecycle, window visibility, context failure and reduced motion gate the renderer itself. Quality requests are clamped under battery saving and thermal pressure; budgets are provisional until measured on hardware.

The single built-in theme is `living-lake`. Retired saved names resolve to it. Native material functions bypass old bitmap capsule/ring/orb art. Obsolete dock controls are hidden for this theme while stored values remain compatible. The introductory branding shares the same world, without allocating another renderer.

## Validation boundaries

The `ui-world` Actions job checks the candidate's production shader,
declared/uploaded uniforms, non-black output, reduced-motion time invariance and
audio response in portrait and landscape. Python QA suites cover evidence-driven
navigation selectors and retained smoke coverage. JVM suites cover
Back/history/save restoration, theme migration, unsaved clip editing and
exclusive renderer ownership. Nine additional mailbox cases cover cold-frame
retention, independent rearming, stale peaks, bounded strengths and lifecycle
replay resets. These describe checks to run, not current pass claims.

Current-commit Android compilation, all-module checks, APK packaging and emulator
evidence are required by [validation status](VALIDATION.md). The prior APK and
software capture reports are retained with explicit historical labels. Sustained
physical-device performance remains a separate gate. A software shader capture
is not an Android screenshot or a phone frame-rate measurement.
