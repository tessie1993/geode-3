package dev.geode.editor

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Provider metadata is advisory: enforce the byte budget while reading, including unknown-size streams. */
internal object EditorTextInput {
    fun readUtf8(
        input: InputStream,
        maxBytes: Int,
    ): String {
        val output = ByteArrayOutputStream(minOf(maxBytes, BUFFER_SIZE))
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - output.size() + 1))
            if (count < 0) break
            // A non-progressing provider is not allowed to spin forever.
            if (count == 0) throw IOException("The document provider stopped delivering data")
            if (count > maxBytes - output.size()) throw IOException("The document exceeds its import limit")
            output.write(buffer, 0, count)
        }
        return Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(output.toByteArray()))
            .toString()
            .removePrefix("\uFEFF")
    }

    fun withinByteLimit(
        text: String,
        maxBytes: Int,
    ): Boolean = text.length <= maxBytes && text.toByteArray(Charsets.UTF_8).size <= maxBytes

    private const val BUFFER_SIZE = 8192
}
