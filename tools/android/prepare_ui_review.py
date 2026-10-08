#!/usr/bin/env python3
"""Copy a bounded set of original CI evidence into a small review artifact.

The complete emulator artifact remains authoritative. This utility neither edits
images nor changes test results; missing or oversized evidence is listed plainly.
"""
import argparse
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

MIB = 1024 * 1024
TOTAL_BUDGET = 30 * MIB
SCREENSHOT_BUDGET = 24 * MIB
VIDEO_BUDGET = 5 * MIB
MANIFEST_RESERVE = 64 * 1024
THEME_SLUGS = ("living-lake",)
COMPONENT_SCREENSHOTS = tuple(
    (f"component-kit-{theme}", (f"*-component-kit-{theme}.png",)) for theme in THEME_SLUGS
)
PRIMARY_SCREENSHOTS = (
    ("navigation-orbit", ("*-navigation-selected-orbit-player.png", "*-default-orbit-player.png")),
    ("default-player", ("*-fixture-playing-hero.png", "*-default-player.png")),
    ("playing-player", ("*-fixture-playing-transport.png", "*-fixture-paused.png")),
    ("library-tracks", ("*-library-tracks.png",)),
    ("visuals-styles", ("*-visuals-styles.png",)),
    ("studio", ("*-default-studio.png",)),
    ("settings", ("*-settings-home.png", "*-settings-look.png", "*-playing-settings.png", "*-default-settings.png")),
    ("live-visualizer", ("*-live-visualizer.png",)),
) + tuple(
    (f"library-{tab}", (f"*-library-{tab}.png",))
    for tab in ("albums", "artists", "folders", "playlists")
) + tuple(
    (f"visuals-{tab}", (f"*-visuals-{tab}.png",))
    for tab in ("presets", "customize", "textures", "takes")
)
ADAPTIVE_SCREENSHOTS = tuple(
    (f"font-200-{destination}", (f"*-compact-font-200-{destination}.png",))
    for destination in ("player", "library", "visuals", "studio", "settings")
) + tuple(
    (f"landscape-{destination}", (f"*-landscape-{destination}.png",))
    for destination in ("player", "library", "visuals", "studio", "settings")
)
EXTRA_SCREENSHOTS = (
    ("living-lake-theme", ("*-theme-lake-default.png",)),
    ("navigation-back", ("*-navigation-back-library.png",)),
    ("navigation-close", ("*-navigation-cancelled-player.png",)),
    ("visuals-customize-toolbar-a", ("*-visuals-customize-toolbar-a.png",)),
    ("visuals-customize-toolbar-b", ("*-visuals-customize-toolbar-b.png",)),
) + ADAPTIVE_SCREENSHOTS + tuple(
    (f"glass-theme-{theme}", (f"*-glass-theme-{theme}.png",))
    for theme in THEME_SLUGS
) + tuple(
    (f"settings-{tab}", (f"*-settings-{tab}.png",))
    for tab in ("look", "audio", "export", "folders", "behavior", "help", "about")
)
SCREENSHOTS = COMPONENT_SCREENSHOTS + PRIMARY_SCREENSHOTS + EXTRA_SCREENSHOTS
COMPONENT_VIDEOS = tuple(
    (f"component-motion-{theme}.mp4", f"component-motion-{theme}") for theme in THEME_SLUGS
)


def capture_number(path):
    try:
        return int(path.name.split("-", 1)[0])
    except ValueError:
        return 0


def loaded_player_hero(xml):
    try:
        tree = ET.parse(xml)
    except (ET.ParseError, OSError):
        return False
    return any(node.get("text") in ("NOW PLAYING", "PAUSED") for node in tree.iter("node"))


def prepare(source, output, *, screens_only=False):
    output.mkdir(parents=True, exist_ok=True)
    # The folder is reserved for this disposable CI artifact, never source data.
    for previous in output.iterdir():
        if previous.is_file():
            previous.unlink()
    smoke = source / "smoke"
    manifest = {
        "scope": "selected, unmodified emulator UI evidence; full emulator artifact retains all logs and captures",
        "source_directory": str(source),
        "screens_only": screens_only,
        "selection": "latest completed PNG/XML pair for each preferred named capture; loaded Player hero required",
        "priority": ("last UI tree; component previews; orbit, main screens and Library/Visuals tabs; "
                     "Customize toolbars and adaptive sizes; Living Lake theme and Settings groups"
                     if screens_only else
                     "last UI tree; component previews and motion; orbit, main screens and Library/Visuals tabs; "
                     "main motion; Customize toolbars and adaptive sizes; Living Lake theme and Settings groups"),
        "requested_screenshot_roles": [role for role, _ in SCREENSHOTS],
        "budgets_bytes": {"total": TOTAL_BUDGET, "screenshots_and_xml": SCREENSHOT_BUDGET,
                          "video_per_file": VIDEO_BUDGET, "manifest_reserve": MANIFEST_RESERVE},
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

    def copy_screenshots(roles):
        nonlocal screenshots
        for role, patterns in roles:
            image = None
            for pattern in patterns:
                candidates = sorted(smoke.glob(pattern), key=capture_number, reverse=True)
                image = next((path for path in candidates if path.is_file()
                              and path.with_suffix(".xml").is_file()
                              and (role != "default-player" or loaded_player_hero(path.with_suffix(".xml")))), None)
                if image is not None:
                    break
            if image is None:
                reason = "no completed loaded Player hero screenshot/XML pair" if role == "default-player" else "no completed screenshot/XML pair"
                manifest["omitted"].append({"role": role, "reason": reason, "patterns": patterns})
                continue
            xml = image.with_suffix(".xml")
            size = image.stat().st_size + xml.stat().st_size
            reason = None
            if screenshots + size > SCREENSHOT_BUDGET:
                reason = "screenshot/XML budget"
            elif total + size > TOTAL_BUDGET - MANIFEST_RESERVE:
                reason = "total budget"
            if reason:
                manifest["omitted"].append({"role": role, "source": str(image), "reason": reason, "bytes": size})
                continue
            copy(image, f"{role}.png", role)
            copy(xml, f"{role}.xml", role)
            screenshots += size

    def copy_videos(roles):
        for filename, role in roles:
            video = smoke / filename
            if not video.is_file():
                manifest["omitted"].append({"role": role, "source": str(video), "reason": "missing"})
                continue
            size = video.stat().st_size
            video_metadata = smoke / f"{role}.json"
            metadata_size = video_metadata.stat().st_size if video_metadata.is_file() else 0
            reason = None
            if size == 0:
                reason = "empty video"
            elif size > VIDEO_BUDGET:
                reason = "exceeds 5 MiB per-file video budget"
            elif total + size + metadata_size > TOTAL_BUDGET - MANIFEST_RESERVE:
                reason = "total budget"
            if reason:
                manifest["omitted"].append({"role": role, "source": str(video), "reason": reason, "bytes": size})
                continue
            copy(video, filename, role)
            if video_metadata.is_file():
                copy(video_metadata, video_metadata.name, f"{role}-metadata")

    last_ui = max((path for path in smoke.glob("[0-9]*.xml") if path.is_file()),
                  key=capture_number, default=None)
    if last_ui is None:
        manifest["omitted"].append({"role": "last-ui.xml", "reason": "no captured UI XML"})
    elif total + last_ui.stat().st_size > TOTAL_BUDGET - MANIFEST_RESERVE:
        manifest["omitted"].append({"role": "last-ui.xml", "source": str(last_ui),
                                    "reason": "total budget", "bytes": last_ui.stat().st_size})
    else:
        copy(last_ui, "last-ui.xml", "last-ui.xml")

    copy_screenshots(COMPONENT_SCREENSHOTS)
    if not screens_only:
        copy_videos(COMPONENT_VIDEOS)
    copy_screenshots(PRIMARY_SCREENSHOTS)
    if not screens_only:
        copy_videos((("ui-motion.mp4", "motion-video"),))
    copy_screenshots(EXTRA_SCREENSHOTS)

    metadata = [smoke / name for name in (
        "component-kit-display.json", "component-kit-display-restored.json",
        "device.json", "steps.json", "original-device-configuration.json", "motion-video.json",
        "screenrecord.txt", "crash.txt", "meminfo.txt", "gfxinfo.txt",
    )] + [smoke / f"component-motion-{theme}.json" for theme in THEME_SLUGS
    ] + [source / "device.txt", source / "instrumentation.txt"]
    diagnostics = sorted({path for pattern in ("*-gfxinfo.txt", "*-meminfo.txt", "*-display.txt")
                          for path in smoke.glob(pattern)})
    for path in metadata + diagnostics:
        if not path.is_file():
            manifest["omitted"].append({"role": "metadata", "source": str(path), "reason": "missing"})
            continue
        review_name = f"emulator-{path.name}" if path.parent == source else path.name
        if (output / review_name).is_file():
            continue
        size = path.stat().st_size
        if total + size > TOTAL_BUDGET - MANIFEST_RESERVE:
            manifest["omitted"].append({"role": "metadata", "source": str(path), "reason": "total budget", "bytes": size})
            continue
        copy(path, review_name, "metadata")

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
    parser.add_argument("--screens-only", action="store_true",
                        help="Copy original screenshots, UI XML and metadata without movies")
    args = parser.parse_args()
    source = args.source.resolve()
    output = args.output.resolve()
    if source == output or source in output.parents or output in source.parents:
        raise ValueError("Review output must be outside the full emulator evidence folder")
    prepare(args.source, args.output, screens_only=args.screens_only)


if __name__ == "__main__":
    main()
