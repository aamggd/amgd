#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
python3 tools/verify_v213_commercial_multitenant.py
[[ -f gradle/wrapper/gradle-wrapper.jar ]] || { echo 'FAIL: gradle/wrapper/gradle-wrapper.jar is missing'; exit 20; }
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[[ -n "$SDK" && -d "$SDK" ]] || { echo 'FAIL: set ANDROID_SDK_ROOT or ANDROID_HOME to Android SDK'; exit 21; }
./gradlew --version
./gradlew clean test :app:lintVitalRelease :app:assembleRelease
SCHEMA="app/schemas/com.fush.erp.data.FushDatabase/54.json"
[[ -f "$SCHEMA" ]] || { echo "FAIL: Room compiler did not export $SCHEMA"; exit 22; }
if command -v adb >/dev/null 2>&1 && adb devices | awk 'NR>1 && $2=="device" {found=1} END{exit !found}'; then
  ./gradlew :app:connectedDebugAndroidTest
else
  echo 'DEVICE GATE PENDING: no adb device/emulator; connectedDebugAndroidTest not run.'
  exit 23
fi
echo 'V213 ANDROID COMMERCIAL BUILD GATE PASS'
