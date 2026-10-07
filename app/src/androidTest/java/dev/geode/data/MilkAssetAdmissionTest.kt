package dev.geode.data

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files

class MilkAssetAdmissionTest {
    @Test
    fun folderRejectsSameHugeTgaAsDirectAdmissionAndKeepsExistingFiles() {
        withDirectory { dir ->
            val existing = File(dir, "kept.milk").apply { writeText(PRESET) }
            val tga = ByteArray(19).apply {
                this[2] = 2
                this[12] = 0xff.toByte()
                this[13] = 0xff.toByte()
                this[14] = 0xff.toByte()
                this[15] = 0xff.toByte()
                this[16] = 32
            }
            assertTrue(runCatching {
                MilkAssetAdmission.stage(ByteArrayInputStream(tga), File(dir, "direct.stage"), "tga")
            }.isFailure)
            val report = MilkPackImporter.import(
                listOf(entry("valid.milk", PRESET.toByteArray()), entry("huge.tga", tga)), dir,
            )
            assertEquals(0, report.total)
            assertFalse(File(dir, "valid.milk").exists())
            assertEquals(PRESET, existing.readText())
            assertTrue(report.outcomes.all { !it.imported })
        }
    }

    @Test
    fun unknownLengthPresetIsBounded() {
        withDirectory { dir ->
            var delivered = 0L
            val endless = object : InputStream() {
                override fun read(): Int = 'a'.code

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    buffer.fill('a'.code.toByte(), offset, offset + length)
                    delivered += length
                    return length
                }
            }
            assertTrue(runCatching { MilkAssetAdmission.stage(endless, File(dir, "stage"), "milk") }.isFailure)
            assertTrue(delivered <= MilkAssetAdmission.MAX_PRESET_BYTES + 16 * 1024)
        }
    }

    @Test
    fun excessiveShallowFolderIsRejectedBeforeOpeningProviders() {
        withDirectory { dir ->
            var opened = false
            val entries = List(MilkAssetAdmission.MAX_ENTRIES + 1) {
                MilkPackImporter.Entry("$it.milk") {
                    opened = true
                    ByteArrayInputStream(PRESET.toByteArray())
                }
            }
            assertEquals(0, MilkPackImporter.import(entries, dir).total)
            assertFalse(opened)
        }
    }

    @Test
    fun aggregateBudgetRejectsBeforeOverflow() {
        val budget = MilkAssetAdmission.Budget()
        repeat(8) { budget.consume(MilkAssetAdmission.MAX_TEXTURE_BYTES.toInt()) }
        assertTrue(runCatching { budget.consume(1) }.isFailure)
        assertEquals(MilkAssetAdmission.MAX_PACK_BYTES, budget.bytes)
    }

    @Test
    fun validLegacyPresetImportsWithoutChangingBytes() {
        withDirectory { dir ->
            val bytes = PRESET.toByteArray()
            assertEquals(1, MilkPackImporter.import(listOf(entry("valid.milk", bytes)), dir).presets)
            assertTrue(File(dir, PresetStore.milkFileName("valid.milk")).readBytes().contentEquals(bytes))
        }
    }

    @Test
    fun validTgaIsAdmittedThroughBothRoutes() {
        withDirectory { dir ->
            val tga = ByteArray(21).apply {
                this[2] = 2
                this[12] = 1
                this[14] = 1
                this[16] = 24
            }
            MilkAssetAdmission.stage(ByteArrayInputStream(tga), File(dir, "direct.stage"), "tga")
            val report = MilkPackImporter.import(listOf(entry("pixel.tga", tga)), dir)
            assertEquals(1, report.textures)
            assertTrue(File(dir, "textures/pixel.tga").readBytes().contentEquals(tga))
        }
    }

    @Test
    fun rawDibRetainsSupportWithoutBitmapFactory() {
        withDirectory { dir ->
            val dib = ByteArray(16).apply {
                this[0] = 12
                this[4] = 1
                this[6] = 1
                this[8] = 1
                this[10] = 24
            }
            MilkAssetAdmission.stage(ByteArrayInputStream(dib), File(dir, "direct.stage"), "dib")
            assertEquals(1, MilkPackImporter.import(listOf(entry("pixel.dib", dib)), dir).textures)
        }
    }

    @Test
    fun headerOnlyDdsCannotPassAdmission() {
        withDirectory { dir ->
            assertTrue(runCatching {
                MilkAssetAdmission.stage(ByteArrayInputStream("DDS ".toByteArray()), File(dir, "stage"), "dds")
            }.isFailure)
        }
    }

    private fun entry(name: String, bytes: ByteArray) = MilkPackImporter.Entry(name) { ByteArrayInputStream(bytes) }

    private fun withDirectory(block: (File) -> Unit) {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val dir = Files.createTempDirectory(cache.toPath(), "milk-admission-test").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private companion object {
        const val PRESET = "[preset00]\nfRating=3.0\nzoom=1.0\n"
    }
}
