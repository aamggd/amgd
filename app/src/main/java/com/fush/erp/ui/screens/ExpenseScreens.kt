package com.fush.erp.ui.screens

import com.fush.erp.domain.TrustedTimeService
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
import com.fush.erp.attachments.AttachmentStorage
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.*
import com.fush.erp.domain.AccountingService
import com.fush.erp.domain.ExpenseDatePolicy
import com.fush.erp.domain.ExpenseClassificationPolicy
import com.fush.erp.domain.TreasuryVoucherOperationIdentity
import com.fush.erp.ui.FushMetricCard
import com.fush.erp.ui.FushSectionHeader
import com.fush.erp.ui.FushStatusPill
import com.fush.erp.ui.FushStatusTone
import com.fush.erp.ui.FushInlineState
import com.fush.erp.ui.FushEmptyState
import com.fush.erp.ui.FushDateField
import com.fush.erp.ui.FushDecimalField
import com.fush.erp.ui.FushOperationMessage
import com.fush.erp.ui.FushSearchableSelectionField
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

private val EXPENSE_COST_CENTERS = ExpenseClassificationPolicy.costCenters.map { it.code to it.nameAr }

private val EXPENSE_REFERENCE_TYPES = ExpenseClassificationPolicy.referenceTypes.map { it.code to it.nameAr }

@Composable
fun ExpenseManagementTab(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier) {
    var showAdd by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<ExpenseReportRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var centerFilter by remember { mutableStateOf<String?>(null) }
    var attachmentExpenseId by remember { mutableStateOf<Long?>(null) }
    var attachmentExpenseTitle by remember { mutableStateOf("") }

    // v104 architecture: entering Expenses performs exactly one bounded, one-shot Room call.
    // There are no long-lived Room collectors on the entry path. Any mapper/query problem is
    // contained in this coroutine and becomes an inline warning instead of a process-level crash.
    LaunchedEffect(reloadToken) {
        loading = true
        loadError = null
        rows = try {
            container.db.expenseDao().reportRows(0L, Long.MAX_VALUE)
        } catch (error: Throwable) {
            runCatching { android.util.Log.e("FushExpenseV104", "Expense list load failed", error) }
            loadError = "تعذر قراءة الحركات القديمة بأمان. يمكنك تسجيل مصروف جديد أو إعادة المحاولة."
            emptyList()
        }
        loading = false
    }

    val filtered = remember(rows, search, from, to, centerFilter) {
        rows.filter { row ->
            (centerFilter == null || row.costCenterCode == centerFilter) &&
                expenseDateMatch(row.voucherDate, from, to) &&
                (search.isBlank() || listOf(
                    row.voucherNo,
                    row.description,
                    row.expenseAccountName,
                    row.costCenterName,
                    row.employeeName,
                    row.salesRepName,
                    row.customerName,
                    row.supplierName,
                    row.itemName,
                    row.organizationUnit,
                    row.referenceNo,
                    row.referenceLabel
                ).any { it.contains(search.trim(), ignoreCase = true) })
        }
    }
    val total = filtered.sumOf { it.amountBase }
    val average = if (filtered.isEmpty()) 0.0 else total / filtered.size
    val centers = rows
        .map { it.costCenterCode to it.costCenterName }
        .filter { it.first.isNotBlank() }
        .distinctBy { it.first }
        .sortedBy { it.second }
    val byAccount = filtered.groupBy { it.expenseAccountName.ifBlank { "غير مصنف" } }
        .mapValues { (_, list) -> list.sumOf { it.amountBase } }
        .toList()
        .sortedByDescending { it.second }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FushSectionHeader(
                title = "المصروفات",
                subtitle = "واجهة جديدة مستقلة وخفيفة: عرض الحركات أولًا، والبيانات الإضافية تُحمّل فقط عند الحاجة."
            )
            FushOperationMessage(message, onConsumed = { message = null })
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = { showAdd = true }, modifier = Modifier.weight(1f)) {
                            Text("تسجيل مصروف جديد")
                        }
                        OutlinedButton(onClick = { reloadToken++ }) {
                            Text("تحديث")
                        }
                    }
                    if (loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("جاري تحميل حركات المصروفات…", style = MaterialTheme.typography.bodySmall)
                    }
                    loadError?.let { warning ->
                        FushInlineState(warning)
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(
                    "إجمالي المصروف",
                    expenseMoney(total),
                    Modifier.weight(1f),
                    helper = "حسب النتائج المعروضة",
                    tone = FushStatusTone.Info
                )
                FushMetricCard(
                    "عدد الحركات",
                    filtered.size.toString(),
                    Modifier.weight(1f),
                    helper = "الحركات المطابقة"
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(
                    "متوسط الحركة",
                    expenseMoney(average),
                    Modifier.weight(1f),
                    helper = "الإجمالي ÷ العدد"
                )
                FushMetricCard(
                    "أكبر حساب",
                    byAccount.firstOrNull()?.let { expenseMoney(it.second) } ?: "0.00",
                    Modifier.weight(1f),
                    helper = byAccount.firstOrNull()?.first ?: "لا توجد بيانات",
                    tone = FushStatusTone.Neutral
                )
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    FushSectionHeader("بحث وفترة", "الفلاتر هنا تعتمد على بيانات الحركة نفسها ولا تفتح جداول إضافية.")
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("بحث") },
                        placeholder = { Text("رقم السند، الوصف، الحساب، الموظف، الطرف…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FushDateField(value = from, onValueChange = { from = it }, label = "من تاريخ", modifier = Modifier.weight(1f))
                        FushDateField(value = to, onValueChange = { to = it }, label = "إلى تاريخ", modifier = Modifier.weight(1f))
                    }
                    if (centers.isNotEmpty()) {
                        ExpenseNullableSelectionField(
                            "مركز التكلفة",
                            centerFilter?.let { code -> centers.firstOrNull { it.first == code } },
                            centers,
                            { it.second }
                        ) { selected -> centerFilter = selected?.first }
                    }
                    if (search.isNotBlank() || from.isNotBlank() || to.isNotBlank() || centerFilter != null) {
                        TextButton(onClick = {
                            search = ""
                            from = ""
                            to = ""
                            centerFilter = null
                        }) { Text("مسح الفلاتر") }
                    }
                }
            }
        }

        if (byAccount.isNotEmpty()) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FushSectionHeader("ملخص حسب الحساب", "تحليل مباشر من الحركات المحملة دون استعلامات إضافية.")
                        byAccount.take(5).forEach { (account, amount) ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(account, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text(expenseMoney(amount), style = MaterialTheme.typography.titleSmall)
                            }
                            if (account != byAccount.take(5).last().first) HorizontalDivider()
                        }
                    }
                }
            }
        }

        item {
            FushSectionHeader("حركات المصروف", "${filtered.size} حركة")
        }
        if (!loading && filtered.isEmpty()) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    FushEmptyState(
                        if (loadError == null) "لا توجد حركات مصروف" else "تعذر عرض الحركات القديمة",
                        if (loadError == null) "يمكنك البدء بتسجيل مصروف جديد." else "الشاشة بقيت مفتوحة بأمان. جرّب التحديث أو سجّل حركة جديدة.",
                        Modifier.padding(18.dp)
                    )
                }
            }
        }
        items(filtered, key = { "expense-v104-${it.expenseId}-${it.voucherId}" }) { row ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(row.voucherNo.ifBlank { "سند مصروف" }, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${row.expenseAccountCode} — ${row.expenseAccountName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(expenseMoney(row.amountBase), style = MaterialTheme.typography.titleLarge)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (row.costCenterName.isNotBlank()) FushStatusPill(row.costCenterName, FushStatusTone.Info)
                        if (row.paymentMethod.isNotBlank()) FushStatusPill(row.paymentMethod, FushStatusTone.Neutral)
                        if (row.attachmentCount > 0) FushStatusPill("${row.attachmentCount} مرفق", FushStatusTone.Success)
                    }
                    if (row.description.isNotBlank()) Text(row.description)
                    Text(
                        "${expenseDate(row.voucherDate)} • ${row.currencyCode} ${expenseMoney(row.amountOriginal)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val dimensions = listOfNotNull(
                        row.employeeName.takeIf { it.isNotBlank() }?.let { "الموظف: $it" },
                        row.salesRepName.takeIf { it.isNotBlank() }?.let { "المندوب: $it" },
                        row.customerName.takeIf { it.isNotBlank() }?.let { "العميل: $it" },
                        row.supplierName.takeIf { it.isNotBlank() }?.let { "المورد: $it" },
                        row.itemName.takeIf { it.isNotBlank() }?.let { "الصنف: $it" },
                        row.organizationUnit.takeIf { it.isNotBlank() }?.let { "الفرع: $it" }
                    )
                    dimensions.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (row.expenseId > 0 && row.attachmentCount > 0) {
                        TextButton(onClick = {
                            attachmentExpenseId = row.expenseId
                            attachmentExpenseTitle = row.voucherNo.ifBlank { "سند مصروف" }
                        }) { Text("عرض المرفقات (${row.attachmentCount})") }
                    }
                    if (row.referenceType != "NONE" && row.referenceNo.isNotBlank()) {
                        Text(
                            "المرجع: ${expenseReferenceName(row.referenceType)} — ${row.referenceNo}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    attachmentExpenseId?.let { expenseId ->
        ExpenseAttachmentsDialog(
            container = container,
            expenseId = expenseId,
            title = attachmentExpenseTitle,
            onDismiss = { attachmentExpenseId = null },
            onMessage = { message = it }
        )
    }

    if (showAdd) {
        ExpenseAddDialogHostV104(
            container = container,
            user = user,
            onDismiss = { showAdd = false },
            onMessage = { message = it },
            onPosted = {
                showAdd = false
                reloadToken++
            }
        )
    }
}


@Composable
private fun ExpenseAttachmentsDialog(
    container: AppContainer,
    expenseId: Long,
    title: String,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val attachments by container.db.expenseDao().observeAttachments(expenseId).collectAsState(initial = emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
        title = { Text("مرفقات $title") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (attachments.isEmpty()) {
                    FushInlineState("لا توجد مرفقات لهذا المصروف.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(attachments, key = { it.id }) { attachment ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(attachment.fileName, style = MaterialTheme.typography.titleSmall)
                                    Text(attachment.mimeType.ifBlank { "ملف" }, style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(onClick = {
                                            runCatching { context.startActivity(AttachmentStorage.openIntent(context, attachment.uri, attachment.mimeType)) }
                                                .onFailure { onMessage(it.message ?: "تعذر فتح المرفق") }
                                        }) { Text("فتح") }
                                        TextButton(onClick = {
                                            runCatching {
                                                context.startActivity(Intent.createChooser(AttachmentStorage.shareIntent(context, attachment.uri, attachment.fileName, attachment.mimeType), "مشاركة المرفق"))
                                            }.onFailure { onMessage(it.message ?: "تعذر مشاركة المرفق") }
                                        }) { Text("مشاركة") }
                                        TextButton(onClick = {
                                            runCatching {
                                                AttachmentStorage.exportToDownloads(context, attachment.uri, attachment.fileName, attachment.mimeType)
                                                onMessage("تم حفظ نسخة من ${attachment.fileName}")
                                            }.onFailure { onMessage(it.message ?: "تعذر حفظ نسخة المرفق") }
                                        }) { Text("حفظ نسخة") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

private data class ExpenseAddSnapshotV104(
    val accounts: List<AccountEntity>,
    val employees: List<EmployeeEntity>,
    val salesReps: List<SalesRepresentativeEntity>,
    val customers: List<CustomerEntity>,
    val suppliers: List<SupplierEntity>,
    val items: List<ItemEntity>,
    val treasury: List<TreasuryBalanceRow>,
    val currencies: List<CurrencyEntity>,
    val salesInvoices: List<SalesInvoiceSummary>,
    val purchaseInvoices: List<PurchaseInvoiceSummary>,
    val productionOrders: List<ProductionOrderSummary>,
    val warnings: List<String>
)

@Composable
private fun ExpenseAddDialogHostV104(
    container: AppContainer,
    user: UserEntity,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    onPosted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<ExpenseAddSnapshotV104?>(null) }
    var loading by remember { mutableStateOf(true) }
    var fatalLoadError by remember { mutableStateOf<String?>(null) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var attachmentUri by remember { mutableStateOf("") }
    var attachmentName by remember { mutableStateOf("") }
    var attachmentMime by remember { mutableStateOf("") }
    var posting by remember { mutableStateOf(false) }

    LaunchedEffect(loadAttempt) {
        loading = true
        fatalLoadError = null
        val warnings = mutableListOf<String>()

        suspend fun <T> safeLoad(label: String, fallback: T, block: suspend () -> T): T = try {
            block()
        } catch (error: Throwable) {
            runCatching { android.util.Log.e("FushExpenseV104", "Add-expense source failed: $label", error) }
            warnings += label
            fallback
        }

        val accounts = safeLoad("حسابات المصروف", emptyList()) { container.db.accountDao().allActive() }
            .filter { it.isPosting && it.type == "EXPENSE" }
        val treasury = safeLoad("الخزائن", emptyList()) { container.db.accountingDao().observeTreasuryBalances().first() }
        val currencies = safeLoad("العملات", emptyList()) { container.db.currencyDao().allActive() }
        val employees = safeLoad("الموظفين", emptyList()) { container.db.employeeDao().activeEmployees() }
        val salesReps = safeLoad("المندوبين", emptyList()) { container.db.salesRepresentativeDao().active() }
        val customers = safeLoad("العملاء", emptyList()) { container.db.customerDao().allActive() }
        val suppliers = safeLoad("الموردين", emptyList()) { container.db.supplierDao().allActive() }
        val items = safeLoad("الأصناف", emptyList()) { container.db.itemDao().allActive() }
        val salesInvoices = safeLoad("فواتير المبيعات", emptyList()) { container.db.salesDao().observeSummaries().first() }
        val purchaseInvoices = safeLoad("فواتير المشتريات", emptyList()) { container.db.purchaseDao().observeSummaries().first() }
        val productionOrders = safeLoad("أوامر الإنتاج", emptyList()) { container.db.productionDao().observeOrderSummaries().first() }

        if (accounts.isEmpty() || treasury.isEmpty() || currencies.isEmpty()) {
            fatalLoadError = buildString {
                append("تعذر تجهيز البيانات الأساسية لتسجيل المصروف.")
                if (warnings.isNotEmpty()) append(" المصادر المتأثرة: ${warnings.joinToString("، ")}.")
            }
            snapshot = null
        } else {
            snapshot = ExpenseAddSnapshotV104(
                accounts = accounts,
                employees = employees,
                salesReps = salesReps,
                customers = customers,
                suppliers = suppliers,
                items = items,
                treasury = treasury,
                currencies = currencies,
                salesInvoices = salesInvoices,
                purchaseInvoices = purchaseInvoices,
                productionOrders = productionOrders,
                warnings = warnings
            )
        }
        loading = false
    }

    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val pickedName = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }.orEmpty()
            }.getOrDefault("").ifBlank { "مرفق مصروف" }
            runCatching { AttachmentStorage.importFromUri(context, uri, "expense", pickedName) }
                .onSuccess { stored ->
                    attachmentUri = stored.reference
                    attachmentMime = stored.mimeType
                    attachmentName = stored.fileName
                }
                .onFailure { onMessage(it.message ?: "تعذر نسخ المرفق إلى تخزين FUSH") }
        }
    }

    when {
        loading -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
            title = { Text("تجهيز تسجيل المصروف") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("يتم تحميل البيانات اللازمة فقط لهذه العملية…")
                }
            }
        )
        fatalLoadError != null -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { Button(onClick = { loadAttempt++ }) { Text("إعادة المحاولة") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
            title = { Text("تعذر تجهيز النموذج") },
            text = { Text(fatalLoadError.orEmpty()) }
        )
        snapshot != null -> {
            val data = snapshot ?: return
            if (data.warnings.isNotEmpty()) {
                LaunchedEffect(data.warnings) {
                    onMessage("تم فتح نموذج المصروف، لكن تعذر تحميل: ${data.warnings.joinToString("، ")}")
                }
            }
            AddExpenseDialog(
                treasury = data.treasury,
                accounts = data.accounts,
                currencies = data.currencies,
                employees = data.employees,
                salesReps = data.salesReps,
                customers = data.customers,
                suppliers = data.suppliers,
                items = data.items,
                salesInvoices = data.salesInvoices,
                purchaseInvoices = data.purchaseInvoices,
                productionOrders = data.productionOrders,
                attachmentUri = attachmentUri,
                attachmentName = attachmentName,
                attachmentMime = attachmentMime,
                isPosting = posting,
                onPickAttachment = { attachmentPicker.launch(arrayOf("application/pdf", "image/*")) },
                onDismiss = onDismiss
            ) { request ->
                if (posting) return@AddExpenseDialog
                posting = true
                scope.launch {
                    try {
                        val id = container.accountingService.postVoucher(request.copy(createdBy = user.id))
                        onMessage("تم ترحيل المصروف والقيد رقم $id")
                        onPosted()
                    } catch (error: Throwable) {
                        runCatching { android.util.Log.e("FushExpenseV104", "Expense post failed", error) }
                        onMessage(error.message ?: "تعذر ترحيل المصروف")
                        posting = false
                    }
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
    attachmentUri: String,
    attachmentName: String,
    attachmentMime: String,
    isPosting: Boolean,
    onPickAttachment: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (AccountingService.VoucherRequest) -> Unit
) {
    var source by remember { mutableStateOf<TreasuryBalanceRow?>(treasury.firstOrNull()) }
    var account by remember { mutableStateOf<AccountEntity?>(null) }
    var currency by remember { mutableStateOf<CurrencyEntity?>(null) }
    var amount by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("1") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(expenseTodayText()) }
    var employee by remember { mutableStateOf<EmployeeEntity?>(null) }
    var salesRep by remember { mutableStateOf<SalesRepresentativeEntity?>(null) }
    var center by remember { mutableStateOf<Pair<String, String>?>(null) }
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
    val operationId = remember { TreasuryVoucherOperationIdentity.newOperationId() }
    val parsedVoucherDate = remember(date) { ExpenseDatePolicy.parseStartOrNull(date) }

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

    val refReady = when (refType.first) {
        "SALES_INVOICE" -> salesInvoice != null
        "PURCHASE_INVOICE" -> purchaseInvoice != null
        "PRODUCTION_ORDER" -> productionOrder != null
        "CUSTOMER" -> customer != null
        "SUPPLIER" -> supplier != null
        "PRODUCT" -> item != null
        "BRANCH", "FACILITY" -> organizationUnit.isNotBlank()
        "SALES_ORDER", "PURCHASE_ORDER", "DISTRIBUTION", "OTHER" -> referenceNo.isNotBlank() || referenceLabel.isNotBlank() || organizationUnit.isNotBlank()
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
                    ExpenseSelectionField("العملة", currency, currencies, { it.nameAr }) { currency=it; if(it?.isBase == true) rate="1" }
                    FushDecimalField(amount, { amount=it }, "المبلغ", modifier=Modifier.fillMaxWidth())
                    FushDecimalField(rate, { rate=it }, "سعر الصرف", modifier=Modifier.fillMaxWidth())
                    FushDateField(date, { date=it }, "التاريخ", modifier=Modifier.fillMaxWidth())
                    if (parsedVoucherDate == null) {
                        Text(
                            "التاريخ غير صالح. استخدم الصيغة yyyy-MM-dd.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedTextField(description, { description=it }, label={Text("البيان / الوصف")}, modifier=Modifier.fillMaxWidth())
                }
                item {
                    Text("الأبعاد التحليلية", style=MaterialTheme.typography.titleSmall)
                    ExpenseNullableSelectionField("الموظف — اختياري", employee, employees, { "${it.code} — ${it.fullNameAr}" }) { employee=it }
                    ExpenseNullableSelectionField("مندوب المبيعات — اختياري", salesRep, salesReps, { "${it.code} — ${it.fullNameAr}" }) { salesRep=it }
                    ExpenseSelectionField("مركز التكلفة / القسم — إلزامي", center, EXPENSE_COST_CENTERS, { it.second }) { center=it }
                    OutlinedTextField(
                        organizationUnit,
                        { organizationUnit=it },
                        label={ Text(if (refType.first in setOf("BRANCH", "FACILITY")) "الفرع / المنشأة — إلزامي" else "الفرع / المنشأة — اختياري") },
                        modifier=Modifier.fillMaxWidth()
                    )
                    ExpenseNullableSelectionField("العميل — اختياري", customer, customers, { "${it.code} — ${it.nameAr}" }) { customer=it }
                    ExpenseNullableSelectionField("المورد — اختياري", supplier, suppliers, { "${it.code} — ${it.nameAr}" }) { supplier=it }
                    ExpenseNullableSelectionField("المنتج / الصنف — اختياري", item, items, { "${it.code} — ${it.nameAr}" }) { item=it }
                }
                item {
                    Text("المرجع / العملية", style=MaterialTheme.typography.titleSmall)
                    ExpenseSelectionField("نوع المرجع", refType, EXPENSE_REFERENCE_TYPES, { it.second }) { selected -> selected?.let { refType = it } }
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
                    OutlinedButton(onClick=onPickAttachment) { Text(if(attachmentName.isBlank()) "إرفاق فاتورة / سند" else "تغيير المرفق") }
                    attachmentName.takeIf { it.isNotBlank() }?.let { Text(it, style=MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isPosting && source != null && account != null && currency != null && center != null && parsedVoucherDate != null && amount.toDoubleOrNull()?.let { it > 0 } == true && rate.toDoubleOrNull()?.let { it > 0 } == true && description.isNotBlank() && refReady,
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
                            voucherDate=requireNotNull(parsedVoucherDate) { "التاريخ غير صالح" },
                            createdBy=0L,
                            operationId = operationId,
                            expenseContext=AccountingService.ExpenseContext(
                                employeeId=employee?.id,
                                salesRepId=salesRep?.id,
                                costCenterCode=requireNotNull(center).first,
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
            ) { Text(if (isPosting) "جارٍ الترحيل…" else "ترحيل المصروف") }
        },
        dismissButton = { TextButton(onClick=onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun <T> ExpenseSelectionField(label: String, selected: T?, options: List<T>, title: (T)->String, onSelect:(T?)->Unit) {
    FushSearchableSelectionField(
        label = label,
        selectedText = selected?.let(title).orEmpty(),
        options = options,
        optionText = title,
        onSelected = { onSelect(it) },
        onCleared = { onSelect(null) },
        placeholder = "اختر أو اكتب للبحث",
    )
}

@Composable
private fun <T> ExpenseNullableSelectionField(label: String, selected: T?, options: List<T>, title:(T)->String, onSelect:(T?)->Unit) {
    FushSearchableSelectionField(
        label = label,
        selectedText = selected?.let(title).orEmpty(),
        options = options,
        optionText = title,
        onSelected = { onSelect(it) },
        onCleared = { onSelect(null) },
        allowClear = true,
        onClearSelected = { onSelect(null) },
        placeholder = "الكل / بدون — أو اكتب للبحث",
    )
}

private fun expenseReferenceName(code:String)=EXPENSE_REFERENCE_TYPES.firstOrNull{it.first==code}?.second ?: code
private fun expenseMoney(v:Double)=String.format(Locale.US,"%,.2f",v)
private fun expenseDate(v:Long)=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date(v))
private fun expenseTodayText()=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date(TrustedTimeService.now()))
private fun expenseDateMatch(value:Long,from:String,to:String):Boolean {
    val parser=SimpleDateFormat("yyyy-MM-dd",Locale.US).apply{isLenient=false}
    val f=runCatching{if(from.isBlank())null else parser.parse(from)?.time}.getOrNull()
    val t=runCatching{if(to.isBlank())null else parser.parse(to)?.time?.plus(86_400_000L-1)}.getOrNull()
    return (f==null || value>=f) && (t==null || value<=t)
}
