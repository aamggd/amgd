from pathlib import Path
import base64
import gzip
import hashlib
import subprocess

root = Path("FushERP_Mobile_Phase5")
payload_path = Path("phase14_3_1_accounting_tables/phase14_3_1.diff.gz.b64")
payload = payload_path.read_text(encoding="utf-8").strip()

expected_payload_sha = "a3b36f2f9a54bab43a45679a80f58201207c20d405e1e46324899276227356b4"
actual_payload_sha = hashlib.sha256(payload.encode("utf-8")).hexdigest()
if actual_payload_sha != expected_payload_sha:
    raise SystemExit(f"Phase 14.3.1 payload SHA mismatch: {actual_payload_sha}")

diff_bytes = gzip.decompress(base64.b64decode(payload))
expected_diff_sha = "766942716667846b441a07593645f3215ec683e0c88466e4dd23a0d50e1229cb"
actual_diff_sha = hashlib.sha256(diff_bytes).hexdigest()
if actual_diff_sha != expected_diff_sha:
    raise SystemExit(f"Phase 14.3.1 diff SHA mismatch: {actual_diff_sha}")

build = root / "app/build.gradle.kts"
text = build.read_text(encoding="utf-8")
assert 'versionCode = 34' in text
assert 'versionName = "0.14.3-phase14-sales-budget"' in text

proc = subprocess.run(
    ["patch", "-p1", "--forward", "--batch"],
    cwd=root,
    input=diff_bytes,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
)
print(proc.stdout.decode("utf-8", errors="replace"))
if proc.returncode != 0:
    raise SystemExit(proc.returncode)

text = build.read_text(encoding="utf-8")
assert 'versionCode = 35' in text
assert 'versionName = "0.14.3.1-phase14-accounting-tables"' in text

screen = root / "app/src/main/java/com/fush/erp/ui/screens/AccountingScreens.kt"
s = screen.read_text(encoding="utf-8")
for needle in [
    "AccountingTableHeader",
    "AccountingTableRow",
    "AccountingSummaryRow",
    "اسحب الجدول أفقياً",
    "حركة مدين",
    "تفصيل الإيرادات",
    "رصيد النقد آخر الفترة",
]:
    assert needle in s, needle

print("Phase 14.3.1 accounting tables patch checks passed")
