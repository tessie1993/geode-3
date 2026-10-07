package dev.geode.analysis

/** Validates a snapshot before native work and again before exposing its result. */
internal class AnalysisFrameGate(
    private val publicationLock: Any,
    private val currentRateHz: () -> Int,
    private val isCurrentSnapshot: () -> Boolean,
) {
    fun <T> run(
        configuredRateHz: Int,
        analyze: () -> T,
        publish: (T) -> Unit,
    ): Boolean {
        synchronized(publicationLock) {
            if (!matches(configuredRateHz)) return false
        }
        // A capture boundary must never wait on a native FFT. Its result is checked below.
        val frame = analyze()
        synchronized(publicationLock) {
            if (!matches(configuredRateHz)) return false
            publish(frame)
        }
        return true
    }

    private fun matches(configuredRateHz: Int): Boolean =
        currentRateHz() == configuredRateHz && isCurrentSnapshot()
}
