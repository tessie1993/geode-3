# Tidal Glass UI replacement

The visual direction and original assets below are historical. The replacement is now specified in [SPATIAL_JELLY_UI_PLAN.md](SPATIAL_JELLY_UI_PLAN.md), with all eleven skins rebuilt from the new kit. Its current asset provenance is in [SPATIAL_ASSETS.md](SPATIAL_ASSETS.md). The preservation and service-regression contracts here still apply.

Base: PR 13 commit `5ee5e05d5a10f2533622dc73513a61047859bddc`. The UI branch follows the user's merge of PR 13; it does not merge that PR itself.

## Visual direction

Use the supplied motion references and approved forest creek concept: layered natural scenery, thick refractive waterglass, warm glass pebbles, wet stone, floating volume, droplets and contact ripples. Production assets are separate environment, orb, pebble and capsule WebP images, rather than a flattened screenshot. New Tidal Glass is the default for an unsaved theme selection; the ten existing mineral packs and their saved slugs remain available.

Keep the live visualizer in its existing fullscreen host. The player's scenic orb observes the current live feature stream and is decorative; it does not instantiate another renderer, capture source or analyzer. Ambient motion runs only while the Activity is resumed, at a bounded cadence. Reduced motion and disabled system animations leave the material static. Press ripples use each control's existing interaction source and do not intercept scene or editor gestures.

## Preservation contract

- Keep the Activity-scoped PlayerViewModel, shared PlayerSession, engine bindings and service ownership unchanged. Invoke the existing transport, capture and editing callbacks.
- Retain Player, Library, Visuals, Studio and Settings, including user-intent filtering, saved destination, compact/top/bottom mini-player, adaptive rail and two-pane layout.
- Retain permission and onboarding flows, search, notices, crash recovery, fullscreen/PiP and second-screen presentation.
- Retain queue, favourites, seek, shuffle/repeat, A/B loop, automatic visuals, source capture, sleep timer and listening history.
- Retain all Library sections, Visuals tabs, presets/templates/imports, customization locks/undo/modulation/GLSL and renderer error states.
- Retain all Studio editing lanes, clip/take workflows, export progress/cancel, destination selection and sharing. Decorative art never changes native underlays/overlays or exported frames.
- Preserve theme, font, text-scale, opacity, dim, corner and motion preferences; use readable text, control semantics and generous touch targets.

## Validation

Repository policy requires builds, tests and lint to run in GitHub Actions. Use the existing Android workflow for APK/instrumentation builds, quality gates, native regressions and emulator checks. Extend the UI-tree-driven emulator smoke flow to cover all five destinations, section/tab reachability, search, appearance settings, large fonts, landscape and a short motion recording. Inspect screenshots, hierarchy, crash logs and resource snapshots before marking the UI PR ready. Physical-device fluidity remains outside emulator evidence.

Real WAV playback in Actions run 37 exposed an existing foreground-service crash: the UI drives the shared player directly, but PlaybackService built its MediaLibrarySession without registering it with Media3. Android terminated playback after the service failed to post its foreground notification. Register the same session with addSession; retain Media3's notification handling and the existing player ownership. PlaybackServiceForegroundTest exercises direct PCM playback without a controller, requires a real foreground media notification, and observes playback beyond the startup deadline.

## Asset provenance

Generated with the built-in ImageGen tool from the approved mockup. Asset prompts isolate: (1) the continuous forest creek environment without UI or glass objects; (2) the large asymmetric aqua waterglass orb with transparent surroundings; (3) an empty champagne/aqua glass control pebble; (4) an empty thick horizontal waterglass capsule. All exclude text, icons and watermarks. Compressed assets live in `app/src/main/res/drawable-nodpi/tidal_*.webp` and total approximately 0.65 MiB. No remote images or new network permission are required.
