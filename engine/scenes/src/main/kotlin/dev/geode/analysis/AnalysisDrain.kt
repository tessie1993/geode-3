package dev.geode.analysis

import kotlinx.coroutines.CancellationException

internal fun requireAnalysisWanted(stillWanted: () -> Boolean) {
    if (!stillWanted()) throw CancellationException("analysis cancelled")
}

/** The native pull can complete after cancellation; check again before retaining its result. */
internal fun drainAnalysisFrames(
    stillWanted: () -> Boolean,
    pull: () -> Boolean,
    consume: () -> Unit,
) {
    while (true) {
        requireAnalysisWanted(stillWanted)
        if (!pull()) return
        requireAnalysisWanted(stillWanted)
        consume()
    }
}
