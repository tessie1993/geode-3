package dev.geode.export

import java.io.File
import java.io.IOException
import java.util.UUID

/** Process-owned scratch: delayed startup cleanup cannot select this process's files. */
internal object RenderScratch {
    private const val DIRECTORY_PREFIX = "geode-render-"
    private val processDirectory = DIRECTORY_PREFIX + UUID.randomUUID().toString()
    private val legacyPrefixes = listOf("geode_aac_", "geode_loop_", "studio-")

    fun directory(cacheDir: File): File =
        File(cacheDir, processDirectory).also {
            if (!it.isDirectory && !it.mkdirs() && !it.isDirectory) {
                throw IOException("Could not create render scratch directory")
            }
        }

    fun sweepPreviousRuns(cacheDir: File) {
        cacheDir.listFiles().orEmpty().forEach { file ->
            when {
                file.name == processDirectory -> Unit
                file.isDirectory && file.name.startsWith(DIRECTORY_PREFIX) -> file.deleteRecursively()
                file.isFile && legacyPrefixes.any { file.name.startsWith(it) } -> file.delete()
            }
        }
    }
}
