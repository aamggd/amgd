package com.fush.erp.cloud

data class InventoryProductionConflictDifference(
    val field: String,
    val localValue: String,
    val cloudValue: String,
    val severity: String = "BUSINESS",
)

data class InventoryProductionConflict(
    val documentType: String,
    val documentNo: String,
    val differences: List<InventoryProductionConflictDifference>,
)

data class InventoryProductionSyncResult(
    val uploadedProductionOrders: Int,
    val downloadedProductionOrders: Int,
    val unchangedProductionOrders: Int,
    val productionConflicts: Int,
    val skippedLocalProductionOrders: Int,
    val inventoryLotsPublished: Int,
    val inventoryLotsAdjusted: Int,
    val inventoryLotsUnchanged: Int,
    val inventoryAbsoluteQuantityDelta: Double,
    val bootstrappedCloud: Boolean,
    val completedAtEpochMillis: Long,
    val conflictDetails: List<InventoryProductionConflict> = emptyList(),
)
