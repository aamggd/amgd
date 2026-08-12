from pathlib import Path
import base64
import gzip
import hashlib
import subprocess

root = Path("FushERP_Mobile_Phase5")
payload_path = Path("phase14_3_sales_budget/phase14_3.diff.gz.b64")
payload = payload_path.read_text(encoding="utf-8").strip()

expected_payload_sha = "964411051a84fde4efb7abfb7586ef78d8164aa10129ade6e3304e97d252022f"
actual_payload_sha = hashlib.sha256(payload.encode("utf-8")).hexdigest()
if actual_payload_sha != expected_payload_sha:
    raise SystemExit(f"Phase 14.3 payload SHA mismatch: {actual_payload_sha}")

diff_bytes = gzip.decompress(base64.b64decode(payload))
expected_diff_sha = "30293e2e5a2a0e55460df0c6c3c0564473553309f8cde7ce59fbd2c184330637"
actual_diff_sha = hashlib.sha256(diff_bytes).hexdigest()
if actual_diff_sha != expected_diff_sha:
    raise SystemExit(f"Phase 14.3 diff SHA mismatch: {actual_diff_sha}")

build = root / "app/build.gradle.kts"
text = build.read_text(encoding="utf-8")
assert 'versionCode = 33' in text
assert 'versionName = "0.14.2.1-phase14-planning-menu-fix"' in text

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
assert 'versionCode = 34' in text
assert 'versionName = "0.14.3-phase14-sales-budget"' in text

checks = {
    "app/src/main/java/com/fush/erp/data/entity/PlanningEntities.kt": ["SalesBudgetWeekEntity", "WeeklySalesActualRow"],
    "app/src/main/java/com/fush/erp/data/dao/PlanningDao.kt": ["weeklySalesActual", "sales_budget_weeks"],
    "app/src/main/java/com/fush/erp/domain/PlanningMath.kt": ["distributeMonthlyTarget", "validateWeeklyBudget", "achievementPct"],
    "app/src/main/java/com/fush/erp/domain/PlanningService.kt": ["autoDistributeWeeklySalesBudget", "saveWeeklySalesBudget"],
    "app/src/main/java/com/fush/erp/ui/screens/PlanningScreen.kt": ["موازنة المبيعات الشهرية والأسبوعية", "تعديل الموازنة الأسبوعية", "تحديث المبيعات الفعلية"],
    "app/src/test/java/com/fush/erp/domain/PlanningMathTest.kt": ["weekly_budget_distribution_sums_to_monthly_target", "weekly_budget_total_must_equal_monthly_target"],
    "PHASE14_3_SCOPE.md": ["Phase 14.3", "Monthly & Weekly Sales Budget"],
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
assert "MIGRATION_15_16" in migrations
assert "version = 16" in database
assert "SalesBudgetWeekEntity::class" in database
assert "MIGRATION_15_16" in container

print("Phase 14.3 patch checks passed")
