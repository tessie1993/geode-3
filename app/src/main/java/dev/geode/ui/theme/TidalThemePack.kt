package dev.geode.ui.theme

import androidx.compose.ui.graphics.Color
import dev.geode.R

/** The waterglass default keeps the complete mineral-pack fallback and interaction sounds. */
internal object TidalThemePack {
    const val SLUG = "tidal-glass"

    fun create(base: ThemePack): ThemePack =
        base.copy(
            slug = SLUG,
            name = "Tidal Glass",
            stone = "waterglass, river stone and moss",
            isLight = false,
            palette =
                StonePalette(
                    background = Color(0xFF071719),
                    backgroundDeep = Color(0xFF020B0D),
                    surface = Color(0xFF123235),
                    surfaceHigh = Color(0xFF244D4E),
                    primary = Color(0xFF9AE3DE),
                    secondary = Color(0xFFE9D5B0),
                    accent = Color(0xFFD1F6EA),
                    glow = Color(0xFFB4F1E7),
                    onBackground = Color(0xFFF2FBF7),
                    onSurface = Color(0xFFF2FBF7),
                    muted = Color(0xFFBCD6D1),
                    outline = Color(0xFF567D79),
                    danger = Color(0xFFFFACA0),
                ),
            material =
                base.material.copy(
                    ambientPortrait = R.drawable.tidal_forest,
                    ambientLandscape = R.drawable.tidal_forest,
                    ambientSquare = R.drawable.tidal_forest,
                ),
        )
}

val ThemePack.isTidalGlass: Boolean
    get() = slug == TidalThemePack.SLUG
