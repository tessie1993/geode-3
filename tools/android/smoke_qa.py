#!/usr/bin/env python3
"""UI-tree-driven smoke evidence on a disposable CI emulator.

Every tap and swipe comes from measured UI XML. Changing production screens use
fresh hierarchies; the debug component kit reuses its validated fixed layout
while recording, then proves the resulting callback and selection state. This covers onboarding,
generated-fixture playback, UI preferences and adaptive navigation. Emulator frame
and memory reports are emulator diagnostics. This does not measure physical
route latency or audio fidelity, test export or purchase, or assess all accessibility.
"""
import argparse
from array import array
import json
import math
import re
import subprocess
import sys
import time
import wave
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "dev.geode.debug"
DESTINATIONS = ("Player", "Library", "Visuals", "Studio", "Settings")
LIBRARY_TABS = ("Tracks", "Albums", "Artists", "Folders", "Playlists")
VISUALS_TABS = ("Presets", "Styles", "Customize", "Textures", "Takes")
SETTINGS_TABS = ("Look", "Audio", "Export", "Folders", "Behavior", "Help", "About")
THEMES = (
    ("Tidal Glass", "tidal-glass"), ("Lapis Lazuli", "lapis-lazuli"),
    ("Sugilite", "sugilite"), ("Amethyst", "amethyst"),
    ("Clear Quartz", "clear-quartz"), ("Azurite", "azurite"),
    ("Firestone", "firestone"), ("Kyanite", "kyanite"),
    ("Malachite", "malachite"), ("Mookaite", "mookaite"), ("Onyx", "onyx"),
)
THEME_LABELS = tuple(name for name, _ in THEMES)
CUSTOMIZE_AB_LABELS = ("A/B", "Set A", "Recall A", "Set B", "Recall B")
MOTION_LABEL = "Slow the motion down"
ANIMATION_SETTINGS = (
    "window_animation_scale", "transition_animation_scale", "animator_duration_scale",
)
DEVICE_SETTINGS = (
    ("system", "font_scale"), ("system", "accelerometer_rotation"),
    ("system", "user_rotation"),
) + tuple(("global", name) for name in ANIMATION_SETTINGS)


def slug(label):
    return re.sub(r"[^a-z0-9]+", "-", label.lower()).strip("-")


class SmokeRun:
    def __init__(self, serial, output):
        self.serial = serial
        self.output = output
        self.output.mkdir(parents=True, exist_ok=True)
        self.step = 0
        self.events = []
        self.configuration = None
        self.component = None
        self.fixtures = []

    def adb(self, *args, check=True):
        return subprocess.run(
            ["adb", "-s", self.serial, *args], check=check,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=45,
        ).stdout

    def shell(self, *args):
        return self.adb("shell", *args).decode("utf-8", errors="replace").strip()

    def capture(self, label):
        self.step += 1
        stem = f"{self.step:02d}-{slug(label)}"
        for attempt in range(3):
            raw = self.adb("exec-out", "uiautomator", "dump", "/dev/tty").decode(
                "utf-8", errors="replace"
            )
            start = raw.find("<?xml")
            end = raw.rfind("</hierarchy>")
            if start >= 0 and end >= 0:
                break
            (self.output / f"{stem}-capture-{attempt}.txt").write_text(raw)
            time.sleep(1)
        if start < 0 or end < 0:
            raise RuntimeError(f"No UI hierarchy for {label}: {raw[:300]}")
        xml = raw[start:end + len("</hierarchy>")]
        (self.output / f"{stem}.xml").write_text(xml)
        (self.output / f"{stem}.png").write_bytes(self.adb("exec-out", "screencap", "-p"))
        root = ET.fromstring(xml)
        labels = [n.get("text") or n.get("content-desc") for n in root.iter("node")]
        print(f"UI {stem}: {[value for value in labels if value]}", flush=True)
        return root

    @staticmethod
    def bounds(node):
        value = node.get("bounds", "")
        if not re.fullmatch(r"\[-?\d+,-?\d+\]\[-?\d+,-?\d+\]", value):
            return None
        x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", value))
        if x1 < 0 or y1 < 0 or x2 <= x1 or y2 <= y1:
            return None
        return x1, y1, x2, y2

    @staticmethod
    def matches(root, label):
        return [n for n in root.iter("node") if label in (n.get("text"), n.get("content-desc"))]

    @staticmethod
    def find(root, label, include_disabled=False):
        for node in SmokeRun.matches(root, label):
            if (include_disabled or node.get("enabled") != "false") and SmokeRun.bounds(node):
                return node
        return None

    @staticmethod
    def action(root, label):
        """Resolve the exact label to its interactive or active-tab ancestor.

        Compose exports the active Tab as selected/focusable but non-clickable
        in Android's hierarchy. Its current XML bounds remain the target for a
        harmless reselect; unselected controls still require clickable=true.
        """
        parents = {child: parent for parent in root.iter() for child in parent}
        for node in SmokeRun.matches(root, label):
            current = node
            while current is not None:
                if current.get("enabled") == "false":
                    break
                interactive = current.get("clickable") == "true"
                active_tab = current.get("selected") == "true" and current.get("focusable") == "true"
                if (interactive or active_tab) and SmokeRun.bounds(current):
                    return current
                current = parents.get(current)
        return None

    @staticmethod
    def fully_visible_action(root, label):
        node = SmokeRun.action(root, label)
        if node is None:
            return None
        x1, y1, x2, y2 = SmokeRun.bounds(node)
        parents = {child: parent for parent in root.iter() for child in parent}
        current = parents.get(node)
        while current is not None:
            if current.get("scrollable") == "true":
                viewport = SmokeRun.bounds(current)
                if viewport is None:
                    return None
                vx1, vy1, vx2, vy2 = viewport
                if not (vx1 <= x1 < x2 <= vx2 and vy1 <= y1 < y2 <= vy2):
                    return None
            current = parents.get(current)
        return node

    @staticmethod
    def assert_selected(root, label, *, allow_checked=False):
        parents = {child: parent for parent in root.iter() for child in parent}
        for node in SmokeRun.matches(root, label):
            current = node
            while current is not None:
                if current.get("selected") == "true" or (
                    allow_checked and current.get("checkable") == "true" and current.get("checked") == "true"
                ):
                    return
                current = parents.get(current)
        raise AssertionError(f"Destination or choice was not selected after tap: {label}")

    @staticmethod
    def assert_not_selected(root, label, *, allow_checked=False):
        SmokeRun.assert_labels(root, label)
        try:
            SmokeRun.assert_selected(root, label, allow_checked=allow_checked)
        except AssertionError:
            return
        raise AssertionError(f"Choice remained selected after toggling off: {label}")

    @staticmethod
    def assert_labels(root, *labels):
        missing = [label for label in labels if SmokeRun.find(root, label, include_disabled=True) is None]
        if missing:
            raise AssertionError(f"Visible semantic labels missing: {missing}")

    @staticmethod
    def checked(root, label):
        parents = {child: parent for parent in root.iter() for child in parent}
        inspected = []
        for node in SmokeRun.matches(root, label):
            path = []
            current = node
            while current is not None:
                path.append({key: current.get(key) for key in
                             ("class", "checkable", "checked", "clickable", "bounds")})
                if current.get("checkable") == "true" and current.get("checked") in ("true", "false"):
                    return current.get("checked") == "true"
                current = parents.get(current)
            inspected.append(path)
        raise AssertionError(f"Labelled control missing checked semantics: {label}; matching ancestry: {inspected}")

    def swipe(self, root, direction, reverse=False, scroll_labels=None):
        candidates = []
        for node in root.iter("node"):
            bounds = self.bounds(node)
            if node.get("scrollable") != "true" or bounds is None:
                continue
            if scroll_labels and not any(self.matches(node, label) for label in scroll_labels):
                continue
            x1, y1, x2, y2 = bounds
            class_name = node.get("class", "")
            known_horizontal = "HorizontalScrollView" in class_name
            known_vertical = "ScrollView" in class_name and not known_horizontal
            # Compose can export a generic View for a scroller. A wide, short
            # landscape content list must remain a vertical candidate: use its
            # area below, rather than treating its aspect ratio as proof of axis.
            if direction == "vertical":
                eligible = not known_horizontal
            else:
                eligible = known_horizontal or (not known_vertical and x2 - x1 > (y2 - y1) * 2)
            if eligible:
                candidates.append((node, bounds))
        if scroll_labels:
            # The theme picker is below the Settings tab row. Restrict the
            # gesture to the innermost live scroller containing theme labels,
            # including generic Compose Views exported in landscape.
            candidates = [
                item for item in candidates
                if not any(other is not item[0] and other in item[0].iter("node")
                           for other, _ in candidates)
            ]
        if not candidates:
            return False
        # Tabs are the top horizontal scroller; the content list is the largest
        # vertical scroller. Never use screenshot coordinates or fixed offsets.
        if direction == "horizontal":
            node, (x1, y1, x2, y2) = min(candidates, key=lambda item: item[1][1])
            start = (x1 + (x2 - x1) * 3 // 4, (y1 + y2) // 2)
            end = (x1 + (x2 - x1) // 4, (y1 + y2) // 2)
        else:
            node, (x1, y1, x2, y2) = max(
                candidates, key=lambda item: (item[1][2] - item[1][0]) * (item[1][3] - item[1][1])
            )
            start = ((x1 + x2) // 2, y1 + (y2 - y1) * 3 // 4)
            end = ((x1 + x2) // 2, y1 + (y2 - y1) // 4)
        if reverse:
            start, end = end, start
        self.shell("input", "swipe", *(str(value) for value in (*start, *end)), "350")
        self.events.append(f"Scrolled {direction} from {node.get('bounds')}, reverse={reverse}")
        return True

    def seek(self, label, *, clickable=False, scroll=None, reverse=False, scroll_labels=None, fully_visible=False):
        for attempt in range(8):
            root = self.capture(f"find-{slug(label)}-{attempt}")
            if clickable:
                node = self.fully_visible_action(root, label) if fully_visible else self.action(root, label)
            else:
                node = self.find(root, label, include_disabled=True)
            if node is not None:
                return root, node
            if scroll:
                self.swipe(root, scroll, reverse, scroll_labels)
            time.sleep(1)
        raise AssertionError(f"UI action missing after polling and tree-derived scroll: {label}")

    def tap(self, label, *, scroll=None, reverse=False, scroll_labels=None, settle=1):
        root, _ = self.seek(label, clickable=True, scroll=scroll, reverse=reverse, scroll_labels=scroll_labels)
        self.tap_current(root, label, settle=settle)

    def tap_current(self, root, label, *, settle=1):
        node = self.action(root, label)
        if node is None:
            raise AssertionError(f"Current hierarchy has no enabled action: {label}")
        x1, y1, x2, y2 = self.bounds(node)
        self.shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        self.events.append(f"Tapped {label} from {node.get('bounds')}")
        time.sleep(settle)

    def select_theme(self, name, *, reverse=False):
        # A checked Compose RadioButton is exported as non-clickable. Prove its
        # exact labelled checked ancestry before skipping a redundant selection;
        # an unchecked choice still needs its real enabled action and callback.
        root, _ = self.seek(name, scroll="horizontal", reverse=reverse, scroll_labels=THEME_LABELS)
        if self.checked(root, name):
            self.events.append(f"Theme {name} already checked in its exact labelled ancestry")
        else:
            # A visible label can belong to a clipped radio whose midpoint is
            # outside the picker. Scroll its whole enabled action into view and
            # use the fresh hierarchy before tapping; preferences checks success.
            root, _ = self.seek(name, clickable=True, fully_visible=True, scroll="horizontal",
                                reverse=reverse, scroll_labels=THEME_LABELS)
            self.tap_current(root, name)

    def component_kit(self):
        """Exercise the debug material surface before any production UI or playback."""
        component = f"{PACKAGE}/dev.geode.ui.SpatialComponentKitActivity"
        supports_recording = bool(self.adb("shell", "which", "screenrecord", check=False).strip())
        remote = "/sdcard/geode-component-motion.mp4"
        try:
            self.shell("wm", "size", "720x1600")
            self.shell("wm", "density", "320")
            self.shell("settings", "put", "system", "font_scale", "1.0")
            self.shell("settings", "put", "system", "accelerometer_rotation", "0")
            self.shell("settings", "put", "system", "user_rotation", "0")
            for setting in ANIMATION_SETTINGS:
                self.shell("settings", "put", "global", setting, "1.0")
            display = {
                "scope": "debug component kit only; restored before MainActivity",
                "requested": {"size": "720x1600", "density": 320, "font_scale": 1.0,
                              "accelerometer_rotation": 0, "user_rotation": 0},
                "wm_size": self.shell("wm", "size"), "wm_density": self.shell("wm", "density"),
                "settings": [{"namespace": ns, "key": key, "value": self.shell("settings", "get", ns, key)}
                             for ns, key in DEVICE_SETTINGS],
            }
            (self.output / "component-kit-display.json").write_text(json.dumps(display, indent=2))
            for name, theme_slug in THEMES:
                self.shell("am", "start", "-S", "-W", "-f", "0x10008000", "-n", component,
                           "--es", "theme_slug", theme_slug)
                root = self.capture(f"component-kit-{theme_slug}")
                self.assert_labels(root, "Component kit", name, "Awaken", "Scene: 0", "Matte reading panel", "Interactions: 0",
                                   "Press capsule", "Pressed preview", "Selected capsule", "Disabled capsule",
                                   "Press round button", "Pressed round preview", "Selected round button", "Disabled round button")
                self.assert_selected(root, name, allow_checked=True)
                self.assert_selected(root, "Selected capsule", allow_checked=True)
                self.assert_selected(root, "Selected round button", allow_checked=True)
                for disabled in ("Disabled capsule", "Disabled round button"):
                    if self.action(root, disabled) is not None:
                        raise AssertionError(f"Component kit disabled control is interactive: {disabled}")
                self.component_movie(root, theme_slug, remote, supports_recording)
            self.events.append("All eleven debug component themes captured; actual capsule/round callbacks and selection verified")
        finally:
            self.adb("shell", "rm", "-f", remote, check=False)
            self.restore_configuration()
            restored = {
                "scope": "original device configuration restored before MainActivity",
                "wm_size": self.shell("wm", "size"), "wm_density": self.shell("wm", "density"),
                "settings": [{"namespace": ns, "key": key, "value": self.shell("settings", "get", ns, key)}
                             for ns, key in DEVICE_SETTINGS],
            }
            (self.output / "component-kit-display-restored.json").write_text(json.dumps(restored, indent=2))
            # MainActivity is started once by run() immediately after this returns.
            self.shell("am", "force-stop", PACKAGE)

    def component_movie(self, root, theme_slug, remote, supports_recording):
        stem = f"component-motion-{theme_slug}"
        result = {"scope": "debug shared components; no audio, GL or foreground service", "theme": theme_slug,
                  "time_limit_seconds": 14, "bit_rate": 700000, "recording_tail_seconds": 1,
                  "action_timing_reference": "tap completion since recorder launch, before settling",
                  "actions": [], "action_elapsed_seconds": {}}
        labels = ("Awaken", "Press capsule", "Press round button", "Selected capsule")
        # This debug screen has fixed-height rows and no scroll or navigation.
        # Awaken only resets its scene generation; these labels and bounds stay
        # fixed through all three callbacks. Validate every native target before
        # recording instead of spending most of the film dumping unchanged XML.
        recorder = None
        try:
            targets = {}
            for label in labels:
                node = self.fully_visible_action(root, label)
                if node is None or node.get("clickable") != "true":
                    raise AssertionError(f"Component movie target is not fully visible and enabled: {label}")
                targets[label] = node.get("bounds")
            result["validated_target_bounds"] = targets
            started = time.monotonic()
            if supports_recording:
                recorder = subprocess.Popen(
                    ["adb", "-s", self.serial, "shell", "screenrecord", "--time-limit", "14",
                     "--bit-rate", "700000", remote], stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                )
            time.sleep(0.2)
            self.tap_current(root, "Awaken", settle=0)
            result["actions"].append("Tapped Awaken")
            result["action_elapsed_seconds"]["Awaken"] = round(time.monotonic() - started, 2)
            time.sleep(1.8)
            for label in labels[1:]:
                self.tap_current(root, label, settle=0)
                result["actions"].append(f"Tapped {label}")
                result["action_elapsed_seconds"][label] = round(time.monotonic() - started, 2)
                time.sleep(0.5)
            root = self.capture(f"component-action-{theme_slug}-3")
            self.assert_labels(root, "Scene: 1", "Interactions: 3")
            self.assert_not_selected(root, "Selected capsule", allow_checked=True)
            self.assert_selected(root, "Selected round button", allow_checked=True)
            for label, bounds in targets.items():
                node = self.fully_visible_action(root, label)
                if node is None or node.get("clickable") != "true" or node.get("bounds") != bounds:
                    raise AssertionError(f"Component movie target changed its measured layout: {label}")
            if recorder is not None:
                stdout, stderr = recorder.communicate(timeout=25)
                (self.output / f"{stem}-screenrecord.txt").write_bytes(stdout + stderr)
                if recorder.returncode:
                    raise RuntimeError(f"Component screenrecord exited {recorder.returncode}: {theme_slug}")
                destination = self.output / f"{stem}.mp4"
                self.adb("pull", remote, str(destination))
                if not destination.stat().st_size:
                    raise RuntimeError(f"Component screenrecord was empty: {theme_slug}")
                self.assert_recorded_actions(result, labels)
                result["status"] = "recorded"
            else:
                result["status"] = "unsupported: screenrecord unavailable; native callbacks still verified"
        finally:
            if recorder is not None and recorder.poll() is None:
                recorder.kill()
                recorder.communicate(timeout=10)
            self.adb("shell", "rm", "-f", remote, check=False)
            result.setdefault("status", "failed; inspect exact component UI and recording diagnostics")
            (self.output / f"{stem}.json").write_text(json.dumps(result, indent=2))

    @staticmethod
    def assert_recorded_actions(result, expected):
        """A successful capture must contain every action and its settling tail."""
        deadline = result["time_limit_seconds"] - result["recording_tail_seconds"]
        result["actions_within_recording"] = [
            label for label, elapsed in result["action_elapsed_seconds"].items()
            if math.isfinite(elapsed) and 0 <= elapsed <= deadline
        ]
        missing = [label for label in expected if label not in result["actions_within_recording"]]
        if missing:
            raise AssertionError(f"Motion actions missing from recording before {deadline}s tail deadline: {missing}")

    def visit(self, label, profile="default"):
        self.tap(label)
        root = self.capture(f"{profile}-{label}")
        self.assert_selected(root, label)
        self.assert_labels(root, *DESTINATIONS)
        if not self.shell("pidof", PACKAGE):
            raise AssertionError(f"App process died after {label}")
        return root

    @staticmethod
    def assert_restarted(root):
        # ElementTree leaf nodes are falsey even when they are valid matches.
        if SmokeRun.find(root, "I understand") is not None or SmokeRun.find(root, "Not now") is not None:
            raise AssertionError("Onboarding did not persist across process restart")
        if SmokeRun.find(root, "Player") is None:
            raise AssertionError("Player navigation missing after restart")

    def restart(self, label):
        self.shell("am", "force-stop", PACKAGE)
        self.shell("am", "start", "-W", "-n", self.component)
        time.sleep(2)
        root = self.capture(label)
        self.assert_restarted(root)
        self.events.append("Onboarding persisted across process restart")
        return root

    def save_configuration(self):
        self.configuration = {
            "settings": [{"namespace": ns, "key": key, "value": self.shell("settings", "get", ns, key)}
                         for ns, key in DEVICE_SETTINGS],
            "wm_size": self.shell("wm", "size"),
            "wm_density": self.shell("wm", "density"),
        }
        (self.output / "original-device-configuration.json").write_text(json.dumps(self.configuration, indent=2))

    def restore_setting(self, namespace, key):
        entry = next(item for item in self.configuration["settings"]
                     if item["namespace"] == namespace and item["key"] == key)
        if entry["value"] in ("", "null"):
            self.shell("settings", "delete", namespace, key)
        else:
            self.shell("settings", "put", namespace, key, entry["value"])

    def restore_configuration(self):
        if self.configuration is None:
            return
        errors = []
        for entry in self.configuration["settings"]:
            try:
                self.restore_setting(entry["namespace"], entry["key"])
            except (subprocess.SubprocessError, RuntimeError) as error:
                errors.append(str(error))
        for kind in ("size", "density"):
            override = re.search(r"Override (?:size|density):\s*(\S+)", self.configuration[f"wm_{kind}"])
            try:
                self.shell("wm", kind, override.group(1) if override else "reset")
            except (subprocess.SubprocessError, RuntimeError) as error:
                errors.append(str(error))
        if errors:
            raise RuntimeError(f"Device configuration restoration failed: {errors}")
        self.events.append("Restored font scale, rotation, display overrides and animation scales")

    def diagnostic_reports(self, label):
        for report, command in {
            "gfxinfo": ("dumpsys", "gfxinfo", PACKAGE, "framestats"),
            "meminfo": ("dumpsys", "meminfo", PACKAGE),
            "display": ("dumpsys", "display"),
        }.items():
            (self.output / f"{label}-{report}.txt").write_text(self.shell(*command))

    def transport(self, profile, *, has_media=False):
        play_label = "Pause" if has_media else "Play"
        self.seek(play_label, scroll="vertical")
        root = self.capture(f"{profile}-transport")
        self.assert_labels(root, "Shuffle", "Previous", play_label, "Next", "Repeat", *DESTINATIONS)
        parents = {child: parent for parent in root.iter() for child in parent}
        for label in ("Previous", play_label, "Next"):
            disabled = False
            node = self.find(root, label, include_disabled=True)
            while node is not None:
                disabled = disabled or node.get("enabled") == "false"
                node = parents.get(node)
            if disabled == has_media:
                if has_media:
                    raise AssertionError(f"Loaded-track transport should be enabled: {label}")
                raise AssertionError(f"Empty-queue transport should be disabled: {label}")
        self.events.append(f"{profile}: transport retained; has_media={has_media}, playing={has_media}")

    @staticmethod
    def fixture_directory(canonical_external):
        root = canonical_external.rstrip("/")
        if re.fullmatch(r"/storage/emulated/\d+", root) is None:
            raise AssertionError(f"External storage did not resolve to a canonical emulator volume: {canonical_external!r}")
        return f"{root}/Music/GeodeUiQa"

    @staticmethod
    def indexed_audio_row(rows, filename, expected_remote):
        for row in rows.splitlines():
            if f"_display_name={filename}," not in row:
                continue
            title = re.search(r"title=(.*?), duration=", row)
            length = re.search(r"duration=(\d+)", row)
            music = re.search(r"is_music=(\d+)", row)
            data = re.search(r"(?:^|, )_data=(.*)$", row)
            if (title and title.group(1) and length and int(length.group(1)) >= 44000
                    and music and music.group(1) == "1" and data and data.group(1) == expected_remote):
                return {"title": title.group(1), "media_store_row": row}
        return None

    def seed_audio(self):
        """Create deterministic, original PCM fixtures; verify actual MediaStore ingestion."""
        # Run-36's MediaProvider could not traverse /sdcard -> self/primary in
        # its SELinux namespace. Resolve that alias in the shell namespace and
        # broadcast the actual /storage/emulated/<user> path, not the alias.
        canonical_external = self.shell("readlink", "-f", "/sdcard")
        (self.output / "fixture-storage-root.txt").write_text(f"readlink -f /sdcard: {canonical_external}\n")
        remote_dir = self.fixture_directory(canonical_external)
        self.shell("mkdir", "-p", remote_dir)
        directories = self.adb("shell", "ls", "-ldZ", "/sdcard", "/storage/self/primary",
                               canonical_external, remote_dir, check=False).decode("utf-8", errors="replace")
        (self.output / "fixture-storage-root.txt").write_text(
            f"readlink -f /sdcard: {canonical_external}\nfixture directory: {remote_dir}\n{directories}"
        )
        fixture_dir = self.output / "audio-fixtures"
        fixture_dir.mkdir(exist_ok=True)
        sample_rate, duration = 44100, 45
        for name, frequency in (("geode_qa_river_a", 220), ("geode_qa_river_b", 330)):
            path = fixture_dir / f"{name}.wav"
            pcm = array("h")
            for sample in range(sample_rate * duration):
                seconds = sample / sample_rate
                envelope = min(1.0, seconds * 8, (duration - seconds) * 8)
                pulse = 0.72 + 0.28 * math.sin(2 * math.pi * 2 * seconds)
                value = int(5000 * envelope * pulse * math.sin(2 * math.pi * frequency * seconds))
                pcm.extend((value, value))
            if sys.byteorder != "little":
                pcm.byteswap()
            with wave.open(str(path), "wb") as audio:
                audio.setnchannels(2)
                audio.setsampwidth(2)
                audio.setframerate(sample_rate)
                audio.writeframes(pcm.tobytes())
            remote = f"{remote_dir}/{path.name}"
            self.adb("push", str(path), remote)
            storage = {"path": remote, "expected_bytes": path.stat().st_size}
            try:
                storage["ls_with_selinux_context"] = self.shell("ls", "-lZ", remote)
                storage["stat_mode_uid_gid_bytes"] = self.shell("stat", "-c", "%a:%u:%g:%s", remote)
                if int(storage["stat_mode_uid_gid_bytes"].split(":")[-1]) != storage["expected_bytes"]:
                    raise AssertionError(f"Pushed WAV size differs from original fixture: {remote}")
                self.adb("shell", "test", "-r", remote)
                storage["shell_readable"] = True
                header = self.adb("exec-out", "head", "-c", "12", remote)
                storage["header_hex"] = header.hex()
                if header[:4] != b"RIFF" or header[8:12] != b"WAVE":
                    raise AssertionError(f"Pushed fixture is not readable PCM WAV: {remote}")
            finally:
                (self.output / f"fixture-storage-{name}.json").write_text(json.dumps(storage, indent=2))
            scan = self.shell("am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", f"file://{remote}")
            (self.output / f"scanner-{name}.txt").write_text(scan)
            self.fixtures.append({"file": path.name, "remote": remote, "seconds": duration,
                                  "sample_rate": sample_rate, "channels": 2, "pcm_bits": 16})
        for _ in range(30):
            rows = self.shell("content", "query", "--uri", "content://media/external/audio/media",
                              "--projection", "_id:_display_name:title:duration:is_music:_data")
            (self.output / "media-store-fixtures.txt").write_text(rows)
            for fixture in self.fixtures:
                indexed = self.indexed_audio_row(rows, fixture["file"], fixture["remote"])
                if indexed is not None:
                    fixture.update(indexed)
            if all("title" in fixture for fixture in self.fixtures):
                break
            time.sleep(1)
        else:
            raise AssertionError("Generated WAVs were not indexed as playable Music tracks; inspect media-store-fixtures.txt and scanner logs")
        (self.output / "audio-fixtures.json").write_text(json.dumps(self.fixtures, indent=2))
        self.events.append("Two original 45-second stereo PCM WAV fixtures indexed by MediaStore")

    @staticmethod
    def seek_node(root):
        return next((node for node in root.iter("node")
                     if node.get("content-desc", "").startswith("Seek. ") and SmokeRun.bounds(node)), None)

    @staticmethod
    def position_seconds(node):
        match = re.fullmatch(r"Seek\. (\d+):(\d{2}) of (\d+):(\d{2})", node.get("content-desc", ""))
        if match is None:
            raise AssertionError("Seek control lacks current position and duration semantics")
        minutes, seconds, duration_minutes, duration_seconds = map(int, match.groups())
        return minutes * 60 + seconds, duration_minutes * 60 + duration_seconds

    @staticmethod
    def current_hero_track(root):
        """Read the title in the exact PlayerHero status/metadata column.

        Run-35's hierarchy exports this column as overline, title, subtitle.
        QueuePreview and the mini-player are outside that immediate subtree.
        """
        parents = {child: parent for parent in root.iter() for child in parent}
        for status in root.iter("node"):
            if status.get("text") not in ("NOW PLAYING", "PAUSED") or not SmokeRun.bounds(status):
                continue
            metadata = parents.get(status)
            if metadata is None or metadata.tag != "node":
                continue
            texts = [node for node in metadata.findall("node")
                     if node.get("text") and SmokeRun.bounds(node)]
            if len(texts) >= 2 and texts[0] is status:
                return texts[1].get("text")
        return None

    def seek_current_hero_track(self, expected, label):
        actual = None
        for attempt in range(8):
            root = self.capture(f"{label}-current-hero-{attempt}")
            actual = self.current_hero_track(root)
            if actual == expected:
                self.events.append(f"Current PlayerHero track verified: {expected}")
                return root
            self.swipe(root, "vertical", reverse=True)
            time.sleep(1)
        raise AssertionError(f"Current PlayerHero track did not become {expected!r}; last hero title={actual!r}")

    @staticmethod
    def assert_position_advanced(before_node, after_node):
        before, duration = SmokeRun.position_seconds(before_node)
        after, after_duration = SmokeRun.position_seconds(after_node)
        if duration <= 0 or after_duration != duration or after < before + 2:
            raise AssertionError(f"Playback position did not advance >=2s: before={before}, after={after}, duration={duration}/{after_duration}")
        return before, after, duration

    def playback_progress(self):
        before_root = self.capture("resumed-playback-position-before")
        before_node = self.seek_node(before_root)
        if before_node is None or self.action(before_root, "Pause") is None:
            raise AssertionError("Resumed playback needs enabled Pause and a visible position control")
        time.sleep(2)
        after_root = self.capture("resumed-playback-position-after")
        after_node = self.seek_node(after_root)
        if after_node is None or self.action(after_root, "Pause") is None:
            raise AssertionError("Playback or position control disappeared while measuring elapsed progress")
        before, after, duration = self.assert_position_advanced(before_node, after_node)
        self.events.append(f"Resumed playback advanced {before}s → {after}s of {duration}s")

    def playback(self):
        self.visit("Library")
        self.tap("Tracks", scroll="horizontal", reverse=True)
        self.tap(self.fixtures[0]["title"], scroll="vertical")
        self.visit("Player")
        self.seek_current_hero_track(self.fixtures[0]["title"], "fixture-playing")
        self.capture("fixture-playing-hero")
        self.transport("fixture-playing", has_media=True)
        self.tap("Pause", scroll="vertical")
        self.assert_labels(self.capture("fixture-paused"), "Play")
        self.tap("Next", scroll="vertical")
        self.seek_current_hero_track(self.fixtures[1]["title"], "next-track")
        self.capture("fixture-next-track")
        # Stay paused so Media3's Previous action goes to the preceding item
        # rather than restarting an item that has already played >3 seconds.
        self.tap("Previous", scroll="vertical")
        self.seek_current_hero_track(self.fixtures[0]["title"], "previous-track")
        self.capture("fixture-previous-track")
        root, _ = self.seek("Play", scroll="vertical")
        slider = self.seek_node(root)
        if slider is None:
            raise AssertionError("Seek control missing alongside Player transport")
        before, duration = self.position_seconds(slider)
        x1, y1, x2, y2 = self.bounds(slider)
        self.shell("input", "swipe", str(x1 + (x2 - x1) // 5), str((y1 + y2) // 2),
                   str(x1 + (x2 - x1) * 3 // 5), str((y1 + y2) // 2), "450")
        time.sleep(1)
        root = self.capture("fixture-seeked")
        after_slider = self.seek_node(root)
        if after_slider is None:
            raise AssertionError("Seek control disappeared after gesture")
        after, after_duration = self.position_seconds(after_slider)
        if duration < 44 or after_duration != duration or not (duration * 0.4 <= after <= duration * 0.8) or after <= before + 8:
            raise AssertionError(f"Seek did not change position as expected: before={before}, after={after}, duration={duration}")
        self.events.append(f"Tree-derived seek changed paused position {before}s → {after}s of {duration}s")
        self.tap("Shuffle", scroll="vertical")
        self.assert_selected(self.capture("shuffle-enabled"), "Shuffle", allow_checked=True)
        self.tap("Shuffle", scroll="vertical")
        self.assert_not_selected(self.capture("shuffle-restored"), "Shuffle", allow_checked=True)
        self.tap("Repeat", scroll="vertical")
        self.assert_selected(self.capture("repeat-all"), "Repeat", allow_checked=True)
        self.tap("Repeat", scroll="vertical")
        self.assert_selected(self.capture("repeat-one"), "Repeat", allow_checked=True)
        self.tap("Repeat", scroll="vertical")
        self.assert_not_selected(self.capture("repeat-off"), "Repeat", allow_checked=True)
        self.tap("Repeat", scroll="vertical")
        self.assert_selected(self.capture("repeat-all-for-navigation"), "Repeat", allow_checked=True)
        self.tap("Play", scroll="vertical")
        self.playback_progress()
        self.transport("fixture-resumed", has_media=True)
        for destination in DESTINATIONS:
            self.visit(destination, "playing")
        self.visit("Player", "playing")
        self.transport("playing-after-navigation", has_media=True)
        self.events.append("Actual playback pause/resume, Next/Previous, seek, Shuffle/Repeat and five destinations verified")

    def ensure_playing(self, profile):
        self.visit("Player", profile)
        root, _ = self.seek("Repeat", scroll="vertical")
        if self.action(root, "Pause") is None:
            if self.action(root, "Play") is not None:
                self.tap("Play", scroll="vertical")
            else:
                self.visit("Library", profile)
                self.tap("Tracks", scroll="horizontal", reverse=True)
                self.tap(self.fixtures[0]["title"], scroll="vertical")
                self.visit("Player", profile)
        self.transport(profile, has_media=True)

    def search(self):
        self.tap("Search", scroll="vertical", reverse=True)
        root = self.capture("search-open")
        self.assert_labels(root, "Close search", "Search tracks, playlists & presets", "Type to search your music")
        field = next((node for node in root.iter("node")
                      if node.get("class") == "android.widget.EditText" and self.bounds(node)), None)
        if field is None:
            raise AssertionError("Search text input missing from UI tree")
        x1, y1, x2, y2 = self.bounds(field)
        self.shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        query = "geode-ui-smoke-no-result"
        self.shell("input", "text", query)
        time.sleep(1)
        root = self.capture("search-no-results")
        self.assert_labels(root, f"No results for “{query}”")
        self.tap("Close search")
        root = self.capture("search-closed")
        if self.find(root, "Close search") is not None:
            raise AssertionError("Search overlay did not close")
        self.assert_labels(root, *DESTINATIONS)
        self.events.append("Search accepted a query, reported no results and closed")

    def tabs(self, destination, labels):
        self.visit(destination)
        for label in labels:
            self.tap(label, scroll="horizontal")
            root = self.capture(f"{destination}-{label}")
            self.assert_selected(root, label)
            self.assert_selected(root, destination)
            if destination == "Library" and label == "Playlists":
                self.assert_labels(root, "New playlist", "Import playlist…")
            if destination == "Visuals" and label == "Textures":
                self.assert_labels(root, "Import images", "No textures imported yet.")
            if destination == "Visuals" and label == "Customize":
                self.customize_toolbar()
        self.events.append(f"All {len(labels)} {destination} tabs selected and captured")

    def customize_toolbar(self):
        self.seek("Set A", clickable=True, fully_visible=True, scroll="vertical")
        root = self.capture("visuals-customize-toolbar-a")
        self.assert_selected(root, "Visuals")
        self.assert_selected(root, "Customize")
        if self.fully_visible_action(root, "Set A") is None:
            raise AssertionError("Customize Set A action was clipped by its actual scroll viewport")
        self.seek("Set B", clickable=True, fully_visible=True, scroll="horizontal", scroll_labels=CUSTOMIZE_AB_LABELS)
        root = self.capture("visuals-customize-toolbar-b")
        if self.fully_visible_action(root, "Set B") is None:
            raise AssertionError("Customize Set B action was clipped by its actual scroll viewport")
        self.events.append("Customize Set A and Set B enabled actions fully contained in their native scroll viewports")

    def live_visualizer(self):
        # View live expands the existing shared native VisualizerView. Its exact
        # collapse icon label comes from VisualizerScreen's action_collapse string.
        self.tap("View live", scroll="vertical", reverse=True)
        root = self.capture("live-visualizer")
        self.assert_labels(root, "Collapse")
        if self.action(root, "Collapse") is None:
            raise AssertionError("Live visualizer had no enabled native Collapse action")
        self.tap_current(root, "Collapse")
        root = self.capture("live-visualizer-collapsed")
        self.assert_selected(root, "Visuals")
        self.assert_selected(root, "Takes")
        self.assert_labels(root, *DESTINATIONS, "View live")
        if self.find(root, "Collapse") is not None:
            raise AssertionError("Live visualizer Collapse action remained after dismissal")
        if not self.shell("pidof", PACKAGE):
            raise AssertionError("App process died after collapsing the live visualizer")
        self.events.append("Existing live visualizer opened and collapsed to the original Visuals Takes tab")

    def preferences(self):
        self.visit("Settings")
        self.tap("Look", scroll="horizontal", reverse=True)
        root, _ = self.seek("Tidal Glass", scroll="horizontal", scroll_labels=THEME_LABELS)
        if not self.checked(root, "Tidal Glass"):
            raise AssertionError("Fresh-install default theme RadioButton was not checked: Tidal Glass")
        self.events.append("Fresh-install default theme is Tidal Glass")
        for name, theme_slug in THEMES:
            self.select_theme(name)
            root = self.capture(f"glass-picker-{theme_slug}")
            if not self.checked(root, name):
                raise AssertionError(f"Theme RadioButton was not checked after selection: {name}")
            self.visit("Player", f"glass-{theme_slug}")
            self.seek("Search", scroll="vertical", reverse=True)
            root = self.capture(f"glass-theme-{theme_slug}")
            if self.current_hero_track(root) not in {fixture["title"] for fixture in self.fixtures}:
                raise AssertionError(f"Loaded PlayerHero missing after selecting theme: {name}")
            self.visit("Settings", f"glass-{theme_slug}")
            self.tap("Look", scroll="horizontal", reverse=True)
        self.events.append("All eleven built-in themes selected and captured with the actual loaded PlayerHero")
        self.select_theme("Tidal Glass", reverse=True)
        if not self.checked(self.capture("theme-tidal-restored"), "Tidal Glass"):
            raise AssertionError("Restored theme RadioButton was not checked: Tidal Glass")
        self.restart("theme-tidal-restart")
        self.visit("Settings", "tidal-theme-persisted")
        self.tap("Look", scroll="horizontal", reverse=True)
        root, _ = self.seek("Tidal Glass", scroll="horizontal", scroll_labels=THEME_LABELS)
        if not self.checked(root, "Tidal Glass"):
            raise AssertionError("Restored theme RadioButton was not checked after restart: Tidal Glass")
        self.events.append("Restored Tidal Glass theme persisted across process restart")
        self.ensure_playing("tidal-theme")
        self.visit("Settings", "tidal-theme")
        self.tap("Behavior", scroll="horizontal")
        root, _ = self.seek(MOTION_LABEL, scroll="vertical")
        before = self.checked(root, MOTION_LABEL)
        if before:
            raise AssertionError("Fresh-install reduced motion was unexpectedly enabled")
        self.tap(MOTION_LABEL, scroll="vertical")
        if not self.checked(self.capture("reduced-motion-enabled"), MOTION_LABEL):
            raise AssertionError("Reduced-motion switch did not enable")
        self.restart("preferences-restart")
        self.visit("Settings")
        self.tap("Behavior", scroll="horizontal")
        root, _ = self.seek(MOTION_LABEL, scroll="vertical")
        if not self.checked(root, MOTION_LABEL):
            raise AssertionError("Reduced-motion preference did not persist across restart")
        self.tap(MOTION_LABEL, scroll="vertical")
        if self.checked(self.capture("reduced-motion-restored"), MOTION_LABEL):
            raise AssertionError("Reduced-motion switch did not restore")
        self.events.append("Theme switching and reduced-motion persistence verified")

    def profiles(self):
        # A 360 dp portrait viewport at 200% system font scale. The exact display
        # configuration and UI trees are artifacts so visual review can assess it.
        self.shell("wm", "size", "1080x1920")
        self.shell("wm", "density", "480")
        self.shell("settings", "put", "system", "accelerometer_rotation", "0")
        self.shell("settings", "put", "system", "user_rotation", "0")
        self.shell("settings", "put", "system", "font_scale", "2.0")
        self.restart("compact-font-200-restart")
        self.ensure_playing("compact-font-200")
        for label in DESTINATIONS:
            self.visit(label, "compact-font-200")
        self.visit("Player", "compact-font-200")
        self.transport("compact-font-200", has_media=True)
        self.diagnostic_reports("compact-font-200")
        self.shell("settings", "put", "system", "font_scale", "1.0")
        self.shell("settings", "put", "system", "user_rotation", "1")
        self.restart("landscape-restart")
        self.ensure_playing("landscape")
        for label in DESTINATIONS:
            self.visit(label, "landscape")
        self.visit("Player", "landscape")
        self.transport("landscape", has_media=True)
        self.diagnostic_reports("landscape")
        self.restore_configuration()
        self.restart("original-display-restored")
        self.ensure_playing("restored")
        for label in DESTINATIONS:
            self.visit(label, "restored")
        self.visit("Player", "restored")
        self.events.append("All five destinations replayed at 200% compact, landscape and restored display")

    def motion_video(self):
        result = {"scope": "emulator UI animation sample; no physical-device performance claim",
                  "time_limit_seconds": 40, "bit_rate": 700000, "recording_tail_seconds": 1,
                  "action_timing_reference": "tap completion since recorder launch, before settling"}
        if not self.adb("shell", "which", "screenrecord", check=False).strip():
            result["status"] = "unsupported: screenrecord unavailable"
            (self.output / "motion-video.json").write_text(json.dumps(result, indent=2))
            return
        remote = "/sdcard/geode-ui-motion.mp4"
        recorder = None
        try:
            for setting in ANIMATION_SETTINGS:
                self.shell("settings", "put", "global", setting, "1.0")
            # Restart returns the scroll position to the Search action and gives
            # Compose the current animation policy before recording water motion.
            self.restart("motion-ready")
            self.ensure_playing("motion-ready")
            self.seek("Search", scroll="vertical", reverse=True)
            started = time.monotonic()
            recorder = subprocess.Popen(
                ["adb", "-s", self.serial, "shell", "screenrecord", "--time-limit", "40", "--bit-rate", "700000", remote],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            )
            time.sleep(0.2)
            result["actions"] = []
            result["action_elapsed_seconds"] = {}
            labels = ("Library", "Player", "Search", "Close search")
            for label in labels:
                self.tap(label, scroll="vertical" if label == "Search" else None,
                         reverse=label == "Search", settle=0)
                result["actions"].append(f"Tapped {label}")
                result["action_elapsed_seconds"][label] = round(time.monotonic() - started, 2)
                time.sleep(0.15)
            stdout, stderr = recorder.communicate(timeout=55)
            (self.output / "screenrecord.txt").write_bytes(stdout + stderr)
            if recorder.returncode:
                raise RuntimeError(f"screenrecord exited {recorder.returncode}")
            self.adb("pull", remote, str(self.output / "ui-motion.mp4"))
            if not (self.output / "ui-motion.mp4").stat().st_size:
                raise RuntimeError("screenrecord produced an empty video")
            self.assert_recorded_actions(result, labels)
            result["status"] = "recorded"
            self.events.append("Recorded 40-second animation sample with every navigation/Search action inside the recording and a settling tail, animation scales temporarily enabled")
        finally:
            if recorder is not None and recorder.poll() is None:
                recorder.kill()
                recorder.communicate(timeout=10)
            for setting in ANIMATION_SETTINGS:
                self.restore_setting("global", setting)
            self.adb("shell", "rm", "-f", remote, check=False)
            result.setdefault("status", "failed; inspect UI logs and screenrecord diagnostics")
            (self.output / "motion-video.json").write_text(json.dumps(result, indent=2))

    def run(self):
        metadata = {
            "serial": self.serial,
            "api": self.shell("getprop", "ro.build.version.sdk"),
            "model": self.shell("getprop", "ro.product.model"),
            "package": PACKAGE, "variant": "debug", "runs": 1,
            "scope": "eleven debug component kits, fresh onboarding, empty queue, generated WAV playback, five destinations, search, ten Library/Visuals tabs, Customize toolbar visibility, native live visualizer collapse, seven Settings tabs, eleven themes, preferences, process restart, adaptive UI",
            "performance_scope": "emulator gfxinfo/meminfo only; no physical latency, GPU or frame-rate guarantee",
            "profiles": {"compact-font-200": {"size": "1080x1920", "density": 480, "font_scale": 2.0},
                         "landscape": {"size": "1080x1920", "density": 480, "font_scale": 1.0, "user_rotation": 1}},
        }
        (self.output / "device.json").write_text(json.dumps(metadata, indent=2))
        self.save_configuration()
        self.component = self.shell("cmd", "package", "resolve-activity", "--brief", PACKAGE).splitlines()[-1]
        if not self.component.startswith(PACKAGE + "/"):
            raise RuntimeError(f"Activity did not resolve for {PACKAGE}: {self.component}")
        # This script is only for an ephemeral CI emulator, never a user's data.
        if self.shell("pm", "clear", PACKAGE) != "Success":
            raise RuntimeError("Could not prepare a fresh debug install")
        self.shell("logcat", "-c")
        self.component_kit()
        self.shell("am", "start", "-W", "-n", self.component)
        self.tap("I understand")
        self.tap("Not now")
        self.tap("Skip")
        self.capture("fresh-onboarding-complete")
        permission = "android.permission.READ_MEDIA_AUDIO" if int(metadata["api"]) >= 33 else "android.permission.READ_EXTERNAL_STORAGE"
        self.shell("pm", "grant", PACKAGE, permission)
        self.events.append(f"Granted {permission} after onboarding for Library browsing and generated-fixture playback")
        self.shell("dumpsys", "gfxinfo", PACKAGE, "reset")
        self.visit("Player")
        self.transport("default")
        self.search()
        self.seed_audio()
        self.tabs("Library", LIBRARY_TABS)
        self.playback()
        self.tabs("Visuals", VISUALS_TABS)
        self.live_visualizer()
        root = self.visit("Studio")
        self.assert_labels(root, "Open a video…", "NOTHING RENDERED YET")
        self.tabs("Settings", SETTINGS_TABS)
        self.preferences()
        self.diagnostic_reports("default-navigation")
        self.profiles()
        self.motion_video()
        self.restart("final-restart")
        self.ensure_playing("final")
        self.visit("Settings", "final")
        self.tap("Look", scroll="horizontal", reverse=True)
        root, _ = self.seek("Tidal Glass", scroll="horizontal")
        self.assert_selected(root, "Tidal Glass", allow_checked=True)
        self.visit("Player", "final")

    def finish(self):
        try:
            self.restore_configuration()
        finally:
            for name, args in {
                "crash.txt": ("logcat", "-b", "crash", "-d"),
                "logcat.txt": ("logcat", "-d", "-t", "4000"),
                "meminfo.txt": ("dumpsys", "meminfo", PACKAGE),
                "gfxinfo.txt": ("dumpsys", "gfxinfo", PACKAGE, "framestats"),
            }.items():
                (self.output / name).write_bytes(self.adb("shell", *args, check=False))
            (self.output / "steps.json").write_text(json.dumps(self.events, indent=2))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--serial")
    args = parser.parse_args()
    serial = args.serial
    if not serial:
        devices = subprocess.check_output(["adb", "devices"], text=True)
        ready = [line.split()[0] for line in devices.splitlines() if line.endswith("\tdevice")]
        if len(ready) != 1:
            raise RuntimeError(f"Expected exactly one test device, found {len(ready)}")
        serial = ready[0]
    if not serial.startswith("emulator-"):
        raise RuntimeError("Destructive fresh-install smoke is restricted to a disposable emulator")
    run = SmokeRun(serial, args.output)
    try:
        run.run()
    finally:
        run.finish()
    if PACKAGE in (args.output / "crash.txt").read_text():
        raise AssertionError("App crash found; inspect crash.txt")
    print("PASS: fixture playback, UI navigation, preferences, adaptive captures and restart smoke (one emulator run)")


if __name__ == "__main__":
    main()
