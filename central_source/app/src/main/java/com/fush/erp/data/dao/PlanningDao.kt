package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.DemandSeasonalityEntity
import com.fush.erp.data.entity.InventoryPlanningPolicyEntity
import com.fush.erp.data.entity.ProductionPlanEntity
import com.fush.erp.data.entity.ProductionPlanMaterialEntity
import com.fush.erp.data.entity.ProductionPlanMaterialView
import com.fush.erp.data.entity.DemandPlanEntity
import com.fush.erp.data.entity.MonthlyDemandHistoryRow
import com.fush.erp.data.entity.SalesBudgetWeekEntity
import com.fush.erp.data.entity.WeeklySalesActualRow
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanningDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInventoryPlanningPolicy(row: InventoryPlanningPolicyEntity): Long

    @Query("SELECT * FROM inventory_planning_policies WHERE itemId = :itemId LIMIT 1")
    suspend fun inventoryPlanningPolicy(itemId: Long): InventoryPlanningPolicyEntity?

    @Query("SELECT * FROM inventory_planning_policies WHERE itemId = :itemId LIMIT 1")
    fun observeInventoryPlanningPolicy(itemId: Long): Flow<InventoryPlanningPolicyEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProductionPlan(row: ProductionPlanEntity): Long

    @Update
    suspend fun updateProductionPlan(row: ProductionPlanEntity)

    @Query("""
        SELECT * FROM production_plans
        WHERE itemId = :itemId AND planYear = :planYear AND planMonth = :planMonth
        LIMIT 1
    """)
    suspend fun productionPlan(itemId: Long, planYear: Int, planMonth: Int): ProductionPlanEntity?

    @Query("""
        SELECT * FROM production_plans
        WHERE itemId = :itemId AND planYear = :planYear AND planMonth = :planMonth
        LIMIT 1
    """)
    fun observeProductionPlan(itemId: Long, planYear: Int, planMonth: Int): Flow<ProductionPlanEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProductionPlanMaterials(rows: List<ProductionPlanMaterialEntity>): List<Long>

    @Query("DELETE FROM production_plan_materials WHERE productionPlanId = :productionPlanId")
    suspend fun deleteProductionPlanMaterials(productionPlanId: Long)

    @Query("SELECT * FROM production_plan_materials WHERE productionPlanId = :productionPlanId ORDER BY sequenceNo, id")
    suspend fun productionPlanMaterials(productionPlanId: Long): List<ProductionPlanMaterialEntity>

    @Query("""
        SELECT ppm.id AS id, ppm.productionPlanId AS productionPlanId, ppm.itemId AS itemId,
               i.code AS code, i.nameAr AS itemName, u.nameAr AS unitName,
               ppm.sequenceNo AS sequenceNo, ppm.perBatchQtyBase AS perBatchQtyBase,
               ppm.expectedLossPct AS expectedLossPct, ppm.requiredQtyBase AS requiredQtyBase,
               ppm.currentStockQtyBase AS currentStockQtyBase, ppm.dailyUsageQtyBase AS dailyUsageQtyBase,
               ppm.safetyStockQtyBase AS safetyStockQtyBase, ppm.reorderPointQtyBase AS reorderPointQtyBase,
               ppm.suggestedPurchaseQtyBase AS suggestedPurchaseQtyBase,
               ppm.projectedEndingQtyBase AS projectedEndingQtyBase,
               COALESCE(ipp.safetyStockDays, 0) AS safetyStockDays,
               COALESCE(ipp.leadTimeDays, 0) AS leadTimeDays,
               COALESCE(ipp.note, '') AS policyNote
        FROM production_plan_materials ppm
        JOIN items i ON i.id = ppm.itemId
        JOIN units u ON u.id = i.baseUnitId
        LEFT JOIN inventory_planning_policies ipp ON ipp.itemId = ppm.itemId
        WHERE ppm.productionPlanId = :productionPlanId
        ORDER BY ppm.sequenceNo, ppm.id
    """)
    fun observeProductionPlanMaterialViews(productionPlanId: Long): Flow<List<ProductionPlanMaterialView>>

    @Query("""
        SELECT * FROM demand_plans
        WHERE itemId = :itemId AND planYear = :planYear AND planMonth = :planMonth
          AND status = 'APPROVED'
        ORDER BY provinceCode
    """)
    suspend fun approvedDemandPlans(itemId: Long, planYear: Int, planMonth: Int): List<DemandPlanEntity>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeeklySalesBudget(rows: List<SalesBudgetWeekEntity>): List<Long>

    @Query("SELECT * FROM sales_budget_weeks WHERE demandPlanId = :demandPlanId ORDER BY weekNo")
    fun observeWeeklySalesBudget(demandPlanId: Long): Flow<List<SalesBudgetWeekEntity>>

    @Query("SELECT * FROM sales_budget_weeks WHERE demandPlanId = :demandPlanId ORDER BY weekNo")
    suspend fun weeklySalesBudget(demandPlanId: Long): List<SalesBudgetWeekEntity>

    @Query("DELETE FROM sales_budget_weeks WHERE demandPlanId = :demandPlanId")
    suspend fun deleteWeeklySalesBudget(demandPlanId: Long)

    @Query("""
        WITH sales_weekly AS (
            SELECT
                CASE
                    WHEN CAST(strftime('%d', si.invoiceDate / 1000, 'unixepoch') AS INTEGER) <= 7 THEN 1
                    WHEN CAST(strftime('%d', si.invoiceDate / 1000, 'unixepoch') AS INTEGER) <= 14 THEN 2
                    WHEN CAST(strftime('%d', si.invoiceDate / 1000, 'unixepoch') AS INTEGER) <= 21 THEN 3
                    WHEN CAST(strftime('%d', si.invoiceDate / 1000, 'unixepoch') AS INTEGER) <= 28 THEN 4
                    ELSE 5
                END AS weekNo,
                COALESCE(SUM(sl.baseQuantity), 0) AS soldQtyBase,
                COALESCE(SUM(sl.netOriginal * si.exchangeRate), 0) AS soldValueBase
            FROM sales_invoices si
            JOIN sales_lines sl ON sl.invoiceId = si.id
            WHERE si.status = 'POSTED'
              AND sl.itemId = :itemId
              AND si.invoiceDate BETWEEN :fromDate AND :toDate
              AND (
                    (:provinceCode = 'TAIZ' AND si.province LIKE '%تعز%') OR
                    (:provinceCode = 'ADEN' AND si.province LIKE '%عدن%') OR
                    (:provinceCode = 'SANAA' AND si.province LIKE '%صنعاء%') OR
                    (:provinceCode = 'OTHER' AND si.province NOT LIKE '%تعز%' AND si.province NOT LIKE '%عدن%' AND si.province NOT LIKE '%صنعاء%')
                  )
            GROUP BY weekNo
        ),
        returns_weekly AS (
            SELECT
                CASE
                    WHEN CAST(strftime('%d', sr.returnDate / 1000, 'unixepoch') AS INTEGER) <= 7 THEN 1
                    WHEN CAST(strftime('%d', sr.returnDate / 1000, 'unixepoch') AS INTEGER) <= 14 THEN 2
                    WHEN CAST(strftime('%d', sr.returnDate / 1000, 'unixepoch') AS INTEGER) <= 21 THEN 3
                    WHEN CAST(strftime('%d', sr.returnDate / 1000, 'unixepoch') AS INTEGER) <= 28 THEN 4
                    ELSE 5
                END AS weekNo,
                COALESCE(SUM(srl.baseQuantity), 0) AS returnedQtyBase,
                COALESCE(SUM(srl.lineNetOriginal * sr.exchangeRate), 0) AS returnedValueBase
            FROM sales_returns sr
            JOIN sales_return_lines srl ON srl.returnId = sr.id
            JOIN sales_invoices si ON si.id = sr.salesInvoiceId
            WHERE sr.status = 'POSTED'
              AND srl.itemId = :itemId
              AND sr.returnDate BETWEEN :fromDate AND :toDate
              AND (
                    (:provinceCode = 'TAIZ' AND si.province LIKE '%تعز%') OR
                    (:provinceCode = 'ADEN' AND si.province LIKE '%عدن%') OR
                    (:provinceCode = 'SANAA' AND si.province LIKE '%صنعاء%') OR
                    (:provinceCode = 'OTHER' AND si.province NOT LIKE '%تعز%' AND si.province NOT LIKE '%عدن%' AND si.province NOT LIKE '%صنعاء%')
                  )
            GROUP BY weekNo
        ),
        weeks(weekNo) AS (
            SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
        )
        SELECT
            weeks.weekNo AS weekNo,
            COALESCE(sales_weekly.soldQtyBase, 0) AS soldQtyBase,
            COALESCE(returns_weekly.returnedQtyBase, 0) AS returnedQtyBase,
            COALESCE(sales_weekly.soldQtyBase, 0) - COALESCE(returns_weekly.returnedQtyBase, 0) AS netQtyBase,
            COALESCE(sales_weekly.soldValueBase, 0) AS soldValueBase,
            COALESCE(returns_weekly.returnedValueBase, 0) AS returnedValueBase,
            COALESCE(sales_weekly.soldValueBase, 0) - COALESCE(returns_weekly.returnedValueBase, 0) AS netValueBase
        FROM weeks
        LEFT JOIN sales_weekly ON sales_weekly.weekNo = weeks.weekNo
        LEFT JOIN returns_weekly ON returns_weekly.weekNo = weeks.weekNo
        ORDER BY weeks.weekNo
    """)
    suspend fun weeklySalesActual(
        itemId: Long,
        provinceCode: String,
        fromDate: Long,
        toDate: Long
    ): List<WeeklySalesActualRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDemandPlan(row: DemandPlanEntity): Long

    @Query("""
        SELECT * FROM demand_plans
        WHERE itemId = :itemId AND provinceCode = :provinceCode
          AND planYear = :planYear AND planMonth = :planMonth
        LIMIT 1
    """)
    fun observeDemandPlan(
        itemId: Long,
        provinceCode: String,
        planYear: Int,
        planMonth: Int
    ): Flow<DemandPlanEntity?>

    @Query("""
        SELECT * FROM demand_plans
        WHERE itemId = :itemId AND provinceCode = :provinceCode
          AND planYear = :planYear AND planMonth = :planMonth
        LIMIT 1
    """)
    suspend fun demandPlan(
        itemId: Long,
        provinceCode: String,
        planYear: Int,
        planMonth: Int
    ): DemandPlanEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeasonality(row: DemandSeasonalityEntity): Long

    @Query("""
        SELECT * FROM demand_seasonality
        WHERE itemId = :itemId AND provinceCode = :provinceCode
        ORDER BY month
    """)
    fun observeSeasonality(itemId: Long, provinceCode: String): Flow<List<DemandSeasonalityEntity>>

    @Query("""
        SELECT * FROM demand_seasonality
        WHERE itemId = :itemId AND provinceCode = :provinceCode
        ORDER BY month
    """)
    suspend fun seasonalityRows(itemId: Long, provinceCode: String): List<DemandSeasonalityEntity>

    @Query("""
        SELECT * FROM demand_seasonality
        WHERE itemId = :itemId AND provinceCode = :provinceCode AND month = :month
        LIMIT 1
    """)
    suspend fun seasonality(itemId: Long, provinceCode: String, month: Int): DemandSeasonalityEntity?

    @Query("""
        WITH sales_monthly AS (
            SELECT
                CAST(strftime('%Y', si.invoiceDate / 1000, 'unixepoch') AS INTEGER) AS year,
                CAST(strftime('%m', si.invoiceDate / 1000, 'unixepoch') AS INTEGER) AS month,
                COALESCE(SUM(sl.baseQuantity), 0) AS soldQtyBase
            FROM sales_invoices si
            JOIN sales_lines sl ON sl.invoiceId = si.id
            WHERE si.status = 'POSTED'
              AND sl.itemId = :itemId
              AND si.invoiceDate BETWEEN :fromDate AND :toDate
              AND (
                    (:provinceCode = 'TAIZ' AND si.province LIKE '%تعز%') OR
                    (:provinceCode = 'ADEN' AND si.province LIKE '%عدن%') OR
                    (:provinceCode = 'SANAA' AND si.province LIKE '%صنعاء%') OR
                    (:provinceCode = 'OTHER' AND si.province NOT LIKE '%تعز%' AND si.province NOT LIKE '%عدن%' AND si.province NOT LIKE '%صنعاء%')
                  )
            GROUP BY year, month
        ),
        returns_monthly AS (
            SELECT
                CAST(strftime('%Y', sr.returnDate / 1000, 'unixepoch') AS INTEGER) AS year,
                CAST(strftime('%m', sr.returnDate / 1000, 'unixepoch') AS INTEGER) AS month,
                COALESCE(SUM(srl.baseQuantity), 0) AS returnedQtyBase
            FROM sales_returns sr
            JOIN sales_return_lines srl ON srl.returnId = sr.id
            JOIN sales_invoices si ON si.id = sr.salesInvoiceId
            WHERE sr.status = 'POSTED'
              AND srl.itemId = :itemId
              AND sr.returnDate BETWEEN :fromDate AND :toDate
              AND (
                    (:provinceCode = 'TAIZ' AND si.province LIKE '%تعز%') OR
                    (:provinceCode = 'ADEN' AND si.province LIKE '%عدن%') OR
                    (:provinceCode = 'SANAA' AND si.province LIKE '%صنعاء%') OR
                    (:provinceCode = 'OTHER' AND si.province NOT LIKE '%تعز%' AND si.province NOT LIKE '%عدن%' AND si.province NOT LIKE '%صنعاء%')
                  )
            GROUP BY year, month
        ),
        months AS (
            SELECT year, month FROM sales_monthly
            UNION
            SELECT year, month FROM returns_monthly
        )
        SELECT
            months.year AS year,
            months.month AS month,
            COALESCE(sales_monthly.soldQtyBase, 0) AS soldQtyBase,
            COALESCE(returns_monthly.returnedQtyBase, 0) AS returnedQtyBase,
            COALESCE(sales_monthly.soldQtyBase, 0) - COALESCE(returns_monthly.returnedQtyBase, 0) AS netQtyBase
        FROM months
        LEFT JOIN sales_monthly ON sales_monthly.year = months.year AND sales_monthly.month = months.month
        LEFT JOIN returns_monthly ON returns_monthly.year = months.year AND returns_monthly.month = months.month
        ORDER BY months.year, months.month
    """)
    suspend fun monthlyDemandHistory(
        itemId: Long,
        provinceCode: String,
        fromDate: Long,
        toDate: Long
    ): List<MonthlyDemandHistoryRow>
}
