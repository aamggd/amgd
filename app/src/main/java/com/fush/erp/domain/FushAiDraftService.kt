package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AuditEventEntity
import com.fush.erp.data.entity.FushAiDraftEntity
import com.fush.erp.data.entity.UserEntity
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * v211 draft boundary. AI may create/approve/cancel rows in fush_ai_drafts only.
 * This service never posts a sales invoice, treasury voucher, production order, journal, or stock movement.
 */
class FushAiDraftService(private val db: FushDatabase) {

    suspend fun createFromTool(user: UserEntity, tool: String, args: JSONObject, question: String): FushAiDraftEntity = db.withTransaction {
        val permissions = if (user.role == "ADMIN") emptySet() else db.securityDao().permissionCodesForRole(user.role).toSet()
        val proposal = when (tool) {
            FushAiToolPolicy.DRAFT_SALES_INVOICE -> buildSalesDraft(user, permissions, args, question)
            FushAiToolPolicy.DRAFT_TREASURY_VOUCHER -> buildTreasuryDraft(user, permissions, args, question)
            FushAiToolPolicy.DRAFT_PRODUCTION_ORDER -> buildProductionDraft(user, permissions, args, question)
            else -> error("أداة المسودة غير معروفة")
        }
        val id = db.fushAiDao().insert(proposal)
        val saved = requireNotNull(db.fushAiDao().byId(id)) { "تعذر حفظ مسودة FUSH AI" }
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = user.id,
                action = "CREATE",
                entityType = "FUSH_AI_DRAFT",
                entityId = id.toString(),
                newValue = "${saved.draftType}|${saved.status}|${saved.summary.take(500)}",
                reason = "إنشاء مسودة بواسطة FUSH AI — لا أثر محاسبي أو مخزني",
            )
        )
        saved
    }

    suspend fun approve(user: UserEntity, draftId: Long): FushAiDraftEntity = db.withTransaction {
        val row = requireNotNull(db.fushAiDao().byId(draftId)) { "المسودة غير موجودة" }
        require(row.requestedBy == user.id || user.role == "ADMIN") { "لا يمكنك اعتماد مسودة مستخدم آخر" }
        require(row.status == "PENDING") { "لا يمكن اعتماد المسودة بحالتها الحالية: ${row.status}" }
        requirePermissionForDraft(user, row.draftType)
        val now = TrustedTimeService.now()
        val updated = row.copy(status = "APPROVED", approvedBy = user.id, approvedAt = now)
        db.fushAiDao().update(updated)
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = user.id,
                action = "APPROVE",
                entityType = "FUSH_AI_DRAFT",
                entityId = draftId.toString(),
                oldValue = "PENDING",
                newValue = "APPROVED",
                reason = "اعتماد مسودة AI فقط — لم يتم إنشاء أو ترحيل مستند ERP",
            )
        )
        updated
    }

    suspend fun cancel(user: UserEntity, draftId: Long): FushAiDraftEntity = db.withTransaction {
        val row = requireNotNull(db.fushAiDao().byId(draftId)) { "المسودة غير موجودة" }
        require(row.requestedBy == user.id || user.role == "ADMIN") { "لا يمكنك إلغاء مسودة مستخدم آخر" }
        require(row.status == "PENDING") { "لا يمكن إلغاء المسودة بحالتها الحالية: ${row.status}" }
        val now = TrustedTimeService.now()
        val updated = row.copy(status = "CANCELLED", cancelledBy = user.id, cancelledAt = now)
        db.fushAiDao().update(updated)
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = user.id,
                action = "CANCEL",
                entityType = "FUSH_AI_DRAFT",
                entityId = draftId.toString(),
                oldValue = "PENDING",
                newValue = "CANCELLED",
                reason = "إلغاء مسودة FUSH AI",
            )
        )
        updated
    }

    private suspend fun buildSalesDraft(
        user: UserEntity,
        permissions: Set<String>,
        args: JSONObject,
        question: String,
    ): FushAiDraftEntity {
        requirePermission(user, permissions, SecurityPermissions.SALES_POST)
        val customers = db.customerDao().allActive()
        val items = db.itemDao().allActive()
        val warehouses = db.warehouseDao().allActive()
        val customerQuery = args.string("customerQuery", "customer", "customerName").ifBlank { question }
        val itemQuery = args.string("itemQuery", "item", "itemName").ifBlank { question }
        val customer = bestMatch(customerQuery, customers) { listOf(it.nameAr, it.nameEn, it.code) }
        val item = bestMatch(itemQuery, items) { listOf(it.nameAr, it.nameEn, it.code) }
        val quantity = args.positiveDouble("quantity") ?: quantityFromQuestion(question)
        val unitPrice = args.positiveDouble("unitPrice")
        val paymentType = when {
            args.optString("paymentType").equals("CREDIT", true) || normalize(question).contains("اجل") -> "CREDIT"
            else -> "CASH"
        }
        val warehouse = warehouses.firstOrNull()
        val missing = buildList {
            if (customer == null) add("العميل")
            if (item == null) add("الصنف")
            if (quantity == null) add("الكمية")
            if (unitPrice == null) add("سعر الوحدة/قائمة السعر")
            if (warehouse == null) add("المخزن")
            add("رقم الفاتورة النهائي")
        }
        val payload = JSONObject()
            .put("draftVersion", 1)
            .put("customerId", customer?.id ?: JSONObject.NULL)
            .put("customerCode", customer?.code ?: "")
            .put("customerName", customer?.nameAr ?: customerQuery.take(120))
            .put("itemId", item?.id ?: JSONObject.NULL)
            .put("itemCode", item?.code ?: "")
            .put("itemName", item?.nameAr ?: itemQuery.take(120))
            .put("quantity", quantity ?: JSONObject.NULL)
            .put("unitPriceOriginal", unitPrice ?: JSONObject.NULL)
            .put("paymentType", paymentType)
            .put("currencyCode", customer?.currencyCode ?: args.optString("currencyCode", "YER_NEW"))
            .put("warehouseId", warehouse?.id ?: JSONObject.NULL)
            .put("warehouseName", warehouse?.nameAr ?: "")
            .put("notes", args.optString("notes").ifBlank { question.take(500) })
            .put("missingFields", JSONArray(missing))
        val summary = buildString {
            appendLine("النوع: فاتورة مبيعات (مسودة فقط)")
            appendLine("العميل: ${customer?.let { "${it.code} — ${it.nameAr}" } ?: "غير محدد"}")
            appendLine("الصنف: ${item?.let { "${it.code} — ${it.nameAr}" } ?: "غير محدد"}")
            appendLine("الكمية: ${quantity?.toString() ?: "غير محددة"}")
            appendLine("السعر: ${unitPrice?.toString() ?: "يحدد من قائمة السعر/النموذج النهائي"}")
            appendLine("نوع البيع: ${if (paymentType == "CREDIT") "آجل" else "نقدي"}")
            if (missing.isNotEmpty()) append("الحقول التي تحتاج مراجعة: ${missing.joinToString("، ")}")
        }.trim()
        return FushAiDraftEntity(
            draftType = "SALES_INVOICE",
            title = "مسودة فاتورة مبيعات",
            summary = summary,
            payloadJson = payload.toString(),
            requestedBy = user.id,
        )
    }

    private suspend fun buildTreasuryDraft(
        user: UserEntity,
        permissions: Set<String>,
        args: JSONObject,
        question: String,
    ): FushAiDraftEntity {
        requirePermission(user, permissions, SecurityPermissions.TREASURY_POST)
        val treasuries = db.accountingDao().allActiveTreasury()
        val accounts = db.accountDao().allActive().filter { it.isPosting }
        val q = normalize(question)
        val type = args.optString("type").uppercase().takeIf { it in setOf("RECEIPT", "PAYMENT", "INCOME", "EXPENSE", "TRANSFER") }
            ?: when {
                q.contains("صرف") || q.contains("دفع") -> "PAYMENT"
                q.contains("قبض") || q.contains("تحصيل") -> "RECEIPT"
                q.contains("مصروف") -> "EXPENSE"
                q.contains("دخل") || q.contains("ايراد") -> "INCOME"
                q.contains("تحويل") -> "TRANSFER"
                else -> "PAYMENT"
            }
        val currencyCode = detectCurrency(args.optString("currencyCode"), question)
        val treasuryQuery = args.string("treasuryQuery", "treasury", "cashbox").ifBlank { question }
        val treasury = bestMatch(treasuryQuery, treasuries.filter { currencyCode.isBlank() || it.currencyCode == currencyCode }) {
            listOf(it.nameAr, it.code, it.groupCode)
        } ?: treasuries.firstOrNull { currencyCode.isBlank() || it.currencyCode == currencyCode }
        val offsetCode = args.optString("offsetAccountCode").trim()
        val offset = accounts.firstOrNull { offsetCode.isNotBlank() && it.code.equals(offsetCode, true) }
        val amount = args.positiveDouble("amount") ?: moneyFromQuestion(question)
        val missing = buildList {
            if (treasury == null) add("الخزينة/البنك والعملة المطابقة")
            if (amount == null) add("المبلغ")
            if (type != "TRANSFER" && offset == null) add("الحساب المقابل")
            if (type == "TRANSFER") add("الخزينة الهدف")
        }
        val payload = JSONObject()
            .put("draftVersion", 1)
            .put("voucherType", type)
            .put("treasuryAccountId", treasury?.id ?: JSONObject.NULL)
            .put("treasuryName", treasury?.nameAr ?: "")
            .put("currencyCode", currencyCode.ifBlank { treasury?.currencyCode ?: "" })
            .put("amountOriginal", amount ?: JSONObject.NULL)
            .put("offsetAccountId", offset?.id ?: JSONObject.NULL)
            .put("offsetAccountCode", offset?.code ?: offsetCode)
            .put("description", args.optString("description").ifBlank { question.take(500) })
            .put("missingFields", JSONArray(missing))
        val summary = buildString {
            appendLine("النوع: سند خزينة (مسودة فقط) — $type")
            appendLine("الخزينة: ${treasury?.let { "${it.nameAr} / ${it.currencyCode}" } ?: "غير محددة"}")
            appendLine("المبلغ: ${amount?.toString() ?: "غير محدد"} ${currencyCode.ifBlank { treasury?.currencyCode.orEmpty() }}")
            appendLine("الحساب المقابل: ${offset?.let { "${it.code} — ${it.nameAr}" } ?: "غير محدد"}")
            if (missing.isNotEmpty()) append("الحقول التي تحتاج مراجعة: ${missing.joinToString("، ")}")
        }.trim()
        return FushAiDraftEntity(
            draftType = "TREASURY_VOUCHER",
            title = "مسودة سند خزينة",
            summary = summary,
            payloadJson = payload.toString(),
            requestedBy = user.id,
        )
    }

    private suspend fun buildProductionDraft(
        user: UserEntity,
        permissions: Set<String>,
        args: JSONObject,
        question: String,
    ): FushAiDraftEntity {
        requirePermission(user, permissions, SecurityPermissions.PRODUCTION_POST)
        val recipes = db.recipeDao().observeSummaries().first().filter { it.status == "ACTIVE" }
        val warehouses = db.warehouseDao().allActive()
        val employees = db.employeeDao().observeActiveEmployees().first()
        val recipeQuery = args.string("recipeQuery", "product", "recipe").ifBlank { question }
        val recipe = bestMatch(recipeQuery, recipes) { listOf(it.productName, it.code) }
        val quantity = args.positiveDouble("quantity") ?: quantityFromQuestion(question)
        val raw = warehouses.firstOrNull { normalize(it.code + " " + it.nameAr).let { s -> s.contains("raw") || s.contains("rm") || s.contains("خام") } }
            ?: warehouses.firstOrNull()
        val finished = warehouses.firstOrNull { normalize(it.code + " " + it.nameAr).let { s -> s.contains("fg") || s.contains("finished") || s.contains("نهائي") } }
            ?: warehouses.firstOrNull { it.id != raw?.id }
        val employeeQuery = args.string("employeeQuery", "employee", "operator")
        val employee = if (employeeQuery.isBlank()) null else bestMatch(employeeQuery, employees) { listOf(it.fullNameAr, it.code) }
        val missing = buildList {
            if (recipe == null) add("الوصفة/المنتج")
            if (quantity == null) add("كمية الإنتاج المخططة")
            if (raw == null) add("مخزن المواد الخام")
            if (finished == null) add("مخزن المنتج النهائي")
            if (employee == null) add("موظف الإنتاج")
        }
        val payload = JSONObject()
            .put("draftVersion", 1)
            .put("recipeId", recipe?.id ?: JSONObject.NULL)
            .put("recipeCode", recipe?.code ?: "")
            .put("productName", recipe?.productName ?: recipeQuery.take(120))
            .put("plannedOutputQtyBase", quantity ?: JSONObject.NULL)
            .put("rawWarehouseId", raw?.id ?: JSONObject.NULL)
            .put("rawWarehouseName", raw?.nameAr ?: "")
            .put("finishedWarehouseId", finished?.id ?: JSONObject.NULL)
            .put("finishedWarehouseName", finished?.nameAr ?: "")
            .put("operatorEmployeeId", employee?.id ?: JSONObject.NULL)
            .put("operatorName", employee?.fullNameAr ?: "")
            .put("notes", args.optString("notes").ifBlank { question.take(500) })
            .put("missingFields", JSONArray(missing))
        val summary = buildString {
            appendLine("النوع: أمر إنتاج (مسودة فقط)")
            appendLine("الوصفة/المنتج: ${recipe?.let { "${it.code} — ${it.productName}" } ?: "غير محدد"}")
            appendLine("الكمية المخططة: ${quantity?.toString() ?: "غير محددة"}")
            appendLine("مخزن الخام: ${raw?.nameAr ?: "غير محدد"}")
            appendLine("مخزن النهائي: ${finished?.nameAr ?: "غير محدد"}")
            appendLine("موظف الإنتاج: ${employee?.fullNameAr ?: "غير محدد"}")
            if (missing.isNotEmpty()) append("الحقول التي تحتاج مراجعة: ${missing.joinToString("، ")}")
        }.trim()
        return FushAiDraftEntity(
            draftType = "PRODUCTION_ORDER",
            title = "مسودة أمر إنتاج",
            summary = summary,
            payloadJson = payload.toString(),
            requestedBy = user.id,
        )
    }

    private suspend fun requirePermissionForDraft(user: UserEntity, draftType: String) {
        val permission = when (draftType) {
            "SALES_INVOICE" -> SecurityPermissions.SALES_POST
            "TREASURY_VOUCHER" -> SecurityPermissions.TREASURY_POST
            "PRODUCTION_ORDER" -> SecurityPermissions.PRODUCTION_POST
            else -> error("نوع المسودة غير معروف")
        }
        if (user.role != "ADMIN") db.requireUserPermission(user.id, permission)
    }

    private fun requirePermission(user: UserEntity, permissions: Set<String>, permission: String) {
        require(user.role == "ADMIN" || permission in permissions) { "لا تملك الصلاحية المطلوبة لإنشاء هذه المسودة ($permission)" }
    }

    private fun JSONObject.string(vararg names: String): String = names.asSequence()
        .map { optString(it).trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()

    private fun JSONObject.positiveDouble(name: String): Double? = when (val raw = opt(name)) {
        is Number -> raw.toDouble().takeIf { it > 0.0 && it.isFinite() }
        is String -> raw.replace(",", "").toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }
        else -> null
    }

    private fun quantityFromQuestion(question: String): Double? {
        val n = normalizeDigits(question)
        val regex = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:قطعه|قطعة|حبه|حبة|وحده|وحدة|كرتون|كرتونه|كرتونة|كيلو|كجم)")
        return regex.find(n)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    private fun moneyFromQuestion(question: String): Double? {
        val n = normalizeDigits(question).replace(",", "")
        val regex = Regex("(?:مبلغ|بقيمة|قيمه|صرف|قبض|دفع|تحصيل)\\s*(\\d+(?:\\.\\d+)?)")
        return regex.find(n)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.takeIf { it > 0.0 }
            ?: Regex("\\b(\\d{2,}(?:\\.\\d+)?)\\b").find(n)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    private fun detectCurrency(arg: String, question: String): String {
        val raw = normalize(arg + " " + question)
        return when {
            raw.contains("yer old") || raw.contains("طبعه قديم") || raw.contains("طبعة قديم") || raw.contains("قديم") -> "YER_OLD"
            raw.contains("usd") || raw.contains("دولار") -> "USD"
            raw.contains("sar") || raw.contains("سعود") -> "SAR"
            raw.contains("yer new") || raw.contains("طبعه جديد") || raw.contains("طبعة جديد") || raw.contains("جديد") -> "YER_NEW"
            else -> arg.trim().uppercase()
        }
    }

    private fun normalizeDigits(value: String): String = value
        .replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3').replace('٤', '4')
        .replace('٥', '5').replace('٦', '6').replace('٧', '7').replace('٨', '8').replace('٩', '9')

    private fun normalize(value: String): String = normalizeDigits(value)
        .lowercase()
        .replace(Regex("[ًٌٍَُِّْـ]"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun <T> bestMatch(query: String, rows: List<T>, values: (T) -> List<String>): T? {
        val q = normalize(query)
        if (q.isBlank()) return null
        return rows.maxByOrNull { row ->
            values(row).maxOfOrNull { raw ->
                val c = normalize(raw)
                when {
                    c.isBlank() -> 0
                    q.contains(c) -> 1000 + c.length
                    c.contains(q) && q.length >= 3 -> 500 + q.length
                    else -> c.split(' ').filter { it.length >= 2 }.count { q.contains(it) } * 10
                }
            } ?: 0
        }?.takeIf { row -> values(row).any { raw ->
            val c = normalize(raw)
            c.isNotBlank() && (q.contains(c) || (c.contains(q) && q.length >= 3) || c.split(' ').any { it.length >= 2 && q.contains(it) })
        } }
    }
}
