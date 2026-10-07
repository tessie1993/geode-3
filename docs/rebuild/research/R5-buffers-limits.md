# R5 - Buffers and hardcoded limits: audio -> analysis -> visuals, and the mic path

Read-only audit. Nothing was compiled, run or checked out.

| Ref | What it is | Used for |
|---|---|---|
| `origin/codex/account-visual-foundation` @ 09d646f | PR #9, "PR9" below | visual path |
| `origin/main` @ 03db04e | PR #10 merged | mic path |

`MicCapture.kt`, `AudioCapturePump.kt` and `PlaybackCapture.kt` are byte-identical on both refs, so the mic findings hold for either.

Evidence labels:
- **[V]**: verified by reading the ref.
- **[E]**: arithmetic or estimate built on verified constants.
- **[P]**: platform behaviour (Android/Media3) taken from documentation knowledge. It is not in the repo and must be measured on a device before it drives a design.

Unit conventions:
- "fr" = frames, one sample per channel.
- Everything fed to the renderer is **mono fr**, so fr = samples there.
- Times are ms at **44.1 / 48 kHz**.

Graphify note:
- The index is at main (fd467d1, then 03db04e), not PR9, so I used it for orientation only. Everything below was confirmed by reading the PR9 files.
- One Graphify callers query (`snapshotLatest`) was declined by the user. The dead-code claim for it rests on `git grep` on the PR9 ref.

---

## A. Data-path diagram, sizes and latencies

```
 SOURCES                      CAPTURE / TAP                     RINGS                               CONSUMERS
 ------------------------------------------------------------------------------------------------------------------------------
 PLAYER  ExoPlayer decode ->  Media3 chain:                                                        
   decoder buffer              [0] TeeAudioProcessor = PcmTap   PcmTap.staging 4096 fr x ch
   (1024..4096+ fr, BURST)         handleBuffer (playback thr)  (PcmTap.kt:104) -- writes the whole decoded
                               [1] silence-skip [2] Sonic(speed) buffer in <=4096-fr chunks, no pacing
                               [3] NativeDspProcessor (EQ..)       |
                               -> DefaultAudioSink -> AudioTrack   |    <- AudioTrack queue Q (>=250 ms typ, <=750 [P]) is
                                  -> HAL (H) -> speaker/BT              |      BEHIND the tap: the tap is Q+H ahead of the ear
 MIC     AudioRecord MONO     Pump thread (default prio):       |
   44.1k first, 48k, 22.05k      AudioRecord.read 1024 fr       |   (23.2 / 21.3 ms per read, avg wait 11.6 / 10.7)
   float|s16, buf = 4 x min      (AudioCapturePump.kt:66-95)    |
 CAPTURE AudioRecord (projection) same pump, STEREO float,      |
   48k first, 44.1k, 22.05k      1024 fr x 2ch                  v
                                                       captureSink (PlaybackEngine.kt:46-50)
                                                         |-> PcmRingBuffer 65 536 mono fr (mid) + 65 536 (side)
                                                         |      1486 / 1365 ms; usable 3/4 = 1115 / 1024 ms
                                                         |      -> GL thread: SurfacePcmFeed.read (wallpaper, dream)
                                                         |         or PlayerSession.latestPcm (app); <=4096 fr per GL frame
                                                         '-> SampleRing 65 536 fr x 2 ch (512 KB), 1486 / 1365 ms
                                                                -> AnalysisEngine worker (Dispatchers.Default), tick 16 ms wall-clock
                                                                   newest 2048 fr -> M/S -> JNI ReactiveAnalyzer (FFT 2048, 64 log bands)
                                                                   46.4 / 42.7 ms window, hop 705.6 / 768 fr
                                                                   -> AudioFeatures (64 bands, 128-pt waveform, 12 chroma) per tick
 FEATURES -> StateFlow -> **Main thread** (Dispatchers.Main.immediate collect, PlaybackEngine.kt:112,143) -> AudioBus.publish
             -> wallpaper/dream feeder thread sleep(16) -> VisualizerRenderer.features (@Volatile)
             -> app: Compose LaunchedEffect collect (EnginePlumbing.kt:46-49) -> VisualizerRenderer.features
 GL thread each frame (VisualizerRenderer.kt:183-210): setFeatures (237 floats) ; pushPcm(chunk)
   Renderer.pushPcm -> FramePcm.pending_ (4096 fr) -> beginFrame() -> frame_ (4096 fr, one snapshot/frame)
   -> every scene (active, outgoing, layer): acceptPcm
        ShaderScene.pcm_ 4096 -> 512-texel max-magnitude row -> R32F 512x2 tex
        MilkdropPcm 8192 -> submit chunks <=480 -> projectM (retains 576 = 13.1 / 12.0 ms) PROJECTM_MONO
        PcmPulse -> peak of newest block
   -> FBO A/B RGBA8 at (surface x supersample 1.0/1.25/1.4 x thermal 1/.85/.7/.6) -> composite -> eglSwap -> scan-out
```

### Per-stage latency [E from verified constants unless tagged [P]]

| Stage | Adds (ms) | Notes |
|---|---|---|
| Tap -> ring (player) | 0 | burst granularity 23-93 ms: head jumps by a whole decoder buffer (<=4096 fr = 92.9 / 85.3 ms) |
| Capture read chunk (mic, capture) | avg 11.6 / 10.7, max 23.2 / 21.3 | 1024 fr per blocking read (`AudioCapturePump.kt:122`) |
| Analysis tick wait | 0-16, avg 8 | 16 ms timer (`AnalysisEngine.kt:251`), not data-driven |
| FFT window centre delay | 23.2 / 21.3 | a Hann window of 2048 fr is centred half a window behind the newest sample |
| Native analysis compute | 1-3 | est. |
| Features -> Main thread -> consumer | 0-16+, avg 8 | double main-thread hop (see C-3); couples to UI jank |
| GL frame wait | 0-16.7 @60 Hz, avg 8.3 | FramePacer divides vsync (60 on a 120 Hz panel) |
| Render + compose + scan-out | 17-33 | 1-2 frames @60 Hz |
| **Sum, newest sample -> pixel** | **~74 avg (39-108)** | |

### Total live audio -> pixel estimates

| Path | Sound/ring-head -> pixel | Relative to what is *heard* |
|---|---|---|
| **Player** (ExoPlayer tap) | ring head -> pixel ~74 ms | Audible time = ring-head time + Q (AudioTrack queue) + H (HAL). Q is >=250 ms with Media3's `DefaultAudioTrackBufferSizeProvider` defaults (250-750 ms clamp) [P]; H is 20-100 ms wired/speaker, 150-300 ms BT [P]. **Pixels lead the sound by roughly Q + H - 74 = 200-300 ms (wired/speaker), 350-550 ms on BT.** The code never subtracts Q + H (see C-1). |
| **Mic** (current AudioRecord) | input path 20-60 ms [P] + chunk wait 11.6 / 10.7 avg + ~74 = **~105-145 ms** | the sound is live, so this is pure lag; 100 ms is visible on a kick |
| **Mic** (target AAudio, 4-10 ms input, burst-sized callbacks, no main hop) | 5-15 ms + ~66 = **~70-85 ms**; ~55-65 ms with data-driven analysis (E) | |
| **Other-app capture** (MediaProjection AudioRecord, mixer tap) | input path 20-40 ms [P] + chunk wait ~11 + ~74 = **~105-125 ms after the mixer** | audible = mixer + H (20-100 ms speaker, 150-300 ms BT). Visuals lag by 5-100 ms on speaker/wired and are roughly in sync or early on BT. Only a per-route user offset can fix this; H is unknowable here. |

The wallpaper and dream paths add the feeder `Thread.sleep(16)` (avg +8 ms, drifting phase) on top of the player or capture numbers.

---

## B. Buffer table

The "Derive?" column answers: should the size come from device facts? Those facts are `AudioManager` `PROPERTY_OUTPUT_FRAMES_PER_BUFFER` / `PROPERTY_OUTPUT_SAMPLE_RATE`, the stream's actual rate, the AAudio burst, the display refresh, and `GL_MAX_*`.

| # | Buffer, file:line | Size and units | ms @44.1 / 48 | Writer -> reader, sync | Overflow / underflow / stale | Derive? |
|---|---|---|---|---|---|---|
| B1 | AudioRecord buffer, mic: `MicCapture.kt:77,82-88` | bytes = max(4 x `getMinBufferSize`, 4096); mono; requested 44.1k, float or s16 | device-dependent | HAL -> pump thread, framework | overrun is silent (no xrun count read); underflow = blocking read | **Yes**: burst multiple. Requesting 44.1k when the HAL is 48k forces the resampler [P]. |
| B2 | AudioRecord buffer, capture: `PlaybackCapture.kt:101-102` | bytes = max(4 x min, 1024 x ch x 4); stereo float preferred | device | same | same | Yes |
| B3 | Pump read arrays: `AudioCapturePump.kt:66-67,122` | `FloatArray` and `ShortArray` of 1024 x ch (1024 fr) | 23.2 / 21.3 | pump thread only; blocking read | none | **Yes**: read size = burst (4-10 ms), not 1024 |
| B4 | `PcmTap.staging`: `PcmTap.kt:30,47-48,104` | `FloatArray` 4096 x ch (4096 fr) | 92.9 / 85.3 | Media3 playback thread only | loops chunks; 24/32-bit input silently dropped (`PcmTapFormat.kt:18-23`) | Keep |
| B5 | `PcmRingBuffer.data` / `sideData`: `PcmRingBuffer.kt:6,14-16` | 2 x 65 536 mono floats (mid, (L-R)/2), 256 KB each; `Long` indices | 1486 / 1365; usable 3/4: 1115 / 1024 | writers: playback thread **or** pump thread (two possible; no writer lock). Reader: GL thread. Volatile `writeIndex` + `AtomicLong` sequence/CAS (`readNewSince` only) | overflow overwrites oldest; reader clips to min(out.size, 3/4 ring); underflow = 0 / null; stale = cursor kept | size = rate x (max lead 0.75 s + 0.25 s) |
| B6 | `SampleRing`: `SampleRing.kt:6,18`, `PlaybackEngine.kt:37` | 65 536 fr x 2 ch floats = 512 KB; `maxWriteFrames` 16 384 (the write `require`s, i.e. throws on the audio thread, if exceeded) | 1486 / 1365; kept history 1115 / 1024 | writers `synchronized(writerLock)`; analysis reader lock-free seqlock without fences (C-9) | overflow overwrites; reader gets null until 2048 fr; `beginEpoch` zeroes `written` | derive from rate |
| B7 | `MidSideWindow` arrays: `MidSideWindow.kt:12-16` | planar 2 x 2048 + mid 2048 + side 2048 floats | 46.4 / 42.7 window | analysis worker only | same position -> skipped | derive from rate (FFT size) |
| B8 | `AudioFeatures` arrays: `AnalysisEngine.kt:152-188` | bands 64, waveform 128, chroma 12 floats, allocated every tick (62.5/s, ~1 KB) | tick 16 | analysis thread -> StateFlow -> Main -> GL | StateFlow conflates; blank after 250 ms idle | keep |
| B9 | `AudioBus.latest`: `AudioBus.kt:6,17-30` | 1 reference; `STALE_MS` = 1500 | 1500 | Main -> feeder thread (volatile) | stale >1.5 s -> null -> idle animation | keep |
| B10 | `NativeViz.featureFrame`: `NativeViz.kt:37` | `FloatArray(237)` = 33 + 64 + 128 + 12 | per GL frame | GL thread -> JNI copy -> `Renderer.features_` under `stateLock_` | the same frame is re-sent every GL frame; nothing can go stale | keep |
| B11 | `SurfacePcmFeed.scratch`: `SurfacePcmFeed.kt:31,87` | 4096 mono floats | 92.9 / 85.3 | GL thread; `@Synchronized` vs the feeder's `publishIdle` | newest 4096 only; null on writer overlap | keep |
| B12 | `PlayerSession.pcmScratch`: `PlayerSession.kt:373-380` | 4096 mono floats | 92.9 / 85.3 | GL thread; legacy `copyNewSince` + shared `lastCopyEndIndex` | newest 4096 | keep |
| B13 | `FramePcm.pending_` / `frame_`: `PcmDelivery.hpp:47,70-71` | 2 x 4096 floats (mono) | 92.9 / 85.3 | `pushPcm` runs on the GL thread in practice; under `std::mutex stateLock_` | newest-kept; consumed once per frame; empty -> no delivery | keep (see D) |
| B14 | `ShaderScene.pcm_`, row, tex: `ShaderScene.cpp:33,40-45,126-138`, `ShaderScene.hpp:22,105,136` | 4096 floats; 512-texel row (max-magnitude decimation); R32F 512 x 2 | window = 1 frame: 735 / 800 fr @60 | GL thread | no PCM this frame -> falls back to the 128-pt feature waveform (C-8) | derive window from time, not frame |
| B15 | `MilkdropPcm.pending_`: `PcmDelivery.hpp:80,141` | 8192 floats; submit chunks <= `projectm_pcm_get_max_samples()` (480 per the repo comment); projectM keeps 576 | 185.8 / 170.7; 576 = 13.1 / 12.0 | GL thread | input gap >100 ms -> one 576-sample silence flush; never re-feeds old audio | keep (engine-defined) |
| B16 | `AnalysisSession.buffer_` / `sideBuffer_`: `AnalysisSession.cpp:13,33-34,65-99` | 4 x fft growing to 8 x fft (8192 -> 16 384) floats x 2; `vector::resize` in `push` | n/a | hop-locked native path; **not used by the live loop** | drops oldest; hop clamped to fft size (`:19-22`) | derive |
| B17 | `AudioPresentationClock`: `AudioPresentationClock.kt:5,40` | 64 segments, copy-on-write list per append | n/a | tap flush -> (nobody) | nothing reads it | n/a |
| B18 | AudioTrack buffer (Media3 `DefaultAudioSink`) | not in repo; >=250 ms, <=750 ms [P] | 250+ | **behind** the tap | the dominant latency, never tapped | measure `AudioTrack.getTimestamp` |
| B19 | `NativeDspProcessor.scratch`: `NativeDspProcessor.kt:48,91` | direct `ByteBuffer`, grows to the largest input, never shrinks | n/a | playback thread | allocates views per buffer | keep |
| B20 | GL render targets: `Renderer.cpp:443-455`, `Framebuffer.cpp:18` | fboA/fboB (+ MilkDrop `frame_`) RGBA8, W x H x supersample x tier | n/a | GL | no clamp to `GL_MAX_TEXTURE_SIZE` (probed at `GlProber.cpp:672`, never read) | **Yes**: GL limits + pixel budget |

---

## C. Mistakes, ranked by what the user sees or hears

Status key: **fixed in PR #9**, **still present**, **new in PR #9**.

### Fixed in PR #9 (verified in the diff vs main)

- **F1 Stale PCM re-delivery to scenes.**
  - Before: the old `deliverPcm` copied `pcm_[0..pcmCount_)` out under the lock on every call and never consumed it. The same block was re-fed every frame and again to the outgoing and layer scenes.
  - Now: `FramePcm::beginFrame` consumes pending input once per frame under `stateLock_` and shares one immutable snapshot with all scenes (`RendererFrame.cpp:41,147-158`, `PcmDelivery.hpp:45-72`).
- **F2 MilkDrop stale re-feed.**
  - Before: with no fresh PCM it pushed the 128-pt `features.waveform` into projectM every frame.
  - Now: a bounded newest-keeping queue is chunked by the engine max (`MilkdropScene.cpp:223-225`).
  - Silence handling: after 100 ms with no input, one 576-sample silence flush (`PcmDelivery.hpp:78-131`). Old audio is never re-submitted.
- **F3 Unchanged-window re-analysis and mixed windows.**
  - `Position(epoch, frames, sourceChannels)` equality skips an unchanged window (`MidSideWindow.kt:34`).
  - Epoch, generation and rate are coupled in `AnalysisInput` (`:61-75`). A window must lie entirely after a restart or rate change, the rate is captured once per window (`AnalysisEngine.kt:145-148`), and a late FFT cannot publish over a reset (`:192`).
- **F4 Shared consumer cursor.** `SurfacePcmFeed` gives each wallpaper, preview and dream its own cursor, starting at the head, with `readNewSince` returning the exact end index. This removes the shared mutable `lastCopyEndIndex` race between consumers (`PcmRingBuffer.kt:86-98`, `SurfacePcmFeed.kt:39-46`).
- **F5 Poisoned input.** Non-finite PCM is rejected (`Renderer.cpp:109-117`) and the feature frame is validated (`InputAdmission.hpp:34-60`).
- **F6 Features frozen on pause.** Now blank after 250 ms idle (`AnalysisEngine.kt:137`). This trade-off is reported as C-4.
- **F7 Multi-writer on SampleRing.** `synchronized(writerLock)` added (`SampleRing.kt:71`).
- **F8 Narrow low bands.** Log bands narrower than an FFT bin now share that bin's power by overlap (`LogBands.cpp:42-61`).
- **F9 Stereo power.** Mid and side power are summed so anti-phase stereo is not lost (`Spectrum.cpp:32-40`, `ReactiveAnalyzer.cpp:63-68`).

### Open findings

| # | Sev | file:line | What is wrong | What the user sees / hears | Fix | Status |
|---|---|---|---|---|---|---|
| **C-1** | **HIGH** | `PcmTap.kt:60-85`; `MvzAudioProcessorChain.kt:23-24`; `AnalysisEngine.kt:135`; `SurfacePcmFeed.kt:75`; `PlayerSession.kt:377`; `PlaybackEngine.kt:42-44`; `AudioPresentationClock.kt:49-76` | The tap is the **first** processor, before the AudioTrack, so the ring head is Q + H ahead of the ear. Every consumer reads the newest samples. `AudioPresentationClock` and `SinkClockDriver` are built and fed at every tap boundary, but a repo-wide `git grep` finds **no caller** of `inputPositionAt` or `presentationTimeOf` outside tests. | Beats, flashes, MilkDrop and scene pulses land **~200-300 ms before** the sound (more on BT). Visuals stop or jump before the audio at pause, seek and track change. | Choose the analysis window end and the PCM cursor from the audible input position (`clock.inputPositionAt(now - H)` or Media3 position). Ring capacity already covers 750 ms + window. Add a per-output-route user offset; measure H with `AudioTrack.getTimestamp`. For capture and mic, offset only. | **Still present** (PR #9 added epoch/generation bookkeeping, not compensation) |
| **C-2** | **HIGH** | `AnalysisEngine.kt:218-229,251,258-259`; `AnalysisInput.kt:61-75`; `MidSideWindow.kt:31-53`; `PcmTap.kt:73-83`; `ReactiveAnalyzer.hpp:24` | The hop is wall clock: a 16 ms `delay()` loop that always grabs the **newest** 2048 fr. The head advances in decoder bursts of up to 4096 fr, which is larger than the window, so up to half the audio is **never analysed**. Unchanged windows are skipped (PR #9), but jumps are not tiled. Every tracker (tempo, flux, bar) assumes exactly 62.5 frames/s of audio time, and `DT_SECONDS` is a constant. | Missed or doubled hits, flux spikes at burst edges, BPM wander (TempoTracker quantises integer periods at 62.5 Hz, ±3% near 200 BPM), beat flashes jittering by up to a tick. | Data clock: keep a read cursor and analyse every hop (rate / 62.5, fractional accumulator, or hop = rate / display Hz), with dt = hop / rate. The native hop-locked `AnalysisSession.push/pull` (`AnalysisSession.cpp:65-120`) already does this and is unused live. | **Still present** (PR #9 only skips repeats) |
| C-3 | MED | `PlaybackEngine.kt:112,143-147`; `EnginePlumbing.kt:44-49`; `VisualizerWallpaperService.kt:119-135`; `VisualizerDreamService.kt:107-123` | Features go analysis thread -> StateFlow -> **Main thread** (`Dispatchers.Main.immediate` collect) -> `AudioBus.publish`. The app path then hops through a Compose `LaunchedEffect`. The wallpaper feeder is a separate `Thread.sleep(16)` loop (drifting period, not deadline). | +0-16 ms (avg 8) plus UI-jank coupling. A scrolling list can stall the beat. Wallpaper features show sample-and-hold jitter. | Publish a volatile/`AtomicReference` frame directly from the analysis thread. Let the GL thread read it each frame; drop the feeder thread (it exists only to publish idle features). | Still present |
| C-4 | MED | `AnalysisEngine.kt:131-138,253`; `ReactiveAnalyzer.hpp:78`; `AdaptiveRange.hpp` warmup 1.5 s; `StructureTracker.hpp:8` warmup 5 s | Any >=250 ms input stall (pause, seek, buffering, CPU or thermal starvation, `delay()` hiccup) calls `analyzer.reset()`, which wipes range, whitening, tempo, bar and structure state. Seek and track change also bump the epoch. | After every pause longer than a quarter second, or any seek, visuals go flat or over-sensitive for 1.5-5 s and BPM/bar phase vanish. | Split "blank the outputs" (250 ms) from "forget learned state" (>= 5-10 s or a source change). Keep tempo across seeks within a track. | **New in PR #9** |
| C-5 | MED | `MicCapture.kt:75,114` | The mic asks for **44.1 kHz first**; most phone input HALs are 48 kHz native [P]. This forces the platform resampler, defeats the fast-capture path, and 44.1 is baked in as a default. | Extra mic lag (est. 10-40 ms), alias artefacts, laggy mic visuals. | Use the native rate (`PROPERTY_OUTPUT_SAMPLE_RATE`, or AAudio unspecified rate). | Still present |
| C-6 | MED | `AudioCapturePump.kt:92-95,101`; `CaptureController.kt:244-250` | On any read error (`ERROR_DEAD_OBJECT`, headset unplug, BT route flip, mediaserver restart) the worker `break`s and the pump is dead. There is **no reopen**. The UI notices only by polling `active` and shows "unavailable". | Mic or capture visuals go silent after a headset/BT change until the user toggles the mic. | Reopen with backoff in a supervisor (not the callback). Register `AudioDeviceCallback`. | Still present |
| C-7 | MED | `AudioCapturePump.kt:65,108-119` | The capture thread runs at default priority (no `THREAD_PRIORITY_URGENT_AUDIO`). `stop()` is `@Synchronized` and `join(500)`s **on the calling (UI) thread** while holding the pump lock. | Occasional capture overruns under GPU load; up to 500 ms UI hitch on a mic toggle if `read` is stuck. | Raise priority; make stop asynchronous (with a callback stream there is no join). | Still present |
| C-8 | MED | `ShaderScene.cpp:126-138`; `SceneCommon.hpp:105-120` | The scope waveform is built from **only this frame's PCM**: 735 / 800 fr @60 Hz, 367 / 400 @120 Hz, 1470 / 1600 @30 Hz. Its window length is the frame period. When a frame has no PCM it falls back to the 128-pt analysis waveform (a different source). | Oscilloscope scale changes with refresh rate and thermal tier; the shape flickers between two sources. | Keep a rolling 1024-2048-fr window (~23-46 ms) in `FramePcm`; no live fallback. | Still present (PR #9 fixed MilkDrop only) |
| C-9 | MED | `SampleRing.kt:23,71-86,92-111` | Seqlock without fences. The writer's `revision++` is a volatile store followed by plain stores, which may become visible before it. The reader's copy is plain loads between two volatile loads, which may sink below the second one. `PcmRingBuffer.kt:39-59,97` fixed this with `AtomicLong` increments and a CAS. SampleRing, the analysis input, did not get the same fix. | Rare torn 2048-fr analysis window -> one spurious spike or false onset. Practically rare, formally unsound on ARM. | Use `AtomicLong`/`VarHandle` fences as in `PcmRingBuffer`. | **New in PR #9** (the seqlock itself is new; main had none) |
| C-10 | MED | `Renderer.cpp:480-494,529-531` | `scenes_` is never evicted. Only `releaseScenes()` on surface (re)creation clears it. A fluid Ultra scene is 1024 x 1024 particle MRT (RGBA32F) + 1024 x 1024 dye + 256 sim (`FluidQuality.hpp:16`). | GPU memory grows as the user browses scenes; hitches, LMK kills, thermal. | LRU: keep active, outgoing, layer, + 1 warm. | Still present |
| C-11 | MED | `ThermalGovernor.cpp:7-15`, `ThermalGovernor.hpp:13` | `ThermalTierInfo.fpsCap` (30 at Minimal) is dead: Graphify and `git grep` show no reader. Minimal only lowers render scale to 0.6. | The phone stays at 60 fps while "Minimal"; heat persists. | Feed `fpsCap` into `FramePacer.policy` (`Capped(min(user, cap))`). | Still present |
| C-12 | MED | `Renderer.cpp:443-462`; `Framebuffer.cpp:9-32`; `GlProber.cpp:672` | No pixel budget and no `GL_MAX_TEXTURE_SIZE`/`GL_MAX_RENDERBUFFER_SIZE` clamp. Render size = surface x {1.4 <1600 px, 1.25 <2200, 1.0} x tier. `maxTextureSize` is probed and serialised but never used. Wallpaper surfaces can be 2x wide. | Black wallpaper or dream on low-limit GPUs; huge RGBA8 FBOs (and projectM `frame_`) on tablets and foldables. | `min(scale x surface, maxTextureSize, pixel budget by tier)`. | Still present |
| C-13 | MED | `AnalysisEngine.kt:263`; `ReactiveAnalyzer.kt:8`; `LogBands.hpp:8` | FFT fixed at 2048 at every rate. At 96 kHz the window is 21 ms with 46.9 Hz bins: the 30 Hz-start log bands collapse. At 22.05 kHz (mic fallback) it is 93 ms. Hop clamp for >= 128 kHz is silent (`AnalysisSession.cpp:19-22`). | Hi-res files get smeared bass bands; the 22.05 kHz fallback is laggy. | Pick the power of two closest to ~43 ms (1024 @22.05k, 2048 @44.1/48k, 4096 @96k). | Still present |
| C-14 | MED | `PlaybackEngine.kt:46-50`; `PcmRingBuffer.kt:30-60` | `PcmRingBuffer` is documented single-producer, but `captureSink` is shared by the ExoPlayer tap and the capture pumps. `pause()` is asynchronous, so a final tap write can overlap the first mic write. `SampleRing` has `writerLock`; `PcmRingBuffer` has none. | A glitch burst at mic or capture start (lost `writeIndex` update). | Writer lock or epoch handoff. | Still present |
| C-15 | LOW | `ReactiveAnalyzer.cpp:48-57` | The silence path returns before `flux_.next()` and `tempo_.step()`. SuperFlux history stays at its pre-silence frame, so the first frame after silence mismatches. | One spurious onset after a silent gap (mid-track silence, quiet-room mic). | Push zeros through whitening and flux. | Still present |
| C-16 | LOW | `VisualizerRenderer.kt:173,201-202`; `NativeViz.kt:84-91` | Per-frame JNI **string** arguments on the GL thread (`setScene`, `setLayer`, `setTransition`): `GetStringUTFChars` malloc x3 + `stateLock_` x3 per frame. | Tiny GC/malloc pressure; jank risk at 120 Hz. | Change-detect in Kotlin. | Still present |
| C-17 | LOW | `SurfacePcmFeed.kt:63,80-81` | `PcmChunk` allocated every frame; `waveform.copyOf()` every 16 ms. | GC pressure. | Reuse a holder. | Still present |
| C-18 | LOW | `PlayerSession.kt:376-380` | The in-app path still uses legacy `copyNewSince` + shared `lastCopyEndIndex`, not the validated `readNewSince`. `snapshotLatest` / `snapshotLatestSide` (`PcmRingBuffer.kt:117-134`) are dead (git grep). | Nothing today; a trap if a second consumer is added. | Use `SurfacePcmFeed` everywhere; delete the dead methods. | Still present |
| C-19 | LOW | `MilkdropScene.cpp:134-135` | `projectm_set_fps(60)` and mesh 48 x 32 are fixed regardless of paced fps or tier. | May mis-scale projectM frame-based decay at 30/90/120 fps (verify). | Pass the paced fps; tier the mesh. | Still present |
| C-20 | LOW | `RendererFrame.cpp:29` vs `FramePacer.kt:359-362` | The renderer's dt is wall time at `onDrawFrame` (clamp 1 ms-100 ms). The pacer's vsync-quantised dt (clamp 1/15 s) is not used. | Micro-jitter in motion. | Pass `frameTimeNanos`. | Still present |
| C-21 | LOW | `MicCapture.kt:100-111`; no `microphone` foreground-service type or permission in the manifest (`AndroidManifest.xml:30-52,200-260`) | VOICE_RECOGNITION fallback may apply AGC/NS. The mic pump lives in the UI `PlayerSession`, with no mic FGS. | Pumping levels on non-UNPROCESSED devices; silent mic when the app is backgrounded, so no mic-driven wallpaper or dream [P]. | Disable effects or use `MIC`; add FGS type `microphone` if the wallpaper is to use the mic. | Still present |
| C-22 | LOW | `SampleRing.kt:66-68` | `write` `require`s `frameCount <= 16 384`, i.e. it throws on the audio path. Safe today (max 4096 from the tap, 1024 from the pumps), but fragile. | Nothing today; a crash if chunks are enlarged. | Split the write instead of throwing. | Latent |
| C-23 | INFO | `PcmDelivery.hpp:85` | 735 / 800 fr per frame at 60 Hz against projectM's 576 retained: ~22% of samples are never seen. At 30 fps, ~61%. This is projectM's design. | n/a | Keep; note the repo's 480-vs-576 comment. | Info |
| C-24 | INFO (export-adjacent) | `RendererFrame.cpp:155-157`; `OffscreenSceneRenderer.kt:77-85` | The renderer feeds export MilkDrop a **128-pt block-averaged timeline waveform** as "PCM" (once per frame). Non-MilkDrop scenes get none. | Export looks different from live: projectM expects 576 real samples, and PCM-peak pulses are live-only. | Study with the export team. | Info |

---

## D. Hardcoded engine limits

Decisions: **keep**, **derive** (from device facts), **tier** (quality tier), **user** (user setting).

### Analysis and audio

| Limit | file:line | Value | Decision and why |
|---|---|---|---|
| FFT size | `AnalysisEngine.kt:263`, `ReactiveAnalyzer.kt:8`, `ReactiveAnalyzer.hpp:24` | 2048 | **derive** to ~43 ms (C-13); **tier** a 4096 bass-only band for < 250 Hz later |
| Band count | `geode_api.h:17`, `AnalysisEngine.kt:261` | 64 | **keep** (wire format and shaders) |
| Analysis hop | `AnalysisEngine.kt:251,258-259` | 16 ms / 62.5 Hz / DT 0.016 | **derive** from the data clock or display Hz (C-2) |
| Idle reset | `AnalysisEngine.kt:253` | 250 ms | **derive** >= 3 x producer chunk; separate blank from forget (C-4) |
| Pulse hold | `AnalysisEngine.kt:256` | 3 hops | **derive** = ceil(1.5 x frame period / hop) |
| Band edges / tilt | `LogBands.hpp:8-11` | 30-16 000 Hz, 3.01 dB/oct, ref 1 kHz | **keep**; top = min(16k, rate/2), bottom >= 1.5 bins |
| Silence RMS | `ReactiveAnalyzer.hpp:79` | 1e-5 | **keep**; mic profiles can add a floor (**user**: `LiveInputProfile`) |
| Waveform points | `geode_api.h:18` | 128 | **keep** for the feature frame (wire format); do not use as PCM |
| Chroma | `Chromagram.hpp:9-12` | 200-5000 Hz, attack 60 ms / release 350 ms | **keep** (musical) |
| Adaptive/structure timing | `AdaptiveRange.hpp:12`, `StructureTracker.hpp:8-24`, `OnsetPeakPicker.hpp:11`, `ReactiveAnalyzer.hpp:76` | 1.5 s warmup, 5 s warmup, 8 s peak memory, 20 s macro | **keep**; reset policy must change (C-4) |
| Tempo range | `TempoTracker.hpp:8` | 60-200 BPM, 64 resonators, half-life 4 s | **keep**; **user** range later |
| Native hop-locked buffers | `AnalysisSession.cpp:13` | 4-8 windows | **keep** |
| Input rate defaults | `AnalysisEngine.kt:33` (44100), `ReactiveAnalyzer.kt:9` (48000), `CaptureController.kt:122` (44100 fallback) | 44.1k / 48k | **derive** (stream rate); today harmless |
| SampleRing / PcmRingBuffer | `PlaybackEngine.kt:35,37`, `PcmRingBuffer.kt:6` | 65 536 fr | **derive** = nextPow2(rate x 1.0 s); stays 65 536 at 48k |
| Read chunk | `AudioCapturePump.kt:122` | 1024 fr | **derive** = burst (see E) |
| Buffer multiplier | `AudioCapturePump.kt:124` | 4 x min | **derive** |
| Capture rates | `MicCapture.kt:75`, `PlaybackCapture.kt:82,118` | 44.1k, 48k, 22.05k | **derive** |
| Channel count | `MicCapture.kt:43`, `PlaybackCapture.kt:83` | MONO (mic), STEREO then MONO | **keep**; **user**: stereo mic on devices that offer it |
| Silence grace | `AudioCapturePump.kt:128` | 4 s | **keep** |
| AudioBus stale | `AudioBus.kt:6` | 1500 ms | **keep** |
| PcmTap staging | `PcmTap.kt:104` | 4096 fr | **keep** (>= decoder block) |
| Feed windows | `PcmDelivery.hpp:47,80`, `SurfacePcmFeed.kt:87` | 4096 / 8192 / 4096 | **keep** (covers the 100 ms dt clamp); roll a fixed 1024-2048 for scopes (C-8) |
| MilkDrop grace / retained | `PcmDelivery.hpp:81,85` | 100 ms / 576 | **keep**; assert on projectM upgrade |

### Visual engine

| Limit | file:line | Value | Decision and why |
|---|---|---|---|
| Frame dt clamp | `RendererFrame.cpp:29` | 1 ms-100 ms | **keep** but align with `FramePacer` 1/15 s |
| Supersample | `Renderer.cpp:457-462` | 1.4 / 1.25 / 1.0 at 1600 / 2200 px | **tier** + **derive** (GPU class, pixel budget, `GL_MAX_TEXTURE_SIZE`) |
| Thermal tiers | `ThermalGovernor.cpp:7-15`, `.hpp:64-69` | scale 1 / .85 / .7 / .6; passes off from Reduced; fps cap 30 (dead); sample 1.25 s; escalate 3 s; relax 60 s; headroom .75 / .85 / .95 | **tier**; wire the fps cap |
| Perf monitor | `ThermalGovernor.hpp:22` | target 50 fps, 2.5 s sustain, window 30 | **derive** from paced fps (already partly) |
| Paced fps | `FramePacer.kt:356`, `VisualizerWallpaperService.kt:288` | 60 | **user** + battery tier; wallpaper should default 30 |
| FramePacer | `FramePacer.kt:374-389` | vsync 4-22 ms, max divisor 8, window 240 | **keep** (measured from vsync) |
| projectM | `MilkdropScene.cpp:134-135` | 60 fps, mesh 48 x 32 | **tier** + **derive** from paced fps |
| Fluid tiers | `FluidQuality.hpp:16-22` | Ultra 256 / 1024 / 1024-side particles / 28 iterations ... Min 64 / 256 / 160 / 12 | **tier** (user + auto); initial tier from GPU class |
| Scene sim grids | `AcidScene.hpp:36` (540), `LifeScene.hpp:36` (288), `SilkScene.hpp:36` (320), `MycoScene.hpp:35` (384 trail), `RippleSim.hpp:77` (384), `Overlays.hpp:44` (256), `FlowField.hpp:35` (64), `FluidLook.hpp:45-47` (bloom 256 x 8, sunrays 196) | | **tier** (scale with tier) |
| March budget | `MarchBudget.kt:23-32`, `ShaderScene.cpp:14-17` | 64-128 steps, detail 0.25-1.5 | **user** (Detail) + auto-tier |
| FBO format | `Framebuffer.cpp:18` | RGBA8 everywhere | **tier**: RGBA16F or R11G11B10 where probed (`GlCaps`), RGBA8 on low tier |
| Audio texture | `ShaderScene.hpp:22`, `.cpp:68` | 512 x 2 R32F | **keep** |
| Touch / overlay | `TouchField.hpp:10` (5 pts), `Overlays.hpp:42` (24), `RippleSim.hpp:63,65` (64 / 6) | | **keep** |
| Emitters | `FluidEmitters.hpp:57` | 16 splats/frame | **keep** |
| Caches | `ProgramBinaryCache.cpp:32` (256), `Transition.hpp:68` (4) | | **keep** |
| Flash budget | `VisualSafety.hpp:47-48` | 1 s window, 16 edges | **keep** (WCAG) |
| Scene cache | `Renderer.cpp:480-494` | unbounded | **tier** (LRU, C-10) |

---

## E. Sizing rules from device facts (including the AAudio mic design)

### Rules

| Quantity | Rule |
|---|---|
| Sample rate | The stream's actual rate: `AAudioStream_getSampleRate()` or `AudioRecord.getSampleRate()`. Fallback = `PROPERTY_OUTPUT_SAMPLE_RATE` (almost always 48 000). Never default to 44 100. |
| Callback / read size | `AAudioStream_getFramesPerBurst()` (96-480 fr, 2-10 ms @48k). AudioRecord fallback proxy: `PROPERTY_OUTPUT_FRAMES_PER_BUFFER`, clamped to 128-512. |
| Hardware buffer | Burst multiple: capacity 8 x burst (the app only needs enough slack for scheduler jitter; input buffer fill is latency only if the reader falls behind). |
| Software ring | nextPow2(rate x (fft window + lead + 4 x max chunk + 0.25 s)). Mic: 16 384 fr (341 ms @48k). Player: stays 65 536 (must hold Q <= 750 ms + window). |
| FFT size | Power of two nearest rate x 0.043 s (1024 @22.05k, 2048 @44.1/48k, 4096 @96k). |
| Analysis hop | Data-driven: advance a cursor by `round(rate / hopHz)` with a fractional accumulator, `dt = hop / rate`. Choose hopHz = display refresh or 62.5. |
| Visual PCM window | The newest N fr with N = clamp(round(rate x 0.0213), 512, 2048), independent of fps (C-8). Feed projectM the newest 576. |
| Render targets | `min(surface x scale, GL_MAX_TEXTURE_SIZE, GL_MAX_RENDERBUFFER_SIZE, pixel budget by tier)`. |
| Frame rate | FramePacer's measured vsync (already done). Thermal fps cap must be wired in. |
| Sync offset | A per-route (speaker / wired / BT / USB) user offset in ms, default from measured H where available. |

### AAudio mic design

The AAudio and AudioRecord platform behaviours below are [P]: documented Android behaviour that was not checked in the repo, so measure it on a device.

1. **Open.**
   - Direction INPUT, performance mode LOW_LATENCY, sharing SHARED (EXCLUSIVE optional). Input preset UNPROCESSED when `PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED` is "true", else VOICE_RECOGNITION (disable AGC/NS if exposed), else MIC.
   - Format FLOAT, channel count 1 (stereo as a user setting), sample rate **unspecified**.
   - After open read rate, channels, `framesPerBurst`, `bufferCapacity` into an atomic `StreamInfo { rate, channels, burst, epoch }`. Set capacity = 8 x burst.
2. **Callback -> SPSC ring (native, no JNI in the audio thread).**
   - The data callback does only: convert to mono mid (and side if stereo), copy into a pre-allocated power-of-two float ring, publish `writeIdx.store(w + n, release)`. No locks, no allocation, no logging, no JNI.
   - Readers snapshot with `writeIdx.load(acquire)`, copy the last N, then re-check `writeIdx2 - (r - N) <= capacity`. This is a lap check, not a seqlock; one writer, any number of readers.
   - Keep `epoch` (incremented on every reopen or rate change) in the same atomic block so `AnalysisInput`'s boundary logic works unchanged.
   - Overflow overwrites the oldest. Underflow returns "not enough yet".
3. **Disconnect and error recovery.**
   - `errorCallback` receives `AAUDIO_ERROR_DISCONNECTED` (and other errors). It must only post a message; AAudio forbids closing from that thread.
   - A supervisor thread closes and reopens with exponential backoff (100 ms -> 2 s) and a reopen cap. On success it bumps `epoch`, re-reads rate and burst, and tells analysis to reset.
   - Also register `AudioDeviceCallback` for add/remove (USB mic, headset) and reopen on a new preferred device. `AudioRecord` `ERROR_DEAD_OBJECT` follows the same path (C-6).
4. **Native consumers.**
   - Feed the analysis from the same ring on a native worker using the existing hop-locked path (`AnalysisSession.push/pull`), which removes the JNI array copies and the GC, and fixes C-2.
   - The GL thread keeps its own cursor into the same ring for the visual PCM feed. Features go out through one atomic frame, not through Main (C-3).
5. **AudioRecord fallback sizing.**
   - Sample rate = `PROPERTY_OUTPUT_SAMPLE_RATE`; mono; FLOAT (API 23+), else s16.
   - Buffer bytes = max(2 x `getMinBufferSize`, 4 x readFrames x bytesPerFrame).
   - `readFrames` = burst proxy (128-512), not 1024. Worker at `THREAD_PRIORITY_URGENT_AUDIO`. Same `StreamInfo` and ring.
   - Detect `ERROR_DEAD_OBJECT` and reopen; poll `getRoutedDevice()`.
   - Expect a higher latency floor than AAudio [P]; report it so the sync offset can default accordingly.
6. **Manifest and background.**
   - Wallpaper or dream mic needs an FGS of type `microphone` plus `FOREGROUND_SERVICE_MICROPHONE`; neither is declared today (C-21). Without them Android 11+ delivers silence to a backgrounded app [P].
   - The existing `silenceLikely` check (4 s of exact zero) is the right detector for that case; surface it.
7. **Mid-stream changes.**
   - Treat rate, channel count or device changes as a new `epoch`. Do not retune the FFT mid-window; the existing `AnalysisInput` boundary (`AnalysisInput.kt:37-43`) already provides that.
