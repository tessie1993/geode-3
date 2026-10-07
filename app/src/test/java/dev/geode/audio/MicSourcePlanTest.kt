package dev.geode.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which backend the microphone opens is decided here, from the API level and what each open
 * returns, so the AAudio-first order and every fallback can be pinned without a device.
 */
class MicSourcePlanTest {
    private val aaudio = "aaudio"

    private val record = "record"

    private fun opener(
        tried: MutableList<MicBackend>,
        aaudioResult: () -> String?,
        recordResult: () -> String?,
    ): (MicBackend) -> String? =
        { backend ->
            tried += backend
            when (backend) {
                MicBackend.AAUDIO -> aaudioResult()
                MicBackend.AUDIO_RECORD -> recordResult()
            }
        }

    @Test
    fun `android 8_0 and 8_1 never try AAudio`() {
        assertEquals(listOf(MicBackend.AUDIO_RECORD), MicSourcePlan.order(26))
        assertEquals(listOf(MicBackend.AUDIO_RECORD), MicSourcePlan.order(27))
    }

    @Test
    fun `android 9 and later try AAudio first and AudioRecord second`() {
        val expected = listOf(MicBackend.AAUDIO, MicBackend.AUDIO_RECORD)
        assertEquals(expected, MicSourcePlan.order(28))
        assertEquals(expected, MicSourcePlan.order(36))
    }

    @Test
    fun `AAudio opening leaves AudioRecord untried`() {
        val tried = mutableListOf<MicBackend>()
        val opened = MicSourcePlan.openFirst(34, opener(tried, { aaudio }, { record }))
        assertEquals(MicBackend.AAUDIO, opened?.backend)
        assertEquals(aaudio, opened?.source)
        assertEquals(listOf(MicBackend.AAUDIO), tried)
    }

    @Test
    fun `AudioRecord is used when AAudio does not open`() {
        val tried = mutableListOf<MicBackend>()
        val opened = MicSourcePlan.openFirst(34, opener(tried, { null }, { record }))
        assertEquals(MicBackend.AUDIO_RECORD, opened?.backend)
        assertEquals(record, opened?.source)
        assertEquals(listOf(MicBackend.AAUDIO, MicBackend.AUDIO_RECORD), tried)
    }

    @Test
    fun `AudioRecord is used when AAudio throws`() {
        val tried = mutableListOf<MicBackend>()
        val opened =
            MicSourcePlan.openFirst(
                34,
                opener(tried, { throw UnsatisfiedLinkError("no libgeode") }, { record }),
            )
        assertEquals(MicBackend.AUDIO_RECORD, opened?.backend)
        assertEquals(listOf(MicBackend.AAUDIO, MicBackend.AUDIO_RECORD), tried)
    }

    @Test
    fun `below android 9 only AudioRecord is asked`() {
        val tried = mutableListOf<MicBackend>()
        val opened = MicSourcePlan.openFirst(27, opener(tried, { aaudio }, { record }))
        assertEquals(MicBackend.AUDIO_RECORD, opened?.backend)
        assertEquals(listOf(MicBackend.AUDIO_RECORD), tried)
    }

    @Test
    fun `no backend opening is a null result`() {
        val tried = mutableListOf<MicBackend>()
        assertNull(MicSourcePlan.openFirst(34, opener(tried, { null }, { null })))
        assertEquals(listOf(MicBackend.AAUDIO, MicBackend.AUDIO_RECORD), tried)
        assertNull(MicSourcePlan.openFirst(26, opener(mutableListOf(), { aaudio }, { null })))
    }

    @Test
    fun `AudioRecord rates start at the device rate and keep 44_1 kHz as the next try`() {
        assertEquals(listOf(48_000, 44_100, 22_050), MicSourcePlan.recordRates(48_000))
        assertEquals(listOf(44_100, 48_000, 22_050), MicSourcePlan.recordRates(44_100))
        assertEquals(listOf(16_000, 44_100, 48_000, 22_050), MicSourcePlan.recordRates(16_000))
    }

    @Test
    fun `an unknown or implausible device rate counts as 48 kHz`() {
        val fallback = listOf(48_000, 44_100, 22_050)
        assertEquals(fallback, MicSourcePlan.recordRates(null))
        assertEquals(fallback, MicSourcePlan.recordRates(0))
        assertEquals(fallback, MicSourcePlan.recordRates(-1))
        assertEquals(fallback, MicSourcePlan.recordRates(96_000))
    }

    @Test
    fun `the AudioRecord buffer is two minimum buffers and holds at least two reads`() {
        assertEquals(7_680, MicSourcePlan.recordBufferBytes(3_840, 256, 4))
        assertEquals(2_048, MicSourcePlan.recordBufferBytes(100, 256, 4))
        assertEquals(1_024, MicSourcePlan.recordBufferBytes(100, 256, 2))
    }

    @Test
    fun `the AAudio read size is one burst within bounds`() {
        assertEquals(192, MicSourcePlan.aaudioReadFrames(192))
        assertEquals(480, MicSourcePlan.aaudioReadFrames(480))
        assertEquals(64, MicSourcePlan.aaudioReadFrames(16))
        assertEquals(1_024, MicSourcePlan.aaudioReadFrames(4_096))
    }

    @Test
    fun `an unknown AAudio burst reads the AudioRecord chunk size`() {
        assertEquals(MicSourcePlan.RECORD_READ_FRAMES, MicSourcePlan.aaudioReadFrames(0))
        assertEquals(MicSourcePlan.RECORD_READ_FRAMES, MicSourcePlan.aaudioReadFrames(-1))
    }

    @Test
    fun `the microphone AudioRecord chunk is 256 frames and AAudio starts at android 9`() {
        assertEquals(256, MicSourcePlan.RECORD_READ_FRAMES)
        assertEquals(28, MicSourcePlan.AAUDIO_MIN_API)
    }

    @Test
    fun `record configurations continue when initialized recorder fails to start`() {
        val attempts = mutableListOf<Pair<Int, Int>>()
        val result =
            MicSourcePlan.openRecord(48_000, intArrayOf(4, 2)) { rate, encoding ->
                attempts += rate to encoding
                if (encoding == 4) null else "started"
            }
        assertEquals("started", result)
        assertEquals(listOf(48_000 to 4, 48_000 to 2), attempts)
    }

    @Test
    fun `record configuration first success skips remaining attempts`() {
        var attempts = 0
        val result =
            MicSourcePlan.openRecord(48_000, intArrayOf(4, 2)) { _, _ ->
                attempts++
                "started"
            }
        assertEquals("started", result)
        assertEquals(1, attempts)
    }

    @Test
    fun `record configuration failures and exceptions exhaust the ordered configurations`() {
        val attempts = mutableListOf<Pair<Int, Int>>()
        val result =
            MicSourcePlan.openRecord<String>(48_000, intArrayOf(4, 2)) { rate, encoding ->
                attempts += rate to encoding
                if (encoding == 4) error("refused start") else null
            }
        assertNull(result)
        assertEquals(listOf(48_000 to 4, 48_000 to 2, 44_100 to 4, 44_100 to 2, 22_050 to 4, 22_050 to 2), attempts)
    }
}
