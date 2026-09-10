package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fush.erp.R
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.*
import com.fush.erp.ui.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun AdvancedInventoryScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier, onOpenMasterData: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val balances by container.db.stockDao().observeWarehouseBalances().collectAsState(initial = emptyList())
    val reorderAt = remember { System.currentTimeMillis() }
    val reorder by container.db.advancedInventoryDao().observeReorderAlerts(reorderAt).collectAsState(initial = emptyList())
    val reorderPolicies by container.db.advancedInventoryDao().observeReorderPolicies().collectAsState(initial = emptyList())
    val expiryUntil = remember { System.currentTimeMillis() + 30L * 86_400_000L }
    val expiry by container.db.advancedInventoryDao().observeExpiryAlerts(expiryUntil).collectAsState(initial = emptyList())
    val lots by container.db.advancedInventoryDao().observeLotBalances().collectAsState(initial = emptyList())
    val counts by container.db.advancedInventoryDao().observeCounts().collectAsState(initial = emptyList())
    val transfers by container.db.advancedInventoryDao().observeTransfers().collectAsState(initial = emptyList())
    val movements by container.db.advancedInventoryDao().observeMovements(null).collectAsState(initial = emptyList())
    val warehouses by container.db.warehouseDao().observeAll().collectAsState(initial = emptyList())
    val itemsList by container.db.itemDao().observeAll().collectAsState(initial = emptyList())
    var section by remember { mutableStateOf("الأرصدة") }
    val sectionLabels = listOf(
        "الأرصدة" to R.string.inventory_tab_balances,
        "تنبيهات" to R.string.inventory_tab_alerts,
        "الجرد" to R.string.inventory_tab_counts,
        "التحويلات" to R.string.inventory_tab_transfers,
        "التشغيلات" to R.string.inventory_tab_lots,
        "الحركة" to R.string.inventory_tab_movements,
    )
    var selectedWarehouseId by remember { mutableStateOf<Long?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var inventorySearch by remember { mutableStateOf("") }
    var showOpening by remember { mutableStateOf(false) }
    var showLegacyLotAssignment by remember { mutableStateOf(false) }
    var showStartCount by remember { mutableStateOf(false) }
    var showStartTransfer by remember { mutableStateOf(false) }
    var showReorderPolicies by remember { mutableStateOf(false) }
    var editCountId by remember { mutableStateOf<Long?>(null) }
    var editTransferId by remember { mutableStateOf<Long?>(null) }
    var lotToControl by remember { mutableStateOf<InventoryLotAlertRow?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val stockedLocations = remember(balances) { balances.count { abs(it.quantityBase) > 0.000000001 } }
    val activeWarehouses = remember(warehouses) { warehouses.count { it.isActive } }
    val filteredBalances = remember(balances, selectedWarehouseId, selectedCategory, inventorySearch) {
        val q = inventorySearch.trim().lowercase(Locale.ROOT)
        balances.filter { row ->
            (selectedWarehouseId == null || row.warehouseId == selectedWarehouseId) &&
                (selectedCategory == null || row.category == selectedCategory) &&
                (q.isBlank() || listOf(row.nameAr, row.code, row.warehouseName, itemCategoryLabel(row.category)).any { it.lowercase(Locale.ROOT).contains(q) })
        }
    }
    val groupedBalances = remember(filteredBalances) { filteredBalances.groupBy { it.warehouseId } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item(key = "inventory-overview") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FushSectionHeader(
                    title = stringResource(R.string.inventory_title),
                    subtitle = stringResource(R.string.inventory_subtitle),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FushMetricCard(stringResource(R.string.inventory_stock_locations), stockedLocations.toString(), Modifier.weight(1f), stringResource(R.string.inventory_item_warehouse), FushStatusTone.Info)
                    FushMetricCard(stringResource(R.string.inventory_reorder), reorder.size.toString(), Modifier.weight(1f), stringResource(R.string.inventory_active_alert), if (reorder.isNotEmpty()) FushStatusTone.Danger else FushStatusTone.Success)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FushMetricCard(stringResource(R.string.inventory_expiry_30), expiry.size.toString(), Modifier.weight(1f), stringResource(R.string.inventory_expired_near_lot), if (expiry.isNotEmpty()) FushStatusTone.Warning else FushStatusTone.Success)
                    FushMetricCard(stringResource(R.string.inventory_active_warehouses), activeWarehouses.toString(), Modifier.weight(1f), stringResource(R.string.inventory_warehouse_total, warehouses.size), FushStatusTone.Info)
                }
                Spacer(Modifier.height(4.dp))
                FushSectionHeader(stringResource(R.string.inventory_quick_actions), stringResource(R.string.inventory_quick_actions_detail))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showStartCount = true }, modifier = Modifier.weight(1f), enabled = warehouses.isNotEmpty()) { Text(stringResource(R.string.inventory_start_count)) }
                    Button(onClick = { showStartTransfer = true }, modifier = Modifier.weight(1f), enabled = warehouses.size > 1) { Text(stringResource(R.string.inventory_transfer)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showOpening = true }, modifier = Modifier.weight(1f), enabled = warehouses.isNotEmpty() && itemsList.isNotEmpty()) { Text(stringResource(R.string.inventory_opening_balance)) }
                    OutlinedButton(onClick = onOpenMasterData, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.inventory_items_units)) }
                }
                OutlinedButton(onClick = { showLegacyLotAssignment = true }, modifier = Modifier.fillMaxWidth(), enabled = lots.isNotEmpty()) {
                    Text(stringResource(R.string.inventory_link_legacy))
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(sectionLabels) { (sectionKey, labelRes) ->
                        FilterChip(selected = section == sectionKey, onClick = { section = sectionKey }, label = { Text(stringResource(labelRes)) })
                    }
                }
            }
        }

        if (message != null) {
            item(key = "inventory-message") { FushOperationMessage(message, onConsumed = { message = null }) }
        }

        when (section) {
            "تنبيهات" -> inventoryAlertItems(reorder, expiry, onConfigure = { showReorderPolicies = true })
            "الجرد" -> inventoryCountItems(counts, onEdit = { editCountId = it })
            "التحويلات" -> inventoryTransferItems(transfers, onOpen = { editTransferId = it })
            "التشغيلات" -> inventoryLotItems(lots, onControl = { lotToControl = it })
            "الحركة" -> inventoryMovementItems(movements)
            else -> inventoryBalanceItems(
                rows = balances,
                filtered = filteredBalances,
                grouped = groupedBalances,
                lots = lots,
                warehouses = warehouses,
                selectedWarehouseId = selectedWarehouseId,
                selectedCategory = selectedCategory,
                search = inventorySearch,
                onWarehouseSelected = { selectedWarehouseId = it },
                onCategorySelected = { selectedCategory = it },
                onSearchChange = { inventorySearch = it },
            )
        }
    }

    if (showReorderPolicies) {
        WarehouseReorderPolicyDialog(
            policies = reorderPolicies,
            warehouses = warehouses,
            itemsList = itemsList,
            onDismiss = { showReorderPolicies = false },
            onSave = { warehouse, item, level ->
                scope.launch {
                    try {
                        container.advancedInventoryService.setWarehouseReorderPolicy(warehouse.id, item.id, level, user.id)
                        message = "تم حفظ حد إعادة الطلب لـ ${item.nameAr} في ${warehouse.nameAr}"
                        showReorderPolicies = false
                    } catch (e: Exception) { message = e.message ?: "تعذر حفظ سياسة إعادة الطلب" }
                }
            },
            onDelete = { policy ->
                scope.launch {
                    try {
                        container.advancedInventoryService.deleteWarehouseReorderPolicy(policy.id, user.id)
                        message = "تم حذف سياسة إعادة الطلب لـ ${policy.itemName} من ${policy.warehouseName}"
                        showReorderPolicies = false
                    } catch (e: Exception) { message = e.message ?: "تعذر حذف سياسة إعادة الطلب" }
                }
            }
        )
    }

    if (showOpening) {
        AdvancedOpeningStockDialog(itemsList, warehouses, onDismiss = { showOpening = false }) { item, warehouse, qty, cost, note ->
            scope.launch {
                try {
                    val entryId = container.inventoryService.postOpeningStock(warehouse.id, item.id, qty, cost, user.id, note)
                    message = "تم ترحيل الرصيد الافتتاحي والقيد رقم $entryId"
                    showOpening = false
                } catch (e: Exception) { message = e.message ?: "تعذر إدخال الرصيد الافتتاحي" }
            }
        }
    }

    if (showLegacyLotAssignment) {
        LegacyLotAssignmentDialog(
            lots = lots,
            items = itemsList,
            onDismiss = { showLegacyLotAssignment = false },
            onSave = { source, item, qty, targetLot, targetExpiry, note ->
                scope.launch {
                    try {
                        container.advancedInventoryService.assignLegacyLotAndExpiry(
                            warehouseId = source.warehouseId,
                            itemId = source.itemId,
                            sourceLotNo = source.lotNo,
                            sourceExpiryDate = source.expiryDate,
                            quantityBase = qty,
                            targetLotNo = targetLot,
                            targetExpiryDate = targetExpiry,
                            changedBy = user.id,
                            reason = note
                        )
                        message = "تم ربط رصيد ${item.nameAr} بالتشغيلة/الصلاحية دون تغيير كمية أو قيمة المخزون."
                        showLegacyLotAssignment = false
                        section = "التشغيلات"
                    } catch (e: Exception) { message = e.message ?: "تعذر ربط الرصيد القديم" }
                }
            }
        )
    }

    if (showStartTransfer) {
        StartWarehouseTransferDialog(warehouses, onDismiss = { showStartTransfer = false }) { from, to, date, note ->
            scope.launch {
                try {
                    val id = container.advancedInventoryService.startWarehouseTransfer(from.id, to.id, date, user.id, note)
                    message = "تم إنشاء تحويل مخزني مسودة رقم $id. أضف الأصناف ثم رحّله."
                    showStartTransfer = false
                    editTransferId = id
                    section = "التحويلات"
                } catch (e: Exception) { message = e.message ?: "تعذر إنشاء التحويل المخزني" }
            }
        }
    }

    if (showStartCount) {
        StartInventoryCountDialog(warehouses, onDismiss = { showStartCount = false }) { warehouse, note ->
            scope.launch {
                try {
                    val id = container.advancedInventoryService.startCount(warehouse.id, user.id, note)
                    message = "تم إنشاء محضر الجرد رقم $id. أدخل الكميات الفعلية ثم رحّله."
                    showStartCount = false
                    editCountId = id
                    section = "الجرد"
                } catch (e: Exception) { message = e.message ?: "تعذر بدء الجرد" }
            }
        }
    }

    editCountId?.let { id ->
        InventoryCountDialog(container, user, id, itemsList, onDismiss = { editCountId = null }) { text ->
            message = text
            editCountId = null
        }
    }

    editTransferId?.let { id ->
        WarehouseTransferDialog(container, user, id, itemsList, warehouses, onDismiss = { editTransferId = null }) { text ->
            message = text
            editTransferId = null
            section = "التحويلات"
        }
    }

    lotToControl?.let { lot ->
        LotControlDialog(lot, onDismiss = { lotToControl = null }) { status, reason ->
            scope.launch {
                try {
                    container.advancedInventoryService.setLotStatus(lot.warehouseId, lot.itemId, lot.lotNo, lot.expiryDate, status, reason, user.id)
                    message = "تم تحديث حالة التشغيلة إلى ${statusAr(status)}"
                    lotToControl = null
                } catch (e: Exception) { message = e.message ?: "تعذر تحديث حالة التشغيلة" }
            }
        }
    }
}

private fun LazyListScope.inventoryBalanceItems(
    rows: List<WarehouseStockBalanceRow>,
    filtered: List<WarehouseStockBalanceRow>,
    grouped: Map<Long, List<WarehouseStockBalanceRow>>,
    lots: List<InventoryLotAlertRow>,
    warehouses: List<WarehouseEntity>,
    selectedWarehouseId: Long?,
    selectedCategory: String?,
    search: String,
    onWarehouseSelected: (Long?) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onSearchChange: (String) -> Unit,
) {
    val now = System.currentTimeMillis()
    item(key = "inventory-balances-filters") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FushSectionHeader(stringResource(R.string.inventory_current_balances), stringResource(R.string.inventory_current_balances_detail))
            OutlinedTextField(
                value = search,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.inventory_search)) },
                placeholder = { Text(stringResource(R.string.inventory_search_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                supportingText = { Text(stringResource(R.string.inventory_showing, filtered.size, rows.size)) },
            )
            Text(stringResource(R.string.inventory_warehouse), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item { FilterChip(selected = selectedWarehouseId == null, onClick = { onWarehouseSelected(null) }, label = { Text(stringResource(R.string.inventory_all_warehouses)) }) }
                items(warehouses.filter { it.isActive }) { wh ->
                    FilterChip(selected = selectedWarehouseId == wh.id, onClick = { onWarehouseSelected(wh.id) }, label = { Text(warehouseShortName(wh)) })
                }
            }
            Text(stringResource(R.string.inventory_item_type), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item { FilterChip(selected = selectedCategory == null, onClick = { onCategorySelected(null) }, label = { Text(stringResource(R.string.inventory_all)) }) }
                item { FilterChip(selected = selectedCategory == "FINISHED_GOOD", onClick = { onCategorySelected("FINISHED_GOOD") }, label = { Text(stringResource(R.string.inventory_finished_good)) }) }
                item { FilterChip(selected = selectedCategory == "RAW_MATERIAL", onClick = { onCategorySelected("RAW_MATERIAL") }, label = { Text(stringResource(R.string.inventory_raw_material)) }) }
                item { FilterChip(selected = selectedCategory == "PACKAGING", onClick = { onCategorySelected("PACKAGING") }, label = { Text(stringResource(R.string.inventory_packaging)) }) }
            }
        }
    }

    if (filtered.isEmpty()) {
        item(key = "inventory-balances-empty") {
            FushEmptyState(
                title = stringResource(R.string.inventory_no_filtered_balance),
                detail = stringResource(R.string.inventory_no_filtered_balance_detail),
            )
        }
    }

    warehouses.filter { wh -> grouped.containsKey(wh.id) }.forEach { wh ->
        item(key = "warehouse-header-${wh.id}") {
            FushSectionHeader(warehouseDisplayName(wh), wh.location.takeIf { it.isNotBlank() })
        }
        items(grouped[wh.id].orEmpty(), key = { "${it.warehouseId}-${it.itemId}" }) { row ->
            val itemLots = lots.filter { it.warehouseId == row.warehouseId && it.itemId == row.itemId && it.quantityBase > 0.000000001 }
            val sellable = itemLots.filter { (it.expiryDate == null || it.expiryDate >= now) && it.controlStatus == "ACCEPTED" }.sumOf { it.quantityBase }
            val expired = itemLots.filter { it.expiryDate != null && it.expiryDate < now }.sumOf { it.quantityBase }
            val controlled = itemLots.filter { it.controlStatus != "ACCEPTED" }.sumOf { it.quantityBase }
            ElevatedCard(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.nameAr, style = MaterialTheme.typography.titleMedium)
                            Text("${row.code} • ${itemCategoryLabel(row.category)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FushStatusPill("${fmtQty(row.quantityBase)} ${row.baseUnitName}", FushStatusTone.Info)
                    }
                    if (itemLots.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FushStatusPill(stringResource(R.string.inventory_available_qty, fmtQty(sellable)), FushStatusTone.Success)
                            if (expired > 0.000000001) FushStatusPill(stringResource(R.string.inventory_expired_qty, fmtQty(expired)), FushStatusTone.Danger)
                            if (controlled > 0.000000001) FushStatusPill(stringResource(R.string.inventory_controlled_qty, fmtQty(controlled)), FushStatusTone.Warning)
                        }
                    }
                }
            }
        }
    }
}

private fun warehouseShortName(warehouse: WarehouseEntity): String = when (warehouse.code.uppercase(Locale.ROOT)) {
    "FG" -> "النهائي"
    "RM" -> "المواد الخام"
    "FG-QC" -> "حجر النهائي"
    "RM-QC" -> "حجر الخام"
    "RET" -> "المرتجعات"
    else -> warehouse.nameAr
}

private fun warehouseDisplayName(warehouse: WarehouseEntity): String = when (warehouse.code.uppercase(Locale.ROOT)) {
    "FG" -> "مخزن المنتج النهائي"
    "RM" -> "مخزن المواد الخام والتغليف"
    "FG-QC" -> "حجر المنتج النهائي"
    "RM-QC" -> "حجر المواد الخام"
    "RET" -> "مخزن المرتجعات"
    else -> warehouse.nameAr
}

private fun itemCategoryLabel(category: String): String = when (category) {
    "FINISHED_GOOD" -> "منتج نهائي"
    "RAW_MATERIAL" -> "مواد خام"
    "PACKAGING" -> "مواد تغليف"
    else -> "أخرى"
}

private fun LazyListScope.inventoryAlertItems(
    reorder: List<InventoryAlertRow>,
    expiry: List<InventoryLotAlertRow>,
    onConfigure: () -> Unit,
) {
    val now = System.currentTimeMillis()
    item(key = "inventory-alerts-summary") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FushSectionHeader(stringResource(R.string.inventory_alerts_title), stringResource(R.string.inventory_alerts_detail))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(stringResource(R.string.inventory_reorder), reorder.size.toString(), Modifier.weight(1f), stringResource(R.string.inventory_item_warehouse), if (reorder.isNotEmpty()) FushStatusTone.Danger else FushStatusTone.Success)
                FushMetricCard(stringResource(R.string.inventory_expiry_30), expiry.size.toString(), Modifier.weight(1f), stringResource(R.string.inventory_lot), if (expiry.isNotEmpty()) FushStatusTone.Warning else FushStatusTone.Success)
            }
            OutlinedButton(onClick = onConfigure, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.inventory_reorder_setup)) }
        }
    }
    item(key = "inventory-alerts-reorder-header") { FushSectionHeader(stringResource(R.string.inventory_reorder), stringResource(R.string.inventory_reorder_detail)) }
    if (reorder.isEmpty()) {
        item(key = "inventory-alerts-reorder-empty") { FushEmptyState(stringResource(R.string.inventory_no_reorder_alerts), stringResource(R.string.inventory_no_reorder_alerts_detail)) }
    }
    items(reorder, key = { "reorder-${it.warehouseId}-${it.itemId}" }) { row ->
        val tone = if (row.quantityBase <= 0.000000001) FushStatusTone.Danger else FushStatusTone.Warning
        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.nameAr, style = MaterialTheme.typography.titleMedium)
                        Text("${row.warehouseName} (${row.warehouseCode}) • ${row.code}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FushStatusPill(stringResource(R.string.inventory_available_qty, fmtQty(row.quantityBase)), tone)
                }
                Text(stringResource(R.string.inventory_reorder_level, fmtQty(row.reorderLevel), row.baseUnitName), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    item(key = "inventory-alerts-expiry-header") { FushSectionHeader(stringResource(R.string.inventory_expiry), stringResource(R.string.inventory_expiry_detail)) }
    if (expiry.isEmpty()) {
        item(key = "inventory-alerts-expiry-empty") { FushEmptyState(stringResource(R.string.inventory_no_expiry_alerts), stringResource(R.string.inventory_no_expiry_alerts_detail)) }
    }
    items(expiry, key = { "expiry-${it.warehouseId}-${it.itemId}-${it.lotNo}-${it.expiryDate}" }) { row ->
        val expired = row.expiryDate?.let { it < now } == true
        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.nameAr, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.inventory_lot_label, row.warehouseName, row.lotNo ?: stringResource(R.string.inventory_no_lot_number)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FushStatusPill(if (expired) stringResource(R.string.inventory_expired) else stringResource(R.string.inventory_near_expiry), if (expired) FushStatusTone.Danger else FushStatusTone.Warning)
                }
                Text(stringResource(R.string.inventory_expiry_balance, row.expiryDate?.let(::fmtDate) ?: stringResource(R.string.inventory_no_date), fmtQty(row.quantityBase)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WarehouseReorderPolicyDialog(
    policies: List<WarehouseReorderPolicyView>,
    warehouses: List<WarehouseEntity>,
    itemsList: List<ItemEntity>,
    onDismiss: () -> Unit,
    onSave: (WarehouseEntity, ItemEntity, Double) -> Unit,
    onDelete: (WarehouseReorderPolicyView) -> Unit
) {
    var selectedWarehouse by remember { mutableStateOf<WarehouseEntity?>(null) }
    var selectedItem by remember { mutableStateOf<ItemEntity?>(null) }
    var levelText by remember { mutableStateOf("") }
    var warehouseExpanded by remember { mutableStateOf(false) }
    var itemExpanded by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    fun loadPolicy(policy: WarehouseReorderPolicyView) {
        selectedWarehouse = warehouses.firstOrNull { it.id == policy.warehouseId }
        selectedItem = itemsList.firstOrNull { it.id == policy.itemId }
        levelText = fmtQty(policy.reorderLevel)
        localError = null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("حدود إعادة الطلب حسب المخزن", style = MaterialTheme.typography.titleLarge)
                Text("اختر المخزن التشغيلي الفعلي للصنف. مخازن الحجر والمرتجعات لا تُنشأ لها سياسة تلقائياً.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))

                Box {
                    OutlinedButton(onClick = { warehouseExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedWarehouse?.let { "${it.nameAr} (${it.code})" } ?: "اختر المخزن")
                    }
                    DropdownMenu(expanded = warehouseExpanded, onDismissRequest = { warehouseExpanded = false }) {
                        warehouses.filter { it.isActive }.forEach { warehouse ->
                            DropdownMenuItem(
                                text = { Text("${warehouse.nameAr} (${warehouse.code})") },
                                onClick = { selectedWarehouse = warehouse; warehouseExpanded = false; localError = null }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(onClick = { itemExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedItem?.let { "${it.nameAr} (${it.code})" } ?: "اختر الصنف")
                    }
                    DropdownMenu(expanded = itemExpanded, onDismissRequest = { itemExpanded = false }) {
                        itemsList.filter { it.isActive }.forEach { item ->
                            DropdownMenuItem(
                                text = { Text("${item.nameAr} (${item.code})") },
                                onClick = {
                                    selectedItem = item
                                    itemExpanded = false
                                    val existing = policies.firstOrNull { it.warehouseId == selectedWarehouse?.id && it.itemId == item.id }
                                    levelText = existing?.let { fmtQty(it.reorderLevel) } ?: if (item.reorderLevel > 0) fmtQty(item.reorderLevel) else ""
                                    localError = null
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = levelText,
                    onValueChange = { levelText = it; localError = null },
                    label = { Text("حد إعادة الطلب بالوحدة الأساسية") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                localError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("إغلاق") }
                    Button(
                        onClick = {
                            val warehouse = selectedWarehouse
                            val item = selectedItem
                            val level = levelText.trim().toDoubleOrNull()
                            when {
                                warehouse == null -> localError = "اختر المخزن"
                                item == null -> localError = "اختر الصنف"
                                level == null || !level.isFinite() || level < 0 -> localError = "أدخل حداً صحيحاً يساوي صفراً أو أكبر"
                                else -> onSave(warehouse, item, level)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("حفظ") }
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                Text("السياسات الحالية", style = MaterialTheme.typography.titleMedium)
                if (policies.isEmpty()) {
                    FushInlineState("لا توجد سياسات بعد. الحدود القديمة تُرحّل تلقائياً للمخزن التشغيلي عند الترقية إذا كانت أكبر من صفر.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(policies, key = { it.id }) { policy ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(policy.itemName, style = MaterialTheme.typography.titleSmall)
                                    Text("${policy.warehouseName} (${policy.warehouseCode}) • ${fmtQty(policy.reorderLevel)} ${policy.baseUnitName}", style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = { loadPolicy(policy) }) { Text("تعديل") }
                                        TextButton(onClick = { onDelete(policy) }) { Text("حذف") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.inventoryCountItems(rows: List<InventoryCountSummaryRow>, onEdit: (Long) -> Unit) {
    item(key = "inventory-counts-header") { FushSectionHeader("محاضر الجرد", "الجرد المسودّة والفروق ونتيجة الترحيل") }
    if (rows.isEmpty()) item(key = "inventory-counts-empty") { FushEmptyState("لا توجد محاضر جرد", "ابدأ أول جرد مخزني لتظهر المحاضر والفروقات هنا.") }
    items(rows, key = { "count-${it.id}" }) { row ->
        val draft = row.status == "DRAFT"
        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.countNo, style = MaterialTheme.typography.titleMedium)
                        Text("${row.warehouseName} • ${fmtDate(row.countDate)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FushStatusPill(statusAr(row.status), if (draft) FushStatusTone.Warning else FushStatusTone.Success)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FushStatusPill("${row.lineCount} سطر", FushStatusTone.Info)
                    FushStatusPill("${row.varianceLines} بفروق", if (row.varianceLines > 0) FushStatusTone.Warning else FushStatusTone.Success)
                }
                Text("قيمة الفرق: ${fmtMoney(row.varianceValueBase)}", style = MaterialTheme.typography.bodyMedium)
                if (draft) Button(onClick = { onEdit(row.id) }, modifier = Modifier.fillMaxWidth()) { Text("إدخال الكميات / الترحيل") }
            }
        }
    }
}

private fun LazyListScope.inventoryLotItems(rows: List<InventoryLotAlertRow>, onControl: (InventoryLotAlertRow) -> Unit) {
    item(key = "inventory-lots-header") { FushSectionHeader("التشغيلات والحجر", "المحجور أو الموقوف لا يدخل في البيع أو صرف الإنتاج") }
    if (rows.isEmpty()) item(key = "inventory-lots-empty") { FushEmptyState("لا توجد أرصدة تشغيلات", "أرصدة التشغيلات المتاحة والمحجوزة ستظهر هنا عند توفرها.") }
    items(rows, key = { "lot-${it.warehouseId}-${it.itemId}-${it.lotNo}-${it.expiryDate}" }) { row ->
        val tone = when (row.controlStatus) {
            "ACCEPTED" -> FushStatusTone.Success
            "QUARANTINED", "HOLD" -> FushStatusTone.Warning
            else -> FushStatusTone.Danger
        }
        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.nameAr, style = MaterialTheme.typography.titleMedium)
                        Text("${row.warehouseName} • ${row.code}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FushStatusPill(statusAr(row.controlStatus), tone)
                }
                Text("التشغيلة: ${row.lotNo ?: "بدون رقم"} • الصلاحية: ${row.expiryDate?.let(::fmtDate) ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                Text("الرصيد ${fmtQty(row.quantityBase)} • القيمة ${fmtMoney(row.inventoryValueBase)}", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { onControl(row) }, modifier = Modifier.fillMaxWidth()) { Text("تغيير حالة التشغيلة") }
            }
        }
    }
}

private fun LazyListScope.inventoryMovementItems(rows: List<InventoryMovementRow>) {
    item(key = "inventory-movements-header") { FushSectionHeader("حركة المخزون", "آخر ${rows.size} حركة مخزون مسجلة") }
    if (rows.isEmpty()) item(key = "inventory-movements-empty") { FushEmptyState("لا توجد حركات مخزون", "ستظهر هنا حركات الإدخال والإخراج والتحويل بعد تنفيذها.") }
    items(rows, key = { "movement-${it.id}" }) { row ->
        val inbound = row.quantityBase > 0
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.small) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(row.itemName, style = MaterialTheme.typography.titleSmall)
                    Text("${fmtDate(row.movementDate)} • ${row.warehouseName} • ${movementAr(row.movementType)} • ${row.lotNo ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FushStatusPill((if (inbound) "+" else "") + fmtQty(row.quantityBase), if (inbound) FushStatusTone.Success else FushStatusTone.Warning)
            }
        }
    }
}

private fun LazyListScope.inventoryTransferItems(rows: List<WarehouseTransferSummaryRow>, onOpen: (Long) -> Unit) {
    item(key = "inventory-transfers-header") {
        FushSectionHeader("التحويلات بين المستودعات", "التحويل يحافظ على التشغيلة والصلاحية والتكلفة ولا يغيّر إجمالي قيمة المخزون")
    }
    if (rows.isEmpty()) item(key = "inventory-transfers-empty") { FushEmptyState("لا توجد تحويلات مخزنية", "أنشئ تحويلًا بين المخازن لتظهر مستندات التحويل هنا.") }
    items(rows, key = { "transfer-${it.id}" }) { row ->
        val draft = row.status == "DRAFT"
        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(row.transferNo, style = MaterialTheme.typography.titleMedium)
                    FushStatusPill(statusAr(row.status), if (draft) FushStatusTone.Warning else FushStatusTone.Success)
                }
                Text("${row.fromWarehouseName} ← ${row.toWarehouseName} • ${fmtDate(row.transferDate)}", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FushStatusPill("${row.lineCount} سطر", FushStatusTone.Info)
                    FushStatusPill("كمية ${fmtQty(row.totalQtyBase)}", FushStatusTone.Neutral)
                }
                Text("القيمة المنقولة: ${fmtMoney(row.totalValueBase)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { onOpen(row.id) }, modifier = Modifier.fillMaxWidth()) { Text(if (draft) "فتح وإكمال التحويل" else "عرض التفاصيل") }
            }
        }
    }
}

@Composable
private fun StartWarehouseTransferDialog(
    warehouses: List<WarehouseEntity>,
    onDismiss: () -> Unit,
    onStart: (WarehouseEntity, WarehouseEntity, Long, String) -> Unit
) {
    var from by remember { mutableStateOf<WarehouseEntity?>(null) }
    var to by remember { mutableStateOf<WarehouseEntity?>(null) }
    var dateText by remember { mutableStateOf(fmtDate(System.currentTimeMillis())) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(warehouses) {
        if (from == null) from = warehouses.firstOrNull { it.code == "RM" } ?: warehouses.firstOrNull()
        if (to == null) to = warehouses.firstOrNull { it.id != from?.id }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحويل مخزني جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("أنشئ التحويل كمسودة، ثم أضف الأصناف والتشغيلات قبل الترحيل النهائي.")
                SelectionField("من مخزن", from?.nameAr ?: "اختر", warehouses, { it.nameAr }) {
                    from = it
                    if (to?.id == it.id) to = warehouses.firstOrNull { w -> w.id != it.id }
                }
                SelectionField("إلى مخزن", to?.nameAr ?: "اختر", warehouses.filter { it.id != from?.id }, { it.nameAr }) { to = it }
                FushDateField(dateText, { dateText = it }, "تاريخ التحويل", modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = from != null && to != null && from?.id != to?.id, onClick = {
                val date = parseInventoryDate(dateText)
                if (date == null) error = "أدخل التاريخ بصيغة yyyy-MM-dd"
                else onStart(from!!, to!!, date, note)
            }) { Text("إنشاء مسودة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun WarehouseTransferDialog(
    container: AppContainer,
    user: UserEntity,
    transferId: Long,
    itemsList: List<ItemEntity>,
    warehouses: List<WarehouseEntity>,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var transfer by remember { mutableStateOf<WarehouseTransferEntity?>(null) }
    var lines by remember { mutableStateOf(emptyList<WarehouseTransferLineView>()) }
    var refresh by remember { mutableIntStateOf(0) }
    var showAddLine by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    var cancelReason by remember { mutableStateOf("") }
    var showReverse by remember { mutableStateOf(false) }
    var reversalReason by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(transferId, refresh) {
        transfer = container.db.advancedInventoryDao().transferById(transferId)
        lines = container.db.advancedInventoryDao().transferLineViews(transferId)
    }
    val row = transfer
    val fromWarehouse = warehouses.firstOrNull { it.id == row?.fromWarehouseId }
    val toWarehouse = warehouses.firstOrNull { it.id == row?.toWarehouseId }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("تحويل مخزني ${row?.transferNo ?: transferId}", style = MaterialTheme.typography.headlineSmall)
                if (row != null) {
                    Text("${fromWarehouse?.nameAr ?: row.fromWarehouseId} ← ${toWarehouse?.nameAr ?: row.toWarehouseId} • ${fmtDate(row.transferDate)} • ${statusAr(row.status)}")
                    if (row.notes.isNotBlank()) Text("ملاحظة: ${row.notes}", style = MaterialTheme.typography.bodySmall)
                    if (row.status == "POSTED") Text("تم الترحيل النهائي؛ لا يمكن تعديل الأصناف بعد الترحيل. يمكن تنفيذ عكس كامل إذا كانت الكمية الأصلية ما زالت متاحة في مخزن الوجهة.", color = MaterialTheme.colorScheme.primary)
                    if (row.status == "CANCELLED") Text("ملغي: ${row.cancelReason}", color = MaterialTheme.colorScheme.error)
                    if (row.status == "REVERSED") {
                        Text("تم عكس التحويل بتاريخ ${row.reversedAt?.let(::fmtDate) ?: "—"}", color = MaterialTheme.colorScheme.tertiary)
                        if (row.reversalReason.isNotBlank()) Text("سبب العكس: ${row.reversalReason}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (row?.status == "DRAFT") {
                    Button(onClick = { showAddLine = true }, modifier = Modifier.fillMaxWidth()) { Text("إضافة صنف / تشغيلة") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (lines.isEmpty()) item { FushInlineState("لا توجد أصناف في التحويل.") }
                    items(lines, key = { it.id }) { line ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${line.itemCode} — ${line.itemName}", style = MaterialTheme.typography.titleMedium)
                                Text("الكمية: ${fmtQty(line.quantityBase)} • التشغيلة: ${line.lotNo ?: "بدون رقم"} • الصلاحية: ${line.expiryDate?.let(::fmtDate) ?: "—"}")
                                if (row?.status == "POSTED") Text("تكلفة الوحدة المنقولة: ${fmtMoney(line.unitCostBase)} • القيمة: ${fmtMoney(line.quantityBase * line.unitCostBase)}")
                                if (row?.status == "DRAFT") {
                                    TextButton(onClick = {
                                        scope.launch {
                                            try {
                                                container.advancedInventoryService.removeWarehouseTransferLine(line.id, user.id)
                                                refresh++
                                                error = null
                                            } catch (e: Exception) { error = e.message ?: "تعذر حذف السطر" }
                                        }
                                    }) { Text("حذف السطر") }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("إغلاق") }
                    if (row?.status == "POSTED") {
                        TextButton(onClick = { showReverse = true }, enabled = !saving) { Text("عكس التحويل") }
                        Spacer(Modifier.width(6.dp))
                    }
                    if (row?.status == "DRAFT") {
                        TextButton(onClick = { showCancel = true }, enabled = !saving) { Text("إلغاء التحويل") }
                        Spacer(Modifier.width(6.dp))
                        Button(enabled = !saving && lines.isNotEmpty(), onClick = {
                            scope.launch {
                                saving = true
                                try {
                                    container.advancedInventoryService.postWarehouseTransfer(transferId, user.id)
                                    onDone("تم ترحيل التحويل ${row.transferNo}: خرجت الكميات من ${fromWarehouse?.nameAr ?: "المصدر"} ودخلت إلى ${toWarehouse?.nameAr ?: "الوجهة"} بنفس التكلفة والتشغيلة.")
                                } catch (e: Exception) { error = e.message ?: "تعذر ترحيل التحويل" }
                                finally { saving = false }
                            }
                        }) { Text(if (saving) "جارٍ الترحيل..." else "ترحيل نهائي") }
                    }
                }
            }
        }
    }

    if (showAddLine && row != null) {
        WarehouseTransferLineDialog(container, row.fromWarehouseId, row.transferDate, itemsList, onDismiss = { showAddLine = false }) { item, lot, qty ->
            scope.launch {
                try {
                    container.advancedInventoryService.addWarehouseTransferLine(transferId, item.id, qty, lot.lotNo, lot.expiryDate, user.id)
                    showAddLine = false
                    refresh++
                    error = null
                } catch (e: Exception) { error = e.message ?: "تعذر إضافة الصنف" }
            }
        }
    }

    if (showCancel && row != null) {
        AlertDialog(
            onDismissRequest = { showCancel = false },
            title = { Text("إلغاء التحويل") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الإلغاء متاح للمسودة فقط ولا ينشئ أي حركة مخزون.")
                    OutlinedTextField(cancelReason, { cancelReason = it }, label = { Text("سبب الإلغاء - مطلوب") })
                }
            },
            confirmButton = {
                Button(enabled = cancelReason.isNotBlank(), onClick = {
                    scope.launch {
                        try {
                            container.advancedInventoryService.cancelWarehouseTransfer(transferId, cancelReason, user.id)
                            showCancel = false
                            onDone("تم إلغاء التحويل ${row.transferNo} بدون تغيير المخزون.")
                        } catch (e: Exception) { error = e.message ?: "تعذر إلغاء التحويل" }
                    }
                }) { Text("تأكيد الإلغاء") }
            },
            dismissButton = { TextButton(onClick = { showCancel = false }) { Text("رجوع") } }
        )
    }

    if (showReverse && row != null) {
        AlertDialog(
            onDismissRequest = { if (!saving) showReverse = false },
            title = { Text("عكس التحويل المرحّل") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيُنشئ النظام حركتين عكسيتين لكل سطر: إخراجاً من مخزن الوجهة وإدخالاً إلى مخزن المصدر بنفس التشغيلة والتكلفة الأصلية. لا تُحذف الحركة الأصلية.")
                    Text("العكس كامل، ويُرفض إذا لم تعد الكمية الأصلية متاحة في مخزن الوجهة أو إذا تسبب العكس في رصيد تاريخي سالب.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(reversalReason, { reversalReason = it }, label = { Text("سبب العكس - مطلوب") })
                }
            },
            confirmButton = {
                Button(enabled = reversalReason.isNotBlank() && !saving, onClick = {
                    scope.launch {
                        saving = true
                        try {
                            container.advancedInventoryService.reverseWarehouseTransfer(transferId, reversalReason, user.id)
                            showReverse = false
                            onDone("تم عكس التحويل ${row.transferNo}: عادت الكميات من ${toWarehouse?.nameAr ?: "الوجهة"} إلى ${fromWarehouse?.nameAr ?: "المصدر"} بحركات عكسية موثقة.")
                        } catch (e: Exception) { error = e.message ?: "تعذر عكس التحويل" }
                        finally { saving = false }
                    }
                }) { Text(if (saving) "جارٍ العكس..." else "تأكيد العكس") }
            },
            dismissButton = { TextButton(onClick = { showReverse = false }, enabled = !saving) { Text("رجوع") } }
        )
    }
}

@Composable
private fun WarehouseTransferLineDialog(
    container: AppContainer,
    sourceWarehouseId: Long,
    transferDate: Long,
    itemsList: List<ItemEntity>,
    onDismiss: () -> Unit,
    onAdd: (ItemEntity, LotBalanceRow, Double) -> Unit
) {
    var item by remember { mutableStateOf<ItemEntity?>(null) }
    var lots by remember { mutableStateOf(emptyList<LotBalanceRow>()) }
    var lot by remember { mutableStateOf<LotBalanceRow?>(null) }
    var qtyText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(itemsList) { if (item == null) item = itemsList.firstOrNull() }
    LaunchedEffect(sourceWarehouseId, transferDate, item?.id) {
        lots = item?.let { container.db.stockDao().lotBalancesAt(sourceWarehouseId, it.id, transferDate) } ?: emptyList()
        lot = lots.firstOrNull()
        qtyText = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة صنف للتحويل") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionField("الصنف", item?.nameAr ?: "اختر", itemsList, { "${it.code} — ${it.nameAr}" }) {
                    item = it
                    lot = null
                    error = null
                }
                if (lots.isEmpty()) {
                    FushInlineState("لا يوجد رصيد لهذا الصنف في مخزن المصدر.", tone = FushStatusTone.Danger)
                } else {
                    SelectionField(
                        "التشغيلة / الرصيد",
                        lot?.let { transferLotLabel(it) } ?: "اختر",
                        lots,
                        { transferLotLabel(it) }
                    ) { lot = it }
                    lot?.let { selected ->
                        Text("المتاح: ${fmtQty(selected.quantityBase)} • قيمة الرصيد: ${fmtMoney(selected.inventoryValueBase)}", style = MaterialTheme.typography.bodySmall)
                    }
                    FushDecimalField(qtyText, { qtyText = it }, "الكمية بالوحدة الأساسية", modifier = Modifier.fillMaxWidth())
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = item != null && lot != null && qtyText.toDoubleOrNull() != null, onClick = {
                val qty = qtyText.toDoubleOrNull()
                val available = lot?.quantityBase ?: 0.0
                when {
                    qty == null || qty <= 0.0 -> error = "أدخل كمية أكبر من صفر"
                    qty > available + 0.000000001 -> error = "الكمية أكبر من الرصيد المتاح"
                    else -> onAdd(item!!, lot!!, qty)
                }
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun transferLotLabel(row: LotBalanceRow): String = buildString {
    append(row.lotNo ?: "بدون تشغيلة")
    row.expiryDate?.let { append(" • ${fmtDate(it)}") }
    append(" • متاح ${fmtQty(row.quantityBase)}")
}

private fun parseInventoryDate(text: String): Long? = try {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(text.trim())?.time?.plus(86_400_000L - 1L)
} catch (_: Exception) { null }

@Composable
private fun StartInventoryCountDialog(warehouses: List<WarehouseEntity>, onDismiss: () -> Unit, onStart: (WarehouseEntity, String) -> Unit) {
    var warehouse by remember { mutableStateOf<WarehouseEntity?>(null) }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(warehouses) { if (warehouse == null) warehouse = warehouses.firstOrNull { it.code == "RM" } ?: warehouses.firstOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بدء جرد فعلي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سيحفظ النظام لقطة من الرصيد الحالي حسب الصنف والتشغيلة، ثم تدخل الكمية الفعلية.")
                SelectionField("المخزن", warehouse?.nameAr ?: "اختر", warehouses, { it.nameAr }) { warehouse = it }
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة الجرد") })
            }
        },
        confirmButton = { Button(enabled = warehouse != null, onClick = { onStart(warehouse!!, note) }) { Text("إنشاء الجرد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun InventoryCountDialog(
    container: AppContainer,
    user: UserEntity,
    countId: Long,
    itemsList: List<ItemEntity>,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var count by remember { mutableStateOf<InventoryCountEntity?>(null) }
    var lines by remember { mutableStateOf(emptyList<InventoryCountLineEntity>()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAddMissingLine by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(countId, refresh) {
        count = container.db.advancedInventoryDao().countById(countId)
        lines = container.db.advancedInventoryDao().countLines(countId)
    }
    Dialog(onDismissRequest = { if (!saving && !showAddMissingLine) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Column(Modifier.padding(12.dp)) {
                Text("الجرد ${count?.countNo ?: countId}", style = MaterialTheme.typography.headlineSmall)
                Text("أدخل الكمية الفعلية لكل سطر. الفرق = الفعلي - رصيد النظام.")
                OutlinedButton(
                    enabled = count?.status == "DRAFT" && !saving,
                    onClick = { showAddMissingLine = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("إضافة صنف/تشغيلة غير موجودة في لقطة الجرد") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(lines, key = { it.id }) { line ->
                        var qtyText by remember(line.id, line.countedQtyBase) { mutableStateOf(line.countedQtyBase?.let(::fmtQty) ?: "") }
                        var reason by remember(line.id, line.reason) { mutableStateOf(line.reason) }
                        val itemName = itemsList.firstOrNull { it.id == line.itemId }?.nameAr ?: "صنف ${line.itemId}"
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(itemName, style = MaterialTheme.typography.titleMedium)
                                Text("النظام: ${fmtQty(line.systemQtyBase)} • تشغيلة: ${line.lotNo ?: "—"}")
                                FushDecimalField(qtyText, { qtyText = it }, "الكمية الفعلية", modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(reason, { reason = it }, label = { Text("سبب الفرق - اختياري") }, singleLine = true)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("الفرق: ${line.countedQtyBase?.let { fmtQty(it - line.systemQtyBase) } ?: "—"}")
                                    TextButton(onClick = {
                                        scope.launch {
                                            try {
                                                val q = requireNotNull(qtyText.toDoubleOrNull()) { "أدخل كمية صحيحة" }
                                                container.advancedInventoryService.setCountedQuantity(line.id, q, reason, user.id)
                                                refresh++
                                                error = null
                                            } catch (e: Exception) { error = e.message }
                                        }
                                    }) { Text("حفظ السطر") }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("إغلاق") }
                    Spacer(Modifier.width(6.dp))
                    Button(enabled = !saving && lines.isNotEmpty(), onClick = {
                        scope.launch {
                            saving = true
                            try {
                                val entry = container.advancedInventoryService.postCount(countId, user.id)
                                onDone(if (entry == null) "تم ترحيل الجرد بدون فروق" else "تم ترحيل الجرد وقيد الفروق رقم $entry")
                            } catch (e: Exception) { error = e.message ?: "تعذر ترحيل الجرد" }
                            finally { saving = false }
                        }
                    }) { Text(if (saving) "جارٍ الترحيل..." else "ترحيل الجرد") }
                }
            }
        }
    }

    if (showAddMissingLine) {
        AddMissingInventoryCountLineDialog(
            itemsList = itemsList.filter { it.isActive },
            onDismiss = { showAddMissingLine = false },
            onSave = { item, qty, cost, lotNo, expiryDate, reason ->
                scope.launch {
                    try {
                        container.advancedInventoryService.addMissingCountLine(
                            countId = countId,
                            itemId = item.id,
                            countedQtyBase = qty,
                            unitCostBase = cost,
                            lotNo = lotNo,
                            expiryDate = expiryDate,
                            reason = reason,
                            createdBy = user.id
                        )
                        error = null
                        showAddMissingLine = false
                        refresh++
                    } catch (e: Exception) {
                        error = e.message ?: "تعذر إضافة الصنف إلى الجرد"
                    }
                }
            }
        )
    }
}

@Composable
private fun AddMissingInventoryCountLineDialog(
    itemsList: List<ItemEntity>,
    onDismiss: () -> Unit,
    onSave: (ItemEntity, Double, Double, String?, Long?, String) -> Unit
) {
    var item by remember { mutableStateOf<ItemEntity?>(null) }
    var qtyText by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("") }
    var lotText by remember { mutableStateOf("") }
    var expiryText by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(itemsList) { if (item == null) item = itemsList.firstOrNull() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة رصيد فعلي غير موجود بالنظام") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("استخدم هذا الخيار فقط عندما تجد أثناء الجرد صنفاً أو تشغيلة موجودة فعلياً ولم تكن موجودة في لقطة بداية الجرد. سيعتبر رصيد النظام لهذا السطر = صفر.")
                SelectionField("الصنف", item?.nameAr ?: "اختر", itemsList, { "${it.code} • ${it.nameAr}" }) { selected ->
                    item = selected
                    lotText = ""
                    expiryText = ""
                    localError = null
                }
                FushDecimalField(qtyText, { qtyText = it }, "الكمية الفعلية", modifier = Modifier.fillMaxWidth())
                FushDecimalField(costText, { costText = it }, "تكلفة الوحدة بالريال الجديد", modifier = Modifier.fillMaxWidth())
                if (item?.lotTracked == true) {
                    OutlinedTextField(lotText, { lotText = it }, label = { Text("رقم التشغيلة") }, singleLine = true)
                }
                if (item?.expiryTracked == true) {
                    FushDateField(expiryText, { expiryText = it }, "تاريخ الصلاحية", modifier = Modifier.fillMaxWidth(), optional = true)
                }
                OutlinedTextField(reason, { reason = it }, label = { Text("سبب/ملاحظة") }, singleLine = true)
                localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = item != null, onClick = {
                val selected = item ?: return@Button
                val qty = qtyText.toDoubleOrNull()
                val cost = costText.toDoubleOrNull()
                val expiry = if (selected.expiryTracked) parseInventoryDate(expiryText) else null
                when {
                    qty == null || qty <= 0.0 -> localError = "أدخل كمية فعلية أكبر من صفر"
                    cost == null || cost <= 0.0 -> localError = "أدخل تكلفة وحدة أكبر من صفر"
                    selected.lotTracked && lotText.isBlank() -> localError = "رقم التشغيلة مطلوب لهذا الصنف"
                    selected.expiryTracked && expiry == null -> localError = "تاريخ الصلاحية غير صحيح"
                    else -> onSave(
                        selected,
                        qty,
                        cost,
                        lotText.trim().ifBlank { null },
                        expiry,
                        reason
                    )
                }
            }) { Text("إضافة إلى الجرد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun LotControlDialog(lot: InventoryLotAlertRow, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var status by remember { mutableStateOf(lot.controlStatus) }
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حالة التشغيلة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${lot.nameAr} • ${lot.lotNo ?: "بدون رقم"}")
                StringSelectionField("الحالة", statusAr(status), listOf("ACCEPTED", "QUARANTINE", "BLOCKED", "RETURNED")) { status = it }
                OutlinedTextField(reason, { reason = it }, label = { Text("السبب / الملاحظة") })
                if (status != "ACCEPTED") Text("تنبيه: هذه التشغيلة ستُستبعد من البيع وصرف الإنتاج.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Button(onClick = { onSave(status, reason) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun LegacyLotAssignmentDialog(
    lots: List<InventoryLotAlertRow>,
    items: List<ItemEntity>,
    onDismiss: () -> Unit,
    onSave: (InventoryLotAlertRow, ItemEntity, Double, String?, Long?, String) -> Unit
) {
    val candidates = remember(lots, items) {
        lots.filter { row ->
            val item = items.firstOrNull { it.id == row.itemId }
            row.quantityBase > 0.000000001 && item != null &&
                ((item.lotTracked && row.lotNo.isNullOrBlank()) || (item.expiryTracked && row.expiryDate == null))
        }
    }
    var source by remember(candidates) { mutableStateOf(candidates.firstOrNull()) }
    val item = source?.let { s -> items.firstOrNull { it.id == s.itemId } }
    var qtyText by remember(source) { mutableStateOf(source?.let { fmtQty(it.quantityBase) } ?: "") }
    var lotText by remember(source) { mutableStateOf(source?.lotNo.orEmpty()) }
    var expiryText by remember(source) { mutableStateOf(source?.expiryDate?.let(::fmtDate).orEmpty()) }
    var note by remember { mutableStateOf("ربط رصيد مخزون قديم بتشغيلة وصلاحية") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ربط رصيد قديم بتشغيلة وصلاحية") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("هذه العملية لا تضيف ولا تحذف مخزوناً. تنقل نفس الكمية ونفس القيمة من الرصيد القديم غير المكتمل إلى تشغيلة/صلاحية صحيحة، مع تسجيلها في سجل التدقيق.", style = MaterialTheme.typography.bodySmall)
                if (candidates.isEmpty()) {
                    FushInlineState("لا يوجد حاليًا رصيد قديم يحتاج استكمال رقم تشغيلة أو تاريخ صلاحية.", tone = FushStatusTone.Success)
                } else {
                    SelectionField(
                        "الرصيد القديم",
                        source?.let { "${it.nameAr} — ${it.warehouseName} — ${fmtQty(it.quantityBase)}" } ?: "اختر",
                        candidates,
                        { "${it.nameAr} — ${it.warehouseName} — ${fmtQty(it.quantityBase)}" }
                    ) { source = it }
                    source?.let { s ->
                        Text("الكود: ${s.code} • التشغيلة الحالية: ${s.lotNo ?: "بدون رقم"} • الصلاحية الحالية: ${s.expiryDate?.let(::fmtDate) ?: "بدون تاريخ"}", style = MaterialTheme.typography.bodySmall)
                    }
                    FushDecimalField(qtyText, { qtyText = it }, "الكمية المراد ربطها", modifier = Modifier.fillMaxWidth())
                    if (item?.lotTracked == true) {
                        OutlinedTextField(lotText, { lotText = it }, label = { Text("رقم التشغيلة الجديد - مطلوب") }, singleLine = true)
                    }
                    if (item?.expiryTracked == true) {
                        FushDateField(expiryText, { expiryText = it }, "تاريخ الصلاحية", modifier = Modifier.fillMaxWidth())
                        item.shelfLifeDays?.let { days -> Text("مدة الصلاحية المعرفة للصنف: $days يوم", style = MaterialTheme.typography.bodySmall) }
                    }
                    OutlinedTextField(note, { note = it }, label = { Text("ملاحظة / سبب") })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (candidates.isNotEmpty()) {
                Button(onClick = {
                    val s = source ?: return@Button
                    val i = item ?: return@Button
                    val qty = qtyText.toDoubleOrNull()
                    val expiry = if (i.expiryTracked) parseInventoryDate(expiryText) else null
                    when {
                        qty == null || qty <= 0.0 -> error = "أدخل كمية صحيحة أكبر من صفر"
                        qty > s.quantityBase + 0.000000001 -> error = "الكمية أكبر من الرصيد القديم المتاح"
                        i.lotTracked && lotText.isBlank() -> error = "أدخل رقم التشغيلة"
                        i.expiryTracked && expiry == null -> error = "أدخل تاريخ الصلاحية بصيغة yyyy-MM-dd"
                        else -> { error = null; onSave(s, i, qty, if (i.lotTracked) lotText.trim() else null, expiry, note) }
                    }
                }) { Text("ربط الرصيد") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun AdvancedOpeningStockDialog(
    items: List<ItemEntity>,
    warehouses: List<WarehouseEntity>,
    onDismiss: () -> Unit,
    onSave: (ItemEntity, WarehouseEntity, Double, Double, String) -> Unit
) {
    var item by remember { mutableStateOf<ItemEntity?>(null) }
    var warehouse by remember { mutableStateOf<WarehouseEntity?>(null) }
    var qtyText by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(items) { if (item == null) item = items.firstOrNull() }
    LaunchedEffect(warehouses) { if (warehouse == null) warehouse = warehouses.firstOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("رصيد مخزون افتتاحي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionField("الصنف", item?.nameAr ?: "اختر", items, { it.nameAr }) { item = it }
                SelectionField("المخزن", warehouse?.nameAr ?: "اختر", warehouses, { it.nameAr }) { warehouse = it }
                FushDecimalField(qtyText, { qtyText = it }, "الكمية بالوحدة الأساسية", modifier = Modifier.fillMaxWidth())
                FushDecimalField(costText, { costText = it }, "تكلفة الوحدة بالريال الجديد", modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة") })
            }
        },
        confirmButton = { Button(enabled = item != null && warehouse != null && qtyText.toDoubleOrNull() != null && costText.toDoubleOrNull() != null, onClick = { onSave(item!!, warehouse!!, qtyText.toDouble(), costText.toDouble(), note) }) { Text("ترحيل") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun fmtQty(value: Double): String = if (abs(value - value.toLong()) < 0.000001) value.toLong().toString() else "%.3f".format(Locale.US, value)
private fun fmtMoney(value: Double): String = "%,.2f".format(Locale.US, value)
private fun fmtDate(value: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(value))
private fun statusAr(status: String): String = when (status) {
    "DRAFT" -> "مسودة"
    "POSTED" -> "مرحّل"
    "ACCEPTED" -> "مقبول"
    "QUARANTINE" -> "محجور"
    "BLOCKED" -> "موقوف"
    "RETURNED" -> "مرتجع"
    "CANCELLED" -> "ملغي"
    "REVERSED" -> "معكوس"
    else -> status
}
private fun movementAr(type: String): String = when (type) {
    "PURCHASE" -> "شراء"
    "PURCHASE_RETURN" -> "مرتجع شراء"
    "SALE" -> "بيع"
    "SALES_RETURN" -> "مرتجع بيع"
    "PRODUCTION_ISSUE" -> "صرف إنتاج"
    "PRODUCTION_ISSUE_RETURN" -> "تصحيح/مرتجع صرف إنتاج"
    "PRODUCTION_COST_REVALUE_OUT" -> "إعادة تقييم تكلفة إنتاج - إخراج"
    "PRODUCTION_COST_REVALUE_IN" -> "إعادة تقييم تكلفة إنتاج - إدخال"
    "PRODUCTION_RECEIPT" -> "استلام إنتاج"
    "OPENING" -> "رصيد افتتاحي"
    "LEGACY_LOT_RECLASS_OUT" -> "إعادة تصنيف رصيد قديم - إخراج"
    "LEGACY_LOT_RECLASS_IN" -> "إعادة تصنيف رصيد قديم - إدخال تشغيلة/صلاحية"
    "COUNT_ADJUSTMENT" -> "تسوية جرد"
    "TRANSFER_OUT" -> "تحويل مخزني - صادر"
    "TRANSFER_IN" -> "تحويل مخزني - وارد"
    "TRANSFER_REVERSAL_OUT" -> "عكس تحويل مخزني - صادر من الوجهة"
    "TRANSFER_REVERSAL_IN" -> "عكس تحويل مخزني - وارد إلى المصدر"
    else -> type
}
