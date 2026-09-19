#!/usr/bin/env bash
set -euxo pipefail
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
  for i in 1 2 3 4 5 6 7 8; do
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

assert_text_wait(){
  local txt="$1" out="$2"
  local i
  for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
    dump_ui "$out"
    if grep -q "$txt" "$E/$out.xml"; then
      return 0
    fi
    sleep 2
  done
  echo "ASSERT_TEXT_NOT_FOUND $txt"
  return 1
}

assert_text_scroll(){
  local txt="$1" out="$2"
  local i
  for i in 1 2 3 4 5 6 7 8; do
    dump_ui "$out"
    if grep -q "$txt" "$E/$out.xml"; then
      return 0
    fi
    adb shell input swipe 540 1500 540 600 350
    sleep 1
  done
  echo "ASSERT_TEXT_NOT_FOUND $txt"
  return 1
}

launch_app(){
  adb shell am force-stop "$P" >/dev/null 2>&1 || true
  adb shell am start -n "$P/.MainActivity" >/dev/null 2>&1 || true
  sleep 8
  PID="$(adb shell pidof "$P" | tr -d '\r' || true)"
  test -n "$PID"
  echo "$PID" > "$E/pid.txt"
}

adb uninstall "$P" >/dev/null 2>&1 || true
adb install "$APK" | tee "$E/install.txt"
adb logcat -c
launch_app

dump_ui home
grep -q 'لوحة المورد' "$E/home.xml"
grep -q 'المتجر متاح الآن' "$E/home.xml"

# Generate a local pending operation.
tap_text_scroll 'حالة المتجر'
dump_ui home-offline
grep -q 'المتجر متوقف مؤقتًا' "$E/home-offline.xml"

# Open live backend center.
tap_text_scroll 'الحساب'
tap_text_scroll 'مركز المزامنة'
assert_text_wait 'Backend ANDAK متصل' backend-online
grep -q 'المصادقة مطلوبة قبل إرسال أي عملية للمورد' "$E/backend-online.xml"
grep -q 'Scope: supplier:self' "$E/backend-online.xml"

# Attempt sync: live backend exists, but no supplier session, so operation must stay local.
tap_text_scroll 'محاولة المزامنة'
assert_text_scroll 'محجوبة — يلزم تسجيل دخول المورد' auth-blocked
assert_text_scroll 'Backend متصل، يلزم تسجيل دخول المورد' last-attempt

# Persist the auth-blocked queue across relaunch.
launch_app
tap_text_scroll 'الحساب'
tap_text_scroll 'مركز المزامنة'
assert_text_wait 'Backend ANDAK متصل' backend-online-after-relaunch
assert_text_scroll 'محجوبة — يلزم تسجيل دخول المورد' auth-blocked-persisted

adb logcat -d -b crash > "$E/crash.log" || true
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|ResourceResolutionException|Caused by|$P" > "$E/relevant.log" || true
if grep -q "$P" "$E/crash.log"; then
  cat "$E/crash.log"
  exit 1
fi

adb exec-out screencap -p > "$E/final-screen.png"
echo SIGNED_GATE7_RUNTIME_PASS | tee "$E/result.txt"
