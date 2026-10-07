package dev.geode.export

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import dev.geode.R
import dev.geode.util.bestEffort
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume

@UnstableApi
class StudioExporter(
    private val context: Context,
) {
    sealed interface Result {
        data class Saved(
            val uri: Uri,
            val durationMs: Long,
        ) : Result

        data class Failed(
            val message: String,
        ) : Result

        data object Cancelled : Result
    }

    suspend fun export(
        source: Uri,
        sourceDurationMs: Long,
        edit: ClipEdit,
        displayName: String,
        codec: ExportCodec = ExportCodec.H264,
        destination: Uri? = null,
        onProgress: (Float) -> Unit,
    ): Result {
        val lut = edit.lutUri?.let { uri -> withContext(Dispatchers.IO) { CubeLut.load(context, uri) } }
        val item =
            MediaItem
                .Builder()
                .setUri(source)
                .setClippingConfiguration(edit.clipping())
                .build()
        val edited =
            EditedMediaItem
                .Builder(item)
                .setRemoveAudio(edit.mute)
                .setEffects(Effects(emptyList(), edit.videoEffects(lut)))
                .apply { edit.speedProvider()?.let { setSpeed(it) } }
                .build()
        val composition = Composition.Builder(EditedMediaItemSequence.Builder().addItem(edited).build()).build()
        return exportComposition(composition, edit.outputMs(sourceDurationMs), displayName, codec, destination, onProgress)
    }

    /**
     * Renders [composition] and saves it either to Movies/Geode (when [destination] is null) or
     * straight into the SAF document [destination] the caller already opened — the same choice
     * [VideoExporter.exportToDestination] offers the visualizer export path. Below API 29
     * [publish] cannot insert into MediaStore at all, so callers on those versions must always
     * pass a [destination]; [ExportHost] enforces that by forcing its folder picker there.
     */
    suspend fun exportComposition(
        composition: Composition,
        outputDurationMs: Long,
        displayName: String,
        codec: ExportCodec = ExportCodec.H264,
        destination: Uri? = null,
        onProgress: (Float) -> Unit,
    ): Result {
        val scratch = File(RenderScratch.directory(context.cacheDir), "studio-${UUID.randomUUID()}.mp4")
        try {
            val outcome =
                withContext(Dispatchers.Main) {
                    runTransformer(composition, scratch, outputDurationMs, codec.available(), onProgress)
                }
            if (outcome != null) return outcome
            return if (destination != null) {
                withContext(Dispatchers.IO) { publishToDestination(scratch, destination, outputDurationMs) }
            } else {
                val published = withContext(Dispatchers.IO) { publish(scratch, displayName) }
                published
                    ?.let { Result.Saved(it, outputDurationMs) }
                    ?: Result.Failed("The finished file could not be saved to Movies/Geode.")
            }
        } finally {
            scratch.delete()
        }
    }

    private suspend fun runTransformer(
        composition: Composition,
        output: File,
        outputDurationMs: Long,
        codec: ExportCodec,
        onProgress: (Float) -> Unit,
    ): Result? {
        var activeTransformer: Transformer? = null
        try {
            return suspendCancellableCoroutine { continuation ->
                val built =
                    Transformer
                        .Builder(context)
                        .setVideoMimeType(codec.mimeType)
                        .addListener(
                            object : Transformer.Listener {
                                override fun onCompleted(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                ) {
                                    continuation.resumeOnce(null)
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException,
                                ) {
                                    continuation.resumeOnce(Result.Failed(describe(exportException)))
                                }
                            },
                        ).build()
                activeTransformer = built
                runCatching { built.start(composition, output.absolutePath) }
                    .onFailure {
                        continuation.resumeOnce(Result.Failed(it.message ?: "The export could not be started."))
                        return@suspendCancellableCoroutine
                    }
                val holder = ProgressHolder()
                val scope = kotlinx.coroutines.CoroutineScope(continuation.context)
                scope.launch {
                    while (continuation.isActive) {
                        val state = runCatching { built.getProgress(holder) }.getOrNull()
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress / 100f)
                        }
                        delay(PROGRESS_POLL_MS)
                    }
                }
                if (outputDurationMs <= 0L) onProgress(0f)
            }
        } finally {
            // Transformer belongs to Main. Cancellation must finish here before the coordinator
            // admits another job or the outer finally removes Transformer's output file.
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                bestEffort(TAG, "Release Studio Transformer") { activeTransformer?.cancel() }
            }
        }
    }

    private suspend fun publish(
        file: File,
        displayName: String,
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val resolver = context.contentResolver
            publishMediaStoreVideo(PendingVideoStore(resolver, displayName)) { uri ->
                val output =
                    resolver.openOutputStream(uri)
                        ?: throw IOException(context.getString(R.string.export_output_open_failed))
                output.use { out -> file.inputStream().use { it.copyTo(out) } }
                true
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w(TAG, "Could not publish Studio export", error)
            null
        }
    }

    /**
     * Copies [file] into the SAF document [destination] the caller already created via
     * `CreateDocument`. Mirrors [VideoExporter.exportToDestination]'s write, and cleans up the
     * (now empty or partial) document on any failure rather than leaving a broken file behind.
     */
    private suspend fun publishToDestination(
        file: File,
        destination: Uri,
        outputDurationMs: Long,
    ): Result =
        runCatching {
            val resolver = context.contentResolver
            val wrote =
                resolver.openOutputStream(destination)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
            if (!wrote) {
                bestEffort(TAG, "DocumentsContract.deleteDocument(resolver, de...") {
                    DocumentsContract.deleteDocument(resolver, destination)
                }
                return Result.Failed(
                    "The folder you chose would not let the file be written. Some cloud providers refuse " +
                        "this; try your Videos library or a folder on the device.",
                )
            }
            currentCoroutineContext().ensureActive()
            Result.Saved(destination, outputDurationMs)
        }.getOrElse { e ->
            bestEffort(TAG, "DocumentsContract.deleteDocument(resolver, de...") {
                DocumentsContract.deleteDocument(context.contentResolver, destination)
            }
            if (e is CancellationException) throw e
            Result.Failed(e.message ?: "The export could not be saved to that folder.")
        }

    private fun describe(exception: ExportException): String =
        when (exception.errorCode) {
            ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
            ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
            ->
                "This device's video encoder would not accept that output — try a smaller size or a " +
                    "different aspect ratio."
            ExportException.ERROR_CODE_DECODER_INIT_FAILED,
            ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            ExportException.ERROR_CODE_IO_FILE_NOT_FOUND,
            -> "That clip could not be read — the file may have moved, or be in a format this device cannot decode."
            ExportException.ERROR_CODE_IO_NO_PERMISSION -> "Geode does not have permission to read that file."
            else -> exception.message ?: "The export failed."
        }

    private companion object {
        const val PROGRESS_POLL_MS = 250L

        fun CancellableContinuation<Result?>.resumeOnce(value: Result?) {
            if (isActive) resume(value)
        }
    }
}

private const val TAG = "StudioExporter"
