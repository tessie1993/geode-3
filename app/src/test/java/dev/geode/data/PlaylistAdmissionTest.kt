package dev.geode.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class PlaylistAdmissionTest {
    @Test
    fun unknownLengthStreamStopsAtByteBudget() {
        var consumed = 0
        val stream =
            object : InputStream() {
                override fun read(): Int {
                    consumed++
                    return 'x'.code
                }

                override fun read(
                    bytes: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int {
                    bytes.fill('x'.code.toByte(), offset, offset + length)
                    consumed += length
                    return length
                }
            }
        try {
            PlaylistFormats.readText(stream)
            fail("An endless provider must be rejected")
        } catch (_: IOException) {
            assertEquals(PlaylistFormats.MAX_BYTES + 1, consumed)
        }
    }

    @Test
    fun exactByteBudgetIsAccepted() {
        val bytes = ByteArray(PlaylistFormats.MAX_BYTES) { 'a'.code.toByte() }
        assertEquals(bytes.size, PlaylistFormats.readText(ByteArrayInputStream(bytes)).length)
    }

    @Test
    fun entryBudgetsApplyToAllSupportedFormats() {
        val count = PlaylistFormats.MAX_ENTRIES + 1
        val samples =
            mapOf(
                "large.m3u" to "song.mp3\n".repeat(count),
                "large.pls" to (1..count).joinToString("\n", prefix = "[playlist]\n") { "File$it=song.mp3" },
                "large.xspf" to "<playlist><trackList>" +
                    "<track><location>song.mp3</location></track>".repeat(count) + "</trackList></playlist>",
            )
        for ((name, text) in samples) {
            assertTrue(name, PlaylistFormats.parse(name, text) is PlaylistParse.Unreadable)
        }
    }

    @Test
    fun exactEntryBudgetIsAccepted() {
        val parsed =
            PlaylistFormats.parse("full.m3u", "song.mp3\n".repeat(PlaylistFormats.MAX_ENTRIES)) as PlaylistParse.Parsed
        assertEquals(PlaylistFormats.MAX_ENTRIES, parsed.entries.size)
    }

    @Test
    fun validFormatsKeepLocationsAndDuration() {
        val samples =
            mapOf(
                "music.m3u" to "#EXTM3U\n#EXTINF:12,Track\nfolder/song.mp3",
                "music.pls" to "[playlist]\nFile1=folder/song.mp3\nTitle1=Track\nLength1=12",
                "music.xspf" to "<playlist><trackList><track><location>folder/song.mp3</location>" +
                    "<title>Track</title><duration>12000</duration></track></trackList></playlist>",
            )
        for ((name, text) in samples) {
            val parsed = PlaylistFormats.parse(name, text) as PlaylistParse.Parsed
            assertEquals(listOf(PlaylistEntry("folder/song.mp3", "Track", 12_000)), parsed.entries)
        }
    }
}
