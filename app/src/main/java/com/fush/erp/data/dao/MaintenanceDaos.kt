package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAsset(row: AssetEntity): Long

    @Update
    suspend fun updateAsset(row: AssetEntity)

    @Query("SELECT * FROM assets WHERE id = :id LIMIT 1")
    suspend fun assetById(id: Long): AssetEntity?

    @Query("SELECT * FROM assets WHERE code = :code LIMIT 1")
    suspend fun assetByCode(code: String): AssetEntity?

    @Query("SELECT * FROM assets WHERE isActive = 1 ORDER BY nameAr")
    fun observeAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE isActive = 1 AND status = 'ACTIVE' ORDER BY nameAr")
    suspend fun operationalAssets(): List<AssetEntity>

    @Query("SELECT COUNT(*) FROM assets WHERE isActive = 1")
    fun observeAssetCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlan(row: MaintenancePlanEntity): Long

    @Update
    suspend fun updatePlan(row: MaintenancePlanEntity)

    @Query("SELECT * FROM maintenance_plans WHERE id = :id LIMIT 1")
    suspend fun planById(id: Long): MaintenancePlanEntity?

    @Query("SELECT * FROM maintenance_plans WHERE assetId = :assetId AND isActive = 1 ORDER BY nameAr")
    suspend fun plansForAsset(assetId: Long): List<MaintenancePlanEntity>

    @Query("SELECT COUNT(*) FROM maintenance_plans WHERE assetId = :assetId AND isActive = 1 AND nextDueAt IS NOT NULL AND nextDueAt < :now")
    suspend fun overduePlanCount(assetId: Long, now: Long): Int

    @Query("SELECT COUNT(*) FROM maintenance_plans WHERE isActive = 1 AND nextDueAt IS NOT NULL AND nextDueAt < :now")
    fun observeOverduePlanCount(now: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWorkOrder(row: MaintenanceWorkOrderEntity): Long

    @Update
    suspend fun updateWorkOrder(row: MaintenanceWorkOrderEntity)

    @Query("SELECT * FROM maintenance_work_orders WHERE id = :id LIMIT 1")
    suspend fun workOrderById(id: Long): MaintenanceWorkOrderEntity?

    @Query("SELECT * FROM maintenance_work_orders WHERE status NOT IN ('COMPLETED','CANCELLED') ORDER BY dueAt IS NULL, dueAt, openedAt DESC")
    fun observeOpenWorkOrders(): Flow<List<MaintenanceWorkOrderEntity>>

    @Query("SELECT COUNT(*) FROM maintenance_work_orders WHERE status NOT IN ('COMPLETED','CANCELLED')")
    fun observeOpenWorkOrderCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM maintenance_work_orders WHERE status NOT IN ('COMPLETED','CANCELLED') AND dueAt IS NOT NULL AND dueAt < :now")
    fun observeOverdueWorkOrderCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM maintenance_work_orders WHERE workType = 'PREVENTIVE' AND dueAt IS NOT NULL AND dueAt BETWEEN :from AND :to")
    suspend fun preventiveDueCount(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM maintenance_work_orders WHERE workType = 'PREVENTIVE' AND status = 'COMPLETED' AND dueAt IS NOT NULL AND completedAt IS NOT NULL AND completedAt <= dueAt AND dueAt BETWEEN :from AND :to")
    suspend fun preventiveCompletedOnTimeCount(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM maintenance_work_orders WHERE status = 'COMPLETED' AND completedAt BETWEEN :from AND :to")
    suspend fun closedWorkOrderCount(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM maintenance_work_orders WHERE status = 'COMPLETED' AND completedAt BETWEEN :from AND :to AND (dueAt IS NULL OR completedAt <= dueAt)")
    suspend fun closedOnTimeCount(from: Long, to: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBreakdown(row: BreakdownEntity): Long

    @Update
    suspend fun updateBreakdown(row: BreakdownEntity)

    @Query("SELECT * FROM breakdowns WHERE id = :id LIMIT 1")
    suspend fun breakdownById(id: Long): BreakdownEntity?

    @Query("SELECT * FROM breakdowns ORDER BY occurredAt DESC, id DESC")
    fun observeBreakdowns(): Flow<List<BreakdownEntity>>

    @Query("SELECT COUNT(*) FROM breakdowns WHERE assetId = :assetId AND occurredAt >= :from")
    suspend fun recentBreakdownCount(assetId: Long, from: Long): Int

    @Query("SELECT COALESCE(SUM(downtimeMinutes),0) FROM breakdowns WHERE occurredAt BETWEEN :from AND :to")
    suspend fun downtimeMinutes(from: Long, to: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAssetInspection(row: AssetInspectionEntity): Long

    @Query("SELECT * FROM asset_inspections WHERE assetId = :assetId AND inspectionType = :type ORDER BY inspectionDate DESC, id DESC LIMIT 1")
    suspend fun latestAssetInspection(assetId: Long, type: String): AssetInspectionEntity?

    @Query("SELECT * FROM asset_inspections ORDER BY inspectionDate DESC, id DESC LIMIT 100")
    fun observeAssetInspections(): Flow<List<AssetInspectionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCalibration(row: CalibrationRecordEntity): Long

    @Query("SELECT * FROM calibration_records WHERE assetId = :assetId ORDER BY checkedAt DESC, id DESC LIMIT 1")
    suspend fun latestCalibration(assetId: Long): CalibrationRecordEntity?

    @Query("SELECT * FROM calibration_records ORDER BY checkedAt DESC, id DESC LIMIT 100")
    fun observeCalibrations(): Flow<List<CalibrationRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSafetyIncident(row: SafetyIncidentEntity): Long

    @Update
    suspend fun updateSafetyIncident(row: SafetyIncidentEntity)

    @Query("SELECT * FROM safety_incidents WHERE id = :id LIMIT 1")
    suspend fun safetyIncidentById(id: Long): SafetyIncidentEntity?

    @Query("SELECT * FROM safety_incidents ORDER BY occurredAt DESC, id DESC")
    fun observeSafetyIncidents(): Flow<List<SafetyIncidentEntity>>

    @Query("SELECT COUNT(*) FROM safety_incidents WHERE status <> 'CLOSED'")
    fun observeOpenSafetyIncidentCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSafetyInspection(row: SafetyInspectionEntity): Long

    @Update
    suspend fun updateSafetyInspection(row: SafetyInspectionEntity)

    @Query("SELECT * FROM safety_inspections ORDER BY inspectionDate DESC, id DESC LIMIT 100")
    fun observeSafetyInspections(): Flow<List<SafetyInspectionEntity>>

    @Query("SELECT COUNT(*) FROM assets WHERE isActive = 1 AND ((inspectionDueAt IS NOT NULL AND inspectionDueAt < :now) OR (calibrationRequired = 1 AND calibrationDueAt IS NOT NULL AND calibrationDueAt < :now))")
    fun observeOverdueEquipmentCheckCount(now: Long): Flow<Int>
}
