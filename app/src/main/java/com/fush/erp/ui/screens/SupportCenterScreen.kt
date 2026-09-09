package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.SupportFindingRow
import com.fush.erp.data.entity.SalesInvoiceEntity
import com.fush.erp.data.entity.SalesShipmentExpenseEntity
import com.fush.erp.data.entity.SupportSessionViewRow
import com.fush.erp.data.entity.UserEntity
import com.fush.erp.domain.BusinessTimeZone
import com.fush.erp.domain.SupportPolicy
import com.fush.erp.domain.TrustedTimeService
import com.fush.erp.ui.FushSearchableSelectionField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter

private data class CleanupShipmentExpenseOption(
    val expense: SalesShipmentExpenseEntity,
    val shipmentNo: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportCenterScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val tickets by container.db.supportDao().observeTickets().collectAsState(initial = emptyList())
    val sessions by container.db.supportDao().observeSessions().collectAsState(initial = emptyList())
    val installationBinding = remember { container.vendorSupportProvisioningManager.installationBinding() }
    val supportUsers by container.db.supportDao().observeSupportUsers(installationBinding).collectAsState(initial = emptyList())
    val vendorIdentity by container.db.supportDao().observeVendorIdentity(installationBinding).collectAsState(initial = null)
    val latestVendorIdentity by container.db.supportDao().observeLatestVendorIdentity().collectAsState(initial = null)
    val legacySupportUsers by container.db.supportDao().observeLegacySupportUsers().collectAsState(initial = emptyList())
    val latestVendorKey by container.db.supportDao().observeLatestVendorSupportKey().collectAsState(initial = null)
    val isAdmin = user.role == "ADMIN"
    val isSupport = user.role == SupportPolicy.SUPPORT_ROLE
    var clockNow by remember { mutableLongStateOf(TrustedTimeService.now()) }
    LaunchedEffect(Unit) {
        while (true) { delay(5_000L); clockNow = TrustedTimeService.now() }
    }
    val activeForUser = remember(sessions, user.id, clockNow) {
        sessions.filter {
            it.supportUserId == user.id && it.status == "ACTIVE" && it.revokedAt == null && it.closedAt == null &&
                it.expiresAt != Long.MAX_VALUE && it.expiresAt > clockNow &&
                it.expiresAt > it.startedAt && it.expiresAt - it.startedAt <= 1_440L * 60_000L
        }
    }

    var showNewTicket by remember { mutableStateOf(false) }
    var selectedSupportUser by remember { mutableStateOf<UserEntity?>(null) }
    var selectedSession by remember { mutableStateOf<SupportSessionViewRow?>(null) }
    var durationMinutes by remember { mutableLongStateOf(60L) }
    var reason by remember { mutableStateOf("") }
    var targetId by remember { mutableStateOf("") }
    var secondaryId by remember { mutableStateOf("") }
    var findings by remember { mutableStateOf<List<SupportFindingRow>>(emptyList()) }
    var resultMessage by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingActivation by remember { mutableStateOf<Triple<Long, Long, Long>?>(null) }
    var pendingAdminCleanupTicketId by remember { mutableStateOf<Long?>(null) }
    var provisioningRequest by remember { mutableStateOf("") }
    var signedProvisioningPackage by remember { mutableStateOf("") }
    var signedKeyRotationPackage by remember { mutableStateOf("") }
    var pendingProvisioningAction by remember { mutableStateOf<String?>(null) }
    var cleanupRefresh by remember { mutableIntStateOf(0) }
    var selectedCleanupInvoice by remember { mutableStateOf<SalesInvoiceEntity?>(null) }
    var selectedCleanupExpense by remember { mutableStateOf<CleanupShipmentExpenseOption?>(null) }

    val cleanupInvoices by produceState(
        initialValue = emptyList<SalesInvoiceEntity>(),
        key1 = isAdmin,
        key2 = selectedSession?.id,
        key3 = cleanupRefresh,
    ) {
        value = if (isAdmin && selectedSession != null) {
            container.db.salesDao().allInvoicesForCloudSync().sortedWith(compareByDescending<SalesInvoiceEntity> { it.invoiceDate }.thenByDescending { it.id })
        } else emptyList()
    }
    val cleanupShipmentExpenses by produceState(
        initialValue = emptyList<CleanupShipmentExpenseOption>(),
        key1 = isAdmin,
        key2 = selectedSession?.id,
        key3 = cleanupRefresh,
    ) {
        value = if (isAdmin && selectedSession != null) {
            container.db.shipmentDao().allExpensesForCloudSync()
                .sortedWith(compareByDescending<SalesShipmentExpenseEntity> { it.expenseDate }.thenByDescending { it.id })
                .map { expense ->
                    CleanupShipmentExpenseOption(
                        expense = expense,
                        shipmentNo = container.db.shipmentDao().shipmentById(expense.shipmentId)?.shipmentNo ?: "شحنة #${expense.shipmentId}",
                    )
                }
        } else emptyList()
    }

    fun runTargetCommand(block: suspend (Long) -> com.fush.erp.domain.SupportCommandResult) {
        val id = targetId.toLongOrNull()
        if (id == null) { resultMessage = "أدخل Record ID صحيحًا"; return }
        if (selectedSession == null) { resultMessage = "اختر Support Session"; return }
        scope.launch {
            busy = true
            runCatching { block(id) }
                .onSuccess { findings = it.findings; resultMessage = it.summary }
                .onFailure { resultMessage = it.message ?: "فشل الأمر" }
            busy = false
        }
    }

    LaunchedEffect(activeForUser) {
        if ((isSupport || isAdmin) && selectedSession?.id !in activeForUser.map { it.id }) selectedSession = activeForUser.firstOrNull()
    }

    val selectedAudit = if (selectedSession != null) {
        val rows by container.db.supportDao().observeAuditForSession(selectedSession!!.id).collectAsState(initial = emptyList())
        rows
    } else emptyList()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("FUSH Support Center", style = MaterialTheme.typography.headlineSmall)
            Text(
                "وصول الصيانة منفصل عن صلاحيات التشغيل. كل فحص/إصلاح يتطلب Ticket + Support Session فعالة ويسجل في سجل صيانة غير قابل للتعديل.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (isAdmin) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("FUSH Vendor Identity Lifecycle", style = MaterialTheme.typography.titleMedium)
                        when {
                            vendorIdentity != null && supportUsers.isNotEmpty() -> {
                                Text("Vendor Identity موثقة لهذا التثبيت ✅")
                                Text("${supportUsers.first().displayName} — ${supportUsers.first().username}")
                                Text("Credential v${vendorIdentity!!.credentialVersion} • ${vendorIdentity!!.lifecycleAction}", style = MaterialTheme.typography.bodySmall)
                            }
                            latestVendorIdentity != null && latestVendorIdentity!!.installationBinding != installationBinding -> {
                                Text("تم اكتشاف Vendor Identity تاريخية من جهاز/تثبيت آخر. الوصول Fail-Closed حتى Signed Rebind.")
                                Text("التاريخ القديم سيبقى محفوظًا؛ Rebind يضيف سجل Supersession جديد ولا يحذف السابق.", style = MaterialTheme.typography.bodySmall)
                            }
                            latestVendorIdentity == null && legacySupportUsers.isNotEmpty() -> {
                                Text("تم اكتشاف Legacy FUSH_SUPPORT بدون Vendor Identity ⚠️")
                                Text("الحساب محظور من الدخول حتى Signed Legacy Claim من FUSH. عدد الحسابات: ${legacySupportUsers.size}", style = MaterialTheme.typography.bodySmall)
                            }
                            else -> Text("Fresh Install: لا توجد Vendor Identity. يلزم Signed Provisioning Package من FUSH.")
                        }
                        Text("Signing key: ${latestVendorKey?.keyId ?: "fush-support-v1 (bootstrap)"}", style = MaterialTheme.typography.bodySmall)

                        if (vendorIdentity == null || supportUsers.isEmpty()) {
                            Button(onClick = {
                                scope.launch {
                                    if (container.securityService.hasRecentReauthentication(user.id)) {
                                        runCatching { container.securityService.createVendorSupportProvisioningChallenge(user.id) }
                                            .onSuccess { provisioningRequest = it.requestToken(); resultMessage = "تم إنشاء ${it.purpose} Challenge صالح لمدة 24 ساعة" }
                                            .onFailure { resultMessage = it.message ?: "تعذر إنشاء Lifecycle Challenge" }
                                    } else pendingProvisioningAction = "CHALLENGE_DEFAULT"
                                }
                            }) { Text("إنشاء Provision/Rebind/Legacy Challenge") }
                        } else {
                            Button(onClick = {
                                scope.launch {
                                    if (container.securityService.hasRecentReauthentication(user.id)) {
                                        runCatching { container.securityService.createVendorSupportProvisioningChallenge(user.id, com.fush.erp.domain.VendorLifecycleAction.ROTATE) }
                                            .onSuccess { provisioningRequest = it.requestToken(); resultMessage = "تم إنشاء Credential Rotation Challenge" }
                                            .onFailure { resultMessage = it.message ?: "تعذر إنشاء Rotation Challenge" }
                                    } else pendingProvisioningAction = "CHALLENGE_ROTATE"
                                }
                            }) { Text("تدوير بيانات اعتماد FUSH_SUPPORT — Signed") }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    if (container.securityService.hasRecentReauthentication(user.id)) {
                                        runCatching { container.securityService.createVendorSupportProvisioningChallenge(user.id, com.fush.erp.domain.VendorLifecycleAction.KEY_ROTATE) }
                                            .onSuccess { provisioningRequest = it.requestToken(); resultMessage = "تم إنشاء Vendor Key Rotation Challenge" }
                                            .onFailure { resultMessage = it.message ?: "تعذر إنشاء Key Rotation Challenge" }
                                    } else pendingProvisioningAction = "CHALLENGE_KEY"
                                }
                            }) { Text("تدوير مفتاح توقيع Vendor — Signed Supersession") }
                        }

                        if (provisioningRequest.isNotBlank()) {
                            SelectionContainer { Text(provisioningRequest, style = MaterialTheme.typography.bodySmall) }
                        }
                        OutlinedTextField(
                            value = signedProvisioningPackage,
                            onValueChange = { signedProvisioningPackage = it.trim() },
                            label = { Text("Signed Identity Lifecycle Package (FSP2)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                        )
                        Button(
                            enabled = signedProvisioningPackage.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    if (container.securityService.hasRecentReauthentication(user.id)) {
                                        runCatching { container.securityService.provisionVendorSupport(user.id, signedProvisioningPackage) }
                                            .onSuccess { resultMessage = "تم تطبيق Signed Vendor Lifecycle بنجاح"; signedProvisioningPackage = ""; provisioningRequest = "" }
                                            .onFailure { resultMessage = it.message ?: "رفض Signed Vendor Package" }
                                    } else pendingProvisioningAction = "IMPORT_IDENTITY"
                                }
                            }
                        ) { Text("تحقق وتطبيق Vendor Lifecycle") }

                        OutlinedTextField(
                            value = signedKeyRotationPackage,
                            onValueChange = { signedKeyRotationPackage = it.trim() },
                            label = { Text("Signed Vendor Key Rotation Package (FSK1)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                        )
                        OutlinedButton(
                            enabled = signedKeyRotationPackage.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    if (container.securityService.hasRecentReauthentication(user.id)) {
                                        runCatching { container.securityService.rotateVendorSupportSigningKey(user.id, signedKeyRotationPackage) }
                                            .onSuccess { resultMessage = "تم تدوير Vendor signing key إلى $it"; signedKeyRotationPackage = ""; provisioningRequest = "" }
                                            .onFailure { resultMessage = it.message ?: "رفض Key Rotation Package" }
                                    } else pendingProvisioningAction = "IMPORT_KEY"
                                }
                            }
                        ) { Text("تحقق وتطبيق Vendor Key Rotation") }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("إدارة صلاحية الصيانة", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { showNewTicket = true }) { Text("فتح Support Ticket") }
                        FushSearchableSelectionField(
                            label = "حساب FUSH_SUPPORT",
                            selectedText = selectedSupportUser?.let { "${it.displayName} — ${it.username}" }.orEmpty(),
                            options = supportUsers,
                            optionText = { "${it.displayName} — ${it.username}" },
                            searchTerms = { listOf(it.displayName, it.username) },
                            onSelected = { selectedSupportUser = it },
                            onCleared = { selectedSupportUser = null },
                            allowClear = true,
                            onClearSelected = { selectedSupportUser = null },
                        )
                        Text("مدة الجلسة")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(60L to "ساعة", 360L to "6 ساعات", 1_440L to "24 ساعة").forEach { (v, label) ->
                                FilterChip(selected = durationMinutes == v, onClick = { durationMinutes = v }, label = { Text(label) })
                            }
                        }
                    }
                }
            }
            item { Text("بلاغات الدعم", style = MaterialTheme.typography.titleMedium) }
            items(tickets, key = { "ticket-${it.id}" }) { ticket ->
                val activeSession = sessions.firstOrNull {
                    it.ticketId == ticket.id && it.status == "ACTIVE" && it.revokedAt == null && it.closedAt == null &&
                        it.expiresAt != Long.MAX_VALUE && it.expiresAt > clockNow &&
                        it.expiresAt > it.startedAt && it.expiresAt - it.startedAt <= 1_440L * 60_000L
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Ticket #${ticket.id} • ${ticket.status}", style = MaterialTheme.typography.titleSmall)
                        Text("الشركة: ${ticket.companyId}${ticket.branchId?.let { " • الفرع: $it" } ?: ""}")
                        Text(ticket.problemDescription)
                        Text("فتح: ${supportDateTime(ticket.createdAt)}", style = MaterialTheme.typography.bodySmall)
                        if (activeSession == null && ticket.status != "CLOSED") {
                            Button(
                                enabled = selectedSupportUser != null,
                                onClick = {
                                    val supportId = selectedSupportUser!!.id
                                    scope.launch {
                                        if (container.securityService.hasRecentReauthentication(user.id)) {
                                            busy = true
                                            runCatching {
                                                container.supportService.activateSession(user.id, ticket.id, supportId, durationMinutes)
                                            }.onSuccess { resultMessage = "تم تفعيل Support Session #$it" }
                                                .onFailure { resultMessage = it.message ?: "تعذر تفعيل الجلسة" }
                                            busy = false
                                        } else {
                                            pendingActivation = Triple(ticket.id, supportId, durationMinutes)
                                        }
                                    }
                                }
                            ) { Text("تفعيل الصيانة") }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        if (container.securityService.hasRecentReauthentication(user.id)) {
                                            busy = true
                                            runCatching {
                                                container.supportService.activateAdminTemporaryTestCleanupSession(user.id, ticket.id)
                                            }.onSuccess { resultMessage = "تم تفعيل تنظيف اختبار مؤقت لمدة ساعة — Session #$it" }
                                                .onFailure { resultMessage = it.message ?: "تعذر تفعيل تنظيف الاختبار" }
                                            busy = false
                                        } else {
                                            pendingAdminCleanupTicketId = ticket.id
                                        }
                                    }
                                },
                                enabled = !busy,
                            ) { Text("تنظيف اختبار محلي — ساعة (لا يحتاج FSP2)") }
                        } else if (activeSession != null) {
                            Text("جلسة فعالة: ${activeSession.supportDisplayName} — ${activeSession.supportUsername}")
                            Text("الانتهاء التلقائي: ${supportDateTime(activeSession.expiresAt)}")
                            OutlinedButton(onClick = {
                                scope.launch {
                                    runCatching { container.supportService.revokeSession(user.id, activeSession.id, "إلغاء يدوي من مدير الشركة") }
                                        .onSuccess { resultMessage = "تم إلغاء وصول الدعم فورًا" }
                                        .onFailure { resultMessage = it.message ?: "تعذر الإلغاء" }
                                }
                            }) { Text("إلغاء الوصول الآن") }
                        }
                        if (ticket.status != "CLOSED" && activeSession == null) {
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { container.supportService.closeTicket(user.id, ticket.id, "إغلاق البلاغ من مدير الشركة") }
                                        .onSuccess { resultMessage = "تم إغلاق Ticket #${ticket.id}" }
                                        .onFailure { resultMessage = it.message ?: "تعذر إغلاق البلاغ" }
                                }
                            }) { Text("إغلاق البلاغ") }
                        }
                    }
                }
            }
        }

        if (isAdmin && activeForUser.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("تنظيف بيانات الاختبار — ADMIN مؤقت", style = MaterialTheme.typography.titleMedium)
                        Text("هذه الجلسة لمدة ساعة فقط ولا تفتح أدوات الإصلاح العامة. الحذف مقيد بفاتورة/مصروف اختبار وبسبب صريح.", style = MaterialTheme.typography.bodySmall)
                        FushSearchableSelectionField(
                            label = "جلسة التنظيف الفعالة",
                            selectedText = selectedSession?.let { "Session #${it.id} • Ticket #${it.ticketId}" }.orEmpty(),
                            options = activeForUser,
                            optionText = { "Session #${it.id} • Ticket #${it.ticketId} • حتى ${supportDateTime(it.expiresAt)}" },
                            searchTerms = { listOf(it.id.toString(), it.ticketId.toString(), it.companyId) },
                            onSelected = { selectedSession = it },
                            onCleared = { selectedSession = null },
                        )
                        if (selectedSession != null) {
                            OutlinedTextField(reason, { reason = it }, label = { Text("سبب الحذف — يجب أن يذكر اختبار/وهمية") }, modifier = Modifier.fillMaxWidth())

                            FushSearchableSelectionField(
                                label = "فاتورة البيع — ابحث برقم الفاتورة الظاهر",
                                selectedText = selectedCleanupInvoice?.let { "فاتورة ${it.invoiceNo} • JE-${it.invoiceNo}" }.orEmpty(),
                                options = cleanupInvoices,
                                optionText = { "فاتورة ${it.invoiceNo} • JE-${it.invoiceNo}" },
                                searchTerms = { listOf(it.invoiceNo, "JE-${it.invoiceNo}", it.notes) },
                                supportingText = { "${it.status} • الإجمالي ${it.totalOriginal} ${it.currencyCode} • ${supportDateTime(it.invoiceDate)}" },
                                onSelected = { selectedCleanupInvoice = it },
                                onCleared = { selectedCleanupInvoice = null },
                                maxResults = 50,
                            )
                            selectedCleanupInvoice?.let { invoice ->
                                Text(
                                    "سيتم حذف الفاتورة الظاهرة رقم ${invoice.invoiceNo} وآثار الاختبار المرتبطة بها فقط.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Button(onClick = {
                                val invoice = selectedCleanupInvoice
                                if (invoice == null) { resultMessage = "اختر فاتورة الاختبار من القائمة أولاً"; return@Button }
                                scope.launch {
                                    busy = true
                                    runCatching { container.supportService.deleteTestSalesInvoiceBundle(user.id, selectedSession!!.id, invoice.id, reason) }
                                        .onSuccess {
                                            findings = emptyList()
                                            resultMessage = "Cleanup PASS: ${it.summary}"
                                            selectedCleanupInvoice = null
                                            cleanupRefresh++
                                        }
                                        .onFailure { resultMessage = it.message ?: "رفض/فشل حذف بيانات الاختبار" }
                                    busy = false
                                }
                            }, enabled = !busy && selectedCleanupInvoice != null) { Text("حذف فاتورة الاختبار المختارة وآثارها — مؤقت") }

                            FushSearchableSelectionField(
                                label = "مصروف الشحنة — ابحث برقم الشحنة أو سند الصرف",
                                selectedText = selectedCleanupExpense?.let { "${it.shipmentNo} • ${it.expense.paymentVoucherNo}" }.orEmpty(),
                                options = cleanupShipmentExpenses,
                                optionText = { "${it.shipmentNo} • ${it.expense.paymentVoucherNo}" },
                                searchTerms = { listOf(it.shipmentNo, it.expense.paymentVoucherNo, it.expense.description, it.expense.paymentReference, it.expense.expenseType) },
                                supportingText = { "${it.expense.description.ifBlank { it.expense.expenseType }} • ${it.expense.amountOriginal} ${it.expense.currencyCode} • ${supportDateTime(it.expense.expenseDate)}" },
                                onSelected = { selectedCleanupExpense = it },
                                onCleared = { selectedCleanupExpense = null },
                                maxResults = 50,
                            )
                            selectedCleanupExpense?.let { option ->
                                Text(
                                    "سيتم حذف مصروف الشحنة ${option.shipmentNo} المرتبط بالسند ${option.expense.paymentVoucherNo} فقط.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Button(onClick = {
                                val option = selectedCleanupExpense
                                if (option == null) { resultMessage = "اختر مصروف الشحنة الاختباري من القائمة أولاً"; return@Button }
                                scope.launch {
                                    busy = true
                                    runCatching { container.supportService.deleteTestShipmentExpense(user.id, selectedSession!!.id, option.expense.id, reason) }
                                        .onSuccess {
                                            findings = emptyList()
                                            resultMessage = "Cleanup PASS: ${it.summary}"
                                            selectedCleanupExpense = null
                                            cleanupRefresh++
                                        }
                                        .onFailure { resultMessage = it.message ?: "رفض/فشل حذف مصروف الشحنة الاختباري" }
                                    busy = false
                                }
                            }, enabled = !busy && selectedCleanupExpense != null) { Text("حذف مصروف الشحنة المختار — مؤقت") }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    runCatching { container.supportService.closeSession(user.id, selectedSession!!.id, "انتهاء تنظيف بيانات الاختبار") }
                                        .onSuccess { selectedSession = null; resultMessage = "تم إنهاء جلسة التنظيف" }
                                        .onFailure { resultMessage = it.message ?: "تعذر إنهاء الجلسة" }
                                }
                            }) { Text("إنهاء جلسة التنظيف") }
                        }
                    }
                }
            }
        }

        if (isSupport) {
            item {
                if (activeForUser.isEmpty()) {
                    Card(Modifier.fillMaxWidth()) { Text("لا توجد Support Session فعالة لهذا الحساب. لا يوجد وصول لبيانات الشركة.", Modifier.padding(12.dp)) }
                } else {
                    FushSearchableSelectionField(
                        label = "Support Session الفعالة",
                        selectedText = selectedSession?.let { "Session #${it.id} • Ticket #${it.ticketId}" }.orEmpty(),
                        options = activeForUser,
                        optionText = { "Session #${it.id} • Ticket #${it.ticketId} • ${it.companyId}" },
                        searchTerms = { listOf(it.id.toString(), it.ticketId.toString(), it.companyId, it.branchId.orEmpty()) },
                        onSelected = { selectedSession = it },
                        onCleared = { selectedSession = null },
                    )
                }
            }
            if (selectedSession != null) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("أدوات التشخيص والإصلاح", style = MaterialTheme.typography.titleMedium)
                            Text("Session #${selectedSession!!.id} • Ticket #${selectedSession!!.ticketId} • ${selectedSession!!.companyId}")
                            OutlinedTextField(reason, { reason = it }, label = { Text("سبب الفحص/الإصلاح") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(targetId, { targetId = it.filter(Char::isDigit) }, label = { Text("Record ID / Invoice ID / Item ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(secondaryId, { secondaryId = it.filter(Char::isDigit) }, label = { Text("ID إضافي — مثل Warehouse ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Button(onClick = { runTargetCommand { id -> container.supportService.diagnoseInvoice(user.id, selectedSession!!.id, id, reason) } }, enabled = !busy) { Text("Diagnose Invoice") }
                            Button(onClick = {
                                val warehouseId = secondaryId.toLongOrNull()
                                if (warehouseId == null) resultMessage = "أدخل Warehouse ID في الحقل الإضافي"
                                else runTargetCommand { itemId -> container.supportService.recalculateInventory(user.id, selectedSession!!.id, warehouseId, itemId, reason) }
                            }, enabled = !busy) { Text("Recalculate Inventory") }
                            Button(onClick = { runTargetCommand { id -> container.supportService.recalculateAverageCost(user.id, selectedSession!!.id, id, reason) } }, enabled = !busy) { Text("Recalculate Average Cost") }
                            Button(onClick = { runTargetCommand { id -> container.supportService.checkCustomerBalance(user.id, selectedSession!!.id, id, reason) } }, enabled = !busy) { Text("Check Customer Balance") }
                            Button(onClick = { runTargetCommand { id -> container.supportService.checkSupplierBalance(user.id, selectedSession!!.id, id, reason) } }, enabled = !busy) { Text("Check Supplier Balance") }
                            Button(onClick = { runTargetCommand { id -> container.supportService.checkTreasuryBalance(user.id, selectedSession!!.id, id, reason) } }, enabled = !busy) { Text("Check Cash/Bank Balance") }
                            Button(onClick = {
                                scope.launch {
                                    busy = true
                                    runCatching { container.supportService.findUnbalancedJournals(user.id, selectedSession!!.id, reason) }
                                        .onSuccess { findings = it.findings; resultMessage = it.summary }
                                        .onFailure { resultMessage = it.message ?: "فشل الفحص" }
                                    busy = false
                                }
                            }, enabled = !busy) { Text("Find Unbalanced Journal Entries") }
                            Button(onClick = {
                                scope.launch {
                                    busy = true
                                    runCatching { container.supportService.findIncompleteTransactions(user.id, selectedSession!!.id, reason) }
                                        .onSuccess { findings = it.findings; resultMessage = it.summary }
                                        .onFailure { resultMessage = it.message ?: "فشل الفحص" }
                                    busy = false
                                }
                            }, enabled = !busy) { Text("Find Failed/Incomplete Transactions") }
                            Button(onClick = {
                                val id = targetId.toLongOrNull()
                                if (id == null) { resultMessage = "أدخل Journal Entry ID"; return@Button }
                                scope.launch {
                                    busy = true
                                    runCatching { container.supportService.rebuildAccountingEntry(user.id, selectedSession!!.id, id, reason) }
                                        .onSuccess { findings = it.findings; resultMessage = "Repair PASS: ${it.summary}" }
                                        .onFailure { resultMessage = it.message ?: "رفض/فشل Repair" }
                                    busy = false
                                }
                            }, enabled = !busy) { Text("Rebuild Accounting Entry — STAGING فقط") }
                            Button(onClick = {
                                val id = targetId.toLongOrNull()
                                if (id == null) { resultMessage = "أدخل Journal Entry ID"; return@Button }
                                scope.launch {
                                    busy = true
                                    runCatching { container.supportService.repostTransaction(user.id, selectedSession!!.id, id, reason) }
                                        .onSuccess { findings = it.findings; resultMessage = "Repost PASS: ${it.summary}" }
                                        .onFailure { resultMessage = it.message ?: "رفض/فشل Repost" }
                                    busy = false
                                }
                            }, enabled = !busy) { Text("Repost Transaction — Safe STAGING") }
                            Button(onClick = {
                                val id = targetId.toLongOrNull()
                                if (id == null) { resultMessage = "أدخل Invoice ID لفاتورة الاختبار"; return@Button }
                                scope.launch {
                                    busy = true
                                    runCatching { container.supportService.deleteTestSalesInvoiceBundle(user.id, selectedSession!!.id, id, reason) }
                                        .onSuccess { findings = emptyList(); resultMessage = "Cleanup PASS: ${it.summary}" }
                                        .onFailure { resultMessage = it.message ?: "رفض/فشل حذف بيانات الاختبار" }
                                    busy = false
                                }
                            }, enabled = !busy) { Text("حذف فاتورة اختبار وآثارها — مؤقت") }
                            Button(onClick = {
                                val id = targetId.toLongOrNull()
                                if (id == null) { resultMessage = "أدخل Shipment Expense ID"; return@Button }
                                scope.launch {
                                    busy = true
                                    runCatching { container.supportService.deleteTestShipmentExpense(user.id, selectedSession!!.id, id, reason) }
                                        .onSuccess { findings = emptyList(); resultMessage = "Cleanup PASS: ${it.summary}" }
                                        .onFailure { resultMessage = it.message ?: "رفض/فشل حذف مصروف الشحنة الاختباري" }
                                    busy = false
                                }
                            }, enabled = !busy) { Text("حذف مصروف شحنة اختبار — مؤقت") }
                            Text("ملاحظة: أوامر الحذف أعلاه مؤقتة ومقيدة بجلسة FUSH_SUPPORT فعالة وسبب يذكر صراحة أنها بيانات اختبار. لا يوجد محرر Database عام.", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = {
                                scope.launch { container.supportService.closeSession(user.id, selectedSession!!.id, "انتهاء عمل الدعم"); selectedSession = null }
                            }) { Text("إنهاء Support Session") }
                        }
                    }
                }
                if (findings.isNotEmpty()) {
                    item { Text("النتائج", style = MaterialTheme.typography.titleMedium) }
                    items(findings, key = { "finding-${it.module}-${it.recordType}-${it.recordId}-${it.reference}" }) { f ->
                        ListItem(
                            headlineContent = { Text("${f.severity} • ${f.reference}") },
                            supportingContent = { Text("${f.module} • ${f.recordType} #${f.recordId}\n${f.details}") },
                        )
                    }
                }
                item { Text("Support Audit Log — غير قابل للتعديل", style = MaterialTheme.typography.titleMedium) }
                items(selectedAudit, key = { "support-audit-${it.id}" }) { a ->
                    ListItem(
                        headlineContent = { Text(a.action) },
                        supportingContent = {
                            Text("${supportDateTime(a.eventAt)} • ${a.module} • ${a.recordTable} #${a.recordId}\n${a.reason}${if (a.validationSummary.isNotBlank()) "\nValidation: ${a.validationSummary}" else ""}")
                        }
                    )
                }
            }
        }

        if (!isAdmin && !isSupport) item {
            Text("مركز الدعم متاح فقط لمدير الشركة أو حساب FUSH_SUPPORT.")
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (resultMessage.isNotBlank()) item { Text(resultMessage, color = MaterialTheme.colorScheme.primary) }
    }

    if (showNewTicket) {
        NewSupportTicketDialog(
            onDismiss = { showNewTicket = false },
            onCreate = { company, branch, problem ->
                scope.launch {
                    runCatching { container.supportService.createTicket(user.id, company, branch, problem) }
                        .onSuccess { resultMessage = "تم إنشاء Support Ticket #$it"; showNewTicket = false }
                        .onFailure { resultMessage = it.message ?: "تعذر إنشاء البلاغ" }
                }
            }
        )
    }

    pendingActivation?.let { pending ->
        ReauthenticationDialog(
            container = container,
            userId = user.id,
            actionLabel = "تفعيل وصول FUSH Support لمدة محددة",
            onDismiss = { pendingActivation = null },
            onVerified = {
                pendingActivation = null
                scope.launch {
                    busy = true
                    runCatching {
                        container.supportService.activateSession(user.id, pending.first, pending.second, pending.third)
                    }.onSuccess { resultMessage = "تم تفعيل Support Session #$it" }
                        .onFailure { resultMessage = it.message ?: "تعذر تفعيل الجلسة" }
                    busy = false
                }
            }
        )
    }

    pendingAdminCleanupTicketId?.let { ticketId ->
        ReauthenticationDialog(
            container = container,
            userId = user.id,
            actionLabel = "تفعيل تنظيف بيانات اختبار لمدة ساعة",
            onDismiss = { pendingAdminCleanupTicketId = null },
            onVerified = {
                pendingAdminCleanupTicketId = null
                scope.launch {
                    busy = true
                    runCatching { container.supportService.activateAdminTemporaryTestCleanupSession(user.id, ticketId) }
                        .onSuccess { resultMessage = "تم تفعيل تنظيف اختبار مؤقت لمدة ساعة — Session #$it" }
                        .onFailure { resultMessage = it.message ?: "تعذر تفعيل تنظيف الاختبار" }
                    busy = false
                }
            }
        )
    }

    pendingProvisioningAction?.let { action ->
        ReauthenticationDialog(
            container = container,
            userId = user.id,
            actionLabel = when (action) {
                "CHALLENGE_DEFAULT" -> "إنشاء Vendor Provision/Rebind/Legacy Challenge"
                "CHALLENGE_ROTATE" -> "إنشاء Credential Rotation Challenge"
                "CHALLENGE_KEY" -> "إنشاء Vendor Key Rotation Challenge"
                "IMPORT_KEY" -> "تطبيق Signed Vendor Key Rotation"
                else -> "تطبيق Signed Vendor Identity Lifecycle"
            },
            onDismiss = { pendingProvisioningAction = null },
            onVerified = {
                val pending = pendingProvisioningAction
                pendingProvisioningAction = null
                scope.launch {
                    when (pending) {
                        "CHALLENGE_DEFAULT" -> runCatching { container.securityService.createVendorSupportProvisioningChallenge(user.id) }
                            .onSuccess { provisioningRequest = it.requestToken(); resultMessage = "تم إنشاء ${it.purpose} Challenge" }
                            .onFailure { resultMessage = it.message ?: "تعذر إنشاء Challenge" }
                        "CHALLENGE_ROTATE" -> runCatching { container.securityService.createVendorSupportProvisioningChallenge(user.id, com.fush.erp.domain.VendorLifecycleAction.ROTATE) }
                            .onSuccess { provisioningRequest = it.requestToken(); resultMessage = "تم إنشاء Credential Rotation Challenge" }
                            .onFailure { resultMessage = it.message ?: "تعذر إنشاء Rotation Challenge" }
                        "CHALLENGE_KEY" -> runCatching { container.securityService.createVendorSupportProvisioningChallenge(user.id, com.fush.erp.domain.VendorLifecycleAction.KEY_ROTATE) }
                            .onSuccess { provisioningRequest = it.requestToken(); resultMessage = "تم إنشاء Vendor Key Rotation Challenge" }
                            .onFailure { resultMessage = it.message ?: "تعذر إنشاء Key Rotation Challenge" }
                        "IMPORT_KEY" -> runCatching { container.securityService.rotateVendorSupportSigningKey(user.id, signedKeyRotationPackage) }
                            .onSuccess { resultMessage = "تم تدوير Vendor signing key إلى $it"; signedKeyRotationPackage = ""; provisioningRequest = "" }
                            .onFailure { resultMessage = it.message ?: "رفض Key Rotation Package" }
                        else -> runCatching { container.securityService.provisionVendorSupport(user.id, signedProvisioningPackage) }
                            .onSuccess { resultMessage = "تم تطبيق Signed Vendor Lifecycle بنجاح"; signedProvisioningPackage = ""; provisioningRequest = "" }
                            .onFailure { resultMessage = it.message ?: "رفض Signed Vendor Package" }
                    }
                }
            }
        )
    }

}

@Composable
private fun NewSupportTicketDialog(onDismiss: () -> Unit, onCreate: (String, String?, String) -> Unit) {
    var company by remember { mutableStateOf(SupportPolicy.DEFAULT_COMPANY_ID) }
    var branch by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Support Ticket جديد") },
        text = {
            com.fush.erp.ui.FushDialogForm {
                OutlinedTextField(company, { company = it }, label = { Text("Company ID") }, singleLine = true)
                OutlinedTextField(branch, { branch = it }, label = { Text("Branch ID — اختياري") }, singleLine = true)
                OutlinedTextField(problem, { problem = it }, label = { Text("وصف المشكلة") }, minLines = 3)
            }
        },
        confirmButton = { Button(onClick = { onCreate(company, branch.takeIf { it.isNotBlank() }, problem) }) { Text("إنشاء") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

private fun supportDateTime(epoch: Long): String =
    Instant.ofEpochMilli(epoch).atZone(BusinessTimeZone.zoneId).format(DateTimeFormatter.ofPattern("dd/MM/yyyy — hh:mm:ss a"))
