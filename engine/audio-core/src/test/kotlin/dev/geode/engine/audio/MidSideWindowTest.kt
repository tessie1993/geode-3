package dev.geode.engine.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MidSideWindowTest {
    @Test
    fun `a completed window is delivered only once to each consumer`() {
        val ring = SampleRing(16, 2, 8)
        val first = MidSideWindow(ring, 4)
        val second = MidSideWindow(ring, 4)
        assertFalse(first.refresh())
        ring.write(floatArrayOf(1f, 1f, 2f, 2f, 3f, 3f, 4f, 4f), 4, 2)
        assertTrue(first.refresh())
        assertTrue(second.refresh())
        assertFalse(first.refresh())
        assertEquals(4L, first.position?.frames)
        ring.write(floatArrayOf(5f, 5f), 1, 2)
        assertTrue(first.refresh())
        assertArrayEquals(floatArrayOf(2f, 3f, 4f, 5f), first.mid, 0f)
    }

    @Test
    fun `epoch change cannot reuse a partial old window at the same cursor`() {
        val ring = SampleRing(16, 2, 8)
        val window = MidSideWindow(ring, 4)
        ring.write(FloatArray(8) { 1f }, 4, 2)
        assertTrue(window.refresh())
        val oldEpoch = window.position?.epoch
        ring.beginEpoch()
        assertFalse(window.refresh())
        ring.write(FloatArray(4), 2, 2)
        assertFalse(window.refresh())
        ring.write(FloatArray(4), 2, 2)
        assertTrue(window.refresh())
        assertTrue(oldEpoch != window.position?.epoch)
        assertArrayEquals(FloatArray(4), window.mid, 0f)
    }

    @Test
    fun `stereo side and mono format come from the same snapshot`() {
        val ring = SampleRing(16, 2, 8)
        val window = MidSideWindow(ring, 4)
        ring.write(floatArrayOf(1f, -1f, 1f, -1f, 1f, -1f, 1f, -1f), 4, 2)
        assertTrue(window.refresh())
        assertArrayEquals(FloatArray(4), window.mid, 0f)
        assertArrayEquals(FloatArray(4) { 1f }, window.side, 0f)
        ring.beginEpoch()
        ring.write(FloatArray(4) { 0.5f }, 4, 1)
        assertTrue(window.refresh())
        assertArrayEquals(FloatArray(4) { 0.5f }, window.mid, 0f)
        assertArrayEquals(FloatArray(4), window.side, 0f)
    }
}
