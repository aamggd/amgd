package com.fush.erp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fush.erp.data.AppContainer
import com.fush.erp.data.entity.*
import com.fush.erp.ui.*
import com.fush.erp.domain.SecurityPermissions
import kotlinx.coroutines.launch

@Composable
fun GovernanceScreen(container: AppContainer, user: UserEntity, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val documents by container.db.governanceDao().observeDocuments().collectAsState(initial = emptyList())
    val changes by container.db.governanceDao().observeChangeRequests().collectAsState(initial = emptyList())
    val approvals by container.db.governanceDao().observeApprovals().collectAsState(initial = emptyList())
    val audit by container.db.governanceDao().observeAuditEvents().collectAsState(initial = emptyList())
    val rolePermissions by container.db.securityDao().observePermissionCodesForRole(user.role).collectAsState(initial = emptyList())
    val permissionSet = remember(rolePermissions) { rolePermissions.toSet() }
    val canManage = user.role == "ADMIN" || SecurityPermissions.GOVERNANCE_MANAGE in permissionSet
    val canApprove = user.role == "ADMIN" || SecurityPermissions.APPROVAL_DECIDE in permissionSet
    val canAudit = user.role == "ADMIN" || SecurityPermissions.AUDIT_VIEW in permissionSet
    var tab by remember { mutableStateOf(0) }
    var showDocument by remember { mutableStateOf(false) }
    var showChange by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val pendingApprovals = approvals.count { it.status == "PENDING" }
    val activeChanges = changes.count { it.status == "SUBMITTED" || it.status == "DRAFT" }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FushSectionHeader(
                    title = "الحوكمة والتدقيق",
                    subtitle = "الوثائق المضبوطة، إدارة التغيير، الموافقات وفصل المهام مع سجل تدقيق غير قابل للحذف من الواجهة.",
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FushMetricCard("الوثائق", documents.size.toString(), Modifier.weight(1f), helper = "الإصدارات المسجلة")
                    FushMetricCard("تغييرات نشطة", activeChanges.toString(), Modifier.weight(1f), helper = "مسودة أو مرسلة", tone = if (activeChanges > 0) FushStatusTone.Warning else FushStatusTone.Success)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FushMetricCard("موافقات معلقة", pendingApprovals.toString(), Modifier.weight(1f), helper = "تحتاج قرارًا", tone = if (pendingApprovals > 0) FushStatusTone.Warning else FushStatusTone.Success)
                    FushMetricCard("أحداث التدقيق", audit.size.toString(), Modifier.weight(1f), helper = "آخر الأحداث المحملة")
                }
            }
        }
        item {
            ScrollableTabRow(selectedTabIndex = tab) {
                listOf("الوثائق", "التغييرات", "الموافقات", "التدقيق").forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                }
            }
        }
        if (message != null) {
            item { FushOperationMessage(message, onConsumed = { message = null }) }
        }
        when (tab) {
            0 -> {
                item {
                    if (canManage) Button(onClick = { showDocument = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("وثيقة / إجراء جديد") }
                    else FushInlineState("لديك صلاحية عرض الوثائق فقط؛ إنشاء الوثائق يتطلب صلاحية إدارة الحوكمة.", tone = FushStatusTone.Info)
                }
                if (documents.isEmpty()) item {
                    FushEmptyState("لا توجد وثائق مضبوطة", "أنشئ أول وثيقة أو إجراء ليظهر سجل الإصدارات وحالة الاعتماد هنا.")
                }
                items(documents, key = { "governance-document-${it.id}" }) { d ->
                    ListItem(
                        headlineContent = { Text("${d.documentCode} — ${d.titleAr}") },
                        supportingContent = { Text("الإصدار ${d.versionNo} • ${d.category} • ${d.status}") },
                        trailingContent = {
                            if (d.status == "DRAFT" && canApprove && d.createdBy != user.id) {
                                TextButton(onClick = {
                                    scope.launch {
                                        container.securityService.requirePermission(user.id, SecurityPermissions.APPROVAL_DECIDE)
                                        val now = System.currentTimeMillis()
                                        container.db.governanceDao().updateDocument(d.copy(status="EFFECTIVE", effectiveAt=now, approvedBy=user.id, approvedAt=now))
                                        container.db.governanceDao().insertAudit(AuditEventEntity(userId=user.id, action="APPROVE", entityType="DOCUMENT", entityId=d.id.toString(), oldValue="DRAFT", newValue="EFFECTIVE", reason="اعتماد الوثيقة"))
                                    }
                                }) { Text("اعتماد") }
                            }
                        }
                    )
                }
            }
            1 -> {
                item {
                    if (canManage) Button(onClick = { showChange = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("طلب تغيير جديد") }
                    else FushInlineState("لديك صلاحية عرض طلبات التغيير فقط؛ الإنشاء يتطلب صلاحية إدارة الحوكمة.", tone = FushStatusTone.Info)
                }
                if (changes.isEmpty()) item {
                    FushEmptyState("لا توجد طلبات تغيير", "طلبات التغيير المقترحة والمعتمدة ستظهر هنا مع أثرها وحالتها.")
                }
                items(changes, key = { "governance-change-${it.id}" }) { c ->
                    ListItem(
                        headlineContent = { Text("${c.requestNo} — ${c.subject}") },
                        supportingContent = { Text("${c.changeType} • ${c.status}\n${c.reason}") },
                        trailingContent = {
                            if (c.status == "SUBMITTED" && canApprove && c.requestedBy != user.id) {
                                TextButton(onClick = {
                                    scope.launch {
                                        container.securityService.requirePermission(user.id, SecurityPermissions.APPROVAL_DECIDE)
                                        val now = System.currentTimeMillis()
                                        container.db.governanceDao().updateChangeRequest(c.copy(status="APPROVED", approvedBy=user.id, approvedAt=now))
                                        container.db.governanceDao().insertAudit(AuditEventEntity(userId=user.id, action="APPROVE", entityType="CHANGE_REQUEST", entityId=c.id.toString(), oldValue="SUBMITTED", newValue="APPROVED", reason=c.reason))
                                    }
                                }) { Text("اعتماد") }
                            }
                        }
                    )
                }
            }
            2 -> {
                if (approvals.isEmpty()) item {
                    FushEmptyState("لا توجد موافقات", "لا توجد طلبات موافقة مسجلة أو معلقة حاليًا.")
                }
                items(approvals, key = { "governance-approval-${it.id}" }) { a ->
                    ListItem(
                        headlineContent = { Text(a.title) },
                        supportingContent = { Text("${a.referenceType} #${a.referenceId} • ${a.status} • مطلوب: ${a.requestedRole}") },
                        trailingContent = {
                            if (a.status == "PENDING" && canApprove && (a.requestedRole == user.role || user.role == "ADMIN")) {
                                if (a.requestedBy == user.id) {
                                    Text("مستخدم آخر", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                } else Row {
                                    TextButton(onClick = { scope.launch { decideApproval(container, a, user, true) } }) { Text("موافقة") }
                                    TextButton(onClick = { scope.launch { decideApproval(container, a, user, false) } }) { Text("رفض") }
                                }
                            }
                        }
                    )
                }
            }
            else -> {
                if (!canAudit) item { FushEmptyState("صلاحية التدقيق مطلوبة", "ليس لديك صلاحية عرض سجل التدقيق.") }
                if (canAudit) item { FushInlineState("آخر 200 حدث. لا يوجد حذف من الواجهة.", tone = FushStatusTone.Info) }
                if (canAudit && audit.isEmpty()) item {
                    FushEmptyState("لا توجد أحداث تدقيق", "ستظهر هنا عمليات الإنشاء والاعتماد والرفض والتغييرات المسجلة.")
                }
                if (canAudit) items(audit, key = { "governance-audit-${it.id}" }) { e ->
                    ListItem(
                        headlineContent = { Text("${e.action} • ${e.entityType} #${e.entityId}") },
                        supportingContent = { Text("المستخدم ${e.userId} • ${e.reason}\n${e.oldValue} → ${e.newValue}") }
                    )
                }
            }
        }
    }

    if (showDocument) AddDocumentDialog(onDismiss = { showDocument = false }) { code, title, category, owner, summary ->
        scope.launch {
            try {
                container.securityService.requirePermission(user.id, SecurityPermissions.GOVERNANCE_MANAGE)
                val id = container.db.governanceDao().insertDocument(ControlledDocumentEntity(documentCode=code, titleAr=title, category=category, ownerRole=owner, contentSummary=summary, createdBy=user.id))
                container.db.governanceDao().insertAudit(AuditEventEntity(userId=user.id, action="CREATE", entityType="DOCUMENT", entityId=id.toString(), newValue="DRAFT", reason="إنشاء وثيقة مضبوطة"))
                showDocument = false; message = "تم إنشاء الوثيقة كمسودة"
            } catch (e: Exception) { message = e.message ?: "تعذر إنشاء الوثيقة" }
        }
    }

    if (showChange) AddChangeDialog(onDismiss = { showChange = false }) { type, subject, reason, quality, financial, inventory ->
        scope.launch {
            try {
                container.securityService.requirePermission(user.id, SecurityPermissions.GOVERNANCE_MANAGE)
                val no = "CHG-${System.currentTimeMillis().toString().takeLast(8)}"
                val id = container.db.governanceDao().insertChangeRequest(ChangeRequestEntity(requestNo=no, changeType=type, subject=subject, reason=reason, qualityImpact=quality, financialImpact=financial, inventoryImpact=inventory, requestedBy=user.id))
                container.db.governanceDao().insertApproval(ApprovalRequestEntity(referenceType="CHANGE_REQUEST", referenceId=id.toString(), title="اعتماد تغيير: $subject", requestedRole="ADMIN", requestedBy=user.id))
                container.db.governanceDao().insertAudit(AuditEventEntity(userId=user.id, action="CREATE", entityType="CHANGE_REQUEST", entityId=id.toString(), newValue="SUBMITTED", reason=reason))
                showChange = false; message = "تم إنشاء طلب التغيير وإرساله للموافقة"
            } catch (e: Exception) { message = e.message ?: "تعذر إنشاء طلب التغيير" }
        }
    }
}

private suspend fun decideApproval(container: AppContainer, row: ApprovalRequestEntity, user: UserEntity, approve: Boolean) {
    container.securityService.requirePermission(user.id, SecurityPermissions.APPROVAL_DECIDE)
    require(row.requestedBy != user.id) { "فصل المهام: لا يجوز لمقدم الطلب اعتماد طلبه بنفسه." }
    val status = if (approve) "APPROVED" else "REJECTED"
    val now = System.currentTimeMillis()
    container.db.governanceDao().updateApproval(row.copy(status=status, decisionBy=user.id, decisionAt=now, decisionNote=if (approve) "تم الاعتماد" else "تم الرفض"))
    container.db.governanceDao().insertAudit(AuditEventEntity(userId=user.id, action=status, entityType="APPROVAL", entityId=row.id.toString(), oldValue="PENDING", newValue=status, reason=row.title))
}

@Composable
private fun AddDocumentDialog(onDismiss: () -> Unit, onSave: (String,String,String,String,String) -> Unit) {
    var code by remember { mutableStateOf("") }; var title by remember { mutableStateOf("") }; var category by remember { mutableStateOf("SOP") }; var owner by remember { mutableStateOf("") }; var summary by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss, title={Text("وثيقة مضبوطة جديدة")}, text={ Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(code,{code=it},label={Text("الكود مثل SOP-PROD-001")},singleLine=true)
        OutlinedTextField(title,{title=it},label={Text("العنوان")},singleLine=true)
        OutlinedTextField(category,{category=it},label={Text("الفئة: SOP / FORM / POLICY")},singleLine=true)
        OutlinedTextField(owner,{owner=it},label={Text("المالك/المسؤول")},singleLine=true)
        OutlinedTextField(summary,{summary=it},label={Text("ملخص المحتوى")})
    }}, confirmButton={Button(enabled=code.isNotBlank()&&title.isNotBlank(),onClick={onSave(code.trim(),title.trim(),category.trim(),owner.trim(),summary.trim())}){Text("حفظ مسودة")}}, dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}

@Composable
private fun AddChangeDialog(onDismiss: () -> Unit, onSave: (String,String,String,String,String,String) -> Unit) {
    var type by remember { mutableStateOf("BOM") }; var subject by remember { mutableStateOf("") }; var reason by remember { mutableStateOf("") }; var quality by remember { mutableStateOf("") }; var financial by remember { mutableStateOf("") }; var inventory by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onDismiss, title={Text("طلب تغيير")}, text={ Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("لا تعدّل العناصر الحرجة مباشرة؛ أنشئ طلب تغيير أولاً.")
        OutlinedTextField(type,{type=it},label={Text("النوع: BOM / SUPPLIER / LABEL / PRICE / CREDIT ...")},singleLine=true)
        OutlinedTextField(subject,{subject=it},label={Text("الموضوع")},singleLine=true)
        OutlinedTextField(reason,{reason=it},label={Text("سبب التغيير")})
        OutlinedTextField(quality,{quality=it},label={Text("أثر الجودة")})
        OutlinedTextField(financial,{financial=it},label={Text("الأثر المالي")})
        OutlinedTextField(inventory,{inventory=it},label={Text("أثر المخزون")})
    }}, confirmButton={Button(enabled=subject.isNotBlank()&&reason.isNotBlank(),onClick={onSave(type.trim(),subject.trim(),reason.trim(),quality.trim(),financial.trim(),inventory.trim())}){Text("إرسال للموافقة")}}, dismissButton={TextButton(onClick=onDismiss){Text("إلغاء")}})
}
