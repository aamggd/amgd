package com.fush.erp.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import com.fush.erp.R
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.*
import com.fush.erp.ui.*
import com.fush.erp.domain.AccountingService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CustomersScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier) {
    val customers by container.db.customerDao().observeAll().collectAsState(initial = emptyList())
    val receivables by container.db.salesDao().observeReceivables(System.currentTimeMillis()).collectAsState(initial = emptyList())
    val currencies by container.db.currencyDao().observeAll().collectAsState(initial = emptyList())
    val salesReps by container.db.salesRepresentativeDao().observeActive().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<CustomerEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val noGovernorate = stringResource(R.string.customers_no_governorate)

    if (selected != null) {
        CustomerProfileScreen(container, user, selected!!, onBack = { selected = null }, modifier = modifier)
        return
    }

    val filtered = remember(customers, search) {
        val q = search.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) customers else customers.filter {
            listOf(it.nameAr, it.nameEn, it.code, it.phone, it.province, it.address).any { x -> x.lowercase(Locale.ROOT).contains(q) }
        }
    }
    val totalOutstanding = remember(receivables) { receivables.sumOf { it.outstandingBase } }
    val totalOverdue = remember(receivables) { receivables.sumOf { it.overdueBase } }
    val creditEnabled = remember(customers) { customers.count { it.allowCredit } }

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
                    title = stringResource(R.string.customers_title),
                    subtitle = stringResource(R.string.customers_subtitle),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Button(onClick = { showAdd = true }, shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.customers_new)) }
            }
        }

        if (message != null) {
            item { FushOperationMessage(message, onConsumed = { message = null }) }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(
                    label = stringResource(R.string.customers_count),
                    value = customers.size.toString(),
                    helper = stringResource(R.string.customers_registered),
                    modifier = Modifier.weight(1f),
                    tone = FushStatusTone.Info,
                )
                FushMetricCard(
                    label = stringResource(R.string.customers_total_receivables),
                    value = partyMoney(totalOutstanding),
                    helper = stringResource(R.string.common_base_currency),
                    modifier = Modifier.weight(1f),
                    tone = if (totalOutstanding > 0.000001) FushStatusTone.Warning else FushStatusTone.Success,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(
                    label = stringResource(R.string.customers_overdue),
                    value = partyMoney(totalOverdue),
                    helper = stringResource(R.string.customers_need_followup),
                    modifier = Modifier.weight(1f),
                    tone = if (totalOverdue > 0.000001) FushStatusTone.Danger else FushStatusTone.Success,
                )
                FushMetricCard(
                    label = stringResource(R.string.customers_credit_allowed),
                    value = creditEnabled.toString(),
                    helper = stringResource(R.string.customers_credit_policy_helper),
                    modifier = Modifier.weight(1f),
                    tone = FushStatusTone.Info,
                )
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text(stringResource(R.string.customers_search)) },
                placeholder = { Text(stringResource(R.string.customers_search_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                supportingText = { Text(stringResource(R.string.customers_showing, filtered.size, customers.size)) },
            )
        }

        if (filtered.isEmpty()) {
            item {
                FushEmptyState(
                    title = if (customers.isEmpty()) stringResource(R.string.sales_no_customers) else stringResource(R.string.common_no_matching_results),
                    detail = if (customers.isEmpty()) stringResource(R.string.customers_empty_detail) else stringResource(R.string.customers_search_empty_detail),
                )
            }
        }

        items(filtered, key = { it.id }) { customer ->
            val balance = receivables.firstOrNull { it.customerId == customer.id }
            val outstanding = balance?.outstandingBase ?: 0.0
            val overdue = balance?.overdueBase ?: 0.0
            val balanceTone = when {
                overdue > 0.000001 -> FushStatusTone.Danger
                outstanding > 0.000001 -> FushStatusTone.Warning
                else -> FushStatusTone.Success
            }

            ElevatedCard(
                onClick = { selected = customer },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FushUserAvatar(customer.nameAr)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(customer.nameAr, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${customer.code} • ${customer.province.ifBlank { noGovernorate }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            FushStatusPill(customer.currencyCode, FushStatusTone.Info)
                            FushStatusPill(stringResource(R.string.customers_classification, customer.classification), FushStatusTone.Neutral)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.customers_outstanding_balance), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(partyMoney(outstanding), style = MaterialTheme.typography.titleLarge)
                        }
                        FushStatusPill(
                            when {
                                overdue > 0.000001 -> stringResource(R.string.customers_overdue_amount, partyMoney(overdue))
                                outstanding > 0.000001 -> stringResource(R.string.customers_open_receivable)
                                else -> stringResource(R.string.customers_no_receivable)
                            },
                            balanceTone,
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (customer.allowCredit) stringResource(R.string.customers_credit_days, customer.creditDays) else stringResource(R.string.customers_cash_default),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(stringResource(R.string.customers_open_profile), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddCustomerPartyDialog(currencies, salesReps, onDismiss = { showAdd = false }) { name, phone, province, channel, currency, limit, days, credit, rep ->
            scope.launch {
                try {
                    val row = container.salesService.createCustomer(name, phone, province, channel, currency.code, limit, days, credit, rep?.fullNameAr.orEmpty(), user.id, rep?.id)
                    message = "تم إنشاء العميل ${row.code}"
                    showAdd = false
                } catch (e: Exception) { message = e.message ?: "تعذر إنشاء العميل" }
            }
        }
    }
}

@Composable
fun SuppliersScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier) {
    val suppliers by container.db.supplierDao().observeAll().collectAsState(initial = emptyList())
    val balances by container.db.purchaseDao().observeSupplierBalances(System.currentTimeMillis()).collectAsState(initial = emptyList())
    val currencies by container.db.currencyDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<SupplierEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val noContact = stringResource(R.string.common_no_contact)

    if (selected != null) {
        SupplierProfileScreen(container, user, selected!!, onBack = { selected = null }, modifier = modifier)
        return
    }

    val filtered = remember(suppliers, search) {
        val q = search.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) suppliers else suppliers.filter {
            listOf(it.nameAr, it.nameEn, it.code, it.phone, it.address, it.currencyCode).any { x -> x.lowercase(Locale.ROOT).contains(q) }
        }
    }
    val totalOutstanding = remember(balances) { balances.sumOf { it.outstandingBase } }
    val totalOverdue = remember(balances) { balances.sumOf { it.overdueBase } }
    val creditTermsCount = remember(suppliers) { suppliers.count { it.paymentTermsDays > 0 } }

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
                    title = stringResource(R.string.suppliers_title),
                    subtitle = stringResource(R.string.suppliers_subtitle),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Button(onClick = { showAdd = true }, shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.suppliers_new)) }
            }
        }

        if (message != null) {
            item { FushOperationMessage(message, onConsumed = { message = null }) }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(
                    label = stringResource(R.string.suppliers_count),
                    value = suppliers.size.toString(),
                    helper = stringResource(R.string.suppliers_registered),
                    modifier = Modifier.weight(1f),
                    tone = FushStatusTone.Info,
                )
                FushMetricCard(
                    label = stringResource(R.string.suppliers_total_due),
                    value = partyMoney(totalOutstanding),
                    helper = stringResource(R.string.common_base_currency),
                    modifier = Modifier.weight(1f),
                    tone = if (totalOutstanding > 0.000001) FushStatusTone.Warning else FushStatusTone.Success,
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(
                    label = stringResource(R.string.suppliers_overdue),
                    value = partyMoney(totalOverdue),
                    helper = stringResource(R.string.suppliers_liability_followup),
                    modifier = Modifier.weight(1f),
                    tone = if (totalOverdue > 0.000001) FushStatusTone.Danger else FushStatusTone.Success,
                )
                FushMetricCard(
                    label = stringResource(R.string.suppliers_with_terms),
                    value = creditTermsCount.toString(),
                    helper = stringResource(R.string.suppliers_terms_helper),
                    modifier = Modifier.weight(1f),
                    tone = FushStatusTone.Info,
                )
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text(stringResource(R.string.suppliers_search)) },
                placeholder = { Text(stringResource(R.string.suppliers_search_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                supportingText = { Text(stringResource(R.string.suppliers_showing, filtered.size, suppliers.size)) },
            )
        }

        if (filtered.isEmpty()) {
            item {
                FushEmptyState(
                    title = if (suppliers.isEmpty()) stringResource(R.string.purchases_no_suppliers) else stringResource(R.string.common_no_matching_results),
                    detail = if (suppliers.isEmpty()) stringResource(R.string.suppliers_empty_detail) else stringResource(R.string.customers_search_empty_detail),
                )
            }
        }

        items(filtered, key = { it.id }) { supplier ->
            val balance = balances.firstOrNull { it.supplierId == supplier.id }
            val outstanding = balance?.outstandingBase ?: 0.0
            val overdue = balance?.overdueBase ?: 0.0
            val balanceTone = when {
                overdue > 0.000001 -> FushStatusTone.Danger
                outstanding > 0.000001 -> FushStatusTone.Warning
                else -> FushStatusTone.Success
            }

            ElevatedCard(
                onClick = { selected = supplier },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
            ) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FushUserAvatar(supplier.nameAr)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(supplier.nameAr, style = MaterialTheme.typography.titleMedium)
                            Text(
                                supplier.code,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            FushStatusPill(supplier.currencyCode, FushStatusTone.Info)
                            FushStatusPill(
                                if (supplier.paymentTermsDays > 0) stringResource(R.string.suppliers_payment_terms, supplier.paymentTermsDays) else stringResource(R.string.common_cash),
                                if (supplier.paymentTermsDays > 0) FushStatusTone.Warning else FushStatusTone.Success,
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.suppliers_due_balance), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(partyMoney(outstanding), style = MaterialTheme.typography.titleLarge)
                        }
                        FushStatusPill(
                            when {
                                overdue > 0.000001 -> stringResource(R.string.suppliers_overdue_amount, partyMoney(overdue))
                                outstanding > 0.000001 -> stringResource(R.string.suppliers_open_liability)
                                else -> stringResource(R.string.suppliers_no_due)
                            },
                            balanceTone,
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            supplier.address.ifBlank { supplier.phone.ifBlank { noContact } },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.suppliers_open_profile), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddSupplierPartyDialog(currencies, onDismiss = { showAdd = false }) { name, phone, currency, days, address ->
            scope.launch {
                try {
                    val row = container.purchaseService.createSupplier(name, phone, currency.code, days, address = address, createdBy = user.id)
                    message = "تم إنشاء المورد ${row.code}"
                    showAdd = false
                } catch (e: Exception) { message = e.message ?: "تعذر إنشاء المورد" }
            }
        }
    }
}

@Composable
private fun CustomerProfileScreen(container: AppContainer, user: UserEntity, customer: CustomerEntity, onBack: () -> Unit, modifier: Modifier) {
    val tabs = listOf("المعلومات", "كشف الحساب", "الفواتير", "التحصيلات", "السندات", "المرتجعات", "العمولات", "المرفقات", "التدقيق")
    var tab by remember { mutableIntStateOf(0) }
    var showSettlement by remember { mutableStateOf(false) }
    var reverseVoucher by remember { mutableStateOf<PartyVoucherEntity?>(null) }
    var reverseReceipt by remember { mutableStateOf<CustomerReceiptEntity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val vouchers by container.db.partyDao().observeCustomerVouchers(customer.id).collectAsState(initial = emptyList())
    val attachments by container.db.partyDao().observeCustomerAttachments(customer.id).collectAsState(initial = emptyList())
    val audits by container.db.governanceDao().observeCustomerAudit(customer.id).collectAsState(initial = emptyList())
    val events by produceState(initialValue = emptyList<CustomerLedgerEventRow>(), customer.id, vouchers) { value = container.db.salesDao().customerLedgerEvents(customer.id) }
    val invoices by produceState(initialValue = emptyList<SalesInvoiceEntity>(), customer.id) { value = container.db.salesDao().customerInvoices(customer.id) }
    val receipts by produceState(initialValue = emptyList<CustomerReceiptEntity>(), customer.id, message) { value = container.db.salesDao().customerReceipts(customer.id) }
    val returns by produceState(initialValue = emptyList<SalesReturnEntity>(), customer.id) { value = container.db.salesDao().customerReturns(customer.id) }
    val commissions by produceState(initialValue = emptyList<SalesCommissionEntity>(), customer.id) { value = container.db.salesDao().customerCommissions(customer.id) }
    val running = remember(events) { var b=0.0; events.map { b += it.debitBase-it.creditBase; it to b } }
    val currentBalance = running.lastOrNull()?.second ?: 0.0

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onBack, shape = MaterialTheme.shapes.medium) { Text("رجوع") }
                    FushUserAvatar(customer.nameAr)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(customer.nameAr, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${customer.code} • ${customer.province} • ${customer.currencyCode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FushStatusPill(if (customer.isActive) "نشط" else "غير نشط", if (customer.isActive) FushStatusTone.Success else FushStatusTone.Neutral)
                    FushStatusPill(if (customer.allowCredit) "الآجل مسموح" else "نقدي", if (customer.allowCredit) FushStatusTone.Warning else FushStatusTone.Success)
                    FushStatusPill("تصنيف ${customer.classification}", FushStatusTone.Info)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FushMetricCard(
                        label = "الرصيد الحالي",
                        value = partyMoney(currentBalance),
                        helper = stringResource(R.string.common_base_currency),
                        modifier = Modifier.weight(1f),
                        tone = if (currentBalance > 0.000001) FushStatusTone.Warning else FushStatusTone.Success,
                    )
                    FushMetricCard(
                        label = "فواتير المبيعات",
                        value = invoices.size.toString(),
                        helper = "${receipts.size} تحصيل • ${returns.size} مرتجع",
                        modifier = Modifier.weight(1f),
                        tone = FushStatusTone.Info,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showSettlement = true }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) { Text("تحصيل فاتورة") }
                    OutlinedButton(
                        onClick = { message = "صرف مبلغ للعميل يجب أن يتم من مرتجع المبيعات مع اختيار رد نقدي حتى تبقى الفاتورة والذمة والأستاذ متطابقة." },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                    ) { Text("صرف للعميل") }
                }
                FushOperationMessage(message, onConsumed = { message = null })
            }
        }
        item {
            ScrollableTabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                edgePadding = 10.dp,
            ) {
                tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }
        }
        when (tab) {
            0 -> item { PartyInfoCustomer(customer) }
            1 -> customerLedgerItems(running)
            2 -> profileSimpleEntityItems(if (invoices.isEmpty()) "لا توجد فواتير مبيعات." else null, invoices) { r -> "${partyDate(r.invoiceDate)} • ${r.invoiceNo} • ${partyMoney(r.totalBase)} • ${if(r.paymentType=="CASH") "نقدي" else "آجل"}" }
            3 -> customerReceiptItems(receipts, onReverse = { reverseReceipt = it })
            4 -> voucherItems(vouchers, onReverse = { reverseVoucher = it })
            5 -> profileSimpleEntityItems(if (returns.isEmpty()) "لا توجد مرتجعات." else null, returns) { r -> "${partyDate(r.returnDate)} • ${r.returnNo} • ${partyMoney(r.totalBase)} • ${r.reason}" }
            6 -> profileSimpleEntityItems(if (commissions.isEmpty()) "لا توجد عمولات مرتبطة بمبيعات العميل." else null, commissions) { r -> "فاتورة #${r.invoiceId} • مستحق ${partyMoney(r.earnedBase)} • معكوس ${partyMoney(r.reversedBase)} • ${r.beneficiary}" }
            7 -> item { AttachmentTab(container, user, customerId = customer.id, supplierId = null, attachments = attachments, onMessage = { message=it }) }
            else -> auditItems(audits)
        }
    }
    if (showSettlement) {
        CustomerPartySettlementDialog(container, user, customer, onDismiss = { showSettlement = false }) { text ->
            message = text
            showSettlement = false
        }
    }
    reverseVoucher?.let { voucher -> ReverseVoucherDialog(container, user, voucher, onDismiss={reverseVoucher=null}) { message=it; reverseVoucher=null } }
    reverseReceipt?.let { receipt -> ReverseCustomerReceiptDialog(container, user, receipt, onDismiss={reverseReceipt=null}) { message=it; reverseReceipt=null } }
}

@Composable
private fun SupplierProfileScreen(container: AppContainer, user: UserEntity, supplier: SupplierEntity, onBack: () -> Unit, modifier: Modifier) {
    val tabs = listOf("المعلومات", "كشف الحساب", "فواتير المشتريات", "دفعات المورد", "السندات", "المرتجعات", "المرفقات", "التدقيق")
    var tab by remember { mutableIntStateOf(0) }
    var showSettlement by remember { mutableStateOf(false) }
    var reverseVoucher by remember { mutableStateOf<PartyVoucherEntity?>(null) }
    var reversePayment by remember { mutableStateOf<SupplierPaymentDetailRow?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val vouchers by container.db.partyDao().observeSupplierVouchers(supplier.id).collectAsState(initial = emptyList())
    val attachments by container.db.partyDao().observeSupplierAttachments(supplier.id).collectAsState(initial = emptyList())
    val audits by container.db.governanceDao().observeSupplierAudit(supplier.id).collectAsState(initial = emptyList())
    val events by produceState(initialValue = emptyList<SupplierLedgerEventRow>(), supplier.id, vouchers) { value = container.db.purchaseDao().supplierLedgerEvents(supplier.id, System.currentTimeMillis()) }
    val invoices by produceState(initialValue = emptyList<PurchaseInvoiceEntity>(), supplier.id) { value = container.db.purchaseDao().supplierInvoices(supplier.id) }
    val payments by produceState(initialValue = emptyList<SupplierPaymentDetailRow>(), supplier.id, message) { value = container.db.purchaseDao().supplierPayments(supplier.id) }
    val returns by produceState(initialValue = emptyList<PurchaseReturnEntity>(), supplier.id) { value = container.db.purchaseDao().supplierReturns(supplier.id) }
    val running = remember(events) { var b=0.0; events.map { b += it.creditBase-it.debitBase; it to b } }
    val currentBalance = running.lastOrNull()?.second ?: 0.0

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onBack, shape = MaterialTheme.shapes.medium) { Text("رجوع") }
                    FushUserAvatar(supplier.nameAr)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(supplier.nameAr, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${supplier.code} • ${supplier.currencyCode} • ${supplier.address.ifBlank { "بدون عنوان" }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FushStatusPill(if (supplier.isActive) "نشط" else "غير نشط", if (supplier.isActive) FushStatusTone.Success else FushStatusTone.Neutral)
                    FushStatusPill(if (supplier.paymentTermsDays > 0) "أجل ${supplier.paymentTermsDays} يوم" else "نقدي", if (supplier.paymentTermsDays > 0) FushStatusTone.Warning else FushStatusTone.Success)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FushMetricCard(
                        label = "الرصيد المستحق",
                        value = partyMoney(currentBalance),
                        helper = "المبلغ المستحق للمورد",
                        modifier = Modifier.weight(1f),
                        tone = if (currentBalance > 0.000001) FushStatusTone.Warning else FushStatusTone.Success,
                    )
                    FushMetricCard(
                        label = "فواتير المشتريات",
                        value = invoices.size.toString(),
                        helper = "${payments.size} دفعة • ${returns.size} مرتجع",
                        modifier = Modifier.weight(1f),
                        tone = FushStatusTone.Info,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showSettlement = true }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) { Text("دفع فاتورة") }
                    OutlinedButton(
                        onClick = { message = "قبض مبلغ من المورد يجب أن يتم من مرتجع المشتريات مع اختيار استرداد نقدي حتى تبقى الفاتورة والذمة والأستاذ متطابقة." },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                    ) { Text("قبض من المورد") }
                }
                FushOperationMessage(message, onConsumed = { message = null })
            }
        }
        item {
            ScrollableTabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                edgePadding = 10.dp,
            ) {
                tabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
            }
        }
        when (tab) {
            0 -> item { PartyInfoSupplier(supplier) }
            1 -> supplierLedgerItems(running)
            2 -> profileSimpleEntityItems(if (invoices.isEmpty()) "لا توجد فواتير مشتريات." else null, invoices) { r -> "${partyDate(r.invoiceDate)} • ${r.invoiceNo} • ${partyMoney(r.totalBase)}" }
            3 -> profileSimpleEntityItems(if (payments.isEmpty()) "لا توجد دفعات للمورد." else null, payments) { r -> "${partyDate(r.paymentDate)} • ${r.paymentNo} • ${partyMoney(r.cashAmountBase)} • ${r.treasuryName}${if (r.invoiceNo.isNotBlank()) " • ${r.invoiceNo}" else ""}" }
            4 -> voucherItems(vouchers, onReverse = { reverseVoucher = it })
            5 -> profileSimpleEntityItems(if (returns.isEmpty()) "لا توجد مرتجعات مشتريات." else null, returns) { r -> "${partyDate(r.returnDate)} • ${r.returnNo} • ${partyMoney(r.totalBase)} • ${r.reason}" }
            6 -> item { AttachmentTab(container, user, customerId = null, supplierId = supplier.id, attachments = attachments, onMessage = { message=it }) }
            else -> auditItems(audits)
        }
    }
    if (showSettlement) {
        SupplierPartySettlementDialog(container, user, supplier, onDismiss = { showSettlement = false }) { text ->
            message = text
            showSettlement = false
        }
    }
    reverseVoucher?.let { voucher -> ReverseVoucherDialog(container, user, voucher, onDismiss={reverseVoucher=null}) { message=it; reverseVoucher=null } }
    reversePayment?.let { payment -> ReverseSupplierPaymentDialog(container, user, payment, onDismiss={reversePayment=null}) { message=it; reversePayment=null } }
}

@Composable
private fun PartyInfoCustomer(c: CustomerEntity) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FushSectionHeader("المعلومات الأساسية", "بيانات التعريف والسياسة التجارية للعميل")
        ElevatedCard(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CustomerInfoRow("الكود", c.code)
                CustomerInfoRow("الاسم", c.nameAr)
                if (c.nameEn.isNotBlank()) CustomerInfoRow("English", c.nameEn)
                CustomerInfoRow("الهاتف", c.phone.ifBlank { "—" })
                CustomerInfoRow("العنوان", c.address.ifBlank { "—" })
                CustomerInfoRow("المحافظة", c.province)
                CustomerInfoRow("قناة البيع", c.channel)
                CustomerInfoRow("التصنيف", c.classification)
                CustomerInfoRow("العملة", c.currencyCode)
                CustomerInfoRow("المندوب", c.salesRepName.ifBlank { "—" })
                CustomerInfoRow("تاريخ الإنشاء", partyDate(c.createdAt), divider = false)
            }
        }
        Surface(
            color = if (c.allowCredit) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (c.allowCredit) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("السياسة الائتمانية", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (c.allowCredit) "البيع الآجل مسموح حتى ${c.creditDays} يوم وبسقف ${partyMoney(c.creditLimitBase)} بالعملة الأساسية."
                    else "البيع الآجل غير مفعّل لهذا العميل.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun CustomerInfoRow(label: String, value: String, divider: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
@Composable
private fun PartyInfoSupplier(s: SupplierEntity) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FushSectionHeader("المعلومات الأساسية", "بيانات المورد وشروط الدفع")
        ElevatedCard(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SupplierInfoRow("الكود", s.code)
                SupplierInfoRow("الاسم", s.nameAr)
                if (s.nameEn.isNotBlank()) SupplierInfoRow("English", s.nameEn)
                SupplierInfoRow("الهاتف", s.phone.ifBlank { "—" })
                SupplierInfoRow("العنوان", s.address.ifBlank { "—" })
                SupplierInfoRow("العملة", s.currencyCode)
                SupplierInfoRow("تاريخ الإنشاء", partyDate(s.createdAt), divider = false)
            }
        }
        Surface(
            color = if (s.paymentTermsDays > 0) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (s.paymentTermsDays > 0) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("شروط الدفع", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (s.paymentTermsDays > 0) "مدة السداد المتفق عليها ${s.paymentTermsDays} يوم." else "المورد مضبوط على السداد النقدي دون فترة أجل.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SupplierInfoRow(label: String, value: String, divider: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

private fun LazyListScope.customerLedgerItems(rows: List<Pair<CustomerLedgerEventRow,Double>>) {
    item {
        val balance = rows.lastOrNull()?.second ?: 0.0
        FushSectionHeader("كشف الحساب", "سجل زمني موحّد لجميع حركات العميل")
        Spacer(Modifier.height(8.dp))
        FushMetricCard(
            label = "الرصيد الحالي",
            value = partyMoney(balance),
            helper = partyBalanceTextCustomer(balance),
            modifier = Modifier.fillMaxWidth(),
            tone = if (balance > 0.000001) FushStatusTone.Warning else FushStatusTone.Success,
        )
    }
    if (rows.isEmpty()) item { FushInlineState("لا توجد حركات على حساب العميل حتى الآن.") }
    items(rows) { (event, balance) ->
        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(partyEventLabel(event.eventType), style = MaterialTheme.typography.titleSmall)
                        Text("${partyDate(event.eventDate)} • ${event.referenceNo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FushStatusPill("رصيد ${partyMoney(balance)}", if (balance > 0.000001) FushStatusTone.Warning else FushStatusTone.Success)
                }
                if (event.invoiceNo.isNotBlank()) Text("المرجع: ${event.invoiceNo}", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(Modifier.weight(1f), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f), shape = MaterialTheme.shapes.small) {
                        Column(Modifier.padding(9.dp)) { Text("مدين", style = MaterialTheme.typography.labelSmall); Text(partyMoney(event.debitBase), style = MaterialTheme.typography.titleSmall) }
                    }
                    Surface(Modifier.weight(1f), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f), shape = MaterialTheme.shapes.small) {
                        Column(Modifier.padding(9.dp)) { Text("دائن", style = MaterialTheme.typography.labelSmall); Text(partyMoney(event.creditBase), style = MaterialTheme.typography.titleSmall) }
                    }
                }
                if (event.notes.isNotBlank()) Text(event.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun LazyListScope.supplierLedgerItems(rows: List<Pair<SupplierLedgerEventRow,Double>>) {
    item {
        val balance = rows.lastOrNull()?.second ?: 0.0
        FushSectionHeader("كشف الحساب", "سجل زمني موحّد لجميع حركات المورد")
        Spacer(Modifier.height(8.dp))
        FushMetricCard("الرصيد المستحق", partyMoney(balance), Modifier.fillMaxWidth(), helper = "المبلغ المستحق للمورد", tone = if (balance > 0.000001) FushStatusTone.Warning else FushStatusTone.Success)
    }
    if (rows.isEmpty()) item { FushInlineState("لا توجد حركات على حساب المورد حتى الآن.") }
    items(rows) { (event, balance) ->
        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(partyEventLabel(event.eventType), style = MaterialTheme.typography.titleSmall)
                        Text("${partyDate(event.eventDate)} • ${event.referenceNo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FushStatusPill("رصيد ${partyMoney(balance)}", if (balance > 0.000001) FushStatusTone.Warning else FushStatusTone.Success)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(Modifier.weight(1f), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f), shape = MaterialTheme.shapes.small) {
                        Column(Modifier.padding(9.dp)) { Text("مدين", style = MaterialTheme.typography.labelSmall); Text(partyMoney(event.debitBase), style = MaterialTheme.typography.titleSmall) }
                    }
                    Surface(Modifier.weight(1f), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f), shape = MaterialTheme.shapes.small) {
                        Column(Modifier.padding(9.dp)) { Text("دائن", style = MaterialTheme.typography.labelSmall); Text(partyMoney(event.creditBase), style = MaterialTheme.typography.titleSmall) }
                    }
                }
                if (event.notes.isNotBlank()) Text(event.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun LazyListScope.customerReceiptItems(rows: List<CustomerReceiptEntity>, onReverse: (CustomerReceiptEntity)->Unit) {
    val reversedIds = rows.mapNotNull { it.reversalOfReceiptId }.toSet()
    item { FushSectionHeader("تحصيلات العميل", "التصحيح يتم بالعكس المحاسبي؛ لا يحذف التحصيل الأصلي.") }
    if (rows.isEmpty()) item { FushInlineState("لا توجد تحصيلات.") }
    items(rows, key = { it.id }) { r ->
        val isReversal = r.reversalOfReceiptId != null
        val isReversedOriginal = r.id in reversedIds
        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("${partyDate(r.receiptDate)} • ${r.receiptNo}", style = MaterialTheme.typography.titleSmall)
                Text("${partyMoney(kotlin.math.abs(r.amountOriginal))} ${r.currencyCode} • أساس ${partyMoney(kotlin.math.abs(r.amountBase))}")
                FushStatusPill(when { isReversal -> "مستند عكس"; isReversedOriginal -> "معكوس"; else -> "مرحل" }, if (isReversal || isReversedOriginal) FushStatusTone.Warning else FushStatusTone.Success)
                if (r.notes.isNotBlank()) Text(r.notes, style = MaterialTheme.typography.bodySmall)
                if (!isReversal && !isReversedOriginal) OutlinedButton(onClick = { onReverse(r) }) { Text("عكس التحصيل") }
            }
        }
    }
}

private fun LazyListScope.supplierPaymentItems(rows: List<SupplierPaymentDetailRow>, onReverse: (SupplierPaymentDetailRow)->Unit) {
    val reversedIds = rows.mapNotNull { it.reversalOfPaymentId }.toSet()
    item { FushSectionHeader("دفعات المورد", "التصحيح يتم بالعكس المحاسبي مع إعادة فتح رصيد الفاتورة.") }
    if (rows.isEmpty()) item { FushInlineState("لا توجد دفعات مورد.") }
    items(rows, key = { it.paymentId }) { r ->
        val isReversal = r.reversalOfPaymentId != null
        val isReversedOriginal = r.paymentId in reversedIds
        ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("${partyDate(r.paymentDate)} • ${r.paymentNo}", style = MaterialTheme.typography.titleSmall)
                Text("فاتورة ${r.invoiceNo} • ${partyMoney(kotlin.math.abs(r.cashAmountBase))} • ${r.treasuryName}")
                FushStatusPill(when { isReversal -> "مستند عكس"; isReversedOriginal -> "معكوس"; else -> "مرحل" }, if (isReversal || isReversedOriginal) FushStatusTone.Warning else FushStatusTone.Success)
                if (!isReversal && !isReversedOriginal) OutlinedButton(onClick = { onReverse(r) }) { Text("عكس الدفعة") }
            }
        }
    }
}

@Composable private fun ReverseCustomerReceiptDialog(container:AppContainer,user:UserEntity,receipt:CustomerReceiptEntity,onDismiss:()->Unit,onDone:(String)->Unit){
    val scope=rememberCoroutineScope(); var reason by remember{mutableStateOf("")}; var date by remember{mutableStateOf(partyDate(System.currentTimeMillis()))}; var error by remember{mutableStateOf<String?>(null)}; var saving by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest={if(!saving)onDismiss()},title={Text("عكس التحصيل ${receipt.receiptNo}")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("سيُنشأ مستند عكس وقيد عكسي، وتُعاد ذمة الفاتورة ويُصحح استحقاق العمولة تلقائياً.");OutlinedTextField(date,{date=it},label={Text("تاريخ العكس YYYY-MM-DD")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(reason,{reason=it},label={Text("سبب العكس — إلزامي")},modifier=Modifier.fillMaxWidth());error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={Button(enabled=!saving&&reason.isNotBlank(),onClick={scope.launch{saving=true;try{val result=container.salesService.reverseReceipt(receipt.id,reason,user.id,partyParseDate(date));onDone("تم عكس ${receipt.receiptNo} بالمستند ${result.reversalReceiptNo} وإعادة ${partyMoney(result.restoredReceivableBase)} إلى ذمة العميل") }catch(e:Exception){error=e.message?:"تعذر عكس التحصيل"}finally{saving=false}}}){Text(if(saving)"جارٍ..." else "تأكيد العكس")}},dismissButton={TextButton(onClick=onDismiss,enabled=!saving){Text("إلغاء")}})
}

@Composable private fun ReverseSupplierPaymentDialog(container:AppContainer,user:UserEntity,payment:SupplierPaymentDetailRow,onDismiss:()->Unit,onDone:(String)->Unit){
    val scope=rememberCoroutineScope(); var reason by remember{mutableStateOf("")}; var date by remember{mutableStateOf(partyDate(System.currentTimeMillis()))}; var error by remember{mutableStateOf<String?>(null)}; var saving by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest={if(!saving)onDismiss()},title={Text("عكس الدفعة ${payment.paymentNo}")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Text("سيُعاد رصيد الفاتورة إلى المورد ويُعكس قيد الخزينة وفروق العملة كما سُجلت أصلاً.");OutlinedTextField(date,{date=it},label={Text("تاريخ العكس YYYY-MM-DD")},singleLine=true,modifier=Modifier.fillMaxWidth());OutlinedTextField(reason,{reason=it},label={Text("سبب العكس — إلزامي")},modifier=Modifier.fillMaxWidth());error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={Button(enabled=!saving&&reason.isNotBlank(),onClick={scope.launch{saving=true;try{val result=container.purchaseService.reverseSupplierPayment(payment.paymentId,reason,user.id,partyParseDate(date));onDone("تم عكس ${payment.paymentNo} بالمستند ${result.reversalPaymentNo} وإعادة ${partyMoney(result.restoredPayableBase)} إلى ذمة المورد") }catch(e:Exception){error=e.message?:"تعذر عكس دفعة المورد"}finally{saving=false}}}){Text(if(saving)"جارٍ..." else "تأكيد العكس")}},dismissButton={TextButton(onClick=onDismiss,enabled=!saving){Text("إلغاء")}})
}


private fun LazyListScope.voucherItems(vouchers: List<PartyVoucherEntity>, onReverse: (PartyVoucherEntity)->Unit) {
    item { FushSectionHeader("السندات المرتبطة بالطرف", "السند المرحل لا يعدل أو يحذف؛ التصحيح بالعكس فقط.") }
    if (vouchers.isEmpty()) item { FushInlineState("لا توجد سندات مرتبطة بهذا الطرف.") }
    items(vouchers, key={"party-voucher-${it.id}"}) { v ->
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text("${partyDate(v.voucherDate)} • ${if(v.voucherType=="RECEIPT") "سند قبض" else "سند صرف"} • ${v.voucherNo}")
                Text("${v.partyNameSnapshot} • ${partyMoney(v.amountOriginal)} ${v.currencyCode}")
                Text(v.description)
                Text("الحالة: ${if(v.status=="POSTED") "مرحل" else "معكوس"}")
                if(v.status=="REVERSED") Text("سبب العكس: ${v.reversalReason}", color=MaterialTheme.colorScheme.error)
                if(v.status=="POSTED") OutlinedButton(onClick={onReverse(v)}) { Text("عكس السند") }
            }
        }
    }
}

private fun LazyListScope.auditItems(rows: List<AuditEventEntity>) {
    item { FushSectionHeader("سجل التدقيق والحركات", "الأحداث والتعديلات المرتبطة بهذا الطرف") }
    if(rows.isEmpty()) item { FushInlineState("لا توجد أحداث تدقيق.") }
    items(rows,key={"party-audit-${it.id}"}) { e ->
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Text("${partyDateTime(e.eventAt)} • ${e.action} • مستخدم #${e.userId}")
                Text("${e.entityType} #${e.entityId}", style=MaterialTheme.typography.bodySmall)
                if(e.reason.isNotBlank()) Text(e.reason)
                if(e.newValue.isNotBlank()) Text(e.newValue, style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun <T> LazyListScope.profileSimpleEntityItems(empty: String?, rows: List<T>, text: (T)->String) {
    if(empty!=null) item { FushInlineState(empty) }
    items(rows) { r -> ElevatedCard(Modifier.fillMaxWidth()) { Text(text(r), Modifier.padding(10.dp)) } }
}

@Composable
private fun AttachmentTab(container: AppContainer, user: UserEntity, customerId: Long?, supplierId: Long?, attachments: List<PartyAttachmentEntity>, onMessage:(String)->Unit) {
    val context=LocalContext.current; val scope=rememberCoroutineScope()
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if(uri!=null) scope.launch {
            try {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val name = runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null } }.getOrNull() ?: "مرفق"
                val mime=context.contentResolver.getType(uri) ?: ""
                container.db.withTransaction {
                    container.db.partyDao().insertAttachment(PartyAttachmentEntity(customerId=customerId,supplierId=supplierId,fileName=name,mimeType=mime,uri=uri.toString(),createdBy=user.id))
                    container.db.governanceDao().insertAudit(AuditEventEntity(userId=user.id,action="ATTACH",entityType=if(customerId!=null)"CUSTOMER" else "SUPPLIER",entityId=(customerId?:supplierId!!).toString(),newValue=name,reason="إضافة مرفق"))
                }
                onMessage("تمت إضافة المرفق $name")
            } catch(e:Exception){onMessage(e.message?:"تعذر إضافة المرفق")}
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("المرفقات", style=MaterialTheme.typography.titleLarge); Button(onClick={launcher.launch(arrayOf("*/*"))}){Text("إضافة مرفق")}
        if(attachments.isEmpty()) FushInlineState("لا توجد مرفقات.")
        attachments.forEach { a -> ElevatedCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(10.dp), horizontalArrangement=Arrangement.SpaceBetween) { Column(Modifier.weight(1f)){Text(a.fileName); Text("${a.mimeType.ifBlank{"ملف"}} • ${partyDateTime(a.createdAt)}",style=MaterialTheme.typography.bodySmall)}; TextButton(onClick={ runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }.onFailure{onMessage("تعذر فتح المرفق")}}){Text("فتح")} } } }
    }
}

@Composable
private fun CustomerPartySettlementDialog(
    container: AppContainer,
    user: UserEntity,
    customer: CustomerEntity,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val invoices by produceState(initialValue = emptyList<SalesInvoiceSummary>(), customer.id) {
        value = container.db.salesDao().openInvoiceSummaries(customer.id)
    }
    val treasuries by container.db.accountingDao().observeTreasuryBalances().collectAsState(initial = emptyList())
    var invoice by remember { mutableStateOf<SalesInvoiceSummary?>(null) }
    var treasury by remember { mutableStateOf<TreasuryBalanceRow?>(null) }
    var rate by remember { mutableStateOf("1") }
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var autoAllocate by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(invoices) {
        if (invoice == null || invoices.none { it.id == invoice?.id }) invoice = invoices.firstOrNull()
        if (invoices.size <= 1) autoAllocate = false
    }
    LaunchedEffect(invoice?.id, treasuries, autoAllocate, invoices) {
        invoice?.let { inv ->
            val options = treasuries.filter { it.currencyCode == inv.currencyCode }
            if (treasury?.id !in options.map { it.id }) treasury = options.firstOrNull()
            val historicalRate = if (inv.totalOriginal > 0.0) inv.totalBase / inv.totalOriginal else 1.0
            if (rate.toDoubleOrNull() == null || rate == "1") rate = historicalRate.toString()
            val relevant = invoices.filter { it.currencyCode == inv.currencyCode }
            val totalOriginalOpen = relevant.sumOf { row ->
                val rowRate = if (row.totalOriginal > 0.0) row.totalBase / row.totalOriginal else 1.0
                if (rowRate > 0.0) row.outstandingBase / rowRate else 0.0
            }
            amount = if (autoAllocate) totalOriginalOpen.toString()
            else if (historicalRate > 0.0) (inv.outstandingBase / historicalRate).toString() else ""
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("تحصيل العميل — ${customer.nameAr}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (invoices.isEmpty()) Text("لا توجد فواتير آجلة مفتوحة لهذا العميل.") else {
                PartySelectionField("عملة / فاتورة مرجعية", invoice?.let { "${it.invoiceNo} • ${it.currencyCode}" } ?: "اختر", invoices, { "${it.invoiceNo} • ${it.currencyCode} • متبقي ${partyMoney(it.outstandingBase)}" }) { invoice = it; error = null }
                val sameCurrency = invoices.filter { it.currencyCode == invoice?.currencyCode }
                if (sameCurrency.size > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoAllocate, onCheckedChange = { autoAllocate = it })
                        Text("توزيع تلقائي على أقدم الفواتير المفتوحة بنفس العملة")
                    }
                    if (autoAllocate) Text("سيتم توزيع المبلغ على ${sameCurrency.size} فاتورة حسب الأقدم أولاً، دون تجاوز رصيد أي فاتورة.", style = MaterialTheme.typography.bodySmall)
                }
                val options = treasuries.filter { it.currencyCode == invoice?.currencyCode }
                PartySelectionField("الخزينة / البنك", treasury?.nameAr ?: "اختر", options, { "${it.nameAr} • ${it.currencyCode}" }) { treasury = it }
                if (options.isEmpty()) Text("لا توجد خزينة نشطة بعملة الفاتورة.", color = MaterialTheme.colorScheme.error)
                OutlinedTextField(rate, { rate = it }, label = { Text("سعر الصرف الحالي للتحصيل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ بعملة الفاتورة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
                Text("كل فاتورة تُقفل بسعرها التاريخي، بينما يخرج/يدخل النقد بسعر يوم التحصيل؛ فرق العملة يُسجل تلقائيًا.", style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(
            enabled = !saving && invoice != null && treasury != null && amount.toDoubleOrNull()?.let { it > 0 } == true && rate.toDoubleOrNull()?.let { it > 0 } == true,
            onClick = { scope.launch {
                saving = true
                try {
                    val inv = requireNotNull(invoice)
                    if (autoAllocate) {
                        val result = container.salesService.postReceiptAutoAllocate(
                            customerId = customer.id,
                            amountOriginal = amount.toDouble(),
                            currencyCode = inv.currencyCode,
                            exchangeRate = rate.toDouble(),
                            notes = notes,
                            createdBy = user.id,
                            treasuryAccountId = requireNotNull(treasury).id
                        )
                        onDone("تم تحصيل ${result.receiptNo} وتوزيعه على ${result.allocationCount} فاتورة")
                    } else {
                        val result = container.salesService.postReceipt(
                            customerId = customer.id,
                            invoiceId = inv.id,
                            amountOriginal = amount.toDouble(),
                            currencyCode = inv.currencyCode,
                            exchangeRate = rate.toDouble(),
                            notes = notes,
                            createdBy = user.id,
                            treasuryAccountId = requireNotNull(treasury).id
                        )
                        onDone("تم تحصيل ${result.receiptNo} وربطه بالفاتورة ${inv.invoiceNo}")
                    }
                } catch (e: Exception) { error = e.message ?: "تعذر تسجيل التحصيل" }
                finally { saving = false }
            } }
        ) { Text(if (saving) "جارٍ..." else "ترحيل التحصيل") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") } }
    )
}

@Composable
private fun SupplierPartySettlementDialog(
    container: AppContainer,
    user: UserEntity,
    supplier: SupplierEntity,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val invoices by produceState(initialValue = emptyList<SupplierInvoicePayableRow>(), supplier.id) {
        value = container.db.purchaseDao().openSupplierInvoices(supplier.id)
    }
    val treasuries by container.db.accountingDao().observeTreasuryBalances().collectAsState(initial = emptyList())
    var invoice by remember { mutableStateOf<SupplierInvoicePayableRow?>(null) }
    var treasury by remember { mutableStateOf<TreasuryBalanceRow?>(null) }
    var rate by remember { mutableStateOf("1") }
    var amount by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var autoAllocate by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(invoices) {
        if (invoice == null || invoices.none { it.invoiceId == invoice?.invoiceId }) invoice = invoices.firstOrNull()
        if (invoices.size <= 1) autoAllocate = false
    }
    LaunchedEffect(invoice?.invoiceId, treasuries, autoAllocate, invoices) {
        invoice?.let { inv ->
            val options = treasuries.filter { it.currencyCode == inv.currencyCode }
            if (treasury?.id !in options.map { it.id }) treasury = options.firstOrNull()
            if (rate.toDoubleOrNull() == null || rate == "1") rate = inv.invoiceExchangeRate.toString()
            val relevant = invoices.filter { it.currencyCode == inv.currencyCode }
            val totalOriginalOpen = relevant.sumOf { row -> if (row.invoiceExchangeRate > 0.0) row.outstandingBase / row.invoiceExchangeRate else 0.0 }
            amount = if (autoAllocate) totalOriginalOpen.toString()
            else if (inv.invoiceExchangeRate > 0.0) (inv.outstandingBase / inv.invoiceExchangeRate).toString() else ""
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("دفع المورد — ${supplier.nameAr}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (invoices.isEmpty()) Text("لا توجد فواتير آجلة مفتوحة لهذا المورد.") else {
                PartySelectionField("عملة / فاتورة مرجعية", invoice?.let { "${it.invoiceNo} • ${it.currencyCode}" } ?: "اختر", invoices, { "${it.invoiceNo} • ${it.currencyCode} • متبقي ${partyMoney(it.outstandingBase)}" }) { invoice = it; error = null }
                val sameCurrency = invoices.filter { it.currencyCode == invoice?.currencyCode }
                if (sameCurrency.size > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoAllocate, onCheckedChange = { autoAllocate = it })
                        Text("توزيع تلقائي على أقدم الفواتير المستحقة بنفس العملة")
                    }
                    if (autoAllocate) Text("سيتم توزيع الدفعة على ${sameCurrency.size} فاتورة حسب تاريخ الاستحقاق/الأقدم أولاً.", style = MaterialTheme.typography.bodySmall)
                }
                val options = treasuries.filter { it.currencyCode == invoice?.currencyCode }
                PartySelectionField("الخزينة / البنك", treasury?.nameAr ?: "اختر", options, { "${it.nameAr} • ${it.currencyCode}" }) { treasury = it }
                if (options.isEmpty()) Text("لا توجد خزينة نشطة بعملة الفاتورة.", color = MaterialTheme.colorScheme.error)
                OutlinedTextField(rate, { rate = it }, label = { Text("سعر الصرف الحالي للدفع") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ بعملة الفاتورة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
                Text("كل فاتورة تُقفل بسعرها التاريخي، ويُسجل فرق سعر الصرف المجمع تلقائيًا في قيد الدفعة.", style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(
            enabled = !saving && invoice != null && treasury != null && amount.toDoubleOrNull()?.let { it > 0 } == true && rate.toDoubleOrNull()?.let { it > 0 } == true,
            onClick = { scope.launch {
                saving = true
                try {
                    val inv = requireNotNull(invoice)
                    if (autoAllocate) {
                        val result = container.purchaseService.postSupplierPaymentAutoAllocate(
                            supplierId = supplier.id,
                            treasuryAccountId = requireNotNull(treasury).id,
                            amountOriginal = amount.toDouble(),
                            currencyCode = inv.currencyCode,
                            paymentExchangeRate = rate.toDouble(),
                            notes = notes,
                            createdBy = user.id
                        )
                        onDone("تم دفع ${result.paymentNo} وتوزيعه على ${result.allocationCount} فاتورة")
                    } else {
                        val result = container.purchaseService.postSupplierPayment(
                            supplierId = supplier.id,
                            invoiceId = inv.invoiceId,
                            treasuryAccountId = requireNotNull(treasury).id,
                            amountOriginal = amount.toDouble(),
                            paymentExchangeRate = rate.toDouble(),
                            notes = notes,
                            createdBy = user.id
                        )
                        onDone("تم دفع ${result.paymentNo} وربطه بالفاتورة ${inv.invoiceNo}")
                    }
                } catch (e: Exception) { error = e.message ?: "تعذر تسجيل دفعة المورد" }
                finally { saving = false }
            } }
        ) { Text(if (saving) "جارٍ..." else "ترحيل الدفعة") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("إلغاء") } }
    )
}

@Composable private fun ReverseVoucherDialog(container:AppContainer,user:UserEntity,voucher:PartyVoucherEntity,onDismiss:()->Unit,onDone:(String)->Unit){ val scope=rememberCoroutineScope(); var reason by remember{mutableStateOf("")}; var error by remember{mutableStateOf<String?>(null)}; AlertDialog(onDismissRequest=onDismiss,title={Text("عكس ${voucher.voucherNo}")},text={Column{Text("سيتم إنشاء قيد عكسي ولن يحذف المستند الأصلي."); OutlinedTextField(reason,{reason=it},label={Text("سبب العكس — إلزامي")},modifier=Modifier.fillMaxWidth()); error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={Button(enabled=reason.isNotBlank(),onClick={scope.launch{try{val id=container.accountingService.reverseEntry(voucher.journalEntryId,reason,user.id);onDone("تم عكس السند وإنشاء القيد $id")}catch(e:Exception){error=e.message?:"تعذر العكس"}}}){Text("تأكيد العكس")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}}) }


@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun <T> PartySelectionField(label:String,current:String,options:List<T>,optionLabel:(T)->String,onSelected:(T)->Unit){ var expanded by remember{mutableStateOf(false)}; ExposedDropdownMenuBox(expanded,onExpandedChange={expanded=!expanded}){OutlinedTextField(current,{},readOnly=true,label={Text(label)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},modifier=Modifier.menuAnchor().fillMaxWidth());ExposedDropdownMenu(expanded,onDismissRequest={expanded=false}){options.forEach{o->DropdownMenuItem(text={Text(optionLabel(o))},onClick={onSelected(o);expanded=false})}}} }

@Composable private fun AddCustomerPartyDialog(currencies:List<CurrencyEntity>,salesReps:List<SalesRepresentativeEntity>,onDismiss:()->Unit,onSave:(String,String,String,String,CurrencyEntity,Double,Int,Boolean,SalesRepresentativeEntity?)->Unit){ var name by remember{mutableStateOf("")};var phone by remember{mutableStateOf("")};var province by remember{mutableStateOf("تعز")};var channel by remember{mutableStateOf("RETAIL")};var currency by remember{mutableStateOf<CurrencyEntity?>(null)};var limit by remember{mutableStateOf("408000")};var days by remember{mutableStateOf("30")};var credit by remember{mutableStateOf(false)};var rep by remember{mutableStateOf<SalesRepresentativeEntity?>(null)};LaunchedEffect(currencies){if(currency==null)currency=currencies.firstOrNull{it.code=="YER_NEW"}?:currencies.firstOrNull()};AlertDialog(onDismissRequest=onDismiss,title={Text("عميل جديد")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(name,{name=it},label={Text("الاسم")});OutlinedTextField(phone,{phone=it},label={Text("الهاتف")});OutlinedTextField(province,{province=it},label={Text("المحافظة")});PartySelectionField("قناة البيع",channel,listOf("DIRECT","RETAIL","DISTRIBUTOR_CASH","DISTRIBUTOR_CREDIT"),{it}){channel=it};PartySelectionField("العملة",currency?.nameAr?:"اختر",currencies,{it.nameAr}){currency=it};Row{Checkbox(credit,{credit=it});Text("السماح بالآجل")};if(credit){OutlinedTextField(limit,{limit=it},label={Text("السقف الائتماني")});OutlinedTextField(days,{days=it},label={Text("أيام الائتمان")})};if(salesReps.isNotEmpty())PartySelectionField("مندوب المبيعات",rep?.let{"${it.code} — ${it.fullNameAr}"}?:"بدون مندوب",salesReps,{"${it.code} — ${it.fullNameAr}"}){rep=it}else FushInlineState("لا يوجد مناديب مبيعات نشطون. يمكنك إضافتهم من قسم مناديب المبيعات.")}},confirmButton={Button(enabled=name.isNotBlank()&&currency!=null&&limit.toDoubleOrNull()!=null&&days.toIntOrNull()!=null,onClick={onSave(name,phone,province,channel,currency!!,limit.toDouble(),days.toInt(),credit,rep)}){Text("حفظ")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}}) }
@Composable private fun AddSupplierPartyDialog(currencies:List<CurrencyEntity>,onDismiss:()->Unit,onSave:(String,String,CurrencyEntity,Int,String)->Unit){var name by remember{mutableStateOf("")};var phone by remember{mutableStateOf("")};var currency by remember{mutableStateOf<CurrencyEntity?>(null)};var days by remember{mutableStateOf("0")};var address by remember{mutableStateOf("")};LaunchedEffect(currencies){if(currency==null)currency=currencies.firstOrNull{it.code=="YER_NEW"}?:currencies.firstOrNull()};AlertDialog(onDismissRequest=onDismiss,title={Text("مورد جديد")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(name,{name=it},label={Text("الاسم")});OutlinedTextField(phone,{phone=it},label={Text("الهاتف")});OutlinedTextField(address,{address=it},label={Text("العنوان")});PartySelectionField("العملة",currency?.nameAr?:"اختر",currencies,{it.nameAr}){currency=it};OutlinedTextField(days,{days=it},label={Text("أيام السداد")})}},confirmButton={Button(enabled=name.isNotBlank()&&currency!=null&&days.toIntOrNull()!=null,onClick={onSave(name,phone,currency!!,days.toInt(),address)}){Text("حفظ")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}}) }

private fun partyEventLabel(t:String)=when(t){"INVOICE"->"فاتورة";"RECEIPT"->"تحصيل";"RECEIPT_REVERSAL"->"عكس تحصيل";"PAYMENT"->"دفعة";"PAYMENT_REVERSAL"->"عكس دفعة";"RETURN","SALES_RETURN"->"مرتجع";"CASH_REFUND"->"رد نقدي";"VOUCHER_RECEIPT"->"سند قبض";"VOUCHER_PAYMENT"->"سند صرف";"VOUCHER_REVERSAL"->"عكس سند";else->t}
private fun partyMoney(v:Double)=if(kotlin.math.abs(v-v.toLong())<0.000001)v.toLong().toString() else "%.2f".format(Locale.US,v)
private fun partyBalanceTextCustomer(v:Double)=if(v>=0)"الرصيد المستحق على العميل: ${partyMoney(v)}" else "رصيد لصالح العميل: ${partyMoney(-v)}"
private fun partyDate(v:Long)=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date(v))
private fun partyDateTime(v:Long)=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(Date(v))
private fun partyParseDate(s:String):Long=SimpleDateFormat("yyyy-MM-dd",Locale.US).apply{isLenient=false}.parse(s)?.time?:throw IllegalArgumentException("التاريخ غير صالح")
