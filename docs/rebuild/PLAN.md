# Geode — plan (fresh start, 2026-10-06)

## Goal (owner)
A Play Store–ready app with these parts:
- **Music player.**
- **Visualizer:** new, high-end, trippy 3D styles, plus the Fluid family, the 8 raymarched styles and a working MilkDrop.
- **Customize tab:** new, with universal controls.
- **Exporter:** includes the full Studio editor.
- **Accounts and Google:** a free → premium unlock, Google sign-in, and Google integration.
- **Engineering:** Android best practices throughout.

## Rules
- **Workers and branches:** only Sonnet workers. Each worker does one job on one branch, which becomes one PR.
  - No fleets or workflows.
  - At most two workers run at once, and they never touch the same files.
- **Builds:** no compiling, testing or linting in this session. GitHub Actions is the only gate.
- **Clean-room:** features are judged by what the user sees. Code, comments and docs are claims to check, not facts.
- **My review of each PR:**
  1. Read the whole diff against the worker's contract.
  2. Re-trace the user flow with Graphify, then by reading the code.
  3. Send findings back to the same worker.
  4. Merge only when CI is green.
- **Licences:**
  - MIT, BSD, Apache, zlib or CC0 code may be ported, with notices added.
  - Shadertoy's default licence (CC BY-NC-SA), lygia's licence (Prosperity) and GPL: copy the technique only, clean-room.

## 1. Think — where the app is (facts)

### Main and open PRs
- **main (a477382):**
  - Toolchain updated: NDK r30, CMake 4.1.2, JDK 25, AGP 9.4.1, Media3 1.11.1, Oboe 1.11.0.
  - CI now builds native changes.
  - The ship-apk "Publish GitHub Release" step fails.
- **PR #4:** removes tag writing and the duplicate finder. CI is running.

### Player
- Audio keeps playing when the UI closes, but the player's rules stop:
  - The rules live in the UI-scoped `ui/PlayerSession.kt` (1,340 lines) and run on a 500 ms poll. They cover preference application, error-skip, play counts, queue save, A-B repeat, bookmarks and fades.
  - A cold start from Bluetooth or Android Auto skips the user's preferences.
- The visualizer audio tap sits before the EQ.
- The optional native player has no audio focus and ignores speed and pitch.

### Visualizer
- 102 styles. We keep the Fluid family, the 8 raymarched styles and MilkDrop; about 82 go.
- Beat uniforms are pinned to 0, so styles don't react to the beat.
- Rotation spins up and then snaps back.
- The last audio chunk is re-fed every frame, so visuals keep moving while paused.
- Visuals run ahead of the audio.
- The renderer is destroyed on every screen change, which is why MilkDrop resets to its "M" logo.

### MilkDrop
- projectM gets only 576 samples per frame.
- Textures are re-linked on every list fetch.
- A thermal-tier resize resets projectM and causes black frames.
- Export uses the wall clock, so it is not deterministic.
- The starter-pack marker is written even when the copy fails.

### Exporter
- Track → video works.
- Studio has these gaps:
  - no preview;
  - the Visual and Overlay lanes are never exported;
  - gaps between clips are ignored;
  - keyframes are timed from the wrong origin;
  - it does not run under the export service.

### Missing entirely
3D foundation, billing, sign-in, Drive backup, Play readiness.

## 2. Decisions (owner can override)

### Removals
- Native player, bit-perfect output and crossfade.
- Tag writing (tag reading stays).
- Duplicate finder.
- Unused theme art.
- About 82 styles.

### 3D
- Prototype with Diligent Engine first, behind a go/no-go gate. Research R2 picks the integration route.
- Fallback: extend our own C++ engine.
- Either way, styles are C++ and share a camera director, a post stack and one style contract.

### Customization
- Every style implements one shared set of universal controls.
- The new screen is built after the styles exist, from scratch.

### Account and premium
- Sign in with Google is optional, via Credential Manager.
- Backup goes to the user's own Drive appDataFolder. There is no backend of our own.
- Premium is sold through Play Billing as a subscription plus a lifetime purchase. Play restores it, not the sign-in.
- The photosensitivity safety clamps are never sold or gated.

## 3. Research (before build)
Four Sonnet agents, one job each, all read-only. Reports go to `scratchpad/research/`.

- **R1 Visual sources, from the owner's screenshots.**
  - Find permissively licensed code repos for the looks in Trance 5D, the tunnel app (37 tunnels, 10 backgrounds, 3D toggle, ± speed, 100+ options), De-Stress particles and the Fluids particle LWP.
  - Licences must be verified from each repo's LICENSE file.
  - Output: a ranked style list mapped to sources, camera choreography for each, and the control set those apps share.
- **R2 3D engine route.**
  - Can Diligent Engine build with NDK r30 and CMake 4.1?
  - Can it attach to our GL context next to projectM, or run Vulkan with interop?
  - Do DiligentFX post effects work on GLES?
  - What happens to offscreen export, APK size and CI build time?
  - Output: a go/no-go and an integration recipe, or the fallback design.
- **R3 Play Billing and Google integration.**
  - Current versions and patterns for: Play Billing (subscription + lifetime), Credential Manager / Sign in with Google, AuthorizationClient + Drive appDataFolder, WorkManager, In-App Review and In-App Updates.
  - The 2026 Play policy rules: target API, account deletion, data safety, foreground service types, privacy policy.
  - The list of Play Console and Cloud Console actions the owner must do.
- **R4 Codebase sweep** (Graphify).
  - Values hardcoded that shouldn't be: strings, colours, limits, paths, durations, IDs, URLs, magic numbers in UI and export.
  - Play-readiness gaps: manifest permissions, foreground service types, signing, R8, notices, the failing release job.
  - Output: a list with file:line and the fix for each.
- **R5 Buffers and engine limits** (owner, 10-06).
  - Every buffer on the audio → analysis → visual → export path: its size and units, threads, sync, overflow/underflow/stale behaviour and latency.
  - Mistakes in those buffers, ranked.
  - Hardcoded engine limits, each marked: keep, derive from device, quality tier, or user setting.
  - Sizing rules derived from device facts.
  - Its fixes feed the B2 and C1 contracts, and the A2b mic contract.

## 4. Build — two lanes, one worker per lane at a time

### Lane A: app and player
| # | Package | Done when |
|---|---|---|
| A1 | Merge PR #4 (tag writing, duplicate finder) | CI green |
| A2 | Remove native player, bit-perfect, crossfade and Oboe (owner, 10-06) | Settings only show working options; CI green |
| A2b | Low-latency mic via AAudio (NDK, no Oboe): low-latency, exclusive mode, native rate, unprocessed preset; tighter `AudioRecord` fallback on Android 8.x | Mic visuals react with roughly 10–20 ms latency instead of 25–90 ms; plugging or unplugging a headset recovers |
| A3 | Player owns its rules: service-side prefs, error-skip, play counts, queue save, A-B, fades; tap after EQ | Cold start from Bluetooth/Auto applies prefs; unplug pauses with the UI closed |
| A4 | Premium foundation | See below |
| A5 | Google sign-in, Drive backup and restore, In-App Review, In-App Updates | See below |
| A6 | Best practices | See below |

- **A4 Premium foundation:**
  - Play Billing (subscription + lifetime).
  - An EntitlementRepository as the single source of truth.
  - A debug fake for billing.
  - A fair unlock sheet and a Settings entry.
- **A5 Google:**
  - Optional Google sign-in through Credential Manager.
  - Drive backup and restore via WorkManager.
  - In-App Review and In-App Updates.
  - A Settings account section with sign out and delete.
- **A6 Best practices:**
  - One DI framework (Hilt).
  - ViewModels with StateFlow.
  - Edge-to-edge and predictive back.
  - Accessibility.
  - A baseline profile.
  - R8.

### Lane B: visuals
| # | Package | Done when |
|---|---|---|
| B1 | Style cull: ~82 styles, their shaders and C++, dead Kotlin mirrors, dead controls | Only the kept styles are listed; CI green |
| B2 | Engine correctness | See below |
| B3 | MilkDrop fixed | See below |
| B4 | 3D prototype (R2 route): one tunnel style end to end, live and export | Go/no-go on CI plus a device check |
| B5 | 3D foundation | See below |
| B6 | New trippy 3D styles from R1, 1–2 per PR | See below |
| B7 | Universal control contract plus the new Visuals/Customize screen, built from scratch | Old VisualsHub and CustomizeTabs deleted |

- **B2 Engine correctness:**
  - Fixes: rotation as a rate, beat reaction restored, audio delivered once per chunk, A/V latency compensated, renderer survives screen changes.
  - Done when visuals settle on pause, never snap, and the style survives screen switches.
- **B3 MilkDrop fixed:**
  - Fixes: full PCM, preset restore, no black frames, textures by preset pack, lazy list, starter-pack retry, deterministic export.
  - Done when the owner's four symptoms are gone on a device.
- **B5 3D foundation:**
  - Camera director (splines, springs, beat swoops, bar cuts).
  - Audio uniform buffer plus FFT history.
  - Post stack: trails, bloom, DOF, chroma, motion blur, tonemap, then FlashBudget last.
  - Quality tiers and deterministic offscreen render.
  - The 8 raymarched styles and the Fluid family ported onto it.
- **B6 New styles:** each comes with camera choreography, audio mapping, quality tiers, licence notices, and support for every universal control.

### Lane C: export
Starts after B2, in Lane A's slot.

| # | Package |
|---|---|
| C1 | Exporter core |
| C2 | Full Studio |

- **C1 Exporter core:** export renders exactly what the live view shows; captions; loudness; human-readable file names and errors; Cancel in the notification.
- **C2 Full Studio:**
  - A composed preview.
  - The Visual and Overlay lanes exported.
  - Gaps and keyframe origin fixed.
  - Ken Burns and audio gain applied.
  - Runs under the export service.

### Finish
| # | Package |
|---|---|
| F1 | Premium gates wired: styles beyond the free set, export above 720p / 3 min, watermark |
| F2 | Play readiness |
| F3 | Hardcoded-value fixes from R4, dead code, docs and CHANGELOG rewritten to match reality |

**F2 Play readiness:**
- Release signing from secrets; AAB; R8 keeps.
- Manifest permissions and foreground service types.
- Privacy policy and data-safety answers.
- Licence notices.
- Store listing text.
- The release workflow fixed.

**Order:**
- A1 → A2 ∥ B1 → A3 ∥ B2 → B3.
- Then A4 ∥ B4 → A5 ∥ B5 → C1 ∥ B6… → C2 ∥ B6… → B7 → A6 → F1 → F2 → F3.

## 5. Owner actions (code can't do these)
- **Play Console:**
  - Create the app.
  - Create the products: `premium_monthly`, `premium_yearly`, `premium_lifetime`.
  - Set up licence testers.
- **Google Cloud:**
  - OAuth clients: Web client ID for Credential Manager, plus the Android client.
  - Enable the Drive API.
  - Configure the consent screen.
- **Signing:** upload key and keystore secrets in GitHub (never committed).
- **Hosting:** a privacy policy URL.
- **Device checks** listed in each PR.
