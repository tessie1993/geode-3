# AndroidX Graphics Path native compatibility rebuild

GitHub Actions run `37550743637` compiled the app successfully, then rejected
the official `androidx.graphics:graphics-path:1.1.0` native binaries for both
shipped ABIs: `GNU_RELRO` did not end on a 16 KB boundary. Upgrading from the
previous transitive artifact did not resolve this.

Android's [16 KB guidance](https://developer.android.com/guide/practices/page-sizes)
requires rebuilding incompatible prebuilt libraries. App linker flags cannot
relink a native binary already packaged inside an AAR.

`src/` contains Apache-2.0 native sources from the AndroidX commit
recorded in `source-lock.json`. The source snapshot predates 1.1.0 and the
native source directory had no subsequent changes before that release. Each
file's upstream Git blob hash, or the explicitly recorded Geode-modified hash,
is checked before building. Copyright headers and
the complete upstream license are retained.

The API 30 instrumented regression in run `37552958015` exposed an upstream
iterator defect: calling `calculateSize()` populated the same conic converter
used by `next()`. A circle then emitted two extra quadratics before its first
Move (12 segments instead of 10). `PathIterator::count` now uses a separate
converter. The original hash remains in the lock beside the modified hash and
reason; the packaged notice identifies this local change. The test retains its
exact size assertion and also compares iteration with and without a size query.

`tools/android/rebuild_graphics_path.py` runs inside the shared Android CI setup:

1. Fetch the exact official 1.1.0 AAR, POM and Gradle module metadata over HTTPS
   from Google Maven. Check the AAR's size and strong hashes against that metadata.
2. Compile the three native translation units for arm64-v8a and x86_64 with the
   app's pinned NDK/CMake and Android API 26, Geode's existing minimum SDK.
3. Preserve the JNI SONAME/export map. Verify dynamic exports against the original
   binaries, reject an unexpected shared C++ runtime, and check every ELF load
   segment plus RELRO end alignment.
4. Replace only those two JNI entries. Verify all original non-replaced entries
   byte for byte, including classes.jar, consumer rules and Android metadata.
   Include the source license and rebuild notice in the resulting AAR assets.
5. Update only the AAR's metadata hashes/size. Omit unavailable documentation
   variants. Keep the original dependency POM and normal runtime/API variants.
6. Serve this coordinate from an exclusive generated local Maven repository.
   A missing preparation step fails dependency resolution instead of falling
   back to the incompatible Google artifact. No files in Gradle's cache are patched.
7. Include original/rebuilt hashes and source/toolchain provenance in the debug
   artifact and optional release artifact. The packaged APK/AAB still passes the
   independent whole-app alignment gate.

The app only ships the two 64-bit ABIs above. Any future ABI expansion must extend
this rebuild and its compatibility checks before that ABI can ship. Other ABIs
in the upstream AAR are retained unchanged and are excluded by the app ABI filters.

This is a controlled source rebuild, not an official AndroidX release. Remove the
adapter only after a newer official artifact passes the same packaged-library
checks and device tests. Runtime verification on a 16 KB device remains a release
gate even when binary inspection succeeds. The API 30 emulator exercises the
native pre-API-34 path iterator in addition to the app's usual smoke journey.
