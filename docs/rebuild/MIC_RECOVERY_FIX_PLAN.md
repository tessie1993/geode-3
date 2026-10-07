# Microphone recovery fixes — 7 October 2026

Requested by the owner after reviewing Claude's work. Baseline: main
`0d56c540166e3254114ffbdc6ec68a1ba5d9f543`. Claude's additional documentation
commits through `e451178` are outside this implementation branch.

## Scope and delegation

One implementation agent owns the four coupled fixes below, on
`codex/mic-recovery-fixes-20261007`. The coordinating agent reviews the complete
diff and integration paths. Use the available Codex agent for the owner's current
delegation request. Preserve the current Media3/AAudio architecture; a stack
migration is outside this bug-fix scope. Do not modify unrelated licence work.

Builds, tests and lint run in GitHub Actions, following the existing repository
rule. Source inspection is not runtime verification. Prepare regression tests
with each fix; do not claim they pass until CI supplies that evidence. Do not
merge into main without green CI.

## 1. Capture discontinuities and stale analysis

Problem: native recovery returns zero frames while the analysis ring retains its
last window. Analysis consumes that window repeatedly. On reopen, old and new
route samples can share a window, even after the sample rate changes.

Plan:
- Expose capture discontinuities independently of sample-rate changes.
- Invalidate buffered PCM and reset published analysis at disconnect, before
  accepting samples from a reopened or replacement source.
- Have analysis detect capture boundaries and stalled PCM, settling to silence
  during missing input. Preserve the native tracker's fixed 62.5 Hz cadence during
  ordinary inter-chunk gaps; freshness detection must not rescale beat timing.
- Make buffer reset/writes and source ownership safe across stop/start races.
- Do not perform renderer or UI work directly on the native capture path.

Acceptance: after valid audio followed by a disconnect, old energy and beats are
not analyzed again as new input. Same-rate and different-rate reconnects start
cleanly, and stopped workers cannot write into the new capture session.

## 2. AudioRecord fallback after AAudio recovery fails

Problem: the fallback is only selected at initial startup. Exhausting native
reopen attempts ends capture and clears the microphone preference.

Plan:
- After the bounded native recovery policy fails, close the AAudio source and
  try the existing AudioRecord backend once for that recovery episode.
- Keep the capture session active on successful fallback and publish its format
  before its first samples. Reset continuity as in fix 1.
- If no backend succeeds, surface the existing unavailable state.
- A user stop must cancel pending recovery and prevent a late replacement from
  publishing samples; every source must be released exactly once.
- Keep microphone-specific fallback out of other-app playback capture.

Acceptance: a scripted native terminal failure followed by a valid fallback
continues capture; both failing stops it; stop during replacement releases any
late-opened source without restarting capture or writing PCM.

## 3. Continue AudioRecord configuration attempts after start failure

Problem: openRecord returns the first initialized recorder's start result, even
when that result is null, skipping all remaining configurations.

Plan:
- Return only a successfully started source; otherwise continue the existing
  rate/encoding order.
- Preserve release of rejected recorders and avoid duplicate release.
- Exercise the real configuration-selection seam using injectable open/start
  results rather than testing an unrelated copy of the loop.

Acceptance: first configuration initializes but fails start, second succeeds;
all fail returns unavailable; first success short-circuits further attempts.

## 4. Refresh capture read sizing on every reopen

Problem: AAudioMicSource reads the burst size only at initial creation, and the
pump allocates one permanent read buffer. Reopened devices can have another burst
size even when their sample rate is unchanged.

Plan:
- Refresh the clamped burst-based read size when the stream generation changes.
- Have the pump honor the current frame count safely, including shrink and grow
  transitions, without allocating a new buffer on every steady-state read.
- Keep the JNI/native upper bounds and interleaved sample/frame units consistent.

Acceptance: scripted same-rate reopen changes read size in both directions;
subsequent reads request the new count, and only returned frames reach the sink.

## Review and verification

- Inspect the implementation and regression tests together against each scenario.
- Check microphone start, disconnect, reopen, fallback, stop and restart ownership.
- Check effects on the shared playback capture pump and analysis consumer.
- Open a focused PR and inspect Kotlin tests/lint, native regressions, APK build,
  and emulator CI jobs. Clearly record any inaccessible or failing gate.
- Device-only follow-up: headset/USB/Bluetooth route changes, microphone permission
  revocation, rapid toggles, and measured capture-to-visual latency.

## Android skill verification plan — owner steering at 18:26 CEST

Skills read: Android Emulator QA and Android Performance. Implementation resumes
only after this revision. Evidence available now: baseline main's Actions run
`37646893676` passed the APK build, Kotlin tests/analysis/lint, native regressions,
and existing emulator instrumentation/navigation smoke. These are baseline checks,
not evidence for this patch. No local adb binary or emulator access is available.

1. Finish and review deterministic tests for each of the four reported scenarios.
   Test scripted failures at production seams; do not claim physical route events
   or performance measurements from these tests.
2. Add real Android microphone lifecycle instrumentation to the existing disposable
   CI emulator: grant RECORD_AUDIO, verify capture opens, receives PCM, reports a
   valid rate, stops publication after stop, and survives repeated start/stop.
   Inspect the actual feature availability and record any missing device capability
   instead of treating a skipped hardware scenario as a pass.
3. Retain the emulator serial/API/model, test-run output, microphone logcat, crash
   log and before/after memory evidence with the CI run. Use the emulator skill's
   UI-tree-derived bounds if a UI-driven step is added; do not guess coordinates.
4. Open a focused PR, run Actions, inspect failures and fix them before reporting
   completion. Check the final commit, rather than an earlier passing run.
5. For physical-device follow-up, choose the focused flow: microphone visualization,
   route removal/reconnect, then stop. Use Perfetto to inspect capture-thread
   scheduling, lock waits and main-thread stalls; Simpleperf only if CPU cost is
   the question. Capture exact device/API/build/run counts and preserve each trace.
   Emulator timing, source code, buffer arithmetic and memory snapshots do not prove
   low latency or absence of native leaks. Actual headset/USB/Bluetooth behavior and
   capture-to-visual latency remain explicitly unverified without physical evidence.

## 5. Bind analysis publication to its configured sample rate

Source review found a race between applying native analyzer configuration and
snapshotting PCM: a capture reconnect can publish a new rate and epoch between
those operations, allowing one new-format window to use the old native rate.

Plan:
- Return the rate actually applied to the native analyzer and pass it explicitly
  into the analysis tick.
- Check that rate against the currently published input rate before native
  analysis and again before publishing the resulting features, retaining the
  existing epoch and pending-reset guards.
- Serialize rate publication with the feature-publication gate. Keep native FFT
  work outside that lock so capture never waits for analysis to finish.
- Add deterministic tests at the production frame gate, including a rate change
  after configuration but before the PCM snapshot, a change after snapshot but
  before analysis, and a change during analysis. Prove the next correctly
  configured tick can publish normally.

Acceptance: a window whose rate differs from the configured native rate never
reaches native analysis; a format/epoch change during native analysis suppresses
its feature publication. Matching-rate uninterrupted frames still publish. These
checks run in Actions, with no local build, tests or lint.
