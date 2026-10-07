# Android build and verification

`.github/workflows/android.yml` is the single workflow. It replaces the former
`ship-apk.yml` and `release.yml` entry points, preserving signed AAB generation as
an explicitly requested job in the same workflow.

## Debug build

1. Push commits to a pull request or `main`, or choose **Android Build → Run workflow**.
2. Open the run and the **Build downloadable debug APK** job.
3. Download `Geode-debug-<run number>` from the run's artifacts or summary link.
4. Extract it and install **Geode-debug.apk**. Its package is `dev.geode.debug`;
   it is debug-signed and can coexist with the release application.
5. `SHA256SUMS` verifies the app and instrumentation APK; `commit.txt` identifies
   the exact checked-out commit (a merge commit for pull-request builds).

The instrumentation APK is a test runner, not a second user-facing app. Artifacts
expire after three days to limit storage usage; rerun the workflow to regenerate.
An artifact upload failure fails the debug job, because a downloadable APK is
part of that job's output contract. No build is called downloadable before upload.

## Independent checks

- **Kotlin tests, analysis and lint:** all-module Detekt, ktlint and unit tests;
  Android app lint. Reports and findings remain visible if a check fails.
- **Native and release validation tests:** host C++ motion regressions and Python
  malformed-ELF/ZIP/native-alignment regression fixtures.
- **Test the built APK on an emulator:** installs the exact uploaded APKs; checks
  instrumentation runner results and performs a fresh-install onboarding and
  navigation smoke. Saves screenshots, UI trees, logcat, memory and frame data.

APK compilation is independent of formatting/analysis, so a failed quality check
cannot erase a successfully compiled review build. All checks still gate a signed
bundle and PR merge. No checks use `continue-on-error` to claim a passing result.
The emulator is evidence for a narrow functional flow, not GPU performance,
Bluetooth latency, Play Billing, physical-device compatibility or Play approval.

The Gradle daemon and analysis JDK are 21. The compiler toolchain remains 25 and
emits JVM 17 bytecode. Pinning only JAVA_HOME did not work: the daemon criteria
file overrides it. Detekt's JDK property expects a Directory provider, not a File
provider. Both failures were observed in Actions and corrected at their source.

## Signed bundle

Push a `v*` tag, or manually run with `release_bundle=true`. The signed job waits
for every verification job. Required repository secrets remain:

- `MUSICVIZ_KEYSTORE_BASE64`
- `MUSICVIZ_KEYSTORE_PASSWORD`
- `MUSICVIZ_KEY_ALIAS`
- `MUSICVIZ_KEY_PASSWORD`

Missing secrets fail closed. The job builds and verifies the signed AAB and uploads
its checksum, R8 mapping and native symbols. It does not publish to Google Play.
Device/release checks in `PLAY_RELEASE.md` remain required before submission.
