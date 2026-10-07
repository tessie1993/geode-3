package dev.geode.audio

import dev.geode.engine.audio.PcmSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The pump is what turns a [CaptureSource] into sink writes, so what a reopened stream, a failed read
 * and a stop must do is pinned here against a scripted source instead of a microphone.
 */
class AudioCapturePumpTest {
    private sealed interface Step {
        data class Frames(
            val count: Int,
        ) : Step

        data class Reopen(
            val rateHz: Int,
        ) : Step

        data class Fail(
            val code: Int,
        ) : Step
    }

    private class ScriptedSource(
        rateHz: Int,
    ) : CaptureSource {
        private val steps = LinkedBlockingQueue<Step>()

        @Volatile
        private var rate = rateHz

        val released = CountDownLatch(1)

        override val sampleRateHz: Int get() = rate

        override val channels: Int = 1

        override val readFrames: Int = 8

        fun script(vararg next: Step) {
            next.forEach { steps.add(it) }
        }

        override fun read(dst: FloatArray): Int =
            when (val step = steps.poll(POLL_MS, TimeUnit.MILLISECONDS)) {
                null -> 0
                is Step.Frames -> step.count
                is Step.Reopen -> {
                    rate = step.rateHz
                    0
                }
                is Step.Fail -> step.code
            }

        override fun interrupt() = Unit

        override fun release() = released.countDown()
    }

    private class GatedSource : CaptureSource {
        val entered = CountDownLatch(1)

        val gate = CountDownLatch(1)

        val released = CountDownLatch(1)

        override val sampleRateHz: Int = 48_000

        override val channels: Int = 1

        override val readFrames: Int = 8

        override fun read(dst: FloatArray): Int {
            entered.countDown()
            gate.await(AWAIT_SECONDS, TimeUnit.SECONDS)
            return readFrames
        }

        override fun interrupt() = Unit

        override fun release() = released.countDown()
    }

    private class TestPump(
        sink: PcmSink,
    ) : AudioCapturePump(sink, 44_100, { 0L }) {
        override val threadName = "geode-test-pump"

        override fun noteLevel(
            buffer: FloatArray,
            count: Int,
            startedAtMs: Long,
        ) = Unit

        override fun resetLevel() = Unit

        fun begin(
            source: CaptureSource,
            onSampleRate: (Int) -> Unit,
        ) = startPump(source, onSampleRate)
    }

    private fun waitUntil(condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (condition()) return true
            Thread.sleep(POLL_MS)
        }
        return condition()
    }

    @Test
    fun `the source rate is reported at start and again after a reopen at another rate`() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val written = CountDownLatch(2)
        val pump =
            TestPump(
                PcmSink { _, frames, _ ->
                    events += "write:$frames"
                    written.countDown()
                },
            )
        val source = ScriptedSource(rateHz = 48_000)
        source.script(Step.Frames(4), Step.Reopen(44_100), Step.Frames(3))

        pump.begin(source) { events += "rate:$it" }

        assertTrue(written.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        pump.stop()
        assertEquals(listOf("rate:48000", "write:4", "rate:44100", "write:3"), events.toList())
        assertEquals(44_100, pump.sampleRateHz)
        assertTrue(source.released.await(AWAIT_SECONDS, TimeUnit.SECONDS))
    }

    @Test
    fun `a rate that does not change is reported once`() {
        val rates = Collections.synchronizedList(mutableListOf<Int>())
        val written = CountDownLatch(3)
        val pump = TestPump(PcmSink { _, _, _ -> written.countDown() })
        val source = ScriptedSource(rateHz = 48_000)
        source.script(Step.Frames(2), Step.Frames(2), Step.Frames(2))

        pump.begin(source) { rates += it }

        assertTrue(written.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        pump.stop()
        assertEquals(listOf(48_000), rates.toList())
        assertEquals(48_000, pump.sampleRateHz)
    }

    @Test
    fun `a failed read ends the run and releases the source`() {
        val pump = TestPump(PcmSink { _, _, _ -> })
        val source = ScriptedSource(rateHz = 48_000)
        source.script(Step.Fail(-3))

        pump.begin(source) {}

        assertTrue(source.released.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        assertTrue(waitUntil { !pump.active })
    }

    @Test
    fun `stop releases the source and leaves the pump inactive`() {
        val pump = TestPump(PcmSink { _, _, _ -> })
        val source = ScriptedSource(rateHz = 48_000)

        pump.begin(source) {}
        assertTrue(pump.active)
        pump.stop()

        assertTrue(source.released.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        assertFalse(pump.active)
    }

    @Test
    fun `a read that was blocked across stop writes nothing`() {
        val writes = Collections.synchronizedList(mutableListOf<Int>())
        val pump = TestPump(PcmSink { _, frames, _ -> writes += frames })
        val source = GatedSource()

        pump.begin(source) {}
        assertTrue(source.entered.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        pump.stop()
        source.gate.countDown()

        assertTrue(source.released.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(emptyList<Int>(), writes.toList())
        assertFalse(pump.active)
    }

    private companion object {
        const val POLL_MS = 5L

        const val AWAIT_SECONDS = 5L
    }
}
