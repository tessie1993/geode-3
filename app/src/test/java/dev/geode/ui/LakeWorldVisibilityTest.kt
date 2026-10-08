package dev.geode.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LakeWorldVisibilityTest {
    @Test
    fun nativePreviewDestinationsNeverRunWorldInParallel() {
        assertFalse(LakeWorldVisibility.isActive(GeodeDestination.VISUALS))
        assertFalse(LakeWorldVisibility.isActive(GeodeDestination.STUDIO))
        assertTrue(LakeWorldVisibility.isActive(GeodeDestination.PLAYER))
        assertTrue(LakeWorldVisibility.isActive(GeodeDestination.LIBRARY))
        assertTrue(LakeWorldVisibility.isActive(GeodeDestination.SETTINGS))
    }

    @Test
    fun exclusiveOwnersSuspendWorld() {
        assertFalse(LakeWorldVisibility.isActive(GeodeDestination.PLAYER, expanded = true))
        assertFalse(LakeWorldVisibility.isActive(GeodeDestination.PLAYER, searching = true))
        assertFalse(LakeWorldVisibility.isActive(GeodeDestination.PLAYER, onboarding = true))
        assertFalse(LakeWorldVisibility.isActive(GeodeDestination.PLAYER, errorDialog = true))
        assertFalse(LakeWorldVisibility.isActive(GeodeDestination.PLAYER, secondScreen = true))
        assertFalse(LakeWorldVisibility.isActive(GeodeDestination.PLAYER, exporting = true))
    }

    @Test
    fun orbitOwnsWorldAfterNativePreviewContentIsRemoved() {
        assertTrue(LakeWorldVisibility.isActive(GeodeDestination.VISUALS, orbitOpen = true))
        assertTrue(LakeWorldVisibility.isActive(GeodeDestination.STUDIO, orbitOpen = true))
        assertFalse(LakeWorldVisibility.isActive(GeodeDestination.VISUALS, orbitOpen = true, expanded = true))
    }
}
