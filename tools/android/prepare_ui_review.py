#!/usr/bin/env python3
"""Copy a bounded set of original CI evidence into a small review artifact.

The complete emulator artifact remains authoritative. This utility neither edits
images nor changes test results; missing or oversized evidence is listed plainly.
"""
import argparse
import hashlib
import json
import shutil
from pathlib import Path

MIB = 1024 * 1024
TOTAL_BUDGET = 30 * MIB
SCREENSHOT_BUDGET = 24 * MIB
VIDEO_BUDGET = 5 * MIB
MANIFEST_RESERVE = 64 * 1024
SCREENSHOTS = (
    ("default-player", ("*-default-player.png", "*-fresh-onboarding-complete.png")),
    ("playing-player", ("*-fixture-playing-transport.png", "*-fixture-paused.png")),
    ("library", ("*-library-tracks.png", "*-default-library.png")),
    ("visuals", ("*-visuals-styles.png", "*-default-visuals.png")),
    ("studio", ("*-default-studio.png",)),
    ("settings", ("*-theme-tidal-restored.png", "*-default-settings.png")),
    ("font-200-player", ("*-compact-font-200-player.png",)),
    ("landscape-player", ("*-landscape-player.png",)),
)


def capture_number(path):
    try:
        return int(path.name.split("-", 1)[0])
    except ValueError:
        return 0


def prepare(source, output):
    output.mkdir(parents=True, exist_ok=True)
    # The folder is reserved for this disposable CI artifact, never source data.
    for previous in output.iterdir():
        if previous.is_file():
            previous.unlink()
    smoke = source / "smoke"
    manifest = {
        "scope": "selected, unmodified emulator UI evidence; full emulator artifact retains all logs and captures",
        "source_directory": str(source),
        "budgets_bytes": {"total": TOTAL_BUDGET, "screenshots_and_xml": SCREENSHOT_BUDGET,
                          "video": VIDEO_BUDGET, "manifest_reserve": MANIFEST_RESERVE},
        "copied": [], "omitted": [],
    }
    total = 0
    screenshots = 0

    def copy(path, name, role):
        nonlocal total
        size = path.stat().st_size
        destination = output / name
        shutil.copyfile(path, destination)
        total += size
        manifest["copied"].append({"role": role, "source": str(path), "review_file": name,
                                   "bytes": size, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})

    for role, patterns in SCREENSHOTS:
        image = None
        for pattern in patterns:
            candidates = sorted(smoke.glob(pattern), key=capture_number)
            image = next((path for path in candidates if path.with_suffix(".xml").is_file()), None)
            if image is not None:
                break
        if image is None:
            manifest["omitted"].append({"role": role, "reason": "no completed screenshot/XML pair", "patterns": patterns})
            continue
        xml = image.with_suffix(".xml")
        size = image.stat().st_size + xml.stat().st_size
        if screenshots + size > SCREENSHOT_BUDGET:
            manifest["omitted"].append({"role": role, "source": str(image), "reason": "screenshot/XML budget", "bytes": size})
            continue
        copy(image, f"{role}.png", role)
        copy(xml, f"{role}.xml", role)
        screenshots += size

    video = smoke / "ui-motion.mp4"
    if video.is_file() and video.stat().st_size <= VIDEO_BUDGET:
        copy(video, "ui-motion.mp4", "motion-video")
    else:
        manifest["omitted"].append({"role": "motion-video", "source": str(video),
                                    "reason": "missing or exceeds 5 MiB video budget",
                                    "bytes": video.stat().st_size if video.is_file() else None})

    metadata = [smoke / name for name in (
        "device.json", "steps.json", "original-device-configuration.json", "motion-video.json",
        "screenrecord.txt", "crash.txt", "meminfo.txt", "gfxinfo.txt",
    )] + [source / "device.txt", source / "instrumentation.txt"]
    diagnostics = sorted({path for pattern in ("*-gfxinfo.txt", "*-meminfo.txt", "*-display.txt")
                          for path in smoke.glob(pattern)})
    for path in metadata + diagnostics:
        if not path.is_file():
            manifest["omitted"].append({"role": "metadata", "source": str(path), "reason": "missing"})
            continue
        size = path.stat().st_size
        if total + size > TOTAL_BUDGET - MANIFEST_RESERVE:
            manifest["omitted"].append({"role": "metadata", "source": str(path), "reason": "total budget", "bytes": size})
            continue
        copy(path, f"emulator-{path.name}" if path.parent == source else path.name, "metadata")

    manifest["copied_bytes"] = total
    manifest["screenshots_and_xml_bytes"] = screenshots
    manifest["screenshot_count"] = sum(item["review_file"].endswith(".png") for item in manifest["copied"])
    encoded = (json.dumps(manifest, indent=2) + "\n").encode()
    if len(encoded) > MANIFEST_RESERVE or total + len(encoded) > TOTAL_BUDGET:
        raise RuntimeError("UI review manifest exceeded its reserved budget")
    (output / "manifest.json").write_bytes(encoded)
    print(f"UI review: {manifest['screenshot_count']} original PNG/XML pairs, {total + len(encoded)} bytes; {len(manifest['omitted'])} omissions recorded")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=Path("app/build/reports/emulator"))
    parser.add_argument("--output", type=Path, default=Path("app/build/reports/ui-review"))
    args = parser.parse_args()
    source = args.source.resolve()
    output = args.output.resolve()
    if source == output or source in output.parents or output in source.parents:
        raise ValueError("Review output must be outside the full emulator evidence folder")
    prepare(args.source, args.output)


if __name__ == "__main__":
    main()
