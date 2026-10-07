# Local batch evidence — 7 October 2026

This ledger distinguishes code, source review and executed evidence. The owner
requested implementation and integration before one final GitHub delivery.
No local compilation, unit tests, instrumentation or lint ran in this batch.
`git diff --check` checks patch whitespace only. Final Actions/device results
are not implied by source review.

Integration checkout: `geode-local-batch`, branch `codex/local-complete-batch`.
Baseline: local `2d1f3f2`. A separately active team was changing `geode-3`; those
files were preserved there. This checkout snapshots and reconciles reviewed
packages, with new agent work in isolated worktrees. The final remote check found
the other team's batch delivered as `8592187` (local `23bab36`). Its Rod Tunnel
camera adapter, helper and regression are retained alongside the distinct
Prismatic Passage director. Each updates only its own scene. The retained
MidSideWindow boundary helper remains compatible; A1 uses the reviewed
AnalysisInput lifecycle boundary.

| Package | Code/review state | Regression evidence and remaining limit |
|---|---|---|
| Analysis restart/rate boundary | A1 integrated; independent source review passed | Six authored lifecycle cases, including real worker restart and full new window admission. Tests unexecuted. Native/device stress still required |
| Native input admission | N1 integrated; independent source review passed | Per-field finite/type/domain checks, atomic frame publication and modulator bounds. Preserve ABI order and negative sentinels. Tests unexecuted |
| Playlist import | Prior-team snapshot source reviewed; list IO kept off Main | Bounded unknown-size streams, unique-name transaction, forced-write failure and concurrent-store tests authored. Other playlist mutations remain separate |
| Studio transition memory | Prior-team snapshot source reviewed | One-shot CPU frame ownership, late arrival, checked sizes, composition teardown; unit and GL instrumentation sources. Actual Media3/GLES behavior awaits execution |
| Export scratch ownership | Prior-team snapshot source reviewed | Per-process scratch directories and startup cutoff prevent delayed sweep selecting current-run work; focused source tests pending execution |
| Foreground export admission | E2 integrated; independent source review passed | Completion-owned leases survive cancellation before dispatch; all three render/mux paths poll lease cancellation. Offline native drain is cancellation-aware. Seven new tests unexecuted |
| MilkDrop assets | I1 integrated; independent source review passed | Common bounded admission for direct/folder import, per-entry outcomes and preserved existing bytes. Twelve unit and four route instrumentation tests unexecuted |
| LUT/subtitle imports | E1 integrated; independent source review passed | Bounded off-main reads, retain valid prior selection, reject invalid configured LUT instead of silent omission. Thirteen regression tests unexecuted |
| Prismatic Passage | V1 integrated; two independent source reviews | Spatial bead/fractal corridor and morphing sculpture; native audio camera, Motion/orbit controls, catalog/detail registration. Legacy MotionField cannot secretly modify its speed/zoom. No runtime art/performance approval |
| Control-take durability | T1 integrated; independent source review passed | Durable typed save, atomic unique-name allocation, exact pending capture retry and source/offset frozen together; failure/concurrency/source-change instrumentation tests unexecuted. Pending payload is session-memory only |
| Capture permission cleanup | Prior-team snapshot source reviewed | Revoked audio permission stops capture service; device permission/lifecycle test remains required |

## Explicit remaining work

- Native scene session state must outlive full renderer/GL scene destruction;
  the new camera is currently retained across scene init/release, not destruction.
- Unsaved take recovery is session-memory only; process-death recovery and
  microphone-source identity need a separate persistence/capture contract.
- Live-performance recording must preserve actual rendered/audio output. A
  parameter-event take cannot reconstruct a fresh generative performance.
- Presentation-clock synchronization, audible-processing tap and Oboe microphone
  integration still need their own bounded implementation and device evidence.
- Studio timeline gaps, Visual/Overlay lanes, preview parity and service-owned
  Studio export lifetime are separate incomplete packages.
- Account/premium foundations are not production auth or Billing. Configured
  adapters, backend signature/receipt verification, restore and account deletion
  remain required, along with owner-controlled OAuth, Play products and backend.
- Play release also requires signing/configuration, policy/legal/support URLs,
  store assets, real-device performance/accessibility and purchase testing.

## Next contracts

1. Scene-session lifetime: GL-independent spatial state owned by the visual
   session; surface recreation restores pose and generated intent without reseeding.
2. Account/Billing vertical slice: real configured adapters and authenticated
   backend entitlement boundary; unavailable configuration stays explicitly disabled.
3. Recording vertical slice: capture the realized composition and synchronized
   audio, then edit/export that take without regenerating its motion.

Build success alone does not establish Play readiness or final visual quality.
