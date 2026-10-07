package dev.geode.analysis

import dev.geode.engine.audio.SampleRing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisFrameGateTest {
    private val ring = SampleRing(capacityFrames = 16, channelCount = 2, maxWriteFrames = 4)
    private val input = AnalysisInput(ring, windowFrames = 4)
    private val publicationLock = Any()
    private var rateHz = 48_000
    private var resetPending = false
    private val gate =
        AnalysisFrameGate(publicationLock, { rateHz }) {
            !resetPending && ring.epoch == input.window.epoch
        }
    private var analyzed = 0
    private val published = mutableListOf<Float>()

    private fun snapshot(value: Float = 0.5f) {
        ring.write(FloatArray(4) { value }, 4, 1)
        assertEquals(AnalysisInput.State.FRESH, input.poll(0, rateHz))
    }

    private fun publishRate(value: Int) {
        synchronized(publicationLock) { rateHz = value }
    }

    private fun tick(configuredRateHz: Int): Boolean =
        gate.run(
            configuredRateHz,
            analyze = {
                analyzed++
                input.window.mid[0]
            },
            publish = { published += it },
        )

    @Test
    fun `rate changed after configuration before snapshot skips analysis then resumes at correct rate`() {
        val configuredRateHz = rateHz
        ring.beginEpoch()
        publishRate(44_100)
        snapshot()

        assertFalse(tick(configuredRateHz))
        assertEquals(0, analyzed)
        assertTrue(published.isEmpty())
        assertTrue(tick(rateHz))
        assertEquals(1, analyzed)
        assertEquals(listOf(0.5f), published)
    }

    @Test
    fun `rate changed after snapshot before analysis also rejects the configured rate`() {
        val configuredRateHz = rateHz
        snapshot()
        publishRate(44_100)

        assertFalse(tick(configuredRateHz))
        assertEquals(0, analyzed)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `rate changed during native work suppresses publication`() {
        val configuredRateHz = rateHz
        snapshot()
        val accepted =
            gate.run(
                configuredRateHz,
                analyze = {
                    analyzed++
                    publishRate(44_100)
                    input.window.mid[0]
                },
                publish = { published += it },
            )

        assertFalse(accepted)
        assertEquals(1, analyzed)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `same rate epoch changed during native work still suppresses publication`() {
        snapshot()
        val accepted =
            gate.run(
                rateHz,
                analyze = {
                    ring.beginEpoch()
                    input.window.mid[0]
                },
                publish = { published += it },
            )

        assertFalse(accepted)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `pending reset rejects even an otherwise matching frame`() {
        snapshot()
        resetPending = true

        assertFalse(tick(rateHz))
        assertEquals(0, analyzed)
        assertTrue(published.isEmpty())
    }
}
