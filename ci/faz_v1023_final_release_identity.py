#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])

gradle = root / 'app/build.gradle.kts'
text = gradle.read_text(encoding='utf-8')
old_code = '        versionCode = 23\n'
old_name = '        versionName = "1.0.22-faz-product-master-variants"\n'
if text.count(old_code) != 1:
    raise SystemExit(f'expected one versionCode 23, found {text.count(old_code)}')
if text.count(old_name) != 1:
    raise SystemExit(f'expected one v1.0.22 versionName, found {text.count(old_name)}')
text = text.replace(old_code, '        versionCode = 24\n', 1)
text = text.replace(old_name, '        versionName = "1.0.23-faz-supplier-item-movement-report"\n', 1)
gradle.write_text(text, encoding='utf-8')

# This older feature contract must remain valid in later releases instead of pinning the whole app to v1.0.22.
test = root / 'app/src/test/java/com/fush/erp/domain/EmployeeCustodyAndPayrollHotfixContractTest.kt'
t = test.read_text(encoding='utf-8')
old_block = '''    @Test\n    fun sourceVersionIsV122() {\n        val gradle = source("build.gradle.kts")\n        assertTrue(gradle.contains("versionCode = 23"))\n        assertTrue(gradle.contains("1.0.22-faz-product-master-variants"))\n    }\n'''
new_block = '''    @Test\n    fun sourceVersionRetainsCustodyHotfixOrLater() {\n        val gradle = source("build.gradle.kts")\n        val versionCode = Regex("""versionCode\\s*=\\s*(\\d+)""")\n            .find(gradle)?.groupValues?.get(1)?.toIntOrNull() ?: 0\n        assertTrue(versionCode >= 23)\n        assertTrue(gradle.contains("versionName = "))\n    }\n'''
if t.count(old_block) != 1:
    raise SystemExit(f'expected one stale v1.0.22 version contract, found {t.count(old_block)}')
t = t.replace(old_block, new_block, 1)
test.write_text(t, encoding='utf-8')

# Fresh assertions on the final identity.
final = gradle.read_text(encoding='utf-8')
assert re.search(r'versionCode\s*=\s*24\b', final)
assert 'versionName = "1.0.23-faz-supplier-item-movement-report"' in final
print('FINAL_RELEASE_IDENTITY_PATCH=PASS')
