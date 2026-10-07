package dev.geode.editor

import dev.geode.ui.LyricLine
import java.io.IOException
import java.io.InputStream

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
) {
    fun spans(atMs: Long): Boolean = atMs >= startMs && atMs < endMs
}

/** SubRip in and out, and the two conversions the editor needs: lyric lines to cues, cues to and from a text lane. */
object Subtitles {
    const val MAX_LYRIC_CUE_MS: Long = 8_000L
    private const val MIN_CUE_MS: Long = MIN_CLIP_DURATION_MS

    const val MAX_INPUT_BYTES = 1024 * 1024
    const val MAX_CUES = 2000
    private const val MAX_LINE_CHARS = 4096
    private const val MAX_CUE_CHARS = 8192
    private const val MAX_LINES = 16_000

    /** A rejected document produces no cues; imports never retain a partially parsed file. */
    fun readSrt(input: InputStream): List<SubtitleCue>? =
        try {
            parseSrt(EditorTextInput.readUtf8(input, MAX_INPUT_BYTES)).takeIf { it.isNotEmpty() }
        } catch (_: IOException) {
            null
        }

    fun parseSrt(text: String): List<SubtitleCue> {
        if (!EditorTextInput.withinByteLimit(text, MAX_INPUT_BYTES)) return emptyList()
        val cues = ArrayList<SubtitleCue>()
        val block = ArrayList<String>()
        var blockChars = 0
        var lineCount = 0
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            if (++lineCount > MAX_LINES || raw.length > MAX_LINE_CHARS || raw.any { it < ' ' && it != '\t' }) return emptyList()
            val line = raw.trim()
            if (line.isEmpty()) {
                if (block.isNotEmpty()) {
                    if (cues.size >= MAX_CUES) return emptyList()
                    cues += cueFromBlock(block) ?: return emptyList()
                    block.clear()
                    blockChars = 0
                }
            } else {
                blockChars += line.length
                if (blockChars > MAX_CUE_CHARS) return emptyList()
                block += line
            }
        }
        if (block.isNotEmpty()) {
            if (cues.size >= MAX_CUES) return emptyList()
            cues += cueFromBlock(block) ?: return emptyList()
        }
        return cues.sortedBy { it.startMs }
    }

    private fun cueFromBlock(lines: List<String>): SubtitleCue? {
        val timingIndex = if (lines[0].all(Char::isDigit)) 1 else 0
        val match = lines.getOrNull(timingIndex)?.let(TIMING::matchEntire) ?: return null
        val start = stampMs(match.groupValues, 1) ?: return null
        val end = stampMs(match.groupValues, 5) ?: return null
        val body = lines.drop(timingIndex + 1).joinToString("\n")
        return if (end - start >= MIN_CUE_MS && body.isNotBlank()) SubtitleCue(start, end, body) else null
    }

    fun toSrt(cues: List<SubtitleCue>): String =
        cues
            .sortedBy { it.startMs }
            .mapIndexed { index, cue -> "${index + 1}\n${stamp(cue.startMs)} --> ${stamp(cue.endMs)}\n${cue.text}\n" }
            .joinToString("\n")

    /** Each synced line shows until the next one starts, at most [MAX_LYRIC_CUE_MS]; the last runs to [endMs]. */
    fun fromLyrics(
        lines: List<LyricLine>,
        endMs: Long,
    ): List<SubtitleCue> {
        val timed = lines.filter { it.timeMs >= 0 && it.text.isNotBlank() }.sortedBy { it.timeMs }
        return timed.mapIndexedNotNull { index, line ->
            val next = timed.getOrNull(index + 1)?.timeMs ?: endMs
            val end = minOf(next, line.timeMs + MAX_LYRIC_CUE_MS)
            if (end - line.timeMs < MIN_CUE_MS) null else SubtitleCue(line.timeMs, end, line.text)
        }
    }

    fun cuesFrom(lanes: List<Lane>): List<SubtitleCue> =
        lanes
            .filter { it.kind == LaneKind.Text && !it.muted }
            .flatMap { lane -> lane.clips.filter(Clip::enabled) }
            .mapNotNull { clip -> (clip.content as? ClipContent.Text)?.let { SubtitleCue(clip.startMs, clip.endMs, it.text) } }
            .sortedBy { it.startMs }

    fun clipsFrom(
        cues: List<SubtitleCue>,
        idFor: () -> ClipId,
    ): List<Clip> =
        cues
            .filter { it.endMs - it.startMs >= MIN_CUE_MS }
            .map { Clip(idFor(), ClipContent.Text(it.text), startMs = it.startMs, durationMs = it.endMs - it.startMs) }

    private fun stampMs(
        groups: List<String>,
        first: Int,
    ): Long? {
        val h = groups[first].toLong()
        val m = groups[first + 1].toLong()
        val s = groups[first + 2].toLong()
        if (m !in 0..59 || s !in 0..59) return null
        val ms = groups[first + 3].padEnd(3, '0').take(3).toLong()
        return ((h * 60 + m) * 60 + s) * 1000 + ms
    }

    private fun stamp(ms: Long): String {
        val h = ms / 3_600_000
        val m = ms / 60_000 % 60
        val s = ms / 1000 % 60
        return "%02d:%02d:%02d,%03d".format(h, m, s, ms % 1000)
    }

    private val TIMING = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""")
}
