from pathlib import Path
import base64
import gzip
import hashlib
import subprocess

workspace = Path('.')
root = workspace / 'FushERP_Mobile_Phase5'
chunks_dir = Path('phase15_2_reports_export/payload_chunks')
payload = ''.join((chunks_dir / name).read_text(encoding='utf-8').strip() for name in sorted(p.name for p in chunks_dir.glob('chunk*.txt')))
diff_bytes = gzip.decompress(base64.b64decode(payload))
expected_diff_sha = '9fff26ce32eb0befe09c51271866dd077ff43061836043490a1ee73bde266759'
actual_diff_sha = hashlib.sha256(diff_bytes).hexdigest()
if actual_diff_sha != expected_diff_sha:
    raise SystemExit(f'Phase 15.2 diff SHA mismatch: {actual_diff_sha}')

build = root / 'app/build.gradle.kts'
text = build.read_text(encoding='utf-8')
assert 'versionCode = 36' in text
assert 'versionName = "0.15.1-phase15-backup-restore"' in text

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
assert 'versionCode = 37' in text
assert 'versionName = "0.15.2-phase15-reports-export"' in text

checks = {
    'app/src/main/java/com/fush/erp/ui/export/ReportExportSupport.kt': [
        'fun share(', 'ACTION_SEND', 'FileProvider.getUriForFile'
    ],
    'app/src/main/java/com/fush/erp/ui/screens/ReportsScreen.kt': [
        'ReportExportActions', 'buildExecutiveExportDocument', 'buildSalesExportDocument',
        'buildPurchasesExportDocument', 'buildInventoryExportDocument',
        'buildQualityExportDocument', 'buildFinanceExportDocument',
        'مشاركة PDF', 'مشاركة Excel', 'معاينة قبل الطباعة / طباعة'
    ],
    'PHASE15_2_SCOPE.md': ['Phase 15.2', 'Direct Android sharing', 'No Room schema change']
}
for relative, needles in checks.items():
    path = root / relative
    assert path.exists(), relative
    content = path.read_text(encoding='utf-8')
    for needle in needles:
        assert needle in content, f'{relative}: {needle}'

database = (root / 'app/src/main/java/com/fush/erp/data/FushDatabase.kt').read_text(encoding='utf-8')
assert 'version = 16' in database

print('Phase 15.2 patch checks passed')
