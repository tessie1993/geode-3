#!/usr/bin/env bash
set -euo pipefail
report_dir=app/build/reports/emulator
package=dev.geode.debug
instrumentation_timeout_seconds=600
instrumentation_log_pid=
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
  timeout --kill-after=2s 15s adb -s "$android_serial" logcat -b crash -d \
    > "$report_dir/instrumentation-crash.txt" 2>&1 || true
  timeout --kill-after=2s 15s adb -s "$android_serial" logcat -d -v threadtime \
    > "$report_dir/instrumentation-logcat.txt" 2>&1 || true
  timeout --kill-after=2s 15s adb -s "$android_serial" logcat -d -v threadtime \
    MicLifecycleTest:I MicCapture:I AAudioMicSource:I AudioRecordSource:I geode.mic:I '*:S' \
    > "$report_dir/mic-logcat.txt" 2>&1 || true
  for phase in before after; do
    timeout --kill-after=2s 15s adb -s "$android_serial" exec-out run-as "$package" \
      cat "cache/mic-lifecycle-$phase-meminfo.txt" \
      > "$report_dir/mic-lifecycle-$phase-meminfo.txt" 2>&1 || true
  done
  mkdir -p "$report_dir/spatial-scene-review"
  timeout --kill-after=2s 30s adb -s "$android_serial" exec-out run-as "$package" \
    tar -C cache -cf - spatial-scene-review \
    | tar -xf - -C "$report_dir" || true
}

print_evidence_tail() {
  local label=$1
  local evidence_file=$2
  local lines=$3
  printf '\n[emulator evidence] %s (last %s lines)\n' "$label" "$lines"
  if [ -s "$evidence_file" ]; then
    tail -n "$lines" "$evidence_file" || true
  else
    printf 'Evidence unavailable: %s\n' "$evidence_file"
  fi
}

print_instrumentation_stall() {
  # Job logs remain readable even when the artifact download cannot be opened.
  print_evidence_tail "fluid switch progress" "$report_dir/spatial-scene-review/fluid-switch-progress.txt" 60
  print_evidence_tail "live instrumentation stages" "$report_dir/instrumentation-stage-logcat.txt" 60
  local native_stacks="$report_dir/instrumentation-native-stacks.txt"
  local key_frames='libgeode|libGLES|libEGL|SwiftShader|name: Instr|Permission denied|failed|error'
  printf '\n[emulator evidence] native stack excerpt (at most 120 lines)\n'
  if [ -s "$native_stacks" ]; then
    if grep -Eq "$key_frames" "$native_stacks"; then
      grep -E -B 6 -A 12 "$key_frames" "$native_stacks" | head -n 120 || true
    else
      head -n 40 "$native_stacks" || true
    fi
  else
    printf 'Native stack evidence unavailable: %s\n' "$native_stacks"
  fi
}

capture_instrumentation_stall() {
  # Capture while the instrumented process still exists; force-stop comes last.
  timeout --kill-after=2s 15s adb -s "$android_serial" shell ps -A -T \
    > "$report_dir/instrumentation-threads.txt" 2>&1 || true
  timeout --kill-after=2s 15s adb -s "$android_serial" shell dumpsys meminfo "$package" \
    > "$report_dir/instrumentation-meminfo.txt" 2>&1 || true
  timeout --kill-after=2s 15s adb -s "$android_serial" shell dumpsys activity processes \
    > "$report_dir/instrumentation-processes.txt" 2>&1 || true
  local app_pid
  app_pid=$(timeout --kill-after=2s 10s adb -s "$android_serial" shell pidof -s "$package" | tr -d '\r') || true
  if [[ "$app_pid" =~ ^[0-9]+$ ]]; then
    printf 'package=%s\npid=%s\n' "$package" "$app_pid" > "$report_dir/instrumentation-pid.txt"
    # Both are best-effort on the API-30 debug emulator; denied access is retained.
    timeout --kill-after=2s 20s adb -s "$android_serial" shell run-as "$package" debuggerd -b "$app_pid" \
      > "$report_dir/instrumentation-native-stacks.txt" 2>&1 || true
    timeout --kill-after=2s 10s adb -s "$android_serial" shell run-as "$package" kill -3 "$app_pid" \
      > "$report_dir/instrumentation-java-stack-request.txt" 2>&1 || true
  fi
  capture_instrumentation_evidence
  print_instrumentation_stall
  timeout --kill-after=2s 10s adb -s "$android_serial" shell am force-stop "$package" \
    > "$report_dir/instrumentation-force-stop.txt" 2>&1 || true
}

finish() {
  status=$?
  # Save instrumentation logs before smoke_qa.py clears Logcat for its UI flow.
  if [ ! -f "$report_dir/instrumentation-logcat.txt" ]; then
    capture_instrumentation_evidence
  fi
  if [ -n "$instrumentation_log_pid" ]; then
    kill "$instrumentation_log_pid" 2>/dev/null || true
    wait "$instrumentation_log_pid" 2>/dev/null || true
  fi
  timeout --kill-after=2s 15s adb -s "$android_serial" logcat -b crash -d \
    > "$report_dir/crash.txt" 2>&1 || true
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
adb -s "$android_serial" logcat -v threadtime SpatialSceneRenderTest:I TestRunner:I AndroidRuntime:E '*:S' \
  > "$report_dir/instrumentation-stage-logcat.txt" 2>&1 &
instrumentation_log_pid=$!
# am instrument can exit 0 even when tests fail; require the runner's success summary.
instrumentation_started=$(date -u +%FT%TZ)
instrumentation_start_seconds=$SECONDS
instrumentation_status=0
timeout --kill-after=10s "${instrumentation_timeout_seconds}s" \
  adb -s "$android_serial" shell am instrument -w -r dev.geode.debug.test/androidx.test.runner.AndroidJUnitRunner \
  2>&1 | tee "$report_dir/instrumentation.txt" || instrumentation_status=$?
{
  printf 'started=%s\nfinished=%s\n' "$instrumentation_started" "$(date -u +%FT%TZ)"
  printf 'elapsed_seconds=%s\n' "$((SECONDS - instrumentation_start_seconds))"
  printf 'timeout_seconds=%s\nexit_status=%s\n' "$instrumentation_timeout_seconds" "$instrumentation_status"
} > "$report_dir/instrumentation-timing.txt"
print_evidence_tail "instrumentation timing" "$report_dir/instrumentation-timing.txt" 6
if [ "$instrumentation_status" -eq 124 ] || [ "$instrumentation_status" -eq 137 ]; then
  echo "Instrumentation stopped (exit=$instrumentation_status, deadline=${instrumentation_timeout_seconds}s); capturing stage/process evidence" >&2
  capture_instrumentation_stall
  exit "$instrumentation_status"
fi
capture_instrumentation_evidence
if [ "$instrumentation_status" -ne 0 ] || ! grep -Eq 'OK \([1-9][0-9]* tests?\)' "$report_dir/instrumentation.txt"; then
  exit 1
fi
python3 tools/android/smoke_qa.py --serial "$android_serial" --output "$report_dir/smoke"
