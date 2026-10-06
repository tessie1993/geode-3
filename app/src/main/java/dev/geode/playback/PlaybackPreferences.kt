package dev.geode.playback

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dev.geode.analysis.PlaybackMath
import dev.geode.data.PlayerPrefs

/** Applies saved options on the player thread, independently of an Activity's lifetime. */
@OptIn(UnstableApi::class)
internal class PlaybackPreferences(
    private val player: Player,
    private val configureReplayGain: (Int, Float, Boolean) -> Unit,
    private val applyBitPerfect: (Boolean) -> Unit,
) {
    private var applied: PlayerPrefs? = null

    fun apply(prefs: PlayerPrefs) {
        val next = prefs.coerced()
        val previous = applied
        if (previous?.speed != next.speed || previous?.pitchSemitones != next.pitchSemitones) {
            player.playbackParameters = PlaybackParameters(next.speed, PlaybackMath.semitonesToRatio(next.pitchSemitones))
        }
        (player as? ExoPlayer)?.let { exoPlayer ->
            if (previous?.skipSilence != next.skipSilence) exoPlayer.skipSilenceEnabled = next.skipSilence
            if (previous?.pauseOnNoisy != next.pauseOnNoisy) exoPlayer.setHandleAudioBecomingNoisy(next.pauseOnNoisy)
        }
        (player as? NativePlayer)?.let { nativePlayer ->
            if (previous?.crossfadeMs != next.crossfadeMs ||
                previous?.crossfadeCurve != next.crossfadeCurve ||
                previous?.gapless != next.gapless
            ) {
                nativePlayer.applyPrefs(next.crossfadeMs, next.crossfadeCurve, next.gapless)
            }
            if (previous?.bitPerfect != next.bitPerfect) applyBitPerfect(next.bitPerfect)
        }
        if (previous?.replayGainMode != next.replayGainMode ||
            previous?.replayGainPreampDb != next.replayGainPreampDb ||
            previous?.replayGainClipGuard != next.replayGainClipGuard
        ) {
            configureReplayGain(next.replayGainMode, next.replayGainPreampDb, next.replayGainClipGuard)
        }
        // Only changed preferences drive the player. A controller may change these directly;
        // an unrelated settings edit must not reset the active queue's shuffle/repeat choices.
        if (previous?.shuffle != next.shuffle) player.shuffleModeEnabled = next.shuffle
        if (previous?.repeatMode != next.repeatMode) player.repeatMode = next.repeatMode
        applied = next
    }
}
