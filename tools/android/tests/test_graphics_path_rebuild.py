import io
from pathlib import Path
import sys
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from rebuild_graphics_path import rebuild_archive


class GraphicsPathArchiveTest(unittest.TestCase):
    def test_replacing_native_bytes_preserves_source_archive_metadata_and_java_api(self):
        source = io.BytesIO()
        with zipfile.ZipFile(source, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("jni/arm64-v8a/libandroidx.graphics.path.so", b"old native")
            archive.writestr("classes.jar", bytes(range(256)) * 40)
            archive.writestr("proguard.txt", b"keep the JNI entry points")
            archive.writestr("AndroidManifest.xml", b"original manifest")
        with zipfile.ZipFile(io.BytesIO(source.getvalue())) as original:
            before = {info.filename: (info.header_offset, info.CRC, info.file_size)
                      for info in original.infolist()}
            replacement = b"new compiled native binary" * 1024
            result = rebuild_archive(original, {
                "jni/arm64-v8a/libandroidx.graphics.path.so": replacement,
            })
            after = {info.filename: (info.header_offset, info.CRC, info.file_size)
                     for info in original.infolist()}
            self.assertEqual(before, after)
            with zipfile.ZipFile(io.BytesIO(result)) as rebuilt:
                self.assertEqual(replacement, rebuilt.read("jni/arm64-v8a/libandroidx.graphics.path.so"))
                for filename in ("classes.jar", "proguard.txt", "AndroidManifest.xml"):
                    self.assertEqual(original.read(filename), rebuilt.read(filename))
                self.assertIn("assets/licenses/androidx-graphics-path/LICENSE.txt", rebuilt.namelist())


if __name__ == "__main__":
    unittest.main()
