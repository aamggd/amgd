package com.fush.erp.domain

object CashRefundCoveragePolicy {
    const val EPS = 1e-8

    fun availableBase(actualCashBase: Double, alreadyRefundedBase: Double): Double =
        actualCashBase - alreadyRefundedBase

    fun requireCovered(
        actualCashBase: Double,
        alreadyRefundedBase: Double,
        requestedRefundBase: Double = 0.0,
        context: String
    ) {
        require(actualCashBase.isFinite() && alreadyRefundedBase.isFinite() && requestedRefundBase.isFinite()) {
            "قيم حماية الرد النقدي غير صالحة"
        }
        require(actualCashBase + EPS >= alreadyRefundedBase + requestedRefundBase) {
            val available = availableBase(actualCashBase, alreadyRefundedBase).coerceAtLeast(0.0)
            "$context: الحد النقدي المتاح ${"%.2f".format(available)} ولا يسمح بمبلغ إضافي ${"%.2f".format(requestedRefundBase)}"
        }
    }
}
