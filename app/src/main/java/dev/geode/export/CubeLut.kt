package dev.geode.export

import android.content.Context
import android.net.Uri
import dev.geode.editor.EditorTextInput
import java.io.IOException
import java.io.InputStream
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

/** Adobe/Resolve .cube tables, converted to Media3's cube[r][g][b] ARGB layout. */
class CubeLut private constructor(
    val size: Int,
    val cube: Array<Array<IntArray>>,
) {
    companion object {
        const val GAMMA_SIZE = 33
        const val MAX_INPUT_BYTES = 16 * 1024 * 1024
        private const val MAX_SIZE = 65
        private const val MAX_LINE_CHARS = 512
        private val WHITESPACE = Regex("\\s+")

        fun load(
            context: Context,
            uri: String,
        ): CubeLut? =
            try {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use(::read)
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }

        /** The caller owns the stream and executes provider IO on a worker dispatcher. */
        fun read(input: InputStream): CubeLut? =
            try {
                parse(EditorTextInput.readUtf8(input, MAX_INPUT_BYTES))
            } catch (_: IOException) {
                null
            }

        fun parse(text: String): CubeLut? {
            if (!EditorTextInput.withinByteLimit(text, MAX_INPUT_BYTES)) return null
            val parser = CubeParser()
            for (raw in text.removePrefix("\uFEFF").lineSequence()) {
                if (raw.length > MAX_LINE_CHARS || !parser.accept(raw.substringBefore('#').trim())) return null
            }
            return parser.build()
        }

        /** Per-channel gamma as a cube: output = input ^ (1 / gamma) on each channel. */
        fun gamma(
            red: Float,
            green: Float,
            blue: Float,
            size: Int = GAMMA_SIZE,
        ): CubeLut {
            require(size in 2..MAX_SIZE && red.isFinite() && green.isFinite() && blue.isFinite())
            val last = (size - 1).toFloat()
            val r = FloatArray(size) { (it / last).pow(1f / red.coerceAtLeast(0.01f)) }
            val g = FloatArray(size) { (it / last).pow(1f / green.coerceAtLeast(0.01f)) }
            val b = FloatArray(size) { (it / last).pow(1f / blue.coerceAtLeast(0.01f)) }
            return CubeLut(size, Array(size) { ri -> Array(size) { gi -> IntArray(size) { bi -> argb(r[ri], g[gi], b[bi]) } } })
        }

        private fun triple(
            parts: List<String>,
            from: Int,
        ): FloatArray? {
            if (parts.size != from + 3) return null
            val values = FloatArray(3)
            for (channel in values.indices) {
                values[channel] = parts[from + channel].toFloatOrNull()?.takeIf(Float::isFinite) ?: return null
            }
            return values
        }

        private fun argb(
            r: Float,
            g: Float,
            b: Float,
        ): Int =
            (0xff shl 24) or
                ((r.coerceIn(0f, 1f) * 255f).roundToInt() shl 16) or
                ((g.coerceIn(0f, 1f) * 255f).roundToInt() shl 8) or
                (b.coerceIn(0f, 1f) * 255f).roundToInt()

        private class CubeParser {
            private var size = 0
            private var is3d = false
            private var rows = FloatArray(0)
            private var rowCount = 0
            private var domainMin = floatArrayOf(0f, 0f, 0f)
            private var domainMax = floatArrayOf(1f, 1f, 1f)
            private val headers = mutableSetOf<String>()

            fun accept(line: String): Boolean {
                if (line.isEmpty()) return true
                val parts = line.split(WHITESPACE)
                val directive = parts[0].uppercase()
                return when (directive) {
                    "TITLE", "LUT_3D_SIZE", "LUT_1D_SIZE", "DOMAIN_MIN", "DOMAIN_MAX" -> {
                        if (rowCount != 0 || !headers.add(directive)) return false
                        acceptHeader(directive, parts)
                    }
                    else -> {
                        if (size == 0 || rowCount >= rows.size / 3) return false
                        val values = triple(parts, 0) ?: return false
                        values.copyInto(rows, rowCount++ * 3)
                        true
                    }
                }
            }

            private fun acceptHeader(
                directive: String,
                parts: List<String>,
            ): Boolean =
                when (directive) {
                    "TITLE" -> parts.size >= 2
                    "LUT_3D_SIZE", "LUT_1D_SIZE" -> {
                        val declared = parts.getOrNull(1)?.toIntOrNull()
                        if (size != 0 || parts.size != 2 || declared == null || declared !in 2..MAX_SIZE) {
                            false
                        } else {
                            size = declared
                            is3d = directive == "LUT_3D_SIZE"
                            rows = FloatArray((if (is3d) size * size * size else size) * 3)
                            true
                        }
                    }
                    "DOMAIN_MIN" -> triple(parts, 1)?.also { domainMin = it } != null
                    "DOMAIN_MAX" -> triple(parts, 1)?.also { domainMax = it } != null
                    else -> false
                }

            fun build(): CubeLut? {
                if (size == 0 || rowCount != rows.size / 3) return null
                val span = FloatArray(3) { domainMax[it] - domainMin[it] }
                if (span.any { !it.isFinite() || it <= 0f }) return null
                // DOMAIN specifies input coordinates, never a normalization of the output rows.
                val coordinates =
                    Array(3) { channel ->
                        FloatArray(size) { index ->
                            ((index.toDouble() / (size - 1) - domainMin[channel]) / span[channel])
                                .coerceIn(0.0, 1.0).toFloat() * (size - 1)
                        }
                    }
                return CubeLut(
                    size,
                    Array(size) { r ->
                        Array(size) { g ->
                            IntArray(size) { b ->
                                val x = coordinates[0][r]
                                val y = coordinates[1][g]
                                val z = coordinates[2][b]
                                argb(sample(x, y, z, 0), sample(x, y, z, 1), sample(x, y, z, 2))
                            }
                        }
                    },
                )
            }

            private fun sample(
                x: Float,
                y: Float,
                z: Float,
                channel: Int,
            ): Float {
                if (!is3d) {
                    val at =
                        when (channel) {
                            0 -> x
                            1 -> y
                            else -> z
                        }
                    val low = floor(at).toInt()
                    return mix(rows[low * 3 + channel], rows[minOf(low + 1, size - 1) * 3 + channel], at - low)
                }
                val lowX = floor(x).toInt()
                val lowY = floor(y).toInt()
                val lowZ = floor(z).toInt()
                fun row(
                    dx: Int,
                    dy: Int,
                    dz: Int,
                ): Float {
                    val index =
                        minOf(lowX + dx, size - 1) +
                            minOf(lowY + dy, size - 1) * size +
                            minOf(lowZ + dz, size - 1) * size * size
                    return rows[index * 3 + channel]
                }
                return mix(
                    mix(mix(row(0, 0, 0), row(1, 0, 0), x - lowX), mix(row(0, 1, 0), row(1, 1, 0), x - lowX), y - lowY),
                    mix(mix(row(0, 0, 1), row(1, 0, 1), x - lowX), mix(row(0, 1, 1), row(1, 1, 1), x - lowX), y - lowY),
                    z - lowZ,
                )
            }

            // Use double intermediates so opposite large, finite float endpoints cannot overflow.
            private fun mix(
                a: Float,
                b: Float,
                fraction: Float,
            ): Float = (a.toDouble() * (1.0 - fraction) + b.toDouble() * fraction).toFloat()
        }
    }
}
