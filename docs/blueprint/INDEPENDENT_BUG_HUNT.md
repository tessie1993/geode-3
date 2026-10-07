# Independent bug hunt — 7 October 2026

Read-only review of `geode-3`, account/visual foundation branch (latest observed local head `4735a82`). No local build, tests, lint, emulator, or benchmark ran. These are source-traced failure paths with proposed Actions/device checks, not observed production crashes. Existing native-freshness, preset-storage, inert-control, timeline-gap, and recorded-take findings are not repeated. Graphify tools were unavailable, so the review followed source calls directly.

Preserve all current C++ styles and MilkDrop. None of these fixes requires removing a style or making live movement follow a repeatable route.

## Ranked fixes with one owner per package

### H01 — P1: Studio loses ownership of an active export

**Assigned owner: `studio_export_fixes`.**

`StudioScreen.kt::StudioRoute` calls `refreshStudioClips()` on entry (line 58). `ExportController.kt::refreshStudioClips` (258) writes `Loading`, then `Idle`, into the same `_studio.phase` that `startStudioExport` (315) and `startProjectExport` (340) use as their sole exclusion guard. Closing the clip editor also calls `clearStudioResult` (382), which unconditionally writes `Idle`. Neither action cancels the running job.

Consequently, close/re-enter/refresh can permit a second export while the first is alive. Both use one `StudioExporter`, one `transformer` field, one cancellation flag, and one `studioJob` slot. An earlier completion can null the newer job reference and overwrite its progress/result. Every ordinary completion also publishes `Done`/`Failed`, immediately calls `refreshStudioClips`, and erases that terminal result.

The same owner should contain exceptions: both Studio launch bodies lack `catch/finally`; `StudioExporter.export` constructs effects/composition before its inner `try`, and `exportComposition` has only `finally`. An invalid configuration/initialization exception can escape the launched coroutine without clearing Running. Project composition is also built synchronously before launching.

**Acceptance:** delay exporter A; re-enter Studio, refresh the list, and close/reopen the editor; exporter B must not start. Cancel must target A. On completion the result survives list refresh. Inject preparation failure and stale progress/completion callbacks; publish one terminal state and release ownership. Preserve cancellation exceptions. Broader background-service ownership is an existing separate gap.

### H02 — P1: Failed output publication leaves rows or reports false success

**Assigned owner: `studio_export_fixes`.**

`StudioExporter.kt::publish` (184–220) inserts an `IS_PENDING` MediaStore row, then wraps stream copy/close/update in `runCatching`. If copy, close, or update throws, `getOrNull` discards the exception but does not delete the inserted row. `update(IS_PENDING=0)` returning zero is ignored and the URI is returned as saved. `VideoExporter.kt::export` similarly ignores that update result, and calls `openFileDescriptor(outUri, "w")` before entering its cleanup `try`; a thrown open exception bypasses row deletion.

The startup orphan sweep is not a substitute: it runs only on container creation and ignores rows younger than five minutes. Failed output can remain hidden and occupy storage throughout the session.

**Acceptance:** inject null open, throwing open, write failure, close failure, zero-row update, update exception, and cancellation. No success before publication is confirmed; each app-created failed row is removed once, the original exception remains diagnosable, and no unrelated existing user document is deleted. Apply a bounded output transaction rather than more scattered `runCatching` blocks.

### H03 — P1: Studio transitions retain a full CPU frame for every boundary

**Proposed sole owner: transition-memory agent; files `GlTransitionEffect.kt`, `TransitionFrameStore.kt`, transition-specific tests. Coordinate API changes with Studio owner.**

`ProjectComposition.kt::videoSequenceFor` creates a store per transition and attaches it to both adjacent effects. `TransitionCaptureProgram.release` (`GlTransitionEffect.kt:80`) allocates and publishes an RGBA direct buffer via `readBack` (101). `GlTransitionProgram.configure` (128) uploads that buffer but never consumes/clears `store.frame`; release deletes its GL textures, not the stored CPU frame. The composition still references its effects and stores for the duration of the export.

Retained CPU memory grows with the number of capture programs that have released while their stores remain reachable. A 3840×2160 RGBA frame is 33,177,600 bytes (about 31.6 MiB); thirty retained frames approach 949 MiB before decoders, textures, and encoder memory. Whether this accumulation occurs at boundaries or during final teardown depends on Media3's lifecycle and needs measurement. The exact failure threshold is device-dependent. This review does **not** assume that Media3 always configures/releases adjacent effects in a particular order; transition timing also needs an explicit integration check.

**Acceptance:** use a consume-once handoff or bounded frame owner with defined lifetime; release buffers after successful upload and on cancellation/error, and handle a frame arriving after configure. An Actions emulator export with many short transitions must show bounded outstanding frame storage; a 4K physical-device run validates peak native memory and visible transitions. Test dimensions with checked byte arithmetic and cleanup after readback/upload failure.

### H04 — P1: LUT/subtitle input can allocate without limit and block the UI

**Proposed sole owner: editor-input agent; files `CubeLut.kt`, `StudioPreview.kt`, relevant `StudioScreen.kt` LUT state, `ui/studio/TimelineLanes.kt`, parser tests. Coordinate off-main project preparation with Studio owner.**

`CubeLut.load` (26) calls `readBytes()` without a byte limit; `parse` accumulates a `FloatArray` for every numeric row before checking table dimensions. `MAX_SIZE=65` limits the eventual cube dimensions, not the amount of input read or rows retained. `ProjectComposition.build → videoItem → CubeLut.load` runs synchronously from `startProjectExport` on the UI path. The SRT picker in `TimelineLanes.kt:155–162` also reads and parses the entire provider stream directly inside the activity-result callback.

There is also a concrete false-success display: the LUT picker immediately sets `lutUri`; `studio_lut_loaded` is shown solely because the URI is non-null. A binary file, malformed cube, missing grant, or unreadable URI makes `CubeLut.load` return null; preview/export silently omit the LUT while the screen still says it is loaded.

**Acceptance:** bounded streaming readers with explicit byte/row/cue limits, finite values, supported syntax and an off-main parse boundary. A rejected input must retain the previous valid LUT/captions and show a recoverable error. Test a provider that never reports size, oversized numeric rows, malformed/NaN cube content, slow reads, cancelled picks, and invalid SRT. Selecting a text file through the LUT picker must not show Loaded or silently export an ungraded result.

### H05 — P1: MilkDrop folder import bypasses the texture admission policy

**Proposed sole owner: MilkDrop-asset agent; files `MilkImportController.kt`, `MilkPackImporter.kt`, `TextureStore.kt`, bounded shared asset validation.**

`TextureStore.importOne` limits compressed bytes and checks some image headers. However `MilkImportController.importMilkFolderAsync → MilkPackImporter.import → copy` streams every accepted extension to disk without a per-file or total-pack budget, image validation, or file-count limit. Folder traversal limits depth only. Single `.milk` import also copies unbounded input. The importer and subsequent texture relinker read entire `.milk` files into strings.

The direct texture path is also incomplete: PNG/JPEG/BMP validation accepts any positive decoded dimensions; DDS checks only its four-byte magic, and TGA checks selected header fields without a dimension/pixel budget. A small compressed file with enormous dimensions can pass admission. Subsequent projectM decode/allocation behavior needs native/device verification; the admission bypass itself is confirmed.

**Acceptance:** one common admission policy for direct texture, individual `.milk`, and folder imports; validate encoded bytes, dimensions/decoded footprint, syntax, file count and aggregate budget before committing. Stream to staging, retain existing assets on failure, and report per-entry outcomes. Test the identical malformed/oversized asset through both entry points, a large shallow folder, and a huge declared dimension in a small header. Preserve legitimate formats and all shipped presets; do not solve this by deleting MilkDrop support.

### H06 — P2: Startup scratch cleanup can delete files from the current run

**Proposed sole owner: export-scratch agent; `GeodeContainer.kt` and export scratch ownership helpers.**

`GeodeContainer.kt:41–42` claims cleanup finishes before any render can start. In fact `sweepStaleRenderScratch` launches on `Dispatchers.IO` (45), and its `listFiles()` runs later. There is no startup barrier or run/session ownership check. If that task is delayed until a new export creates a matching `studio-`, `geode_aac_`, or `geode_loop_` file, the sweep deletes the current run's file. This is an ordering defect; normal-device frequency has not been measured.

**Acceptance:** scope scratch to a process/run identity or take a previous-run inventory before new work is admitted; cleanup must never select an active run's resources. A controlled scheduler test pauses the sweep, creates a new run and old orphan, then resumes; only the orphan is deleted. Do not move expensive directory/provider IO onto the main thread to repair ordering.

### H07 — P2: Export proceeds after its foreground service fails to start

**Proposed sole owner: export-service agent; `ExportService.kt`, `ExportRun.kt`, controller start handshake. Coordinate controller edits with Studio owner.**

`ExportService.start` (122) swallows `startForegroundService` failure and returns no status. `ExportController.startExport/startLoopRender` mark the run active and launch expensive work regardless. Inside `ExportService.onCreate`, both initial and later foreground promotion use `bestEffort`; promotion failure does not fail/cancel the export or stop the service. The code therefore cannot establish the background-lifetime promise it presents.

Android distinguishes starting a service from promoting it to foreground, and both prerequisites and background-start restrictions can cause failures: [official launch contract](https://developer.android.com/develop/background-work/services/fgs/launch), inspected 7 October 2026. Notification permission denial alone is **not** equivalent to foreground start failure and must not be used to disable otherwise permitted work.

**Acceptance:** explicit start/promotion result or service-owned admission; on failure stop/cancel safely and offer a visible retry. Inject service-start and promotion exceptions and assert no detached expensive job survives. Device test immediate backgrounding and exhausted media-processing allowance. Existing long-render timeout/cancellation requirements remain separate.

## Account/security foundation review: honest scaffolding, not release wiring

- Only the debug preview calls `PremiumUnlockScreen`; its default is `Unconfigured`, purchase/restore callbacks cannot be activated, and legal actions display a configuration notice. I found no production fake price or callback that directly grants access.
- `AccountState`, `PremiumAccessPolicy`, and the Python billing service have no live auth/payment wiring. The comments and READMEs correctly say so. A public `VERIFIED` enum is policy input, **not** a verified signature. There is currently no JSON/HTTP adapter to trace as an exploit, so the enum's existence alone is not reported as an active bypass. The future adapter must authenticate signed claims before construction; execution gates and secure storage still do not exist.
- **Configuration mismatch to fix before wiring:** Android `PremiumProduct` hardcodes `premium_monthly`, `premium_yearly`, `premium_lifetime`; `PremiumProductUiModel.canPurchase` and the access policy reject every other ID. The server `Catalog` explicitly accepts injected Console identifiers. No actual production Console IDs were supplied. One billing-integration owner should establish a shared validated catalog/config contract; do not invent prices or treat these draft IDs as deployed configuration.
- The premium screen's sentence about a profile not determining “purchase ownership” exposes internal identity design in the product UI. Keep the useful promise that sign-in is optional; move commerce-principal details to engineering docs. This is copy quality, not a security failure.
- Current Media3 documentation says untrusted default connections have read access only. I did **not** report a blanket remote-control exploit from the exported playback service merely because its callback lacks a connection override. A separate permissionless companion-app test is appropriate before asserting its actual exposed library/metadata surface.

## Concrete documentation/slop corrections

These are claims contradicted by source, not objections to comment length or developer style:

1. Root README still says the removed workflows ship a signed APK and a Play Store release. Current Actions should be described by its actual debug artifact and gated signed-AAB behavior. Building a bundle is not publication to Play.
2. Root README calls FormDrive a driver that modulates every family; current native audit finds it inert. Describe its actual state until the render consumers exist. Do not remove the retained style family to hide that claim.
3. Root README says the exporter works unchanged on captured other-app audio. `PlayerSession` supplies `currentUri` as `exportUri`, `CaptureController` pauses playback without replacing that URI, and the exporter analyses/muxes that local file. No captured PCM recording is passed. With a previously loaded song, export uses that song; without one, `startExport` returns. State that capture currently drives live visuals only; actual performance/audio recording belongs to its explicit feature package.
4. `GeodeContainer` says every publisher deletes a row on clean failure and startup scratch is safe to sweep wholesale. H02 and H06 show the exceptions. Update these claims together with the fixes.
5. New blueprint documents now preserve all styles and specify generative live camera movement. Historical rebuild instructions still mention dropping roughly 82 styles. Their historical label is useful, but a direct supersession note on that removal paragraph prevents a later worker treating it as an active task.

Proposed checks above belong in GitHub Actions or named device runs. This report establishes neither completeness of the bug search nor Play readiness.
