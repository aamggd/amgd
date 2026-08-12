from pathlib import Path
import base64
import gzip
import hashlib
import subprocess

workspace = Path('.')
root = workspace / 'FushERP_Mobile_Phase5'
payload = Path('phase14_5_2_purchase_price/phase14_5_2.diff.gz.b64').read_text(encoding='utf-8').strip()
expected_payload_sha = 'bef874518301dbe996795a16726c60b0b07c91f4e21e2808cf5a52a0a2f0b7c7'
actual_payload_sha = hashlib.sha256(payload.encode('utf-8')).hexdigest()
if actual_payload_sha != expected_payload_sha:
    raise SystemExit(f'Phase 14.5.2 payload SHA mismatch: {actual_payload_sha}')

diff_bytes = gzip.decompress(base64.b64decode(payload))
expected_diff_sha = 'b3c4657ffe28b1b8d44e8ef693fad7100bc8b502958ed57a0c6dcd1b67e672e2'
actual_diff_sha = hashlib.sha256(diff_bytes).hexdigest()
if actual_diff_sha != expected_diff_sha:
    raise SystemExit(f'Phase 14.5.2 diff SHA mismatch: {actual_diff_sha}')

build = root / 'app/build.gradle.kts'
database = root / 'app/src/main/java/com/fush/erp/data/FushDatabase.kt'
text = build.read_text(encoding='utf-8')
assert 'versionCode = 40' in text
assert 'versionName = "0.15.4.1-phase14.5-customer-purchase-details"' in text
assert 'version = 17' in database.read_text(encoding='utf-8')

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
assert 'versionCode = 41' in text
assert 'versionName = "0.15.4.2-phase14.5-purchase-price-compare"' in text
assert 'version = 17' in database.read_text(encoding='utf-8')
checks = {
    'app/src/main/java/com/fush/erp/data/dao/PurchaseDaos.kt': ['lastPurchasePrice', 'p.currencyCode = :currencyCode'],
    'app/src/main/java/com/fush/erp/data/entity/PurchaseEntities.kt': ['LastPurchasePriceRow'],
    'app/src/main/java/com/fush/erp/domain/PurchaseMath.kt': ['PurchasePriceVariance', 'priceVariance'],
    'app/src/main/java/com/fush/erp/ui/screens/PurchaseScreens.kt': ['آخر سعر شراء:', 'فرق السعر:', 'lastPurchasePrice'],
    'app/src/test/java/com/fush/erp/domain/PurchaseMathTest.kt': ['comparesCurrentPurchasePriceWithPreviousPrice'],
    'PHASE14_5_2_SCOPE.md': ['Phase 14.5.2', 'Room schema remains 17'],
}
for relative, needles in checks.items():
    content = (root / relative).read_text(encoding='utf-8')
    for needle in needles:
        assert needle in content, f'{relative}: {needle}'
print('Phase 14.5.2 patch checks passed')
