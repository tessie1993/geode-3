# Product specification and feature inventory

Status: target specification, not a list of completed features. Source baseline:
`9848b28`; see `IMPLEMENTATION_STATUS.md` for this branch's actual changes.

## Spec sheet

| Field | Specification |
|---|---|
| Working name / package | Geode / existing `dev.geode`; preserve identity and upgrade data |
| Product | Local music player, interactive visual instrument, wallpaper and music-video studio |
| Primary users | Music listeners, generative-art explorers, musicians and video creators |
| Platform | Android phones, tablets and foldables; Android Auto browsing is a player surface, not a visualizer surface |
| Compatibility baseline | Existing minSdk 26, targetSdk 36, compileSdk 37; arm64-v8a and x86_64; GLES capability probes; 16 KB native support |
| Native implementation | C++20, NDK/CMake, GLES 3.x, Oboe, KISS FFT and isolated projectM library |
| Android implementation | Kotlin, Compose, Media3, Hilt, coroutines/StateFlow, Room/DataStore as migrations justify them |
| Audio sources | Local music; optional microphone; eligible Android playback capture; silent generative drive |
| Output | Live visualization, wallpaper, still image, offline MP4 H.264/AAC; HEVC/4K only when encoder capability and quality tests pass |
| Main navigation | Listen / Explore / Studio; Customize is attached to the current visual, Settings through profile/settings entry |
| Connectivity | Offline core; separate optional Google/Drive and billing surfaces; no mandatory account |
| Business model | Free foundation plus subscription/lifetime premium per historical plan; prices/limits require final product configuration |
| Product constraints | No false all-app capture promise, no medical claims, no unlicensed presets, no dead controls, no silent partial export |

## Priority definitions

**R0** = blocker before any public candidate. **R1** = required for the complete
product in the existing owner plan. **R2** = explicitly optional expansion, not a
hidden omission. "Present" below means code exists, not device-certified.

## Player and audio

| ID | Feature | Baseline / priority | Acceptance evidence |
|---|---|---|---|
| P01 | MediaStore + SAF import; albums/artists/folders/search | Present / R0 | Denied permission still permits document import; revoked URI yields actionable error; no main-thread scan |
| P02 | Queue, repeat/shuffle, favourites, playlists and smart playlists | Present / R0 | Stable media IDs survive edits, restore and shuffle; missing files never crash or loop forever |
| P03 | Background playback and system/Bluetooth controls | Present with ownership defects / R0 | Cold service launch applies saved preferences; notification/widget/Auto and UI agree |
| P04 | Audio focus, duck/pause and unplug handling | Incomplete across engines / R0 | Focus loss, calls, headphones and Bluetooth disconnect behave with no Activity alive |
| P05 | EQ, ReplayGain, speed/pitch, skip silence | Present / R0 | Only supported controls shown; effects survive UI teardown and rate changes; no clipping beyond defined limiter behavior |
| P06 | Sleep timer, A-B repeat, history and long-track resume | UI-owned portions need migration / R1 | Rules run under service lifetime; process restoration and seek boundaries tested |
| P07 | Timed lyrics, track details, playlist import/export | Partial / R1 | Malformed metadata fails safely; file-format round trip; no writing user audio tags in initial release |
| P08 | Native low-latency microphone via Oboe | Planned; current mic is AudioRecord / R1 | Measured callback-to-feature latency, negotiated sample rate, route/disconnect recovery and denied permission |
| P09 | Device playback capture | Present / R1 | Consent each session as required; token revocation stops service; ineligible source clearly reported |
| P10 | Explicit source arbitration | Rebuild / R0 | Exactly one selected PCM producer; switching resets epoch without mixing unrelated sources |
| P11 | Android Auto and widget | Present / R1 | Browsing/control on a cold process, missing art, process death and system-selected track |
| P12 | Radio/streaming | R2 | Network model, stream failures and privacy separately approved; never imply present today |

## Visuals and customization

| ID | Feature | Baseline / priority | Acceptance evidence |
|---|---|---|---|
| V01 | Curated Fluid, raymarched and MilkDrop catalog | Present, needs repair/curation / R0 | All advertised styles render on supported tiers; fallback never goes black |
| V02 | Six signature spatial looks | New / R1 | Distinct silhouettes/materials/camera behavior; each meets scene contract and device budget |
| V03 | Beat/band/structure response | Broken/inconsistent paths / R0 | Synthetic impulses and musical fixtures exercise advertised controls; silence settles correctly |
| V04 | Presentation-aligned motion | Needs repair / R0 | Video of audible pulse + image response establishes measured offset by output route |
| V05 | Touch, multitouch and optional gyro | Touch present, gyro scope to implement / R1 | Gesture cancellation cleans up; sensor opt-in and recenter; reduced-motion mode respected |
| V06 | Universal controls | Rebuild / R1 | Every displayed macro maps to visible behavior; unsupported advanced controls absent |
| V07 | Palette editor, randomize/locks, reset, undo | Partial / R1 | One reversible edit model; locks survive randomization; original preset remains unchanged |
| V08 | Modulation: bands, beat, LFO, envelope and mappings | Present but needs complete wiring / R1 | Same timestamp produces same value in preview/export; bounded output; no NaN propagation |
| V09 | Preset browser/search/favourites/import/export | Present / R1 | Cold-start imports cannot overwrite; batch import validates size, paths, version and dependencies |
| V10 | MilkDrop preset/texture packs and transitions | Present with lifecycle defects / R0 | Pack persists through screen switches, thermal resize and GL recreation; error names offending file |
| V11 | Live wallpaper | Present / R1 | Pauses when invisible, respects saver/thermal state, restores preset; no background microphone by surprise |
| V12 | Safe visual defaults / reduced motion | Partial / R0 | Persisted setting affects every family and final composite; safety is never a paid feature |
| V13 | Immersive / landscape / PiP | Partial / R1 | PiP continues rendering if offered; transitions preserve scene; controls have accessible alternatives |
| V14 | External display, MIDI, gamepad, NDI/Cast | R2 | Separate latency, network, permission and compatibility matrix before advertising |

## Studio and output

| ID | Feature | Baseline / priority | Acceptance evidence |
|---|---|---|---|
| E01 | Track-to-video and still image | Present / R0 | Valid media, correct length/orientation/audio and a visible destination |
| E02 | Multi-lane project model | Present / R1 | Stable clip IDs; gap, overlap, trim, move and delete semantics documented and persisted |
| E03 | Composed preview | Missing / R1 | Video, visuals, image/overlay/text and audio preview the same project evaluation as export |
| E04 | Full Visual/Overlay lane export | Missing/incomplete / R1 | No lane silently omitted; unsupported effect blocks export with named reason |
| E05 | Keyframes, transitions, speed ramps, Ken Burns, gain | Partial / R1 | Clip-local and project time map correctly after split/move/ripple; audio/video stay aligned |
| E06 | Captions, lyrics/SRT, LUT, loudness | Partial / R1 | Unicode/layout, timing and colour management verified in exported file |
| E07 | Resolution/aspect/codec presets | Present / R1 | Query encoder support; 9:16, 16:9 and 1:1 validated; honest fallback shown before start |
| E08 | Background export, progress, ETA, cancel | Partial / R0 | Service owns job, cancel removes partial output, full disk and process death recover without corrupt project |
| E09 | Deterministic rendering | Incomplete / R0 | Fixed seed and timestamps; repeat exports match visual checkpoints within GPU tolerance |
| E10 | Project/template backup, duplicate and version migration | Partial / R1 | Atomic write; malformed/newer versions preserved; imported project cannot escape its storage root |

## Accounts, premium and polish

| ID | Feature | Priority | Acceptance evidence |
|---|---|---|---|
| A01 | Optional Credential Manager Google sign-in | R1 | Cancel/no credential/offline flows; local use always available; no OAuth secret in app |
| A02 | Drive appData backup/restore | R1 | Explicit authorization, checksum/schema validation, conflict preview, cancellation and delete backup |
| A03 | Subscription + lifetime entitlement | R1 | Play test purchases: pending, acknowledged, restore, refund, expiry, grace and account changes |
| A04 | Fair premium gates | R1 | Exact limits visible before purchase/export; owned content restored; safety, basic playback and data access remain available |
| A05 | Help, support, privacy and notices | R0 | Reachable without sign-in; accurate to shipped behavior; source/licence notices agree with binary |
| A06 | Accessibility and adaptive layouts | R0 | TalkBack labels, 48 dp targets, large fonts, contrast, switch access and tablet/foldable checks |
| A07 | Baseline profile and startup polish | R1 | Release-build before/after measurement; no blocking media scan at launch |
| A08 | In-app review/update | R1 | Asked after successful value, never blocks use; correct update cancellation/failure handling |

## Measurable quality targets

These are proposed acceptance budgets, **not achieved measurements**. Confirm on
a named baseline device in phase 0, record thermal state and media fixtures.

| Metric | Proposed gate |
|---|---|
| First interactive frame | p95 <= 2 seconds cold start on baseline, excluding first permission prompt |
| Visual frame time | Sustained 60 fps target: p95 <= 16.7 ms on high tier; graceful 30 fps <= 33.3 ms on low tier |
| Audio reliability | No audible underruns in 30-minute screen-off/local playback and 20 route-change repetitions |
| Native callback | No heap allocation, lock, JNI, file/network I/O or stream reopen in callback |
| A/V synchronization | Measured <= one 30 fps frame for local visual reaction after calibration; report Bluetooth separately |
| Export timing | Duration within one output frame; no accumulating A/V drift over 10-minute 44.1/48/96/192 kHz fixtures |
| Memory | No monotonic growth after 100 scene switches or 20 start/stop capture/export cycles |
| Thermal | 20-minute sustained run: automatic quality reduction without audio interruption or context/preset loss |
| Safety | Reduced motion disables flash/shake behavior and constrains camera motion; no claim of medical safety |
| Reliability | No open reproducible crash, ANR, data loss or R0 defect before candidate promotion |

Test mono/stereo, 44.1/48/96/192 kHz, VBR, corrupt/truncated input, seek,
offload disabled/enabled where supported, speaker/wired/USB/Bluetooth and denied
permissions. Do not turn a quality target into store copy until evidence exists.
