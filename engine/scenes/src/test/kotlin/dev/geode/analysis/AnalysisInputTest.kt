package dev.geode.analysis

import dev.geode.engine.audio.SampleRing
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisInputTest {
    private val ring = SampleRing(capacityFrames = 4096, channelCount = 2, maxWriteFrames = 2048)
    private val input = AnalysisInput(ring, windowFrames = 1024)

    @Test
    fun `normal chunk gaps wait but a stalled input becomes silent`() {
        ring.write(FloatArray(1024) { 1f }, 1024, 1)
        assertEquals(AnalysisInput.State.FRESH, input.poll(0, 16_000))
        assertEquals(AnalysisInput.State.WAITING, input.poll(16_000_000, 16_000))
        assertEquals(AnalysisInput.State.WAITING, input.poll(64_000_000, 16_000))
        assertEquals(AnalysisInput.State.SILENT, input.poll(128_000_000, 16_000))
        assertFalse(input.discontinuity)
        ring.write(FloatArray(1024) { 0.5f }, 1024, 1)
        assertEquals(AnalysisInput.State.FRESH, input.poll(144_000_000, 16_000))
    }

    @Test
    fun `same rate reconnect is immediately silent and waits for a complete fresh window`() {
        ring.write(FloatArray(1024) { 1f }, 1024, 1)
        assertEquals(AnalysisInput.State.FRESH, input.poll(0, 48_000))
        ring.beginEpoch()
        assertEquals(AnalysisInput.State.SILENT, input.poll(16_000_000, 48_000))
        assertTrue(input.discontinuity)
        ring.write(FloatArray(512) { 0.25f }, 512, 1)
        assertEquals(AnalysisInput.State.SILENT, input.poll(32_000_000, 48_000))
        ring.write(FloatArray(512) { 0.25f }, 512, 1)
        assertEquals(AnalysisInput.State.FRESH, input.poll(48_000_000, 48_000))
        assertArrayEquals(FloatArray(1024) { 0.25f }, input.window.mid, 0f)
        assertArrayEquals(FloatArray(1024), input.window.side, 0f)
    }

    @Test
    fun `new format window arriving between ticks still carries the discontinuity`() {
        ring.write(FloatArray(1024) { 1f }, 1024, 1)
        input.poll(0, 48_000)
        ring.beginEpoch()
        ring.write(FloatArray(2048) { if (it % 2 == 0) 0.75f else 0.25f }, 1024, 2)
        assertEquals(AnalysisInput.State.FRESH, input.poll(16_000_000, 44_100))
        assertTrue(input.discontinuity)
        assertArrayEquals(FloatArray(1024) { 0.5f }, input.window.mid, 0f)
        assertArrayEquals(FloatArray(1024) { 0.25f }, input.window.side, 0f)
    }
}
