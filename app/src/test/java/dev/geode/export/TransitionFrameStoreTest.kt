package dev.geode.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer

class TransitionFrameStoreTest {
    @Test
    fun `consumer may check before producer finishes and still receive the frame once`() {
        val store = TransitionFrameStore()
        val frame = frame()
        assertNull(store.take())
        assertTrue(store.publish { frame })
        assertSame(frame, store.take())
        assertNull(store.take())
        assertFalse(store.publish { error("Consumed boundaries must skip readback") })
    }

    @Test
    fun `pending frame is not overwritten by duplicate end of input`() {
        val store = TransitionFrameStore()
        val frame = frame()
        assertTrue(store.publish { frame })
        assertFalse(store.publish { error("Duplicate publication must skip readback") })
        assertSame(frame, store.take())
    }

    @Test
    fun `consumer teardown rejects a later producer and avoids allocation`() {
        val store = TransitionFrameStore()
        store.close()
        store.close()
        assertFalse(store.publish { error("Cancelled consumer must skip readback") })
        assertNull(store.take())
    }

    @Test
    fun `consumer teardown drops a pending frame`() {
        val store = TransitionFrameStore()
        store.publish { frame() }
        store.close()
        assertNull(store.take())
        assertFalse(store.publish { error("Closed boundaries cannot be reused") })
    }

    @Test
    fun `readback failure closes the boundary and preserves its cause`() {
        val store = TransitionFrameStore()
        val failure = IllegalStateException("readback failed")
        try {
            store.publish { throw failure }
            fail("Readback failure was swallowed")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        assertNull(store.take())
        assertFalse(store.publish { error("Failed boundaries must skip readback") })
    }

    @Test
    fun `ownership is already transferred before upload can fail`() {
        val store = TransitionFrameStore()
        val frame = frame()
        store.publish { frame }
        val upload = store.take()
        assertSame(frame, upload)
        // The composition can retain the store for the whole export without retaining the upload buffer.
        assertNull(store.take())
        assertFalse(store.publish { error("Upload failures cannot trigger another readback") })
    }

    @Test
    fun `many retained boundary stores do not retain consumed frames`() {
        val boundaries = List(100) { TransitionFrameStore() }
        for (store in boundaries) {
            val captured = frame()
            store.publish { captured }
            assertSame(captured, store.take())
        }
        for (store in boundaries) assertNull(store.take())
    }

    @Test
    fun `frame bytes use checked arithmetic including extreme dimensions`() {
        assertEquals(33_177_600, TransitionFrameStore.rgbaByteCount(3840, 2160))
        assertEquals(4, TransitionFrameStore.rgbaByteCount(1, 1))
        for ((width, height) in listOf(0 to 1, 1 to 0, -1 to 1, 1 to -1, 65536 to 65536, Int.MAX_VALUE to Int.MAX_VALUE)) {
            try {
                TransitionFrameStore.rgbaByteCount(width, height)
                fail("Accepted dimensions $width x $height")
            } catch (_: IllegalArgumentException) {
                // Invalid dimensions are rejected before direct allocation or GL access.
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `truncated frame cannot reach GL upload`() {
        TransitionFrameStore.CapturedFrame(2, 2, ByteBuffer.allocate(15))
    }

    private fun frame() = TransitionFrameStore.CapturedFrame(2, 2, ByteBuffer.allocate(16))
}
