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
tap_text_scroll 'الحساب'
dump_ui account
grep -q 'الحساب والمتجر' "$E/account.xml"
grep -q 'الإعدادات والمستحقات والتسويات المالية' "$E/account.xml"

assert_text_scroll 'المستحقات الحالية' financial-current
assert_text_scroll 'صافي المستحق' financial-net
assert_text_scroll '131,260 ر.ي' financial-net-value
assert_text_scroll 'التسوية القادمة: 21 سبتمبر 2026' financial-due
assert_text_scroll 'سجل التسويات' settlements
assert_text_scroll '13–19 سبتمبر 2026' current-settlement
assert_text_scroll 'جاهزة للتسوية' settlement-status

tap_text_scroll 'عرض التفاصيل'
assert_text_scroll 'تفاصيل التسوية STL-2026-38' settlement-detail
assert_text_scroll 'عمولة عندك' commission-detail
assert_text_scroll 'آخر الحركات المالية' ledger-title
assert_text_scroll 'طلب تم تسليمه للمندوب' ledger-order
assert_text_scroll 'عمولة منصة عندك' ledger-commission
assert_text_scroll 'مرتجع معتمد' ledger-return
assert_text_scroll 'طريقة استلام المستحقات' payout-method
assert_text_scroll 'طلب كشف حساب' statement-button

adb logcat -d -b crash > "$E/crash.log" || true
adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime|ResourceResolutionException|Caused by|$P" > "$E/relevant.log" || true
if grep -q "$P" "$E/crash.log"; then
  cat "$E/crash.log"
  exit 1
fi

adb exec-out screencap -p > "$E/final-screen.png"
echo SIGNED_GATE4_RUNTIME_PASS | tee "$E/result.txt"
