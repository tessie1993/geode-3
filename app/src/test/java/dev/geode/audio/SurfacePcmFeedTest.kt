package dev.geode.audio

import dev.geode.render.scene.PcmChunk
import dev.geode.wallpaper.IdleFeatures
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SurfacePcmFeedTest {
    @Test
    fun `wallpaper preview and dream independently receive actual capture PCM`() {
        val registry = PcmSourceRegistry()
        val ring = PcmRingBuffer(64)
        registry.attach(ring)
        val feeds = List(3) { registry.createFeed().also { it.start() } }
        // The actual producer ring downmixes these stereo capture frames.
        ring.writeInterleaved(floatArrayOf(0.2f, 0.4f, -0.8f, 0.4f), 2, 2)
        feeds.forEach { feed ->
            assertArrayEquals(floatArrayOf(0.3f, -0.2f), samples(feed.read()), EPSILON)
            assertNull(feed.read())
        }
        assertEquals(0L, ring.lastCopyEndIndex)
        feeds.forEach(SurfacePcmFeed::stop)
    }

    @Test
    fun `different render cadences do not consume another surfaces samples`() {
        val ring = PcmRingBuffer(64)
        val wallpaper = SurfacePcmFeed { ring }
        val dream = SurfacePcmFeed { ring }
        wallpaper.start()
        dream.start()
        ring.writeInterleaved(floatArrayOf(0.1f, 0.2f), 2, 1)
        assertArrayEquals(floatArrayOf(0.1f, 0.2f), samples(wallpaper.read()), EPSILON)
        ring.writeInterleaved(floatArrayOf(0.3f), 1, 1)
        assertArrayEquals(floatArrayOf(0.1f, 0.2f, 0.3f), samples(dream.read()), EPSILON)
        assertArrayEquals(floatArrayOf(0.3f), samples(wallpaper.read()), EPSILON)
        assertNull(dream.read())
        assertNull(wallpaper.read())
    }

    @Test
    fun `a captured end index cannot be overwritten by another reader`() {
        val ring = PcmRingBuffer(64)
        val first = FloatArray(8)
        ring.writeInterleaved(floatArrayOf(0.1f, 0.2f), 2, 1)
        val snapshot = checkNotNull(ring.readNewSince(0, first))
        ring.writeInterleaved(floatArrayOf(0.3f, 0.4f), 2, 1)
        // The legacy Activity reader still uses the side-channel API.
        ring.copyNewSince(0, FloatArray(8))
        assertEquals(4L, ring.lastCopyEndIndex)
        assertEquals(2L, snapshot.endIndex)
        assertArrayEquals(floatArrayOf(0.1f, 0.2f), first.copyOf(snapshot.count), EPSILON)
        val next = FloatArray(8)
        val later = checkNotNull(ring.readNewSince(snapshot.endIndex, next))
        assertArrayEquals(floatArrayOf(0.3f, 0.4f), next.copyOf(later.count), EPSILON)
    }

    @Test
    fun `stop and resume skip retained audio and invalidate late idle producers`() {
        val ring = PcmRingBuffer(64)
        val feed = SurfacePcmFeed { ring }
        val oldGeneration = feed.start()
        ring.writeInterleaved(floatArrayOf(0.8f), 1, 1)
        feed.stop()
        assertNull(feed.read())
        ring.writeInterleaved(floatArrayOf(0.9f), 1, 1)
        val currentGeneration = feed.start()
        feed.publishIdle(oldGeneration, floatArrayOf(0.7f))
        assertNull(feed.read())
        feed.publishIdle(currentGeneration, floatArrayOf(0.05f))
        assertArrayEquals(floatArrayOf(0.05f), samples(feed.read()), EPSILON)
        assertNull(feed.read())
        ring.writeInterleaved(floatArrayOf(0.4f), 1, 1)
        assertArrayEquals(floatArrayOf(0.4f), samples(feed.read()), EPSILON)
    }

    @Test
    fun `old playback release cannot unregister a replacement source`() {
        val registry = PcmSourceRegistry()
        val original = PcmRingBuffer(64)
        val replacement = PcmRingBuffer(64)
        registry.attach(original)
        val feed = registry.createFeed()
        feed.start()
        original.writeInterleaved(floatArrayOf(0.8f), 1, 1)
        replacement.writeInterleaved(floatArrayOf(0.9f), 1, 1)
        registry.attach(replacement)
        registry.detach(original)
        assertNull(feed.read()) // Drop both the old source and pre-attachment history.
        replacement.writeInterleaved(floatArrayOf(0.3f), 1, 1)
        assertArrayEquals(floatArrayOf(0.3f), samples(feed.read()), EPSILON)
        registry.detach(replacement)
        replacement.writeInterleaved(floatArrayOf(0.6f), 1, 1)
        assertNull(feed.read())
    }

    @Test
    fun `idle generative waveforms are delivered once without a player session`() {
        val feed = PcmSourceRegistry().createFeed()
        val generation = feed.start()
        val idle = IdleFeatures()
        val first = idle.tick(0.016f)
        feed.publishIdle(generation, first.waveform)
        assertArrayEquals(first.waveform, samples(feed.read()), EPSILON)
        assertNull(feed.read())
        val next = idle.tick(0.016f)
        feed.publishIdle(generation, next.waveform)
        assertArrayEquals(next.waveform, samples(feed.read()), EPSILON)
        assertTrue(!first.waveform.contentEquals(next.waveform))
        assertNull(feed.read())
    }

    @Test
    fun `real capture wins over pending idle and music selection clears idle`() {
        val ring = PcmRingBuffer(64)
        val feed = SurfacePcmFeed { ring }
        val generation = feed.start()
        feed.publishIdle(generation, floatArrayOf(0.05f))
        ring.writeInterleaved(floatArrayOf(0.6f), 1, 1)
        assertArrayEquals(floatArrayOf(0.6f), samples(feed.read()), EPSILON)
        assertNull(feed.read())
        feed.publishIdle(generation, floatArrayOf(0.04f))
        feed.publishIdle(generation, null)
        assertNull(feed.read())
    }

    @Test(timeout = 10_000)
    fun `accepted concurrent ring snapshots match their returned sample positions`() {
        val ring = PcmRingBuffer(1024)
        val start = CountDownLatch(1)
        val finished = AtomicBoolean(false)
        val writer =
            Thread {
                start.await()
                try {
                    repeat(1000) { block ->
                        ring.writeInterleaved(FloatArray(16) { block * 16f + it }, 16, 1)
                    }
                } finally {
                    finished.set(true)
                }
            }
        writer.start()
        start.countDown()
        val scratch = FloatArray(64)
        var cursor = 0L
        var accepted = 0
        try {
            while (!finished.get()) {
                ring.readNewSince(cursor, scratch)?.let { read ->
                    assertPositionedSamples(read, scratch)
                    cursor = read.endIndex
                    accepted += read.count
                }
            }
            writer.join(TimeUnit.SECONDS.toMillis(2))
            assertTrue(!writer.isAlive)
            val last = checkNotNull(ring.readNewSince(cursor, scratch))
            assertPositionedSamples(last, scratch)
            accepted += last.count
            assertTrue(accepted > 0)
        } finally {
            writer.join(TimeUnit.SECONDS.toMillis(2))
        }
    }

    private fun assertPositionedSamples(
        read: PcmRingBuffer.Read,
        samples: FloatArray,
    ) {
        repeat(read.count) { offset ->
            assertEquals((read.endIndex - read.count + offset).toFloat(), samples[offset], 0f)
        }
    }

    private fun samples(chunk: PcmChunk?): FloatArray {
        val present = checkNotNull(chunk) { "expected fresh surface PCM" }
        return present.data.copyOf(present.count)
    }

    private companion object {
        const val EPSILON = 1e-6f
    }
}
