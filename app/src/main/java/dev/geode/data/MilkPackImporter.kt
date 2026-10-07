package dev.geode.data

import java.io.File
import java.io.InputStream

object MilkPackImporter {
    class Entry(
        val name: String,
        val open: () -> InputStream?,
    )

    data class Report(
        val presets: Int,
        val textures: Int,
        val skipped: Int,
        val presetsMissingTextures: Int,
        val outcomes: List<Outcome> = emptyList(),
    ) {
        val total: Int get() = presets + textures
    }

    data class Outcome(
        val name: String,
        val imported: Boolean,
        val reason: String?,
    )

    /** Admit the entire pack before publishing any files; never overwrite an existing asset. */
    @Suppress("TooGenericExceptionCaught")
    fun import(
        entries: List<Entry>,
        milkDir: File,
    ): Report =
        synchronized(MilkAssetAdmission) {
            if (entries.size > MilkAssetAdmission.MAX_ENTRIES) {
                return@synchronized Report(0, 0, entries.size, 0, listOf(Outcome("folder", false, "too many entries")))
            }
            val textureDir = File(milkDir, "textures")
            milkDir.mkdirs()
            textureDir.mkdirs()
            val outcomes = mutableListOf<Outcome>()
            val staged = mutableListOf<Pair<File, File>>()
            val published = mutableListOf<File>()
            val budget = MilkAssetAdmission.Budget()
            val staging =
                runCatching { java.nio.file.Files.createTempDirectory(milkDir.toPath(), ".import-").toFile() }
                    .getOrElse {
                        return@synchronized Report(0, 0, entries.size, 0, listOf(Outcome("folder", false, "could not stage folder")))
                    }
            try {
                for (entry in entries) {
                    val extension = entry.name.substringAfterLast('.', "").lowercase()
                    val target = targetFor(entry.name, extension, milkDir, textureDir)
                    if (target == null || target.exists()) {
                        outcomes += Outcome(entry.name, false, if (target == null) "unsupported type" else "already exists")
                        continue
                    }
                    require(staged.none { it.second == target }) { "duplicate asset name: ${entry.name}" }
                    val file = File(staging, staged.size.toString())
                    requireNotNull(entry.open()) { "could not read ${entry.name}" }.use { input ->
                        MilkAssetAdmission.stage(input, file, extension, budget)
                    }
                    staged += file to target
                }
                for ((file, target) in staged) {
                    // createNewFile is an exclusive claim: a concurrent picker save wins safely.
                    check(target.createNewFile()) { "asset appeared during import: ${target.name}" }
                    published += target
                    check(AtomicWrite.stream(target) { out -> file.inputStream().use { it.copyTo(out) } }) {
                        "could not publish ${target.name}"
                    }
                }
                val presets = published.filter { it.extension == "milk" }
                outcomes += published.map { Outcome(it.name, true, null) }
                Report(
                    presets.size,
                    published.size - presets.size,
                    outcomes.count { !it.imported },
                    presets.count { missesATexture(it, textureDir) },
                    outcomes,
                )
            } catch (error: Exception) {
                published.forEach { it.delete() }
                Report(0, 0, entries.size, 0, entries.map { Outcome(it.name, false, error.message ?: "pack rejected") })
            } finally {
                staging.deleteRecursively()
            }
        }

    private fun targetFor(
        name: String,
        extension: String,
        milkDir: File,
        textureDir: File,
    ): File? {
        val leaf = name.substringAfterLast('/').substringAfterLast('\\')
        require(leaf.length <= 240 && leaf != "." && leaf != ".." && '\u0000' !in leaf) { "invalid asset name" }
        return when {
            extension == "milk" -> File(milkDir, PresetStore.milkFileName(leaf))
            extension in MilkAssetAdmission.textureExtensions -> File(textureDir, leaf)
            else -> null
        }
    }

    fun missesATexture(
        preset: File,
        textureDir: File,
    ): Boolean {
        val text = runCatching { preset.readText() }.getOrDefault("")
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
