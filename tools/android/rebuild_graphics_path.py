#!/usr/bin/env python3
"""Rebuild AndroidX's native PathIterator with Geode's pinned NDK, in CI only.

The official 1.1.0 Java API, resources, consumer rules and metadata remain intact.
Only the two native binaries shipped by Geode are replaced. Generated Maven
metadata contains the rebuilt AAR's hashes, never the original AAR's hashes.
"""

import copy
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "third_party/androidx-graphics-path"
WORK = ROOT / "build/graphics-path-rebuild"
VERSION = "1.1.0"
NDK = "30.0.16248370"
CMAKE = "4.1.2"
ABIS = ("arm64-v8a", "x86_64")
NAME = f"graphics-path-{VERSION}"
MAVEN_PATH = f"androidx/graphics/graphics-path/{VERSION}"
ORIGIN = f"https://dl.google.com/dl/android/maven2/{MAVEN_PATH}"
REPOSITORY = ROOT / "build/verified-maven" / MAVEN_PATH
LIBRARY = "libandroidx.graphics.path.so"

sys.path.insert(0, str(ROOT / "tools/release"))
from check_native_alignment import check_elf  # noqa: E402


def digest(data, algorithm="sha256"):
    return hashlib.new(algorithm, data).hexdigest()


def download(suffix):
    url = f"{ORIGIN}/{NAME}.{suffix}"
    with urllib.request.urlopen(url, timeout=90) as response:
        if not response.url.startswith("https://dl.google.com/"):
            raise RuntimeError("Unexpected Maven download origin")
        data = response.read(32 * 1024 * 1024 + 1)
    if len(data) > 32 * 1024 * 1024:
        raise RuntimeError(f"Unexpectedly large {suffix} artifact")
    return data


def run(*command):
    subprocess.run([str(part) for part in command], check=True)


def verify_sources():
    lock = json.loads((SOURCE / "source-lock.json").read_text())
    for entry in lock["files"]:
        data = (SOURCE / "src" / entry["path"]).read_bytes()
        blob = b"blob " + str(len(data)).encode() + b"\0" + data
        expected = entry.get("geode_git_blob", entry["git_blob"])
        if digest(blob, "sha1") != expected:
            raise RuntimeError(f"Locked source changed: {entry['path']}")
    return lock


def symbols(readelf, library):
    output = subprocess.check_output(
        [str(readelf), "--dyn-syms", "--wide", str(library)], text=True
    )
    exported = set()
    for line in output.splitlines():
        columns = line.split()
        if len(columns) >= 8 and columns[4] in ("GLOBAL", "WEAK"):
            if columns[6] != "UND":
                exported.add(columns[7].split("@")[0])
    return exported


def rebuild_archive(archive, replacements):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as rebuilt:
        for info in archive.infolist():
            # writestr mutates ZipInfo.header_offset and size/CRC metadata.
            # Reusing the source object corrupts later reads from that archive.
            rebuilt.writestr(copy.copy(info), replacements.get(info.filename, archive.read(info)))
        for filename in ("LICENSE.txt", "NOTICE.txt"):
            rebuilt.writestr(f"assets/licenses/androidx-graphics-path/{filename}",
                             (SOURCE / filename).read_bytes())
    result = buffer.getvalue()
    with zipfile.ZipFile(io.BytesIO(result)) as rebuilt:
        for info in archive.infolist():
            if info.filename not in replacements:
                if rebuilt.read(info.filename) != archive.read(info):
                    raise RuntimeError(f"Changed upstream content: {info.filename}")
    return result


def main():
    lock = verify_sources()
    sdk_value = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk_value:
        raise RuntimeError("Android SDK location is required")
    sdk = Path(sdk_value)
    ndk = sdk / "ndk" / NDK
    cmake = sdk / "cmake" / CMAKE / "bin/cmake"
    ninja = cmake.with_name("ninja")
    readelf = ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
    WORK.mkdir(parents=True, exist_ok=True)

    module_bytes = download("module")
    module = json.loads(module_bytes)
    pom = download("pom")
    original = download("aar")
    aar_entries = [
        artifact
        for variant in module["variants"]
        for artifact in variant.get("files", [])
        if artifact.get("name") == f"{NAME}.aar"
    ]
    if not aar_entries:
        raise RuntimeError("Official metadata has no matching AAR")
    for entry in aar_entries:
        if entry.get("size") != len(original):
            raise RuntimeError("Official AAR size does not match metadata")
        if not any(algorithm in entry for algorithm in ("sha256", "sha512")):
            raise RuntimeError("Official metadata has no strong AAR checksum")
        for algorithm in ("sha256", "sha512", "sha1", "md5"):
            if algorithm in entry and digest(original, algorithm) != entry[algorithm]:
                raise RuntimeError(f"Official AAR {algorithm} does not match metadata")

    replacements = {}
    with zipfile.ZipFile(io.BytesIO(original)) as archive:
        if len(archive.namelist()) != len(set(archive.namelist())):
            raise RuntimeError("Official AAR contains duplicate entries")
        # This artifact is not a signed JAR. Fail if that upstream contract ever
        # changes; do not publish modified content under stale signatures.
        if any(name.upper().startswith("META-INF/") and
               name.upper().endswith((".SF", ".RSA", ".DSA", ".EC"))
               for name in archive.namelist()):
            raise RuntimeError("Unexpected signed AAR; requires explicit review")
        for abi in ABIS:
            entry = f"jni/{abi}/{LIBRARY}"
            old = WORK / f"original-{abi}.so"
            old.write_bytes(archive.read(entry))
            build = WORK / abi
            run(cmake, "-S", SOURCE, "-B", build, "-G", "Ninja",
                f"-DCMAKE_MAKE_PROGRAM={ninja}",
                f"-DCMAKE_TOOLCHAIN_FILE={ndk}/build/cmake/android.toolchain.cmake",
                f"-DANDROID_ABI={abi}", "-DANDROID_PLATFORM=android-26",
                "-DANDROID_STL=c++_static", "-DCMAKE_BUILD_TYPE=Release")
            run(cmake, "--build", build, "--parallel", "2")
            new = build / LIBRARY
            data = new.read_bytes()
            check_elf(data)
            dynamic = subprocess.check_output([str(readelf), "-d", str(new)], text=True)
            if f"Library soname: [{LIBRARY}]" not in dynamic:
                raise RuntimeError(f"Changed native SONAME for {abi}")
            if "libc++_shared.so" in dynamic:
                raise RuntimeError("Unexpected shared C++ runtime dependency")
            if symbols(readelf, old) != symbols(readelf, new):
                raise RuntimeError(f"Changed JNI exports for {abi}")
            replacements[entry] = data

        # Byte-for-byte preservation of all upstream non-native entries also
        # proves that classes.jar, public API and consumer rules are unchanged.
        result = rebuild_archive(archive, replacements)

    for entry in aar_entries:
        entry["size"] = len(result)
        for algorithm in ("sha256", "sha512", "sha1", "md5"):
            entry[algorithm] = digest(result, algorithm)
    # Only runtime/API AAR variants are served from this generated repository.
    # Omit source/javadoc variants rather than advertise files absent locally.
    module["variants"] = [
        variant for variant in module["variants"]
        if any(entry.get("name") == f"{NAME}.aar" for entry in variant.get("files", []))
    ]
    REPOSITORY.mkdir(parents=True, exist_ok=True)
    (REPOSITORY / f"{NAME}.aar").write_bytes(result)
    (REPOSITORY / f"{NAME}.pom").write_bytes(pom)
    (REPOSITORY / f"{NAME}.module").write_text(json.dumps(module, indent=2) + "\n")
    provenance = {
        "coordinate": f"androidx.graphics:graphics-path:{VERSION}",
        "upstream_source": lock,
        "original_aar_sha256": digest(original),
        "original_module_sha256": digest(module_bytes),
        "original_pom_sha256": digest(pom),
        "rebuilt_aar_sha256": digest(result),
        "ndk": NDK, "cmake": CMAKE, "android_api": 26,
        "libraries": {name: digest(data) for name, data in replacements.items()},
    }
    (WORK / "provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
    print(f"Prepared {NAME}: {len(replacements)} verified 16 KB native libraries")
    print(f"Rebuilt AAR SHA256: {digest(result)}")


if __name__ == "__main__":
    main()
