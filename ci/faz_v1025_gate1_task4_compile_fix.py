#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])

selector = root / 'app/src/main/java/com/fush/erp/ui/FushSearchableSelectionField.kt'
text = selector.read_text(encoding='utf-8')
old = '                        onCreateNew(effectiveSearch.trim())\n'
new = '                        onCreateNew?.invoke(effectiveSearch.trim())\n'
if text.count(old) != 1:
    raise SystemExit(f'selector nullable callback anchor count={text.count(old)}')
selector.write_text(text.replace(old, new, 1), encoding='utf-8')

dialog = root / 'app/src/main/java/com/fush/erp/ui/screens/UnifiedItemDialog.kt'
text = dialog.read_text(encoding='utf-8')
old = '                                    barcode = barcode.ifBlank { null },\n'
new = '                                    barcode = barcode.trim().takeIf { it.isNotEmpty() },\n'
if text.count(old) != 1:
    raise SystemExit(f'barcode nullable anchor count={text.count(old)}')
dialog.write_text(text.replace(old, new, 1), encoding='utf-8')

print('GATE1_TASK4_COMPILE_FIX=PASS')
