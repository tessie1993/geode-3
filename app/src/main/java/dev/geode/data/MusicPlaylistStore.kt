package dev.geode.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class MusicPlaylist(
    val name: String,
    val trackUris: List<String> = emptyList(),
)

class MusicPlaylistStore(
    context: Context,
) {
    private val dir = File(context.filesDir, "music-playlists").apply { mkdirs() }

    init {
        synchronized(writeLock) { migrateLegacyFileNames() }
    }

    private fun migrateLegacyFileNames() {
        dir
            .listFiles { f -> f.extension == "json" }
            .orEmpty()
            .forEach { f ->
                val name = runCatching { fromJson(f.readText()).name }.getOrNull() ?: return@forEach
                val stem = sanitize(name)
                if (f.nameWithoutExtension == stem) return@forEach
                val target = File(dir, "$stem.json")
                if (!target.exists()) f.renameTo(target)
            }
    }

    fun list(): List<MusicPlaylist> =
        dir
            .listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { fromJson(it.readText()) }.getOrNull() }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    fun save(playlist: MusicPlaylist): Boolean =
        synchronized(writeLock) {
            runCatching { AtomicWrite.text(fileOf(playlist.name), toJson(playlist)) }.getOrDefault(false)
        }

    /** Name reservation and durable publication are one transaction, including across store instances. */
    fun saveUnique(playlist: MusicPlaylist): MusicPlaylist? =
        synchronized(writeLock) {
            val base = playlist.name.trim().ifBlank { "Playlist" }.take(MAX_IMPORT_NAME_LENGTH)
            var name = base
            var suffix = 2
            val names = list().map { it.name }.toSet()
            while (name in names || fileOf(name).exists()) name = "$base (${suffix++})"
            val unique = playlist.copy(name = name)
            if (save(unique)) unique else null
        }

    fun delete(name: String) {
        synchronized(writeLock) { fileOf(name).delete() }
    }

    fun addTrack(
        name: String,
        uri: String,
    ): MusicPlaylist {
        val current = current(name) ?: return MusicPlaylist(name)
        val updated =
            if (current.trackUris.contains(uri)) current else current.copy(trackUris = current.trackUris + uri)
        save(updated)
        return updated
    }

    fun rename(
        oldName: String,
        newName: String,
    ): Boolean =
        synchronized(writeLock) {
            val current = list().firstOrNull { it.name == oldName } ?: return@synchronized false
            if (newName.isBlank() || list().any { it.name == newName }) return@synchronized false
            if (!AtomicWrite.text(fileOf(newName), toJson(current.copy(name = newName)))) return@synchronized false
            if (sanitize(oldName) != sanitize(newName)) delete(oldName)
            true
        }

    fun move(
        name: String,
        from: Int,
        to: Int,
    ): MusicPlaylist {
        val current = current(name) ?: return MusicPlaylist(name)
        val uris = current.trackUris.toMutableList()
        if (from !in uris.indices) return current
        val target = to.coerceIn(0, uris.size - 1)
        val item = uris.removeAt(from)
        uris.add(target, item)
        val updated = current.copy(trackUris = uris)
        save(updated)
        return updated
    }

    fun removeTrack(
        name: String,
        uri: String,
    ): MusicPlaylist {
        val current = current(name) ?: return MusicPlaylist(name)
        val updated = current.copy(trackUris = current.trackUris.filterNot { it == uri })
        save(updated)
        return updated
    }

    private companion object {
        val writeLock = Any()
        // safeFileName produces ASCII; leave filesystem room for its hash and numbered copies.
        const val MAX_IMPORT_NAME_LENGTH = 160
    }

    private fun current(name: String): MusicPlaylist? {
        list().firstOrNull { it.name == name }?.let { return it }
        return if (readable(fileOf(name))) MusicPlaylist(name) else null
    }

    private fun readable(f: File): Boolean = !f.exists() || runCatching { fromJson(f.readText()) }.isSuccess

    private fun fileOf(name: String): File = File(dir, sanitize(name) + ".json")

    private fun sanitize(name: String): String = PresetStore.safeFileName(name)

    private fun toJson(p: MusicPlaylist): String {
        val arr = JSONArray()
        p.trackUris.forEach { arr.put(it) }
        return JSONObject().put("name", p.name).put("tracks", arr).toString()
    }

    private fun fromJson(text: String): MusicPlaylist {
        val o = JSONObject(text)
        val arr = o.getJSONArray("tracks")
        return MusicPlaylist(
            name = o.getString("name"),
            trackUris = (0 until arr.length()).map { arr.getString(it) },
        )
    }
}
