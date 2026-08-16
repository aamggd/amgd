package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AdvancedInventoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCount(row: InventoryCountEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCountLines(rows: List<InventoryCountLineEntity>)

    @Update
    suspend fun updateCount(row: InventoryCountEntity)

    @Update
    suspend fun updateCountLine(row: InventoryCountLineEntity)

    @Query("SELECT * FROM inventory_counts WHERE id = :id LIMIT 1")
    suspend fun countById(id: Long): InventoryCountEntity?

    @Query("SELECT * FROM inventory_count_lines WHERE countId = :countId ORDER BY itemId, expiryKey, lotKey")
    suspend fun countLines(countId: Long): List<InventoryCountLineEntity>

    @Query("SELECT * FROM inventory_count_lines WHERE id = :id LIMIT 1")
    suspend fun countLineById(id: Long): InventoryCountLineEntity?

    @Query("""
        SELECT sm.itemId AS itemId, sm.lotNo AS lotNo, sm.expiryDate AS expiryDate,
               COALESCE(SUM(sm.quantityBase), 0) AS quantityBase,
               COALESCE(SUM(sm.quantityBase * sm.unitCostBase), 0) AS inventoryValueBase
        FROM stock_movements sm
        WHERE sm.warehouseId = :warehouseId
        GROUP BY sm.itemId, sm.lotNo, sm.expiryDate
        HAVING ABS(COALESCE(SUM(sm.quantityBase), 0)) > 0.000000001
        ORDER BY sm.itemId, CASE WHEN sm.expiryDate IS NULL THEN 1 ELSE 0 END, sm.expiryDate, sm.lotNo
    """)
    suspend fun snapshot(warehouseId: Long): List<InventorySnapshotRow>

    @Query("""
        SELECT c.id AS id, c.countNo AS countNo, c.countDate AS countDate,
               w.nameAr AS warehouseName, c.status AS status,
               COUNT(l.id) AS lineCount,
               COALESCE(SUM(CASE WHEN ABS(l.varianceQtyBase) > 0.000000001 THEN 1 ELSE 0 END), 0) AS varianceLines,
               COALESCE(SUM(l.varianceQtyBase * l.unitCostBase), 0) AS varianceValueBase
        FROM inventory_counts c
        JOIN warehouses w ON w.id = c.warehouseId
        LEFT JOIN inventory_count_lines l ON l.countId = c.id
        GROUP BY c.id
        ORDER BY c.countDate DESC, c.id DESC
        LIMIT 100
    """)
    fun observeCounts(): Flow<List<InventoryCountSummaryRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLotControl(row: InventoryLotControlEntity): Long

    @Query("""
        SELECT * FROM inventory_lot_controls
        WHERE warehouseId = :warehouseId AND itemId = :itemId AND lotKey = :lotKey AND expiryKey = :expiryKey
        LIMIT 1
    """)
    suspend fun lotControl(warehouseId: Long, itemId: Long, lotKey: String, expiryKey: Long): InventoryLotControlEntity?

    @Query("SELECT COUNT(*) FROM inventory_lot_controls WHERE status IN ('QUARANTINE','BLOCKED','RETURNED')")
    fun observeControlledLotCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReorderPolicy(row: WarehouseReorderPolicyEntity): Long

    @Query("SELECT * FROM warehouse_reorder_policies WHERE warehouseId = :warehouseId AND itemId = :itemId LIMIT 1")
    suspend fun reorderPolicy(warehouseId: Long, itemId: Long): WarehouseReorderPolicyEntity?

    @Query("SELECT * FROM warehouse_reorder_policies WHERE id = :policyId LIMIT 1")
    suspend fun reorderPolicyById(policyId: Long): WarehouseReorderPolicyEntity?

    @Query("DELETE FROM warehouse_reorder_policies WHERE id = :policyId")
    suspend fun deleteReorderPolicy(policyId: Long)

    @Query("""
        SELECT p.id AS id, p.warehouseId AS warehouseId, w.code AS warehouseCode,
               w.nameAr AS warehouseName, p.itemId AS itemId, i.code AS itemCode,
               i.nameAr AS itemName, p.reorderLevel AS reorderLevel, u.nameAr AS baseUnitName
        FROM warehouse_reorder_policies p
        JOIN warehouses w ON w.id = p.warehouseId
        JOIN items i ON i.id = p.itemId
        JOIN units u ON u.id = i.baseUnitId
        WHERE w.isActive = 1 AND i.isActive = 1
        ORDER BY w.nameAr, i.nameAr
    """)
    fun observeReorderPolicies(): Flow<List<WarehouseReorderPolicyView>>

    @Query("""
        SELECT p.warehouseId AS warehouseId, w.code AS warehouseCode, w.nameAr AS warehouseName,
               i.id AS itemId, i.code AS code, i.nameAr AS nameAr,
               COALESCE(SUM(CASE
                   WHEN sm.id IS NOT NULL
                    AND (sm.expiryDate IS NULL OR sm.expiryDate >= :at)
                    AND COALESCE((SELECT c.status FROM inventory_lot_controls c
                                  WHERE c.warehouseId = p.warehouseId AND c.itemId = p.itemId
                                    AND c.lotKey = COALESCE(sm.lotNo, '')
                                    AND c.expiryKey = COALESCE(sm.expiryDate, -1)
                                  LIMIT 1), 'ACCEPTED') = 'ACCEPTED'
                   THEN sm.quantityBase ELSE 0 END), 0) AS quantityBase,
               p.reorderLevel AS reorderLevel, u.nameAr AS baseUnitName
        FROM warehouse_reorder_policies p
        JOIN warehouses w ON w.id = p.warehouseId
        JOIN items i ON i.id = p.itemId
        JOIN units u ON u.id = i.baseUnitId
        LEFT JOIN stock_movements sm ON sm.warehouseId = p.warehouseId
                                    AND sm.itemId = p.itemId
                                    AND sm.movementDate <= :at
        WHERE w.isActive = 1 AND i.isActive = 1
        GROUP BY p.warehouseId, w.code, w.nameAr, i.id, i.code, i.nameAr, p.reorderLevel, u.nameAr
        HAVING COALESCE(SUM(CASE
                   WHEN sm.id IS NOT NULL
                    AND (sm.expiryDate IS NULL OR sm.expiryDate >= :at)
                    AND COALESCE((SELECT c.status FROM inventory_lot_controls c
                                  WHERE c.warehouseId = p.warehouseId AND c.itemId = p.itemId
                                    AND c.lotKey = COALESCE(sm.lotNo, '')
                                    AND c.expiryKey = COALESCE(sm.expiryDate, -1)
                                  LIMIT 1), 'ACCEPTED') = 'ACCEPTED'
                   THEN sm.quantityBase ELSE 0 END), 0) <= p.reorderLevel
        ORDER BY (p.reorderLevel - COALESCE(SUM(CASE
                   WHEN sm.id IS NOT NULL
                    AND (sm.expiryDate IS NULL OR sm.expiryDate >= :at)
                    AND COALESCE((SELECT c.status FROM inventory_lot_controls c
                                  WHERE c.warehouseId = p.warehouseId AND c.itemId = p.itemId
                                    AND c.lotKey = COALESCE(sm.lotNo, '')
                                    AND c.expiryKey = COALESCE(sm.expiryDate, -1)
                                  LIMIT 1), 'ACCEPTED') = 'ACCEPTED'
                   THEN sm.quantityBase ELSE 0 END), 0)) DESC, w.nameAr, i.nameAr
    """)
    fun observeReorderAlerts(at: Long): Flow<List<InventoryAlertRow>>

    @Query("""
        SELECT w.id AS warehouseId, w.nameAr AS warehouseName,
               i.id AS itemId, i.code AS code, i.nameAr AS nameAr,
               sm.lotNo AS lotNo, sm.expiryDate AS expiryDate,
               COALESCE(SUM(sm.quantityBase), 0) AS quantityBase,
               COALESCE(SUM(sm.quantityBase * sm.unitCostBase), 0) AS inventoryValueBase,
               COALESCE((SELECT c.status FROM inventory_lot_controls c
                         WHERE c.warehouseId = sm.warehouseId AND c.itemId = sm.itemId
                           AND c.lotKey = COALESCE(sm.lotNo, '')
                           AND c.expiryKey = COALESCE(sm.expiryDate, -1)
                         ORDER BY c.changedAt DESC LIMIT 1), 'ACCEPTED') AS controlStatus
        FROM stock_movements sm
        JOIN warehouses w ON w.id = sm.warehouseId
        JOIN items i ON i.id = sm.itemId
        WHERE sm.expiryDate IS NOT NULL AND sm.expiryDate <= :untilDate
        GROUP BY sm.warehouseId, sm.itemId, sm.lotNo, sm.expiryDate
        HAVING COALESCE(SUM(sm.quantityBase), 0) > 0.000000001
        ORDER BY sm.expiryDate, i.nameAr
    """)
    fun observeExpiryAlerts(untilDate: Long): Flow<List<InventoryLotAlertRow>>

    @Query("""
        SELECT w.id AS warehouseId, w.nameAr AS warehouseName,
               i.id AS itemId, i.code AS code, i.nameAr AS nameAr,
               sm.lotNo AS lotNo, sm.expiryDate AS expiryDate,
               COALESCE(SUM(sm.quantityBase), 0) AS quantityBase,
               COALESCE(SUM(sm.quantityBase * sm.unitCostBase), 0) AS inventoryValueBase,
               COALESCE((SELECT c.status FROM inventory_lot_controls c
                         WHERE c.warehouseId = sm.warehouseId AND c.itemId = sm.itemId
                           AND c.lotKey = COALESCE(sm.lotNo, '')
                           AND c.expiryKey = COALESCE(sm.expiryDate, -1)
                         ORDER BY c.changedAt DESC LIMIT 1), 'ACCEPTED') AS controlStatus
        FROM stock_movements sm
        JOIN warehouses w ON w.id = sm.warehouseId
        JOIN items i ON i.id = sm.itemId
        GROUP BY sm.warehouseId, sm.itemId, sm.lotNo, sm.expiryDate
        HAVING COALESCE(SUM(sm.quantityBase), 0) > 0.000000001
        ORDER BY i.nameAr, sm.expiryDate, sm.lotNo
    """)
    fun observeLotBalances(): Flow<List<InventoryLotAlertRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransfer(row: WarehouseTransferEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransferLine(row: WarehouseTransferLineEntity): Long

    @Update
    suspend fun updateTransfer(row: WarehouseTransferEntity)

    @Update
    suspend fun updateTransferLine(row: WarehouseTransferLineEntity)

    @Query("DELETE FROM warehouse_transfer_lines WHERE id = :lineId")
    suspend fun deleteTransferLine(lineId: Long)

    @Query("SELECT * FROM warehouse_transfers WHERE id = :transferId LIMIT 1")
    suspend fun transferById(transferId: Long): WarehouseTransferEntity?

    @Query("SELECT * FROM warehouse_transfer_lines WHERE id = :lineId LIMIT 1")
    suspend fun transferLineById(lineId: Long): WarehouseTransferLineEntity?

    @Query("SELECT * FROM warehouse_transfer_lines WHERE transferId = :transferId ORDER BY id")
    suspend fun transferLines(transferId: Long): List<WarehouseTransferLineEntity>

    @Query("""
        SELECT l.id AS id, l.itemId AS itemId, i.code AS itemCode, i.nameAr AS itemName,
               l.quantityBase AS quantityBase, l.unitCostBase AS unitCostBase,
               l.lotNo AS lotNo, l.expiryDate AS expiryDate
        FROM warehouse_transfer_lines l
        JOIN items i ON i.id = l.itemId
        WHERE l.transferId = :transferId
        ORDER BY i.nameAr, l.expiryKey, l.lotKey
    """)
    suspend fun transferLineViews(transferId: Long): List<WarehouseTransferLineView>

    @Query("""
        SELECT t.id AS id, t.transferNo AS transferNo, t.transferDate AS transferDate,
               fw.nameAr AS fromWarehouseName, tw.nameAr AS toWarehouseName,
               t.status AS status, COUNT(l.id) AS lineCount,
               COALESCE(SUM(l.quantityBase), 0) AS totalQtyBase,
               COALESCE(SUM(l.quantityBase * l.unitCostBase), 0) AS totalValueBase
        FROM warehouse_transfers t
        JOIN warehouses fw ON fw.id = t.fromWarehouseId
        JOIN warehouses tw ON tw.id = t.toWarehouseId
        LEFT JOIN warehouse_transfer_lines l ON l.transferId = t.id
        GROUP BY t.id
        ORDER BY t.transferDate DESC, t.id DESC
        LIMIT 200
    """)
    fun observeTransfers(): Flow<List<WarehouseTransferSummaryRow>>

    @Query("""
        SELECT sm.id AS id, sm.movementDate AS movementDate, w.nameAr AS warehouseName,
               sm.itemId AS itemId, i.nameAr AS itemName, sm.movementType AS movementType,
               sm.quantityBase AS quantityBase, sm.unitCostBase AS unitCostBase,
               sm.lotNo AS lotNo, sm.expiryDate AS expiryDate,
               sm.referenceType AS referenceType, sm.referenceId AS referenceId
        FROM stock_movements sm
        JOIN warehouses w ON w.id = sm.warehouseId
        JOIN items i ON i.id = sm.itemId
        WHERE (:itemId IS NULL OR sm.itemId = :itemId)
        ORDER BY sm.movementDate DESC, sm.id DESC
        LIMIT 500
    """)
    fun observeMovements(itemId: Long?): Flow<List<InventoryMovementRow>>
}
