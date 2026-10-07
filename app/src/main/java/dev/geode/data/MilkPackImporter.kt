package dev.geode.data

import java.io.File
import java.io.InputStream

object MilkPackImporter {
    class Entry(
        val name: String,
        val open: () -> InputStream?,
    )

    data class EntryResult(
        val name: String,
        val storedPath: String? = null,
        val skipReason: String? = null,
    )

    data class Report(
        val presets: Int,
        val textures: Int,
        val skipped: Int,
        val presetsMissingTextures: Int,
        val results: List<EntryResult> = emptyList(),
        val issue: String? = null,
    ) {
        val total: Int get() = presets + textures
    }

    fun import(
        entries: List<Entry>,
        milkDir: File,
    ): Report =
        synchronized(MilkAssetAdmission.importLock) {
            val textureDir = File(milkDir, "textures")
            val budget = MilkAssetAdmission.Budget()
            val results = entries.take(MilkAssetAdmission.MAX_BATCH_FILES).map { entry ->
                val extension = entry.name.substringAfterLast('.', "").lowercase()
                val nameIssue = MilkAssetAdmission.fileNameIssue(entry.name)
                val target = if (nameIssue == null) targetFor(entry.name, extension, milkDir, textureDir) else null
                val reason = if (target == null) {
                    nameIssue ?: "unsupported file type"
                } else {
                    MilkAssetAdmission.store(target, extension, budget, replaceExisting = false, entry.open)
                }
                EntryResult(entry.name.take(240), if (reason == null) target?.absolutePath else null, reason)
            }
            val imported = results.mapNotNull { it.storedPath?.let(::File) }
            val importedPresets = imported.filter { it.extension == "milk" }
            Report(
                presets = importedPresets.size,
                textures = imported.size - importedPresets.size,
                skipped = entries.size - imported.size,
                presetsMissingTextures = importedPresets.count { missesATexture(it, textureDir) },
                results = results,
                issue = if (entries.size > results.size) "Import stopped at the ${MilkAssetAdmission.MAX_BATCH_FILES}-file limit." else null,
            )
        }

    private fun targetFor(
        name: String,
        extension: String,
        milkDir: File,
        textureDir: File,
    ): File? {
        val leaf = name.substringAfterLast('/').substringAfterLast('\\')
        return when {
            extension == "milk" -> File(milkDir, PresetStore.milkFileName(leaf))
            extension in MilkAssetAdmission.textureExtensions -> File(textureDir, TextureStore.safeTextureFileName(leaf))
            else -> null
        }
    }

    fun missesATexture(
        preset: File,
        textureDir: File,
    ): Boolean {
        val text = runCatching { MilkAssetAdmission.readPresetText(preset) }.getOrDefault("")
        if (text.isEmpty()) return false
        val available =
            textureDir
                .listFiles()
                .orEmpty()
                .map { it.nameWithoutExtension.lowercase() }
                .toSet()
        // The sampler grammar lives in MilkTextureLinks so the importer and the linker can
        // never drift apart on what counts as a texture reference.
        return MilkTextureLinks.SAMPLER_REFERENCE
            .findAll(text)
            .map { it.groupValues[1].lowercase() }
            .filterNot { it in MilkTextureLinks.BUILTIN_SAMPLERS }
            .filterNot { it.startsWith("rand") }
            .any { it !in available }
    }
}
