package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.FixedAssetDepreciationEntity
import com.fush.erp.data.entity.FixedAssetDisposalEntity
import com.fush.erp.data.entity.FixedAssetEntity
import com.fush.erp.data.entity.FixedAssetRegisterRow
import kotlinx.coroutines.flow.Flow

@Dao
interface FixedAssetDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAsset(row: FixedAssetEntity): Long

    @Update
    suspend fun updateAsset(row: FixedAssetEntity)

    @Query("SELECT * FROM fixed_assets WHERE id = :id LIMIT 1")
    suspend fun assetById(id: Long): FixedAssetEntity?

    @Query("SELECT * FROM fixed_assets WHERE assetNo = :assetNo LIMIT 1")
    suspend fun assetByNo(assetNo: String): FixedAssetEntity?

    @Query("SELECT * FROM fixed_assets ORDER BY acquisitionDate DESC, id DESC")
    suspend fun allAssets(): List<FixedAssetEntity>

    @Query("SELECT * FROM fixed_assets WHERE status <> 'CANCELLED' AND inServiceDate <= :asOf AND (disposalDate IS NULL OR disposalDate >= :fromDate) ORDER BY id")
    suspend fun assetsRelevantToPeriod(fromDate: Long, asOf: Long): List<FixedAssetEntity>

    @Query("""
        SELECT fa.id AS id, fa.assetNo AS assetNo, fa.maintenanceAssetId AS maintenanceAssetId,
               fa.nameAr AS nameAr, fa.category AS category,
               fa.acquisitionDate AS acquisitionDate, fa.inServiceDate AS inServiceDate,
               fa.acquisitionCostBase AS acquisitionCostBase, fa.residualValueBase AS residualValueBase,
               fa.usefulLifeMonths AS usefulLifeMonths, fa.depreciationMethod AS depreciationMethod,
               fa.status AS status,
               COALESCE((SELECT SUM(d.amountBase) FROM fixed_asset_depreciations d WHERE d.assetId = fa.id AND d.status = 'POSTED'), 0) AS accumulatedDepreciationBase,
               fa.acquisitionCostBase - COALESCE((SELECT SUM(d.amountBase) FROM fixed_asset_depreciations d WHERE d.assetId = fa.id AND d.status = 'POSTED'), 0) AS netBookValueBase,
               fa.disposalDate AS disposalDate, fa.disposalProceedsBase AS disposalProceedsBase,
               fa.disposalGainLossBase AS disposalGainLossBase
        FROM fixed_assets fa
        ORDER BY fa.acquisitionDate DESC, fa.id DESC
    """)
    fun observeRegister(): Flow<List<FixedAssetRegisterRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDepreciation(row: FixedAssetDepreciationEntity): Long

    @Update
    suspend fun updateDepreciation(row: FixedAssetDepreciationEntity)

    @Query("SELECT * FROM fixed_asset_depreciations WHERE id = :id LIMIT 1")
    suspend fun depreciationById(id: Long): FixedAssetDepreciationEntity?

    @Query("SELECT * FROM fixed_asset_depreciations WHERE assetId = :assetId AND fiscalYear = :fiscalYear AND periodNo = :periodNo AND status = 'POSTED' ORDER BY id DESC LIMIT 1")
    suspend fun postedDepreciationForPeriod(assetId: Long, fiscalYear: Int, periodNo: Int): FixedAssetDepreciationEntity?

    @Query("SELECT COALESCE(SUM(amountBase),0) FROM fixed_asset_depreciations WHERE assetId = :assetId AND status = 'POSTED' AND depreciationDate <= :asOf")
    suspend fun accumulatedDepreciation(assetId: Long, asOf: Long): Double

    @Query("SELECT COUNT(*) FROM fixed_asset_depreciations WHERE assetId = :assetId AND status = 'POSTED' AND depreciationDate > :afterDate")
    suspend fun laterPostedDepreciationCount(assetId: Long, afterDate: Long): Int

    @Query("SELECT * FROM fixed_asset_depreciations WHERE assetId = :assetId ORDER BY depreciationDate DESC, id DESC")
    fun observeDepreciations(assetId: Long): Flow<List<FixedAssetDepreciationEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDisposal(row: FixedAssetDisposalEntity): Long

    @Update
    suspend fun updateDisposal(row: FixedAssetDisposalEntity)

    @Query("SELECT * FROM fixed_asset_disposals WHERE id = :id LIMIT 1")
    suspend fun disposalById(id: Long): FixedAssetDisposalEntity?

    @Query("SELECT * FROM fixed_asset_disposals WHERE assetId = :assetId AND status = 'POSTED' ORDER BY id DESC LIMIT 1")
    suspend fun activeDisposal(assetId: Long): FixedAssetDisposalEntity?

    @Query("SELECT COUNT(*) FROM fixed_asset_depreciations WHERE assetId = :assetId AND status = 'POSTED'")
    suspend fun postedDepreciationCount(assetId: Long): Int

    @Query("""
        SELECT COALESCE(SUM(fa.acquisitionCostBase),0)
        FROM fixed_assets fa
        WHERE fa.acquisitionDate <= :asOf
          AND NOT (fa.status = 'CANCELLED' AND fa.cancelledAt IS NOT NULL AND fa.cancelledAt <= :asOf)
          AND NOT EXISTS (
              SELECT 1 FROM fixed_asset_disposals d
              WHERE d.assetId = fa.id AND d.disposalDate <= :asOf
                AND (d.status = 'POSTED' OR (d.status = 'REVERSED' AND d.reversedAt IS NOT NULL AND d.reversedAt > :asOf))
          )
    """)
    suspend fun fixedAssetCostSubledger(asOf: Long): Double

    @Query("""
        SELECT COALESCE(SUM(d.amountBase),0)
        FROM fixed_asset_depreciations d
        JOIN fixed_assets fa ON fa.id = d.assetId
        WHERE d.depreciationDate <= :asOf
          AND (d.status = 'POSTED' OR (d.status = 'REVERSED' AND d.reversedAt IS NOT NULL AND d.reversedAt > :asOf))
          AND fa.acquisitionDate <= :asOf
          AND NOT (fa.status = 'CANCELLED' AND fa.cancelledAt IS NOT NULL AND fa.cancelledAt <= :asOf)
          AND NOT EXISTS (
              SELECT 1 FROM fixed_asset_disposals x
              WHERE x.assetId = fa.id AND x.disposalDate <= :asOf
                AND (x.status = 'POSTED' OR (x.status = 'REVERSED' AND x.reversedAt IS NOT NULL AND x.reversedAt > :asOf))
          )
    """)
    suspend fun accumulatedDepreciationSubledger(asOf: Long): Double
}
