package dev.geode.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.geode.R
import dev.geode.export.ClipEdit
import dev.geode.export.CubeLut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A selection becomes editable state only after the complete document has passed admission. */
@Composable
internal fun StudioLutPicker(
    edit: ClipEdit,
    onEdit: (ClipEdit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestEdit by rememberUpdatedState(edit)
    val latestOnEdit by rememberUpdatedState(onEdit)
    var validatedUri by remember { mutableStateOf<String?>(null) }
    var invalidUri by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var importFailed by remember { mutableStateOf(false) }
    var importJob by remember { mutableStateOf<Job?>(null) }
    var importGeneration by remember { mutableStateOf(0L) }

    LaunchedEffect(edit.lutUri) {
        val uri = edit.lutUri
        invalidUri = null
        if (uri == null) {
            validatedUri = null
        } else if (validatedUri != uri) {
            val valid = withContext(Dispatchers.IO) { CubeLut.load(context, uri) != null }
            if (valid) validatedUri = uri else invalidUri = uri
        }
    }

    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val generation = ++importGeneration
            importJob?.cancel()
            importing = true
            importFailed = false
            importJob =
                scope.launch {
                    try {
                        val valid =
                            withContext(Dispatchers.IO) {
                                CubeLut.load(context, uri.toString()) ?: return@withContext false
                                try {
                                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    true
                                } catch (_: SecurityException) {
                                    false
                                }
                            }
                        if (generation != importGeneration) return@launch
                        if (valid) {
                            validatedUri = uri.toString()
                            invalidUri = null
                            latestOnEdit(latestEdit.copy(lutUri = uri.toString()))
                        } else {
                            importFailed = true
                        }
                    } finally {
                        if (generation == importGeneration) importing = false
                    }
                }
        }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        CrystalButton(filled = false, onClick = { if (!importing) picker.launch(arrayOf("*/*")) }) {
            Text(stringResource(R.string.studio_lut_pick))
        }
        if (edit.lutUri != null) {
            TextButton(onClick = {
                importGeneration++
                importJob?.cancel()
                importing = false
                importFailed = false
                validatedUri = null
                latestOnEdit(latestEdit.copy(lutUri = null))
            }) { Text(stringResource(R.string.studio_lut_clear)) }
        }
    }
    if (importing || (edit.lutUri != null && edit.lutUri != validatedUri && edit.lutUri != invalidUri)) {
        LinearProgressIndicator()
        Text(stringResource(R.string.studio_input_loading), style = MaterialTheme.typography.bodySmall)
    }
    if (importFailed || (edit.lutUri != null && edit.lutUri == invalidUri)) {
        Text(
            stringResource(R.string.studio_lut_invalid),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Text(
        edit.lutUri?.takeIf { it == validatedUri }?.let {
            stringResource(R.string.studio_lut_loaded, it.substringAfterLast('/').substringAfterLast(':'))
        } ?: stringResource(R.string.studio_lut_explainer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
