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


if __name__ == "__main__":
    unittest.main()
