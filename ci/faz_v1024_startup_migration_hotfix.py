#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])

bootstrap = root / 'app/src/main/java/com/fush/erp/data/AccountingWaveBRoomBootstrap.kt'
text = bootstrap.read_text(encoding='utf-8')
old = '            MIGRATION_50_51_EMPLOYEE_CUSTODY,\n            MIGRATION_51_52_PRODUCT_MASTER\n'
new = '            MIGRATION_50_51_EMPLOYEE_CUSTODY,\n            MIGRATION_51_52_PRODUCT_MASTER,\n            MIGRATION_52_53_SUPPLIER_PROVENANCE\n'
if text.count(old) != 1:
    raise SystemExit(f'bootstrap migration anchor mismatch: found {text.count(old)}')
text = text.replace(old, new, 1)
bootstrap.write_text(text, encoding='utf-8')

build = root / 'app/build.gradle.kts'
gradle = build.read_text(encoding='utf-8')
gradle2, n1 = re.subn(r'versionCode\s*=\s*24\b', 'versionCode = 25', gradle, count=1)
gradle3, n2 = re.subn(r'versionName\s*=\s*"1\.0\.23-faz-supplier-item-movement-report"', 'versionName = "1.0.24-faz-startup-migration-hotfix"', gradle2, count=1)
if n1 != 1 or n2 != 1:
    raise SystemExit(f'version identity anchor mismatch: code={n1} name={n2}')
build.write_text(gradle3, encoding='utf-8')

print('STARTUP_MIGRATION_HOTFIX_APPLIED=PASS')
print('versionCode=25')
print('versionName=1.0.24-faz-startup-migration-hotfix')
