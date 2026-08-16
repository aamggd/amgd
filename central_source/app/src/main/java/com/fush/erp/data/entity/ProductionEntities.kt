package com.fush.erp.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recipes",
    indices = [
        Index(value = ["code", "versionNo"], unique = true),
        Index("productItemId")
    ],
    foreignKeys = [ForeignKey(
        entity = ItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["productItemId"],
        onDelete = ForeignKey.RESTRICT
    )]
)
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val productItemId: Long,
    val versionNo: Int,
    val effectiveFrom: Long,
    val targetOutputQtyBase: Double,
    val status: String = "ACTIVE",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recipe_components",
    indices = [Index("recipeId"), Index("itemId")],
    foreignKeys = [
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class RecipeComponentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val itemId: Long,
    val quantityBase: Double,
    val expectedLossPct: Double = 0.0,
    val stage: String = "PREPARATION",
    val sequenceNo: Int = 0
)

@Entity(
    tableName = "production_orders",
    indices = [Index(value = ["orderNo"], unique = true), Index("recipeId"), Index("productItemId"), Index("rawWarehouseId"), Index("finishedWarehouseId")],
    foreignKeys = [
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["productItemId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = WarehouseEntity::class, parentColumns = ["id"], childColumns = ["rawWarehouseId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = WarehouseEntity::class, parentColumns = ["id"], childColumns = ["finishedWarehouseId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class ProductionOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderNo: String,
    val recipeId: Long,
    val productItemId: Long,
    val plannedOutputQtyBase: Double,
    val rawWarehouseId: Long,
    val finishedWarehouseId: Long,
    val plannedDate: Long,
    val status: String = "PLANNED",
    val directLaborCostBase: Double = 0.0,
    val primaryAssetId: Long? = null,
    val notes: String = "",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null
)

@Entity(
    tableName = "production_materials",
    indices = [Index("orderId"), Index("itemId")],
    foreignKeys = [
        ForeignKey(entity = ProductionOrderEntity::class, parentColumns = ["id"], childColumns = ["orderId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class ProductionMaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val recipeComponentId: Long,
    val itemId: Long,
    val standardQtyBase: Double,
    val reservedQtyBase: Double = 0.0,
    val issuedQtyBase: Double = 0.0,
    val issueCostBase: Double = 0.0
)

@Entity(
    tableName = "production_batches",
    indices = [Index(value = ["batchNo"], unique = true), Index(value = ["orderId"], unique = true)],
    foreignKeys = [ForeignKey(
        entity = ProductionOrderEntity::class,
        parentColumns = ["id"],
        childColumns = ["orderId"],
        onDelete = ForeignKey.RESTRICT
    )]
)
data class ProductionBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchNo: String,
    val orderId: Long,
    val manufactureDate: Long,
    val expiryDate: Long,
    val status: String = "MATERIALS_RESERVED",
    val actualOutputQtyBase: Double = 0.0,
    val acceptedQtyBase: Double = 0.0,
    val rejectedQtyBase: Double = 0.0,
    val scrapQtyBase: Double = 0.0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "production_issues",
    indices = [Index("orderId"), Index("materialId"), Index("itemId")],
    foreignKeys = [
        ForeignKey(entity = ProductionOrderEntity::class, parentColumns = ["id"], childColumns = ["orderId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProductionMaterialEntity::class, parentColumns = ["id"], childColumns = ["materialId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class ProductionIssueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val materialId: Long,
    val itemId: Long,
    val quantityBase: Double,
    val unitCostBase: Double,
    val totalCostBase: Double,
    val lotNo: String? = null,
    val expiryDate: Long? = null,
    @ColumnInfo(defaultValue = "'ISSUE'") val issueKind: String = "ISSUE",
    val correctionOfIssueId: Long? = null,
    @ColumnInfo(defaultValue = "''") val reason: String = "",
    val createdBy: Long? = null,
    val issueDate: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "quality_specifications",
    indices = [
        Index("productItemId"),
        Index(value = ["productItemId", "stage", "parameterName"], unique = true)
    ],
    foreignKeys = [ForeignKey(
        entity = ItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["productItemId"],
        onDelete = ForeignKey.RESTRICT
    )]
)
data class QualitySpecificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productItemId: Long,
    val stage: String = "FINAL",
    val parameterName: String,
    val unit: String,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val targetValue: Double? = null,
    val requiredSampleSize: Int = 1,
    val isRequired: Boolean = true,
    val isActive: Boolean = true,
    val notes: String = "",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "quality_checks",
    indices = [Index("batchId"), Index("specificationId")],
    foreignKeys = [ForeignKey(
        entity = ProductionBatchEntity::class,
        parentColumns = ["id"],
        childColumns = ["batchId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class QualityCheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val stage: String,
    val checkName: String,
    val resultValue: String = "",
    val specificationId: Long? = null,
    val measuredValue: Double? = null,
    @ColumnInfo(defaultValue = "''") val unit: String = "",
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val targetValue: Double? = null,
    @ColumnInfo(defaultValue = "0") val sampleSize: Int = 0,
    val decision: String,
    val notes: String = "",
    val checkedBy: Long,
    val checkedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "quality_check_samples",
    indices = [
        Index("checkId"),
        Index(value = ["checkId", "sequenceNo"], unique = true)
    ],
    foreignKeys = [ForeignKey(
        entity = QualityCheckEntity::class,
        parentColumns = ["id"],
        childColumns = ["checkId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class QualityCheckSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val checkId: Long,
    val sequenceNo: Int,
    val measuredValue: Double
)

@Entity(
    tableName = "non_conformances",
    indices = [Index("batchId")],
    foreignKeys = [ForeignKey(
        entity = ProductionBatchEntity::class,
        parentColumns = ["id"],
        childColumns = ["batchId"],
        onDelete = ForeignKey.RESTRICT
    )]
)
data class NonConformanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val code: String,
    val description: String,
    val immediateAction: String = "",
    val rootCause: String = "",
    val correctiveAction: String = "",
    val preventiveAction: String = "",
    val responsible: String = "",
    val dueDate: Long? = null,
    val status: String = "OPEN",
    val effectivenessVerified: Boolean = false,
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null
)


data class RecipeComponentView(
    val itemId: Long,
    val itemName: String,
    val quantityBase: Double,
    val unitName: String,
    val stage: String
)

data class RecipeSummary(
    val id: Long,
    val code: String,
    val versionNo: Int,
    val productName: String,
    val targetOutputQtyBase: Double,
    val status: String
)

data class ProductionOrderSummary(
    val id: Long,
    val orderNo: String,
    val plannedDate: Long,
    val productName: String,
    val plannedOutputQtyBase: Double,
    val status: String,
    val batchNo: String?
)

data class ProductionMaterialView(
    val id: Long,
    val itemId: Long,
    val itemName: String,
    val standardQtyBase: Double,
    val reservedQtyBase: Double,
    val issuedQtyBase: Double,
    val issueCostBase: Double,
    val unitName: String
)

data class LotBalanceRow(
    val itemId: Long,
    val lotNo: String?,
    val expiryDate: Long?,
    val quantityBase: Double,
    val inventoryValueBase: Double
)
