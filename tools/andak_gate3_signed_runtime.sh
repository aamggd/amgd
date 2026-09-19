#!/usr/bin/env bash
set -euo pipefail
P=com.fush.market.supplier
E="$GITHUB_WORKSPACE/evidence"
mkdir -p "$E"

dump_ui(){ local n="$1"; adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || true; adb pull /sdcard/window.xml "$E/$n.xml" >/dev/null 2>&1 || true; }

tap_text(){
  local txt="$1"
  local i
  for i in 1 2 3 4 5 6; do
    dump_ui tap-source
    if python "$GITHUB_WORKSPACE/tools/andak_tap_text.py" "$E/tap-source.xml" "$txt"; then
      sleep 2
      return 0
    fi
    adb shell input swipe 540 1500 540 600 350
    sleep 1
  done
  echo "TEXT_NOT_FOUND $txt"
  return 1
}

assert_text(){
  local txt="$1" out="$2"
  local i
  for i in 1 2 3 4 5 6; do
    dump_ui "$out"
    if grep -q "$txt" "$E/$out.xml"; then return 0; fi
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
grep -q 'عندك للمورد' "$E/home.xml"

tap_text 'الطلبات'
dump_ui orders
grep -q 'تفاصيل الطلب والتجهيز والتسليم الآمن للمندوب' "$E/orders.xml"
grep -q 'خصوصية العميل محمية' "$E/orders.xml"

tap_text 'عرض تفاصيل الطلب'
dump_ui detail
grep -q 'تفاصيل الطلب #A-1048' "$E/detail.xml"
grep -q 'بيانات الاتصال والعنوان الكامل مخفية عن المورد' "$E/detail.xml"
assert_text 'قهوة عربية 250 جم' detail-lines
tap_text 'العودة إلى الطلبات'

tap_text 'قبول الطلب'
assert_text 'بدء التجهيز' accepted
tap_text 'بدء التجهيز'
assert_text 'تم التجهيز — جاهز للتسليم' preparing
tap_text 'تم التجهيز — جاهز للتسليم'
assert_text 'رمز استلام المندوب: 4821' ready
tap_text 'تأكيد التسليم للمندوب'
assert_text 'تم تسليم الطلب لمندوب عندك بنجاح' handed

adb shell pm clear "$P" >/dev/null
sleep 2
launch_app
tap_text 'الطلبات'
tap_text 'رفض'
dump_ui reject
grep -q 'رفض الطلب #A-1048' "$E/reject.xml"
grep -q 'نفاد أحد الأصناف' "$E/reject.xml"
tap_text 'تأكيد الرفض'
assert_text 'مرفوض' rejected
assert_text 'تم رفض الطلب من المورد' rejected-message

adb logcat -d -b crash > "$E/crash.log" || true
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|ResourceResolutionException|Caused by|$P" > "$E/relevant.log" || true
! grep -q "$P" "$E/crash.log"
adb exec-out screencap -p > "$E/final-screen.png"
echo SIGNED_GATE3_RUNTIME_PASS | tee "$E/result.txt"
