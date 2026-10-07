package dev.geode.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class MilkAssetAdmissionTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `a rejected replacement keeps the existing file and clears staging`() {
        val target = File(temporary.root, "saved.milk").apply { writeText(PRESET) }
        assertNotNull(store(target, "milk", "not a preset".toByteArray()))
        assertEquals(PRESET, target.readText())
        assertEquals(listOf(target.name), temporary.root.listFiles().orEmpty().map { it.name })
        assertNull(store(target, "milk", "$PRESET\nfDecay=0.95".toByteArray()))
        assertTrue(target.readText().endsWith("fDecay=0.95"))
    }

    @Test
    fun `a provider without a size is stopped at the byte cap plus one`() {
        var delivered = 0L
        val endless = object : InputStream() {
            override fun read(): Int {
                delivered++
                return 'x'.code
            }

            override fun read(
                b: ByteArray,
                off: Int,
                len: Int,
            ): Int {
                b.fill('x'.code.toByte(), off, off + len)
                delivered += len
                return len
            }
        }
        val target = File(temporary.root, "large.milk")
        val result = MilkAssetAdmission.store(target, "milk", MilkAssetAdmission.Budget(), true) { endless }
        assertNotNull(result)
        assertEquals(MilkAssetAdmission.MAX_PRESET_BYTES + 1, delivered)
        assertFalse(target.exists())
        assertTrue(temporary.root.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `zero length provider reads cannot spin forever`() {
        val source = ByteArrayInputStream(PRESET.toByteArray())
        val input = object : InputStream() {
            override fun read(): Int = source.read()

            override fun read(
                b: ByteArray,
                off: Int,
                len: Int,
            ): Int = 0
        }
        val target = File(temporary.root, "slow.milk")
        assertNull(MilkAssetAdmission.store(target, "milk", MilkAssetAdmission.Budget(), true) { input })
        assertEquals(PRESET, target.readText())
    }

    @Test
    fun `batch byte budget includes rejected files and preserves the next destination`() {
        val budget = MilkAssetAdmission.Budget(maxBytes = 24)
        assertNotNull(store(File(temporary.root, "bad.milk"), "milk", ByteArray(20), budget))
        val previous = File(temporary.root, "good.milk").apply { writeText(PRESET) }
        assertNotNull(store(previous, "milk", PRESET.toByteArray(), budget))
        assertEquals(PRESET, previous.readText())
    }

    @Test
    fun `batch decoded budget prevents small encoded textures accumulating large surfaces`() {
        val budget = MilkAssetAdmission.Budget(maxDecodedBytes = 4)
        assertNull(store(File(temporary.root, "one.tga"), "tga", tga(), budget))
        val second = File(temporary.root, "two.tga")
        assertNotNull(store(second, "tga", tga(), budget))
        assertFalse(second.exists())
    }

    @Test
    fun `file count limit rejects before opening another provider stream`() {
        val budget = MilkAssetAdmission.Budget(maxFiles = 1)
        assertNull(store(File(temporary.root, "one.milk"), "milk", PRESET.toByteArray(), budget))
        var opened = false
        assertNotNull(
            MilkAssetAdmission.store(File(temporary.root, "two.milk"), "milk", budget, true) {
                opened = true
                ByteArrayInputStream(PRESET.toByteArray())
            },
        )
        assertFalse(opened)
    }

    @Test
    fun `legacy encoded comments and versionless milk presets retain their original bytes`() {
        val bytes = ("; caf\u00e9\n" + PRESET).toByteArray(Charsets.ISO_8859_1)
        val target = File(temporary.root, "legacy.milk")
        assertNull(store(target, "milk", bytes))
        assertTrue(bytes.contentEquals(target.readBytes()))
    }

    @Test
    fun `oversized legacy preset cannot be read by the relinker`() {
        val target = temporary.newFile("legacy.milk")
        java.io.RandomAccessFile(target, "rw").use { it.setLength(MilkAssetAdmission.MAX_PRESET_BYTES + 1) }
        assertTrue(runCatching { MilkAssetAdmission.readPresetText(target) }.exceptionOrNull() is MilkAssetAdmission.Rejected)
    }

    @Test
    fun `TGA dimension bombs and malformed RLE never replace a valid texture`() {
        val target = File(temporary.root, "image.tga").apply { writeBytes(tga()) }
        assertNotNull(store(target, "tga", tga(width = 8193)))
        val brokenRle = tga().copyOf(22).also {
            it[2] = 10
            it[18] = 0x81.toByte() // Claims two pixels for a one-pixel image.
        }
        assertNotNull(store(target, "tga", brokenRle))
        assertTrue(tga().contentEquals(target.readBytes()))
    }

    @Test
    fun `DDS validates size depth arrays mips and minimum payload`() {
        val valid = dds()
        assertEquals(64L, TextureHeaderAdmission.dds(valid, valid.size.toLong()))
        val huge = valid.copyOf().also { put32(it, 16, 0x7fffffff) }
        assertTrue(runCatching { TextureHeaderAdmission.dds(huge, huge.size.toLong()) }.isFailure)
        assertTrue(runCatching { TextureHeaderAdmission.dds(valid, 128) }.isFailure)
        val array = valid.copyOf(149).also {
            "DX10".toByteArray().copyInto(it, 84)
            put32(it, 140, Int.MAX_VALUE)
        }
        assertTrue(runCatching { TextureHeaderAdmission.dds(array, array.size.toLong()) }.isFailure)
    }

    @Test
    fun `truncated DX10 textures do not replace a valid destination`() {
        val target = File(temporary.root, "image.dds").apply { writeBytes(dds()) }
        val truncated = dds().copyOf(149).also {
            "DX10".toByteArray().copyInto(it, 84)
            put32(it, 128, 28) // DXGI_FORMAT_R8G8B8A8_UNORM needs 64 bytes for 4x4.
            put32(it, 132, 3)
            put32(it, 140, 1)
        }
        assertNotNull(store(target, "dds", truncated))
        assertTrue(dds().contentEquals(target.readBytes()))
        assertNull(store(target, "dds", truncated.copyOf(148 + 64)))
    }

    @Test
    fun `decoded pixel footprint limits wide images even within dimension limit`() {
        assertEquals(4L, TextureHeaderAdmission.decodedBytes(1, 1))
        assertTrue(runCatching { TextureHeaderAdmission.decodedBytes(8192, 8192) }.isFailure)
        assertTrue(runCatching { TextureHeaderAdmission.decodedBytes(1, 1, Long.MAX_VALUE) }.isFailure)
    }

    private fun store(
        target: File,
        extension: String,
        bytes: ByteArray,
        budget: MilkAssetAdmission.Budget = MilkAssetAdmission.Budget(),
    ): String? = MilkAssetAdmission.store(target, extension, budget, true) { ByteArrayInputStream(bytes) }

    private fun tga(width: Int = 1): ByteArray =
        ByteArray(21).also {
            it[2] = 2
            it[12] = width.toByte()
            it[13] = (width ushr 8).toByte()
            it[14] = 1
            it[16] = 24
        }

    private fun dds(): ByteArray =
        ByteArray(136).also {
            "DDS ".toByteArray().copyInto(it)
            put32(it, 4, 124)
            put32(it, 12, 4)
            put32(it, 16, 4)
            put32(it, 76, 32)
            "DXT1".toByteArray().copyInto(it, 84)
        }

    private fun put32(
        bytes: ByteArray,
        offset: Int,
        value: Int,
    ) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (8 * index)).toByte() }
    }

    private companion object {
        const val PRESET = "[preset00]\nfDecay=0.97"
    }
}
