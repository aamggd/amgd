package com.fush.erp.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.*
import com.fush.erp.domain.ProductionMath
import com.fush.erp.ui.*
import com.fush.erp.ui.export.ReportExportActions
import com.fush.erp.ui.export.ReportExportDocument
import com.fush.erp.ui.export.ReportExportTable
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProductionScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier, onNavigate: (String) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val recipes by container.db.recipeDao().observeSummaries().collectAsState(initial = emptyList())
    val orders by container.db.productionDao().observeOrderSummaries().collectAsState(initial = emptyList())
    val warehouses by container.db.warehouseDao().observeAll().collectAsState(initial = emptyList())
    val assets by container.db.maintenanceDao().observeAssets().collectAsState(initial = emptyList())
    val employees by container.db.employeeDao().observeActiveEmployees().collectAsState(initial = emptyList())
    val allItems by container.db.itemDao().observeAll().collectAsState(initial = emptyList())
    var showNewRecipe by remember { mutableStateOf(false) }
    var showNewOrder by remember { mutableStateOf(false) }
    var outputOrder by remember { mutableStateOf<ProductionOrderSummary?>(null) }
    var qualityBatch by remember { mutableStateOf<ProductionBatchEntity?>(null) }
    var versionRecipe by remember { mutableStateOf<RecipeSummary?>(null) }
    var deleteRecipe by remember { mutableStateOf<RecipeSummary?>(null) }
    var deleteOrder by remember { mutableStateOf<ProductionOrderSummary?>(null) }
    var availabilityDialog by remember { mutableStateOf<MaterialAvailabilityDialogState?>(null) }
    var detailOrderId by remember { mutableStateOf<Long?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var orderSearch by remember { mutableStateOf("") }
    var orderFilter by remember { mutableStateOf("الكل") }

    val activeRecipes = recipes.count { it.status == "ACTIVE" }
    val activeOrders = orders.count { it.status !in setOf("CLOSED", "REJECTED", "CANCELLED") }
    val qualityHoldOrders = orders.count { it.status == "QC_HOLD" }
    val completedOrders = orders.count { it.status == "CLOSED" }
    val filteredOrders = orders.filter { order ->
        val matchesSearch = orderSearch.isBlank() ||
            order.orderNo.contains(orderSearch, ignoreCase = true) ||
            order.productName.contains(orderSearch, ignoreCase = true) ||
            (order.batchNo?.contains(orderSearch, ignoreCase = true) == true)
        val matchesFilter = when (orderFilter) {
            "نشطة" -> order.status !in setOf("CLOSED", "REJECTED", "CANCELLED")
            "تحت الجودة" -> order.status == "QC_HOLD"
            "مكتملة" -> order.status == "CLOSED"
            "مرفوضة" -> order.status == "REJECTED"
            else -> true
        }
        matchesSearch && matchesFilter
    }

    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            FushSectionHeader(
                title = "الإنتاج والجودة",
                subtitle = "متابعة دورة التصنيع من الوصفة وحجز المواد حتى الإفراج النهائي عن الدفعة وتثبيت التكلفة الفعلية."
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FushMetricCard("أوامر نشطة", activeOrders.toString(), Modifier.weight(1f), "قيد التنفيذ", FushStatusTone.Info)
                FushMetricCard("تحت الجودة", qualityHoldOrders.toString(), Modifier.weight(1f), "بانتظار قرار", if (qualityHoldOrders > 0) FushStatusTone.Warning else FushStatusTone.Success)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FushMetricCard("وصفات فعالة", activeRecipes.toString(), Modifier.weight(1f), "إصدارات تشغيل", FushStatusTone.Neutral)
                FushMetricCard("مكتملة", completedOrders.toString(), Modifier.weight(1f), "أوامر مغلقة", FushStatusTone.Success)
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("إجراءات التشغيل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showNewOrder = true },
                            enabled = activeRecipes > 0,
                            modifier = Modifier.weight(1f)
                        ) { Text("أمر إنتاج جديد") }
                        OutlinedButton(onClick = { showNewRecipe = true }, modifier = Modifier.weight(1f)) { Text("وصفة جديدة") }
                    }
                    OutlinedButton(onClick = { onNavigate("تقارير الإنتاج") }, modifier = Modifier.fillMaxWidth()) {
                        Text("تقارير الإنتاج والجودة والتكلفة")
                    }
                    if (activeRecipes == 0) {
                        Text(
                            "أنشئ وصفة فعالة أولًا قبل إنشاء أمر إنتاج.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    FushOperationMessage(message, onConsumed = { message = null })
                }
            }
        }
        item {
            FushSectionHeader("الوصفات المعتمدة", "كل إصدار يحافظ على كميات المواد المرجعية للدفعة ولا يغيّر الإصدارات المستخدمة سابقًا.")
        }
        if (recipes.isEmpty()) {
            item {
                FushEmptyState(
                    title = "لا توجد وصفات إنتاج حتى الآن",
                    detail = "أنشئ وصفة فعالة قبل بدء أول أمر إنتاج.",
                )
            }
        }
        items(recipes, key = { "recipe-${it.id}" }) { recipe ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(recipe.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${recipe.code} • إصدار ${recipe.versionNo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FushStatusPill(
                            if (recipe.status == "ACTIVE") "فعالة" else "موقوفة",
                            if (recipe.status == "ACTIVE") FushStatusTone.Success else FushStatusTone.Neutral
                        )
                    }
                    DetailLine("الناتج المرجعي", "${formatProductionQty(recipe.targetOutputQtyBase)} عبوة")
                    Text("الكميات المرجعية ثابتة للدفعة؛ الناتج المخطط والفعلي قد يختلفان حسب أمر الإنتاج.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (recipe.status == "ACTIVE") {
                            OutlinedButton(onClick = { versionRecipe = recipe }) { Text("إصدار جديد") }
                        }
                        TextButton(onClick = { deleteRecipe = recipe }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        item {
            FushSectionHeader("أوامر الإنتاج", "تابع المرحلة الحالية واتخذ الإجراء التالي من بطاقة الأمر نفسها.")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = orderSearch,
                onValueChange = { orderSearch = it },
                label = { Text("بحث برقم الأمر أو المنتج أو الدفعة") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("الكل", "نشطة", "تحت الجودة", "مكتملة", "مرفوضة")) { filter ->
                    FilterChip(selected = orderFilter == filter, onClick = { orderFilter = filter }, label = { Text(filter) })
                }
            }
        }
        if (filteredOrders.isEmpty()) {
            item {
                FushEmptyState(
                    title = if (orders.isEmpty()) "لا توجد أوامر إنتاج حتى الآن" else "لا توجد نتائج مطابقة",
                    detail = if (orders.isEmpty()) "أنشئ أول أمر إنتاج بعد التأكد من وجود وصفة فعالة ومخازن جاهزة." else "غيّر البحث أو مرشح الحالة لعرض أوامر أخرى.",
                )
            }
        }
        items(filteredOrders, key = { "order-${it.id}" }) { order ->
            ProductionOrderCard(
                order = order,
                onDetails = { detailOrderId = order.id },
                onDelete = { deleteOrder = order },
                onAction = {
                    scope.launch {
                        try {
                            when (order.status) {
                                "PLANNED" -> {
                                    val rows = container.productionService.materialAvailability(order.id)
                                    val canReserve = rows.all { it.isAvailable }
                                    if (canReserve) container.productionService.reserveMaterials(order.id, user.id)
                                    availabilityDialog = MaterialAvailabilityDialogState(order.orderNo, rows, canReserve)
                                    message = null
                                }
                                "MATERIALS_RESERVED" -> {
                                    val cost = container.productionService.issueReservedMaterials(order.id, user.id)
                                    message = "تم صرف المواد. تكلفة الصرف ${formatMoney(cost)} ريال"
                                }
                                "MATERIALS_ISSUED" -> { container.productionService.beginPreparation(order.id, user.id); message = "بدأت مرحلة التحضير" }
                                "PREPARATION" -> { container.productionService.beginMixing(order.id, user.id); message = "بدأت مرحلة الخلط" }
                                "MIXING" -> { container.productionService.beginFilling(order.id, user.id); message = "بدأت مرحلة التعبئة" }
                                "FILLING" -> outputOrder = order
                                "QC_HOLD" -> {
                                    qualityBatch = container.db.productionDao().batchForOrder(order.id)
                                    if (qualityBatch == null) message = "لم يتم العثور على الدفعة"
                                }
                            }
                        } catch (e: Exception) {
                            message = e.message ?: "تعذر تنفيذ العملية"
                        }
                    }
                }
            )
        }
    }

    if (showNewRecipe) {
        NewRecipeDialog(
            finishedProducts = allItems.filter { it.category == "FINISHED_GOOD" && it.isActive },
            componentItems = allItems.filter { it.category != "FINISHED_GOOD" && it.isActive },
            onDismiss = { showNewRecipe = false }
        ) { product, outputQty, components, notes ->
            scope.launch {
                try {
                    val id = container.productionService.createRecipe(
                        productItemId = product.id,
                        targetOutputQtyBase = outputQty,
                        components = components.map {
                            com.fush.erp.domain.ProductionService.RecipeComponentInput(
                                itemId = it.item.id,
                                quantityBase = it.quantityBase,
                                stage = it.stage
                            )
                        },
                        notes = notes,
                        userId = user.id
                    )
                    message = "تم إنشاء الوصفة رقم $id للمنتج ${product.nameAr}"
                    showNewRecipe = false
                } catch (e: Exception) {
                    message = e.message ?: "تعذر إنشاء الوصفة"
                }
            }
        }
    }

    if (showNewOrder) {
        NewProductionOrderDialog(
            recipes = recipes.filter { it.status == "ACTIVE" },
            warehouses = warehouses,
            assets = assets,
            employees = employees,
            onDismiss = { showNewOrder = false }
        ) { recipe, rawWarehouse, finishedWarehouse, asset, operator, outputQty, laborCost, notes ->
            scope.launch {
                try {
                    val id = container.productionService.createOrder(
                        recipeId = recipe.id,
                        rawWarehouseId = rawWarehouse.id,
                        finishedWarehouseId = finishedWarehouse.id,
                        plannedOutputQtyBase = outputQty,
                        directLaborCostBase = laborCost,
                        plannedDate = System.currentTimeMillis(),
                        createdBy = user.id,
                        primaryAssetId = asset?.id,
                        operatorEmployeeId = operator?.id,
                        notes = notes
                    )
                    message = "تم إنشاء أمر الإنتاج رقم $id"
                    showNewOrder = false
                } catch (e: Exception) {
                    message = e.message ?: "تعذر إنشاء أمر الإنتاج"
                }
            }
        }
    }

    availabilityDialog?.let { result ->
        MaterialAvailabilityDialog(
            state = result,
            onDismiss = { availabilityDialog = null },
            onOpenInventory = {
                availabilityDialog = null
                onNavigate("المخزون")
            }
        )
    }


    deleteOrder?.let { order ->
        FushConfirmDialog(
            title = "حذف أمر الإنتاج",
            detail = if (order.status == "MATERIALS_RESERVED")
                "سيتم حذف ${order.orderNo} وإلغاء حجز المواد تلقائيًا لأنه لم يتم صرفها من المخزون بعد."
            else
                "سيتم حذف ${order.orderNo}. هذا مسموح فقط قبل صرف أي مواد أو إنشاء دفعة.",
            confirmLabel = "حذف الأمر",
            destructive = true,
            onDismiss = { deleteOrder = null },
            onConfirm = {
                scope.launch {
                    try {
                        container.productionService.deleteOrder(order.id, user.id)
                        message = "تم حذف أمر الإنتاج ${order.orderNo}"
                        deleteOrder = null
                    } catch (e: Exception) {
                        message = e.message ?: "تعذر حذف أمر الإنتاج"
                        deleteOrder = null
                    }
                }
            },
        )
    }

    deleteRecipe?.let { recipe ->
        FushConfirmDialog(
            title = "حذف الوصفة",
            detail = "سيتم طلب حذف ${recipe.productName} — إصدار ${recipe.versionNo}. إذا كانت الوصفة مستخدمة في أي أمر إنتاج فلن يسمح النظام بحذفها حفاظًا على التتبع والتكلفة.",
            confirmLabel = "حذف نهائي",
            destructive = true,
            onDismiss = { deleteRecipe = null },
            onConfirm = {
                scope.launch {
                    try {
                        container.productionService.deleteRecipe(recipe.id, user.id)
                        message = "تم حذف الوصفة ${recipe.code} إصدار ${recipe.versionNo}"
                        deleteRecipe = null
                    } catch (e: Exception) {
                        message = e.message ?: "تعذر حذف الوصفة"
                        deleteRecipe = null
                    }
                }
            },
        )
    }

    versionRecipe?.let { recipe ->
        RecipeVersionDialog(container, recipe, onDismiss = { versionRecipe = null }) { quantities, note ->
            scope.launch {
                try {
                    val id = container.productionService.createRecipeVersion(recipe.id, quantities, note, user.id)
                    message = "تم إنشاء إصدار وصفة جديد رقم $id دون تعديل الإصدار المستخدم"
                    versionRecipe = null
                } catch (e: Exception) {
                    message = e.message ?: "تعذر إنشاء إصدار الوصفة"
                }
            }
        }
    }

    outputOrder?.let { order ->
        SubmitQualityDialog(order, onDismiss = { outputOrder = null }) { actual, scrap, notes ->
            scope.launch {
                try {
                    val batchId = container.productionService.submitForQuality(order.id, actual, scrap, notes, user.id)
                    qualityBatch = container.db.productionDao().batchById(batchId)
                    message = "تم إنشاء الدفعة ${qualityBatch?.batchNo ?: batchId} ووضعها تحت فحص الجودة"
                    outputOrder = null
                } catch (e: Exception) {
                    message = e.message ?: "تعذر إرسال الدفعة للجودة"
                }
            }
        }
    }

    qualityBatch?.let { batch ->
        BatchQualityDialog(
            container = container,
            batch = batch,
            user = user,
            onDismiss = { qualityBatch = null },
            onCompleted = { text ->
                message = text
                qualityBatch = null
            }
        )
    }

    detailOrderId?.let { orderId ->
        ProductionOrderDetailDialog(
            container = container,
            orderId = orderId,
            user = user,
            onDismiss = { detailOrderId = null }
        )
    }
}

private data class MaterialAvailabilityDialogState(
    val orderNo: String,
    val rows: List<com.fush.erp.domain.ProductionService.MaterialAvailability>,
    val reserved: Boolean
)

@Composable
private fun MaterialAvailabilityDialog(
    state: MaterialAvailabilityDialogState,
    onDismiss: () -> Unit,
    onOpenInventory: () -> Unit
) {
    val hasShortage = state.rows.any { !it.isAvailable }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نتيجة فحص المواد") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("أمر الإنتاج: ${state.orderNo}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (state.reserved) "جميع المواد متوفرة وتم حجزها بنجاح."
                        else "يوجد نقص في المواد التالية. لم يتم إجراء أي حجز جزئي.",
                        color = if (state.reserved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                itemsIndexed(state.rows, key = { index, row -> "material-availability-${row.itemId}-$index" }) { _, row ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${row.itemName} (${row.itemCode})", style = MaterialTheme.typography.titleSmall)
                            Text("المطلوب ${formatProductionQty(row.requiredQtyBase)} ${row.unitName}")
                            Text("المتاح ${formatProductionQty(row.availableQtyBase)} ${row.unitName}")
                            if (row.isAvailable) {
                                Text("متوفر", color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text(
                                    "ناقص ${formatProductionQty(row.shortageQtyBase)} ${row.unitName}",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (hasShortage) {
                Button(onClick = onOpenInventory) { Text("الذهاب إلى المخزون") }
            } else {
                Button(onClick = onDismiss) { Text("متابعة") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

@Composable
private fun ProductionOrderCard(
    order: ProductionOrderSummary,
    onDetails: () -> Unit,
    onDelete: () -> Unit,
    onAction: () -> Unit
) {
    val terminal = order.status in setOf("CLOSED", "REJECTED", "CANCELLED")
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(order.orderNo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(order.productName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FushStatusPill(statusAr(order.status), productionStatusTone(order.status))
            }
            DetailLine("تاريخ التخطيط", formatProductionDate(order.plannedDate))
            DetailLine("الإنتاج المخطط", "${formatProductionQty(order.plannedOutputQtyBase)} عبوة")
            order.batchNo?.let { DetailLine("رقم الدفعة", it) }
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("المرحلة الحالية", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(productionStageLabel(order.status), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            OutlinedButton(onClick = onDetails, modifier = Modifier.fillMaxWidth()) { Text("بيان الإنتاج والتكلفة والتتبع") }
            if (!terminal) {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(nextActionAr(order.status)) }
            }
            if (order.status in setOf("PLANNED", "MATERIALS_RESERVED")) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("حذف أمر الإنتاج", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}


private data class ProductionDetailMaterial(
    val row: ProductionMaterialView,
    val itemCode: String,
    val stage: String,
    val issues: List<ProductionIssueEntity>
)

private data class ProductionOrderDetailState(
    val order: ProductionOrderEntity,
    val recipe: RecipeEntity?,
    val product: ItemEntity?,
    val rawWarehouseName: String,
    val finishedWarehouseName: String,
    val assetName: String?,
    val operatorName: String?,
    val batch: ProductionBatchEntity?,
    val materials: List<ProductionDetailMaterial>,
    val checks: List<QualityCheckEntity>,
    val nonConformances: List<NonConformanceEntity>,
    val cost: com.fush.erp.domain.ProductionService.CostSummary
)

@Composable
private fun ProductionOrderDetailDialog(
    container: AppContainer,
    orderId: Long,
    user: UserEntity,
    onDismiss: () -> Unit
) {
    var detail by remember(orderId) { mutableStateOf<ProductionOrderDetailState?>(null) }
    var error by remember(orderId) { mutableStateOf<String?>(null) }
    var correctionMaterial by remember(orderId) { mutableStateOf<ProductionDetailMaterial?>(null) }
    var refreshToken by remember(orderId) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(orderId, refreshToken) {
        try {
            val order = requireNotNull(container.db.productionDao().orderById(orderId)) { "أمر الإنتاج غير موجود" }
            val recipe = container.db.recipeDao().byId(order.recipeId)
            val product = container.db.itemDao().byId(order.productItemId)
            val warehouses = container.db.warehouseDao().allActive()
            val rawWarehouse = warehouses.firstOrNull { it.id == order.rawWarehouseId }
            val finishedWarehouse = warehouses.firstOrNull { it.id == order.finishedWarehouseId }
            val assetName = order.primaryAssetId?.let { container.db.maintenanceDao().assetById(it)?.nameAr }
            val operatorName = container.db.employeeDao().operatorAssignment(order.id)
                ?.let { container.db.employeeDao().employeeById(it.employeeId)?.fullNameAr }
            val batch = container.db.productionDao().batchForOrder(order.id)
            val views = container.db.productionDao().materialViews(order.id)
            val issues = container.db.productionDao().issuesForOrder(order.id)
            val stages = recipe?.let { container.db.recipeDao().componentViews(it.id) }
                ?.associate { it.itemId to it.stage }
                ?: emptyMap()
            val materials = views.map { row ->
                ProductionDetailMaterial(
                    row = row,
                    itemCode = container.db.itemDao().byId(row.itemId)?.code ?: "—",
                    stage = stages[row.itemId] ?: "",
                    issues = issues.filter { it.materialId == row.id }
                )
            }
            val checks = batch?.let { container.db.productionDao().checksForBatch(it.id) } ?: emptyList()
            val ncs = batch?.let { container.db.productionDao().nonConformancesForBatch(it.id) } ?: emptyList()
            detail = ProductionOrderDetailState(
                order = order,
                recipe = recipe,
                product = product,
                rawWarehouseName = rawWarehouse?.nameAr ?: "مخزن #${order.rawWarehouseId}",
                finishedWarehouseName = finishedWarehouse?.nameAr ?: "مخزن #${order.finishedWarehouseId}",
                assetName = assetName,
                operatorName = operatorName,
                batch = batch,
                materials = materials,
                checks = checks,
                nonConformances = ncs,
                cost = container.productionService.orderCost(order.id)
            )
        } catch (e: Exception) {
            error = e.message ?: "تعذر تحميل تفاصيل أمر الإنتاج"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بيان الإنتاج التفصيلي") },
        text = {
            when {
                error != null -> FushErrorState(
                    title = "تعذر تحميل تفاصيل أمر الإنتاج",
                    detail = error!!,
                )
                detail == null -> FushLoadingState(
                    title = "جاري تحميل أمر الإنتاج",
                    detail = "يتم تجهيز المواد المصروفة والمخرجات والتكلفة وبيانات الجودة.",
                )
                else -> {
                    val d = detail!!
                    val b = d.batch
                    val actualQty = b?.actualOutputQtyBase ?: 0.0
                    val acceptedQty = b?.acceptedQtyBase ?: 0.0
                    val planAchievement = if (d.order.plannedOutputQtyBase > 0.0 && actualQty > 0.0) {
                        actualQty / d.order.plannedOutputQtyBase * 100.0
                    } else 0.0
                    val acceptanceRate = if (actualQty > 0.0) acceptedQty / actualQty * 100.0 else 0.0
                    val exportDocument = buildProductionOrderExportDocument(d)

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 620.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            ReportExportActions(
                                document = exportDocument,
                                baseName = "FushERP-${d.order.orderNo}",
                                printJobName = "${d.order.orderNo} — بيان الإنتاج"
                            )
                            HorizontalDivider()
                        }
                        item {
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(d.order.orderNo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                            Text(d.product?.nameAr ?: "المنتج غير متاح", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        FushStatusPill(statusAr(d.order.status), productionStatusTone(d.order.status))
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FushMetricCard("المخطط", formatProductionQty(d.order.plannedOutputQtyBase), Modifier.weight(1f), "عبوة")
                                        FushMetricCard("الفعلي", formatProductionQty(actualQty), Modifier.weight(1f), "عبوة", FushStatusTone.Info)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        FushMetricCard("المقبول", formatProductionQty(acceptedQty), Modifier.weight(1f), "عبوة", FushStatusTone.Success)
                                        FushMetricCard("التكلفة", formatMoney(d.cost.totalCostBase), Modifier.weight(1f), "ريال", FushStatusTone.Neutral)
                                    }
                                }
                            }
                        }
                        item {
                            DetailSectionTitle("بيانات أمر الإنتاج")
                            DetailLine("رقم الأمر", d.order.orderNo)
                            DetailLine("المنتج", d.product?.let { "${it.nameAr} (${it.code})" } ?: "—")
                            DetailLine("الحالة", statusAr(d.order.status))
                            DetailLine("تاريخ التخطيط", formatProductionDate(d.order.plannedDate))
                            DetailLine("تاريخ الإنشاء", formatProductionDateTime(d.order.createdAt))
                            d.order.closedAt?.let { DetailLine("تاريخ الإغلاق", formatProductionDateTime(it)) }
                            DetailLine("الوصفة", d.recipe?.let { "${it.code} — إصدار ${it.versionNo}" } ?: "—")
                            DetailLine("الناتج القياسي للوصفة", d.recipe?.let { "${formatProductionQty(it.targetOutputQtyBase)} عبوة" } ?: "—")
                            DetailLine("الإنتاج المخطط", "${formatProductionQty(d.order.plannedOutputQtyBase)} عبوة")
                            DetailLine("مخزن المواد", d.rawWarehouseName)
                            DetailLine("مخزن المنتج النهائي", d.finishedWarehouseName)
                            DetailLine("المعدة الرئيسية", d.assetName ?: "بدون")
                            DetailLine("المشغل", d.operatorName ?: "غير معين")
                            if (d.order.notes.isNotBlank()) DetailLine("ملاحظات الأمر", d.order.notes)
                        }

                        item {
                            HorizontalDivider()
                            DetailSectionTitle("الدفعة والناتج والجودة")
                            if (b == null) {
                                Text("لم يتم إنشاء دفعة لهذا الأمر حتى الآن.")
                            } else {
                                DetailLine("رقم الدفعة", b.batchNo)
                                DetailLine("حالة الدفعة", statusAr(b.status))
                                DetailLine("تاريخ الإنتاج", formatProductionDate(b.manufactureDate))
                                DetailLine("تاريخ الانتهاء", formatProductionDate(b.expiryDate))
                                DetailLine("الناتج الفعلي", "${formatProductionQty(b.actualOutputQtyBase)} عبوة")
                                DetailLine("المقبول", "${formatProductionQty(b.acceptedQtyBase)} عبوة")
                                DetailLine("المرفوض", "${formatProductionQty(b.rejectedQtyBase)} عبوة")
                                DetailLine("التالف", "${formatProductionQty(b.scrapQtyBase)} عبوة")
                                DetailLine("تحقيق خطة الإنتاج", "${formatPercent(planAchievement)}%")
                                DetailLine("نسبة القبول من الناتج", "${formatPercent(acceptanceRate)}%")
                                if (b.notes.isNotBlank()) DetailLine("ملاحظات الدفعة", b.notes)
                            }
                        }

                        item {
                            HorizontalDivider()
                            DetailSectionTitle("المواد المستخدمة والأسعار")
                            Text(
                                "الأسعار أدناه هي تكلفة الصرف الفعلية التاريخية من التشغيلات التي صُرفت لهذا الأمر، وليست سعر الشراء الحالي.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        items(d.materials, key = { "production-material-${it.row.id}" }) { m ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("${m.row.itemName} (${m.itemCode})", style = MaterialTheme.typography.titleSmall)
                                    if (m.stage.isNotBlank()) DetailLine("مرحلة الاستخدام", stageArDetail(m.stage))
                                    DetailLine("الكمية القياسية", "${formatProductionQty(m.row.standardQtyBase)} ${m.row.unitName}")
                                    DetailLine("المحجوز", "${formatProductionQty(m.row.reservedQtyBase)} ${m.row.unitName}")
                                    DetailLine("المصروف فعليًا", "${formatProductionQty(m.row.issuedQtyBase)} ${m.row.unitName}")
                                    val averageUnitCost = if (m.row.issuedQtyBase > 0.0) m.row.issueCostBase / m.row.issuedQtyBase else 0.0
                                    DetailLine(
                                        "متوسط تكلفة الوحدة المصروفة",
                                        if (m.row.issuedQtyBase > 0.0) "${formatMoney(averageUnitCost)} ريال/${m.row.unitName}" else "لم تُصرف بعد"
                                    )
                                    DetailLine("إجمالي تكلفة المادة", "${formatMoney(m.row.issueCostBase)} ريال")
                                    if (m.row.issuedQtyBase > 1e-9) {
                                        OutlinedButton(
                                            onClick = { correctionMaterial = m },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("تصحيح صرف المواد") }
                                    }
                                    if (m.issues.isNotEmpty()) {
                                        HorizontalDivider()
                                        Text("تفاصيل التشغيلات المصروفة", style = MaterialTheme.typography.labelLarge)
                                        m.issues.forEach { issue ->
                                            val correction = issue.issueKind == "CORRECTION_RETURN" || issue.quantityBase < 0.0
                                            Text(
                                                "• ${if (correction) "تصحيح/مرتجع" else "صرف"} — تشغيلة ${issue.lotNo ?: "بدون رقم"}: " +
                                                    "${formatProductionQty(issue.quantityBase)} ${m.row.unitName} × " +
                                                    "${formatMoney(issue.unitCostBase)} = ${formatMoney(issue.totalCostBase)} ريال",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (correction) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            issue.expiryDate?.let {
                                                Text("  الصلاحية: ${formatProductionDate(it)}", style = MaterialTheme.typography.bodySmall)
                                            }
                                            Text("  التاريخ: ${formatProductionDateTime(issue.issueDate)}", style = MaterialTheme.typography.bodySmall)
                                            if (correction && issue.reason.isNotBlank()) {
                                                Text("  سبب التصحيح: ${issue.reason}", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            HorizontalDivider()
                            DetailSectionTitle("ملخص التكلفة الفعلية")
                            DetailLine("تكلفة المواد", "${formatMoney(d.cost.materialCostBase)} ريال")
                            DetailLine("تكلفة العمالة المباشرة", "${formatMoney(d.cost.laborCostBase)} ريال")
                            DetailLine("إجمالي تكلفة أمر الإنتاج", "${formatMoney(d.cost.totalCostBase)} ريال")
                            DetailLine(
                                "تكلفة العبوة المقبولة",
                                if (d.cost.acceptedQtyBase > 0.0) "${formatMoney(d.cost.unitCostBase)} ريال/عبوة"
                                else "تُحسب بعد قبول الدفعة"
                            )
                        }

                        item {
                            HorizontalDivider()
                            DetailSectionTitle("فحوص الجودة")
                            if (d.checks.isEmpty()) FushInlineState("لا توجد فحوص جودة مسجلة.")
                        }

                        items(d.checks, key = { "production-quality-check-${it.id}" }) { check ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(check.checkName, style = MaterialTheme.typography.titleSmall)
                                    DetailLine("المرحلة", stageArDetail(check.stage))
                                    DetailLine("القرار", if (check.decision == "PASS") "ناجح / مقبول" else "فشل / مرفوض")
                                    if (check.measuredValue != null) {
                                        DetailLine("القراءة الفعلية", "${formatProductionQty(check.measuredValue)} ${check.unit}")
                                        DetailLine("حدود القبول", qualityLimitsText(check.minValue, check.maxValue, check.unit))
                                        check.targetValue?.let { DetailLine("القيمة المستهدفة", "${formatProductionQty(it)} ${check.unit}") }
                                        DetailLine("حجم العينة", check.sampleSize.toString())
                                    } else if (check.resultValue.isNotBlank()) DetailLine("النتيجة", check.resultValue)
                                    DetailLine("وقت الفحص", formatProductionDateTime(check.checkedAt))
                                    if (check.notes.isNotBlank()) DetailLine("الملاحظات", check.notes)
                                }
                            }
                        }

                        item {
                            HorizontalDivider()
                            DetailSectionTitle("عدم المطابقة / CAPA")
                            if (d.nonConformances.isEmpty()) FushInlineState("لا توجد حالات عدم مطابقة مسجلة.", tone = FushStatusTone.Success)
                        }

                        items(d.nonConformances, key = { "production-nc-${it.id}" }) { nc ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("${nc.code} • ${nc.status}", style = MaterialTheme.typography.titleSmall)
                                    Text(nc.description)
                                    if (nc.immediateAction.isNotBlank()) DetailLine("الإجراء الفوري", nc.immediateAction)
                                    if (nc.rootCause.isNotBlank()) DetailLine("السبب الجذري", nc.rootCause)
                                    if (nc.correctiveAction.isNotBlank()) DetailLine("الإجراء التصحيحي", nc.correctiveAction)
                                    if (nc.preventiveAction.isNotBlank()) DetailLine("الإجراء الوقائي", nc.preventiveAction)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("إغلاق") } }
    )

    correctionMaterial?.let { material ->
        ProductionIssueCorrectionDialog(
            material = material,
            onDismiss = { correctionMaterial = null },
            onSave = { correctedQty, reason ->
                scope.launch {
                    try {
                        val result = container.productionService.correctMaterialIssue(
                            orderId = orderId,
                            materialId = material.row.id,
                            correctedIssuedQtyBase = correctedQty,
                            reason = reason,
                            createdBy = user.id
                        )
                        val costText = result.correctedBatchUnitCostBase?.let {
                            " • تكلفة العبوة بعد التصحيح ${formatMoney(it)} ريال"
                        } ?: ""
                        val message = if (result.addedQtyBase > 1e-9) {
                            buildString {
                                append("تمت زيادة الصرف ${formatProductionQty(result.addedQtyBase)} ${material.row.unitName}")
                                if (result.finishedGoodsAddedBase > 1e-9) append(" • المنتج النهائي +${formatProductionQty(result.finishedGoodsAddedBase)} عبوة")
                                if (result.linkedBottleLabelsAddedBase > 1e-9) append(" • ملصقات العبوات +${formatProductionQty(result.linkedBottleLabelsAddedBase)}")
                                if (result.linkedPacksAddedBase > 1e-9) append(" • الباكتات +${formatProductionQty(result.linkedPacksAddedBase)}")
                                if (result.linkedPackLabelsAddedBase > 1e-9) append(" • ملصقات الباكت +${formatProductionQty(result.linkedPackLabelsAddedBase)}")
                                append(costText)
                            }
                        } else {
                            "تم إرجاع ${formatProductionQty(result.returnedQtyBase)} ${material.row.unitName} بقيمة ${formatMoney(result.returnedCostBase)} ريال$costText"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        correctionMaterial = null
                        error = null
                        refreshToken++
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "تعذر تصحيح صرف المواد", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun ProductionIssueCorrectionDialog(
    material: ProductionDetailMaterial,
    onDismiss: () -> Unit,
    onSave: (Double, String) -> Unit
) {
    val suggested = material.row.standardQtyBase.coerceIn(0.0, material.row.issuedQtyBase)
    var correctedQtyText by remember(material.row.id, material.row.issuedQtyBase) {
        mutableStateOf(formatProductionQty(if (suggested < material.row.issuedQtyBase) suggested else material.row.issuedQtyBase))
    }
    var reason by remember(material.row.id) { mutableStateOf("") }
    val corrected = correctedQtyText.toDoubleOrNull()
    val returnedQty = if (corrected != null) (material.row.issuedQtyBase - corrected).coerceAtLeast(0.0) else 0.0
    val addedQty = if (corrected != null) (corrected - material.row.issuedQtyBase).coerceAtLeast(0.0) else 0.0
    val isBottlePackaging = material.itemCode.uppercase().startsWith("PK-BOTTLE-")
    val targetPacks = if (corrected != null && corrected >= 0.0 && isBottlePackaging) {
        runCatching { com.fush.erp.domain.ProductionMath.packagingPackCount(corrected, 24) }.getOrNull()
    } else null
    val wholeBottleQtyValid = !isBottlePackaging || corrected == null || runCatching {
        com.fush.erp.domain.ProductionMath.requireWholePieceQuantity(corrected, "عدد العبوات النهائي")
    }.isSuccess
    val valid = corrected != null && corrected >= 0.0 && wholeBottleQtyValid &&
        kotlin.math.abs(corrected - material.row.issuedQtyBase) > 1e-9 && reason.trim().length >= 3

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تصحيح صرف ${material.row.itemName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("المصروف الحالي: ${formatProductionQty(material.row.issuedQtyBase)} ${material.row.unitName}")
                Text("الكمية القياسية: ${formatProductionQty(material.row.standardQtyBase)} ${material.row.unitName}")
                OutlinedTextField(
                    value = correctedQtyText,
                    onValueChange = { correctedQtyText = it },
                    label = { Text("الكمية الصحيحة النهائية (${material.row.unitName})") },
                    singleLine = true
                )
                if (corrected != null && corrected < material.row.issuedQtyBase) {
                    Text(
                        "سيُعاد للمخزون: ${formatProductionQty(returnedQty)} ${material.row.unitName}",
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (corrected != null && corrected > material.row.issuedQtyBase) {
                    Text(
                        "سيُصرف إضافيًا: ${formatProductionQty(addedQty)} ${material.row.unitName}",
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (isBottlePackaging && targetPacks != null) {
                        Text(
                            "تصحيح مترابط: يزيد المنتج النهائي بنفس فرق العبوات، وملصق العبوة 1:1، ويصبح احتياج الباكتات ${formatProductionQty(targetPacks)} باكيت (24 عبوة/باكيت)، وملصق الباكيت بنفس العدد إن كان موجودًا.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("سبب التصحيح") },
                    minLines = 2
                )
                Text(
                    "لن يُحذف الصرف القديم. سيُنشئ النظام حركة تصحيح مستقلة. عند النقص يعيد الفرق للمخزون، وعند الزيادة يصرف الفرق من المخزون ويصحح تكلفة الإنتاج والمحاسبة. زيادة عبوات التغليف تربط تلقائيًا بالمنتج النهائي والملصقات والباكتات.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = { onSave(corrected!!, reason.trim()) }) { Text("ترحيل التصحيح") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun buildProductionOrderExportDocument(d: ProductionOrderDetailState): ReportExportDocument {
    val b = d.batch
    val actualQty = b?.actualOutputQtyBase ?: 0.0
    val acceptedQty = b?.acceptedQtyBase ?: 0.0
    val planAchievement = if (d.order.plannedOutputQtyBase > 0.0 && actualQty > 0.0) {
        actualQty / d.order.plannedOutputQtyBase * 100.0
    } else 0.0
    val acceptanceRate = if (actualQty > 0.0) acceptedQty / actualQty * 100.0 else 0.0

    val summary = mutableListOf(
        "رقم أمر الإنتاج" to d.order.orderNo,
        "المنتج" to (d.product?.let { "${it.nameAr} (${it.code})" } ?: "—"),
        "الحالة" to statusAr(d.order.status),
        "تاريخ التخطيط" to formatProductionDate(d.order.plannedDate),
        "تاريخ الإنشاء" to formatProductionDateTime(d.order.createdAt),
        "الوصفة" to (d.recipe?.let { "${it.code} — إصدار ${it.versionNo}" } ?: "—"),
        "الناتج القياسي للوصفة" to (d.recipe?.let { "${formatProductionQty(it.targetOutputQtyBase)} عبوة" } ?: "—"),
        "الإنتاج المخطط" to "${formatProductionQty(d.order.plannedOutputQtyBase)} عبوة",
        "مخزن المواد الخام" to d.rawWarehouseName,
        "مخزن المنتج النهائي" to d.finishedWarehouseName,
        "المعدة الرئيسية" to (d.assetName ?: "بدون"),
        "المشغل" to (d.operatorName ?: "غير معين")
    )
    d.order.closedAt?.let { summary += "تاريخ الإغلاق" to formatProductionDateTime(it) }
    if (d.order.notes.isNotBlank()) summary += "ملاحظات الأمر" to d.order.notes
    if (b != null) {
        summary += listOf(
            "رقم الدفعة" to b.batchNo,
            "حالة الدفعة" to statusAr(b.status),
            "تاريخ الإنتاج" to formatProductionDate(b.manufactureDate),
            "تاريخ الانتهاء" to formatProductionDate(b.expiryDate),
            "الناتج الفعلي" to "${formatProductionQty(b.actualOutputQtyBase)} عبوة",
            "المقبول" to "${formatProductionQty(b.acceptedQtyBase)} عبوة",
            "المرفوض" to "${formatProductionQty(b.rejectedQtyBase)} عبوة",
            "التالف" to "${formatProductionQty(b.scrapQtyBase)} عبوة",
            "تحقيق خطة الإنتاج" to "${formatPercent(planAchievement)}%",
            "نسبة القبول من الناتج" to "${formatPercent(acceptanceRate)}%"
        )
        if (b.notes.isNotBlank()) summary += "ملاحظات الدفعة" to b.notes
    }
    summary += listOf(
        "تكلفة المواد" to "${formatMoney(d.cost.materialCostBase)} ريال",
        "تكلفة العمالة المباشرة" to "${formatMoney(d.cost.laborCostBase)} ريال",
        "إجمالي تكلفة أمر الإنتاج" to "${formatMoney(d.cost.totalCostBase)} ريال",
        "تكلفة العبوة المقبولة" to if (d.cost.acceptedQtyBase > 0.0) "${formatMoney(d.cost.unitCostBase)} ريال/عبوة" else "تُحسب بعد قبول الدفعة"
    )

    val materials = ReportExportTable(
        title = "المواد المستخدمة والأسعار",
        headers = listOf("المادة", "الكود", "مرحلة الاستخدام", "الوحدة", "الكمية القياسية", "المحجوز", "المصروف فعليًا", "متوسط تكلفة الوحدة", "إجمالي تكلفة المادة"),
        rows = d.materials.map { m ->
            val averageUnitCost = if (m.row.issuedQtyBase > 0.0) m.row.issueCostBase / m.row.issuedQtyBase else 0.0
            listOf(
                m.row.itemName,
                m.itemCode,
                stageArDetail(m.stage),
                m.row.unitName,
                formatProductionQty(m.row.standardQtyBase),
                formatProductionQty(m.row.reservedQtyBase),
                formatProductionQty(m.row.issuedQtyBase),
                if (m.row.issuedQtyBase > 0.0) "${formatMoney(averageUnitCost)} ريال/${m.row.unitName}" else "لم تُصرف بعد",
                "${formatMoney(m.row.issueCostBase)} ريال"
            )
        }
    )

    val issueLots = ReportExportTable(
        title = "تفاصيل التشغيلات المصروفة",
        headers = listOf("المادة", "نوع الحركة", "رقم التشغيلة", "الكمية", "الوحدة", "تكلفة الوحدة", "الإجمالي", "الصلاحية", "التاريخ", "سبب التصحيح"),
        rows = d.materials.flatMap { m ->
            m.issues.map { issue ->
                listOf(
                    m.row.itemName,
                    if (issue.issueKind == "CORRECTION_RETURN" || issue.quantityBase < 0.0) "تصحيح/مرتجع" else "صرف",
                    issue.lotNo ?: "بدون رقم",
                    formatProductionQty(issue.quantityBase),
                    m.row.unitName,
                    "${formatMoney(issue.unitCostBase)} ريال",
                    "${formatMoney(issue.totalCostBase)} ريال",
                    issue.expiryDate?.let { formatProductionDate(it) } ?: "—",
                    formatProductionDateTime(issue.issueDate),
                    issue.reason.ifBlank { "—" }
                )
            }
        }
    )

    val checks = ReportExportTable(
        title = "فحوص الجودة",
        headers = listOf("الفحص", "المرحلة", "القرار", "القراءة", "الوحدة", "حدود القبول", "الهدف", "العينة", "وقت الفحص", "الملاحظات"),
        rows = d.checks.map { check ->
            listOf(
                check.checkName,
                stageArDetail(check.stage),
                if (check.decision == "PASS") "ناجح / مقبول" else "فشل / مرفوض",
                check.measuredValue?.let { formatProductionQty(it) } ?: check.resultValue.ifBlank { "—" },
                check.unit.ifBlank { "—" },
                if (check.measuredValue != null) qualityLimitsText(check.minValue, check.maxValue, check.unit) else "—",
                check.targetValue?.let { formatProductionQty(it) } ?: "—",
                if (check.sampleSize > 0) check.sampleSize.toString() else "—",
                formatProductionDateTime(check.checkedAt),
                check.notes.ifBlank { "—" }
            )
        }
    )

    val ncs = ReportExportTable(
        title = "عدم المطابقة / CAPA",
        headers = listOf("الكود", "الحالة", "الوصف", "الإجراء الفوري", "السبب الجذري", "الإجراء التصحيحي", "الإجراء الوقائي"),
        rows = d.nonConformances.map { nc ->
            listOf(
                nc.code,
                nc.status,
                nc.description,
                nc.immediateAction.ifBlank { "—" },
                nc.rootCause.ifBlank { "—" },
                nc.correctiveAction.ifBlank { "—" },
                nc.preventiveAction.ifBlank { "—" }
            )
        }
    )

    return ReportExportDocument(
        title = "بيان الإنتاج التفصيلي — ${d.order.orderNo}",
        subtitle = "Fush ERP — تقرير تكلفة وتتبع وجودة أمر الإنتاج",
        summary = summary,
        tables = listOf(materials, issueLots, checks, ncs),
        notes = listOf("أسعار المواد هي تكلفة الصرف الفعلية التاريخية للتشغيلات المصروفة لهذا الأمر، وليست سعر الشراء الحالي.")
    )
}

@Composable
private fun DetailSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.35f))
    }
}

private fun stageArDetail(stage: String): String = when (stage) {
    "RECEIVING" -> "الاستلام"
    "PREPARATION" -> "التحضير"
    "MIXING" -> "الخلط"
    "FILLING" -> "التعبئة"
    "FINAL" -> "الفحص النهائي"
    else -> stage
}

private fun formatProductionDate(value: Long): String =
    SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date(value))

private fun formatProductionDateTime(value: Long): String =
    SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US).format(Date(value))

private fun formatPercent(value: Double): String =
    if (kotlin.math.abs(value - value.toLong()) < 0.000001) value.toLong().toString()
    else "%.1f".format(Locale.US, value)

private data class NewRecipeComponentDraft(
    val item: ItemEntity,
    val quantityBase: Double,
    val stage: String
)

@Composable
private fun NewRecipeDialog(
    finishedProducts: List<ItemEntity>,
    componentItems: List<ItemEntity>,
    onDismiss: () -> Unit,
    onSave: (ItemEntity, Double, List<NewRecipeComponentDraft>, String) -> Unit
) {
    var product by remember { mutableStateOf<ItemEntity?>(null) }
    var outputText by remember { mutableStateOf("") }
    var componentItem by remember { mutableStateOf<ItemEntity?>(null) }
    var componentQtyText by remember { mutableStateOf("") }
    var componentStage by remember { mutableStateOf("PREPARATION") }
    var notes by remember { mutableStateOf("") }
    var localMessage by remember { mutableStateOf<String?>(null) }
    val components = remember { mutableStateListOf<NewRecipeComponentDraft>() }

    fun stageAr(stage: String): String = when (stage) {
        "PREPARATION" -> "التحضير"
        "MIXING" -> "الخلط"
        "FILLING" -> "التعبئة"
        else -> stage
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("وصفة جديدة") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 560.dp)) {
                item {
                    if (finishedProducts.isEmpty()) {
                        FushInlineState("لا يوجد منتج نهائي. أضف المنتج أولًا من القائمة ← البيانات ← المواد والأصناف.", tone = FushStatusTone.Danger)
                    } else {
                        SelectionField("المنتج النهائي", product?.nameAr ?: "اختر", finishedProducts, { "${it.nameAr} (${it.code})" }) { product = it }
                    }
                    OutlinedTextField(
                        value = outputText,
                        onValueChange = { outputText = it },
                        label = { Text("الناتج المرجعي المتوقع للدفعة") },
                        supportingText = { Text("رقم مرجعي للمقارنة فقط؛ لا يغيّر كميات المواد. نفس مواد الوصفة قد تعطي ناتجًا فعليًا أكثر أو أقل.") },
                        singleLine = true
                    )
                    Text("أضف مكونات الخلطة واحدًا واحدًا — هذه الكميات ثابتة للدفعة الواحدة", style = MaterialTheme.typography.titleSmall)
                    SelectionField("المكون", componentItem?.nameAr ?: "اختر", componentItems, { "${it.nameAr} (${it.code})" }) { componentItem = it }
                    OutlinedTextField(
                        value = componentQtyText,
                        onValueChange = { componentQtyText = it },
                        label = { Text("كمية المكون") },
                        singleLine = true
                    )
                    StringSelectionField(
                        "المرحلة",
                        stageAr(componentStage),
                        listOf("PREPARATION", "MIXING", "FILLING")
                    ) { componentStage = it }
                    OutlinedButton(
                        onClick = {
                            val item = componentItem
                            val qty = componentQtyText.toDoubleOrNull()
                            when {
                                item == null -> localMessage = "اختر المكون"
                                qty == null || qty <= 0.0 -> localMessage = "أدخل كمية صحيحة للمكون"
                                components.any { it.item.id == item.id } -> localMessage = "هذا المكون مضاف بالفعل"
                                else -> {
                                    components.add(NewRecipeComponentDraft(item, qty, componentStage))
                                    componentItem = null
                                    componentQtyText = ""
                                    localMessage = null
                                }
                            }
                        },
                        enabled = componentItems.isNotEmpty()
                    ) { Text("إضافة المكون") }
                    localMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    HorizontalDivider()
                    Text("المكونات (${components.size})", style = MaterialTheme.typography.titleSmall)
                }
                items(components, key = { it.item.id }) { component ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(component.item.nameAr)
                                Text("${formatProductionQty(component.quantityBase)} ${stageAr(component.stage)}")
                            }
                            TextButton(onClick = { components.remove(component) }) { Text("حذف") }
                        }
                    }
                }
                item {
                    OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات الوصفة") })
                }
            }
        },
        confirmButton = {
            Button(
                enabled = product != null && outputText.toDoubleOrNull()?.let { it > 0 } == true && components.isNotEmpty(),
                onClick = { onSave(product!!, outputText.toDouble(), components.toList(), notes) }
            ) { Text("حفظ الوصفة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun RecipeVersionDialog(
    container: AppContainer,
    recipe: RecipeSummary,
    onDismiss: () -> Unit,
    onSave: (Map<Long, Double>, String) -> Unit
) {
    var components by remember { mutableStateOf<List<RecipeComponentView>>(emptyList()) }
    var quantities by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(recipe.id) {
        components = container.db.recipeDao().componentViews(recipe.id)
        quantities = components.associate { it.itemId to formatProductionQty(it.quantityBase) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إصدار جديد من ${recipe.code} v${recipe.versionNo}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 520.dp)) {
                item { Text("الإصدار السابق لا يتم تعديله. غيّر كميات الإصدار الجديد ثم احفظه.") }
                items(components) { component ->
                    OutlinedTextField(
                        value = quantities[component.itemId] ?: "",
                        onValueChange = { v -> quantities = quantities + (component.itemId to v) },
                        label = { Text("${component.itemName} (${component.unitName})") },
                        supportingText = { Text(component.stage) },
                        singleLine = true
                    )
                }
                item { OutlinedTextField(note, { note = it }, label = { Text("سبب/ملاحظة الإصدار") }) }
            }
        },
        confirmButton = {
            val valid = components.isNotEmpty() && components.all { quantities[it.itemId]?.toDoubleOrNull()?.let { q -> q > 0 } == true }
            Button(enabled = valid, onClick = { onSave(quantities.mapValues { it.value.toDouble() }, note) }) { Text("إنشاء الإصدار") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun NewProductionOrderDialog(
    recipes: List<RecipeSummary>,
    warehouses: List<WarehouseEntity>,
    assets: List<AssetEntity>,
    employees: List<EmployeeEntity>,
    onDismiss: () -> Unit,
    onSave: (RecipeSummary, WarehouseEntity, WarehouseEntity, AssetEntity?, EmployeeEntity?, Double, Double, String) -> Unit
) {
    var recipe by remember { mutableStateOf<RecipeSummary?>(null) }
    var raw by remember { mutableStateOf<WarehouseEntity?>(null) }
    var finished by remember { mutableStateOf<WarehouseEntity?>(null) }
    var asset by remember { mutableStateOf<AssetEntity?>(null) }
    var operator by remember { mutableStateOf<EmployeeEntity?>(null) }
    var outputText by remember { mutableStateOf("360") }
    var laborText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    LaunchedEffect(recipes) { if (recipe == null) recipe = recipes.firstOrNull() }
    LaunchedEffect(warehouses) {
        if (raw == null) raw = warehouses.firstOrNull { it.code == "RM" } ?: warehouses.firstOrNull()
        if (finished == null) finished = warehouses.firstOrNull { it.code == "FG" } ?: warehouses.firstOrNull()
    }
    LaunchedEffect(assets) { if (asset == null) asset = assets.firstOrNull { it.assetType == "FILLING_MACHINE" } ?: assets.firstOrNull() }
    LaunchedEffect(employees) { if (operator == null) operator = employees.firstOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("أمر إنتاج جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionField("الوصفة", recipe?.let { "${it.code} v${it.versionNo}" } ?: "اختر", recipes, { "${it.code} v${it.versionNo}" }) { recipe = it; outputText = formatProductionQty(it.targetOutputQtyBase) }
                SelectionField("مخزن المواد", raw?.nameAr ?: "اختر", warehouses, { it.nameAr }) { raw = it }
                SelectionField("مخزن المنتج", finished?.nameAr ?: "اختر", warehouses, { it.nameAr }) { finished = it }
                SelectionField("المعدة الرئيسية", asset?.nameAr ?: "بدون", assets, { "${it.nameAr} (${it.status})" }) { asset = it }
                SelectionField("موظف الإنتاج / المشغل", operator?.fullNameAr ?: "اختر موظف الإنتاج", employees, { "${it.fullNameAr} — ${it.jobTitle}" }) { operator = it }
                Text("أجور/عمولة هذه الدفعة ستُسجل استحقاقاً على موظف الإنتاج المختار عند ترحيل نتيجة الدفعة. وإذا تم اختيار معدة رئيسية فيجب أن يكون تدريب الموظف وتصريح المعدة ساريين.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    outputText,
                    { outputText = it },
                    label = { Text("الإنتاج المتوقع/المخطط بالعبوة") },
                    supportingText = { Text("تغييره لا يضاعف ولا يقلل مواد الوصفة؛ المواد ثابتة للدفعة، والناتج الفعلي يُسجل بعد التعبئة.") },
                    singleLine = true
                )
                OutlinedTextField(
                    laborText,
                    { laborText = it },
                    label = { Text("عمولة/أجر موظف الإنتاج لهذه الدفعة بالريال") },
                    supportingText = { Text("سيُحمل المبلغ على تكلفة الدفعة ويُسجل في حساب 2200 كمستحق لموظف الإنتاج المختار.") },
                    singleLine = true
                )
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") })
            }
        },
        confirmButton = {
            Button(
                enabled = recipe != null && raw != null && finished != null && operator != null && outputText.toDoubleOrNull()?.let { it > 0 } == true && runCatching { com.fush.erp.domain.ProductionMath.parseDirectLaborCostInput(laborText) }.isSuccess,
                onClick = { onSave(recipe!!, raw!!, finished!!, asset, operator, outputText.toDouble(), com.fush.erp.domain.ProductionMath.parseDirectLaborCostInput(laborText), notes) }
            ) { Text("إنشاء") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun SubmitQualityDialog(
    order: ProductionOrderSummary,
    onDismiss: () -> Unit,
    onSubmit: (Double, Double, String) -> Unit
) {
    var actual by remember { mutableStateOf(formatProductionQty(order.plannedOutputQtyBase)) }
    var scrap by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إرسال الدفعة للجودة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سجل الناتج الفعلي والتالف قبل الإفراج عن المنتج.")
                OutlinedTextField(actual, { actual = it }, label = { Text("الناتج الفعلي بالعبوة") }, singleLine = true)
                OutlinedTextField(scrap, { scrap = it }, label = { Text("التالف") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظة التشغيل") })
            }
        },
        confirmButton = { Button(enabled = actual.toDoubleOrNull()?.let { it > 0 } == true && scrap.toDoubleOrNull()?.let { it >= 0 } == true, onClick = { onSubmit(actual.toDouble(), scrap.toDouble(), notes) }) { Text("تحت الفحص") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun BatchQualityDialog(
    container: AppContainer,
    batch: ProductionBatchEntity,
    user: UserEntity,
    onDismiss: () -> Unit,
    onCompleted: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var checks by remember { mutableStateOf<List<QualityCheckEntity>>(emptyList()) }
    var nonConformances by remember { mutableStateOf<List<NonConformanceEntity>>(emptyList()) }
    var specifications by remember { mutableStateOf<List<QualitySpecificationEntity>>(emptyList()) }
    var productItemId by remember { mutableStateOf<Long?>(null) }
    var selectedSpecId by remember { mutableStateOf<Long?>(null) }
    var sampleReadings by remember { mutableStateOf<List<String>>(emptyList()) }
    var samplesByCheck by remember { mutableStateOf<Map<Long, List<QualityCheckSampleEntity>>>(emptyMap()) }
    var quantitativeNotes by remember { mutableStateOf("") }
    var legacyCheckName by remember { mutableStateOf("فحص وصفي إضافي") }
    var legacyResultValue by remember { mutableStateOf("") }
    var legacyDecision by remember { mutableStateOf("PASS") }
    var legacyNotes by remember { mutableStateOf("") }
    var showLegacy by remember { mutableStateOf(false) }
    var showSpecEditor by remember { mutableStateOf(false) }
    var editingSpec by remember { mutableStateOf<QualitySpecificationEntity?>(null) }
    var showNc by remember { mutableStateOf(false) }
    var closeNc by remember { mutableStateOf<NonConformanceEntity?>(null) }
    var showReject by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            checks = container.db.productionDao().checksForBatch(batch.id)
            samplesByCheck = checks.associate { it.id to container.db.productionDao().samplesForQualityCheck(it.id) }
            nonConformances = container.db.productionDao().nonConformancesForBatch(batch.id)
            val order = container.db.productionDao().orderById(batch.orderId)
            productItemId = order?.productItemId
            specifications = order?.let { container.db.productionDao().qualitySpecificationsForProduct(it.productItemId, "FINAL") }.orEmpty()
            val active = specifications.filter { it.isActive }
            if (selectedSpecId !in active.map { it.id }) selectedSpecId = active.firstOrNull()?.id
        }
    }
    LaunchedEffect(batch.id) { reload() }

    val activeSpecs = specifications.filter { it.isActive }
    val selectedSpec = activeSpecs.firstOrNull { it.id == selectedSpecId }
    LaunchedEffect(selectedSpecId, selectedSpec?.requiredSampleSize) {
        val required = selectedSpec?.requiredSampleSize ?: 0
        sampleReadings = List(required) { "" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("جودة الدفعة ${batch.batchNo}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 580.dp)) {
                item {
                    val passCount = checks.count { it.decision == "PASS" }
                    val failCount = checks.count { it.decision == "FAIL" }
                    val openNcCount = nonConformances.count { it.status != "CLOSED" }
                    FushSectionHeader(
                        title = "قرار جودة الدفعة",
                        subtitle = "راجع نتائج العينات وعدم المطابقة قبل الإفراج عن المنتج النهائي."
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FushMetricCard("PASS", passCount.toString(), Modifier.weight(1f), tone = FushStatusTone.Success)
                        FushMetricCard("FAIL", failCount.toString(), Modifier.weight(1f), tone = if (failCount > 0) FushStatusTone.Danger else FushStatusTone.Neutral)
                        FushMetricCard("NC مفتوحة", openNcCount.toString(), Modifier.weight(1f), tone = if (openNcCount > 0) FushStatusTone.Warning else FushStatusTone.Neutral)
                    }
                    Spacer(Modifier.height(8.dp))
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("الدفعة ${batch.batchNo}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                FushStatusPill(statusAr(batch.status), productionStatusTone(batch.status))
                            }
                            DetailLine("الناتج الفعلي", "${formatProductionQty(batch.actualOutputQtyBase)} عبوة")
                            DetailLine("التالف", "${formatProductionQty(batch.scrapQtyBase)} عبوة")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("الفحص الكمي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (activeSpecs.isEmpty()) {
                        FushInlineState("لا توجد مواصفات كمية فعالة لهذا المنتج. عرّف حدود القبول وحجم العينة قبل الاعتماد.", tone = FushStatusTone.Danger)
                    } else {
                        QualitySpecificationSelectionField(
                            specs = activeSpecs,
                            selectedId = selectedSpecId,
                            onSelect = { selectedSpecId = it }
                        )
                        selectedSpec?.let { spec ->
                            Text("${spec.parameterName} • الحدود: ${qualityLimitsText(spec.minValue, spec.maxValue, spec.unit)}${spec.targetValue?.let { " • الهدف ${formatProductionQty(it)} ${spec.unit}" } ?: ""}")
                            Text("العينة المطلوبة: ${spec.requiredSampleSize}${if (spec.isRequired) " • إلزامي" else " • اختياري"}", style = MaterialTheme.typography.bodySmall)
                        }
                        selectedSpec?.let { spec ->
                            Text("أدخل ${spec.requiredSampleSize} قراءات منفصلة. يجب أن تكون كل قراءة ضمن حدود القبول.", style = MaterialTheme.typography.bodySmall)
                            sampleReadings.forEachIndexed { index, value ->
                                OutlinedTextField(
                                    value = value,
                                    onValueChange = { next ->
                                        sampleReadings = sampleReadings.toMutableList().also { it[index] = next }
                                    },
                                    label = { Text("قراءة العينة ${index + 1}") },
                                    singleLine = true
                                )
                            }
                            val parsed = sampleReadings.mapNotNull { it.toDoubleOrNull()?.takeIf { value -> value.isFinite() } }
                            if (sampleReadings.isNotEmpty() && parsed.size == sampleReadings.size) {
                                val summary = ProductionMath.summarizeQualitySamples(parsed, spec.minValue, spec.maxValue)
                                Text(
                                    "المتوسط ${formatProductionQty(summary.average)} ${spec.unit} • الأدنى ${formatProductionQty(summary.minimum)} • الأعلى ${formatProductionQty(summary.maximum)} • غير المطابق ${summary.failedCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (summary.failedCount == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        OutlinedTextField(quantitativeNotes, { quantitativeNotes = it }, label = { Text("ملاحظات القراءة") })
                        Button(
                            enabled = selectedSpec != null && sampleReadings.isNotEmpty() && sampleReadings.all { it.toDoubleOrNull()?.isFinite() == true },
                            onClick = {
                                val spec = selectedSpec ?: return@Button
                                val values = sampleReadings.map { it.toDouble() }
                                scope.launch {
                                    try {
                                        val id = container.productionService.recordQuantitativeQualityCheck(
                                            batch.id, spec.id, values, quantitativeNotes, user.id
                                        )
                                        val saved = container.db.productionDao().checksForBatch(batch.id).firstOrNull { it.id == id }
                                        val summary = ProductionMath.summarizeQualitySamples(values, spec.minValue, spec.maxValue)
                                        message = if (saved?.decision == "PASS") {
                                            "تم تسجيل ${values.size} قراءات: جميعها ضمن حدود القبول"
                                        } else {
                                            "تم تسجيل ${values.size} قراءات: ${summary.failedCount} قراءة خارج حدود القبول"
                                        }
                                        sampleReadings = List(spec.requiredSampleSize) { "" }; quantitativeNotes = ""; reload()
                                    } catch (e: Exception) { message = e.message }
                                }
                            }
                        ) { Text("تسجيل قراءات العينة") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { editingSpec = null; showSpecEditor = true }) { Text("إضافة مواصفة") }
                        TextButton(onClick = { showLegacy = !showLegacy }) { Text(if (showLegacy) "إخفاء الفحص الوصفي" else "فحص وصفي إضافي") }
                    }
                    if (showLegacy) {
                        OutlinedTextField(legacyCheckName, { legacyCheckName = it }, label = { Text("اسم الفحص الوصفي") }, singleLine = true)
                        OutlinedTextField(legacyResultValue, { legacyResultValue = it }, label = { Text("النتيجة الوصفية") }, singleLine = true)
                        StringSelectionField("القرار الوصفي", legacyDecision, listOf("PASS", "FAIL")) { legacyDecision = it }
                        OutlinedTextField(legacyNotes, { legacyNotes = it }, label = { Text("ملاحظات") })
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    container.productionService.recordQualityCheck(batch.id, "FINAL", legacyCheckName, legacyResultValue, legacyDecision, legacyNotes, user.id)
                                    message = "تم تسجيل الفحص الوصفي"
                                    legacyResultValue = ""; legacyNotes = ""; reload()
                                } catch (e: Exception) { message = e.message }
                            }
                        }, enabled = legacyCheckName.isNotBlank()) { Text("تسجيل الوصفي") }
                    }
                    OutlinedButton(onClick = { showNc = true }) { Text("فتح عدم مطابقة / CAPA") }
                    FushOperationMessage(message, onConsumed = { message = null })
                    HorizontalDivider()
                    Text("مواصفات المنتج (${specifications.size})", style = MaterialTheme.typography.titleSmall)
                }
                items(specifications, key = { "spec-${it.id}" }) { spec ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${spec.parameterName} • ${if (spec.isActive) "فعالة" else "موقوفة"}")
                            Text("${qualityLimitsText(spec.minValue, spec.maxValue, spec.unit)} • عينة ${spec.requiredSampleSize}${if (spec.isRequired) " • إلزامي" else ""}", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { editingSpec = spec; showSpecEditor = true }) { Text("تعديل") }
                                TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            container.productionService.setQualitySpecificationActive(spec.id, !spec.isActive, user.id)
                                            reload()
                                        } catch (e: Exception) { message = e.message }
                                    }
                                }) { Text(if (spec.isActive) "إيقاف" else "تفعيل") }
                            }
                        }
                    }
                }
                item {
                    HorizontalDivider()
                    Text("القراءات والفحوص (${checks.size})", style = MaterialTheme.typography.titleSmall)
                }
                items(checks, key = { "check-${it.id}" }) { check ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${check.checkName}: ${if (check.decision == "PASS") "مقبول" else "مرفوض"}")
                            if (check.measuredValue != null) {
                                val samples = samplesByCheck[check.id].orEmpty()
                                if (samples.isNotEmpty()) {
                                    val values = samples.map { it.measuredValue }
                                    val summary = ProductionMath.summarizeQualitySamples(values, check.minValue, check.maxValue)
                                    Text("المتوسط ${formatProductionQty(summary.average)} ${check.unit} • ${qualityLimitsText(check.minValue, check.maxValue, check.unit)}")
                                    Text("الأدنى ${formatProductionQty(summary.minimum)} • الأعلى ${formatProductionQty(summary.maximum)} • مطابق ${summary.passedCount}/${values.size}", style = MaterialTheme.typography.bodySmall)
                                    Text(values.mapIndexed { index, value -> "${index + 1}) ${formatProductionQty(value)}" }.joinToString("  •  "), style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text("قراءة تاريخية مجمعة ${formatProductionQty(check.measuredValue)} ${check.unit} • ${qualityLimitsText(check.minValue, check.maxValue, check.unit)}")
                                    Text("حجم العينة المسجل تاريخيًا: ${check.sampleSize}${check.targetValue?.let { " • الهدف ${formatProductionQty(it)} ${check.unit}" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                                }
                            } else if (check.resultValue.isNotBlank()) Text(check.resultValue)
                        }
                    }
                }
                item {
                    HorizontalDivider()
                    Text("عدم المطابقة (${nonConformances.size})", style = MaterialTheme.typography.titleSmall)
                }
                items(nonConformances) { nc ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${nc.code} • ${nc.status}")
                            Text(nc.description)
                            if (nc.status != "CLOSED") TextButton(onClick = { closeNc = nc }) { Text("إغلاق CAPA بعد التحقق") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    try {
                        val cost = container.productionService.acceptBatch(batch.id, batch.actualOutputQtyBase, user.id)
                        onCompleted("تم قبول ${batch.batchNo}. تكلفة العبوة الفعلية ${formatMoney(cost.unitCostBase)} ريال")
                    } catch (e: Exception) { message = e.message ?: "تعذر قبول الدفعة" }
                }
            }) { Text("قبول الدفعة") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { showReject = true }) { Text("رفض") }
                TextButton(onClick = onDismiss) { Text("إغلاق") }
            }
        }
    )

    if (showSpecEditor) {
        QualitySpecificationDialog(
            initial = editingSpec,
            onDismiss = { showSpecEditor = false; editingSpec = null },
            onSave = { parameter, unit, minValue, maxValue, targetValue, sampleSize, required, notes ->
                val productId = productItemId
                if (productId == null) {
                    message = "تعذر تحديد المنتج"
                } else scope.launch {
                    try {
                        container.productionService.saveQualitySpecification(
                            existingId = editingSpec?.id,
                            productItemId = productId,
                            stage = "FINAL",
                            parameterName = parameter,
                            unit = unit,
                            minValue = minValue,
                            maxValue = maxValue,
                            targetValue = targetValue,
                            requiredSampleSize = sampleSize,
                            isRequired = required,
                            notes = notes,
                            userId = user.id
                        )
                        message = if (editingSpec == null) "تمت إضافة مواصفة الجودة" else "تم تحديث مواصفة الجودة"
                        showSpecEditor = false; editingSpec = null; reload()
                    } catch (e: Exception) { message = e.message }
                }
            }
        )
    }

    if (showNc) {
        NonConformanceDialog(onDismiss = { showNc = false }) { description, immediate, responsible ->
            scope.launch {
                try {
                    container.productionService.createNonConformance(batch.id, description, immediate, responsible, null, user.id)
                    message = "تم فتح حالة عدم مطابقة"
                    showNc = false; reload()
                } catch (e: Exception) { message = e.message }
            }
        }
    }
    closeNc?.let { nc ->
        CloseCapaDialog(nc, onDismiss = { closeNc = null }) { rootCause, corrective, preventive, verified ->
            scope.launch {
                try {
                    container.productionService.closeNonConformance(nc.id, rootCause, corrective, preventive, verified, user.id)
                    message = "تم إغلاق ${nc.code} بعد التحقق من الفعالية"
                    closeNc = null; reload()
                } catch (e: Exception) { message = e.message }
            }
        }
    }
    if (showReject) {
        RejectBatchDialog(onDismiss = { showReject = false }) { reason ->
            scope.launch {
                try {
                    val cost = container.productionService.rejectBatch(batch.id, reason, user.id)
                    onCompleted("تم رفض ${batch.batchNo} وتحميل ${formatMoney(cost.totalCostBase)} ريال كخسائر إنتاج/جودة")
                } catch (e: Exception) { message = e.message }
            }
        }
    }
}

@Composable
private fun QualitySpecificationDialog(
    initial: QualitySpecificationEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, Double?, Double?, Double?, Int, Boolean, String) -> Unit
) {
    var parameter by remember(initial?.id) { mutableStateOf(initial?.parameterName.orEmpty()) }
    var unit by remember(initial?.id) { mutableStateOf(initial?.unit.orEmpty()) }
    var minText by remember(initial?.id) { mutableStateOf(initial?.minValue?.toString().orEmpty()) }
    var maxText by remember(initial?.id) { mutableStateOf(initial?.maxValue?.toString().orEmpty()) }
    var targetText by remember(initial?.id) { mutableStateOf(initial?.targetValue?.toString().orEmpty()) }
    var sampleText by remember(initial?.id) { mutableStateOf((initial?.requiredSampleSize ?: 1).toString()) }
    var required by remember(initial?.id) { mutableStateOf(initial?.isRequired ?: true) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    val minValue = minText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val maxValue = maxText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val targetValue = targetText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val sample = sampleText.toIntOrNull()
    val numbersValid = (minText.isBlank() || minValue != null) && (maxText.isBlank() || maxValue != null) &&
        (targetText.isBlank() || targetValue != null) && (minValue != null || maxValue != null) &&
        (minValue == null || maxValue == null || minValue <= maxValue) &&
        (targetValue == null || minValue == null || targetValue >= minValue) &&
        (targetValue == null || maxValue == null || targetValue <= maxValue)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة مواصفة جودة كمية" else "تعديل مواصفة الجودة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(parameter, { parameter = it }, label = { Text("اسم المعيار/الخاصية") }, singleLine = true)
                OutlinedTextField(unit, { unit = it }, label = { Text("وحدة القياس") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(minText, { minText = it }, label = { Text("الحد الأدنى") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(maxText, { maxText = it }, label = { Text("الحد الأعلى") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(targetText, { targetText = it }, label = { Text("القيمة المستهدفة - اختياري") }, singleLine = true)
                OutlinedTextField(sampleText, { sampleText = it }, label = { Text("الحد الأدنى لحجم العينة") }, singleLine = true)
                Row {
                    Checkbox(checked = required, onCheckedChange = { required = it })
                    Text("مواصفة إلزامية لقبول الدفعة", modifier = Modifier.padding(top = 12.dp))
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات المواصفة") })
                Text("لن يخمن النظام حدوداً قياسية للمنتج؛ أدخل الحدود المعتمدة لديكم.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = parameter.isNotBlank() && unit.isNotBlank() && numbersValid && sample?.let { it > 0 } == true,
                onClick = { onSave(parameter.trim(), unit.trim(), minValue, maxValue, targetValue, sample!!, required, notes.trim()) }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun qualityLimitsText(minValue: Double?, maxValue: Double?, unit: String): String = when {
    minValue != null && maxValue != null -> "من ${formatProductionQty(minValue)} إلى ${formatProductionQty(maxValue)} $unit"
    minValue != null -> "≥ ${formatProductionQty(minValue)} $unit"
    maxValue != null -> "≤ ${formatProductionQty(maxValue)} $unit"
    else -> "بدون حدود كمية"
}

@Composable
private fun QualitySpecificationSelectionField(
    specs: List<QualitySpecificationEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = specs.firstOrNull { it.id == selectedId }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("معيار الجودة: ${selected?.parameterName ?: "اختر"}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            specs.forEach { spec ->
                DropdownMenuItem(
                    text = { Text("${spec.parameterName} — ${qualityLimitsText(spec.minValue, spec.maxValue, spec.unit)}") },
                    onClick = { onSelect(spec.id); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun NonConformanceDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var description by remember { mutableStateOf("") }
    var immediate by remember { mutableStateOf("") }
    var responsible by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("عدم مطابقة / CAPA") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(description, { description = it }, label = { Text("وصف عدم المطابقة") })
            OutlinedTextField(immediate, { immediate = it }, label = { Text("الإجراء الفوري") })
            OutlinedTextField(responsible, { responsible = it }, label = { Text("المسؤول") })
        } },
        confirmButton = { Button(enabled = description.isNotBlank(), onClick = { onSave(description, immediate, responsible) }) { Text("فتح الحالة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun CloseCapaDialog(
    nc: NonConformanceEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Boolean) -> Unit
) {
    var rootCause by remember { mutableStateOf(nc.rootCause) }
    var corrective by remember { mutableStateOf(nc.correctiveAction) }
    var preventive by remember { mutableStateOf(nc.preventiveAction) }
    var verified by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إغلاق CAPA ${nc.code}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(rootCause, { rootCause = it }, label = { Text("السبب الجذري") })
                OutlinedTextField(corrective, { corrective = it }, label = { Text("الإجراء التصحيحي") })
                OutlinedTextField(preventive, { preventive = it }, label = { Text("الإجراء الوقائي") })
                Row { Checkbox(verified, { verified = it }); Text("تم التحقق من فعالية الإجراء") }
            }
        },
        confirmButton = {
            Button(
                enabled = rootCause.isNotBlank() && corrective.isNotBlank() && preventive.isNotBlank() && verified,
                onClick = { onSave(rootCause, corrective, preventive, verified) }
            ) { Text("إغلاق") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun RejectBatchDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("رفض الدفعة") },
        text = { OutlinedTextField(reason, { reason = it }, label = { Text("سبب الرفض") }) },
        confirmButton = { Button(enabled = reason.isNotBlank(), onClick = { onSave(reason) }) { Text("تأكيد الرفض") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun productionStatusTone(status: String): FushStatusTone = when (status) {
    "CLOSED", "ACCEPTED" -> FushStatusTone.Success
    "QC_HOLD", "UNDER_QC" -> FushStatusTone.Warning
    "REJECTED" -> FushStatusTone.Danger
    "CANCELLED" -> FushStatusTone.Neutral
    "PLANNED" -> FushStatusTone.Neutral
    else -> FushStatusTone.Info
}

private fun productionStageLabel(status: String): String = when (status) {
    "PLANNED" -> "1/7 التخطيط وفحص توفر المواد"
    "MATERIALS_RESERVED" -> "2/7 المواد محجوزة وجاهزة للصرف"
    "MATERIALS_ISSUED" -> "3/7 تم صرف المواد للتشغيل"
    "PREPARATION" -> "4/7 التحضير"
    "MIXING" -> "5/7 الخلط"
    "FILLING" -> "6/7 التعبئة وتسجيل الناتج"
    "QC_HOLD", "UNDER_QC" -> "7/7 فحص الجودة والإفراج"
    "ACCEPTED", "CLOSED" -> "اكتملت الدورة وتم اعتماد الدفعة"
    "REJECTED" -> "تم رفض الدفعة"
    "CANCELLED" -> "تم إلغاء الأمر"
    else -> statusAr(status)
}

private fun statusAr(status: String): String = when (status) {
    "PLANNED" -> "مخطط"
    "MATERIALS_RESERVED" -> "مواد محجوزة"
    "MATERIALS_ISSUED" -> "تم صرف المواد"
    "PREPARATION" -> "قيد التحضير"
    "MIXING" -> "قيد الخلط"
    "FILLING" -> "قيد التعبئة"
    "QC_HOLD" -> "تحت فحص الجودة"
    "ACCEPTED" -> "مقبول"
    "CLOSED" -> "مغلق / مقبول"
    "REJECTED" -> "مرفوض"
    "CANCELLED" -> "ملغي"
    else -> status
}

private fun nextActionAr(status: String): String = when (status) {
    "PLANNED" -> "فحص التوفر وحجز المواد"
    "MATERIALS_RESERVED" -> "صرف المواد حسب FEFO"
    "MATERIALS_ISSUED" -> "بدء التحضير"
    "PREPARATION" -> "الانتقال إلى الخلط"
    "MIXING" -> "الانتقال إلى التعبئة"
    "FILLING" -> "تسجيل الناتج وإرساله للجودة"
    "QC_HOLD" -> "فتح فحص الجودة"
    else -> "عرض"
}

private fun formatProductionQty(value: Double): String = if (kotlin.math.abs(value - value.toLong()) < 0.000001) value.toLong().toString() else "%.3f".format(Locale.US, value)
private fun formatMoney(value: Double): String = "%,.2f".format(Locale.US, value)
