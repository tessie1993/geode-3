package dev.geode.ui.theme

/** Only the reserved built-ins use this kit; imported packs retain their authored surfaces. */
val ThemePack.isJellyGlass: Boolean
    get() = slug in JellyGlassSlugs

private val JellyGlassSlugs =
    setOf(
        "tidal-glass",
        "lapis-lazuli",
        "sugilite",
        "amethyst",
        "clear-quartz",
        "azurite",
        "firestone",
        "kyanite",
        "malachite",
        "mookaite",
        "onyx",
    )
