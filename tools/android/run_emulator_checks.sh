#!/usr/bin/env bash
set -euo pipefail
report_dir=app/build/reports/emulator
package=dev.geode.debug
mkdir -p "$report_dir"
mapfile -t ready_devices < <(adb devices | awk '$2 == "device" { print $1 }')
if [ "${#ready_devices[@]}" -ne 1 ]; then
  echo "Expected exactly one disposable CI emulator, found ${#ready_devices[@]}" >&2
  exit 1
fi
android_serial=${ready_devices[0]}
if [[ "$android_serial" != emulator-* ]]; then
  echo "Permission changes and fresh-install smoke are restricted to a disposable emulator" >&2
  exit 1
fi

capture_instrumentation_evidence() {
  adb -s "$android_serial" logcat -b crash -d > "$report_dir/instrumentation-crash.txt" 2>&1 || true
  adb -s "$android_serial" logcat -d -v threadtime > "$report_dir/instrumentation-logcat.txt" 2>&1 || true
  adb -s "$android_serial" logcat -d -v threadtime \
    MicLifecycleTest:I MicCapture:I AAudioMicSource:I AudioRecordSource:I geode.mic:I '*:S' \
    > "$report_dir/mic-logcat.txt" 2>&1 || true
  for phase in before after; do
    adb -s "$android_serial" exec-out run-as "$package" cat "cache/mic-lifecycle-$phase-meminfo.txt" \
      > "$report_dir/mic-lifecycle-$phase-meminfo.txt" 2>&1 || true
  done
  mkdir -p "$report_dir/spatial-scene-review"
  adb -s "$android_serial" exec-out run-as "$package" tar -C cache -cf - spatial-scene-review \
    | tar -xf - -C "$report_dir" || true
}

finish() {
  status=$?
  # Save instrumentation logs before smoke_qa.py clears Logcat for its UI flow.
  if [ ! -f "$report_dir/instrumentation-logcat.txt" ]; then
    capture_instrumentation_evidence
  fi
  adb -s "$android_serial" logcat -b crash -d > "$report_dir/crash.txt" 2>&1 || true
  exit "$status"
}
trap finish EXIT
{
  printf 'serial=%s\npackage=%s\nvariant=debug\nplanned_mic_sessions=3\n' "$android_serial" "$package"
  printf 'api=%s\n' "$(adb -s "$android_serial" shell getprop ro.build.version.sdk | tr -d '\r')"
  printf 'model=%s\n' "$(adb -s "$android_serial" shell getprop ro.product.model | tr -d '\r')"
  printf 'android=%s\n' "$(adb -s "$android_serial" shell getprop ro.build.version.release | tr -d '\r')"
  printf 'scope=real PCM and repeated stop/start; no physical route or latency measurements\n'
} > "$report_dir/device.txt"
adb -s "$android_serial" shell pm list features > "$report_dir/device-features.txt"
adb -s "$android_serial" install -r dist/Geode-debug.apk
adb -s "$android_serial" install -r dist/Geode-debug-androidTest.apk
adb -s "$android_serial" shell pm grant "$package" android.permission.RECORD_AUDIO
adb -s "$android_serial" logcat -c
# am instrument can exit 0 even when tests fail; require the runner's success summary.
adb -s "$android_serial" shell am instrument -w -r dev.geode.debug.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "$report_dir/instrumentation.txt"
capture_instrumentation_evidence
if ! grep -Eq 'OK \([1-9][0-9]* tests?\)' "$report_dir/instrumentation.txt"; then
  exit 1
fi
python3 tools/android/smoke_qa.py --serial "$android_serial" --output "$report_dir/smoke"
