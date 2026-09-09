package com.fush.erp.domain

import kotlin.math.abs

data class InventoryCostTraceGroup(
    val sourceType: String,
    val sourceId: String,
    val layerValueBase: Double,
    val glInventoryDeltaBase: Double
)

object InventoryCostTracePolicy {
    private const val EPS = 0.01

    fun requireValuationReconstructable(stockMovementValueBase: Double, layerValueBase: Double) {
        require(stockMovementValueBase.isFinite() && layerValueBase.isFinite()) { "INVENTORY_COST_TRACE_NON_FINITE" }
        require(abs(stockMovementValueBase - layerValueBase) <= EPS) { "INVENTORY_COST_LAYER_VALUATION_MISMATCH" }
    }

    fun requireMappedGlGroupsReconcile(groups: List<InventoryCostTraceGroup>) {
        groups.forEach { row ->
            require(row.sourceType.isNotBlank() && row.sourceId.isNotBlank()) { "INVENTORY_COST_GL_SOURCE_REQUIRED" }
            require(abs(row.layerValueBase - row.glInventoryDeltaBase) <= EPS) {
                "INVENTORY_COST_GL_MISMATCH:${row.sourceType}:${row.sourceId}"
            }
        }
    }
}
