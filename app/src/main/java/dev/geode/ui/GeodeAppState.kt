package dev.geode.ui

import android.view.Display
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.geode.R
import dev.geode.ui.theme.StoneIcon

/**
 * The app's top-level destinations. Native controls and optical lenses share these identities.
 *
 * Each destination carries its own label and icon so the bar is built from this list rather
 * than from a parallel one — a destination cannot exist without a way to reach it, and a
 * `when` over these has no `else`, so adding a screen is a compile error until it is handled.
 */
enum class GeodeDestination(
    @param:StringRes val labelRes: Int,
    val icon: StoneIcon,
) {
    PLAYER(R.string.nav_player, StoneIcon.PLAY),
    LIBRARY(R.string.nav_library, StoneIcon.LIBRARY),
    VISUALS(R.string.nav_visuals, StoneIcon.VISUALIZER),
    STUDIO(R.string.nav_studio, StoneIcon.STUDIO),
    SETTINGS(R.string.nav_settings, StoneIcon.SETTINGS),
}

@Stable
class GeodeAppState(
    dest: GeodeDestination = GeodeDestination.PLAYER,
    expanded: Boolean = false,
    searching: Boolean = false,
    bootDone: Boolean = false,
    orbitOpen: Boolean = false,
    history: List<GeodeDestination> = emptyList(),
) {
    var dest by mutableStateOf(dest)

    var expanded by mutableStateOf(expanded)

    var searching by mutableStateOf(searching)

    var bootDone by mutableStateOf(bootDone)

    var orbitOpen by mutableStateOf(orbitOpen)
        private set

    private var history by mutableStateOf(history.takeLast(MAX_HISTORY))

    var predictiveBackProgress by mutableFloatStateOf(0f)
        private set

    var externalDisplay: Display? = null
        internal set

    val onPlayer: Boolean get() = dest == GeodeDestination.PLAYER

    val previousDestination: GeodeDestination?
        get() = history.lastOrNull() ?: GeodeDestination.PLAYER.takeUnless { onPlayer }

    val canNavigateBack: Boolean
        get() = orbitOpen || previousDestination != null

    fun openSearch() {
        closeOrbit()
        searching = true
    }

    fun closeSearch() {
        searching = false
    }

    fun expand() {
        closeOrbit()
        expanded = true
    }

    fun collapse() {
        expanded = false
    }

    fun navigateTo(destination: GeodeDestination) {
        closeOrbit()
        predictiveBackProgress = 0f
        if (destination == dest) return
        history =
            if (destination == GeodeDestination.PLAYER) {
                emptyList()
            } else {
                val existing = history.indexOf(destination)
                if (existing >= 0) history.take(existing) else (history + dest).takeLast(MAX_HISTORY)
            }
        dest = destination
    }

    fun resetToPlayer() {
        closeOrbit()
        history = emptyList()
        predictiveBackProgress = 0f
        dest = GeodeDestination.PLAYER
    }

    fun openOrbit() {
        if (!expanded && !searching) orbitOpen = true
    }

    fun closeOrbit() {
        orbitOpen = false
        predictiveBackProgress = 0f
    }

    fun toggleOrbit() {
        if (orbitOpen) closeOrbit() else openOrbit()
    }

    fun updateBackProgress(progress: Float) {
        predictiveBackProgress = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    }

    /** Commit only after Android completes the gesture; cancellation only resets its progress. */
    fun navigateBack() {
        predictiveBackProgress = 0f
        when {
            searching -> closeSearch()
            expanded -> collapse()
            orbitOpen -> closeOrbit()
            previousDestination != null -> {
                dest = previousDestination!!
                history = history.dropLast(1)
            }
        }
    }

    companion object {
        private const val MAX_HISTORY = 20

        val Saver: Saver<GeodeAppState, List<Any>> =
            Saver(
                // Saved as the enum name, not its ordinal: reordering the navigation bar then
                // cannot silently restore a process-death survivor onto a different screen.
                save = { listOf(it.dest.name, it.expanded, it.searching, it.bootDone, it.orbitOpen, it.history.map { d -> d.name }) },
                restore = {
                    GeodeAppState(
                        dest =
                            GeodeDestination.entries.firstOrNull { d -> d.name == it.getOrNull(0) }
                                ?: GeodeDestination.PLAYER,
                        expanded = it.getOrNull(1) as? Boolean ?: false,
                        searching = it.getOrNull(2) as? Boolean ?: false,
                        bootDone = it.getOrNull(3) as? Boolean ?: false,
                        orbitOpen = it.getOrNull(4) as? Boolean ?: false,
                        history =
                            (it.getOrNull(5) as? List<*>)
                                ?.mapNotNull { name ->
                                    GeodeDestination.entries.firstOrNull { d -> d.name == name }
                                }.orEmpty(),
                    )
                },
            )
    }
}

@Composable
fun rememberGeodeAppState(externalDisplay: Display?): GeodeAppState {
    val state = rememberSaveable(saver = GeodeAppState.Saver) { GeodeAppState() }
    state.externalDisplay = externalDisplay
    return state
}
