# Geode Android product blueprint

Research date: **7 October 2026** (Europe/Amsterdam). Repository inspected at
`9848b2854f173ac5875a4cc4cd7761f2745bfa74`.

**Product:** a premium Android music player and audiovisual instrument. Listen,
explore fluid and spatial visuals, shape a look, and turn it into a repeatable
performance, wallpaper or edited video. The implementation uses **C++20,
Media3 and AAudio**, with Kotlin/Compose for Android integration and UI.

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
| [Authentication, premium and security](SECURITY_AUTH_PREMIUM.md) | Identity, verified purchases, backend API contract, threat model and abuse tests |
| [Build and delivery plan](DELIVERY_PLAN.md) | Ordered work packages, dependencies, migration, verification and completion gates |
| [Play release plan](PLAY_RELEASE.md) | Signing, native compatibility, Console tasks, privacy, listing and rollout |
| [GitHub Actions](CI.md) | Debug APK download, build/test jobs, emulator evidence and opt-in signed AAB |
| [Implementation status](IMPLEMENTATION_STATUS.md) | Changes in this branch and evidence still needed |

## Relationship to the existing plan

`docs/rebuild/PLAN.md` remains the work-package outline. This blueprint expands
its acceptance criteria. Owner decision (7 October 2026): **Oboe is removed**
together with the native player, bit-perfect output and crossfade (PR #10). Media3
plays music; native low-latency microphone input uses AAudio from the NDK directly,
with an AudioRecord fallback where AAudio input is unreliable.

Retain the planned Music, Visualizer, Customize, Studio, optional Google and
premium capabilities. The owner's decision stands: the approximately 82 older
styles are removed, and the removal ships with saved-preset migration (a removed
style id maps to a kept style). Preserve the Fluid family, eight raymarched styles
and MilkDrop boundary.

GitHub Actions remains the compile/test/lint gate for this work, as specified by
the existing plan. Device checks are separate gates. No production release or
store readiness claim follows merely from an APK compiling.

## Ownership (owner decision, 7 October 2026)

Two agent sessions work on this repository. Each package has one owner; the other
session does not edit its files while a PR for it is open.

| Area | Owner | Packages |
|---|---|---|
| Visuals: engine correctness, projectM, 3D prototype and foundation, signature scenes, style catalog, universal controls and the new Visualizer/Customize screens | Claude Code session (`docs/rebuild/PLAN.md` lane B) | D03, D05, D08, D09, D11, the Visualizer/Customize part of D15 |
| Microphone input (AAudio) | Claude Code session (lane A2b) | D06 |
| Playback, data, account, premium and billing, Google identity and Drive, exporter and Studio, app shell, release | Codex sessions | D00–D02, D04, D07, D10, D12–D14, the rest of D15, D16–D19 |

In-flight visual changes in PR #9 land first; later visual work builds on them.

## Decisions and unresolved external inputs

| Item | Decision / boundary |
|---|---|
| Engine | Retain the existing GLES C++ engine; evaluate Diligent in an isolated prototype before any renderer replacement |
| Music | Media3 owns decoding, queue, media session, focus and background lifetime |
| Native audio | AAudio input feeds a bounded native PCM queue; no JNI or allocation in the audio callback |
| Privacy | Core experience works offline; Google/Drive and billing require separately reviewed disclosures |
| Monetization | Existing plan's monthly/yearly subscription and lifetime purchase retained in specification; prices and product configuration are owner inputs |
| Purchase security | Client-only entitlements have limitations; server verification is the recommended premium architecture, requiring an explicit change to the old no-backend decision |
| Identity | Optional Google sign-in; never required to play local music; Play entitlement is independent of Google profile |
| Publication | Upload key, Play/Cloud configuration, privacy/support URLs and device evidence are not present in this checkout |

Release success means every required feature passes its user journey, existing
data survives upgrades, audio and video remain synchronized, and a signed AAB
passes the documented release gates. It does not mean maximizing the number of
settings or copying another application's branding or assets.
