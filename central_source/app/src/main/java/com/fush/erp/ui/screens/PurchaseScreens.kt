package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.fush.erp.domain.PurchaseDraftLine
import com.fush.erp.domain.PurchaseMath
import com.fush.erp.domain.PurchaseReturnDraftLine
import com.fush.erp.domain.PurchaseService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PurchaseLineUi(
    val item: ItemEntity,
    val unit: UnitEntity,
    val factor: Double,
    val quantity: Double,
    val unitPrice: Double,
    val lotNo: String? = null,
    val expiryDate: Long? = null
) {
    val total: Double get() = quantity * unitPrice
}

@Composable
fun PurchasesScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val suppliers by container.db.supplierDao().observeAll().collectAsState(initial = emptyList())
    val purchases by container.db.purchaseDao().observeSummaries().collectAsState(initial = emptyList())
    val itemsList by container.db.itemDao().observeAll().collectAsState(initial = emptyList())
    val units by container.db.unitDao().observeAll().collectAsState(initial = emptyList())
    val warehouses by container.db.warehouseDao().observeAll().collectAsState(initial = emptyList())
    val currencies by container.db.currencyDao().observeAll().collectAsState(initial = emptyList())
    val balanceAsOf = remember { System.currentTimeMillis() }
    val supplierBalances by container.db.purchaseDao().observeSupplierBalances(balanceAsOf).collectAsState(initial = emptyList())
    var showSupplier by remember { mutableStateOf(false) }
    var showPurchase by remember { mutableStateOf(false) }
    var returnInvoice by remember { mutableStateOf<PurchaseInvoiceSummary?>(null) }
    var detailInvoice by remember { mutableStateOf<PurchaseInvoiceSummary?>(null) }
    var accountSupplier by remember { mutableStateOf<SupplierEntity?>(null) }
    var paymentSupplier by remember { mutableStateOf<SupplierEntity?>(null) }
    var showAging by remember { mutableStateOf(false) }
    var purchaseSearch by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val cashLabel = stringResource(R.string.common_cash)
    val creditLabel = stringResource(R.string.common_credit)

    val filteredPurchases = remember(purchases, purchaseSearch, cashLabel, creditLabel) {
        val q = purchaseSearch.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) purchases else purchases.filter { purchase ->
            listOf(
                purchase.invoiceNo,
                purchase.supplierName,
                purchase.currencyCode,
                if (purchase.paymentType == "CASH") cashLabel else creditLabel,
            ).any { it.lowercase(Locale.ROOT).contains(q) }
        }
    }
    val totalPurchaseBase = remember(purchases) { purchases.sumOf { it.totalBase } }
    val totalPayablesBase = remember(supplierBalances) { supplierBalances.sumOf { it.outstandingBase } }
    val overduePayablesBase = remember(supplierBalances) { supplierBalances.sumOf { it.overdueBase } }
    val creditPurchaseCount = remember(purchases) { purchases.count { it.paymentType != "CASH" } }

    LazyColumn(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FushSectionHeader(
                    title = stringResource(R.string.purchases_title),
                    subtitle = stringResource(R.string.purchases_subtitle),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { showPurchase = true },
                    enabled = suppliers.isNotEmpty(),
                    shape = MaterialTheme.shapes.medium,
                ) { Text(stringResource(R.string.purchases_new_invoice)) }
            }
        }

        if (suppliers.isEmpty()) {
            item {
                FushSystemState(
                    title = stringResource(R.string.purchases_no_suppliers),
                    detail = stringResource(R.string.purchases_create_supplier_first),
                )
            }
        }

        if (message != null) {
            item { FushOperationMessage(message, onConsumed = { message = null }) }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(
                    label = stringResource(R.string.purchases_total),
                    value = formatMoney(totalPurchaseBase),
                    helper = stringResource(R.string.common_base_currency),
                    modifier = Modifier.weight(1f),
                    tone = FushStatusTone.Info,
                )
                FushMetricCard(
                    label = stringResource(R.string.purchases_supplier_due),
                    value = formatMoney(totalPayablesBase),
                    helper = stringResource(R.string.purchases_open_payables),
                    modifier = Modifier.weight(1f),
                    tone = if (totalPayablesBase > 0.000001) FushStatusTone.Warning else FushStatusTone.Success,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(
                    label = stringResource(R.string.purchases_overdue_suppliers),
                    value = formatMoney(overduePayablesBase),
                    helper = stringResource(R.string.purchases_needs_settlement),
                    modifier = Modifier.weight(1f),
                    tone = if (overduePayablesBase > 0.000001) FushStatusTone.Danger else FushStatusTone.Success,
                )
                FushMetricCard(
                    label = stringResource(R.string.purchases_credit_invoices),
                    value = creditPurchaseCount.toString(),
                    helper = stringResource(R.string.purchases_from_total, purchases.size),
                    modifier = Modifier.weight(1f),
                    tone = if (creditPurchaseCount > 0) FushStatusTone.Warning else FushStatusTone.Success,
                )
            }
        }

        item {
            OutlinedButton(
                onClick = { showAging = true },
                enabled = suppliers.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) { Text(stringResource(R.string.purchases_supplier_aging)) }
        }

        item {
            FushSectionHeader(stringResource(R.string.purchases_invoices_header), stringResource(R.string.purchases_invoice_count, filteredPurchases.size, purchases.size))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = purchaseSearch,
                onValueChange = { purchaseSearch = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.purchases_search_invoices)) },
                placeholder = { Text(stringResource(R.string.purchases_search_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
        }

        if (filteredPurchases.isEmpty()) {
            item {
                FushEmptyState(
                    title = if (purchases.isEmpty()) stringResource(R.string.purchases_no_invoices) else stringResource(R.string.common_no_matching_results),
                    detail = if (purchases.isEmpty()) stringResource(R.string.purchases_no_invoices_detail) else stringResource(R.string.common_change_search),
                )
            }
        }

        items(filteredPurchases, key = { it.id }) { purchase ->
            val isCash = purchase.paymentType == "CASH"
            ElevatedCard(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(purchase.invoiceNo, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${purchase.supplierName} • ${formatDate(purchase.invoiceDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        FushStatusPill(if (isCash) cashLabel else creditLabel, if (isCash) FushStatusTone.Success else FushStatusTone.Warning)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(stringResource(R.string.common_total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${formatMoney(purchase.totalOriginal)} ${purchase.currencyCode}", style = MaterialTheme.typography.titleMedium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.common_base_currency), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatMoney(purchase.totalBase), style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { detailInvoice = purchase }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.common_details)) }
                        OutlinedButton(onClick = { returnInvoice = purchase }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.common_return)) }
                    }
                }
            }
        }
    }

    if (showSupplier) {
        AddSupplierDialog(currencies, onDismiss = { showSupplier = false }) { name, phone, currency, days ->
            scope.launch {
                try {
                    val supplier = container.purchaseService.createSupplier(name, phone, currency.code, days, createdBy = user.id)
                    message = "تمت إضافة المورد بالكود ${supplier.code}"
                    showSupplier = false
                } catch (e: Exception) {
                    message = e.message ?: "تعذر إضافة المورد"
                }
            }
        }
    }

    if (showPurchase) {
        PurchaseInvoiceDialog(
            container = container,
            user = user,
            suppliers = suppliers,
            itemsList = itemsList,
            units = units,
            warehouses = warehouses,
            currencies = currencies,
            onDismiss = { showPurchase = false },
            onPosted = { invoiceId ->
                message = "تم ترحيل فاتورة الشراء رقم $invoiceId وتحديث المخزون والقيد المحاسبي"
                showPurchase = false
            },
            onError = { message = it }
        )
    }

    detailInvoice?.let { invoice ->
        PurchaseInvoiceDetailDialog(
            container = container,
            invoiceSummary = invoice,
            itemsList = itemsList,
            units = units,
            warehouses = warehouses,
            onDismiss = { detailInvoice = null }
        )
    }

    returnInvoice?.let { invoice ->
        PurchaseReturnDialog(
            container = container,
            user = user,
            invoice = invoice,
            itemsList = itemsList,
            units = units,
            onDismiss = { returnInvoice = null },
            onPosted = { id ->
                message = "تم ترحيل مرتجع المشتريات رقم $id وتخفيض المخزون وعكس القيد"
                returnInvoice = null
            },
            onError = { message = it }
        )
    }

    accountSupplier?.let { supplier ->
        SupplierAccountDialog(container, supplier, onDismiss = { accountSupplier = null })
    }
    paymentSupplier?.let { supplier ->
        SupplierPaymentDialog(
            container = container,
            user = user,
            supplier = supplier,
            onDismiss = { paymentSupplier = null },
            onPosted = { paymentNo ->
                message = "تم ترحيل دفعة المورد $paymentNo وتحديث حساب الدائنين"
                paymentSupplier = null
            },
            onError = { message = it }
        )
    }
    if (showAging) {
        SupplierAgingDialog(container, onDismiss = { showAging = false })
    }
}

@Composable
private fun PurchaseInvoiceDetailDialog(
    container: AppContainer,
    invoiceSummary: PurchaseInvoiceSummary,
    itemsList: List<ItemEntity>,
    units: List<UnitEntity>,
    warehouses: List<WarehouseEntity>,
    onDismiss: () -> Unit
) {
    val invoice by produceState<PurchaseInvoiceEntity?>(initialValue = null, key1 = invoiceSummary.id) {
        value = container.db.purchaseDao().invoiceById(invoiceSummary.id)
    }
    val purchaseLines by produceState(initialValue = emptyList<PurchaseLineEntity>(), key1 = invoiceSummary.id) {
        value = container.db.purchaseDao().linesForInvoice(invoiceSummary.id)
    }
    val returns by produceState(initialValue = emptyList<PurchaseReturnEntity>(), key1 = invoiceSummary.id) {
        value = container.db.purchaseDao().returnsForInvoice(invoiceSummary.id)
    }
    val supplierPayments by produceState(initialValue = emptyList<SupplierPaymentDetailRow>(), key1 = invoiceSummary.id) {
        value = container.db.purchaseDao().supplierPaymentsForInvoice(invoiceSummary.id)
    }
    val returnedQtyByLine by produceState(initialValue = emptyMap<Long, Double>(), key1 = invoiceSummary.id, key2 = purchaseLines) {
        value = purchaseLines.associate { it.id to container.db.purchaseDao().returnedQuantityForLine(it.id) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f)
        ) {
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("بيان فاتورة المشتريات", style = MaterialTheme.typography.headlineSmall)
                Text(invoiceSummary.invoiceNo, style = MaterialTheme.typography.titleLarge)
                HorizontalDivider()

                invoice?.let { row ->
                    val warehouse = warehouses.firstOrNull { it.id == row.warehouseId }
                    Text("بيانات الفاتورة", style = MaterialTheme.typography.titleMedium)
                    Text("المورد: ${invoiceSummary.supplierName}")
                    if (row.supplierInvoiceNo.isNotBlank()) Text("رقم فاتورة المورد: ${row.supplierInvoiceNo}")
                    Text("التاريخ: ${formatDate(row.invoiceDate)}")
                    row.dueDate?.let { Text("تاريخ الاستحقاق: ${formatDate(it)}") }
                    Text("نوع الشراء: ${if (row.paymentType == "CASH") "نقدي" else "آجل"} • الحالة: ${if (row.status == "POSTED") "مرحلة" else row.status}")
                    Text("المخزن: ${warehouse?.nameAr ?: "#${row.warehouseId}"}${warehouse?.code?.let { " • $it" } ?: ""}")
                    Text("العملة: ${row.currencyCode} • سعر الصرف: ${formatMoneyDetailed(row.exchangeRate)}")
                    if (row.notes.isNotBlank()) Text("ملاحظات: ${row.notes}")

                    HorizontalDivider()
                    Text("تفاصيل الأصناف (${purchaseLines.size})", style = MaterialTheme.typography.titleMedium)
                    if (purchaseLines.isEmpty()) FushInlineState("لا توجد أسطر محفوظة لهذه الفاتورة.")
                    purchaseLines.forEachIndexed { index, line ->
                        val item = itemsList.firstOrNull { it.id == line.itemId }
                        val unit = units.firstOrNull { it.id == line.unitId }
                        val returnedQty = returnedQtyByLine[line.id] ?: 0.0
                        val remainingQty = (line.quantity - returnedQty).coerceAtLeast(0.0)
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${index + 1}. ${item?.nameAr ?: "صنف #${line.itemId}"}", style = MaterialTheme.typography.titleSmall)
                                item?.let { Text("الكود: ${it.code}", style = MaterialTheme.typography.bodySmall) }
                                Text("الكمية: ${formatMoney(line.quantity)} ${unit?.nameAr ?: "وحدة"} • الأساسية: ${formatMoney(line.baseQuantity)}")
                                if (kotlin.math.abs(line.factorToBase - 1.0) > 0.000001) {
                                    Text("معامل التحويل للوحدة الأساسية: ${formatMoneyDetailed(line.factorToBase)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("سعر الوحدة: ${formatMoney(line.unitPriceOriginal)} ${row.currencyCode}")
                                Text("إجمالي السطر: ${formatMoney(line.lineTotalOriginal)} ${row.currencyCode}")
                                Text("تكلفة الوحدة الأساسية التاريخية: ${formatMoneyDetailed(line.unitCostBase)} • تكلفة السطر الأساسية: ${formatMoney(line.baseQuantity * line.unitCostBase)}", style = MaterialTheme.typography.bodySmall)
                                if (!line.lotNo.isNullOrBlank()) Text("التشغيلة/الدفعة: ${line.lotNo}")
                                line.expiryDate?.let { Text("الصلاحية: ${formatDate(it)}") }
                                if (returnedQty > 0.000001) {
                                    Text("المرتجع: ${formatMoney(returnedQty)} ${unit?.nameAr ?: "وحدة"} • المتبقي من الشراء: ${formatMoney(remainingQty)}", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    Text("الملخص المالي", style = MaterialTheme.typography.titleMedium)
                    Text("الإجمالي قبل التسويات: ${formatMoney(row.subtotalOriginal)} ${row.currencyCode}")
                    Text("إجمالي الفاتورة: ${formatMoney(row.totalOriginal)} ${row.currencyCode}", style = MaterialTheme.typography.titleMedium)
                    Text("الإجمالي بالعملة الأساسية: ${formatMoney(row.totalBase)}")
                    val returnedOriginal = returns.sumOf { it.totalOriginal }
                    val returnedBase = returns.sumOf { it.totalBase }
                    if (returnedBase > 0.000001) {
                        Text("إجمالي المرتجعات: ${formatMoney(returnedOriginal)} ${row.currencyCode} (${formatMoney(returnedBase)} أساسي)", color = MaterialTheme.colorScheme.error)
                    }
                    Text("صافي المشتريات بعد المرتجعات: ${formatMoney((row.totalBase - returnedBase).coerceAtLeast(0.0))} بالعملة الأساسية")
                    if (row.paymentType == "CREDIT") {
                        val supplierCreditReturns = returns.filter { it.settlementType == "SUPPLIER_CREDIT" }.sumOf { it.totalBase }
                        val paidBase = supplierPayments.sumOf { it.allocatedBase }
                        val payableBase = (row.totalBase - supplierCreditReturns).coerceAtLeast(0.0)
                        Text("المسدد للمورد: ${formatMoney(paidBase)} بالعملة الأساسية")
                        Text("المتبقي للمورد: ${formatMoney((payableBase - paidBase).coerceAtLeast(0.0))} بالعملة الأساسية", style = MaterialTheme.typography.titleMedium)
                    }

                    if (supplierPayments.isNotEmpty()) {
                        HorizontalDivider()
                        Text("دفعات المورد", style = MaterialTheme.typography.titleMedium)
                        supplierPayments.forEach { payment ->
                            Text("${payment.paymentNo} • ${formatDate(payment.paymentDate)} • ${formatMoney(payment.amountOriginal)} ${payment.currencyCode} • ${payment.treasuryName}")
                        }
                    }

                    if (returns.isNotEmpty()) {
                        HorizontalDivider()
                        Text("المرتجعات", style = MaterialTheme.typography.titleMedium)
                        returns.forEach { purchaseReturn ->
                            val settlement = when (purchaseReturn.settlementType) {
                                "CASH_REFUND" -> "استرداد نقدي"
                                "SUPPLIER_CREDIT" -> "تخفيض رصيد المورد"
                                else -> purchaseReturn.settlementType
                            }
                            Text("${purchaseReturn.returnNo} • ${formatDate(purchaseReturn.returnDate)} • ${formatMoney(purchaseReturn.totalOriginal)} ${purchaseReturn.currencyCode} • $settlement${purchaseReturn.reason.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""}")
                        }
                    }
                } ?: Text("جارٍ تحميل تفاصيل الفاتورة...")

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) { Text("إغلاق") }
                }
            }
        }
    }
}

@Composable
private fun AddSupplierDialog(
    currencies: List<CurrencyEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, CurrencyEntity, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf<CurrencyEntity?>(null) }
    var daysText by remember { mutableStateOf("0") }
    LaunchedEffect(currencies) { if (currency == null) currency = currencies.firstOrNull { it.isBase } ?: currencies.firstOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مورد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("كود المورد: ينشئه النظام تلقائياً عند الحفظ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(name, { name = it }, label = { Text("اسم المورد") }, singleLine = true)
                FushPhoneField(phone, { phone = it }, modifier = Modifier.fillMaxWidth())
                SelectionField("العملة", currency?.nameAr ?: "اختر", currencies, { it.nameAr }) { currency = it }
                FushIntegerField(daysText, { daysText = it.filter { c -> c.isDigit() } }, "مدة السداد بالأيام", modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && currency != null && daysText.toIntOrNull() != null,
                onClick = { onSave(name, phone, currency!!, daysText.toInt()) }
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun PurchaseInvoiceDialog(
    container: AppContainer,
    user: UserEntity,
    suppliers: List<SupplierEntity>,
    itemsList: List<ItemEntity>,
    units: List<UnitEntity>,
    warehouses: List<WarehouseEntity>,
    currencies: List<CurrencyEntity>,
    onDismiss: () -> Unit,
    onPosted: (Long) -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val treasuryAccounts by container.db.accountingDao().observeTreasuryBalances().collectAsState(initial = emptyList())
    var supplier by remember { mutableStateOf<SupplierEntity?>(null) }
    var warehouse by remember { mutableStateOf<WarehouseEntity?>(null) }
    var currency by remember { mutableStateOf<CurrencyEntity?>(null) }
    var treasury by remember { mutableStateOf<TreasuryBalanceRow?>(null) }
    var paymentType by remember { mutableStateOf("CASH") }
    var exchangeRateText by remember { mutableStateOf("1") }
    var supplierInvoiceNo by remember { mutableStateOf("") }
    var invoiceDateText by remember { mutableStateOf(formatDate(System.currentTimeMillis())) }
    var item by remember { mutableStateOf<ItemEntity?>(null) }
    var conversion by remember { mutableStateOf<ItemUnitConversionEntity?>(null) }
    var conversions by remember { mutableStateOf<List<ItemUnitConversionEntity>>(emptyList()) }
    var quantityText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var lotNoText by remember { mutableStateOf("") }
    var expiryText by remember { mutableStateOf("") }
    var lastPurchasePrice by remember { mutableStateOf<LastPurchasePriceRow?>(null) }
    var loadingLastPrice by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf<PurchaseLineUi>() }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(suppliers) { if (supplier == null) supplier = suppliers.firstOrNull() }
    LaunchedEffect(warehouses) { if (warehouse == null) warehouse = warehouses.firstOrNull { it.code == "RM" } ?: warehouses.firstOrNull() }
    LaunchedEffect(currencies, supplier?.currencyCode) {
        currency = currencies.firstOrNull { it.code == supplier?.currencyCode } ?: currencies.firstOrNull { it.isBase } ?: currencies.firstOrNull()
        exchangeRateText = if (currency?.isBase == true) "1" else exchangeRateText
    }
    LaunchedEffect(currency?.code, treasuryAccounts) {
        val options = treasuryAccounts.filter { it.currencyCode == currency?.code }
        if (treasury?.id !in options.map { it.id }) treasury = options.firstOrNull()
    }
    LaunchedEffect(itemsList) { if (item == null) item = itemsList.firstOrNull { it.category != "FINISHED_GOOD" } }
    LaunchedEffect(item?.id) {
        conversions = item?.let { container.db.itemUnitConversionDao().forItem(it.id).filter { c -> c.allowPurchase } } ?: emptyList()
        conversion = conversions.firstOrNull()
    }
    LaunchedEffect(item?.id, conversion?.unitId, currency?.code) {
        val selectedItem = item
        val selectedConversion = conversion
        val selectedCurrency = currency
        if (selectedItem == null || selectedConversion == null || selectedCurrency == null) {
            lastPurchasePrice = null
        } else {
            loadingLastPrice = true
            try {
                lastPurchasePrice = container.db.purchaseDao().lastPurchasePrice(
                    itemId = selectedItem.id,
                    unitId = selectedConversion.unitId,
                    currencyCode = selectedCurrency.code
                )
            } finally {
                loadingLastPrice = false
            }
        }
    }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("فاتورة شراء جديدة", style = MaterialTheme.typography.headlineSmall)
                Text("رقم فاتورة النظام ينشأ تلقائياً عند الترحيل (PINV-YYYYMMDD-####).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                SelectionField("المورد", supplier?.nameAr ?: "اختر", suppliers, { it.nameAr }) { supplier = it }
                OutlinedTextField(supplierInvoiceNo, { supplierInvoiceNo = it }, label = { Text("رقم فاتورة المورد - اختياري") }, singleLine = true)
                FushDateField(invoiceDateText, { invoiceDateText = it }, "تاريخ الفاتورة", modifier = Modifier.fillMaxWidth())
                SelectionField("المخزن", warehouse?.nameAr ?: "اختر", warehouses, { it.nameAr }) { warehouse = it }
                SelectionField("العملة", currency?.nameAr ?: "اختر", currencies, { it.nameAr }) { currency = it; if (it.isBase) exchangeRateText = "1" }
                FushDecimalField(exchangeRateText, { exchangeRateText = it }, "سعر الصرف إلى الريال الجديد", modifier = Modifier.fillMaxWidth(), enabled = currency?.isBase != true)
                StringSelectionField("السداد", if (paymentType == "CASH") "نقدي" else "آجل", listOf("CASH", "CREDIT")) { paymentType = it }
                if (paymentType == "CASH") {
                    val cashOptions = treasuryAccounts.filter { it.currencyCode == currency?.code }
                    SelectionField("الخزينة / البنك", treasury?.nameAr ?: "اختر", cashOptions, { "${it.nameAr} • ${it.currencyCode}" }) { treasury = it }
                    if (cashOptions.isEmpty()) Text("لا توجد خزينة أو حساب بنكي نشط بعملة الفاتورة.", color = MaterialTheme.colorScheme.error)
                }
                HorizontalDivider()
                Text("إضافة صنف للفاتورة", style = MaterialTheme.typography.titleMedium)
                SelectionField("الصنف", item?.nameAr ?: "اختر", itemsList.filter { it.category != "FINISHED_GOOD" }, { it.nameAr }) { item = it }
                SelectionField(
                    "الوحدة",
                    conversion?.let { c -> units.firstOrNull { it.id == c.unitId }?.nameAr } ?: "اختر",
                    conversions,
                    { c -> units.firstOrNull { it.id == c.unitId }?.nameAr ?: "وحدة" }
                ) { conversion = it }
                FushDecimalField(quantityText, { quantityText = it }, "الكمية", modifier = Modifier.fillMaxWidth())
                FushDecimalField(priceText, { priceText = it }, "سعر الوحدة بالعملة المختارة", modifier = Modifier.fillMaxWidth())
                if (loadingLastPrice) {
                    Text("جارٍ جلب آخر سعر شراء...", style = MaterialTheme.typography.bodySmall)
                } else {
                    val previous = lastPurchasePrice
                    val selectedUnitName = conversion?.let { c -> units.firstOrNull { it.id == c.unitId }?.nameAr } ?: "الوحدة"
                    if (previous == null) {
                        Text(
                            "لا يوجد شراء سابق لنفس الصنف بهذه الوحدة والعملة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "آخر سعر شراء: ${formatMoney(previous.unitPriceOriginal)} ${previous.currencyCode} / $selectedUnitName • ${formatDate(previous.invoiceDate)} • ${previous.supplierName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        priceText.toDoubleOrNull()?.takeIf { it >= 0.0 }?.let { currentPrice ->
                            val variance = PurchaseMath.priceVariance(currentPrice, previous.unitPriceOriginal)
                            val direction = when {
                                variance.amount > 0.0000001 -> "أعلى من آخر شراء"
                                variance.amount < -0.0000001 -> "أقل من آخر شراء"
                                else -> "مطابق لآخر شراء"
                            }
                            val sign = if (variance.amount > 0.0) "+" else ""
                            val pct = variance.percent?.let { " (${if (it > 0) "+" else ""}${String.format(Locale.US, "%.2f", it)}%)" } ?: ""
                            val comparisonColor = when {
                                variance.amount > 0.0000001 -> MaterialTheme.colorScheme.error
                                variance.amount < -0.0000001 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(
                                "فرق السعر: $sign${formatMoney(variance.amount)} ${previous.currencyCode}$pct — $direction",
                                style = MaterialTheme.typography.bodySmall,
                                color = comparisonColor
                            )
                        }
                    }
                }
                OutlinedTextField(lotNoText, { lotNoText = it }, label = { Text("رقم التشغيلة - اختياري") }, singleLine = true)
                FushDateField(expiryText, { expiryText = it }, "تاريخ الصلاحية", modifier = Modifier.fillMaxWidth(), optional = true)
                OutlinedButton(
                    onClick = {
                        try {
                            val q = requireNotNull(quantityText.toDoubleOrNull()) { "أدخل كمية صحيحة" }
                            val p = requireNotNull(priceText.toDoubleOrNull()) { "أدخل سعراً صحيحاً" }
                            require(q > 0 && p >= 0) { "الكمية والسعر غير صالحين" }
                            val i = requireNotNull(item) { "اختر الصنف" }
                            val c = requireNotNull(conversion) { "اختر الوحدة" }
                            val u = requireNotNull(units.firstOrNull { it.id == c.unitId }) { "الوحدة غير موجودة" }
                            val expiry = if (expiryText.isBlank()) null else requireNotNull(parseDate(expiryText)) { "تاريخ الصلاحية غير صحيح" }
                            lines.add(PurchaseLineUi(i, u, c.factorToBase, q, p, lotNoText.trim().ifBlank { null }, expiry))
                            quantityText = ""
                            priceText = ""
                            lotNoText = ""
                            expiryText = ""
                            error = null
                        } catch (e: Exception) { error = e.message }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("إضافة السطر") }

                if (lines.isNotEmpty()) {
                    Text("بنود الفاتورة", style = MaterialTheme.typography.titleMedium)
                    lines.forEachIndexed { index, line ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(line.item.nameAr)
                                    Text("${formatMoney(line.quantity)} ${line.unit.nameAr} × ${formatMoney(line.unitPrice)} = ${formatMoney(line.total)}")
                                    Text("عامل التحويل: ${formatMoney(line.factor)}")
                                    if (!line.lotNo.isNullOrBlank()) Text("التشغيلة: ${line.lotNo}")
                                    line.expiryDate?.let { Text("الصلاحية: ${formatDate(it)}") }
                                }
                                TextButton(onClick = { lines.removeAt(index) }) { Text("حذف") }
                            }
                        }
                    }
                    Text("الإجمالي: ${formatMoney(lines.sumOf { it.total })} ${currency?.code ?: ""}", style = MaterialTheme.typography.titleMedium)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = !saving && lines.isNotEmpty() && supplier != null && warehouse != null && currency != null && (paymentType != "CASH" || treasury != null),
                        onClick = {
                            scope.launch {
                                saving = true
                                try {
                                    val rate = if (currency?.isBase == true) 1.0 else requireNotNull(exchangeRateText.toDoubleOrNull()) { "سعر الصرف غير صالح" }
                                    val invoiceDate = requireNotNull(parseDate(invoiceDateText)) { "تاريخ الفاتورة غير صحيح" }
                                    val id = container.purchaseService.postPurchase(
                                        PurchaseService.PostPurchaseRequest(
                                            supplierId = supplier!!.id,
                                            warehouseId = warehouse!!.id,
                                            currencyCode = currency!!.code,
                                            exchangeRate = rate,
                                            paymentType = paymentType,
                                            supplierInvoiceNo = supplierInvoiceNo,
                                            invoiceDate = invoiceDate,
                                            createdBy = user.id,
                                            lines = lines.map { PurchaseDraftLine(it.item.id, it.unit.id, it.quantity, it.factor, it.unitPrice, it.lotNo, it.expiryDate) },
                                            treasuryAccountId = if (paymentType == "CASH") treasury?.id else null
                                        )
                                    )
                                    onPosted(id)
                                } catch (e: Exception) {
                                    val text = e.message ?: "تعذر ترحيل الفاتورة"
                                    error = text
                                    onError(text)
                                } finally { saving = false }
                            }
                        }
                    ) { Text(if (saving) "جارٍ الترحيل..." else "ترحيل الفاتورة") }
                }
            }
        }
    }
}

@Composable
private fun PurchaseReturnDialog(
    container: AppContainer,
    user: UserEntity,
    invoice: PurchaseInvoiceSummary,
    itemsList: List<ItemEntity>,
    units: List<UnitEntity>,
    onDismiss: () -> Unit,
    onPosted: (Long) -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val treasuryAccounts by container.db.accountingDao().observeTreasuryBalances().collectAsState(initial = emptyList())
    var treasury by remember { mutableStateOf<TreasuryBalanceRow?>(null) }
    val purchaseLines by produceState(initialValue = emptyList<PurchaseLineEntity>(), key1 = invoice.id) {
        value = container.db.purchaseDao().linesForInvoice(invoice.id)
    }
    val returnedQtyByLine by produceState(initialValue = emptyMap<Long, Double>(), key1 = invoice.id, key2 = purchaseLines) {
        value = purchaseLines.associate { it.id to container.db.purchaseDao().returnedQuantityForLine(it.id) }
    }
    val quantities = remember(invoice.id) { mutableStateMapOf<Long, String>() }
    var settlement by remember { mutableStateOf(if (invoice.paymentType == "CASH") "CASH_REFUND" else "SUPPLIER_CREDIT") }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(treasuryAccounts, invoice.currencyCode) {
        val options = treasuryAccounts.filter { it.currencyCode == invoice.currencyCode }
        if (treasury?.id !in options.map { it.id }) treasury = options.firstOrNull()
    }

    val returnableLines = purchaseLines.filter { line ->
        (line.quantity - (returnedQtyByLine[line.id] ?: 0.0)) > 0.000000001
    }
    val selectedDrafts = returnableLines.mapNotNull { line ->
        quantities[line.id]?.toDoubleOrNull()?.takeIf { it > 0.0 }?.let { qty -> PurchaseReturnDraftLine(line.id, qty) }
    }
    val selectedTotal = selectedDrafts.sumOf { draft ->
        val line = purchaseLines.firstOrNull { it.id == draft.purchaseLineId }
        draft.quantity * (line?.unitPriceOriginal ?: 0.0)
    }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("مرتجع مشتريات جزئي", style = MaterialTheme.typography.headlineSmall)
                Text("من الفاتورة ${invoice.invoiceNo} — ${invoice.supplierName}")
                Text("يمكن إرجاع جزء من سطر واحد أو عدة أسطر في مستند مرتجع واحد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

                if (purchaseLines.isEmpty()) {
                    Text("جارٍ تحميل أصناف الفاتورة...")
                } else if (returnableLines.isEmpty()) {
                    FushInlineState("لا توجد كميات متبقية قابلة للمرتجع في هذه الفاتورة.", tone = FushStatusTone.Warning)
                } else {
                    returnableLines.forEach { line ->
                        val itemName = itemsList.firstOrNull { it.id == line.itemId }?.nameAr ?: "صنف #${line.itemId}"
                        val unitName = units.firstOrNull { it.id == line.unitId }?.nameAr ?: "وحدة"
                        val alreadyReturned = returnedQtyByLine[line.id] ?: 0.0
                        val remaining = (line.quantity - alreadyReturned).coerceAtLeast(0.0)
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(itemName, style = MaterialTheme.typography.titleSmall)
                                Text("المشترى: ${formatMoney(line.quantity)} $unitName • سبق إرجاع: ${formatMoney(alreadyReturned)} • المتاح: ${formatMoney(remaining)}", style = MaterialTheme.typography.bodySmall)
                                if (!line.lotNo.isNullOrBlank()) Text("التشغيلة: ${line.lotNo}", style = MaterialTheme.typography.bodySmall)
                                line.expiryDate?.let { Text("الصلاحية: ${formatDate(it)}", style = MaterialTheme.typography.bodySmall) }
                                OutlinedTextField(
                                    value = quantities[line.id].orEmpty(),
                                    onValueChange = { quantities[line.id] = it },
                                    label = { Text("كمية الإرجاع ($unitName)") },
                                    singleLine = true,
                                    isError = quantities[line.id]?.toDoubleOrNull()?.let { it > remaining + 1e-9 } == true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { quantities[line.id] = remaining.toString() }, enabled = !saving) { Text("إرجاع كل المتبقي") }
                                    if (!quantities[line.id].isNullOrBlank()) {
                                        TextButton(onClick = { quantities[line.id] = "" }, enabled = !saving) { Text("مسح") }
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedDrafts.isNotEmpty()) {
                    Text("إجمالي المرتجع المحدد: ${formatMoney(selectedTotal)} ${invoice.currencyCode}", style = MaterialTheme.typography.titleMedium)
                }
                StringSelectionField("التسوية", if (settlement == "CASH_REFUND") "استرداد نقدي" else "تخفيض رصيد المورد", listOf("CASH_REFUND", "SUPPLIER_CREDIT")) { settlement = it }
                if (settlement == "CASH_REFUND") {
                    val refundOptions = treasuryAccounts.filter { it.currencyCode == invoice.currencyCode }
                    SelectionField("الخزينة / البنك للاسترداد", treasury?.nameAr ?: "اختر", refundOptions, { "${it.nameAr} • ${it.currencyCode}" }) { treasury = it }
                    if (refundOptions.isEmpty()) Text("لا توجد خزينة أو حساب بنكي نشط بعملة الفاتورة.", color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(reason, { reason = it }, label = { Text("سبب المرتجع (مطلوب)") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                val quantitiesWithinLimits = selectedDrafts.all { draft ->
                    val line = purchaseLines.first { it.id == draft.purchaseLineId }
                    val remaining = (line.quantity - (returnedQtyByLine[line.id] ?: 0.0)).coerceAtLeast(0.0)
                    draft.quantity <= remaining + 1e-9
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = !saving && selectedDrafts.isNotEmpty() && quantitiesWithinLimits && reason.isNotBlank() && (settlement != "CASH_REFUND" || treasury != null),
                        onClick = {
                            scope.launch {
                                saving = true
                                error = null
                                try {
                                    val id = container.purchaseService.postPurchaseReturn(
                                        PurchaseService.PostPurchaseReturnRequest(
                                            purchaseInvoiceId = invoice.id,
                                            settlementType = settlement,
                                            reason = reason,
                                            createdBy = user.id,
                                            lines = selectedDrafts,
                                            treasuryAccountId = if (settlement == "CASH_REFUND") treasury?.id else null
                                        )
                                    )
                                    onPosted(id)
                                } catch (e: Exception) {
                                    val text = e.message ?: "تعذر ترحيل المرتجع"
                                    error = text
                                    onError(text)
                                } finally { saving = false }
                            }
                        }
                    ) { Text(if (saving) "جارٍ الترحيل..." else "ترحيل المرتجع") }
                }
            }
        }
    }
}



@Composable
private fun SupplierPaymentDialog(
    container: AppContainer,
    user: UserEntity,
    supplier: SupplierEntity,
    onDismiss: () -> Unit,
    onPosted: (String) -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val openInvoices by produceState(initialValue = emptyList<SupplierInvoicePayableRow>(), key1 = supplier.id) {
        value = container.db.purchaseDao().openSupplierInvoices(supplier.id)
    }
    val treasuryAccounts by produceState(initialValue = emptyList<TreasuryAccountEntity>(), key1 = supplier.id) {
        value = container.db.accountingDao().allActiveTreasury()
    }
    var invoice by remember { mutableStateOf<SupplierInvoicePayableRow?>(null) }
    var treasury by remember { mutableStateOf<TreasuryAccountEntity?>(null) }
    var amountText by remember { mutableStateOf("") }
    var rateText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(formatDate(System.currentTimeMillis())) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(openInvoices) {
        if (invoice == null || openInvoices.none { it.invoiceId == invoice?.invoiceId }) invoice = openInvoices.firstOrNull()
    }
    LaunchedEffect(invoice?.invoiceId) {
        invoice?.let {
            rateText = formatMoneyDetailed(it.invoiceExchangeRate)
            val matching = treasuryAccounts.filter { t -> t.currencyCode == it.currencyCode }
            if (treasury == null || matching.none { t -> t.id == treasury?.id }) treasury = matching.firstOrNull()
        }
    }
    LaunchedEffect(treasuryAccounts, invoice?.currencyCode) {
        val currency = invoice?.currencyCode ?: return@LaunchedEffect
        val matching = treasuryAccounts.filter { it.currencyCode == currency }
        if (treasury == null || matching.none { it.id == treasury?.id }) treasury = matching.firstOrNull()
    }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("دفع مورد", style = MaterialTheme.typography.headlineSmall)
                Text("${supplier.nameAr} • ${supplier.code}")
                if (openInvoices.isEmpty()) {
                    FushInlineState("لا توجد فواتير آجلة مستحقة لهذا المورد.", tone = FushStatusTone.Success)
                } else {
                    SelectionField(
                        "الفاتورة",
                        invoice?.let { "${it.invoiceNo} • متبقي ${formatMoney(it.outstandingBase)}" } ?: "اختر",
                        openInvoices,
                        { "${it.invoiceNo} • ${formatDate(it.invoiceDate)} • متبقي ${formatMoney(it.outstandingBase)}" }
                    ) { selected -> invoice = selected; amountText = ""; error = null }
                    invoice?.let { selected ->
                        val outstandingOriginal = if (selected.invoiceExchangeRate > 0.0) selected.outstandingBase / selected.invoiceExchangeRate else 0.0
                        Text("الرصيد المتبقي: ${formatMoney(selected.outstandingBase)} بالعملة الأساسية ≈ ${formatMoney(outstandingOriginal)} ${selected.currencyCode}")
                        selected.dueDate?.let { Text("تاريخ الاستحقاق: ${formatDate(it)}") }
                        val compatibleTreasury = treasuryAccounts.filter { it.currencyCode == selected.currencyCode }
                        SelectionField(
                            "الصندوق / البنك",
                            treasury?.nameAr ?: "اختر",
                            compatibleTreasury,
                            { "${it.nameAr} • ${it.currencyCode}" }
                        ) { treasury = it }
                        if (compatibleTreasury.isEmpty()) {
                            FushInlineState("لا توجد خزينة نشطة بعملة ${selected.currencyCode}. أنشئها من الحسابات أولًا.", tone = FushStatusTone.Danger)
                        }
                        FushDecimalField(amountText, { amountText = it }, "المبلغ المدفوع (${selected.currencyCode})", modifier = Modifier.fillMaxWidth())
                        FushDecimalField(rateText, { rateText = it }, "سعر الصرف بتاريخ الدفع", modifier = Modifier.fillMaxWidth())
                        Text("يخفّض حساب المورد بسعر الفاتورة التاريخي، وأي فرق عن سعر الدفع يسجل تلقائياً كربح/خسارة فرق عملة.", style = MaterialTheme.typography.bodySmall)
                    }
                    FushDateField(dateText, { dateText = it }, "تاريخ الدفع", modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات - اختياري") })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = !saving && invoice != null && treasury != null && amountText.toDoubleOrNull() != null && rateText.toDoubleOrNull() != null,
                        onClick = {
                            scope.launch {
                                saving = true
                                try {
                                    val selected = requireNotNull(invoice) { "اختر الفاتورة" }
                                    val result = container.purchaseService.postSupplierPayment(
                                        supplierId = supplier.id,
                                        invoiceId = selected.invoiceId,
                                        treasuryAccountId = requireNotNull(treasury).id,
                                        amountOriginal = requireNotNull(amountText.toDoubleOrNull()) { "المبلغ غير صالح" },
                                        paymentExchangeRate = requireNotNull(rateText.toDoubleOrNull()) { "سعر الصرف غير صالح" },
                                        notes = notes,
                                        createdBy = user.id,
                                        paymentDate = requireNotNull(parseDate(dateText)) { "تاريخ الدفع غير صحيح" }
                                    )
                                    onPosted(result.paymentNo)
                                } catch (e: Exception) {
                                    val text = e.message ?: "تعذر ترحيل دفعة المورد"
                                    error = text
                                    onError(text)
                                } finally { saving = false }
                            }
                        }
                    ) { Text(if (saving) "جارٍ الترحيل..." else "ترحيل الدفع") }
                }
            }
        }
    }
}

@Composable
private fun SupplierAccountDialog(
    container: AppContainer,
    supplier: SupplierEntity,
    onDismiss: () -> Unit
) {
    val asOf = remember { System.currentTimeMillis() }
    val events by produceState(initialValue = emptyList<SupplierLedgerEventRow>(), key1 = supplier.id) {
        value = container.db.purchaseDao().supplierLedgerEvents(supplier.id, asOf)
    }
    val openInvoices by produceState(initialValue = emptyList<SupplierInvoicePayableRow>(), key1 = supplier.id) {
        value = container.db.purchaseDao().openSupplierInvoices(supplier.id)
    }
    val payments by produceState(initialValue = emptyList<SupplierPaymentDetailRow>(), key1 = supplier.id) {
        value = container.db.purchaseDao().supplierPayments(supplier.id)
    }
    val running = remember(events) {
        var balance = 0.0
        events.map { event ->
            balance += event.creditBase - event.debitBase
            event to balance
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("كشف حساب المورد", style = MaterialTheme.typography.headlineSmall)
                Text("${supplier.nameAr} • ${supplier.code}")
                Text("الرصيد الحالي: ${formatMoney(running.lastOrNull()?.second ?: 0.0)} بالعملة الأساسية", style = MaterialTheme.typography.titleMedium)
                Text("فواتير مفتوحة: ${openInvoices.size} • دفعات مسجلة: ${payments.size}")
                HorizontalDivider()
                Text("الحركة", style = MaterialTheme.typography.titleMedium)
                if (running.isEmpty()) FushInlineState("لا توجد حركة دائنة لهذا المورد.")
                running.forEach { (event, balance) ->
                    val type = when (event.eventType) {
                        "INVOICE" -> "فاتورة"
                        "RETURN" -> "مرتجع"
                        "PAYMENT" -> "دفعة"
                        else -> event.eventType
                    }
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${formatDate(event.eventDate)} • $type • ${event.referenceNo}", style = MaterialTheme.typography.titleSmall)
                            Text("مدين: ${formatMoney(event.debitBase)} • دائن: ${formatMoney(event.creditBase)} • الرصيد: ${formatMoney(balance)}")
                            if (event.notes.isNotBlank()) Text(event.notes, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (openInvoices.isNotEmpty()) {
                    HorizontalDivider()
                    Text("الفواتير المفتوحة", style = MaterialTheme.typography.titleMedium)
                    openInvoices.forEach { row ->
                        Text("${row.invoiceNo} • ${formatDate(row.invoiceDate)} • استحقاق ${row.dueDate?.let(::formatDate) ?: "غير محدد"} • متبقي ${formatMoney(row.outstandingBase)}")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) { Text("إغلاق") }
                }
            }
        }
    }
}

@Composable
private fun SupplierAgingDialog(container: AppContainer, onDismiss: () -> Unit) {
    val asOf = remember { System.currentTimeMillis() }
    val rows by produceState(initialValue = emptyList<SupplierAgingRow>(), key1 = asOf) {
        value = container.db.purchaseDao().supplierAging(asOf)
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("أعمار ديون الموردين AP Aging", style = MaterialTheme.typography.headlineSmall)
                Text("حتى ${formatDate(asOf)} • جميع القيم بالعملة الأساسية")
                if (rows.isEmpty()) FushInlineState("لا توجد أرصدة موردين مفتوحة.", tone = FushStatusTone.Success)
                rows.forEach { row ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(row.supplierName, style = MaterialTheme.typography.titleMedium)
                            Text("غير مستحق: ${formatMoney(row.currentBase)}")
                            Text("1–30 يوم: ${formatMoney(row.days1To30Base)} • 31–60: ${formatMoney(row.days31To60Base)}")
                            Text("61–90 يوم: ${formatMoney(row.days61To90Base)} • أكثر من 90: ${formatMoney(row.over90Base)}")
                            Text("الإجمالي: ${formatMoney(row.totalOutstandingBase)}", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                if (rows.isNotEmpty()) {
                    HorizontalDivider()
                    Text("إجمالي الدائنين المفتوح: ${formatMoney(rows.sumOf { it.totalOutstandingBase })}", style = MaterialTheme.typography.titleMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button(onClick = onDismiss) { Text("إغلاق") } }
            }
        }
    }
}

private fun formatMoney(value: Double): String = if (kotlin.math.abs(value - value.toLong()) < 0.000001) value.toLong().toString() else "%.2f".format(Locale.US, value)
private fun formatMoneyDetailed(value: Double): String = if (kotlin.math.abs(value - value.toLong()) < 0.000001) value.toLong().toString() else "%.4f".format(Locale.US, value)

private fun formatDate(value: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(value))

private fun parseDate(value: String): Long? = try {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)?.time
} catch (_: Exception) { null }
