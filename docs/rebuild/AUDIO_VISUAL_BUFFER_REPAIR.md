# Audio, camera and fluid buffer repair

Base: `2074aa3`. Work branch: `codex/reactive-camera-buffer-repair`.

## Diagnosis and design

Research preceded implementation. The reference patterns were independent frequency mappings,
attack/release envelopes, bounded frame-independent camera movement, and fresh PCM delivery:

- [TouchDesigner audioAnalysis](https://derivative.ca/UserGuide/Palette:audioAnalysis)
- [Notch Sound FFT Modifier](https://manual.notch.one/2026.2/en/docs/reference/nodes/modifiers/sound-fft-modifier/)
- [Three.js OrbitControls](https://threejs.org/docs/pages/OrbitControls.html)
- [projectM v4.1.7 PCM API](https://github.com/projectM-visualizer/projectm/blob/v4.1.7/src/api/include/projectM-4/audio.h)
- [GLES 3.0 specification](https://registry.khronos.org/OpenGL/specs/es/3.0/es_spec_3.0.pdf)
- [EXT_color_buffer_float](https://registry.khronos.org/OpenGL/extensions/EXT/EXT_color_buffer_float.txt)

The renderer retained and replayed the last PCM batch. Multiple producer pushes replaced one
another before a render. Initial host probes against the production delivery code reproduced
both problems. The scene callback also ran while holding the producer mutex.

Analysis selected the latest window by wall-clock wake rather than consuming chronological
sample endpoints. That could repeat a window or skip early events in a decoder batch. Transport
boundaries also needed to invalidate both PCM stores and the analysis state for same-rate seeks.

Fluid code had separate confirmed failure paths: unsupported float color attachments, grid
allocation failure tearing down usable shaders, partial shader construction leaking programs,
and every visited heavy scene retaining independent GPU targets. These are source-level failure
paths, not a diagnosis measured on the owner's device.

## Behavior in this patch

- A fixed-capacity PCM queue appends fresh batches and drains once into an immutable render-frame
  snapshot. Active, layer and outgoing scenes receive the same snapshot. Overflow retains the
  newest samples, and scene work runs outside the producer mutex.
- Tap discontinuities invalidate both audio stores and analysis before format notifications.
- Analysis uses rational 16 ms sample endpoints, indexed epoch-checked reads, actual sample-step
  durations, at most four catch-up windows per wake, and explicit resets after lost history.
  Unchanged PCM does not run another FFT. Reset markers retain their original request boundary.
  A one-window ring with fractional-rate producer chunks resynchronizes to a readable full window
  rather than indefinitely chasing an overwritten endpoint.
- Batched events retain their maxima and expire during input gaps. MilkDrop receives fresh PCM
  in chunks bounded by its public API limit. Once the source is silent, 576 zeros overwrite the
  pinned projectM version's retained raw history once; feature-only export remains supported.
- Native camera rigs integrate slow orbits and corridor travel for KIFS, Curl Bloom, Rod Tunnel
  and Nectar Flow. They maintain world-up horizons and bounded dolly movement, and zero speed
  holds the complete pose. Fixed camera styles retain the projection assumptions required by
  their touch interactions. Background exposure follows slower passage energy.
- Bass, mids, treble, overall energy, slow swell and edge-latched accents have independent response
  times. Accents affect local geometry and highlights. Global audio drive and reduced motion
  controls apply to the new motion paths.
- Fluid buffers allocate transactionally and preserve caller GL state. Failed allocations retry
  bounded smaller grids; failed resizes keep previous complete targets and usable shaders. GPU
  residency retains active, layer and outgoing scenes and releases inactive heavy scenes.
  Requested quality and disabled particle settings apply before initial allocation; quality
  downgrades shrink particle targets. Float solver buffers retain signed values.
- Unsupported float attachments and rejected display shaders use a visible procedural recovery
  display with the selected palette and distinct Ink/Marble paper treatment. This display has
  its own integrated phase, filtered audio and edge accents, and reports the GPU failure. It is
  not a signed-fluid solver. If even its small shader is refused, it clears a visible surface.
- Fluid audio drive scales levels and transient strengths, and zero drive silences musical
  input including raw PCM strikes. Idle physical flow remains independent from musical input.

## Verification and limits

The repository requires builds, tests and lint to run in GitHub Actions. No such checks ran after
that rule was discovered. The completed patch has had source review; new regression and Android
render tests await CI. The initial failing host probes are diagnosis
evidence, not final validation.

CI includes native PCM, MilkDrop delivery, camera, sound-envelope and fluid allocation/drive regressions,
Kotlin analysis and boundary tests, and real GLES instrumentation. The Android checks capture PNG
evidence before asserting errors or visible output for eight spatial shaders, nine fluid looks,
CurlFlow and Water. They cover fresh input, gaps, silence, resize, recreation and repeated Medium
quality style changes with particle targets enabled. Evidence is collected with emulator reports.

This change does not connect the heard-output presentation clock to analysis window selection.
At sample rates above 128 kHz, a 2048-sample FFT is shorter than the 16 ms hop; continuous coverage
would need a larger window or resampling. projectM presets can still animate autonomously after
their raw audio is cleared. Unsupported-device recovery must be checked separately from the
float-capable emulator path.

The owner has authorized publishing the repair branch and a new PR. GitHub Actions remains the
required validation gate before any merge. The initial downloadable package predates publication
and records the source-review status at that time.
