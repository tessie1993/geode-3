package dev.geode.data

import android.content.Context
import androidx.annotation.WorkerThread
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class TakeInfo(
    val name: String,
    val durationMs: Long,
    val eventCount: Int,
    val trackUri: String?,
    val sizeBytes: Long,
)

sealed interface TakeWrite {
    data class Saved(
        val name: String,
    ) : TakeWrite

    data object Failed : TakeWrite
}

class TakeStore internal constructor(
    private val dir: File,
    private val writeText: (File, String) -> Boolean = AtomicWrite::text,
) {
    constructor(context: Context) : this(File(context.filesDir, "takes"))

    private val directoryLock = locks.computeIfAbsent(dir.absolutePath) { Any() }

    init {
        synchronized(directoryLock) {
            dir.mkdirs()
            migrateLegacyFileNames()
        }
    }

    private fun migrateLegacyFileNames() {
        dir
            .listFiles { f -> f.isFile && f.extension == "json" }
            .orEmpty()
            .forEach { f ->
                val name = runCatching { PerformanceTake.Timeline(f.readText()).name }.getOrNull() ?: return@forEach
                val stem = PresetStore.safeFileName(name)
                if (f.nameWithoutExtension == stem) return@forEach
                val target = File(dir, "$stem.json")
                if (!target.exists()) f.renameTo(target)
            }
    }

    private fun fileOf(name: String): File = File(dir, PresetStore.safeFileName(name) + ".json")

    @WorkerThread
    fun list(): List<TakeInfo> =
        dir
            .listFiles { f -> f.isFile && f.extension == "json" }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .mapNotNull { f ->
                runCatching {
                    val t = PerformanceTake.Timeline(f.readText())
                    TakeInfo(t.name, t.durationMs, t.eventCount, t.trackUri, f.length())
                }.getOrNull()
            }

    @WorkerThread
    fun load(name: String): PerformanceTake.Timeline? =
        runCatching { PerformanceTake.Timeline(fileOf(name).readText()) }
            .onFailure { dev.geode.RingLog.note("TakeStore", "take failed to load: $name", it) }
            .getOrNull()

    @WorkerThread
    fun save(
        name: String,
        json: String,
    ): TakeWrite =
        synchronized(directoryLock) {
            val requested = name.trim()
            if (requested.isEmpty()) return@synchronized TakeWrite.Failed
            var candidate = requested
            var n = 2
            while (fileOf(candidate).exists()) {
                candidate = "$requested $n"
                n++
            }
            val body =
                runCatching {
                    org.json
                        .JSONObject(json)
                        .put("name", candidate)
                        .toString()
                }.getOrNull() ?: return@synchronized TakeWrite.Failed
            if (!runCatching { writeText(fileOf(candidate), body) }.getOrDefault(false)) {
                return@synchronized TakeWrite.Failed
            }
            TakeWrite.Saved(candidate)
        }

    @WorkerThread
    fun delete(name: String) {
        synchronized(directoryLock) { fileOf(name).delete() }
    }

    fun rename(
        from: String,
        to: String,
    ): Boolean =
        synchronized(directoryLock) {
            renameLocked(from, to)
        }

    private fun renameLocked(
        from: String,
        to: String,
    ): Boolean {
        val target = to.trim()
        val src = fileOf(from)
        if (!src.isFile || target.isEmpty()) return false
        val dest = fileOf(target)
        if (dest.exists()) return false
        val updated =
            runCatching {
                org.json
                    .JSONObject(src.readText())
                    .put("name", target)
                    .toString()
            }.getOrNull() ?: return false
        if (!writeText(dest, updated)) return false
        src.delete()
        return true
    }

    private companion object {
        val locks = ConcurrentHashMap<String, Any>()
    }
}
