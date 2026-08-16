package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.*
import com.fush.erp.ui.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EmployeesScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val employees by container.db.employeeDao().observeEmployees().collectAsState(initial = emptyList())
    val courses by container.db.employeeDao().observeActiveCourses().collectAsState(initial = emptyList())
    val trainings by container.db.employeeDao().observeTrainingSummaries().collectAsState(initial = emptyList())
    val authorizations by container.db.employeeDao().observeAuthorizationSummaries().collectAsState(initial = emptyList())
    val assets by container.db.maintenanceDao().observeAssets().collectAsState(initial = emptyList())
    val orders by container.db.productionDao().observeOrderSummaries().collectAsState(initial = emptyList())
    var showEmployee by remember { mutableStateOf(false) }
    var editEmployee by remember { mutableStateOf<EmployeeEntity?>(null) }
    var showCourse by remember { mutableStateOf(false) }
    var showTraining by remember { mutableStateOf(false) }
    var showAuthorization by remember { mutableStateOf(false) }
    var showAssignment by remember { mutableStateOf(false) }
    var revoke by remember { mutableStateOf<EquipmentAuthorizationSummary?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var selectedEmployeeId by remember { mutableStateOf<Long?>(null) }
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }
    val now = System.currentTimeMillis()

    val selectedEmployee = employees.firstOrNull { it.id == selectedEmployeeId }
    if (selectedEmployee != null) {
        EmployeeProfileScreen(
            container = container,
            user = user,
            employee = selectedEmployee,
            onBack = { selectedEmployeeId = null },
            modifier = modifier
        )
        return
    }

    val activeCount = employees.count { it.status == "ACTIVE" }
    val productionCount = employees.count { it.department == "الإنتاج" && it.status == "ACTIVE" }
    val expiredTrainingCount = trainings.count { it.expiresAt != null && it.expiresAt < now }
    val authorizationRiskCount = authorizations.count { it.status != "ACTIVE" || (it.expiresAt != null && it.expiresAt < now) }
    val competenceAlerts = expiredTrainingCount + authorizationRiskCount
    val filteredEmployees = remember(employees, search, statusFilter) {
        val q = search.trim().lowercase(Locale.ROOT)
        employees.filter { employee ->
            val statusMatches = statusFilter == "ALL" || employee.status == statusFilter
            val searchMatches = q.isBlank() || listOf(
                employee.code,
                employee.fullNameAr,
                employee.fullNameEn,
                employee.jobTitle,
                employee.department,
                employee.phone
            ).any { it.lowercase(Locale.ROOT).contains(q) }
            statusMatches && searchMatches
        }
    }

    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            FushSectionHeader(
                title = "الموظفون والموارد البشرية",
                subtitle = "ملف تشغيلي موحد للموظف: الوظيفة، التدريب، تصاريح المعدات، أوامر الإنتاج، المستحقات والسندات."
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard("إجمالي الموظفين", employees.size.toString(), Modifier.weight(1f), helper = "كل الملفات")
                FushMetricCard("الموظفون النشطون", activeCount.toString(), Modifier.weight(1f), helper = "متاحون للتشغيل", tone = FushStatusTone.Success)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard("فريق الإنتاج", productionCount.toString(), Modifier.weight(1f), helper = "نشط في قسم الإنتاج", tone = FushStatusTone.Info)
                FushMetricCard(
                    "تنبيهات الكفاءة",
                    competenceAlerts.toString(),
                    Modifier.weight(1f),
                    helper = "تدريب أو تصريح يحتاج مراجعة",
                    tone = if (competenceAlerts > 0) FushStatusTone.Warning else FushStatusTone.Success
                )
            }
        }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FushSectionHeader("إجراءات الموارد البشرية", "أنشئ ملف الموظف ثم أكمل التدريب والتصريح وربط التشغيل عند الحاجة.")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showEmployee = true }, modifier = Modifier.weight(1f)) { Text("موظف جديد") }
                        OutlinedButton(onClick = { showCourse = true }, modifier = Modifier.weight(1f)) { Text("برنامج تدريب") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showTraining = true },
                            enabled = employees.isNotEmpty() && courses.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("تسجيل تدريب") }
                        OutlinedButton(
                            onClick = { showAuthorization = true },
                            enabled = employees.isNotEmpty() && courses.isNotEmpty() && assets.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("تصريح معدة") }
                    }
                    Button(
                        onClick = { showAssignment = true },
                        enabled = employees.isNotEmpty() && orders.any { it.status !in setOf("CLOSED", "REJECTED", "CANCELLED") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تعيين موظف إنتاج / مشغل لأمر") }
                    FushOperationMessage(message, onConsumed = { message = null })
                }
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("بحث بالاسم أو الكود أو القسم أو المسمى") },
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

        item { FushSectionHeader("دليل الموظفين", "${filteredEmployees.size} ملف مطابق للبحث والتصفية") }
        if (filteredEmployees.isEmpty()) item {
            FushEmptyState(
                title = if (employees.isEmpty()) "لا توجد ملفات موظفين بعد" else "لا توجد نتائج مطابقة",
                detail = if (employees.isEmpty()) "أضف أول موظف لبدء إدارة التدريب والتصاريح ومستحقات الإنتاج." else "غيّر البحث أو مرشح الحالة لعرض موظفين آخرين.",
            )
        }
        items(filteredEmployees, key = { it.id }) { employee ->
            ElevatedCard(onClick = { selectedEmployeeId = employee.id }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(employee.fullNameAr, style = MaterialTheme.typography.titleMedium)
                            Text("${employee.code} • ${employee.jobTitle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FushStatusPill(
                            if (employee.status == "ACTIVE") "نشط" else "موقوف",
                            if (employee.status == "ACTIVE") FushStatusTone.Success else FushStatusTone.Warning
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("القسم: ${employee.department}", style = MaterialTheme.typography.bodyMedium)
                        employee.phone.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { editEmployee = employee }) { Text("تعديل") }
                        TextButton(onClick = { selectedEmployeeId = employee.id }) { Text("فتح الملف") }
                    }
                }
            }
        }

        item { FushSectionHeader("التدريب والكفاءة", "آخر سجلات التدريب مع إبراز السجلات المنتهية") }
        if (trainings.isEmpty()) item {
            FushEmptyState("لا توجد سجلات تدريب", "ستظهر هنا الدورات المكتملة ونتائج الكفاءة عند تسجيلها.")
        }
        items(trainings.take(30)) { row ->
            val expired = row.expiresAt != null && row.expiresAt < now
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${row.employeeName} — ${row.courseTitle}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        FushStatusPill(if (expired) "منتهي" else row.result, if (expired || row.result != "PASS") FushStatusTone.Warning else FushStatusTone.Success)
                    }
                    Text("ملاحظة عملية: ${if (row.practicalObserved) "نعم" else "لا"}", style = MaterialTheme.typography.bodySmall)
                    Text("أكمل: ${fmtDate(row.completedAt)} • الصلاحية: ${row.expiresAt?.let(::fmtDate) ?: "مفتوحة"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { FushSectionHeader("تصاريح تشغيل المعدات", "التصريح النشط يربط الموظف بالمعدة والتدريب المؤهل") }
        if (authorizations.isEmpty()) item {
            FushEmptyState("لا توجد تصاريح تشغيل", "أصدر تصريحًا بعد التحقق من التدريب والكفاءة المطلوبة للمعدة.")
        }
        items(authorizations.take(30)) { row ->
            val expired = row.expiresAt != null && row.expiresAt < now
            val active = row.status == "ACTIVE" && !expired
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${row.employeeName} — ${row.assetName}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        FushStatusPill(if (active) "ساري" else if (expired) "منتهي" else row.status, if (active) FushStatusTone.Success else FushStatusTone.Warning)
                    }
                    Text("${row.authorizationNo} • تدريب: ${row.courseTitle}", style = MaterialTheme.typography.bodySmall)
                    Text("الصلاحية: ${row.expiresAt?.let(::fmtDate) ?: "حتى الإلغاء"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (row.status == "ACTIVE") TextButton(onClick = { revoke = row }) { Text("إلغاء التصريح") }
                }
            }
        }
    }

    if (showEmployee) AddEmployeeDialog(onDismiss = { showEmployee = false }) { code, name, title, department, phone, notes ->
        scope.launch {
            try {
                container.employeeService.createEmployee(code, name, title, department, phone, notes, user.id)
                message = "تمت إضافة الموظف"
                showEmployee = false
            } catch (e: Exception) { message = e.message ?: "تعذر إضافة الموظف" }
        }
    }

    editEmployee?.let { employee ->
        EditEmployeeDialog(employee, onDismiss = { editEmployee = null }) { name, en, title, department, phone, status, notes ->
            scope.launch {
                try {
                    container.employeeService.updateEmployee(employee.id, name, en, title, department, phone, status, notes, user.id)
                    message = "تم تحديث ملف الموظف ${employee.code}"
                    editEmployee = null
                } catch (e: Exception) { message = e.message ?: "تعذر تحديث الموظف" }
            }
        }
    }

    if (showCourse) AddCourseDialog(onDismiss = { showCourse = false }) { code, title, category, assetType, description, practical ->
        scope.launch {
            try {
                container.employeeService.createCourse(code, title, category, assetType, description, practical, user.id)
                message = "تمت إضافة برنامج التدريب"
                showCourse = false
            } catch (e: Exception) { message = e.message ?: "تعذر إضافة التدريب" }
        }
    }

    if (showTraining) RecordTrainingDialog(employees.filter { it.status == "ACTIVE" }, courses, onDismiss = { showTraining = false }) { employee, course, result, practical, days, trainer, cert, notes ->
        scope.launch {
            try {
                container.employeeService.recordTraining(employee.id, course.id, result, practical, days, trainer, cert, notes, user.id)
                message = "تم تسجيل تدريب ${employee.fullNameAr}"
                showTraining = false
            } catch (e: Exception) { message = e.message ?: "تعذر تسجيل التدريب" }
        }
    }

    if (showAuthorization) AuthorizationDialog(employees.filter { it.status == "ACTIVE" }, assets, courses, onDismiss = { showAuthorization = false }) { employee, asset, course, days, notes ->
        scope.launch {
            try {
                container.employeeService.authorizeEquipment(employee.id, asset.id, course.id, days, notes, user.id)
                message = "تم إصدار تصريح تشغيل للمعدة"
                showAuthorization = false
            } catch (e: Exception) { message = e.message ?: "تعذر إصدار التصريح" }
        }
    }

    if (showAssignment) AssignOperatorDialog(
        employees.filter { it.status == "ACTIVE" },
        orders.filter { it.status !in setOf("CLOSED", "REJECTED", "CANCELLED") },
        onDismiss = { showAssignment = false }
    ) { employee, order ->
        scope.launch {
            try {
                container.employeeService.assignOperator(order.id, employee.id, user.id)
                message = "تم تعيين ${employee.fullNameAr} موظف إنتاج للأمر ${order.orderNo} وربط استحقاق الأجور به"
                showAssignment = false
            } catch (e: Exception) { message = e.message ?: "تعذر تعيين المشغل" }
        }
    }

    revoke?.let { row ->
        RevokeAuthorizationDialog(row, onDismiss = { revoke = null }) { reason ->
            scope.launch {
                try {
                    container.employeeService.revokeAuthorization(row.id, reason, user.id)
                    message = "تم إلغاء التصريح ${row.authorizationNo}"
                    revoke = null
                } catch (e: Exception) { message = e.message ?: "تعذر إلغاء التصريح" }
            }
        }
    }
}

@Composable
private fun EmployeeProfileScreen(
    container: AppContainer,
    user: UserEntity,
    employee: EmployeeEntity,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val allTrainings by container.db.employeeDao().observeTrainingSummaries().collectAsState(initial = emptyList())
    val allAuthorizations by container.db.employeeDao().observeAuthorizationSummaries().collectAsState(initial = emptyList())
    val vouchers by container.db.partyDao().observeEmployeeVouchers(employee.id).collectAsState(initial = emptyList())
    val productionCompensations by container.db.employeeDao().observeProductionCompensations(employee.id).collectAsState(initial = emptyList())
    val productionLaborPayments by container.db.employeeDao().observeProductionLaborPayments(employee.id).collectAsState(initial = 0.0)
    val audits by container.db.governanceDao().observeAuditEventsForEntity("EMPLOYEE", employee.id.toString()).collectAsState(initial = emptyList())
    val trainings = remember(allTrainings, employee.id) { allTrainings.filter { it.employeeId == employee.id } }
    val authorizations = remember(allAuthorizations, employee.id) { allAuthorizations.filter { it.employeeId == employee.id } }
    var tab by remember { mutableIntStateOf(0) }
    var showEdit by remember { mutableStateOf(false) }
    var reverseVoucher by remember { mutableStateOf<PartyVoucherEntity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val now = System.currentTimeMillis()
    val tabs = listOf("الملف", "التدريب", "التصاريح", "الإنتاج", "السندات", "التدقيق")
    val accruedProductionCompensation = productionCompensations.filter { it.isAccrued }.sumOf { it.laborCostBase }
    val outstandingProductionCompensation = (accruedProductionCompensation - productionLaborPayments).coerceAtLeast(0.0)
    val validTrainingCount = trainings.count { it.result == "PASS" && (it.expiresAt == null || it.expiresAt >= now) }
    val activeAuthorizationCount = authorizations.count { it.status == "ACTIVE" && (it.expiresAt == null || it.expiresAt >= now) }

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
                        Text(employee.fullNameAr, style = MaterialTheme.typography.headlineSmall)
                        Text("${employee.code} • ${employee.jobTitle} • ${employee.department}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FushStatusPill(
                        if (employee.status == "ACTIVE") "نشط" else "موقوف",
                        if (employee.status == "ACTIVE") FushStatusTone.Success else FushStatusTone.Warning
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showEdit = true }, modifier = Modifier.weight(1f)) { Text("تعديل الملف") }
                    OutlinedButton(onClick = { tab = 3 }, modifier = Modifier.weight(1f)) { Text("عرض المستحقات") }
                }
                FushOperationMessage(message, onConsumed = { message = null })
            }
        }
        }

        item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard(
                    "المتبقي المستحق",
                    "${employeeMoney(outstandingProductionCompensation)} ر.ي",
                    Modifier.weight(1f),
                    helper = "أجور إنتاج غير مصروفة",
                    tone = if (outstandingProductionCompensation > 0.000001) FushStatusTone.Warning else FushStatusTone.Success
                )
                FushMetricCard("إجمالي المستحق", "${employeeMoney(accruedProductionCompensation)} ر.ي", Modifier.weight(1f), helper = "مرحّل إلى 2200", tone = FushStatusTone.Info)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FushMetricCard("المدفوع", "${employeeMoney(productionLaborPayments)} ر.ي", Modifier.weight(1f), helper = "من حساب أجور الموظفين")
                FushMetricCard("الكفاءة السارية", validTrainingCount.toString(), Modifier.weight(1f), helper = "$activeAuthorizationCount تصريح معدات", tone = FushStatusTone.Success)
            }
        }
        }

        item {
        ScrollableTabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 10.dp)) {
            tabs.forEachIndexed { index, title -> Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) }) }
        }
        }
        when (tab) {
                0 -> {
                    item { FushSectionHeader("المعلومات الأساسية", "البيانات الوظيفية المعتمدة لهذا الموظف") }
                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("الكود: ${employee.code}")
                                Text("الاسم: ${employee.fullNameAr}")
                                if (employee.fullNameEn.isNotBlank()) Text("English: ${employee.fullNameEn}")
                                Text("المسمى الوظيفي: ${employee.jobTitle}")
                                Text("القسم: ${employee.department}")
                                Text("الهاتف: ${employee.phone.ifBlank { "—" }}")
                                Text("تاريخ التوظيف: ${fmtDate(employee.hireDate)}")
                                if (employee.notes.isNotBlank()) Text("ملاحظات: ${employee.notes}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        FushSectionHeader("ملخص الملف", "نظرة سريعة على النشاط والارتباطات")
                    }
                    item {
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("سجلات التدريب: ${trainings.size} • الساري: $validTrainingCount")
                                Text("تصاريح المعدات: ${authorizations.size} • النشط: $activeAuthorizationCount")
                                Text("أوامر الإنتاج المرتبطة: ${productionCompensations.size}")
                                Text("السندات المرتبطة: ${vouchers.size}")
                            }
                        }
                    }
                }
                1 -> {
                    item { FushSectionHeader("التدريب والكفاءة", "صلاحية الموظف للمهام تعتمد على التدريب ومدة سريانه") }
                    if (trainings.isEmpty()) item { FushInlineState("لا توجد سجلات تدريب لهذا الموظف.") }
                    items(trainings, key = { "employee-training-${it.id}" }) { row ->
                        val expired = row.expiresAt != null && row.expiresAt < now
                        val valid = row.result == "PASS" && !expired
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.courseTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    FushStatusPill(if (expired) "منتهي" else row.result, if (valid) FushStatusTone.Success else FushStatusTone.Warning)
                                }
                                Text("ملاحظة عملية: ${if (row.practicalObserved) "نعم" else "لا"}")
                                Text("أكمل: ${fmtDate(row.completedAt)} • الصلاحية: ${row.expiresAt?.let(::fmtDate) ?: "مفتوحة"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                2 -> {
                    item { FushSectionHeader("تصاريح المعدات", "الترخيص التشغيلي المرتبط بالمعدة والتدريب المؤهل") }
                    if (authorizations.isEmpty()) item { FushInlineState("لا توجد تصاريح تشغيل مرتبطة بهذا الموظف.") }
                    items(authorizations, key = { "employee-authorization-${it.id}" }) { row ->
                        val expired = row.expiresAt != null && row.expiresAt < now
                        val active = row.status == "ACTIVE" && !expired
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${row.authorizationNo} — ${row.assetName}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    FushStatusPill(if (active) "ساري" else if (expired) "منتهي" else row.status, if (active) FushStatusTone.Success else FushStatusTone.Warning)
                                }
                                Text("التدريب: ${row.courseTitle}")
                                Text("الإصدار: ${fmtDate(row.issuedAt)} • الانتهاء: ${row.expiresAt?.let(::fmtDate) ?: "مفتوح"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                3 -> {
                    item { FushSectionHeader("استحقاقات الإنتاج", "الاستحقاق ينشأ من أمر الإنتاج ويُرحّل إلى حساب 2200 قبل الصرف") }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FushMetricCard("المستحق", "${employeeMoney(accruedProductionCompensation)} ر.ي", Modifier.weight(1f), tone = FushStatusTone.Info)
                            FushMetricCard("المدفوع", "${employeeMoney(productionLaborPayments)} ر.ي", Modifier.weight(1f), tone = FushStatusTone.Success)
                        }
                    }
                    item {
                        FushMetricCard(
                            "المتبقي المطلوب تسويته",
                            "${employeeMoney(outstandingProductionCompensation)} ر.ي",
                            Modifier.fillMaxWidth(),
                            helper = "يُصرف بسند صرف على حساب 2200 مع اختيار الموظف نفسه",
                            tone = if (outstandingProductionCompensation > 0.000001) FushStatusTone.Warning else FushStatusTone.Success
                        )
                    }
                    if (productionCompensations.isEmpty()) item { FushInlineState("لا توجد أوامر إنتاج مرتبطة بهذا الموظف.") }
                    items(productionCompensations, key = { "employee-production-comp-${it.orderId}" }) { row ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.orderNo, style = MaterialTheme.typography.titleMedium)
                                    FushStatusPill(
                                        if (row.isAccrued) "مستحق" else "بانتظار الترحيل",
                                        if (row.isAccrued) FushStatusTone.Success else FushStatusTone.Warning
                                    )
                                }
                                Text("تاريخ التخطيط: ${fmtDate(row.plannedDate)} • حالة الأمر: ${row.orderStatus}")
                                Text("أجر/عمولة الدفعة: ${employeeMoney(row.laborCostBase)} ريال", style = MaterialTheme.typography.titleSmall)
                                Text(if (row.isAccrued) "تم ترحيل الاستحقاق إلى حساب 2200" else "لا يمكن اعتباره مستحقًا قبل ترحيل نتيجة الدفعة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                4 -> {
                    item { FushSectionHeader("السندات المرتبطة بالموظف", "السند المرحل لا يُحذف؛ أي تصحيح يتم بقيد عكسي مستقل") }
                    if (vouchers.isEmpty()) item { FushInlineState("لا توجد سندات مرتبطة بهذا الموظف.") }
                    items(vouchers, key = { "employee-voucher-${it.id}" }) { voucher ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(voucher.voucherNo, style = MaterialTheme.typography.titleMedium)
                                    FushStatusPill(voucher.status, if (voucher.status == "POSTED") FushStatusTone.Success else FushStatusTone.Warning)
                                }
                                Text("${fmtDate(voucher.voucherDate)} • ${if (voucher.voucherType == "PAYMENT") "سند صرف" else "سند قبض"} • ${employeeMoney(voucher.amountBase)} ريال")
                                Text(voucher.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (voucher.status == "POSTED") OutlinedButton(onClick = { reverseVoucher = voucher }) { Text("عكس السند") }
                            }
                        }
                    }
                }
                else -> {
                    item { FushSectionHeader("سجل التدقيق", "تاريخ التغييرات والإجراءات المرتبطة بملف الموظف") }
                    if (audits.isEmpty()) item { FushInlineState("لا توجد أحداث تدقيق لهذا الموظف.") }
                    items(audits, key = { "employee-audit-${it.id}" }) { audit ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("${employeeDateTime(audit.eventAt)} • ${audit.action}", style = MaterialTheme.typography.titleSmall)
                                if (audit.reason.isNotBlank()) Text(audit.reason)
                                if (audit.newValue.isNotBlank()) Text(audit.newValue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
    }

    if (showEdit) {
        EditEmployeeDialog(employee, onDismiss = { showEdit = false }) { name, nameEn, title, department, phone, status, notes ->
            scope.launch {
                try {
                    container.employeeService.updateEmployee(employee.id, name, nameEn, title, department, phone, status, notes, user.id)
                    message = "تم تحديث ملف الموظف"
                    showEdit = false
                } catch (e: Exception) { message = e.message ?: "تعذر تعديل الموظف" }
            }
        }
    }

    reverseVoucher?.let { voucher ->
        EmployeeReverseVoucherDialog(voucher, onDismiss = { reverseVoucher = null }) { reason ->
            scope.launch {
                try {
                    val entryId = container.accountingService.reverseEntry(voucher.journalEntryId, reason, user.id)
                    message = "تم عكس السند وإنشاء القيد $entryId"
                    reverseVoucher = null
                } catch (e: Exception) { message = e.message ?: "تعذر عكس السند" }
            }
        }
    }
}

@Composable
private fun EmployeeReverseVoucherDialog(voucher: PartyVoucherEntity, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("عكس ${voucher.voucherNo}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("سيبقى السند الأصلي محفوظاً ويُنشأ قيد عكسي مستقل.")
                OutlinedTextField(reason, { reason = it }, label = { Text("سبب العكس") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(enabled = reason.isNotBlank(), onClick = { onSave(reason) }) { Text("عكس") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

private fun employeeMoney(value: Double): String = if (kotlin.math.abs(value - value.toLong()) < 0.000001) value.toLong().toString() else "%.2f".format(Locale.US, value)
private fun employeeDateTime(value: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(value))

@Composable
private fun AddEmployeeDialog(onDismiss: () -> Unit, onSave: (String, String, String, String, String, String) -> Unit) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("الإنتاج") }
    var phone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إضافة موظف") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(code, { code = it }, label = { Text("كود الموظف — اختياري، يولد تلقائياً") }, singleLine = true)
            OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, singleLine = true)
            OutlinedTextField(title, { title = it }, label = { Text("المسمى الوظيفي") }, singleLine = true)
            HrStringSelectionField("القسم", department, listOf("الإنتاج", "الجودة", "المخازن", "الصيانة", "المبيعات", "المحاسبة", "الإدارة", "أخرى")) { department = it }
            OutlinedTextField(phone, { phone = it }, label = { Text("الهاتف") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") })
        }
    }, confirmButton = { Button(enabled = name.isNotBlank() && title.isNotBlank(), onClick = { onSave(code, name, title, department, phone, notes) }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
private fun EditEmployeeDialog(employee: EmployeeEntity, onDismiss: () -> Unit, onSave: (String, String, String, String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf(employee.fullNameAr) }
    var nameEn by remember { mutableStateOf(employee.fullNameEn) }
    var title by remember { mutableStateOf(employee.jobTitle) }
    var department by remember { mutableStateOf(employee.department) }
    var phone by remember { mutableStateOf(employee.phone) }
    var status by remember { mutableStateOf(employee.status) }
    var notes by remember { mutableStateOf(employee.notes) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تعديل ${employee.code}") }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(nameEn, { nameEn = it }, label = { Text("الاسم الإنجليزي — اختياري") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(title, { title = it }, label = { Text("المسمى الوظيفي") }, modifier = Modifier.fillMaxWidth())
            HrStringSelectionField("القسم", department, listOf("الإنتاج", "الجودة", "المخازن", "الصيانة", "المبيعات", "المحاسبة", "الإدارة", "أخرى")) { department = it }
            HrStringSelectionField("الحالة", status, listOf("ACTIVE", "INACTIVE")) { status = it }
            OutlinedTextField(phone, { phone = it }, label = { Text("الهاتف") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { Button(enabled = name.isNotBlank() && title.isNotBlank(), onClick = { onSave(name, nameEn, title, department, phone, status, notes) }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
private fun AddCourseDialog(onDismiss: () -> Unit, onSave: (String, String, String, String?, String, Boolean) -> Unit) {
    var code by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("EQUIPMENT") }
    var assetType by remember { mutableStateOf("GENERAL") }
    var description by remember { mutableStateOf("") }
    var practical by remember { mutableStateOf(true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("برنامج تدريب") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(code, { code = it }, label = { Text("الكود") }, singleLine = true)
            OutlinedTextField(title, { title = it }, label = { Text("عنوان التدريب") }, singleLine = true)
            HrStringSelectionField("الفئة", category, listOf("SAFETY", "PROCESS", "EQUIPMENT", "QUALITY", "SOP", "OTHER")) { category = it }
            HrStringSelectionField("نوع المعدة المرتبط", assetType, listOf("GENERAL", "FILLING_MACHINE", "BURNER", "MEASURING_TOOL", "SAFETY_EQUIPMENT", "OTHER")) { assetType = it }
            OutlinedTextField(description, { description = it }, label = { Text("الوصف/المحتوى") })
            Row { Checkbox(practical, { practical = it }); Text("يتطلب ملاحظة عملية قبل العمل المستقل") }
        }
    }, confirmButton = { Button(enabled = code.isNotBlank() && title.isNotBlank(), onClick = { onSave(code, title, category, assetType.takeUnless { it == "GENERAL" }, description, practical) }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
private fun RecordTrainingDialog(employees: List<EmployeeEntity>, courses: List<TrainingCourseEntity>, onDismiss: () -> Unit, onSave: (EmployeeEntity, TrainingCourseEntity, String, Boolean, Int?, String, String, String) -> Unit) {
    var employee by remember { mutableStateOf<EmployeeEntity?>(employees.firstOrNull()) }
    var course by remember { mutableStateOf<TrainingCourseEntity?>(courses.firstOrNull()) }
    var result by remember { mutableStateOf("PASS") }
    var practical by remember { mutableStateOf(true) }
    var daysText by remember { mutableStateOf("") }
    var trainer by remember { mutableStateOf("") }
    var cert by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تسجيل تدريب وكفاءة") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            HrSelectionField("الموظف", employee?.fullNameAr ?: "اختر", employees, { it.fullNameAr }) { employee = it }
            HrSelectionField("التدريب", course?.titleAr ?: "اختر", courses, { it.titleAr }) { course = it; practical = it.requiresPracticalObservation }
            HrStringSelectionField("النتيجة", result, listOf("PASS", "FAIL")) { result = it }
            Row { Checkbox(practical, { practical = it }); Text("اجتاز الملاحظة العملية") }
            OutlinedTextField(daysText, { daysText = it }, label = { Text("مدة الصلاحية بالأيام — اختياري") }, singleLine = true)
            OutlinedTextField(trainer, { trainer = it }, label = { Text("المدرب") }, singleLine = true)
            OutlinedTextField(cert, { cert = it }, label = { Text("مرجع الشهادة/السجل") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") })
        }
    }, confirmButton = {
        val days = daysText.toIntOrNull()
        Button(enabled = employee != null && course != null && (daysText.isBlank() || days?.let { it > 0 } == true), onClick = { onSave(employee!!, course!!, result, practical, days, trainer, cert, notes) }) { Text("تسجيل") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
private fun AuthorizationDialog(employees: List<EmployeeEntity>, assets: List<AssetEntity>, courses: List<TrainingCourseEntity>, onDismiss: () -> Unit, onSave: (EmployeeEntity, AssetEntity, TrainingCourseEntity, Int?, String) -> Unit) {
    var employee by remember { mutableStateOf<EmployeeEntity?>(employees.firstOrNull()) }
    var asset by remember { mutableStateOf<AssetEntity?>(assets.firstOrNull { it.assetType == "FILLING_MACHINE" } ?: assets.firstOrNull()) }
    var course by remember { mutableStateOf<TrainingCourseEntity?>(courses.firstOrNull { it.assetType == asset?.assetType }) }
    var daysText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تصريح تشغيل معدة") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("لن يصدر التصريح إذا لم يكن لدى الموظف تدريب ناجح وساري لنفس البرنامج.", style = MaterialTheme.typography.bodySmall)
            HrSelectionField("الموظف", employee?.fullNameAr ?: "اختر", employees, { it.fullNameAr }) { employee = it }
            HrSelectionField("المعدة", asset?.nameAr ?: "اختر", assets, { "${it.nameAr} (${it.status})" }) { selected -> asset = selected; course = courses.firstOrNull { it.assetType == selected.assetType } }
            val eligibleCourses = courses.filter { it.assetType == asset?.assetType }
            HrSelectionField("التدريب المطلوب", course?.titleAr ?: "لا يوجد تدريب مطابق", eligibleCourses, { it.titleAr }) { course = it }
            OutlinedTextField(daysText, { daysText = it }, label = { Text("مدة التصريح بالأيام — اختياري") }, singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") })
        }
    }, confirmButton = {
        val days = daysText.toIntOrNull()
        Button(enabled = employee != null && asset != null && course != null && (daysText.isBlank() || days?.let { it > 0 } == true), onClick = { onSave(employee!!, asset!!, course!!, days, notes) }) { Text("إصدار") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
private fun AssignOperatorDialog(employees: List<EmployeeEntity>, orders: List<ProductionOrderSummary>, onDismiss: () -> Unit, onSave: (EmployeeEntity, ProductionOrderSummary) -> Unit) {
    var employee by remember { mutableStateOf<EmployeeEntity?>(employees.firstOrNull()) }
    var order by remember { mutableStateOf<ProductionOrderSummary?>(orders.firstOrNull()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تعيين مشغل لأمر إنتاج") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("يرتبط أجر/عمولة أمر الإنتاج بموظف الإنتاج المحدد. وإذا كان للأمر معدة رئيسية يتحقق النظام أيضاً من التدريب والتصريح.")
            HrSelectionField("أمر الإنتاج", order?.orderNo ?: "اختر", orders, { "${it.orderNo} — ${it.status}" }) { order = it }
            HrSelectionField("موظف الإنتاج / المشغل", employee?.fullNameAr ?: "اختر", employees, { it.fullNameAr }) { employee = it }
        }
    }, confirmButton = { Button(enabled = employee != null && order != null, onClick = { onSave(employee!!, order!!) }) { Text("تعيين") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

@Composable
private fun RevokeAuthorizationDialog(row: EquipmentAuthorizationSummary, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("إلغاء ${row.authorizationNo}") }, text = { OutlinedTextField(reason, { reason = it }, label = { Text("سبب الإلغاء") }) }, confirmButton = { Button(enabled = reason.isNotBlank(), onClick = { onSave(reason) }) { Text("إلغاء التصريح") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("رجوع") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> HrSelectionField(label: String, current: String, values: List<T>, labelOf: (T) -> String, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(value = current, onValueChange = {}, readOnly = true, label = { Text(label) }, modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { value -> DropdownMenuItem(text = { Text(labelOf(value)) }, onClick = { onSelected(value); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HrStringSelectionField(label: String, current: String, values: List<String>, onSelected: (String) -> Unit) {
    HrSelectionField(label, current, values, { it }, onSelected)
}

private fun fmtDate(value: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(value))
