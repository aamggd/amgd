package com.fush.erp.domain

import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.JournalEntryEntity
import com.fush.erp.data.entity.JournalLineEntity
import kotlin.math.abs

/**
 * Rebuilds the journal that the original business document should have produced.
 *
 * This validator never trusts the STAGING journal to choose accounts or amounts. For cash/bank
 * operations, the originating document must carry the treasury provenance. Historical documents
 * created before v137 can legitimately have a null treasury reference; those repairs fail closed
 * rather than inferring the account from the journal being repaired.
 */
class JournalSemanticExpectation(private val db: FushDatabase) {
    data class ExpectedJournal(
        val sourceType: String,
        val sourceId: Long,
        val entryNo: String,
        val entryDate: Long,
        val currencyCode: String,
        val exchangeRate: Double,
        val lines: List<DraftJournalLine>,
        val sourceSummary: String,
    )

    data class Comparison(
        val matches: Boolean,
        val summary: String,
        val expected: ExpectedJournal,
    )

    suspend fun compare(entry: JournalEntryEntity, actualLines: List<JournalLineEntity>): Comparison {
        val expected = expectedFor(entry)
        val actualDraft = actualLines.map { DraftJournalLine(it.accountId, it.debit, it.credit) }
        val issues = JournalSemanticComparisonPolicy.issues(
            expectedHeader = JournalSemanticComparisonPolicy.Header(expected.entryNo, expected.entryDate, expected.currencyCode, expected.exchangeRate),
            actualHeader = JournalSemanticComparisonPolicy.Header(entry.entryNo, entry.entryDate, entry.currencyCode, entry.exchangeRate),
            expectedLines = expected.lines,
            actualLines = actualDraft,
        )
        val expectedLines = JournalSemanticComparisonPolicy.normalized(expected.lines)
        val actual = JournalSemanticComparisonPolicy.normalized(actualDraft)
        val expectedDebit = expectedLines.sumOf { it.debit }
        val expectedCredit = expectedLines.sumOf { it.credit }
        val actualDebit = actual.sumOf { it.debit }
        val actualCredit = actual.sumOf { it.credit }

        val summary = buildString {
            append("source=${expected.sourceType}:${expected.sourceId};")
            append("semanticMatch=${issues.isEmpty()};")
            append("expectedDebit=${fmt(expectedDebit)};expectedCredit=${fmt(expectedCredit)};")
            append("actualDebit=${fmt(actualDebit)};actualCredit=${fmt(actualCredit)};")
            append("sourceSummary=${expected.sourceSummary}")
            if (issues.isNotEmpty()) append(";issues=${issues.joinToString(" || ")}")
        }
        return Comparison(issues.isEmpty(), summary, expected)
    }

    private suspend fun expectedFor(entry: JournalEntryEntity): ExpectedJournal {
        val sourceId = entry.sourceId?.toLongOrNull()
            ?: throw IllegalStateException("مرجع العملية الأصلية غير صالح: ${entry.sourceType}:${entry.sourceId}")
        return when (entry.sourceType) {
            "SALE" -> expectedSale(sourceId)
            "PURCHASE" -> expectedPurchase(sourceId)
            "CUSTOMER_RECEIPT" -> expectedCustomerReceipt(sourceId)
            "SUPPLIER_PAYMENT" -> expectedSupplierPayment(sourceId)
            "SALES_RETURN" -> expectedSalesReturn(sourceId)
            "PURCHASE_RETURN" -> expectedPurchaseReturn(sourceId)
            "ADDITIONAL_CHARGE" -> expectedAdditionalCharge(sourceId)
            "ADDITIONAL_CHARGE_PAYMENT" -> expectedAdditionalChargePayment(sourceId)
            else -> throw IllegalStateException("لا يوجد Expected Journal typed للمصدر ${entry.sourceType}")
        }
    }

    private suspend fun expectedSale(id: Long): ExpectedJournal {
        val invoice = requireNotNull(db.salesDao().invoiceById(id)) { "فاتورة البيع الأصلية غير موجودة" }
        require(invoice.status == "POSTED") { "فاتورة البيع الأصلية ليست POSTED" }
        val debitAccountId = if (invoice.paymentType == "CASH") {
            treasuryGlAccount(invoice.treasuryAccountId, invoice.currencyCode, "فاتورة البيع ${invoice.invoiceNo}")
        } else accountId("1300", "حساب العملاء")
        val sales = accountId("4000", "حساب المبيعات")
        val cogs = accountId("5000", "حساب تكلفة المبيعات")
        val inventory = accountId("1200", "حساب المخزون")
        val costBase = db.salesDao().linesForInvoice(invoice.id).sumOf { line ->
            db.salesDao().allocationsForLine(line.id).sumOf { allocation -> allocation.costBase }
        }
        val freeBase = db.salesDao().linesForInvoice(invoice.id).sumOf { it.freeBaseQuantity }
        val settlements = db.additionalChargesDao().settlementsForInvoice(invoice.id).filter { it.status == "ACTIVE" }
        val chargeRows = settlements.map { settlement ->
            settlement to requireNotNull(db.additionalChargesDao().chargeById(settlement.chargeId)) { "الرسم الإضافي المرتبط بالفاتورة غير موجود" }
        }.filter { (_, charge) -> charge.status == "POSTED" }
        val externalDirectBase = chargeRows
            .filter { (_, charge) -> charge.bearer == "CUSTOMER" && charge.paidBy == "CUSTOMER_DIRECT" }
            .sumOf { (settlement, _) -> settlement.amountBase }
        val collectibleBase = (invoice.totalBase - externalDirectBase).coerceAtLeast(0.0)
        val itemAndLegacyRevenueBase = (
            invoice.grossOriginal - invoice.discountOriginal +
                invoice.transportOriginal + invoice.feesOriginal + invoice.riskMarginOriginal
            ) * invoice.exchangeRate
        val chargeCreditsByAccount = chargeRows
            .filter { (_, charge) -> charge.bearer == "CUSTOMER" && charge.paidBy != "CUSTOMER_DIRECT" }
            .groupBy { (_, charge) ->
                when (charge.accountingTreatment) {
                    "RECOVERABLE" -> charge.recoverableAccountId
                    "SERVICE_REVENUE" -> charge.revenueAccountId
                    else -> throw IllegalStateException("معالجة رسم العميل غير مدعومة في الفاتورة")
                }
            }
            .mapValues { (_, rows) -> rows.sumOf { (settlement, _) -> settlement.amountBase } }
        val lines = mutableListOf(
            DraftJournalLine(debitAccountId, collectibleBase, 0.0),
            DraftJournalLine(sales, 0.0, itemAndLegacyRevenueBase),
        )
        chargeCreditsByAccount.forEach { (accountId, amountBase) ->
            if (amountBase > EPS) lines += DraftJournalLine(accountId, 0.0, amountBase)
        }
        if (costBase > EPS) {
            lines += DraftJournalLine(cogs, costBase, 0.0)
            lines += DraftJournalLine(inventory, 0.0, costBase)
        }
        return ExpectedJournal(
            "SALE", id, "JE-${invoice.invoiceNo}", invoice.invoiceDate, invoice.currencyCode, invoice.exchangeRate, lines,
            "paymentType=${invoice.paymentType};documentTotalBase=${fmt(invoice.totalBase)};collectibleBase=${fmt(collectibleBase)};externalDirectBase=${fmt(externalDirectBase)};additionalCharges=${settlements.size};cogsBase=${fmt(costBase)};freeBaseQty=${fmt(freeBase)}"
        )
    }

    private suspend fun expectedPurchase(id: Long): ExpectedJournal {
        val invoice = requireNotNull(db.purchaseDao().invoiceById(id)) { "فاتورة الشراء الأصلية غير موجودة" }
        require(invoice.status == "POSTED") { "فاتورة الشراء الأصلية ليست POSTED" }
        val inventory = accountId("1200", "حساب المخزون")
        val credit = if (invoice.paymentType == "CASH") {
            treasuryGlAccount(invoice.treasuryAccountId, invoice.currencyCode, "فاتورة الشراء ${invoice.invoiceNo}")
        } else accountId("2100", "حساب الموردين")
        return ExpectedJournal(
            "PURCHASE", id, "JE-${invoice.invoiceNo}", invoice.invoiceDate, invoice.currencyCode, invoice.exchangeRate,
            listOf(
                DraftJournalLine(inventory, invoice.totalBase, 0.0),
                DraftJournalLine(credit, 0.0, invoice.totalBase),
            ),
            "paymentType=${invoice.paymentType};totalBase=${fmt(invoice.totalBase)}"
        )
    }

    private suspend fun expectedCustomerReceipt(id: Long): ExpectedJournal {
        val receipt = requireNotNull(db.salesDao().receiptById(id)) { "سند التحصيل الأصلي غير موجود" }
        require(receipt.reversalOfReceiptId == null) { "سند عكس التحصيل يُعالج بقيد REVERSAL وليس Support Rebuild" }
        val allocations = db.salesDao().receiptAllocations(id)
        require(allocations.isNotEmpty()) { "سند التحصيل بلا تخصيصات؛ لا يمكن إعادة اشتقاق القيد بأمان" }
        val cashReceivableBase = allocations.sumOf { it.amountBase }
        val discountBase = allocations.sumOf { it.discountBase }
        require(abs(receipt.amountBase - cashReceivableBase) <= EPS) { "بيانات التحصيل لا تطابق تخصيصاته النقدية" }
        require(abs(receipt.discountBase - discountBase) <= EPS) { "خصم التحصيل لا يطابق تخصيصاته" }
        val settled = cashReceivableBase + discountBase
        val treasuryCash = receipt.amountOriginal * receipt.exchangeRate
        val treasury = treasuryGlAccount(receipt.treasuryAccountId, receipt.currencyCode, "سند التحصيل ${receipt.receiptNo}")
        val receivables = accountId("1300", "حساب العملاء")
        val lines = mutableListOf(DraftJournalLine(treasury, treasuryCash, 0.0))
        if (discountBase > EPS) lines += DraftJournalLine(accountId("4110", "خصومات تسوية العملاء"), discountBase, 0.0)
        lines += DraftJournalLine(receivables, 0.0, settled)
        val diff = treasuryCash - cashReceivableBase
        if (diff > EPS) lines += DraftJournalLine(accountId("4250", "أرباح فروق العملة"), 0.0, diff)
        else if (diff < -EPS) lines += DraftJournalLine(accountId("6750", "خسائر فروق العملة"), -diff, 0.0)
        return ExpectedJournal(
            "CUSTOMER_RECEIPT", id, "JE-${receipt.receiptNo}", receipt.receiptDate, receipt.currencyCode, receipt.exchangeRate, lines,
            "cashOriginal=${fmt(receipt.amountOriginal)};cashReceivableBase=${fmt(cashReceivableBase)};discountBase=${fmt(discountBase)};treasuryCashBase=${fmt(treasuryCash)};fx=${fmt(diff)}"
        )
    }

    private suspend fun expectedSupplierPayment(id: Long): ExpectedJournal {
        val payment = requireNotNull(db.purchaseDao().supplierPaymentById(id)) { "دفعة المورد الأصلية غير موجودة" }
        require(payment.reversalOfPaymentId == null) { "عكس دفعة المورد يُعالج بقيد REVERSAL وليس Support Rebuild" }
        val allocations = db.purchaseDao().supplierPaymentAllocations(id)
        require(allocations.isNotEmpty()) { "دفعة المورد بلا تخصيصات؛ لا يمكن إعادة اشتقاق القيد بأمان" }
        val allocated = allocations.sumOf { it.allocatedBase }
        val cash = payment.cashAmountBase
        val payable = accountId("2100", "حساب الموردين")
        val treasuryEntity = requireNotNull(db.accountingDao().treasuryById(payment.treasuryAccountId)) { "خزينة دفعة المورد غير موجودة" }
        require(treasuryEntity.currencyCode == payment.currencyCode) { "عملة خزينة دفعة المورد لا تطابق المصدر" }
        val lines = mutableListOf(
            DraftJournalLine(payable, allocated, 0.0),
            DraftJournalLine(treasuryEntity.accountId, 0.0, cash),
        )
        val diff = cash - allocated
        if (diff > EPS) lines += DraftJournalLine(accountId("6750", "خسائر فروق العملة"), diff, 0.0)
        else if (diff < -EPS) lines += DraftJournalLine(accountId("4250", "أرباح فروق العملة"), 0.0, -diff)
        return ExpectedJournal(
            "SUPPLIER_PAYMENT", id, "JE-${payment.paymentNo}", payment.paymentDate, payment.currencyCode, payment.exchangeRate, lines,
            "allocatedBase=${fmt(allocated)};cashBase=${fmt(cash)};fx=${fmt(diff)}"
        )
    }

    private suspend fun expectedSalesReturn(id: Long): ExpectedJournal {
        val row = requireNotNull(db.salesDao().returnById(id)) { "مرتجع المبيعات الأصلي غير موجود" }
        require(row.status == "POSTED") { "مرتجع المبيعات الأصلي ليس POSTED" }
        val invoice = requireNotNull(db.salesDao().invoiceById(row.salesInvoiceId)) { "فاتورة مرتجع المبيعات غير موجودة" }
        val inventory = accountId("1200", "حساب المخزون")
        val cogs = accountId("5000", "حساب تكلفة المبيعات")
        val lines = mutableListOf<DraftJournalLine>()
        if (row.totalBase > EPS) {
            val salesReturns = accountId("4100", "مردودات المبيعات")
            val settlement = if (row.settlementType == "CASH_REFUND") {
                treasuryGlAccount(row.treasuryAccountId, invoice.currencyCode, "مرتجع المبيعات ${row.returnNo}")
            } else accountId("1300", "حساب العملاء")
            lines += DraftJournalLine(salesReturns, row.totalBase, 0.0)
            lines += DraftJournalLine(settlement, 0.0, row.totalBase)
        }
        if (row.totalCostBase > EPS) {
            lines += DraftJournalLine(inventory, row.totalCostBase, 0.0)
            lines += DraftJournalLine(cogs, 0.0, row.totalCostBase)
        }
        require(lines.isNotEmpty()) { "مرتجع المبيعات لا ينتج قيدًا ماليًا" }
        return ExpectedJournal(
            "SALES_RETURN", id, "JE-${row.returnNo}", row.returnDate, invoice.currencyCode, invoice.exchangeRate, lines,
            "settlement=${row.settlementType};salesBase=${fmt(row.totalBase)};costBase=${fmt(row.totalCostBase)}"
        )
    }

    private suspend fun expectedPurchaseReturn(id: Long): ExpectedJournal {
        val row = requireNotNull(db.purchaseDao().returnById(id)) { "مرتجع الشراء الأصلي غير موجود" }
        require(row.status == "POSTED") { "مرتجع الشراء الأصلي ليس POSTED" }
        val invoice = requireNotNull(db.purchaseDao().invoiceById(row.purchaseInvoiceId)) { "فاتورة مرتجع الشراء غير موجودة" }
        val inventory = accountId("1200", "حساب المخزون")
        val debit = if (row.settlementType == "CASH_REFUND") {
            treasuryGlAccount(row.treasuryAccountId, invoice.currencyCode, "مرتجع الشراء ${row.returnNo}")
        } else accountId("2100", "حساب الموردين")
        return ExpectedJournal(
            "PURCHASE_RETURN", id, "JE-${row.returnNo}", row.returnDate, invoice.currencyCode, invoice.exchangeRate,
            listOf(
                DraftJournalLine(debit, row.totalBase, 0.0),
                DraftJournalLine(inventory, 0.0, row.totalBase),
            ),
            "settlement=${row.settlementType};totalBase=${fmt(row.totalBase)}"
        )
    }

    private suspend fun expectedAdditionalCharge(id: Long): ExpectedJournal {
        val charge = requireNotNull(db.additionalChargesDao().chargeById(id)) { "الرسم الإضافي الأصلي غير موجود" }
        require(charge.status == "POSTED") { "الرسم الإضافي الأصلي ليس POSTED" }
        require(charge.paidBy != "CUSTOMER_DIRECT" && charge.accountingTreatment != "SERVICE_REVENUE") {
            "هذا الرسم لا ينشئ قيد إثبات مستقل"
        }
        val payments = db.additionalChargesDao().paymentsForCharge(charge.id)
        val initial = payments.firstOrNull { it.paymentDate == charge.chargeDate && it.paidBy == "COMPANY" }
        val initialPaid = initial?.amountOriginal?.coerceAtMost(charge.amountOriginal) ?: 0.0
        val paidBase = initialPaid * charge.exchangeRate
        val unpaidBase = (charge.amountBase - paidBase).coerceAtLeast(0.0)
        val debit = if (charge.accountingTreatment == "RECOVERABLE") charge.recoverableAccountId else charge.expenseAccountId
        val lines = mutableListOf(DraftJournalLine(debit, charge.amountBase, 0.0))
        if (paidBase > EPS) {
            val treasury = requireNotNull(initial?.treasuryAccountId?.let { db.accountingDao().treasuryById(it) }) { "خزينة دفع الرسم الأصلية غير موجودة" }
            lines += DraftJournalLine(treasury.accountId, 0.0, paidBase)
        }
        if (unpaidBase > EPS) lines += DraftJournalLine(charge.payableAccountId, 0.0, unpaidBase)
        return ExpectedJournal(
            "ADDITIONAL_CHARGE", id, "JE-${charge.chargeNo}", charge.chargeDate, charge.currencyCode, charge.exchangeRate, lines,
            "chargeNo=${charge.chargeNo};treatment=${charge.accountingTreatment};amountBase=${fmt(charge.amountBase)};initialPaidBase=${fmt(paidBase)}"
        )
    }

    private suspend fun expectedAdditionalChargePayment(id: Long): ExpectedJournal {
        val payment = requireNotNull(db.additionalChargesDao().paymentById(id)) { "دفعة الرسم الإضافي الأصلية غير موجودة" }
        require(payment.paidBy == "COMPANY") { "الدفع المباشر بواسطة العميل لا ينشئ قيد خزينة للشركة" }
        val charge = requireNotNull(db.additionalChargesDao().chargeById(payment.chargeId)) { "الرسم المرتبط بالدفعة غير موجود" }
        val treasury = requireNotNull(payment.treasuryAccountId?.let { db.accountingDao().treasuryById(it) }) { "خزينة دفعة الرسم غير موجودة" }
        val carryingBase = payment.amountOriginal * charge.exchangeRate
        val lines = mutableListOf(
            DraftJournalLine(charge.payableAccountId, carryingBase, 0.0),
            DraftJournalLine(treasury.accountId, 0.0, payment.amountBase),
        )
        val diff = payment.amountBase - carryingBase
        if (diff > EPS) lines += DraftJournalLine(accountId("6750", "خسائر فروق العملة"), diff, 0.0)
        else if (diff < -EPS) lines += DraftJournalLine(accountId("4250", "أرباح فروق العملة"), 0.0, -diff)
        return ExpectedJournal(
            "ADDITIONAL_CHARGE_PAYMENT", id, "JE-${payment.paymentNo}", payment.paymentDate, payment.currencyCode, payment.exchangeRate, lines,
            "charge=${charge.chargeNo};carryingBase=${fmt(carryingBase)};cashBase=${fmt(payment.amountBase)};fx=${fmt(diff)}"
        )
    }

    private suspend fun accountId(code: String, label: String): Long =
        requireNotNull(db.accountDao().byCode(code)) { "$label $code غير موجود" }.id

    private suspend fun treasuryGlAccount(treasuryId: Long?, currencyCode: String, sourceLabel: String): Long {
        val id = requireNotNull(treasuryId) {
            "$sourceLabel لا يحتوي Treasury provenance محفوظة. هذا مستند تاريخي/قديم ولا يمكن Safe Repair له بالتخمين. استخدم Repair Command متخصص بعد مراجعة المصدر."
        }
        val treasury = requireNotNull(db.accountingDao().treasuryById(id)) { "خزينة المصدر #$id غير موجودة" }
        require(treasury.currencyCode == currencyCode) { "عملة خزينة المصدر لا تطابق عملة المستند" }
        return treasury.accountId
    }

    private fun nearZero(value: Double): Boolean = abs(value) <= EPS
    private fun fmt(value: Double): String = "%.6f".format(java.util.Locale.US, value)
    companion object {
        private const val EPS = 0.000001
    }
}
