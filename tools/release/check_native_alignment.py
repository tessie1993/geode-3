#!/usr/bin/env python3
"""Fail closed on malformed or non-16-KB-compatible native libraries in APK/AABs.

Checks every PT_LOAD (not the largest alignment), GNU_RELRO end alignment and
uncompressed APK entry offsets. AAB ZIP alignment must additionally be checked
with bundletool; runtime testing on a 16-KB device is still required.
"""

import argparse
from pathlib import Path
import struct
import sys
import zipfile

PAGE_SIZE = 16384
PT_LOAD = 1
PT_GNU_RELRO = 0x6474E552


def check_elf(data: bytes) -> None:
    if len(data) < 64 or data[:4] != b"\x7fELF":
        raise ValueError("missing or truncated ELF header")
    if data[4:7] != b"\x02\x01\x01":
        raise ValueError("expected ELF64 little-endian version 1")
    phoff = struct.unpack_from("<Q", data, 32)[0]
    phentsize, phnum = struct.unpack_from("<HH", data, 54)
    if not phnum or phnum == 0xFFFF or phentsize < 56:
        raise ValueError("missing or unsupported program-header table")
    if phoff < 64 or phoff + phentsize * phnum > len(data):
        raise ValueError("program-header table is out of bounds")
    loads = 0
    for index in range(phnum):
        kind, _, offset, address, _, size, memory_size, alignment = struct.unpack_from(
            "<IIQQQQQQ", data, phoff + index * phentsize
        )
        if kind == PT_LOAD:
            loads += 1
            if alignment < PAGE_SIZE or alignment & (alignment - 1):
                raise ValueError(f"PT_LOAD[{index}] has invalid alignment {alignment}")
            if offset % alignment != address % alignment:
                raise ValueError(f"PT_LOAD[{index}] offset/address are not congruent")
            if size > memory_size or offset + size > len(data):
                raise ValueError(f"PT_LOAD[{index}] is out of bounds")
        elif kind == PT_GNU_RELRO and (address + memory_size) % PAGE_SIZE:
            raise ValueError(f"GNU_RELRO[{index}] end is not 16-KB aligned")
    if not loads:
        raise ValueError("no PT_LOAD segments")


def check_archive(path: Path) -> int:
    count = 0
    with zipfile.ZipFile(path) as archive, path.open("rb") as raw:
        for entry in archive.infolist():
            if not entry.filename.endswith(".so"):
                continue
            try:
                check_elf(archive.read(entry))
                if path.suffix.lower() == ".apk" and entry.compress_type == zipfile.ZIP_STORED:
                    raw.seek(entry.header_offset)
                    header = raw.read(30)
                    if len(header) != 30 or header[:4] != b"PK\x03\x04":
                        raise ValueError("invalid ZIP local header")
                    name_size, extra_size = struct.unpack_from("<HH", header, 26)
                    start = entry.header_offset + 30 + name_size + extra_size
                    if start % PAGE_SIZE:
                        raise ValueError(f"uncompressed APK entry starts at unaligned offset {start}")
            except ValueError as error:
                raise ValueError(f"{path.name}!{entry.filename}: {error}") from error
            count += 1
    if not count:
        raise ValueError(f"{path}: no native libraries found")
    return count


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="+", type=Path)
    args = parser.parse_args()
    archives = set()
    for path in args.paths:
        if not path.exists():
            parser.error(f"path does not exist: {path}")
        if path.is_dir():
            archives.update(p for p in path.rglob("*") if p.suffix.lower() in (".apk", ".aab"))
        elif path.suffix.lower() in (".apk", ".aab"):
            archives.add(path)
        else:
            parser.error(f"expected APK, AAB or output directory: {path}")
    if not archives:
        parser.error("no APK/AAB files found; build an artifact before verifying")
    errors = []
    for archive in sorted(archives):
        try:
            count = check_archive(archive)
            print(f"PASS {archive}: {count} native libraries")
        except (OSError, ValueError, zipfile.BadZipFile, RuntimeError) as error:
            errors.append(str(error))
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
