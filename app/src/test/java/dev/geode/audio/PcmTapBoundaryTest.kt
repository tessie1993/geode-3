package dev.geode.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import dev.geode.engine.audio.PcmSink
import dev.geode.engine.audio.SampleRing
import dev.geode.engine.audioandroid.PcmTap
import dev.geode.engine.audioandroid.TapBoundaryListener
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(UnstableApi::class)
class PcmTapBoundaryTest {
    private val rendererRing = PcmRingBuffer(8192)
    private val analysisRing = SampleRing(capacityFrames = 8192, channelCount = 2, maxWriteFrames = 4096)
    private val events = mutableListOf<String>()
    private val writeSizes = mutableListOf<Int>()
    private val tap =
        PcmTap(
            object : PcmSink {
                override fun discontinuity() {
                    rendererRing.discontinuity()
                    analysisRing.beginEpoch()
                    events += "reset"
                }

                override fun write(
                    interleaved: FloatArray,
                    frameCount: Int,
                    sourceChannelCount: Int,
                ) {
                    rendererRing.write(interleaved, frameCount, sourceChannelCount)
                    analysisRing.write(interleaved, frameCount, sourceChannelCount)
                    writeSizes += frameCount
                }
            },
            onFormat = { events += "format:${it.generation}" },
        ).apply {
            boundaryListener =
                TapBoundaryListener { ended, endedFrames, begun ->
                    events += "boundary:$endedFrames:${ended?.generation}->${begun.generation}"
                }
        }

    private fun flush(channels: Int = 1) = tap.flush(48_000, channels, C.ENCODING_PCM_16BIT)

    private fun pcm16(samples: ShortArray): ByteBuffer =
        ByteBuffer.allocate(samples.size * Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            samples.forEach(::putShort)
            flip()
        }

    @Test
    fun `same rate flush invalidates renderer and analysis PCM before notifications`() {
        flush()
        tap.handleBuffer(pcm16(ShortArray(4) { 16_384 }))
        val output = FloatArray(4)
        assertEquals(4, rendererRing.copyNewSince(0, output))
        val cursor = rendererRing.lastCopyEndIndex
        assertTrue(analysisRing.snapshotLatest(Array(2) { FloatArray(4) }))
        events.clear()

        flush()

        assertEquals(listOf("reset", "boundary:4:1->2", "format:2"), events)
        assertEquals(2, analysisRing.epoch)
        assertFalse(rendererRing.snapshotLatest(output))
        assertEquals(0, rendererRing.copyNewSince(cursor, output))
        assertFalse(analysisRing.snapshotLatest(Array(2) { FloatArray(4) }))
        assertEquals(0L, tap.framesWritten)

        tap.handleBuffer(pcm16(ShortArray(2) { 8192 }))
        assertFalse(rendererRing.snapshotLatest(output))
        assertFalse(analysisRing.snapshotLatest(Array(2) { FloatArray(4) }))
        assertEquals(2, rendererRing.copyNewSince(0, output))
        assertArrayEquals(floatArrayOf(0.25f, 0.25f), output.copyOf(2), 0f)
    }

    @Test
    fun `format boundary removes old stereo side samples before fresh mono window`() {
        flush(channels = 2)
        tap.handleBuffer(pcm16(shortArrayOf(16_384, -16_384, 16_384, -16_384)))
        val side = FloatArray(2)
        assertTrue(rendererRing.snapshotLatestSide(side))
        assertArrayEquals(floatArrayOf(0.5f, 0.5f), side, 0f)

        flush(channels = 1)
        assertFalse(rendererRing.snapshotLatestSide(side))
        tap.handleBuffer(pcm16(shortArrayOf(8192, 8192)))

        assertTrue(rendererRing.snapshotLatestSide(side))
        assertArrayEquals(FloatArray(2), side, 0f)
        assertEquals(1, analysisRing.sourceChannelCount)
    }

    @Test
    fun `decoder batch spanning staging chunks reaches both rings in order`() {
        flush()
        tap.handleBuffer(pcm16(ShortArray(4099) { if (it < 4096) 8192 else 16_384 }))

        assertEquals(listOf(4096, 3), writeSizes)
        assertEquals(4099L, tap.framesWritten)
        val mono = FloatArray(4099)
        assertEquals(4099, rendererRing.copyNewSince(0, mono))
        assertArrayEquals(FloatArray(4099) { if (it < 4096) 0.25f else 0.5f }, mono, 0f)
        val planar = Array(2) { FloatArray(4099) }
        assertTrue(analysisRing.snapshotLatest(planar))
        assertArrayEquals(mono, planar[0], 0f)
    }
}
