package dev.geode.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dev.geode.audio.AudioFxController
import dev.geode.audio.AudioFxPresets
import dev.geode.audio.PcmRingBuffer
import dev.geode.audio.TapRenderersFactory
import dev.geode.audio.dsp.NativeDspProcessor
import dev.geode.data.GeodePrefsFiles
import dev.geode.data.PlayerPrefsRepository
import dev.geode.data.PlayerPrefsStore
import dev.geode.data.SharedPrefsPlayerPrefsRepository
import dev.geode.engine.audio.AudioPresentationClock
import dev.geode.engine.audio.PcmSink
import dev.geode.engine.audio.SampleRing
import dev.geode.engine.audioandroid.PcmTap
import dev.geode.engine.audioandroid.SinkClockDriver
import dev.geode.engine.audioandroid.TapBoundaryListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackSession internal constructor(
    context: Context,
) {
    val ring = PcmRingBuffer()

    internal val sampleRing = SampleRing(capacityFrames = 1 shl 16, channelCount = 2)

    val analysis = dev.geode.analysis.AnalysisEngine(sampleRing)

    @Volatile
    var onAudioFormat: ((sampleRateHz: Int, channelCount: Int, encoding: Int) -> Unit)? = null

    internal val presentationClock = AudioPresentationClock()

    internal val clockDriver = SinkClockDriver(presentationClock)

    internal val captureSink =
        object : PcmSink {
            override fun write(
                interleaved: FloatArray,
                frameCount: Int,
                sourceChannelCount: Int,
            ) {
                ring.writeInterleaved(interleaved, frameCount, sourceChannelCount)
                sampleRing.write(interleaved, frameCount, sourceChannelCount)
            }

            override fun discontinuity() {
                ring.discontinuity()
                sampleRing.beginEpoch()
                analysis.reset()
            }
        }

    internal val tap =
        PcmTap(captureSink) { format ->
            val hook = onAudioFormat
            if (hook != null) {
                hook(format.sampleRateHz, format.channelCount, format.encoding)
            } else {
                analysis.sampleRateHz = format.sampleRateHz
            }
        }.apply {
            boundaryListener =
                TapBoundaryListener { ended, endedFrames, begun ->
                    clockDriver.onTapBoundary(ended, endedFrames, begun)
                }
        }

    internal val dsp = NativeDspProcessor()

    private val prefsFiles = GeodePrefsFiles(context)

    private val playerPrefsStore = PlayerPrefsStore(prefsFiles.player)

    private val initialPlayerPrefs = playerPrefsStore.load()

    // WAKE_MODE_LOCAL takes a partial wake lock while playback is active, so the CPU
    // cannot doze mid-track with the screen off. Not the WIFI variant: nothing streams.
    val exoPlayer: ExoPlayer =
        ExoPlayer
            .Builder(context, TapRenderersFactory(context, tap, clockDriver, dsp = listOf(dsp)))
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    context,
                    androidx.media3.extractor.ExtractorsFactory {
                        androidx.media3.extractor
                            .DefaultExtractorsFactory()
                            .createExtractors() +
                            dev.geode.audio.AiffExtractor()
                    },
                ),
            ).setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            ).setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

    val player: Player = exoPlayer

    val audioFx = AudioFxController(prefsFiles.audioFx, AudioFxPresets.all(context), dsp)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val playerPrefsRepository: PlayerPrefsRepository = SharedPrefsPlayerPrefsRepository(playerPrefsStore, scope)

    val replayGain = ReplayGain(context.contentResolver, scope) { audioFx.setGainDb(it) }

    private val playbackPreferences = PlaybackPreferences(exoPlayer, replayGain::configure)

    val sleepTimer = SleepTimer(player, scope)

    private val interestHook: () -> Unit = { syncAnalysis() }

    init {
        // Media buttons and Android Auto can create the service before any UI exists. Apply
        // persisted options before this player is exposed to a MediaLibrarySession.
        playbackPreferences.apply(initialPlayerPrefs)
        player.addListener(replayGain)
        scope.launch {
            // The repository initially exposes defaults while its disk load is in flight.
            // Do not let that temporary value overwrite the startup snapshot above.
            playerPrefsRepository.loaded()
            playerPrefsRepository.prefs.collect { playbackPreferences.apply(it) }
        }
        dev.geode.audio.AudioBus.onInterestChanged = interestHook
        syncAnalysis()
        scope.launch {
            analysis.features.collect {
                dev.geode.audio.AudioBus
                    .publish(it)
            }
        }
    }

    private fun syncAnalysis() {
        if (dev.geode.audio.AudioBus.hasConsumers) analysis.start(scope) else analysis.stop()
    }

    val playbackWanted: Boolean
        get() =
            player.playWhenReady &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED

    internal fun release() {
        analysis.close()
        if (dev.geode.audio.AudioBus.onInterestChanged === interestHook) {
            dev.geode.audio.AudioBus.onInterestChanged = null
        }
        scope.cancel()
        onAudioFormat = null
        audioFx.release()
        player.removeListener(replayGain)
        player.release()
        dsp.release()
    }
}

object PlaybackEngine {
    private var app: Context? = null
    private var session: PlaybackSession? = null
    private var uiHolds = 0
    private var serviceHolds = 0

    private fun rebindTo(context: Context): Context {
        val current = context.applicationContext
        if (app !== current) {
            // Dropping the reference is not enough: the old session owns an ExoPlayer, the audio
            // effect chain, the PCM tap and a coroutine scope, none of which the GC reclaims.
            session?.release()
            session = null
            uiHolds = 0
            serviceHolds = 0
            app = current
        }
        return current
    }

    @Synchronized
    private fun sessionFor(context: Context): PlaybackSession {
        val current = rebindTo(context)
        return session ?: PlaybackSession(current).also { session = it }
    }

    @Synchronized
    fun acquireForUi(context: Context): PlaybackSession = sessionFor(context).also { uiHolds++ }

    @Synchronized
    fun releaseUi() {
        if (uiHolds > 0) uiHolds--
        releaseIfUnused()
    }

    @Synchronized
    fun acquireForService(context: Context): PlaybackSession = sessionFor(context).also { serviceHolds++ }

    @Synchronized
    fun releaseService() {
        if (serviceHolds > 0) serviceHolds--
        releaseIfUnused()
    }

    private fun releaseIfUnused() {
        if (uiHolds > 0 || serviceHolds > 0) return
        session?.release()
        session = null
        app = null
    }
}
