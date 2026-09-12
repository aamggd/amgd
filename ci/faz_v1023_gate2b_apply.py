#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])


def write(rel: str, text: str):
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one anchor, found {count}')
    return text.replace(old, new, 1)


write('app/src/main/java/com/fush/erp/ui/screens/SupplierItemMovementReportUi.kt', r'''package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fush.erp.data.entity.ItemEntity
import com.fush.erp.data.entity.SupplierEntity
import com.fush.erp.data.entity.WarehouseEntity
import com.fush.erp.domain.SupplierItemMovementReportResult
import com.fush.erp.domain.SupplierItemMovementReportTotals
import com.fush.erp.ui.FushSearchableSelectionField
import com.fush.erp.ui.FushSectionHeader
import com.fush.erp.ui.export.ReportExportDocument
import com.fush.erp.ui.export.ReportExportTable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object SupplierItemMovementReportExportFactory {
    fun build(
        result: SupplierItemMovementReportResult?,
        periodLabel: String,
        from: Long,
        to: Long,
        supplierLabel: String,
        itemLabel: String,
        warehouseLabel: String
    ): ReportExportDocument {
        if (result == null) return ReportExportDocument(
            title = "تقرير حركة أصناف المورد — FAZ Solar ERP",
            subtitle = supplierReportPeriodSubtitle(periodLabel, from, to),
            summary = listOf(
                "فلتر المورد" to supplierLabel,
                "فلتر الصنف" to itemLabel,
                "فلتر المخزن" to warehouseLabel
            ),
            notes = listOf("لا توجد بيانات تقرير متاحة للفلاتر والفترة الحالية.")
        )

        val t = result.totals
        val summaryTable = ReportExportTable(
            title = "ملخص حركة المورد والصنف",
            headers = listOf(
                "المورد", "كود المورد", "الصنف", "الكود", "الوحدة",
                "افتتاحي", "مشتريات", "مرتجع شراء", "مبيعات", "مرتجع بيع",
                "حركة أخرى", "ختامي", "قيمة الختامي", "حالة المصدر"
            ),
            rows = result.summaries.map { r ->
                listOf(
                    r.supplierName,
                    r.supplierCode ?: "—",
                    r.itemName,
                    r.code,
                    r.baseUnitName,
                    supplierReportQty(r.openingQtyBase),
                    supplierReportQty(r.purchasesQtyBase),
                    supplierReportQty(r.purchaseReturnsQtyBase),
                    supplierReportQty(r.salesQtyBase),
                    supplierReportQty(r.salesReturnsQtyBase),
                    supplierReportQty(r.otherNetQtyBase),
                    supplierReportQty(r.closingQtyBase),
                    supplierReportMoney(r.closingValueBase),
                    supplierAttributionLabel(r.attributionStatus)
                )
            }
        )
        val movementTable = ReportExportTable(
            title = "تفاصيل حركة أصناف المورد",
            headers = listOf(
                "التاريخ", "المورد", "الصنف", "الكود", "المخزن", "النوع",
                "وارد", "صادر", "تكلفة الوحدة", "قيمة الحركة", "المستند",
                "التشغيلة", "الانتهاء", "حالة المصدر"
            ),
            rows = result.rows.map { r ->
                listOf(
                    supplierReportDate(r.eventDate),
                    r.supplierName,
                    r.itemName,
                    r.code,
                    r.warehouseName,
                    supplierMovementTypeLabel(r.movementType),
                    if (r.quantityInBase > 0.0) supplierReportQty(r.quantityInBase) else "—",
                    if (r.quantityOutBase > 0.0) supplierReportQty(r.quantityOutBase) else "—",
                    supplierReportMoney(r.unitCostBase),
                    supplierReportMoney(r.inventoryValueDeltaBase),
                    r.documentNo.ifBlank { "${r.referenceType} #${r.referenceId ?: "—"}" },
                    r.lotNo ?: "—",
                    r.expiryDate?.let(::supplierReportDate) ?: "—",
                    supplierAttributionLabel(r.attributionStatus)
                )
            }
        )
        return ReportExportDocument(
            title = "تقرير حركة أصناف المورد — FAZ Solar ERP",
            subtitle = supplierReportPeriodSubtitle(periodLabel, from, to),
            summary = listOf(
                "الفترة" to "$periodLabel • ${supplierReportDate(from)} — ${supplierReportDate(to)}",
                "فلتر المورد" to supplierLabel,
                "فلتر الصنف" to itemLabel,
                "فلتر المخزن" to warehouseLabel,
                "الرصيد الافتتاحي" to supplierReportQty(t.openingQtyBase),
                "المشتريات" to supplierReportQty(t.purchasesQtyBase),
                "مرتجعات الشراء" to supplierReportQty(t.purchaseReturnsQtyBase),
                "المبيعات" to supplierReportQty(t.salesQtyBase),
                "مرتجعات المبيعات" to supplierReportQty(t.salesReturnsQtyBase),
                "الحركة الأخرى" to supplierReportQty(t.otherNetQtyBase),
                "الرصيد الختامي" to supplierReportQty(t.closingQtyBase),
                "قيمة الرصيد الختامي" to supplierReportMoney(t.closingValueBase),
                "أسطر منسوبة بدقة" to t.exactSummaryCount.toString(),
                "أسطر غير منسوبة" to t.unattributedSummaryCount.toString(),
                "رصيد غير منسوب" to supplierReportQty(t.unattributedClosingQtyBase)
            ),
            tables = listOf(summaryTable, movementTable),
            notes = listOf(
                "إسناد البيع للمورد يعتمد على سجل مصدر المخزون المحفوظ فعليًا في النظام، ولا يتم تخمين المورد من آخر فاتورة شراء أو من اسم الصنف.",
                "أي مخزون تاريخي أو حركة لا يمكن إثبات مصدرها تظهر صراحة باسم «غير منسوب» وتبقى منفصلة عن الموردين المعروفين.",
                "PDF وExcel ومعاينة الطباعة تستخدم نفس بيانات التقرير ونفس الفترة والفلاتر الظاهرة على الشاشة."
            )
        )
    }
}

@Composable
internal fun SupplierItemMovementReportTab(
    result: SupplierItemMovementReportResult?,
    suppliers: List<SupplierEntity>,
    itemsList: List<ItemEntity>,
    warehouses: List<WarehouseEntity>,
    selectedSupplierId: Long?,
    onSupplierChange: (Long?) -> Unit,
    selectedItemId: Long?,
    onItemChange: (Long?) -> Unit,
    selectedWarehouseId: Long?,
    onWarehouseChange: (Long?) -> Unit
) {
    val supplierOptions = suppliers.map { it.id to it.nameAr }
    val itemOptions = itemsList.map { it.id to "${it.nameAr} — ${it.code}" }
    val warehouseOptions = warehouses.map { it.id to it.nameAr }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FushSectionHeader(
                "حركة أصناف المورد",
                "تتبع الرصيد والمشتريات والمبيعات والمرتجعات حسب المصدر الفعلي للمخزون. الرصيد غير المعروف لا يُنسب تخمينًا لأي مورد."
            )
            Spacer(Modifier.height(8.dp))
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("فلاتر التقرير", style = MaterialTheme.typography.titleMedium)
                    SupplierMovementOptionalFilter(
                        label = "المورد",
                        selectedId = selectedSupplierId,
                        options = supplierOptions,
                        onSelected = onSupplierChange
                    )
                    SupplierMovementOptionalFilter(
                        label = "الصنف / SKU",
                        selectedId = selectedItemId,
                        options = itemOptions,
                        onSelected = onItemChange
                    )
                    SupplierMovementOptionalFilter(
                        label = "المخزن",
                        selectedId = selectedWarehouseId,
                        options = warehouseOptions,
                        onSelected = onWarehouseChange
                    )
                    Text(
                        "اترك أي فلتر فارغًا لعرض الكل ضمن الفترة المختارة من أعلى مركز التقارير.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (result == null) {
            item { Text("لا توجد بيانات تقرير محملة بعد.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            item { SupplierMovementTotalsCard(result.totals) }
            item { SupplierMovementSectionTitle("الملخص حسب المورد والصنف") }
            if (result.summaries.isEmpty()) {
                item { Text("لا توجد أرصدة أو حركات مطابقة للفلاتر الحالية.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(result.summaries) { r ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${r.supplierName} • ${r.itemName}", style = MaterialTheme.typography.titleMedium)
                        Text("${r.code} • ${r.baseUnitName} • ${supplierAttributionLabel(r.attributionStatus)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SupplierMovementValueRow("الرصيد الافتتاحي", supplierReportQty(r.openingQtyBase))
                        SupplierMovementValueRow("المشتريات / مرتجع الشراء", "${supplierReportQty(r.purchasesQtyBase)} / ${supplierReportQty(r.purchaseReturnsQtyBase)}")
                        SupplierMovementValueRow("المبيعات / مرتجع البيع", "${supplierReportQty(r.salesQtyBase)} / ${supplierReportQty(r.salesReturnsQtyBase)}")
                        SupplierMovementValueRow("حركة أخرى", supplierReportQty(r.otherNetQtyBase))
                        SupplierMovementValueRow("الرصيد الختامي", supplierReportQty(r.closingQtyBase))
                        SupplierMovementValueRow("قيمة الرصيد", supplierReportMoney(r.closingValueBase))
                    }
                }
            }

            item { SupplierMovementSectionTitle("تفاصيل الحركات") }
            if (result.rows.isEmpty()) {
                item { Text("لا توجد حركات داخل الفترة المحددة.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(result.rows.take(100)) { r ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${r.supplierName} • ${r.itemName}", style = MaterialTheme.typography.titleMedium)
                        Text("${supplierReportDate(r.eventDate)} • ${r.warehouseName} • ${supplierMovementTypeLabel(r.movementType)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SupplierMovementValueRow("وارد", if (r.quantityInBase > 0.0) supplierReportQty(r.quantityInBase) else "—")
                        SupplierMovementValueRow("صادر", if (r.quantityOutBase > 0.0) supplierReportQty(r.quantityOutBase) else "—")
                        SupplierMovementValueRow("تكلفة الوحدة", supplierReportMoney(r.unitCostBase))
                        SupplierMovementValueRow("قيمة الحركة", supplierReportMoney(r.inventoryValueDeltaBase))
                        SupplierMovementValueRow("المستند", r.documentNo.ifBlank { "${r.referenceType} #${r.referenceId ?: "—"}" })
                        SupplierMovementValueRow("التشغيلة", r.lotNo ?: "—")
                        SupplierMovementValueRow("المصدر", supplierAttributionLabel(r.attributionStatus))
                    }
                }
            }
            if (result.rows.size > 100) {
                item {
                    Text(
                        "تعرض الشاشة أول 100 حركة لتخفيف الحمل؛ PDF وExcel يتضمنان جميع الحركات المطابقة للفلاتر.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SupplierMovementOptionalFilter(
    label: String,
    selectedId: Long?,
    options: List<Pair<Long, String>>,
    onSelected: (Long?) -> Unit
) {
    FushSearchableSelectionField(
        label = label,
        selectedText = options.firstOrNull { it.first == selectedId }?.second.orEmpty(),
        options = options,
        optionText = { it.second },
        searchTerms = { listOf(it.first.toString(), it.second) },
        onSelected = { onSelected(it.first) },
        onCleared = { onSelected(null) },
        allowClear = true,
        onClearSelected = { onSelected(null) },
        placeholder = "الكل — اكتب للبحث"
    )
}

@Composable
private fun SupplierMovementTotalsCard(t: SupplierItemMovementReportTotals) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("ملخص الفترة", style = MaterialTheme.typography.titleMedium)
            SupplierMovementValueRow("الرصيد الافتتاحي", supplierReportQty(t.openingQtyBase))
            SupplierMovementValueRow("المشتريات", supplierReportQty(t.purchasesQtyBase))
            SupplierMovementValueRow("مرتجعات الشراء", supplierReportQty(t.purchaseReturnsQtyBase))
            SupplierMovementValueRow("المبيعات", supplierReportQty(t.salesQtyBase))
            SupplierMovementValueRow("مرتجعات المبيعات", supplierReportQty(t.salesReturnsQtyBase))
            SupplierMovementValueRow("الحركة الأخرى", supplierReportQty(t.otherNetQtyBase))
            SupplierMovementValueRow("الرصيد الختامي", supplierReportQty(t.closingQtyBase))
            SupplierMovementValueRow("قيمة الرصيد الختامي", supplierReportMoney(t.closingValueBase))
            SupplierMovementValueRow("غير منسوب", "${t.unattributedSummaryCount} سطر • رصيد ${supplierReportQty(t.unattributedClosingQtyBase)}")
        }
    }
}

@Composable
private fun SupplierMovementSectionTitle(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SupplierMovementValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun supplierAttributionLabel(status: String): String = when (status) {
    "EXACT" -> "منسوب للمورد"
    "UNATTRIBUTED" -> "غير منسوب"
    else -> status.ifBlank { "غير محدد" }
}

private fun supplierMovementTypeLabel(type: String): String = when (type) {
    "PURCHASE" -> "شراء"
    "PURCHASE_RETURN" -> "مرتجع شراء"
    "SALE" -> "بيع"
    "SALES_RETURN" -> "مرتجع بيع"
    "OPENING_STOCK" -> "مخزون افتتاحي"
    "ADJUSTMENT" -> "تسوية مخزون"
    "TRANSFER" -> "تحويل"
    "ISSUE" -> "صرف"
    "RECEIPT" -> "استلام"
    else -> type
}

private fun supplierReportPeriodSubtitle(periodLabel: String, from: Long, to: Long): String =
    "الفترة: $periodLabel • ${supplierReportDate(from)} — ${supplierReportDate(to)}"

private fun supplierReportDate(ms: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
private fun supplierReportQty(value: Double): String = String.format(Locale.US, "%,.3f", value).trimEnd('0').trimEnd('.')
private fun supplierReportMoney(value: Double): String = String.format(Locale.US, "%,.2f", value)
''')

reports = root / 'app/src/main/java/com/fush/erp/ui/screens/ReportsScreen.kt'
text = reports.read_text(encoding='utf-8')

text = replace_once(
    text,
    '    val statementSuppliers by container.db.supplierDao().observeAll().collectAsState(initial = emptyList())\n',
    '    val statementSuppliers by container.db.supplierDao().observeAll().collectAsState(initial = emptyList())\n'
    '    val supplierMovementItems by container.db.itemDao().observeAll().collectAsState(initial = emptyList())\n'
    '    val supplierMovementWarehouses by container.db.warehouseDao().observeAll().collectAsState(initial = emptyList())\n',
    'report live filter lists'
)

text = replace_once(
    text,
    '    var periodComparison by remember { mutableStateOf<PeriodComparisonReport?>(null) }\n',
    '    var periodComparison by remember { mutableStateOf<PeriodComparisonReport?>(null) }\n'
    '    var supplierMovementSupplierId by remember { mutableStateOf<Long?>(null) }\n'
    '    var supplierMovementItemId by remember { mutableStateOf<Long?>(null) }\n'
    '    var supplierMovementWarehouseId by remember { mutableStateOf<Long?>(null) }\n'
    '    var supplierItemMovementReport by remember { mutableStateOf<SupplierItemMovementReportResult?>(null) }\n',
    'supplier report state'
)

text = replace_once(
    text,
    '    LaunchedEffect(tab, from, to, ledgerAccountId, statementPartyType, statementCustomerId, statementSupplierId, agingPartyType) {\n',
    '    LaunchedEffect(tab, from, to, ledgerAccountId, statementPartyType, statementCustomerId, statementSupplierId, agingPartyType, supplierMovementSupplierId, supplierMovementItemId, supplierMovementWarehouseId) {\n',
    'report launched effect keys'
)

text = replace_once(
    text,
    '                "المشتريات" -> suppliers = container.db.reportDao().supplierPurchases(from, to)\n',
    '                "المشتريات" -> suppliers = container.db.reportDao().supplierPurchases(from, to)\n'
    '                "حركة أصناف المورد" -> supplierItemMovementReport = container.supplierItemMovementReportService.load(\n'
    '                    SupplierItemMovementReportFilter(\n'
    '                        fromDate = from,\n'
    '                        toDate = to,\n'
    '                        supplierId = supplierMovementSupplierId,\n'
    '                        itemId = supplierMovementItemId,\n'
    '                        warehouseId = supplierMovementWarehouseId\n'
    '                    )\n'
    '                )\n',
    'report loading branch'
)

text = replace_once(
    text,
    '        statementSupplierId, partyStatement, agingPartyType, agingRows, expenses, treasuryReport, periodComparison\n',
    '        statementSupplierId, partyStatement, agingPartyType, agingRows, expenses, treasuryReport, periodComparison,\n'
    '        supplierItemMovementReport, supplierMovementSupplierId, supplierMovementItemId, supplierMovementWarehouseId,\n'
    '        supplierMovementItems, supplierMovementWarehouses\n',
    'export remember dependencies'
)

text = replace_once(
    text,
    '    ) {\n        buildCurrentReportExportDocument(\n',
    '    ) {\n        if (tab == "حركة أصناف المورد") {\n'
    '            SupplierItemMovementReportExportFactory.build(\n'
    '                result = supplierItemMovementReport,\n'
    '                periodLabel = period,\n'
    '                from = from,\n'
    '                to = to,\n'
    '                supplierLabel = statementSuppliers.firstOrNull { it.id == supplierMovementSupplierId }?.nameAr ?: "كل الموردين",\n'
    '                itemLabel = supplierMovementItems.firstOrNull { it.id == supplierMovementItemId }?.let { "${it.nameAr} — ${it.code}" } ?: "كل الأصناف",\n'
    '                warehouseLabel = supplierMovementWarehouses.firstOrNull { it.id == supplierMovementWarehouseId }?.nameAr ?: "كل المخازن"\n'
    '            )\n'
    '        } else {\n'
    '            buildCurrentReportExportDocument(\n',
    'supplier export dispatch start'
)

text = replace_once(
    text,
    '            periodComparison = periodComparison\n        )\n    }\n\n    BoxWithConstraints',
    '            periodComparison = periodComparison\n            )\n        }\n    }\n\n    BoxWithConstraints',
    'supplier export dispatch close'
)

call_anchor = '                        { agingPartyType = it }, agingRows, expenses, treasuryReport, periodComparison, to,\n'
call_replacement = (
    '                        { agingPartyType = it }, agingRows, expenses, treasuryReport, periodComparison,\n'
    '                        statementSuppliers, supplierMovementItems, supplierMovementWarehouses,\n'
    '                        supplierMovementSupplierId, { supplierMovementSupplierId = it },\n'
    '                        supplierMovementItemId, { supplierMovementItemId = it },\n'
    '                        supplierMovementWarehouseId, { supplierMovementWarehouseId = it }, supplierItemMovementReport, to,\n'
)
count = text.count(call_anchor)
if count != 2:
    raise SystemExit(f'ReportTabContent call anchors: expected 2, found {count}')
text = text.replace(call_anchor, call_replacement)

text = replace_once(
    text,
    'private val reportTabs = listOf(\n    "ملخص", "المبيعات", "المشتريات", "المخزون", "المالية", "الخزائن والبنوك", "مقارنة الفترات", "المصروفات", "الأستاذ العام", "أعمار الديون", "كشف الأطراف"\n)',
    'private val reportTabs = listOf(\n    "ملخص", "المبيعات", "المشتريات", "حركة أصناف المورد", "المخزون", "المالية", "الخزائن والبنوك", "مقارنة الفترات", "المصروفات", "الأستاذ العام", "أعمار الديون", "كشف الأطراف"\n)',
    'report tab list'
)

text = replace_once(
    text,
    '    periodComparison: PeriodComparisonReport?,\n    reportTo: Long,\n',
    '    periodComparison: PeriodComparisonReport?,\n'
    '    supplierMovementSuppliers: List<SupplierEntity>,\n'
    '    supplierMovementItems: List<ItemEntity>,\n'
    '    supplierMovementWarehouses: List<WarehouseEntity>,\n'
    '    supplierMovementSupplierId: Long?,\n'
    '    onSupplierMovementSupplierChange: (Long?) -> Unit,\n'
    '    supplierMovementItemId: Long?,\n'
    '    onSupplierMovementItemChange: (Long?) -> Unit,\n'
    '    supplierMovementWarehouseId: Long?,\n'
    '    onSupplierMovementWarehouseChange: (Long?) -> Unit,\n'
    '    supplierItemMovementReport: SupplierItemMovementReportResult?,\n'
    '    reportTo: Long,\n',
    'ReportTabContent signature'
)

text = replace_once(
    text,
    '        "المشتريات" -> PurchasesTab(suppliers)\n        "المخزون" -> InventoryTab(\n',
    '        "المشتريات" -> PurchasesTab(suppliers)\n'
    '        "حركة أصناف المورد" -> SupplierItemMovementReportTab(\n'
    '            result = supplierItemMovementReport,\n'
    '            suppliers = supplierMovementSuppliers,\n'
    '            itemsList = supplierMovementItems,\n'
    '            warehouses = supplierMovementWarehouses,\n'
    '            selectedSupplierId = supplierMovementSupplierId,\n'
    '            onSupplierChange = onSupplierMovementSupplierChange,\n'
    '            selectedItemId = supplierMovementItemId,\n'
    '            onItemChange = onSupplierMovementItemChange,\n'
    '            selectedWarehouseId = supplierMovementWarehouseId,\n'
    '            onWarehouseChange = onSupplierMovementWarehouseChange\n'
    '        )\n'
    '        "المخزون" -> InventoryTab(\n',
    'ReportTabContent report branch'
)

text = replace_once(
    text,
    '    "المشتريات" -> "تقرير المشتريات"\n    "المخزون" -> "تقرير المخزون"\n',
    '    "المشتريات" -> "تقرير المشتريات"\n    "حركة أصناف المورد" -> "تقرير حركة أصناف المورد"\n    "المخزون" -> "تقرير المخزون"\n',
    'report title'
)

text = replace_once(
    text,
    '    "المشتريات" -> "FAZ-Solar-Purchases-Report"\n    "المخزون" -> "FAZ-Solar-Inventory-Report"\n',
    '    "المشتريات" -> "FAZ-Solar-Purchases-Report"\n    "حركة أصناف المورد" -> "FAZ-Solar-Supplier-Item-Movement"\n    "المخزون" -> "FAZ-Solar-Inventory-Report"\n',
    'report basename'
)

reports.write_text(text, encoding='utf-8')
print('GATE2B_PATCH_APPLIED=PASS')
