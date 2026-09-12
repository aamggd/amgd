#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])
path = root / 'app/src/main/java/com/fush/erp/ui/screens/SupplierItemMovementReportUi.kt'
text = path.read_text(encoding='utf-8')
old = 'import androidx.compose.foundation.layout.weight\n'
new = 'import androidx.compose.foundation.layout.*\n'
count = text.count(old)
if count != 1:
    raise SystemExit(f'weight import root-cause guard: expected 1 direct import, found {count}')
text = text.replace(old, new, 1)
path.write_text(text, encoding='utf-8')
print('GATE2B_WEIGHT_IMPORT_ROOT_CAUSE_FIX=PASS')
