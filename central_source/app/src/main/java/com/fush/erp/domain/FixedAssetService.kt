package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AuditEventEntity
import com.fush.erp.data.entity.FixedAssetDepreciationEntity
import com.fush.erp.data.entity.FixedAssetDisposalEntity
import com.fush.erp.data.entity.FixedAssetEntity
import com.fush.erp.data.entity.FixedAssetRegisterRow
import com.fush.erp.data.entity.JournalEntryEntity
import com.fush.erp.data.entity.JournalLineEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class FixedAssetService(private val db: FushDatabase) {
    data class RegisterRequest(
        val nameAr: String,
        val nameEn: String = "",
        val category: String = "OTHER",
        val maintenanceAssetId: Long? = null,
        val acquisitionDate: Long,
        val inServiceDate: Long,
        val usefulLifeMonths: Int,
        val residualValueBase: Double = 0.0,
        val acquisitionMode: String,
        val treasuryAccountId: Long? = null,
        val acquisitionCostOriginal: Double,
        val currencyCode: String = "YER_NEW",
        val exchangeRate: Double = 1.0,
        val notes: String = "",
        val createdBy: Long
    )

    data class DisposalRequest(
        val assetId: Long,
        val disposalDate: Long,
        val proceedsOriginal: Double = 0.0,
        val treasuryAccountId: Long? = null,
        val currencyCode: String = "YER_NEW",
        val exchangeRate: Double = 1.0,
        val reason: String,
        val createdBy: Long
    )

    fun observeRegister(): Flow<List<FixedAssetRegisterRow>> = db.fixedAssetDao().observeRegister()

    suspend fun registerAsset(request: RegisterRequest): Long = db.withTransaction {
        require(request.nameAr.trim().isNotBlank()) { "اسم الأصل مطلوب" }
        require(request.category in setOf("BUILDING", "MACHINERY", "VEHICLE", "EQUIPMENT", "FURNITURE", "IT", "OTHER")) { "تصنيف الأصل غير صالح" }
        require(request.acquisitionMode in setOf("TREASURY", "OPENING")) { "طريقة اقتناء الأصل غير صالحة" }
        require(request.inServiceDate >= request.acquisitionDate) { "تاريخ بدء الاستخدام لا يمكن أن يسبق تاريخ الاقتناء" }
        require(request.usefulLifeMonths > 0) { "العمر الإنتاجي بالأشهر مطلوب" }
        require(request.acquisitionCostOriginal.isFinite() && request.acquisitionCostOriginal > 0.0) { "تكلفة الأصل يجب أن تكون أكبر من صفر" }
        request.maintenanceAssetId?.let {
            requireNotNull(db.maintenanceDao().assetById(it)) { "الأصل التشغيلي المرتبط غير موجود" }
        }
        AccountingService(db).requirePostingPeriodOpen(request.acquisitionDate)

        val assetAccount = requireAccount("1500", "ASSET")
        val accumulatedAccount = requireAccount("1590", "ASSET")
        val depreciationExpense = requireAccount("6800", "EXPENSE")

        val costBase: Double
        val creditAccountId: Long
        val journalCurrency: String
        val journalRate: Double
        when (request.acquisitionMode) {
            "TREASURY" -> {
                val treasury = requireNotNull(request.treasuryAccountId?.let { db.accountingDao().treasuryById(it) }) { "الخزينة/البنك مطلوب لشراء الأصل" }
                require(treasury.isActive) { "الخزينة غير نشطة" }
                require(treasury.currencyCode == request.currencyCode) { "عملة شراء الأصل يجب أن تطابق عملة الخزينة" }
                validateRate(request.currencyCode, request.exchangeRate)
                costBase = request.acquisitionCostOriginal * request.exchangeRate
                creditAccountId = treasury.accountId
                journalCurrency = request.currencyCode
                journalRate = request.exchangeRate
            }
            else -> {
                require(request.currencyCode == "YER_NEW" && kotlin.math.abs(request.exchangeRate - 1.0) < 0.0000001) {
                    "الرصيد الافتتاحي للأصول يسجل بالعملة الأساسية"
                }
                costBase = request.acquisitionCostOriginal
                creditAccountId = requireAccount("3100", "EQUITY").id
                journalCurrency = "YER_NEW"
                journalRate = 1.0
            }
        }
        FixedAssetMath.depreciableBase(costBase, request.residualValueBase)

        val assetNo = AutoNumberService(db).nextDocumentNo("FA", request.acquisitionDate)
        val journalLines = listOf(
            DraftJournalLine(assetAccount.id, costBase, 0.0),
            DraftJournalLine(creditAccountId, 0.0, costBase)
        )
        AccountingValidator.validate(journalLines)
        val entryId = db.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = AutoNumberService(db).nextDocumentNo("FAA", request.acquisitionDate),
                entryDate = request.acquisitionDate,
                description = "اقتناء أصل ثابت $assetNo — ${request.nameAr.trim()}",
                currencyCode = journalCurrency,
                exchangeRate = journalRate,
                sourceType = "FIXED_ASSET_ACQUISITION",
                sourceId = assetNo,
                createdBy = request.createdBy
            )
        )
        db.journalDao().insertLines(journalLines.map { JournalLineEntity(entryId = entryId, accountId = it.accountId, debit = it.debit, credit = it.credit) })

        val id = db.fixedAssetDao().insertAsset(
            FixedAssetEntity(
                assetNo = assetNo,
                maintenanceAssetId = request.maintenanceAssetId,
                nameAr = request.nameAr.trim(),
                nameEn = request.nameEn.trim(),
                category = request.category,
                acquisitionDate = request.acquisitionDate,
                inServiceDate = request.inServiceDate,
                acquisitionCostBase = costBase,
                residualValueBase = request.residualValueBase,
                usefulLifeMonths = request.usefulLifeMonths,
                assetAccountId = assetAccount.id,
                accumulatedDepreciationAccountId = accumulatedAccount.id,
                depreciationExpenseAccountId = depreciationExpense.id,
                acquisitionMode = request.acquisitionMode,
                acquisitionJournalEntryId = entryId,
                notes = request.notes.trim(),
                createdBy = request.createdBy
            )
        )
        audit(request.createdBy, "CREATE", "FIXED_ASSET", id, "$assetNo|cost=$costBase|mode=${request.acquisitionMode}|journal=$entryId", request.notes.ifBlank { "تسجيل أصل ثابت" })
        id
    }

    suspend fun postDepreciationForPeriod(periodId: Long, createdBy: Long): List<Long> = db.withTransaction {
        val period = requireNotNull(db.accountingDao().periodById(periodId)) { "الفترة المحاسبية غير موجودة" }
        require(period.status == "OPEN") { "الفترة المحاسبية مقفلة" }
        require(period.endDate < System.currentTimeMillis()) { "لا يمكن ترحيل إهلاك فترة لم تنته بعد" }
        val ids = mutableListOf<Long>()
        for (asset in db.fixedAssetDao().assetsRelevantToPeriod(period.startDate, period.endDate)) {
            postDepreciationInternal(asset, period.fiscalYear, period.periodNo, period.startDate, period.endDate, period.endDate, createdBy)?.let(ids::add)
        }
        ids
    }

    suspend fun reverseDepreciation(depreciationId: Long, reason: String, reversedBy: Long, reversalDate: Long = System.currentTimeMillis()): Long = db.withTransaction {
        require(reason.trim().isNotBlank()) { "سبب عكس الإهلاك مطلوب" }
        AccountingService(db).requirePostingPeriodOpen(reversalDate)
        val dep = requireNotNull(db.fixedAssetDao().depreciationById(depreciationId)) { "قيد الإهلاك غير موجود" }
        require(dep.status == "POSTED") { "تم عكس هذا الإهلاك مسبقاً" }
        require(db.fixedAssetDao().laterPostedDepreciationCount(dep.assetId, dep.depreciationDate) == 0) { "يجب عكس الإهلاكات اللاحقة أولاً" }
        require(db.fixedAssetDao().activeDisposal(dep.assetId) == null) { "يجب عكس استبعاد الأصل أولاً" }
        val reversalId = reverseJournal(dep.journalEntryId, reversalDate, "عكس إهلاك أصل: ${reason.trim()}", "FIXED_ASSET_DEPRECIATION_REVERSAL", reversedBy)
        db.fixedAssetDao().updateDepreciation(dep.copy(status = "REVERSED", reversalEntryId = reversalId, reversalReason = reason.trim(), reversedBy = reversedBy, reversedAt = reversalDate))
        val asset = requireNotNull(db.fixedAssetDao().assetById(dep.assetId)) { "الأصل غير موجود" }
        if (asset.status == "FULLY_DEPRECIATED") db.fixedAssetDao().updateAsset(asset.copy(status = "ACTIVE", updatedAt = System.currentTimeMillis()))
        audit(reversedBy, "REVERSE", "FIXED_ASSET_DEPRECIATION", dep.id, "journal=$reversalId", reason.trim())
        reversalId
    }

    suspend fun disposeAsset(request: DisposalRequest): Long = db.withTransaction {
        require(request.reason.trim().isNotBlank()) { "سبب الاستبعاد/البيع مطلوب" }
        require(request.proceedsOriginal.isFinite() && request.proceedsOriginal >= 0.0) { "متحصلات البيع غير صالحة" }
        AccountingService(db).requirePostingPeriodOpen(request.disposalDate)
        val asset = requireNotNull(db.fixedAssetDao().assetById(request.assetId)) { "الأصل غير موجود" }
        require(asset.status in setOf("ACTIVE", "FULLY_DEPRECIATED")) { "الأصل غير متاح للاستبعاد" }
        require(request.disposalDate >= asset.inServiceDate) { "تاريخ الاستبعاد يسبق بدء استخدام الأصل" }
        require(db.fixedAssetDao().activeDisposal(asset.id) == null) { "الأصل مستبعد مسبقاً" }
        require(db.fixedAssetDao().laterPostedDepreciationCount(asset.id, request.disposalDate) == 0) { "يوجد إهلاك مرحل بعد تاريخ الاستبعاد" }

        val period = requireNotNull(db.accountingDao().periodForDate(request.disposalDate)) { "يجب إنشاء فترة محاسبية لتاريخ الاستبعاد" }
        require(period.status == "OPEN") { "فترة الاستبعاد مقفلة" }
        postDepreciationInternal(asset, period.fiscalYear, period.periodNo, period.startDate, period.endDate, request.disposalDate, request.createdBy)

        val accumulated = db.fixedAssetDao().accumulatedDepreciation(asset.id, request.disposalDate)
        val nbv = FixedAssetMath.netBookValue(asset.acquisitionCostBase, accumulated)
        var proceedsBase = 0.0
        var treasuryAccountId: Long? = null
        var journalCurrency = "YER_NEW"
        var journalRate = 1.0
        var treasuryGlAccountId: Long? = null
        if (request.proceedsOriginal > FixedAssetMath.TOLERANCE) {
            val treasury = requireNotNull(request.treasuryAccountId?.let { db.accountingDao().treasuryById(it) }) { "الخزينة/البنك مطلوب عند وجود متحصلات بيع" }
            require(treasury.isActive) { "الخزينة غير نشطة" }
            require(treasury.currencyCode == request.currencyCode) { "عملة المتحصلات يجب أن تطابق عملة الخزينة" }
            validateRate(request.currencyCode, request.exchangeRate)
            proceedsBase = request.proceedsOriginal * request.exchangeRate
            treasuryAccountId = treasury.id
            treasuryGlAccountId = treasury.accountId
            journalCurrency = request.currencyCode
            journalRate = request.exchangeRate
        } else {
            require(request.treasuryAccountId == null) { "لا تحدد خزينة عندما لا توجد متحصلات" }
        }
        val gainLoss = FixedAssetMath.disposalGainLoss(proceedsBase, nbv)
        val lines = mutableListOf<DraftJournalLine>()
        if (accumulated > FixedAssetMath.TOLERANCE) lines += DraftJournalLine(asset.accumulatedDepreciationAccountId, accumulated, 0.0)
        if (proceedsBase > FixedAssetMath.TOLERANCE) lines += DraftJournalLine(requireNotNull(treasuryGlAccountId), proceedsBase, 0.0)
        if (gainLoss < -FixedAssetMath.TOLERANCE) lines += DraftJournalLine(requireAccount("6900", "EXPENSE").id, -gainLoss, 0.0)
        lines += DraftJournalLine(asset.assetAccountId, 0.0, asset.acquisitionCostBase)
        if (gainLoss > FixedAssetMath.TOLERANCE) lines += DraftJournalLine(requireAccount("4200", "REVENUE").id, 0.0, gainLoss)
        AccountingValidator.validate(lines)
        val entryId = db.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = AutoNumberService(db).nextDocumentNo("FAD", request.disposalDate),
                entryDate = request.disposalDate,
                description = "استبعاد/بيع الأصل ${asset.assetNo} — ${asset.nameAr}",
                currencyCode = journalCurrency,
                exchangeRate = journalRate,
                sourceType = "FIXED_ASSET_DISPOSAL",
                sourceId = asset.id.toString(),
                createdBy = request.createdBy
            )
        )
        db.journalDao().insertLines(lines.map { JournalLineEntity(entryId = entryId, accountId = it.accountId, debit = it.debit, credit = it.credit) })
        val disposalId = db.fixedAssetDao().insertDisposal(
            FixedAssetDisposalEntity(
                assetId = asset.id,
                disposalDate = request.disposalDate,
                proceedsBase = proceedsBase,
                treasuryAccountId = treasuryAccountId,
                currencyCode = journalCurrency,
                exchangeRate = journalRate,
                proceedsOriginal = request.proceedsOriginal,
                acquisitionCostBase = asset.acquisitionCostBase,
                accumulatedDepreciationBase = accumulated,
                carryingValueBase = nbv,
                gainLossBase = gainLoss,
                journalEntryId = entryId,
                reason = request.reason.trim(),
                createdBy = request.createdBy
            )
        )
        db.fixedAssetDao().updateAsset(asset.copy(status = "DISPOSED", disposalDate = request.disposalDate, disposalProceedsBase = proceedsBase, disposalGainLossBase = gainLoss, disposalJournalEntryId = entryId, disposalReason = request.reason.trim(), updatedAt = System.currentTimeMillis()))
        audit(request.createdBy, "DISPOSE", "FIXED_ASSET", asset.id, "disposal=$disposalId|journal=$entryId|nbv=$nbv|proceeds=$proceedsBase|gainLoss=$gainLoss", request.reason.trim())
        disposalId
    }

    suspend fun reverseDisposal(disposalId: Long, reason: String, reversedBy: Long, reversalDate: Long = System.currentTimeMillis()): Long = db.withTransaction {
        require(reason.trim().isNotBlank()) { "سبب عكس الاستبعاد مطلوب" }
        AccountingService(db).requirePostingPeriodOpen(reversalDate)
        val disposal = requireNotNull(db.fixedAssetDao().disposalById(disposalId)) { "عملية الاستبعاد غير موجودة" }
        require(disposal.status == "POSTED") { "تم عكس الاستبعاد مسبقاً" }
        val asset = requireNotNull(db.fixedAssetDao().assetById(disposal.assetId)) { "الأصل غير موجود" }
        val reversalId = reverseJournal(disposal.journalEntryId, reversalDate, "عكس استبعاد أصل: ${reason.trim()}", "FIXED_ASSET_DISPOSAL_REVERSAL", reversedBy)
        db.fixedAssetDao().updateDisposal(disposal.copy(status = "REVERSED", reversalEntryId = reversalId, reversalReason = reason.trim(), reversedBy = reversedBy, reversedAt = reversalDate))
        val accumulated = db.fixedAssetDao().accumulatedDepreciation(asset.id, reversalDate)
        val fully = accumulated >= FixedAssetMath.depreciableBase(asset.acquisitionCostBase, asset.residualValueBase) - FixedAssetMath.TOLERANCE
        db.fixedAssetDao().updateAsset(asset.copy(status = if (fully) "FULLY_DEPRECIATED" else "ACTIVE", disposalDate = null, disposalProceedsBase = 0.0, disposalGainLossBase = 0.0, disposalJournalEntryId = null, disposalReason = "", updatedAt = System.currentTimeMillis()))
        audit(reversedBy, "REVERSE_DISPOSAL", "FIXED_ASSET", asset.id, "journal=$reversalId", reason.trim())
        reversalId
    }

    suspend fun cancelAcquisition(assetId: Long, reason: String, cancelledBy: Long, reversalDate: Long = System.currentTimeMillis()): Long = db.withTransaction {
        require(reason.trim().isNotBlank()) { "سبب إلغاء اقتناء الأصل مطلوب" }
        AccountingService(db).requirePostingPeriodOpen(reversalDate)
        val asset = requireNotNull(db.fixedAssetDao().assetById(assetId)) { "الأصل غير موجود" }
        require(asset.status in setOf("ACTIVE", "FULLY_DEPRECIATED")) { "لا يمكن إلغاء هذا الأصل" }
        require(db.fixedAssetDao().postedDepreciationCount(asset.id) == 0) { "لا يمكن إلغاء الاقتناء بعد ترحيل الإهلاك؛ استخدم الاستبعاد" }
        require(db.fixedAssetDao().activeDisposal(asset.id) == null) { "يجب عكس الاستبعاد أولاً" }
        val originalEntryId = requireNotNull(asset.acquisitionJournalEntryId) { "قيد اقتناء الأصل غير موجود" }
        val reversalId = reverseJournal(originalEntryId, reversalDate, "إلغاء اقتناء أصل: ${reason.trim()}", "FIXED_ASSET_ACQUISITION_REVERSAL", cancelledBy)
        db.fixedAssetDao().updateAsset(asset.copy(status = "CANCELLED", acquisitionReversalEntryId = reversalId, cancellationReason = reason.trim(), cancelledBy = cancelledBy, cancelledAt = reversalDate, updatedAt = System.currentTimeMillis()))
        audit(cancelledBy, "CANCEL", "FIXED_ASSET", asset.id, "journal=$reversalId", reason.trim())
        reversalId
    }

    suspend fun depreciationControlIssues(periodId: Long): List<String> {
        val period = requireNotNull(db.accountingDao().periodById(periodId)) { "الفترة المحاسبية غير موجودة" }
        return depreciationControlIssues(period.startDate, period.endDate, period.fiscalYear, period.periodNo)
    }

    suspend fun depreciationControlIssues(fromDate: Long, toDate: Long, fiscalYear: Int, periodNo: Int): List<String> {
        val issues = mutableListOf<String>()
        for (asset in db.fixedAssetDao().assetsRelevantToPeriod(fromDate, toDate)) {
            if (asset.inServiceDate > toDate || asset.status == "CANCELLED") continue
            if (asset.disposalDate != null && asset.disposalDate < fromDate) continue
            val accumulated = db.fixedAssetDao().accumulatedDepreciation(asset.id, toDate)
            val remaining = FixedAssetMath.depreciableBase(asset.acquisitionCostBase, asset.residualValueBase) - accumulated
            if (remaining <= FixedAssetMath.TOLERANCE) continue
            if (db.fixedAssetDao().postedDepreciationForPeriod(asset.id, fiscalYear, periodNo) == null) {
                issues += "${asset.assetNo} — ${asset.nameAr}: إهلاك الفترة غير مرحل"
            }
        }
        return issues
    }

    private suspend fun postDepreciationInternal(asset: FixedAssetEntity, fiscalYear: Int, periodNo: Int, periodStart: Long, periodEnd: Long, postingDate: Long, createdBy: Long): Long? {
        if (asset.status == "CANCELLED" || asset.inServiceDate > periodEnd || (asset.disposalDate != null && asset.disposalDate < periodStart)) return null
        if (db.fixedAssetDao().postedDepreciationForPeriod(asset.id, fiscalYear, periodNo) != null) return null
        val accumulatedBefore = db.fixedAssetDao().accumulatedDepreciation(asset.id, periodStart - 1)
        val amount = FixedAssetMath.depreciationForPeriod(asset.acquisitionCostBase, asset.residualValueBase, asset.usefulLifeMonths, accumulatedBefore)
        if (amount <= FixedAssetMath.TOLERANCE) return null
        AccountingService(db).requirePostingPeriodOpen(postingDate)
        val lines = listOf(
            DraftJournalLine(asset.depreciationExpenseAccountId, amount, 0.0),
            DraftJournalLine(asset.accumulatedDepreciationAccountId, 0.0, amount)
        )
        AccountingValidator.validate(lines)
        val entryId = db.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = AutoNumberService(db).nextDocumentNo("FADP", postingDate),
                entryDate = postingDate,
                description = "إهلاك الأصل ${asset.assetNo} — ${asset.nameAr} — $fiscalYear/$periodNo",
                currencyCode = "YER_NEW",
                exchangeRate = 1.0,
                sourceType = "FIXED_ASSET_DEPRECIATION",
                sourceId = "${asset.id}:$fiscalYear:$periodNo",
                createdBy = createdBy
            )
        )
        db.journalDao().insertLines(lines.map { JournalLineEntity(entryId = entryId, accountId = it.accountId, debit = it.debit, credit = it.credit) })
        val depId = db.fixedAssetDao().insertDepreciation(
            FixedAssetDepreciationEntity(
                assetId = asset.id,
                fiscalYear = fiscalYear,
                periodNo = periodNo,
                depreciationDate = postingDate,
                amountBase = amount,
                journalEntryId = entryId,
                createdBy = createdBy
            )
        )
        val accumulatedAfter = accumulatedBefore + amount
        val fully = accumulatedAfter >= FixedAssetMath.depreciableBase(asset.acquisitionCostBase, asset.residualValueBase) - FixedAssetMath.TOLERANCE
        if (fully && asset.status == "ACTIVE") db.fixedAssetDao().updateAsset(asset.copy(status = "FULLY_DEPRECIATED", updatedAt = System.currentTimeMillis()))
        audit(createdBy, "DEPRECIATE", "FIXED_ASSET", asset.id, "period=$fiscalYear/$periodNo|amount=$amount|journal=$entryId", "ترحيل الإهلاك الشهري")
        return depId
    }

    private suspend fun reverseJournal(originalEntryId: Long, reversalDate: Long, description: String, sourceType: String, createdBy: Long): Long {
        val original = requireNotNull(db.journalDao().byId(originalEntryId)) { "القيد الأصلي غير موجود" }
        val originalLines = db.journalDao().linesForEntry(originalEntryId)
        require(originalLines.isNotEmpty()) { "القيد الأصلي لا يحتوي سطوراً" }
        val reversed = originalLines.map { DraftJournalLine(it.accountId, it.credit, it.debit) }
        AccountingValidator.validate(reversed)
        val id = db.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = AutoNumberService(db).nextDocumentNo("FAR", reversalDate),
                entryDate = reversalDate,
                description = description,
                currencyCode = original.currencyCode,
                exchangeRate = original.exchangeRate,
                sourceType = sourceType,
                sourceId = originalEntryId.toString(),
                createdBy = createdBy
            )
        )
        db.journalDao().insertLines(originalLines.map { JournalLineEntity(entryId = id, accountId = it.accountId, debit = it.credit, credit = it.debit, memo = "عكس: ${it.memo}") })
        return id
    }

    private suspend fun requireAccount(code: String, type: String) = requireNotNull(db.accountDao().byCode(code)) { "الحساب $code غير موجود" }.also {
        require(it.isActive && it.isPosting && it.type == type) { "الحساب $code غير صالح للترحيل" }
    }

    private suspend fun validateRate(currencyCode: String, exchangeRate: Double) {
        require(db.currencyDao().allActive().any { it.code == currencyCode }) { "العملة غير موجودة" }
        require(exchangeRate.isFinite() && exchangeRate > 0.0) { "سعر الصرف غير صالح" }
        if (currencyCode == "YER_NEW") require(kotlin.math.abs(exchangeRate - 1.0) < 0.0000001) { "سعر العملة الأساسية يجب أن يساوي 1" }
    }

    private suspend fun audit(userId: Long, action: String, entityType: String, entityId: Long, newValue: String, reason: String) {
        db.governanceDao().insertAudit(AuditEventEntity(userId = userId, action = action, entityType = entityType, entityId = entityId.toString(), newValue = newValue, reason = reason))
    }
}
