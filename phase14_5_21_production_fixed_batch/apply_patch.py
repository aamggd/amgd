from pathlib import Path

ROOT = Path('FushERP_Mobile_Phase5')


def repl(rel, old, new, count=1):
    p = ROOT / rel
    text = p.read_text(encoding='utf-8')
    if old not in text:
        raise SystemExit(f'pattern not found {rel}: {old[:100]!r}')
    p.write_text(text.replace(old, new, count), encoding='utf-8')


def insert_after(rel, anchor, add):
    p = ROOT / rel
    text = p.read_text(encoding='utf-8')
    if add in text:
        return
    if anchor not in text:
        raise SystemExit(f'anchor not found {rel}: {anchor[:100]!r}')
    p.write_text(text.replace(anchor, anchor + add, 1), encoding='utf-8')

repl('app/build.gradle.kts', 'versionCode = 59', 'versionCode = 60')
repl('app/build.gradle.kts', 'versionName = "0.15.4.20-phase14.5-customer-statement-search"', 'versionName = "0.15.4.21-phase14.5-production-order-delete-fixed-batch"')

insert_after(
    'app/src/main/java/com/fush/erp/domain/ProductionMath.kt',
    '''    fun scaleQuantity(componentQty: Double, recipeOutputQty: Double, plannedOutputQty: Double): Double {\n        require(componentQty >= 0.0 && componentQty.isFinite()) { "كمية مكون الوصفة غير صالحة" }\n        require(recipeOutputQty > 0.0 && recipeOutputQty.isFinite()) { "ناتج الوصفة القياسي غير صالح" }\n        require(plannedOutputQty > 0.0 && plannedOutputQty.isFinite()) { "كمية الإنتاج المخططة غير صالحة" }\n        return componentQty * plannedOutputQty / recipeOutputQty\n    }\n''',
    '''\n    fun fixedBatchComponentQuantity(componentQty: Double, plannedOutputQty: Double): Double {\n        require(componentQty > 0.0 && componentQty.isFinite())\n        require(plannedOutputQty > 0.0 && plannedOutputQty.isFinite())\n        return componentQty\n    }\n'''
)

insert_after(
    'app/src/main/java/com/fush/erp/data/dao/ProductionDaos.kt',
    '''    @Update\n    suspend fun updateOrder(row: ProductionOrderEntity)\n''',
    '''\n    @Query("DELETE FROM production_orders WHERE id = :orderId")\n    suspend fun deleteOrderById(orderId: Long): Int\n'''
)

insert_after(
    'app/src/main/java/com/fush/erp/domain/ProductionService.kt',
    '''    suspend fun deleteRecipe(recipeId: Long) = db.withTransaction {\n        val recipe = requireNotNull(db.recipeDao().byId(recipeId)) { "الوصفة غير موجودة" }\n        val usedByOrders = db.recipeDao().productionOrderCount(recipeId)\n        require(usedByOrders == 0) {\n            "لا يمكن حذف الوصفة ${recipe.code} إصدار ${recipe.versionNo} لأنها مستخدمة في $usedByOrders أمر إنتاج. يجب الاحتفاظ بها للتتبع والتكلفة."\n        }\n        val deleted = db.recipeDao().deleteById(recipeId)\n        check(deleted == 1) { "تعذر حذف الوصفة" }\n    }\n''',
    '''\n\n    suspend fun deleteOrder(orderId: Long, userId: Long, reason: String = "حذف أمر إنتاج قبل الصرف") = db.withTransaction {\n        val order = requireNotNull(db.productionDao().orderById(orderId)) { "أمر الإنتاج غير موجود" }\n        require(order.status in setOf("PLANNED", "MATERIALS_RESERVED")) {\n            "لا يمكن حذف أمر الإنتاج بعد صرف المواد. بعد الصرف يجب استخدام التصحيح/الإلغاء للحفاظ على المخزون والمحاسبة."\n        }\n        require(db.productionDao().batchForOrder(orderId) == null) { "لا يمكن حذف أمر له دفعة إنتاج" }\n        require(db.productionDao().issuesForOrder(orderId).isEmpty()) { "لا يمكن حذف أمر توجد عليه حركات صرف مواد" }\n        val materials = db.productionDao().materialsForOrder(orderId)\n        require(materials.all { it.issuedQtyBase <= 1e-9 && it.issueCostBase <= 1e-9 }) {\n            "لا يمكن حذف أمر بعد تسجيل صرف أو تكلفة مواد"\n        }\n        db.governanceDao().insertAudit(\n            AuditEventEntity(\n                userId = userId,\n                action = "DELETE_PRODUCTION_ORDER",\n                entityType = "PRODUCTION_ORDER",\n                entityId = order.id.toString(),\n                oldValue = "${order.orderNo}|status=${order.status}|planned=${order.plannedOutputQtyBase}",\n                newValue = "DELETED",\n                reason = reason.ifBlank { "حذف أمر إنتاج قبل الصرف" }\n            )\n        )\n        val deleted = db.productionDao().deleteOrderById(order.id)\n        check(deleted == 1) { "تعذر حذف أمر الإنتاج" }\n    }\n'''
)

repl(
    'app/src/main/java/com/fush/erp/domain/ProductionService.kt',
    '''                    standardQtyBase = ProductionMath.scaleQuantity(\n                        component.quantityBase,\n                        recipe.targetOutputQtyBase,\n                        plannedOutputQtyBase\n                    )''',
    '''                    standardQtyBase = ProductionMath.fixedBatchComponentQuantity(\n                        componentQty = component.quantityBase,\n                        plannedOutputQty = plannedOutputQtyBase\n                    )'''
)

repl(
    'app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt',
    '''    var deleteRecipe by remember { mutableStateOf<RecipeSummary?>(null) }\n    var availabilityDialog by remember { mutableStateOf<MaterialAvailabilityDialogState?>(null) }''',
    '''    var deleteRecipe by remember { mutableStateOf<RecipeSummary?>(null) }\n    var deleteOrder by remember { mutableStateOf<ProductionOrderSummary?>(null) }\n    var availabilityDialog by remember { mutableStateOf<MaterialAvailabilityDialogState?>(null) }'''
)

repl(
    'app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt',
    '''                    Text("${recipe.code} • الناتج القياسي ${formatProductionQty(recipe.targetOutputQtyBase)} عبوة • ${recipe.status}")''',
    '''                    Text("${recipe.code} • الناتج المرجعي المتوقع ${formatProductionQty(recipe.targetOutputQtyBase)} عبوة • ${recipe.status}")\n                    Text("كميات مواد الوصفة ثابتة للدفعة الواحدة؛ الناتج المخطط والفعلي قد يزيد أو ينقص.", style = MaterialTheme.typography.bodySmall)'''
)

repl(
    'app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt',
    '''                onDetails = { detailOrderId = order.id },\n                onAction = {''',
    '''                onDetails = { detailOrderId = order.id },\n                onDelete = { deleteOrder = order },\n                onAction = {'''
)

insert_after(
    'app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt',
    '''    availabilityDialog?.let { result ->\n        MaterialAvailabilityDialog(\n            state = result,\n            onDismiss = { availabilityDialog = null },\n            onOpenInventory = {\n                availabilityDialog = null\n                onNavigate("المخزون")\n            }\n        )\n    }\n''',
    '''\n\n    deleteOrder?.let { order ->\n        AlertDialog(\n            onDismissRequest = { deleteOrder = null },\n            title = { Text("حذف أمر الإنتاج") },\n            text = {\n                Text(\n                    if (order.status == "MATERIALS_RESERVED")\n                        "هل تريد حذف ${order.orderNo}؟ سيتم إلغاء حجز المواد تلقائيًا لأنه لم يتم صرفها من المخزون بعد."\n                    else\n                        "هل تريد حذف ${order.orderNo}؟ هذا مسموح فقط قبل صرف أي مواد أو إنشاء دفعة."\n                )\n            },\n            confirmButton = {\n                Button(\n                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),\n                    onClick = {\n                        scope.launch {\n                            try {\n                                container.productionService.deleteOrder(order.id, user.id)\n                                message = "تم حذف أمر الإنتاج ${order.orderNo}"\n                                deleteOrder = null\n                            } catch (e: Exception) {\n                                message = e.message ?: "تعذر حذف أمر الإنتاج"\n                                deleteOrder = null\n                            }\n                        }\n                    }\n                ) { Text("حذف الأمر") }\n            },\n            dismissButton = { TextButton(onClick = { deleteOrder = null }) { Text("إلغاء") } }\n        )\n    }\n'''
)

repl(
    'app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt',
    '''private fun ProductionOrderCard(\n    order: ProductionOrderSummary,\n    onDetails: () -> Unit,\n    onAction: () -> Unit\n) {''',
    '''private fun ProductionOrderCard(\n    order: ProductionOrderSummary,\n    onDetails: () -> Unit,\n    onDelete: () -> Unit,\n    onAction: () -> Unit\n) {'''
)

repl(
    'app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt',
    '''            if (order.status !in setOf("CLOSED", "REJECTED", "CANCELLED")) {\n                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(nextActionAr(order.status)) }\n            }''',
    '''            if (order.status !in setOf("CLOSED", "REJECTED", "CANCELLED")) {\n                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(nextActionAr(order.status)) }\n            }\n            if (order.status in setOf("PLANNED", "MATERIALS_RESERVED")) {\n                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {\n                    Text("حذف أمر الإنتاج", color = MaterialTheme.colorScheme.error)\n                }\n            }'''
)

repl(
    'app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt',
    '''                        label = { Text("الناتج القياسي بالوحدة الأساسية") },\n                        singleLine = true\n                    )\n                    Text("أضف مكونات الخلطة واحدًا واحدًا", style = MaterialTheme.typography.titleSmall)''',
    '''                        label = { Text("الناتج المرجعي المتوقع للدفعة") },\n                        supportingText = { Text("رقم مرجعي للمقارنة فقط؛ لا يغيّر كميات المواد. نفس مواد الوصفة قد تعطي ناتجًا فعليًا أكثر أو أقل.") },\n                        singleLine = true\n                    )\n                    Text("أضف مكونات الخلطة واحدًا واحدًا — هذه الكميات ثابتة للدفعة الواحدة", style = MaterialTheme.typography.titleSmall)'''
)

repl(
    'app/src/main/java/com/fush/erp/ui/screens/ProductionScreens.kt',
    '''                OutlinedTextField(outputText, { outputText = it }, label = { Text("الإنتاج المخطط بالعبوة") }, singleLine = true)''',
    '''                OutlinedTextField(\n                    outputText,\n                    { outputText = it },\n                    label = { Text("الإنتاج المتوقع/المخطط بالعبوة") },\n                    supportingText = { Text("تغييره لا يضاعف ولا يقلل مواد الوصفة؛ المواد ثابتة للدفعة، والناتج الفعلي يُسجل بعد التعبئة.") },\n                    singleLine = true\n                )'''
)

insert_after(
    'app/src/test/java/com/fush/erp/domain/ProductionMathTest.kt',
    '''    @Test\n    fun `recipe quantities scale with planned output`() {\n        assertEquals(5.0, ProductionMath.scaleQuantity(2.5, 360.0, 720.0), 1e-9)\n        assertEquals(150.0, ProductionMath.scaleQuantity(75.0, 360.0, 720.0), 1e-9)\n    }\n''',
    '''\n    @Test\n    fun fixedBatchRecipeKeepsSameMaterialsWhenExpectedYieldChanges() {\n        assertEquals(2.5, ProductionMath.fixedBatchComponentQuantity(2.5, 360.0), 1e-9)\n        assertEquals(2.5, ProductionMath.fixedBatchComponentQuantity(2.5, 372.0), 1e-9)\n        assertEquals(2.5, ProductionMath.fixedBatchComponentQuantity(2.5, 350.0), 1e-9)\n    }\n'''
)

(ROOT / 'PHASE14_5_21_SCOPE.md').write_text('''# Phase 14.5.21 — Production Order Delete + Fixed Batch Formula\n\n- Production orders may be hard-deleted only while PLANNED or MATERIALS_RESERVED.\n- Deleting a MATERIALS_RESERVED order releases its reservation because no stock issue has happened yet.\n- Orders with material issues, batches, stock/accounting effects cannot be hard-deleted.\n- Deletion is audited with user, order number, status and reason.\n- Recipe component quantities are fixed inputs for one batch.\n- The recipe output quantity is a reference/expected yield, not a scaler for materials.\n- Planned output may be above or below the reference without changing recipe material quantities.\n- Actual output remains entered after filling and may differ from planned/reference yield.\n- Existing production orders keep their snapshotted material quantities; no data migration changes them.\n- Room schema remains 23.\n''', encoding='utf-8')
print('Phase 14.5.21 patch applied')
