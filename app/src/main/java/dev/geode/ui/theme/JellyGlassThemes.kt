package dev.geode.ui.theme

/** Only the reserved built-ins use this kit; imported packs retain their authored surfaces. */
val ThemePack.isJellyGlass: Boolean
    get() = isLivingLake
