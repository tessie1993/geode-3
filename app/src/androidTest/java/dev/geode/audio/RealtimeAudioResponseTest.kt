package dev.geode.audio

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.geode.analysis.AudioFeatures
import dev.geode.playback.PlaybackSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin

/**
 * Exercises the real WAV extractor, Media3 audio sink, PCM tap and C++ analyzer.
 * These tests establish decoded-audio responsiveness, not speaker-to-screen latency.
 * The pause regression deliberately requires the stale-feature bug to be fixed.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class RealtimeAudioResponseTest {
    @Test
    fun decodedFrequencyChangesReachFeaturesAndNewSilentSourceClearsThem() =
        withSession("frequency-and-source-change") { probe ->
            probe.play(probe.wav("low", frequencyHz = LOW_HZ))
            val low = probe.await("low tone reaches the native analyzer") { it.features.hasSignal() }

            val previousEpoch = low.epoch
            probe.play(probe.wav("high", frequencyHz = HIGH_HZ))
            val high =
                probe.await("new high tone changes the spectrum") {
                    it.epoch > previousEpoch &&
                        it.frames >= FFT_FRAMES &&
                        it.features.hasSignal() &&
                        it.features.centroid > low.features.centroid + MIN_CENTROID_SHIFT
                }
            assertTrue("frequency response must change the bands", !high.features.bands.contentEquals(low.features.bands))

            probe.play(probe.wav("silence", frequencyHz = 0.0))
            probe.await("a new silent source clears old energy and events") {
                it.epoch > high.epoch && it.frames >= FFT_FRAMES && it.features.isQuiet()
            }
        }

    @Test
    fun seekingFromToneIntoSilenceClearsThePreviousEpoch() =
        withSession("seek-to-silence") { probe ->
            probe.play(probe.wav("tone-then-silence", frequencyHz = LOW_HZ, toneUntilMs = TONE_END_MS))
            val tone = probe.await("tone before seek") { it.features.hasSignal() }

            onMain { probe.session.player.seekTo(SILENT_SEEK_MS) }

            probe.await("seek flushes the source and replaces its features") {
                it.epoch > tone.epoch && it.frames >= FFT_FRAMES && it.features.isQuiet()
            }
        }

    @Test
    fun pausedPlaybackDoesNotKeepPublishingTheLastToneAsLiveAudio() =
        withSession("pause-freshness") { probe ->
            probe.play(probe.wav("continuous-tone", frequencyHz = LOW_HZ))
            probe.await("tone before pause") { it.features.hasSignal() }
            onMain { probe.session.player.pause() }

            var previousFrames = -1L
            var unchangedSinceMs = SystemClock.elapsedRealtime()
            probe.await("PCM producer stops after pause") {
                if (it.frames != previousFrames) {
                    previousFrames = it.frames
                    unchangedSinceMs = it.atMs
                }
                it.atMs - unchangedSinceMs >= PRODUCER_IDLE_MS
            }

            // Ambient camera movement can continue; stale music energy and impulses cannot.
            probe.await("paused source clears stale audio features", timeoutMs = QUIET_TIMEOUT_MS) {
                it.features.isQuiet()
            }
        }

    private fun withSession(
        name: String,
        block: (Probe) -> Unit,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = IsolatedPreferencesContext(instrumentation.targetContext)
        val fixtureDirectory = File(context.cacheDir, "audio-response-${UUID.randomUUID()}").also { it.mkdirs() }
        val previousInterestHook = AudioBus.onInterestChanged
        val session =
            onMain {
                check(!AudioBus.hasConsumers) { "Audio response tests require an idle app process" }
                AudioBus.clear()
                PlaybackSession(context).also { AudioBus.addConsumer() }
            }
        val probe = Probe(session, fixtureDirectory)
        try {
            check(session.exoPlayer != null) { "Fixture must exercise the production Media3 path" }
            block(probe)
        } finally {
            try {
                probe.writeEvidence(context, name)
            } finally {
                onMain {
                    AudioBus.removeConsumer()
                    session.release()
                    AudioBus.onInterestChanged = previousInterestHook
                    AudioBus.clear()
                }
                runBlocking { session.analysis.closeAndJoin() }
                fixtureDirectory.deleteRecursively()
                context.clearTestPreferences()
            }
        }
    }

    private data class Observation(
        val atMs: Long,
        val epoch: Int,
        val frames: Long,
        val features: AudioFeatures,
    )

    private class Probe(
        val session: PlaybackSession,
        private val directory: File,
    ) {
        private val evidence = StringBuilder("elapsed_ms,epoch,frames,rms,centroid,max_band,beat,transient\n")

        fun play(file: File) =
            onMain {
                session.player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                session.player.prepare()
                session.player.play()
            }

        fun await(
            description: String,
            timeoutMs: Long = PLAYBACK_TIMEOUT_MS,
            predicate: (Observation) -> Boolean,
        ): Observation {
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            var last: Observation
            do {
                last =
                    Observation(
                        atMs = SystemClock.elapsedRealtime(),
                        epoch = session.sampleRing.epoch,
                        frames = session.sampleRing.writtenFrames,
                        features = session.analysis.features.value,
                    )
                val f = last.features
                evidence.append(
                    "${last.atMs},${last.epoch},${last.frames},${f.rms},${f.centroid}," +
                        "${f.bands.maxOrNull()},${f.beat},${f.transient}\n",
                )
                check(onMain { session.player.playerError } == null) { "Media3 failed while waiting for $description" }
                if (predicate(last)) return last
                SystemClock.sleep(POLL_MS)
            } while (SystemClock.elapsedRealtime() < deadline)
            val playerState = onMain { "state=${session.player.playbackState}, playing=${session.player.isPlaying}" }
            throw AssertionError(
                "$description: timeout after $timeoutMs ms; $playerState, epoch=${last.epoch}, " +
                    "frames=${last.frames}, rms=${last.features.rms}, centroid=${last.features.centroid}, " +
                    "maxBand=${last.features.bands.maxOrNull()}",
            )
        }

        fun wav(
            name: String,
            frequencyHz: Double,
            toneUntilMs: Long = FIXTURE_DURATION_MS,
        ): File {
            val frames = (SAMPLE_RATE * FIXTURE_DURATION_MS / MS_PER_SECOND).toInt()
            val payloadBytes = frames * CHANNELS * PCM_BYTES
            val buffer = ByteBuffer.allocate(WAV_HEADER_BYTES + payloadBytes).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
            buffer.putInt(payloadBytes + WAV_HEADER_BYTES - RIFF_PREFIX_BYTES)
            buffer.put("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            buffer.putInt(PCM_FORMAT_BYTES)
            buffer.putShort(PCM_FORMAT.toShort())
            buffer.putShort(CHANNELS.toShort())
            buffer.putInt(SAMPLE_RATE)
            buffer.putInt(SAMPLE_RATE * CHANNELS * PCM_BYTES)
            buffer.putShort((CHANNELS * PCM_BYTES).toShort())
            buffer.putShort((PCM_BYTES * BITS_PER_BYTE).toShort())
            buffer.put("data".toByteArray(Charsets.US_ASCII))
            buffer.putInt(payloadBytes)
            repeat(frames) { frame ->
                val active = frequencyHz > 0 && frame.toLong() * MS_PER_SECOND < toneUntilMs * SAMPLE_RATE
                val amplitude = if (active) TONE_AMPLITUDE * sin(2 * PI * frequencyHz * frame / SAMPLE_RATE) else 0.0
                val sample = (amplitude * Short.MAX_VALUE).toInt().toShort()
                repeat(CHANNELS) { buffer.putShort(sample) }
            }
            return File(directory, "$name.wav").also { it.writeBytes(buffer.array()) }
        }

        fun writeEvidence(
            context: Context,
            name: String,
        ) {
            val destination = File(context.getExternalFilesDir(null) ?: context.filesDir, "audio-response")
            destination.mkdirs()
            File(destination, "$name.csv").writeText(evidence.toString())
        }
    }

    /** Keeps test playback preferences isolated from app/user settings. */
    private class IsolatedPreferencesContext(base: Context) : ContextWrapper(base) {
        private val prefix = "audio-response-${UUID.randomUUID()}-"
        private val names = mutableSetOf<String>()

        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(
            name: String,
            mode: Int,
        ): SharedPreferences {
            val scopedName = prefix + name
            names.add(scopedName)
            return super.getSharedPreferences(scopedName, mode)
        }

        fun clearTestPreferences() = names.forEach { deleteSharedPreferences(it) }
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 2
        const val PCM_BYTES = 2
        const val PCM_FORMAT = 1
        const val PCM_FORMAT_BYTES = 16
        const val WAV_HEADER_BYTES = 44
        const val RIFF_PREFIX_BYTES = 8
        const val BITS_PER_BYTE = 8
        const val MS_PER_SECOND = 1000L
        const val FIXTURE_DURATION_MS = 10_000L
        const val TONE_END_MS = 4000L
        const val SILENT_SEEK_MS = 6000L
        const val LOW_HZ = 187.5
        const val HIGH_HZ = 6000.0
        const val TONE_AMPLITUDE = 0.5
        const val FFT_FRAMES = 2048L
        const val MIN_CENTROID_SHIFT = 0.15f
        const val SIGNAL_RMS = 0.2f
        const val SIGNAL_BAND = 0.1f
        const val QUIET_LEVEL = 0.001f
        const val POLL_MS = 20L
        const val PRODUCER_IDLE_MS = 250L
        const val PLAYBACK_TIMEOUT_MS = 8000L
        const val QUIET_TIMEOUT_MS = 2500L

        fun AudioFeatures.hasSignal(): Boolean = rms > SIGNAL_RMS && bands.any { it > SIGNAL_BAND }

        fun AudioFeatures.isQuiet(): Boolean =
            rms < QUIET_LEVEL &&
                bands.all { it < QUIET_LEVEL } &&
                !beat && !downbeat && !sectionBoundary && !drop && !arrival &&
                transient < QUIET_LEVEL && kick < QUIET_LEVEL && snare < QUIET_LEVEL && hat < QUIET_LEVEL

        fun <T> onMain(block: () -> T): T {
            var result: Result<T>? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
            return checkNotNull(result).getOrThrow()
        }
    }
}
