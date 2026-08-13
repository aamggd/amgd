#!/usr/bin/env python3
from pathlib import Path

root = Path("FushERP_Mobile_Phase5")
ui = root / "app/src/main/java/com/fush/erp/ui/screens/AdvancedInventoryScreens.kt"
if not ui.is_file():
    raise SystemExit("AdvancedInventoryScreens.kt not found")

text = ui.read_text(encoding="utf-8")
old = "    val reorder by container.db.advancedInventoryDao().observeReorderAlerts().collectAsState(initial = emptyList())"
new = "\n".join([
    "    val reorderAt = remember { System.currentTimeMillis() }",
    "    val reorder by container.db.advancedInventoryDao().observeReorderAlerts(reorderAt).collectAsState(initial = emptyList())",
    "    val reorderPolicies by container.db.advancedInventoryDao().observeReorderPolicies().collectAsState(initial = emptyList())",
])

if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit("Could not find reorder declaration to repair")

ui.write_text(text, encoding="utf-8")
rej = Path(str(ui) + ".rej")
if rej.exists():
    rej.unlink()

checks = {
    root / "app/build.gradle.kts": ["versionCode = 46", "0.15.4.7-phase14.5-warehouse-reorder"],
    root / "app/src/main/java/com/fush/erp/data/FushDatabase.kt": ["WarehouseReorderPolicyEntity::class", "version = 20"],
    root / "app/src/main/java/com/fush/erp/data/Migrations.kt": ["MIGRATION_19_20", "warehouse_reorder_policies"],
    root / "app/src/main/java/com/fush/erp/data/dao/AdvancedInventoryDao.kt": ["observeReorderPolicies", "observeReorderAlerts(at: Long)"],
    ui: ["observeReorderAlerts(reorderAt)", "reorderPolicies", "إعداد حدود إعادة الطلب حسب المخزن"],
    root / "app/src/test/java/com/fush/erp/domain/WarehouseReorderMathTest.kt": ["usableQuantity_excludesExpiredAndControlledLots"],
}
for path, needles in checks.items():
    if not path.is_file():
        raise SystemExit(f"Missing expected file: {path}")
    body = path.read_text(encoding="utf-8")
    for needle in needles:
        if needle not in body:
            raise SystemExit(f"Repair verification failed: {path} missing {needle}")

print("Phase 14.5.7 UI compatibility repair completed and verified")
