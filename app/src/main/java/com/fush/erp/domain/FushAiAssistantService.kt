package com.fush.erp.domain

import com.fush.erp.cloud.FushAiRemoteHistoryTurn
import com.fush.erp.cloud.FushAiRemoteService
import com.fush.erp.cloud.FushAiRemoteToolCall
import com.fush.erp.cloud.FushAiRemoteToolResult
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.CustomerEntity
import com.fush.erp.data.entity.ItemEntity
import com.fush.erp.data.entity.UserEntity
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * v210: permission-gated tool-calling assistant.
 *
 * A private language-model gateway may decide which read-only ERP tools are needed and phrase
 * the final answer, but every tool is executed on-device by this service after a second local
 * permission check. If the private model is unavailable, the v206 deterministic parser remains
 * available as a safe offline fallback. No write tool is exposed in v210.
 */
class FushAiAssistantService(
    private val db: FushDatabase,
    private val remote: FushAiRemoteService? = null,
) {
    private val draftService = FushAiDraftService(db)

    suspend fun ask(
        user: UserEntity,
        rawQuestion: String,
        history: List<FushAiConversationTurn> = emptyList(),
    ): FushAiAnswer {
        val question = rawQuestion.trim()
        if (question.isBlank()) {
            return FushAiAnswer("FUSH AI", "اكتب سؤالك عن المبيعات أو العملاء أو المخزون أو الخزينة أو الإنتاج أو الشحنات.")
        }
        val permissions = if (user.role == "ADMIN") emptySet() else db.securityDao().permissionCodesForRole(user.role).toSet()

        val remoteHistory = history.takeLast(8).map {
            FushAiRemoteHistoryTurn(if (it.fromUser) "user" else "assistant", it.text)
        }
        val allowedTools = FushAiToolPolicy.allowedTools(user.role, permissions)
        val plan = runCatching {
            remote?.plan(user, question, remoteHistory, allowedTools)
        }.getOrNull()

        if (plan != null) {
            if (plan.toolCalls.isNotEmpty()) {
                val executed = plan.toolCalls.take(4).mapNotNull { call ->
                    executeRemoteTool(user, permissions, call, question)?.let { call to it }
                }
                executed.firstOrNull { it.second.draftId != null }?.second?.let { draftAnswer ->
                    return draftAnswer.copy(engine = FushAiEngine.PRIVATE_LLM)
                }
                val toolResults = executed.map { (call, answer) ->
                    FushAiRemoteToolResult(call.name, "${answer.title}\n${answer.body}")
                }
                if (toolResults.isNotEmpty()) {
                    val natural = runCatching {
                        remote?.answer(user, question, remoteHistory, toolResults)
                    }.getOrNull()?.trim().orEmpty()
                    if (natural.isNotBlank()) {
                        return FushAiAnswer("FUSH AI", natural, FushAiEngine.PRIVATE_LLM)
                    }
                    return FushAiAnswer(
                        "FUSH AI",
                        toolResults.joinToString("\n\n") { it.content },
                        FushAiEngine.TOOL_FALLBACK,
                    )
                }
            }
            plan.directReply?.takeIf { it.isNotBlank() }?.let {
                return FushAiAnswer("FUSH AI", it, FushAiEngine.PRIVATE_LLM)
            }
        }

        return askLocal(user, permissions, question)
    }

    private suspend fun askLocal(
        user: UserEntity,
        permissions: Set<String>,
        question: String,
    ): FushAiAnswer {
        val intent = FushAiIntentParser.detect(question)
        val answer = when (intent) {
            FushAiIntent.HELP -> helpAnswer()
            FushAiIntent.SALES_TODAY -> requirePermission(user, permissions, SecurityPermissions.SALES_VIEW) { salesSummary(question, DateRange.today()) }
            FushAiIntent.SALES_MONTH -> requirePermission(user, permissions, SecurityPermissions.SALES_VIEW) { salesSummary(question, DateRange.currentMonth()) }
            FushAiIntent.CUSTOMER_BALANCE -> requirePermission(user, permissions, SecurityPermissions.CUSTOMERS_VIEW) { customerBalance(question) }
            FushAiIntent.STOCK_ITEM -> requirePermission(user, permissions, SecurityPermissions.INVENTORY_VIEW) { stockItem(question) }
            FushAiIntent.TREASURY_BALANCE -> requirePermission(user, permissions, SecurityPermissions.ACCOUNTING_VIEW) { treasuryBalance() }
            FushAiIntent.OVERDUE_INVOICES -> requirePermission(user, permissions, SecurityPermissions.SALES_VIEW) { overdueInvoices() }
            FushAiIntent.PURCHASES_MONTH -> requirePermission(user, permissions, SecurityPermissions.PURCHASES_VIEW) { purchasesMonth() }
            FushAiIntent.PRODUCTION_STATUS -> requirePermission(user, permissions, SecurityPermissions.PRODUCTION_VIEW) { productionStatus() }
            FushAiIntent.SHIPMENTS_STATUS -> requirePermission(user, permissions, SecurityPermissions.SALES_VIEW) { shipmentsStatus() }
            FushAiIntent.BUSINESS_SUMMARY -> businessSummary(user, permissions)
            FushAiIntent.DRAFT_SALES_INVOICE -> createDraftAnswer(user, FushAiToolPolicy.DRAFT_SALES_INVOICE, question)
            FushAiIntent.DRAFT_TREASURY_VOUCHER -> createDraftAnswer(user, FushAiToolPolicy.DRAFT_TREASURY_VOUCHER, question)
            FushAiIntent.DRAFT_PRODUCTION_ORDER -> createDraftAnswer(user, FushAiToolPolicy.DRAFT_PRODUCTION_ORDER, question)
            FushAiIntent.UNKNOWN -> unknownAnswer()
        }
        return answer.copy(engine = FushAiEngine.LOCAL_SAFE)
    }

    private suspend fun executeRemoteTool(
        user: UserEntity,
        permissions: Set<String>,
        call: FushAiRemoteToolCall,
        question: String,
    ): FushAiAnswer? {
        if (!FushAiToolPolicy.isAllowed(call.name, user.role, permissions)) return null
        val args = call.arguments
        return when (call.name) {
            FushAiToolPolicy.SALES_SUMMARY -> {
                val period = args.optString("period", "today").lowercase()
                val q = if (period == "month") "مبيعات هذا الشهر" else "مبيعات اليوم"
                val range = if (period == "month") DateRange.currentMonth() else DateRange.today()
                requirePermission(user, permissions, SecurityPermissions.SALES_VIEW) { salesSummary(q, range) }
            }
            FushAiToolPolicy.CUSTOMER_BALANCE -> {
                val query = args.optString("query").ifBlank { args.optString("customer") }.ifBlank { "العميل" }
                requirePermission(user, permissions, SecurityPermissions.CUSTOMERS_VIEW) { customerBalance("رصيد العميل $query") }
            }
            FushAiToolPolicy.STOCK_ITEM -> {
                val query = args.optString("query").ifBlank { args.optString("item") }.ifBlank { "الصنف" }
                requirePermission(user, permissions, SecurityPermissions.INVENTORY_VIEW) { stockItem("مخزون $query") }
            }
            FushAiToolPolicy.TREASURY_BALANCES -> requirePermission(user, permissions, SecurityPermissions.ACCOUNTING_VIEW) { treasuryBalance() }
            FushAiToolPolicy.OVERDUE_INVOICES -> requirePermission(user, permissions, SecurityPermissions.SALES_VIEW) { overdueInvoices() }
            FushAiToolPolicy.PURCHASES_SUMMARY -> requirePermission(user, permissions, SecurityPermissions.PURCHASES_VIEW) { purchasesMonth() }
            FushAiToolPolicy.PRODUCTION_STATUS -> requirePermission(user, permissions, SecurityPermissions.PRODUCTION_VIEW) { productionStatus() }
            FushAiToolPolicy.SHIPMENTS_STATUS -> requirePermission(user, permissions, SecurityPermissions.SALES_VIEW) { shipmentsStatus() }
            FushAiToolPolicy.BUSINESS_SUMMARY -> businessSummary(user, permissions)
            FushAiToolPolicy.DRAFT_SALES_INVOICE,
            FushAiToolPolicy.DRAFT_TREASURY_VOUCHER,
            FushAiToolPolicy.DRAFT_PRODUCTION_ORDER -> createDraftAnswer(user, call.name, question, args)
            else -> null
        }
    }

    private suspend fun createDraftAnswer(
        user: UserEntity,
        tool: String,
        question: String,
        args: org.json.JSONObject = org.json.JSONObject(),
    ): FushAiAnswer {
        val actualQuestion = question.ifBlank { args.optString("request").ifBlank { "إنشاء مسودة" } }
        return runCatching { draftService.createFromTool(user, tool, args, actualQuestion) }
            .fold(
                onSuccess = { row ->
                    FushAiAnswer(
                        title = row.title,
                        body = row.summary + "\n\nهذه مسودة AI معزولة. اعتمادها لا ينشئ فاتورة أو سندًا أو أمر إنتاج فعليًا ولا يرحّل أي قيد.",
                        draftId = row.id,
                        draftType = row.draftType,
                        draftStatus = row.status,
                    )
                },
                onFailure = { e -> FushAiAnswer("تعذر إنشاء المسودة", e.message ?: "تعذر إنشاء المسودة") }
            )
    }

    suspend fun approveDraft(user: UserEntity, draftId: Long): FushAiAnswer = runCatching { draftService.approve(user, draftId) }
        .fold(
            onSuccess = { row -> FushAiAnswer("تم اعتماد المسودة", row.summary + "\n\nالحالة: APPROVED. لم يتم تنفيذ أي مستند ERP.", draftId = row.id, draftType = row.draftType, draftStatus = row.status) },
            onFailure = { e -> FushAiAnswer("تعذر اعتماد المسودة", e.message ?: "تعذر اعتماد المسودة") }
        )

    suspend fun cancelDraft(user: UserEntity, draftId: Long): FushAiAnswer = runCatching { draftService.cancel(user, draftId) }
        .fold(
            onSuccess = { row -> FushAiAnswer("تم إلغاء المسودة", "تم إلغاء المسودة #${row.id}. لم يؤثر ذلك على أي مستند ERP.", draftId = row.id, draftType = row.draftType, draftStatus = row.status) },
            onFailure = { e -> FushAiAnswer("تعذر إلغاء المسودة", e.message ?: "تعذر إلغاء المسودة") }
        )

    private suspend fun requirePermission(
        user: UserEntity,
        permissions: Set<String>,
        permission: String,
        block: suspend () -> FushAiAnswer,
    ): FushAiAnswer {
        if (user.role != "ADMIN" && permission !in permissions) {
            return FushAiAnswer(
                "غير مسموح",
                "لا أستطيع عرض هذه البيانات لأن حسابك لا يملك الصلاحية المطلوبة ($permission). المساعد يحترم نفس صلاحيات FUSH ERP ولا يتجاوزها."
            )
        }
        return block()
    }

    private suspend fun businessSummary(user: UserEntity, permissions: Set<String>): FushAiAnswer {
        val lines = mutableListOf<String>()
        val today = DateRange.today()
        if (user.role == "ADMIN" || SecurityPermissions.SALES_VIEW in permissions) {
            val r = db.reportDao().executive(today.from, today.to)
            lines += "• صافي مبيعات اليوم: ${money(r.grossSalesBase - r.salesReturnsBase)}"
            lines += "• تحصيلات اليوم: ${money(r.collectionsBase)}"
        }
        if (user.role == "ADMIN" || SecurityPermissions.PURCHASES_VIEW in permissions) {
            val r = db.reportDao().executive(today.from, today.to)
            lines += "• صافي مشتريات اليوم: ${money(r.grossPurchasesBase - r.purchaseReturnsBase)}"
        }
        if (user.role == "ADMIN" || SecurityPermissions.ACCOUNTING_VIEW in permissions) {
            val balances = db.accountingDao().observeTreasuryBalances().first()
            lines += "• إجمالي رصيد الخزينة بالعملة الأساسية: ${money(balances.sumOf { it.balanceBase })}"
        }
        if (user.role == "ADMIN" || SecurityPermissions.PRODUCTION_VIEW in permissions) {
            val orders = db.productionDao().observeOrderSummaries().first()
            lines += "• أوامر الإنتاج المفتوحة: ${orders.count { it.status.uppercase() !in setOf("CLOSED", "COMPLETED", "CANCELLED") }}"
        }
        if (lines.isEmpty()) {
            return FushAiAnswer("ملخص العمل", "صلاحيات حسابك الحالية لا تسمح بعرض مؤشرات تشغيلية من الأقسام.")
        }
        return FushAiAnswer("ملخص العمل اليوم", lines.joinToString("\n"))
    }

    private suspend fun salesSummary(question: String, range: DateRange): FushAiAnswer {
        val report = db.reportDao().executive(range.from, range.to)
        val net = report.grossSalesBase - report.salesReturnsBase
        val period = if (FushAiIntentParser.detect(question) == FushAiIntent.SALES_TODAY) "اليوم" else "هذا الشهر"
        return FushAiAnswer(
            "مبيعات $period",
            buildString {
                appendLine("إجمالي المبيعات: ${money(report.grossSalesBase)}")
                appendLine("المرتجعات: ${money(report.salesReturnsBase)}")
                appendLine("صافي المبيعات: ${money(net)}")
                append("التحصيلات: ${money(report.collectionsBase)}")
            }
        )
    }

    private suspend fun customerBalance(question: String): FushAiAnswer {
        val customers = db.customerDao().allActive()
        val customer = bestCustomerMatch(question, customers)
            ?: return FushAiAnswer(
                "رصيد العميل",
                "لم أتعرف على اسم العميل. اكتب مثلًا: «كم مديونية العميل أحمد؟» أو استخدم كود العميل."
            )
        val outstanding = db.salesDao().customerOutstandingBase(customer.id)
        val overdue = db.salesDao().overdueInvoiceCountAsOf(customer.id, TrustedTimeService.now())
        return FushAiAnswer(
            "${customer.nameAr} — ${customer.code}",
            buildString {
                appendLine("الرصيد المستحق: ${money(outstanding)}")
                appendLine("الفواتير المتأخرة: $overdue")
                if (customer.province.isNotBlank()) appendLine("الموقع: ${customer.province}")
                if (customer.address.isNotBlank()) append("العنوان: ${customer.address}")
            }.trim()
        )
    }

    private suspend fun stockItem(question: String): FushAiAnswer {
        val items = db.itemDao().allActive()
        val item = bestItemMatch(question, items)
            ?: return FushAiAnswer(
                "المخزون",
                "اذكر اسم الصنف أو كوده. مثال: «كم مخزون فوش؟» أو «مخزون ITEM-001»."
            )
        val warehouses = db.warehouseDao().allActive()
        val balances = warehouses.map { wh -> wh to db.stockDao().balance(wh.id, item.id) }
        val total = balances.sumOf { it.second }
        val nonZero = balances.filter { kotlin.math.abs(it.second) > 0.0000001 }
        return FushAiAnswer(
            "مخزون ${item.nameAr}",
            buildString {
                appendLine("الإجمالي: ${qty(total)} ${item.baseUnitNameFallback()}")
                if (nonZero.isEmpty()) {
                    append("لا يوجد رصيد حالي في المستودعات النشطة.")
                } else {
                    appendLine("حسب المستودع:")
                    nonZero.take(8).forEach { (wh, balance) -> appendLine("• ${wh.nameAr}: ${qty(balance)}") }
                }
            }.trim()
        )
    }

    private suspend fun treasuryBalance(): FushAiAnswer {
        val balances = db.accountingDao().observeTreasuryBalances().first()
        if (balances.isEmpty()) return FushAiAnswer("الخزينة", "لا توجد خزائن مفعلة حاليًا.")
        return FushAiAnswer(
            "أرصدة الخزينة",
            buildString {
                balances.take(12).forEach { row ->
                    appendLine("• ${row.nameAr}: ${money(row.balanceOriginal)} ${row.currencyCode}")
                }
                append("الإجمالي بالعملة الأساسية: ${money(balances.sumOf { it.balanceBase })}")
            }
        )
    }

    private suspend fun overdueInvoices(): FushAiAnswer {
        val now = TrustedTimeService.now()
        val rows = db.salesDao().observeSummaries().first()
            .filter { it.paymentType == "CREDIT" && it.outstandingBase > 0.0000001 && (it.dueDate ?: Long.MAX_VALUE) < now }
            .sortedBy { it.dueDate }
        if (rows.isEmpty()) return FushAiAnswer("الفواتير المتأخرة", "لا توجد فواتير ائتمانية متأخرة مستحقة حاليًا.")
        return FushAiAnswer(
            "الفواتير المتأخرة (${rows.size})",
            buildString {
                appendLine("إجمالي المتأخر: ${money(rows.sumOf { it.outstandingBase })}")
                rows.take(8).forEach { row ->
                    appendLine("• ${row.invoiceNo} — ${row.customerName}: ${money(row.outstandingBase)} — استحقاق ${date(row.dueDate)}")
                }
                if (rows.size > 8) append("… و${rows.size - 8} فواتير أخرى")
            }.trim()
        )
    }

    private suspend fun purchasesMonth(): FushAiAnswer {
        val range = DateRange.currentMonth()
        val report = db.reportDao().executive(range.from, range.to)
        return FushAiAnswer(
            "مشتريات هذا الشهر",
            "إجمالي المشتريات: ${money(report.grossPurchasesBase)}\n" +
                "مرتجعات المشتريات: ${money(report.purchaseReturnsBase)}\n" +
                "صافي المشتريات: ${money(report.grossPurchasesBase - report.purchaseReturnsBase)}"
        )
    }

    private suspend fun productionStatus(): FushAiAnswer {
        val orders = db.productionDao().observeOrderSummaries().first()
        if (orders.isEmpty()) return FushAiAnswer("الإنتاج", "لا توجد أوامر إنتاج مسجلة.")
        val grouped = orders.groupingBy { it.status.ifBlank { "UNKNOWN" } }.eachCount().toList().sortedByDescending { it.second }
        return FushAiAnswer(
            "حالة الإنتاج",
            buildString {
                appendLine("إجمالي الأوامر: ${orders.size}")
                grouped.forEach { (status, count) -> appendLine("• $status: $count") }
                appendLine("أحدث الأوامر:")
                orders.take(5).forEach { row -> appendLine("• ${row.orderNo} — ${row.productName} — ${row.status}${row.batchNo?.let { " — تشغيلة $it" } ?: ""}") }
            }.trim()
        )
    }

    private suspend fun shipmentsStatus(): FushAiAnswer {
        val rows = db.shipmentDao().shipmentSummaries()
        if (rows.isEmpty()) return FushAiAnswer("الشحنات", "لا توجد شحنات مسجلة.")
        val pending = rows.filter { it.status.uppercase() !in setOf("CLOSED", "COMPLETED", "CANCELLED") || it.remainingExpenseBase > 0.0000001 }
        return FushAiAnswer(
            "الشحنات",
            buildString {
                appendLine("إجمالي الشحنات: ${rows.size}")
                appendLine("تحتاج متابعة/تسوية: ${pending.size}")
                pending.take(6).forEach { row ->
                    appendLine("• ${row.shipmentNo} — ${row.destinationProvince} — ${row.status} — متبقي نقل ${money(row.remainingExpenseBase)}")
                }
            }.trim()
        )
    }

    private fun helpAnswer() = FushAiAnswer(
        "ماذا أستطيع أن أفعل؟",
        "أنا مساعد FUSH للقراءة والتحليل، ويمكنني أيضًا إنشاء مسودات معزولة لفاتورة مبيعات أو سند خزينة أو أمر إنتاج. المسودات لا تُرحّل ولا تؤثر على الأرصدة.\n\n" +
            "جرّب:\n" +
            "• مبيعات اليوم\n" +
            "• كم مبيعات هذا الشهر؟\n" +
            "• كم مديونية العميل أحمد؟\n" +
            "• كم مخزون فوش؟\n" +
            "• أرصدة الخزينة\n" +
            "• الفواتير المتأخرة\n" +
            "• مشتريات هذا الشهر\n" +
            "• حالة الإنتاج\n" +
            "• الشحنات غير المسواة\n" +
            "• أعطني ملخص اليوم\n" +
            "• جهز مسودة فاتورة للعميل أحمد 10 قطع فوش\n" +
            "• جهز مسودة سند صرف مبلغ 50000\n" +
            "• جهز مسودة أمر إنتاج 360 قطعة فوش"
    )

    private fun unknownAnswer() = FushAiAnswer(
        "لم أفهم السؤال بعد",
        "اكتب «مساعدة» لعرض الأمثلة. عند استخدام النموذج الخاص تُرسل فقط نتائج الأدوات المسموحة اللازمة لصياغة الرد، ولا يحصل النموذج على قاعدة البيانات أو SQL."
    )

    private fun bestCustomerMatch(question: String, rows: List<CustomerEntity>): CustomerEntity? =
        rows.maxByOrNull { matchScore(question, it.nameAr, it.nameEn, it.code) }?.takeIf { matchScore(question, it.nameAr, it.nameEn, it.code) > 0 }

    private fun bestItemMatch(question: String, rows: List<ItemEntity>): ItemEntity? =
        rows.maxByOrNull { matchScore(question, it.nameAr, it.nameEn, it.code) }?.takeIf { matchScore(question, it.nameAr, it.nameEn, it.code) > 0 }

    private fun matchScore(question: String, vararg candidates: String): Int {
        val q = normalize(question)
        return candidates.maxOfOrNull { candidate ->
            val c = normalize(candidate)
            if (c.isBlank()) 0
            else when {
                q.contains(c) -> 1000 + c.length
                c.contains(q) && q.length >= 3 -> 500 + q.length
                else -> c.split(' ').filter { it.length >= 2 }.count { q.contains(it) } * 10
            }
        } ?: 0
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun money(value: Double): String = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }.format(value)

    private fun qty(value: Double): String = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 3
        minimumFractionDigits = 0
    }.format(value)

    private fun date(value: Long?): String {
        if (value == null) return "غير محدد"
        return Instant.ofEpochMilli(value).atZone(BusinessTimeZone.zoneId).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private fun ItemEntity.baseUnitNameFallback(): String = "وحدة أساسية"
}

data class FushAiConversationTurn(val fromUser: Boolean, val text: String)

enum class FushAiEngine { PRIVATE_LLM, TOOL_FALLBACK, LOCAL_SAFE }

data class FushAiAnswer(
    val title: String,
    val body: String,
    val engine: FushAiEngine = FushAiEngine.LOCAL_SAFE,
    val draftId: Long? = null,
    val draftType: String? = null,
    val draftStatus: String? = null,
)

object FushAiToolPolicy {
    const val SALES_SUMMARY = "sales_summary"
    const val CUSTOMER_BALANCE = "customer_balance"
    const val STOCK_ITEM = "stock_item"
    const val TREASURY_BALANCES = "treasury_balances"
    const val OVERDUE_INVOICES = "overdue_invoices"
    const val PURCHASES_SUMMARY = "purchases_summary"
    const val PRODUCTION_STATUS = "production_status"
    const val SHIPMENTS_STATUS = "shipments_status"
    const val BUSINESS_SUMMARY = "business_summary"
    const val DRAFT_SALES_INVOICE = "draft_sales_invoice"
    const val DRAFT_TREASURY_VOUCHER = "draft_treasury_voucher"
    const val DRAFT_PRODUCTION_ORDER = "draft_production_order"

    fun allowedTools(role: String, permissions: Set<String>): Set<String> {
        if (role == "ADMIN") return setOf(
            SALES_SUMMARY, CUSTOMER_BALANCE, STOCK_ITEM, TREASURY_BALANCES, OVERDUE_INVOICES,
            PURCHASES_SUMMARY, PRODUCTION_STATUS, SHIPMENTS_STATUS, BUSINESS_SUMMARY,
            DRAFT_SALES_INVOICE, DRAFT_TREASURY_VOUCHER, DRAFT_PRODUCTION_ORDER,
        )
        return buildSet {
            if (SecurityPermissions.SALES_VIEW in permissions) {
                add(SALES_SUMMARY); add(OVERDUE_INVOICES); add(SHIPMENTS_STATUS)
            }
            if (SecurityPermissions.CUSTOMERS_VIEW in permissions) add(CUSTOMER_BALANCE)
            if (SecurityPermissions.INVENTORY_VIEW in permissions) add(STOCK_ITEM)
            if (SecurityPermissions.ACCOUNTING_VIEW in permissions) add(TREASURY_BALANCES)
            if (SecurityPermissions.PURCHASES_VIEW in permissions) add(PURCHASES_SUMMARY)
            if (SecurityPermissions.PRODUCTION_VIEW in permissions) add(PRODUCTION_STATUS)
            if (SecurityPermissions.SALES_POST in permissions) add(DRAFT_SALES_INVOICE)
            if (SecurityPermissions.TREASURY_POST in permissions) add(DRAFT_TREASURY_VOUCHER)
            if (SecurityPermissions.PRODUCTION_POST in permissions) add(DRAFT_PRODUCTION_ORDER)
            if (isNotEmpty()) add(BUSINESS_SUMMARY)
        }
    }

    fun isAllowed(tool: String, role: String, permissions: Set<String>): Boolean =
        tool in allowedTools(role, permissions)
}

enum class FushAiIntent {
    HELP,
    SALES_TODAY,
    SALES_MONTH,
    CUSTOMER_BALANCE,
    STOCK_ITEM,
    TREASURY_BALANCE,
    OVERDUE_INVOICES,
    PURCHASES_MONTH,
    PRODUCTION_STATUS,
    SHIPMENTS_STATUS,
    BUSINESS_SUMMARY,
    DRAFT_SALES_INVOICE,
    DRAFT_TREASURY_VOUCHER,
    DRAFT_PRODUCTION_ORDER,
    UNKNOWN,
}

object FushAiIntentParser {
    fun detect(raw: String): FushAiIntent {
        val q = normalize(raw)
        if (q in setOf("مساعده", "مساعدة", "help", "ماذا تستطيع", "وش تقدر تسوي") || q.contains("كيف استخدم")) return FushAiIntent.HELP
        if ((q.contains("مسوده") || q.contains("جهز") || q.contains("انشئ")) && q.contains("فاتور")) return FushAiIntent.DRAFT_SALES_INVOICE
        if ((q.contains("مسوده") || q.contains("جهز") || q.contains("انشئ")) && (q.contains("سند") || q.contains("صرف") || q.contains("قبض"))) return FushAiIntent.DRAFT_TREASURY_VOUCHER
        if ((q.contains("مسوده") || q.contains("جهز") || q.contains("انشئ")) && q.contains("انتاج")) return FushAiIntent.DRAFT_PRODUCTION_ORDER
        if ((q.contains("مديوني") || q.contains("رصيد العميل") || q.contains("ذمه العميل") || q.contains("ذمة العميل")) && q.contains("عميل")) return FushAiIntent.CUSTOMER_BALANCE
        if (q.contains("مخزون") || q.contains("رصيد صنف") || q.contains("كمية صنف")) return FushAiIntent.STOCK_ITEM
        if (q.contains("خزين") || q.contains("صندوق") || q.contains("ارصده الخزين") || q.contains("أرصدة الخزين")) return FushAiIntent.TREASURY_BALANCE
        if (q.contains("متاخر") && q.contains("فاتور") || q.contains("الفواتير المتاخره") || q.contains("الفواتير المتأخرة")) return FushAiIntent.OVERDUE_INVOICES
        if (q.contains("شحن")) return FushAiIntent.SHIPMENTS_STATUS
        if (q.contains("انتاج") || q.contains("إنتاج") || q.contains("اوامر الانتاج") || q.contains("أوامر الإنتاج")) return FushAiIntent.PRODUCTION_STATUS
        if (q.contains("مشتريات") && (q.contains("شهر") || q.contains("هذا الشهر") || q.contains("كم"))) return FushAiIntent.PURCHASES_MONTH
        if (q.contains("مبيعات") && (q.contains("اليوم") || q.contains("يوم"))) return FushAiIntent.SALES_TODAY
        if (q.contains("مبيعات") && (q.contains("شهر") || q.contains("هذا الشهر") || q.contains("كم"))) return FushAiIntent.SALES_MONTH
        if (q.contains("ملخص") || q.contains("الوضع اليوم") || q.contains("اعطني وضع") || q.contains("أعطني وضع")) return FushAiIntent.BUSINESS_SUMMARY
        return FushAiIntent.UNKNOWN
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace('ة', 'ه')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
}

private data class DateRange(val from: Long, val to: Long) {
    companion object {
        fun today(now: Long = TrustedTimeService.now()): DateRange {
            val zone = BusinessTimeZone.zoneId
            val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
            return DateRange(from, to)
        }

        fun currentMonth(now: Long = TrustedTimeService.now()): DateRange {
            val zone = BusinessTimeZone.zoneId
            val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val first = LocalDate.of(day.year, day.monthValue, 1)
            val from = first.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = first.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
            return DateRange(from, to)
        }
    }
}
