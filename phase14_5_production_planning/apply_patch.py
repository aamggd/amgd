from pathlib import Path
import base64
import gzip
import hashlib
import subprocess

workspace = Path('.')
root = workspace / 'FushERP_Mobile_Phase5'
parts = sorted(Path('phase14_5_production_planning').glob('part_*.b64'))
if len(parts) != 4:
    raise SystemExit(f'Expected 4 Phase 14.5 payload parts, found {len(parts)}')

payload = ''.join(p.read_text(encoding='utf-8').strip() for p in parts)
expected_payload_sha = 'cbe13ef079834928afa9e47840e485995e9a1a5e4f297bd3556f8379b1b9b4d3'
actual_payload_sha = hashlib.sha256(payload.encode('utf-8')).hexdigest()
if actual_payload_sha != expected_payload_sha:
    raise SystemExit(f'Phase 14.5 payload SHA mismatch: {actual_payload_sha}')

diff_bytes = gzip.decompress(base64.b64decode(payload))
expected_diff_sha = '6ea21412a9b92c5797dee7bf8852b20f3437e9d7193f296436a60df083c67048'
actual_diff_sha = hashlib.sha256(diff_bytes).hexdigest()
if actual_diff_sha != expected_diff_sha:
    raise SystemExit(f'Phase 14.5 diff SHA mismatch: {actual_diff_sha}')

build = root / 'app/build.gradle.kts'
database_path = root / 'app/src/main/java/com/fush/erp/data/FushDatabase.kt'
text = build.read_text(encoding='utf-8')
assert 'versionCode = 38' in text
assert 'versionName = "0.15.3-phase14.4-seasonality-advanced"' in text
assert 'version = 16' in database_path.read_text(encoding='utf-8')

proc = subprocess.run(
    ['patch', '-p0', '--forward', '--batch'],
    cwd=workspace,
    input=diff_bytes,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
)
print(proc.stdout.decode('utf-8', errors='replace'))
if proc.returncode != 0:
    raise SystemExit(proc.returncode)

text = build.read_text(encoding='utf-8')
assert 'versionCode = 39' in text
assert 'versionName = "0.15.4-phase14.5-production-material-planning"' in text
assert 'version = 17' in database_path.read_text(encoding='utf-8')

checks = {
    'app/src/main/java/com/fush/erp/data/entity/PlanningEntities.kt': [
        'InventoryPlanningPolicyEntity', 'ProductionPlanEntity',
        'ProductionPlanMaterialEntity', 'ProductionPlanMaterialView'
    ],
    'app/src/main/java/com/fush/erp/domain/PlanningMath.kt': [
        'safetyStockQty', 'reorderPointQty', 'requiredBatchCount',
        'componentRequirement', 'suggestedPurchaseQty'
    ],
    'app/src/main/java/com/fush/erp/domain/PlanningService.kt': [
        'generateProductionPlan', 'approveProductionPlan',
        'reopenProductionPlan', 'saveInventoryPlanningPolicy'
    ],
    'app/src/main/java/com/fush/erp/data/Migrations.kt': [
        'MIGRATION_16_17', 'inventory_planning_policies',
        'production_plans', 'production_plan_materials'
    ],
    'app/src/main/java/com/fush/erp/ui/screens/PlanningScreen.kt': [
        'خطة الإنتاج والمواد والمخزون', 'توليد خطة الإنتاج والمواد',
        'أيام مخزون الأمان', 'الشراء المقترح'
    ],
    'PHASE14_5_SCOPE.md': [
        'Phase 14.5', 'Room schema: 16 -> 17'
    ],
}
for relative, needles in checks.items():
    path = root / relative
    assert path.exists(), relative
    content = path.read_text(encoding='utf-8')
    for needle in needles:
        assert needle in content, f'{relative}: {needle}'

print('Phase 14.5 patch checks passed')
