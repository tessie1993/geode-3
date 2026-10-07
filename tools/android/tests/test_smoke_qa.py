from pathlib import Path
import sys
import unittest
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from smoke_qa import SmokeRun, THEME_LABELS


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


class MotionRecordingEvidenceTest(unittest.TestCase):
    def test_run_42_actions_after_film_end_cannot_pass_recording_evidence(self):
        result = {
            "time_limit_seconds": 14, "recording_tail_seconds": 1,
            "action_elapsed_seconds": {
                "Awaken": 1.23, "Press capsule": 8.84,
                "Press round button": 15.30, "Selected capsule": 20.97,
            },
        }
        with self.assertRaisesRegex(AssertionError, "Press round button.*Selected capsule"):
            SmokeRun.assert_recorded_actions(result, tuple(result["action_elapsed_seconds"]))
        self.assertEqual(result["actions_within_recording"], ["Awaken", "Press capsule"])

    def test_recording_requires_every_expected_action_and_one_second_tail(self):
        result = {
            "time_limit_seconds": 40, "recording_tail_seconds": 1,
            "action_elapsed_seconds": {"Library": 7.4, "Player": 14.5, "Search": 22.0, "Close search": 39.01},
        }
        labels = ("Library", "Player", "Search", "Close search")
        with self.assertRaisesRegex(AssertionError, "39s tail deadline.*Close search"):
            SmokeRun.assert_recorded_actions(result, labels)
        result["action_elapsed_seconds"]["Close search"] = 39
        SmokeRun.assert_recorded_actions(result, labels)
        self.assertEqual(result["actions_within_recording"], list(labels))
        del result["action_elapsed_seconds"]["Player"]
        with self.assertRaisesRegex(AssertionError, "Player"):
            SmokeRun.assert_recorded_actions(result, labels)

    def test_invalid_elapsed_time_cannot_prove_an_action_was_recorded(self):
        for elapsed in (-1, float("inf"), float("nan")):
            with self.subTest(elapsed=elapsed):
                result = {
                    "time_limit_seconds": 14, "recording_tail_seconds": 1,
                    "action_elapsed_seconds": {"Awaken": elapsed},
                }
                with self.assertRaisesRegex(AssertionError, "Awaken"):
                    SmokeRun.assert_recorded_actions(result, ("Awaken",))
                self.assertEqual(result["actions_within_recording"], [])


class SemanticSelectorTest(unittest.TestCase):
    def test_fixture_directory_uses_resolved_emulator_external_volume(self):
        self.assertEqual(SmokeRun.fixture_directory("/storage/emulated/0"), "/storage/emulated/0/Music/GeodeUiQa")
        self.assertEqual(SmokeRun.fixture_directory("/storage/emulated/10/"), "/storage/emulated/10/Music/GeodeUiQa")

    def test_fixture_directory_rejects_sdcard_alias_and_unresolved_volume(self):
        for root in ("/sdcard", "/storage/self/primary", "", "/storage/emulated"):
            with self.subTest(root=root), self.assertRaisesRegex(AssertionError, "canonical emulator volume"):
                SmokeRun.fixture_directory(root)

    def test_scanner_placeholder_rows_cannot_prove_fixture_ingestion(self):
        remote = "/storage/emulated/0/Music/GeodeUiQa/geode_qa_river_a.wav"
        rows = f"Row: 0 _id=18, _display_name=geode_qa_river_a.wav, title=geode_qa_river_a, duration=NULL, is_music=NULL, _data={remote}"
        self.assertIsNone(SmokeRun.indexed_audio_row(rows, "geode_qa_river_a.wav", remote))

    def test_indexed_music_requires_actual_duration_and_matching_fixture_path(self):
        remote = "/storage/emulated/0/Music/GeodeUiQa/geode_qa_river_a.wav"
        rows = f"Row: 0 _id=18, _display_name=geode_qa_river_a.wav, title=geode_qa_river_a, duration=45000, is_music=1, _data={remote}"
        indexed = SmokeRun.indexed_audio_row(rows, "geode_qa_river_a.wav", remote)
        self.assertEqual(indexed["title"], "geode_qa_river_a")
        self.assertIsNone(SmokeRun.indexed_audio_row(rows, "geode_qa_river_a.wav", "/sdcard/Music/GeodeUiQa/geode_qa_river_a.wav"))

    def test_current_hero_track_rejects_expected_title_only_in_queue(self):
        root = ET.fromstring('''<hierarchy>
            <node bounds="[32,323][288,380]">
                <node text="PAUSED" bounds="[32,323][177,337]" />
                <node text="geode_qa_river_a" bounds="[32,337][278,365]" />
                <node text="Unknown artist" bounds="[32,365][234,380]" />
            </node>
            <node bounds="[20,400][300,500]">
                <node text="UP NEXT" bounds="[20,400][100,420]" />
                <node text="geode_qa_river_b" bounds="[20,430][280,460]" />
            </node>
        </hierarchy>''')
        self.assertEqual(SmokeRun.current_hero_track(root), "geode_qa_river_a")
        self.assertNotEqual(SmokeRun.current_hero_track(root), "geode_qa_river_b")

    def test_current_hero_track_accepts_title_only_in_status_metadata_subtree(self):
        for status in ("NOW PLAYING", "PAUSED"):
            with self.subTest(status=status):
                root = ET.fromstring(f'''<hierarchy>
                    <node bounds="[32,323][288,380]">
                        <node text="{status}" bounds="[32,323][177,337]" />
                        <node text="geode_qa_river_b" bounds="[32,337][278,365]" />
                        <node text="Unknown artist" bounds="[32,365][234,380]" />
                    </node>
                    <node text="geode_qa_river_a" bounds="[20,430][280,460]" />
                </hierarchy>''')
                self.assertEqual(SmokeRun.current_hero_track(root), "geode_qa_river_b")

    def test_queue_title_without_hero_status_cannot_prove_current_track(self):
        root = ET.fromstring('''<hierarchy>
            <node text="UP NEXT" bounds="[20,400][100,420]" />
            <node text="geode_qa_river_b" bounds="[20,430][280,460]" />
        </hierarchy>''')
        self.assertIsNone(SmokeRun.current_hero_track(root))

    def test_flat_status_without_metadata_column_cannot_prove_queue_title(self):
        root = ET.fromstring('''<hierarchy>
            <node text="PAUSED" bounds="[20,400][100,420]" />
            <node text="geode_qa_river_b" bounds="[20,430][280,460]" />
        </hierarchy>''')
        self.assertIsNone(SmokeRun.current_hero_track(root))

    def test_playback_progress_rejects_static_elapsed_position(self):
        before = ET.fromstring('<node content-desc="Seek. 0:27 of 0:45" />')
        after = ET.fromstring('<node content-desc="Seek. 0:27 of 0:45" />')
        with self.assertRaisesRegex(AssertionError, "did not advance"):
            SmokeRun.assert_position_advanced(before, after)

    def test_playback_progress_requires_same_duration_and_two_elapsed_seconds(self):
        before = ET.fromstring('<node content-desc="Seek. 0:27 of 0:45" />')
        after = ET.fromstring('<node content-desc="Seek. 0:29 of 0:45" />')
        self.assertEqual(SmokeRun.assert_position_advanced(before, after), (27, 29, 45))
        changed_track = ET.fromstring('<node content-desc="Seek. 0:31 of 1:00" />')
        with self.assertRaisesRegex(AssertionError, "did not advance"):
            SmokeRun.assert_position_advanced(before, changed_track)

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

    def test_customize_action_clipped_by_actual_row_viewport_is_not_fully_visible(self):
        # Captured run-41 toolbar: the actionable button extends above/below
        # the 26-pixel row viewport and only two pixels of its text are visible.
        root = ET.fromstring('''<hierarchy>
            <node class="android.widget.HorizontalScrollView" scrollable="true" bounds="[16,424][304,450]">
                <node enabled="true" clickable="true" bounds="[51,413][118,458]">
                    <node text="Set A" enabled="true" bounds="[67,436][102,438]" />
                </node>
            </node>
        </hierarchy>''')
        self.assertIsNotNone(SmokeRun.action(root, "Set A"))
        self.assertIsNone(SmokeRun.fully_visible_action(root, "Set A"))

    def test_customize_action_must_fit_both_native_scroll_viewports(self):
        for viewport, expected in (("[16,404][304,466]", True), ("[16,424][304,466]", False)):
            with self.subTest(viewport=viewport):
                root = ET.fromstring(f'''<hierarchy>
                    <node scrollable="true" bounds="{viewport}">
                        <node class="android.widget.HorizontalScrollView" scrollable="true" bounds="[16,404][304,466]">
                            <node enabled="true" clickable="true" bounds="[51,413][118,458]">
                                <node text="Set A" bounds="[67,426][102,440]" />
                            </node>
                        </node>
                    </node>
                </hierarchy>''')
                self.assertEqual(SmokeRun.fully_visible_action(root, "Set A") is not None, expected)

    def test_switch_checked_state_uses_labelled_toggle_not_sibling_text(self):
        root = ET.fromstring('''<hierarchy>
            <node text="Slow the motion down" bounds="[0,0][200,60]" />
            <node content-desc="Slow the motion down" checkable="true" checked="true" bounds="[200,0][300,60]" />
        </hierarchy>''')
        self.assertTrue(SmokeRun.checked(root, "Slow the motion down"))

    def test_switch_label_child_resolves_only_its_checkable_ancestor(self):
        for state in ("true", "false"):
            with self.subTest(state=state):
                root = ET.fromstring(f'''<hierarchy>
                    <node text="Slow the motion down" bounds="[0,0][200,60]" />
                    <node checkable="true" checked="{state}" clickable="true" bounds="[200,0][300,60]">
                        <node content-desc="Slow the motion down" checkable="false" checked="false" bounds="[220,20][280,40]" />
                    </node>
                </hierarchy>''')
                self.assertEqual(SmokeRun.checked(root, "Slow the motion down"), state == "true")

    def test_switch_checked_state_never_borrows_an_unlabelled_sibling(self):
        root = ET.fromstring('''<hierarchy>
            <node text="Slow the motion down" checkable="false" bounds="[0,0][200,60]" />
            <node checkable="true" checked="true" bounds="[200,0][300,60]" />
        </hierarchy>''')
        with self.assertRaisesRegex(AssertionError, "matching ancestry"):
            SmokeRun.checked(root, "Slow the motion down")

    def test_switch_checked_state_requires_an_explicit_boolean(self):
        root = ET.fromstring('''<hierarchy>
            <node content-desc="Slow the motion down" checkable="true" bounds="[200,0][300,60]" />
        </hierarchy>''')
        with self.assertRaisesRegex(AssertionError, "missing checked semantics"):
            SmokeRun.checked(root, "Slow the motion down")

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

    def test_theme_reselect_proves_checked_parent_without_tapping_nonclickable_radio(self):
        # Captured run-41 Settings Look shape: both exact labelled descendants
        # belong to a checked, focusable, non-clickable RadioButton ancestor.
        root = ET.fromstring('''<hierarchy>
            <node enabled="true" checkable="true" checked="true" clickable="false" focusable="true"
                  selected="false" bounds="[30,270][118,319]">
                <node content-desc="Tidal Glass" checkable="false" checked="false" clickable="false"
                      enabled="true" bounds="[30,270][118,319]" />
                <node text="Tidal Glass" checkable="false" checked="false" clickable="false"
                      enabled="true" bounds="[44,295][104,309]" />
            </node>
        </hierarchy>''')
        run = SmokeRun.__new__(SmokeRun)
        run.events = []
        seeks, taps = [], []
        def seek(label, **kwargs):
            seeks.append((label, kwargs))
            return root, SmokeRun.find(root, label)
        run.seek = seek
        run.tap_current = lambda current, label: taps.append(label)
        self.assertIsNone(SmokeRun.action(root, "Tidal Glass"))
        run.select_theme("Tidal Glass")
        self.assertEqual(taps, [])
        self.assertEqual(seeks, [("Tidal Glass", {"scroll": "horizontal", "reverse": False,
                                                "scroll_labels": THEME_LABELS})])
        self.assertTrue(SmokeRun.checked(root, "Tidal Glass"))

    def test_unchecked_theme_requires_its_real_selection_action(self):
        root = ET.fromstring('''<hierarchy>
            <node enabled="true" checkable="true" checked="false" clickable="true" bounds="[128,270][216,319]">
                <node content-desc="Lapis Lazuli" checkable="false" checked="false" bounds="[128,270][216,319]" />
            </node>
        </hierarchy>''')
        run = SmokeRun.__new__(SmokeRun)
        run.events = []
        run.seek = lambda label, **kwargs: (root, SmokeRun.find(root, label))
        taps = []
        run.tap_current = lambda current, label: taps.append((current, label))
        self.assertIsNotNone(SmokeRun.action(root, "Lapis Lazuli"))
        run.select_theme("Lapis Lazuli")
        self.assertEqual(taps, [(root, "Lapis Lazuli")])

    def test_theme_choice_without_explicit_checked_state_is_rejected(self):
        root = ET.fromstring('''<hierarchy>
            <node content-desc="Tidal Glass" selected="true" clickable="true" bounds="[30,270][118,319]" />
        </hierarchy>''')
        run = SmokeRun.__new__(SmokeRun)
        run.events = []
        run.seek = lambda label, **kwargs: (root, SmokeRun.find(root, label))
        taps = []
        run.tap_current = lambda current, label: taps.append(label)
        with self.assertRaisesRegex(AssertionError, "missing checked semantics"):
            run.select_theme("Tidal Glass")
        self.assertEqual(taps, [])

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

    def test_theme_scroll_targets_picker_below_settings_tabs(self):
        root = ET.fromstring('''<hierarchy>
            <node class="android.widget.HorizontalScrollView" scrollable="true" bounds="[0,80][320,140]">
                <node text="Look" bounds="[0,80][80,140]" />
                <node text="Behavior" bounds="[160,80][260,140]" />
            </node>
            <node class="android.view.View" scrollable="true" bounds="[0,160][800,400]">
                <node class="android.widget.HorizontalScrollView" scrollable="true" bounds="[30,220][300,290]">
                    <node content-desc="Tidal Glass" bounds="[30,220][118,290]" />
                    <node content-desc="Lapis Lazuli" bounds="[128,220][216,290]" />
                </node>
            </node>
        </hierarchy>''')
        run = SmokeRun.__new__(SmokeRun)
        run.events = []
        calls = []
        run.shell = lambda *args: calls.append(args)
        self.assertTrue(run.swipe(root, "horizontal", scroll_labels=("Tidal Glass", "Lapis Lazuli")))
        command = calls[0]
        self.assertEqual(command[:2], ("input", "swipe"))
        self.assertEqual(command[3], command[5])
        self.assertGreater(int(command[2]), int(command[4]))
        self.assertGreaterEqual(int(command[3]), 220)
        self.assertLessEqual(int(command[3]), 290)
        calls.clear()
        self.assertFalse(run.swipe(root, "horizontal", scroll_labels=("Onyx",)))
        self.assertEqual(calls, [])


if __name__ == "__main__":
    unittest.main()
