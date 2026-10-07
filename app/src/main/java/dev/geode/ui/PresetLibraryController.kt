package dev.geode.ui

import android.app.Application
import android.net.Uri
import dev.geode.data.Preset
import dev.geode.data.PresetAdmission
import dev.geode.data.PresetAdmissionException
import dev.geode.data.PresetFailure
import dev.geode.data.PresetFolders
import dev.geode.data.PresetRepository
import dev.geode.data.PresetStore
import dev.geode.data.PresetWrite
import dev.geode.render.scene.SceneIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class PresetLibraryController(
    private val application: Application,
    private val presets: PresetRepository,
    private val scope: CoroutineScope,
    private val storeScope: CoroutineScope,
    private val host: Host,
) {
    interface Host {
        val vizState: StateFlow<VizUiState>

        fun updatePresets(transform: (List<Preset>) -> List<Preset>)

        val presetMirrorUri: String?

        val activeMilkPath: String?
    }

    val folders: StateFlow<PresetFolders> = presets.folders
    private val libraryMutex = Mutex()
    private val reservedNames = BuiltInPresets.ALL.map { it.name }.toSet()

    fun addPresetFolder(path: String) {
        storeScope.launch { libraryMutex.withLock { presets.addFolder(path) } }
    }

    fun renamePresetFolder(
        from: String,
        to: String,
    ) {
        storeScope.launch { libraryMutex.withLock { presets.renameFolder(from, to) } }
    }

    fun movePresetToFolder(
        name: String,
        folder: String,
    ) {
        storeScope.launch {
            libraryMutex.withLock {
                presets.moveToFolder(name, folder)
                mirrorPresetToChosenFolder(name)
                relistPresets()
            }
        }
    }

    fun userMilkPresets(): List<java.io.File> {
        val dir = java.io.File(application.filesDir, "milk")
        dev.geode.render.scene.MilkStarterPack
            .install(application, dir)
        return dir
            .listFiles { f -> f.isFile && f.extension == "milk" }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()
    }

    fun refreshInitial() {
        storeScope.launch {
            libraryMutex.withLock {
                relistPresets()
                presets.refreshFolders()
            }
        }
    }

    private suspend fun relistPresets() {
        val listed = BuiltInPresets.ALL + presets.list()
        host.updatePresets { listed }
    }

    fun savePreset(
        name: String,
        customShader: String?,
        folder: String = "",
        replacing: Preset? = null,
        onResult: (PresetWrite) -> Unit,
    ) {
        @Suppress("NAME_SHADOWING")
        val name = name.replace(" · ", " - ").trim().ifEmpty { "Preset" }
        val s = host.vizState.value
        val milkPath = host.activeMilkPath
        storeScope.launch {
            val result = libraryMutex.withLock {
                operationResult {
                    val milkSource = if (s.sceneId == SceneIds.MILKDROP && milkPath != null) {
                        java.io.File(milkPath).inputStream().use(PresetAdmission::readText)
                    } else {
                        null
                    }
                    presets.save(
                        Preset(name, s.sceneId, s.attack, s.decay, customShader, s.params, milkSource),
                        folder,
                        reservedNames,
                        replacing,
                    )
                }.also { publishSaved(it) }
            }
            withContext(Dispatchers.Main.immediate) { onResult(result) }
        }
    }

    private suspend fun operationResult(block: suspend () -> PresetWrite): PresetWrite = try {
        block()
    } catch (e: PresetAdmissionException) {
        PresetWrite.Failed(e.reason, e.field)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        PresetWrite.Failed(PresetFailure.IO)
    }

    private suspend fun publishSaved(result: PresetWrite) {
        if (result is PresetWrite.Saved) {
            // A listing failure after a successful commit must not turn that commit into a
            // failed save (and make Retry create another copy). Retain the committed entry.
            try {
                relistPresets()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                host.updatePresets { current -> current.filterNot { it.name == result.preset.name } + result.preset }
            }
            // The committed JSON embeds this source. The local .milk file is a derived copy;
            // keep preparing it for the existing mirror/apply paths after the primary commit.
            runCatching {
                result.preset.milkPreset?.let { presets.materializeMilk(result.preset.name, it) }
            }
            mirrorPresetToChosenFolder(result.preset.name)
        }
    }

    private fun mirrorPresetToChosenFolder(name: String) {
        val uriStr = host.presetMirrorUri ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val tree =
                    androidx.documentfile.provider.DocumentFile
                        .fromTreeUri(application, Uri.parse(uriStr))
                        ?: return@runCatching

                fun copyInto(
                    src: java.io.File,
                    mime: String,
                ) {
                    if (!src.exists()) return
                    tree.findFile(src.name)?.delete()
                    val dest = tree.createFile(mime, src.name) ?: return
                    application.contentResolver.openOutputStream(dest.uri)?.use { out ->
                        src.inputStream().use { it.copyTo(out) }
                    }
                }
                presets.fileOf(name)?.let { copyInto(it, "application/json") }
                milkFileFor(name).let { copyInto(it, "text/plain") }
            }
        }
    }

    private fun milkFileFor(presetName: String): java.io.File =
        java.io.File(
            java.io.File(application.filesDir, "milk").apply { mkdirs() },
            PresetStore.milkFileName(presetName),
        )

    fun milkPresetPathFor(preset: Preset): String? {
        if (preset.sceneId != SceneIds.MILKDROP) return null
        return presets.materializeMilk(preset.name, preset.milkPreset)
    }

    fun presetShareLink(name: String): String? {
        val preset =
            host.vizState.value.presets
                .firstOrNull { it.name == name } ?: return null
        val link = PresetLink.encode(PresetStore.toJson(preset))
        return link.takeIf { it.length <= PresetLink.MAX_LINK_LENGTH }
    }

    fun importPresetLink(text: String, onResult: (PresetWrite) -> Unit) {
        storeScope.launch {
            val result = libraryMutex.withLock {
                val link = PresetLink.findIn(text)
                val json = link?.let(PresetLink::decode)
                if (json == null) PresetWrite.Failed(PresetFailure.MALFORMED) else importPresetJson(json)
            }
            withContext(Dispatchers.Main.immediate) { onResult(result) }
        }
    }

    fun importPresetFile(
        uri: Uri,
        onResult: (PresetWrite) -> Unit,
    ) {
        storeScope.launch {
            val result = libraryMutex.withLock {
                operationResult {
                    val json = application.contentResolver.openInputStream(uri)?.use(PresetAdmission::readText)
                        ?: return@operationResult PresetWrite.Failed(PresetFailure.IO)
                    importPresetJson(json)
                }
            }
            withContext(Dispatchers.Main.immediate) { onResult(result) }
        }
    }

    private suspend fun importPresetJson(json: String): PresetWrite = operationResult {
        val incoming = PresetAdmission.decode(json)
        presets.save(incoming.copy(name = incoming.name.replace(" · ", " - ").trim()), reservedNames = reservedNames)
    }.also { publishSaved(it) }

    fun presetFile(name: String): java.io.File? = presets.fileOf(name)

    fun deletePreset(name: String) {
        if (BuiltInPresets.isBuiltIn(name)) return
        storeScope.launch {
            libraryMutex.withLock {
                removeMirroredPreset(presets.fileOf(name)?.name, milkFileFor(name).name)
                presets.delete(name)
                relistPresets()
            }
        }
    }

    private fun removeMirroredPreset(
        jsonName: String?,
        milkName: String?,
    ) {
        val uriStr = host.presetMirrorUri ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val tree =
                    androidx.documentfile.provider.DocumentFile
                        .fromTreeUri(application, Uri.parse(uriStr))
                        ?: return@runCatching
                jsonName?.let { tree.findFile(it)?.delete() }
                milkName?.let { tree.findFile(it)?.delete() }
            }
        }
    }
}
