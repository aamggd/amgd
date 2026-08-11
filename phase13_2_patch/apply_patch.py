from pathlib import Path

ROOT = Path('FushERP_Mobile_Phase5')

# Version
p = ROOT / 'app/build.gradle.kts'
text = p.read_text(encoding='utf-8')
text = text.replace('versionCode = 19', 'versionCode = 20')
text = text.replace('versionName = "0.13.1-phase13-recipe-delete"', 'versionName = "0.13.2-phase13-material-availability"')
assert 'versionCode = 20' in text
assert '0.13.2-phase13-material-availability' in text
p.write_text(text, encoding='utf-8')

# Production service: detailed availability + improved shortage message
p = ROOT / 'app/src/main/java/com/fush/erp/domain/ProductionService.kt'
text = p.read_text(encoding='utf-8')
marker = '''    data class RecipeComponentInput(\n        val itemId: Long,\n        val quantityBase: Double,\n        val stage: String = "PREPARATION",\n        val expectedLossPct: Double = 0.0\n    )\n\n'''
insert = marker + '''    data class MaterialAvailability(\n        val itemId: Long,\n        val itemCode: String,\n        val itemName: String,\n        val unitName: String,\n        val requiredQtyBase: Double,\n        val availableQtyBase: Double,\n        val shortageQtyBase: Double\n    ) {\n        val isAvailable: Boolean get() = shortageQtyBase <= 1e-9\n    }\n\n'''
if marker not in text:
    raise SystemExit('RecipeComponentInput marker not found')
text = text.replace(marker, insert, 1)

old = '''    suspend fun reserveMaterials(orderId: Long) = db.withTransaction {\n        val order = requireNotNull(db.productionDao().orderById(orderId)) { "أمر الإنتاج غير موجود" }\n        require(order.status == "PLANNED") { "يمكن حجز المواد لأمر مخطط فقط" }\n        val materials = db.productionDao().materialsForOrder(orderId)\n        require(materials.isNotEmpty()) { "لا توجد مواد في أمر الإنتاج" }\n        materials.forEach { material ->\n            val balance = db.stockDao().balance(order.rawWarehouseId, material.itemId)\n            val otherReservations = db.productionDao().reservedByOtherOrders(order.rawWarehouseId, material.itemId, orderId)\n            val available = balance - otherReservations\n            require(available + 1e-9 >= material.standardQtyBase) {\n                "المخزون المتاح لا يكفي للصنف رقم ${material.itemId}: المطلوب ${fmt(material.standardQtyBase)} والمتاح ${fmt(available)}"\n            }\n        }\n        materials.forEach { material ->\n            db.productionDao().updateMaterial(material.copy(reservedQtyBase = material.standardQtyBase))\n        }\n        db.productionDao().updateOrder(order.copy(status = "MATERIALS_RESERVED"))\n    }\n'''
new = '''    suspend fun materialAvailability(orderId: Long): List<MaterialAvailability> {\n        val order = requireNotNull(db.productionDao().orderById(orderId)) { "أمر الإنتاج غير موجود" }\n        val materials = db.productionDao().materialsForOrder(orderId)\n        require(materials.isNotEmpty()) { "لا توجد مواد في أمر الإنتاج" }\n        return materials.map { material ->\n            val item = requireNotNull(db.itemDao().byId(material.itemId)) { "الصنف رقم ${material.itemId} غير موجود" }\n            val unit = db.unitDao().byId(item.baseUnitId)\n            val balance = db.stockDao().balance(order.rawWarehouseId, material.itemId)\n            val otherReservations = db.productionDao().reservedByOtherOrders(order.rawWarehouseId, material.itemId, orderId)\n            val available = (balance - otherReservations).coerceAtLeast(0.0)\n            val shortage = (material.standardQtyBase - available).coerceAtLeast(0.0)\n            MaterialAvailability(\n                itemId = item.id,\n                itemCode = item.code,\n                itemName = item.nameAr,\n                unitName = unit?.nameAr ?: "وحدة",\n                requiredQtyBase = material.standardQtyBase,\n                availableQtyBase = available,\n                shortageQtyBase = shortage\n            )\n        }\n    }\n\n    suspend fun reserveMaterials(orderId: Long) = db.withTransaction {\n        val order = requireNotNull(db.productionDao().orderById(orderId)) { "أمر الإنتاج غير موجود" }\n        require(order.status == "PLANNED") { "يمكن حجز المواد لأمر مخطط فقط" }\n        val materials = db.productionDao().materialsForOrder(orderId)\n        require(materials.isNotEmpty()) { "لا توجد مواد في أمر الإنتاج" }\n        val availability = materialAvailability(orderId)\n        val shortages = availability.filterNot { it.isAvailable }\n        require(shortages.isEmpty()) {\n            val first = shortages.first()\n            "المخزون المتاح لا يكفي للمادة ${first.itemName} (${first.itemCode}): المطلوب ${fmt(first.requiredQtyBase)} ${first.unitName} والمتاح ${fmt(first.availableQtyBase)} ${first.unitName} والناقص ${fmt(first.shortageQtyBase)} ${first.unitName}"\n        }\n        materials.forEach { material ->\n            db.productionDao().updateMaterial(material.copy(reservedQtyBase = material.standardQtyBase))\n        }\n        db.productionDao().updateOrder(order.copy(status = "MATERIALS_RESERVED"))\n    }\n'''
if old not in text:
    raise SystemExit('reserveMaterials block not found')
text = text.replace(old, new, 1)
p.write_text(text, encoding='utf-8')

# Production UI: dialog + navigate to inventory
p = ROOT / 'app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt'
text = p.read_text(encoding='utf-8')
text = text.replace(
    'fun ProductionScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier) {',
    'fun ProductionScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier, onNavigate: (String) -> Unit = {}) {'
)
text = text.replace(
'''    var deleteRecipe by remember { mutableStateOf<RecipeSummary?>(null) }\n    var message by remember { mutableStateOf<String?>(null) }\n''',
'''    var deleteRecipe by remember { mutableStateOf<RecipeSummary?>(null) }\n    var availabilityDialog by remember { mutableStateOf<MaterialAvailabilityDialogState?>(null) }\n    var message by remember { mutableStateOf<String?>(null) }\n''', 1)
old_action = '''                                "PLANNED" -> { container.productionService.reserveMaterials(order.id); message = "تم حجز المواد للأمر ${order.orderNo}" }\n'''
new_action = '''                                "PLANNED" -> {\n                                    val rows = container.productionService.materialAvailability(order.id)\n                                    val canReserve = rows.all { it.isAvailable }\n                                    if (canReserve) {\n                                        container.productionService.reserveMaterials(order.id)\n                                    }\n                                    availabilityDialog = MaterialAvailabilityDialogState(\n                                        orderNo = order.orderNo,\n                                        rows = rows,\n                                        reserved = canReserve\n                                    )\n                                    message = null\n                                }\n'''
if old_action not in text:
    raise SystemExit('PLANNED action not found')
text = text.replace(old_action, new_action, 1)

marker = '''    deleteRecipe?.let { recipe ->\n'''
dialog = '''    availabilityDialog?.let { result ->\n        MaterialAvailabilityDialog(\n            state = result,\n            onDismiss = { availabilityDialog = null },\n            onOpenInventory = {\n                availabilityDialog = null\n                onNavigate("المخزون")\n            }\n        )\n    }\n\n'''
if marker not in text:
    raise SystemExit('deleteRecipe marker not found')
text = text.replace(marker, dialog + marker, 1)

marker = '''@Composable\nprivate fun ProductionOrderCard(order: ProductionOrderSummary, onAction: () -> Unit) {\n'''
component = '''private data class MaterialAvailabilityDialogState(\n    val orderNo: String,\n    val rows: List<com.fush.erp.domain.ProductionService.MaterialAvailability>,\n    val reserved: Boolean\n)\n\n@Composable\nprivate fun MaterialAvailabilityDialog(\n    state: MaterialAvailabilityDialogState,\n    onDismiss: () -> Unit,\n    onOpenInventory: () -> Unit\n) {\n    val hasShortage = state.rows.any { !it.isAvailable }\n    AlertDialog(\n        onDismissRequest = onDismiss,\n        title = { Text("نتيجة فحص المواد") },\n        text = {\n            LazyColumn(\n                modifier = Modifier.heightIn(max = 520.dp),\n                verticalArrangement = Arrangement.spacedBy(8.dp)\n            ) {\n                item {\n                    Text("أمر الإنتاج: ${state.orderNo}", style = MaterialTheme.typography.titleSmall)\n                    Text(\n                        if (state.reserved) "جميع المواد متوفرة وتم حجزها بنجاح."\n                        else "يوجد نقص في المواد التالية. لم يتم إجراء أي حجز جزئي.",\n                        color = if (state.reserved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error\n                    )\n                }\n                items(state.rows, key = { it.itemId }) { row ->\n                    ElevatedCard(Modifier.fillMaxWidth()) {\n                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {\n                            Text("${row.itemName} (${row.itemCode})", style = MaterialTheme.typography.titleSmall)\n                            Text("المطلوب ${formatProductionQty(row.requiredQtyBase)} ${row.unitName}")\n                            Text("المتاح ${formatProductionQty(row.availableQtyBase)} ${row.unitName}")\n                            if (row.isAvailable) {\n                                Text("متوفر", color = MaterialTheme.colorScheme.primary)\n                            } else {\n                                Text(\n                                    "ناقص ${formatProductionQty(row.shortageQtyBase)} ${row.unitName}",\n                                    color = MaterialTheme.colorScheme.error\n                                )\n                            }\n                        }\n                    }\n                }\n            }\n        },\n        confirmButton = {\n            if (hasShortage) {\n                Button(onClick = onOpenInventory) { Text("الذهاب إلى المخزون") }\n            } else {\n                Button(onClick = onDismiss) { Text("متابعة") }\n            }\n        },\n        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }\n    )\n}\n\n@Composable\nprivate fun ProductionOrderCard(order: ProductionOrderSummary, onAction: () -> Unit) {\n'''
if marker not in text:
    raise SystemExit('ProductionOrderCard marker not found')
text = text.replace(marker, component, 1)
p.write_text(text, encoding='utf-8')

# Wire navigation from shell
p = ROOT / 'app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt'
text = p.read_text(encoding='utf-8')
old = '"الإنتاج" -> ProductionScreen(container, user, Modifier.padding(pad))'
new = '"الإنتاج" -> ProductionScreen(container, user, Modifier.padding(pad), onNavigate = { page = it })'
if old not in text:
    raise SystemExit('HomeShell production call not found')
text = text.replace(old, new, 1)
p.write_text(text, encoding='utf-8')

(ROOT / 'PHASE13_2_SCOPE.md').write_text('''# Phase 13.2 — Material Availability UX Fix\n\n- Detailed material availability dialog for planned production orders.\n- Shows Arabic item name, code, required quantity, available quantity, base unit, and shortage.\n- No partial reservation when any material is short.\n- Automatically reserves all materials only when every component is available.\n- Direct button to open Inventory when shortages exist.\n- Improved service error text uses item name/code instead of numeric item id.\n- No database schema change; existing data is preserved.\n''', encoding='utf-8')

print('PHASE13_2_PATCH_APPLIED')
