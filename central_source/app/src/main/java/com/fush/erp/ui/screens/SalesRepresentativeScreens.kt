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
import androidx.compose.ui.unit.dp
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.*
import com.fush.erp.ui.*
import com.fush.erp.domain.AccountingService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun SalesRepresentativesScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val reps by container.db.salesRepresentativeDao().observeAll().collectAsState(initial = emptyList())
    val employees by container.db.employeeDao().observeActiveEmployees().collectAsState(initial = emptyList())
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }
    var message by remember { mutableStateOf<String?>(null) }

    val selected = reps.firstOrNull { it.id == selectedId }
    if (selected != null) {
        SalesRepresentativeProfile(
            container = container,
            user = user,
            rep = selected,
            onBack = { selectedId = null },
            modifier = modifier
        )
        return
    }

    val activeCount = reps.count { it.status == "ACTIVE" }
    val internalCount = reps.count { it.repType == "INTERNAL" }
    val externalCount = reps.size - internalCount
    val filtered = remember(reps, search, statusFilter) {
        val q = search.trim().lowercase(Locale.ROOT)
        reps.filter { rep ->
            val statusMatches = statusFilter == "ALL" || rep.status == statusFilter
            val searchMatches = q.isBlank() || listOf(rep.code, rep.fullNameAr, rep.fullNameEn, rep.phone, rep.territory, rep.repType, rep.status)
                .any { value -> value.lowercase(Locale.ROOT).contains(q) }
            statusMatches && searchMatches
        }
    }

    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            FushSectionHeader(
                "مناديب المبيعات",
                "هوية ثابتة لكل مندوب مع العملاء والفواتير والتحصيلات والمرتجعات والعمولات والسندات المرتبطة."
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard("إجمالي المناديب", reps.size.toString(), Modifier.weight(1f), helper = "كل الملفات")
                FushMetricCard("النشطون", activeCount.toString(), Modifier.weight(1f), helper = "متاحون للبيع والتحصيل", tone = FushStatusTone.Success)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard("داخليون", internalCount.toString(), Modifier.weight(1f), helper = "مرتبطون بموظفين", tone = FushStatusTone.Info)
                FushMetricCard("خارجيون", externalCount.toString(), Modifier.weight(1f), helper = "مندوبون مستقلون")
            }
        }
        item {
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("إضافة مندوب مبيعات") }
            FushOperationMessage(message, onConsumed = { message = null })
        }
        item {
            OutlinedTextField(
                search,
                { search = it },
                label = { Text("بحث بالاسم أو الكود أو المنطقة أو الهاتف") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = statusFilter == "ALL", onClick = { statusFilter = "ALL" }, label = { Text("الكل") })
                FilterChip(selected = statusFilter == "ACTIVE", onClick = { statusFilter = "ACTIVE" }, label = { Text("نشط") })
                FilterChip(selected = statusFilter == "INACTIVE", onClick = { statusFilter = "INACTIVE" }, label = { Text("موقوف") })
            }
        }
        item { FushSectionHeader("دليل المناديب", "${filtered.size} ملف مطابق للبحث والتصفية") }
        if (filtered.isEmpty()) item {
            FushEmptyState(
                title = if (reps.isEmpty()) "لا يوجد مناديب مبيعات بعد" else "لا توجد نتائج مطابقة",
                detail = if (reps.isEmpty()) "أضف أول مندوب لربط العملاء والمبيعات والتحصيلات والعمولات." else "غيّر البحث أو مرشح الحالة لعرض مناديب آخرين.",
            )
        }
        items(filtered, key = { it.id }) { rep ->
            ElevatedCard(onClick = { selectedId = rep.id }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(rep.fullNameAr, style = MaterialTheme.typography.titleMedium)
                            Text("${rep.code} • ${if (rep.repType == "INTERNAL") "مندوب داخلي" else "مندوب خارجي"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FushStatusPill(if (rep.status == "ACTIVE") "نشط" else "موقوف", if (rep.status == "ACTIVE") FushStatusTone.Success else FushStatusTone.Warning)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("المنطقة: ${rep.territory.ifBlank { "غير محددة" }}")
                        Text("عمولة ${repMoney(rep.commissionRatePct)}%", color = MaterialTheme.colorScheme.primary)
                    }
                    if (rep.phone.isNotBlank()) Text(rep.phone, style = MaterialTheme.typography.bodySmall)
                    Text("فتح ملف المندوب ←", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (showAdd) {
        AddSalesRepDialog(employees, onDismiss = { showAdd = false }) { type, employee, name, en, phone, territory, rate, notes ->
            scope.launch {
                try {
                    val row = container.salesRepresentativeService.create(type, employee?.id, name, en, phone, territory, rate, notes, user.id)
                    message = "تم إنشاء المندوب ${row.code}"
                    showAdd = false
                    selectedId = row.id
                } catch (e: Exception) { message = e.message ?: "تعذر إنشاء المندوب" }
            }
        }
    }
}

@Composable
private fun SalesRepresentativeProfile(
    container: AppContainer,
    user: UserEntity,
    rep: SalesRepresentativeEntity,
    onBack: () -> Unit,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    val customers by container.db.salesRepresentativeDao().observeCustomers(rep.id).collectAsState(initial = emptyList())
    val allCustomers by container.db.customerDao().observeAll().collectAsState(initial = emptyList())
    val invoices by container.db.salesRepresentativeDao().observeInvoices(rep.id).collectAsState(initial = emptyList())
    val commissions by container.db.salesRepresentativeDao().observeCommissions(rep.id).collectAsState(initial = emptyList())
    val vouchers by container.db.partyDao().observeSalesRepVouchers(rep.id).collectAsState(initial = emptyList())
    val audits by container.db.governanceDao().observeAuditEventsForEntity("SALES_REP", rep.id.toString()).collectAsState(initial = emptyList())
    val salesBase by container.db.salesRepresentativeDao().observeSalesBase(rep.id).collectAsState(initial = 0.0)
    val collectionsBase by container.db.salesRepresentativeDao().observeCollectionsBase(rep.id).collectAsState(initial = 0.0)
    val returnsBase by container.db.salesRepresentativeDao().observeReturnsBase(rep.id).collectAsState(initial = 0.0)
    val earned by container.db.salesRepresentativeDao().observeCommissionEarnedBase(rep.id).collectAsState(initial = 0.0)
    val reversed by container.db.salesRepresentativeDao().observeCommissionReversedBase(rep.id).collectAsState(initial = 0.0)
    val paid by container.db.salesRepresentativeDao().observeCommissionPaidBase(rep.id).collectAsState(initial = 0.0)
    val due = max(0.0, earned - reversed - paid)
    var tab by remember { mutableIntStateOf(0) }
    var showEdit by remember { mutableStateOf(false) }
    var showAssignCustomer by remember { mutableStateOf(false) }
    var showPayment by remember { mutableStateOf(false) }
    var reverseVoucher by remember { mutableStateOf<PartyVoucherEntity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val tabs = listOf("الملف", "العملاء", "المبيعات", "العمولات", "السندات", "التدقيق")

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onBack) { Text("رجوع") }
                    Column(Modifier.weight(1f)) {
                        Text(rep.fullNameAr, style = MaterialTheme.typography.headlineSmall)
                        Text("${rep.code} • ${if (rep.repType == "INTERNAL") "مندوب داخلي" else "مندوب خارجي"} • ${rep.territory.ifBlank { "بدون منطقة" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FushStatusPill(if (rep.status == "ACTIVE") "نشط" else "موقوف", if (rep.status == "ACTIVE") FushStatusTone.Success else FushStatusTone.Warning)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showEdit = true }, modifier = Modifier.weight(1f)) { Text("تعديل") }
                    OutlinedButton(onClick = { showAssignCustomer = true }, enabled = rep.status == "ACTIVE", modifier = Modifier.weight(1f)) { Text("ربط عميل") }
                    Button(onClick = { showPayment = true }, enabled = rep.status == "ACTIVE" && due > 0.000001, modifier = Modifier.weight(1f)) { Text("صرف عمولة") }
                }
                FushOperationMessage(message, onConsumed = { message = null })
            }
        }
        }

        item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(
                    "العمولة المستحقة",
                    "${repMoney(due)} ر.ي",
                    Modifier.weight(1f),
                    helper = "المكتسب - المعكوس - المصروف",
                    tone = if (due > 0.000001) FushStatusTone.Warning else FushStatusTone.Success
                )
                FushMetricCard("المبيعات", "${repMoney(salesBase)} ر.ي", Modifier.weight(1f), helper = "إجمالي فواتير المندوب", tone = FushStatusTone.Info)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard("التحصيلات", "${repMoney(collectionsBase)} ر.ي", Modifier.weight(1f), helper = "تحصيلات فواتير المندوب", tone = FushStatusTone.Success)
                FushMetricCard("المرتجعات", "${repMoney(returnsBase)} ر.ي", Modifier.weight(1f), helper = "تؤثر على العمولة", tone = if (returnsBase > 0.000001) FushStatusTone.Warning else FushStatusTone.Neutral)
            }
        }
        }

        item {
        ScrollableTabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 10.dp)) {
            tabs.forEachIndexed { i, title -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) }) }
        }
        }
        when (tab) {
                0 -> {
                    item { FushSectionHeader("بيانات المندوب", "الهوية، نوع الارتباط، النطاق ونسبة العمولة الافتراضية") }
                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("الكود: ${rep.code}")
                                Text("النوع: ${if (rep.repType == "INTERNAL") "مندوب داخلي مرتبط بموظف" else "مندوب خارجي"}")
                                Text("الهاتف: ${rep.phone.ifBlank { "—" }}")
                                Text("المنطقة/النطاق: ${rep.territory.ifBlank { "—" }}")
                                Text("نسبة العمولة الافتراضية: ${repMoney(rep.commissionRatePct)}%")
                                if (rep.notes.isNotBlank()) Text("ملاحظات: ${rep.notes}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item { FushSectionHeader("مؤشرات العمولة", "توضح دورة الاستحقاق من الاكتساب حتى الصرف") }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FushMetricCard("المكتسبة", "${repMoney(earned)} ر.ي", Modifier.weight(1f), tone = FushStatusTone.Info)
                            FushMetricCard("المعكوسة", "${repMoney(reversed)} ر.ي", Modifier.weight(1f), tone = if (reversed > 0.000001) FushStatusTone.Warning else FushStatusTone.Neutral)
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FushMetricCard("المصروفة", "${repMoney(paid)} ر.ي", Modifier.weight(1f), tone = FushStatusTone.Success)
                            FushMetricCard("المتبقية", "${repMoney(due)} ر.ي", Modifier.weight(1f), tone = if (due > 0.000001) FushStatusTone.Warning else FushStatusTone.Success)
                        }
                    }
                }
                1 -> {
                    item { FushSectionHeader("العملاء التابعون", "${customers.size} عميل مرتبط بالمندوب") }
                    if (customers.isEmpty()) item { FushInlineState("لا يوجد عملاء مرتبطون بهذا المندوب.") }
                    items(customers, key = { "rep-customer-${it.id}" }) { c ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(c.nameAr, style = MaterialTheme.typography.titleMedium)
                                Text("${c.code} • ${c.province} • ${c.phone.ifBlank { "بدون هاتف" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                2 -> {
                    item { FushSectionHeader("فواتير المبيعات", "الفواتير المرتبطة بهوية المندوب الثابتة") }
                    if (invoices.isEmpty()) item { FushInlineState("لا توجد فواتير مرتبطة بهذا المندوب.") }
                    items(invoices, key = { "rep-invoice-${it.id}" }) { inv ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(inv.invoiceNo, style = MaterialTheme.typography.titleMedium)
                                Text("${repDate(inv.invoiceDate)} • ${repMoney(inv.totalBase)} ريال")
                                Text("${inv.salesRepNameSnapshot.ifBlank { rep.fullNameAr }} • نسبة ${repMoney(inv.salesRepRatePct)}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                3 -> {
                    item { FushSectionHeader("سجل العمولات", "العمولة تستحق بعد التحصيل وتُخفض بالعكس أو المرتجع حسب قواعد النظام") }
                    item {
                        FushMetricCard(
                            "المستحق الحالي",
                            "${repMoney(due)} ر.ي",
                            Modifier.fillMaxWidth(),
                            helper = "جاهز للصرف عبر سند عمولة مرتبط بالمندوب",
                            tone = if (due > 0.000001) FushStatusTone.Warning else FushStatusTone.Success
                        )
                    }
                    if (commissions.isEmpty()) item { FushInlineState("لا توجد عمولات مكتسبة بعد. العمولة تستحق بعد التحصيل.") }
                    items(commissions, key = { "rep-commission-${it.id}" }) { c ->
                        val net = c.earnedBase - c.reversedBase
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("فاتورة #${c.invoiceId}", style = MaterialTheme.typography.titleMedium)
                                    FushStatusPill(c.status, if (net > 0.000001) FushStatusTone.Success else FushStatusTone.Warning)
                                }
                                Text("${repDate(c.createdAt)} • صافي العمولة ${repMoney(net)} ريال", style = MaterialTheme.typography.titleSmall)
                                Text("مكتسب ${repMoney(c.earnedBase)} • معكوس ${repMoney(c.reversedBase)} • النسبة ${repMoney(c.ratePct)}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                4 -> {
                    item { FushSectionHeader("سندات عمولة المندوب", "كل سند مرتبط بالمندوب؛ السند المرحل يُصحح بالعكس ولا يُحذف") }
                    if (vouchers.isEmpty()) item { FushInlineState("لا توجد سندات مرتبطة بهذا المندوب.") }
                    items(vouchers, key = { "rep-voucher-${it.id}" }) { v ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(v.voucherNo, style = MaterialTheme.typography.titleMedium)
                                    FushStatusPill(v.status, if (v.status == "POSTED") FushStatusTone.Success else FushStatusTone.Warning)
                                }
                                Text("${repDate(v.voucherDate)} • ${if (v.voucherType == "PAYMENT") "سند صرف" else "سند قبض"} • ${repMoney(v.amountBase)} ريال")
                                Text(v.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (v.status == "POSTED") OutlinedButton(onClick = { reverseVoucher = v }) { Text("عكس السند") }
                            }
                        }
                    }
                }
                else -> {
                    item { FushSectionHeader("سجل التدقيق", "الأحداث والتعديلات المرتبطة بملف المندوب") }
                    if (audits.isEmpty()) item { FushInlineState("لا توجد أحداث تدقيق.") }
                    items(audits, key = { "rep-audit-${it.id}" }) { a ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("${repDateTime(a.eventAt)} • ${a.action}", style = MaterialTheme.typography.titleSmall)
                                if (a.reason.isNotBlank()) Text(a.reason)
                                if (a.newValue.isNotBlank()) Text(a.newValue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
    }

    if (showEdit) {
        EditSalesRepDialog(rep, onDismiss = { showEdit = false }) { name, en, phone, territory, rate, status, notes ->
            scope.launch {
                try {
                    container.salesRepresentativeService.update(rep.id, name, en, phone, territory, rate, status, notes, user.id)
                    message = "تم تحديث بيانات المندوب"
                    showEdit = false
                } catch (e: Exception) { message = e.message ?: "تعذر تحديث المندوب" }
            }
        }
    }

    if (showAssignCustomer) {
        AssignCustomerToRepDialog(allCustomers, rep, onDismiss = { showAssignCustomer = false }) { customer ->
            scope.launch {
                try {
                    val result = container.salesRepresentativeService.assignCustomerWithHistory(
                        customerId = customer.id,
                        salesRepId = rep.id,
                        updatedBy = user.id
                    )
                    message = if (result.invoicesLinked > 0 || result.commissionsLinked > 0) {
                        "تم ربط العميل ${customer.nameAr} بالمندوب وربط ${result.invoicesLinked} فاتورة سابقة و${result.commissionsLinked} حركة عمولة"
                    } else {
                        "تم ربط العميل ${customer.nameAr} بالمندوب، ولا توجد مبيعات سابقة غير مرتبطة"
                    }
                    showAssignCustomer = false
                } catch (e: Exception) { message = e.message ?: "تعذر ربط العميل" }
            }
        }
    }

    if (showPayment) {
        SalesRepCommissionPaymentDialog(container, rep, due, onDismiss = { showPayment = false }) { request ->
            scope.launch {
                try {
                    val id = container.accountingService.postVoucher(request.copy(createdBy = user.id))
                    message = "تم صرف العمولة وترحيل القيد رقم $id"
                    showPayment = false
                } catch (e: Exception) { message = e.message ?: "تعذر صرف العمولة" }
            }
        }
    }

    reverseVoucher?.let { v ->
        SalesRepReverseVoucherDialog(v, onDismiss = { reverseVoucher = null }) { reason ->
            scope.launch {
                try {
                    val id = container.accountingService.reverseEntry(v.journalEntryId, reason, user.id)
                    message = "تم عكس السند وإنشاء القيد $id"
                    reverseVoucher = null
                } catch (e: Exception) { message = e.message ?: "تعذر عكس السند" }
            }
        }
    }
}

@Composable
private fun RepMetric(label: String, value: Double) {
    ElevatedCard(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text("${repMoney(value)} ريال") } }
}

@Composable
private fun AddSalesRepDialog(
    employees: List<EmployeeEntity>,
    onDismiss: () -> Unit,
    onSave: (String, EmployeeEntity?, String, String, String, String, Double, String) -> Unit
) {
    var type by remember { mutableStateOf("INTERNAL") }
    var employee by remember { mutableStateOf<EmployeeEntity?>(null) }
    var name by remember { mutableStateOf("") }
    var nameEn by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var territory by remember { mutableStateOf("تعز") }
    var rate by remember { mutableStateOf("10") }
    var notes by remember { mutableStateOf("") }
    LaunchedEffect(type, employees) { if (type == "INTERNAL" && employee == null) employee = employees.firstOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مندوب مبيعات") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                RepStringSelection("نوع المندوب", type, listOf("INTERNAL", "EXTERNAL"), { if (it == "INTERNAL") "داخلي — موظف" else "خارجي" }) { type = it; if (it == "EXTERNAL") employee = null }
                if (type == "INTERNAL") {
                    RepSelection("الموظف", employee?.let { "${it.code} — ${it.fullNameAr}" } ?: "اختر", employees, { "${it.code} — ${it.fullNameAr} • ${it.jobTitle}" }) { employee = it; name = it.fullNameAr; phone = it.phone }
                }
                OutlinedTextField(name, { name = it }, label = { Text("اسم المندوب") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(nameEn, { nameEn = it }, label = { Text("الاسم الإنجليزي — اختياري") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("الهاتف") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(territory, { territory = it }, label = { Text("المنطقة / نطاق العمل") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(rate, { rate = it }, label = { Text("نسبة العمولة %") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
                Text("كود المندوب ينشئه النظام تلقائياً.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = { Button(enabled = name.isNotBlank() && rate.toDoubleOrNull()?.let { it in 0.0..100.0 } == true && (type == "EXTERNAL" || employee != null), onClick = { onSave(type, employee, name, nameEn, phone, territory, rate.toDouble(), notes) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun EditSalesRepDialog(rep: SalesRepresentativeEntity, onDismiss: () -> Unit, onSave: (String, String, String, String, Double, String, String) -> Unit) {
    var name by remember { mutableStateOf(rep.fullNameAr) }; var en by remember { mutableStateOf(rep.fullNameEn) }; var phone by remember { mutableStateOf(rep.phone) }; var territory by remember { mutableStateOf(rep.territory) }; var rate by remember { mutableStateOf(rep.commissionRatePct.toString()) }; var status by remember { mutableStateOf(rep.status) }; var notes by remember { mutableStateOf(rep.notes) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تعديل المندوب") }, text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        OutlinedTextField(name,{name=it},label={Text("الاسم")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(en,{en=it},label={Text("English")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(phone,{phone=it},label={Text("الهاتف")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(territory,{territory=it},label={Text("المنطقة")},modifier=Modifier.fillMaxWidth()); OutlinedTextField(rate,{rate=it},label={Text("العمولة %")},modifier=Modifier.fillMaxWidth()); RepStringSelection("الحالة",status,listOf("ACTIVE","INACTIVE"),{if(it=="ACTIVE")"نشط" else "موقوف"}){status=it}; OutlinedTextField(notes,{notes=it},label={Text("ملاحظات")},modifier=Modifier.fillMaxWidth())
    } }, confirmButton = { Button(enabled=name.isNotBlank()&&rate.toDoubleOrNull()?.let{it in 0.0..100.0}==true,onClick={onSave(name,en,phone,territory,rate.toDouble(),status,notes)}){Text("حفظ")} }, dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable
private fun AssignCustomerToRepDialog(customers: List<CustomerEntity>, rep: SalesRepresentativeEntity, onDismiss: () -> Unit, onSave: (CustomerEntity) -> Unit) {
    var customer by remember { mutableStateOf<CustomerEntity?>(null) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("ربط عميل بـ ${rep.fullNameAr}")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){RepSelection("العميل",customer?.let{"${it.code} — ${it.nameAr}"}?:"اختر العميل",customers,{"${it.code} — ${it.nameAr} • ${it.province}"}){customer=it}; customer?.salesRepName?.takeIf{it.isNotBlank()}?.let{Text("المندوب الحالي: $it",style=MaterialTheme.typography.bodySmall)}; Text("سيتم أيضًا ربط الفواتير السابقة لهذا العميل التي لا يوجد عليها مندوب، وربط عمولاتها السابقة بالمندوب. لن يتم تغيير أي فاتورة مرتبطة أصلًا بمندوب آخر.",style=MaterialTheme.typography.bodySmall)}},confirmButton={Button(enabled=customer!=null,onClick={onSave(customer!!)}){Text("ربط المبيعات السابقة")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable
private fun SalesRepCommissionPaymentDialog(container: AppContainer, rep: SalesRepresentativeEntity, due: Double, onDismiss: () -> Unit, onSave: (AccountingService.VoucherRequest) -> Unit) {
    val treasury by container.db.accountingDao().observeTreasuryBalances().collectAsState(initial = emptyList())
    val accounts by container.db.accountDao().observeAll().collectAsState(initial = emptyList())
    var source by remember { mutableStateOf<TreasuryBalanceRow?>(null) }; var amount by remember { mutableStateOf(repMoney(due)) }; var rate by remember { mutableStateOf("1") }; var description by remember { mutableStateOf("صرف عمولة مندوب المبيعات ${rep.fullNameAr}") }; var reference by remember { mutableStateOf("") }
    LaunchedEffect(treasury) { if (source == null) source = treasury.firstOrNull() }
    LaunchedEffect(source?.currencyCode) { if (source?.currencyCode == "YER_NEW") rate = "1" }
    val commissionAccount = accounts.firstOrNull { it.code == "2300" }
    val amountValue = amount.toDoubleOrNull()
    val rateValue = rate.toDoubleOrNull()
    val amountBase = if (amountValue != null && rateValue != null) amountValue * rateValue else null
    val canPost = source != null && commissionAccount != null && amountValue != null && amountValue > 0.0 &&
        rateValue != null && rateValue > 0.0 && amountBase != null && amountBase <= due + 0.01 && description.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("صرف عمولة — ${rep.fullNameAr}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("المستحق الحالي: ${repMoney(due)} ريال")
                RepSelection("الخزينة / البنك", source?.nameAr ?: "اختر", treasury, { "${it.nameAr} • ${it.currencyCode}" }) { source = it }
                OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(rate, { rate = it }, label = { Text("سعر الصرف") }, modifier = Modifier.fillMaxWidth())
                amountBase?.let { Text("القيمة الأساسية: ${repMoney(it)} ريال", style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(description, { description = it }, label = { Text("البيان") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(reference, { reference = it }, label = { Text("المرجع — اختياري") }, modifier = Modifier.fillMaxWidth())
                if (commissionAccount == null) Text("حساب عمولات البيع 2300 غير موجود", color = MaterialTheme.colorScheme.error)
                if (amountBase != null && amountBase > due + 0.01) Text("المبلغ يتجاوز العمولة المستحقة", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                enabled = canPost,
                onClick = {
                    onSave(
                        AccountingService.VoucherRequest(
                            type = "PAYMENT",
                            treasuryAccountId = source!!.id,
                            offsetAccountId = commissionAccount!!.id,
                            amountOriginal = amountValue!!,
                            currencyCode = source!!.currencyCode,
                            exchangeRate = rateValue!!,
                            description = description,
                            referenceNo = reference,
                            createdBy = 0L,
                            salesRepId = rep.id
                        )
                    )
                }
            ) { Text("ترحيل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun SalesRepReverseVoucherDialog(voucher: PartyVoucherEntity, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss,title={Text("عكس ${voucher.voucherNo}")},text={Column{Text("سيبقى السند الأصلي محفوظاً وسيُنشأ قيد عكسي.");OutlinedTextField(reason,{reason=it},label={Text("سبب العكس")},modifier=Modifier.fillMaxWidth())}},confirmButton={Button(enabled=reason.isNotBlank(),onClick={onSave(reason)}){Text("عكس")}},dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> RepSelection(label: String, current: String, options: List<T>, optionLabel: (T) -> String, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={expanded=!expanded}) { OutlinedTextField(current,{},readOnly=true,label={Text(label)},trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)},modifier=Modifier.menuAnchor().fillMaxWidth());ExposedDropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){options.forEach{item->DropdownMenuItem(text={Text(optionLabel(item))},onClick={onSelected(item);expanded=false})}} }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepStringSelection(label:String,current:String,options:List<String>,optionLabel:(String)->String,onSelected:(String)->Unit){RepSelection(label,optionLabel(current),options,optionLabel,onSelected)}

private fun repMoney(v: Double): String = if (kotlin.math.abs(v-v.toLong()) < 0.000001) v.toLong().toString() else "%.2f".format(Locale.US,v)
private fun repDate(v: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(v))
private fun repDateTime(v: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(v))
