package dev.geode.ui

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import dev.geode.RingLog
import dev.geode.data.MilkAssetAdmission
import dev.geode.data.MilkPackImporter
import dev.geode.data.MilkTextureLink
import dev.geode.data.MilkTextureLinks
import dev.geode.data.PresetStore
import dev.geode.render.scene.MilkStarterPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal class MilkImportController(
    private val application: Application,
    private val scope: CoroutineScope,
) {
    private val textureLinks = MilkTextureLinks(application)

    private fun builtInDir(): File = File(application.filesDir, "milk-builtin")

    private fun importDir(): File = File(application.filesDir, "milk")

    @Suppress("TooGenericExceptionCaught")
    fun milkPresetFilesAsync(onDone: (List<MilkFile>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val files =
                try {
                    builtInDir().deleteRecursively()
                    File(importDir(), "textures").mkdirs()
                    MilkStarterPack.install(application, importDir())
                    // Idempotent refresh: catches textures imported before a preset existed,
                    // presets from older installs that predate linking, and starter presets.
                    textureLinks.relinkAll()
                    importDir()
                        .listFiles { f -> f.extension == "milk" }
                        .orEmpty()
                        .map { MilkFile(it.nameWithoutExtension, it.absolutePath) }
                        .sortedBy { it.name }
                } catch (t: Throwable) {
                    RingLog.note("MilkFiles", "milk list failed", t)
                    emptyList()
                }
            withContext(Dispatchers.Main) { onDone(files) }
        }
    }

    fun importMilkPresetAsync(
        uri: Uri,
        onDone: (String?) -> Unit,
    ) {
        scope.launch(Dispatchers.IO) {
            val path = importMilkPresetBlocking(uri)
            withContext(Dispatchers.Main) { onDone(path) }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    internal fun importMilkPresetBlocking(uri: Uri): String? =
        try {
            val dir = importDir().apply { mkdirs() }
            val display = displayNameOf(uri).orEmpty().ifBlank { "preset" }
            MilkAssetAdmission.fileNameIssue(display)?.let { throw MilkAssetAdmission.Rejected(it) }
            val file = File(dir, PresetStore.milkFileName(display))
            val reason = MilkAssetAdmission.store(
                target = file,
                extension = "milk",
                budget = MilkAssetAdmission.Budget(),
                replaceExisting = true,
            ) { application.contentResolver.openInputStream(uri) }
            if (reason == null) {
                runCatching { textureLinks.relink(file) }
                    .onFailure { RingLog.note("MilkImport", "preset saved but texture links could not be refreshed", it) }
                file.absolutePath
            } else {
                RingLog.note("MilkImport", reason)
                null
            }
        } catch (t: Throwable) {
            RingLog.note("MilkImport", "milk import failed", t)
            null
        }

    fun importMilkFolderAsync(
        treeUri: Uri,
        onDone: (MilkPackImporter.Report) -> Unit,
    ) {
        scope.launch(Dispatchers.IO) {
            val scan = collectMilkEntries(treeUri)
            val imported = MilkPackImporter.import(scan.entries, importDir())
            // Report the post-link state and preserve scan errors instead of treating an
            // unreadable or truncated folder as a successful empty import.
            val linked = runCatching { textureLinks.relinkAll() }
            val report = imported.copy(
                presetsMissingTextures = linked.getOrDefault(imported.presetsMissingTextures),
                issue = listOfNotNull(
                    scan.issue,
                    imported.issue,
                    "Texture links could not be refreshed.".takeIf { linked.isFailure },
                ).joinToString(" ").ifBlank { null },
            )
            withContext(Dispatchers.Main) { onDone(report) }
        }
    }

    private data class FolderScan(
        val entries: List<MilkPackImporter.Entry>,
        val issue: String?,
    )

    private fun collectMilkEntries(treeUri: Uri): FolderScan {
        val entries = mutableListOf<MilkPackImporter.Entry>()
        val pending = ArrayDeque<Pair<String, Int>>()
        val visited = mutableSetOf<String>()
        var nodes = 0
        var issue: String? = null
        try {
            pending.add(DocumentsContract.getTreeDocumentId(treeUri) to 0)
            while (pending.isNotEmpty()) {
                val (id, depth) = pending.removeFirst()
                if (!visited.add(id)) continue
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, id)
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                )
                val cursor = application.contentResolver.query(childrenUri, projection, null, null, null)
                    ?: return FolderScan(entries, "Folder could not be read completely.")
                cursor.use { children ->
                    while (children.moveToNext()) {
                        if (++nodes > MilkAssetAdmission.MAX_WALK_NODES) {
                            return FolderScan(entries, "Folder scan stopped at the ${MilkAssetAdmission.MAX_WALK_NODES}-entry limit.")
                        }
                        val childId = children.getString(0) ?: continue
                        val name = children.getString(1) ?: continue
                        if (children.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (depth < MilkAssetAdmission.MAX_WALK_DEPTH) {
                                pending.add(childId to depth + 1)
                            } else {
                                issue = "Folders deeper than ${MilkAssetAdmission.MAX_WALK_DEPTH} levels were skipped."
                            }
                        } else {
                            val extension = name.substringAfterLast('.', "").lowercase()
                            if (extension != "milk" && extension !in MilkAssetAdmission.textureExtensions) continue
                            if (entries.size >= MilkAssetAdmission.MAX_BATCH_FILES) {
                                return FolderScan(entries, "Folder scan stopped at the ${MilkAssetAdmission.MAX_BATCH_FILES}-file limit.")
                            }
                            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            entries += MilkPackImporter.Entry(name) { application.contentResolver.openInputStream(uri) }
                        }
                    }
                }
            }
        } catch (_: IllegalArgumentException) {
            issue = "Folder could not be read completely."
        } catch (_: SecurityException) {
            issue = "Folder access was denied."
        } catch (_: java.io.IOException) {
            issue = "Folder could not be read completely."
        }
        return FolderScan(entries, issue)
    }

    /** What [MilkTextureLinks.relink] decided for this preset, in reference order. */
    fun textureLinksFor(path: String): List<MilkTextureLink> = textureLinks.resolutionFor(File(path))

    fun assignTextureAsync(
        path: String,
        expected: String,
        texture: String?,
        onDone: (List<MilkTextureLink>) -> Unit,
    ) {
        scope.launch(Dispatchers.IO) {
            val links = textureLinks.assign(File(path), expected, texture)
            withContext(Dispatchers.Main) { onDone(links) }
        }
    }

    private fun displayNameOf(uri: Uri): String? =
        runCatching {
            application.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
}
