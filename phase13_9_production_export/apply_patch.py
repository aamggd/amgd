from pathlib import Path
import base64
import gzip
import hashlib
import subprocess

root = Path("FushERP_Mobile_Phase5")
payload_path = Path("phase13_9_production_export/production_export.diff.gz.b64")
payload = payload_path.read_text(encoding="utf-8").strip()

expected_payload_sha = "d7ee91823593178f423f12f73f8ad66383731b78de289d0123082dc3211f4a4b"
actual_payload_sha = hashlib.sha256(payload.encode("utf-8")).hexdigest()
if actual_payload_sha != expected_payload_sha:
    raise SystemExit(f"Phase 13.9 payload SHA mismatch: {actual_payload_sha}")

diff_bytes = gzip.decompress(base64.b64decode(payload))
expected_diff_sha = "ad86ae60f36d541a0ffc2c90d7a804ce08fe0e1ceda512a54efa84f072e49de2"
actual_diff_sha = hashlib.sha256(diff_bytes).hexdigest()
if actual_diff_sha != expected_diff_sha:
    raise SystemExit(f"Phase 13.9 diff SHA mismatch: {actual_diff_sha}")

build = root / "app/build.gradle.kts"
text = build.read_text(encoding="utf-8")
assert 'versionCode = 28' in text
assert 'versionName = "0.13.8-phase13-production-reports"' in text

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
assert 'versionCode = 29' in text
assert 'versionName = "0.13.9-phase13-production-export"' in text

export_file = root / "app/src/main/java/com/fush/erp/ui/export/ReportExportSupport.kt"
assert export_file.exists()
export_text = export_file.read_text(encoding="utf-8")
for needle in [
    "fun exportPdf",
    "fun exportXlsx",
    "fun printPreview",
    "rightToLeft=\\\"1\\\"",
    "Downloads",
]:
    assert needle in export_text, needle

reports = (root / "app/src/main/java/com/fush/erp/ui/screens/ReportsScreen.kt").read_text(encoding="utf-8")
production = (root / "app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt").read_text(encoding="utf-8")
for needle in ["معاينة قبل الطباعة / طباعة", "FushERP-Production-Report", "buildProductionReportExportDocument"]:
    assert needle in reports, needle
for needle in ["معاينة قبل الطباعة / طباعة", "buildProductionOrderExportDocument", "تفاصيل التشغيلات المصروفة"]:
    assert needle in production, needle

print("Phase 13.9 patch checks passed")
