package dev.geode.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.geode.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay

/** Scenic material observes the existing analyzer; it never owns a capture or renderer. */
@Composable
internal fun TidalPlayerArtwork(
    viewModel: PlayerViewModel,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    val running = rememberTidalMotionRunning(reducedMotion) && live
    val energy = produceState(0f, running) {
        if (!running) {
            value = 0f
            return@produceState
        }
        while (true) {
            val target = viewModel.features.value.rms.coerceIn(0f, 1f)
            value += (target - value) * 0.24f
            delay(SPECTRUM_TICK_MS)
        }
    }
    val configuration = LocalConfiguration.current
    val screenHeight = (configuration.screenHeightDp * 0.32f).coerceIn(180f, 300f)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val height = minOf(screenHeight, maxWidth.value * 0.92f).coerceAtLeast(140f).dp
        TidalWaterOrb(
            modifier = Modifier.fillMaxWidth().height(height),
            reducedMotion = reducedMotion,
            energy = { energy.value },
        )
    }
}
