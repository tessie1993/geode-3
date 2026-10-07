package dev.geode.audio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.geode.engine.audio.PcmSink
import dev.geode.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Exercises real Android capture, accepting silent PCM from a headless emulator. */
@RunWith(AndroidJUnit4::class)
class MicCaptureLifecycleTest {
    @Test(timeout = 30_000)
    fun realMicrophonePublishesPcmAndStopsAcrossRepeatedSessions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals(
            "Permission changes are restricted to the disposable debug package",
            "dev.geode.debug",
            context.packageName,
        )
        val hasMicrophone = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        val device = "${Build.MODEL}, API ${Build.VERSION.SDK_INT}, FEATURE_MICROPHONE=$hasMicrophone"
        Log.i(TAG, "Mic lifecycle capability: $device")
        assumeTrue("Skipping real microphone lifecycle: $device", hasMicrophone)

        // The CI runner restricts itself to an emulator before granting this permission.
        assertEquals(
            "CI must grant RECORD_AUDIO before instrumentation",
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO),
        )
        val probe = PcmProbe()
        val capture = MicCapture(context, probe)
        val launch = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(launch) as MainActivity
        try {
            assertForeground(activity)
            saveMeminfo("before")
            repeat(3) { cycle ->
                assertForeground(activity)
                probe.beginSession()
                val reportedRate = AtomicInteger()
                try {
                    val failure = capture.start { reportedRate.set(it) }
                    assertNull("Advertised microphone failed to open on $device", failure)
                    assertTrue("Microphone pump was not active", capture.active)
                    assertTrue("No real PCM callback in session ${cycle + 1}", probe.firstPcm.await(5, TimeUnit.SECONDS))
                    assertNull("Invalid PCM callback", probe.invalidPcm.get())
                    assertTrue("A positive input rate must be reported", reportedRate.get() > 0)
                    assertEquals(reportedRate.get(), capture.sampleRateHz)
                } finally {
                    capture.stop()
                    probe.stopCompleted.set(true)
                }
                assertFalse("Microphone remained active after stop", capture.active)
                val writesAtStop = probe.writes.get()
                // Observe several native read timeouts without requiring audible input or sleeping.
                assertFalse("PCM arrived after stop returned", probe.afterStop.await(250, TimeUnit.MILLISECONDS))
                assertEquals("PCM write count changed after stop", writesAtStop, probe.writes.get())
                assertNull("Invalid PCM callback", probe.invalidPcm.get())
                Log.i(
                    TAG,
                    "Mic lifecycle session ${cycle + 1}: rate=${reportedRate.get()} Hz, writes=$writesAtStop, stopped",
                )
            }
        } finally {
            try {
                capture.stop()
                saveMeminfo("after")
            } finally {
                instrumentation.runOnMainSync { activity.finish() }
                instrumentation.waitForIdleSync()
            }
        }
    }

    private fun assertForeground(activity: MainActivity) {
        val state = AtomicReference<Lifecycle.State>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { state.set(activity.lifecycle.currentState) }
        assertEquals("Microphone capture requires a foreground MainActivity", Lifecycle.State.RESUMED, state.get())
        Log.i(TAG, "Mic lifecycle foreground: MainActivity RESUMED")
    }

    private fun saveMeminfo(phase: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val descriptor = instrumentation.uiAutomation.executeShellCommand("dumpsys meminfo ${context.packageName}")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            File(context.cacheDir, "mic-lifecycle-$phase-meminfo.txt").outputStream().use { output -> input.copyTo(output) }
        }
    }

    private class PcmProbe : PcmSink {
        val writes = AtomicInteger()
        val invalidPcm = AtomicReference<String?>()
        val stopCompleted = AtomicBoolean(true)

        @Volatile
        var firstPcm = CountDownLatch(1)
            private set

        @Volatile
        var afterStop = CountDownLatch(1)
            private set

        fun beginSession() {
            firstPcm = CountDownLatch(1)
            afterStop = CountDownLatch(1)
            writes.set(0)
            invalidPcm.set(null)
            stopCompleted.set(false)
        }

        override fun write(
            interleaved: FloatArray,
            frameCount: Int,
            sourceChannelCount: Int,
        ) {
            val validShape = frameCount > 0 && sourceChannelCount == 1 && frameCount <= interleaved.size
            if (!validShape || !(0 until frameCount).all { interleaved[it].isFinite() }) {
                invalidPcm.compareAndSet(
                    null,
                    "frames=$frameCount, channels=$sourceChannelCount, capacity=${interleaved.size}",
                )
            }
            writes.incrementAndGet()
            if (stopCompleted.get()) afterStop.countDown()
            firstPcm.countDown()
        }
    }

    private companion object {
        const val TAG = "MicLifecycleTest"
    }
}
