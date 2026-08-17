#!/usr/bin/env bash
set -euo pipefail

wait_for_android_service() {
  local service_name="$1"
  echo "Waiting for Android service: ${service_name}"
  for i in $(seq 1 180); do
    if adb shell service check "$service_name" 2>/dev/null | grep -q 'found'; then
      echo "Service ${service_name} is ready"
      return 0
    fi
    sleep 2
  done
  echo "Android service ${service_name} did not become ready" >&2
  adb shell service list || true
  return 1
}

adb wait-for-device
for i in $(seq 1 180); do
  BOOTED="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  [ "$BOOTED" = '1' ] && break
  sleep 2
done
test "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = '1'

# The previous QA harness observed sys.boot_completed before PackageManager/ActivityManager
# were usable on the non-KVM runner. Wait for the actual Android framework services.
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

CANDIDATE="$GITHUB_WORKSPACE/exact-central-candidate/${CENTRAL_CANDIDATE_APK}"
SIGNED="$GITHUB_WORKSPACE/evidence/FushERP-Central-v102-QA-test-signed.apk"
TEST_APK="$GITHUB_WORKSPACE/work-source/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
test -s "$CANDIDATE"
test -s "$TEST_APK"
echo "${CENTRAL_CANDIDATE_SHA256}  ${CANDIDATE}" | sha256sum -c -

"$ANDROID_HOME/build-tools/36.0.0/apksigner" sign \
  --ks "$HOME/.android/debug.keystore" \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$SIGNED" "$CANDIDATE"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs "$SIGNED" \
  | tee "$GITHUB_WORKSPACE/evidence/test-signed-candidate-cert.txt"
sha256sum "$SIGNED" | tee "$GITHUB_WORKSPACE/evidence/test-signed-candidate.sha256"

adb uninstall com.fush.erp.recovery.test >/dev/null 2>&1 || true
adb uninstall "$APP_ID" >/dev/null 2>&1 || true
adb install -r "$SIGNED" | tee "$GITHUB_WORKSPACE/evidence/apk-install.txt"
adb install -r "$TEST_APK" | tee "$GITHUB_WORKSPACE/evidence/test-apk-install.txt"

adb logcat -c
LAUNCHER="$("$ANDROID_HOME/build-tools/36.0.0/aapt" dump badging "$SIGNED" \
  | sed -n "s/launchable-activity: name='\([^']*\)'.*/\1/p" | head -n 1)"
test -n "$LAUNCHER"
adb shell am start -W -n "${APP_ID}/${LAUNCHER}" \
  | tee "$GITHUB_WORKSPACE/evidence/apk-launch.txt"
sleep 8
adb shell pidof "$APP_ID" | tee "$GITHUB_WORKSPACE/evidence/apk-pid.txt"
adb shell dumpsys package "$APP_ID" \
  | grep -E 'versionCode=|versionName=' | head -5 \
  | tee "$GITHUB_WORKSPACE/evidence/apk-version.txt"

# Instrument the verified merged Central candidate itself with QA-only tests.
adb shell pm list instrumentation | tee "$GITHUB_WORKSPACE/evidence/instrumentation-list.txt"
set +e
adb shell am instrument -w -r \
  com.fush.erp.recovery.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "$GITHUB_WORKSPACE/evidence/release-candidate-instrumentation.txt"
TEST_RC=${PIPESTATUS[0]}
set -e
test "$TEST_RC" -eq 0
! grep -Fq 'FAILURES!!!' "$GITHUB_WORKSPACE/evidence/release-candidate-instrumentation.txt"
! grep -Fq 'INSTRUMENTATION_FAILED' "$GITHUB_WORKSPACE/evidence/release-candidate-instrumentation.txt"
grep -Eq 'OK \([1-9][0-9]* tests?\)|INSTRUMENTATION_CODE: -1' \
  "$GITHUB_WORKSPACE/evidence/release-candidate-instrumentation.txt"

adb logcat -d -v brief > "$GITHUB_WORKSPACE/evidence/final-logcat.txt"
if grep -E 'FATAL EXCEPTION:.*|Process: com\.fush\.erp\.recovery,' \
  "$GITHUB_WORKSPACE/evidence/final-logcat.txt"; then
  echo 'Fatal crash detected in merged Central APK session' >&2
  exit 1
fi
