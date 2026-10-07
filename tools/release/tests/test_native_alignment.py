"""Regression fixtures for the release gate; run in GitHub Actions."""

import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

MODULE_PATH = Path(__file__).resolve().parents[1] / "check_native_alignment.py"
SPEC = importlib.util.spec_from_file_location("native_alignment", MODULE_PATH)
alignment = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(alignment)


def elf(alignments=(16384, 16384), relro_end=None):
    count = len(alignments) + (relro_end is not None)
    data = bytearray(64 + 56 * count)
    data[:7] = b"\x7fELF\x02\x01\x01"
    struct.pack_into("<Q", data, 32, 64)
    struct.pack_into("<HH", data, 54, 56, count)
    for index, size in enumerate(alignments):
        struct.pack_into("<IIQQQQQQ", data, 64 + 56 * index, 1, 5, 0, 0, 0, len(data), len(data), size)
    if relro_end is not None:
        struct.pack_into("<IIQQQQQQ", data, 64 + 56 * len(alignments), 0x6474E552, 4, 0, 0, 0, 0, relro_end, 1)
    return bytes(data)


class NativeAlignmentTest(unittest.TestCase):
    def test_every_load_segment_must_align(self):
        alignment.check_elf(elf())
        alignment.check_elf(elf((65536, 16384)))
        for sizes in ((4096, 16384), (16384, 4096), (0, 16384), (24576, 16384)):
            with self.subTest(sizes=sizes), self.assertRaises(ValueError):
                alignment.check_elf(elf(sizes))

    def test_malformed_files_do_not_pass(self):
        for data in (b"", b"\x7f" + bytes(100), elf()[:70], elf(())):
            with self.subTest(data_length=len(data)), self.assertRaises(ValueError):
                alignment.check_elf(data)
        data = bytearray(elf())
        struct.pack_into("<Q", data, 32, 2**63)
        with self.assertRaises(ValueError):
            alignment.check_elf(data)

    def test_load_offset_must_match_virtual_address(self):
        data = bytearray(elf())
        struct.pack_into("<Q", data, 64 + 16, 4096)
        with self.assertRaises(ValueError):
            alignment.check_elf(data)

    def test_relro_end_must_align(self):
        alignment.check_elf(elf(relro_end=16384))
        with self.assertRaises(ValueError):
            alignment.check_elf(elf(relro_end=4096))

    def test_all_libraries_in_an_aab_are_checked(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.aab"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("base/lib/arm64-v8a/good.so", elf())
                archive.writestr("base/lib/x86_64/bad.so", elf((4096, 16384)))
            with self.assertRaisesRegex(ValueError, "bad.so"):
                alignment.check_archive(path)

    def test_apk_entry_alignment_is_distinct_from_elf_alignment(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.apk"
            name = "lib/arm64-v8a/test.so"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr(name, elf())
            with self.assertRaisesRegex(ValueError, "APK entry"):
                alignment.check_archive(path)
            info = zipfile.ZipInfo(name)
            padding = 16384 - 30 - len(name.encode())
            info.extra = struct.pack("<HH", 0xCAFE, padding - 4) + bytes(padding - 4)
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr(info, elf())
            self.assertEqual(1, alignment.check_archive(path))

    def test_empty_archive_fails_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.aab"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("manifest.txt", "no native code")
            with self.assertRaisesRegex(ValueError, "no native"):
                alignment.check_archive(path)


if __name__ == "__main__":
    unittest.main()
