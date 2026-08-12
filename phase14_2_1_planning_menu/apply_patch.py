from pathlib import Path
import base64, gzip, hashlib, subprocess

root = Path('FushERP_Mobile_Phase5')
payload = Path('phase14_2_1_planning_menu/phase14_2_1.diff.gz.b64').read_text(encoding='utf-8').strip()
assert hashlib.sha256(payload.encode()).hexdigest() == 'b6132ff7c4c5c408b2855ddb0251ae1b98abff41981376b7312a23a66e30d48e'
diff_bytes = gzip.decompress(base64.b64decode(payload))
assert hashlib.sha256(diff_bytes).hexdigest() == '9b20f8997fba26562b04c8454869ce3af617ad4a7f74c1395628364921fe840d'

build = root/'app/build.gradle.kts'
text = build.read_text(encoding='utf-8')
assert 'versionCode = 32' in text
assert '0.14.2-phase14-demand-plan-approval' in text

p = subprocess.run(['patch','-p1','--forward','--batch'], cwd=root, input=diff_bytes, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
print(p.stdout.decode(errors='replace'))
if p.returncode != 0: raise SystemExit(p.returncode)

text = build.read_text(encoding='utf-8')
assert 'versionCode = 33' in text
assert '0.14.2.1-phase14-planning-menu-fix' in text
home = (root/'app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt').read_text(encoding='utf-8')
assert '.verticalScroll(rememberScrollState())' in home
assert home.index('"التخطيط والموسمية" to "التخطيط"') < home.index('"المخزون والمستودعات" to "المخزون"')
print('Phase 14.2.1 patch checks passed')
