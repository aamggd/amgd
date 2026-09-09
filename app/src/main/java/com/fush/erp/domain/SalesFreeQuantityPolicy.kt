package com.fush.erp.domain

import kotlin.math.max

object SalesFreeQuantityPolicy {
    const val EPS = 1e-9

    fun allowedFreeBase(soldBase: Double, limitPct: Double): Double {
        require(soldBase >= 0.0 && soldBase.isFinite()) { "الكمية المباعة غير صالحة" }
        require(limitPct in 0.0..100.0 && limitPct.isFinite()) { "حد المجاني يجب أن يكون بين 0 و100%" }
        return soldBase * limitPct / 100.0
    }

    fun requiresApproval(totalSoldBase: Double, totalFreeBase: Double, limitPct: Double): Boolean {
        require(totalFreeBase >= 0.0 && totalFreeBase.isFinite()) { "الكمية المجانية غير صالحة" }
        return totalFreeBase > allowedFreeBase(totalSoldBase, limitPct) + EPS
    }

    fun excessFreeBase(totalSoldBase: Double, totalFreeBase: Double, limitPct: Double): Double =
        max(0.0, totalFreeBase - allowedFreeBase(totalSoldBase, limitPct))
}
