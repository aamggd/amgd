package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_counts",
    indices = [Index(value = ["countNo"], unique = true), Index("warehouseId"), Index("status"), Index("countDate")],
    foreignKeys = [ForeignKey(entity = WarehouseEntity::class, parentColumns = ["id"], childColumns = ["warehouseId"], onDelete = ForeignKey.RESTRICT)]
)
data class InventoryCountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val countNo: String,
    val warehouseId: Long,
    val countDate: Long,
    val status: String = "DRAFT",
    val notes: String = "",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val postedAt: Long? = null
)

@Entity(
    tableName = "inventory_count_lines",
    indices = [Index("countId"), Index("itemId"), Index(value = ["countId", "itemId", "lotKey", "expiryKey"], unique = true)],
    foreignKeys = [
        ForeignKey(entity = InventoryCountEntity::class, parentColumns = ["id"], childColumns = ["countId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class InventoryCountLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val countId: Long,
    val itemId: Long,
    val lotNo: String? = null,
    val expiryDate: Long? = null,
    val lotKey: String = "",
    val expiryKey: Long = -1L,
    val systemQtyBase: Double,
    val countedQtyBase: Double? = null,
    val varianceQtyBase: Double = 0.0,
    val unitCostBase: Double,
    val reason: String = ""
)

@Entity(
    tableName = "inventory_lot_controls",
    indices = [
        Index("warehouseId"), Index("itemId"), Index("status"),
        Index(value = ["warehouseId", "itemId", "lotKey", "expiryKey"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(entity = WarehouseEntity::class, parentColumns = ["id"], childColumns = ["warehouseId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class InventoryLotControlEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val warehouseId: Long,
    val itemId: Long,
    val lotNo: String? = null,
    val expiryDate: Long? = null,
    val lotKey: String = "",
    val expiryKey: Long = -1L,
    val status: String = "ACCEPTED",
    val reason: String = "",
    val changedBy: Long,
    val changedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "warehouse_reorder_policies",
    indices = [
        Index("warehouseId"), Index("itemId"),
        Index(value = ["warehouseId", "itemId"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(entity = WarehouseEntity::class, parentColumns = ["id"], childColumns = ["warehouseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class WarehouseReorderPolicyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val warehouseId: Long,
    val itemId: Long,
    val reorderLevel: Double,
    val updatedBy: Long,
    val updatedAt: Long = System.currentTimeMillis()
)



@Entity(
    tableName = "warehouse_transfers",
    indices = [
        Index(value = ["transferNo"], unique = true),
        Index("fromWarehouseId"), Index("toWarehouseId"), Index("transferDate"), Index("status")
    ],
    foreignKeys = [
        ForeignKey(entity = WarehouseEntity::class, parentColumns = ["id"], childColumns = ["fromWarehouseId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = WarehouseEntity::class, parentColumns = ["id"], childColumns = ["toWarehouseId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class WarehouseTransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transferNo: String,
    val transferDate: Long,
    val fromWarehouseId: Long,
    val toWarehouseId: Long,
    val status: String = "DRAFT",
    val notes: String = "",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val postedBy: Long? = null,
    val postedAt: Long? = null,
    val cancelReason: String = "",
    val reversalReason: String = "",
    val reversedBy: Long? = null,
    val reversedAt: Long? = null
)

@Entity(
    tableName = "warehouse_transfer_lines",
    indices = [
        Index("transferId"), Index("itemId"),
        Index(value = ["transferId", "itemId", "lotKey", "expiryKey"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(entity = WarehouseTransferEntity::class, parentColumns = ["id"], childColumns = ["transferId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class WarehouseTransferLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transferId: Long,
    val itemId: Long,
    val quantityBase: Double,
    val unitCostBase: Double = 0.0,
    val lotNo: String? = null,
    val expiryDate: Long? = null,
    val lotKey: String = "",
    val expiryKey: Long = -1L
)

data class WarehouseTransferSummaryRow(
    val id: Long,
    val transferNo: String,
    val transferDate: Long,
    val fromWarehouseName: String,
    val toWarehouseName: String,
    val status: String,
    val lineCount: Int,
    val totalQtyBase: Double,
    val totalValueBase: Double
)

data class WarehouseTransferLineView(
    val id: Long,
    val itemId: Long,
    val itemCode: String,
    val itemName: String,
    val quantityBase: Double,
    val unitCostBase: Double,
    val lotNo: String?,
    val expiryDate: Long?
)

data class InventorySnapshotRow(
    val itemId: Long,
    val lotNo: String?,
    val expiryDate: Long?,
    val quantityBase: Double,
    val inventoryValueBase: Double
)

data class InventoryCountSummaryRow(
    val id: Long,
    val countNo: String,
    val countDate: Long,
    val warehouseName: String,
    val status: String,
    val lineCount: Int,
    val varianceLines: Int,
    val varianceValueBase: Double
)

data class InventoryAlertRow(
    val warehouseId: Long,
    val warehouseCode: String,
    val warehouseName: String,
    val itemId: Long,
    val code: String,
    val nameAr: String,
    val quantityBase: Double,
    val reorderLevel: Double,
    val baseUnitName: String
)

data class WarehouseReorderPolicyView(
    val id: Long,
    val warehouseId: Long,
    val warehouseCode: String,
    val warehouseName: String,
    val itemId: Long,
    val itemCode: String,
    val itemName: String,
    val reorderLevel: Double,
    val baseUnitName: String
)

data class InventoryLotAlertRow(
    val warehouseId: Long,
    val warehouseName: String,
    val itemId: Long,
    val code: String,
    val nameAr: String,
    val lotNo: String?,
    val expiryDate: Long?,
    val quantityBase: Double,
    val inventoryValueBase: Double,
    val controlStatus: String
)

data class InventoryMovementRow(
    val id: Long,
    val movementDate: Long,
    val warehouseName: String,
    val itemId: Long,
    val itemName: String,
    val movementType: String,
    val quantityBase: Double,
    val unitCostBase: Double,
    val lotNo: String?,
    val expiryDate: Long?,
    val referenceType: String,
    val referenceId: Long
)
