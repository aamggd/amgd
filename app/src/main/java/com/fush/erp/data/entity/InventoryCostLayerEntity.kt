package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Immutable accounting evidence for the cost carried by one stock movement. */
@Entity(
    tableName = "inventory_cost_layers",
    foreignKeys = [ForeignKey(
        entity = StockMovementEntity::class,
        parentColumns = ["id"],
        childColumns = ["stockMovementId"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [
        Index("itemId"), Index("warehouseId"),
        Index("glSourceType", "glSourceId"), Index("sourceReferenceType", "sourceReferenceId")
    ]
)
data class InventoryCostLayerEntity(
    @PrimaryKey val stockMovementId: Long,
    val movementDate: Long,
    val warehouseId: Long,
    val itemId: Long,
    val movementType: String,
    val quantityBase: Double,
    val unitCostBase: Double,
    val signedValueBase: Double,
    val costingMethodVersion: String = "MOVEMENT_ACTUAL_V1",
    val lotNo: String = "",
    val sourceReferenceType: String,
    val sourceReferenceId: Long,
    val glSourceType: String = "",
    val glSourceId: String = "",
    val traceClass: String = "UNMAPPED"
)
