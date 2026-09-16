#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])
p = root / 'app/src/test/java/com/fush/erp/ui/GlobalSearchableAutocompleteContractTest.kt'
text = p.read_text(encoding='utf-8')
old = '        assertTrue(component.contains("contains(effectiveSearch)"))\n'
new = '        assertTrue(component.contains("contains(normalizedSearch)") || component.contains("contains(effectiveSearch)"))\n'
if text.count(old) != 1:
    raise SystemExit(f'stale searchable contract anchor count={text.count(old)}')
p.write_text(text.replace(old, new, 1), encoding='utf-8')
print('SEARCHABLE_AUTOCOMPLETE_CONTRACT_UPDATED=PASS')
