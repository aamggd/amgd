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

assert_text_scroll(){
  local txt="$1" out="$2"
  local i
  for i in 1 2 3 4 5 6 7 8; do
    dump_ui "$out"
    if grep -q "$txt" "$E/$out.xml"; then return 0; fi
    adb shell input swipe 540 1500 540 600 350
    sleep 1
  done
  echo "ASSERT_TEXT_NOT_FOUND $txt"
  return 1
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
grep -q 'Gate 8' "$E/home.xml"

# Backend must still be live.
tap_text_scroll 'الحساب'
assert_text_scroll 'تسجيل الدخول مطلوب للمزامنة' auth-card
tap_text_scroll 'تسجيل دخول المورد'
dump_ui login
grep -q 'Supabase Auth' "$E/login.xml"
grep -q 'Android Keystore' "$E/login.xml"

# Exercise real Supabase Auth rejection safely with non-existent credentials.
tap_text_scroll 'البريد الإلكتروني للمورد'
adb shell input text 'nobody-g8%40example.com'
sleep 1
tap_text_scroll 'كلمة المرور'
adb shell input text 'NotARealPassword12345'
sleep 1
tap_text_scroll 'تسجيل الدخول'
sleep 6
dump_ui login-rejected
grep -q 'تسجيل الدخول' "$E/login-rejected.xml"
if grep -q 'المورد مسجل الدخول' "$E/login-rejected.xml"; then
  echo 'FATAL: invalid credentials unexpectedly authenticated'
  exit 1
fi

# Return and verify unauthenticated queue remains safely blocked.
tap_text_scroll 'العودة إلى الحساب'
tap_text_scroll 'حالة المتجر'
tap_text_scroll 'الحساب'
tap_text_scroll 'مركز المزامنة'
assert_text_scroll 'Backend ANDAK متصل' backend-online
tap_text_scroll 'محاولة المزامنة'
assert_text_scroll 'محجوبة — يلزم تسجيل دخول المورد' auth-blocked

adb logcat -d -b crash > "$E/crash.log" || true
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|ResourceResolutionException|Caused by|$P" > "$E/relevant.log" || true
if grep -q "$P" "$E/crash.log"; then
  cat "$E/crash.log"
  exit 1
fi
adb exec-out screencap -p > "$E/final-screen.png"
echo SIGNED_GATE8_RUNTIME_PASS | tee "$E/result.txt"
