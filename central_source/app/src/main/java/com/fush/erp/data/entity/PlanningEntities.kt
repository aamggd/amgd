package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "demand_seasonality",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("itemId"),
        Index("provinceCode"),
        Index("month"),
        Index(value = ["itemId", "provinceCode", "month"], unique = true)
    ]
)
data class DemandSeasonalityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val provinceCode: String,
    val month: Int,
    val demandFactor: Double = 1.0,
    val note: String = "",
    val updatedBy: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "demand_plans",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("itemId"),
        Index("provinceCode"),
        Index("planYear"),
        Index("planMonth"),
        Index("status"),
        Index(value = ["itemId", "provinceCode", "planYear", "planMonth"], unique = true)
    ]
)
data class DemandPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val provinceCode: String,
    val planYear: Int,
    val planMonth: Int,
    val baselineQtyBase: Double,
    val seasonFactor: Double,
    val systemForecastQtyBase: Double,
    val plannedQtyBase: Double,
    val manualAdjustmentQtyBase: Double,
    val note: String = "",
    val status: String = "DRAFT",
    val revision: Int = 1,
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedBy: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val approvedBy: Long? = null,
    val approvedAt: Long? = null,
    val lastActionReason: String = ""
)



@Entity(
    tableName = "sales_budget_weeks",
    foreignKeys = [
        ForeignKey(
            entity = DemandPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["demandPlanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("demandPlanId"),
        Index("weekNo"),
        Index(value = ["demandPlanId", "weekNo"], unique = true)
    ]
)
data class SalesBudgetWeekEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val demandPlanId: Long,
    val weekNo: Int,
    val plannedQtyBase: Double,
    val note: String = "",
    val updatedBy: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

data class WeeklySalesActualRow(
    val weekNo: Int,
    val soldQtyBase: Double,
    val returnedQtyBase: Double,
    val netQtyBase: Double,
    val soldValueBase: Double,
    val returnedValueBase: Double,
    val netValueBase: Double
)


@Entity(
    tableName = "inventory_planning_policies",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class InventoryPlanningPolicyEntity(
    @PrimaryKey val itemId: Long,
    val safetyStockDays: Double = 0.0,
    val leadTimeDays: Double = 0.0,
    val note: String = "",
    val updatedBy: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "production_plans",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("itemId"),
        Index("planYear"),
        Index("planMonth"),
        Index("status"),
        Index(value = ["itemId", "planYear", "planMonth"], unique = true)
    ]
)
data class ProductionPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val planYear: Int,
    val planMonth: Int,
    val recipeId: Long,
    val recipeVersionNo: Int,
    val recipeTargetOutputQtyBase: Double,
    val approvedDemandQtyBase: Double,
    val approvedProvinceCount: Int,
    val finishedStockQtyBase: Double,
    val finishedDailyDemandQtyBase: Double,
    val finishedSafetyStockQtyBase: Double,
    val finishedReorderPointQtyBase: Double,
    val netProductionNeedQtyBase: Double,
    val plannedBatchCount: Int,
    val plannedOutputQtyBase: Double,
    val projectedEndingFinishedQtyBase: Double,
    val status: String = "DRAFT",
    val revision: Int = 1,
    val generatedBy: Long,
    val generatedAt: Long = System.currentTimeMillis(),
    val updatedBy: Long,
    val updatedAt: Long = System.currentTimeMillis(),
    val approvedBy: Long? = null,
    val approvedAt: Long? = null,
    val lastActionReason: String = ""
)

@Entity(
    tableName = "production_plan_materials",
    foreignKeys = [
        ForeignKey(
            entity = ProductionPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["productionPlanId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("productionPlanId"),
        Index("itemId"),
        Index(value = ["productionPlanId", "sequenceNo"], unique = true)
    ]
)
data class ProductionPlanMaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productionPlanId: Long,
    val itemId: Long,
    val sequenceNo: Int,
    val perBatchQtyBase: Double,
    val expectedLossPct: Double,
    val requiredQtyBase: Double,
    val currentStockQtyBase: Double,
    val dailyUsageQtyBase: Double,
    val safetyStockQtyBase: Double,
    val reorderPointQtyBase: Double,
    val suggestedPurchaseQtyBase: Double,
    val projectedEndingQtyBase: Double
)

data class ProductionPlanMaterialView(
    val id: Long,
    val productionPlanId: Long,
    val itemId: Long,
    val code: String,
    val itemName: String,
    val unitName: String,
    val sequenceNo: Int,
    val perBatchQtyBase: Double,
    val expectedLossPct: Double,
    val requiredQtyBase: Double,
    val currentStockQtyBase: Double,
    val dailyUsageQtyBase: Double,
    val safetyStockQtyBase: Double,
    val reorderPointQtyBase: Double,
    val suggestedPurchaseQtyBase: Double,
    val projectedEndingQtyBase: Double,
    val safetyStockDays: Double,
    val leadTimeDays: Double,
    val policyNote: String
)

data class MonthlyDemandHistoryRow(
    val year: Int,
    val month: Int,
    val soldQtyBase: Double,
    val returnedQtyBase: Double,
    val netQtyBase: Double
)

data class DemandForecastSnapshot(
    val itemId: Long,
    val provinceCode: String,
    val forecastYear: Int,
    val forecastMonth: Int,
    val baselineQtyBase: Double,
    val seasonFactor: Double,
    val forecastQtyBase: Double,
    val history: List<MonthlyDemandHistoryRow>
)


data class SeasonalDemandAnalysis(
    val itemId: Long,
    val provinceCode: String,
    val baselineQtyBase: Double,
    val historyMonths: Int,
    val summerActualAvgQtyBase: Double,
    val winterActualAvgQtyBase: Double,
    val summerFactorAvg: Double,
    val winterFactorAvg: Double,
    val summerForecastMonthlyQtyBase: Double,
    val winterForecastMonthlyQtyBase: Double,
    val summerSamples: Int,
    val winterSamples: Int
)

data class ProvinceSeasonalityComparisonRow(
    val provinceCode: String,
    val summerActualAvgQtyBase: Double,
    val winterActualAvgQtyBase: Double,
    val summerFactorAvg: Double,
    val winterFactorAvg: Double,
    val summerForecastMonthlyQtyBase: Double,
    val winterForecastMonthlyQtyBase: Double
)
