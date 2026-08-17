#!/usr/bin/env python3
import base64
import hashlib
import hmac
import os
import re
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PKG = "com.fush.erp.recovery"
ACTIVITY = "com.fush.erp.MainActivity"
USERNAME = "runtimeadmin"
PASSWORD = "FushRuntime2026@Backup"
DISPLAY = "Runtime Backup Admin"
OUT = Path(os.environ.get("RUNTIME_OUT", "runtime-evidence"))
OUT.mkdir(parents=True, exist_ok=True)


def run(*args, check=True, capture=True):
    cmd = [str(a) for a in args]
    p = subprocess.run(cmd, text=True, stdout=subprocess.PIPE if capture else None,
                       stderr=subprocess.STDOUT if capture else None)
    if check and p.returncode != 0:
        raise RuntimeError(f"command failed {p.returncode}: {' '.join(cmd)}\n{p.stdout or ''}")
    return (p.stdout or "").strip()


def adb(*args, check=True):
    return run("adb", *args, check=check)


def dump(name="window"):
    remote = "/sdcard/window.xml"
    adb("shell", "uiautomator", "dump", remote)
    local = OUT / f"{name}.xml"
    adb("pull", remote, str(local))
    return ET.parse(local).getroot()


def bounds_center(raw):
    m = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", raw or "")
    if not m:
        raise RuntimeError(f"bad bounds: {raw}")
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def nodes(root):
    return list(root.iter("node"))


def find_node(root, texts=(), descs=(), contains=(), klass=None, enabled=None):
    for n in nodes(root):
        if klass and n.attrib.get("class") != klass:
            continue
        if enabled is not None and n.attrib.get("enabled") != ("true" if enabled else "false"):
            continue
        text = n.attrib.get("text", "")
        desc = n.attrib.get("content-desc", "")
        if texts and text in texts:
            return n
        if descs and desc in descs:
            return n
        if contains and any(x in text or x in desc for x in contains):
            return n
    return None


def wait_node(name, timeout=30, **kwargs):
    end = time.time() + timeout
    last = None
    while time.time() < end:
        try:
            root = dump(f"wait-{name}")
            n = find_node(root, **kwargs)
            if n is not None:
                return n, root
            last = root
        except Exception:
            pass
        time.sleep(1)
    if last is not None:
        ET.ElementTree(last).write(OUT / f"timeout-{name}.xml", encoding="utf-8")
    raise RuntimeError(f"timed out waiting for {name}")


def tap_node(node):
    x, y = bounds_center(node.attrib.get("bounds"))
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(0.5)


def click(name, texts=(), descs=(), contains=(), timeout=30):
    n, _ = wait_node(name, timeout=timeout, texts=texts, descs=descs, contains=contains)
    tap_node(n)


def edit_nodes(root, enabled_only=True):
    result = []
    for n in nodes(root):
        if n.attrib.get("class") != "android.widget.EditText":
            continue
        if enabled_only and n.attrib.get("enabled") != "true":
            continue
        result.append(n)
    return result


def set_text(node, value):
    tap_node(node)
    adb("shell", "input", "keyevent", "KEYCODE_CTRL_A", check=False)
    adb("shell", "input", "keyevent", "KEYCODE_DEL", check=False)
    adb("shell", "input", "text", value)
    time.sleep(0.4)


def force_arabic_preferences():
    xml = OUT / "fush_ui_preferences.xml"
    xml.write_text('<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n<string name="language_tag">ar</string>\n<boolean name="dark_theme" value="false" />\n</map>\n', encoding="utf-8")
    adb("push", str(xml), "/data/local/tmp/fush_ui_preferences.xml")
    adb("shell", "run-as", PKG, "sh", "-c", "mkdir -p shared_prefs && cp /data/local/tmp/fush_ui_preferences.xml shared_prefs/fush_ui_preferences.xml")


def launch():
    adb("shell", "am", "force-stop", PKG, check=False)
    adb("shell", "am", "start", "-W", "-n", f"{PKG}/{ACTIVITY}")
    time.sleep(2)


def totp(secret):
    normalized = re.sub(r"\s+", "", secret).upper()
    pad = "=" * ((8 - len(normalized) % 8) % 8)
    key = base64.b32decode(normalized + pad)
    counter = int(time.time() // 30)
    msg = struct.pack(">Q", counter)
    digest = hmac.new(key, msg, hashlib.sha1).digest()
    off = digest[-1] & 0x0F
    code = (struct.unpack(">I", digest[off:off+4])[0] & 0x7fffffff) % 1000000
    return f"{code:06d}"


def first_run_onboarding():
    _, root = wait_node("initial-admin", contains=("إعداد مدير النظام",), timeout=60)
    edits = edit_nodes(root)
    if len(edits) < 4:
        raise RuntimeError(f"expected >=4 admin edit fields, found {len(edits)}")
    set_text(edits[0], USERNAME)
    set_text(edits[1], DISPLAY)
    set_text(edits[2], PASSWORD)
    set_text(edits[3], PASSWORD)
    click("create-admin", texts=("إنشاء مدير النظام",), timeout=10)

    _, root = wait_node("mfa-setup", contains=("إعداد التحقق الثنائي MFA",), timeout=30)
    edits = edit_nodes(root)
    if not edits:
        raise RuntimeError("MFA current-password field not found")
    set_text(edits[0], PASSWORD)
    click("mfa-generate", texts=("إنشاء مفتاح MFA",), timeout=10)

    _, root = wait_node("mfa-secret", contains=("رابط الإعداد اليدوي",), timeout=20)
    secret = None
    for n in nodes(root):
        text = (n.attrib.get("text") or "").strip()
        if re.fullmatch(r"[A-Z2-7]{16,}", text):
            secret = text
            break
    if not secret:
        raise RuntimeError("MFA Base32 secret not found in UI dump")
    (OUT / "mfa-secret-redacted.txt").write_text(f"captured-length={len(secret)}\n", encoding="utf-8")
    edits = edit_nodes(root)
    if not edits:
        raise RuntimeError("MFA code field not found")
    set_text(edits[-1], totp(secret))
    click("mfa-confirm", texts=("تحقق وفعّل MFA",), timeout=10)
    click("mfa-continue", texts=("حفظت الرموز — متابعة",), timeout=30)
    wait_node("home-menu", texts=("القائمة",), descs=("فتح القائمة الرئيسية",), timeout=40)
    return secret


def login_existing(secret):
    _, root = wait_node("login", texts=("تسجيل الدخول",), contains=("مرحبًا بعودتك",), timeout=60)
    edits = edit_nodes(root)
    if len(edits) < 2:
        raise RuntimeError(f"expected login username/password, found {len(edits)}")
    set_text(edits[0], USERNAME)
    set_text(edits[1], PASSWORD)
    click("login-action", texts=("تسجيل الدخول",), timeout=10)
    _, root = wait_node("mfa-login", contains=("التحقق الثنائي", "رمز MFA أو رمز الاسترداد"), timeout=20)
    edits = edit_nodes(root)
    enabled = [n for n in edits if n.attrib.get("enabled") == "true"]
    if not enabled:
        raise RuntimeError("enabled MFA login field not found")
    set_text(enabled[-1], totp(secret))
    click("mfa-login-submit", texts=("تحقق ودخول",), timeout=10)
    wait_node("home-after-login", texts=("القائمة",), descs=("فتح القائمة الرئيسية",), timeout=40)


def open_drawer():
    root = dump("home-before-drawer")
    n = find_node(root, texts=("القائمة",), descs=("فتح القائمة الرئيسية",))
    if n is None:
        raise RuntimeError("menu control not found")
    tap_node(n)
    wait_node("drawer", texts=("النسخ الاحتياطي والاستعادة",), timeout=15)


def open_backup_and_capture(label):
    open_drawer()
    adb("logcat", "-c")
    root = dump(f"{label}-drawer-before-click")
    node = find_node(root, texts=("النسخ الاحتياطي والاستعادة", "النسخ الاحتياطي"), contains=("النسخ الاحتياطي",))
    if node is None:
        raise RuntimeError("backup drawer item not found")
    tap_node(node)
    time.sleep(4)
    full = adb("logcat", "-d", "-v", "threadtime", check=False)
    (OUT / f"{label}.logcat.txt").write_text(full, encoding="utf-8", errors="replace")
    fatal_lines = []
    lines = full.splitlines()
    for i, line in enumerate(lines):
        if "FATAL EXCEPTION" in line or "AndroidRuntime" in line and "FATAL" in line:
            fatal_lines.extend(lines[max(0, i-5):min(len(lines), i+120)])
            break
    (OUT / f"{label}.fatal.txt").write_text("\n".join(fatal_lines) + ("\n" if fatal_lines else ""), encoding="utf-8")
    adb("exec-out", "screencap", "-p", check=False)
    try:
        root = dump(f"{label}-after-click")
        screen_text = "\n".join((n.attrib.get("text") or "") for n in nodes(root))
    except Exception as e:
        screen_text = f"UI dump failed: {e}"
    pid = adb("shell", "pidof", PKG, check=False).strip()
    crashed = bool(fatal_lines) or not pid
    visible = "النسخ الاحتياطي والاستعادة" in screen_text or "نسخة احتياطية كاملة" in screen_text
    status = f"crashed={crashed}\npid={pid or 'none'}\nbackup_screen_visible={visible}\n"
    (OUT / f"{label}.status.txt").write_text(status, encoding="utf-8")
    print(f"{label}: {status.strip()}")
    return crashed, visible


def install(apk, replace=False):
    args = ["install"]
    if replace:
        args.append("-r")
    args.append(apk)
    out = adb(*args)
    if "Success" not in out:
        raise RuntimeError(f"adb install failed: {out}")


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: runtime_probe.py CENTRAL_DEBUG_APK P1_DEBUG_APK")
    central, p1 = sys.argv[1:]
    adb("wait-for-device")
    adb("shell", "settings", "put", "global", "window_animation_scale", "0", check=False)
    adb("shell", "settings", "put", "global", "transition_animation_scale", "0", check=False)
    adb("shell", "settings", "put", "global", "animator_duration_scale", "0", check=False)

    adb("uninstall", PKG, check=False)  # fresh TEST emulator only; never a production database
    install(central)
    force_arabic_preferences()
    launch()
    secret = first_run_onboarding()
    central_crash, central_visible = open_backup_and_capture("central-before")

    # Upgrade the exact same TEST installation to P1. -r preserves app data/Room DB.
    install(p1, replace=True)
    force_arabic_preferences()
    launch()
    login_existing(secret)
    p1_crash, p1_visible = open_backup_and_capture("p1-before")

    classification = (
        "CENTRAL_ONLY" if central_crash and not p1_crash else
        "BOTH" if central_crash and p1_crash else
        "P1_ONLY" if (not central_crash and p1_crash) else
        "NO_CRASH_REPRODUCED"
    )
    (OUT / "classification.txt").write_text(
        f"classification={classification}\ncentral_crash={central_crash}\ncentral_visible={central_visible}\n"
        f"p1_crash={p1_crash}\np1_visible={p1_visible}\n",
        encoding="utf-8"
    )
    print(f"CLASSIFICATION={classification}")
    # Diagnostic probe intentionally succeeds even when a crash is reproduced so evidence uploads.


if __name__ == "__main__":
    main()
