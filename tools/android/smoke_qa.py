#!/usr/bin/env python3
"""UI-tree-driven smoke run on a disposable CI emulator, with reviewable evidence.

Uses the Test Android Apps workflow: every tap is computed from current UI XML.
This only exercises fresh-install onboarding and navigation without media access;
it is not a playback, purchase, accessibility or physical-device performance test.
"""
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "dev.geode.debug"


class SmokeRun:
    def __init__(self, serial, output):
        self.serial = serial
        self.output = output
        self.output.mkdir(parents=True, exist_ok=True)
        self.step = 0
        self.events = []

    def adb(self, *args, check=True):
        return subprocess.run(
            ["adb", "-s", self.serial, *args], check=check,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=45,
        ).stdout

    def shell(self, *args):
        return self.adb("shell", *args).decode("utf-8", errors="replace").strip()

    def capture(self, label):
        self.step += 1
        stem = f"{self.step:02d}-{label}"
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
        labels = [v for v in labels if v]
        print(f"UI {stem}: {labels}", flush=True)
        return root

    @staticmethod
    def find(root, label):
        matches = [n for n in root.iter("node") if label in (n.get("text"), n.get("content-desc"))]
        for node in matches:
            if node.get("enabled") != "false" and re.fullmatch(r"\[\d+,\d+\]\[\d+,\d+\]", node.get("bounds", "")):
                return node
        return None

    @staticmethod
    def assert_selected(root, label):
        parents = {child: parent for parent in root.iter() for child in parent}
        for node in root.iter("node"):
            if label not in (node.get("text"), node.get("content-desc")):
                continue
            current = node
            while current is not None:
                if current.get("selected") == "true":
                    return
                current = parents.get(current)
        raise AssertionError(f"Destination was not selected after tap: {label}")

    def tap(self, label):
        # Poll for boot/onboarding transitions; if a scrollable container hides
        # the action, derive the swipe from its bounds and inspect a fresh tree.
        for attempt in range(6):
            root = self.capture(f"find-{label.lower().replace(' ', '-')}-{attempt}")
            node = self.find(root, label)
            if node is not None:
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
                if x2 > x1 and y2 > y1:
                    self.shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                    self.events.append(f"Tapped {label} from {node.get('bounds')}")
                    time.sleep(1)
                    return
            scroll = next((n for n in root.iter("node") if n.get("scrollable") == "true"), None)
            if scroll is not None:
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", scroll.get("bounds")))
                self.shell("input", "swipe", str((x1 + x2) // 2), str(y1 + (y2-y1)*3//4),
                           str((x1 + x2) // 2), str(y1 + (y2-y1)//4), "350")
            time.sleep(1)
        raise AssertionError(f"UI action missing after polling and scroll: {label}")

    @staticmethod
    def assert_restarted(root):
        # ElementTree leaf nodes are falsey even when they are valid matches.
        # Test existence explicitly so the guard also detects repeated onboarding.
        if SmokeRun.find(root, "I understand") is not None or SmokeRun.find(root, "Not now") is not None:
            raise AssertionError("Onboarding did not persist across process restart")
        if SmokeRun.find(root, "Player") is None:
            raise AssertionError("Player navigation missing after restart")

    def run(self):
        metadata = {
            "serial": self.serial,
            "api": self.shell("getprop", "ro.build.version.sdk"),
            "model": self.shell("getprop", "ro.product.model"),
            "package": PACKAGE,
            "variant": "debug",
            "runs": 1,
            "scope": "fresh install, no media access, navigation, process restart",
        }
        (self.output / "device.json").write_text(json.dumps(metadata, indent=2))
        component = self.shell("cmd", "package", "resolve-activity", "--brief", PACKAGE).splitlines()[-1]
        if not component.startswith(PACKAGE + "/"):
            raise RuntimeError(f"Activity did not resolve for {PACKAGE}: {component}")
        # This script is only for an ephemeral CI emulator, never a user's data.
        if self.shell("pm", "clear", PACKAGE) != "Success":
            raise RuntimeError("Could not prepare a fresh debug install")
        self.shell("logcat", "-c")
        self.shell("am", "start", "-W", "-n", component)
        self.tap("I understand")
        self.tap("Not now")
        self.tap("Skip")
        self.shell("dumpsys", "gfxinfo", PACKAGE, "reset")
        for label in ("Library", "Visuals", "Settings", "Player"):
            self.tap(label)
            self.assert_selected(self.capture(label.lower()), label)
            if not self.shell("pidof", PACKAGE):
                raise AssertionError(f"App process died after {label}")
        (self.output / "navigation-gfxinfo.txt").write_text(self.shell("dumpsys", "gfxinfo", PACKAGE, "framestats"))
        (self.output / "navigation-meminfo.txt").write_text(self.shell("dumpsys", "meminfo", PACKAGE))
        self.shell("am", "force-stop", PACKAGE)
        self.shell("am", "start", "-W", "-n", component)
        time.sleep(2)
        root = self.capture("restart")
        self.assert_restarted(root)
        self.events.append("Onboarding persisted across process restart")

    def finish(self):
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
    crash = (args.output / "crash.txt").read_text()
    if PACKAGE in crash:
        raise AssertionError("App crash found; inspect crash.txt")
    print("PASS: fresh-install navigation and restart smoke (one emulator run)")


if __name__ == "__main__":
    main()
