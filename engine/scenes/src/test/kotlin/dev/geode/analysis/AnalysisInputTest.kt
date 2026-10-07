package dev.geode.analysis

import dev.geode.engine.audio.SampleRing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisInputTest {
    @Test
    fun `restart of the real worker cannot reanalyze its retained PCM`() =
        runTest {
            val ring = SampleRing(16, 2, 8)
            val input = AnalysisInput(ring, 4, 44100)
            var window: AnalysisInput.Window? = null
            var clears = 0
            val worker =
                AnalysisWorker(
                    dispatcher = StandardTestDispatcher(testScheduler),
                    loop = {
                        window = input.openWindow { clears++ }
                        awaitCancellation()
                    },
                    release = {},
                )
            try {
                worker.start(this)
                runCurrent()
                ring.write(FloatArray(8) { 1f }, 4, 2)
                assertNotNull(checkNotNull(window).refresh())
                worker.stop()
                worker.start(this)
                runCurrent()
                assertEquals(2, clears)
                assertNull("restart must wait for new PCM", checkNotNull(window).refresh())
                assertNull(checkNotNull(window).refresh())

                ring.write(FloatArray(6), 3, 2)
                assertNull("partly retained FFT window is still stale", checkNotNull(window).refresh())
                ring.write(FloatArray(2), 1, 2)
                val next = checkNotNull(checkNotNull(window).refresh())
                assertArrayEquals(FloatArray(4), next.mid, 0f)
                assertArrayEquals(FloatArray(4), next.side, 0f)
            } finally {
                worker.close().await()
            }
        }

    @Test
    fun `first run also discards PCM captured before the consumer opened`() {
        val ring = SampleRing(16, 2, 8)
        ring.write(FloatArray(8) { 1f }, 4, 2)
        val input = AnalysisInput(ring, 4, 44100)
        val window = input.openWindow {}
        assertNull(window.refresh())
        ring.write(floatArrayOf(1f, -1f, 1f, -1f, 1f, -1f, 1f, -1f), 4, 2)
        val frame = checkNotNull(window.refresh())
        assertArrayEquals(FloatArray(4), frame.mid, 0f)
        assertArrayEquals(FloatArray(4) { 1f }, frame.side, 0f)
    }

    @Test
    fun `rate change excludes both retained and partly replaced old rate windows`() {
        val ring = SampleRing(16, 2, 8)
        val input = AnalysisInput(ring, 4, 44100)
        val window = input.openWindow {}
        ring.write(FloatArray(8) { 1f }, 4, 2)
        val old = checkNotNull(window.refresh())
        var publishedRate = old.sampleRateHz

        input.setSampleRate(48000) { publishedRate = 0 }
        assertFalse(input.publishIfCurrent(old) { publishedRate = old.sampleRateHz })
        assertEquals(0, publishedRate)
        assertNull(window.refresh())
        ring.write(FloatArray(4), 2, 2)
        assertNull(window.refresh())
        ring.write(FloatArray(4), 2, 2)
        val next = checkNotNull(window.refresh())
        assertEquals(48000, next.sampleRateHz)
        assertArrayEquals(FloatArray(4), next.mid, 0f)
        assertTrue(input.publishIfCurrent(next) { publishedRate = next.sampleRateHz })
        assertEquals(48000, publishedRate)
    }

    @Test
    fun `rate update during native work cannot change that windows configuration or publish it`() {
        val ring = SampleRing(16, 2, 8)
        val input = AnalysisInput(ring, 4, 44100)
        val window = input.openWindow {}
        ring.write(FloatArray(8) { 1f }, 4, 2)
        val inFlight = checkNotNull(window.refresh())
        val configuredRate = inFlight.sampleRateHz

        input.setSampleRate(48000) {}
        assertEquals(44100, inFlight.sampleRateHz)
        assertEquals(configuredRate, inFlight.sampleRateHz)
        assertFalse(input.isCurrent(inFlight))
        assertFalse(input.publishIfCurrent(inFlight) { error("stale FFT must not publish") })

        // A rapid A -> B -> A change must not make the old frame valid again.
        input.setSampleRate(44100) {}
        assertFalse(input.isCurrent(inFlight))
        ring.write(FloatArray(8), 4, 2)
        val fresh = checkNotNull(window.refresh())
        assertEquals(44100, fresh.sampleRateHz)
        assertTrue(input.isCurrent(fresh))
    }

    @Test
    fun `reset invalidates work already in flight and demands a whole new window`() {
        val ring = SampleRing(16, 2, 8)
        val input = AnalysisInput(ring, 4, 44100)
        val window = input.openWindow {}
        ring.write(FloatArray(8) { 1f }, 4, 2)
        val inFlight = checkNotNull(window.refresh())
        var published = true
        input.reset { published = false }
        assertFalse(input.publishIfCurrent(inFlight) { published = true })
        assertFalse(published)
        assertNull(window.refresh())
        ring.write(FloatArray(8), 4, 2)
        assertTrue(input.publishIfCurrent(checkNotNull(window.refresh())) { published = true })
        assertTrue(published)
    }

    @Test
    fun `new source epoch starts at zero without inheriting the previous frame threshold`() {
        val ring = SampleRing(16, 2, 8)
        val input = AnalysisInput(ring, 4, 44100)
        ring.write(FloatArray(16) { 1f }, 8, 2)
        val window = input.openWindow {}
        ring.beginEpoch()
        ring.write(FloatArray(2) { 0.25f }, 2, 1)
        assertNull(window.refresh())
        ring.write(FloatArray(2) { 0.25f }, 2, 1)
        val frame = checkNotNull(window.refresh())
        assertEquals(4L, frame.position.frames)
        assertArrayEquals(FloatArray(4) { 0.25f }, frame.mid, 0f)
        assertArrayEquals(FloatArray(4), frame.side, 0f)
        ring.beginEpoch()
        assertFalse(input.publishIfCurrent(frame) { error("previous source must not publish") })
    }
}
