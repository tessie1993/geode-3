package dev.geode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlesTest {
    @Test
    fun `complete cues support BOM CRLF dot stamps and multiline text`() {
        val cues = Subtitles.readSrt("\uFEFF1\r\n00:00:01,000 --> 00:00:03.5\r\nHello\r\nworld\r\n".byteInputStream())!!
        assertEquals(listOf(SubtitleCue(1000, 3500, "Hello\nworld")), cues)
        assertEquals(cues, Subtitles.parseSrt(Subtitles.toSrt(cues)))
    }

    @Test
    fun `malformed block after valid cue cannot partially import`() {
        val invalid =
            listOf(
                "$CUE\n\nnot a subtitle",
                CUE.replace("00:00:02,000", "00:60:02,000"),
                CUE.replace("00:00:02,000", "00:00:00,000"),
                CUE.replace("00:00:02,000", "00:00:01,001"),
                CUE.replace("Hello", "\u0000binary"),
                CUE.replace("Hello", "x".repeat(4097)),
                CUE.replace("Hello", ""),
                CUE.replace(" --> ", " -> "),
            )
        invalid.forEach { assertTrue(it, Subtitles.parseSrt(it).isEmpty()) }
    }

    @Test
    fun `cue count and aggregate byte limits reject without partial results`() {
        val full = List(Subtitles.MAX_CUES) { CUE }.joinToString("\n\n")
        assertEquals(Subtitles.MAX_CUES, Subtitles.parseSrt(full).size)
        assertTrue(Subtitles.parseSrt("$full\n\n$CUE").isEmpty())
        assertNull(Subtitles.readSrt(ByteArray(Subtitles.MAX_INPUT_BYTES + 1) { 'x'.code.toByte() }.inputStream()))
    }

    @Test
    fun `cue text budget and row budget are enforced`() {
        assertTrue(Subtitles.parseSrt(CUE.replace("Hello", List(3) { "x".repeat(3000) }.joinToString("\n"))).isEmpty())
        assertTrue(Subtitles.parseSrt("\n".repeat(16_001) + CUE).isEmpty())
    }

    @Test
    fun `invalid utf8 and zero progress streams reject`() {
        assertNull(Subtitles.readSrt(byteArrayOf(0xc3.toByte(), 0x28).inputStream()))
        val stalled =
            object : java.io.InputStream() {
                override fun read(): Int = 0

                override fun read(
                    target: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int = 0
            }
        assertNull(Subtitles.readSrt(stalled))
    }

    private companion object {
        const val CUE = "1\n00:00:01,000 --> 00:00:02,000\nHello"
    }
}
