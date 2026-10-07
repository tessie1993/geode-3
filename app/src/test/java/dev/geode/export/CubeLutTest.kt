package dev.geode.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

class CubeLutTest {
    @Test
    fun `3d rows retain red-fastest cube order`() {
        val lut = CubeLut.parse(IDENTITY)!!
        assertEquals(2, lut.size)
        assertEquals(0xffff0000.toInt(), lut.cube[1][0][0])
        assertEquals(0xff00ff00.toInt(), lut.cube[0][1][0])
        assertEquals(0xff0000ff.toInt(), lut.cube[0][0][1])
        assertEquals(0xffffffff.toInt(), lut.cube[1][1][1])
    }

    @Test
    fun `1d input domain is sampled without renormalizing output values`() {
        val lut = CubeLut.parse("LUT_1D_SIZE 2\nDOMAIN_MIN 0 0 0\nDOMAIN_MAX 2 2 2\n0 0 0\n1 1 1")!!
        assertEquals(0xff808080.toInt(), lut.cube[1][1][1])
        val constant = CubeLut.parse("LUT_1D_SIZE 2\nDOMAIN_MIN -1 -1 -1\n0.5 0.5 0.5\n0.5 0.5 0.5")!!
        assertEquals(0xff808080.toInt(), constant.cube[0][0][0])
    }

    @Test
    fun `missing excess mixed and malformed cube rows reject the entire document`() {
        val invalid =
            listOf(
                IDENTITY.substringBeforeLast("1 1 1"),
                "$IDENTITY\n0 0 0",
                IDENTITY.replace("0 0 0", "NaN 0 0"),
                IDENTITY.replace("0 0 0", "Infinity 0 0"),
                IDENTITY.replace("0 0 0", "0 0 0 1"),
                IDENTITY.replace("LUT_3D_SIZE 2", "LUT_3D_SIZE 66"),
                IDENTITY.replace("LUT_3D_SIZE 2", "LUT_3D_SIZE 2147483647"),
                "LUT_1D_SIZE 2\n$IDENTITY",
                "DOMAIN_MAX 0 1 1\n$IDENTITY",
                "DOMAIN_MIN NaN 0 0\n$IDENTITY",
                "DOMAIN_MIN -3.4e38 0 0\nDOMAIN_MAX 3.4e38 1 1\n$IDENTITY",
                "a text file, not a LUT",
                "0 0 0\n$IDENTITY",
            )
        invalid.forEach { assertNull(it, CubeLut.parse(it)) }
    }

    @Test
    fun `oversized lines and invalid utf8 cannot be accepted`() {
        assertNull(CubeLut.parse("#" + "x".repeat(513) + "\n" + IDENTITY))
        assertNull(CubeLut.read(byteArrayOf(0xc3.toByte(), 0x28).inputStream()))
        assertNotNull(CubeLut.read(("\uFEFF# test\n" + IDENTITY).byteInputStream()))
    }

    @Test
    fun `unknown-size stream is stopped at byte budget plus one`() {
        val source =
            object : InputStream() {
                var delivered = 0

                override fun read(): Int {
                    delivered++
                    return 'x'.code
                }

                override fun read(
                    target: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int {
                    target.fill('x'.code.toByte(), offset, offset + length)
                    delivered += length
                    return length
                }
            }
        assertNull(CubeLut.read(source))
        assertEquals(CubeLut.MAX_INPUT_BYTES + 1, source.delivered)
    }

    @Test
    fun `finite extreme row values clamp safely`() {
        val lut = CubeLut.parse("LUT_1D_SIZE 2\n-3.4e38 0 0\n3.4e38 1 1")!!
        assertEquals(0xff000000.toInt(), lut.cube[0][0][0])
        assertEquals(0xffffffff.toInt(), lut.cube[1][1][1])
    }

    @Test
    fun `gamma rejects nonfinite values and invalid cube dimensions`() {
        for (gamma in listOf(Float.NaN, Float.POSITIVE_INFINITY)) {
            assertTrue(runCatching { CubeLut.gamma(gamma, 1f, 1f) }.isFailure)
        }
        assertTrue(runCatching { CubeLut.gamma(1f, 1f, 1f, 1) }.isFailure)
    }

    private companion object {
        const val IDENTITY = "LUT_3D_SIZE 2\n0 0 0\n1 0 0\n0 1 0\n1 1 0\n0 0 1\n1 0 1\n0 1 1\n1 1 1"
    }
}
