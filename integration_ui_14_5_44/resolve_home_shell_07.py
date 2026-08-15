from pathlib import Path

path = Path("app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt")
text = path.read_text(encoding="utf-8")

replacements = [
    (
        'Text("لا توجد تنبيهات تشغيلية معلقة حاليًا.", modifier = Modifier.padding(16.dp))',
        'FushInlineState("لا توجد تنبيهات تشغيلية معلقة حاليًا.", modifier = Modifier.padding(8.dp), tone = FushStatusTone.Success)',
    ),
    (
        'if (filteredUnits.isEmpty()) item { Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) { Text("لا توجد وحدات مطابقة.", Modifier.fillMaxWidth().padding(14.dp)) } }',
        'if (filteredUnits.isEmpty()) item { FushInlineState("لا توجد وحدات مطابقة للبحث.") }',
    ),
    (
        'if (filteredWarehouses.isEmpty()) item { Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) { Text("لا توجد مخازن مطابقة.", Modifier.fillMaxWidth().padding(14.dp)) } }',
        'if (filteredWarehouses.isEmpty()) item { FushInlineState("لا توجد مخازن مطابقة للبحث.") }',
    ),
    (
        'if (filteredConversions.isEmpty()) item { Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) { Text("لا توجد تحويلات وحدات مطابقة.", Modifier.fillMaxWidth().padding(14.dp)) } }',
        'if (filteredConversions.isEmpty()) item { FushInlineState("لا توجد تحويلات وحدات مطابقة للبحث.") }',
    ),
    (
        'if (filteredItems.isEmpty()) item { Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) { Text("لا توجد أصناف مطابقة.", Modifier.fillMaxWidth().padding(14.dp)) } }',
        'if (filteredItems.isEmpty()) item { FushInlineState("لا توجد أصناف مطابقة للبحث.") }',
    ),
]

for old, new in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"HomeShell compatibility anchor count for {old!r}: expected 1, got {count}")
    text = text.replace(old, new, 1)

# Preserve the verified 14.5.43 alert-list structure. Patch 07 was authored
# against an older local context that placed alerts.take(4) in an else branch.
# Only its UI state rendering is ported here; no navigation/business logic changes.
path.write_text(text, encoding="utf-8")
print("Applied deterministic Phase 14.5.44 HomeShell UI compatibility resolution")
