from pathlib import Path
import base64
import hashlib
import io
import tarfile

root = Path("FushERP_Mobile_Phase5")
parts = [Path(f"phase13_9_production_export/part_{i:02d}.b64") for i in range(6)]
for part in parts:
    if not part.exists():
        raise SystemExit(f"Missing Phase 13.9 payload chunk: {part}")

payload = "".join(part.read_text(encoding="utf-8").strip() for part in parts)
expected_payload_sha = "657e5f3fd2ef5089196aec5e9823f4ed9fbd0b1824b902680ee0b0c2b4ce3b02"
actual_payload_sha = hashlib.sha256(payload.encode("utf-8")).hexdigest()
if actual_payload_sha != expected_payload_sha:
    raise SystemExit(f"Phase 13.9 chunk payload SHA mismatch: {actual_payload_sha}")

tar_bytes = base64.b64decode(payload)
expected_tar_sha = "45e5aef8f243f55a65a8fa103f63aadc2537397cf84632711973e4f3862415c8"
actual_tar_sha = hashlib.sha256(tar_bytes).hexdigest()
if actual_tar_sha != expected_tar_sha:
    raise SystemExit(f"Phase 13.9 tar SHA mismatch: {actual_tar_sha}")

build = root / "app/build.gradle.kts"
text = build.read_text(encoding="utf-8")
assert 'versionCode = 28' in text
assert 'versionName = "0.13.8-phase13-production-reports"' in text

with tarfile.open(fileobj=io.BytesIO(tar_bytes), mode="r:gz") as tf:
    tf.extractall(root)

text = build.read_text(encoding="utf-8")
assert 'versionCode = 29' in text
assert 'versionName = "0.13.9-phase13-production-export"' in text

export_file = root / "app/src/main/java/com/fush/erp/ui/export/ReportExportSupport.kt"
assert export_file.exists()
export_text = export_file.read_text(encoding="utf-8")
for needle in ["fun exportPdf", "fun exportXlsx", "fun printPreview", "rightToLeft=\\\"1\\\"", "Downloads"]:
    assert needle in export_text, needle

reports = (root / "app/src/main/java/com/fush/erp/ui/screens/ReportsScreen.kt").read_text(encoding="utf-8")
production = (root / "app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt").read_text(encoding="utf-8")
for needle in ["معاينة قبل الطباعة / طباعة", "FushERP-Production-Report", "buildProductionReportExportDocument"]:
    assert needle in reports, needle
for needle in ["معاينة قبل الطباعة / طباعة", "buildProductionOrderExportDocument", "تفاصيل التشغيلات المصروفة"]:
    assert needle in production, needle

print("Phase 13.9 chunked payload applied and verified")
