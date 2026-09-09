package com.fush.erp.domain

import kotlin.math.abs
import java.time.ZoneId

data class WarehouseTransferBalancePoint(
    val movementDate: Long,
    val quantityBase: Double
)

object WarehouseTransferMath {
    const val EPS = 0.000000001

    fun unitCost(quantityBase: Double, inventoryValueBase: Double): Double {
        require(quantityBase.isFinite() && quantityBase > EPS) { "الرصيد المتاح غير صالح" }
        require(inventoryValueBase.isFinite()) { "قيمة المخزون غير صالحة" }
        val cost = inventoryValueBase / quantityBase
        require(cost.isFinite() && cost >= -EPS) { "تكلفة المخزون غير صالحة" }
        return if (abs(cost) <= EPS) 0.0 else cost.coerceAtLeast(0.0)
    }

    fun validateQuantity(requestedQtyBase: Double, availableQtyBase: Double) {
        require(requestedQtyBase.isFinite() && requestedQtyBase > EPS) { "الكمية المطلوبة يجب أن تكون أكبر من صفر" }
        require(availableQtyBase.isFinite() && availableQtyBase >= -EPS) { "الرصيد المتاح غير صالح" }
        require(requestedQtyBase <= availableQtyBase + EPS) { "كمية التحويل أكبر من الرصيد المتاح" }
    }

    fun minimumAvailableFrom(points: List<WarehouseTransferBalancePoint>, transferDate: Long): Double {
        require(transferDate > 0L) { "تاريخ التحويل غير صالح" }
        var running = 0.0
        var minimum: Double? = null
        var transferCheckpointCaptured = false

        points.forEachIndexed { index, point ->
            require(point.movementDate > 0L && point.quantityBase.isFinite()) { "حركة مخزون تاريخية غير صالحة" }
            if (index > 0) {
                require(points[index - 1].movementDate <= point.movementDate) { "حركات المخزون التاريخية غير مرتبة" }
            }

            if (point.movementDate > transferDate && !transferCheckpointCaptured) {
                minimum = running
                transferCheckpointCaptured = true
            }

            running += point.quantityBase

            when {
                point.movementDate < transferDate -> Unit
                point.movementDate == transferDate -> {
                    val nextDate = points.getOrNull(index + 1)?.movementDate
                    if (nextDate != transferDate) {
                        minimum = minOf(minimum ?: running, running)
                        transferCheckpointCaptured = true
                    }
                }
                else -> minimum = minOf(minimum ?: running, running)
            }
        }

        if (!transferCheckpointCaptured) minimum = running
        val result = minimum ?: running
        return if (kotlin.math.abs(result) <= EPS) 0.0 else result
    }


    /**
     * Historical capacity for date-only ERP documents. Intra-day movement order is
     * intentionally ignored; each business day contributes its closing balance.
     */
    fun minimumClosingBalanceFromBusinessDay(
        points: List<WarehouseTransferBalancePoint>,
        effectiveDate: Long,
        zoneId: ZoneId = BusinessTimeZone.zoneId
    ): Double {
        require(effectiveDate > 0L) { "تاريخ الحركة غير صالح" }
        val effectiveDay = BusinessDatePolicy.businessDay(effectiveDate, zoneId)
        val dailyDeltas = linkedMapOf<java.time.LocalDate, Double>()

        points.forEachIndexed { index, point ->
            require(point.movementDate > 0L && point.quantityBase.isFinite()) { "حركة مخزون تاريخية غير صالحة" }
            if (index > 0) {
                require(points[index - 1].movementDate <= point.movementDate) { "حركات المخزون التاريخية غير مرتبة" }
            }
            val day = BusinessDatePolicy.businessDay(point.movementDate, zoneId)
            dailyDeltas[day] = (dailyDeltas[day] ?: 0.0) + point.quantityBase
        }

        var running = 0.0
        var minimum: Double? = null
        var effectiveCheckpointCaptured = false

        dailyDeltas.forEach { (day, delta) ->
            // If the first movement after the effective day is on a later day, the
            // effective day's closing balance is the balance brought forward.
            if (!effectiveCheckpointCaptured && day.isAfter(effectiveDay)) {
                minimum = running
                effectiveCheckpointCaptured = true
            }

            running += delta
            if (!day.isBefore(effectiveDay)) {
                minimum = minOf(minimum ?: running, running)
                effectiveCheckpointCaptured = true
            }
        }

        if (!effectiveCheckpointCaptured) minimum = running
        val result = minimum ?: running
        return if (abs(result) <= EPS) 0.0 else result
    }

    fun validateHistoricalQuantity(requestedQtyBase: Double, minimumAvailableQtyBase: Double) {
        require(requestedQtyBase.isFinite() && requestedQtyBase > EPS) { "الكمية المطلوبة يجب أن تكون أكبر من صفر" }
        require(minimumAvailableQtyBase.isFinite()) { "الرصيد التاريخي غير صالح" }
        require(minimumAvailableQtyBase >= -EPS) { "المخزون يحتوي رصيداً تاريخياً سالباً لهذه التشغيلة" }
        require(requestedQtyBase <= minimumAvailableQtyBase + EPS) {
            "الكمية المطلوبة تتجاوز الحد الآمن تاريخياً؛ الحد الآمن بأثر رجعي هو ${formatQty(minimumAvailableQtyBase)}"
        }
    }

    private fun formatQty(value: Double): String =
        if (kotlin.math.abs(value - value.toLong().toDouble()) <= EPS) value.toLong().toString() else "%.6f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')

    fun validateReversalAvailability(requestedQtyBase: Double, availableQtyBase: Double) {
        require(requestedQtyBase.isFinite() && requestedQtyBase > EPS) { "كمية العكس يجب أن تكون أكبر من صفر" }
        require(availableQtyBase.isFinite()) { "الرصيد المتاح غير صالح" }
        require(requestedQtyBase <= availableQtyBase + EPS) {
            "لا يمكن عكس التحويل لأن رصيد مخزن الوجهة لم يعد يكفي للكمية الأصلية"
        }
    }
}
