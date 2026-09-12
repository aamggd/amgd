#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])
base = root / 'app/src/test/java/com/fush/erp/domain'

patches = {
    'EmployeeCompensationContractTest.kt': (
        'assertTrue(db.contains("FUSH_DB_SCHEMA_VERSION = 52"))',
        'assertTrue(currentSchemaVersion(db) >= 47)'
    ),
    'EmployeeCustodyAndPayrollHotfixContractTest.kt': (
        'assertTrue(db.contains("FUSH_DB_SCHEMA_VERSION = 52"))',
        'assertTrue(currentSchemaVersion(db) >= 51)'
    ),
    'EmployeeTerminationRetroactiveContractTest.kt': (
        'assertTrue(db.contains("FUSH_DB_SCHEMA_VERSION = 52"))',
        'assertTrue(currentSchemaVersion(db) >= 50)'
    ),
    'ProductMasterVariantsContractTest.kt': (
        'assertTrue(db.contains("FUSH_DB_SCHEMA_VERSION = 52"))',
        'assertTrue(currentSchemaVersion(db) >= 52)'
    ),
}

helper = '''\n    private fun currentSchemaVersion(dbSource: String): Int =\n        Regex("""FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)""")\n            .find(dbSource)?.groupValues?.get(1)?.toIntOrNull() ?: 0\n'''

for name, (old, new) in patches.items():
    path = base / name
    text = path.read_text(encoding='utf-8')
    if text.count(old) != 1:
        raise SystemExit(f'{name}: expected one stale schema-52 assertion, found {text.count(old)}')
    text = text.replace(old, new, 1)
    class_name = path.stem
    anchor = f'class {class_name} {{\n'
    if text.count(anchor) != 1:
        raise SystemExit(f'{name}: expected exactly one class anchor, found {text.count(anchor)}')
    text = text.replace(anchor, anchor + helper, 1)
    path.write_text(text, encoding='utf-8')
    print(f'{name}=PATCHED')

print('LEGACY_SCHEMA_CONTRACTS_FORWARD_COMPATIBLE=PASS')
