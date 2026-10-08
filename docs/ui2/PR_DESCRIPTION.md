# Rebuild Geode UI around Living Lake on latest main

The built-in UI used dark capsule controls and stacked navigation instead of
the natural spatial direction in the reference video. This change replaces
that presentation with a bright original dawn lake, a separately rendered wet
mineral geode and refractive orbit navigation. Player, Library, Visuals, Studio
and Settings now share pale readable components, native typography and one
compact playback/navigation entry.

The branch starts from main `fa75ced697c45ba091b552200c40f78e9ad92392`.
Main's camera, PCM, audio-response and fluid repairs stay unchanged. Existing
playback, library, visualizer, editor and export actions remain their feature
owners. The UI world consumes the existing audio feature stream, uses a bounded
mailbox for brief independent beat/transient edges, and pauses when a native
preview, export, hidden window or paused lifecycle owns rendering. Saved route
and editor state, reduced motion, adaptive route-list navigation and a static
renderer fallback are included.

Original portrait/landscape environment assets, the runtime shader/material
sources, source-aligned wireframes, feature blueprint and asset provenance are
included under `docs/ui2`. The new software-GLES Actions job captures the exact
production shader in both orientations and gates the optional signed bundle
alongside the existing build, Kotlin, native regression and emulator jobs.

Validation is pending on the exact reviewed commit. No local build, test, lint
or shader preview ran, following repository policy. Nine new audio-mailbox
regression cases await CI. Earlier APK and software-render evidence is retained
with explicit historical labels; it does not validate this candidate. After
Actions passes, native surface handoffs, accessibility, visual acceptance and
sustained physical-device performance still need review.
