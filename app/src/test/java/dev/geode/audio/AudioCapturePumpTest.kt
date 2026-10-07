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
            val readFrames: Int = 8,
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

        override var readFrames: Int = 8
            private set

        override var generation: Int = 0
            private set

        val readSizes = Collections.synchronizedList(mutableListOf<Int>())
        var releases = 0
            private set

        fun script(vararg next: Step) {
            next.forEach { steps.add(it) }
        }

        override fun read(dst: FloatArray): Int {
            readSizes += dst.size
            return when (val step = steps.poll(POLL_MS, TimeUnit.MILLISECONDS)) {
                null -> 0
                is Step.Frames -> step.count
                is Step.Reopen -> {
                    rate = step.rateHz
                    readFrames = step.readFrames
                    generation++
                    0
                }

                is Step.Fail -> step.code
            }
        }

        override fun interrupt() = Unit

        override fun release() {
            releases++
            released.countDown()
        }
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
            onSampleRate: (Int) -> Unit = {},
            fallback: (() -> CaptureSource?)? = null,
        ) = startPump(source, onSampleRate, fallback)
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

        pump.begin(source, onSampleRate = { events += "rate:$it" })

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

        pump.begin(source, onSampleRate = { rates += it })

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

        pump.begin(source)

        assertTrue(source.released.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        assertTrue(waitUntil { !pump.active })
    }

    @Test
    fun `stop releases the source and leaves the pump inactive`() {
        val pump = TestPump(PcmSink { _, _, _ -> })
        val source = ScriptedSource(rateHz = 48_000)

        pump.begin(source)
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

        pump.begin(source)
        assertTrue(source.entered.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        pump.stop()
        source.gate.countDown()

        assertTrue(source.released.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        assertEquals(emptyList<Int>(), writes.toList())
        assertFalse(pump.active)
    }

    @Test
    fun `same rate reconnect clears the sink and resizes reads in both directions`() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val written = CountDownLatch(3)
        val source = ScriptedSource(48_000)
        source.script(Step.Frames(4), Step.Reopen(48_000, 16), Step.Frames(12), Step.Reopen(48_000, 4), Step.Frames(3))
        val pump =
            TestPump(
                object : PcmSink {
                    override fun discontinuity() {
                        events += "reset"
                    }

                    override fun write(
                        interleaved: FloatArray,
                        frameCount: Int,
                        sourceChannelCount: Int,
                    ) {
                        events += "write:$frameCount"
                        written.countDown()
                    }
                },
            )
        pump.begin(source)
        assertTrue(written.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        pump.stop()
        assertEquals(listOf(8, 8, 16, 16, 4), source.readSizes.take(5))
        assertEquals(listOf("reset", "write:4", "reset", "write:12", "reset", "write:3"), events.take(6))
        assertEquals(1, source.releases)
    }

    @Test
    fun `native terminal failure releases it before opening fallback and reports fallback format before PCM`() {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val written = CountDownLatch(1)
        val native = ScriptedSource(48_000).apply { script(Step.Fail(-3)) }
        val record = ScriptedSource(16_000).apply { script(Step.Frames(5)) }
        val pump =
            TestPump(
                PcmSink { _, frames, _ ->
                    events += "write:$frames"
                    written.countDown()
                },
            )
        pump.begin(
            native,
            onSampleRate = { events += "rate:$it" },
            fallback = {
                assertEquals(1, native.releases)
                events += "open"
                record
            },
        )
        assertTrue(written.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        assertTrue(pump.active)
        pump.stop()
        assertEquals(listOf("rate:48000", "open", "rate:16000", "write:5"), events.toList())
        assertEquals(1, native.releases)
        assertEquals(1, record.releases)
    }

    @Test
    fun `fallback is attempted once and failure ends the capture`() {
        val native = ScriptedSource(48_000).apply { script(Step.Fail(-3)) }
        val record = ScriptedSource(48_000).apply { script(Step.Fail(-3)) }
        var attempts = 0
        val pump = TestPump(PcmSink { _, _, _ -> })
        pump.begin(
            native,
            fallback = {
                attempts++
                record
            },
        )
        assertTrue(record.released.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        assertTrue(waitUntil { !pump.active })
        assertEquals(1, attempts)
        assertEquals(1, native.releases)
        assertEquals(1, record.releases)
    }

    @Test
    fun `unavailable fallback ends capture`() {
        val source = ScriptedSource(48_000).apply { script(Step.Fail(-3)) }
        val pump = TestPump(PcmSink { _, _, _ -> })
        pump.begin(source, fallback = { null })
        assertTrue(waitUntil { !pump.active })
        assertEquals(1, source.releases)
    }

    @Test
    fun `stop during fallback opening releases late source without publishing into restarted session`() {
        val opened = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val written = CountDownLatch(1)
        val events = Collections.synchronizedList(mutableListOf<Int>())
        val native = ScriptedSource(48_000).apply { script(Step.Fail(-3)) }
        val late = ScriptedSource(16_000).apply { script(Step.Frames(7)) }
        val current = ScriptedSource(44_100).apply { script(Step.Frames(3)) }
        val pump =
            TestPump(
                PcmSink { _, frames, _ ->
                    events += frames
                    written.countDown()
                },
            )
        pump.begin(
            native,
            fallback = {
                opened.countDown()
                gate.await(AWAIT_SECONDS, TimeUnit.SECONDS)
                late
            },
        )
        assertTrue(opened.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        pump.stop()
        pump.begin(current)
        gate.countDown()
        assertTrue(late.released.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        assertTrue(written.await(AWAIT_SECONDS, TimeUnit.SECONDS))
        assertTrue(pump.active)
        assertEquals(44_100, pump.sampleRateHz)
        pump.stop()
        assertEquals(listOf(3), events.toList())
        assertEquals(1, late.releases)
        assertEquals(1, native.releases)
        assertEquals(1, current.releases)
    }

    private companion object {
        const val POLL_MS = 5L

        const val AWAIT_SECONDS = 5L
    }
}
