package dev.geode.audio

import dev.geode.render.scene.PcmChunk
import java.util.concurrent.atomic.AtomicReference

/** Observes the active playback/capture ring without creating or retaining a player session. */
internal class PcmSourceRegistry {
    private val active = AtomicReference<PcmRingBuffer?>(null)

    fun attach(ring: PcmRingBuffer) {
        active.set(ring)
    }

    fun detach(ring: PcmRingBuffer) {
        active.compareAndSet(ring, null)
    }

    fun createFeed(): SurfacePcmFeed = SurfacePcmFeed(active::get)
}

/**
 * A wallpaper, preview or dream owns one feed and one cursor. Start/source
 * replacement starts at the current head so old retained music is not replayed.
 * Lifecycle/idle publication runs off the GL thread; read runs on the GL thread.
 * The returned chunk is borrowed until the next read and is copied into native
 * memory immediately by VisualizerRenderer.
 */
internal class SurfacePcmFeed(
    private val source: () -> PcmRingBuffer?,
) {
    private val scratch = FloatArray(PCM_FRAMES)
    private var ring: PcmRingBuffer? = null
    private var cursor = 0L
    private var generation = 0L
    private var active = false
    private var pendingIdle: FloatArray? = null

    @Synchronized
    fun start(): Long {
        generation++
        active = true
        ring = source()
        cursor = ring?.currentWriteIndex() ?: 0L
        pendingIdle = null
        return generation
    }

    @Synchronized
    fun stop() {
        generation++
        active = false
        ring = null
        cursor = 0L
        pendingIdle = null
    }

    /** Null selects real music features; a waveform is one freshly generated idle observation. */
    @Synchronized
    fun publishIdle(
        owner: Long,
        waveform: FloatArray?,
    ) {
        if (active && owner == generation) pendingIdle = waveform?.copyOf()
    }

    @Synchronized
    fun read(): PcmChunk? {
        if (!active) return null
        val current = source()
        if (current !== ring) {
            ring = current
            cursor = current?.currentWriteIndex() ?: 0L
            pendingIdle = null
        }
        val read = current?.readNewSince(cursor, scratch)
        if (read != null) cursor = read.endIndex
        val idle = pendingIdle
        pendingIdle = null
        return when {
            read != null && read.count > 0 -> PcmChunk(scratch, read.count)
            idle != null && idle.isNotEmpty() -> PcmChunk(idle, idle.size)
            else -> null
        }
    }

    private companion object {
        const val PCM_FRAMES = 4096
    }
}
