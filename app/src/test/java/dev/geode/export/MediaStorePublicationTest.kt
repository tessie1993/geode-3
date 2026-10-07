package dev.geode.export

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class MediaStorePublicationTest {
    @Test
    fun `publication happens only after the output has closed`() =
        runTest {
            val store = FakeStore()
            val result =
                publishMediaStoreVideo(store) {
                    store.open().use { it.write(1) }
                    true
                }
            assertEquals("new-row", result)
            assertEquals(listOf("insert", "open", "write", "close", "publish"), store.events)
            assertTrue(store.published)
        }

    @Test
    fun `open copy close and pending update failures roll back the newly inserted row`() =
        runTest {
            for (step in listOf("open", "write", "close", "publish")) {
                val store = FakeStore(failAt = step)
                val error =
                    try {
                        publishMediaStoreVideo(store) {
                            store.open().use { it.write(1) }
                            true
                        }
                        null
                    } catch (failure: IOException) {
                        failure
                    }
                assertSame("failure at $step", store.failure, error)
                assertEquals("rollback at $step", listOf("new-row"), store.deleted)
                assertFalse(store.published)
            }
        }

    @Test
    fun `zero updated rows is a failure and cannot be reported as saved`() =
        runTest {
            val store = FakeStore(updatedRows = 0)
            var failed = false
            try {
                publishMediaStoreVideo(store) { true }
            } catch (_: IOException) {
                failed = true
            }
            assertTrue(failed)
            assertEquals(listOf("new-row"), store.deleted)
        }

    @Test
    fun `failed insert never deletes existing user media`() =
        runTest {
            val store = FakeStore(insertedId = null)
            var failed = false
            try {
                publishMediaStoreVideo(store) { error("no row was created") }
            } catch (_: IOException) {
                failed = true
            }
            assertTrue(failed)
            assertEquals(listOf("insert"), store.events)
            assertTrue(store.deleted.isEmpty())
        }

    @Test
    fun `user cancellation rolls back without publishing`() =
        runTest {
            val store = FakeStore()
            assertNull(publishMediaStoreVideo(store) { false })
            assertEquals(listOf("insert", "delete"), store.events)
            assertEquals(listOf("new-row"), store.deleted)
        }

    @Test
    fun `coroutine cancellation during output closes and rolls back before completing`() =
        runTest {
            val store = FakeStore()
            var cancellationPropagated = false
            val job =
                launch {
                    try {
                        publishMediaStoreVideo(store) {
                            store.open().use { awaitCancellation() }
                        }
                    } catch (error: CancellationException) {
                        cancellationPropagated = true
                        throw error
                    }
                }
            runCurrent()
            job.cancel()
            job.join()
            assertTrue(cancellationPropagated)
            assertEquals(listOf("insert", "open", "close", "delete"), store.events)
        }

    @Test
    fun `rollback provider failure does not hide the original output error`() =
        runTest {
            val store = FakeStore(failAt = "write", failDelete = true)
            val error =
                try {
                    publishMediaStoreVideo(store) {
                        store.open().use { it.write(1) }
                        true
                    }
                    null
                } catch (failure: IOException) {
                    failure
                }
            assertSame(store.failure, error)
            assertEquals(listOf("new-row"), store.deleted)
        }

    @Test
    fun `cancellation during successful provider commit preserves the published file`() =
        runTest {
            val store = FakeStore()
            val job =
                launch {
                    val exportingJob = currentCoroutineContext()[Job]
                    store.onPublish = { exportingJob?.cancel() }
                    publishMediaStoreVideo(store) { true }
                }
            job.join()
            assertTrue(job.isCancelled)
            assertTrue(store.published)
            assertTrue(store.deleted.isEmpty())
            assertEquals(listOf("insert", "publish"), store.events)
        }

    private class FakeStore(
        private val failAt: String? = null,
        private val updatedRows: Int = 1,
        private val insertedId: String? = "new-row",
        private val failDelete: Boolean = false,
    ) : PendingMediaStore<String> {
        val events = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val failure = IOException("provider failure at $failAt")
        var published = false
        var onPublish: () -> Unit = {}

        override fun insert(): String? {
            record("insert")
            return insertedId
        }

        override fun publish(id: String): Int {
            assertEquals("new-row", id)
            record("publish")
            published = updatedRows == 1
            onPublish()
            return updatedRows
        }

        override fun delete(id: String) {
            deleted += id
            record("delete")
            if (failDelete) throw IOException("provider refused rollback")
        }

        fun open(): OutputStream {
            record("open")
            return object : OutputStream() {
                override fun write(value: Int) = record("write")

                override fun close() = record("close")
            }
        }

        private fun record(event: String) {
            events += event
            if (failAt == event) throw failure
        }
    }
}
