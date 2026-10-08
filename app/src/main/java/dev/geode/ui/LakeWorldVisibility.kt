package dev.geode.ui

/** The world yields its render budget before a foreground native renderer or opaque owner opens. */
internal object LakeWorldVisibility {
    fun isActive(
        destination: GeodeDestination,
        expanded: Boolean = false,
        searching: Boolean = false,
        onboarding: Boolean = false,
        errorDialog: Boolean = false,
        secondScreen: Boolean = false,
        orbitOpen: Boolean = false,
        exporting: Boolean = false,
    ): Boolean =
        (orbitOpen || (destination != GeodeDestination.VISUALS && destination != GeodeDestination.STUDIO)) &&
            !expanded &&
            !searching &&
            !onboarding &&
            !errorDialog &&
            !secondScreen &&
            !exporting
}
