package dev.geode.data

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class MilkAssetImportRoutesTest {
    @Test
    fun invalidTextureIsRejectedByBothPickerAndFolderRoutes() {
        withStore { context, root ->
            val input = File(root, "huge.tga").apply { writeBytes(tga(width = 8193)) }
            val direct = TextureStore(context).importDetailed(listOf(Uri.fromFile(input)))
            assertFalse(direct.results.single().imported)
            val pack = MilkPackImporter.import(listOf(entry(input)), File(root, "pack"))
            assertEquals(0, pack.total)
            assertEquals(1, pack.skipped)
            assertNotNull(pack.results.single().skipReason)
            assertTrue(direct.textures.isEmpty())
        }
    }

    @Test
    fun invalidDirectReplacementRetainsTextureAndFolderReportsPartialSuccess() {
        withStore { context, root ->
            val input = File(root, "valid.tga").apply { writeBytes(tga()) }
            val store = TextureStore(context)
            val first = store.importDetailed(listOf(Uri.fromFile(input)))
            assertTrue(first.results.single().imported)
            val stored = File(first.textures.single().path)
            input.writeBytes("DDS ".toByteArray())
            assertFalse(store.importDetailed(listOf(Uri.fromFile(input))).results.single().imported)
            assertTrue(tga().contentEquals(stored.readBytes()))
            val packDir = File(root, "pack")
            val pack = MilkPackImporter.import(
                listOf(
                    entry(input),
                    MilkPackImporter.Entry("working.milk") { ByteArrayInputStream("[preset00]\nfDecay=0.97".toByteArray()) },
                ),
                packDir,
            )
            assertEquals(1, pack.presets)
            assertEquals(0, pack.textures)
            assertEquals(1, pack.skipped)
            assertTrue(File(packDir, "working.milk").isFile)
            assertFalse(File(packDir, "textures/valid.tga").exists())
        }
    }

    @Test
    fun excessFolderEntriesAreReportedAndTheirStreamsAreNeverOpened() {
        withStore { _, root ->
            var opened = 0
            val entries = List(MilkAssetAdmission.MAX_BATCH_FILES + 1) { index ->
                MilkPackImporter.Entry("preset$index.milk") {
                    opened++
                    null
                }
            }
            val report = MilkPackImporter.import(entries, File(root, "pack"))
            assertEquals(MilkAssetAdmission.MAX_BATCH_FILES, opened)
            assertEquals(entries.size, report.skipped)
            assertNotNull(report.issue)
            assertEquals(0, report.total)
        }
    }

    @Test
    fun shippedPresetsRemainAdmissibleWithoutRewriting() {
        withStore { context, root ->
            val names = context.assets.list("milk").orEmpty().filter { it.endsWith(".milk") }
            assertTrue(names.isNotEmpty())
            val report = MilkPackImporter.import(
                names.map { name -> MilkPackImporter.Entry(name) { context.assets.open("milk/$name") } },
                File(root, "pack"),
            )
            assertEquals(names.size, report.presets)
            assertEquals(0, report.skipped)
        }
    }

    private fun withStore(body: (Context, File) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(app.cacheDir.toPath(), "milk-admission-").toFile()
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this

            override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        }
        try {
            body(context, root)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun entry(file: File): MilkPackImporter.Entry = MilkPackImporter.Entry(file.name) { file.inputStream() }

    private fun tga(width: Int = 1): ByteArray =
        ByteArray(21).also {
            it[2] = 2
            it[12] = width.toByte()
            it[13] = (width ushr 8).toByte()
            it[14] = 1
            it[16] = 24
        }
}
