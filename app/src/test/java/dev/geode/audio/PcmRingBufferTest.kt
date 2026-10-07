package dev.geode.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmRingBufferTest {
    @Test
    fun `capture boundary keeps renderer cursor monotonic and excludes old samples`() {
        val ring = PcmRingBuffer(16)
        val output = FloatArray(4)
        ring.write(floatArrayOf(1f, 1f, 1f, 1f), 4, 1)
        assertEquals(4, ring.copyNewSince(0, output))
        val cursor = ring.lastCopyEndIndex
        ring.discontinuity()
        assertFalse(ring.snapshotLatest(output))
        assertEquals(0, ring.copyNewSince(cursor, output))
        ring.write(floatArrayOf(0.5f, 0.5f), 2, 1)
        assertFalse(ring.snapshotLatest(output))
        assertEquals(2, ring.copyNewSince(0, output))
        assertArrayEquals(floatArrayOf(0.5f, 0.5f), output.take(2).toFloatArray(), 0f)
        ring.write(floatArrayOf(0.25f, 0.25f), 2, 1)
        assertTrue(ring.snapshotLatest(output))
        assertArrayEquals(floatArrayOf(0.5f, 0.5f, 0.25f, 0.25f), output, 0f)
        assertEquals(8L, ring.currentWriteIndex())
    }
}
