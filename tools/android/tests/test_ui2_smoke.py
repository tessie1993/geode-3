"""Synthetic UI evidence tests; these do not claim an emulator or renderer run."""
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from smoke_qa import NAV_CLOSE_LABEL, NAV_OPEN_PREFIX, SmokeRun
from prepare_ui_review import prepare


def tree(nodes):
    return ET.fromstring(f"<hierarchy>{nodes}</hierarchy>")


def handle(destination, bounds="[10,700][210,760]", clickable="true"):
    return (f'<node content-desc="{NAV_OPEN_PREFIX}{destination}" enabled="true" '
            f'clickable="{clickable}" bounds="{bounds}" />')


class OrbitEvidenceTest(unittest.TestCase):
    def test_heading_or_stale_noninteractive_handle_cannot_prove_current_destination(self):
        for nodes in ('<node text="Player" bounds="[0,0][100,40]" />',
                      handle("Player", clickable="false")):
            with self.subTest(nodes=nodes), self.assertRaisesRegex(AssertionError, "unambiguous enabled geode handle"):
                SmokeRun.current_destination(tree(nodes))

    def test_duplicate_icon_and_label_for_same_handle_are_unambiguous(self):
        root = tree(f'''<node clickable="true" enabled="true" bounds="[10,700][210,760]">
            <node content-desc="{NAV_OPEN_PREFIX}Library" bounds="[10,700][210,760]" />
            <node content-desc="{NAV_OPEN_PREFIX}Library" bounds="[40,710][80,750]" />
        </node><node text="Player" bounds="[0,0][100,40]" />''')
        self.assertEqual(SmokeRun.current_destination(root), "Library")
        SmokeRun.assert_destination(root, "Library")

    def test_conflicting_enabled_handles_fail_instead_of_guessing(self):
        with self.assertRaisesRegex(AssertionError, "unambiguous enabled geode handle"):
            SmokeRun.current_destination(tree(handle("Player") + handle("Library")))

    def test_current_handle_cannot_prove_orbit_closed_when_close_action_remains(self):
        root = tree(handle("Library") + f'<node content-desc="{NAV_CLOSE_LABEL}" clickable="true" bounds="[10,10][70,70]" />')
        with self.assertRaisesRegex(AssertionError, "remained open"):
            SmokeRun.assert_destination(root, "Library")

    def test_nonselected_heading_does_not_prove_selected_orbit_route(self):
        root = tree('''<node text="Library" bounds="[0,0][200,40]" />
            <node clickable="true" selected="false" bounds="[150,200][250,320]">
                <node text="Library" bounds="[155,280][245,310]" />
            </node>''')
        with self.assertRaisesRegex(AssertionError, "not selected"):
            SmokeRun.assert_selected(root, "Library")

    def test_visit_opens_handle_before_route_and_ignores_duplicate_heading(self):
        run = SimulatedOrbit()
        with patch("smoke_qa.time.sleep"):
            result = run.visit("Library")
        self.assertEqual(run.inputs, ["open-handle", "select-library"])
        self.assertEqual(run.current_destination(result), "Library")
        self.assertFalse(run.open)

    def test_tap_attempt_without_destination_change_fails(self):
        run = SimulatedOrbit(route_callback=False)
        with patch("smoke_qa.time.sleep"), self.assertRaisesRegex(AssertionError, "did not become Library"):
            run.visit("Library")
        self.assertEqual(run.inputs, ["open-handle", "select-library"])


class SimulatedOrbit(SmokeRun):
    def __init__(self, route_callback=True):
        self.open = False
        self.current = "Player"
        self.events = []
        self.inputs = []
        self.route_callback = route_callback

    def capture(self, label):
        if not self.open:
            # The screen heading is not a navigation action.
            return tree('<node text="Library" clickable="false" bounds="[10,10][200,50]" />' + handle(self.current))
        return tree(f'''<node text="Navigate" bounds="[10,10][200,50]" />
            <node content-desc="{NAV_CLOSE_LABEL}" clickable="true" bounds="[300,10][360,70]" />
            <node text="Choose a destination" bounds="[10,500][300,540]" />
            <node text="Library" clickable="false" bounds="[0,80][400,120]" />
            <node clickable="true" selected="false" bounds="[150,200][250,320]">
                <node text="Library" bounds="[155,280][245,310]" />
            </node>''')

    def shell(self, *args):
        if args == ("pidof", "dev.geode.debug"):
            return "1234"
        if args == ("input", "tap", "110", "730") and not self.open:
            self.open = True
            self.inputs.append("open-handle")
        elif args == ("input", "tap", "200", "260") and self.open:
            self.inputs.append("select-library")
            self.open = False
            if self.route_callback:
                self.current = "Library"
        else:
            raise AssertionError(f"Tap did not target current measured handle/route: {args}")
        return ""


class SettingsHierarchyEvidenceTest(unittest.TestCase):
    def test_opening_sibling_group_returns_to_parent_first(self):
        run = SimulatedSettings()
        with patch("smoke_qa.time.sleep"):
            run.settings_group("Audio")
        self.assertEqual(run.inputs, ["back-to-settings-home", "open-audio"])
        self.assertEqual(run.group, "Audio")

    def test_group_row_without_content_callback_cannot_pass(self):
        run = SimulatedSettings(group_callback=False)
        with patch("smoke_qa.time.sleep"), self.assertRaisesRegex(AssertionError, "semantic labels missing"):
            run.settings_group("Audio")


class SimulatedSettings(SmokeRun):
    def __init__(self, group_callback=True):
        self.group = "Look"
        self.group_callback = group_callback
        self.events = []
        self.inputs = []

    def capture(self, label):
        common = handle("Settings") + '<node text="Settings" bounds="[10,10][200,50]" />'
        if self.group:
            return tree(common + f'''<node text="{self.group}" bounds="[70,60][200,100]" />
                <node content-desc="‹ Back" clickable="true" bounds="[10,60][60,110]" />''')
        return tree(common + '''<node clickable="true" bounds="[10,120][300,180]">
                    <node text="Look" bounds="[20,125][280,170]" />
                </node><node clickable="true" bounds="[10,200][300,260]">
                    <node text="Audio" bounds="[20,205][280,250]" />
                </node>''')

    def visit(self, label, profile="default"):
        if label != "Settings":
            raise AssertionError(label)
        return self.capture(label)

    def shell(self, *args):
        if args == ("input", "tap", "35", "85") and self.group:
            self.group = None
            self.inputs.append("back-to-settings-home")
        elif args == ("input", "tap", "155", "230") and self.group is None:
            self.inputs.append("open-audio")
            if self.group_callback:
                self.group = "Audio"
        else:
            raise AssertionError(f"Tap did not target the measured Settings hierarchy: {args}")
        return ""


class ReviewSelectionEvidenceTest(unittest.TestCase):
    def test_orbit_and_new_theme_evidence_are_copied_without_rewriting(self):
        with tempfile.TemporaryDirectory() as folder:
            base = Path(folder)
            source, output = base / "source", base / "review"
            smoke = source / "smoke"
            smoke.mkdir(parents=True)
            for stem in ("01-navigation-selected-orbit-player", "02-component-kit-living-lake"):
                (smoke / f"{stem}.png").write_bytes(b"original screenshot fixture")
                (smoke / f"{stem}.xml").write_text('<hierarchy><node text="Navigate" /></hierarchy>')
            prepare(source, output, screens_only=True)
            self.assertEqual((output / "navigation-orbit.png").read_bytes(), b"original screenshot fixture")
            self.assertEqual((output / "component-kit-living-lake.png").read_bytes(), b"original screenshot fixture")
            self.assertFalse((output / "component-kit-tidal-glass.png").exists())
            self.assertTrue((output / "manifest.json").is_file())


if __name__ == "__main__":
    unittest.main()
