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

# Create one real local change so Sync Queue is exercised.
tap_text_scroll 'حالة المتجر'
dump_ui home-offline
grep -q 'المتجر متوقف مؤقتًا' "$E/home-offline.xml"

# Open account and sync center.
tap_text_scroll 'الحساب'
assert_text_scroll 'مركز المزامنة' account-sync-button
tap_text_scroll 'مركز المزامنة'
dump_ui sync-center
grep -q 'Offline-first' "$E/sync-center.xml"
grep -q 'الخادم المركزي غير مهيأ' "$E/sync-center.xml"
grep -q 'supplier:self' "$E/sync-center.xml"
grep -q 'بيانات اتصال العميل والعنوان الكامل غير مشمولة' "$E/sync-center.xml"
assert_text_scroll 'حالة المتجر' sync-queue-item

# Attempt sync: item must remain safe locally and become blocked, not falsely synced.
tap_text_scroll 'محاولة المزامنة'
assert_text_scroll 'محجوبة — الخادم غير مهيأ' sync-blocked
assert_text_scroll 'الآن — الخادم المركزي غير مهيأ' sync-last-attempt

# Relaunch and verify Sync Queue persistence.
launch_app
tap_text_scroll 'الحساب'
tap_text_scroll 'مركز المزامنة'
assert_text_scroll 'محجوبة — الخادم غير مهيأ' sync-persisted

# Relaunch to avoid depending on a scrolled return button.
launch_app
tap_text_scroll 'الحساب'

# Notification center: 3 seeded + sync warning = 4 unread.
tap_text_scroll 'الإشعارات (4)'
dump_ui notifications
grep -q 'تنبيهات الطلبات والمنتجات والتسويات والمزامنة' "$E/notifications.xml"
grep -q 'تعذر إرسال المزامنة' "$E/notifications.xml"
tap_text_scroll 'تحديد الكل كمقروء'

# Read-state persistence.
launch_app
tap_text_scroll 'الحساب'
assert_text_scroll 'الإشعارات (0)' notifications-read-persisted

adb logcat -d -b crash > "$E/crash.log" || true
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|ResourceResolutionException|Caused by|$P" > "$E/relevant.log" || true
if grep -q "$P" "$E/crash.log"; then
  cat "$E/crash.log"
  exit 1
fi

adb exec-out screencap -p > "$E/final-screen.png"
echo SIGNED_GATE6_RUNTIME_PASS | tee "$E/result.txt"
