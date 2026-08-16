package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AccountEntity
import com.fush.erp.data.entity.JournalEntryEntity
import com.fush.erp.data.entity.JournalLineEntity
import com.fush.erp.data.entity.TreasuryAccountEntity
import com.fush.erp.data.entity.PartyVoucherEntity
import com.fush.erp.data.entity.AuditEventEntity
import com.fush.erp.data.entity.ExpenseAttachmentEntity
import com.fush.erp.data.entity.ExpenseDimensionEntity
import com.fush.erp.data.entity.FiscalYearClosingEntity
import com.fush.erp.data.entity.TreasuryCashCountEntity
import com.fush.erp.data.entity.BankStatementEntity
import com.fush.erp.data.entity.BankStatementLineEntity
import com.fush.erp.data.entity.TreasuryFxRevaluationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class AccountingService(private val db: FushDatabase) {
    data class ManualLine(
        val accountId: Long,
        val debitOriginal: Double = 0.0,
        val creditOriginal: Double = 0.0,
        val memo: String = ""
    )

    data class ExpenseAttachmentInput(
        val fileName: String,
        val mimeType: String = "",
        val uri: String,
        val notes: String = ""
    )

    data class FiscalYearClosingResult(
        val fiscalYear: Int,
        val status: String,
        val closingRecordId: Long,
        val closingEntryId: Long?,
        val reversalEntryId: Long?,
        val netIncomeBase: Double
    )

    data class ExpenseContext(
        val employeeId: Long? = null,
        val salesRepId: Long? = null,
        val costCenterCode: String = "OTHER",
        val organizationUnit: String = "",
        val referenceType: String = "NONE",
        val referenceId: Long? = null,
        val referenceNo: String = "",
        val referenceLabel: String = "",
        val customerId: Long? = null,
        val supplierId: Long? = null,
        val itemId: Long? = null,
        val attachment: ExpenseAttachmentInput? = null
    )

    data class VoucherRequest(
        val type: String,
        val treasuryAccountId: Long,
        val targetTreasuryAccountId: Long? = null,
        val offsetAccountId: Long? = null,
        val amountOriginal: Double,
        val currencyCode: String,
        val exchangeRate: Double,
        val description: String,
        val referenceNo: String = "",
        val voucherDate: Long = System.currentTimeMillis(),
        val createdBy: Long,
        val customerId: Long? = null,
        val supplierId: Long? = null,
        val employeeId: Long? = null,
        val salesRepId: Long? = null,
        val expenseContext: ExpenseContext? = null
    )

    private data class PartyLink(
        val partyType: String = "NONE",
        val partyName: String = "",
        val customerId: Long? = null,
        val supplierId: Long? = null,
        val employeeId: Long? = null,
        val salesRepId: Long? = null
    )

    private val moneyTolerance = 0.01

    private suspend fun treasuryRate(currencyCode: String, at: Long): Double {
        if (currencyCode == "YER_NEW") return 1.0
        val rate = requireNotNull(db.currencyDao().latestRateAt(currencyCode, at)) {
            "لا يوجد سعر صرف للعملة $currencyCode في تاريخ العملية"
        }.rateToBase
        require(rate.isFinite() && rate > 0.0) { "سعر صرف $currencyCode غير صالح" }
        return rate
    }

    suspend fun addAccount(
        code: String,
        nameAr: String,
        nameEn: String,
        type: String,
        parentCode: String?,
        isPosting: Boolean = true,
        createdBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.ACCOUNTING_POST)
        require(code.trim().isNotBlank()) { "كود الحساب مطلوب" }
        require(nameAr.trim().isNotBlank()) { "اسم الحساب مطلوب" }
        require(type in setOf("ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE")) { "نوع الحساب غير صالح" }
        require(db.accountDao().byCode(code.trim()) == null) { "كود الحساب مستخدم مسبقاً" }
        parentCode?.takeIf { it.isNotBlank() }?.let { parent ->
            requireNotNull(db.accountDao().byCode(parent.trim())) { "الحساب الأب غير موجود" }
        }
        db.accountDao().insert(
            AccountEntity(
                code = code.trim(),
                nameAr = nameAr.trim(),
                nameEn = nameEn.trim(),
                type = type,
                parentCode = parentCode?.trim()?.takeIf { it.isNotBlank() },
                isPosting = isPosting
            )
        )
    }

    suspend fun addTreasuryAccount(
        code: String,
        nameAr: String,
        kind: String,
        accountId: Long,
        currencyCode: String,
        bankName: String,
        accountNumber: String,
        createdBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.TREASURY_POST)
        require(code.trim().isNotBlank() && nameAr.trim().isNotBlank()) { "كود واسم الخزينة مطلوبان" }
        require(kind in setOf("CASH", "BANK")) { "نوع الخزينة غير صالح" }
        val account = requirePostingAccount(accountId)
        require(account.type == "ASSET") { "حساب الخزينة/البنك يجب أن يكون من نوع أصل" }
        require(db.accountingDao().treasuryByAccountId(accountId) == null) { "الحساب مربوط بخزينة أخرى" }
        require(db.currencyDao().allActive().any { it.code == currencyCode }) { "العملة غير موجودة" }
        db.accountingDao().insertTreasury(
            TreasuryAccountEntity(
                code = code.trim(),
                nameAr = nameAr.trim(),
                kind = kind,
                accountId = accountId,
                currencyCode = currencyCode,
                bankName = bankName.trim(),
                accountNumber = accountNumber.trim(),
                createdBy = createdBy
            )
        )
    }

    suspend fun postManualJournal(
        description: String,
        entryDate: Long,
        currencyCode: String,
        exchangeRate: Double,
        lines: List<ManualLine>,
        createdBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.ACCOUNTING_POST)
        require(description.trim().isNotBlank()) { "وصف القيد مطلوب" }
        requirePostingPeriodOpen(entryDate)
        validateRate(currencyCode, exchangeRate)
        val baseLines = lines.map { line ->
            val account = requirePostingAccount(line.accountId)
            ControlAccountPolicy.requireManualPostingAllowed(account.code)
            db.accountingDao().treasuryByAccountId(line.accountId)?.let { treasury ->
                require(treasury.currencyCode == currencyCode) {
                    "القيد على الخزينة ${treasury.nameAr} يجب أن يستخدم عملتها ${treasury.currencyCode}"
                }
            }
            require(line.debitOriginal >= 0 && line.creditOriginal >= 0) { "لا يسمح بمبالغ سالبة" }
            DraftJournalLine(line.accountId, line.debitOriginal * exchangeRate, line.creditOriginal * exchangeRate)
        }
        AccountingValidator.validate(baseLines)
        val entryNo = documentNo("JV")
        val entryId = db.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = entryNo,
                entryDate = entryDate,
                description = description.trim(),
                currencyCode = currencyCode,
                exchangeRate = exchangeRate,
                sourceType = "MANUAL",
                sourceId = UUID.randomUUID().toString(),
                createdBy = createdBy
            )
        )
        db.journalDao().insertLines(
            lines.mapIndexed { index, line ->
                JournalLineEntity(
                    entryId = entryId,
                    accountId = line.accountId,
                    debit = baseLines[index].debit,
                    credit = baseLines[index].credit,
                    memo = line.memo.trim()
                )
            }
        )
        entryId
    }

    suspend fun postVoucher(request: VoucherRequest): Long = db.withTransaction {
        db.requireUserPermission(request.createdBy, SecurityPermissions.TREASURY_POST)
        require(request.type in setOf("RECEIPT", "PAYMENT", "EXPENSE", "INCOME", "TRANSFER")) { "نوع السند غير صالح" }
        if (request.type == "EXPENSE") requireNotNull(request.expenseContext) { "بيانات تصنيف المصروف مطلوبة" }
        else require(request.expenseContext == null) { "أبعاد المصروف تستخدم مع سند المصروف فقط" }
        require(request.amountOriginal > 0.0 && request.amountOriginal.isFinite()) { "المبلغ يجب أن يكون أكبر من صفر" }
        require(request.description.trim().isNotBlank()) { "بيان السند مطلوب" }
        requirePostingPeriodOpen(request.voucherDate)
        validateRate(request.currencyCode, request.exchangeRate)
        val treasury = requireNotNull(db.accountingDao().treasuryById(request.treasuryAccountId)) { "الخزينة غير موجودة" }
        require(treasury.isActive) { "الخزينة غير نشطة" }
        require(request.currencyCode == treasury.currencyCode) { "عملة السند يجب أن تطابق عملة الخزينة" }
        val amountBase = request.amountOriginal * request.exchangeRate
        val lines: List<DraftJournalLine>
        val sourceType: String
        val prefix: String
        var voucherOffset: AccountEntity? = null
        var partyLink = PartyLink()

        if (request.type == "TRANSFER") {
            require(request.customerId == null && request.supplierId == null && request.employeeId == null && request.salesRepId == null) { "التحويل بين الخزائن لا يرتبط بطرف" }
            val target = requireNotNull(request.targetTreasuryAccountId?.let { db.accountingDao().treasuryById(it) }) { "الخزينة المستلمة مطلوبة" }
            require(target.isActive && target.id != treasury.id) { "اختر خزينة مستلمة مختلفة" }
            require(target.currencyCode == treasury.currencyCode) { "التحويل بين عملتين مختلفتين يؤجل إلى مرحلة العملات المتقدمة" }
            lines = listOf(
                DraftJournalLine(target.accountId, amountBase, 0.0),
                DraftJournalLine(treasury.accountId, 0.0, amountBase)
            )
            sourceType = TreasuryMovementType.TRANSFER.sourceType
            prefix = "TV"
        } else {
            val offset = requireNotNull(request.offsetAccountId?.let { requirePostingAccount(it) }) { "الحساب المقابل مطلوب" }
            require(offset.id != treasury.accountId) { "الحساب المقابل يجب أن يختلف عن حساب الخزينة" }
            ControlAccountPolicy.requireGenericVoucherAllowed(offset.code)
            partyLink = resolvePartyLink(offset, request)
            voucherOffset = offset
            if (request.type == "EXPENSE") require(offset.type == "EXPENSE") { "سند المصروف يجب أن يستخدم حساب مصروف" }
            if (request.type == "INCOME") require(offset.type == "REVENUE") { "سند الإيراد يجب أن يستخدم حساب إيراد" }
            lines = when (request.type) {
                "RECEIPT", "INCOME" -> listOf(
                    DraftJournalLine(treasury.accountId, amountBase, 0.0),
                    DraftJournalLine(offset.id, 0.0, amountBase)
                )
                else -> listOf(
                    DraftJournalLine(offset.id, amountBase, 0.0),
                    DraftJournalLine(treasury.accountId, 0.0, amountBase)
                )
            }
            sourceType = TreasuryMovementTypePolicy.forVoucher(request.type, partyLink.partyType).sourceType
            prefix = when (request.type) {
                "RECEIPT" -> "RV"
                "PAYMENT" -> "PV"
                "EXPENSE" -> "EV"
                else -> "IV"
            }
        }

        AccountingValidator.validate(lines)
        val voucherNo = documentNo(prefix)
        val description = buildString {
            append(request.description.trim())
            if (request.referenceNo.isNotBlank()) append(" | مرجع: ${request.referenceNo.trim()}")
        }
        val entryId = db.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = voucherNo,
                entryDate = request.voucherDate,
                description = description,
                currencyCode = request.currencyCode,
                exchangeRate = request.exchangeRate,
                sourceType = sourceType,
                sourceId = UUID.randomUUID().toString(),
                createdBy = request.createdBy
            )
        )
        db.journalDao().insertLines(lines.map { JournalLineEntity(entryId = entryId, accountId = it.accountId, debit = it.debit, credit = it.credit) })
        voucherOffset?.let { offset ->
            val voucherId = db.partyDao().insertVoucher(
                PartyVoucherEntity(
                    voucherNo = voucherNo,
                    voucherType = request.type,
                    treasuryAccountId = treasury.id,
                    offsetAccountId = offset.id,
                    customerId = partyLink.customerId,
                    supplierId = partyLink.supplierId,
                    employeeId = partyLink.employeeId,
                    salesRepId = partyLink.salesRepId,
                    partyType = partyLink.partyType,
                    partyNameSnapshot = partyLink.partyName,
                    voucherDate = request.voucherDate,
                    currencyCode = request.currencyCode,
                    exchangeRate = request.exchangeRate,
                    amountOriginal = request.amountOriginal,
                    amountBase = amountBase,
                    description = request.description.trim(),
                    referenceNo = request.referenceNo.trim(),
                    journalEntryId = entryId,
                    createdBy = request.createdBy
                )
            )
            if (request.type == "EXPENSE") {
                insertExpenseDimension(
                    partyVoucherId = voucherId,
                    treasury = treasury,
                    context = requireNotNull(request.expenseContext),
                    createdBy = request.createdBy
                )
            }
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = request.createdBy,
                    action = "POST",
                    entityType = "PARTY_VOUCHER",
                    entityId = voucherId.toString(),
                    newValue = "$voucherNo|${request.type}|${partyLink.partyType}|${partyLink.customerId ?: partyLink.supplierId ?: partyLink.employeeId ?: partyLink.salesRepId ?: 0}|${request.amountOriginal}|${request.currencyCode}",
                    reason = request.description.trim()
                )
            )
        }
        entryId
    }

    suspend fun reverseEntry(entryId: Long, reason: String, createdBy: Long, reversalDate: Long = System.currentTimeMillis()): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.ACCOUNTING_POST)
        require(reason.trim().isNotBlank()) { "سبب العكس مطلوب" }
        requirePostingPeriodOpen(reversalDate)
        val original = requireNotNull(db.journalDao().byId(entryId)) { "القيد غير موجود" }
        require(original.status == "POSTED") { "القيد غير مرحل" }
        require(original.sourceType in REVERSIBLE_SOURCE_TYPES) {
            "هذا القيد ناتج عن عملية تشغيلية؛ يجب عكسه من شاشة العملية الأصلية لحماية التتبع"
        }
        require(db.journalDao().reversalCount(entryId) == 0) { "تم عكس هذا القيد مسبقاً" }
        val linkedVoucher = db.partyDao().voucherByJournalEntryId(entryId)
        linkedVoucher?.let { require(it.status == "POSTED") { "تم عكس هذا السند مسبقاً" } }
        val originalLines = db.journalDao().linesForEntry(entryId)
        require(originalLines.isNotEmpty()) { "القيد لا يحتوي سطوراً" }
        val reversed = originalLines.map { DraftJournalLine(it.accountId, it.credit, it.debit) }
        AccountingValidator.validate(reversed)
        val newId = db.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = documentNo("REV"),
                entryDate = reversalDate,
                description = "عكس ${original.entryNo}: ${reason.trim()}",
                currencyCode = original.currencyCode,
                exchangeRate = original.exchangeRate,
                sourceType = "REVERSAL",
                sourceId = original.id.toString(),
                createdBy = createdBy
            )
        )
        db.journalDao().insertLines(
            originalLines.map { JournalLineEntity(entryId = newId, accountId = it.accountId, debit = it.credit, credit = it.debit, memo = "عكس: ${it.memo}") }
        )
        linkedVoucher?.let { voucher ->
            db.partyDao().updateVoucher(
                voucher.copy(
                    status = "REVERSED",
                    reversalReason = reason.trim(),
                    reversedBy = createdBy,
                    reversedAt = reversalDate,
                    reversalJournalEntryId = newId
                )
            )
            db.governanceDao().insertAudit(
                AuditEventEntity(
                    userId = createdBy,
                    action = "REVERSE",
                    entityType = "PARTY_VOUCHER",
                    entityId = voucher.id.toString(),
                    oldValue = "POSTED",
                    newValue = "REVERSED|journal=$newId",
                    reason = reason.trim()
                )
            )
        }
        newId
    }

    suspend fun createCashCount(
        treasuryAccountId: Long,
        countDate: Long,
        actualBalanceOriginal: Double,
        notes: String,
        createdBy: Long
    ): Long = db.withTransaction {
        require(actualBalanceOriginal.isFinite() && actualBalanceOriginal >= 0.0) { "الرصيد الفعلي للجرد غير صالح" }
        val treasury = requireNotNull(db.accountingDao().treasuryById(treasuryAccountId)) { "الخزينة غير موجودة" }
        require(treasury.isActive && treasury.kind == "CASH") { "الجرد النقدي متاح للصناديق النشطة فقط" }
        requirePostingPeriodOpen(countDate)
        val rate = treasuryRate(treasury.currencyCode, countDate)
        val expectedOriginal = db.accountingDao().treasuryBookOriginalBalance(treasury.accountId, treasury.currencyCode, countDate)
        val expectedBase = db.accountingDao().treasuryBookBalance(treasury.accountId, countDate)
        val computation = TreasuryFxMath.cashCountOriginal(expectedOriginal, actualBalanceOriginal, rate)
        val id = db.accountingDao().insertCashCount(
            TreasuryCashCountEntity(
                treasuryAccountId = treasury.id,
                countDate = countDate,
                expectedBalanceBase = expectedBase,
                actualBalanceBase = expectedBase + computation.varianceBase,
                differenceBase = computation.varianceBase,
                expectedBalanceOriginal = computation.expectedBalanceOriginal,
                actualBalanceOriginal = computation.actualBalanceOriginal,
                differenceOriginal = computation.differenceOriginal,
                rateToBase = rate,
                status = computation.status,
                notes = notes.trim(),
                createdBy = createdBy
            )
        )
        db.governanceDao().insertAudit(AuditEventEntity(
            userId = createdBy,
            action = "COUNT",
            entityType = "TREASURY_CASH_COUNT",
            entityId = id.toString(),
            newValue = "${treasury.code}|currency=${treasury.currencyCode}|expectedOriginal=${computation.expectedBalanceOriginal}|actualOriginal=${computation.actualBalanceOriginal}|differenceOriginal=${computation.differenceOriginal}|rate=$rate|differenceBase=${computation.varianceBase}|${computation.status}",
            reason = notes.trim().ifBlank { "جرد صندوق" }
        ))
        id
    }

    suspend fun resolveCashCountDifference(
        cashCountId: Long,
        reason: String,
        resolvedBy: Long
    ): Long = db.withTransaction {
        require(reason.trim().isNotBlank()) { "سبب تسوية فرق الصندوق مطلوب" }
        val count = requireNotNull(db.accountingDao().cashCountById(cashCountId)) { "سجل الجرد غير موجود" }
        require(count.status == "VARIANCE") { "هذا الجرد لا يحتوي فرقاً مفتوحاً" }
        require(kotlin.math.abs(count.differenceOriginal) > TreasuryFxMath.ORIGINAL_TOLERANCE) { "لا يوجد فرق يحتاج إلى تسوية" }
        requirePostingPeriodOpen(count.countDate)
        val treasury = requireNotNull(db.accountingDao().treasuryById(count.treasuryAccountId)) { "الخزينة غير موجودة" }
        require(treasury.kind == "CASH") { "سجل الجرد لا يخص صندوقاً نقدياً" }
        val cashOverShort = requireNotNull(db.accountDao().byCode("6950")) { "حساب فروقات الصندوق 6950 غير موجود" }
        require(cashOverShort.isActive && cashOverShort.isPosting && cashOverShort.type == "EXPENSE") {
            "حساب فروقات الصندوق 6950 يجب أن يكون حساب مصروف نشطاً وقابلاً للترحيل"
        }
        val amountBase = kotlin.math.abs(count.differenceBase)
        require(amountBase > moneyTolerance) { "قيمة فرق الجرد الأساسية لا تحتاج قيداً" }
        val lines = if (count.differenceOriginal > 0.0) {
            listOf(DraftJournalLine(treasury.accountId, amountBase, 0.0), DraftJournalLine(cashOverShort.id, 0.0, amountBase))
        } else {
            listOf(DraftJournalLine(cashOverShort.id, amountBase, 0.0), DraftJournalLine(treasury.accountId, 0.0, amountBase))
        }
        AccountingValidator.validate(lines)
        val entryId = db.journalDao().insertEntry(JournalEntryEntity(
            entryNo = documentNo("CADJ"),
            entryDate = count.countDate,
            description = "تسوية فرق جرد ${treasury.nameAr}",
            currencyCode = treasury.currencyCode,
            exchangeRate = count.rateToBase,
            sourceType = "CASH_COUNT_ADJUSTMENT",
            sourceId = count.id.toString(),
            createdBy = resolvedBy
        ))
        db.journalDao().insertLines(lines.map { line -> JournalLineEntity(
            entryId = entryId,
            accountId = line.accountId,
            debit = line.debit,
            credit = line.credit,
            memo = "تسوية فرق الجرد رقم ${count.id} — ${count.differenceOriginal} ${treasury.currencyCode}"
        ) })
        db.accountingDao().updateCashCount(count.copy(
            status = "RESOLVED",
            resolutionEntryId = entryId,
            resolutionReason = reason.trim(),
            resolvedBy = resolvedBy,
            resolvedAt = System.currentTimeMillis()
        ))
        db.governanceDao().insertAudit(AuditEventEntity(
            userId = resolvedBy,
            action = "RESOLVE",
            entityType = "TREASURY_CASH_COUNT",
            entityId = count.id.toString(),
            oldValue = "VARIANCE|original=${count.differenceOriginal}|base=${count.differenceBase}",
            newValue = "RESOLVED|journal=$entryId",
            reason = reason.trim()
        ))
        entryId
    }

    suspend fun createBankStatement(
        treasuryAccountId: Long,
        startDate: Long,
        endDate: Long,
        openingBalanceOriginal: Double,
        closingBalanceOriginal: Double,
        notes: String,
        createdBy: Long
    ): Long = db.withTransaction {
        require(startDate <= endDate) { "فترة كشف البنك غير صالحة" }
        require(openingBalanceOriginal.isFinite() && closingBalanceOriginal.isFinite()) { "أرصدة كشف البنك غير صالحة" }
        val treasury = requireNotNull(db.accountingDao().treasuryById(treasuryAccountId)) { "الحساب البنكي غير موجود" }
        require(treasury.isActive && treasury.kind == "BANK") { "المطابقة البنكية متاحة للحسابات البنكية النشطة فقط" }
        requirePostingPeriodOpen(endDate)
        val previous = db.accountingDao().latestBankStatement(treasury.id)
        if (previous != null) {
            require(startDate > previous.endDate) { "فترة كشف البنك تتداخل مع كشف سابق" }
            require(kotlin.math.abs(openingBalanceOriginal - previous.closingBalanceOriginal) <= TreasuryFxMath.ORIGINAL_TOLERANCE) {
                "الرصيد الافتتاحي للكشف لا يساوي الرصيد الختامي للكشف السابق بالعملة الأصلية"
            }
        } else {
            val openingBook = db.accountingDao().treasuryBookOriginalBalance(treasury.accountId, treasury.currencyCode, (startDate - 1L).coerceAtLeast(0L))
            require(kotlin.math.abs(openingBalanceOriginal - openingBook) <= TreasuryFxMath.ORIGINAL_TOLERANCE) {
                "أول كشف بنكي يجب أن يبدأ من الرصيد الدفتري الافتتاحي بالعملة الأصلية (${treasury.currencyCode})"
            }
        }
        val startRate = treasuryRate(treasury.currencyCode, startDate)
        val endRate = treasuryRate(treasury.currencyCode, endDate)
        val id = db.accountingDao().insertBankStatement(BankStatementEntity(
            treasuryAccountId = treasury.id,
            startDate = startDate,
            endDate = endDate,
            currencyCode = treasury.currencyCode,
            openingBalanceOriginal = openingBalanceOriginal,
            closingBalanceOriginal = closingBalanceOriginal,
            openingBalanceBase = openingBalanceOriginal * startRate,
            closingBalanceBase = closingBalanceOriginal * endRate,
            notes = notes.trim(),
            createdBy = createdBy
        ))
        db.governanceDao().insertAudit(AuditEventEntity(
            userId = createdBy,
            action = "CREATE",
            entityType = "BANK_STATEMENT",
            entityId = id.toString(),
            newValue = "${treasury.code}|${treasury.currencyCode}|$startDate..$endDate|openingOriginal=$openingBalanceOriginal|closingOriginal=$closingBalanceOriginal",
            reason = notes.trim().ifBlank { "إنشاء كشف بنكي للمطابقة" }
        ))
        id
    }

    suspend fun addBankStatementLine(
        statementId: Long,
        transactionDate: Long,
        referenceNo: String,
        description: String,
        amountOriginal: Double,
        createdBy: Long
    ): Long = db.withTransaction {
        require(amountOriginal.isFinite() && kotlin.math.abs(amountOriginal) > TreasuryFxMath.ORIGINAL_TOLERANCE) { "مبلغ حركة البنك غير صالح" }
        val statement = requireNotNull(db.accountingDao().bankStatementById(statementId)) { "كشف البنك غير موجود" }
        require(statement.status == "DRAFT") { "لا يمكن تعديل كشف بنكي تم اعتماده" }
        require(transactionDate in statement.startDate..statement.endDate) { "تاريخ حركة البنك خارج فترة الكشف" }
        val rate = treasuryRate(statement.currencyCode, transactionDate)
        val id = db.accountingDao().insertBankStatementLine(BankStatementLineEntity(
            statementId = statement.id,
            transactionDate = transactionDate,
            referenceNo = referenceNo.trim(),
            description = description.trim(),
            amountOriginal = amountOriginal,
            amountBase = amountOriginal * rate
        ))
        db.governanceDao().insertAudit(AuditEventEntity(
            userId = createdBy,
            action = "CREATE",
            entityType = "BANK_STATEMENT_LINE",
            entityId = id.toString(),
            newValue = "statement=$statementId|amountOriginal=$amountOriginal|currency=${statement.currencyCode}|rate=$rate|date=$transactionDate",
            reason = description.trim().ifBlank { "إضافة حركة كشف بنكي" }
        ))
        id
    }

    suspend fun matchBankStatementLine(lineId: Long, journalEntryId: Long, matchedBy: Long) = db.withTransaction {
        val line = requireNotNull(db.accountingDao().bankStatementLineById(lineId)) { "حركة كشف البنك غير موجودة" }
        val statement = requireNotNull(db.accountingDao().bankStatementById(line.statementId)) { "كشف البنك غير موجود" }
        require(statement.status == "DRAFT") { "كشف البنك تمت مطابقته ولا يمكن تعديله" }
        require(line.matchedJournalEntryId == null) { "حركة البنك مرتبطة بقيد بالفعل" }
        val treasury = requireNotNull(db.accountingDao().treasuryById(statement.treasuryAccountId)) { "الحساب البنكي غير موجود" }
        require(db.accountingDao().bankMatchCountForJournal(treasury.id, journalEntryId) == 0) { "القيد مستخدم في مطابقة أخرى لنفس الحساب البنكي" }
        val movement = db.accountingDao().bankBookMovements(treasury.accountId, statement.endDate)
            .firstOrNull { it.entryId == journalEntryId }
            ?: throw IllegalArgumentException("القيد المختار لا يحتوي حركة على هذا الحساب البنكي")
        require(movement.currencyCode == statement.currencyCode) { "عملة القيد لا تطابق عملة كشف البنك" }
        require(kotlin.math.abs(movement.amountOriginal - line.amountOriginal) <= TreasuryFxMath.ORIGINAL_TOLERANCE) {
            "قيمة حركة البنك ${line.amountOriginal} ${statement.currencyCode} لا تطابق حركة القيد ${movement.amountOriginal}"
        }
        db.accountingDao().updateBankStatementLine(
            line.copy(
                matchedJournalEntryId = journalEntryId,
                matchedBy = matchedBy,
                matchedAt = System.currentTimeMillis()
            )
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = matchedBy,
                action = "MATCH",
                entityType = "BANK_STATEMENT_LINE",
                entityId = line.id.toString(),
                newValue = "journal=$journalEntryId|amountOriginal=${line.amountOriginal}|currency=${statement.currencyCode}",
                reason = "مطابقة حركة البنك بالقيد الدفتري"
            )
        )
    }

    suspend fun unmatchBankStatementLine(lineId: Long, userId: Long) = db.withTransaction {
        val line = requireNotNull(db.accountingDao().bankStatementLineById(lineId)) { "حركة كشف البنك غير موجودة" }
        val statement = requireNotNull(db.accountingDao().bankStatementById(line.statementId)) { "كشف البنك غير موجود" }
        require(statement.status == "DRAFT") { "كشف البنك تمت مطابقته ولا يمكن تعديله" }
        val old = requireNotNull(line.matchedJournalEntryId) { "حركة البنك غير مرتبطة بقيد" }
        db.accountingDao().updateBankStatementLine(
            line.copy(matchedJournalEntryId = null, matchedBy = null, matchedAt = null)
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = "UNMATCH",
                entityType = "BANK_STATEMENT_LINE",
                entityId = line.id.toString(),
                oldValue = "journal=$old",
                newValue = "UNMATCHED",
                reason = "فك مطابقة حركة البنك"
            )
        )
    }

    suspend fun bankReconciliation(statementId: Long): ForeignBankReconciliationComputation {
        val statement = requireNotNull(db.accountingDao().bankStatementById(statementId)) { "كشف البنك غير موجود" }
        val treasury = requireNotNull(db.accountingDao().treasuryById(statement.treasuryAccountId)) { "الحساب البنكي غير موجود" }
        val first = requireNotNull(db.accountingDao().firstBankStatement(treasury.id)) { "تعذر تحديد أول كشف بنكي" }
        val lines = db.accountingDao().bankStatementLines(statement.id)
        val bookClosingOriginal = db.accountingDao().treasuryBookOriginalBalance(treasury.accountId, treasury.currencyCode, statement.endDate)
        val matchedIds = db.accountingDao().matchedJournalIdsThrough(treasury.id, statement.endDate).filterNotNull().toSet()
        val outstandingOriginal = db.accountingDao()
            .bankBookMovementsInRange(treasury.accountId, first.startDate, statement.endDate)
            .asSequence()
            .filterNot { it.entryId in matchedIds }
            .sumOf { it.amountOriginal }
        return TreasuryFxMath.bankReconciliationOriginal(
            currencyCode = treasury.currencyCode,
            openingBalanceOriginal = statement.openingBalanceOriginal,
            closingBalanceOriginal = statement.closingBalanceOriginal,
            statementLineAmountsOriginal = lines.map { it.amountOriginal },
            bookClosingBalanceOriginal = bookClosingOriginal,
            outstandingBookNetOriginal = outstandingOriginal
        )
    }

    suspend fun finalizeBankReconciliation(statementId: Long, reconciledBy: Long): ForeignBankReconciliationComputation = db.withTransaction {
        val statement = requireNotNull(db.accountingDao().bankStatementById(statementId)) { "كشف البنك غير موجود" }
        require(statement.status == "DRAFT") { "تمت مطابقة كشف البنك مسبقاً" }
        require(db.accountingDao().unmatchedBankStatementLineCount(statement.id) == 0) {
            "لا يمكن إنهاء المطابقة قبل ربط جميع حركات كشف البنك بقيودها الدفترية"
        }
        val result = bankReconciliation(statement.id)
        require(kotlin.math.abs(result.arithmeticDifferenceOriginal) <= TreasuryFxMath.ORIGINAL_TOLERANCE) {
            "كشف البنك غير متوازن حسابياً بالعملة ${result.currencyCode}: الرصيد الافتتاحي + الحركات لا يساوي الرصيد الختامي"
        }
        require(kotlin.math.abs(result.differenceOriginal) <= TreasuryFxMath.ORIGINAL_TOLERANCE) {
            "المطابقة البنكية غير مكتملة؛ الفرق بعد الحركات الدفترية المعلقة = ${result.differenceOriginal} ${result.currencyCode}"
        }
        db.accountingDao().updateBankStatement(statement.copy(
            status = "RECONCILED", reconciledBy = reconciledBy, reconciledAt = System.currentTimeMillis()
        ))
        db.governanceDao().insertAudit(AuditEventEntity(
            userId = reconciledBy,
            action = "RECONCILE",
            entityType = "BANK_STATEMENT",
            entityId = statement.id.toString(),
            oldValue = "DRAFT",
            newValue = "RECONCILED|currency=${result.currencyCode}|differenceOriginal=${result.differenceOriginal}|outstandingOriginal=${result.outstandingBookNetOriginal}",
            reason = "اعتماد المطابقة البنكية"
        ))
        result
    }

    suspend fun bankStatementLines(statementId: Long) = db.accountingDao().bankStatementLines(statementId)

    suspend fun bankBookMovements(statementId: Long): List<com.fush.erp.data.entity.BankBookMovementRow> {
        val statement = requireNotNull(db.accountingDao().bankStatementById(statementId)) { "كشف البنك غير موجود" }
        val treasury = requireNotNull(db.accountingDao().treasuryById(statement.treasuryAccountId)) { "الحساب البنكي غير موجود" }
        return db.accountingDao().bankBookMovements(treasury.accountId, statement.endDate)
    }

    fun observeFxRevaluations() = db.accountingDao().observeFxRevaluations()

    private suspend fun reverseFxRevaluationInternal(
        row: TreasuryFxRevaluationEntity,
        reversedBy: Long,
        reason: String
    ): Long? {
        require(row.status == "POSTED") { "إعادة التقييم معكوسة مسبقاً" }
        val reversalId = row.journalEntryId?.let { originalId ->
            val original = requireNotNull(db.journalDao().byId(originalId)) { "قيد إعادة تقييم العملة غير موجود" }
            val originalLines = db.journalDao().linesForEntry(originalId)
            require(originalLines.isNotEmpty()) { "قيد إعادة تقييم العملة لا يحتوي سطوراً" }
            val reversed = originalLines.map { DraftJournalLine(it.accountId, it.credit, it.debit) }
            AccountingValidator.validate(reversed)
            val id = db.journalDao().insertEntry(JournalEntryEntity(
                entryNo = documentNo("FXR"),
                entryDate = row.valuationDate,
                description = "عكس إعادة تقييم ${row.currencyCode}: ${reason.trim()}",
                currencyCode = "YER_NEW",
                exchangeRate = 1.0,
                sourceType = "FX_REVALUATION_REVERSAL",
                sourceId = row.id.toString(),
                createdBy = reversedBy
            ))
            db.journalDao().insertLines(originalLines.map { line -> JournalLineEntity(
                entryId = id,
                accountId = line.accountId,
                debit = line.credit,
                credit = line.debit,
                memo = "عكس إعادة تقييم: ${line.memo}"
            ) })
            id
        }
        db.accountingDao().updateFxRevaluation(row.copy(
            status = "REVERSED",
            reversalEntryId = reversalId,
            reversedBy = reversedBy,
            reversedAt = System.currentTimeMillis(),
            reversalReason = reason.trim()
        ))
        db.governanceDao().insertAudit(AuditEventEntity(
            userId = reversedBy,
            action = "REVERSE",
            entityType = "TREASURY_FX_REVALUATION",
            entityId = row.id.toString(),
            oldValue = "POSTED|journal=${row.journalEntryId}",
            newValue = "REVERSED|journal=$reversalId",
            reason = reason.trim()
        ))
        return reversalId
    }

    suspend fun revalueForeignTreasuries(
        valuationDate: Long,
        createdBy: Long,
        reason: String
    ): List<Long> = db.withTransaction {
        require(reason.trim().isNotBlank()) { "سبب إعادة تقييم العملات مطلوب" }
        require(valuationDate <= System.currentTimeMillis()) { "لا يمكن إعادة تقييم العملات بتاريخ مستقبلي" }
        requirePostingPeriodOpen(valuationDate)
        val fxGain = requireNotNull(db.accountDao().byCode("4250")) { "حساب أرباح فروق العملة 4250 غير موجود" }
        val fxLoss = requireNotNull(db.accountDao().byCode("6750")) { "حساب خسائر فروق العملة 6750 غير موجود" }
        require(fxGain.isActive && fxGain.isPosting && fxGain.type == "REVENUE") { "حساب 4250 غير صالح" }
        require(fxLoss.isActive && fxLoss.isPosting && fxLoss.type == "EXPENSE") { "حساب 6750 غير صالح" }

        val ids = mutableListOf<Long>()
        for (treasury in db.accountingDao().allActiveTreasury().filter { it.currencyCode != "YER_NEW" }) {
            val rate = treasuryRate(treasury.currencyCode, valuationDate)
            val currentOriginal = db.accountingDao().treasuryBookOriginalBalance(treasury.accountId, treasury.currencyCode, valuationDate)
            val existing = db.accountingDao().activeFxRevaluation(treasury.id, valuationDate)
            if (existing != null) {
                val currentCarryingWithExisting = db.accountingDao().treasuryBookBalance(treasury.accountId, valuationDate)
                if (
                    kotlin.math.abs(existing.originalBalance - currentOriginal) <= TreasuryFxMath.ORIGINAL_TOLERANCE &&
                    kotlin.math.abs(existing.rateToBase - rate) <= TreasuryFxMath.ORIGINAL_TOLERANCE &&
                    kotlin.math.abs(existing.targetBalanceBase - currentCarryingWithExisting) <= moneyTolerance
                ) {
                    ids += existing.id
                    continue
                }
                reverseFxRevaluationInternal(existing, createdBy, "تحديث إعادة التقييم: ${reason.trim()}")
            }

            val carryingBefore = db.accountingDao().treasuryBookBalance(treasury.accountId, valuationDate)
            val computation = TreasuryFxMath.revaluation(currentOriginal, carryingBefore, rate)
            val revaluationId = db.accountingDao().insertFxRevaluation(TreasuryFxRevaluationEntity(
                treasuryAccountId = treasury.id,
                valuationDate = valuationDate,
                currencyCode = treasury.currencyCode,
                originalBalance = computation.originalBalance,
                carryingBalanceBeforeBase = computation.carryingBalanceBeforeBase,
                rateToBase = computation.rateToBase,
                targetBalanceBase = computation.targetBalanceBase,
                differenceBase = computation.differenceBase,
                status = "POSTED",
                journalEntryId = null,
                reason = reason.trim(),
                createdBy = createdBy
            ))
            val journalEntryId = if (computation.needsJournal) {
                val amount = kotlin.math.abs(computation.differenceBase)
                val lines = if (computation.differenceBase > 0.0) {
                    listOf(
                        DraftJournalLine(treasury.accountId, amount, 0.0),
                        DraftJournalLine(fxGain.id, 0.0, amount)
                    )
                } else {
                    listOf(
                        DraftJournalLine(fxLoss.id, amount, 0.0),
                        DraftJournalLine(treasury.accountId, 0.0, amount)
                    )
                }
                AccountingValidator.validate(lines)
                val entryId = db.journalDao().insertEntry(JournalEntryEntity(
                    entryNo = documentNo("FXV"),
                    entryDate = valuationDate,
                    description = "إعادة تقييم ${treasury.nameAr} — ${treasury.currencyCode}",
                    currencyCode = "YER_NEW",
                    exchangeRate = 1.0,
                    sourceType = "FX_REVALUATION",
                    sourceId = revaluationId.toString(),
                    createdBy = createdBy
                ))
                db.journalDao().insertLines(lines.map { line -> JournalLineEntity(
                    entryId = entryId,
                    accountId = line.accountId,
                    debit = line.debit,
                    credit = line.credit,
                    memo = "${computation.originalBalance} ${treasury.currencyCode} × ${computation.rateToBase}"
                ) })
                entryId
            } else null
            val stored = requireNotNull(db.accountingDao().fxRevaluationById(revaluationId))
            db.accountingDao().updateFxRevaluation(stored.copy(journalEntryId = journalEntryId))
            db.governanceDao().insertAudit(AuditEventEntity(
                userId = createdBy,
                action = "REVALUE",
                entityType = "TREASURY_FX_REVALUATION",
                entityId = revaluationId.toString(),
                newValue = "${treasury.code}|${treasury.currencyCode}|original=${computation.originalBalance}|rate=${computation.rateToBase}|before=${computation.carryingBalanceBeforeBase}|target=${computation.targetBalanceBase}|difference=${computation.differenceBase}|journal=$journalEntryId",
                reason = reason.trim()
            ))
            ids += revaluationId
        }
        ids
    }

    private suspend fun reverseFxRevaluationsInRange(
        fromDate: Long,
        toDate: Long,
        userId: Long,
        reason: String
    ) {
        db.accountingDao().activeFxRevaluationsInRange(fromDate, toDate).forEach { row ->
            reverseFxRevaluationInternal(row, userId, reason)
        }
    }

    suspend fun treasuryControl(fromDate: Long, toDate: Long): TreasuryControlReport {
        require(fromDate <= toDate) { "فترة رقابة الخزينة غير صحيحة" }
        val issues = mutableListOf<TreasuryControlIssue>()
        val endDayStart = Calendar.getInstance().apply {
            timeInMillis = toDate
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        for (treasury in db.accountingDao().allActiveTreasury()) {
            val activity = db.accountingDao().treasuryActivityCount(treasury.accountId, fromDate, toDate)
            val currentOriginal = db.accountingDao().treasuryBookOriginalBalance(treasury.accountId, treasury.currencyCode, toDate)
            val currentCarrying = db.accountingDao().treasuryBookBalance(treasury.accountId, toDate)
            if (activity == 0 && kotlin.math.abs(currentOriginal) <= TreasuryFxMath.ORIGINAL_TOLERANCE && kotlin.math.abs(currentCarrying) <= moneyTolerance) continue
            if (treasury.currencyCode != "YER_NEW") {
                val revaluation = db.accountingDao().activeFxRevaluation(treasury.id, toDate)
                if (revaluation == null) {
                    issues += TreasuryControlIssue(treasury.id, treasury.nameAr, treasury.kind, "لا توجد إعادة تقييم عملة معتمدة في نهاية الفترة")
                } else {
                    if (kotlin.math.abs(currentOriginal - revaluation.originalBalance) > TreasuryFxMath.ORIGINAL_TOLERANCE) {
                        issues += TreasuryControlIssue(treasury.id, treasury.nameAr, treasury.kind, "إعادة تقييم العملة قديمة لأن رصيد ${treasury.currencyCode} تغير بعدها")
                    }
                    if (kotlin.math.abs(currentCarrying - revaluation.targetBalanceBase) > moneyTolerance) {
                        issues += TreasuryControlIssue(treasury.id, treasury.nameAr, treasury.kind, "القيمة الدفترية الأساسية لا تطابق آخر إعادة تقييم للعملة")
                    }
                }
            }
            if (treasury.kind == "CASH") {
                if (db.accountingDao().unresolvedCashVarianceCountForTreasury(treasury.id, toDate) > 0) {
                    issues += TreasuryControlIssue(treasury.id, treasury.nameAr, treasury.kind, "يوجد فرق جرد صندوق غير مسوّى")
                }
                if (db.accountingDao().cashCountCountInRange(treasury.id, endDayStart, toDate) == 0) {
                    issues += TreasuryControlIssue(treasury.id, treasury.nameAr, treasury.kind, "لا يوجد جرد صندوق في اليوم الأخير من الفترة")
                }
            } else if (treasury.kind == "BANK") {
                if (db.accountingDao().reconciledBankStatementCovering(treasury.id, fromDate, toDate) == 0) {
                    issues += TreasuryControlIssue(treasury.id, treasury.nameAr, treasury.kind, "لا يوجد كشف بنكي مطابق يغطي كامل الفترة")
                }
            }
        }
        return TreasuryControlReport(fromDate, toDate, issues)
    }

    suspend fun createCalendarFiscalYear(fiscalYear: Int, createdBy: Long): List<Long> = db.withTransaction {
        require(fiscalYear in 2000..2200) { "السنة المالية غير صالحة" }
        require(db.accountingDao().periodCountForYear(fiscalYear) == 0) { "تم إنشاء فترات هذه السنة مسبقاً" }
        val ids = mutableListOf<Long>()
        for (month in 0..11) {
            val start = Calendar.getInstance().apply {
                clear()
                set(fiscalYear, month, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val next = Calendar.getInstance().apply {
                clear()
                set(fiscalYear, month, 1, 0, 0, 0)
                add(Calendar.MONTH, 1)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            ids += db.accountingDao().insertPeriod(
                com.fush.erp.data.entity.AccountingPeriodEntity(
                    fiscalYear = fiscalYear,
                    periodNo = month + 1,
                    nameAr = "الفترة ${month + 1} / $fiscalYear",
                    startDate = start,
                    endDate = next - 1,
                    createdBy = createdBy
                )
            )
        }
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = createdBy,
                action = "CREATE",
                entityType = "ACCOUNTING_FISCAL_YEAR",
                entityId = fiscalYear.toString(),
                newValue = "12 periods|OPEN",
                reason = "إنشاء السنة المالية"
            )
        )
        ids
    }

    suspend fun closePeriod(periodId: Long, reason: String, closedBy: Long): AccountingReconciliationReport = db.withTransaction {
        require(reason.trim().isNotBlank()) { "سبب إقفال الفترة مطلوب" }
        val period = requireNotNull(db.accountingDao().periodById(periodId)) { "الفترة المحاسبية غير موجودة" }
        require(period.status == "OPEN") { "الفترة مقفلة مسبقاً" }
        if (period.periodNo == 12 && db.accountingDao().periodCountForYear(period.fiscalYear) == 12) {
            require(false) { "الفترة 12 تُقفل من عملية إقفال السنة المالية حتى يتم ترحيل نتيجة السنة إلى الأرباح المحتجزة" }
        }
        require(period.endDate < System.currentTimeMillis()) { "لا يمكن إقفال فترة محاسبية لم تنته بعد" }
        require(db.accountingDao().earlierOpenPeriodCount(period.fiscalYear, period.periodNo) == 0) {
            "يجب إقفال الفترات السابقة أولاً"
        }

        val reconciliation = reconciliation(period.endDate)
        require(reconciliation.isMatched) {
            val problems = reconciliation.rows.filterNot { it.isMatched }.joinToString("، ") { "${it.labelAr}: ${it.differenceBase}" }
            "لا يمكن إقفال الفترة قبل معالجة فروقات المطابقة${if (problems.isBlank()) "" else ": $problems"}"
        }

        val treasuryControl = treasuryControl(period.startDate, period.endDate)
        require(treasuryControl.isClear) {
            "لا يمكن إقفال الفترة قبل استكمال رقابة الخزائن والبنوك: " + treasuryControl.issues.joinToString("، ") { "${it.treasuryName}: ${it.message}" }
        }

        val fixedAssetIssues = FixedAssetService(db).depreciationControlIssues(period.startDate, period.endDate, period.fiscalYear, period.periodNo)
        require(fixedAssetIssues.isEmpty()) {
            "لا يمكن إقفال الفترة قبل ترحيل إهلاك الأصول الثابتة: " + fixedAssetIssues.joinToString("، ")
        }

        val now = System.currentTimeMillis()
        db.accountingDao().updatePeriod(
            period.copy(
                status = "CLOSED",
                closedBy = closedBy,
                closedAt = now,
                closeReason = reason.trim()
            )
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = closedBy,
                action = "CLOSE",
                entityType = "ACCOUNTING_PERIOD",
                entityId = period.id.toString(),
                oldValue = "OPEN",
                newValue = "CLOSED|${period.fiscalYear}-${period.periodNo}",
                reason = reason.trim()
            )
        )
        reconciliation
    }

    suspend fun reopenPeriod(periodId: Long, reason: String, reopenedBy: Long) = db.withTransaction {
        require(reason.trim().isNotBlank()) { "سبب إعادة فتح الفترة مطلوب" }
        val period = requireNotNull(db.accountingDao().periodById(periodId)) { "الفترة المحاسبية غير موجودة" }
        require(period.status == "CLOSED") { "الفترة مفتوحة بالفعل" }
        if (period.periodNo == 12 && db.accountingDao().latestClosedFiscalYear(period.fiscalYear) != null) {
            require(false) { "هذه السنة مقفلة سنوياً؛ استخدم إعادة فتح السنة المالية حتى يُعكس قيد الإقفال بصورة موثقة" }
        }
        require(db.accountingDao().laterClosedPeriodAfterDate(period.endDate) == 0) {
            "يجب إعادة فتح جميع الفترات المقفلة اللاحقة أولاً"
        }
        val now = System.currentTimeMillis()
        db.accountingDao().updatePeriod(
            period.copy(
                status = "OPEN",
                reopenedBy = reopenedBy,
                reopenedAt = now,
                reopenReason = reason.trim()
            )
        )
        reverseFxRevaluationsInRange(
            period.startDate,
            period.endDate,
            reopenedBy,
            "إعادة فتح الفترة ${period.fiscalYear}-${period.periodNo}: ${reason.trim()}"
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = reopenedBy,
                action = "REOPEN",
                entityType = "ACCOUNTING_PERIOD",
                entityId = period.id.toString(),
                oldValue = "CLOSED",
                newValue = "OPEN|${period.fiscalYear}-${period.periodNo}",
                reason = reason.trim()
            )
        )
    }

    suspend fun closeFiscalYear(
        fiscalYear: Int,
        reason: String,
        closedBy: Long
    ): FiscalYearClosingResult = db.withTransaction {
        require(reason.trim().isNotBlank()) { "سبب إقفال السنة المالية مطلوب" }
        require(fiscalYear in 2000..2200) { "السنة المالية غير صالحة" }
        require(db.accountingDao().latestClosedFiscalYear(fiscalYear) == null) { "السنة المالية مقفلة مسبقاً" }
        require(db.accountingDao().laterClosedFiscalYearCount(fiscalYear) == 0) {
            "لا يمكن إقفال سنة أقدم بعد إقفال سنة مالية لاحقة"
        }

        val periods = db.accountingDao().periodsForYear(fiscalYear)
        require(periods.size == 12 && periods.map { it.periodNo }.toSet() == (1..12).toSet()) {
            "يجب إنشاء 12 فترة محاسبية كاملة قبل إقفال السنة"
        }
        require(periods.filter { it.periodNo < 12 }.all { it.status == "CLOSED" }) {
            "يجب إقفال الفترات من 1 إلى 11 أولاً"
        }
        val finalPeriod = requireNotNull(periods.firstOrNull { it.periodNo == 12 })
        require(finalPeriod.status == "OPEN") {
            "الفترة 12 يجب أن تكون مفتوحة قبل الإقفال السنوي؛ أعد فتحها أولاً إذا أُقفلت بالطريقة القديمة"
        }
        require(finalPeriod.endDate < System.currentTimeMillis()) { "لا يمكن إقفال سنة مالية لم تنته بعد" }

        val reconciliation = reconciliation(finalPeriod.endDate)
        require(reconciliation.isMatched) {
            val problems = reconciliation.rows.filterNot { it.isMatched }
                .joinToString("، ") { "${it.labelAr}: ${it.differenceBase}" }
            "لا يمكن إقفال السنة قبل معالجة فروقات المطابقة${if (problems.isBlank()) "" else ": $problems"}"
        }

        val treasuryControl = treasuryControl(finalPeriod.startDate, finalPeriod.endDate)
        require(treasuryControl.isClear) {
            "لا يمكن إقفال السنة قبل استكمال رقابة الخزائن والبنوك للفترة 12: " + treasuryControl.issues.joinToString("، ") { "${it.treasuryName}: ${it.message}" }
        }

        val fixedAssetIssues = FixedAssetService(db).depreciationControlIssues(finalPeriod.startDate, finalPeriod.endDate, finalPeriod.fiscalYear, finalPeriod.periodNo)
        require(fixedAssetIssues.isEmpty()) {
            "لا يمكن إقفال السنة قبل ترحيل إهلاك الأصول الثابتة للفترة 12: " + fixedAssetIssues.joinToString("، ")
        }

        val yearStart = periods.minOf { it.startDate }
        val yearEnd = finalPeriod.endDate
        val temporaryDetails = db.journalDao().profitLossDetails(yearStart, yearEnd)
            .filter { it.accountType == "REVENUE" || it.accountType == "EXPENSE" }
        val movements = temporaryDetails.groupBy { it.accountId }.map { (accountId, rows) ->
            TemporaryAccountMovement(
                accountId = accountId,
                debit = rows.sumOf { it.debit },
                credit = rows.sumOf { it.credit }
            )
        }
        val retainedEarnings = requireNotNull(db.accountDao().byCode("3300")) {
            "حساب الأرباح المحتجزة 3300 غير موجود"
        }
        require(retainedEarnings.isActive && retainedEarnings.isPosting && retainedEarnings.type == "EQUITY") {
            "حساب الأرباح المحتجزة 3300 يجب أن يكون حساب حقوق ملكية نشطاً وقابلاً للترحيل"
        }
        val computation = FiscalYearClosingMath.compute(movements, retainedEarnings.id)

        val closingEntryId = if (computation.lines.isEmpty()) {
            null
        } else {
            val entryId = db.journalDao().insertEntry(
                JournalEntryEntity(
                    entryNo = documentNo("YEC"),
                    entryDate = yearEnd,
                    description = "إقفال السنة المالية $fiscalYear",
                    currencyCode = "YER_NEW",
                    exchangeRate = 1.0,
                    sourceType = "YEAR_END_CLOSE",
                    sourceId = fiscalYear.toString(),
                    createdBy = closedBy
                )
            )
            db.journalDao().insertLines(
                computation.lines.map { line ->
                    JournalLineEntity(
                        entryId = entryId,
                        accountId = line.accountId,
                        debit = line.debit,
                        credit = line.credit,
                        memo = if (line.accountId == retainedEarnings.id)
                            "ترحيل نتيجة السنة $fiscalYear" else "إقفال حساب مؤقت للسنة $fiscalYear"
                    )
                }
            )
            entryId
        }

        val now = System.currentTimeMillis()
        val closingRecordId = db.accountingDao().insertFiscalYearClosing(
            FiscalYearClosingEntity(
                fiscalYear = fiscalYear,
                startDate = yearStart,
                endDate = yearEnd,
                closingEntryId = closingEntryId,
                netIncomeBase = computation.netIncomeBase,
                retainedEarningsAccountId = retainedEarnings.id,
                status = "CLOSED",
                closeReason = reason.trim(),
                closedBy = closedBy,
                closedAt = now
            )
        )

        db.accountingDao().updatePeriod(
            finalPeriod.copy(
                status = "CLOSED",
                closedBy = closedBy,
                closedAt = now,
                closeReason = "إقفال سنوي: ${reason.trim()}"
            )
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = closedBy,
                action = "CLOSE",
                entityType = "FISCAL_YEAR",
                entityId = fiscalYear.toString(),
                oldValue = "OPEN",
                newValue = "CLOSED|entry=${closingEntryId ?: 0}|netIncome=${computation.netIncomeBase}",
                reason = reason.trim()
            )
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = closedBy,
                action = "CLOSE",
                entityType = "ACCOUNTING_PERIOD",
                entityId = finalPeriod.id.toString(),
                oldValue = "OPEN",
                newValue = "CLOSED|${finalPeriod.fiscalYear}-12|YEAR_END",
                reason = reason.trim()
            )
        )

        FiscalYearClosingResult(
            fiscalYear = fiscalYear,
            status = "CLOSED",
            closingRecordId = closingRecordId,
            closingEntryId = closingEntryId,
            reversalEntryId = null,
            netIncomeBase = computation.netIncomeBase
        )
    }

    suspend fun reopenFiscalYear(
        fiscalYear: Int,
        reason: String,
        reopenedBy: Long
    ): FiscalYearClosingResult = db.withTransaction {
        require(reason.trim().isNotBlank()) { "سبب إعادة فتح السنة المالية مطلوب" }
        val closing = requireNotNull(db.accountingDao().latestClosedFiscalYear(fiscalYear)) {
            "السنة المالية ليست مقفلة سنوياً"
        }
        require(db.accountingDao().laterClosedFiscalYearCount(fiscalYear) == 0) {
            "يجب إعادة فتح السنوات المالية اللاحقة أولاً"
        }
        require(db.accountingDao().laterClosedPeriodAfterDate(closing.endDate) == 0) {
            "يجب إعادة فتح جميع الفترات المقفلة اللاحقة أولاً"
        }
        val finalPeriod = requireNotNull(db.accountingDao().periodByYearNo(fiscalYear, 12)) {
            "الفترة 12 غير موجودة"
        }
        require(finalPeriod.status == "CLOSED") { "الفترة 12 ليست مقفلة" }

        val now = System.currentTimeMillis()
        db.accountingDao().updatePeriod(
            finalPeriod.copy(
                status = "OPEN",
                reopenedBy = reopenedBy,
                reopenedAt = now,
                reopenReason = "إعادة فتح سنوي: ${reason.trim()}"
            )
        )

        val reversalEntryId = closing.closingEntryId?.let { closingEntryId ->
            require(db.journalDao().reversalCount(closingEntryId) == 0) { "تم عكس قيد الإقفال السنوي مسبقاً" }
            val original = requireNotNull(db.journalDao().byId(closingEntryId)) { "قيد الإقفال السنوي غير موجود" }
            require(original.sourceType == "YEAR_END_CLOSE") { "القيد المرتبط ليس قيد إقفال سنوي" }
            val originalLines = db.journalDao().linesForEntry(closingEntryId)
            require(originalLines.isNotEmpty()) { "قيد الإقفال السنوي لا يحتوي سطوراً" }
            val reversed = originalLines.map { DraftJournalLine(it.accountId, it.credit, it.debit) }
            AccountingValidator.validate(reversed)
            val reversalId = db.journalDao().insertEntry(
                JournalEntryEntity(
                    entryNo = documentNo("YER"),
                    entryDate = closing.endDate,
                    description = "عكس إقفال السنة المالية $fiscalYear: ${reason.trim()}",
                    currencyCode = original.currencyCode,
                    exchangeRate = original.exchangeRate,
                    sourceType = "REVERSAL",
                    sourceId = closingEntryId.toString(),
                    createdBy = reopenedBy
                )
            )
            db.journalDao().insertLines(
                originalLines.map { line ->
                    JournalLineEntity(
                        entryId = reversalId,
                        accountId = line.accountId,
                        debit = line.credit,
                        credit = line.debit,
                        memo = "عكس إقفال سنوي: ${line.memo}"
                    )
                }
            )
            reversalId
        }

        reverseFxRevaluationsInRange(
            finalPeriod.startDate,
            finalPeriod.endDate,
            reopenedBy,
            "إعادة فتح السنة المالية $fiscalYear: ${reason.trim()}"
        )

        db.accountingDao().updateFiscalYearClosing(
            closing.copy(
                status = "REOPENED",
                reversalEntryId = reversalEntryId,
                reopenReason = reason.trim(),
                reopenedBy = reopenedBy,
                reopenedAt = now
            )
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = reopenedBy,
                action = "REOPEN",
                entityType = "FISCAL_YEAR",
                entityId = fiscalYear.toString(),
                oldValue = "CLOSED|entry=${closing.closingEntryId ?: 0}",
                newValue = "REOPENED|reversal=${reversalEntryId ?: 0}",
                reason = reason.trim()
            )
        )
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = reopenedBy,
                action = "REOPEN",
                entityType = "ACCOUNTING_PERIOD",
                entityId = finalPeriod.id.toString(),
                oldValue = "CLOSED",
                newValue = "OPEN|${finalPeriod.fiscalYear}-12|YEAR_END_REOPEN",
                reason = reason.trim()
            )
        )

        FiscalYearClosingResult(
            fiscalYear = fiscalYear,
            status = "REOPENED",
            closingRecordId = closing.id,
            closingEntryId = closing.closingEntryId,
            reversalEntryId = reversalEntryId,
            netIncomeBase = closing.netIncomeBase
        )
    }

    suspend fun reconciliation(asOf: Long): AccountingReconciliationReport {
        val details = reportDetails(0L, asOf)
        val arGl = AccountingReconciliationMath.naturalDebitBalance(details, "1300")
        val apGl = AccountingReconciliationMath.naturalCreditBalance(details, "2100")
        val inventoryGl = AccountingReconciliationMath.naturalDebitBalance(details, "1200")
        val fixedAssetsGl = AccountingReconciliationMath.naturalDebitBalance(details, "1500")
        val accumulatedDepreciationGl = AccountingReconciliationMath.naturalCreditBalance(details, "1590")

        var arSubledger = 0.0
        for (customer in db.customerDao().allCustomers()) {
            arSubledger += db.salesDao().customerLedgerEvents(customer.id)
                .asSequence()
                .filter { it.eventDate <= asOf }
                .sumOf { it.debitBase - it.creditBase }
        }

        var apSubledger = 0.0
        for (supplier in db.supplierDao().allSuppliers()) {
            apSubledger += db.purchaseDao().supplierLedgerEvents(supplier.id, asOf)
                .sumOf { it.creditBase - it.debitBase }
        }

        val inventorySubledger = db.accountingDao().inventorySubledgerValue(asOf)
        val fixedAssetsSubledger = db.fixedAssetDao().fixedAssetCostSubledger(asOf)
        val accumulatedDepreciationSubledger = db.fixedAssetDao().accumulatedDepreciationSubledger(asOf)
        val trial = AccountingReportMath.trialBalance(details)

        return AccountingReconciliationReport(
            asOf = asOf,
            rows = listOf(
                AccountingReconciliationMath.row("1300", "العملاء", arGl, arSubledger),
                AccountingReconciliationMath.row("2100", "الموردون", apGl, apSubledger),
                AccountingReconciliationMath.row("1200", "المخزون", inventoryGl, inventorySubledger),
                AccountingReconciliationMath.row("1500", "الأصول الثابتة", fixedAssetsGl, fixedAssetsSubledger),
                AccountingReconciliationMath.row("1590", "مجمع الإهلاك", accumulatedDepreciationGl, accumulatedDepreciationSubledger)
            ),
            trialBalanceDifferenceBase = trial.totalDebitMovement - trial.totalCreditMovement
        )
    }

    suspend fun requirePostingPeriodOpen(entryDate: Long) {
        val period = db.accountingDao().periodForDate(entryDate) ?: return
        require(period.status == "OPEN") {
            "الفترة المحاسبية ${period.nameAr} مقفلة؛ لا يمكن ترحيل حركة بتاريخ ${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(entryDate))}"
        }
    }

    suspend fun journalDetails(entryId: Long) = db.journalDao().detailsForEntry(entryId)

    suspend fun trialBalance(asOf: Long): TrialBalanceReport =
        AccountingReportMath.trialBalance(reportDetails(0L, asOf))

    suspend fun ledger(accountId: Long, fromDate: Long, toDate: Long): LedgerReport {
        require(fromDate <= toDate) { "الفترة غير صحيحة" }
        return AccountingReportMath.ledger(reportDetails(0L, toDate), accountId, fromDate, toDate)
    }

    suspend fun profitLoss(fromDate: Long, toDate: Long): ProfitLossReport {
        require(fromDate <= toDate) { "الفترة غير صحيحة" }
        return AccountingReportMath.profitLoss(profitLossDetails(fromDate, toDate))
    }

    suspend fun balanceSheet(asOf: Long): BalanceSheetReport =
        AccountingReportMath.balanceSheet(reportDetails(0L, asOf))

    suspend fun cashFlow(fromDate: Long, toDate: Long): CashFlowReport {
        require(fromDate <= toDate) { "الفترة غير صحيحة" }
        val treasuryIds = db.accountingDao().allActiveTreasury().map { it.accountId }.toSet()
        return AccountingReportMath.cashFlow(reportDetails(0L, toDate), treasuryIds, fromDate, toDate)
    }

    private suspend fun profitLossDetails(fromDate: Long, toDate: Long): List<ReportDetail> =
        db.journalDao().profitLossDetails(fromDate, toDate).map {
            ReportDetail(
                accountId = it.accountId,
                accountCode = it.accountCode,
                accountNameAr = it.accountNameAr,
                accountType = it.accountType,
                entryId = it.entryId,
                entryNo = it.entryNo,
                entryDate = it.entryDate,
                description = it.description,
                sourceType = it.sourceType,
                debit = it.debit,
                credit = it.credit,
                memo = it.memo
            )
        }

    private suspend fun reportDetails(fromDate: Long, toDate: Long): List<ReportDetail> =
        db.journalDao().reportDetails(fromDate, toDate).map {
            ReportDetail(
                accountId = it.accountId,
                accountCode = it.accountCode,
                accountNameAr = it.accountNameAr,
                accountType = it.accountType,
                entryId = it.entryId,
                entryNo = it.entryNo,
                entryDate = it.entryDate,
                description = it.description,
                sourceType = it.sourceType,
                debit = it.debit,
                credit = it.credit,
                memo = it.memo
            )
        }

    private suspend fun insertExpenseDimension(
        partyVoucherId: Long,
        treasury: TreasuryAccountEntity,
        context: ExpenseContext,
        createdBy: Long
    ) {
        val costCenters = mapOf(
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
        val costCenterCode = context.costCenterCode.trim().uppercase(Locale.US)
        val costCenterName = requireNotNull(costCenters[costCenterCode]) { "مركز التكلفة غير صالح" }
        val employee = context.employeeId?.let { id ->
            requireNotNull(db.employeeDao().employeeById(id)) { "الموظف المرتبط بالمصروف غير موجود" }
        }
        val inferredRep = employee?.id?.let { db.salesRepresentativeDao().byEmployeeId(it) }
        val rep = context.salesRepId?.let { id ->
            requireNotNull(db.salesRepresentativeDao().byId(id)) { "مندوب المبيعات المرتبط بالمصروف غير موجود" }
        } ?: inferredRep
        val customer = context.customerId?.let { id -> requireNotNull(db.customerDao().byId(id)) { "العميل المرتبط بالمصروف غير موجود" } }
        val supplier = context.supplierId?.let { id -> requireNotNull(db.supplierDao().byId(id)) { "المورد المرتبط بالمصروف غير موجود" } }
        val item = context.itemId?.let { id -> requireNotNull(db.itemDao().byId(id)) { "المنتج/الصنف المرتبط بالمصروف غير موجود" } }

        val allowedReferences = setOf(
            "NONE", "SALES_INVOICE", "SALES_ORDER", "PURCHASE_INVOICE", "PURCHASE_ORDER",
            "PRODUCTION_ORDER", "DISTRIBUTION", "CUSTOMER", "SUPPLIER", "PRODUCT", "BRANCH", "FACILITY", "OTHER"
        )
        val referenceType = context.referenceType.trim().uppercase(Locale.US).ifBlank { "NONE" }
        require(referenceType in allowedReferences) { "نوع مرجع المصروف غير صالح" }
        var referenceId = context.referenceId
        var referenceNo = context.referenceNo.trim()
        var referenceLabel = context.referenceLabel.trim()
        when (referenceType) {
            "SALES_INVOICE" -> {
                val row = requireNotNull(referenceId?.let { db.salesDao().invoiceById(it) }) { "اختر فاتورة المبيعات المرتبطة بالمصروف" }
                referenceNo = row.invoiceNo
                referenceLabel = "فاتورة مبيعات ${row.invoiceNo}"
            }
            "PURCHASE_INVOICE" -> {
                val row = requireNotNull(referenceId?.let { db.purchaseDao().invoiceById(it) }) { "اختر فاتورة المشتريات المرتبطة بالمصروف" }
                referenceNo = row.invoiceNo
                referenceLabel = "فاتورة مشتريات ${row.invoiceNo}"
            }
            "PRODUCTION_ORDER" -> {
                val row = requireNotNull(referenceId?.let { db.productionDao().orderById(it) }) { "اختر أمر الإنتاج المرتبط بالمصروف" }
                referenceNo = row.orderNo
                referenceLabel = "أمر إنتاج ${row.orderNo}"
            }
            "CUSTOMER" -> {
                val row = requireNotNull(customer) { "اختر العميل المرتبط بالمصروف" }
                referenceId = row.id
                referenceNo = row.code
                referenceLabel = row.nameAr
            }
            "SUPPLIER" -> {
                val row = requireNotNull(supplier) { "اختر المورد المرتبط بالمصروف" }
                referenceId = row.id
                referenceNo = row.code
                referenceLabel = row.nameAr
            }
            "PRODUCT" -> {
                val row = requireNotNull(item) { "اختر المنتج/الصنف المرتبط بالمصروف" }
                referenceId = row.id
                referenceNo = row.code
                referenceLabel = row.nameAr
            }
            "SALES_ORDER", "PURCHASE_ORDER", "DISTRIBUTION", "BRANCH", "FACILITY", "OTHER" -> {
                require(referenceNo.isNotBlank() || referenceLabel.isNotBlank() || context.organizationUnit.isNotBlank()) {
                    "أدخل رقم أو وصف مرجع المصروف"
                }
            }
            else -> {
                referenceId = null
                referenceNo = ""
                referenceLabel = ""
            }
        }

        val expenseId = db.expenseDao().insertDimension(
            ExpenseDimensionEntity(
                partyVoucherId = partyVoucherId,
                employeeId = employee?.id,
                employeeNameSnapshot = employee?.fullNameAr.orEmpty(),
                salesRepId = rep?.id,
                salesRepNameSnapshot = rep?.fullNameAr.orEmpty(),
                costCenterCode = costCenterCode,
                costCenterNameSnapshot = costCenterName,
                organizationUnit = context.organizationUnit.trim(),
                referenceType = referenceType,
                referenceId = referenceId,
                referenceNo = referenceNo,
                referenceLabelSnapshot = referenceLabel,
                customerId = customer?.id,
                customerNameSnapshot = customer?.nameAr.orEmpty(),
                supplierId = supplier?.id,
                supplierNameSnapshot = supplier?.nameAr.orEmpty(),
                itemId = item?.id,
                itemNameSnapshot = item?.nameAr.orEmpty(),
                paymentMethodSnapshot = "${treasury.kind} — ${treasury.nameAr}",
                createdBy = createdBy
            )
        )
        context.attachment?.let { attachment ->
            require(attachment.fileName.trim().isNotBlank() && attachment.uri.trim().isNotBlank()) { "بيانات مرفق المصروف غير مكتملة" }
            db.expenseDao().insertAttachment(
                ExpenseAttachmentEntity(
                    expenseId = expenseId,
                    fileName = attachment.fileName.trim(),
                    mimeType = attachment.mimeType.trim(),
                    uri = attachment.uri.trim(),
                    notes = attachment.notes.trim(),
                    createdBy = createdBy
                )
            )
        }
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = createdBy,
                action = "POST",
                entityType = "EXPENSE_DIMENSION",
                entityId = expenseId.toString(),
                newValue = "$partyVoucherId|$costCenterCode|${employee?.id ?: 0}|${rep?.id ?: 0}|$referenceType|${referenceId ?: 0}",
                reason = "تصنيف وربط المصروف"
            )
        )
    }

    private suspend fun resolvePartyLink(offset: AccountEntity, request: VoucherRequest): PartyLink {
        return when (offset.code) {
            "1300" -> {
                require(request.supplierId == null && request.employeeId == null && request.salesRepId == null) { "حساب العملاء يقبل عميلاً فقط" }
                val id = requireNotNull(request.customerId) { "يجب تحديد العميل عند استخدام حساب العملاء" }
                val customer = requireNotNull(db.customerDao().byId(id)) { "العميل المحدد غير موجود" }
                require(customer.isActive) { "العميل المحدد غير نشط" }
                PartyLink("CUSTOMER", customer.nameAr, customerId = customer.id)
            }
            "2100" -> {
                require(request.customerId == null && request.employeeId == null && request.salesRepId == null) { "حساب الموردين يقبل مورداً فقط" }
                val id = SupplierMovementIdentity.requireId(request.supplierId)
                val supplier = requireNotNull(db.supplierDao().byId(id)) { "المورد المحدد غير موجود" }
                require(supplier.isActive) { "المورد المحدد غير نشط" }
                PartyLink("SUPPLIER", supplier.nameAr, supplierId = supplier.id)
            }
            "2200" -> {
                require(request.customerId == null && request.supplierId == null && request.salesRepId == null) { "حساب أجور الإنتاج يقبل موظفاً فقط" }
                val id = requireNotNull(request.employeeId) { "يجب تحديد الموظف عند استخدام حساب أجور الإنتاج المستحقة" }
                val employee = requireNotNull(db.employeeDao().employeeById(id)) { "الموظف المحدد غير موجود" }
                require(employee.status == "ACTIVE") { "الموظف المحدد غير نشط" }
                PartyLink("EMPLOYEE", employee.fullNameAr, employeeId = employee.id)
            }
            "2300" -> {
                require(request.customerId == null && request.supplierId == null && request.employeeId == null) { "حساب عمولات البيع يقبل مندوب مبيعات فقط" }
                val id = requireNotNull(request.salesRepId) { "يجب تحديد مندوب المبيعات عند استخدام حساب عمولات البيع المستحقة" }
                val rep = requireNotNull(db.salesRepresentativeDao().byId(id)) { "مندوب المبيعات المحدد غير موجود" }
                require(rep.status == "ACTIVE") { "مندوب المبيعات المحدد غير نشط" }
                PartyLink("SALES_REP", rep.fullNameAr, salesRepId = rep.id)
            }
            else -> {
                require(request.customerId == null && request.supplierId == null && request.employeeId == null && request.salesRepId == null) {
                    "لا يمكن ربط طرف بحساب عام غير مخصص للأطراف"
                }
                PartyLink()
            }
        }
    }

    private suspend fun requirePostingAccount(id: Long): AccountEntity {
        val account = requireNotNull(db.accountDao().byId(id)) { "الحساب غير موجود" }
        require(account.isActive) { "الحساب غير نشط" }
        require(account.isPosting) { "لا يمكن الترحيل إلى حساب تجميعي" }
        return account
    }

    private suspend fun validateRate(currencyCode: String, exchangeRate: Double) {
        require(db.currencyDao().allActive().any { it.code == currencyCode }) { "العملة غير موجودة" }
        require(exchangeRate > 0.0 && exchangeRate.isFinite()) { "سعر الصرف يجب أن يكون أكبر من صفر" }
        if (currencyCode == "YER_NEW") require(kotlin.math.abs(exchangeRate - 1.0) < 0.0000001) { "سعر العملة الأساسية يجب أن يساوي 1" }
    }

    private fun documentNo(prefix: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "$prefix-$stamp-${UUID.randomUUID().toString().take(6)}"
    }

    companion object {
        val REVERSIBLE_SOURCE_TYPES = setOf(
            "MANUAL",
            TreasuryMovementType.CUSTOMER_RECEIPT.sourceType,
            TreasuryMovementType.SUPPLIER_PAYMENT.sourceType,
            TreasuryMovementType.EXPENSE_PAYMENT.sourceType,
            TreasuryMovementType.EMPLOYEE_PAYMENT.sourceType,
            TreasuryMovementType.TRANSFER.sourceType,
            TreasuryMovementType.ADJUSTMENT.sourceType,
            // Historical Phase 14.5.x treasury source names remain reversible after upgrade.
            "TREASURY_RECEIPT", "TREASURY_PAYMENT", "TREASURY_EXPENSE",
            "TREASURY_INCOME", "TREASURY_TRANSFER"
        )
    }
}
