package com.fush.erp.domain

import kotlin.math.min

/** Pure planning model used before any invoice allocation/movement rows are written. */
data class HistoricalLotCapacity(
    val key: String,
    val lotNo: String?,
    val quantityBase: Double,
    val historicalSafeQtyBase: Double,
    val unitCostBase: Double,
)

data class HistoricalLotTake(
    val key: String,
    val lotNo: String?,
    val quantityBase: Double,
    val unitCostBase: Double,
) {
    val costBase: Double get() = quantityBase * unitCostBase
}

data class HistoricalAllocationPlan(
    val requestedQtyBase: Double,
    val totalSafeQtyBase: Double,
    val takes: List<HistoricalLotTake>,
) {
    val allocatedQtyBase: Double get() = takes.sumOf { it.quantityBase }
    val shortageQtyBase: Double get() = (requestedQtyBase - allocatedQtyBase).coerceAtLeast(0.0)
    val isComplete: Boolean get() = shortageQtyBase <= WarehouseTransferMath.EPS
    val totalCostBase: Double get() = takes.sumOf { it.costBase }
}

object SalesHistoricalAllocationMath {
    fun plan(requiredQtyBase: Double, lotsInFefoOrder: List<HistoricalLotCapacity>): HistoricalAllocationPlan {
        require(requiredQtyBase.isFinite() && requiredQtyBase > WarehouseTransferMath.EPS) {
            "كمية البيع المطلوبة يجب أن تكون أكبر من صفر"
        }

        var remaining = requiredQtyBase
        var totalSafe = 0.0
        val takes = mutableListOf<HistoricalLotTake>()

        lotsInFefoOrder.forEach { lot ->
            require(lot.quantityBase.isFinite() && lot.quantityBase >= -WarehouseTransferMath.EPS) { "رصيد التشغيلة غير صالح" }
            require(lot.historicalSafeQtyBase.isFinite()) { "الحد الآمن التاريخي للتشغيلة غير صالح" }
            require(lot.unitCostBase.isFinite() && lot.unitCostBase >= -WarehouseTransferMath.EPS) { "تكلفة التشغيلة غير صالحة" }

            val safeCapacity = min(lot.quantityBase.coerceAtLeast(0.0), lot.historicalSafeQtyBase.coerceAtLeast(0.0))
            totalSafe += safeCapacity
            if (remaining <= WarehouseTransferMath.EPS || safeCapacity <= WarehouseTransferMath.EPS) return@forEach

            val take = min(remaining, safeCapacity)
            takes += HistoricalLotTake(lot.key, lot.lotNo, take, lot.unitCostBase.coerceAtLeast(0.0))
            remaining -= take
        }

        return HistoricalAllocationPlan(requiredQtyBase, totalSafe, takes)
    }
}
