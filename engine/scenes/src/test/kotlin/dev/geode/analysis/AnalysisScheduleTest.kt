package dev.geode.analysis

import dev.geode.engine.audio.SampleRing
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisScheduleTest {
    private data class Window(
        val endpoint: Long,
        val samples: FloatArray,
    )

    private fun drain(
        input: AnalysisInput,
        rateHz: Int,
        nowNs: Long = 0,
    ): List<Window> {
        val output = mutableListOf<Window>()
        repeat(AnalysisInput.MAX_HOPS_PER_WAKE) {
            if (input.poll(nowNs, rateHz) != AnalysisInput.State.FRESH) return output
            output += Window(input.window.endFrame, input.window.mid.copyOf())
        }
        return output
    }

    @Test
    fun `regular and batched decoder delivery analyze identical chronological windows within catchup bound`() {
        val source = FloatArray(2048 + 6 * 768) { index -> if (index == 2600) 1f else index / 10_000f }

        fun delivered(batchHops: Int): List<Window> {
            val ring = SampleRing(capacityFrames = 8192, channelCount = 2, maxWriteFrames = 4096)
            val input = AnalysisInput(ring, 2048)
            val frames = mutableListOf<Window>()
            var offset = 0

            fun write(count: Int) {
                ring.write(source.copyOfRange(offset, offset + count), count, 1)
                offset += count
                frames += drain(input, 48_000)
            }

            write(2048)
            repeat(6 / batchHops) { write(batchHops * 768) }
            return frames
        }

        val regular = delivered(batchHops = 1)
        val batched = delivered(batchHops = 3)
        assertEquals(7, regular.size)
        assertEquals(regular.map(Window::endpoint), batched.map(Window::endpoint))
        regular.zip(batched).forEach { (a, b) -> assertArrayEquals(a.samples, b.samples, 0f) }
        // The batch's early transient is present, rather than replaced with only its newest window.
        assertEquals(1f, batched[1].samples[2600 - 768], 0f)
    }

    @Test
    fun `unchanged PCM waits without returning a duplicate analyzable window`() {
        val ring = SampleRing(capacityFrames = 4096, channelCount = 2, maxWriteFrames = 1024)
        val input = AnalysisInput(ring, 1024)
        ring.write(FloatArray(1024) { 0.5f }, 1024, 1)
        assertEquals(1, drain(input, 16_000).size)
        repeat(3) { wake ->
            assertEquals(AnalysisInput.State.WAITING, input.poll((wake + 1) * 16_000_000L, 16_000))
        }
        assertEquals(1024L, input.window.endFrame)
        assertTrue(drain(input, 16_000, 64_000_000).isEmpty())
    }

    @Test
    fun `rational sample hops preserve 16 milliseconds at common sample rates`() {
        for (rate in listOf(16_000, 44_100, 48_000)) {
            val ring = SampleRing(capacityFrames = 8192, channelCount = 2, maxWriteFrames = 4096)
            val input = AnalysisInput(ring, 2048)
            ring.write(FloatArray(2048), 2048, 1)
            assertEquals(AnalysisInput.State.FRESH, input.poll(0, rate))
            var previous = input.window.endFrame
            val increments = mutableSetOf<Long>()
            repeat(125) { hop ->
                val expected = 2048L + (hop + 1) * rate.toLong() * 16 / 1000
                val delta = (expected - previous).toInt()
                ring.write(FloatArray(delta), delta, 1)
                assertEquals(AnalysisInput.State.FRESH, input.poll((hop + 1) * 16_000_000L, rate))
                assertEquals(expected, input.window.endFrame)
                assertEquals(delta.toFloat() / rate, input.dtSeconds, 1e-8f)
                increments += expected - previous
                previous = expected
            }
            assertEquals(rate * 2L, previous - 2048L)
            if (rate == 44_100) assertEquals(setOf(705L, 706L), increments)
        }
    }

    @Test
    fun `delayed same epoch reset discards old PCM and accepts already arrived fresh window`() {
        val ring = SampleRing(capacityFrames = 4096, channelCount = 2, maxWriteFrames = 1024)
        val input = AnalysisInput(ring, 1024)
        ring.write(FloatArray(1024) { 1f }, 1024, 1)
        assertEquals(AnalysisInput.State.FRESH, input.poll(0, 16_000))
        val marker = ring.position()
        ring.write(FloatArray(1024) { 0.25f }, 1024, 1)
        // The FFT snapshot can have been copied before FrameGate observes the
        // pending reset and rejects it. Reset must allow this endpoint again.
        assertEquals(SampleRing.WindowRead.OK, input.window.refreshAt(2048, marker.epoch))
        input.reset(marker, 16_000)

        assertEquals(AnalysisInput.State.FRESH, input.poll(16_000_000, 16_000))
        assertEquals(2048L, input.window.endFrame)
        assertArrayEquals(FloatArray(1024) { 0.25f }, input.window.mid, 0f)
    }

    @Test
    fun `explicit reset with no fresh window remains silent until complete input`() {
        val ring = SampleRing(capacityFrames = 4096, channelCount = 2, maxWriteFrames = 1024)
        val input = AnalysisInput(ring, 1024)
        ring.write(FloatArray(1024) { 1f }, 1024, 1)
        input.poll(0, 16_000)
        input.reset(ring.position(), 16_000)
        assertEquals(AnalysisInput.State.SILENT, input.poll(16_000_000, 16_000))
        ring.write(FloatArray(512) { 0.25f }, 512, 1)
        assertEquals(AnalysisInput.State.SILENT, input.poll(32_000_000, 16_000))
        ring.write(FloatArray(512) { 0.25f }, 512, 1)
        assertEquals(AnalysisInput.State.FRESH, input.poll(48_000_000, 16_000))
        assertArrayEquals(FloatArray(1024) { 0.25f }, input.window.mid, 0f)
    }

    @Test
    fun `backlog beyond four hops explicitly resets and drains only newest bounded windows`() {
        val ring = SampleRing(capacityFrames = 16_384, channelCount = 2, maxWriteFrames = 4096)
        val input = AnalysisInput(ring, 2048)
        repeat(2) { ring.write(FloatArray(4096) { 0.5f }, 4096, 1) }
        assertEquals(AnalysisInput.State.FRESH, input.poll(0, 48_000))
        assertTrue(input.discontinuity)
        assertEquals(5888L, input.window.endFrame)
        repeat(3) {
            assertEquals(AnalysisInput.State.FRESH, input.poll(0, 48_000))
            assertFalse(input.discontinuity)
        }
        assertEquals(8192L, input.window.endFrame)
        assertEquals(AnalysisInput.State.WAITING, input.poll(0, 48_000))
    }

    @Test
    fun `overwrite resync fits the retained full range even when ring holds one FFT window`() {
        val ring = SampleRing(capacityFrames = 1024, channelCount = 2, maxWriteFrames = 256)
        val input = AnalysisInput(ring, 1024)
        repeat(16) { ring.write(FloatArray(256) { 0.25f }, 256, 1) }
        assertEquals(AnalysisInput.State.FRESH, input.poll(0, 48_000))
        assertTrue(input.discontinuity)
        assertEquals(4096L, input.window.endFrame)
        assertArrayEquals(FloatArray(1024) { 0.25f }, input.window.mid, 0f)
        assertEquals(AnalysisInput.State.WAITING, input.poll(16_000_000, 48_000))
    }

    @Test
    fun `fractional grid cannot starve on a one window ring with 256 frame producer chunks`() {
        val ring = SampleRing(capacityFrames = 1024, channelCount = 2, maxWriteFrames = 256)
        val input = AnalysisInput(ring, 1024)
        repeat(16) { ring.write(FloatArray(256) { 0.25f }, 256, 1) }
        assertEquals(AnalysisInput.State.FRESH, input.poll(0, 44_100))
        assertTrue(input.discontinuity)
        assertEquals(4096L, input.window.endFrame)
        repeat(2) { wake ->
            repeat(3) { ring.write(FloatArray(256) { 0.25f }, 256, 1) }
            assertEquals(AnalysisInput.State.FRESH, input.poll((wake + 1) * 16_000_000L, 44_100))
            assertTrue(input.discontinuity)
            assertEquals(4096L + (wake + 1) * 768L, input.window.endFrame)
        }
    }
}
