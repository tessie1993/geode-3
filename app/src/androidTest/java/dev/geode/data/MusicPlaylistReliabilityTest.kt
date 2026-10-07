package dev.geode.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MusicPlaylistReliabilityTest {
    private lateinit var root: File
    private lateinit var context: Context

    @Before
    fun createDirectory() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(target.cacheDir, "playlist-test-${UUID.randomUUID()}")
        check(root.mkdirs())
        context =
            object : ContextWrapper(target) {
                override fun getFilesDir(): File = root
            }
    }

    @After
    fun removeDirectory() {
        root.deleteRecursively()
    }

    @Test
    fun simultaneousSameTitleImportsPersistSeparately() {
        val left = MusicPlaylistStore(context)
        val right = MusicPlaylistStore(context)
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val futures =
                listOf(left, right).mapIndexed { index, store ->
                    executor.submit(
                        Callable {
                            check(start.await(10, TimeUnit.SECONDS))
                            store.saveUnique(MusicPlaylist("Journey", listOf("content://track/$index")))
                        },
                    )
                }
            start.countDown()
            val saved = futures.map { checkNotNull(it.get(30, TimeUnit.SECONDS)) }
            assertEquals(setOf("Journey", "Journey (2)"), saved.map { it.name }.toSet())
            assertEquals(saved.toSet(), MusicPlaylistStore(context).list().toSet())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun writeFailurePreservesPreviousBytesAndDoesNotClaimImportSuccess() {
        val store = MusicPlaylistStore(context)
        val original = MusicPlaylist("Journey", listOf("content://track/original"))
        assertTrue(store.save(original))
        val file = File(root, "music-playlists/Journey.json")
        val previous = file.readBytes()
        // A non-empty directory at the temp path forces a real atomic publication failure.
        val blockedTemp = File(file.absolutePath + AtomicWrite.TEMP_SUFFIX)
        check(blockedTemp.mkdirs())
        File(blockedTemp, "keep").writeText("blocked")
        assertFalse(store.save(original.copy(trackUris = emptyList())))
        assertArrayEquals(previous, file.readBytes())

        val copy = File(root, "music-playlists/${PresetStore.safeFileName("Journey (2)")}.json.tmp")
        check(copy.mkdirs())
        File(copy, "keep").writeText("blocked")
        assertNull(store.saveUnique(original))
        assertEquals(listOf(original), store.list())
        assertArrayEquals(previous, file.readBytes())
    }

    @Test
    fun unreadableExistingFileIsNeverOverwrittenByImport() {
        val store = MusicPlaylistStore(context)
        val file = File(root, "music-playlists/Journey.json")
        file.writeText("broken older data")
        val saved = checkNotNull(store.saveUnique(MusicPlaylist("Journey")))
        assertEquals("Journey (2)", saved.name)
        assertEquals("broken older data", file.readText())
    }
}
