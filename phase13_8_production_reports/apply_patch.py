from pathlib import Path
import base64
import gzip
import hashlib
import subprocess

root = Path("FushERP_Mobile_Phase5")
payload_path = Path("phase13_8_production_reports/production_reports.diff.gz.b64")
payload = payload_path.read_text(encoding="utf-8").strip()

expected_payload_sha = "ab6473af370713a8f413a99ce5470433b2f0e82c6f4f8d0b100d8a7d273b9a70"
actual_payload_sha = hashlib.sha256((payload + "\n").encode("utf-8")).hexdigest()
if actual_payload_sha != expected_payload_sha:
    # GitHub contents normally preserves no required final newline; verify the exact visible payload too.
    actual_payload_sha = hashlib.sha256(payload.encode("utf-8")).hexdigest()
    if actual_payload_sha != expected_payload_sha:
        raise SystemExit(f"Phase 13.8 payload SHA mismatch: {actual_payload_sha}")

diff_bytes = gzip.decompress(base64.b64decode(payload))
expected_diff_sha = "e797719becbd13a8e0a83baf72e53510dc54696c6483e051f9310502a25ddc51"
actual_diff_sha = hashlib.sha256(diff_bytes).hexdigest()
if actual_diff_sha != expected_diff_sha:
    raise SystemExit(f"Phase 13.8 diff SHA mismatch: {actual_diff_sha}")

build = root / "app/build.gradle.kts"
text = build.read_text(encoding="utf-8")
assert 'versionCode = 27' in text
assert 'versionName = "0.13.7-phase13-production-detail"' in text

proc = subprocess.run(
    ["patch", "-p2", "--forward", "--batch"],
    cwd=root,
    input=diff_bytes,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
)
print(proc.stdout.decode("utf-8", errors="replace"))
if proc.returncode != 0:
    raise SystemExit(proc.returncode)

text = build.read_text(encoding="utf-8")
assert 'versionCode = 28' in text
assert 'versionName = "0.13.8-phase13-production-reports"' in text

reports = root / "app/src/main/java/com/fush/erp/ui/screens/ReportsScreen.kt"
report_text = reports.read_text(encoding="utf-8")
for needle in [
    "مؤشرات الإنتاج للفترة",
    "استهلاك المواد الفعلي",
    "إجمالي تكلفة الإنتاج",
    "متوسط تكلفة المقبول/وحدة",
    "اليوم",
]:
    assert needle in report_text, needle

(root / "PHASE13_8_SCOPE.md").write_text(
    """# Phase 13.8 — Production Reports & Analytics\n\n"
    "- Direct Production > Production reports and analytics entry point.\n"
    "- Daily, current month, last 30 days, current year, and all-period filters.\n"
    "- Production KPIs: planned, actual, accepted, rejected, scrap, plan achievement, acceptance and scrap rates.\n"
    "- Production cost KPIs: materials, labor, total production cost, and average accepted-unit cost.\n"
    "- Historical material consumption report using actual issue quantities and costs.\n"
    "- Per-material quantity, average historical issue cost, total cost, and number of production orders.\n"
    "- Production order report uses manufacture date when a batch exists; otherwise planned date.\n"
    "- No database schema change; existing user data is preserved.\n"
    """,
    encoding="utf-8",
)
