#!/usr/bin/env bash
set -euo pipefail
mkdir -p app/build/reports/emulator
adb install -r dist/Geode-debug.apk
adb install -r dist/Geode-debug-androidTest.apk
# am instrument can exit 0 even when tests fail; require the runner's success summary.
adb shell am instrument -w -r dev.geode.debug.test/androidx.test.runner.AndroidJUnitRunner \
  | tee app/build/reports/emulator/instrumentation.txt
if ! grep -Eq 'OK \([1-9][0-9]* tests?\)' app/build/reports/emulator/instrumentation.txt; then
  adb logcat -b crash -d > app/build/reports/emulator/crash.txt
  exit 1
fi
python3 tools/android/smoke_qa.py --output app/build/reports/emulator/smoke
