from pathlib import Path
import base64
import gzip
import hashlib
import subprocess

workspace = Path('.')
root = workspace / 'FushERP_Mobile_Phase5'
payload_path = Path('phase15_1_backup_restore/phase15_1.diff.gz.b64')
payload = payload_path.read_text(encoding='utf-8').strip()

expected_payload_sha = '703f940b261ee88667cb10117df772895505e86b29d65625bffa6a0056b14dc0'
actual_payload_sha = hashlib.sha256(payload.encode('utf-8')).hexdigest()
if actual_payload_sha != expected_payload_sha:
    raise SystemExit(f'Phase 15.1 payload SHA mismatch: {actual_payload_sha}')

diff_bytes = gzip.decompress(base64.b64decode(payload))
expected_diff_sha = 'db64c1015d1786359a8b7cc9d22fc730eedf85a75e93c07b1b21a6ffe50271e6'
actual_diff_sha = hashlib.sha256(diff_bytes).hexdigest()
if actual_diff_sha != expected_diff_sha:
    raise SystemExit(f'Phase 15.1 diff SHA mismatch: {actual_diff_sha}')

build = root / 'app/build.gradle.kts'
text = build.read_text(encoding='utf-8')
assert 'versionCode = 35' in text
assert 'versionName = "0.14.3.1-phase14-accounting-tables"' in text

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
assert 'versionCode = 36' in text
assert 'versionName = "0.15.1-phase15-backup-restore"' in text

checks = {
    'app/src/main/java/com/fush/erp/backup/BackupRestoreManager.kt': [
        'wal_checkpoint(FULL)', 'PRAGMA quick_check(1)', 'stageRestore', 'applyPendingRestore',
        'pre_restore_safety', 'CREATE_BACKUP', 'RESTORE_BACKUP'
    ],
    'app/src/main/java/com/fush/erp/backup/BackupArchiveCodec.kt': [
        'databaseSha256', 'extractAndVerify', 'FORMAT_VERSION'
    ],
    'app/src/main/java/com/fush/erp/ui/screens/BackupRestoreScreen.kt': [
        'النسخ الاحتياطي والاستعادة', 'إنشاء نسخة احتياطية الآن', 'اختيار ملف للاستعادة', 'مشاركة آخر نسخة'
    ],
    'app/src/main/java/com/fush/erp/FushErpApplication.kt': ['applyPendingRestore'],
    'app/src/main/AndroidManifest.xml': ['FileProvider', '@xml/file_paths'],
    'app/src/test/java/com/fush/erp/backup/BackupArchiveCodecTest.kt': ['archive_round_trip_preserves_manifest_and_database_hash'],
    'PHASE15_1_SCOPE.md': ['Phase 15.1', 'WAL FULL checkpoint', 'No Room schema change']
}
for relative, needles in checks.items():
    path = root / relative
    assert path.exists(), relative
    content = path.read_text(encoding='utf-8')
    for needle in needles:
        assert needle in content, f'{relative}: {needle}'

# Phase 15.1 deliberately does not change the Room schema.
database = (root / 'app/src/main/java/com/fush/erp/data/FushDatabase.kt').read_text(encoding='utf-8')
assert 'version = 16' in database

print('Phase 15.1 patch checks passed')
