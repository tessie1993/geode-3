package dev.geode.engine.bridge

object GeodeNative {
    init {
        System.loadLibrary("geode")
    }

    external fun version(): String

    external fun featureFrameFloats(): Int

    external fun analysisCreate(
        sampleRate: Int,
        fftSize: Int,
        hopRateHz: Float,
    ): Long

    external fun analysisDestroy(handle: Long)

    external fun analysisSetSampleRate(
        handle: Long,
        sampleRate: Int,
    )

    external fun analysisSetTuning(
        handle: Long,
        sensitivity: Float,
        refractoryMs: Float,
        attackSeconds: Float,
        releaseSeconds: Float,
    )

    external fun analysisReset(handle: Long)

    external fun analysisAnalyze(
        handle: Long,
        mid: FloatArray,
        side: FloatArray?,
        dtSeconds: Float,
        out: FloatArray,
    )

    external fun analysisPush(
        handle: Long,
        interleaved: FloatArray,
        frames: Int,
        channels: Int,
    )

    external fun analysisPull(
        handle: Long,
        out: FloatArray,
    ): Boolean

    external fun analysisKey(handle: Long): String

    external fun pulseReplay(
        flux: FloatArray,
        rms: FloatArray,
        hopRateHz: Float,
        sensitivity: Float,
        refractoryMs: Float,
        out: FloatArray,
    )

    external fun drumsCreate(
        bandCount: Int,
        hopRateHz: Float,
        sampleRate: Int,
    ): Long

    external fun drumsDestroy(handle: Long)

    external fun drumsStep(
        handle: Long,
        bands: FloatArray,
        out: FloatArray,
    )

    external fun dspCreate(
        sampleRate: Int,
        channels: Int,
    ): Long

    external fun dspDestroy(handle: Long)

    external fun dspBandCount(): Int

    external fun dspBandCenterHz(band: Int): Float

    external fun dspSetEnabled(
        handle: Long,
        enabled: Boolean,
    )

    external fun dspSetBand(
        handle: Long,
        band: Int,
        millibels: Int,
    )

    external fun dspBand(
        handle: Long,
        band: Int,
    ): Int

    external fun dspSetBassBoost(
        handle: Long,
        permille: Int,
    )

    external fun dspSetLoudnessMb(
        handle: Long,
        millibels: Int,
    )

    external fun dspSetGainDb(
        handle: Long,
        db: Float,
    )

    external fun dspSetCrossfeed(
        handle: Long,
        enabled: Boolean,
    )

    external fun dspSetLimiter(
        handle: Long,
        enabled: Boolean,
    )

    external fun dspReset(handle: Long)

    /** [buffer] must be a direct float buffer holding [frames] interleaved frames; processed in place. */
    external fun dspProcess(
        handle: Long,
        buffer: java.nio.ByteBuffer,
        frames: Int,
    )

    /**
     * Reads the tags of [fd], which native closes. Fills [texts] with UTF-8 title, artist, album, album artist,
     * genre, comment; [ints] with year, track, duration ms, art bytes; [gains] with track gain dB, track peak,
     * album gain dB, album peak. Returns the mask of gains present (1, 2, 4, 8), -1 when unreadable.
     */
    external fun tagsRead(
        fd: Int,
        texts: Array<ByteArray?>,
        ints: IntArray,
        gains: FloatArray,
    ): Int

    /** [assets] is the app's `android.content.res.AssetManager`; typed as Any because this module has no Android SDK. */
    external fun vizCreate(
        assets: Any,
        cacheDir: String,
    ): Long

    external fun vizDestroy(handle: Long)

    external fun vizParamNames(): String

    external fun vizSetParams(
        handle: Long,
        values: FloatArray,
    )

    external fun vizSetParam(
        handle: Long,
        name: String,
        value: Float,
    ): Boolean

    external fun vizSetFeatures(
        handle: Long,
        frame: FloatArray,
    )

    external fun vizSetReducedMotion(
        handle: Long,
        on: Boolean,
    )

    external fun vizSetLayer(
        handle: Long,
        sceneId: String,
        mix: Float,
        blendMode: Int,
    )

    external fun vizSetTransition(
        handle: Long,
        id: String,
        durationMs: Long,
    )

    external fun vizBeginParamMorph(
        handle: Long,
        seconds: Float,
    )

    external fun vizSetTouch(
        handle: Long,
        xy: FloatArray,
        points: Int,
    )

    external fun vizQueueTouchStroke(
        handle: Long,
        nx: Float,
        ny: Float,
        ndx: Float,
        ndy: Float,
        dt: Float,
        strength: Float,
    )

    external fun vizSetFluidInjection(
        handle: Long,
        force: String?,
        dye: String?,
    )

    external fun vizLoadMilkPreset(
        handle: Long,
        path: String,
    )

    external fun vizReloadMilkPreset(handle: Long)

    external fun vizSetMilkTextureDir(
        handle: Long,
        dir: String,
    )

    external fun vizTakeMilkPresetLoaded(handle: Long): String?

    external fun vizPushPcm(
        handle: Long,
        mono: FloatArray,
        count: Int,
    )

    external fun vizSetCustomShader(
        handle: Long,
        sceneId: String,
        fragmentSource: String,
    )

    external fun vizCustomShader(
        handle: Long,
        sceneId: String,
    ): String?

    external fun vizSetLfo(
        handle: Long,
        slot: Int,
        config: FloatArray,
    )

    external fun vizSetAdsr(
        handle: Long,
        slot: Int,
        config: FloatArray,
    )

    external fun vizSetThermal(
        handle: Long,
        platformStatus: Int,
        headroom: Float,
    )

    external fun vizSetPacedFps(
        handle: Long,
        fps: Float,
    )

    external fun vizSetOffscreen(
        handle: Long,
        on: Boolean,
    )

    external fun vizKnows(
        handle: Long,
        sceneId: String,
    ): Boolean

    external fun vizSceneIds(handle: Long): String

    external fun vizLastError(handle: Long): String

    external fun vizSurfaceCreated(handle: Long)

    external fun vizSurfaceChanged(
        handle: Long,
        width: Int,
        height: Int,
    )

    external fun vizSetScene(
        handle: Long,
        sceneId: String,
    ): Boolean

    external fun vizWarmTransition(
        handle: Long,
        id: String,
    )

    external fun vizCut(handle: Long)

    external fun vizRender(
        handle: Long,
        timeSeconds: Double,
        targetFbo: Int,
    )

    external fun vizReleaseScenes(handle: Long)

    /** Not RT-safe on the native side: only call this on a dsp handle that no audio thread is processing. */
    external fun dspSetSampleRate(
        handle: Long,
        sampleRate: Int,
    )

    external fun dspSampleRate(handle: Long): Int

    /**
     * Full-frame RGBA8 overlay drawn last, premultiplied alpha, over the finished composite. `pixels`
     * is Android's `Bitmap.getPixels` ARGB layout, width*height entries; null (or a non-positive
     * size) clears it. Any thread; latched for the next frame.
     */
    external fun vizSetOverlay(
        handle: Long,
        pixels: IntArray?,
        width: Int,
        height: Int,
    )

    /**
     * Full-frame RGBA8 underlay blended UNDER/INTO the scene: [blend] 0 = screen (replaces the scene
     * where it is black), 1 = multiply, 2 = add; [amount] 0..1. Same pixel layout as [vizSetOverlay];
     * null (or a non-positive size) clears it. Any thread; latched for the next frame.
     */
    external fun vizSetUnderlay(
        handle: Long,
        pixels: IntArray?,
        width: Int,
        height: Int,
        blend: Int,
        amount: Float,
    )

    /**
     * An AAudio microphone stream, not yet opened. [preferUnprocessed] asks for the UNPROCESSED input
     * preset (API 28+), else VOICE_RECOGNITION. 0 when native could not allocate it. One handle belongs
     * to one thread at a time: [micStart], then [micRead], then [micStop] and [micDestroy] on the
     * thread that read.
     */
    external fun micCreate(preferUnprocessed: Boolean): Long

    /** Opens a low-latency float mono stream (EXCLUSIVE, then SHARED) and starts it. */
    external fun micStart(handle: Long): Boolean

    /**
     * Blocks up to [timeoutNanos] for up to [maxFrames] mono frames and writes them to the front of [dst].
     * Returns the frames read; 0 when none arrived in time or while a disconnected stream is being
     * reopened (the reopening runs inside these calls, on the calling thread), negative when the stream
     * is gone for good.
     */
    external fun micRead(
        handle: Long,
        dst: FloatArray,
        maxFrames: Int,
        timeoutNanos: Long,
    ): Int

    external fun micStop(handle: Long)

    external fun micDestroy(handle: Long)

    external fun micSampleRate(handle: Long): Int

    /** The device's channel count; [micRead] always returns mono. */
    external fun micChannels(handle: Long): Int

    external fun micFramesPerBurst(handle: Long): Int

    external fun micBufferFrames(handle: Long): Int

    /** AAudio's value: 0 exclusive, 1 shared. */
    external fun micSharingMode(handle: Long): Int

    /** AAudio's value: 10 none, 12 low latency. */
    external fun micPerformanceMode(handle: Long): Int

    /** The last AAudio result that was not OK, 0 if none. */
    external fun micLastError(handle: Long): Int

    /** Rises by one for every successful (re)open, so a change means the sample rate may differ. */
    external fun micGeneration(handle: Long): Int

    /** Peak, 0..1, of the last non-empty [micRead]. */
    external fun micLastPeak(handle: Long): Float
}
