from pathlib import Path
import base64
import gzip
import hashlib
import subprocess

workspace = Path('.')
root = workspace / 'FushERP_Mobile_Phase5'
chunks_dir = Path('phase15_2_reports_export/payload_chunks')
payload = ''.join(
    (chunks_dir / name).read_text(encoding='utf-8').strip()
    for name in sorted(p.name for p in chunks_dir.glob('chunk*.txt'))
)

diff_bytes = gzip.decompress(base64.b64decode(payload))
expected_diff_sha = '041126262638ec8405b38f246ee40e806245dd71f79270778d6404c384704ee8'
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
        'fun sharePdf(', 'fun shareXlsx(', 'Intent.ACTION_SEND',
        'FileProvider.getUriForFile', 'report-share'
    ],
    'app/src/main/java/com/fush/erp/ui/export/ReportExportActions.kt': [
        'التصدير والمشاركة والطباعة', 'مشاركة PDF', 'مشاركة Excel',
        'معاينة قبل الطباعة / طباعة'
    ],
    'app/src/main/java/com/fush/erp/ui/screens/ReportsScreen.kt': [
        'buildCurrentReportExportDocument', 'buildExecutiveReportExportDocument',
        'buildSalesReportExportDocument', 'buildPurchasesReportExportDocument',
        'buildInventoryReportExportDocument', 'buildProductionReportExportDocument',
        'buildQualityReportExportDocument', 'buildFinanceReportExportDocument',
        'ReportExportActions('
    ],
    'app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt': [
        'ReportExportActions(', 'buildProductionOrderExportDocument'
    ],
    'app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt': [
        'BuildConfig.VERSION_NAME', 'المرحلة 15.2 جاهزة'
    ],
    'app/src/main/res/xml/file_paths.xml': [
        'cache-path', 'report_share', 'report-share/'
    ],
    'PHASE15_2_SCOPE.md': [
        'Phase 15.2', 'PDF', 'Excel', 'Room schema يبقى 16'
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

print('Phase 15.2 patch checks passed')
