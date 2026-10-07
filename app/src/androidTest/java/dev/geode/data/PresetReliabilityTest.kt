package dev.geode.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.geode.render.bridge.NativeViz
import dev.geode.render.scene.SceneIds
import dev.geode.render.scene.SceneParams
import dev.geode.ui.BuiltInPresets
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Uses real Android JSON/stream behavior; executed by the Actions emulator gate. */
@RunWith(AndroidJUnit4::class)
class PresetReliabilityTest {
    private lateinit var root: File
    private val original = Preset("Ocean", SceneIds.DEFAULT, 0.6f, 0.12f)

    @Before
    fun createDirectory() {
        root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "preset-test-${UUID.randomUUID()}")
        check(root.mkdirs())
    }

    @After
    fun removeDirectory() {
        root.deleteRecursively()
    }

    @Test
    fun failedReplacementKeepsPreviousBytesAndReturnsFailure() {
        val good = PresetStore(root)
        val saved = saved(good.save(original))
        val file = checkNotNull(good.fileOf(saved.name))
        val previous = file.readBytes()
        val failing = PresetStore(root) { _, _ -> false }

        val result = failing.save(saved.copy(params = saved.params.copy(zoom = 2f)), replacing = saved)

        assertEquals(PresetWrite.Failed(PresetFailure.IO), result)
        assertArrayEquals(previous, file.readBytes())
        assertEquals(listOf(saved), good.list())
    }

    @Test
    fun failedCopyDoesNotReserveNameOrPublishAnEntry() {
        val failing = PresetStore(root) { _, _ -> false }
        assertEquals(PresetWrite.Failed(PresetFailure.IO), failing.save(original))
        assertTrue(failing.list().isEmpty())
        assertEquals(original.name, saved(PresetStore(root).save(original)).name)
    }

    @Test
    fun coldStartCopyAllocatesFromDiskAndKeepsBothLooks() {
        val first = PresetStore(root)
        saved(first.save(original))
        val before = checkNotNull(first.fileOf(original.name)).readBytes()
        // No repository list or UI refresh is performed on the second instance.
        val second = PresetStore(root)
        val copy = saved(second.save(original.copy(params = original.params.copy(zoom = 2f))))

        assertEquals("Ocean 2", copy.name)
        assertEquals(2, second.list().size)
        assertArrayEquals(before, checkNotNull(second.fileOf(original.name)).readBytes())
        assertEquals(2f, copy.params.zoom)
    }

    @Test
    fun concurrentCopiesAcrossStoreInstancesCannotOverwrite() {
        val left = PresetStore(root)
        val right = PresetStore(root)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val futures = (0 until 12).map { index ->
                executor.submit(Callable {
                    saved((if (index % 2 == 0) left else right).save(original)).name
                })
            }
            val names = futures.map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(12, names.toSet().size)
            assertEquals(names.toSet(), left.list().map { it.name }.toSet())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun staleConfirmationDoesNotReplaceNewerContent() {
        val store = PresetStore(root)
        val old = saved(store.save(original))
        val newer = saved(store.save(old.copy(params = old.params.copy(zoom = 2f)), replacing = old))
        val bytes = checkNotNull(store.fileOf(newer.name)).readBytes()

        assertEquals(PresetWrite.Failed(PresetFailure.CONFLICT), store.save(old, replacing = old))
        assertArrayEquals(bytes, checkNotNull(store.fileOf(newer.name)).readBytes())
    }

    @Test
    fun builtInNamesAndUnreadableFilesAreNeverOverwrittenByCopies() {
        val store = PresetStore(root)
        val reservedCopy = saved(store.save(original, reservedNames = setOf("Ocean")))
        assertEquals("Ocean 2", reservedCopy.name)
        val unreadable = File(root, "presets/Ocean.json").apply { writeText("future or corrupt content") }
        val previous = unreadable.readBytes()
        val next = saved(store.save(original))

        assertEquals("Ocean 3", next.name)
        assertArrayEquals(previous, unreadable.readBytes())
    }

    @Test
    fun replaceRetainsItsFolderAndNeedsExactlyOneMatchingRecord() {
        val store = PresetStore(root)
        val old = saved(store.save(original, folder = "Set"))
        saved(store.save(old.copy(params = old.params.copy(zoom = 2f)), folder = "", replacing = old))
        assertEquals("Set", store.folderOf(old.name))
        val duplicate = File(root, "presets/Other/Ocean.json")
        check(duplicate.parentFile!!.mkdirs())
        duplicate.writeText(PresetStore.toJson(old))
        assertEquals(PresetWrite.Failed(PresetFailure.CONFLICT), store.save(old, replacing = old))
    }

    @Test
    fun defaultAndBuiltInRequestedValuesRemainImportable() {
        assertEquals(original, PresetAdmission.decode(PresetStore.toJson(original)))
        for (preset in BuiltInPresets.ALL) {
            try {
                assertEquals(preset, PresetAdmission.decode(PresetStore.toJson(preset)))
            } catch (e: PresetAdmissionException) {
                fail("Built-in ${preset.name}: ${e.reason}/${e.field}")
            }
        }
    }

    @Test
    fun retainedSceneCatalogAndNativeSentinelsSurvive() {
        val native = NativeViz(InstrumentationRegistry.getInstrumentation().targetContext)
        try {
            assertTrue(native.create())
            // Independent C++ SceneRegistry inventory: omission from the app admission set fails.
            val nativeIds = native.sceneIds().toSet()
            assertEquals(nativeIds, PresetAdmission.sceneIds)
            for (scene in nativeIds) {
                val preset = original.copy(sceneId = scene)
                assertEquals(preset, PresetAdmission.decode(PresetStore.toJson(preset)))
            }
        } finally {
            native.destroy()
        }
        val result = PresetAdmission.decode(PresetStore.toJson(original))
        assertEquals(-1f, result.params.paletteBaseOverride)
        assertEquals(-1, result.params.paletteLut)
        assertEquals(SceneParams.DEFAULT.formDrive, result.params.formDrive)
    }

    @Test
    fun absentLegacyOptionalFieldsUseExistingDefaults() {
        val json = """{"name":"Ocean","sceneId":"${SceneIds.DEFAULT}","attack":0.6,"decay":0.12}"""
        assertEquals(original, PresetAdmission.decode(json))
    }

    @Test
    fun futureSchemaAndFieldsAreRejectedWithoutRewriting() {
        reject(PresetFailure.UNSUPPORTED_VERSION, document().put("schemaVersion", 2).toString())
        reject(PresetFailure.UNSUPPORTED_FIELD, document().put("futureCamera", 0.5).toString())
        reject(PresetFailure.UNSUPPORTED_SCENE, document().put("sceneId", "future_scene").toString())
    }

    @Test
    fun invalidNumericTypesOverflowAndEnumsAreRejected() {
        reject(PresetFailure.INVALID_VALUE, document().put("speed", "NaN").toString())
        reject(PresetFailure.INVALID_VALUE, document().put("speed", 1e39).toString())
        reject(PresetFailure.INVALID_VALUE, document().put("fluidIterations", -1).toString())
        reject(PresetFailure.INVALID_VALUE, document().put("fluidIterations", 20.5).toString())
        reject(PresetFailure.INVALID_VALUE, document().put("particleShape", 100).toString())
        reject(PresetFailure.INVALID_VALUE, document().put("symmetry", 11).toString())
        reject(PresetFailure.INVALID_VALUE, document().put("flowEnabled", "true").toString())
        reject(PresetFailure.INVALID_VALUE, document().put("paletteBaseOverride", -0.5).toString())
    }

    @Test
    fun validRequestedValuesAreNotCollapsedToOutputSafetyCaps() {
        val p = original.copy(params = original.params.copy(brightness = 2f, bloom = 1f, fluidQuality = 4))
        assertEquals(p, PresetAdmission.decode(PresetStore.toJson(p)))
        assertEquals(0.3f, PresetAdmission.decode(document().put("zoom", 0.3).toString()).params.zoom)
    }

    @Test
    fun malformedDuplicateNestedAndTrailingJsonCannotEnterStorage() {
        reject(PresetFailure.MALFORMED, """{"name":"one","name":"two"}""")
        reject(PresetFailure.MALFORMED, "{name:'unquoted'}")
        reject(PresetFailure.INVALID_VALUE, document().put("speed", JSONObject()).toString())
        reject(PresetFailure.MALFORMED, PresetStore.toJson(original) + " {}")
        reject(PresetFailure.MALFORMED, "{\"name\":")
    }

    @Test
    fun boundedReaderStopsAtLimitPlusOneEvenWithoutProviderMetadata() {
        var consumed = 0
        val endless = object : InputStream() {
            override fun read(): Int {
                consumed++
                return 'x'.code
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                consumed += length
                buffer.fill('x'.code.toByte(), offset, offset + length)
                return length
            }
        }
        try {
            PresetAdmission.readText(endless)
            fail("oversize provider accepted")
        } catch (e: PresetAdmissionException) {
            assertEquals(PresetFailure.TOO_LARGE, e.reason)
        }
        assertEquals(PresetAdmission.MAX_BYTES + 1, consumed)
        assertFalse(File(root, "presets").exists())
    }

    @Test
    fun malformedUtf8IsNotSilentlyReplaced() {
        try {
            PresetAdmission.readText(byteArrayOf(0xc3.toByte(), 0x28).inputStream())
            fail("malformed UTF-8 accepted")
        } catch (e: PresetAdmissionException) {
            assertEquals(PresetFailure.MALFORMED, e.reason)
        }
    }

    private fun document(): JSONObject = JSONObject(PresetStore.toJson(original))

    private fun saved(result: PresetWrite): Preset = when (result) {
        is PresetWrite.Saved -> result.preset
        is PresetWrite.Failed -> error("save failed: ${result.reason}/${result.field}")
    }

    private fun reject(reason: PresetFailure, json: String) {
        try {
            PresetAdmission.decode(json)
            fail("invalid preset accepted")
        } catch (e: PresetAdmissionException) {
            assertEquals(reason, e.reason)
        }
    }
}
