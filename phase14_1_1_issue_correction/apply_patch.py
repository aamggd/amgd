from pathlib import Path
import base64
import gzip
import hashlib
import subprocess

root = Path("FushERP_Mobile_Phase5")
parts = [
    Path("phase14_1_1_issue_correction/part_00.b64"),
    Path("phase14_1_1_issue_correction/part_01.b64"),
    Path("phase14_1_1_issue_correction/part_02.b64"),
]
payload = "".join(path.read_text(encoding="utf-8").strip() for path in parts)

expected_payload_sha = "c2dc443c464820028c02ebdff37880470b80f0c54162f1319b261e4effb4be65"
actual_payload_sha = hashlib.sha256(payload.encode("utf-8")).hexdigest()
if actual_payload_sha != expected_payload_sha:
    raise SystemExit(f"Phase 14.1.1 payload SHA mismatch: {actual_payload_sha}")

diff_bytes = gzip.decompress(base64.b64decode(payload))
expected_diff_sha = "d765133f13d8956b7be78af159bb5e7dc83fdfe098f9a9a2cf39f87a405f4dfa"
actual_diff_sha = hashlib.sha256(diff_bytes).hexdigest()
if actual_diff_sha != expected_diff_sha:
    raise SystemExit(f"Phase 14.1.1 diff SHA mismatch: {actual_diff_sha}")

build = root / "app/build.gradle.kts"
text = build.read_text(encoding="utf-8")
assert 'versionCode = 30' in text
assert 'versionName = "0.14.1-phase14-seasonality-demand"' in text

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
assert 'versionCode = 31' in text
assert 'versionName = "0.14.1.1-phase14-production-issue-correction"' in text

production_service = (root / "app/src/main/java/com/fush/erp/domain/ProductionService.kt").read_text(encoding="utf-8")
production_ui = (root / "app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt").read_text(encoding="utf-8")
migrations = (root / "app/src/main/java/com/fush/erp/data/Migrations.kt").read_text(encoding="utf-8")
database = (root / "app/src/main/java/com/fush/erp/data/FushDatabase.kt").read_text(encoding="utf-8")
container = (root / "app/src/main/java/com/fush/erp/data/AppContainer.kt").read_text(encoding="utf-8")
scope = (root / "PHASE14_1_1_SCOPE.md").read_text(encoding="utf-8")

for needle in ["CORRECT_PRODUCTION_ISSUE", "PRODUCTION_ISSUE_RETURN", "correctMaterialIssue"]:
    assert needle in production_service, needle
assert "تصحيح صرف المواد" in production_ui
assert "MIGRATION_13_14" in migrations
assert "version = 14" in database
assert "MIGRATION_13_14" in container
assert "Phase 14.1.1" in scope

print("Phase 14.1.1 patch checks passed")
