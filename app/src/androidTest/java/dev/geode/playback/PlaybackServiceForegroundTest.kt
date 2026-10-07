package dev.geode.playback

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.geode.ui.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.sin

/** Exercises the production service with direct shared-player playback, without a controller. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackServiceForegroundTest {
    @Test(timeout = 35_000)
    fun directPlaybackPostsMediaNotificationAndSurvivesForegroundDeadline() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals("This playback fixture belongs to the disposable debug app", "dev.geode.debug", context.packageName)
        val fixture = File(context.cacheDir, "playback-service-foreground.wav")
        writeWave(fixture)
        val launch = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(launch) as MainActivity
        val started = CountDownLatch(1)
        val failed = CountDownLatch(1)
        val error = AtomicReference<PlaybackException?>()
        var playback: PlaybackSession? = null
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) started.countDown()
                }

                override fun onPlayerError(playerError: PlaybackException) {
                    error.set(playerError)
                    failed.countDown()
                    started.countDown()
                }
            }
        try {
            instrumentation.runOnMainSync { PlaybackService.stop(context) }
            awaitCondition("Previous playback service did not stop") { runningService(context) == null }
            instrumentation.runOnMainSync {
                val shared = PlaybackEngine.acquireForUi(context)
                playback = shared
                shared.player.addListener(listener)
                shared.player.setMediaItem(
                    MediaItem
                        .Builder()
                        .setUri(Uri.fromFile(fixture))
                        .setMediaMetadata(MediaMetadata.Builder().setTitle("Foreground playback fixture").build())
                        .build(),
                )
                shared.player.prepare()
                shared.player.play()
            }
            assertTrue("Real PCM fixture did not start", started.await(5, TimeUnit.SECONDS))
            assertNull("PCM fixture failed to play", error.get())
            instrumentation.runOnMainSync {
                assertTrue("Shared player must actually be playing", playback!!.player.isPlaying)
                // Exercise the actionless production start, without a controller connection that
                // could implicitly register the service's session and mask the original bug.
                PlaybackService.ensureRunning(context)
            }
            awaitCondition("Playback service never posted its foreground media notification") {
                runningService(context)?.foreground == true && hasMediaNotification(context)
            }
            Log.i(TAG, "Direct playback: production service foreground with media notification")
            val initialPosition = AtomicReference<Long>()
            instrumentation.runOnMainSync { initialPosition.set(playback!!.player.currentPosition) }
            // Run 37 died about ten seconds after start. Observe beyond that deadline while the
            // actual player keeps reading PCM, rather than accepting a session or UI state alone.
            assertFalse("Playback failed after foreground promotion", failed.await(12, TimeUnit.SECONDS))
            assertTrue("Playback service lost foreground state", runningService(context)?.foreground == true)
            assertTrue("Media notification disappeared during playback", hasMediaNotification(context))
            instrumentation.runOnMainSync {
                val player = playback!!.player
                assertTrue("Playback stopped after the foreground deadline", player.isPlaying)
                assertTrue("PCM playback position did not advance", player.currentPosition > initialPosition.get() + 1_000)
            }
            Log.i(TAG, "Direct playback: still foreground and advancing beyond the startup deadline")
        } finally {
            instrumentation.runOnMainSync {
                playback?.let {
                    it.player.removeListener(listener)
                    it.player.stop()
                    it.player.clearMediaItems()
                }
                PlaybackService.stop(context)
                if (playback != null) PlaybackEngine.releaseUi()
                activity.finish()
            }
            instrumentation.waitForIdleSync()
            fixture.delete()
        }
    }

    private fun awaitCondition(
        message: String,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(100)
        }
        assertTrue(message, condition())
    }

    @Suppress("DEPRECATION")
    private fun runningService(context: Context): ActivityManager.RunningServiceInfo? =
        context
            .getSystemService(ActivityManager::class.java)
            .getRunningServices(Int.MAX_VALUE)
            .firstOrNull { it.service.className == PlaybackService::class.java.name }

    private fun hasMediaNotification(context: Context): Boolean =
        context
            .getSystemService(NotificationManager::class.java)
            .activeNotifications
            .any { it.notification.category == Notification.CATEGORY_TRANSPORT }

    private fun writeWave(file: File) {
        val frames = SAMPLE_RATE * DURATION_SECONDS
        val dataBytes = frames * CHANNELS * 2
        val header =
            ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(dataBytes + 36)
                put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1)
                putShort(CHANNELS.toShort())
                putInt(SAMPLE_RATE)
                putInt(SAMPLE_RATE * CHANNELS * 2)
                putShort((CHANNELS * 2).toShort())
                putShort(16)
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataBytes)
            }
        val chunk = ByteBuffer.allocate(1_024 * CHANNELS * 2).order(ByteOrder.LITTLE_ENDIAN)
        file.outputStream().use { output ->
            output.write(header.array())
            var frame = 0
            while (frame < frames) {
                chunk.clear()
                repeat(minOf(1_024, frames - frame)) {
                    val sample = (sin(2 * PI * 220 * frame / SAMPLE_RATE) * 4_000).toInt().toShort()
                    repeat(CHANNELS) { chunk.putShort(sample) }
                    frame++
                }
                output.write(chunk.array(), 0, chunk.position())
            }
        }
    }

    private companion object {
        const val TAG = "PlaybackServiceTest"
        const val SAMPLE_RATE = 44_100
        const val CHANNELS = 2
        const val DURATION_SECONDS = 60
    }
}
