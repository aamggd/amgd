#!/usr/bin/env python3
from pathlib import Path
import re
import shutil
import sys

if len(sys.argv) != 3:
    raise SystemExit("usage: apply_expenses_p1.py <central_source_root> <payload_dir>")

root = Path(sys.argv[1]).resolve()
payload = Path(sys.argv[2]).resolve()


def must_replace(path: Path, old: str, new: str, count: int = 1):
    text = path.read_text(encoding="utf-8")
    actual = text.count(old)
    if actual != count:
        raise RuntimeError(f"{path}: expected {count} occurrence(s), found {actual}: {old[:120]!r}")
    path.write_text(text.replace(old, new, count), encoding="utf-8")


def copy_payload(name: str, destination: str):
    dst = root / destination
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(payload / name, dst)


fush_db = root / "app/src/main/java/com/fush/erp/data/FushDatabase.kt"
db_text = fush_db.read_text(encoding="utf-8")
m = re.search(r"const val FUSH_DB_SCHEMA_VERSION = (\d+)", db_text)
if not m:
    raise RuntimeError("FUSH_DB_SCHEMA_VERSION not found")
base_schema = int(m.group(1))
target_schema = base_schema + 1
migration_name = f"MIGRATION_{base_schema}_{target_schema}_EXPENSE_WORKFLOW_PROVISIONAL"

copy_payload("ExpenseWorkflowEntity.kt", "app/src/main/java/com/fush/erp/data/entity/ExpenseWorkflowEntity.kt")
copy_payload("ExpenseWorkflowDao.kt", "app/src/main/java/com/fush/erp/data/dao/ExpenseWorkflowDao.kt")
copy_payload("ExpenseWorkflowService.kt", "app/src/main/java/com/fush/erp/domain/ExpenseWorkflowService.kt")
copy_payload("ExpenseLifecyclePolicyTest.kt", "app/src/test/java/com/fush/erp/domain/ExpenseLifecyclePolicyTest.kt")

# Room registration. Migration numbering is intentionally provisional and derived from the
# central schema used for this validation run.
db_text = db_text.replace(
    f"const val FUSH_DB_SCHEMA_VERSION = {base_schema}",
    f"const val FUSH_DB_SCHEMA_VERSION = {target_schema}",
    1,
)
entity_anchor = "        ExpenseAttachmentEntity::class,\n"
if entity_anchor not in db_text:
    raise RuntimeError("ExpenseAttachmentEntity anchor not found")
db_text = db_text.replace(entity_anchor, entity_anchor + "        ExpenseWorkflowRequestEntity::class,\n", 1)
dao_anchor = "    abstract fun expenseDao(): ExpenseDao\n"
if dao_anchor not in db_text:
    raise RuntimeError("expenseDao anchor not found")
db_text = db_text.replace(dao_anchor, dao_anchor + "    abstract fun expenseWorkflowDao(): ExpenseWorkflowDao\n", 1)
fush_db.write_text(db_text, encoding="utf-8")

app_container = root / "app/src/main/java/com/fush/erp/data/AppContainer.kt"
app_text = app_container.read_text(encoding="utf-8")
match = re.search(r"\.addMigrations\((.*?)\)\.build\(\)", app_text, re.S)
if not match:
    raise RuntimeError("Room addMigrations(...) registration not found")
registered = match.group(1).strip()
if migration_name in registered:
    raise RuntimeError("expense workflow migration already registered")
replacement = f".addMigrations({registered}, {migration_name}).build()"
app_text = app_text[:match.start()] + replacement + app_text[match.end():]
app_container.write_text(app_text, encoding="utf-8")

migration_sql = (payload / "expense_workflow_migration.sql").read_text(encoding="utf-8")
statements = [s.strip() for s in migration_sql.split(";") if s.strip()]
exec_lines = []
for statement in statements:
    exec_lines.append('        db.execSQL("""\n' + statement + '\n        """.trimIndent())')
migration_block = (
    "\n\n// Expenses P1 branch only: controlled Draft -> Submitted -> Approved/Rejected -> Paid lifecycle.\n"
    "// The numeric transition is PROVISIONAL / BRANCH ONLY and must be renumbered by Central Integration if needed.\n"
    f"val {migration_name} = object : Migration({base_schema}, {target_schema}) {{\n"
    "    override fun migrate(db: SupportSQLiteDatabase) {\n"
    + "\n".join(exec_lines)
    + "\n    }\n}\n"
)
migrations = root / "app/src/main/java/com/fush/erp/data/Migrations.kt"
migrations.write_text(migrations.read_text(encoding="utf-8").rstrip() + migration_block, encoding="utf-8")

# Direct EXPENSE posting is no longer a valid public path after P1. The workflow service supplies
# the approved request id; all other voucher types retain their current behavior.
accounting = root / "app/src/main/java/com/fush/erp/domain/AccountingService.kt"
must_replace(
    accounting,
    "        val expenseContext: ExpenseContext? = null\n    )",
    "        val expenseContext: ExpenseContext? = null,\n        val approvedExpenseRequestId: Long? = null\n    )",
)
must_replace(
    accounting,
    '        if (request.type == "EXPENSE") requireNotNull(request.expenseContext) { "بيانات تصنيف المصروف مطلوبة" }\n        else require(request.expenseContext == null) { "أبعاد المصروف تستخدم مع سند المصروف فقط" }',
    '        if (request.type == "EXPENSE") {\n            requireNotNull(request.expenseContext) { "بيانات تصنيف المصروف مطلوبة" }\n            val approvalId = requireNotNull(request.approvedExpenseRequestId) { "يجب دفع المصروف من دورة الاعتماد" }\n            val approval = requireNotNull(db.expenseWorkflowDao().byId(approvalId)) { "طلب اعتماد المصروف غير موجود" }\n            ExpenseLifecyclePolicy.requireCanPay(approval.approvalStatus, approval.paymentStatus)\n        } else {\n            require(request.expenseContext == null) { "أبعاد المصروف تستخدم مع سند المصروف فقط" }\n            require(request.approvedExpenseRequestId == null) { "مرجع اعتماد المصروف يستخدم مع سند المصروف فقط" }\n        }',
)

service = root / "app/src/main/java/com/fush/erp/domain/ExpenseWorkflowService.kt"
service_text = service.read_text(encoding="utf-8")
service_text = service_text.replace(
    "                createdBy = actorId,\n                expenseContext = AccountingService.ExpenseContext(",
    "                createdBy = actorId,\n                approvedExpenseRequestId = row.id,\n                expenseContext = AccountingService.ExpenseContext(",
    1,
)
service.write_text(service_text, encoding="utf-8")

screens = root / "app/src/main/java/com/fush/erp/ui/screens/ExpenseScreens.kt"
must_replace(
    screens,
    "import com.fush.erp.domain.ExpenseClassificationPolicy\n",
    "import com.fush.erp.domain.ExpenseClassificationPolicy\nimport com.fush.erp.domain.ExpenseLifecyclePolicy\nimport com.fush.erp.domain.ExpenseWorkflowService\n",
)
must_replace(
    screens,
    "    val scope = rememberCoroutineScope()\n    val rows by container.db.expenseDao().observeReportRows().collectAsState(initial = emptyList())",
    "    val scope = rememberCoroutineScope()\n    val workflowService = remember(container.db) { ExpenseWorkflowService(container.db) }\n    val workflowRequests by container.db.expenseWorkflowDao().observeAll().collectAsState(initial = emptyList())\n    val rows by container.db.expenseDao().observeReportRows().collectAsState(initial = emptyList())",
)
must_replace(
    screens,
    "    var showAdd by remember { mutableStateOf(false) }\n    var message by remember { mutableStateOf<String?>(null) }",
    "    var showAdd by remember { mutableStateOf(false) }\n    var message by remember { mutableStateOf<String?>(null) }\n    var rejectTarget by remember { mutableStateOf<ExpenseWorkflowRequestEntity?>(null) }\n    var rejectReason by remember { mutableStateOf(\"\") }",
)
must_replace(
    screens,
    '                    FushSectionHeader("إجراء جديد", "سند المصروف يربط الحساب بالخزينة والأبعاد التشغيلية دون إضافة حسابات فرعية غير ضرورية.")\n                    Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("تسجيل مصروف جديد") }',
    '                    FushSectionHeader("إجراء جديد", "أنشئ مسودة مصروف، ثم أرسلها للاعتماد قبل السماح بالدفع والترحيل المحاسبي.")\n                    Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("إنشاء مسودة مصروف") }',
)
workflow_ui = '''        item { FushSectionHeader("دورة اعتماد المصروفات", "حالة الاعتماد مستقلة عن حالة الدفع، ولا ينشأ القيد المحاسبي إلا بعد الاعتماد ثم الدفع.") }
        if (workflowRequests.isEmpty()) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    FushEmptyState("لا توجد طلبات مصروف", "أنشئ مسودة جديدة لبدء دورة الاعتماد.", Modifier.padding(18.dp))
                }
            }
        }
        items(workflowRequests, key = { "expense-request-${it.id}" }) { request ->
            val lifecycle = ExpenseLifecyclePolicy.lifecycleLabel(request.approvalStatus, request.paymentStatus)
            val tone = when (lifecycle) {
                ExpenseLifecyclePolicy.PAID -> FushStatusTone.Success
                ExpenseLifecyclePolicy.REJECTED -> FushStatusTone.Warning
                ExpenseLifecyclePolicy.APPROVED -> FushStatusTone.Info
                ExpenseLifecyclePolicy.SUBMITTED -> FushStatusTone.Warning
                else -> FushStatusTone.Neutral
            }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(request.requestNo, style = MaterialTheme.typography.titleMedium)
                            Text(
                                accounts.firstOrNull { it.id == request.expenseAccountId }?.let { "${it.code} — ${it.nameAr}" } ?: "حساب مصروف #${request.expenseAccountId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FushStatusPill(lifecycle, tone)
                    }
                    Text("${expenseMoney(request.amountOriginal)} ${request.currencyCode} • ${expenseDate(request.expenseDate)}", style = MaterialTheme.typography.bodyMedium)
                    Text(request.description, style = MaterialTheme.typography.bodySmall)
                    Text("الاعتماد: ${request.approvalStatus} • الدفع: ${request.paymentStatus}", style = MaterialTheme.typography.labelMedium)
                    when {
                        request.approvalStatus == ExpenseLifecyclePolicy.DRAFT -> {
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        workflowService.submit(request.id, user.id)
                                        message = "تم إرسال ${request.requestNo} للاعتماد"
                                    } catch (e: Exception) { message = e.message ?: "تعذر إرسال الطلب" }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("إرسال للاعتماد") }
                        }
                        request.approvalStatus == ExpenseLifecyclePolicy.SUBMITTED -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    scope.launch {
                                        try {
                                            workflowService.approve(request.id, user.id)
                                            message = "تم اعتماد ${request.requestNo}"
                                        } catch (e: Exception) { message = e.message ?: "تعذر اعتماد الطلب" }
                                    }
                                }, modifier = Modifier.weight(1f)) { Text("اعتماد") }
                                OutlinedButton(onClick = { rejectTarget = request; rejectReason = "" }, modifier = Modifier.weight(1f)) { Text("رفض") }
                            }
                        }
                        request.approvalStatus == ExpenseLifecyclePolicy.APPROVED && request.paymentStatus == ExpenseLifecyclePolicy.UNPAID -> {
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        val entryId = workflowService.pay(request.id, user.id)
                                        message = "تم دفع وترحيل ${request.requestNo} بالقيد رقم $entryId"
                                    } catch (e: Exception) { message = e.message ?: "تعذر دفع المصروف" }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("دفع وترحيل المصروف") }
                        }
                        request.approvalStatus == ExpenseLifecyclePolicy.REJECTED -> {
                            Text("سبب الرفض: ${request.rejectionReason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        request.paymentStatus == ExpenseLifecyclePolicy.PAID -> {
                            Text("تم الترحيل المحاسبي • القيد #${request.journalEntryId ?: 0}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
'''
anchor = '        item { FushSectionHeader("حركات المصروف", "${filtered.size} حركة مطابقة للفلاتر الحالية.") }\n'
text = screens.read_text(encoding="utf-8")
if text.count(anchor) != 1:
    raise RuntimeError("expense movements section anchor not found")
text = text.replace(anchor, workflow_ui + anchor, 1)
screens.write_text(text, encoding="utf-8")

must_replace(
    screens,
    "                    val id = container.accountingService.postVoucher(request.copy(createdBy = user.id))\n                    message = \"تم ترحيل المصروف والقيد رقم $id\"",
    "                    val id = workflowService.createDraft(request.copy(createdBy = user.id), user.id)\n                    message = \"تم حفظ مسودة طلب المصروف رقم $id\"",
)
must_replace(screens, '        title = { Text("مصروف جديد") },', '        title = { Text("مسودة مصروف جديدة") },')
must_replace(screens, '            ) { Text("ترحيل المصروف") }', '            ) { Text("حفظ المسودة") }')

reject_dialog = '''
    rejectTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { rejectTarget = null; rejectReason = "" },
            title = { Text("رفض طلب المصروف") },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = { Text("سبب الرفض — إلزامي") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = rejectReason.isNotBlank(),
                    onClick = {
                        scope.launch {
                            try {
                                workflowService.reject(target.id, rejectReason, user.id)
                                message = "تم رفض ${target.requestNo}"
                                rejectTarget = null
                                rejectReason = ""
                            } catch (e: Exception) { message = e.message ?: "تعذر رفض الطلب" }
                        }
                    }
                ) { Text("تأكيد الرفض") }
            },
            dismissButton = { TextButton(onClick = { rejectTarget = null; rejectReason = "" }) { Text("إلغاء") } }
        )
    }
'''
end_anchor = "    }\n}\n\n@Composable\nprivate fun AddExpenseDialog("
text = screens.read_text(encoding="utf-8")
if text.count(end_anchor) != 1:
    raise RuntimeError("ExpenseManagementTab end anchor not found")
text = text.replace(end_anchor, "    }\n" + reject_dialog + "}\n\n@Composable\nprivate fun AddExpenseDialog(", 1)
screens.write_text(text, encoding="utf-8")

print(f"EXPENSES_P1_BASE_SCHEMA={base_schema}")
print(f"EXPENSES_P1_TARGET_SCHEMA={target_schema}")
print(f"EXPENSES_P1_MIGRATION={migration_name}")
