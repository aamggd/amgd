#!/usr/bin/env bash
set -euo pipefail

wait_for_android_service() {
  local service_name="$1"
  local service_output
  echo "Waiting for Android service: ${service_name}"
  for i in $(seq 1 180); do
    service_output="$(adb shell service check "$service_name" 2>/dev/null || true)"
    if grep -F 'found' <<<"$service_output" >/dev/null; then
      echo "Service ${service_name} is ready"
      return 0
    fi
    sleep 2
  done
  echo "Android service ${service_name} did not become ready" >&2
  adb shell service list || true
  return 1
}

extract_first_fatal() {
  local log_file="$1"
  local out_file="$2"
  python3 - "$log_file" "$out_file" <<'PY'
from pathlib import Path
import sys

src = Path(sys.argv[1])
out = Path(sys.argv[2])
lines = src.read_text(encoding='utf-8', errors='replace').splitlines()
start = next((i for i, line in enumerate(lines) if 'FATAL EXCEPTION:' in line), None)
if start is None:
    out.write_text('NO_FATAL_EXCEPTION_CAPTURED\n', encoding='utf-8')
    raise SystemExit(0)

picked = []
for line in lines[start:]:
    if picked and ' E AndroidRuntime:' not in line and ' AndroidRuntime:' not in line:
        break
    picked.append(line)
out.write_text('\n'.join(picked) + '\n', encoding='utf-8')
PY
}

capture_runtime_diagnostics() {
  local prefix="$1"
  local log_file="$GITHUB_WORKSPACE/evidence/${prefix}-logcat.txt"
  adb logcat -d -v threadtime > "$log_file" 2>&1 || true
  adb shell dumpsys activity processes > "$GITHUB_WORKSPACE/evidence/${prefix}-activity-processes.txt" 2>&1 || true
  adb shell dumpsys package "$APP_ID" > "$GITHUB_WORKSPACE/evidence/${prefix}-target-package.txt" 2>&1 || true
  adb shell dumpsys package com.fush.erp.recovery.test > "$GITHUB_WORKSPACE/evidence/${prefix}-test-package.txt" 2>&1 || true
  extract_first_fatal "$log_file" "$GITHUB_WORKSPACE/evidence/${prefix}-first-fatal.txt" || true
}

ensure_android_test_runner() {
  local build_file="$GITHUB_WORKSPACE/work-source/app/build.gradle.kts"
  local runner_coordinate='androidx.test:runner:1.7.0'

  # The diagnostic evidence showed the instrumentation component was declared but
  # AndroidJUnitRunner itself was absent from the QA test APK. Patch only the disposable
  # QA work copy, rebuild androidTest, and prove the runner bytecode is packaged.
  if ! grep -F "$runner_coordinate" "$build_file" >/dev/null; then
    python3 - <<'PY'
from pathlib import Path
p = Path('app/build.gradle.kts')
text = p.read_text(encoding='utf-8')
anchor = '    androidTestImplementation("androidx.test:core-ktx:1.7.0")\n'
line = '    androidTestImplementation("androidx.test:runner:1.7.0")\n'
if line not in text:
    if anchor not in text:
        raise SystemExit('QA androidTest dependency anchor not found')
    p.write_text(text.replace(anchor, anchor + line), encoding='utf-8')
PY
  fi

  grep -Fn "$runner_coordinate" "$build_file" \
    | tee "$GITHUB_WORKSPACE/evidence/android-test-runner-dependency.txt"
  gradle --no-daemon :app:assembleDebugAndroidTest

  local test_apk="$GITHUB_WORKSPACE/work-source/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
  test -s "$test_apk"
  python3 - "$test_apk" "$GITHUB_WORKSPACE/evidence/android-test-runner-class.txt" <<'PY'
from pathlib import Path
from zipfile import ZipFile
import sys

apk = Path(sys.argv[1])
out = Path(sys.argv[2])
needle = b'androidx/test/runner/AndroidJUnitRunner'
hits = []
with ZipFile(apk) as zf:
    for name in zf.namelist():
        if name.startswith('classes') and name.endswith('.dex'):
            if needle in zf.read(name):
                hits.append(name)
if not hits:
    raise SystemExit('AndroidJUnitRunner bytecode is not packaged in QA instrumentation APK')
out.write_text('AndroidJUnitRunner packaged in: ' + ', '.join(hits) + '\n', encoding='utf-8')
print(out.read_text(encoding='utf-8'), end='')
PY
}

run_instrumentation_class() {
  local class_name="$1"
  local evidence_name="$2"
  local output="$GITHUB_WORKSPACE/evidence/${evidence_name}-instrumentation.txt"
  local requested="$GITHUB_WORKSPACE/evidence/${evidence_name}-requested-test.txt"

  # Run each final instrumentation gate independently. Do not pipe am instrument through
  # an early-closing consumer: always save its exit code and capture logcat/dumpsys first.
  adb logcat -c || true
  printf 'requested_class=%s\n' "$class_name" > "$requested"
  set +e
  adb shell am instrument -w -r \
    -e class "$class_name" \
    com.fush.erp.recovery.test/androidx.test.runner.AndroidJUnitRunner \
    > "$output" 2>&1
  local test_rc=$?
  set -e
  cat "$output"
  grep -E '^INSTRUMENTATION_STATUS: (class|test)=' "$output" >> "$requested" || true
  capture_runtime_diagnostics "$evidence_name"
  printf 'am_instrument_exit_code=%s\n' "$test_rc" >> "$requested"

  test "$test_rc" -eq 0
  ! grep -F 'FAILURES!!!' "$output" >/dev/null
  ! grep -F 'INSTRUMENTATION_FAILED' "$output" >/dev/null
  ! grep -F 'shortMsg=Process crashed' "$output" >/dev/null
  grep -E 'OK \([1-9][0-9]* tests?\)|INSTRUMENTATION_CODE: -1' "$output" >/dev/null
}

adb wait-for-device
for i in $(seq 1 180); do
  BOOTED="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  [ "$BOOTED" = '1' ] && break
  sleep 2
done
test "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = '1'

# sys.boot_completed may become true before PackageManager/ActivityManager are usable.
wait_for_android_service package
wait_for_android_service activity
for i in $(seq 1 120); do
  if adb shell cmd package list packages >/dev/null 2>&1 \
     && adb shell am get-current-user >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
adb shell cmd package list packages >/dev/null
adb shell am get-current-user >/dev/null
sleep 10

cd "$GITHUB_WORKSPACE/work-source"
ensure_android_test_runner

CANDIDATE="$GITHUB_WORKSPACE/exact-central-candidate/${CENTRAL_CANDIDATE_APK}"
SIGNED="$GITHUB_WORKSPACE/evidence/FushERP-Central-v102-QA-test-signed.apk"
TEST_APK="$GITHUB_WORKSPACE/work-source/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
SIGNED_TEST="$GITHUB_WORKSPACE/evidence/FushERP-Central-v102-QA-test-instrumentation-signed.apk"
APKSIGNER="$ANDROID_HOME/build-tools/36.0.0/apksigner"
AAPT="$ANDROID_HOME/build-tools/36.0.0/aapt"
test -s "$CANDIDATE"
test -s "$TEST_APK"
echo "${CENTRAL_CANDIDATE_SHA256}  ${CANDIDATE}" | sha256sum -c -

# Sign both QA-only install artifacts with the same deterministic QA signer.
"$APKSIGNER" sign \
  --ks "$HOME/.android/debug.keystore" \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$SIGNED" "$CANDIDATE"
"$APKSIGNER" sign \
  --ks "$HOME/.android/debug.keystore" \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$SIGNED_TEST" "$TEST_APK"

"$APKSIGNER" verify --verbose --print-certs "$SIGNED" \
  | tee "$GITHUB_WORKSPACE/evidence/test-signed-candidate-cert.txt"
"$APKSIGNER" verify --verbose --print-certs "$SIGNED_TEST" \
  | tee "$GITHUB_WORKSPACE/evidence/test-signed-instrumentation-cert.txt"
sha256sum "$SIGNED" | tee "$GITHUB_WORKSPACE/evidence/test-signed-candidate.sha256"
sha256sum "$SIGNED_TEST" | tee "$GITHUB_WORKSPACE/evidence/test-signed-instrumentation.sha256"

"$APKSIGNER" verify --print-certs "$SIGNED" \
  > "$GITHUB_WORKSPACE/evidence/test-signed-candidate-cert-raw.txt"
"$APKSIGNER" verify --print-certs "$SIGNED_TEST" \
  > "$GITHUB_WORKSPACE/evidence/test-signed-instrumentation-cert-raw.txt"
TARGET_CERT="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' \
  "$GITHUB_WORKSPACE/evidence/test-signed-candidate-cert-raw.txt" | sed -n '1p')"
TEST_CERT="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' \
  "$GITHUB_WORKSPACE/evidence/test-signed-instrumentation-cert-raw.txt" | sed -n '1p')"
test -n "$TARGET_CERT"
test -n "$TEST_CERT"
printf 'target=%s\ninstrumentation=%s\n' "$TARGET_CERT" "$TEST_CERT" \
  | tee "$GITHUB_WORKSPACE/evidence/instrumentation-signer-match.txt"
test "$TARGET_CERT" = "$TEST_CERT"

adb uninstall com.fush.erp.recovery.test >/dev/null 2>&1 || true
adb uninstall "$APP_ID" >/dev/null 2>&1 || true
adb install -r "$SIGNED" | tee "$GITHUB_WORKSPACE/evidence/apk-install.txt"
adb install -r "$SIGNED_TEST" | tee "$GITHUB_WORKSPACE/evidence/test-apk-install.txt"

adb logcat -c
"$AAPT" dump badging "$SIGNED" > "$GITHUB_WORKSPACE/evidence/apk-badging.txt"
LAUNCHER="$(sed -n "s/launchable-activity: name='\([^']*\)'.*/\1/p" \
  "$GITHUB_WORKSPACE/evidence/apk-badging.txt" | sed -n '1p')"
test -n "$LAUNCHER"
adb shell am start -W -n "${APP_ID}/${LAUNCHER}" \
  | tee "$GITHUB_WORKSPACE/evidence/apk-launch.txt"
sleep 8
adb shell pidof "$APP_ID" | tee "$GITHUB_WORKSPACE/evidence/apk-pid.txt"
adb shell dumpsys package "$APP_ID" > "$GITHUB_WORKSPACE/evidence/apk-package-dumpsys.txt"
grep -E 'versionCode=|versionName=' "$GITHUB_WORKSPACE/evidence/apk-package-dumpsys.txt" \
  | sed -n '1,5p' \
  | tee "$GITHUB_WORKSPACE/evidence/apk-version.txt"
capture_runtime_diagnostics 'fresh-room35-launch'

adb shell pm list instrumentation | tee "$GITHUB_WORKSPACE/evidence/instrumentation-list.txt"

# Gate A: exact candidate fresh runtime + Wave1 Sales/Purchases/Treasury identity contracts.
run_instrumentation_class \
  'com.fush.erp.qa.Wave1P1ReleaseCandidateTest' \
  'release-candidate'

# Gate B: historical Room 34 -> 35 preservation and accounting guard behavior.
run_instrumentation_class \
  'com.fush.erp.data.AccountingP1Migration34To35Test' \
  'migration-34-35'

# Final merged logcat evidence after both successful instrumentations.
adb logcat -d -v brief > "$GITHUB_WORKSPACE/evidence/final-logcat.txt" 2>&1 || true
if grep -E 'FATAL EXCEPTION:.*|Process: com\.fush\.erp\.recovery,' \
  "$GITHUB_WORKSPACE/evidence/final-logcat.txt"; then
  echo 'Fatal crash detected in merged Central APK session' >&2
  exit 1
fi
