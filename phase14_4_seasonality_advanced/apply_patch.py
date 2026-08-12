from pathlib import Path
import base64
import gzip
import hashlib
import subprocess

workspace = Path('.')
root = workspace / 'FushERP_Mobile_Phase5'
parts_dir = Path('phase14_4_seasonality_advanced')
payload = ''.join(
    p.read_text(encoding='utf-8').strip()
    for p in sorted(parts_dir.glob('part_*.b64'))
)

expected_b64_sha = 'a422f5dc8f5453ece6e5987c31c61ba8fb85c4f3b4bfd6453de507a9fc3e0da1'
actual_b64_sha = hashlib.sha256(payload.encode()).hexdigest()
if actual_b64_sha != expected_b64_sha:
    raise SystemExit(f'Phase 14.4 payload SHA mismatch: {actual_b64_sha}')

diff_bytes = gzip.decompress(base64.b64decode(payload))
expected_diff_sha = '827f42b693d9ecef3d210447922b505fc1341060f23e3075b6598c0cd718e363'
actual_diff_sha = hashlib.sha256(diff_bytes).hexdigest()
if actual_diff_sha != expected_diff_sha:
    raise SystemExit(f'Phase 14.4 diff SHA mismatch: {actual_diff_sha}')

build = root / 'app/build.gradle.kts'
text = build.read_text(encoding='utf-8')
assert 'versionCode = 37' in text
assert 'versionName = "0.15.2-phase15-reports-export"' in text

proc = subprocess.run(
    ['patch', '-p1', '--forward', '--batch'],
    cwd=workspace,
    input=diff_bytes,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
)
print(proc.stdout.decode('utf-8', errors='replace'))
if proc.returncode != 0:
    raise SystemExit(proc.returncode)

text = build.read_text(encoding='utf-8')
assert 'versionCode = 38' in text
assert 'versionName = "0.15.3-phase14.4-seasonality-advanced"' in text

checks = {
    'app/src/main/java/com/fush/erp/domain/PlanningService.kt': [
        'suspend fun seasonalDemandAnalysis(',
        'suspend fun provinceSeasonalityComparison(',
        'entityType = "DEMAND_SEASONALITY"',
        'seasonalityRows(itemId, provinceCode)'
    ],
    'app/src/main/java/com/fush/erp/domain/PlanningMath.kt': [
        'val summerMonths: Set<Int> = setOf(4, 5, 6, 7, 8, 9)',
        'val winterMonths: Set<Int> = setOf(10, 11, 12, 1, 2, 3)',
        'fun averageSeasonFactor(',
        'fun relativeDifferencePct('
    ],
    'app/src/main/java/com/fush/erp/ui/screens/PlanningScreen.kt': [
        'تحليل الصيف والشتاء',
        'مقارنة المحافظات الموسمية',
        'SeasonalComparisonRow(',
        'التعريف التشغيلي الافتراضي للتحليل'
    ],
    'app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt': [
        'المرحلة 14.4 جاهزة'
    ],
    'PHASE14_4_SCOPE.md': [
        'Phase 14.4',
        'summer = Apr-Sep',
        'database stays at schema 16'
    ]
}
for relative, needles in checks.items():
    path = root / relative
    assert path.exists(), relative
    content = path.read_text(encoding='utf-8')
    for needle in needles:
        assert needle in content, f'{relative}: {needle}'

database = (root / 'app/src/main/java/com/fush/erp/data/FushDatabase.kt').read_text(encoding='utf-8')
assert 'version = 16' in database

print('Phase 14.4 patch checks passed')
