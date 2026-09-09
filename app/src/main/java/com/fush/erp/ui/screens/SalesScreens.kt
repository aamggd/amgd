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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fush.erp.R
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.*
import com.fush.erp.ui.*
import com.fush.erp.ui.export.ReportExportSupport
import com.fush.erp.ui.export.SalesReceiptPrintSupport
import com.fush.erp.ui.export.SalesInvoicePrintSupport
import com.fush.erp.domain.BusinessDatePolicy
import com.fush.erp.domain.AdditionalChargesService
import com.fush.erp.domain.SalesDraftLine
import com.fush.erp.domain.SalesMath
import com.fush.erp.domain.SalesExchangeRatePolicy
import com.fush.erp.domain.SalesFreeQuantityPolicy
import com.fush.erp.domain.SalesService
import com.fush.erp.domain.ShipmentService
import com.fush.erp.domain.SecurityPermissions
import com.fush.erp.domain.TreasuryVoucherOperationIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class AdditionalChargeDraftUi(
    val type: AdditionalChargeTypeEntity,
    val description: String,
    val amountOriginal: Double,
    val currency: CurrencyEntity,
    val exchangeRate: Double,
    val bearer: String,
    val paymentStatus: String,
    val paidBy: String,
    val paidAmountOriginal: Double,
    val treasuryAccountId: Long?,
    val paymentDate: Long?,
    val paymentReference: String,
    val accountingTreatment: String,
    val notes: String,
) {
    fun toDomain(): AdditionalChargesService.ChargeDraft = AdditionalChargesService.ChargeDraft(
        chargeTypeId = type.id,
        description = description,
        amountOriginal = amountOriginal,
        currencyCode = currency.code,
        exchangeRate = exchangeRate,
        bearer = bearer,
        paymentStatus = paymentStatus,
        paidBy = paidBy,
        paidAmountOriginal = paidAmountOriginal,
        treasuryAccountId = treasuryAccountId,
        paymentDate = paymentDate,
        paymentReference = paymentReference,
        accountingTreatment = accountingTreatment,
        notes = notes,
    )
}

private data class ExistingChargeAllocationUi(
    val row: AdditionalChargeAvailableRow,
    val amountOriginal: Double,
)

private data class ShipmentItemDraftUi(
    val item: ItemEntity,
    val lotNo: String,
    val quantityBase: Double,
)

private data class ShipmentSaleChoice(
    val shipment: ShipmentSaleOptionRow?,
    val label: String,
)

private data class SalesLineUi(
    val item: ItemEntity,
    val unit: UnitEntity,
    val factor: Double,
    val quantity: Double,
    val freeQuantity: Double,
    val unitPrice: Double,
    val preferredShipmentId: Long?,
    val preferredShipmentLabel: String,
    val useShipmentTracking: Boolean,
) {
    val gross: Double get() = quantity * unitPrice
    val totalQuantity: Double get() = quantity + freeQuantity
    val soldBaseQuantity: Double get() = quantity * factor
    val freeBaseQuantity: Double get() = freeQuantity * factor
}

@Composable
fun SalesScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val customers by container.db.customerDao().observeAll().collectAsState(initial = emptyList())
    val salesReps by container.db.salesRepresentativeDao().observeActive().collectAsState(initial = emptyList())
    val invoices by container.db.salesDao().observeSummaries().collectAsState(initial = emptyList())
    val itemsList by container.db.itemDao().observeAll().collectAsState(initial = emptyList())
    val units by container.db.unitDao().observeAll().collectAsState(initial = emptyList())
    val warehouses by container.db.warehouseDao().observeAll().collectAsState(initial = emptyList())
    val currencies by container.db.currencyDao().observeAll().collectAsState(initial = emptyList())
    val receivables by container.db.salesDao().observeReceivables(com.fush.erp.domain.TrustedTimeService.now()).collectAsState(initial = emptyList())
    val rolePermissions by container.db.securityDao().observePermissionCodesForRole(user.role).collectAsState(initial = emptyList())
    val canReverseCollection = user.role == "ADMIN" || SecurityPermissions.COLLECTION_POST in rolePermissions
    val canCreateCustomer = user.role == "ADMIN" || SecurityPermissions.CUSTOMERS_CREATE in rolePermissions
    val canConfigureCharges = user.role == "ADMIN" || SecurityPermissions.ACCOUNTING_POST in rolePermissions
    val canManageShipments = user.role == "ADMIN" || SecurityPermissions.SALES_POST in rolePermissions
    var showSale by remember { mutableStateOf(false) }
    var showCustomerSettlement by remember { mutableStateOf(false) }
    var showPreInvoiceCharge by remember { mutableStateOf(false) }
    var showChargeSettings by remember { mutableStateOf(false) }
    var showShipments by remember { mutableStateOf(false) }
    var collectInvoice by remember { mutableStateOf<SalesInvoiceSummary?>(null) }
    var returnInvoice by remember { mutableStateOf<SalesInvoiceSummary?>(null) }
    var detailInvoice by remember { mutableStateOf<SalesInvoiceSummary?>(null) }
    var invoiceSearch by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingReceiptPrintId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(pendingReceiptPrintId) {
        val receiptId = pendingReceiptPrintId ?: return@LaunchedEffect
        try {
            val receipt = requireNotNull(container.db.salesDao().receiptById(receiptId)) { "سند التحصيل غير موجود" }
            val document = SalesReceiptPrintSupport.document(container, receiptId)
            ReportExportSupport.printPreview(context, document, SalesReceiptPrintSupport.jobName(receipt.receiptNo))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "تعذر فتح معاينة طباعة سند التحصيل"
        } finally {
            if (pendingReceiptPrintId == receiptId) pendingReceiptPrintId = null
        }
    }
    val cashLabel = stringResource(R.string.common_cash)
    val creditLabel = stringResource(R.string.common_credit)
    val filteredInvoices = remember(invoices, invoiceSearch, cashLabel, creditLabel) {
        val q = invoiceSearch.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) invoices else invoices.filter { invoice ->
            listOf(
                invoice.invoiceNo,
                invoice.customerName,
                invoice.currencyCode,
                if (invoice.paymentType == "CASH") cashLabel else creditLabel,
            ).any { it.lowercase(Locale.ROOT).contains(q) }
        }
    }
    val totalInvoiceBase = remember(invoices) { invoices.sumOf { it.totalBase } }
    val totalOutstandingBase = remember(receivables) { receivables.sumOf { it.outstandingBase } }
    val overdueBase = remember(receivables) { receivables.sumOf { it.overdueBase } }
    val openCreditInvoices = remember(invoices) { invoices.count { it.paymentType == "CREDIT" && it.outstandingBase > 0.000001 } }

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
                    title = stringResource(R.string.sales_title),
                    subtitle = stringResource(R.string.sales_subtitle),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { showSale = true },
                        enabled = customers.isNotEmpty() || canCreateCustomer,
                        shape = MaterialTheme.shapes.medium,
                    ) { Text(stringResource(R.string.sales_new_invoice)) }
                    OutlinedButton(
                        onClick = { showCustomerSettlement = true },
                        enabled = customers.isNotEmpty(),
                        shape = MaterialTheme.shapes.medium,
                    ) { Text(stringResource(R.string.sales_customer_collection)) }
                    OutlinedButton(
                        onClick = { showPreInvoiceCharge = true },
                        enabled = customers.isNotEmpty(),
                        shape = MaterialTheme.shapes.medium,
                    ) { Text("رسم / دفعة قبل الفاتورة") }
                    if (canManageShipments) {
                        OutlinedButton(onClick = { showShipments = true }) { Text("الشحنات وتكلفة النقل") }
                    }
                    if (canConfigureCharges) {
                        TextButton(onClick = { showChargeSettings = true }) { Text("إعدادات الرسوم") }
                    }
                }
            }
        }

        if (customers.isEmpty()) {
            item {
                FushSystemState(
                    title = stringResource(R.string.sales_no_customers),
                    detail = stringResource(R.string.sales_create_customer_first),
                )
            }
        }

        if (message != null) {
            item { FushOperationMessage(message, onConsumed = { message = null }) }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(
                    label = stringResource(R.string.sales_total_invoices),
                    value = salesMoney(totalInvoiceBase),
                    helper = stringResource(R.string.common_base_currency),
                    modifier = Modifier.weight(1f),
                    tone = FushStatusTone.Info,
                )
                FushMetricCard(
                    label = stringResource(R.string.sales_open_receivables),
                    value = salesMoney(totalOutstandingBase),
                    helper = stringResource(R.string.sales_customer_balances),
                    modifier = Modifier.weight(1f),
                    tone = if (totalOutstandingBase > 0.000001) FushStatusTone.Warning else FushStatusTone.Success,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(
                    label = stringResource(R.string.sales_overdue_receivables),
                    value = salesMoney(overdueBase),
                    helper = stringResource(R.string.common_needs_follow_up),
                    modifier = Modifier.weight(1f),
                    tone = if (overdueBase > 0.000001) FushStatusTone.Danger else FushStatusTone.Success,
                )
                FushMetricCard(
                    label = stringResource(R.string.sales_open_credit_invoices),
                    value = openCreditInvoices.toString(),
                    helper = stringResource(R.string.sales_not_fully_paid),
                    modifier = Modifier.weight(1f),
                    tone = if (openCreditInvoices > 0) FushStatusTone.Warning else FushStatusTone.Success,
                )
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.sales_credit_policy), style = MaterialTheme.typography.titleSmall)
                        FushStatusPill(stringResource(R.string.sales_credit_control), FushStatusTone.Info)
                    }
                    Text(
                        stringResource(R.string.sales_credit_policy_detail),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item {
            FushSectionHeader(stringResource(R.string.sales_invoices_header), stringResource(R.string.sales_invoice_count, filteredInvoices.size, invoices.size))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = invoiceSearch,
                onValueChange = { invoiceSearch = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.sales_search_invoices)) },
                placeholder = { Text(stringResource(R.string.sales_search_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
            )
        }

        if (filteredInvoices.isEmpty()) {
            item {
                FushEmptyState(
                    title = if (invoices.isEmpty()) stringResource(R.string.sales_no_invoices) else stringResource(R.string.common_no_matching_results),
                    detail = if (invoices.isEmpty()) stringResource(R.string.sales_no_invoices_detail) else stringResource(R.string.common_change_search),
                )
            }
        }

        items(filteredInvoices, key = { it.id }) { invoice ->
            val isCredit = invoice.paymentType == "CREDIT"
            val isPaid = invoice.outstandingBase <= 0.000001
            val isOverdue = isCredit && !isPaid && invoice.dueDate != null && invoice.dueDate < com.fush.erp.domain.TrustedTimeService.now()
            val collectionTone = when {
                isPaid -> FushStatusTone.Success
                isOverdue -> FushStatusTone.Danger
                else -> FushStatusTone.Warning
            }
            val collectionLabel = when {
                isPaid -> stringResource(R.string.common_paid)
                isOverdue -> stringResource(R.string.common_overdue)
                else -> stringResource(R.string.common_open)
            }

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
                            Text(invoice.invoiceNo, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${invoice.customerName} • ${salesDate(invoice.invoiceDate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            FushStatusPill(if (isCredit) creditLabel else cashLabel, if (isCredit) FushStatusTone.Warning else FushStatusTone.Success)
                            if (isCredit) FushStatusPill(collectionLabel, collectionTone)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(stringResource(R.string.common_total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${salesMoney(invoice.totalOriginal)} ${invoice.currencyCode}", style = MaterialTheme.typography.titleMedium)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stringResource(R.string.common_base_currency), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(salesMoney(invoice.totalBase), style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    if (isCredit) {
                        Surface(
                            color = if (isOverdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = if (isOverdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(stringResource(R.string.common_due_amount, salesMoney(invoice.outstandingBase)), style = MaterialTheme.typography.labelLarge)
                                Text(invoice.dueDate?.let { stringResource(R.string.common_due_date, salesDate(it)) } ?: stringResource(R.string.common_no_due_date), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { detailInvoice = invoice }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.common_details)) }
                        if (isCredit && !isPaid) {
                            Button(onClick = { collectInvoice = invoice }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.sales_collection)) }
                        }
                        OutlinedButton(onClick = { returnInvoice = invoice }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.common_return)) }
                    }
                }
            }
        }
    }

    if (showSale) {
        SalesInvoiceDialog(
            container = container,
            user = user,
            customers = customers,
            salesReps = salesReps,
            itemsList = itemsList.filter { it.category == "FINISHED_GOOD" },
            units = units,
            warehouses = warehouses,
            currencies = currencies,
            canCreateCustomer = canCreateCustomer,
            onDismiss = { showSale = false },
            onPosted = { result ->
                message = "تم ترحيل ${result.invoiceNo} وتحديث المخزون والقيود. تكلفة المبيعات ${salesMoney(result.costBase)} ريال"
                showSale = false
            },
            onError = { message = it }
        )
    }

    detailInvoice?.let { invoice ->
        SalesInvoiceDetailDialog(
            container = container,
            user = user,
            invoiceSummary = invoice,
            itemsList = itemsList,
            units = units,
            warehouses = warehouses,
            canReverseCollection = canReverseCollection,
            onPrintReceipt = { pendingReceiptPrintId = it },
            onMessage = { message = it },
            onDismiss = { detailInvoice = null }
        )
    }

    if (showPreInvoiceCharge) {
        PreInvoiceAdditionalChargeDialog(
            container = container,
            user = user,
            customers = customers,
            currencies = currencies,
            onDismiss = { showPreInvoiceCharge = false },
            onDone = { text -> message = text; showPreInvoiceCharge = false },
        )
    }

    if (showChargeSettings && canConfigureCharges) {
        AdditionalChargeSettingsDialog(
            container = container,
            user = user,
            onDismiss = { showChargeSettings = false },
            onMessage = { message = it },
        )
    }

    if (showShipments && canManageShipments) {
        ShipmentManagerDialog(
            container = container,
            user = user,
            warehouses = warehouses,
            itemsList = itemsList,
            currencies = currencies,
            invoices = invoices,
            onDismiss = { showShipments = false },
            onMessage = { message = it },
        )
    }

    if (showCustomerSettlement) {
        CustomerCollectionDialog(
            container = container,
            user = user,
            customers = customers,
            currencies = currencies,
            onDismiss = { showCustomerSettlement = false },
            onDone = { text, receiptId ->
                message = text
                showCustomerSettlement = false
                pendingReceiptPrintId = receiptId
            },
        )
    }

    collectInvoice?.let { invoice ->
        ReceiptDialog(container, user, invoice, currencies, onDismiss = { collectInvoice = null }) { result ->
            message = "تم التحصيل ${result.receiptNo} واستحقاق عمولة ${salesMoney(result.commissionBase)} ريال"
            collectInvoice = null
            pendingReceiptPrintId = result.receiptId
        }
    }

    returnInvoice?.let { invoice ->
        SalesReturnDialog(container, user, invoice, itemsList, units, onDismiss = { returnInvoice = null }) { result ->
            message = "تم ${result.returnNo}: مرتجع ${salesMoney(result.totalBase)} ريال، وإلغاء عمولة ${salesMoney(result.commissionReversedBase)} ريال"
            returnInvoice = null
        }
    }
}

@Composable
private fun SalesInvoiceDetailDialog(
    container: AppContainer,
    user: UserEntity,
    invoiceSummary: SalesInvoiceSummary,
    itemsList: List<ItemEntity>,
    units: List<UnitEntity>,
    warehouses: List<WarehouseEntity>,
    canReverseCollection: Boolean,
    onPrintReceipt: (Long) -> Unit,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var invoiceExportMessage by remember { mutableStateOf<String?>(null) }
    var receiptRefresh by remember { mutableIntStateOf(0) }
    var reverseReceipt by remember { mutableStateOf<CustomerReceiptEntity?>(null) }
    val invoice by produceState<SalesInvoiceEntity?>(initialValue = null, key1 = invoiceSummary.id) {
        value = container.db.salesDao().invoiceById(invoiceSummary.id)
    }
    val saleLines by produceState(initialValue = emptyList<SalesLineEntity>(), key1 = invoiceSummary.id) {
        value = container.db.salesDao().linesForInvoice(invoiceSummary.id)
    }
    val receipts by produceState(initialValue = emptyList<CustomerReceiptEntity>(), key1 = invoiceSummary.id, key2 = receiptRefresh) {
        value = container.db.salesDao().receiptsForInvoice(invoiceSummary.id)
    }
    val returns by produceState(initialValue = emptyList<SalesReturnEntity>(), key1 = invoiceSummary.id) {
        value = container.db.salesDao().returnsForInvoice(invoiceSummary.id)
    }
    val netCommissionBase by produceState(initialValue = 0.0, key1 = invoiceSummary.id) {
        value = container.db.salesDao().netCommissionBaseForInvoice(invoiceSummary.id)
    }
    val additionalChargeDetails by produceState(initialValue = emptyList<AdditionalChargeInvoiceDetailRow>(), key1 = invoiceSummary.id) {
        value = container.db.additionalChargesDao().invoiceChargeDetails(invoiceSummary.id)
    }
    val shipmentCostDetails by produceState(initialValue = emptyList<ShipmentInvoiceCostRow>(), key1 = invoiceSummary.id) {
        value = container.db.shipmentDao().invoiceShipmentCosts(invoiceSummary.id)
    }
    val returnedQtyByLine by produceState(initialValue = emptyMap<Long, Double>(), key1 = invoiceSummary.id, key2 = saleLines) {
        value = saleLines.associate { it.id to container.db.salesDao().returnedQuantityForLine(it.id) }
    }
    val returnedFreeQtyByLine by produceState(initialValue = emptyMap<Long, Double>(), key1 = invoiceSummary.id, key2 = saleLines) {
        value = saleLines.associate { it.id to container.db.salesDao().returnedFreeQuantityForLine(it.id) }
    }
    val allocationsByLine by produceState(initialValue = emptyMap<Long, List<SalesAllocationEntity>>(), key1 = invoiceSummary.id, key2 = saleLines) {
        value = saleLines.associate { it.id to container.db.salesDao().allocationsForLine(it.id) }
    }
    val shipmentAllocationsByLine by produceState(initialValue = emptyMap<Long, List<SalesLineShipmentAllocationRow>>(), key1 = invoiceSummary.id, key2 = saleLines) {
        value = saleLines.associate { it.id to container.db.shipmentDao().shipmentAllocationsForSalesLine(it.id) }
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
                Text("بيان فاتورة المبيعات", style = MaterialTheme.typography.headlineSmall)
                Text(invoiceSummary.invoiceNo, style = MaterialTheme.typography.titleLarge)
                HorizontalDivider()

                invoice?.let { row ->
                    val warehouse = warehouses.firstOrNull { it.id == row.warehouseId }
                    Text("بيانات الفاتورة", style = MaterialTheme.typography.titleMedium)
                    Text("العميل: ${invoiceSummary.customerName}")
                    Text("التاريخ: ${salesDate(row.invoiceDate)}${row.dueDate?.let { " • الاستحقاق: ${salesDate(it)}" } ?: ""}")
                    Text("نوع البيع: ${if (row.paymentType == "CASH") "نقدي" else "آجل"} • الحالة: ${if (row.status == "POSTED") "مرحلة" else row.status}")
                    Text("المحافظة: ${row.province} • القناة: ${salesChannelLabel(row.channel)}")
                    if (row.salesRepNameSnapshot.isNotBlank()) Text("مندوب المبيعات: ${row.salesRepNameSnapshot} • العمولة ${salesMoneyRaw(row.salesRepRatePct)}% • حد المجاني ${salesMoneyRaw(row.freeQtyLimitPctSnapshot)}%")
                    if (row.freeQtyApprovedBy != null) Text("تم اعتماد تجاوز حد المجاني • السبب: ${row.freeQtyApprovalReason}", color = MaterialTheme.colorScheme.primary)
                    Text("المخزن: ${warehouse?.nameAr ?: "#${row.warehouseId}"}${warehouse?.code?.let { " • $it" } ?: ""}")
                    Text("العملة: ${row.currencyCode} • سعر الصرف: ${salesMoneyRaw(row.exchangeRate)}")
                    if (row.notes.isNotBlank()) Text("ملاحظات: ${row.notes}")

                    HorizontalDivider()
                    Text("تفاصيل الأصناف (${saleLines.size})", style = MaterialTheme.typography.titleMedium)
                    if (saleLines.isEmpty()) {
                        FushInlineState("لا توجد أسطر محفوظة لهذه الفاتورة.")
                    }
                    saleLines.forEachIndexed { index, line ->
                        val item = itemsList.firstOrNull { it.id == line.itemId }
                        val unit = units.firstOrNull { it.id == line.unitId }
                        val returnedQty = returnedQtyByLine[line.id] ?: 0.0
                        val returnedFreeQty = returnedFreeQtyByLine[line.id] ?: 0.0
                        val remainingQty = (line.quantity - returnedQty).coerceAtLeast(0.0)
                        val remainingFreeQty = (line.freeQuantity - returnedFreeQty).coerceAtLeast(0.0)
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${index + 1}. ${item?.nameAr ?: "صنف #${line.itemId}"}", style = MaterialTheme.typography.titleSmall)
                                item?.let { Text("الكود: ${it.code}", style = MaterialTheme.typography.bodySmall) }
                                Text("المباع: ${salesMoney(line.quantity)} ${unit?.nameAr ?: "وحدة"} • الأساسية: ${salesMoney(line.baseQuantity)}")
                                if (line.freeQuantity > 0.0) Text("المجاني: ${salesMoney(line.freeQuantity)} ${unit?.nameAr ?: "وحدة"} • الأساسية: ${salesMoney(line.freeBaseQuantity)}", color = MaterialTheme.colorScheme.primary)
                                Text("إجمالي الكمية: ${salesMoney(line.quantity + line.freeQuantity)} ${unit?.nameAr ?: "وحدة"}")
                                if (kotlin.math.abs(line.factorToBase - 1.0) > 0.000001) {
                                    Text("معامل التحويل للوحدة الأساسية: ${salesMoneyRaw(line.factorToBase)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("سعر الوحدة: ${salesMoney(line.unitPriceOriginal)} ${row.currencyCode}")
                                Text("قبل الخصم: ${salesMoney(line.grossOriginal)} • الخصم: ${salesMoney(line.discountOriginal)} • الصافي: ${salesMoney(line.netOriginal)} ${row.currencyCode}")
                                if (returnedQty > 0.000001 || returnedFreeQty > 0.000001) {
                                    Text("مرتجع مباع: ${salesMoney(returnedQty)} • مرتجع مجاني: ${salesMoney(returnedFreeQty)} ${unit?.nameAr ?: "وحدة"}", color = MaterialTheme.colorScheme.error)
                                    Text("المتبقي: مباع ${salesMoney(remainingQty)} • مجاني ${salesMoney(remainingFreeQty)}", style = MaterialTheme.typography.bodySmall)
                                }
                                val allocations = allocationsByLine[line.id].orEmpty()
                                if (allocations.isNotEmpty()) {
                                    Text("التشغيلات المصروفة:", style = MaterialTheme.typography.bodySmall)
                                    allocations.forEach { allocation ->
                                        val lot = allocation.lotNo ?: "بدون تشغيلة"
                                        val expiry = allocation.expiryDate?.let { " • صلاحية ${salesDate(it)}" } ?: ""
                                        Text("• $lot • كمية أساسية ${salesMoney(allocation.quantityBase)}${if (allocation.freeQuantityBase > 0.0) " • مجاني ${salesMoney(allocation.freeQuantityBase)}" else ""}$expiry", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                val shipmentLinks = shipmentAllocationsByLine[line.id].orEmpty()
                                if (shipmentLinks.isNotEmpty()) {
                                    Text("الشحنات المخصصة للسطر:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    shipmentLinks.forEach { link ->
                                        val shipmentInvoiceQty = shipmentAllocationsByLine.values.flatten().filter { it.shipmentId == link.shipmentId }.sumOf { it.quantityBase }
                                        val shipmentInvoiceCost = shipmentCostDetails.filter { it.shipmentId == link.shipmentId }.sumOf { it.allocatedBase }
                                        val lineCostShare = if (shipmentInvoiceQty > 0.000001) shipmentInvoiceCost * link.quantityBase / shipmentInvoiceQty else 0.0
                                        Text("• ${link.shipmentNo} • ${link.destinationProvince} • كمية ${salesMoney(link.quantityBase)} أساسي • حصة شحن ${salesMoney(lineCostShare)} أساسي", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    Text("الملخص المالي", style = MaterialTheme.typography.titleMedium)
                    Text("إجمالي الأصناف قبل الخصم: ${salesMoney(row.grossOriginal)} ${row.currencyCode}")
                    Text("الخصم: ${salesMoney(row.discountOriginal)} ${row.currencyCode} (${salesMoney(row.discountPct)}%)")
                    if (row.transportOriginal != 0.0) Text("النقل: ${salesMoney(row.transportOriginal)} ${row.currencyCode}")
                    if (row.feesOriginal != 0.0) Text("الرسوم: ${salesMoney(row.feesOriginal)} ${row.currencyCode}")
                    if (row.riskMarginOriginal != 0.0) Text("هامش المخاطر: ${salesMoney(row.riskMarginOriginal)} ${row.currencyCode}")
                    if (additionalChargeDetails.isNotEmpty()) {
                        Text("AdditionalCharges — الرسوم والتكاليف الإضافية", style = MaterialTheme.typography.titleMedium)
                        additionalChargeDetails.forEach { charge ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${charge.chargeNo} • ${charge.chargeTypeName} • ${salesMoney(charge.allocatedInvoiceOriginal)} ${row.currencyCode}")
                                    Text("يتحمل: ${chargeBearerLabel(charge.bearer)} • ${chargeTreatmentLabel(charge.accountingTreatment)} • الدفع: ${chargePaymentStatusLabel(charge.paymentStatus)} / ${chargePaidByLabel(charge.paidBy)}", style = MaterialTheme.typography.bodySmall)
                                    charge.paymentAccountName?.let { Text("حساب الدفع: $it${charge.lastPaymentDate?.let { d -> " • ${salesDate(d)}" } ?: ""}${charge.lastPaymentReference?.takeIf { it.isNotBlank() }?.let { r -> " • مرجع $r" } ?: ""}", style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                    }
                    Text("إجمالي الفاتورة: ${salesMoney(row.totalOriginal)} ${row.currencyCode}", style = MaterialTheme.typography.titleMedium)
                    Text("الإجمالي بالعملة الأساسية: ${salesMoney(row.totalBase)}")
                    if (shipmentCostDetails.isNotEmpty()) {
                        HorizontalDivider()
                        Text("الشحنات المرتبطة والتكلفة الفعلية", style = MaterialTheme.typography.titleMedium)
                        shipmentCostDetails.groupBy { it.shipmentId }.forEach { (_, rows) ->
                            val first = rows.first()
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("${first.shipmentNo} • ${first.destinationProvince} • ${salesDate(first.shipmentDate)}")
                                    if (first.transportReference.isNotBlank()) Text("مرجع النقل: ${first.transportReference}", style = MaterialTheme.typography.bodySmall)
                                    rows.forEach { cost ->
                                        Text("${shipmentExpenseTypeLabel(cost.expenseType)}: حصة التكلفة ${salesMoney(cost.allocatedBase)} أساسي • يتحمل ${chargeBearerLabel(cost.bearer)}${if (cost.customerChargeBase > 0.000001) " • أضيف للعميل ${salesMoney(cost.customerChargeBase)} أساسي" else ""} • سند ${cost.paymentVoucherNo}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("إجمالي تكلفة الشحنة المحملة على الفاتورة: ${salesMoney(rows.sumOf { it.allocatedBase })} أساسي", style = MaterialTheme.typography.labelLarge)
                                    val customerShipmentCharge = rows.sumOf { it.customerChargeBase }
                                    if (customerShipmentCharge > 0.000001) Text("المحمّل على العميل من نفس المصروف: ${salesMoney(customerShipmentCharge)} أساسي", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    else Text("هذه الحصة تتحملها الشركة ولا تضاف إلى العميل.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    val returnedOriginal = returns.sumOf { it.totalOriginal }
                    val returnedBase = returns.sumOf { it.totalBase }
                    val externallyPaidBase = additionalChargeDetails.filter { it.bearer == "CUSTOMER" && it.paidBy == "CUSTOMER_DIRECT" }.sumOf { it.allocatedBase }
                    val cashReceivedBase = if (row.paymentType == "CASH") (row.totalBase - externallyPaidBase).coerceAtLeast(0.0) else receipts.sumOf { it.amountBase }
                    val collectionDiscountBase = receipts.sumOf { it.discountBase }
                    Text("المحصل نقدياً بالعملة الأساسية: ${salesMoney(cashReceivedBase)}")
                    if (kotlin.math.abs(collectionDiscountBase) > 0.000001) {
                        Text("خصومات التحصيل: ${salesMoney(collectionDiscountBase)} أساسي")
                        Text("إجمالي تسوية الذمة: ${salesMoney(cashReceivedBase + collectionDiscountBase)} أساسي")
                    }
                    if (returnedBase > 0.000001) {
                        Text("إجمالي المرتجعات: ${salesMoney(returnedOriginal)} ${row.currencyCode} (${salesMoney(returnedBase)} أساسي)", color = MaterialTheme.colorScheme.error)
                    }
                    if (row.paymentType == "CREDIT") {
                        Text("الرصيد المتبقي: ${salesMoney(invoiceSummary.outstandingBase)} أساسي", color = if (invoiceSummary.outstandingBase > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    if (netCommissionBase != 0.0) Text("العمولة الصافية المستحقة: ${salesMoney(netCommissionBase)} أساسي")

                    if (receipts.isNotEmpty()) {
                        HorizontalDivider()
                        Text("التحصيلات", style = MaterialTheme.typography.titleMedium)
                        val reversedReceiptIds = receipts.mapNotNull { it.reversalOfReceiptId }.toSet()
                        receipts.forEach { receipt ->
                            val isReversal = receipt.reversalOfReceiptId != null
                            val isReversedOriginal = receipt.id in reversedReceiptIds
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("${receipt.receiptNo} • ${salesDate(receipt.receiptDate)} • نقدي ${salesMoney(kotlin.math.abs(receipt.amountOriginal))} ${receipt.currencyCode} (${salesMoney(kotlin.math.abs(receipt.amountBase))} أساسي)")
                                if (kotlin.math.abs(receipt.discountOriginal) > 0.000001) {
                                    Text("خصم تحصيل ${salesMoney(kotlin.math.abs(receipt.discountOriginal))} ${receipt.currencyCode} (${salesMoney(kotlin.math.abs(receipt.discountBase))} أساسي) • ${receipt.discountReason}", style = MaterialTheme.typography.bodySmall)
                                }
                                FushStatusPill(
                                    when { isReversal -> "مستند عكس"; isReversedOriginal -> "معكوس"; else -> "مرحل" },
                                    if (isReversal || isReversedOriginal) FushStatusTone.Warning else FushStatusTone.Success,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onPrintReceipt(receipt.id) }) {
                                        Text("طباعة السند")
                                    }
                                    if (canReverseCollection && !isReversal && !isReversedOriginal) {
                                        OutlinedButton(onClick = {
                                            if (canReverseCollection) reverseReceipt = receipt
                                        }) { Text(stringResource(R.string.sales_reverse_collection)) }
                                    }
                                }
                            }
                        }
                    }

                    if (returns.isNotEmpty()) {
                        HorizontalDivider()
                        Text("المرتجعات", style = MaterialTheme.typography.titleMedium)
                        returns.forEach { salesReturn ->
                            Text("${salesReturn.returnNo} • ${salesDate(salesReturn.returnDate)} • ${salesMoney(salesReturn.totalOriginal)} ${salesReturn.currencyCode} • ${salesReturn.reason}")
                        }
                    }
                } ?: Text("جارٍ تحميل تفاصيل الفاتورة...")

                invoice?.let { row ->
                    val warehouse = warehouses.firstOrNull { it.id == row.warehouseId }
                    val document = SalesInvoicePrintSupport.document(
                        invoice = row,
                        summary = invoiceSummary,
                        lines = saleLines,
                        items = itemsList,
                        units = units,
                        warehouse = warehouse,
                        allocationsByLine = allocationsByLine,
                        additionalCharges = additionalChargeDetails,
                        shipmentCosts = shipmentCostDetails,
                    )
                    HorizontalDivider()
                    Text("طباعة وتصدير الفاتورة", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    ReportExportSupport.exportPdf(context, document, SalesInvoicePrintSupport.baseName(row.invoiceNo))
                                }.onSuccess {
                                    invoiceExportMessage = "تم حفظ فاتورة ${row.invoiceNo} PDF في التنزيلات/FushERP"
                                }.onFailure {
                                    invoiceExportMessage = it.message ?: "تعذر حفظ فاتورة PDF"
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("حفظ PDF") }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    ReportExportSupport.sharePdf(context, document, SalesInvoicePrintSupport.baseName(row.invoiceNo))
                                }.onFailure {
                                    invoiceExportMessage = it.message ?: "تعذر مشاركة فاتورة PDF"
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("مشاركة") }
                        Button(
                            onClick = {
                                runCatching {
                                    ReportExportSupport.printPreview(context, document, SalesInvoicePrintSupport.jobName(row.invoiceNo))
                                }.onFailure {
                                    invoiceExportMessage = it.message ?: "تعذر فتح معاينة الطباعة"
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("طباعة") }
                    }
                    invoiceExportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) { Text("إغلاق") }
                }
            }
        }
    }
    if (canReverseCollection) reverseReceipt?.let { receipt ->
        ReverseSalesReceiptDialog(
            container = container,
            user = user,
            receipt = receipt,
            onDismiss = { reverseReceipt = null },
            onDone = { text ->
                reverseReceipt = null
                receiptRefresh += 1
                onMessage(text)
            },
        )
    }
}

@Composable
private fun SalesInvoiceDialog(
    container: AppContainer,
    user: UserEntity,
    customers: List<CustomerEntity>,
    salesReps: List<SalesRepresentativeEntity>,
    itemsList: List<ItemEntity>,
    units: List<UnitEntity>,
    warehouses: List<WarehouseEntity>,
    currencies: List<CurrencyEntity>,
    canCreateCustomer: Boolean,
    onDismiss: () -> Unit,
    onPosted: (SalesService.SalePostResult) -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val treasuryAccounts by container.db.accountingDao().observeTreasuryBalances().collectAsState(initial = emptyList())
    var customer by remember { mutableStateOf<CustomerEntity?>(null) }
    var salesRep by remember { mutableStateOf<SalesRepresentativeEntity?>(null) }
    var warehouse by remember { mutableStateOf<WarehouseEntity?>(null) }
    var currency by remember { mutableStateOf<CurrencyEntity?>(null) }
    var treasury by remember { mutableStateOf<TreasuryBalanceRow?>(null) }
    var paymentType by remember { mutableStateOf("CASH") }
    var exchangeText by remember { mutableStateOf("1") }
    var approvedExchangeRate by remember { mutableStateOf<Double?>(1.0) }
    var exchangeRateOverrideEnabled by remember { mutableStateOf(false) }
    var exchangeRateOverrideReason by remember { mutableStateOf("") }
    var creditDaysText by remember { mutableStateOf("") }
    var discountText by remember { mutableStateOf("0") }
    val chargeTypes by container.db.additionalChargesDao().observeActiveTypes().collectAsState(initial = emptyList())
    val newAdditionalCharges = remember { mutableStateListOf<AdditionalChargeDraftUi>() }
    val existingChargeAllocations = remember { mutableStateListOf<ExistingChargeAllocationUi>() }
    var availableCharges by remember { mutableStateOf<List<AdditionalChargeAvailableRow>>(emptyList()) }
    var showAddAdditionalCharge by remember { mutableStateOf(false) }
    var allocateExistingCharge by remember { mutableStateOf<AdditionalChargeAvailableRow?>(null) }
    var invoiceNoText by remember { mutableStateOf("") }
    var cashReceiptNoText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var item by remember { mutableStateOf<ItemEntity?>(null) }
    var conversion by remember { mutableStateOf<ItemUnitConversionEntity?>(null) }
    var conversions by remember { mutableStateOf<List<ItemUnitConversionEntity>>(emptyList()) }
    var saleDateText by remember { mutableStateOf(salesDate(com.fush.erp.domain.TrustedTimeService.now())) }
    val saleDate = remember(saleDateText) { runCatching { salesParseDate(saleDateText) }.getOrNull() }
    val saleDateIsFuture = saleDate?.let { it > com.fush.erp.domain.TrustedTimeService.now() } == true
    var exchangeRateError by remember { mutableStateOf<String?>(null) }
    var quantityText by remember { mutableStateOf("") }
    var freeQuantityText by remember { mutableStateOf("0") }
    var freeQtyApprovalReason by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var resolvedPrice by remember { mutableStateOf<SalesPriceEntity?>(null) }
    var shipmentOptions by remember { mutableStateOf<List<ShipmentSaleOptionRow>>(emptyList()) }
    var selectedShipmentChoice by remember { mutableStateOf(ShipmentSaleChoice(null, "بدون شحنة — بيع مباشر من المخزن")) }
    val lines = remember { mutableStateListOf<SalesLineUi>() }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showAddCustomer by remember { mutableStateOf(false) }
    val rolePermissions by container.db.securityDao().observePermissionCodesForRole(user.role).collectAsState(initial = emptyList())
    val canApproveFreeQty = user.role == "ADMIN" || SecurityPermissions.SALES_FREE_QTY_APPROVE in rolePermissions
    val canOverrideExchangeRate = user.role == "ADMIN" || SecurityPermissions.EXCHANGE_RATE_OVERRIDE in rolePermissions

    LaunchedEffect(warehouses) { if (warehouse == null) warehouse = warehouses.firstOrNull { it.code == "FG" } ?: warehouses.firstOrNull() }
    LaunchedEffect(itemsList) { if (item == null) item = itemsList.firstOrNull() }
    LaunchedEffect(customer?.id, currencies, salesReps) {
        customer?.let { c ->
            currency = currencies.firstOrNull { it.code == c.currencyCode } ?: currencies.firstOrNull { it.isBase }
            exchangeText = if (currency?.isBase == true) "1" else exchangeText
            paymentType = if (c.allowCredit && c.channel == "DISTRIBUTOR_CREDIT") "CREDIT" else "CASH"
            creditDaysText = c.creditDays.coerceAtLeast(1).toString()
            newAdditionalCharges.clear()
            existingChargeAllocations.clear()
            availableCharges = container.db.additionalChargesDao().availableCustomerCharges(c.id)
            salesRep = c.salesRepId?.let { id -> salesReps.firstOrNull { it.id == id } }
        }
    }
    LaunchedEffect(customer?.id) {
        availableCharges = customer?.let { container.db.additionalChargesDao().availableCustomerCharges(it.id) }.orEmpty()
    }
    LaunchedEffect(currency?.code, saleDate) {
        val cur = currency
        val date = saleDate
        if (cur == null || date == null) {
            exchangeRateError = if (cur != null) "تاريخ الفاتورة غير صالح" else null
            return@LaunchedEffect
        }
        exchangeRateOverrideEnabled = false
        exchangeRateOverrideReason = ""
        try {
            val approved = container.salesService.exchangeRateAt(cur.code, date)
            approvedExchangeRate = approved
            // Keep the canonical 8-decimal accounting value in the editable state.
            // Display-only 4-decimal money formatting must never feed back into posting.
            exchangeText = salesRateRaw(approved)
            exchangeRateError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            approvedExchangeRate = if (cur.isBase) 1.0 else null
            exchangeText = if (cur.isBase) "1" else ""
            exchangeRateError = e.message ?: "لا يوجد سعر صرف معتمد في تاريخ الفاتورة"
        }
    }
    LaunchedEffect(currency?.code, treasuryAccounts) {
        val options = treasuryAccounts.filter { it.currencyCode == currency?.code }
        if (treasury?.id !in options.map { it.id }) treasury = options.firstOrNull()
    }
    LaunchedEffect(item?.id) {
        val i = item
        conversions = if (i == null) emptyList() else container.db.itemUnitConversionDao().forItem(i.id).filter { it.allowSale }
        conversion = conversions.firstOrNull()
    }
    LaunchedEffect(warehouse?.id, item?.id, customer?.governorateId, customer?.province) {
        val w = warehouse
        val i = item
        val province = customer?.province.orEmpty()
        shipmentOptions = if (w == null || i == null) emptyList()
            else ShipmentService(container.db).availableShipmentsForSale(w.id, i.id, province, customer?.governorateId)
        selectedShipmentChoice = shipmentOptions.firstOrNull()?.let { option ->
            ShipmentSaleChoice(option, "${option.shipmentNo} — ${option.destinationProvince}")
        } ?: ShipmentSaleChoice(null, "بدون شحنة — بيع مباشر من المخزن")
    }
    LaunchedEffect(customer?.id, item?.id, currency?.code, conversion?.id, saleDate) {
        val c = customer
        val i = item
        val cur = currency
        val conv = conversion
        val at = saleDate
        if (c == null || i == null || cur == null || conv == null || at == null) {
            resolvedPrice = null
            priceText = ""
            return@LaunchedEffect
        }
        resolvedPrice = container.db.salesDao().latestPrice(i.id, c.channel, c.province, cur.code, salesEndOfDay(at))
        priceText = resolvedPrice?.let { salesMoneyRaw(SalesMath.configuredUnitPrice(it.baseUnitPriceOriginal, conv.factorToBase)) } ?: ""
    }

    val previewDiscountPct = discountText.toDoubleOrNull()
    val invoiceRatePreview = exchangeText.toDoubleOrNull()
    val financialInputsValid = previewDiscountPct?.let { it >= 0.0 && it < 100.0 && it.isFinite() } == true &&
        invoiceRatePreview?.let { it > 0.0 && it.isFinite() } == true
    val exchangeRateOverrideRequired = currency?.isBase != true && approvedExchangeRate != null && invoiceRatePreview != null &&
        SalesExchangeRatePolicy.requiresOverride(invoiceRatePreview, requireNotNull(approvedExchangeRate))
    val previewGross = lines.sumOf { it.gross }
    val previewDiscount = if (financialInputsValid) previewGross * requireNotNull(previewDiscountPct) / 100.0 else 0.0
    val previewCustomerCharges = if (financialInputsValid) {
        val invoiceRate = requireNotNull(invoiceRatePreview)
        newAdditionalCharges.filter { it.bearer == "CUSTOMER" }.sumOf { it.amountOriginal * it.exchangeRate / invoiceRate } +
            existingChargeAllocations.filter { it.row.bearer == "CUSTOMER" }.sumOf { it.amountOriginal * it.row.exchangeRate / invoiceRate }
    } else 0.0
    val previewDirectPaid = if (financialInputsValid) {
        val invoiceRate = requireNotNull(invoiceRatePreview)
        newAdditionalCharges.filter { it.bearer == "CUSTOMER" && it.paidBy == "CUSTOMER_DIRECT" }.sumOf { it.amountOriginal * it.exchangeRate / invoiceRate } +
            existingChargeAllocations.filter { it.row.paidBy == "CUSTOMER_DIRECT" }.sumOf { it.amountOriginal * it.row.exchangeRate / invoiceRate }
    } else 0.0
    val previewCompanyChargesBase = newAdditionalCharges.filter { it.bearer == "COMPANY" }.sumOf { it.amountOriginal * it.exchangeRate }
    val previewShipmentCustomerChargeBase by produceState(
        initialValue = 0.0,
        key1 = lines.toList(),
        key2 = warehouse?.id,
        key3 = customer?.governorateId,
    ) {
        value = runCatching {
            val w = warehouse ?: return@runCatching 0.0
            val province = customer?.province.orEmpty()
            val plannedShipmentQtyByItem = mutableMapOf<Long, Double>()
            val shipmentService = ShipmentService(container.db)
            val plans = lines.map { line ->
                if (!line.useShipmentTracking) {
                    emptyList()
                } else {
                    val plan = shipmentService.planSaleLineShipments(
                        warehouseId = w.id, itemId = line.item.id, province = province,
                        governorateId = customer?.governorateId,
                        requiredQtyBase = line.soldBaseQuantity + line.freeBaseQuantity,
                        preferredShipmentId = line.preferredShipmentId,
                        additionalReservedByShipmentItem = plannedShipmentQtyByItem
                    )
                    plan.forEach { take ->
                        plannedShipmentQtyByItem[take.shipmentItemId] =
                            (plannedShipmentQtyByItem[take.shipmentItemId] ?: 0.0) + take.quantityBase
                    }
                    plan
                }
            }
            ShipmentService(container.db).previewAutomaticInvoiceExpenses(plans).customerChargeBase
        }.getOrDefault(0.0)
    }
    val previewShipmentCustomerCharge = if (financialInputsValid) previewShipmentCustomerChargeBase / requireNotNull(invoiceRatePreview) else 0.0
    val previewFinal = if (financialInputsValid) previewGross - previewDiscount + previewCustomerCharges + previewShipmentCustomerCharge else 0.0
    val previewCollectible = (previewFinal - previewDirectPaid).coerceAtLeast(0.0)
    val previewSoldBase = lines.sumOf { it.soldBaseQuantity }
    val previewFreeBase = lines.sumOf { it.freeBaseQuantity }
    val freeLimitPct = salesRep?.freeQtyLimitPct ?: 0.0
    val freeQtyApprovalRequired = lines.any { line ->
        SalesFreeQuantityPolicy.requiresApproval(line.soldBaseQuantity, line.freeBaseQuantity, freeLimitPct)
    }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("فاتورة بيع", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    value = invoiceNoText,
                    onValueChange = { invoiceNoText = it },
                    label = { Text("رقم الفاتورة") },
                    placeholder = { Text("رقم إلزامي") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("رقم الفاتورة إلزامي ويجب أن يكون فريداً. لن يتم إنشاء الفاتورة بدونه.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                FushDateField(
                    value = saleDateText,
                    onValueChange = { saleDateText = it },
                    label = "تاريخ الفاتورة / العملية",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (saleDate == null) {
                    FushInlineState("تاريخ الفاتورة غير صالح.", tone = FushStatusTone.Danger)
                } else if (saleDateIsFuture) {
                    FushInlineState("لا يمكن ترحيل فاتورة بتاريخ مستقبلي.", tone = FushStatusTone.Danger)
                } else {
                    Text(
                        "تاريخ الإدخال يبقى وقت الإنشاء الفعلي في النظام، بينما المخزون والقيد والترقيم يستخدمون تاريخ الفاتورة المختار.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SalesCustomerSearchField(
                    customers = customers,
                    selected = customer,
                    canCreateCustomer = canCreateCustomer,
                    onSelected = { customer = it },
                    onCreateNew = { showAddCustomer = true },
                )
                if (salesReps.isNotEmpty()) {
                    SalesSelectionField("مندوب المبيعات", salesRep?.let { "${it.code} — ${it.fullNameAr} • ${salesMoneyRaw(it.commissionRatePct)}%" } ?: "بدون مندوب", salesReps, { "${it.code} — ${it.fullNameAr} • ${salesMoneyRaw(it.commissionRatePct)}%" }) { salesRep = it }
                } else {
                    FushInlineState("لا يوجد مندوب مبيعات نشط. يمكن ترحيل الفاتورة بدون مندوب أو إضافته من قسم مناديب المبيعات.", tone = FushStatusTone.Info)
                }
                SalesSelectionField("المخزن", warehouse?.nameAr ?: "اختر", warehouses, { it.nameAr }) { warehouse = it }
                SalesSelectionField("العملة", currency?.nameAr ?: "اختر", currencies, { it.nameAr }) { currency = it }
                if (currency?.isBase != true) {
                    FushDecimalField(
                        exchangeText,
                        { value -> if (exchangeRateOverrideEnabled && canOverrideExchangeRate) exchangeText = value },
                        if (currency?.code == "YER_OLD") "سعر الصرف: 1 ريال قديم = كم ريال جديد" else "سعر الصرف إلى العملة الأساسية",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = exchangeRateOverrideEnabled && canOverrideExchangeRate,
                        isError = exchangeRateError != null,
                    )
                    approvedExchangeRate?.let { approved ->
                        Text(
                            "السعر التاريخي المعتمد: ${salesRateRaw(approved)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (canOverrideExchangeRate && exchangeRateError == null) {
                        OutlinedButton(onClick = {
                            exchangeRateOverrideEnabled = !exchangeRateOverrideEnabled
                            if (!exchangeRateOverrideEnabled) {
                                approvedExchangeRate?.let { exchangeText = salesRateRaw(it) }
                                exchangeRateOverrideReason = ""
                            }
                        }) {
                            Text(if (exchangeRateOverrideEnabled) "إلغاء تغيير سعر الصرف" else "تغيير سعر الصرف")
                        }
                        if (exchangeRateOverrideEnabled) {
                            FushInlineState(
                                "أي سعر مختلف عن السعر التاريخي المعتمد يعتبر استثناءً محاسبياً ويُسجل في سجل التدقيق.",
                                tone = FushStatusTone.Warning,
                            )
                            OutlinedTextField(
                                value = exchangeRateOverrideReason,
                                onValueChange = { exchangeRateOverrideReason = it },
                                label = { Text("سبب تغيير سعر الصرف") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                            )
                        }
                    }
                    if (exchangeRateOverrideRequired) {
                        FushInlineState(
                            if (canOverrideExchangeRate) "سيتم ترحيل الفاتورة بسعر استثنائي بعد تسجيل السبب." else "السعر مختلف عن المعتمد وليس لديك صلاحية EXCHANGE_RATE_OVERRIDE.",
                            tone = FushStatusTone.Warning,
                        )
                    }
                    exchangeRateError?.let {
                        FushInlineState(it, tone = FushStatusTone.Warning)
                    }
                }
                SalesStringSelectionField("نوع البيع", paymentType, if (customer?.allowCredit == true) listOf("CASH", "CREDIT") else listOf("CASH"), { if (it == "CASH") "نقدي" else "آجل" }) { paymentType = it }
                if (paymentType == "CASH") {
                    val cashOptions = treasuryAccounts.filter { it.currencyCode == currency?.code }
                    SalesSelectionField("الخزينة / البنك", treasury?.nameAr ?: "اختر", cashOptions, { "${it.nameAr} • ${it.currencyCode}" }) { treasury = it }
                    OutlinedTextField(
                        value = cashReceiptNoText,
                        onValueChange = { cashReceiptNoText = it },
                        label = { Text("رقم سند التحصيل النقدي") },
                        placeholder = { Text("رقم إلزامي") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (cashOptions.isEmpty()) FushInlineState("لا توجد خزينة أو حساب بنكي نشط بعملة الفاتورة.", tone = FushStatusTone.Warning)
                }
                if (paymentType == "CREDIT") FushIntegerField(creditDaysText, { creditDaysText = it.filter(Char::isDigit) }, "أيام الائتمان", modifier = Modifier.fillMaxWidth())
                FushDecimalField(discountText, { discountText = it }, "الخصم %", modifier = Modifier.fillMaxWidth())
                if (previewDiscountPct != null && previewDiscountPct >= 100.0) {
                    FushInlineState(
                        "خصم 100% غير مسموح. استخدم حقل الكمية المجانية المستقل مع كمية مباعة مدفوعة.",
                        tone = FushStatusTone.Danger,
                    )
                }
                HorizontalDivider()
                Text("AdditionalCharges — الرسوم والتكاليف الإضافية", style = MaterialTheme.typography.titleMedium)
                Text(
                    "النقل والجمارك والتحميل وأي رسوم أخرى تُسجل كسطور مستقلة بسياسة محاسبية قابلة للضبط؛ لا يتم افتراض Principal/Agent من اسم الرسم.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showAddAdditionalCharge = true }, enabled = chargeTypes.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("إضافة رسم جديد") }
                    OutlinedButton(onClick = {
                        scope.launch { availableCharges = customer?.let { container.db.additionalChargesDao().availableCustomerCharges(it.id) }.orEmpty() }
                    }, enabled = customer != null, modifier = Modifier.weight(1f)) { Text("تحديث الرسوم السابقة") }
                }
                if (chargeTypes.isEmpty()) FushInlineState("لا توجد أنواع رسوم نشطة. تحقق من إعدادات الرسوم.", tone = FushStatusTone.Warning)
                newAdditionalCharges.forEachIndexed { index, charge ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("${charge.type.nameAr} • ${salesMoney(charge.amountOriginal)} ${charge.currency.code}", style = MaterialTheme.typography.titleSmall)
                                Text("يتحمل: ${chargeBearerLabel(charge.bearer)} • ${chargeTreatmentLabel(charge.accountingTreatment)} • ${chargePrincipalAgentLabel(charge.type.principalAgentMode)}", style = MaterialTheme.typography.bodySmall)
                                Text("الدفع: ${chargePaymentStatusLabel(charge.paymentStatus)} • ${chargePaidByLabel(charge.paidBy)}${charge.paymentReference.takeIf { it.isNotBlank() }?.let { " • مرجع $it" } ?: ""}", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { newAdditionalCharges.removeAt(index) }) { Text("حذف") }
                        }
                    }
                }
                val alreadyAllocatedIds = existingChargeAllocations.map { it.row.id }.toSet()
                availableCharges.filter { it.id !in alreadyAllocatedIds }.forEach { charge ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${charge.chargeNo} • ${charge.chargeTypeName}", style = MaterialTheme.typography.titleSmall)
                                Text("متبقي للتسوية: ${salesMoney(charge.remainingBase / charge.exchangeRate)} ${charge.currencyCode} • ${chargeTreatmentLabel(charge.accountingTreatment)}", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { allocateExistingCharge = charge }) { Text("ربط جزئي/كامل") }
                        }
                    }
                }
                existingChargeAllocations.forEachIndexed { index, allocation ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("تسوية ${allocation.row.chargeNo} • ${allocation.row.chargeTypeName}", style = MaterialTheme.typography.titleSmall)
                                Text("${salesMoney(allocation.amountOriginal)} ${allocation.row.currencyCode} • المتبقي قبل الربط ${salesMoney(allocation.row.remainingBase / allocation.row.exchangeRate)}", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { existingChargeAllocations.removeAt(index) }) { Text("إزالة") }
                        }
                    }
                }
                HorizontalDivider()
                Text("إضافة صنف", style = MaterialTheme.typography.titleMedium)
                SalesSelectionField("الصنف", item?.nameAr ?: "اختر", itemsList, { it.nameAr }) { item = it }
                SalesSelectionField("الوحدة", conversion?.let { c -> units.firstOrNull { it.id == c.unitId }?.nameAr } ?: "اختر", conversions, { c -> units.firstOrNull { it.id == c.unitId }?.let { "${it.nameAr} × ${salesMoneyRaw(c.factorToBase)}" } ?: "وحدة" }) { conversion = it }
                val shipmentChoices = buildList {
                    add(ShipmentSaleChoice(null, "بدون شحنة — بيع مباشر من المخزن"))
                    shipmentOptions.forEach { option ->
                        val factor = conversion?.factorToBase?.takeIf { it > 0.0 } ?: 1.0
                        val unitName = conversion?.let { c -> units.firstOrNull { it.id == c.unitId }?.nameAr }.orEmpty()
                        add(ShipmentSaleChoice(option, "${option.shipmentNo} — ${option.destinationProvince} — المتاح ${salesMoney(option.remainingQtyBase / factor)} $unitName"))
                    }
                }
                SalesSelectionField(
                    "الشحنة / Shipment (اختياري)",
                    selectedShipmentChoice.label,
                    shipmentChoices,
                    { it.label },
                ) { choice ->
                    selectedShipmentChoice = choice ?: ShipmentSaleChoice(null, "بدون شحنة — بيع مباشر من المخزن")
                }
                if (selectedShipmentChoice.shipment == null) {
                    FushInlineState(
                        "بيع مباشر من المخزن: لن ينشئ النظام تخصيص شحنة أو حصة تكلفة نقل لهذا السطر، وسيُصرف المخزون بالطريقة المعتادة.",
                        tone = FushStatusTone.Info,
                    )
                } else {
                    Text(
                        "الافتراضي: أقدم شحنة متاحة مع تفضيل محافظة العميل. إذا لم تكفِ الشحنة المختارة سيكمل النظام تلقائياً من الشحنات التالية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                FushDecimalField(quantityText, { quantityText = it }, "الكمية المباعة", modifier = Modifier.fillMaxWidth())
                FushDecimalField(freeQuantityText, { freeQuantityText = it }, "الكمية المجانية", modifier = Modifier.fillMaxWidth())
                val draftSold = quantityText.toDoubleOrNull() ?: 0.0
                val draftFree = freeQuantityText.toDoubleOrNull() ?: 0.0
                if (draftSold >= 0.0 && draftFree >= 0.0) {
                    Text("إجمالي الكمية: ${salesMoney(draftSold + draftFree)} ${conversion?.let { c -> units.firstOrNull { it.id == c.unitId }?.nameAr }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it },
                    label = { Text("سعر الوحدة — إدخال يدوي أو سعر مقترح") },
                    singleLine = true
                )
                resolvedPrice?.let { price ->
                    Text(
                        "السعر الظاهر مقترح من القائمة: ${price.province} • ${salesChannelLabel(price.channel)} • من ${salesDate(price.effectiveFrom)}${price.effectiveTo?.let { " إلى ${salesDate(it)}" } ?: ""}. يمكنك تعديله يدويًا قبل إضافة السطر.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } ?: Text(
                    "لا توجد قائمة أسعار سارية لهذه المحافظة/القناة/العملة؛ يمكنك إدخال سعر البيع يدويًا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                OutlinedButton(onClick = {
                    try {
                        val q = requireNotNull(quantityText.toDoubleOrNull()) { "أدخل كمية مباعة صحيحة" }
                        val fq = requireNotNull(freeQuantityText.toDoubleOrNull()) { "أدخل كمية مجانية صحيحة" }
                        val p = requireNotNull(priceText.toDoubleOrNull()) { "أدخل سعر بيع صحيح" }
                        require(q > 0 && fq >= 0 && p > 0) { "الكمية المباعة والسعر يجب أن يكونا أكبر من صفر، والمجاني لا يمكن أن يكون سالباً" }
                        val i = requireNotNull(item) { "اختر الصنف" }
                        val c = requireNotNull(conversion) { "اختر الوحدة" }
                        val u = requireNotNull(units.firstOrNull { it.id == c.unitId }) { "الوحدة غير موجودة" }
                        val ship = selectedShipmentChoice.shipment
                        lines += SalesLineUi(
                            i, u, c.factorToBase, q, fq, p,
                            ship?.shipmentId,
                            ship?.let { "${it.shipmentNo} — ${it.destinationProvince}" } ?: "بدون شحنة — بيع مباشر من المخزن",
                            useShipmentTracking = ship != null,
                        )
                        quantityText = ""; freeQuantityText = "0"; error = null
                    } catch (e: Exception) { error = e.message }
                }, modifier = Modifier.fillMaxWidth()) { Text("إضافة السطر") }

                lines.forEachIndexed { index, line ->
                    val lineDiscount = if (financialInputsValid) line.gross * requireNotNull(previewDiscountPct) / 100.0 else 0.0
                    val lineNet = line.gross - lineDiscount
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(line.item.nameAr, style = MaterialTheme.typography.titleSmall)
                                Text("سعر الوحدة: ${salesMoney(line.unitPrice)} ${currency?.code ?: ""}")
                                Text("المباع: ${salesMoney(line.quantity)} ${line.unit.nameAr}")
                                if (line.freeQuantity > 0.0) Text("المجاني: ${salesMoney(line.freeQuantity)} ${line.unit.nameAr}", color = MaterialTheme.colorScheme.primary)
                                Text("إجمالي الكمية: ${salesMoney(line.totalQuantity)} ${line.unit.nameAr}")
                                Text(
                                    if (line.useShipmentTracking) "الشحنة: ${line.preferredShipmentLabel}" else "المصدر: بيع مباشر من المخزن",
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text("قبل الخصم: ${salesMoney(line.gross)} ${currency?.code ?: ""}")
                                Text("خصم ${salesMoneyRaw(previewDiscountPct ?: 0.0)}%: −${salesMoney(lineDiscount)} ${currency?.code ?: ""}")
                                Text("صافي السطر: ${salesMoney(lineNet)} ${currency?.code ?: ""}", style = MaterialTheme.typography.titleMedium)
                            }
                            TextButton(onClick = { lines.removeAt(index) }) { Text("حذف") }
                        }
                    }
                }
                if (lines.isNotEmpty()) {
                    HorizontalDivider()
                    Text("ملخص الفاتورة قبل الترحيل", style = MaterialTheme.typography.titleMedium)
                    Text("إجمالي الأصناف: ${salesMoney(previewGross)} ${currency?.code ?: ""}")
                    Text("الخصم ${salesMoneyRaw(previewDiscountPct ?: 0.0)}%: −${salesMoney(previewDiscount)} ${currency?.code ?: ""}")
                    Text("رسوم يتحملها العميل: +${salesMoney(previewCustomerCharges)} ${currency?.code ?: ""}")
                    Text("الرسوم/الجمارك: +${salesMoney(previewCustomerCharges)} ${currency?.code ?: ""} — من AdditionalCharges")
                    if (previewShipmentCustomerCharge > 0.000001) Text("حصة نقل/شحن على العميل: +${salesMoney(previewShipmentCustomerCharge)} ${currency?.code ?: ""} — من مصروف الشحنة الفعلي", color = MaterialTheme.colorScheme.primary)
                    if (previewDirectPaid > 0.000001) Text("مدفوع مباشرة بواسطة العميل: −${salesMoney(previewDirectPaid)} ${currency?.code ?: ""} (لا يدخل الصندوق أو الذمم)", color = MaterialTheme.colorScheme.primary)
                    if (previewCompanyChargesBase > 0.000001) Text("مصاريف تتحملها الشركة: ${salesMoney(previewCompanyChargesBase)} أساسي — خارج إجمالي العميل", style = MaterialTheme.typography.bodySmall)
                    Text("إجمالي مستند العميل: ${salesMoney(previewFinal)} ${currency?.code ?: ""}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text("الإجمالي النهائي: ${salesMoney(previewFinal)} ${currency?.code ?: ""}", style = MaterialTheme.typography.titleMedium)
                    Text("المطلوب تحصيله من العميل: ${salesMoney(previewCollectible)} ${currency?.code ?: ""}", style = MaterialTheme.typography.titleMedium)
                    Text("إجمالي المجاني (بالوحدة الأساسية): ${salesMoney(previewFreeBase)}")
                    if (salesRep != null) Text("حد المندوب للمجاني: ${salesMoneyRaw(freeLimitPct)}% من الكمية المباعة", style = MaterialTheme.typography.bodySmall)
                }
                if (freeQtyApprovalRequired) {
                    FushInlineState(
                        "الكمية المجانية تتجاوز حد المندوب في سطر واحد أو أكثر. الحد يطبق على كل صنف مستقلًا لمنع تغطية مجاني صنف بمبيعات صنف آخر. يلزم اعتماد مدير.",
                        tone = FushStatusTone.Warning,
                    )
                    if (canApproveFreeQty) {
                        OutlinedTextField(freeQtyApprovalReason, { freeQtyApprovalReason = it }, label = { Text("سبب اعتماد تجاوز المجاني") }, modifier = Modifier.fillMaxWidth())
                    } else {
                        FushInlineState("ليس لديك صلاحية اعتماد تجاوز حد الكمية المجانية.", tone = FushStatusTone.Danger)
                    }
                }
                if (!financialInputsValid) {
                    FushInlineState("تحقق من الخصم وسعر الصرف والرسوم الإضافية قبل الترحيل.", tone = FushStatusTone.Warning)
                }

                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = !saving && invoiceNoText.isNotBlank() && saleDate != null && !saleDateIsFuture && customer != null && warehouse != null && currency != null && lines.isNotEmpty() && financialInputsValid && exchangeRateError == null && (!exchangeRateOverrideRequired || (canOverrideExchangeRate && exchangeRateOverrideReason.trim().length >= 5)) && (!freeQtyApprovalRequired || (canApproveFreeQty && freeQtyApprovalReason.isNotBlank())) && (paymentType != "CASH" || (treasury != null && cashReceiptNoText.isNotBlank())), onClick = {
                        scope.launch {
                            saving = true
                            try {
                                val rate = if (currency?.isBase == true) 1.0 else requireNotNull(exchangeText.toDoubleOrNull()) { "سعر الصرف غير صالح" }
                                val result = container.salesService.postSale(
                                    SalesService.PostSaleRequest(
                                        customerId = customer!!.id,
                                        warehouseId = warehouse!!.id,
                                        currencyCode = currency!!.code,
                                        exchangeRate = rate,
                                        exchangeRateOverrideReason = if (exchangeRateOverrideRequired) exchangeRateOverrideReason.trim() else "",
                                        paymentType = paymentType,
                                        creditDays = if (paymentType == "CREDIT") requireNotNull(creditDaysText.toIntOrNull()) { "أيام الائتمان غير صالحة" } else 0,
                                        invoiceDate = requireNotNull(saleDate) { "تاريخ الفاتورة غير صالح" },
                                        invoiceNo = invoiceNoText,
                                        cashReceiptNo = if (paymentType == "CASH") cashReceiptNoText else "",
                                        discountPct = requireNotNull(discountText.toDoubleOrNull()) { "الخصم غير صالح" },
                                        transportOriginal = 0.0,
                                        feesOriginal = 0.0,
                                        riskMarginOriginal = 0.0,
                                        additionalCharges = newAdditionalCharges.map { it.toDomain() },
                                        additionalChargeAllocations = existingChargeAllocations.map { AdditionalChargesService.ExistingChargeAllocation(it.row.id, it.amountOriginal) },
                                        notes = notes,
                                        belowFloorApprovedBy = null,
                                        belowFloorReason = "",
                                        salesRepId = salesRep?.id,
                                        freeQtyApprovalReason = if (freeQtyApprovalRequired) freeQtyApprovalReason else "",
                                        createdBy = user.id,
                                        lines = lines.map {
                                            SalesDraftLine(
                                                it.item.id, it.unit.id, it.quantity, it.factor, it.unitPrice, it.freeQuantity,
                                                it.preferredShipmentId, useShipmentTracking = it.useShipmentTracking,
                                            )
                                        },
                                        treasuryAccountId = if (paymentType == "CASH") treasury?.id else null
                                    )
                                )
                                onPosted(result)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                val text = e.message ?: "تعذر ترحيل فاتورة البيع"
                                error = text; onError(text)
                            } finally { saving = false }
                        }
                    }) { Text(if (saving) "جارٍ الترحيل..." else "ترحيل الفاتورة") }
                }
            }
        }
    }

    if (showAddAdditionalCharge) {
        AdditionalChargeDraftDialog(
            container = container,
            chargeDate = saleDate ?: com.fush.erp.domain.TrustedTimeService.now(),
            chargeTypes = chargeTypes,
            currencies = currencies,
            onDismiss = { showAddAdditionalCharge = false },
            onAdd = { row -> newAdditionalCharges += row; showAddAdditionalCharge = false },
        )
    }
    allocateExistingCharge?.let { selected ->
        ExistingChargeAllocationDialog(
            row = selected,
            onDismiss = { allocateExistingCharge = null },
            onAdd = { amount ->
                existingChargeAllocations += ExistingChargeAllocationUi(selected, amount)
                allocateExistingCharge = null
            },
        )
    }

    if (showAddCustomer && canCreateCustomer) {
        AddCustomerPartyDialog(
            container = container,
            currencies = currencies,
            salesReps = salesReps,
            onDismiss = { showAddCustomer = false },
        ) { name, phone, address, governorate, district, area, channel, selectedCurrency, limit, days, credit, rep ->
            scope.launch {
                try {
                    val created = container.salesService.createCustomer(
                        nameAr = name,
                        phone = phone,
                        address = address,
                        province = governorate.nameAr,
                        channel = channel,
                        currencyCode = selectedCurrency.code,
                        creditLimitBase = limit,
                        creditDays = days,
                        allowCredit = credit,
                        salesRepName = rep?.fullNameAr.orEmpty(),
                        createdBy = user.id,
                        salesRepId = rep?.id,
                        governorateId = governorate.id,
                        districtId = district?.id,
                        areaId = area?.id,
                    )
                    customer = created
                    showAddCustomer = false
                    error = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e.message ?: "تعذر إنشاء العميل"
                }
            }
        }
    }
}

@Composable
private fun CustomerCollectionDialog(
    container: AppContainer,
    user: UserEntity,
    customers: List<CustomerEntity>,
    currencies: List<CurrencyEntity>,
    onDismiss: () -> Unit,
    onDone: (String, Long) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val treasuries by container.db.accountingDao().observeTreasuryBalances().collectAsState(initial = emptyList())
    val rolePermissions by container.db.securityDao().observePermissionCodesForRole(user.role).collectAsState(initial = emptyList())
    val canDiscount = user.role == "ADMIN" || SecurityPermissions.COLLECTION_DISCOUNT_POST in rolePermissions
    var customer by remember { mutableStateOf<CustomerEntity?>(null) }
    var receiptDateText by remember { mutableStateOf(salesDate(com.fush.erp.domain.TrustedTimeService.now())) }
    val receiptDate = remember(receiptDateText) { runCatching { salesParseDate(receiptDateText) }.getOrNull() }
    val receiptAsOf = receiptDate?.let { BusinessDatePolicy.endOfBusinessDay(it) }
    val invoices by produceState(initialValue = emptyList<SalesInvoiceSummary>(), key1 = customer?.id, key2 = receiptAsOf) {
        value = if (customer != null && receiptAsOf != null) {
            container.db.salesDao().openInvoiceSummariesAsOf(requireNotNull(customer).id, receiptAsOf)
        } else emptyList()
    }
    var invoice by remember { mutableStateOf<SalesInvoiceSummary?>(null) }
    var receiptCurrency by remember { mutableStateOf<CurrencyEntity?>(null) }
    var treasury by remember { mutableStateOf<TreasuryBalanceRow?>(null) }
    var rate by remember { mutableStateOf("1") }
    var rateError by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("0") }
    var discountReason by remember { mutableStateOf("") }
    var receiptNoText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var autoAllocate by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(invoices) {
        if (invoice == null || invoices.none { it.id == invoice?.id }) invoice = invoices.firstOrNull()
        if (invoices.size <= 1) autoAllocate = false
    }
    LaunchedEffect(customer?.id, currencies, invoice?.id) {
        val preferredCode = customer?.currencyCode ?: invoice?.currencyCode
        val preferred = currencies.firstOrNull { it.isActive && it.code == preferredCode }
        val invoiceCurrency = currencies.firstOrNull { it.isActive && it.code == invoice?.currencyCode }
        if (receiptCurrency == null || receiptCurrency?.isActive != true) {
            receiptCurrency = preferred ?: invoiceCurrency ?: currencies.firstOrNull { it.isActive && it.isBase }
        }
    }
    LaunchedEffect(receiptCurrency?.code, invoice?.id, invoices) {
        if (receiptCurrency?.code != invoice?.currencyCode) autoAllocate = false
    }
    LaunchedEffect(receiptCurrency?.code, treasuries) {
        val options = treasuries.filter { it.currencyCode == receiptCurrency?.code }
        if (treasury?.id !in options.map { it.id }) treasury = options.firstOrNull()
    }
    LaunchedEffect(receiptCurrency?.code, receiptDate) {
        val code = receiptCurrency?.code
        val date = receiptDate
        if (code == null || date == null) {
            rateError = if (code != null) "تاريخ التحصيل غير صالح" else null
            return@LaunchedEffect
        }
        try {
            rate = salesMoneyRaw(container.salesService.exchangeRateAt(code, date))
            rateError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            rate = if (code == "YER_NEW") "1" else ""
            rateError = e.message ?: "لا يوجد سعر صرف معتمد في تاريخ التحصيل"
        }
    }
    LaunchedEffect(invoice?.id, receiptCurrency?.code, rate, autoAllocate, invoices) {
        val inv = invoice ?: return@LaunchedEffect
        val rc = receiptCurrency ?: return@LaunchedEffect
        val receiptRate = rate.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return@LaunchedEffect
        val historicalRate = if (inv.totalOriginal > 0.0) inv.totalBase / inv.totalOriginal else 1.0
        if (autoAllocate && rc.code == inv.currencyCode) {
            val relevant = invoices.filter { it.currencyCode == inv.currencyCode }
            amount = relevant.sumOf { row ->
                val rowRate = if (row.totalOriginal > 0.0) row.totalBase / row.totalOriginal else 1.0
                if (rowRate > 0.0) row.outstandingBase / rowRate else 0.0
            }.toString()
        } else {
            val divisor = if (rc.code == inv.currencyCode) historicalRate else receiptRate
            amount = if (divisor > 0.0) (inv.outstandingBase / divisor).toString() else ""
        }
    }

    val cashOriginal = amount.toDoubleOrNull() ?: 0.0
    val discountOriginal = discount.toDoubleOrNull() ?: 0.0
    val selectedInvoiceCurrency = invoice?.currencyCode.orEmpty()
    val selectedReceiptCurrency = receiptCurrency?.code.orEmpty()
    val isCrossCurrency = selectedInvoiceCurrency.isNotBlank() && selectedReceiptCurrency.isNotBlank() && selectedInvoiceCurrency != selectedReceiptCurrency
    val invoiceHistoricalRate = invoice?.let { if (it.totalOriginal > 0.0) it.totalBase / it.totalOriginal else 1.0 } ?: 1.0
    val receiptRateValue = rate.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
    val cashSettlementBase = cashOriginal * if (isCrossCurrency) receiptRateValue else invoiceHistoricalRate
    val discountBase = if (isCrossCurrency) 0.0 else discountOriginal * invoiceHistoricalRate
    val settlementBase = cashSettlementBase + discountBase
    val settlementInvoiceOriginal = if (invoiceHistoricalRate > 0.0) settlementBase / invoiceHistoricalRate else 0.0
    val outstandingInvoiceOriginal = invoice?.let { if (invoiceHistoricalRate > 0.0) it.outstandingBase / invoiceHistoricalRate else 0.0 } ?: 0.0
    val remainingInvoiceOriginal = (outstandingInvoiceOriginal - settlementInvoiceOriginal).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.sales_customer_collection)) },
        text = {
            com.fush.erp.ui.FushDialogForm {
                OutlinedTextField(
                    value = receiptNoText,
                    onValueChange = { receiptNoText = it },
                    label = { Text("رقم سند التحصيل") },
                    placeholder = { Text("رقم إلزامي") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FushDateField(
                    value = receiptDateText,
                    onValueChange = { receiptDateText = it; error = null },
                    label = "تاريخ التحصيل",
                    modifier = Modifier.fillMaxWidth(),
                )
                SalesCustomerSearchField(
                    customers = customers,
                    selected = customer,
                    canCreateCustomer = false,
                    onSelected = {
                        customer = it
                        invoice = null
                        receiptCurrency = currencies.firstOrNull { c -> c.isActive && c.code == it?.currencyCode }
                        error = null
                    },
                    onCreateNew = {},
                )
                if (customer == null) {
                    FushInlineState("اختر العميل من نتائج البحث؛ النص المكتوب وحده لا يعتبر اختياراً.")
                } else if (receiptDate == null) {
                    FushInlineState("تاريخ التحصيل غير صالح.", tone = FushStatusTone.Warning)
                } else if (invoices.isEmpty()) {
                    Text("لا توجد فواتير آجلة مفتوحة لهذا العميل في تاريخ التحصيل.")
                } else {
                    SalesSelectionField("عملة / فاتورة مرجعية", invoice?.let { "${it.invoiceNo} • ${it.currencyCode}" } ?: "اختر", invoices, { "${it.invoiceNo} • ${it.currencyCode} • متبقي ${salesMoney(it.outstandingBase)}" }) { invoice = it; error = null }
                    val sameCurrencyInvoices = invoices.filter { it.currencyCode == invoice?.currencyCode }
                    SalesSelectionField(
                        "عملة التحصيل",
                        receiptCurrency?.let { "${it.nameAr} • ${it.code}" } ?: "اختر",
                        currencies.filter { it.isActive },
                        { "${it.nameAr} • ${it.code}" },
                    ) { selected ->
                        receiptCurrency = selected
                        treasury = null
                        if (selected?.code != invoice?.currencyCode) {
                            autoAllocate = false
                            discount = "0"
                            discountReason = ""
                        }
                        error = null
                    }
                    if (sameCurrencyInvoices.size > 1 && receiptCurrency?.code == invoice?.currencyCode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = autoAllocate, onCheckedChange = { autoAllocate = it })
                            Text("توزيع تلقائي على أقدم الفواتير المفتوحة بنفس عملة الفاتورة")
                        }
                    }
                    if (isCrossCurrency) {
                        FushInlineState(
                            "الفاتورة $selectedInvoiceCurrency، والتحصيل $selectedReceiptCurrency. ستتم التسوية بالقيمة الأساسية وفق سعر يوم التحصيل دون تغيير الفاتورة التاريخية.",
                            tone = FushStatusTone.Info,
                        )
                    }
                    val options = treasuries.filter { it.currencyCode == receiptCurrency?.code }
                    SalesSelectionField("الخزينة / البنك", treasury?.nameAr ?: "اختر", options, { "${it.nameAr} • ${it.currencyCode}" }) { treasury = it }
                    if (options.isEmpty()) FushInlineState("لا توجد خزينة أو حساب بنكي نشط بعملة التحصيل.", tone = FushStatusTone.Warning)
                    FushDecimalField(
                        rate,
                        { rate = it; rateError = null },
                        if (receiptCurrency?.code == "YER_OLD") "سعر التحصيل: 1 ريال قديم = كم ريال جديد" else "سعر الصرف في تاريخ التحصيل",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = receiptCurrency?.code != "YER_NEW",
                        isError = rateError != null,
                    )
                    rateError?.let { FushInlineState(it, tone = FushStatusTone.Warning) }
                    FushDecimalField(amount, { amount = it }, "المبلغ المقبوض • $selectedReceiptCurrency", modifier = Modifier.fillMaxWidth())
                    FushDecimalField(discount, { value ->
                        discount = value
                        if ((value.toDoubleOrNull() ?: 0.0) <= 0.0) discountReason = ""
                    }, "خصم مسموح به للعميل • $selectedInvoiceCurrency", modifier = Modifier.fillMaxWidth(), enabled = !isCrossCurrency)
                    if (isCrossCurrency) {
                        FushInlineState("الخصم غير متاح داخل سند متعدد العملات؛ رحّل التحصيل أولاً ثم عالج الخصم بعملة الفاتورة.")
                    } else if (discountOriginal > 0.0) {
                        if (!canDiscount) FushInlineState("حسابك لا يملك صلاحية منح خصم أثناء التحصيل.", tone = FushStatusTone.Warning)
                        OutlinedTextField(
                            value = discountReason,
                            onValueChange = { discountReason = it },
                            label = { Text("سبب الخصم — إلزامي") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    HorizontalDivider()
                    Text("يعادل تسوية: ${salesMoney(settlementInvoiceOriginal)} $selectedInvoiceCurrency", style = MaterialTheme.typography.titleSmall)
                    Text("الرصيد قبل التسوية: ${salesMoney(outstandingInvoiceOriginal)} $selectedInvoiceCurrency", style = MaterialTheme.typography.bodySmall)
                    Text("الرصيد المتوقع بعد التسوية: ${salesMoney(remainingInvoiceOriginal)} $selectedInvoiceCurrency", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && receiptNoText.isNotBlank() && receiptDate != null && customer != null && invoice != null && receiptCurrency != null && treasury != null && cashOriginal > 0.0 && discountOriginal >= 0.0 && (!isCrossCurrency || discountOriginal <= 1e-9) && rateError == null && rate.toDoubleOrNull()?.let { it > 0 } == true && (discountOriginal <= 0.0 || (canDiscount && discountReason.isNotBlank())),
                onClick = {
                    scope.launch {
                        saving = true
                        try {
                            val selectedCustomer = requireNotNull(customer)
                            val inv = requireNotNull(invoice)
                            val selectedDate = requireNotNull(receiptDate) { "تاريخ التحصيل غير صالح" }
                            if (autoAllocate && receiptCurrency?.code == inv.currencyCode) {
                                val result = container.salesService.postReceiptAutoAllocate(
                                    customerId = selectedCustomer.id,
                                    amountOriginal = cashOriginal,
                                    discountOriginal = discountOriginal,
                                    discountReason = discountReason,
                                    currencyCode = requireNotNull(receiptCurrency).code,
                                    exchangeRate = rate.toDouble(),
                                    notes = notes,
                                    createdBy = user.id,
                                    receiptDate = selectedDate,
                                    treasuryAccountId = requireNotNull(treasury).id,
                                    receiptNo = receiptNoText,
                                )
                                onDone("تم تحصيل ${result.receiptNo}: نقدي ${salesMoney(result.totalOriginal)} + خصم ${salesMoney(result.discountOriginal)}، موزع على ${result.allocationCount} فاتورة", result.receiptId)
                            } else {
                                val result = container.salesService.postReceipt(
                                    customerId = selectedCustomer.id,
                                    invoiceId = inv.id,
                                    amountOriginal = cashOriginal,
                                    discountOriginal = discountOriginal,
                                    discountReason = discountReason,
                                    currencyCode = requireNotNull(receiptCurrency).code,
                                    exchangeRate = rate.toDouble(),
                                    notes = notes,
                                    createdBy = user.id,
                                    receiptDate = selectedDate,
                                    treasuryAccountId = requireNotNull(treasury).id,
                                    receiptNo = receiptNoText,
                                )
                                onDone("تم تحصيل ${result.receiptNo} وربطه بالفاتورة ${inv.invoiceNo}", result.receiptId)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "تعذر تسجيل التحصيل"
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) "جارٍ..." else "ترحيل التحصيل") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") } },
    )
}

@Composable
private fun ReverseSalesReceiptDialog(
    container: AppContainer,
    user: UserEntity,
    receipt: CustomerReceiptEntity,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(salesDate(com.fush.erp.domain.TrustedTimeService.now())) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("عكس التحصيل ${receipt.receiptNo}") },
        text = {
            com.fush.erp.ui.FushDialogForm {
                Text("سيُنشأ مستند عكس وقيد عكسي، وتُعاد ذمة الفاتورة ويُصحح استحقاق العمولة تلقائياً.")
                OutlinedTextField(date, { date = it }, label = { Text("تاريخ العكس YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(reason, { reason = it }, label = { Text("سبب العكس — إلزامي") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && reason.isNotBlank(),
                onClick = {
                    scope.launch {
                        saving = true
                        try {
                            val result = container.salesService.reverseReceipt(receipt.id, reason, user.id, salesParseDate(date))
                            onDone("تم عكس ${receipt.receiptNo} بالمستند ${result.reversalReceiptNo} وإعادة ${salesMoney(result.restoredReceivableBase)} إلى ذمة العميل")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "تعذر عكس التحصيل"
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(if (saving) "جارٍ..." else "تأكيد العكس") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") } },
    )
}

@Composable
private fun ReceiptDialog(
    container: AppContainer,
    user: UserEntity,
    invoice: SalesInvoiceSummary,
    currencies: List<CurrencyEntity>,
    onDismiss: () -> Unit,
    onPosted: (SalesService.ReceiptResult) -> Unit
) {
    val scope = rememberCoroutineScope()
    val treasuryAccounts by container.db.accountingDao().observeTreasuryBalances().collectAsState(initial = emptyList())
    val rolePermissions by container.db.securityDao().observePermissionCodesForRole(user.role).collectAsState(initial = emptyList())
    val canDiscount = user.role == "ADMIN" || SecurityPermissions.COLLECTION_DISCOUNT_POST in rolePermissions
    var currency by remember { mutableStateOf<CurrencyEntity?>(null) }
    var treasury by remember { mutableStateOf<TreasuryBalanceRow?>(null) }
    var rateText by remember { mutableStateOf("1") }
    var rateError by remember { mutableStateOf<String?>(null) }
    var amountText by remember { mutableStateOf("") }
    var discountText by remember { mutableStateOf("0") }
    var discountReason by remember { mutableStateOf("") }
    var receiptDateText by remember { mutableStateOf(salesDate(com.fush.erp.domain.TrustedTimeService.now())) }
    var receiptNoText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val receiptDate = remember(receiptDateText) { runCatching { salesParseDate(receiptDateText) }.getOrNull() }
    LaunchedEffect(currencies, invoice.id) {
        val invoiceEntity = container.db.salesDao().invoiceById(invoice.id)
        val customerCurrencyCode = invoiceEntity?.let { container.db.customerDao().byId(it.customerId)?.currencyCode }
        currency = currencies.firstOrNull { it.isActive && it.code == customerCurrencyCode }
            ?: currencies.firstOrNull { it.isActive && it.code == invoice.currencyCode }
            ?: currencies.firstOrNull { it.isActive && it.isBase }
    }
    LaunchedEffect(currency?.code, receiptDate) {
        val code = currency?.code
        val date = receiptDate
        if (code == null || date == null) {
            rateError = if (code != null) "تاريخ التحصيل غير صالح" else null
            return@LaunchedEffect
        }
        try {
            rateText = salesMoneyRaw(container.salesService.exchangeRateAt(code, date))
            rateError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            rateText = if (code == "YER_NEW") "1" else ""
            rateError = e.message ?: "لا يوجد سعر صرف معتمد في تاريخ التحصيل"
        }
    }
    LaunchedEffect(currency?.code, treasuryAccounts) {
        val options = treasuryAccounts.filter { it.currencyCode == currency?.code }
        if (treasury?.id !in options.map { it.id }) treasury = options.firstOrNull()
    }
    LaunchedEffect(currency?.code, rateText, invoice.id) {
        val selected = currency ?: return@LaunchedEffect
        val receiptRate = rateText.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return@LaunchedEffect
        val invoiceHistoricalRate = if (invoice.totalOriginal > 0.0) invoice.totalBase / invoice.totalOriginal else 1.0
        val divisor = if (selected.code == invoice.currencyCode) invoiceHistoricalRate else receiptRate
        if (divisor > 0.0) amountText = salesMoneyRaw(invoice.outstandingBase / divisor)
    }
    val cashOriginal = amountText.toDoubleOrNull() ?: 0.0
    val discountOriginal = discountText.toDoubleOrNull() ?: 0.0
    val historicalRate = if (invoice.totalOriginal > 0.0) invoice.totalBase / invoice.totalOriginal else 1.0
    val receiptRateValue = if (currency?.isBase == true) 1.0 else rateText.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
    val isCrossCurrency = currency?.code != null && currency?.code != invoice.currencyCode
    val cashSettlementBase = cashOriginal * if (isCrossCurrency) receiptRateValue else historicalRate
    val discountBase = if (isCrossCurrency) 0.0 else discountOriginal * historicalRate
    val settlementBase = cashSettlementBase + discountBase
    val settlementInvoiceOriginal = if (historicalRate > 0.0) settlementBase / historicalRate else 0.0
    val outstandingOriginal = if (historicalRate > 0.0) invoice.outstandingBase / historicalRate else 0.0
    val remainingOriginal = (outstandingOriginal - settlementInvoiceOriginal).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("تحصيل ${invoice.invoiceNo}") },
        text = {
            com.fush.erp.ui.FushDialogForm {
                Text("${invoice.customerName} • المتبقي ${salesMoney(invoice.outstandingBase)} ريال أساسي")
                OutlinedTextField(
                    value = receiptNoText,
                    onValueChange = { receiptNoText = it },
                    label = { Text("رقم سند التحصيل") },
                    placeholder = { Text("رقم إلزامي") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FushDateField(receiptDateText, { receiptDateText = it; error = null }, "تاريخ التحصيل", modifier = Modifier.fillMaxWidth())
                SalesSelectionField(
                    "عملة التحصيل",
                    currency?.let { "${it.nameAr} • ${it.code}" } ?: "اختر",
                    currencies.filter { it.isActive },
                    { "${it.nameAr} • ${it.code}" },
                ) { selected ->
                    currency = selected
                    treasury = null
                    if (selected?.code != invoice.currencyCode) {
                        discountText = "0"
                        discountReason = ""
                    }
                    error = null
                }
                if (isCrossCurrency) {
                    FushInlineState(
                        "عملة الفاتورة ${invoice.currencyCode}، وعملة التحصيل ${currency?.code}. ستتم التسوية بالقيمة الأساسية وفق سعر يوم التحصيل دون تغيير الفاتورة الأصلية.",
                        tone = FushStatusTone.Info,
                    )
                }
                val receiptTreasuryOptions = treasuryAccounts.filter { it.currencyCode == currency?.code }
                SalesSelectionField("الخزينة / البنك", treasury?.nameAr ?: "اختر", receiptTreasuryOptions, { "${it.nameAr} • ${it.currencyCode}" }) { treasury = it }
                if (receiptTreasuryOptions.isEmpty()) FushInlineState("لا توجد خزينة أو حساب بنكي نشط بعملة التحصيل. من الحسابات > الخزينة افتح الصندوق المطلوب واضغط «إضافة عملة» ثم أضف ${currency?.code.orEmpty()}.", tone = FushStatusTone.Warning)
                if (currency?.isBase != true) {
                    FushDecimalField(
                        rateText,
                        { rateText = it; rateError = null },
                        if (currency?.code == "YER_OLD") "سعر الصرف الحالي: 1 ريال قديم = كم ريال جديد" else "سعر الصرف الحالي للتحصيل",
                        modifier = Modifier.fillMaxWidth(),
                        isError = rateError != null,
                    )
                    rateError?.let { FushInlineState(it, tone = FushStatusTone.Warning) }
                }
                FushDecimalField(amountText, { amountText = it }, "المبلغ المقبوض • ${currency?.code.orEmpty()}", modifier = Modifier.fillMaxWidth())
                FushDecimalField(discountText, { value ->
                    discountText = value
                    if ((value.toDoubleOrNull() ?: 0.0) <= 0.0) discountReason = ""
                }, "خصم مسموح به للعميل • ${invoice.currencyCode}", modifier = Modifier.fillMaxWidth(), enabled = !isCrossCurrency)
                if (isCrossCurrency) {
                    FushInlineState("الخصم غير متاح داخل سند متعدد العملات؛ رحّل التحصيل أولاً ثم عالج الخصم بعملة الفاتورة.")
                } else if (discountOriginal > 0.0) {
                    if (!canDiscount) FushInlineState("حسابك لا يملك صلاحية منح خصم أثناء التحصيل.", tone = FushStatusTone.Warning)
                    OutlinedTextField(discountReason, { discountReason = it }, label = { Text("سبب الخصم — إلزامي") }, modifier = Modifier.fillMaxWidth())
                }
                HorizontalDivider()
                Text("يعادل تسوية: ${salesMoney(settlementInvoiceOriginal)} ${invoice.currencyCode}", style = MaterialTheme.typography.titleSmall)
                Text("الرصيد قبل التسوية: ${salesMoney(outstandingOriginal)} ${invoice.currencyCode}", style = MaterialTheme.typography.bodySmall)
                Text("الرصيد المتوقع بعد التسوية: ${salesMoney(remainingOriginal)} ${invoice.currencyCode}", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !saving && receiptNoText.isNotBlank() && receiptDate != null && currency != null && treasury != null && cashOriginal > 0.0 && discountOriginal >= 0.0 && (!isCrossCurrency || discountOriginal <= 1e-9) && rateError == null && (currency?.isBase == true || rateText.toDoubleOrNull()?.let { it > 0.0 } == true) && (discountOriginal <= 0.0 || (canDiscount && discountReason.isNotBlank())), onClick = {
                scope.launch {
                    saving = true
                    try {
                        val result = container.salesService.postReceipt(
                            customerId = container.db.salesDao().invoiceById(invoice.id)!!.customerId,
                            invoiceId = invoice.id,
                            amountOriginal = cashOriginal,
                            discountOriginal = discountOriginal,
                            discountReason = discountReason,
                            currencyCode = currency!!.code,
                            exchangeRate = if (currency?.isBase == true) 1.0 else requireNotNull(rateText.toDoubleOrNull()) { "سعر الصرف غير صالح" },
                            notes = notes,
                            createdBy = user.id,
                            receiptDate = requireNotNull(receiptDate) { "تاريخ التحصيل غير صالح" },
                            treasuryAccountId = treasury!!.id,
                            receiptNo = receiptNoText
                        )
                        onPosted(result)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) { error = e.message ?: "تعذر التحصيل" }
                    finally { saving = false }
                }
            }) { Text(if (saving) "جارٍ..." else "ترحيل التحصيل") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") } }
    )
}

@Composable
private fun SalesReturnDialog(
    container: AppContainer,
    user: UserEntity,
    invoice: SalesInvoiceSummary,
    itemsList: List<ItemEntity>,
    units: List<UnitEntity>,
    onDismiss: () -> Unit,
    onPosted: (SalesService.ReturnResult) -> Unit
) {
    val scope = rememberCoroutineScope()
    val treasuryAccounts by container.db.accountingDao().observeTreasuryBalances().collectAsState(initial = emptyList())
    var treasury by remember { mutableStateOf<TreasuryBalanceRow?>(null) }
    val saleLines by produceState(initialValue = emptyList<SalesLineEntity>(), key1 = invoice.id) {
        value = container.db.salesDao().linesForInvoice(invoice.id)
    }
    var line by remember { mutableStateOf<SalesLineEntity?>(null) }
    var quantityText by remember { mutableStateOf("") }
    var freeQuantityText by remember { mutableStateOf("0") }
    var settlement by remember { mutableStateOf(if (invoice.paymentType == "CASH") "CASH_REFUND" else "CUSTOMER_CREDIT") }
    var reason by remember { mutableStateOf("") }
    var returnDateText by remember { mutableStateOf(salesDate(com.fush.erp.domain.TrustedTimeService.now())) }
    val returnDate = remember(returnDateText) { runCatching { salesParseDate(returnDateText) }.getOrNull() }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(saleLines) { if (line == null) line = saleLines.firstOrNull() }
    LaunchedEffect(treasuryAccounts, invoice.currencyCode) {
        val options = treasuryAccounts.filter { it.currencyCode == invoice.currencyCode }
        if (treasury?.id !in options.map { it.id }) treasury = options.firstOrNull()
    }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مرتجع مبيعات", style = MaterialTheme.typography.headlineSmall)
                Text("من ${invoice.invoiceNo} — ${invoice.customerName}")
                FushDateField(returnDateText, { returnDateText = it; error = null }, "تاريخ المرتجع", modifier = Modifier.fillMaxWidth())
                SalesSelectionField(
                    "السطر",
                    line?.let { selected -> itemsList.firstOrNull { it.id == selected.itemId }?.nameAr } ?: "اختر",
                    saleLines,
                    { row ->
                        val itemName = itemsList.firstOrNull { it.id == row.itemId }?.nameAr ?: "صنف"
                        val unitName = units.firstOrNull { it.id == row.unitId }?.nameAr ?: "وحدة"
                        "$itemName — ${salesMoney(row.quantity)} $unitName"
                    }
                ) { line = it; quantityText = ""; freeQuantityText = "0" }
                FushDecimalField(quantityText, { quantityText = it }, "مرتجع من الكمية المباعة", modifier = Modifier.fillMaxWidth())
                FushDecimalField(freeQuantityText, { freeQuantityText = it }, "مرتجع من الكمية المجانية", modifier = Modifier.fillMaxWidth())
                val paidReturnQty = quantityText.toDoubleOrNull() ?: 0.0
                val freeReturnQty = freeQuantityText.toDoubleOrNull() ?: 0.0
                val freeOnlyReturn = paidReturnQty <= 0.0 && freeReturnQty > 0.0
                if (freeOnlyReturn) {
                    FushInlineState("مرتجع المجاني يعيد المخزون والتكلفة فقط ولا ينشئ ردًا نقديًا أو إشعارًا دائنًا.", tone = FushStatusTone.Info)
                } else {
                    SalesStringSelectionField("التسوية", settlement, listOf("CUSTOMER_CREDIT", "CASH_REFUND"), { if (it == "CASH_REFUND") "رد نقدي" else "إشعار دائن للعميل" }) { settlement = it }
                }
                if (!freeOnlyReturn && settlement == "CASH_REFUND") {
                    val refundOptions = treasuryAccounts.filter { it.currencyCode == invoice.currencyCode }
                    SalesSelectionField("الخزينة / البنك للرد", treasury?.nameAr ?: "اختر", refundOptions, { "${it.nameAr} • ${it.currencyCode}" }) { treasury = it }
                    if (refundOptions.isEmpty()) Text("لا توجد خزينة أو حساب بنكي نشط بعملة الفاتورة.", color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(reason, { reason = it }, label = { Text("سبب المرتجع") })
                Text("سيعاد المخزون إلى نفس تشغيلات البيع الأصلية، وتنعكس تكلفة المبيعات والعمولة المستحقة تلقائياً.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = !saving && returnDate != null && line != null && ((quantityText.toDoubleOrNull() ?: 0.0) > 0.0 || (freeQuantityText.toDoubleOrNull() ?: 0.0) > 0.0) && reason.isNotBlank() && (freeOnlyReturn || settlement != "CASH_REFUND" || treasury != null), onClick = {
                        scope.launch {
                            saving = true
                            try {
                                onPosted(container.salesService.postReturn(
                                    salesLineId = line!!.id,
                                    quantity = quantityText.toDoubleOrNull() ?: 0.0,
                                    freeQuantity = freeQuantityText.toDoubleOrNull() ?: 0.0,
                                    settlementType = if (freeOnlyReturn) "NO_FINANCIAL" else settlement,
                                    reason = reason,
                                    createdBy = user.id,
                                    returnDate = requireNotNull(returnDate) { "تاريخ المرتجع غير صحيح" },
                                    treasuryAccountId = if (!freeOnlyReturn && settlement == "CASH_REFUND") treasury?.id else null
                                ))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) { error = e.message ?: "تعذر ترحيل المرتجع" }
                            finally { saving = false }
                        }
                    }) { Text(if (saving) "جارٍ..." else "ترحيل المرتجع") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SalesCustomerSearchField(
    customers: List<CustomerEntity>,
    selected: CustomerEntity?,
    canCreateCustomer: Boolean,
    onSelected: (CustomerEntity?) -> Unit,
    onCreateNew: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = selected?.let { "${it.nameAr} — ${it.code}" }.orEmpty()
    var query by remember(selected?.id) { mutableStateOf(selectedLabel) }
    val filterQuery = if (selected != null && query == selectedLabel) "" else query.trim().lowercase(Locale.ROOT)
    val filtered = remember(customers, filterQuery) {
        val matches = if (filterQuery.isBlank()) customers else customers.filter { customer ->
            listOf(
                customer.nameAr,
                customer.nameEn,
                customer.code,
                customer.phone,
                customer.province,
            ).any { it.lowercase(Locale.ROOT).contains(filterQuery) }
        }
        matches.take(30)
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { shouldExpand -> expanded = shouldExpand },
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { value ->
                query = value
                if (selected != null && value != selectedLabel) onSelected(null)
                expanded = true
            },
            label = { Text("العميل") },
            placeholder = { Text("اكتب اسم أو كود العميل...") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp),
        ) {
            if (filtered.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("لا توجد نتائج مطابقة") },
                    onClick = {},
                    enabled = false,
                )
            } else {
                filtered.forEach { customer ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("${customer.nameAr} — ${customer.code}")
                                if (customer.province.isNotBlank()) {
                                    Text(
                                        customer.province,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelected(customer)
                            query = "${customer.nameAr} — ${customer.code}"
                            expanded = false
                        },
                    )
                }
            }
            if (canCreateCustomer) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("+ عميل جديد", color = MaterialTheme.colorScheme.primary) },
                    onClick = {
                        expanded = false
                        onCreateNew()
                    },
                )
            }
        }
    }
}

@Composable
private fun AdditionalChargeDraftDialog(
    container: AppContainer,
    chargeDate: Long,
    chargeTypes: List<AdditionalChargeTypeEntity>,
    currencies: List<CurrencyEntity>,
    onDismiss: () -> Unit,
    onAdd: (AdditionalChargeDraftUi) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val treasuryAccounts by container.db.accountingDao().observeTreasuryBalances().collectAsState(initial = emptyList())
    var type by remember(chargeTypes) { mutableStateOf(chargeTypes.firstOrNull()) }
    var currency by remember(currencies) { mutableStateOf(currencies.firstOrNull { it.isBase } ?: currencies.firstOrNull()) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var bearer by remember(type?.id) { mutableStateOf(type?.defaultBearer ?: "CUSTOMER") }
    var treatment by remember(type?.id) { mutableStateOf(type?.defaultAccountingTreatment ?: "RECOVERABLE") }
    var paymentStatus by remember { mutableStateOf("UNPAID") }
    var paidBy by remember { mutableStateOf("COMPANY") }
    var paidAmountText by remember { mutableStateOf("") }
    var treasury by remember { mutableStateOf<TreasuryBalanceRow?>(null) }
    var paymentDateText by remember(chargeDate) { mutableStateOf(salesDate(chargeDate)) }
    var paymentReference by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var exchangeRate by remember { mutableStateOf<Double?>(null) }
    var rateError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(type?.id) {
        type?.let {
            bearer = it.defaultBearer
            treatment = it.defaultAccountingTreatment
            if (bearer == "COMPANY") paidBy = "COMPANY"
        }
    }
    LaunchedEffect(currency?.code, chargeDate) {
        val cur = currency ?: return@LaunchedEffect
        try {
            exchangeRate = container.additionalChargesService.exchangeRateAt(cur.code, chargeDate)
            rateError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            exchangeRate = null
            rateError = e.message ?: "تعذر تحديد سعر الصرف"
        }
    }
    LaunchedEffect(currency?.code, treasuryAccounts, paidBy) {
        if (paidBy != "COMPANY") {
            treasury = null
        } else {
            val options = treasuryAccounts.filter { it.currencyCode == currency?.code }
            if (treasury?.id !in options.map { it.id }) treasury = options.firstOrNull()
        }
    }
    LaunchedEffect(bearer) {
        if (bearer == "COMPANY") {
            treatment = "COMPANY_EXPENSE"
            paidBy = "COMPANY"
        } else if (treatment == "COMPANY_EXPENSE") {
            treatment = "RECOVERABLE"
        }
    }
    LaunchedEffect(treatment) {
        if (treatment == "SERVICE_REVENUE") {
            bearer = "CUSTOMER"
            paidBy = "COMPANY"
            paymentStatus = "UNPAID"
            paidAmountText = ""
            treasury = null
        }
    }
    LaunchedEffect(paidBy) {
        if (paidBy == "CUSTOMER_DIRECT") {
            bearer = "CUSTOMER"
            treatment = "RECOVERABLE"
            treasury = null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("إضافة سطر AdditionalCharges", style = MaterialTheme.typography.headlineSmall)
                Text("السياسة المحاسبية تُؤخذ من إعداد نوع الرسم وتُحفظ Snapshot مع العملية.", style = MaterialTheme.typography.bodySmall)
                SalesSelectionField("نوع الرسم", type?.nameAr ?: "اختر", chargeTypes, { "${it.nameAr} • ${chargePrincipalAgentLabel(it.principalAgentMode)}" }) { type = it }
                OutlinedTextField(description, { description = it }, label = { Text("الوصف / البيان") }, modifier = Modifier.fillMaxWidth())
                FushDecimalField(amountText, { amountText = it }, "المبلغ", modifier = Modifier.fillMaxWidth())
                SalesSelectionField("العملة", currency?.nameAr ?: "اختر", currencies, { "${it.nameAr} • ${it.code}" }) { currency = it }
                FushDecimalField(exchangeRate?.let(::salesMoneyRaw) ?: "", {}, "سعر الصرف التاريخي", modifier = Modifier.fillMaxWidth(), enabled = false, isError = rateError != null)
                rateError?.let { FushInlineState(it, tone = FushStatusTone.Warning) }
                SalesStringSelectionField("من يتحمل", bearer, listOf("CUSTOMER", "COMPANY"), ::chargeBearerLabel) { bearer = it }
                SalesStringSelectionField(
                    "نوع المعالجة المحاسبية",
                    treatment,
                    if (bearer == "COMPANY") listOf("COMPANY_EXPENSE") else listOf("RECOVERABLE", "SERVICE_REVENUE"),
                    ::chargeTreatmentLabel,
                ) { treatment = it }
                type?.let { FushInlineState("IFRS 15 / Principal-Agent: ${chargePrincipalAgentLabel(it.principalAgentMode)} — قابل للتعديل من إعدادات الرسوم.", tone = FushStatusTone.Info) }
                SalesStringSelectionField("حالة الدفع", paymentStatus, listOf("UNPAID", "PARTIAL", "PAID"), ::chargePaymentStatusLabel) { paymentStatus = it }
                if (treatment != "SERVICE_REVENUE") {
                    SalesStringSelectionField("الدفع بواسطة", paidBy, if (bearer == "COMPANY") listOf("COMPANY") else listOf("COMPANY", "CUSTOMER_DIRECT"), ::chargePaidByLabel) { paidBy = it }
                }
                if (paymentStatus == "PARTIAL") {
                    FushDecimalField(paidAmountText, { paidAmountText = it }, "المبلغ المدفوع جزئياً", modifier = Modifier.fillMaxWidth())
                }
                if (paymentStatus != "UNPAID") {
                    FushDateField(paymentDateText, { paymentDateText = it }, "تاريخ الدفع", modifier = Modifier.fillMaxWidth())
                    Text("عند إنشاء الرسم مع دفعة أولية يجب أن يطابق تاريخ الدفع تاريخ إثبات الرسم. الدفعات اللاحقة تُسجل كسند دفع مستقل.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (paidBy == "COMPANY") {
                        val options = treasuryAccounts.filter { it.currencyCode == currency?.code }
                        SalesSelectionField("حساب الدفع — صندوق / بنك", treasury?.nameAr ?: "اختر", options, { "${it.nameAr} • ${it.currencyCode}" }) { treasury = it }
                        if (options.isEmpty()) FushInlineState("لا توجد خزينة/بنك نشط بعملة الرسم.", tone = FushStatusTone.Warning)
                    }
                    OutlinedTextField(paymentReference, { paymentReference = it }, label = { Text("مرجع الدفع") }, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("إلغاء") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        try {
                            val selectedType = requireNotNull(type) { "اختر نوع الرسم" }
                            val selectedCurrency = requireNotNull(currency) { "اختر العملة" }
                            val rate = requireNotNull(exchangeRate) { "سعر الصرف غير متاح" }
                            val amount = requireNotNull(amountText.toDoubleOrNull()) { "المبلغ غير صالح" }
                            require(amount > 0.0) { "المبلغ يجب أن يكون أكبر من صفر" }
                            val paidAmount = when (paymentStatus) {
                                "UNPAID" -> 0.0
                                "PAID" -> amount
                                else -> requireNotNull(paidAmountText.toDoubleOrNull()) { "المبلغ المدفوع جزئياً غير صالح" }
                            }
                            val payDate = if (paymentStatus == "UNPAID") null else requireNotNull(runCatching { salesParseDate(paymentDateText) }.getOrNull()) { "تاريخ الدفع غير صالح" }
                            if (payDate != null) require(payDate == chargeDate) { "الدفعة الأولية يجب أن تكون في تاريخ الرسم نفسه. إذا كان الدفع في تاريخ آخر سجّل الرسم أولاً ثم دفعة مستقلة." }
                            if (paidBy == "COMPANY" && paymentStatus != "UNPAID") requireNotNull(treasury) { "اختر حساب الدفع" }
                            onAdd(
                                AdditionalChargeDraftUi(
                                    type = selectedType,
                                    description = description.trim(),
                                    amountOriginal = amount,
                                    currency = selectedCurrency,
                                    exchangeRate = rate,
                                    bearer = bearer,
                                    paymentStatus = paymentStatus,
                                    paidBy = paidBy,
                                    paidAmountOriginal = paidAmount,
                                    treasuryAccountId = if (paidBy == "COMPANY" && paymentStatus != "UNPAID") treasury?.id else null,
                                    paymentDate = payDate,
                                    paymentReference = paymentReference.trim(),
                                    accountingTreatment = treatment,
                                    notes = notes.trim(),
                                )
                            )
                        } catch (e: Exception) {
                            error = e.message ?: "بيانات الرسم غير صالحة"
                        }
                    }) { Text("إضافة السطر") }
                }
            }
        }
    }
}

@Composable
private fun ExistingChargeAllocationDialog(
    row: AdditionalChargeAvailableRow,
    onDismiss: () -> Unit,
    onAdd: (Double) -> Unit,
) {
    val remainingOriginal = if (row.exchangeRate > 0.0) row.remainingBase / row.exchangeRate else 0.0
    var amountText by remember(row.id) { mutableStateOf(salesMoneyRaw(remainingOriginal)) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("تسوية رسم سابق", style = MaterialTheme.typography.headlineSmall)
                Text("${row.chargeNo} • ${row.chargeTypeName}")
                Text("المتبقي المتاح للتسوية: ${salesMoney(remainingOriginal)} ${row.currencyCode}", color = MaterialTheme.colorScheme.primary)
                FushDecimalField(amountText, { amountText = it }, "المبلغ المراد ربطه بهذه الفاتورة", modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("إلغاء") }
                    Button(onClick = {
                        try {
                            val amount = requireNotNull(amountText.toDoubleOrNull()) { "المبلغ غير صالح" }
                            require(amount > 0.0) { "المبلغ يجب أن يكون أكبر من صفر" }
                            require(amount <= remainingOriginal + 1e-9) { "المبلغ يتجاوز المتبقي المتاح للتسوية: ${salesMoney(remainingOriginal)} ${row.currencyCode}" }
                            onAdd(amount)
                        } catch (e: Exception) { error = e.message }
                    }) { Text("ربط") }
                }
            }
        }
    }
}

@Composable
private fun PreInvoiceAdditionalChargeDialog(
    container: AppContainer,
    user: UserEntity,
    customers: List<CustomerEntity>,
    currencies: List<CurrencyEntity>,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val chargeTypes by container.db.additionalChargesDao().observeActiveTypes().collectAsState(initial = emptyList())
    var customer by remember { mutableStateOf<CustomerEntity?>(null) }
    var dateText by remember { mutableStateOf(salesDate(com.fush.erp.domain.TrustedTimeService.now())) }
    val chargeDate = remember(dateText) { runCatching { salesParseDate(dateText) }.getOrNull() }
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    if (editing && customer != null && chargeDate != null) {
        AdditionalChargeDraftDialog(
            container = container,
            chargeDate = chargeDate,
            chargeTypes = chargeTypes,
            currencies = currencies,
            onDismiss = { editing = false },
            onAdd = { draft ->
                scope.launch {
                    saving = true
                    try {
                        val result = container.additionalChargesService.postPreInvoiceCharge(customer!!.id, chargeDate, draft.toDomain(), user.id)
                        onDone("تم ترحيل الرسم ${result.charge.chargeNo}. أصبح متاحاً للتسوية مع فاتورة واحدة أو أكثر دون تكرار الذمة.")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "تعذر ترحيل الرسم"
                        editing = false
                    } finally { saving = false }
                }
            },
        )
        return
    }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("رسم / دفعة قبل الفاتورة", style = MaterialTheme.typography.headlineSmall)
                Text("مثال: دفعنا 50,000 نقل نيابة عن العميل. يُثبت كمبلغ قابل للاسترداد، ثم يُسوّى لاحقاً مع الفاتورة دون مضاعفة الذمة.", style = MaterialTheme.typography.bodySmall)
                SalesCustomerSearchField(customers, customer, false, { customer = it }, {})
                FushDateField(dateText, { dateText = it }, "تاريخ إثبات الرسم / الدفع", modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") }
                    Button(onClick = { editing = true }, enabled = customer != null && chargeDate != null && chargeTypes.isNotEmpty() && !saving) { Text("إدخال بيانات الرسم") }
                }
            }
        }
    }
}

@Composable
private fun AdditionalChargeSettingsDialog(
    container: AppContainer,
    user: UserEntity,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val types by container.db.additionalChargesDao().observeActiveTypes().collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<AdditionalChargeTypeEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("إعدادات AdditionalCharges", style = MaterialTheme.typography.headlineSmall)
                FushInlineState("قرار Principal / Agent ليس ثابتاً حسب كلمة «نقل» أو «جمارك». غيّره حسب طبيعة العقد والعلاقة، وسيُحفظ Snapshot على العمليات الجديدة فقط.", tone = FushStatusTone.Info)
                types.forEach { type ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${type.code} — ${type.nameAr}", style = MaterialTheme.typography.titleSmall)
                                Text("${chargeBearerLabel(type.defaultBearer)} • ${chargeTreatmentLabel(type.defaultAccountingTreatment)} • ${chargePrincipalAgentLabel(type.principalAgentMode)}", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { editing = type }) { Text("تعديل السياسة") }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button(onClick = onDismiss) { Text("إغلاق") } }
            }
        }
    }
    editing?.let { type ->
        ChargePolicyEditDialog(
            type = type,
            onDismiss = { editing = null },
            onSave = { bearer, treatment, mode ->
                scope.launch {
                    try {
                        container.additionalChargesService.updateTypePolicy(
                            typeId = type.id,
                            defaultBearer = bearer,
                            defaultTreatment = treatment,
                            principalAgentMode = mode,
                            recoverableAccountId = type.recoverableAccountId,
                            expenseAccountId = type.expenseAccountId,
                            revenueAccountId = type.revenueAccountId,
                            payableAccountId = type.payableAccountId,
                            updatedBy = user.id,
                        )
                        editing = null
                        onMessage("تم تحديث سياسة ${type.nameAr}. العمليات السابقة لم تتغير لأنها تحفظ Snapshot تاريخياً.")
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "تعذر تحديث السياسة"; editing = null }
                }
            },
        )
    }
}

@Composable
private fun ChargePolicyEditDialog(
    type: AdditionalChargeTypeEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var bearer by remember(type.id) { mutableStateOf(type.defaultBearer) }
    var treatment by remember(type.id) { mutableStateOf(type.defaultAccountingTreatment) }
    var mode by remember(type.id) { mutableStateOf(type.principalAgentMode) }
    LaunchedEffect(bearer) {
        if (bearer == "COMPANY") treatment = "COMPANY_EXPENSE"
        else if (treatment == "COMPANY_EXPENSE") treatment = "RECOVERABLE"
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("سياسة ${type.nameAr}", style = MaterialTheme.typography.headlineSmall)
                SalesStringSelectionField("الطرف الافتراضي المتحمل", bearer, listOf("CUSTOMER", "COMPANY"), ::chargeBearerLabel) { bearer = it }
                SalesStringSelectionField("المعالجة الافتراضية", treatment, if (bearer == "COMPANY") listOf("COMPANY_EXPENSE") else listOf("RECOVERABLE", "SERVICE_REVENUE"), ::chargeTreatmentLabel) { treatment = it }
                SalesStringSelectionField("IFRS 15 — طبيعة العلاقة", mode, listOf("REVIEW", "PRINCIPAL", "AGENT"), ::chargePrincipalAgentLabel) { mode = it }
                Text("REVIEW يعني أن النظام لا يفترض أصيل/وكيل ويترك القرار للمحاسب وفق العقد والوقائع.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("إلغاء") }
                    Button(onClick = { onSave(bearer, treatment, mode) }) { Text("حفظ") }
                }
            }
        }
    }
}


@Composable
private fun ShipmentManagerDialog(
    container: AppContainer,
    user: UserEntity,
    warehouses: List<WarehouseEntity>,
    itemsList: List<ItemEntity>,
    currencies: List<CurrencyEntity>,
    invoices: List<SalesInvoiceSummary>,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val summaries by produceState(initialValue = emptyList<ShipmentSummaryRow>(), key1 = refresh) {
        value = container.db.shipmentDao().shipmentSummaries()
    }
    var showCreate by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var deleteCandidate by remember { mutableStateOf<ShipmentSummaryRow?>(null) }
    var deleteBusy by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("الشحنات وتخصيص التكلفة", style = MaterialTheme.typography.headlineSmall)
                        Text("التكلفة الفعلية للشحنة مستقلة تماماً عن أي رسم يُحمّل للعميل.", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = onDismiss) { Text("إغلاق") }
                }
                Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) { Text("إنشاء شحنة جديدة") }
                if (summaries.isEmpty()) {
                    FushInlineState("لا توجد شحنات مسجلة بعد.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(summaries, key = { it.id }) { row ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(row.shipmentNo, style = MaterialTheme.typography.titleMedium)
                                        FushStatusPill(shipmentStatusLabel(row.status), if (row.status == "CLOSED") FushStatusTone.Success else FushStatusTone.Info)
                                    }
                                    Text("${salesDate(row.shipmentDate)} • ${row.warehouseName} ← ${row.destinationProvince}")
                                    if (row.transportReference.isNotBlank()) Text("مرجع النقل: ${row.transportReference}", style = MaterialTheme.typography.bodySmall)
                                    Text("تكلفة فعلية: ${salesMoney(row.totalExpenseBase)} • موزع: ${salesMoney(row.allocatedExpenseBase)} • متبقي: ${salesMoney(row.remainingExpenseBase)} أساسي")
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { selectedId = row.id }, modifier = Modifier.weight(1f)) { Text("تفاصيل الشحنة") }
                                        if (user.role == "ADMIN") {
                                            OutlinedButton(
                                                onClick = { deleteError = null; deleteCandidate = row },
                                                modifier = Modifier.weight(1f),
                                            ) { Text("حذف الشحنة") }
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

    if (showCreate) {
        ShipmentCreateDialog(
            container = container, user = user, warehouses = warehouses, itemsList = itemsList,
            onDismiss = { showCreate = false },
            onDone = { text -> onMessage(text); showCreate = false; refresh++ }
        )
    }
    selectedId?.let { id ->
        ShipmentDetailDialog(
            container = container, user = user, shipmentId = id, currencies = currencies, invoices = invoices,
            onDismiss = { selectedId = null },
            onChanged = { text -> onMessage(text); refresh++ }
        )
    }

    deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { if (!deleteBusy) { deleteCandidate = null; deleteError = null } },
            title = { Text("حذف الشحنة ${candidate.shipmentNo}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيتم الحذف النهائي فقط إذا لم تكن الشحنة مرتبطة بأي فاتورة أو مصروف شحنة. عند وجود مزامنة سحابية سيُنشر Tombstone أولاً حتى لا تعود الشحنة على الهاتف الآخر.")
                    Text("الوجهة: ${candidate.destinationProvince} • التاريخ: ${salesDate(candidate.shipmentDate)}")
                    deleteError?.let { FushInlineState(it, tone = FushStatusTone.Danger) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !deleteBusy,
                    onClick = {
                        deleteBusy = true
                        deleteError = null
                        scope.launch {
                            try {
                                val result = ShipmentService(container.db, container.cloudSyncRepository)
                                    .deleteUnusedShipment(candidate.id, user.id)
                                onMessage(
                                    if (result.cloudTombstonePublished)
                                        "تم حذف الشحنة ${result.shipmentNo} وتثبيت حذفها في السحابة"
                                    else
                                        "تم حذف الشحنة ${result.shipmentNo} محلياً"
                                )
                                deleteCandidate = null
                                refresh++
                            } catch (e: Exception) {
                                deleteError = e.message ?: "تعذر حذف الشحنة"
                            } finally {
                                deleteBusy = false
                            }
                        }
                    },
                ) { Text(if (deleteBusy) "جارٍ الحذف..." else "تأكيد الحذف") }
            },
            dismissButton = {
                TextButton(enabled = !deleteBusy, onClick = { deleteCandidate = null; deleteError = null }) { Text("إلغاء") }
            },
        )
    }
}

@Composable
private fun ShipmentCreateDialog(
    container: AppContainer,
    user: UserEntity,
    warehouses: List<WarehouseEntity>,
    itemsList: List<ItemEntity>,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val governorates by container.db.geographyDao().observeGovernorates().collectAsState(initial = emptyList())
    var dateText by remember { mutableStateOf(salesDate(com.fush.erp.domain.TrustedTimeService.now())) }
    var warehouse by remember { mutableStateOf<WarehouseEntity?>(warehouses.firstOrNull()) }
    var governorate by remember { mutableStateOf<GovernorateEntity?>(null) }
    var district by remember { mutableStateOf<DistrictEntity?>(null) }
    var area by remember { mutableStateOf<AreaEntity?>(null) }
    val districts by produceState(initialValue = emptyList<DistrictEntity>(), key1 = governorate?.id) {
        value = governorate?.let { container.db.geographyDao().districtsForGovernorate(it.id) }.orEmpty()
    }
    val areas by produceState(initialValue = emptyList<AreaEntity>(), key1 = district?.id) {
        value = district?.let { container.db.geographyDao().areasForDistrict(it.id) }.orEmpty()
    }
    LaunchedEffect(governorate?.id) {
        if (district?.governorateId != governorate?.id) district = null
        area = null
    }
    LaunchedEffect(district?.id) { if (area?.districtId != district?.id) area = null }
    var transportRef by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val drafts = remember { mutableStateListOf<ShipmentItemDraftUi>() }
    var item by remember { mutableStateOf<ItemEntity?>(null) }
    var suggestedLot by remember { mutableStateOf<ShipmentService.ShipmentLotAvailability?>(null) }
    var lotLookupBusy by remember { mutableStateOf(false) }
    var lotLookupError by remember { mutableStateOf<String?>(null) }
    var qty by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("شحنة مبيعات جديدة", style = MaterialTheme.typography.headlineSmall)
                FushDateField(dateText, { dateText = it; error = null }, "تاريخ الشحنة", Modifier.fillMaxWidth())
                SalesSelectionField("من مخزن", warehouse?.nameAr ?: "اختر", warehouses, { "${it.code} — ${it.nameAr}" }) { warehouse = it }
                SalesSelectionField(
                    label = "إلى المحافظة",
                    current = governorate?.nameAr ?: "اختر المحافظة",
                    options = governorates,
                    optionLabel = { "${it.nameAr} • ${it.code}" },
                    onSelected = { governorate = it; error = null }
                )
                if (governorate != null) {
                    SalesSelectionField("المديرية", district?.nameAr ?: "بدون مديرية", districts, { it.nameAr }) { district = it; error = null }
                }
                if (district != null) {
                    SalesSelectionField("المنطقة / العزلة", area?.nameAr ?: "بدون منطقة", areas, { it.nameAr }) { area = it; error = null }
                }
                Text("تعديل الهيكل الجغرافي متاح من العملات والجغرافيا ← المحافظات والمديريات والمناطق.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(transportRef, { transportRef = it }, label = { Text("مرجع النقل") }, modifier = Modifier.fillMaxWidth())
                HorizontalDivider()
                Text("أصناف الشحنة", style = MaterialTheme.typography.titleMedium)
                SalesSelectionField("الصنف", item?.nameAr ?: "اختر", itemsList, { "${it.code} — ${it.nameAr}" }) {
                    item = it
                    suggestedLot = null
                    lotLookupError = null
                    error = null
                }
                LaunchedEffect(warehouse?.id, item?.id, dateText, drafts.size) {
                    val selectedWarehouse = warehouse
                    val selectedItem = item
                    if (selectedWarehouse == null || selectedItem == null) {
                        suggestedLot = null
                        return@LaunchedEffect
                    }
                    lotLookupBusy = true
                    lotLookupError = null
                    try {
                        val shipmentDate = salesParseDate(dateText)
                        val alreadyDrafted = drafts
                            .filter { it.item.id == selectedItem.id }
                            .groupBy { it.lotNo }
                            .mapValues { (_, rows) -> rows.sumOf { it.quantityBase } }
                        suggestedLot = ShipmentService(container.db).availableShipmentLotsFifo(
                            warehouseId = selectedWarehouse.id,
                            itemId = selectedItem.id,
                            shipmentDate = shipmentDate,
                            additionalReservedByLot = alreadyDrafted
                        ).firstOrNull()
                    } catch (e: Exception) {
                        suggestedLot = null
                        lotLookupError = e.message ?: "تعذر قراءة رصيد التشغيلات"
                    } finally {
                        lotLookupBusy = false
                    }
                }
                OutlinedTextField(
                    value = when {
                        lotLookupBusy -> "جارٍ تحديد أقدم مخزون..."
                        item == null -> "اختر الصنف أولاً"
                        lotLookupError != null -> "تعذر قراءة المخزون"
                        suggestedLot == null -> "لا يوجد مخزون متاح"
                        suggestedLot?.lotNo.isNullOrBlank() -> "بدون رقم تشغيلة"
                        else -> suggestedLot?.lotNo.orEmpty()
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("التشغيلة / Lot — تلقائي من أقدم مخزون") },
                    supportingText = {
                        when {
                            lotLookupError != null -> Text("خطأ قراءة المخزون: ${lotLookupError}")
                            suggestedLot != null -> Text("المتاح من هذه التشغيلة بعد حجوزات الشحنات المفتوحة: ${salesMoney(suggestedLot!!.availableQtyBase)}")
                            else -> Text("النظام يطبق FIFO ويختار أقدم رصيد متاح فعلياً في المخزن المحدد.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(qty, { qty = it }, label = { Text("الكمية بالوحدة الأساسية") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = {
                    val selected = item
                    val selectedWarehouse = warehouse
                    val q = qty.toDoubleOrNull()
                    if (selected == null) { error = "اختر الصنف"; return@OutlinedButton }
                    if (selectedWarehouse == null) { error = "اختر المخزن"; return@OutlinedButton }
                    if (q == null || q <= 0.0) { error = "الكمية يجب أن تكون أكبر من صفر"; return@OutlinedButton }
                    scope.launch {
                        try {
                            val shipmentDate = salesParseDate(dateText)
                            val alreadyDrafted = drafts
                                .filter { it.item.id == selected.id }
                                .groupBy { it.lotNo }
                                .mapValues { (_, rows) -> rows.sumOf { it.quantityBase } }
                            val lots = ShipmentService(container.db).availableShipmentLotsFifo(
                                warehouseId = selectedWarehouse.id,
                                itemId = selected.id,
                                shipmentDate = shipmentDate,
                                additionalReservedByLot = alreadyDrafted
                            )
                            val allocations = ShipmentService.allocateOldestAvailableLots(lots, q)
                            allocations.forEach { allocation ->
                                drafts += ShipmentItemDraftUi(selected, allocation.lotNo, allocation.quantityBase)
                            }
                            item = null
                            suggestedLot = null
                            qty = ""
                            error = null
                        } catch (e: Exception) {
                            error = e.message ?: "تعذر تحديد التشغيلة من المخزون"
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("إضافة الصنف للشحنة") }
                drafts.forEachIndexed { index, d ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${d.item.code} • ${d.item.nameAr} • ${salesMoney(d.quantityBase)}${if (d.lotNo.isNotBlank()) " • Lot ${d.lotNo}" else ""}", modifier = Modifier.weight(1f))
                            TextButton(onClick = { drafts.removeAt(index) }) { Text("حذف") }
                        }
                    }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
                error?.let { FushInlineState(it, tone = FushStatusTone.Danger) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                    Button(onClick = {
                        busy = true; error = null
                        scope.launch {
                            try {
                                val date = salesParseDate(dateText)
                                val id = ShipmentService(container.db, container.cloudSyncRepository).createShipment(
                                    ShipmentService.CreateShipmentRequest(
                                        shipmentDate = date,
                                        fromWarehouseId = requireNotNull(warehouse) { "اختر المخزن" }.id,
                                        destinationProvince = requireNotNull(governorate) { "اختر المحافظة" }.nameAr,
                                        transportReference = transportRef,
                                        notes = notes,
                                        items = drafts.map { ShipmentService.ShipmentItemDraft(it.item.id, it.lotNo, it.quantityBase) },
                                        createdBy = user.id,
                                        destinationGovernorateId = governorate!!.id,
                                        destinationDistrictId = district?.id,
                                        destinationAreaId = area?.id
                                    )
                                )
                                val row = requireNotNull(container.db.shipmentDao().shipmentById(id))
                                onDone("تم إنشاء الشحنة ${row.shipmentNo}")
                            } catch (e: Exception) { error = e.message ?: "تعذر إنشاء الشحنة" } finally { busy = false }
                        }
                    }, enabled = !busy && drafts.isNotEmpty() && governorate != null, modifier = Modifier.weight(1f)) { Text(if (busy) "جارٍ الحفظ..." else "حفظ الشحنة") }
                }
            }
        }
    }

}

@Composable
private fun AddShipmentProvinceDialog(
    container: AppContainer,
    user: UserEntity,
    currencies: List<CurrencyEntity>,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf<CurrencyEntity?>(currencies.firstOrNull { it.code == "YER_NEW" } ?: currencies.firstOrNull()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(currencies) {
        if (currency == null) currency = currencies.firstOrNull { it.code == "YER_NEW" } ?: currencies.firstOrNull()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("إضافة محافظة جديدة") },
        text = {
            com.fush.erp.ui.FushDialogForm {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("اسم المحافظة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SalesSelectionField(
                    label = "العملة الافتراضية",
                    current = currency?.let { "${it.code} — ${it.nameAr}" } ?: "اختر العملة",
                    options = currencies,
                    optionLabel = { "${it.code} — ${it.nameAr}" },
                    onSelected = { currency = it; error = null }
                )
                Text(
                    "يمكن تعديل سياسة النقل والتسعير لهذه المحافظة لاحقاً من قسم العملات والجغرافيا.",
                    style = MaterialTheme.typography.bodySmall
                )
                error?.let { FushInlineState(it, tone = FushStatusTone.Danger) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && name.isNotBlank() && currency != null,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val normalizedName = name.trim()
                            val existing = container.db.geographyDao().knownProvinceNames()
                                .firstOrNull { it.equals(normalizedName, ignoreCase = true) }
                            if (existing != null) {
                                onSaved(existing)
                            } else {
                                val generatedCode = "P" + java.lang.Long.toString(
                                    com.fush.erp.domain.TrustedTimeService.now(), 36
                                ).uppercase(Locale.US)
                                container.geographyService.upsertProvincePolicy(
                                    code = generatedCode,
                                    nameAr = normalizedName,
                                    currencyCode = requireNotNull(currency).code,
                                    defaultTransportPerCartonBase = 0.0,
                                    requiresDailyFx = false,
                                    requiresActualTransport = false,
                                    requiresFeesAndCustoms = false,
                                    notes = "أضيفت من شاشة شحنات المبيعات",
                                    userId = user.id
                                )
                                onSaved(normalizedName)
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "تعذر إضافة المحافظة"
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "جارٍ الحفظ..." else "حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("إلغاء") } }
    )
}

@Composable
private fun ShipmentDetailDialog(
    container: AppContainer,
    user: UserEntity,
    shipmentId: Long,
    currencies: List<CurrencyEntity>,
    invoices: List<SalesInvoiceSummary>,
    onDismiss: () -> Unit,
    onChanged: (String) -> Unit,
) {
    var refresh by remember { mutableIntStateOf(0) }
    val shipment by produceState<SalesShipmentEntity?>(initialValue = null, key1 = shipmentId, key2 = refresh) { value = container.db.shipmentDao().shipmentById(shipmentId) }
    val itemRows by produceState(initialValue = emptyList<ShipmentItemAllocationRow>(), key1 = shipmentId, key2 = refresh) { value = container.db.shipmentDao().shipmentItemAllocationRows(shipmentId) }
    val expenses by produceState(initialValue = emptyList<SalesShipmentExpenseEntity>(), key1 = shipmentId, key2 = refresh) { value = container.db.shipmentDao().expensesForShipment(shipmentId) }
    val links by produceState(initialValue = emptyList<ShipmentInvoiceLinkRow>(), key1 = shipmentId, key2 = refresh) { value = container.db.shipmentDao().shipmentInvoiceLinks(shipmentId) }
    val summary by produceState<ShipmentSummaryRow?>(initialValue = null, key1 = shipmentId, key2 = refresh) { value = container.db.shipmentDao().shipmentSummaries().firstOrNull { it.id == shipmentId } }
    val candidateInvoices by produceState(initialValue = emptyList<SalesInvoiceSummary>(), key1 = shipmentId, key2 = invoices, key3 = refresh) {
        val shipmentRow = container.db.shipmentDao().shipmentById(shipmentId)
        val governorateId = shipmentRow?.destinationGovernorateId
        value = if (governorateId == null) emptyList() else invoices.filter { summaryRow ->
            container.db.salesDao().invoiceById(summaryRow.id)?.governorateId == governorateId
        }
    }
    var showExpense by remember { mutableStateOf(false) }
    var showAllocation by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                val row = shipment
                Text("تفاصيل الشحنة ${row?.shipmentNo.orEmpty()}", style = MaterialTheme.typography.headlineSmall)
                row?.let {
                    Text("${salesDate(it.shipmentDate)} • إلى ${it.destinationProvince} • ${shipmentStatusLabel(it.status)}")
                    if (it.transportReference.isNotBlank()) Text("مرجع النقل: ${it.transportReference}")
                }
                summary?.let { Text("التكلفة: ${salesMoney(it.totalExpenseBase)} • الموزع: ${salesMoney(it.allocatedExpenseBase)} • المتبقي: ${salesMoney(it.remainingExpenseBase)} أساسي", style = MaterialTheme.typography.titleMedium) }
                HorizontalDivider()
                Text("أصناف وتشغيلات الشحنة", style = MaterialTheme.typography.titleMedium)
                itemRows.forEach { i -> Text("${i.itemCode} • ${i.itemName}${if (i.lotNo.isNotBlank()) " • Lot ${i.lotNo}" else ""} • شُحن ${salesMoney(i.shippedQtyBase)} • وُزع ${salesMoney(i.allocatedQtyBase)} • متبقي ${salesMoney(i.remainingQtyBase)}") }
                HorizontalDivider()
                Text("مصاريف الشحنة الفعلية — حساب 6430", style = MaterialTheme.typography.titleMedium)
                if (expenses.isEmpty()) FushInlineState("لم تسجل مصاريف لهذه الشحنة بعد.")
                expenses.forEach { e -> Text("${shipmentExpenseTypeLabel(e.expenseType)} • ${salesMoney(e.amountBase)} أساسي • يتحمل: ${chargeBearerLabel(e.bearer)} • ${e.paymentMethod} • سند ${e.paymentVoucherNo}${if (e.paymentReference.isNotBlank()) " • مرجع ${e.paymentReference}" else ""}") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showExpense = true }, modifier = Modifier.weight(1f)) { Text("إضافة مصروف") }
                    OutlinedButton(onClick = { showAllocation = true }, enabled = expenses.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("تخصيص على فاتورة") }
                }
                HorizontalDivider()
                Text("الفواتير المرتبطة", style = MaterialTheme.typography.titleMedium)
                if (links.isEmpty()) FushInlineState("لم تُربط فواتير بهذه الشحنة بعد.")
                links.forEach { l -> Text("${l.invoiceNo} • ${l.customerName} • كمية ${salesMoney(l.allocatedQuantityBase)} • تكلفة شحن ${salesMoney(l.allocatedCostBase)} أساسي") }
                FushInlineState("مصروف الشحنة يُسجل مرة واحدة فقط. إذا كان المتحمل=العميل، تضيف الفاتورة حصته الفعلية تلقائياً وتربطها بنفس المصروف؛ وإذا كان المتحمل=الشركة تبقى الحصة تكلفة ربحية فقط. سعر بيع الصنف لا يتغير.", tone = FushStatusTone.Info)
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("إغلاق") }
            }
        }
    }

    if (showExpense) {
        ShipmentExpenseDialog(container, user, shipmentId, currencies, onDismiss = { showExpense = false }) { text ->
            showExpense = false; refresh++; onChanged(text)
        }
    }
    if (showAllocation) {
        ShipmentAllocationDialog(container, user, shipmentId, candidateInvoices, onDismiss = { showAllocation = false }) { text ->
            showAllocation = false; refresh++; onChanged(text)
        }
    }
}

@Composable
private fun ShipmentExpenseDialog(
    container: AppContainer,
    user: UserEntity,
    shipmentId: Long,
    currencies: List<CurrencyEntity>,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val treasuries by produceState(initialValue = emptyList<TreasuryAccountEntity>()) { value = container.db.accountingDao().allActiveTreasury() }
    var type by remember { mutableStateOf("TRANSPORT") }
    var bearer by remember { mutableStateOf("COMPANY") }
    var dateText by remember { mutableStateOf(salesDate(com.fush.erp.domain.TrustedTimeService.now())) }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf<CurrencyEntity?>(currencies.firstOrNull { it.code == "YER_NEW" } ?: currencies.firstOrNull()) }
    var rate by remember { mutableStateOf(if (currency?.code == "YER_NEW") "1" else "") }
    var treasury by remember { mutableStateOf<TreasuryAccountEntity?>(null) }
    var description by remember { mutableStateOf("") }
    var paymentRef by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val operationId = remember { TreasuryVoucherOperationIdentity.newOperationId() }
    val compatibleTreasuries = remember(treasuries, currency) { treasuries.filter { it.currencyCode == currency?.code } }
    LaunchedEffect(currency?.code) { treasury = compatibleTreasuries.firstOrNull(); if (currency?.code == "YER_NEW") rate = "1" }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("إضافة مصروف شحنة", style = MaterialTheme.typography.headlineSmall)
                SalesStringSelectionField("نوع المصروف", type, listOf("TRANSPORT","LOADING","CUSTOMS","OTHER"), ::shipmentExpenseTypeLabel) { type = it }
                SalesStringSelectionField("من يتحمل المصروف", bearer, listOf("COMPANY", "CUSTOMER"), ::chargeBearerLabel) { bearer = it }
                FushDateField(dateText, { dateText = it }, "تاريخ الدفع", Modifier.fillMaxWidth())
                OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ") }, modifier = Modifier.fillMaxWidth())
                SalesSelectionField("العملة", currency?.code ?: "اختر", currencies, { "${it.code} — ${it.nameAr}" }) { currency = it }
                OutlinedTextField(rate, { rate = it }, label = { Text("سعر الصرف") }, modifier = Modifier.fillMaxWidth())
                SalesSelectionField("حساب الدفع", treasury?.nameAr ?: "اختر", compatibleTreasuries, { "${it.kind} — ${it.nameAr}" }) { treasury = it }
                treasury?.let { Text("طريقة الدفع: ${if (it.kind == "BANK") "بنك" else "صندوق"}", style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(paymentRef, { paymentRef = it }, label = { Text("مرجع الدفع / النقل") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("البيان") }, modifier = Modifier.fillMaxWidth())
                FushInlineState(
                    if (bearer == "CUSTOMER")
                        "سيُنشأ سند الصرف مرة واحدة على 6430. عند البيع من هذه الشحنة ستُحمّل على العميل حصته الفعلية من نفس المصروف تلقائياً، مع ربطها بالمصروف الأصلي دون إنشاء مصروف ثانٍ."
                    else
                        "سيُنشأ سند الصرف مرة واحدة على 6430. عند توزيع الشحنة على الفواتير ستظهر حصة التكلفة في الربحية فقط ولن تُضاف على العميل.",
                    tone = FushStatusTone.Info
                )
                error?.let { FushInlineState(it, tone = FushStatusTone.Danger) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) { Text("إلغاء") }
                    Button(onClick = {
                        busy = true; error = null
                        scope.launch {
                            try {
                                val id = ShipmentService(container.db).postActualExpense(
                                    ShipmentService.PostExpenseRequest(
                                        shipmentId = shipmentId, expenseType = type, description = description,
                                        expenseDate = salesParseDate(dateText), amountOriginal = requireNotNull(amount.toDoubleOrNull()) { "المبلغ غير صالح" },
                                        currencyCode = requireNotNull(currency) { "اختر العملة" }.code,
                                        exchangeRate = requireNotNull(rate.toDoubleOrNull()) { "سعر الصرف غير صالح" },
                                        treasuryAccountId = requireNotNull(treasury) { "اختر حساب الدفع" }.id,
                                        paymentReference = paymentRef, bearer = bearer, operationId = operationId, createdBy = user.id
                                    )
                                )
                                val e = requireNotNull(container.db.shipmentDao().expenseById(id))
                                onDone("تم تسجيل مصروف الشحنة بسند ${e.paymentVoucherNo}")
                            } catch (e: Exception) { error = e.message ?: "تعذر تسجيل المصروف" } finally { busy = false }
                        }
                    }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("ترحيل سند الصرف") }
                }
            }
        }
    }
}

@Composable
private fun ShipmentAllocationDialog(
    container: AppContainer,
    user: UserEntity,
    shipmentId: Long,
    invoices: List<SalesInvoiceSummary>,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val expenses by produceState(initialValue = emptyList<SalesShipmentExpenseEntity>(), key1 = shipmentId) { value = container.db.shipmentDao().expensesForShipment(shipmentId) }
    val itemRows by produceState(initialValue = emptyList<ShipmentItemAllocationRow>(), key1 = shipmentId) { value = container.db.shipmentDao().shipmentItemAllocationRows(shipmentId) }
    var invoice by remember { mutableStateOf<SalesInvoiceSummary?>(null) }
    val manual = remember { mutableStateMapOf<Long, String>() }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(expenses) { expenses.forEach { e -> if (!manual.containsKey(e.id)) manual[e.id] = "" } }
    val singleItem = itemRows.map { it.itemId }.distinct().size == 1

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تخصيص الشحنة على فاتورة", style = MaterialTheme.typography.headlineSmall)
                SalesSelectionField("الفاتورة", invoice?.let { "${it.invoiceNo} — ${it.customerName}" } ?: "اختر", invoices, { "${it.invoiceNo} — ${it.customerName}" }) { invoice = it }
                if (singleItem) {
                    FushInlineState("هذه شحنة بصنف واحد: يمكنك استخدام التوزيع التلقائي بالكمية. مثال 10 باكت + فاتورة 5 باكت = 50% من كل مصروف متاح.", tone = FushStatusTone.Info)
                    Button(onClick = {
                        busy = true; error = null
                        scope.launch {
                            try {
                                val result = ShipmentService(container.db).allocateExpensesByQuantity(shipmentId, requireNotNull(invoice) { "اختر الفاتورة" }.id, user.id)
                                onDone("تم توزيع ${salesMoney(result.allocatedExpenseBase)} أساسي؛ المتبقي للشحنة ${salesMoney(result.shipmentRemainingExpenseBase)}")
                            } catch (e: Exception) { error = e.message ?: "تعذر التوزيع" } finally { busy = false }
                        }
                    }, enabled = !busy && invoice != null, modifier = Modifier.fillMaxWidth()) { Text("توزيع تلقائي حسب الكمية") }
                } else {
                    FushInlineState("الشحنة متعددة الأصناف؛ أدخل حصة كل مصروف يدوياً لتجنب افتراض وزن غير صحيح بين وحدات مختلفة.", tone = FushStatusTone.Warning)
                }
                HorizontalDivider()
                Text("توزيع يدوي للمصاريف", style = MaterialTheme.typography.titleMedium)
                expenses.forEach { e ->
                    val allocated by produceState(initialValue = 0.0, key1 = e.id) { value = container.db.shipmentDao().allocatedExpenseBase(e.id) }
                    val remaining = (e.amountBase - allocated).coerceAtLeast(0.0)
                    OutlinedTextField(
                        value = manual[e.id].orEmpty(), onValueChange = { manual[e.id] = it },
                        label = { Text("${shipmentExpenseTypeLabel(e.expenseType)} — المتبقي ${salesMoney(remaining)} أساسي") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedButton(onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val map = manual.mapNotNull { (id, text) -> text.toDoubleOrNull()?.takeIf { it > 0.0 }?.let { id to it } }.toMap()
                            require(map.isNotEmpty()) { "أدخل مبلغ تخصيص واحداً على الأقل" }
                            val result = ShipmentService(container.db).allocateExpensesManually(shipmentId, requireNotNull(invoice) { "اختر الفاتورة" }.id, map, user.id)
                            onDone("تم تخصيص ${salesMoney(result.allocatedExpenseBase)} أساسي؛ المتبقي ${salesMoney(result.shipmentRemainingExpenseBase)}")
                        } catch (e: Exception) { error = e.message ?: "تعذر التخصيص" } finally { busy = false }
                    }
                }, enabled = !busy && invoice != null, modifier = Modifier.fillMaxWidth()) { Text("حفظ التوزيع اليدوي") }
                error?.let { FushInlineState(it, tone = FushStatusTone.Danger) }
                TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("إغلاق") }
            }
        }
    }
}

private fun shipmentExpenseTypeLabel(value: String): String = when(value) {
    "TRANSPORT" -> "نقل"
    "LOADING" -> "تحميل وتنزيل"
    "CUSTOMS" -> "جمارك"
    "OTHER" -> "أخرى"
    else -> value
}

private fun shipmentStatusLabel(value: String): String = when(value) {
    "DRAFT" -> "مسودة"
    "IN_TRANSIT" -> "قيد النقل"
    "DELIVERED" -> "تم التسليم"
    "CLOSED" -> "مغلقة"
    "CANCELLED" -> "ملغاة"
    else -> value
}


private fun chargeBearerLabel(value: String): String = when (value) {
    "CUSTOMER" -> "العميل"
    "COMPANY" -> "الشركة"
    else -> value
}

private fun chargePaymentStatusLabel(value: String): String = when (value) {
    "PAID" -> "مدفوع"
    "PARTIAL" -> "جزئي"
    "UNPAID" -> "غير مدفوع"
    else -> value
}

private fun chargePaidByLabel(value: String): String = when (value) {
    "COMPANY" -> "الشركة"
    "CUSTOMER_DIRECT" -> "العميل مباشرة"
    else -> value
}

private fun chargeTreatmentLabel(value: String): String = when (value) {
    "RECOVERABLE" -> "قابل للاسترداد من العميل"
    "COMPANY_EXPENSE" -> "مصروف على الشركة"
    "SERVICE_REVENUE" -> "إيراد خدمة فعلية"
    else -> value
}

private fun chargePrincipalAgentLabel(value: String): String = when (value) {
    "PRINCIPAL" -> "أصيل Principal"
    "AGENT" -> "وكيل Agent"
    "REVIEW" -> "يُراجع حسب العقد"
    else -> value
}

@Composable
private fun <T> SalesSelectionField(
    label: String,
    current: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T?) -> Unit
) {
    FushSearchableSelectionField(
        label = label,
        selectedText = current.takeUnless { it == "اختر" || it.startsWith("اختر ") } ?: "",
        options = options,
        optionText = optionLabel,
        onSelected = { onSelected(it) },
        onCleared = { onSelected(null) },
        placeholder = current,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SalesStringSelectionField(label: String, current: String, options: List<String>, optionLabel: (String) -> String = { it }, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = optionLabel(current),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelected(option); expanded = false }) }
        }
    }
}

private fun salesChannelLabel(value: String): String = when (value) {
    "DIRECT" -> "بيع مباشر"
    "RETAIL" -> "متجر/تجزئة"
    "DISTRIBUTOR_CASH" -> "موزع نقدي"
    "DISTRIBUTOR_CREDIT" -> "موزع آجل"
    else -> value
}

private fun salesMoney(value: Double): String = if (kotlin.math.abs(value - value.toLong()) < 0.000001) value.toLong().toString() else "%.2f".format(Locale.US, value)
private fun salesRateRaw(value: Double): String {
    val canonical = SalesExchangeRatePolicy.canonical(value)
    return "%.8f".format(Locale.US, canonical).trimEnd('0').trimEnd('.').ifBlank { "0" }
}

private fun salesMoneyRaw(value: Double): String = if (kotlin.math.abs(value - value.toLong()) < 0.000001) value.toLong().toString() else "%.4f".format(Locale.US, value)
private fun salesEndOfDay(value: Long): Long = BusinessDatePolicy.endOfBusinessDay(value)

private fun salesDate(value: Long): String = BusinessDatePolicy.formatIsoDate(value)
private fun salesParseDate(value: String): Long = try {
    BusinessDatePolicy.parseIsoDateStart(value)
} catch (_: Exception) {
    error("تاريخ غير صالح")
}
