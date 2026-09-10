#!/usr/bin/env bash
set -euo pipefail

EVIDENCE_DIR="${GITHUB_WORKSPACE}/evidence"
BOOT_TIMEOUT_SECONDS="${BOOT_TIMEOUT_SECONDS:-420}"
INSTRUMENT_TIMEOUT_SECONDS="${INSTRUMENT_TIMEOUT_SECONDS:-300}"
mkdir -p "$EVIDENCE_DIR"

capture_diagnostics() {
  local prefix="$1"
  adb logcat -d -v threadtime > "$EVIDENCE_DIR/${prefix}-logcat.txt" 2>&1 || true
  adb shell dumpsys activity processes > "$EVIDENCE_DIR/${prefix}-activity-processes.txt" 2>&1 || true
  adb shell dumpsys activity instrumentation > "$EVIDENCE_DIR/${prefix}-activity-instrumentation.txt" 2>&1 || true
  adb shell dumpsys package "$APP_ID" > "$EVIDENCE_DIR/${prefix}-target-package.txt" 2>&1 || true
  adb shell dumpsys package com.fush.erp.recovery.test > "$EVIDENCE_DIR/${prefix}-test-package.txt" 2>&1 || true
}

fail_if_pattern() {
  local pattern="$1"
  local file="$2"
  local label="$3"
  if grep -Fq "$pattern" "$file"; then
    echo "FAIL: ${label}: found '${pattern}' in ${file}" >&2
    return 1
  fi
}

require_pattern() {
  local pattern="$1"
  local file="$2"
  local label="$3"
  if grep -Fq "$pattern" "$file"; then
    return 0
  else
    echo "FAIL: ${label}: missing '${pattern}' in ${file}" >&2
    return 1
  fi
}

validate_instrumentation_output() {
  local output="$1"
  local expected_count="$2"
  local expected_methods_csv="$3"

  fail_if_pattern 'FAILURES!!!' "$output" 'JUnit reported failures'
  fail_if_pattern 'INSTRUMENTATION_FAILED' "$output" 'instrumentation framework failure'
  fail_if_pattern 'shortMsg=Process crashed' "$output" 'instrumentation process crash'
  require_pattern "OK (${expected_count} tests)" "$output" 'exact JUnit summary'
  require_pattern 'INSTRUMENTATION_CODE: -1' "$output" 'successful terminal instrumentation code'

  python3 - "$output" "$expected_count" "$expected_methods_csv" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
expected = int(sys.argv[2])
expected_methods = [x for x in sys.argv[3].split(',') if x]
lines = path.read_text(encoding='utf-8', errors='replace').splitlines()

numtests = []
methods = []
finished = 0
for line in lines:
    if line.startswith('INSTRUMENTATION_STATUS: numtests='):
        try:
            numtests.append(int(line.rsplit('=', 1)[1]))
        except ValueError:
            raise SystemExit(f'Invalid numtests line: {line}')
    elif line.startswith('INSTRUMENTATION_STATUS: test='):
        methods.append(line.split('=', 1)[1])
    elif line.strip() == 'INSTRUMENTATION_STATUS_CODE: 0':
        finished += 1

if not numtests:
    raise SystemExit('No INSTRUMENTATION_STATUS numtests values were emitted')
if any(value != expected for value in numtests):
    raise SystemExit(f'Expected numtests={expected}, observed {numtests}')
if finished != expected:
    raise SystemExit(f'Expected {expected} completed tests (STATUS_CODE 0), observed {finished}')

unique_methods = []
for method in methods:
    if method not in unique_methods:
        unique_methods.append(method)
if len(unique_methods) != expected:
    raise SystemExit(f'Expected {expected} unique test methods, observed {unique_methods}')
if expected_methods and set(unique_methods) != set(expected_methods):
    raise SystemExit(f'Expected methods {expected_methods}, observed {unique_methods}')

print(f'QUALIFIED_TEST_COUNT={expected}')
print('QUALIFIED_METHODS=' + ','.join(unique_methods))
PY
}

run_instrumentation_class() {
  local class_name="$1"
  local expected_count="$2"
  local evidence_name="$3"
  local expected_methods_csv="$4"
  local output="$EVIDENCE_DIR/${evidence_name}-junit.txt"
  local rc_file="$EVIDENCE_DIR/${evidence_name}-am-instrument-rc.txt"

  adb logcat -c || true
  set +e
  timeout --signal=TERM --kill-after=15s "${INSTRUMENT_TIMEOUT_SECONDS}s" \
    adb shell am instrument -w -r \
      -e class "$class_name" \
      com.fush.erp.recovery.test/androidx.test.runner.AndroidJUnitRunner \
      > "$output" 2>&1
  local rc=$?
  set -e

  printf '%s\n' "$rc" > "$rc_file"
  cat "$output"
  capture_diagnostics "$evidence_name"

  if [ "$rc" -eq 124 ] || [ "$rc" -eq 137 ]; then
    cat > "$EVIDENCE_DIR/${evidence_name}-timeout-status.txt" <<EOF
STATUS=QA HARNESS TIMEOUT / AM INSTRUMENT
TEST_CLASS=${class_name}
TIMEOUT_SECONDS=${INSTRUMENT_TIMEOUT_SECONDS}
EXIT_CODE=${rc}
EOF
    cat "$EVIDENCE_DIR/${evidence_name}-timeout-status.txt" >&2
    return "$rc"
  fi

  if [ "$rc" -ne 0 ]; then
    echo "FAIL: am instrument returned non-zero exit code ${rc} for ${class_name}" >&2
    return "$rc"
  fi

  validate_instrumentation_output "$output" "$expected_count" "$expected_methods_csv"
}

wait_for_android() {
  local deadline=$((SECONDS + BOOT_TIMEOUT_SECONDS))
  local booted=''

  while [ "$SECONDS" -lt "$deadline" ]; do
    if adb get-state >/dev/null 2>&1; then
      booted="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
      if [ "$booted" = '1' ]; then
        break
      fi
    fi
    sleep 2
  done

  if [ "$booted" != '1' ]; then
    capture_diagnostics 'emulator-boot-timeout'
    cat > "$EVIDENCE_DIR/emulator-boot-timeout-status.txt" <<EOF
STATUS=QA HARNESS TIMEOUT / EMULATOR BOOT
TIMEOUT_SECONDS=${BOOT_TIMEOUT_SECONDS}
EOF
    cat "$EVIDENCE_DIR/emulator-boot-timeout-status.txt" >&2
    return 124
  fi

  while [ "$SECONDS" -lt "$deadline" ]; do
    local package_service
    local activity_service
    package_service="$(adb shell service check package 2>/dev/null || true)"
    activity_service="$(adb shell service check activity 2>/dev/null || true)"
    if grep -Fq 'found' <<<"$package_service" && grep -Fq 'found' <<<"$activity_service"; then
      printf 'BOOT_QUALIFIED_SECONDS=%s\n' "$SECONDS" | tee "$EVIDENCE_DIR/emulator-boot-qualified.txt"
      return 0
    fi
    sleep 2
  done

  capture_diagnostics 'emulator-service-timeout'
  cat > "$EVIDENCE_DIR/emulator-service-timeout-status.txt" <<EOF
STATUS=QA HARNESS TIMEOUT / ANDROID SERVICES
TIMEOUT_SECONDS=${BOOT_TIMEOUT_SECONDS}
EOF
  cat "$EVIDENCE_DIR/emulator-service-timeout-status.txt" >&2
  return 124
}

wait_for_android

cd "$GITHUB_WORKSPACE/work-source"

gradle --no-daemon :app:assembleDebugAndroidTest

gradle --no-daemon :app:dependencyInsight \
  --configuration debugAndroidTestRuntimeClasspath \
  --dependency kotlinx-serialization-core \
  | tee "$EVIDENCE_DIR/dependency-kotlinx-serialization-core.txt"
gradle --no-daemon :app:dependencyInsight \
  --configuration debugAndroidTestRuntimeClasspath \
  --dependency kotlinx-serialization-json \
  | tee "$EVIDENCE_DIR/dependency-kotlinx-serialization-json.txt"

require_pattern 'org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3' \
  "$EVIDENCE_DIR/dependency-kotlinx-serialization-core.txt" \
  'debugAndroidTestRuntimeClasspath serialization core pin'
require_pattern 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3' \
  "$EVIDENCE_DIR/dependency-kotlinx-serialization-json.txt" \
  'debugAndroidTestRuntimeClasspath serialization json pin'

CANDIDATE="$GITHUB_WORKSPACE/exact-central-candidate/${CENTRAL_CANDIDATE_APK}"
TEST_APK="$GITHUB_WORKSPACE/work-source/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
SIGNED_TARGET="$EVIDENCE_DIR/FushERP-qualification-target-signed.apk"
SIGNED_TEST="$EVIDENCE_DIR/FushERP-qualification-test-signed.apk"
APKSIGNER="$ANDROID_HOME/build-tools/36.0.0/apksigner"

test -s "$CANDIDATE"
test -s "$TEST_APK"
echo "${CENTRAL_CANDIDATE_SHA256}  ${CANDIDATE}" | sha256sum -c -

"$APKSIGNER" sign \
  --ks "$HOME/.android/debug.keystore" \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$SIGNED_TARGET" "$CANDIDATE"
"$APKSIGNER" sign \
  --ks "$HOME/.android/debug.keystore" \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$SIGNED_TEST" "$TEST_APK"

"$APKSIGNER" verify --print-certs "$SIGNED_TARGET" > "$EVIDENCE_DIR/target-cert.txt"
"$APKSIGNER" verify --print-certs "$SIGNED_TEST" > "$EVIDENCE_DIR/test-cert.txt"
TARGET_CERT="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$EVIDENCE_DIR/target-cert.txt" | sed -n '1p')"
TEST_CERT="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$EVIDENCE_DIR/test-cert.txt" | sed -n '1p')"
if [ -z "$TARGET_CERT" ] || [ -z "$TEST_CERT" ] || [ "$TARGET_CERT" != "$TEST_CERT" ]; then
  echo 'FAIL: target and instrumentation APK signatures do not match' >&2
  exit 1
fi

adb uninstall com.fush.erp.recovery.test >/dev/null 2>&1 || true
adb uninstall "$APP_ID" >/dev/null 2>&1 || true
adb install -r "$SIGNED_TARGET" | tee "$EVIDENCE_DIR/target-install.txt"
adb install -r "$SIGNED_TEST" | tee "$EVIDENCE_DIR/test-install.txt"
adb shell pm list instrumentation | tee "$EVIDENCE_DIR/instrumentation-list.txt"

run_instrumentation_class \
  'com.fush.erp.qa.Wave1P1ReleaseCandidateTest' \
  4 \
  'release-candidate' \
  'exactTargetPackage_isRecoveryApplication_andHasLaunchableActivity,saleCollectionAndReturn_keepCustomerIdentityTreasuryPartyAndAccountingEventIdentityAligned,purchasePaymentAndReturn_keepSupplierIdentityTreasuryPartyAndAccountingEventIdentityAligned,generalTreasuryAccount_rejectsOrphanPartyLinkage'

run_instrumentation_class \
  'com.fush.erp.data.AccountingP1Migration34To35Test' \
  2 \
  'migration-34-35' \
  'migrate34To35_preservesHistoricalPostedRows_andEnforcesWave1JournalGuards,migrate32To35_validatesCompleteSecurityFixedAssetAccountingChain_withoutDestructiveReset'

MIGRATION_LOG="$EVIDENCE_DIR/migration-34-35-logcat.txt"
for marker in \
  'BODY_START:migrate34To35_preservesHistoricalPostedRows_andEnforcesWave1JournalGuards' \
  'MIGRATION_EXECUTED:34->35' \
  'BODY_PASS:migrate34To35_preservesHistoricalPostedRows_andEnforcesWave1JournalGuards' \
  'BODY_START:migrate32To35_validatesCompleteSecurityFixedAssetAccountingChain_withoutDestructiveReset' \
  'MIGRATION_CHAIN_EXECUTED:32->35' \
  'BODY_PASS:migrate32To35_validatesCompleteSecurityFixedAssetAccountingChain_withoutDestructiveReset'; do
  require_pattern "$marker" "$MIGRATION_LOG" 'migration test body evidence marker'
done

cat > "$EVIDENCE_DIR/LIGHTWEIGHT-QUALIFICATION-STATUS.txt" <<EOF
STATUS=QA HARNESS LIGHTWEIGHT QUALIFICATION — PASS
Exact Central HEAD=${CENTRAL_SHA}
Exact Central source tree=${CENTRAL_SOURCE_TREE}
Room schema=${ROOM_SCHEMA}
Application ID=${APP_ID}
Wave1P1ReleaseCandidateTest=4/4 PASS
AccountingP1Migration34To35Test=2/2 PASS
Migration test body execution markers=PASS
am instrument non-zero enforcement=ACTIVE
FAILURES!!! rejection=ACTIVE
Expected test-count validation=ACTIVE
Emulator boot timeout=${BOOT_TIMEOUT_SECONDS}s
Per-instrument timeout=${INSTRUMENT_TIMEOUT_SECONDS}s
kotlinx-serialization test runtime=1.7.3
Business Logic changes by QA=NONE
Full Final QA=NOT RUN
EOF
cat "$EVIDENCE_DIR/LIGHTWEIGHT-QUALIFICATION-STATUS.txt"
