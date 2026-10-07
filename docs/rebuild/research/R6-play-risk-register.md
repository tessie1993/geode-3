# R6: Google Play risk register for Geode (dev.geode)

Researched 7 October 2026. Read-only: nothing was built, tested or linted by this agent. Code facts are from
`origin/main` at `03db04e` (A2 merged) unless prefixed `PR9:` (`origin/codex/account-visual-foundation` at `09d646f`).
Line numbers refer to those commits. Policy facts were fetched from the URLs shown on 2026-10-07. Where a fetch returned
a summary only, or I could not check something, the row says **(unverified)**.

Facts I verified directly rather than copying from the blueprint:
- I downloaded the CI debug APK of main run #19 (artifact `Geode-debug-19`, commit 03db04e) and read its merged manifest with `aapt2`.
- I read `libgeode.so` with `strings` and `readelf`.
- I fetched the in-app privacy-policy URL (HTTP 404).
- I read the GitHub Actions run and job states, and the checked-out submodule licence files.

---

## A. Summary

**Verdict: not Play-ready today, but the platform plumbing is in better shape than the paperwork.**

Already right, verified:
- targetSdk 36 meets the 2026 requirement (`app/build.gradle.kts:48`).
- 16 KB page alignment is configured and checked in CI. `libgeode.so` has LOAD alignment 0x4000 and RELRO ends on a 16 KB boundary.
- Every FGS type has its matching `FOREGROUND_SERVICE_*` permission.
- No exact alarms, no `QUERY_ALL_PACKAGES`, no `INTERNET`, no cleartext, no photo/video permission.
- `enableEdgeToEdge()`, predictive back and `onTimeout()` handling are present.
- Imported links are size-bounded.
- Packaged fonts and ported shaders have notices.

What blocks a store release is mostly:
1. Honesty of what users and reviewers are told: the dead privacy URL, the false photosensitivity guarantee, and missing licence notices.
2. A release pipeline that has never produced a signed AAB.
3. Console paperwork with a 14-day clock.

**Blocker count: 13** (severity "blocker" in the register):
- **8 apply to a free, offline v1 built from main** (Scope A). Of these, 3 need engineering or content work (PC-01, SF-01, LG-01) and 5 are owner or Console gates (PC-03, PC-04, PC-05, RE-01, LS-01).
- **5 more apply only if accounts, backend or premium ship** (Scope B: PB-01 to PB-05).

Rough path: Scope A can reach closed testing in about 1 week of engineering if SF-01 is first fixed by changing the copy. Production is then gated by the 14-day closed test, if it applies. Scope B is a multi-week project that should be a later release.

**Recommendation: ship v1.0 free and offline, with no INTERNET.** This removes account deletion, Billing, backend, OAuth and the Data-safety rewrite from the critical path. PR #9's account code is already debug-only (`PR9: PremiumPreview.kt:28`) and unconfigured (`PR9: account/README.md:3-6`). It is still a draft with a red head CI (run 37557709201) and a stale base.

Legend:
- Severity: blocker, major or minor.
- Effort: S under 1 day, M 1-3 days, L 1-2 weeks, XL over 2 weeks, O = owner elapsed time.
- Owner: Claude = visuals + AAudio mic; Codex = playback, data, account, billing, export, Studio, release; Owner = Console, Cloud, signing, URLs, assets.

---

## B. Risk register

### B1. Policy, privacy and Console

| ID | Area | Risk | Sev | Evidence | Fix | Owner | Effort |
|---|---|---|---|---|---|---|---|
| PC-01 | Privacy URL | The in-app policy link, and so presumably the Console URL, points at another repo's Pages site, `tessie1993.github.io/music-visualizer-2/privacy-policy.html`. It returns HTTP 404 today. geode-3 has Pages off (`has_pages=false`). Play requires a working policy link for every app, even ones that collect nothing. | blocker | `AboutSettings.kt:33`; WebFetch 404; https://support.google.com/googleplay/android-developer/answer/10787469 ("All developers must add a privacy policy") | Publish `docs/privacy-policy.html` at a stable HTTPS URL (Pages from /docs of geode-3, or another host). Change `AboutSettings.kt:33` and the Console field to match. | Owner (host) + Codex (constant) | S |
| PC-02 | Privacy text | The policy is stale even for offline v1: <br>1. It lists JTransforms, which is not in the tree. <br>2. It omits `RECEIVE_BOOT_COMPLETED`, which WorkManager adds via Glance. <br>3. It says exports go to "the location you choose in Android's save dialog", but code also publishes to MediaStore `Movies/Geode` on API 29+. <br>4. It gives no developer name and only one personal e-mail as contact. <br>The policy must name the developer, contact, data types, retention and deletion. | major | `docs/privacy-policy.html:135-139,157-160,167`; `export/LoopExtend.kt:438-456`; merged manifest permissions (aapt2); https://support.google.com/googleplay/android-developer/answer/10144311 | Regenerate the permission table from the merged manifest. Describe both export paths. Add developer identity and a support address. | Owner + Codex | S |
| PC-03 | Console forms | All "App content" forms are open. <br>- Data safety (answers in Appendix F). <br>- Content rating (IARC). <br>- Target audience. <br>- Ads (none). <br>- Advertising ID (no `AD_ID`; the merged manifest has none). <br>- App access (no login, but a reviewer needs audio). <br>- Health, financial, news and government declarations (all No). <br>Data safety must match the real build. For main, "no data collected or shared" is correct because the merged manifest has no INTERNET and mic and capture data stay on device. | blocker (owner) | Merged manifest (aapt2); https://support.google.com/googleplay/android-developer/answer/10787469, .../9859655, .../9893335, .../6048248 | Complete the forms. Re-do Data safety whenever INTERNET or an SDK is added. | Owner | S |
| PC-04 | FGS declarations | The manifest uses 4 FGS types: `mediaPlayback`, `mediaProjection`, `mediaProcessing`, `dataSync`. The `dataSync` entry is the export fallback below API 35. Play Console App content > Foreground service needs, per type, a description, an interruption-impact statement and a demo-video link. I read "no hard deadline" in the policy text; the Console may still block rollout (unverified). | blocker (owner) | Manifest `:199,:244,:256`; ExportService `:110-115`; https://support.google.com/googleplay/android-developer/answer/13392821 | Record 4 short screen videos: <br>1. Playback notification. <br>2. Capture consent plus notification. <br>3. Export progress. <br>4. Pre-API-35 export fallback. | Owner (+ Codex to provide a build) | M |
| PC-05 | Closed-test gate | If the developer account is a personal one created after 13 Nov 2023, Production stays disabled until a closed test has at least 12 testers opted in continuously for 14 days and a production-access application is filed. Account type and date are unknown. | blocker (owner), if applicable | https://support.google.com/googleplay/android-developer/answer/14151465; `PLAY_RELEASE.md:110-113` | Start the closed test as soon as a signed AAB exists. This is the critical path. | Owner | O: 14+ days |
| PC-06 | Android Auto review | Auto metadata and a `MediaLibraryService` are declared, so Play runs the car-quality review. It is non-blocking in closed testing but blocking for open testing and production. Likely failures: <br>- VC-1: voice "play X" is not handled in the session. There is no `onSearch` or `searchQuery`; only an Activity `MEDIA_PLAY_FROM_SEARCH` that opens phone UI. <br>- DR-2/DR-3: the 10-second launch and load limits are not evidenced. <br>- MA-1/EP-2: resume and autoplay behaviour is untested on a head unit. | major | Manifest `:109-111,:141-144,:196-205`; `playback/PlaybackService.kt:67-145` (grep: no `searchQuery`, `onSearch` or `onConnect` in app/src/main/java); https://developer.android.com/training/cars/distribute; https://developer.android.com/docs/quality-guidelines/car-app-quality | Choose one: <br>(a) Drop the Auto meta-data for 1.0 and ship Auto in 1.1. <br>(b) Implement search and `searchQuery` handling, test on the Desktop Head Unit, add the Android Auto form factor in Console, and note it in review. | Codex (+ Owner for Console) | M |
| PC-07 | Mic / capture disclosure | The mic switch fires the `RECORD_AUDIO` request directly. The explainer is body text next to the switch, not a step before the OS dialog. "Visualize other apps" needs the same permission, requested together with `POST_NOTIFICATIONS`, then the OS screen-share consent dialog. The data never leaves the device, so strict prominent disclosure is arguably not triggered, but reviewers often ask and the OS wording ("record audio", "screen") is alarming. | major | `ui/AudioSettings.kt:134-143`; `ui/ExternalAudioSettings.kt:75-90`; `ui/PlayerScreen.kt:600-621`; `strings.xml:464,473`; https://support.google.com/googleplay/android-developer/answer/10144311 | Add a short pre-permission sheet before each prompt: what is heard, that it stays on the device, that it is never stored, how to stop. Reuse it in the FGS video and review notes. | Claude (mic UI) / Codex (capture UI) | S |
| PC-08 | Notification access | `GeodeNotificationListener` makes the user grant system Notification access so the app can call `getActiveSessions()`. It reads media sessions only, and the policy discloses it. I found no Play restricted-permission form for it (unverified). | minor | Manifest `:264-272`; `ui/ExternalAudioSettings.kt:132-140`; `privacy-policy.html:127-133` | Keep it optional. Mention it in the review notes. | Codex | S |
| PC-09 | Developer account | Identity verification, public developer contact and EEA trader status are open (the app would carry a premium offer later). The only contact in the repo is a personal Hotmail address. | major (owner) | https://support.google.com/googleplay/android-developer/answer/10840893 , .../10841920 (not read in depth) | Complete verification. Decide which public name and e-mail to expose. | Owner | O |
| PC-10 | Audience / rating | Child-directed would trigger the Families policy and its permission and SDK restrictions. The app has a microphone, screen capture and intense visuals, so declare 13+ or 18+. | minor (owner decision) | https://support.google.com/googleplay/android-developer/answer/9893335 | Declare an age band, not "ages 5 and under" or "all". Avoid child-appealing listing art. | Owner | S |

### B2. Product safety and claims

| ID | Area | Risk | Sev | Evidence | Fix | Owner | Effort |
|---|---|---|---|---|---|---|---|
| SF-01 | Photosensitivity claim | **The absolute guarantee shown to every user is false by the project's own safety doc.** The copy says: "Nothing on screen can flash more than three times a second ... no preset or imported file can raise that ... exported video held to the same limit". `SAFETY_MODEL.md` says the clamp is parameter-level only. projectM/MilkDrop, Shader Studio (user GLSL) and a scene's own brightness swings bypass it, and "the product promise ... is not yet true" until V2-0-02c. `VisualSafety.cpp` clamps parameters and rate-limits parameter edges. It never measures pixels, and there is no red-flash handling. That is a seizure-risk claim, and also a deceptive one for review. | blocker | `strings.xml:22-24,103,609`; `docs/visualizer-v2/SAFETY_MODEL.md:61-80`; `core/viz/VisualSafety.cpp:25-90`; `core/viz/RendererFrame.cpp:244` (only `flash` goes through the budget) | Do these in order: <br>1. **Now (S):** rewrite the three strings to what is true: built-in styles are rate- and depth-limited; imported MilkDrop presets and Shader Studio can flash. Show a one-time warning on the first `.milk` or GLSL import. Add "contains flashing lights" to the listing. <br>2. **Before production (L):** build the measured final-frame limiter (V2-0-02c: downsampled luminance, async readback, also in export). <br>3. Keep `SafetyConsent` (`ui/SafetyConsent.kt`) as the first-run gate. | Claude (visuals) | S then L |
| SF-02 | Hostile or huge imports | On main, a picked `.milk` file is copied unbounded and unvalidated into private storage, and projectM then executes its equations in native code. A malformed or huge preset can fill storage or crash the native renderer. PR #9 adds admission (`MilkAssetAdmission`). Shared `geode://` links are bounded (`PresetLink.kt:13-17`) but import silently on main thread at launch. | major | `ui/MilkImportController.kt:71-73`; `ui/MainActivity.kt:80-101,220-222`; `PR9: data/MilkAssetAdmission.kt` | Land PR #9's admission layer. Add a size cap. Ask for confirmation before importing a link-delivered preset. Run native parse under a time or size guard. | Claude (visuals/MilkDrop) / Codex (data) | M |
| LS-02 | Misleading claims | Copy must not promise things not proven. Candidates: <br>- "4K" (`strings.xml:530` is hedged, but the long side is hard-capped at 4096 without querying the encoder, `export/VideoExporter.kt:78-87`; see also APP_BUG_CONFIG_AUDIT A13 on PR #9). <br>- "Visualize other apps": Spotify and others block capture, and the app shows digital silence (`strings.xml:482`). <br>- "Studio ... Visual/Overlay lanes": IMPLEMENTATION_STATUS says they are not yet exported. <br>- "No account / no network" (`strings.xml:29,121,232,382`; `README.md:11`) is true on main and false once PR #9's work ships. <br>- No "bit-perfect", "lossless" or "gapless" strings remain on main (grep clean). | major | Strings above; `docs/blueprint/IMPLEMENTATION_STATUS.md:20-33`; PLAY_RELEASE listing draft `:141-143`; metadata policy https://support.google.com/googleplay/android-developer/answer/9898842 | Gate every listing sentence on the release matrix, as PLAY_RELEASE already says. Add a CI grep that fails if INTERNET is merged while the "no network" strings exist. | Codex (Studio) / Owner (listing) | S |

### B3. Licences

| ID | Area | Risk | Sev | Evidence | Fix | Owner | Effort |
|---|---|---|---|---|---|---|---|
| LG-01 | TagLib and KISS FFT | **TagLib 2.3.1 (LGPL-2.1 / MPL-1.1) is statically linked into `libgeode.so`** (`add_subdirectory(third_party/taglib)` with shared libs off; I counted 179 TagLib symbol strings in the CI APK's `libgeode.so`). **KISS FFT (BSD-3-Clause) is static too.** Neither appears in either notices file (grep count 0 in both `THIRD_PARTY_NOTICES` and the packaged asset). The repo has no top-level LICENSE, so nothing lets a user modify and relink the combined work, which LGPL-2.1 section 6 expects. | blocker | `CMakeLists.txt:65-79`; `libgeode.so` NEEDED list (readelf): no `libtag.so`; `third_party/taglib/COPYING.LGPL, COPYING.MPL`; `third_party/kissfft/COPYING`; `app/src/main/assets/third_party_notices.txt` | Add both notices, with the licence text. For TagLib either take the MPL-1.1 option and add the MPL text and source pointer, or build it as a separate `libtag.so`. Add TagLib and KISS to `provenance.json`. Pick and add a licence for Geode's own code (LG-04). | Codex (release/legal) | M |
| LG-02 | projectM | The notice says Geode "does not modify" projectM, but the build applies `tools/projectm-v4.1.7-render-fbo-backport.patch` to the submodule at configure time. LGPL needs a prominent modification notice and the modified source (the public repo plus the patch satisfy this if the notice says so). It also labels `projectm-eval` as LGPL; the checked-out file is MIT (`vendor/projectm-eval/LICENSE.md`, 2023 The projectM Team). | major | `THIRD_PARTY_NOTICES:90-117`; `CMakeLists.txt:24-57`; `third_party/projectm/vendor/projectm-eval/LICENSE.md` | Reword: modified by backport patch, source and patch at the repo URL and tag. Move `projectm-eval` to the MIT block. Keep `libprojectM-4.so` shared (it is, per NEEDED). | Codex | S |
| LG-03 | Notices drift | The packaged in-app asset has 968 lines. The root file has 1015 and extra sections for AndroidX Graphics Path and woscope (MIT) that the app asset lacks. Users only see the asset (`AboutSettings.kt:79`). `checkEngineProvenance` reads only the root file (`geode.provenance.gradle.kts:2`), so drift is undetected. | major | `diff` of the two files; `AboutSettings.kt:79`; `build-logic/.../geode.provenance.gradle.kts:2` | Generate the asset from the root file in the build, or add a CI diff gate. | Codex | S |
| LG-04 | Own licence | The public repo has no LICENSE for Geode's own code (default: all rights reserved), while it links LGPL and MPL code (see LG-01). | minor | `git ls-tree origin/main` root listing | Owner chooses a licence, or the dynamic-link approach in LG-01 makes it unnecessary for LGPL compliance. | Owner | S |
| LG-05 | Theme-pack assets | 43 MB of resources: 10 crystal theme packs (`tp_*` WebP tiles, 30 WAV UI sounds) imported from external "MusicViz-<Stone>-Theme-Pack" folders. No notice or provenance entry names their author or licence. Fonts Mali and Mystery Quest are OFL and are covered. | major (owner confirmation) | `tools/import-theme-pack.sh:1-25`; `app/src/main/res/raw/tp_*.wav`; `THIRD_PARTY_NOTICES` (no `tp_` entry) | Owner confirms the packs are original or licensed; record that in the notices. If not, replace them before release. | Owner | S |
| LG-06 | Bundled presets | 6 bundled `.milk` presets have no author header. `geode_bloom` and similar names suggest originals (unverified). | minor | `app/src/main/assets/milk/*.milk:1-6` | Owner confirms authorship. | Owner | S |

### B4. Platform and technical

| ID | Area | Risk | Sev | Evidence | Fix | Owner | Effort |
|---|---|---|---|---|---|---|---|
| TC-01 | Target API | **OK.** targetSdk 36 satisfies the rule: new apps and updates must target API 36 from 31 Aug 2026 (extension to 1 Nov 2026). The next bump will be needed in 2027 (date not published at fetch time). Lint flags `OldTargetApi` because compileSdk is 37. | minor | `app/build.gradle.kts:41,48`; https://developer.android.com/google/play/requirements/target-sdk | Plan the 37 bump after 1.0. | Codex | S |
| TC-02 | 16 KB | Configured: linker flags, NDK r30, AGP 9.4.1, uncompressed libs, rebuilt graphics-path, `checkNativePageAlignment` on every release build. In the CI APK, `libgeode.so` LOAD align is 0x4000 and RELRO ends 16 KB-aligned. Play deadline per the fetched page: updates without 16 KB support cannot be released from 1 Feb 2027. Not yet shown: AAB checked with bundletool, and a run on a 16 KB device or emulator. | minor | `CMakeLists.txt:8-22`; `app/build.gradle.kts:43,113-117,153-170`; `tools/release/check_native_alignment.py`; https://developer.android.com/guide/practices/page-sizes | Run `bundletool` config check and a 16 KB emulator smoke test before the first upload. | Codex | S |
| TC-03 | FGS runtime | `ExportService.start()` swallows any start failure (`runCatching`), so a render can run with no foreground service and die in the background. There is no wake lock during renders, so a 4K render with the screen off can stall (EX-08 in AUDIT_2026-10; no `WakeLock` in `export/`). `onTimeout()` correctly cancels at the Android 15 6-hour limit. | major | `export/ExportService.kt:49-55,110-115,122-131`; grep of `app/src/main/java` | Surface start failure to the UI. Hold a partial wake lock (with timeout) while rendering. | Codex (export) | S |
| TC-04 | Projection runtime | Order of calls is correct (FGS first, then `getMediaProjection`, callback registered before use; the single-use token is guarded by `runCatching`). Per the platform docs, Android 15 QPR1+ stops projection when the device locks and shows a status-bar chip, so "Visualize other apps" ends on lock. The consent text is the generic screen-share dialog. | minor | `audio/PlaybackCaptureService.kt:45-65,123-125`; https://developer.android.com/media/grow/media-projection | Explain "stops when the phone locks" in the sheet from PC-07. | Codex | S |
| TC-05 | Mic in background | The mic path has no microphone-type FGS, so since Android 11 a backgrounded app gets silence. The AAudio replacement must keep this model, or else add a `microphone` FGS (new permission plus a fifth Console declaration). | minor | Manifest `:17,:30-52` (no `FOREGROUND_SERVICE_MICROPHONE`); `audio/MicCapture.kt:35-71`; `PLAY_RELEASE.md:47` | Keep mic foreground-only and say so in UI. AAudio needs no new permission (minSdk 26 equals the AAudio floor). | Claude (mic) | S |
| TC-06 | Exported surfaces | `PlaybackService` is exported with no `onConnect` gate, so any app can browse the library tree and control playback. `onSetMediaItems` accepts any item that already carries a URI (`resolve()` returns it untouched). Lint reports `ExportedService`. `geode://` is BROWSABLE with no confirmation. No component re-launches an intent taken from extras, so I found no intent-redirection path. `intentMatchingFlags` (Android 16, opt-in) is not adopted. | minor | `playback/PlaybackService.kt:113-147`; manifest `:128-144,196-205`; lint results (run 19) | Add a controller allow-list in `onConnect` (system UI, Auto, Assistant, own app). Reject foreign URIs. Consider `enforceIntentFilter`. | Codex (playback) | S |
| TC-07 | Large screens | Android 16 ignores orientation and resizability locks at 600 dp and above. The app sets none (clean) and uses the window size class once (`AppShell.kt:245`). `MainActivity` handles `configChanges` itself, so GL surface and Compose resize behaviour on fold, split-screen and tablet is unverified. | major (unverified) | Manifest `:114-117`; `AppShell.kt:245`; https://developer.android.com/about/versions/16/behavior-changes-16 | Test fold/unfold, split-screen, rotation on a tablet and a foldable. Provide tablet screenshots (assets row LS-01). | Codex (shell) / Claude (Visualizer surface) | M |
| TC-08 | PiP | `ON_PAUSE` pauses the GL view, and PiP is a paused state, so the PiP window shows a frozen frame (GL-02 in AUDIT_2026-10, still present). | minor | `ui/AppShell.kt:101` | Pause on `ON_STOP`, or skip while in PiP. | Claude (Visualizer) | S |
| TC-09 | Backup | `allowBackup=true` with exclusions for the analysis cache, crash file, textures and takes. Restored data holds device-specific MediaStore IDs and dead SAF grants (DA-10 in AUDIT_2026-10). Nothing secret is stored today. Scope B will add tokens and keys, which must be excluded in both rule files. | minor (major for B) | Manifest `:99,:103`; `res/xml/backup_rules.xml`, `data_extraction_rules.xml` | Add explicit excludes for any credential store and `PremiumEntitlement` data. | Codex | S |
| TC-10 | ABIs and reach | Only `arm64-v8a` and `x86_64` are built. Devices running a 32-bit userspace on arm64 hardware cannot install. GLES 3.0 is a hard `uses-feature`. | minor | `app/build.gradle.kts:52`; manifest `:4-6` | Accept, and state it in the listing's device list check. | Owner | S |
| TC-11 | Studio clips by OS | `StudioClips.list` queries `MediaStore.Video` without `READ_MEDIA_VIDEO`. On API 33 and up it likely shows only the app's own exports, while API 32 and below shows all videos (inferred, unverified). Adding the video permission later would trigger the Play photo/video declaration. | minor | `export/StudioClips.kt:26-36`; https://support.google.com/googleplay/android-developer/answer/14115180 | Document behaviour. Use the photo picker for imports, never the media permission. | Codex (Studio) | S |

### B5. Release engineering and quality

| ID | Area | Risk | Sev | Evidence | Fix | Owner | Effort |
|---|---|---|---|---|---|---|---|
| RE-01 | Signing and first AAB | **No signed AAB has ever been built.** The `play-bundle` job was skipped in every run, including main #19. Whether the four `MUSICVIZ_*` secrets exist is unknown (the API returned 403 through the proxy). There is no Play App Signing enrolment on record. The package may already exist in the Console, possibly from the predecessor repo (the `MUSICVIZ_` secret names suggest one), and the Console would then demand the existing upload key. Previous public builds were debug-signed prereleases `apk-v1.8.0-*`. | blocker (owner) | `.github/workflows/android.yml:159-225`; run 37602138475 job "Build signed Play bundle" = skipped; releases list; `PLAY_RELEASE.md:8-34` | Owner: check Console for `dev.geode`, create or recover the upload key, store the four secrets, enable Play App Signing. Then run `workflow_dispatch release_bundle=true`. Check `jarsigner -verify`, the upload fingerprint and the checksum. | Owner (+ Codex) | M |
| RE-02 | R8 untested | PR CI builds only debug (`:app:assembleDebug`) and `lintDebug`. R8 and resource shrinking first run at tag time. Keep rules look adequate: JNI natives, `SceneParams` fields (`proguard-rules.pro:60`), and no `FindClass` or `GetMethodID` exists in the C++ (grep clean). But `-dontwarn androidx.media3.**` (`:30`) can hide missing classes, and nothing proves the release APK starts. | major | `android.yml:38,90`; `proguard-rules.pro:14-60`; `app/build.gradle.kts:80-81` | Add an unsigned `assembleRelease` plus a smoke run of the minified APK on the emulator to PR CI. Run `lintVitalRelease`. | Codex | M |
| RE-03 | Versioning | `versionCode = 32` and `versionName = "1.8.0"` are hard-coded. Every tag build is 32, and Play rejects a duplicate. The blueprint says to pick the next unused code from the Console. | minor | `app/build.gradle.kts:49-50`; `PLAY_RELEASE.md:8-10` | Set from the tag or run number, above the Console maximum. | Codex | S |
| RE-04 | CI state | Main #19 (03db04e) is green on all 4 jobs; `play-bundle` skipped. Earlier main runs #11 and #14 were red. The head of PR #9 (run #16, 09d646f) has red debug-build and quality jobs (cause not read). PR #9 changes 180 files from an older base and also changes `PlaybackEngine.kt`, `PlayerSession.kt` and `core/CMakeLists.txt`, which A2 changed too. A 3-way merge-tree shows real text conflicts only in `README.md`, `docs/blueprint/DELIVERY_PLAN.md` and `docs/blueprint/README.md`. | major | Actions runs 37602138475, 37557709201; `git merge-tree` (read-only) | Rebase PR #9 on main, get a green run, then merge the visuals before any account work. | Codex (PR #9) | M |
| RE-05 | Test depth | Main has 3 unit-test classes and 3 instrumentation files in total, despite CHANGELOG and docs claims (the old audit says 3 files, and I counted 6 across `src/test` and `src/androidTest`). The only emulator is API 30 `google_apis` x86_64, so Android 14/15/16 behaviours (FGS types, edge-to-edge, predictive back, 16 KB) are not exercised. | major | `git ls-tree` of test dirs; `android.yml:141-149`; quality-reports-19 artifact | Add an API 35/36 emulator leg and a 16 KB image. Use PR #9's new tests. | Codex | M |
| RE-06 | Startup | No baseline profile or macrobenchmark exists. The splash waits for user data (`MainActivity.kt:213`). The debug APK has 21 dex files, so the R8 effect on startup is unmeasured. | minor | `git ls-tree` grep; `MainActivity.kt:213-223` | Add a baseline profile after the R8 build is stable. Measure cold start on a mid-range phone. | Codex | M |
| RE-07 | Symbols and mapping | `debugSymbolLevel = FULL` and the job uploads `mapping.txt` and `native-debug-symbols.zip` as an artifact for 14 days. Nothing uploads them to Play. | minor | `app/build.gradle.kts:82-84`; `android.yml:208-221` | Upload the symbols and mapping to the Console for each release, or retain them longer than 14 days. | Owner | S |
| QL-01 | Battery | The wallpaper renders at the app's default target FPS (60) whenever visible, and a 16 ms feeder thread wakes at about 60 Hz. The code's own comment admits the 24-30 fps quality target is unmet. The dream is the same. There is no power-save or platform thermal hook (grep: none), only a frame-time-trend governor in native code. Visibility gating is correct. | major | `wallpaper/VisualizerWallpaperService.kt:111-131,265-282`; `wallpaper/VisualizerDreamService.kt:99-119`; `core/viz/ThermalGovernor.cpp` | Cap to 30 fps after fixing the fluid-quality monitor, as the comment says. Pause in battery saver. Drop the 16 ms thread for a frame-callback. | Claude (visuals) | M |
| QL-02 | Memory | The native renderer never evicts built scenes (`emplace_back`, cleared only on teardown), so GPU and native memory grow with each style visited (about 90 ids). There is no `onTrimMemory` handling (grep: none). Risk is low-RAM devices and Android vitals kills. | major | `core/viz/Renderer.cpp:412-426,463`; grep for `onTrimMemory` | LRU-evict scenes. React to trim-memory callbacks. | Claude (visuals) | M |
| QL-03 | Accessibility | Custom "Crystal"/"Stone" controls everywhere. Signals are thin: 23 `contentDescription` mentions, 48 semantics mentions and 15 explicit `Icon(` calls against 107 click and gesture sites and 48 `IconButton`s. Touch-target helper use is 4 sites. Font scale flows through a custom `textScale` (`StoneTheme.kt:39`). TalkBack for the GL canvas is unaddressed. Pre-launch accessibility checks (labels, 48 dp targets, contrast) will likely flag items. Unverified without a device. | major (unverified) | `ui/Crystal*.kt`, `ui/theme/StoneTheme.kt:39`; greps in section 8 notes | Run Accessibility Scanner and a TalkBack pass. Give the visualizer canvas a described role. Test at 200% font scale. | Codex (shell) / Claude (Visualizer, Customize) | M |
| QL-04 | Review path | There is no bundled demo audio (grep: none), so a reviewer or Robo test sees an empty library. The first screens are the photosensitivity gate and a storage permission prompt. | major | `ui/SafetyConsent.kt`; `ui/FirstRun.kt:41-93`; no media in `res/raw` except `tp_*` UI sounds; `PLAY_RELEASE.md:151-152` | Ship a small, original, licensed demo track or a built-in synthetic input. Write reviewer notes (steps, how to trigger capture). | Codex + Owner | M |
| QL-05 | ANR / crash candidates | See Appendix E. No `runBlocking`, `registerReceiver` or alarms in app code (grep). Only one `!!` in the tree. The strongest candidates are main-thread import work, a synchronous `commit()`, wallpaper start-up and teardown, and native memory growth. | minor | Appendix E | Fix the top three. | Codex / Claude | M |

### B6. Scope B only: accounts, backend, billing (PR #9 and the blueprint)

| ID | Area | Risk | Sev | Evidence | Fix | Owner | Effort |
|---|---|---|---|---|---|---|---|
| PB-01 | Network claims | Adding INTERNET breaks the privacy policy (`:29-31,40,117,148`), four in-app strings, the README, the Data-safety answers and the manifest comments. Cleartext is not blocked explicitly (no `networkSecurityConfig`; the default blocks it for target 28 and up, but state it). | blocker (B) | Policy and strings as in LS-02; manifest has neither attribute | Change all together: policy, strings, Data safety, `usesCleartextTraffic="false"` plus a network security config, CI grep gate. | Codex + Owner | M |
| PB-02 | Account deletion | A Google sign-in that creates a backend profile needs an in-app deletion path and a working web link, with retention explained. Sign-out is not enough. Nothing exists. | blocker (B) | `PR9: account/AccountState.kt` (state only, `signedOut()` clears nothing); https://support.google.com/googleplay/android-developer/answer/13327111 | Build deletion (blueprint section 7 API). Publish a deletion page. List both in the Console. | Codex + Owner | L |
| PB-03 | Billing client | No Play Billing dependency in the version catalog, on main or in PR #9. `PremiumUnlockScreen` is unwired and the catalog state is `Unconfigured`. Minimum library version: the FAQ table lists v7 from 31 Aug 2026 and v8 from 31 Aug 2027, while its prose says v8; the blueprint says 9.1.0 is current. Re-check at upload. | blocker (B) | `gradle/libs.versions.toml` (unchanged in PR #9); `PR9: account/README.md:19-22`; https://developer.android.com/google/play/billing/deprecation-faq | Add the latest stable Billing library and an adapter behind `BillingRepository`. Payments policy forces Play Billing for these digital features. | Codex | L |
| PB-04 | Verification backend | The backend is Python domain code and fixtures only: no HTTP API, no Google calls, no DB, no worker, no RTDN, no lease signer. Unacknowledged purchases are refunded automatically after 3 days. | blocker (B) | `PR9: server/README.md:3-9,38-77`; https://developer.android.com/google/play/billing/security | Build and deploy it per `SECURITY_AUTH_PREMIUM.md` sections 2, 5 and 8. | Codex + Owner (Cloud) | XL |
| PB-05 | OAuth / Drive / products | None of the owner-controlled pieces exist: OAuth clients with the production Play-signing SHA-1, Drive appDataFolder consent, Play products `premium_monthly/yearly/lifetime`, licence testers, hosting project, domain. | blocker (B, owner) | `PLAY_RELEASE.md:83-96`; `SECURITY_AUTH_PREMIUM.md:53-61` | Create them after RE-01 (needs the final signing certificates). | Owner | L |
| PB-06 | Subscription terms | Price, period and trial must come from live product details. Cancellation must be easy to find (link to the Play subscription centre), and Restore must be visible. The unwired screen has the right strings but no data. | major (B) | `PR9: account_strings.xml`; https://support.google.com/googleplay/android-developer/answer/12154973, .../9858738 | Wire real product details. Never display fabricated prices. | Codex | M |
| PB-07 | Premium gates | The free/premium boundary (resolution, duration, watermark, style inventory) is undecided, and no gate exists in main (grep: no "premium" in app code). Do not charge for capabilities the binary cannot run (Visual/Overlay export lanes, 4K negotiation). | major (B) | `FEATURE_SPEC.md:21,88`; `SECURITY_AUTH_PREMIUM.md:206-212` | Product decision first, then entitlement checks at job entry. | Owner + Codex | M |
| PB-08 | Secret storage | Tokens, keys and leases must not enter Auto Backup. `backup_rules.xml` and `data_extraction_rules.xml` exclude only five paths. | major (B) | `PR9: account/README.md:71-75`; backup XML | Keystore-backed store plus explicit excludes in both XML files. | Codex | S |

### B7. Store listing

| ID | Area | Risk | Sev | Evidence | Fix | Owner | Effort |
|---|---|---|---|---|---|---|---|
| LS-01 | Store assets | None exist in the repo: no 512x512 icon PNG (only adaptive vector XML), no 1024x500 feature graphic, no screenshots, no tablet images, no final description. | blocker (owner) | `git ls-tree` search; https://support.google.com/googleplay/android-developer/answer/9866151 | Produce them from the release candidate with licensed demo audio (Appendix G). | Owner (+ Claude for visuals captures) | M |
| LS-03 | Content rating | Questionnaire answers are straightforward (Appendix G). Photosensitivity is not asked by IARC, so the in-app notice plus the listing line carry it. | minor | https://support.google.com/googleplay/android-developer/answer/9859655 | Submit before the first production rollout. | Owner | S |

---

## C. Owner-only actions checklist

Console and account:
- [ ] Check whether `dev.geode` already exists in the Play Console; find or create the app record (RE-01).
- [ ] Verify the developer identity; set the public developer name, contact e-mail and phone; decide on EEA trader status (PC-09).
- [ ] Confirm the account type and creation date to know whether the 12-testers/14-days closed test applies (PC-05).
- [ ] Enrol in Play App Signing; create or recover the upload key. Keep the `.jks` and passwords out of chat and Git (RE-01).
- [ ] Add the four repository secrets: `MUSICVIZ_KEYSTORE_BASE64`, `MUSICVIZ_KEYSTORE_PASSWORD`, `MUSICVIZ_KEY_ALIAS`, `MUSICVIZ_KEY_PASSWORD`.
- [ ] Host the privacy policy at a stable HTTPS URL and tell Codex the URL (PC-01).
- [ ] Choose the support e-mail and name shown publicly.
- [ ] Choose a licence for Geode's own code (LG-04).

App content (each must be filled before production):
- [ ] Data safety (Appendix F).
- [ ] Content rating (Appendix G).
- [ ] Target audience (13+ or 18+).
- [ ] Ads: none.
- [ ] Advertising ID: not used.
- [ ] App access instructions and sample audio for reviewers.
- [ ] Health, financial, news and government declarations: No.
- [ ] Four foreground-service declarations with videos (PC-04).

Listing:
- [ ] Icon 512x512 PNG, feature graphic 1024x500, at least 2 phone screenshots (up to 8 each), tablet screenshots (LS-01).
- [ ] Short description (65 chars in the draft; limit 80) and full description reviewed against SF-01 and LS-02.
- [ ] Category: Music and Audio.
- [ ] Decide Android Auto: drop for 1.0, or add the form factor and release an Auto-capable build to a testing track (PC-06).

Assets and rights:
- [ ] Confirm ownership or licence of the crystal theme packs (art and sounds) (LG-05).
- [ ] Confirm authorship of the six bundled `.milk` presets (LG-06).
- [ ] Supply licensed demo audio for review and screenshots (QL-04).

Testing:
- [ ] Recruit 12 or more closed testers; run the Play pre-launch report.
- [ ] Test on at least one 16 KB device or emulator, one foldable or tablet, and one low-RAM phone.

Scope B only:
- [ ] Choose the hosting project, region, budget and domain.
- [ ] Create OAuth clients using the production Play-signing SHA-1.
- [ ] Create the Play products and licence testers.
- [ ] Publish the account-deletion URL.
- [ ] Rewrite the policy and Data safety for the network build.

---

## D. Recommended release order

**Before closed testing (target: about 1 week of engineering plus owner setup):**
1. Fix scope first: v1.0 is free and offline. Keep INTERNET out. PR #9's account and server code stays debug-only; merge only its visual and reliability work, after a rebase and a green run (RE-04).
2. Legal and honesty fixes, all small: <br>- Privacy URL hosted and linked (PC-01, PC-02). <br>- TagLib and KISS notices (LG-01). <br>- projectM wording (LG-02). <br>- Notices asset sync (LG-03). <br>- Rewrite the three safety strings (SF-01 step 1).
3. Pipeline: <br>- Add unsigned `assembleRelease` plus an emulator smoke run to CI (RE-02). <br>- Owner secrets and upload key (RE-01). <br>- First signed AAB. <br>- Bundletool and 16 KB check (TC-02). <br>- Internal-track install. <br>- Set versionCode from CI (RE-03).
4. Console: FGS videos (PC-04), forms (PC-03), a first listing draft and assets (LS-01), Auto decision (PC-06).
5. Cheap hardening while waiting: <br>- ExportService start failure and wake lock (TC-03). <br>- Mic and capture pre-permission sheet (PC-07). <br>- Playback controller allow-list (TC-06). <br>- A demo track (QL-04).
6. Start the closed test with 12 or more testers (PC-05). The 14-day clock is the critical path.

**During the closed test:**
- Read the pre-launch report and Android vitals.
- Fix crashes and ANRs (Appendix E).
- Do the accessibility pass (QL-03).
- Test foldables and tablets (TC-07).
- Reduce wallpaper battery use (QL-01).
- Add scene eviction and trim-memory handling (QL-02).

**Before production:**
- Land the measured flash limiter (SF-01 step 2), or keep the narrowed copy and ship with MilkDrop and Shader Studio clearly warned.
- Resolve Android Auto (PC-06): either removed, or reviewed and passing.
- Submit the content rating (LS-03) and apply for production access.
- Start with a staged rollout of 5-10%, watch crash, ANR and battery metrics, and halt on a regression. Fix forward with a higher versionCode.

**Release 1.1 and later (Scope B), in this order:**
1. Privacy, Data safety and strings rewrite, together with INTERNET and the network security config (PB-01).
2. Identity plus in-app and web account deletion (PB-02).
3. Backend, Billing adapter, RTDN and reconciliation (PB-03, PB-04). Billing must be acknowledged within 3 days or it is refunded.
4. Play products, OAuth and Drive configuration (PB-05).
5. Subscription disclosure, Restore, cancellation link and entitlement gates (PB-06, PB-07, PB-08).
6. A new closed test and a staged rollout.

---

## Appendix E. Crash and ANR candidates (top 10, with file:line)

None of these is a confirmed field crash; they are ranked by likelihood times impact. Static review only.

1. `ui/MainActivity.kt:220-222` (and `:204-210`): link import runs on the main thread during `onCreate`: base64, gunzip up to 4 MB (`PresetLink.kt:15`), JSON parse and a file write. Jank or ANR on slow storage.
2. `core/viz/Renderer.cpp:424`: unbounded scene cache. GPU and native memory grow per style visited, with no `onTrimMemory`. Low-RAM kill risk.
3. `export/ExportService.kt:122-131`: start failure swallowed. Long renders can run unprotected and be killed. No wake lock (TC-03).
4. `ui/MilkImportController.kt:71-73`: unbounded copy of a picked `.milk`. Storage exhaustion, then native parse of hostile equations (SF-02).
5. `ui/VizStateStore.kt:77` (called from `PlayerSession.kt:1139`): synchronous `SharedPreferences.commit()`, flagged by lint (`ApplySharedPref`). ANR on slow flash if the caller is on the main thread.
6. `wallpaper/VisualizerWallpaperService.kt:82-109`: engine `onCreate` parses JSON, checks files and loads a MilkDrop preset on the main thread. Teardown blocks up to about 400 ms (`:138`, `:253`).
7. `wallpaper/VisualizerWallpaperService.kt:118-130`: a 16 ms polling thread for the lifetime of visibility (battery and wakeups, QL-01).
8. `playback/PlaybackService.kt:124-144`: external controllers can queue arbitrary URIs. Handled by `PlaybackErrors`, but untrusted input reaches the player (TC-06).
9. `ui/AppShell.kt:101`: PiP frozen frame (TC-08); not a crash, but a visible bug in a declared feature.
10. `audio/PlaybackCaptureService.kt:45-65`: projection start. Guarded by `runCatching` and the callback `onStop`, so low risk; it matters for Android 14+ one-time-token rules and Android 15 QPR1 lock-stop behaviour (TC-04).

Checked and clean: no `runBlocking`, `Thread.sleep` on the main thread, `registerReceiver`, `AlarmManager`, or `GlobalScope` in app code. The previous `Integer.MAX_VALUE` overflow in `onGetChildren` is fixed (`PlaybackService.kt:90-95`).

## Appendix F. Data safety draft

**Main today (no INTERNET, verified in merged manifest):**
- Data collected: none.
- Data shared: none.
- Reason: microphone, playback capture, library and media-session data are processed on device only. Play's definition: "Collect means transmitting data from your app off a user's device".
- Security practices: nothing to declare for transit encryption, because there is no transmission.
- A privacy policy URL is still required.

**Scope B (accounts, backend, billing, Drive backup), expected declarations:**

| Data type | Collected | Shared | Purpose | Notes |
|---|---|---|---|---|
| User IDs (Google subject, installation ID) | yes | no | Account management, app functionality | Optional, tied to sign-in |
| Name (display), e-mail (if stored) | if kept | no | Account management | Store the minimum |
| Purchase history | yes | no | App functionality, fraud prevention | Token and product ID on the backend |
| Device or other IDs (installation key thumbprint, Play Integrity evidence) | yes | no | Security, fraud prevention | Short retention |
| Files and docs (Drive appData backup) | optional | no | App functionality | Goes direct to the user's own Drive (unverified how Play counts it) |
| Audio | no | no | n/a | On-device only |
| Crash or diagnostics | no | no | n/a | Crash file stays local; adding any SDK changes this |

Deletion: user-request path plus web page. Encryption in transit: yes. Service providers (Google Play Billing, Cloud hosting) do not count as sharing (Data safety definitions). Reference: https://support.google.com/googleplay/android-developer/answer/10787469.

## Appendix G. Listing, content rating and assets

- Title "Geode: Music & Visuals" is 22 chars (limit 30). Short description draft is 65 chars (limit 80). The metadata policy bans rankings, price text and keyword stuffing (https://support.google.com/googleplay/android-developer/answer/9898842).
- Category: Music and Audio.
- Required assets (https://support.google.com/googleplay/android-developer/answer/9866151): <br>- 512x512 PNG icon, 32-bit alpha, up to 1024 KB. <br>- 1024x500 feature graphic, JPEG or 24-bit PNG, no alpha. <br>- 2 to 8 phone screenshots, 320-3840 px per side. <br>- Tablet screenshots (page says at least 4 for large-screen listing, 1080-7680 px, 16:9 or 9:16). <br>- No Android Auto screenshots required.
- The repo contains none of these (LS-01).
- Content rating answers (IARC): <br>- Violence, sexuality, language, controlled substances, gambling: No. <br>- User-generated content or user interaction: No (presets are shared through the OS share sheet; Geode hosts nothing). <br>- Shares location: No. <br>- Unrestricted internet: No on main. <br>- Digital purchases: No on main; Yes in Scope B. <br>- Expected result: lowest age band, but the questionnaire decides.
- Listing copy to add: "Contains flashing lights and intense visuals" and "Microphone and device-audio modes are optional; some apps block capture." The PLAY_RELEASE.md draft already omits unsupported claims; keep that rule.

## Appendix H. Manifest inventory (merged manifest of main, verified with aapt2 on CI APK)

Permissions declared in source (`AndroidManifest.xml`) and justification:

| Permission | Line | Justification and code | Play action |
|---|---|---|---|
| `READ_MEDIA_AUDIO`; `READ_EXTERNAL_STORAGE` (max SDK 32) | 8-11 | Library scan (`FirstRun.kt:41-93`, `LibraryScreen.kt:80`) | None. The photo/video permissions policy covers images and video only (https://support.google.com/googleplay/android-developer/answer/14115180) |
| `RECORD_AUDIO` | 17 | Live input and Android's playback-capture API (`AudioSettings.kt:141`, `ExternalAudioSettings.kt:81`). The `microphone` feature is optional (18-20) | PC-07 |
| `FOREGROUND_SERVICE` and `_MEDIA_PLAYBACK`, `_MEDIA_PROJECTION`, `_MEDIA_PROCESSING`, `_DATA_SYNC` | 30, 44, 38, 51, 52 | One per service type (`PlaybackService`, `PlaybackCaptureService`, `ExportService`) | PC-04, four declarations |
| `POST_NOTIFICATIONS` | 53 | Export and capture notifications (`ExportHost.kt:368`, `ExternalAudioSettings.kt:82`) | None. Requested at the moment of use |
| `VIBRATE` | 66 | Haptics | None |
| `ACCESS_NETWORK_STATE` | 58-60 | Removed with `tools:node="remove"` | None |
| `WAKE_LOCK` (merged from Media3), `RECEIVE_BOOT_COMPLETED` (merged from WorkManager via Glance), `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (AndroidX) | merged | Not in source | Mention both in the privacy policy (PC-02) |

Not present (verified): INTERNET, SCHEDULE_EXACT_ALARM and USE_EXACT_ALARM, QUERY_ALL_PACKAGES, MANAGE_EXTERNAL_STORAGE, READ_MEDIA_IMAGES and READ_MEDIA_VIDEO, FOREGROUND_SERVICE_MICROPHONE, BLUETOOTH_*, AD_ID, camera, location.

Components:

| Component | Exported | Gate | Note |
|---|---|---|---|
| `MainActivity` | yes | none | LAUNCHER; `geode://preset` and `geode://template` (BROWSABLE, `autoVerify=false`); `MEDIA_PLAY_FROM_SEARCH` |
| `VisualizerWallpaperService` | yes | `BIND_WALLPAPER` | No mic (`pcmProvider = null`) |
| `VisualizerDreamService` | yes | `BIND_DREAM_SERVICE` | No mic |
| `PlaybackService` | yes | none (Media3 `onConnect` not overridden) | `mediaPlayback`; browse and session actions; lint `ExportedService` |
| `MediaButtonReceiver` | yes | none | Needed for playback resumption |
| `NowPlayingWidgetReceiver` | yes | none | Glance widget |
| `PlaybackCaptureService` | no | none | `mediaProjection` |
| `ExportService` | no | none | `mediaProcessing` + `dataSync` |
| `GeodeNotificationListener` | no | `BIND_NOTIFICATION_LISTENER_SERVICE` | Optional |
| `FileProvider` (`.presets`) | no | grants | `files-path presets/` only |
| Library components | mixed | various | Glance `GlanceRemoteViewsService` (`BIND_REMOTEVIEWS`); WorkManager `SystemJobService` (`BIND_JOB_SERVICE`), `DiagnosticsReceiver` and `ProfileInstallReceiver` (`DUMP`); Media3 `BluetoothValidationActivity` (`BLUETOOTH_PRIVILEGED`) |

Other attributes: `allowBackup=true` with `fullBackupContent` and `dataExtractionRules`; `enableOnBackInvokedCallback=true`; `localeConfig` with English only; `appCategory=audio`; `queries` has two intents (media-browser services and `APP_MUSIC` launchers), not `QUERY_ALL_PACKAGES`; no `networkSecurityConfig`; no `usesCleartextTraffic`; `debuggable` appears only in the debug build (the release APK was not inspected, since none exists).

Android 15 and 16 behaviours checked against code: <br>- Edge-to-edge: `MainActivity.kt:215`. <br>- Predictive back: `PredictiveDismiss.kt:38`, `AppShell.kt:152`. <br>- `BOOT_COMPLETED` FGS launch: none; only WorkManager's reschedule receiver. <br>- `dataSync` and `mediaProcessing` 6-hour timeout: handled at `ExportService.kt:49-55`. <br>- Exact alarms: none. <br>- Partial photo access: not applicable. <br>- Intent redirection: no relaunch of extras. <br>- Large-screen orientation locks: none set. <br>- Audio focus from background: Media3 handles focus inside the FGS.

## Appendix I. Policy URL index

- Target API: https://developer.android.com/google/play/requirements/target-sdk
- 16 KB: https://developer.android.com/guide/practices/page-sizes
- Android 15 changes: https://developer.android.com/about/versions/15/behavior-changes-15
- Android 16 changes: https://developer.android.com/about/versions/16/behavior-changes-16
- MediaProjection: https://developer.android.com/media/grow/media-projection
- FGS declaration: https://support.google.com/googleplay/android-developer/answer/13392821
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- User data and prominent disclosure: https://support.google.com/googleplay/android-developer/answer/10144311
- Account deletion: https://support.google.com/googleplay/android-developer/answer/13327111
- New personal account testing: https://support.google.com/googleplay/android-developer/answer/14151465
- Payments: https://support.google.com/googleplay/android-developer/answer/9858738
- Subscriptions: https://support.google.com/googleplay/android-developer/answer/12154973
- Billing security: https://developer.android.com/google/play/billing/security
- Billing library deprecation: https://developer.android.com/google/play/billing/deprecation-faq
- Target audience: https://support.google.com/googleplay/android-developer/answer/9893335
- Content rating: https://support.google.com/googleplay/android-developer/answer/9859655
- Metadata policy: https://support.google.com/googleplay/android-developer/answer/9898842
- Preview assets: https://support.google.com/googleplay/android-developer/answer/9866151
- Photo and video permissions: https://support.google.com/googleplay/android-developer/answer/14115180
- Advertising ID: https://support.google.com/googleplay/android-developer/answer/6048248
- Developer contact: https://support.google.com/googleplay/android-developer/answer/10840893
- Android Auto distribution: https://developer.android.com/training/cars/distribute
- Car app quality: https://developer.android.com/docs/quality-guidelines/car-app-quality
- Media apps for cars: https://developer.android.com/training/cars/media
- Flash threshold standard (referenced by the app's own copy, not fetched): https://www.w3.org/TR/WCAG22/#three-flashes-or-below-threshold

## Appendix J. Not verified

- Whether the four signing secrets exist; whether the Console already has `dev.geode`; the developer account type and date.
- The release (minified) APK or AAB contents. Only the debug APK was inspected.
- Cause of the red PR #9 jobs (run 37557709201).
- Runtime behaviour on Android 14-16, 16 KB devices, foldables, TalkBack, and `StudioClips` on API 33 and up.
- Whether the Console blocks a rollout without FGS declarations; the policy page names no enforcement date.
- Whether Play counts Drive appData backup as "collection" in Data safety.
- Authorship of the crystal theme packs and bundled `.milk` presets.
