import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


BUILTINS = (
    ("lapis-lazuli", "Lapis Lazuli", "lapisLazuli"),
    ("sugilite", "Sugilite", "sugilite"),
    ("amethyst", "Amethyst", "amethyst"),
    ("clear-quartz", "Clear Quartz", "clearQuartz"),
    ("azurite", "Azurite", "azurite"),
    ("firestone", "Firestone", "firestone"),
    ("kyanite", "Kyanite", "kyanite"),
    ("malachite", "Malachite", "malachite"),
    ("mookaite", "Mookaite", "mookaite"),
    ("onyx", "Onyx", "onyx"),
)
SHARED_ASSETS = (
    "spatial_lake_atmosphere",
    "spatial_glass_capsule",
    "spatial_glass_pebble",
    "spatial_glass_orb_shell",
)
COMPONENTS = (
    "album_tile", "bottom_sheet", "card", "chip", "compact_button", "dialog",
    "icon_button", "knob", "list_row", "mini_player", "navigation_bar",
    "primary_button", "progress_ring", "secondary_button", "slider_thumb",
    "slider_track", "text_field", "toggle",
)
STATES = ("default", "focused", "pressed", "selected", "disabled")


class ThemeImportRegressionTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        script_source = Path(__file__).resolve().parents[2] / "import-theme-pack.sh"
        self.script = self.root / "tools" / "import-theme-pack.sh"
        self.script.parent.mkdir(parents=True)
        shutil.copyfile(script_source, self.script)
        self.resources = self.root / "app" / "src" / "main" / "res"
        self.drawables = self.resources / "drawable-nodpi"
        self.drawables.mkdir(parents=True)
        for asset in SHARED_ASSETS:
            (self.drawables / f"{asset}.webp").write_bytes(b"existing shared glass")
        self.catalog = (
            self.root / "app" / "src" / "main" / "java" / "dev" / "geode" /
            "ui" / "theme" / "ThemePackCatalog.kt"
        )
        self.catalog.parent.mkdir(parents=True)
        self.catalog.write_text("existing catalog\n")

    def pack(self, slug, name, *, authored_motion=False):
        pack = self.root / "packs" / slug
        tokens = pack / "tokens"
        tokens.mkdir(parents=True)
        (pack / "manifest.json").write_text(json.dumps({"slug": slug}))
        theme = {
            "name": name,
            "stone": f"{slug} identity",
            "mode": "light" if slug == "clear-quartz" else "dark",
            "textureOpacity": {"background": 0.43, "surface": 0.71, "disabled": 0.19},
        }
        (tokens / "theme.tokens.json").write_text(json.dumps(theme, indent=2))
        colors = {
            "background": "#102030", "backgroundDeep": "#050608",
            "surface": "#304050", "surfaceHigh": "#405060",
            "primary": "#A172D3", "secondary": "#E8CFAD", "accent": "#F5D3ED",
            "glow": "#D8B9EE", "onBackground": "#F3F4F5", "onSurface": "#EEF2F5",
            "muted": "#B9C9D4", "outline": "#8090A0", "danger": "#FF9990",
        }
        (tokens / "colors.json").write_text(json.dumps(colors, indent=2))
        if authored_motion:
            motion = {
                "press": {"durationMs": 77, "scale": 0.91},
                "innerGlowGain": 1.8,
                "release": {"durationMs": 321},
                "focus": {"durationMs": 155},
                "edgeLightGain": 1.4,
                "selected": {"durationMs": 199},
                "crossfadeMs": 88,
            }
            (tokens / "motion.json").write_text(json.dumps(motion, indent=2))
        (pack / "audio").mkdir()
        for sound in ("click-soft", "confirm", "swoop"):
            (pack / "audio" / f"{sound}.wav").write_bytes(f"{slug}:{sound}".encode())
        font = pack / "android" / "res" / "font"
        font.mkdir(parents=True)
        (font / "shared.ttf").write_bytes(b"shared font")
        return pack

    def run_import(self, packs, *, catalog_only=False):
        environment = os.environ.copy()
        environment.pop("CATALOG_ONLY", None)
        if catalog_only:
            environment["CATALOG_ONLY"] = "1"
        return subprocess.run(
            ["bash", str(self.script), *(str(pack) for pack in packs)],
            env=environment,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=30,
        )

    def test_all_builtin_imports_preserve_identity_and_do_not_reinstall_old_art(self):
        # Built-in fixtures deliberately contain no old component art or motion file.
        # A full import must use the app's glass kit and still copy real pack identities.
        packs = [self.pack(slug, name) for slug, name, _ in BUILTINS]
        result = self.run_import(packs)
        self.assertEqual(result.returncode, 0, result.stderr)
        source = self.catalog.read_text()
        self.assertNotIn("R.drawable.tp_", source)
        self.assertEqual(
            {path.name for path in self.drawables.iterdir()},
            {f"{asset}.webp" for asset in SHARED_ASSETS},
        )
        for slug, name, val in BUILTINS:
            self.assertIn(f"val {val} =", source)
            self.assertIn(f'slug = "{slug}"', source)
            self.assertIn(f'name = "{name}"', source)
            self.assertIn(f'stone = "{slug} identity"', source)
            fragment = slug.replace("-", "_")
            for source_sound, target_sound in (
                ("click-soft", "click_soft"), ("confirm", "confirm"), ("swoop", "swoop"),
            ):
                self.assertEqual(
                    (self.resources / "raw" / f"tp_{fragment}_{target_sound}.wav").read_bytes(),
                    f"{slug}:{source_sound}".encode(),
                )
        self.assertIn("primary = Color(0xFFA172D3)", source)
        quartz = source.split("val clearQuartz =", 1)[1].split("val azurite =", 1)[0]
        self.assertIn("isLight = true", quartz)
        registry = re.search(r"val all: List<ThemePack> =\s*listOf\(([^)]*)\)", source).group(1)
        self.assertEqual(
            [value.strip() for value in registry.split(",") if value.strip()],
            ["tidalGlass", *(val for _, _, val in BUILTINS)],
        )
        self.assertIn("val tidalGlass = TidalThemePack.create(kyanite)", source)
        self.assertEqual((self.resources / "font" / "shared.ttf").read_bytes(), b"shared font")

    def test_external_catalog_keeps_authored_motion_material_and_all_state_art(self):
        kyanite = self.pack("kyanite", "Kyanite")
        external = self.pack("garden-lens", "Garden Lens", authored_motion=True)
        result = self.run_import([kyanite, external], catalog_only=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        source = self.catalog.read_text()
        external_source = source.split("val gardenLens =", 1)[1].split("val tidalGlass =", 1)[0]
        self.assertNotIn("jellyMotion()", external_source)
        self.assertNotIn("jellyMaterial()", external_source)
        self.assertNotIn("jellySurfaces()", external_source)
        self.assertIn("pressDurationMs = 77", external_source)
        self.assertIn("pressScale = 0.91f", external_source)
        self.assertIn("releaseDurationMs = 321", external_source)
        self.assertIn("reduceMotionCrossfadeMs = 88", external_source)
        self.assertIn("backgroundOpacity = 0.43f", external_source)
        self.assertIn("surfaceOpacity = 0.71f", external_source)
        self.assertIn("disabledOpacity = 0.19f", external_source)
        resources = set(re.findall(r"R\.drawable\.(tp_garden_lens_\w+)", external_source))
        expected = {
            f"tp_garden_lens_{component}_{state}"
            for component in COMPONENTS for state in STATES
        } | {
            f"tp_garden_lens_{material}" for material in (
                "material_tile", "glow_overlay", "refraction_overlay",
                "ambient_portrait", "ambient_landscape", "ambient_square",
            )
        }
        self.assertEqual(resources, expected)
        self.assertFalse((self.resources / "raw").exists() and list((self.resources / "raw").iterdir()))

    def test_missing_fallback_or_reserved_slug_fails_before_replacing_catalog(self):
        quartz = self.pack("clear-quartz", "Clear Quartz")
        result = self.run_import([quartz])
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Kyanite is required", result.stderr)
        self.assertEqual(self.catalog.read_text(), "existing catalog\n")
        kyanite = self.pack("kyanite", "Kyanite")
        reserved = self.pack("tidal-glass", "Tidal Glass")
        result = self.run_import([kyanite, reserved])
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("reserved native theme", result.stderr)
        self.assertEqual(self.catalog.read_text(), "existing catalog\n")

    def test_missing_shared_kit_fails_before_replacing_catalog(self):
        kyanite = self.pack("kyanite", "Kyanite")
        (self.drawables / "spatial_glass_orb_shell.webp").unlink()
        result = self.run_import([kyanite], catalog_only=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("missing built-in spatial glass asset", result.stderr)
        self.assertEqual(self.catalog.read_text(), "existing catalog\n")


if __name__ == "__main__":
    unittest.main()
