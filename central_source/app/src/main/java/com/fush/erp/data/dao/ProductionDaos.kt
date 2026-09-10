package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRecipe(row: RecipeEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertComponents(rows: List<RecipeComponentEntity>)

    @Query("UPDATE recipes SET status = :status WHERE id = :recipeId")
    suspend fun updateRecipeStatus(recipeId: Long, status: String): Int

    @Query("SELECT COALESCE(MAX(versionNo), 0) FROM recipes WHERE code = :code")
    suspend fun maxVersion(code: String): Int

    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE productItemId = :productItemId AND status = 'ACTIVE' ORDER BY versionNo DESC LIMIT 1")
    suspend fun activeForProduct(productItemId: Long): RecipeEntity?

    @Query("SELECT * FROM recipe_components WHERE recipeId = :recipeId ORDER BY sequenceNo, id")
    suspend fun components(recipeId: Long): List<RecipeComponentEntity>

    @Query("""
        SELECT rc.itemId AS itemId, i.nameAr AS itemName, rc.quantityBase AS quantityBase,
               u.nameAr AS unitName, rc.stage AS stage
        FROM recipe_components rc
        JOIN items i ON i.id = rc.itemId
        JOIN units u ON u.id = i.baseUnitId
        WHERE rc.recipeId = :recipeId
        ORDER BY rc.sequenceNo, rc.id
    """)
    suspend fun componentViews(recipeId: Long): List<RecipeComponentView>

    @Query("""
        SELECT r.id AS id, r.code AS code, r.versionNo AS versionNo,
               i.nameAr AS productName, r.targetOutputQtyBase AS targetOutputQtyBase,
               r.status AS status
        FROM recipes r
        JOIN items i ON i.id = r.productItemId
        ORDER BY r.code, r.versionNo DESC
    """)
    fun observeSummaries(): Flow<List<RecipeSummary>>

    @Query("SELECT COUNT(*) FROM production_orders WHERE recipeId = :recipeId")
    suspend fun productionOrderCount(recipeId: Long): Int

    @Query("DELETE FROM recipes WHERE id = :recipeId")
    suspend fun deleteById(recipeId: Long): Int

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun count(): Int
}

@Dao
interface ProductionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrder(row: ProductionOrderEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMaterials(rows: List<ProductionMaterialEntity>)

    @Update
    suspend fun updateMaterial(row: ProductionMaterialEntity)

    @Update
    suspend fun updateOrder(row: ProductionOrderEntity)

    @Query("DELETE FROM production_orders WHERE id = :orderId")
    suspend fun deleteOrderById(orderId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBatch(row: ProductionBatchEntity): Long

    @Update
    suspend fun updateBatch(row: ProductionBatchEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIssue(row: ProductionIssueEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQualitySpecification(row: QualitySpecificationEntity): Long

    @Update
    suspend fun updateQualitySpecification(row: QualitySpecificationEntity)

    @Query("SELECT * FROM quality_specifications WHERE id = :id LIMIT 1")
    suspend fun qualitySpecificationById(id: Long): QualitySpecificationEntity?

    @Query("SELECT * FROM quality_specifications WHERE productItemId = :productItemId AND stage = :stage ORDER BY isActive DESC, parameterName, id")
    suspend fun qualitySpecificationsForProduct(productItemId: Long, stage: String = "FINAL"): List<QualitySpecificationEntity>

    @Query("SELECT * FROM quality_specifications WHERE productItemId = :productItemId AND stage = :stage AND isActive = 1 ORDER BY parameterName, id")
    suspend fun activeQualitySpecificationsForProduct(productItemId: Long, stage: String = "FINAL"): List<QualitySpecificationEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQualityCheck(row: QualityCheckEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQualityCheckSamples(rows: List<QualityCheckSampleEntity>)

    @Query("SELECT * FROM quality_check_samples WHERE checkId = :checkId ORDER BY sequenceNo, id")
    suspend fun samplesForQualityCheck(checkId: Long): List<QualityCheckSampleEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNonConformance(row: NonConformanceEntity): Long

    @Update
    suspend fun updateNonConformance(row: NonConformanceEntity)

    @Query("SELECT * FROM production_orders WHERE id = :id LIMIT 1")
    suspend fun orderById(id: Long): ProductionOrderEntity?

    @Query("SELECT * FROM production_batches WHERE id = :id LIMIT 1")
    suspend fun batchById(id: Long): ProductionBatchEntity?

    @Query("SELECT * FROM production_batches WHERE orderId = :orderId LIMIT 1")
    suspend fun batchForOrder(orderId: Long): ProductionBatchEntity?

    @Query("SELECT * FROM production_materials WHERE orderId = :orderId ORDER BY id")
    suspend fun materialsForOrder(orderId: Long): List<ProductionMaterialEntity>

    @Query("""
        SELECT COALESCE(SUM(pm.reservedQtyBase - pm.issuedQtyBase), 0)
        FROM production_materials pm
        JOIN production_orders po ON po.id = pm.orderId
        WHERE po.rawWarehouseId = :warehouseId AND pm.itemId = :itemId
          AND po.status NOT IN ('CLOSED', 'CANCELLED', 'REJECTED')
          AND pm.orderId != :excludeOrderId
    """)
    suspend fun reservedByOtherOrders(warehouseId: Long, itemId: Long, excludeOrderId: Long): Double

    @Query("""
        SELECT pm.id AS id, pm.itemId AS itemId, i.nameAr AS itemName,
               pm.standardQtyBase AS standardQtyBase,
               pm.reservedQtyBase AS reservedQtyBase,
               pm.issuedQtyBase AS issuedQtyBase,
               pm.issueCostBase AS issueCostBase,
               u.nameAr AS unitName
        FROM production_materials pm
        JOIN items i ON i.id = pm.itemId
        JOIN units u ON u.id = i.baseUnitId
        WHERE pm.orderId = :orderId
        ORDER BY pm.id
    """)
    suspend fun materialViews(orderId: Long): List<ProductionMaterialView>

    @Query("SELECT * FROM production_issues WHERE orderId = :orderId ORDER BY issueDate, id")
    suspend fun issuesForOrder(orderId: Long): List<ProductionIssueEntity>

    @Query("SELECT * FROM production_issues WHERE materialId = :materialId AND issueKind IN ('ISSUE','CORRECTION_ISSUE') ORDER BY issueDate DESC, id DESC")
    suspend fun baseIssuesForMaterial(materialId: Long): List<ProductionIssueEntity>

    @Query("SELECT COALESCE(SUM(-quantityBase), 0) FROM production_issues WHERE correctionOfIssueId = :issueId AND issueKind = 'CORRECTION_RETURN'")
    suspend fun correctedQtyForIssue(issueId: Long): Double

    @Query("SELECT COALESCE(SUM(quantityBase), 0) FROM production_issues WHERE materialId = :materialId")
    suspend fun netIssuedQtyForMaterial(materialId: Long): Double

    @Query("SELECT COALESCE(SUM(totalCostBase), 0) FROM production_issues WHERE materialId = :materialId")
    suspend fun netIssueCostForMaterial(materialId: Long): Double

    @Query("SELECT COALESCE(SUM(totalCostBase), 0) FROM production_issues WHERE orderId = :orderId")
    suspend fun materialCostForOrder(orderId: Long): Double

    @Query("SELECT * FROM quality_checks WHERE batchId = :batchId ORDER BY checkedAt, id")
    suspend fun checksForBatch(batchId: Long): List<QualityCheckEntity>

    @Query("SELECT COUNT(*) FROM quality_checks WHERE batchId = :batchId AND decision = 'FAIL'")
    suspend fun failedChecks(batchId: Long): Int

    @Query("SELECT COUNT(*) FROM quality_checks WHERE batchId = :batchId AND decision = 'PASS'")
    suspend fun passedChecks(batchId: Long): Int

    @Query("SELECT * FROM non_conformances WHERE batchId = :batchId ORDER BY createdAt DESC")
    suspend fun nonConformancesForBatch(batchId: Long): List<NonConformanceEntity>

    @Query("SELECT * FROM non_conformances WHERE id = :id LIMIT 1")
    suspend fun nonConformanceById(id: Long): NonConformanceEntity?

    @Query("SELECT COUNT(*) FROM non_conformances WHERE batchId = :batchId AND status != 'CLOSED'")
    suspend fun openNonConformanceCount(batchId: Long): Int

    @Query("""
        SELECT po.id AS id, po.orderNo AS orderNo, po.plannedDate AS plannedDate,
               i.nameAr AS productName, po.plannedOutputQtyBase AS plannedOutputQtyBase,
               po.status AS status, pb.batchNo AS batchNo
        FROM production_orders po
        JOIN items i ON i.id = po.productItemId
        LEFT JOIN production_batches pb ON pb.orderId = po.id
        ORDER BY po.plannedDate DESC, po.id DESC
    """)
    fun observeOrderSummaries(): Flow<List<ProductionOrderSummary>>

    @Query("SELECT COUNT(*) FROM production_orders")
    fun observeOrderCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM production_batches WHERE manufactureDate >= :startMillis AND manufactureDate < :endMillis")
    suspend fun countBatchesInRange(startMillis: Long, endMillis: Long): Int

    @Query("SELECT COUNT(*) FROM production_batches WHERE status IN ('QC_HOLD', 'UNDER_QC')")
    fun observeQcHoldCount(): Flow<Int>
}
