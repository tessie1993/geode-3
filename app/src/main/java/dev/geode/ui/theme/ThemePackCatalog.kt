package dev.geode.ui.theme

import androidx.compose.ui.graphics.Color
import dev.geode.R

/** Saved mineral identities, rebuilt with a shared lens and independently coloured glass. */
object ThemePackCatalog {
    val lapisLazuli =
        ThemePack(
            slug = "lapis-lazuli",
            name = "Lapis Lazuli",
            stone = "lapis lazuli",
            isLight = false,
            palette =
                StonePalette(
                    background = Color(0xFF050A20),
                    backgroundDeep = Color(0xFF02040D),
                    surface = Color(0xFF10265C),
                    surfaceHigh = Color(0xFF173A83),
                    primary = Color(0xFF6D98FF),
                    secondary = Color(0xFFD1B36A),
                    accent = Color(0xFF9BB9FF),
                    glow = Color(0xFF80A7FF),
                    onBackground = Color(0xFFF7F8FF),
                    onSurface = Color(0xFFF7F8FF),
                    muted = Color(0xFFBEC7E2),
                    outline = Color(0xFF516491),
                    danger = Color(0xFFFF817D),
                ),
            motion = jellyMotion(),
            material = jellyMaterial(),
            sounds =
                StoneSounds(
                    click = R.raw.tp_lapis_lazuli_click_soft,
                    confirm = R.raw.tp_lapis_lazuli_confirm,
                    swoop = R.raw.tp_lapis_lazuli_swoop,
                ),
            surfaces = jellySurfaces(),
        )

    val sugilite =
        ThemePack(
            slug = "sugilite",
            name = "Sugilite",
            stone = "sugilite",
            isLight = false,
            palette =
                StonePalette(
                    background = Color(0xFF120A1A),
                    backgroundDeep = Color(0xFF07040B),
                    surface = Color(0xFF281538),
                    surfaceHigh = Color(0xFF43205D),
                    primary = Color(0xFFB58AE8),
                    secondary = Color(0xFFD28BDD),
                    accent = Color(0xFFF2C2FF),
                    glow = Color(0xFFD9A8FF),
                    onBackground = Color(0xFFFBF5FF),
                    onSurface = Color(0xFFFBF5FF),
                    muted = Color(0xFFCDBED6),
                    outline = Color(0xFF745A80),
                    danger = Color(0xFFFF7F9C),
                ),
            motion = jellyMotion(),
            material = jellyMaterial(),
            sounds =
                StoneSounds(
                    click = R.raw.tp_sugilite_click_soft,
                    confirm = R.raw.tp_sugilite_confirm,
                    swoop = R.raw.tp_sugilite_swoop,
                ),
            surfaces = jellySurfaces(),
        )

    val amethyst =
        ThemePack(
            slug = "amethyst",
            name = "Amethyst",
            stone = "amethyst",
            isLight = false,
            palette =
                StonePalette(
                    background = Color(0xFF180D25),
                    backgroundDeep = Color(0xFF0B0611),
                    surface = Color(0xFF3A2050),
                    surfaceHigh = Color(0xFF5A3375),
                    primary = Color(0xFFCEAAEF),
                    secondary = Color(0xFF9A78C2),
                    accent = Color(0xFFF1D9FF),
                    glow = Color(0xFFE7C9FF),
                    onBackground = Color(0xFFFEF8FF),
                    onSurface = Color(0xFFFEF8FF),
                    muted = Color(0xFFD7C7E1),
                    outline = Color(0xFF826D91),
                    danger = Color(0xFFFF84A6),
                ),
            motion = jellyMotion(),
            material = jellyMaterial(),
            sounds =
                StoneSounds(
                    click = R.raw.tp_amethyst_click_soft,
                    confirm = R.raw.tp_amethyst_confirm,
                    swoop = R.raw.tp_amethyst_swoop,
                ),
            surfaces = jellySurfaces(),
        )

    val clearQuartz =
        ThemePack(
            slug = "clear-quartz",
            name = "Clear Quartz",
            stone = "clear quartz",
            isLight = true,
            palette =
                StonePalette(
                    background = Color(0xFFEEEAE3),
                    backgroundDeep = Color(0xFFB8B0A5),
                    surface = Color(0xFFF7F4EE),
                    surfaceHigh = Color(0xFFFFFFFF),
                    primary = Color(0xFF9C7845),
                    secondary = Color(0xFF78898F),
                    accent = Color(0xFF6D5639),
                    glow = Color(0xFFFFF7DE),
                    onBackground = Color(0xFF181713),
                    onSurface = Color(0xFF1D1B17),
                    muted = Color(0xFF5D5951),
                    outline = Color(0xFF8E867A),
                    danger = Color(0xFFA13232),
                ),
            motion = jellyMotion(),
            material = jellyMaterial(),
            sounds =
                StoneSounds(
                    click = R.raw.tp_clear_quartz_click_soft,
                    confirm = R.raw.tp_clear_quartz_confirm,
                    swoop = R.raw.tp_clear_quartz_swoop,
                ),
            surfaces = jellySurfaces(),
        )

    val azurite =
        ThemePack(
            slug = "azurite",
            name = "Azurite",
            stone = "azurite",
            isLight = false,
            palette =
                StonePalette(
                    background = Color(0xFF04051A),
                    backgroundDeep = Color(0xFF010208),
                    surface = Color(0xFF111A55),
                    surfaceHigh = Color(0xFF1A297E),
                    primary = Color(0xFF5872F4),
                    secondary = Color(0xFF8B62DB),
                    accent = Color(0xFF91B6FF),
                    glow = Color(0xFF6E8CFF),
                    onBackground = Color(0xFFF7F7FF),
                    onSurface = Color(0xFFF7F7FF),
                    muted = Color(0xFFC4C8E7),
                    outline = Color(0xFF4F5C9B),
                    danger = Color(0xFFFF7F9A),
                ),
            motion = jellyMotion(),
            material = jellyMaterial(),
            sounds =
                StoneSounds(
                    click = R.raw.tp_azurite_click_soft,
                    confirm = R.raw.tp_azurite_confirm,
                    swoop = R.raw.tp_azurite_swoop,
                ),
            surfaces = jellySurfaces(),
        )

    val firestone =
        ThemePack(
            slug = "firestone",
            name = "Firestone",
            stone = "fire agate / firestone",
            isLight = false,
            palette =
                StonePalette(
                    background = Color(0xFF170705),
                    backgroundDeep = Color(0xFF080202),
                    surface = Color(0xFF431208),
                    surfaceHigh = Color(0xFF6E1E0D),
                    primary = Color(0xFFF47C32),
                    secondary = Color(0xFFC74625),
                    accent = Color(0xFFFFC06D),
                    glow = Color(0xFFFF8C3E),
                    onBackground = Color(0xFFFFF5EA),
                    onSurface = Color(0xFFFFF5EA),
                    muted = Color(0xFFD8B9A9),
                    outline = Color(0xFF8B4D35),
                    danger = Color(0xFFFF8D7C),
                ),
            motion = jellyMotion(),
            material = jellyMaterial(),
            sounds =
                StoneSounds(
                    click = R.raw.tp_firestone_click_soft,
                    confirm = R.raw.tp_firestone_confirm,
                    swoop = R.raw.tp_firestone_swoop,
                ),
            surfaces = jellySurfaces(),
        )

    val kyanite =
        ThemePack(
            slug = "kyanite",
            name = "Kyanite",
            stone = "blue kyanite",
            isLight = false,
            palette =
                StonePalette(
                    background = Color(0xFF07111D),
                    backgroundDeep = Color(0xFF03070D),
                    surface = Color(0xFF163653),
                    surfaceHigh = Color(0xFF24577E),
                    primary = Color(0xFF86B9E8),
                    secondary = Color(0xFF6E8DB9),
                    accent = Color(0xFFD2EBFF),
                    glow = Color(0xFF9DCCF4),
                    onBackground = Color(0xFFF6FBFF),
                    onSurface = Color(0xFFF6FBFF),
                    muted = Color(0xFFC2D2E0),
                    outline = Color(0xFF5D7893),
                    danger = Color(0xFFFF827D),
                ),
            motion = jellyMotion(),
            material = jellyMaterial(),
            sounds =
                StoneSounds(
                    click = R.raw.tp_kyanite_click_soft,
                    confirm = R.raw.tp_kyanite_confirm,
                    swoop = R.raw.tp_kyanite_swoop,
                ),
            surfaces = jellySurfaces(),
        )

    val malachite =
        ThemePack(
            slug = "malachite",
            name = "Malachite",
            stone = "malachite",
            isLight = false,
            palette =
                StonePalette(
                    background = Color(0xFF03140D),
                    backgroundDeep = Color(0xFF010806),
                    surface = Color(0xFF07341F),
                    surfaceHigh = Color(0xFF0B5833),
                    primary = Color(0xFF4ED58F),
                    secondary = Color(0xFF77B997),
                    accent = Color(0xFFA7FFD0),
                    glow = Color(0xFF69EDA7),
                    onBackground = Color(0xFFF3FFF8),
                    onSurface = Color(0xFFF3FFF8),
                    muted = Color(0xFFBBD7C6),
                    outline = Color(0xFF39745A),
                    danger = Color(0xFFFF857D),
                ),
            motion = jellyMotion(),
            material = jellyMaterial(),
            sounds =
                StoneSounds(
                    click = R.raw.tp_malachite_click_soft,
                    confirm = R.raw.tp_malachite_confirm,
                    swoop = R.raw.tp_malachite_swoop,
                ),
            surfaces = jellySurfaces(),
        )

    val mookaite =
        ThemePack(
            slug = "mookaite",
            name = "Mookaite",
            stone = "mookaite jasper",
            isLight = false,
            palette =
                StonePalette(
                    background = Color(0xFF21100E),
                    backgroundDeep = Color(0xFF0E0706),
                    surface = Color(0xFF593024),
                    surfaceHigh = Color(0xFF7B4432),
                    primary = Color(0xFFE4A54A),
                    secondary = Color(0xFFC36B50),
                    accent = Color(0xFFFFD890),
                    glow = Color(0xFFF0B55C),
                    onBackground = Color(0xFFFFF8E8),
                    onSurface = Color(0xFFFFF8E8),
                    muted = Color(0xFFE1C7AF),
                    outline = Color(0xFF8A6048),
                    danger = Color(0xFFFF8A79),
                ),
            motion = jellyMotion(),
            material = jellyMaterial(),
            sounds =
                StoneSounds(
                    click = R.raw.tp_mookaite_click_soft,
                    confirm = R.raw.tp_mookaite_confirm,
                    swoop = R.raw.tp_mookaite_swoop,
                ),
            surfaces = jellySurfaces(),
        )

    val onyx =
        ThemePack(
            slug = "onyx",
            name = "Onyx",
            stone = "banded black onyx",
            isLight = false,
            palette =
                StonePalette(
                    background = Color(0xFF08090B),
                    backgroundDeep = Color(0xFF020304),
                    surface = Color(0xFF17191D),
                    surfaceHigh = Color(0xFF292D33),
                    primary = Color(0xFFD7B47A),
                    secondary = Color(0xFF9BA4AE),
                    accent = Color(0xFFF1D8A5),
                    glow = Color(0xFFF6D6A0),
                    onBackground = Color(0xFFF7F4EF),
                    onSurface = Color(0xFFF7F4EF),
                    muted = Color(0xFFBAB7B1),
                    outline = Color(0xFF6C7178),
                    danger = Color(0xFFFF8178),
                ),
            motion = jellyMotion(),
            material = jellyMaterial(),
            sounds =
                StoneSounds(
                    click = R.raw.tp_onyx_click_soft,
                    confirm = R.raw.tp_onyx_confirm,
                    swoop = R.raw.tp_onyx_swoop,
                ),
            surfaces = jellySurfaces(),
        )

    val tidalGlass = TidalThemePack.create(kyanite)

    val all: List<ThemePack> =
        listOf(
            tidalGlass,
            lapisLazuli,
            sugilite,
            amethyst,
            clearQuartz,
            azurite,
            firestone,
            kyanite,
            malachite,
            mookaite,
            onyx,
        )

    fun bySlug(slug: String?): ThemePack = all.firstOrNull { it.slug == slug } ?: all.first()

    private fun jellyMotion(): StoneMotion =
        StoneMotion(
            pressDurationMs = 140,
            pressScale = 0.96f,
            innerGlowGain = 1.2f,
            releaseDurationMs = 440,
            focusDurationMs = 180,
            edgeLightGain = 1.35f,
            selectedDurationMs = 240,
            reduceMotionCrossfadeMs = 0,
        )

    private fun jellyMaterial(): StoneMaterial =
        StoneMaterial(
            tile = R.drawable.spatial_glass_capsule,
            glowOverlay = R.drawable.spatial_glass_pebble,
            refractionOverlay = R.drawable.spatial_glass_orb_shell,
            ambientPortrait = R.drawable.spatial_lake_atmosphere,
            ambientLandscape = R.drawable.spatial_lake_atmosphere,
            ambientSquare = R.drawable.spatial_lake_atmosphere,
            backgroundOpacity = 0.6f,
            surfaceOpacity = 0.32f,
            disabledOpacity = 0.2f,
        )

    private fun jellySurfaces(): Map<StoneComponent, StoneStateArt> =
        StoneComponent.entries.associateWith { component ->
            val lens =
                when (component) {
                    StoneComponent.ICON_BUTTON,
                    StoneComponent.KNOB,
                    StoneComponent.PROGRESS_RING,
                    StoneComponent.SLIDER_THUMB,
                    -> R.drawable.spatial_glass_pebble
                    else -> R.drawable.spatial_glass_capsule
                }
            StoneStateArt(
                default = lens,
                focused = lens,
                pressed = lens,
                selected = lens,
                disabled = lens,
            )
        }
}
