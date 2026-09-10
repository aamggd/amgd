package com.fush.erp.domain

import kotlin.math.max
import kotlin.math.min

object FixedAssetMath {
    const val TOLERANCE = 0.01

    fun depreciableBase(costBase: Double, residualBase: Double): Double {
        require(costBase.isFinite() && costBase > 0.0) { "تكلفة الأصل يجب أن تكون أكبر من صفر" }
        require(residualBase.isFinite() && residualBase >= 0.0) { "القيمة المتبقية غير صالحة" }
        require(residualBase < costBase) { "القيمة المتبقية يجب أن تكون أقل من تكلفة الأصل" }
        return costBase - residualBase
    }

    fun monthlyStraightLine(costBase: Double, residualBase: Double, usefulLifeMonths: Int): Double {
        require(usefulLifeMonths > 0) { "العمر الإنتاجي بالأشهر يجب أن يكون أكبر من صفر" }
        return depreciableBase(costBase, residualBase) / usefulLifeMonths.toDouble()
    }

    fun depreciationForPeriod(
        costBase: Double,
        residualBase: Double,
        usefulLifeMonths: Int,
        accumulatedBeforeBase: Double
    ): Double {
        val depreciable = depreciableBase(costBase, residualBase)
        val remaining = max(0.0, depreciable - max(0.0, accumulatedBeforeBase))
        if (remaining <= TOLERANCE) return 0.0
        return min(monthlyStraightLine(costBase, residualBase, usefulLifeMonths), remaining)
    }

    fun netBookValue(costBase: Double, accumulatedDepreciationBase: Double): Double =
        max(0.0, costBase - max(0.0, accumulatedDepreciationBase))

    fun disposalGainLoss(proceedsBase: Double, netBookValueBase: Double): Double {
        require(proceedsBase.isFinite() && proceedsBase >= 0.0) { "متحصلات الاستبعاد غير صالحة" }
        return proceedsBase - max(0.0, netBookValueBase)
    }
}
