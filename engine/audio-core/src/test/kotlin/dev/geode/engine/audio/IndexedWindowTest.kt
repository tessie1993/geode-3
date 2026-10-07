package dev.geode.engine.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexedWindowTest {
    @Test
    fun `indexed snapshot reads requested range and exposes overwrite or stale epoch`() {
        val ring = SampleRing(capacityFrames = 16, channelCount = 2, maxWriteFrames = 4)
        repeat(4) { block ->
            ring.write(FloatArray(4) { (block * 4 + it).toFloat() }, 4, 1)
        }
        val out = Array(2) { FloatArray(4) }
        val epoch = ring.epoch
        assertEquals(SampleRing.WindowRead.OK, ring.snapshotWindow(4, epoch, out))
        assertArrayEquals(floatArrayOf(4f, 5f, 6f, 7f), out[0], 0f)
        assertEquals(SampleRing.WindowRead.WAITING, ring.snapshotWindow(14, epoch, out))
        ring.write(FloatArray(4) { 99f }, 4, 1)
        assertEquals(SampleRing.WindowRead.GAP, ring.snapshotWindow(0, epoch, out))
        ring.beginEpoch()
        assertEquals(SampleRing.WindowRead.DISCONTINUITY, ring.snapshotWindow(4, epoch, out))
    }

    @Test
    fun `mid side window copies old complete endpoint once then fresh stereo atomically`() {
        val ring = SampleRing(capacityFrames = 16, channelCount = 2, maxWriteFrames = 4)
        val window = MidSideWindow(ring, 4)
        ring.write(floatArrayOf(1f, 2f, 3f, 4f), 4, 1)
        ring.write(floatArrayOf(5f, 6f, 7f, 8f), 4, 1)
        assertEquals(SampleRing.WindowRead.OK, window.refreshAt(4, ring.epoch))
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f), window.mid, 0f)
        assertEquals(SampleRing.WindowRead.WAITING, window.refreshAt(4, ring.epoch))
        val oldEpoch = ring.epoch
        ring.beginEpoch()
        ring.write(floatArrayOf(0.75f, 0.25f, 0.75f, 0.25f, 0.75f, 0.25f, 0.75f, 0.25f), 4, 2)
        assertEquals(SampleRing.WindowRead.DISCONTINUITY, window.refreshAt(4, oldEpoch))
        assertTrue(window.epoch != oldEpoch)
        assertEquals(SampleRing.WindowRead.OK, window.refreshAt(4, ring.epoch))
        assertArrayEquals(FloatArray(4) { 0.5f }, window.mid, 0f)
        assertArrayEquals(FloatArray(4) { 0.25f }, window.side, 0f)
    }
}
