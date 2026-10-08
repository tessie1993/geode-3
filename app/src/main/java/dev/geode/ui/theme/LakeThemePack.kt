package dev.geode.ui.theme

import dev.geode.R
import dev.geode.ui.lake.LakeMaterials

/** One built-in world. Imported packs continue to provide their own authored surfaces. */
object LakeThemePack {
    const val SLUG = "living-lake"

    fun create(): ThemePack {
        val art = R.drawable.ui2_lake_dawn
        return ThemePack(
            slug = SLUG,
            name = "Living Lake",
            stone = "waterglass, pale river stone and living light",
            isLight = true,
            palette = LakeMaterials.palette,
            motion =
                StoneMotion(
                    pressDurationMs = 110,
                    pressScale = 0.975f,
                    innerGlowGain = 0.35f,
                    releaseDurationMs = 300,
                    focusDurationMs = 150,
                    edgeLightGain = 0.6f,
                    selectedDurationMs = 220,
                    reduceMotionCrossfadeMs = 0,
                ),
            material =
                StoneMaterial(
                    tile = art,
                    glowOverlay = art,
                    refractionOverlay = art,
                    ambientPortrait = art,
                    ambientLandscape = R.drawable.ui2_lake_dawn_landscape,
                    ambientSquare = art,
                    backgroundOpacity = 1f,
                    surfaceOpacity = 0f,
                    disabledOpacity = 0.45f,
                ),
            sounds =
                StoneSounds(
                    click = R.raw.tp_clear_quartz_click_soft,
                    confirm = R.raw.tp_clear_quartz_confirm,
                    swoop = R.raw.tp_clear_quartz_swoop,
                ),
            // Native drawing replaces baked button art. Resource slots remain valid for pack serialization.
            surfaces = StoneComponent.entries.associateWith { StoneStateArt(art, art, art, art, art) },
        )
    }
}

val ThemePack.isLivingLake: Boolean
    get() = slug == LakeThemePack.SLUG
