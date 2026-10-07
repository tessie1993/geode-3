from pathlib import Path
import sys
import unittest
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from smoke_qa import SmokeRun


class RestartEvidenceTest(unittest.TestCase):
    def hierarchy(self, text):
        return ET.fromstring(
            f'<hierarchy><node text="{text}" enabled="true" bounds="[0,0][100,100]" /></hierarchy>'
        )

    def test_leaf_player_node_is_valid_restart_evidence(self):
        SmokeRun.assert_restarted(self.hierarchy("Player"))

    def test_leaf_onboarding_node_fails_the_persistence_gate(self):
        for text in ("I understand", "Not now"):
            with self.subTest(text=text), self.assertRaisesRegex(AssertionError, "Onboarding did not persist"):
                SmokeRun.assert_restarted(self.hierarchy(text))

    def test_missing_navigation_fails(self):
        with self.assertRaisesRegex(AssertionError, "Player navigation missing"):
            SmokeRun.assert_restarted(self.hierarchy("Loading"))


class SemanticSelectorTest(unittest.TestCase):
    def test_active_tab_is_targeted_when_android_exports_it_as_nonclickable(self):
        # Captured run-35 shape: a separate Player heading, then the selected
        # nav ancestor containing both icon description and text descendants.
        root = ET.fromstring('''<hierarchy>
            <node text="Player" enabled="true" clickable="false" selected="false" bounds="[16,73][57,89]" />
            <node enabled="true" clickable="false" focusable="true" selected="true" bounds="[14,548][70,628]">
                <node enabled="true" clickable="false" selected="false" bounds="[17,554][67,604]">
                    <node content-desc="Player" enabled="true" clickable="false" selected="false" bounds="[30,567][54,591]" />
                </node>
                <node text="Player" enabled="true" clickable="false" selected="false" bounds="[24,608][60,622]" />
            </node>
        </hierarchy>''')
        self.assertEqual(SmokeRun.action(root, "Player").get("bounds"), "[14,548][70,628]")
        SmokeRun.assert_selected(root, "Player")

    def test_disabled_selected_control_is_never_authorized(self):
        root = ET.fromstring('''<hierarchy>
            <node enabled="false" clickable="false" focusable="true" selected="true" bounds="[14,548][70,628]">
                <node content-desc="Player" enabled="true" clickable="false" bounds="[30,567][54,591]" />
            </node>
        </hierarchy>''')
        self.assertIsNone(SmokeRun.action(root, "Player"))

    def test_focusable_heading_without_selection_is_not_a_tap_target(self):
        root = ET.fromstring('''<hierarchy>
            <node text="Player" enabled="true" clickable="false" focusable="true" selected="false" bounds="[16,73][57,89]" />
        </hierarchy>''')
        self.assertIsNone(SmokeRun.action(root, "Player"))

    def test_destination_action_skips_duplicate_screen_heading(self):
        root = ET.fromstring('''<hierarchy>
            <node text="Library" enabled="true" clickable="false" bounds="[0,0][400,60]" />
            <node text="" enabled="true" clickable="true" selected="true" bounds="[100,700][200,800]">
                <node text="Library" enabled="true" clickable="false" bounds="[110,750][190,780]" />
            </node>
        </hierarchy>''')
        self.assertEqual(SmokeRun.action(root, "Library").get("bounds"), "[100,700][200,800]")
        SmokeRun.assert_selected(root, "Library")

    def test_disabled_transport_is_visible_but_cannot_be_tapped(self):
        root = ET.fromstring('''<hierarchy>
            <node enabled="false" clickable="false" bounds="[100,100][200,200]">
                <node content-desc="Play" enabled="false" bounds="[120,120][180,180]" />
            </node>
        </hierarchy>''')
        SmokeRun.assert_labels(root, "Play")
        self.assertIsNone(SmokeRun.action(root, "Play"))

    def test_offscreen_or_zero_area_bounds_do_not_authorize_taps(self):
        for bounds in ("[0,0][0,100]", "[-20,0][100,100]", "not bounds"):
            with self.subTest(bounds=bounds):
                root = ET.fromstring(f'<hierarchy><node text="Player" clickable="true" bounds="{bounds}" /></hierarchy>')
                self.assertIsNone(SmokeRun.action(root, "Player"))

    def test_switch_checked_state_uses_labelled_toggle_not_sibling_text(self):
        root = ET.fromstring('''<hierarchy>
            <node text="Slow the motion down" bounds="[0,0][200,60]" />
            <node content-desc="Slow the motion down" checkable="true" checked="true" bounds="[200,0][300,60]" />
        </hierarchy>''')
        self.assertTrue(SmokeRun.checked(root, "Slow the motion down"))

    def test_theme_selection_requires_selected_semantics(self):
        root = ET.fromstring('''<hierarchy>
            <node content-desc="Tidal Glass" clickable="true" selected="false" bounds="[0,0][100,100]" />
        </hierarchy>''')
        with self.assertRaisesRegex(AssertionError, "not selected"):
            SmokeRun.assert_selected(root, "Tidal Glass")
        SmokeRun.assert_not_selected(root, "Tidal Glass")

    def test_radio_checked_semantics_are_accepted_only_when_requested(self):
        root = ET.fromstring('''<hierarchy>
            <node content-desc="Tidal Glass" checkable="true" checked="true" selected="false" bounds="[0,0][100,100]" />
        </hierarchy>''')
        SmokeRun.assert_selected(root, "Tidal Glass", allow_checked=True)
        with self.assertRaisesRegex(AssertionError, "not selected"):
            SmokeRun.assert_selected(root, "Tidal Glass")

    def test_seek_evidence_reads_current_position_and_duration(self):
        node = ET.fromstring('<node content-desc="Seek. 0:27 of 0:45" bounds="[100,100][500,160]" />')
        self.assertEqual(SmokeRun.position_seconds(node), (27, 45))
        with self.assertRaisesRegex(AssertionError, "position and duration"):
            SmokeRun.position_seconds(ET.fromstring('<node content-desc="Seek" />'))

    def test_wide_landscape_content_remains_a_vertical_scroll_candidate(self):
        root = ET.fromstring('''<hierarchy>
            <node class="android.widget.HorizontalScrollView" scrollable="true" bounds="[200,0][1920,160]" />
            <node class="android.view.View" scrollable="true" bounds="[200,160][1920,800]" />
        </hierarchy>''')
        run = SmokeRun.__new__(SmokeRun)
        run.events = []
        calls = []
        run.shell = lambda *args: calls.append(args)
        self.assertTrue(run.swipe(root, "vertical"))
        command = calls[0]
        self.assertEqual(command[:2], ("input", "swipe"))
        self.assertEqual(command[2], command[4])
        self.assertGreater(int(command[3]), int(command[5]))
        self.assertGreaterEqual(int(command[5]), 160)
        self.assertLessEqual(int(command[3]), 800)


if __name__ == "__main__":
    unittest.main()
