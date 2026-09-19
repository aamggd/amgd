#!/usr/bin/env bash
set -euo pipefail

P=com.fush.market.supplier
E="$GITHUB_WORKSPACE/evidence"
mkdir -p "$E"

dump_ui(){
  local n="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/window.xml "$E/$n.xml" >/dev/null 2>&1 || true
}

tap_text_scroll(){
  local txt="$1"
  local i
  for i in 1 2 3 4 5 6 7 8 9 10; do
    dump_ui tap-source
    if python "$GITHUB_WORKSPACE/tools/andak_gate4_tap_text.py" "$E/tap-source.xml" "$txt"; then
      sleep 2
      return 0
    fi
    adb shell input swipe 540 1500 540 600 350
    sleep 1
  done
  echo "TEXT_NOT_FOUND $txt"
  return 1
}

assert_text_scroll(){
  local txt="$1" out="$2"
  local i
  for i in 1 2 3 4 5 6 7 8 9 10; do
    dump_ui "$out"
    if grep -q "$txt" "$E/$out.xml"; then return 0; fi
    adb shell input swipe 540 1500 540 600 350
    sleep 1
  done
  echo "ASSERT_TEXT_NOT_FOUND $txt"
  return 1
}

assert_text_wait(){
  local txt="$1" out="$2"
  local i
  for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
    dump_ui "$out"
    if grep -q "$txt" "$E/$out.xml"; then return 0; fi
    sleep 2
  done
  echo "ASSERT_TEXT_NOT_FOUND $txt"
  return 1
}

encode_adb_text(){
  python - "$1" <<'PY'
import sys
s=sys.argv[1]
print(s.replace('%','%25').replace(' ','%s').replace('@','%40'))
PY
}

adb uninstall "$P" >/dev/null 2>&1 || true
adb install "$APK" | tee "$E/install.txt"
adb logcat -c
adb shell am start -n "$P/.MainActivity" | tee "$E/start.txt" || true
sleep 10

PID="$(adb shell pidof "$P" | tr -d '\r' || true)"
echo "$PID" > "$E/pid.txt"
test -n "$PID"

dump_ui home
grep -q 'لوحة المورد' "$E/home.xml"
grep -q 'المتجر متاح الآن' "$E/home.xml"

# Create one pending local operation before authentication.
tap_text_scroll 'حالة المتجر'
dump_ui home-offline
grep -q 'المتجر متوقف مؤقتًا' "$E/home-offline.xml"

# Sign in with the disposable Gate10 supplier account.
tap_text_scroll 'الحساب'
tap_text_scroll 'تسجيل دخول المورد'
dump_ui login
grep -q 'Supabase Auth' "$E/login.xml"
grep -q 'Android Keystore' "$E/login.xml"

tap_text_scroll 'البريد الإلكتروني للمورد'
adb shell input text "$(encode_adb_text "$TEST_EMAIL")"
sleep 1
tap_text_scroll 'كلمة المرور'
adb shell input text "$(encode_adb_text "$TEST_PASSWORD")"
sleep 1
tap_text_scroll 'تسجيل الدخول'

assert_text_wait 'المورد مسجل الدخول' signed-in
assert_text_scroll "$SUPPLIER_CODE" supplier-code
assert_text_scroll 'Scope: supplier:self' supplier-scope

# Full authenticated Live Snapshot from ANDAK backend.
tap_text_scroll 'مركز المزامنة'
assert_text_wait 'Backend ANDAK متصل' backend-online
assert_text_wait 'بيانات الخادم متاحة' snapshot-ready
assert_text_scroll 'قهوة Gate 10 E2E' live-product
assert_text_scroll "$ORDER_REF" live-order
assert_text_scroll 'قبول طلب الخادم' live-order-action

# Authenticated local queue sync to the server.
tap_text_scroll 'محاولة المزامنة'
assert_text_wait 'تمت المزامنة' queue-synced
assert_text_scroll 'تمت مزامنة' sync-summary

# Server-side order transition through the signed Android app.
tap_text_scroll 'قبول طلب الخادم'
assert_text_wait 'تم تحديث الطلب' transition-message
assert_text_scroll 'بدء تجهيز طلب الخادم' transition-next-action

# Session must survive process restart through encrypted Android Keystore storage.
adb shell am force-stop "$P" >/dev/null 2>&1 || true
adb shell am start -n "$P/.MainActivity" >/dev/null 2>&1 || true
sleep 10
tap_text_scroll 'الحساب'
assert_text_wait 'المورد مسجل الدخول' session-restored
assert_text_scroll "$SUPPLIER_CODE" session-code

adb logcat -d -b crash > "$E/crash.log" || true
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|ResourceResolutionException|Caused by|$P" > "$E/relevant.log" || true
if grep -q "$P" "$E/crash.log"; then
  cat "$E/crash.log"
  exit 1
fi

adb exec-out screencap -p > "$E/final-screen.png"
echo SIGNED_GATE10_AUTH_E2E_PASS | tee "$E/result.txt"
