package dev.geode.ui.theme

/** Built-ins only. Retired saved names migrate to the current living world. */
object ThemePackCatalog {
    val livingLake: ThemePack = LakeThemePack.create()
    val all: List<ThemePack> = listOf(livingLake)

    private val retiredSlugs =
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
            "CLEAR_QUARTZ",
            "SUGILITE",
        )

    fun bySlug(slug: String?): ThemePack =
        when (slug) {
            null, livingLake.slug -> livingLake
            in retiredSlugs -> livingLake
            else -> all.firstOrNull { it.slug == slug } ?: livingLake
        }
}
