package dev.geode.ui

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import dev.geode.RingLog
import dev.geode.data.AtomicWrite
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
            val file = File(dir, PresetStore.milkFileName(display))
            val staged = File.createTempFile("preset-admission-", ".stage", application.cacheDir)
            val written =
                try {
                    application.contentResolver.openInputStream(uri)?.use { input ->
                        MilkAssetAdmission.stage(input, staged, "milk")
                        synchronized(MilkAssetAdmission) {
                            AtomicWrite.stream(file) { out -> staged.inputStream().use { it.copyTo(out) } }
                        }
                    } ?: false
                } finally {
                    staged.delete()
                }
            if (written) textureLinks.relink(file)
            if (written) file.absolutePath else null
        } catch (t: Throwable) {
            RingLog.note("MilkImport", "milk import failed", t)
            null
        }

    fun importMilkFolderAsync(
        treeUri: Uri,
        onDone: (MilkPackImporter.Report) -> Unit,
    ) {
        scope.launch(Dispatchers.IO) {
            val entries = mutableListOf<MilkPackImporter.Entry>()
            val collected =
                runCatching {
                    val root = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
                    collectMilkEntries(root, entries, depth = 0, visited = intArrayOf(0))
                }
            val imported =
                if (collected.isSuccess) {
                    MilkPackImporter.import(entries, importDir())
                } else {
                    MilkPackImporter.Report(
                        0,
                        0,
                        entries.size.coerceAtLeast(1),
                        0,
                        listOf(MilkPackImporter.Outcome("folder", false, collected.exceptionOrNull()?.message)),
                    )
                }
            val report =
                if (imported.total > 0) imported.copy(presetsMissingTextures = textureLinks.relinkAll()) else imported
            withContext(Dispatchers.Main) { onDone(report) }
        }
    }

    private fun collectMilkEntries(
        dir: Uri,
        out: MutableList<MilkPackImporter.Entry>,
        depth: Int,
        visited: IntArray,
    ) {
        require(depth <= MILK_WALK_DEPTH) { "folder nesting limit exceeded" }
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(dir, DocumentsContract.getDocumentId(dir))
        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
        requireNotNull(application.contentResolver.query(children, projection, null, null, null)).use { cursor ->
            while (cursor.moveToNext()) {
                require(++visited[0] <= MilkAssetAdmission.MAX_ENTRIES) { "folder entry limit exceeded" }
                val uri = DocumentsContract.buildDocumentUriUsingTree(dir, cursor.getString(0))
                if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    collectMilkEntries(uri, out, depth + 1, visited)
                } else {
                    val name = cursor.getString(1) ?: continue
                    out += MilkPackImporter.Entry(name) { application.contentResolver.openInputStream(uri) }
                }
            }
        }
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

    private companion object {
        const val MILK_WALK_DEPTH = 4
    }
}
