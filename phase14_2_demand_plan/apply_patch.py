from pathlib import Path
import base64
import gzip
import hashlib
import subprocess

root = Path("FushERP_Mobile_Phase5")
payload_path = Path("phase14_2_demand_plan/phase14_2.diff.gz.b64")
payload = payload_path.read_text(encoding="utf-8").strip()

expected_payload_sha = "402126cdd47714349197a56c8ec15b2d1370b17c8dc248641371f9cbaa3c9fad"
actual_payload_sha = hashlib.sha256(payload.encode("utf-8")).hexdigest()
if actual_payload_sha != expected_payload_sha:
    raise SystemExit(f"Phase 14.2 payload SHA mismatch: {actual_payload_sha}")

diff_bytes = gzip.decompress(base64.b64decode(payload))
expected_diff_sha = "bfce564835d9f7ae3bf65659f2b5df78b8b105eec8f478b28a75df4edb0588fb"
actual_diff_sha = hashlib.sha256(diff_bytes).hexdigest()
if actual_diff_sha != expected_diff_sha:
    raise SystemExit(f"Phase 14.2 diff SHA mismatch: {actual_diff_sha}")

build = root / "app/build.gradle.kts"
text = build.read_text(encoding="utf-8")
assert 'versionCode = 31' in text
assert 'versionName = "0.14.1.1-phase14-production-issue-correction"' in text

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
assert 'versionCode = 32' in text
assert 'versionName = "0.14.2-phase14-demand-plan-approval"' in text

checks = {
    "app/src/main/java/com/fush/erp/data/entity/PlanningEntities.kt": ["DemandPlanEntity", "manualAdjustmentQtyBase", "revision"],
    "app/src/main/java/com/fush/erp/data/dao/PlanningDao.kt": ["observeDemandPlan", "upsertDemandPlan"],
    "app/src/main/java/com/fush/erp/domain/PlanningService.kt": ["saveDemandPlanDraft", "approveDemandPlan", "reopenDemandPlan", "DEMAND_PLAN"],
    "app/src/main/java/com/fush/erp/ui/screens/PlanningScreen.kt": ["خطة الطلب — الاعتماد والتعديل اليدوي", "اعتماد خطة الطلب", "إعادة فتح الخطة للتعديل"],
    "app/src/test/java/com/fush/erp/domain/PlanningMathTest.kt": ["manual_adjustment_is_difference_between_plan_and_system_forecast"],
    "PHASE14_2_SCOPE.md": ["Phase 14.2", "DRAFT and APPROVED"],
}
for relative, needles in checks.items():
    path = root / relative
    assert path.exists(), relative
    content = path.read_text(encoding="utf-8")
    for needle in needles:
        assert needle in content, f"{relative}: {needle}"

migrations = (root / "app/src/main/java/com/fush/erp/data/Migrations.kt").read_text(encoding="utf-8")
database = (root / "app/src/main/java/com/fush/erp/data/FushDatabase.kt").read_text(encoding="utf-8")
container = (root / "app/src/main/java/com/fush/erp/data/AppContainer.kt").read_text(encoding="utf-8")
assert "MIGRATION_14_15" in migrations
assert "version = 15" in database
assert "DemandPlanEntity::class" in database
assert "MIGRATION_14_15" in container

print("Phase 14.2 patch checks passed")
