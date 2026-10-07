# Active work and ownership

Updated 7 October 2026. This records work in progress, not release completion.
Current owner scope: all native styles and MilkDrop remain; new spatial content,
simulation and camera are C++/GPU; live movement is generative; captured takes
preserve the realized performance. GitHub Actions is the build/test/lint gate.

| Package | Owner | Scope | Verification / state |
|---|---|---|---|
| Downloadable debug APK | Primary agent | One workflow, SDK/NDK, native alignment, artifact and emulator | PR #7; native dependency rebuild follow-up running. No APK download claimed yet |
| Native audio freshness | Native engine agent | Consume fresh PCM once, retain transition/layer fanout, stop cached live waveform replay into MilkDrop | Isolated branch; implementation under review, native regression required |
| Preset integrity | App storage agent | Durable outcomes, copy/update distinction, serialized import allocation, bounded input | Isolated branch; fault-injection and admission tests required |
| Studio export ownership/publication | Studio agent | Single job independent of screen refresh, persistent terminal status, rollback newly created output on failure | Isolated branch; concurrency/cancellation/publication failure tests required |
| Complete visual journeys | Feature agent | Include all retained C++ families, avoid immediate repeats, honor Hold across automation | Isolated branch; source-derived eligibility and automation tests required |
| Transition memory and scratch lifetime | Studio agent, follow-up | Consume uploaded transition frames, preserve active-run scratch during cleanup | H03/H06 in independent review; queued after current Studio package |
| LUT/subtitle and MilkDrop asset admission | App storage agent, follow-up | Bounded off-main readers, validated outcomes, common direct/folder import policy | H04/H05 in independent review; queued after preset package |
| Export foreground-service admission | Primary agent, follow-up | Explicit service-start/promotion result and safe failure/cancel behavior | H07; controller changes coordinated with Studio owner |
| Independent bug and quality review | Independent reviewer | New bugs, misleading UI/copy, dead controls, placeholder flows, unsafe inputs, trust boundaries | Read-only findings; reviews fixes independently |
| Native 3D camera and scene foundation | Next native package | Shared camera/path/geometry state; generative bead/fractal corridor and morphing object | Specification ready; follows freshness and APK gates |
| Production account/premium | Integration package after build gate | Actual platform adapters, backend security, restore, deletion and failure handling | Foundation exists; production integrations remain incomplete |

Each fix needs one owner, disjoint file ownership, a reviewed diff and exact-head
CI evidence. Tests use controlled fixtures; product live randomness remains fresh.
No package is complete merely because its code or documentation exists.

## Configuration policy

- User-facing choices belong in versioned saved settings or scene parameters.
- Device budgets derive from measured/probed capabilities and an explicit quality tier.
- Product IDs, endpoints and deployment identities belong in reviewed configuration;
  secrets never belong in Android resources or committed source.
- Mathematical constants, ABI/schema indices, safety bounds and dependency pins
  remain named and documented. Removing all numeric constants would weaken the code.
- A displayed control must trace to saved state and an active renderer/platform
  consumer. Unsupported controls are hidden or explicitly unavailable; fake success
  and decorative meters cannot substitute for behavior.

Latest audits: [app](APP_BUG_CONFIG_AUDIT.md), [native](NATIVE_BUG_CONFIG_AUDIT.md),
[camera](VISUAL_STYLE_CAMERA.md), [references](VISUAL_REFERENCE_EVIDENCE.md).
Independent findings and proposed reproductions: [INDEPENDENT_BUG_HUNT.md](INDEPENDENT_BUG_HUNT.md).
