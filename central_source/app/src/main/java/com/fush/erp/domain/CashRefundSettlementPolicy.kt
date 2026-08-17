package com.fush.erp.domain

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
