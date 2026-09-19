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
  for i in 1 2 3 4 5 6 7; do
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
  for i in 1 2 3 4 5 6 7; do
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

dump_ui home-online
grep -q 'لوحة المورد' "$E/home-online.xml"
grep -q 'المتجر متاح الآن' "$E/home-online.xml"

# Store availability switch + persistence across relaunch
tap_text_scroll 'حالة المتجر'
dump_ui home-offline
grep -q 'المتجر متوقف مؤقتًا' "$E/home-offline.xml"
launch_app
dump_ui persisted-offline
grep -q 'المتجر متوقف مؤقتًا' "$E/persisted-offline.xml"

# Account / local persistence information
tap_text_scroll 'الحساب'
dump_ui account-top
grep -q 'الحساب والمتجر' "$E/account-top.xml"
assert_text_scroll 'الحفظ المحلي' account-local
assert_text_scroll 'الاستمرارية دون إنترنت' account-offline

# Statement request must persist across relaunch
tap_text_scroll 'طلب كشف حساب'
assert_text_scroll 'تم طلب كشف الحساب' statement-requested
launch_app
tap_text_scroll 'الحساب'
assert_text_scroll 'تم طلب كشف الحساب' statement-persisted

# Settings page
tap_text_scroll 'إعدادات المتجر'
dump_ui settings
grep -q 'بيانات التشغيل المحفوظة على الجهاز' "$E/settings.xml"
grep -q 'حفظ إعدادات المتجر' "$E/settings.xml"
tap_text_scroll 'العودة إلى الحساب'

# Support page
tap_text_scroll 'المساعدة والدعم'
dump_ui support
grep -q 'إرسال طلب دعم من تطبيق المورد' "$E/support.xml"
grep -q 'إرسال طلب الدعم' "$E/support.xml"

adb logcat -d -b crash > "$E/crash.log" || true
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|ResourceResolutionException|Caused by|$P" > "$E/relevant.log" || true
if grep -q "$P" "$E/crash.log"; then
  cat "$E/crash.log"
  exit 1
fi

adb exec-out screencap -p > "$E/final-screen.png"
echo SIGNED_GATE5_RUNTIME_PASS | tee "$E/result.txt"
