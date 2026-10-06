# Geode Android product blueprint

Research date: **7 October 2026** (Europe/Amsterdam). Repository inspected at
`9848b2854f173ac5875a4cc4cd7761f2745bfa74`.

**Product:** a premium Android music player and audiovisual instrument. Listen,
explore fluid and spatial visuals, shape a look, and turn it into a repeatable
performance, wallpaper or edited video. The implementation uses **C++20,
Media3 and Oboe**, with Kotlin/Compose for Android integration and UI.

This is the implementation blueprint, not a claim that the app is complete.
The existing app has substantial code, but the October audit records failures
in playback lifetime, native lifetime, audio/visual timing and Studio composition.
Store listing inspection does not establish how a competitor behaves on a device.

## Read in this order

| Document | Decision it supports |
|---|---|
| [Reference apps](REFERENCE_APPS.md) | The three requested examples, four additional examples, advertised features and product lessons |
| [Open-source research](OPEN_SOURCE.md) | Sources, licence-file evidence, adoption boundaries and documentation |
| [Feature specification](FEATURE_SPEC.md) | Spec sheet, complete feature inventory, release scope and measurable acceptance criteria |
| [Architecture](ARCHITECTURE.md) | Android/C++ boundaries, audio ownership, clocks, threading, rendering, storage and export |
| [Design specification](DESIGN.md) | Visual direction, screens, journeys, controls, accessibility and scene art direction |
| [Build and delivery plan](DELIVERY_PLAN.md) | Ordered work packages, dependencies, migration, verification and completion gates |
| [Play release plan](PLAY_RELEASE.md) | Signing, native compatibility, Console tasks, privacy, listing and rollout |
| [Implementation status](IMPLEMENTATION_STATUS.md) | Changes in this branch and evidence still needed |

## Relationship to the existing plan

`docs/rebuild/PLAN.md` remains the historical work-package outline. This blueprint
expands its acceptance criteria and resolves the owner's latest instruction:
**retain Oboe**. Its A2/A2b instruction to remove Oboe and use AAudio directly is
superseded. Use Media3 for normal music playback and Oboe for native low-latency
capture; a competing native music player is not required to satisfy that choice.
The current native player must be repaired or retired through a tested migration.

Retain the planned Music, Visualizer, Customize, Studio, optional Google and
premium capabilities. Do not delete the approximately 82 older styles until
replacement coverage, saved-preset migration and user-visible quality are proven.
Preserve the Fluid family, eight raymarched styles and MilkDrop boundary.

GitHub Actions remains the compile/test/lint gate for this work, as specified by
the existing plan. Device checks are separate gates. No production release or
store readiness claim follows merely from an APK compiling.

## Decisions and unresolved external inputs

| Item | Decision / boundary |
|---|---|
| Engine | Retain the existing GLES C++ engine; evaluate Diligent in an isolated prototype before any renderer replacement |
| Music | Media3 owns decoding, queue, media session, focus and background lifetime |
| Native audio | Oboe input feeds a bounded native PCM queue; no JNI or allocation in the audio callback |
| Privacy | Core experience works offline; Google/Drive and billing require separately reviewed disclosures |
| Monetization | Existing plan's monthly/yearly subscription and lifetime purchase retained in specification; prices and product configuration are owner inputs |
| Purchase security | Client-only entitlements have limitations; server verification is the recommended premium architecture, requiring an explicit change to the old no-backend decision |
| Identity | Optional Google sign-in; never required to play local music; Play entitlement is independent of Google profile |
| Publication | Upload key, Play/Cloud configuration, privacy/support URLs and device evidence are not present in this checkout |

Release success means every required feature passes its user journey, existing
data survives upgrades, audio and video remain synchronized, and a signed AAB
passes the documented release gates. It does not mean maximizing the number of
settings or copying another application's branding or assets.
