package com.fush.erp.ui.screens

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.*
import com.fush.erp.domain.AccountingService
import com.fush.erp.ui.export.ReportExportActions
import com.fush.erp.ui.FushMetricCard
import com.fush.erp.ui.FushSectionHeader
import com.fush.erp.ui.FushStatusPill
import com.fush.erp.ui.FushStatusTone
import com.fush.erp.ui.FushInlineState
import com.fush.erp.ui.FushEmptyState
import com.fush.erp.ui.FushDateField
import com.fush.erp.ui.FushDecimalField
import com.fush.erp.ui.FushOperationMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val EXPENSE_COST_CENTERS = listOf(
    "SALES" to "المبيعات",
    "PURCHASES" to "المشتريات",
    "PRODUCTION" to "الإنتاج",
    "ADMIN" to "الإدارة",
    "WAREHOUSE" to "المخزن",
    "DISTRIBUTION" to "التوزيع",
    "MAINTENANCE" to "الصيانة",
    "MARKETING" to "التسويق",
    "OTHER" to "أخرى"
)

private val EXPENSE_REFERENCE_TYPES = listOf(
    "NONE" to "بدون مرجع",
    "SALES_INVOICE" to "فاتورة مبيعات",
    "SALES_ORDER" to "أمر بيع",
    "PURCHASE_INVOICE" to "فاتورة مشتريات",
    "PURCHASE_ORDER" to "أمر شراء",
    "PRODUCTION_ORDER" to "أمر إنتاج",
    "DISTRIBUTION" to "توزيع / توصيل",
    "CUSTOMER" to "عميل",
    "SUPPLIER" to "مورد",
    "PRODUCT" to "منتج / صنف",
    "BRANCH" to "فرع",
    "FACILITY" to "منشأة",
    "OTHER" to "مرجع آخر"
)

@Composable
fun ExpenseManagementTab(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val rows by container.db.expenseDao().observeReportRows().collectAsState(initial = emptyList())
    val repContribution by container.db.expenseDao().observeSalesRepContribution().collectAsState(initial = emptyList())
    val accounts by container.db.accountDao().observeAll().collectAsState(initial = emptyList())
    val employees by container.db.employeeDao().observeActiveEmployees().collectAsState(initial = emptyList())
    val salesReps by container.db.salesRepresentativeDao().observeActive().collectAsState(initial = emptyList())
    val customers by container.db.customerDao().observeAll().collectAsState(initial = emptyList())
    val suppliers by container.db.supplierDao().observeAll().collectAsState(initial = emptyList())
    val itemsMaster by container.db.itemDao().observeAll().collectAsState(initial = emptyList())
    val treasury by container.db.accountingDao().observeTreasuryBalances().collectAsState(initial = emptyList())
    val currencies by container.db.currencyDao().observeAll().collectAsState(initial = emptyList())
    val salesInvoices by container.db.salesDao().observeSummaries().collectAsState(initial = emptyList())
    val purchaseInvoices by container.db.purchaseDao().observeSummaries().collectAsState(initial = emptyList())
    val productionOrders by container.db.productionDao().observeOrderSummaries().collectAsState(initial = emptyList())

    var showAdd by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var accountFilter by remember { mutableStateOf<AccountEntity?>(null) }
    var employeeFilter by remember { mutableStateOf<EmployeeEntity?>(null) }
    var repFilter by remember { mutableStateOf<SalesRepresentativeEntity?>(null) }
    var customerFilter by remember { mutableStateOf<CustomerEntity?>(null) }
    var supplierFilter by remember { mutableStateOf<SupplierEntity?>(null) }
    var centerFilter by remember { mutableStateOf<Pair<String, String>?>(null) }
    var referenceFilter by remember { mutableStateOf<Pair<String, String>?>(null) }
    var organizationFilter by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }

    val expenseAccounts = accounts.filter { it.isPosting && it.type == "EXPENSE" }
    val filtered = rows.filter { row ->
        (accountFilter == null || row.expenseAccountId == accountFilter!!.id) &&
            (employeeFilter == null || row.employeeId == employeeFilter!!.id) &&
            (repFilter == null || row.salesRepId == repFilter!!.id) &&
            (customerFilter == null || row.customerId == customerFilter!!.id) &&
            (supplierFilter == null || row.supplierId == supplierFilter!!.id) &&
            (centerFilter == null || row.costCenterCode == centerFilter!!.first) &&
            (referenceFilter == null || row.referenceType == referenceFilter!!.first) &&
            (organizationFilter.isBlank() || row.organizationUnit.contains(organizationFilter.trim(), true)) &&
            expenseDateMatch(row.voucherDate, from, to) &&
            (search.isBlank() || listOf(
                row.voucherNo, row.description, row.referenceNo, row.referenceLabel, row.employeeName,
                row.salesRepName, row.customerName, row.supplierName, row.itemName, row.organizationUnit
            ).any { it.contains(search.trim(), true) })
    }
    val total = filtered.sumOf { it.amountBase }
    val averageExpense = if (filtered.isEmpty()) 0.0 else total / filtered.size
    val maintenanceSpend = filtered.filter { it.costCenterCode == "MAINTENANCE" }.sumOf { it.amountBase }
    val byAccount = filtered.groupBy { it.expenseAccountName }.mapValues { it.value.sumOf { r -> r.amountBase } }.toList().sortedByDescending { it.second }
    val byCenter = filtered.groupBy { it.costCenterName }.mapValues { it.value.sumOf { r -> r.amountBase } }.toList().sortedByDescending { it.second }
    val topAccount = byAccount.firstOrNull()
    val topCenter = byCenter.firstOrNull()
    val exportFilterSummary = listOfNotNull(
        accountFilter?.let { "حساب المصروف" to "${it.code} — ${it.nameAr}" },
        centerFilter?.let { "مركز التكلفة" to it.second },
        employeeFilter?.let { "الموظف" to it.fullNameAr },
        repFilter?.let { "مندوب المبيعات" to it.fullNameAr },
        customerFilter?.let { "العميل" to it.nameAr },
        supplierFilter?.let { "المورد" to it.nameAr },
        referenceFilter?.let { "نوع المرجع" to it.second },
        organizationFilter.trim().takeIf { it.isNotBlank() }?.let { "الفرع / المنشأة" to it },
        search.trim().takeIf { it.isNotBlank() }?.let { "البحث" to it },
        from.trim().takeIf { it.isNotBlank() }?.let { "من تاريخ" to it },
        to.trim().takeIf { it.isNotBlank() }?.let { "إلى تاريخ" to it }
    )

    LazyColumn(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            FushSectionHeader(
                title = "المصروفات التشغيلية ومراكز التكلفة",
                subtitle = "تابع المصروف حسب الحساب والقسم والموظف والمندوب والطرف والمرجع، مع بقاء القيد المحاسبي هو مصدر الحقيقة."
            )
            FushOperationMessage(message, onConsumed = { message = null })
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard("إجمالي المصروف", expenseMoney(total), Modifier.weight(1f), helper = "حسب الفلاتر الحالية", tone = FushStatusTone.Info)
                FushMetricCard("عدد الحركات", filtered.size.toString(), Modifier.weight(1f), helper = "سند مصروف مرحّل")
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard("متوسط الحركة", expenseMoney(averageExpense), Modifier.weight(1f), helper = "إجمالي ÷ عدد الحركات")
                FushMetricCard(
                    "مصروف الصيانة", expenseMoney(maintenanceSpend), Modifier.weight(1f), helper = "مركز تكلفة الصيانة",
                    tone = if (maintenanceSpend > 0) FushStatusTone.Warning else FushStatusTone.Neutral
                )
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FushSectionHeader("إجراء جديد", "سند المصروف يربط الحساب بالخزينة والأبعاد التشغيلية دون إضافة حسابات فرعية غير ضرورية.")
                    Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("تسجيل مصروف جديد") }
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    FushSectionHeader("الفلاتر والتحليل", "يمكن الجمع بين أكثر من بُعد للوصول إلى تكلفة عملية أو موظف أو فرع محدد.")
                    ExpenseNullableSelectionField("حساب المصروف", accountFilter, expenseAccounts, { "${it.code} — ${it.nameAr}" }) { accountFilter = it }
                    ExpenseNullableSelectionField("مركز التكلفة / القسم", centerFilter, EXPENSE_COST_CENTERS, { it.second }) { centerFilter = it }
                    ExpenseNullableSelectionField("الموظف", employeeFilter, employees, { "${it.code} — ${it.fullNameAr}" }) { employeeFilter = it }
                    ExpenseNullableSelectionField("مندوب المبيعات", repFilter, salesReps, { "${it.code} — ${it.fullNameAr}" }) { repFilter = it }
                    ExpenseNullableSelectionField("نوع المرجع", referenceFilter, EXPENSE_REFERENCE_TYPES, { it.second }) { referenceFilter = it }
                    ExpenseNullableSelectionField("العميل", customerFilter, customers, { "${it.code} — ${it.nameAr}" }) { customerFilter = it }
                    ExpenseNullableSelectionField("المورد", supplierFilter, suppliers, { "${it.code} — ${it.nameAr}" }) { supplierFilter = it }
                    OutlinedTextField(organizationFilter, { organizationFilter = it }, label = { Text("الفرع / المنشأة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(search, { search = it }, label = { Text("بحث في البيان أو المرجع أو الطرف") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FushDateField(from, { from = it }, "من تاريخ", modifier = Modifier.weight(1f))
                        FushDateField(to, { to = it }, "إلى تاريخ", modifier = Modifier.weight(1f))
                    }
                    OutlinedButton(onClick = {
                        accountFilter = null; employeeFilter = null; repFilter = null; customerFilter = null; supplierFilter = null
                        centerFilter = null; referenceFilter = null; organizationFilter = ""; search = ""; from = ""; to = ""
                    }, modifier = Modifier.fillMaxWidth()) { Text("مسح الفلاتر") }
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FushSectionHeader("طباعة تقرير المصروفات", "PDF وExcel ومعاينة الطباعة تستخدم نفس الفلاتر الحالية")
                    ReportExportActions(
                        document = buildExpenseSectionExportDocument(filtered, exportFilterSummary),
                        baseName = "FushERP-Expenses",
                        printJobName = "Fush ERP - Expenses",
                        enabled = filtered.isNotEmpty()
                    )
                }
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FushSectionHeader("أكبر بنود الصرف", "قراءة سريعة للحساب ومركز التكلفة الأكثر استهلاكًا ضمن النطاق الحالي.")
                    topAccount?.let {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("أكبر حساب", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(it.first, style = MaterialTheme.typography.titleSmall)
                            }
                            Text(expenseMoney(it.second), style = MaterialTheme.typography.titleMedium)
                        }
                    } ?: FushInlineState("لا توجد بيانات مصروفات ضمن الفلاتر الحالية.")
                    topCenter?.let {
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("أكبر مركز تكلفة", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(it.first, style = MaterialTheme.typography.titleSmall)
                            }
                            Text(expenseMoney(it.second), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (byAccount.size > 1) {
                        Text(
                            "أعلى الحسابات: " + byAccount.take(3).joinToString(" • ") { "${it.first}: ${expenseMoney(it.second)}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item { FushSectionHeader("حركات المصروف", "${filtered.size} حركة مطابقة للفلاتر الحالية.") }
        if (filtered.isEmpty()) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    FushEmptyState("لا توجد حركات مصروف", "لا توجد حركات مطابقة للفلاتر الحالية.", Modifier.padding(18.dp))
                }
            }
        }
        items(filtered, key = { "expense-${it.expenseId}" }) { row ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(row.voucherNo, style = MaterialTheme.typography.titleMedium)
                            Text("${row.expenseAccountCode} — ${row.expenseAccountName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(expenseMoney(row.amountBase), style = MaterialTheme.typography.titleLarge)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        FushStatusPill(row.costCenterName, FushStatusTone.Info)
                        FushStatusPill(row.paymentMethod, FushStatusTone.Neutral)
                        if (row.attachmentCount > 0) FushStatusPill("${row.attachmentCount} مرفق", FushStatusTone.Success)
                    }
                    Text(row.description)
                    Text("${expenseDate(row.voucherDate)} • ${row.currencyCode} ${expenseMoney(row.amountOriginal)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    row.employeeName.takeIf { it.isNotBlank() }?.let { Text("الموظف: $it", style = MaterialTheme.typography.bodySmall) }
                    row.salesRepName.takeIf { it.isNotBlank() }?.let { Text("مندوب المبيعات: $it", style = MaterialTheme.typography.bodySmall) }
                    row.organizationUnit.takeIf { it.isNotBlank() }?.let { Text("الفرع/المنشأة: $it", style = MaterialTheme.typography.bodySmall) }
                    val parties = listOf(row.customerName, row.supplierName, row.itemName).filter { it.isNotBlank() }
                    if (parties.isNotEmpty()) Text("الربط: ${parties.joinToString(" • ")}", style = MaterialTheme.typography.bodySmall)
                    if (row.referenceType != "NONE") {
                        Text("المرجع: ${expenseReferenceName(row.referenceType)} — ${row.referenceNo} ${row.referenceLabel}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { FushSectionHeader("مساهمة مندوبي المبيعات", "صافي المساهمة = صافي المبيعات − صافي تكلفة البضاعة − المصروفات المباشرة.") }
        if (repContribution.isEmpty()) item { FushInlineState("لا توجد بيانات مساهمة لمناديب المبيعات ضمن الفترة الحالية.") }
        items(repContribution, key = { "rep-contribution-${it.salesRepId}" }) { row ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(row.salesRepName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        FushStatusPill(
                            expenseMoney(row.netContributionBase),
                            if (row.netContributionBase >= 0) FushStatusTone.Success else FushStatusTone.Danger
                        )
                    }
                    Text("صافي المبيعات: ${expenseMoney(row.netSalesBase)}", style = MaterialTheme.typography.bodyMedium)
                    Text("صافي تكلفة البضاعة: ${expenseMoney(row.netCogsBase)}", style = MaterialTheme.typography.bodySmall)
                    Text("المصروفات المباشرة: ${expenseMoney(row.directExpensesBase)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (showAdd) {
        AddExpenseDialog(
            treasury = treasury,
            accounts = expenseAccounts,
            currencies = currencies,
            employees = employees,
            salesReps = salesReps,
            customers = customers,
            suppliers = suppliers,
            items = itemsMaster,
            salesInvoices = salesInvoices,
            purchaseInvoices = purchaseInvoices,
            productionOrders = productionOrders,
            onDismiss = { showAdd = false }
        ) { request ->
            scope.launch {
                try {
                    val id = container.accountingService.postVoucher(request.copy(createdBy = user.id))
                    message = "تم ترحيل المصروف والقيد رقم $id"
                    showAdd = false
                } catch (e: Exception) {
                    message = e.message ?: "تعذر ترحيل المصروف"
                }
            }
        }
    }
}

@Composable
private fun AddExpenseDialog(
    treasury: List<TreasuryBalanceRow>,
    accounts: List<AccountEntity>,
    currencies: List<CurrencyEntity>,
    employees: List<EmployeeEntity>,
    salesReps: List<SalesRepresentativeEntity>,
    customers: List<CustomerEntity>,
    suppliers: List<SupplierEntity>,
    items: List<ItemEntity>,
    salesInvoices: List<SalesInvoiceSummary>,
    purchaseInvoices: List<PurchaseInvoiceSummary>,
    productionOrders: List<ProductionOrderSummary>,
    onDismiss: () -> Unit,
    onSave: (AccountingService.VoucherRequest) -> Unit
) {
    val context = LocalContext.current
    var source by remember { mutableStateOf<TreasuryBalanceRow?>(treasury.firstOrNull()) }
    var account by remember { mutableStateOf<AccountEntity?>(accounts.firstOrNull()) }
    var currency by remember { mutableStateOf<CurrencyEntity?>(null) }
    var amount by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("1") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(expenseTodayText()) }
    var employee by remember { mutableStateOf<EmployeeEntity?>(null) }
    var salesRep by remember { mutableStateOf<SalesRepresentativeEntity?>(null) }
    var center by remember { mutableStateOf(EXPENSE_COST_CENTERS.last()) }
    var organizationUnit by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf<CustomerEntity?>(null) }
    var supplier by remember { mutableStateOf<SupplierEntity?>(null) }
    var item by remember { mutableStateOf<ItemEntity?>(null) }
    var refType by remember { mutableStateOf(EXPENSE_REFERENCE_TYPES.first()) }
    var salesInvoice by remember { mutableStateOf<SalesInvoiceSummary?>(null) }
    var purchaseInvoice by remember { mutableStateOf<PurchaseInvoiceSummary?>(null) }
    var productionOrder by remember { mutableStateOf<ProductionOrderSummary?>(null) }
    var referenceNo by remember { mutableStateOf("") }
    var referenceLabel by remember { mutableStateOf("") }
    var attachmentUri by remember { mutableStateOf("") }
    var attachmentName by remember { mutableStateOf("") }
    var attachmentMime by remember { mutableStateOf("") }

    LaunchedEffect(source?.currencyCode, currencies) {
        source?.let { s ->
            currency = currencies.firstOrNull { it.code == s.currencyCode } ?: currencies.firstOrNull()
            if (s.currencyCode == "YER_NEW") rate = "1"
        }
    }
    LaunchedEffect(employee?.id, salesReps) {
        if (employee != null) salesRep = salesReps.firstOrNull { it.employeeId == employee?.id }
    }
    LaunchedEffect(refType.first) {
        salesInvoice=null; purchaseInvoice=null; productionOrder=null; referenceNo=""; referenceLabel=""
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            attachmentUri = uri.toString()
            attachmentMime = context.contentResolver.getType(uri).orEmpty()
            attachmentName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }.orEmpty().ifBlank { "مرفق مصروف" }
        }
    }

    val refReady = when (refType.first) {
        "SALES_INVOICE" -> salesInvoice != null
        "PURCHASE_INVOICE" -> purchaseInvoice != null
        "PRODUCTION_ORDER" -> productionOrder != null
        "CUSTOMER" -> customer != null
        "SUPPLIER" -> supplier != null
        "PRODUCT" -> item != null
        "SALES_ORDER", "PURCHASE_ORDER", "DISTRIBUTION", "BRANCH", "FACILITY", "OTHER" -> referenceNo.isNotBlank() || referenceLabel.isNotBlank() || organizationUnit.isNotBlank()
        else -> true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مصروف جديد") },
        text = {
            LazyColumn(Modifier.heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                item {
                    ExpenseSelectionField("طريقة الدفع / الخزينة أو البنك", source, treasury, { "${it.nameAr} — ${it.kind}" }) { source = it }
                    ExpenseSelectionField("نوع المصروف / حساب المصروف", account, accounts, { "${it.code} — ${it.nameAr}" }) { account = it }
                    ExpenseSelectionField("العملة", currency, currencies, { it.nameAr }) { currency=it; if(it.isBase) rate="1" }
                    FushDecimalField(amount, { amount=it }, "المبلغ", modifier=Modifier.fillMaxWidth())
                    FushDecimalField(rate, { rate=it }, "سعر الصرف", modifier=Modifier.fillMaxWidth())
                    FushDateField(date, { date=it }, "التاريخ", modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(description, { description=it }, label={Text("البيان / الوصف")}, modifier=Modifier.fillMaxWidth())
                }
                item {
                    Text("الأبعاد التحليلية", style=MaterialTheme.typography.titleSmall)
                    ExpenseNullableSelectionField("الموظف — اختياري", employee, employees, { "${it.code} — ${it.fullNameAr}" }) { employee=it }
                    ExpenseNullableSelectionField("مندوب المبيعات — اختياري", salesRep, salesReps, { "${it.code} — ${it.fullNameAr}" }) { salesRep=it }
                    ExpenseSelectionField("مركز التكلفة / القسم", center, EXPENSE_COST_CENTERS, { it.second }) { center=it }
                    OutlinedTextField(organizationUnit, { organizationUnit=it }, label={Text("الفرع / المنشأة — اختياري")}, modifier=Modifier.fillMaxWidth())
                    ExpenseNullableSelectionField("العميل — اختياري", customer, customers, { "${it.code} — ${it.nameAr}" }) { customer=it }
                    ExpenseNullableSelectionField("المورد — اختياري", supplier, suppliers, { "${it.code} — ${it.nameAr}" }) { supplier=it }
                    ExpenseNullableSelectionField("المنتج / الصنف — اختياري", item, items, { "${it.code} — ${it.nameAr}" }) { item=it }
                }
                item {
                    Text("المرجع / العملية", style=MaterialTheme.typography.titleSmall)
                    ExpenseSelectionField("نوع المرجع", refType, EXPENSE_REFERENCE_TYPES, { it.second }) { refType=it }
                    when (refType.first) {
                        "SALES_INVOICE" -> ExpenseSelectionField("فاتورة المبيعات", salesInvoice, salesInvoices, { "${it.invoiceNo} — ${it.customerName}" }) { salesInvoice=it }
                        "PURCHASE_INVOICE" -> ExpenseSelectionField("فاتورة المشتريات", purchaseInvoice, purchaseInvoices, { "${it.invoiceNo} — ${it.supplierName}" }) { purchaseInvoice=it }
                        "PRODUCTION_ORDER" -> ExpenseSelectionField("أمر الإنتاج", productionOrder, productionOrders, { "${it.orderNo} — ${it.productName}" }) { productionOrder=it }
                        "CUSTOMER" -> Text("يتم استخدام العميل المحدد أعلاه كمرجع.", style=MaterialTheme.typography.bodySmall)
                        "SUPPLIER" -> Text("يتم استخدام المورد المحدد أعلاه كمرجع.", style=MaterialTheme.typography.bodySmall)
                        "PRODUCT" -> Text("يتم استخدام المنتج/الصنف المحدد أعلاه كمرجع.", style=MaterialTheme.typography.bodySmall)
                        "NONE" -> Unit
                        else -> {
                            OutlinedTextField(referenceNo, { referenceNo=it }, label={Text("رقم المرجع")}, modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(referenceLabel, { referenceLabel=it }, label={Text("وصف المرجع")}, modifier=Modifier.fillMaxWidth())
                        }
                    }
                }
                item {
                    Text("المرفق", style=MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick={ picker.launch(arrayOf("application/pdf", "image/*")) }) { Text(if(attachmentName.isBlank()) "إرفاق فاتورة / سند" else "تغيير المرفق") }
                    attachmentName.takeIf { it.isNotBlank() }?.let { Text(it, style=MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = source != null && account != null && currency != null && amount.toDoubleOrNull()?.let { it > 0 } == true && rate.toDoubleOrNull()?.let { it > 0 } == true && description.isNotBlank() && refReady,
                onClick = {
                    val refId = when(refType.first) {
                        "SALES_INVOICE" -> salesInvoice?.id
                        "PURCHASE_INVOICE" -> purchaseInvoice?.id
                        "PRODUCTION_ORDER" -> productionOrder?.id
                        else -> null
                    }
                    val refNoResolved = when(refType.first) {
                        "SALES_INVOICE" -> salesInvoice?.invoiceNo.orEmpty()
                        "PURCHASE_INVOICE" -> purchaseInvoice?.invoiceNo.orEmpty()
                        "PRODUCTION_ORDER" -> productionOrder?.orderNo.orEmpty()
                        else -> referenceNo
                    }
                    val refLabelResolved = when(refType.first) {
                        "SALES_INVOICE" -> salesInvoice?.customerName.orEmpty()
                        "PURCHASE_INVOICE" -> purchaseInvoice?.supplierName.orEmpty()
                        "PRODUCTION_ORDER" -> productionOrder?.productName.orEmpty()
                        else -> referenceLabel
                    }
                    onSave(
                        AccountingService.VoucherRequest(
                            type="EXPENSE",
                            treasuryAccountId=source!!.id,
                            offsetAccountId=account!!.id,
                            amountOriginal=amount.toDouble(),
                            currencyCode=currency!!.code,
                            exchangeRate=rate.toDouble(),
                            description=description,
                            referenceNo=refNoResolved,
                            voucherDate=expenseParseStart(date),
                            createdBy=0L,
                            expenseContext=AccountingService.ExpenseContext(
                                employeeId=employee?.id,
                                salesRepId=salesRep?.id,
                                costCenterCode=center.first,
                                organizationUnit=organizationUnit,
                                referenceType=refType.first,
                                referenceId=refId,
                                referenceNo=refNoResolved,
                                referenceLabel=refLabelResolved,
                                customerId=customer?.id,
                                supplierId=supplier?.id,
                                itemId=item?.id,
                                attachment=attachmentUri.takeIf { it.isNotBlank() }?.let {
                                    AccountingService.ExpenseAttachmentInput(attachmentName, attachmentMime, it)
                                }
                            )
                        )
                    )
                }
            ) { Text("ترحيل المصروف") }
        },
        dismissButton = { TextButton(onClick=onDismiss) { Text("إلغاء") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ExpenseSelectionField(label: String, selected: T?, options: List<T>, title: (T)->String, onSelect:(T)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded=expanded, onExpandedChange={ expanded=!expanded }) {
        OutlinedTextField(
            value=selected?.let(title).orEmpty(), onValueChange={}, readOnly=true,
            label={Text(label)}, trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},
            modifier=Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded=expanded, onDismissRequest={expanded=false}) {
            options.forEach { option -> DropdownMenuItem(text={Text(title(option))}, onClick={onSelect(option);expanded=false}) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ExpenseNullableSelectionField(label: String, selected: T?, options: List<T>, title:(T)->String, onSelect:(T?)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded=expanded, onExpandedChange={expanded=!expanded}) {
        OutlinedTextField(
            value=selected?.let(title) ?: "الكل / بدون", onValueChange={}, readOnly=true,
            label={Text(label)}, trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},
            modifier=Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            DropdownMenuItem(text={Text("الكل / بدون")},onClick={onSelect(null);expanded=false})
            options.forEach { option -> DropdownMenuItem(text={Text(title(option))},onClick={onSelect(option);expanded=false}) }
        }
    }
}

private fun expenseReferenceName(code:String)=EXPENSE_REFERENCE_TYPES.firstOrNull{it.first==code}?.second ?: code
private fun expenseMoney(v:Double)=String.format(Locale.US,"%,.2f",v)
private fun expenseDate(v:Long)=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date(v))
private fun expenseTodayText()=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
private fun expenseParseStart(text:String):Long=runCatching{SimpleDateFormat("yyyy-MM-dd",Locale.US).apply{isLenient=false}.parse(text)?.time}.getOrNull() ?: System.currentTimeMillis()
private fun expenseDateMatch(value:Long,from:String,to:String):Boolean {
    val parser=SimpleDateFormat("yyyy-MM-dd",Locale.US).apply{isLenient=false}
    val f=runCatching{if(from.isBlank())null else parser.parse(from)?.time}.getOrNull()
    val t=runCatching{if(to.isBlank())null else parser.parse(to)?.time?.plus(86_400_000L-1)}.getOrNull()
    return (f==null || value>=f) && (t==null || value<=t)
}
