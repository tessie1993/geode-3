package dev.geode.ui.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.geode.R
import dev.geode.editor.SubtitleCue
import dev.geode.editor.Subtitles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

internal data class SubtitleDocuments(
    val importing: Boolean,
    val errorRes: Int?,
    val pick: () -> Unit,
    val save: () -> Unit,
)

@Composable
internal fun rememberSubtitleDocuments(
    onImport: (List<SubtitleCue>) -> Unit,
    captions: () -> List<SubtitleCue>,
): SubtitleDocuments {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnImport by rememberUpdatedState(onImport)
    val latestCaptions by rememberUpdatedState(captions)
    var importJob by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableStateOf(0L) }
    var loading by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<Int?>(null) }
    var saveError by remember { mutableStateOf<Int?>(null) }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val request = ++generation
            importJob?.cancel()
            loading = true
            importError = null
            importJob =
                scope.launch {
                    try {
                        val cues =
                            withContext(Dispatchers.IO) {
                                try {
                                    context.contentResolver.openInputStream(uri)?.use(Subtitles::readSrt)
                                } catch (_: IOException) {
                                    null
                                } catch (_: SecurityException) {
                                    null
                                }
                            }
                        if (request != generation) return@launch
                        if (cues == null) importError = R.string.studio_srt_invalid else latestOnImport(cues)
                    } finally {
                        if (request == generation) loading = false
                    }
                }
        }
    val exporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SRT_MIME)) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val cues = latestCaptions()
            saveError = null
            scope.launch {
                val saved =
                    withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openOutputStream(uri)?.use { stream ->
                                stream.write(Subtitles.toSrt(cues).toByteArray(Charsets.UTF_8))
                                true
                            } ?: false
                        } catch (_: IOException) {
                            false
                        } catch (_: SecurityException) {
                            false
                        }
                    }
                saveError = if (saved) null else R.string.studio_srt_save_failed
            }
        }
    return SubtitleDocuments(
        importing = loading,
        errorRes = importError ?: saveError,
        pick = { if (!loading) importer.launch(arrayOf(SRT_MIME, "text/plain", "text/*")) },
        save = { exporter.launch("geode_captions_${System.currentTimeMillis()}.srt") },
    )
}

private const val SRT_MIME = "application/x-subrip"
