package dev.geode.ui

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeodeAppStateTest {
    @Test
    fun orbitDismissalPrecedesRouteBack() {
        val state = GeodeAppState()
        state.navigateTo(GeodeDestination.LIBRARY)
        state.openOrbit()
        state.navigateBack()
        assertFalse(state.orbitOpen)
        assertEquals(GeodeDestination.LIBRARY, state.dest)
        state.navigateBack()
        assertEquals(GeodeDestination.PLAYER, state.dest)
        assertFalse(state.canNavigateBack)
    }

    @Test
    fun cancelledBackPreservesDestinationAndHistory() {
        val state = GeodeAppState()
        state.navigateTo(GeodeDestination.LIBRARY)
        state.navigateTo(GeodeDestination.SETTINGS)
        state.updateBackProgress(0.7f)
        state.updateBackProgress(0f)
        assertEquals(GeodeDestination.SETTINGS, state.dest)
        assertEquals(GeodeDestination.LIBRARY, state.previousDestination)
        state.navigateBack()
        assertEquals(GeodeDestination.LIBRARY, state.dest)
    }

    @Test
    fun destinationSelectionClosesOrbitAndAvoidsBackLoops() {
        val state = GeodeAppState()
        state.navigateTo(GeodeDestination.LIBRARY)
        state.navigateTo(GeodeDestination.VISUALS)
        state.openOrbit()
        state.navigateTo(GeodeDestination.LIBRARY)
        assertFalse(state.orbitOpen)
        assertEquals(GeodeDestination.PLAYER, state.previousDestination)
        state.navigateTo(GeodeDestination.PLAYER)
        assertFalse(state.canNavigateBack)
    }

    @Test
    fun searchAndFullscreenCloseOrbit() {
        val state = GeodeAppState()
        state.openOrbit()
        state.openSearch()
        assertFalse(state.orbitOpen)
        state.openOrbit()
        assertFalse(state.orbitOpen)
        state.closeSearch()
        state.openOrbit()
        state.expand()
        assertFalse(state.orbitOpen)
        state.navigateBack()
        assertFalse(state.expanded)
    }

    @Test
    fun saverRestoresNamesHistoryAndAcceptsPreviousVersion() {
        val state = GeodeAppState(bootDone = true)
        state.navigateTo(GeodeDestination.LIBRARY)
        state.navigateTo(GeodeDestination.SETTINGS)
        state.openOrbit()
        val scope =
            object : SaverScope {
                override fun canBeSaved(value: Any): Boolean = true
            }
        val saved = with(GeodeAppState.Saver) { scope.save(state)!! }
        val restored = GeodeAppState.Saver.restore(saved)!!
        assertTrue(restored.bootDone)
        assertTrue(restored.orbitOpen)
        assertEquals(GeodeDestination.SETTINGS, restored.dest)
        assertEquals(GeodeDestination.LIBRARY, restored.previousDestination)
        val old = GeodeAppState.Saver.restore(listOf("LIBRARY", false, false, true))!!
        assertEquals(GeodeDestination.LIBRARY, old.dest)
        assertEquals(GeodeDestination.PLAYER, old.previousDestination)
    }

    @Test
    fun unknownSavedDestinationFallsBackToPlayer() {
        val restored = GeodeAppState.Saver.restore(listOf("REMOVED", false, false, false))!!
        assertEquals(GeodeDestination.PLAYER, restored.dest)
        assertFalse(restored.canNavigateBack)
    }
}
