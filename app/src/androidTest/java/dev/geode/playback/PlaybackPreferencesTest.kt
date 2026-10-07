package dev.geode.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.geode.data.PlayerPrefs
import dev.geode.data.PlayerPrefsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.pow

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackPreferencesTest {
    @Test
    fun savedPreferencesApplyBeforeAnyUiAttaches() {
        withPlayer { context, player ->
            val storage = context.getSharedPreferences("playback-preferences-test", Context.MODE_PRIVATE)
            try {
                val store = PlayerPrefsStore(storage)
                store.save(
                    PlayerPrefs(
                        speed = 1.5f,
                        pitchSemitones = 3f,
                        skipSilence = true,
                        pauseOnNoisy = true,
                        shuffle = true,
                        repeatMode = Player.REPEAT_MODE_ALL,
                        replayGainMode = ReplayGain.MODE_ALBUM,
                        replayGainPreampDb = -2f,
                        replayGainClipGuard = false,
                    ),
                )
                val gains = mutableListOf<Triple<Int, Float, Boolean>>()
                val preferences =
                    PlaybackPreferences(player) { mode, preamp, guard -> gains.add(Triple(mode, preamp, guard)) }

                preferences.apply(store.load())

                assertEquals(1.5f, player.playbackParameters.speed, 0.0001f)
                assertEquals(2.0.pow(3.0 / 12.0).toFloat(), player.playbackParameters.pitch, 0.0001f)
                assertTrue(player.skipSilenceEnabled)
                assertTrue(player.shuffleModeEnabled)
                assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
                assertEquals(listOf(Triple(ReplayGain.MODE_ALBUM, -2f, false)), gains)
            } finally {
                storage.edit().clear().apply()
            }
        }
    }

    @Test
    fun unrelatedSettingsDoNotResetControllerQueueChoices() {
        withPlayer { _, player ->
            var gainApplications = 0
            val preferences = PlaybackPreferences(player) { _, _, _ -> gainApplications++ }
            val initial = PlayerPrefs()
            preferences.apply(initial)

            // Simulate Android Auto or another controller changing the active session.
            player.shuffleModeEnabled = true
            player.repeatMode = Player.REPEAT_MODE_ONE
            preferences.apply(initial.copy(speed = 1.25f, keepScreenOn = true))

            assertEquals(1.25f, player.playbackParameters.speed, 0.0001f)
            assertTrue(player.shuffleModeEnabled)
            assertEquals(Player.REPEAT_MODE_ONE, player.repeatMode)
            assertEquals(1, gainApplications)
        }
    }

    @Test
    fun changedSettingsApplyWithoutReplayingUnchangedGain() {
        withPlayer { _, player ->
            val gains = mutableListOf<Triple<Int, Float, Boolean>>()
            val preferences =
                PlaybackPreferences(player) { mode, preamp, guard -> gains.add(Triple(mode, preamp, guard)) }
            val initial = PlayerPrefs(skipSilence = true, shuffle = true, repeatMode = Player.REPEAT_MODE_ALL)
            preferences.apply(initial)
            val edited =
                initial.copy(
                    skipSilence = false,
                    shuffle = false,
                    repeatMode = Player.REPEAT_MODE_OFF,
                    replayGainMode = ReplayGain.MODE_TRACK,
                    replayGainPreampDb = 2f,
                )

            preferences.apply(edited)
            preferences.apply(edited)

            assertFalse(player.skipSilenceEnabled)
            assertFalse(player.shuffleModeEnabled)
            assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)
            assertEquals(
                listOf(Triple(ReplayGain.MODE_OFF, 0f, true), Triple(ReplayGain.MODE_TRACK, 2f, true)),
                gains,
            )
        }
    }

    private fun withPlayer(block: (Context, ExoPlayer) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val player = ExoPlayer.Builder(context).build()
            try {
                block(context, player)
            } finally {
                player.release()
            }
        }
    }
}
