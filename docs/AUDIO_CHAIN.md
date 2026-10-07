# The Audio Chain

Where playback audio goes, in what order, and which of those stages the visuals
can see. Everything here is already enforced somewhere in the tree; this
collects it so the next person to add an audio feature does not have to
reconstruct it from three files and a test.

Sources of truth, if this doc and the code ever disagree — the code wins:

| What | Where |
|---|---|
| The chain, and why the order is this order | `audio/dsp/MvzAudioProcessorChain.kt` |
| Where it is installed | `audio/TapRenderersFactory.kt` |
| The two rules | this document; the `AudioChainContractTest` that pinned them is not in the tree (see the README's Tests section) |
| Platform effects (a different mechanism) | `audio/AudioFxController.kt`, `ui/EqualizerSettings.kt` |
| The microphone path | `audio/MicCapture.kt`, `audio/MicSourcePlan.kt`, `audio/AudioCapturePump.kt`, `core/audio/capture/MicStream.cpp` |

## The order

```
ExoPlayer
   │
   ▼
DefaultAudioSink  ── MvzAudioProcessorChain ──────────────────────────┐
   │                                                                  │
   │   1. TeeAudioProcessor(PcmTap)       ← the analysis tap          │
   │   2. SilenceSkippingAudioProcessor   ← media3's own              │
   │   3. SonicAudioProcessor             ← media3's own (speed/pitch)│
   │   4. …our DSP stages…                ← empty today              │
   │                                                                  │
   ▼                                                                  │
AudioTrack ──▶ platform audiofx (Equalizer / BassBoost / Loudness) ──▶ speaker
                                       ▲
                        attached to the audio session id,
                        outside the chain entirely
```

The tap (`engine/audio-android/.../PcmTap.kt`) writes into the PCM ring that
`AudioBus` hands to the analysis engine and every scene. Stages 2–4 are downstream of it.

## Why the chain is owned rather than configured

`DefaultAudioSink.Builder.setAudioProcessors(...)` looks like it takes an
ordered chain. It does not. It wraps the array in media3's own
`DefaultAudioProcessorChain`, which allocates `length + 2` and **appends**
`SilenceSkippingAudioProcessor` and `SonicAudioProcessor` after everything you
passed in. Both accept `ENCODING_PCM_16BIT` only, so the first stage of ours
that emitted float would make the pipeline's `configure` throw and playback
would die at track start, with a stack trace pointing into media3.

Using `setAudioProcessorChain` and supplying our own puts media3's two stages
*before* ours. That fixes the ordering and the format problem in one move: a
float stage added at position 4 has nothing 16-bit-only left downstream to
offend. With no DSP stages present, `MvzAudioProcessorChain` is byte-for-byte
equivalent to the `setAudioProcessors(arrayOf(tap))` call it replaced.

If you hand-roll `AudioProcessorChain` again, `applyPlaybackParameters` and
`applySkipSilenceEnabled` are not optional plumbing — they are the only route
by which `PlaybackParameters` and the skip-silence toggle reach the two stages
that implement them. A chain that omits them breaks speed, pitch and
skip-silence with no error.

## Two rules that fail the build

Both were pinned by `AudioChainContractTest` when it existed; the test is not
in the tree, so today they are conventions that this document keeps.

**1. Never enable float output.** `setEnableFloatOutput(true)` reads like the
obvious way to get a float pipeline. It is the opposite. `DefaultAudioSink`'s
own javadoc says *"Audio processing (for example, speed adjustment) will not be
available when float output is in use."* Float output does not give us a float
chain — it removes the chain, and the `TeeAudioProcessor` that feeds every
visual in the app goes with it. The failure is silent: no crash, no log, a
visualizer that never moves and an equalizer that does nothing. **A float chain
is built by having the stages work in float internally**, not by asking the
sink for float output.

This is also why #21 bit-perfect and #23 hi-res are a product question rather
than an engineering task: float output is the only escape from media3's int16
conversion, and taking it deletes the visualizer.

**2. The analysis tap stays first.** `ReactiveAnalyzer.reset` and
`AnalysisEngine.reset` both hold live features to matching the cached and
exported features for the same file — the live and offline paths run the same
`ReactiveAnalyzer`, so they can only agree if they are also fed the same audio — and the offline path decodes the file with
no user EQ in it. Put user-tunable DSP upstream of the tap and live visuals
diverge from every exported video, differently for every user and every preset,
with no test able to pin it. The loudness seek bar, drawn from the offline RMS
curve, would disagree too.

The test carries a list of stage names the chain is expected to grow
(`GainProcessor`, `EqProcessor`, `ConvolutionProcessor`, `CrossfeedProcessor`,
`StereoMatrixProcessor`, `DynamicsProcessor`, `DitherProcessor`). Wiring any of
them ahead of the tap fails the build. Add new stage names to that list when you
add the stage.

## The consequence nobody expects: DSP does not move the visuals

Because the tap is first, **everything the user can do to the sound is
inaudible to the picture.** Fold to mono, cut 12 dB of bass, apply ReplayGain,
crossfeed — the spectrum the scenes draw does not change, because it was
sampled before any of it. The platform equalizer is even further downstream: it
attaches to the audio session id, after `AudioTrack`, outside the chain
entirely.

That is a deliberate trade, not an oversight — it is what buys live/export
parity — but it is surprising enough that it needs saying **once, in the UI, on
every audio-DSP screen**. Otherwise it gets filed as a bug once per feature: the
audio category is a dozen features, and each one will look broken to someone who
turns a knob while watching the visualizer.

If output metering is ever wanted, it gets a **second** tap, downstream, feeding
meters only — never `AudioFeatures`.

## Platform effects are a separate mechanism

`AudioFxController` attaches `Equalizer`, `BassBoost` and `LoudnessEnhancer` to
the audio **session id**, not to the chain. Two consequences the UI already
states:

- There is no session until audio starts, so the controls cannot attach before
  playback — hence *"Play something first."*
- Availability is a device lottery; a device may grant one effect and refuse
  another, and the card reports what it actually got rather than pretending.

When an in-chain EQ lands (#24), the two stack silently — both are real, both
apply — so that slice has to decide whether the platform equalizer is retired
or kept behind an explicit "System FX (legacy)" toggle. Shipping both without
deciding means every EQ curve is applied twice on some devices and once on
others.

## The microphone path

The microphone never touches the chain above. `MicCapture` writes into the same
PCM sink the tap writes into, so the analysis and every scene read it the way
they read playback; only the capture is different.

```
MicCapture.start()
   │  MicSourcePlan.openFirst(Build.VERSION.SDK_INT)
   │
   ├─ API 28+ ─────────▶ AAudioMicSource ──▶ MicStream (core/audio/capture)
   │                       AAudio INPUT stream, blocking AAudioStream_read
   │
   └─ API 26–27, or AAudio would not open
                       ▶ AudioRecordSource ──▶ AudioRecord, mono
                │
                ▼
   AudioCapturePump   one reader thread, THREAD_PRIORITY_URGENT_AUDIO
                │     CaptureSource.read ──▶ PcmSink.write
                ▼
   the shared PCM sink ──▶ analysis engine and scenes
```

**AAudio.** The stream asks for `LOW_LATENCY`, `EXCLUSIVE` and then `SHARED` if
that will not open, float samples, one channel, and the device's own sample
rate, so nothing is forced to 44.1 kHz. A device that hands back 16-bit is
converted and one that hands back two channels is averaged to mono, in native
code, before Kotlin sees the frames. The input preset is `UNPROCESSED` when
`AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED` says so and
`VOICE_RECOGNITION` otherwise; a preset the stream cannot open with is retried
with the other. The buffer is requested at two bursts. Each read is one burst,
bounded to 64–1,024 frames, and waits at most 40 ms.

**Why blocking reads.** AAudio does not allow a stream to be closed from its own
callbacks, and reopening is exactly what a route change needs. With no data
callback nothing runs on a real-time thread, and the pump's ordinary reader
thread closes and reopens the stream itself. The API 28 input-preset call is a
weak reference behind `__builtin_available(android 28, *)`, so `minSdk` stays 26.

**Route changes.** A recoverable AAudio read error closes the stream and advances
its generation immediately. Later reads attempt to reopen with bounded backoff;
a successful reopen advances the generation again. The pump invalidates both PCM
rings and published analysis at each boundary, including same-rate reconnects.
It reports a changed sample rate before writing new PCM and refreshes the clamped
burst-based read size. The Kotlin read buffer changes only when its required size
changes; steady-state reads reuse it.

If native recovery is exhausted, the microphone pump releases AAudio and tries
AudioRecord once. A successful fallback keeps capture active; a failed fallback
ends it and lets the existing controller report unavailable. Other-app playback
capture does not receive this microphone-specific fallback.

**Stopping.** `stop()` invalidates the pump generation under the same lock used
for PCM and format publication. A blocked read or fallback open that returns
late cannot publish into a new session. The reader owns source release; a late
replacement is released without being adopted. Stop waits up to 500 ms for the
reader and can return before a blocked open finishes. Native read, close and
reopen operations remain on the owning reader thread.

**The AudioRecord path** is used below API 28, whenever AAudio will not open, and
when native recovery fails. It tries the device's native output rate first
(`PROPERTY_OUTPUT_SAMPLE_RATE`, usually 48 kHz), then the remaining 44.1, 48 and
22.05 kHz rates without duplicates; float and then 16-bit; a buffer of two
minimum sizes; and 256-frame reads. An initialized recorder whose start fails is
released, then the next configuration is tried. `PlaybackCapture` retains
AudioRecord with 1,024-frame reads and a buffer of four minimum sizes.

**Analysis continuity.** Ring boundaries discard old PCM, and analysis waits for
a complete fresh FFT window. The legacy renderer ring retains monotonic sample
indices so a renderer cursor resumes without consuming pre-boundary audio. The
native analyzer keeps its fixed 62.5 Hz cadence during ordinary inter-chunk gaps;
a previously copied window can be reused during that bounded interval. With no
fresh PCM for two FFT-window durations (at least 48 ms), analysis resets to
silence. An explicit disconnect bypasses that grace and clears immediately.
Offline export analysis is unchanged.

Verification limits:

- Scripted pump, configuration-selection, ring and analysis-input tests exercise
  these production seams. Passing results must come from the patch's Actions
  run; source inspection alone does not verify runtime behavior.
- Android lifecycle instrumentation covers actual capture opening, PCM delivery,
  repeated start/stop and publication stopping on the CI emulator. It does not
  simulate physical headset, USB or Bluetooth routing, permission revocation,
  native service failure, or a device HAL's reconnect behavior.
- No capture-to-visual latency measurement is available for this patch. Low-latency
  mode is a request; granted rate, burst, buffer, sharing and performance mode
  are logged under `geode.mic`. Neither buffer arithmetic nor emulator timing
  establishes physical-device latency or absence of native leaks. Use a focused
  device route-change flow with Perfetto for scheduling and lock waits; preserve
  the device/API/build identity and exact trace before making performance claims.

## Adding a stage

1. Implement `androidx.media3.common.audio.AudioProcessor`, working in float
   internally if it needs the headroom. Do not ask the sink for float output.
2. Pass it in `MvzAudioProcessorChain`'s `dsp` list, which keeps it after the
   tap and after media3's two stages.
3. Add its class name to this document's stage list.
4. Keep filter state across item transitions; reset it on seek. A stage that
   clears its history at every track boundary reintroduces the click gapless
   exists to remove. `onFlush` is the hook, but check on a device *when* media3
   actually calls it before relying on it to tell the two cases apart — this is
   the stage-side half of an invariant the queue side also depends on, and
   only the queue side is testable without a decoder.
5. Configuration belongs on `PlaybackSession`, never on a ViewModel: the service
   and the UI share one session and one player.
6. Add one line of UI copy saying it will not move the visuals.
