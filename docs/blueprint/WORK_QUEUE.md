# Active work and ownership

Updated 7 October 2026. This records work in progress, not release completion.
Current owner scope: all native styles and MilkDrop remain; new spatial content,
simulation and camera are C++/GPU; live movement is generative; captured takes
preserve the realized performance. GitHub Actions is the build/test/lint gate.

Current implementation ownership and acceptance criteria are in
[BATCH_CONTRACTS.md](BATCH_CONTRACTS.md). That contract supersedes the old
APK-first waiting gate: implement and review the batch before the next remote push.
No local compilation, testing or linting. No worker GitHub polling.

Earlier integrated changes include fresh native PCM delivery, stereo analysis,
preset transactions, Studio publication/ownership, visual journey/Hold handling
and UI layout fixes. Their existence does not prove the current combined head.
The debug APK and emulator evidence referenced in the conversation applies to
prior remote commits; this batch still requires its own Actions result.

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
