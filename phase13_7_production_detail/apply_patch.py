from pathlib import Path
import base64
import gzip
import hashlib
import subprocess

root = Path('FushERP_Mobile_Phase5')

build = root / 'app/build.gradle.kts'
text = build.read_text(encoding='utf-8')
assert 'versionCode = 26' in text
assert 'versionName = "0.13.6.2-phase13-dashboard-navigation"' in text

parts_dir = Path('phase13_7_production_detail')
payload = ''.join((parts_dir / f'part_{i:02d}.b64').read_text(encoding='utf-8').strip() for i in range(4))
raw_gz = base64.b64decode(payload, validate=True)
assert hashlib.sha256(raw_gz).hexdigest() == '1e1db8ccabe79a25bc012511dfda1af2025ef8bf9a1654dbcf5d2a47702ec286'
diff_bytes = gzip.decompress(raw_gz)
assert hashlib.sha256(diff_bytes).hexdigest() == 'af5b7a46eb015cc34dfa8f273109aac3e1473c721eb5692032dd04ce2398911a'

proc = subprocess.run(
    ['patch', '-p0', '--forward', '--batch'],
    cwd=root,
    input=diff_bytes,
    stdout=subprocess.PIPE,
    stderr=subprocess.STDOUT,
)
print(proc.stdout.decode('utf-8', errors='replace'))
if proc.returncode != 0:
    raise SystemExit(proc.returncode)

text = build.read_text(encoding='utf-8')
text = text.replace('versionCode = 26', 'versionCode = 27', 1)
text = text.replace(
    'versionName = "0.13.6.2-phase13-dashboard-navigation"',
    'versionName = "0.13.7-phase13-production-detail"',
    1,
)
build.write_text(text, encoding='utf-8')

screen = root / 'app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt'
s = screen.read_text(encoding='utf-8')
for needle in [
    'بيان الإنتاج التفصيلي',
    'المواد المستخدمة والأسعار',
    'متوسط تكلفة الوحدة المصروفة',
    'إجمالي تكلفة أمر الإنتاج',
    'تفاصيل التشغيلات المصروفة',
    'فحوص الجودة',
    'عدم المطابقة / CAPA',
]:
    assert needle in s, needle

(root / 'PHASE13_7_SCOPE.md').write_text('''# Phase 13.7 — Detailed Production Statement\n\n- Detailed production statement button on every production order.\n- Order, recipe/version, warehouses, asset/operator and lifecycle dates.\n- Planned, actual, accepted, rejected and scrap quantities plus plan/yield percentages.\n- Standard/reserved/issued material quantities.\n- Historical issued unit cost and material total cost.\n- Lot-by-lot issued quantity, unit cost, total, expiry and issue date.\n- Material, labor, total production cost and accepted-unit cost.\n- Quality checks and CAPA/nonconformance history.\n- No database schema change. Existing data is preserved.\n''', encoding='utf-8')
