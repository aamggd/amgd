from pathlib import Path

ROOT = Path.cwd()
SALES = ROOT / "app/src/main/java/com/fush/erp/domain/SalesService.kt"
POLICY = ROOT / "app/src/main/java/com/fush/erp/domain/CustomerReceiptAtomicityPolicy.kt"
POLICY_TEST = ROOT / "app/src/test/java/com/fush/erp/domain/CustomerReceiptAtomicityPolicyTest.kt"
REVERSAL_TEST = ROOT / "app/src/test/java/com/fush/erp/domain/OperationalReversalMathTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


text = SALES.read_text(encoding="utf-8")

old_post_receipt = '''    suspend fun postReceipt(
        customerId: Long,
        invoiceId: Long,
        amountOriginal: Double,
        currencyCode: String,
        exchangeRate: Double,
        notes: String,
        createdBy: Long,
        receiptDate: Long = System.currentTimeMillis(),
        treasuryAccountId: Long? = null
    ): ReceiptResult {
        val result = postReceiptAllocations(
            customerId = customerId,
            allocations = listOf(ReceiptAllocationRequest(invoiceId, amountOriginal)),
            currencyCode = currencyCode,
            exchangeRate = exchangeRate,
            notes = notes,
            createdBy = createdBy,
            receiptDate = receiptDate,
            treasuryAccountId = treasuryAccountId
        )
        return ReceiptResult(result.receiptId, result.receiptNo, result.commissionBase)
    }
'''
new_post_receipt = '''    suspend fun postReceipt(
        customerId: Long,
        invoiceId: Long,
        amountOriginal: Double,
        currencyCode: String,
        exchangeRate: Double,
        notes: String,
        createdBy: Long,
        receiptDate: Long = System.currentTimeMillis(),
        treasuryAccountId: Long? = null
    ): ReceiptResult = db.withTransaction {
        val result = postReceiptAllocationsInternal(
            customerId = customerId,
            allocations = listOf(ReceiptAllocationRequest(invoiceId, amountOriginal)),
            currencyCode = currencyCode,
            exchangeRate = exchangeRate,
            notes = notes,
            createdBy = createdBy,
            receiptDate = receiptDate,
            treasuryAccountId = treasuryAccountId
        )
        ReceiptResult(result.receiptId, result.receiptNo, result.commissionBase)
    }
'''
text = replace_once(text, old_post_receipt, new_post_receipt, "postReceipt transaction boundary")

old_totals = '''        val totalOriginal = prepared.sumOf { it.request.amountOriginal }
        val totalAllocatedBase = prepared.sumOf { it.split.allocatedBase }
        val totalCashBase = prepared.sumOf { it.split.cashBase }
        val receiptNo = numbering.nextDocumentNo("RCPT", receiptDate)
'''
new_totals = '''        val totalOriginal = prepared.sumOf { it.request.amountOriginal }
        val totalAllocatedBase = prepared.sumOf { it.split.allocatedBase }
        val totalCashBase = prepared.sumOf { it.split.cashBase }

        // AE-ACC-009: fail closed if this mutation block is ever called outside
        // the Room transaction that must also contain journal posting.
        CustomerReceiptAtomicityPolicy.requireActiveTransaction(db.inTransaction())

        // Resolve every GL dependency and validate the collection entry before
        // the first receipt/allocation/sequence mutation. Any later journal DB
        // failure is deliberately allowed to propagate so Room rolls back the
        // whole receipt transaction.
        val collectionJournalLines = prepareCollectionJournalLines(
            treasuryAccountId = treasury.accountId,
            allocatedBase = totalAllocatedBase,
            cashBase = totalCashBase
        )
        val receiptNo = numbering.nextDocumentNo("RCPT", receiptDate)
'''
text = replace_once(text, old_totals, new_totals, "AE-ACC-009 preflight before mutation")

old_post_call = '''        postCollectionJournal(
            receiptId = receiptId,
            receiptNo = receiptNo,
            treasuryAccountId = treasury.accountId,
            allocatedBase = totalAllocatedBase,
            cashBase = totalCashBase,
            currencyCode = currencyCode,
            exchangeRate = exchangeRate,
            receiptDate = receiptDate,
            createdBy = createdBy
        )
'''
new_post_call = '''        postCollectionJournal(
            receiptId = receiptId,
            receiptNo = receiptNo,
            lines = collectionJournalLines,
            currencyCode = currencyCode,
            exchangeRate = exchangeRate,
            receiptDate = receiptDate,
            createdBy = createdBy
        )
'''
text = replace_once(text, old_post_call, new_post_call, "collection journal uses prevalidated lines")

old_collection = '''    private suspend fun postCollectionJournal(
        receiptId: Long,
        receiptNo: String,
        treasuryAccountId: Long,
        allocatedBase: Double,
        cashBase: Double,
        currencyCode: String,
        exchangeRate: Double,
        receiptDate: Long,
        createdBy: Long
    ) {
        val treasuryAccount = requireNotNull(db.accountDao().byId(treasuryAccountId)) { "حساب الخزينة غير موجود" }
        val receivables = requireNotNull(db.accountDao().byCode("1300")) { "حساب العملاء 1300 غير موجود" }
        val lines = mutableListOf(
            DraftJournalLine(treasuryAccount.id, cashBase, 0.0),
            DraftJournalLine(receivables.id, 0.0, allocatedBase)
        )
        val fxDifference = cashBase - allocatedBase
        if (fxDifference > 1e-9) {
            val fxGain = requireNotNull(db.accountDao().byCode("4250")) { "حساب أرباح فروق العملة 4250 غير موجود" }
            lines += DraftJournalLine(fxGain.id, 0.0, fxDifference)
        } else if (fxDifference < -1e-9) {
            val fxLoss = requireNotNull(db.accountDao().byCode("6750")) { "حساب خسائر فروق العملة 6750 غير موجود" }
            lines += DraftJournalLine(fxLoss.id, -fxDifference, 0.0)
        }
        postJournal(
            entryNo = "JE-$receiptNo",
            date = receiptDate,
            description = "تحصيل عميل $receiptNo",
            currencyCode = currencyCode,
            exchangeRate = exchangeRate,
            sourceType = "CUSTOMER_RECEIPT",
            sourceId = receiptId.toString(),
            createdBy = createdBy,
            lines = lines
        )
    }
'''
new_collection = '''    private suspend fun prepareCollectionJournalLines(
        treasuryAccountId: Long,
        allocatedBase: Double,
        cashBase: Double
    ): List<DraftJournalLine> {
        val treasuryAccount = requireNotNull(db.accountDao().byId(treasuryAccountId)) { "حساب الخزينة غير موجود" }
        val receivables = requireNotNull(db.accountDao().byCode("1300")) { "حساب العملاء 1300 غير موجود" }
        val lines = mutableListOf(
            DraftJournalLine(treasuryAccount.id, cashBase, 0.0),
            DraftJournalLine(receivables.id, 0.0, allocatedBase)
        )
        val fxDifference = cashBase - allocatedBase
        if (fxDifference > 1e-9) {
            val fxGain = requireNotNull(db.accountDao().byCode("4250")) { "حساب أرباح فروق العملة 4250 غير موجود" }
            lines += DraftJournalLine(fxGain.id, 0.0, fxDifference)
        } else if (fxDifference < -1e-9) {
            val fxLoss = requireNotNull(db.accountDao().byCode("6750")) { "حساب خسائر فروق العملة 6750 غير موجود" }
            lines += DraftJournalLine(fxLoss.id, -fxDifference, 0.0)
        }
        AccountingValidator.validate(lines)
        return lines
    }

    private suspend fun postCollectionJournal(
        receiptId: Long,
        receiptNo: String,
        lines: List<DraftJournalLine>,
        currencyCode: String,
        exchangeRate: Double,
        receiptDate: Long,
        createdBy: Long
    ) {
        // Do not catch journal failures here. The exception must escape the
        // caller's Room transaction so receipt/allocation/commission/sequence
        // writes are rolled back together with any partial journal write.
        postJournal(
            entryNo = "JE-$receiptNo",
            date = receiptDate,
            description = "تحصيل عميل $receiptNo",
            currencyCode = currencyCode,
            exchangeRate = exchangeRate,
            sourceType = "CUSTOMER_RECEIPT",
            sourceId = receiptId.toString(),
            createdBy = createdBy,
            lines = lines
        )
    }
'''
text = replace_once(text, old_collection, new_collection, "collection journal preflight split")

SALES.write_text(text, encoding="utf-8")

POLICY.write_text('''package com.fush.erp.domain

/**
 * AE-ACC-009 fail-closed invariant for customer receipt posting.
 *
 * Receipt header, allocations, numbering, commission effects and the cash/AR
 * journal are one accounting operation. The mutation block must run inside the
 * same Room transaction as journal posting so any journal failure rolls all of
 * those writes back.
 */
object CustomerReceiptAtomicityPolicy {
    fun requireActiveTransaction(active: Boolean) {
        check(active) { "Customer receipt posting must run inside one database transaction" }
    }
}
''', encoding="utf-8")

POLICY_TEST.write_text('''package com.fush.erp.domain

import org.junit.Assert.assertThrows
import org.junit.Test

class CustomerReceiptAtomicityPolicyTest {
    @Test
    fun aeAcc009AllowsReceiptMutationInsideActiveTransaction() {
        CustomerReceiptAtomicityPolicy.requireActiveTransaction(true)
    }

    @Test
    fun aeAcc009RejectsReceiptMutationWithoutActiveTransaction() {
        assertThrows(IllegalStateException::class.java) {
            CustomerReceiptAtomicityPolicy.requireActiveTransaction(false)
        }
    }
}
''', encoding="utf-8")

REVERSAL_TEST.write_text('''package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OperationalReversalMathTest {
    @Test
    fun customerReceiptReversalSwapsDebitAndCreditWithoutChangingAccounts() {
        val original = listOf(
            DraftJournalLine(accountId = 10L, debit = 250.0, credit = 0.0),
            DraftJournalLine(accountId = 20L, debit = 0.0, credit = 250.0)
        )

        val reversed = OperationalReversalMath.reverseJournalLines(original)

        assertEquals(10L, reversed[0].accountId)
        assertEquals(0.0, reversed[0].debit, 0.0)
        assertEquals(250.0, reversed[0].credit, 0.0)
        assertEquals(20L, reversed[1].accountId)
        assertEquals(250.0, reversed[1].debit, 0.0)
        assertEquals(0.0, reversed[1].credit, 0.0)
    }
}
''', encoding="utf-8")

print("AE-ACC-009 customer receipt atomicity fix applied")
