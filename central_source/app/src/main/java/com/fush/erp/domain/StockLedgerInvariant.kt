package com.fush.erp.domain

import kotlin.math.abs

/**
 * Canonical stock-ledger invariant for FUSH inventory.
 *
 * A stock balance is derived only from persisted stock movements:
 * balance(item, warehouse, asOf) = Σ quantityBase for movements whose movementDate <= asOf.
 *
 * This helper intentionally contains no mutable/stored-balance concept. It is used by tests and
 * domain code to keep historical recomputation semantics explicit and deterministic.
 */
data class StockLedgerPoint(
    val movementDate: Long,
    val quantityBase: Double,
)

object StockLedgerInvariant {
    const val EPS = 0.000000001

    fun balance(points: Iterable<StockLedgerPoint>, asOf: Long = Long.MAX_VALUE): Double {
        require(asOf >= 0L) { "تاريخ رصيد المخزون غير صالح" }

        var total = 0.0
        points.forEach { point ->
            require(point.movementDate >= 0L) { "تاريخ حركة المخزون غير صالح" }
            require(point.quantityBase.isFinite()) { "كمية حركة المخزون غير صالحة" }
            if (point.movementDate <= asOf) total += point.quantityBase
        }

        require(total.isFinite()) { "رصيد المخزون المحسوب غير صالح" }
        return if (abs(total) <= EPS) 0.0 else total
    }
}
