from pathlib import Path
import base64
import gzip
import hashlib
import subprocess

workspace = Path('.')
root = workspace / 'FushERP_Mobile_Phase5'
payload_path = Path('phase14_5_1_customer_purchase_details/phase14_5_1.diff.gz.b64')
payload = payload_path.read_text(encoding='utf-8').strip()
expected_payload_sha = 'a7405ea60d68546cdf792229c8f0979f48c1b30d5ee16cdab146874e87075faf'
actual_payload_sha = hashlib.sha256(payload.encode('utf-8')).hexdigest()
if actual_payload_sha != expected_payload_sha:
    raise SystemExit(f'Phase 14.5.1 payload SHA mismatch: {actual_payload_sha}')

diff_bytes = gzip.decompress(base64.b64decode(payload))
expected_diff_sha = '15a616f58b75de65f1b901269ff10c5aa51da3a200a228eaff466d2bce2c6e42'
actual_diff_sha = hashlib.sha256(diff_bytes).hexdigest()
if actual_diff_sha != expected_diff_sha:
    raise SystemExit(f'Phase 14.5.1 diff SHA mismatch: {actual_diff_sha}')

build = root / 'app/build.gradle.kts'
database_path = root / 'app/src/main/java/com/fush/erp/data/FushDatabase.kt'
text = build.read_text(encoding='utf-8')
assert 'versionCode = 39' in text
assert 'versionName = "0.15.4-phase14.5-production-material-planning"' in text
assert 'version = 17' in database_path.read_text(encoding='utf-8')

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
assert 'versionCode = 40' in text
assert 'versionName = "0.15.4.1-phase14.5-customer-purchase-details"' in text
assert 'version = 17' in database_path.read_text(encoding='utf-8')

checks = {
    'app/src/main/java/com/fush/erp/data/dao/SalesDaos.kt': ['totalDueBase', 'paidBase', 'observeReceivables'],
    'app/src/main/java/com/fush/erp/data/entity/SalesEntities.kt': ['totalDueBase', 'paidBase'],
    'app/src/main/java/com/fush/erp/ui/screens/SalesScreens.kt': ['إجمالي المستحق:', 'سدد:', 'باقي عليه:'],
    'app/src/main/java/com/fush/erp/data/dao/PurchaseDaos.kt': ['returnsForInvoice'],
    'app/src/main/java/com/fush/erp/ui/screens/PurchaseScreens.kt': ['PurchaseInvoiceDetailDialog', 'بيان فاتورة المشتريات', 'صافي المشتريات بعد المرتجعات'],
    'PHASE14_5_1_SCOPE.md': ['Phase 14.5.1', 'No Room schema change'],
}
for relative, needles in checks.items():
    path = root / relative
    assert path.exists(), relative
    content = path.read_text(encoding='utf-8')
    for needle in needles:
        assert needle in content, f'{relative}: {needle}'

print('Phase 14.5.1 patch checks passed')
