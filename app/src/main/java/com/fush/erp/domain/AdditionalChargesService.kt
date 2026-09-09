package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.*
import kotlin.math.abs

/**
 * v179 additional-charge accounting.
 *
 * Accounting policy is configured per charge type and snapshotted onto each transaction. A charge
 * name never implies principal/agent treatment by itself.
 */
class AdditionalChargesService(private val db: FushDatabase) {
    data class ChargeDraft(
        val chargeTypeId: Long,
        val description: String = "",
        val amountOriginal: Double,
        val currencyCode: String,
        val exchangeRate: Double,
        val bearer: String,
        val paymentStatus: String,
        val paidBy: String,
        val paidAmountOriginal: Double = 0.0,
        val treasuryAccountId: Long? = null,
        val paymentDate: Long? = null,
        val paymentReference: String = "",
        val accountingTreatment: String,
        val notes: String = ""
    )

    data class ExistingChargeAllocation(
        val chargeId: Long,
        val amountChargeOriginal: Double
    )

    data class PostedChargeResult(
        val charge: SalesAdditionalChargeEntity,
        val initialPaymentId: Long?
    )

    suspend fun postPreInvoiceCharge(
        customerId: Long,
        chargeDate: Long,
        draft: ChargeDraft,
        createdBy: Long
    ): PostedChargeResult = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.SALES_POST)
        FutureDocumentDatePolicy.requireNotFuture(chargeDate, "تاريخ الرسم الإضافي")
        AccountingService(db).requirePostingPeriodOpen(chargeDate)
        val customer = requireNotNull(db.customerDao().byId(CustomerMovementIdentity.requireId(customerId))) { "العميل غير موجود" }
        createChargeInsideTransaction(customer, chargeDate, draft, createdBy)
    }

    suspend fun createChargeInsideTransaction(
        customer: CustomerEntity,
        chargeDate: Long,
        draft: ChargeDraft,
        createdBy: Long
    ): PostedChargeResult {
        validateDraft(draft)
        val type = requireNotNull(db.additionalChargesDao().typeById(draft.chargeTypeId)) { "نوع الرسم الإضافي غير موجود" }
        require(type.isActive) { "نوع الرسم الإضافي غير نشط" }
        val historicalRate = exchangeRateAt(draft.currencyCode, chargeDate)
        require(abs(draft.exchangeRate - historicalRate) <= 0.0000001) {
            "سعر صرف الرسم لا يطابق السعر المعتمد في تاريخ العملية. السعر المطلوب: $historicalRate"
        }
        val paidOriginal = normalizedPaidAmount(draft)
        if (draft.paidBy == "COMPANY" && paidOriginal > 1e-9) {
            val treasury = requireNotNull(draft.treasuryAccountId?.let { db.accountingDao().treasuryById(it) }) { "حساب الدفع (صندوق/بنك) مطلوب" }
            require(treasury.isActive) { "حساب الدفع غير نشط" }
            require(treasury.currencyCode == draft.currencyCode) { "عملة حساب الدفع يجب أن تطابق عملة الرسم" }
            requireNotNull(draft.paymentDate) { "تاريخ الدفع مطلوب" }
            FutureDocumentDatePolicy.requireNotFuture(draft.paymentDate, "تاريخ دفع الرسم")
            require(draft.paymentDate == chargeDate) { "الدفعة الأولية يجب أن تكون في تاريخ إثبات الرسم نفسه. للدفعات اللاحقة استخدم سند دفع رسم مستقل" }
            AccountingService(db).requirePostingPeriodOpen(draft.paymentDate)
        }
        if (draft.paidBy == "CUSTOMER_DIRECT") {
            require(draft.bearer == "CUSTOMER") { "الدفع المباشر بواسطة العميل لا يستخدم مع رسم تتحمله الشركة" }
            require(draft.accountingTreatment == "RECOVERABLE") { "الدفع المباشر بواسطة العميل مخصص للرسوم القابلة للاسترداد/الوكالة، وليس لإيراد خدمة أو مصروف شركة" }
            require(draft.treasuryAccountId == null) { "لا يحدد صندوق أو بنك للشركة عندما يدفع العميل مباشرة" }
        }
        require(
            (draft.bearer == "CUSTOMER" && draft.accountingTreatment in setOf("RECOVERABLE", "SERVICE_REVENUE")) ||
                (draft.bearer == "COMPANY" && draft.accountingTreatment == "COMPANY_EXPENSE")
        ) { "نوع المعالجة المحاسبية لا يتوافق مع الطرف الذي يتحمل الرسم" }
        if (draft.accountingTreatment == "SERVICE_REVENUE") {
            require(draft.paidBy == "COMPANY" && paidOriginal <= 1e-9) {
                "إيراد الخدمة يُثبت عند الفاتورة. سجّل أي تكلفة فعلية للخدمة كسطر مصروف مستقل على الشركة"
            }
        }

        val chargeNo = nextChargeNo(chargeDate)
        val amountBase = SalesMath.toBaseAmount(draft.amountOriginal, draft.exchangeRate)
        val chargeId = db.additionalChargesDao().insertCharge(
            SalesAdditionalChargeEntity(
                chargeNo = chargeNo,
                customerId = customer.id,
                chargeTypeId = type.id,
                chargeDate = chargeDate,
                description = draft.description.trim(),
                amountOriginal = draft.amountOriginal,
                currencyCode = draft.currencyCode,
                exchangeRate = draft.exchangeRate,
                amountBase = amountBase,
                bearer = draft.bearer,
                paymentStatus = paymentStatus(draft.amountOriginal, paidOriginal),
                paidBy = draft.paidBy,
                accountingTreatment = draft.accountingTreatment,
                principalAgentModeSnapshot = type.principalAgentMode,
                recoverableAccountId = type.recoverableAccountId,
                expenseAccountId = type.expenseAccountId,
                revenueAccountId = type.revenueAccountId,
                payableAccountId = type.payableAccountId,
                notes = draft.notes.trim(),
                createdBy = createdBy
            )
        )
        var paymentId: Long? = null
        if (paidOriginal > 1e-9) {
            val paymentDate = requireNotNull(draft.paymentDate) { "تاريخ الدفع مطلوب" }
            val paymentNo = nextPaymentNo(paymentDate)
            paymentId = db.additionalChargesDao().insertPayment(
                SalesAdditionalChargePaymentEntity(
                    paymentNo = paymentNo,
                    chargeId = chargeId,
                    paymentDate = paymentDate,
                    amountOriginal = paidOriginal,
                    currencyCode = draft.currencyCode,
                    exchangeRate = draft.exchangeRate,
                    amountBase = paidOriginal * draft.exchangeRate,
                    paidBy = draft.paidBy,
                    treasuryAccountId = if (draft.paidBy == "COMPANY") draft.treasuryAccountId else null,
                    paymentReference = draft.paymentReference.trim(),
                    notes = draft.notes.trim(),
                    createdBy = createdBy
                )
            )
        }
        val charge = requireNotNull(db.additionalChargesDao().chargeById(chargeId)) { "تعذر إعادة قراءة الرسم الإضافي" }
        postRecognitionJournal(charge, paidOriginal, draft.treasuryAccountId, createdBy)
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = createdBy,
                action = "POST",
                entityType = "SALES_ADDITIONAL_CHARGE",
                entityId = chargeId.toString(),
                newValue = "charge=$chargeNo|customer=${customer.code}|type=${type.code}|amount=${draft.amountOriginal}|currency=${draft.currencyCode}|bearer=${draft.bearer}|paidBy=${draft.paidBy}|treatment=${draft.accountingTreatment}|principalAgent=${type.principalAgentMode}",
                reason = draft.notes.trim().ifBlank { "إثبات رسوم وتكاليف إضافية" }
            )
        )
        return PostedChargeResult(charge, paymentId)
    }

    suspend fun postAdditionalPayment(
        chargeId: Long,
        amountOriginal: Double,
        paymentDate: Long,
        exchangeRate: Double,
        treasuryAccountId: Long?,
        paidBy: String,
        paymentReference: String,
        notes: String,
        createdBy: Long
    ): SalesAdditionalChargePaymentEntity = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.SALES_POST)
        val charge = requireNotNull(db.additionalChargesDao().chargeById(chargeId)) { "الرسم الإضافي غير موجود" }
        require(charge.status == "POSTED") { "لا يمكن الدفع على رسم ملغى" }
        require(paidBy == charge.paidBy) { "طريقة الدفع يجب أن تطابق إعداد الرسم الأصلي" }
        require(amountOriginal > 0.0 && amountOriginal.isFinite()) { "مبلغ الدفع يجب أن يكون أكبر من صفر" }
        FutureDocumentDatePolicy.requireNotFuture(paymentDate, "تاريخ دفع الرسم")
        require(paymentDate >= charge.chargeDate) { "تاريخ الدفع لا يمكن أن يسبق تاريخ الرسم" }
        AccountingService(db).requirePostingPeriodOpen(paymentDate)
        val historicalRate = exchangeRateAt(charge.currencyCode, paymentDate)
        require(abs(exchangeRate - historicalRate) <= 0.0000001) { "سعر الصرف لا يطابق السعر المعتمد في تاريخ الدفع. السعر المطلوب: $historicalRate" }
        val paidBefore = db.additionalChargesDao().paidOriginalForCharge(chargeId)
        val remaining = (charge.amountOriginal - paidBefore).coerceAtLeast(0.0)
        require(amountOriginal <= remaining + 1e-9) { "مبلغ الدفع يتجاوز المتبقي على الرسم. المتبقي المتاح: %.2f %s".format(remaining, charge.currencyCode) }

        val treasury = if (paidBy == "COMPANY") {
            requireNotNull(treasuryAccountId?.let { db.accountingDao().treasuryById(it) }) { "حساب الدفع (صندوق/بنك) مطلوب" }.also {
                require(it.isActive) { "حساب الدفع غير نشط" }
                require(it.currencyCode == charge.currencyCode) { "عملة حساب الدفع يجب أن تطابق عملة الرسم" }
            }
        } else {
            require(treasuryAccountId == null) { "لا تستخدم خزينة الشركة عند الدفع المباشر بواسطة العميل" }
            null
        }

        val paymentNo = nextPaymentNo(paymentDate)
        val paymentBase = amountOriginal * exchangeRate
        val paymentId = db.additionalChargesDao().insertPayment(
            SalesAdditionalChargePaymentEntity(
                paymentNo = paymentNo,
                chargeId = charge.id,
                paymentDate = paymentDate,
                amountOriginal = amountOriginal,
                currencyCode = charge.currencyCode,
                exchangeRate = exchangeRate,
                amountBase = paymentBase,
                paidBy = paidBy,
                treasuryAccountId = treasury?.id,
                paymentReference = paymentReference.trim(),
                notes = notes.trim(),
                createdBy = createdBy
            )
        )
        if (paidBy == "COMPANY" && charge.accountingTreatment != "SERVICE_REVENUE") {
            val carryingBase = amountOriginal * charge.exchangeRate
            val lines = mutableListOf(
                DraftJournalLine(charge.payableAccountId, carryingBase, 0.0),
                DraftJournalLine(requireNotNull(treasury).accountId, 0.0, paymentBase)
            )
            val diff = paymentBase - carryingBase
            if (diff > 1e-9) lines += DraftJournalLine(requireAccount("6750").id, diff, 0.0)
            else if (diff < -1e-9) lines += DraftJournalLine(requireAccount("4250").id, 0.0, -diff)
            postJournal(
                entryNo = "JE-$paymentNo",
                date = paymentDate,
                description = "سداد رسم إضافي ${charge.chargeNo}",
                currencyCode = charge.currencyCode,
                exchangeRate = exchangeRate,
                sourceType = "ADDITIONAL_CHARGE_PAYMENT",
                sourceId = paymentId.toString(),
                createdBy = createdBy,
                lines = lines
            )
        }
        refreshPaymentStatus(charge)
        requireNotNull(db.additionalChargesDao().paymentById(paymentId))
    }

    suspend fun updateTypePolicy(
        typeId: Long,
        defaultBearer: String,
        defaultTreatment: String,
        principalAgentMode: String,
        recoverableAccountId: Long,
        expenseAccountId: Long,
        revenueAccountId: Long,
        payableAccountId: Long,
        updatedBy: Long
    ) = db.withTransaction {
        db.requireUserPermission(updatedBy, SecurityPermissions.ACCOUNTING_POST)
        val old = requireNotNull(db.additionalChargesDao().typeById(typeId)) { "نوع الرسم غير موجود" }
        validatePolicy(defaultBearer, defaultTreatment, principalAgentMode)
        listOf(recoverableAccountId, expenseAccountId, revenueAccountId, payableAccountId).forEach { requireNotNull(db.accountDao().byId(it)) { "أحد الحسابات المحددة غير موجود" } }
        db.additionalChargesDao().updateType(
            old.copy(
                defaultBearer = defaultBearer,
                defaultAccountingTreatment = defaultTreatment,
                principalAgentMode = principalAgentMode,
                recoverableAccountId = recoverableAccountId,
                expenseAccountId = expenseAccountId,
                revenueAccountId = revenueAccountId,
                payableAccountId = payableAccountId
            )
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = updatedBy,
                action = "UPDATE_POLICY",
                entityType = "ADDITIONAL_CHARGE_TYPE",
                entityId = typeId.toString(),
                oldValue = "bearer=${old.defaultBearer}|treatment=${old.defaultAccountingTreatment}|principalAgent=${old.principalAgentMode}",
                newValue = "bearer=$defaultBearer|treatment=$defaultTreatment|principalAgent=$principalAgentMode",
                reason = "تحديث سياسة الرسوم الإضافية / Principal-Agent"
            )
        )
    }

    suspend fun cancelCharge(chargeId: Long, reason: String, cancelledBy: Long, cancellationDate: Long): SalesAdditionalChargeEntity = db.withTransaction {
        db.requireUserPermission(cancelledBy, SecurityPermissions.SALES_POST)
        require(reason.trim().isNotBlank()) { "سبب إلغاء الرسم مطلوب" }
        FutureDocumentDatePolicy.requireNotFuture(cancellationDate, "تاريخ إلغاء الرسم")
        AccountingService(db).requirePostingPeriodOpen(cancellationDate)
        val charge = requireNotNull(db.additionalChargesDao().chargeById(chargeId)) { "الرسم الإضافي غير موجود" }
        require(charge.status == "POSTED") { "تم إلغاء الرسم مسبقاً" }
        val activeSettlements = db.additionalChargesDao().settlementsForCharge(chargeId).filter { it.status == "ACTIVE" }
        require(activeSettlements.isEmpty()) { "الرسم مرتبط بفواتير مرحلة. يجب إلغاء/عكس الفاتورة أولاً حتى تُفتح التسويات تلقائياً" }
        val relatedJournals = buildList {
            db.journalDao().bySource("ADDITIONAL_CHARGE", charge.id.toString())?.let(::add)
            db.additionalChargesDao().paymentsForCharge(charge.id).forEach { p -> db.journalDao().bySource("ADDITIONAL_CHARGE_PAYMENT", p.id.toString())?.let(::add) }
        }
        relatedJournals.forEach { reverseJournal(it, reason, cancelledBy, cancellationDate) }
        val cancelled = charge.copy(status="CANCELLED", cancelledBy=cancelledBy, cancelledAt=cancellationDate, cancellationReason=reason.trim())
        db.additionalChargesDao().updateCharge(cancelled)
        db.governanceDao().insertAudit(AuditEventEntity(userId=cancelledBy, action="CANCEL", entityType="SALES_ADDITIONAL_CHARGE", entityId=charge.id.toString(), oldValue=charge.status, newValue="CANCELLED", reason=reason.trim()))
        cancelled
    }

    suspend fun reopenInvoiceSettlements(invoiceId: Long, reason: String, reversedBy: Long, reversalDate: Long) {
        require(reason.isNotBlank()) { "سبب عكس التسوية مطلوب" }
        db.additionalChargesDao().settlementsForInvoice(invoiceId).filter { it.status == "ACTIVE" }.forEach { row ->
            db.additionalChargesDao().updateSettlement(row.copy(status="REVERSED", reversedBy=reversedBy, reversedAt=reversalDate, reversalReason=reason.trim()))
        }
    }

    suspend fun exchangeRateAt(currencyCode: String, date: Long): Double {
        if (currencyCode == "YER_NEW") return 1.0
        val row = requireNotNull(db.currencyDao().latestRateAt(currencyCode, BusinessDatePolicy.endOfBusinessDay(date))) { "لا يوجد سعر صرف للعملة $currencyCode في التاريخ المحدد" }
        require(row.rateToBase > 0.0 && row.rateToBase.isFinite()) { "سعر الصرف المعتمد غير صالح" }
        return row.rateToBase
    }

    private suspend fun postRecognitionJournal(charge: SalesAdditionalChargeEntity, paidOriginal: Double, treasuryAccountId: Long?, createdBy: Long) {
        if (charge.accountingTreatment == "SERVICE_REVENUE" || charge.paidBy == "CUSTOMER_DIRECT") return
        val debitAccountId = if (charge.accountingTreatment == "RECOVERABLE") charge.recoverableAccountId else charge.expenseAccountId
        val paidBase = paidOriginal * charge.exchangeRate
        val unpaidBase = (charge.amountBase - paidBase).coerceAtLeast(0.0)
        val lines = mutableListOf(DraftJournalLine(debitAccountId, charge.amountBase, 0.0))
        if (paidBase > 1e-9) {
            val treasury = requireNotNull(treasuryAccountId?.let { db.accountingDao().treasuryById(it) }) { "حساب الدفع غير موجود" }
            lines += DraftJournalLine(treasury.accountId, 0.0, paidBase)
        }
        if (unpaidBase > 1e-9) lines += DraftJournalLine(charge.payableAccountId, 0.0, unpaidBase)
        postJournal(
            entryNo = "JE-${charge.chargeNo}",
            date = charge.chargeDate,
            description = if (charge.accountingTreatment == "RECOVERABLE") "مبلغ قابل للاسترداد من العميل ${charge.chargeNo}" else "مصروف إضافي على الشركة ${charge.chargeNo}",
            currencyCode = charge.currencyCode,
            exchangeRate = charge.exchangeRate,
            sourceType = "ADDITIONAL_CHARGE",
            sourceId = charge.id.toString(),
            createdBy = createdBy,
            lines = lines
        )
    }

    private suspend fun refreshPaymentStatus(charge: SalesAdditionalChargeEntity) {
        val paid = db.additionalChargesDao().paidOriginalForCharge(charge.id)
        db.additionalChargesDao().updateCharge(charge.copy(paymentStatus = paymentStatus(charge.amountOriginal, paid)))
    }

    private fun paymentStatus(total: Double, paid: Double): String = when {
        paid <= 1e-9 -> "UNPAID"
        paid + 1e-9 >= total -> "PAID"
        else -> "PARTIAL"
    }

    private fun normalizedPaidAmount(draft: ChargeDraft): Double {
        val paid = when (draft.paymentStatus) {
            "UNPAID" -> 0.0
            "PAID" -> draft.amountOriginal
            "PARTIAL" -> draft.paidAmountOriginal
            else -> error("حالة الدفع غير صالحة")
        }
        require(paid >= 0.0 && paid.isFinite() && paid <= draft.amountOriginal + 1e-9) { "المبلغ المدفوع غير صالح" }
        if (draft.paymentStatus == "PARTIAL") require(paid > 1e-9 && paid < draft.amountOriginal - 1e-9) { "في حالة الدفع الجزئي يجب أن يكون المدفوع أكبر من صفر وأقل من قيمة الرسم" }
        return paid
    }

    private fun validateDraft(draft: ChargeDraft) {
        require(draft.chargeTypeId > 0) { "نوع الرسم مطلوب" }
        require(draft.amountOriginal > 0.0 && draft.amountOriginal.isFinite()) { "مبلغ الرسم يجب أن يكون أكبر من صفر" }
        SalesMath.validateExchangeRate(draft.exchangeRate)
        require(draft.bearer in setOf("CUSTOMER", "COMPANY")) { "الطرف المتحمل غير صالح" }
        require(draft.paymentStatus in setOf("PAID", "UNPAID", "PARTIAL")) { "حالة الدفع غير صالحة" }
        require(draft.paidBy in setOf("COMPANY", "CUSTOMER_DIRECT")) { "جهة الدفع غير صالحة" }
        require(draft.accountingTreatment in setOf("RECOVERABLE", "COMPANY_EXPENSE", "SERVICE_REVENUE")) { "المعالجة المحاسبية غير صالحة" }
    }

    private fun validatePolicy(bearer: String, treatment: String, mode: String) {
        require(bearer in setOf("CUSTOMER", "COMPANY")) { "الطرف المتحمل غير صالح" }
        require(treatment in setOf("RECOVERABLE", "COMPANY_EXPENSE", "SERVICE_REVENUE")) { "المعالجة المحاسبية غير صالحة" }
        require(mode in setOf("REVIEW", "PRINCIPAL", "AGENT")) { "إعداد أصيل/وكيل غير صالح" }
        require((bearer == "CUSTOMER" && treatment in setOf("RECOVERABLE", "SERVICE_REVENUE")) ||
            (bearer == "COMPANY" && treatment == "COMPANY_EXPENSE")) { "المعالجة الافتراضية لا تتوافق مع الطرف المتحمل" }
    }

    private suspend fun nextChargeNo(date: Long): String {
        var candidate: String
        do { candidate = "ACH-${AutoNumberService(db).nextDocumentNo("ACH", date)}" } while (db.additionalChargesDao().chargeNoCount(candidate) > 0)
        return candidate
    }

    private suspend fun nextPaymentNo(date: Long): String {
        var candidate: String
        do { candidate = "ACP-${AutoNumberService(db).nextDocumentNo("ACP", date)}" } while (db.additionalChargesDao().paymentNoCount(candidate) > 0)
        return candidate
    }

    private suspend fun requireAccount(code: String): AccountEntity = requireNotNull(db.accountDao().byCode(code)) { "الحساب $code غير موجود" }

    private suspend fun postJournal(entryNo: String, date: Long, description: String, currencyCode: String, exchangeRate: Double, sourceType: String, sourceId: String, createdBy: Long, lines: List<DraftJournalLine>): Long {
        AccountingValidator.validate(lines)
        val id = db.journalDao().insertEntry(JournalEntryEntity(entryNo=entryNo, entryDate=date, description=description, currencyCode=currencyCode, exchangeRate=exchangeRate, sourceType=sourceType, sourceId=sourceId, createdBy=createdBy))
        db.journalDao().insertLines(lines.map { JournalLineEntity(entryId=id, accountId=it.accountId, debit=it.debit, credit=it.credit) })
        return id
    }

    private suspend fun reverseJournal(original: JournalEntryEntity, reason: String, createdBy: Long, reversalDate: Long) {
        if (db.journalDao().reversalCount(original.id) > 0) return
        val lines = db.journalDao().linesForEntry(original.id)
        val no = "JE-RACH-${AutoNumberService(db).nextDocumentNo("RACH", reversalDate)}"
        postJournal(no, reversalDate, "عكس ${original.entryNo}: ${reason.trim()}", original.currencyCode, original.exchangeRate, "REVERSAL", original.id.toString(), createdBy, lines.map { DraftJournalLine(it.accountId, it.credit, it.debit) })
    }
}
