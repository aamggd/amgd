from pathlib import Path

ROOT = Path.cwd()
SALES = ROOT / "app/src/main/java/com/fush/erp/domain/SalesService.kt"
SALES_DAO = ROOT / "app/src/main/java/com/fush/erp/data/dao/SalesDaos.kt"
POLICY = ROOT / "app/src/main/java/com/fush/erp/domain/CashRefundSettlementPolicy.kt"
POLICY_TEST = ROOT / "app/src/test/java/com/fush/erp/domain/CashRefundSettlementPolicyTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# Schema-free DAO aggregate: posted cash refunds already consumed from collected cash.
dao = SALES_DAO.read_text(encoding="utf-8")
old_dao = '''    @Query("SELECT COALESCE(SUM(totalBase), 0) FROM sales_returns WHERE salesInvoiceId = :invoiceId AND status = 'POSTED' AND settlementType = 'CUSTOMER_CREDIT'")
    suspend fun customerCreditReturnedBaseForInvoice(invoiceId: Long): Double
'''
new_dao = old_dao + '''
    @Query("SELECT COALESCE(SUM(totalBase), 0) FROM sales_returns WHERE salesInvoiceId = :invoiceId AND status = 'POSTED' AND settlementType = 'CASH_REFUND'")
    suspend fun cashRefundedBaseForInvoice(invoiceId: Long): Double
'''
dao = replace_once(dao, old_dao, new_dao, "cash-refund aggregate query")
SALES_DAO.write_text(dao, encoding="utf-8")


sales = SALES.read_text(encoding="utf-8")

# Validate CASH_REFUND against net collected cash before treasury resolution or any return mutation.
old_post_return = '''        val customerId = CustomerMovementIdentity.requireId(invoice.customerId)
        val customer = requireNotNull(db.customerDao().byId(customerId)) { "العميل غير موجود" }
        val refundTreasury = if (settlementType == "CASH_REFUND") resolveTreasury(treasuryAccountId, invoice.currencyCode) else null
        val alreadyReturned = db.salesDao().returnedQuantityForLine(line.id)
        SalesMath.validateReturn(quantity, line.quantity, alreadyReturned)
        val returnBaseQty = quantity * line.factorToBase
        val unitNetOriginal = if (line.quantity > 0.0) line.netOriginal / line.quantity else 0.0
        val totalOriginal = quantity * unitNetOriginal
        val totalBase = totalOriginal * invoice.exchangeRate

        val allocationPlan = mutableListOf<Pair<SalesAllocationEntity, Double>>()
'''
new_post_return = '''        val customerId = CustomerMovementIdentity.requireId(invoice.customerId)
        val customer = requireNotNull(db.customerDao().byId(customerId)) { "العميل غير موجود" }
        val alreadyReturned = db.salesDao().returnedQuantityForLine(line.id)
        SalesMath.validateReturn(quantity, line.quantity, alreadyReturned)
        val returnBaseQty = quantity * line.factorToBase
        val unitNetOriginal = if (line.quantity > 0.0) line.netOriginal / line.quantity else 0.0
        val totalOriginal = quantity * unitNetOriginal
        val totalBase = totalOriginal * invoice.exchangeRate

        if (settlementType == "CASH_REFUND") {
            CashRefundSettlementPolicy.requireCashRefund(
                requestedRefundBase = totalBase,
                netCollectedBase = db.salesDao().receivedBaseForInvoice(invoice.id),
                cashRefundedBase = db.salesDao().cashRefundedBaseForInvoice(invoice.id)
            )
        }
        val refundTreasury = if (settlementType == "CASH_REFUND") resolveTreasury(treasuryAccountId, invoice.currencyCode) else null

        val allocationPlan = mutableListOf<Pair<SalesAllocationEntity, Double>>()
'''
sales = replace_once(sales, old_post_return, new_post_return, "postReturn cash refund eligibility")

# A receipt reversal must not make already-refunded cash exceed the remaining net collection.
old_reverse_guard = '''        require(db.journalDao().reversalCount(originalJournal.id) == 0) { "تم عكس قيد هذا التحصيل مسبقاً" }

        val reversalNo = numbering.nextDocumentNo("RCRV", reversalDate)
'''
new_reverse_guard = '''        require(db.journalDao().reversalCount(originalJournal.id) == 0) { "تم عكس قيد هذا التحصيل مسبقاً" }

        allocations.groupBy { it.invoiceId }.forEach { (invoiceId, invoiceAllocations) ->
            CashRefundSettlementPolicy.requireReceiptReversal(
                netCollectedBeforeBase = db.salesDao().receivedBaseForInvoice(invoiceId),
                reversingAllocationBase = invoiceAllocations.sumOf { it.amountBase },
                cashRefundedBase = db.salesDao().cashRefundedBaseForInvoice(invoiceId)
            )
        }

        val reversalNo = numbering.nextDocumentNo("RCRV", reversalDate)
'''
sales = replace_once(sales, old_reverse_guard, new_reverse_guard, "receipt reversal cash-refund consistency")
SALES.write_text(sales, encoding="utf-8")


if POLICY.exists() or POLICY_TEST.exists():
    raise SystemExit("cash refund policy files already exist; refusing non-deterministic overwrite")

POLICY.write_text('''package com.fush.erp.domain

/**
 * Final Audit AE-ACC-009 settlement invariant.
 *
 * CASH_REFUND is a repayment of money that was actually collected from the
 * customer. It must never be used to settle an uncollected receivable and it
 * must never exceed net collected cash that has not already been refunded.
 */
object CashRefundSettlementPolicy {
    private const val EPS = 1e-9

    fun refundableCollectedBase(netCollectedBase: Double, cashRefundedBase: Double): Double {
        require(netCollectedBase.isFinite() && cashRefundedBase.isFinite()) { "قيم التحصيل والاسترداد غير صالحة" }
        require(netCollectedBase >= -EPS && cashRefundedBase >= -EPS) { "قيم التحصيل والاسترداد لا يمكن أن تكون سالبة" }
        require(cashRefundedBase <= netCollectedBase + EPS) { "الاسترداد النقدي السابق يتجاوز صافي التحصيل" }
        return (netCollectedBase.coerceAtLeast(0.0) - cashRefundedBase.coerceAtLeast(0.0)).coerceAtLeast(0.0)
    }

    fun requireCashRefund(
        requestedRefundBase: Double,
        netCollectedBase: Double,
        cashRefundedBase: Double
    ): Double {
        require(requestedRefundBase.isFinite() && requestedRefundBase > EPS) { "مبلغ الاسترداد النقدي غير صالح" }
        val refundable = refundableCollectedBase(netCollectedBase, cashRefundedBase)
        require(requestedRefundBase <= refundable + EPS) {
            "لا يمكن رد مبلغ نقدي يتجاوز المبلغ المحصل القابل للاسترداد فعلياً"
        }
        return refundable
    }

    fun requireReceiptReversal(
        netCollectedBeforeBase: Double,
        reversingAllocationBase: Double,
        cashRefundedBase: Double
    ): Double {
        require(
            netCollectedBeforeBase.isFinite() &&
                reversingAllocationBase.isFinite() &&
                cashRefundedBase.isFinite()
        ) { "قيم عكس التحصيل غير صالحة" }
        require(netCollectedBeforeBase >= -EPS && reversingAllocationBase > EPS && cashRefundedBase >= -EPS) {
            "قيم عكس التحصيل غير صالحة"
        }
        require(reversingAllocationBase <= netCollectedBeforeBase + EPS) {
            "مبلغ عكس التحصيل يتجاوز صافي التحصيل الحالي"
        }
        val netCollectedAfter = (netCollectedBeforeBase - reversingAllocationBase).coerceAtLeast(0.0)
        require(cashRefundedBase <= netCollectedAfter + EPS) {
            "لا يمكن عكس التحصيل لأن جزءاً منه تم رده نقداً للعميل"
        }
        return netCollectedAfter
    }
}
''', encoding="utf-8")

POLICY_TEST.write_text('''package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CashRefundSettlementPolicyTest {
    @Test
    fun uncollectedCreditSaleRejectsCashRefund() {
        assertThrows(IllegalArgumentException::class.java) {
            CashRefundSettlementPolicy.requireCashRefund(
                requestedRefundBase = 100.0,
                netCollectedBase = 0.0,
                cashRefundedBase = 0.0
            )
        }
    }

    @Test
    fun partiallyCollectedSaleLimitsRefundToActuallyCollectedAmount() {
        assertEquals(
            40.0,
            CashRefundSettlementPolicy.requireCashRefund(40.0, 40.0, 0.0),
            0.0
        )
        assertThrows(IllegalArgumentException::class.java) {
            CashRefundSettlementPolicy.requireCashRefund(40.01, 40.0, 0.0)
        }
    }

    @Test
    fun fullyCollectedSaleAllowsFullRefund() {
        assertEquals(
            100.0,
            CashRefundSettlementPolicy.requireCashRefund(100.0, 100.0, 0.0),
            0.0
        )
    }

    @Test
    fun priorRefundIsConsumedAndDuplicateRefundIsPrevented() {
        assertEquals(
            40.0,
            CashRefundSettlementPolicy.refundableCollectedBase(100.0, 60.0),
            0.0
        )
        CashRefundSettlementPolicy.requireCashRefund(40.0, 100.0, 60.0)
        assertThrows(IllegalArgumentException::class.java) {
            CashRefundSettlementPolicy.requireCashRefund(40.01, 100.0, 60.0)
        }
    }

    @Test
    fun receiptReversalCannotUndercutCashAlreadyRefunded() {
        assertThrows(IllegalArgumentException::class.java) {
            CashRefundSettlementPolicy.requireReceiptReversal(
                netCollectedBeforeBase = 100.0,
                reversingAllocationBase = 70.0,
                cashRefundedBase = 40.0
            )
        }
        assertEquals(
            50.0,
            CashRefundSettlementPolicy.requireReceiptReversal(
                netCollectedBeforeBase = 100.0,
                reversingAllocationBase = 50.0,
                cashRefundedBase = 40.0
            ),
            0.0
        )
    }

    @Test
    fun partialCashRefundKeepsArCashAndNetSaleReconciled() {
        val invoiceBase = 100.0
        val collectedBase = 40.0
        val cashRefundBase = 30.0
        val customerCreditReturnBase = 0.0
        val totalReturnBase = cashRefundBase + customerCreditReturnBase
        CashRefundSettlementPolicy.requireCashRefund(cashRefundBase, collectedBase, 0.0)

        val netSaleBase = invoiceBase - totalReturnBase
        val netCashBase = collectedBase - cashRefundBase
        val arBase = invoiceBase - collectedBase - customerCreditReturnBase
        assertEquals(netSaleBase, netCashBase + arBase, 0.0)
    }

    @Test
    fun fullSettlementAcrossCashRefundAndArCreditReconcilesToZero() {
        val invoiceBase = 100.0
        val collectedBase = 40.0
        val cashRefundBase = 40.0
        val customerCreditReturnBase = 60.0
        CashRefundSettlementPolicy.requireCashRefund(cashRefundBase, collectedBase, 0.0)

        val netSaleBase = invoiceBase - cashRefundBase - customerCreditReturnBase
        val netCashBase = collectedBase - cashRefundBase
        val arBase = invoiceBase - collectedBase - customerCreditReturnBase
        assertEquals(0.0, netSaleBase, 0.0)
        assertEquals(0.0, netCashBase, 0.0)
        assertEquals(0.0, arBase, 0.0)
        assertEquals(netSaleBase, netCashBase + arBase, 0.0)
    }

    @Test
    fun cashRefundReturnJournalEquationIsBalanced() {
        val salesReturnBase = 75.0
        val refundTreasuryCreditBase = 75.0
        val inventoryDebitBase = 25.0
        val cogsCreditBase = 25.0
        val debit = salesReturnBase + inventoryDebitBase
        val credit = refundTreasuryCreditBase + cogsCreditBase
        assertEquals(debit, credit, 0.0)
    }
}
''', encoding="utf-8")

print("Final Audit AE-ACC-009 CASH_REFUND settlement fix applied")
