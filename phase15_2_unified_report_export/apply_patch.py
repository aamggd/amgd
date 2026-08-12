from pathlib import Path
import base64, gzip, hashlib, subprocess

ROOT = Path('FushERP_Mobile_Phase5')
PAYLOAD = Path('phase15_2_unified_report_export/phase15_2.diff.gz.b64')
EXPECTED_GZIP_SHA256 = '49cb27a5b7b830d7dd1c6e0e0f32c268a6926ada69abffb5a61ce878139e70e9'
EXPECTED_DIFF_SHA256 = '59121ad727326f8bb80fac21dce882dec1497f91aee3f96029f699010f2357bf'

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

assert ROOT.is_dir(), 'Missing restored FushERP_Mobile_Phase5 source'
gradle = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
assert 'versionCode = 36' in gradle
assert '0.15.1-phase15-backup-restore' in gradle

compressed = base64.b64decode(PAYLOAD.read_text(encoding='utf-8').strip())
assert sha(compressed) == EXPECTED_GZIP_SHA256, 'Phase 15.2 payload digest mismatch'
patch = gzip.decompress(compressed)
assert sha(patch) == EXPECTED_DIFF_SHA256, 'Phase 15.2 diff digest mismatch'

proc = subprocess.run(
    ['patch', '-p4', '--forward', '--batch'],
    input=patch,
    cwd='.',
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
)
print(proc.stdout.decode('utf-8', errors='replace'))
assert proc.returncode == 0, 'Phase 15.2 patch failed'

patched = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
assert 'versionCode = 37' in patched
assert '0.15.2-phase15-unified-report-export' in patched
report_screen = (ROOT / 'app/src/main/java/com/fush/erp/ui/screens/ReportsScreen.kt').read_text(encoding='utf-8')
export_support = (ROOT / 'app/src/main/java/com/fush/erp/ui/export/ReportExportSupport.kt').read_text(encoding='utf-8')
assert 'ReportExportActions' in report_screen
assert 'buildCurrentReportExportDocument' in report_screen
assert 'مشاركة PDF' in report_screen
assert 'مشاركة Excel' in report_screen
assert 'exportAndSharePdf' in export_support
assert 'exportAndShareXlsx' in export_support
assert (ROOT / 'PHASE15_2_SCOPE.md').is_file()
print('Phase 15.2 patch applied successfully.')
