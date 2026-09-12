#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1]).resolve()

files = {
"app/src/main/java/com/fush/erp/data/entity/SupplierStockProvenanceEntities.kt": r'''package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "supplier_stock_sources",
    foreignKeys = [
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"]),
        ForeignKey(entity = WarehouseEntity::class, parentColumns = ["id"], childColumns = ["warehouseId"]),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"]),
        ForeignKey(entity = StockMovementEntity::class, parentColumns = ["id"], childColumns = ["sourceMovementId"])
    ],
    indices = [
        Index("supplierId"),
        Index("warehouseId"),
        Index("itemId"),
        Index(value = ["sourceMovementId"], unique = true),
        Index(value = ["sourceReferenceType", "sourceReferenceId"]),
        Index(value = ["warehouseId", "itemId", "lotNo", "expiryDate", "sourceDate"])
    ]
)
data class SupplierStockSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierId: Long?,
    val warehouseId: Long,
    val itemId: Long,
    val lotNo: String? = null,
    val expiryDate: Long? = null,
    val sourceDate: Long,
    val sourceType: String,
    val sourceMovementId: Long? = null,
    val sourceReferenceType: String,
    val sourceReferenceId: Long? = null,
    val originalQuantityBase: Double,
    val unitCostBase: Double,
    val attributionStatus: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "supplier_stock_allocations",
    foreignKeys = [
        ForeignKey(entity = SupplierStockSourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"]),
        ForeignKey(entity = StockMovementEntity::class, parentColumns = ["id"], childColumns = ["stockMovementId"])
    ],
    indices = [
        Index("sourceId"),
        Index("stockMovementId"),
        Index("reversesAllocationId"),
        Index(value = ["businessReferenceType", "businessReferenceId"])
    ]
)
data class SupplierStockAllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val stockMovementId: Long,
    val movementType: String,
    val businessReferenceType: String,
    val businessReferenceId: Long,
    val quantityBase: Double,
    val unitCostBase: Double,
    val inventoryValueDeltaBase: Double,
    val reversesAllocationId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class SupplierStockSourceBalanceRow(
    val sourceId: Long,
    val supplierId: Long?,
    val warehouseId: Long,
    val itemId: Long,
    val lotNo: String?,
    val expiryDate: Long?,
    val sourceDate: Long,
    val unitCostBase: Double,
    val attributionStatus: String,
    val availableQuantityBase: Double
)
''',
"app/src/main/java/com/fush/erp/data/dao/SupplierStockProvenanceDao.kt": r'''package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fush.erp.data.entity.SupplierStockAllocationEntity
import com.fush.erp.data.entity.SupplierStockSourceBalanceRow
import com.fush.erp.data.entity.SupplierStockSourceEntity

@Dao
interface SupplierStockProvenanceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSource(row: SupplierStockSourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllocation(row: SupplierStockAllocationEntity): Long

    @Query("SELECT * FROM supplier_stock_sources WHERE id = :id LIMIT 1")
    suspend fun sourceById(id: Long): SupplierStockSourceEntity?

    @Query("SELECT * FROM supplier_stock_sources WHERE sourceReferenceType = :type AND sourceReferenceId = :referenceId ORDER BY id LIMIT 1")
    suspend fun sourceByReference(type: String, referenceId: Long): SupplierStockSourceEntity?

    @Query("""
        SELECT s.id AS sourceId, s.supplierId AS supplierId, s.warehouseId AS warehouseId,
               s.itemId AS itemId, s.lotNo AS lotNo, s.expiryDate AS expiryDate,
               s.sourceDate AS sourceDate, s.unitCostBase AS unitCostBase,
               s.attributionStatus AS attributionStatus,
               s.originalQuantityBase + COALESCE(SUM(a.quantityBase), 0) AS availableQuantityBase
        FROM supplier_stock_sources s
        LEFT JOIN supplier_stock_allocations a ON a.sourceId = s.id
        WHERE s.warehouseId = :warehouseId AND s.itemId = :itemId
          AND TRIM(COALESCE(s.lotNo, '')) = TRIM(COALESCE(:lotNo, ''))
          AND COALESCE(s.expiryDate, -1) = COALESCE(:expiryDate, -1)
        GROUP BY s.id
        HAVING s.originalQuantityBase + COALESCE(SUM(a.quantityBase), 0) > 0.000000001
        ORDER BY s.sourceDate ASC, s.id ASC
    """)
    suspend fun availableSources(warehouseId: Long, itemId: Long, lotNo: String?, expiryDate: Long?): List<SupplierStockSourceBalanceRow>

    @Query("SELECT originalQuantityBase + COALESCE((SELECT SUM(quantityBase) FROM supplier_stock_allocations WHERE sourceId = :sourceId), 0) FROM supplier_stock_sources WHERE id = :sourceId")
    suspend fun availableQuantity(sourceId: Long): Double?

    @Query("SELECT * FROM supplier_stock_allocations WHERE businessReferenceType = :type AND businessReferenceId = :referenceId ORDER BY id")
    suspend fun allocationsForBusinessReference(type: String, referenceId: Long): List<SupplierStockAllocationEntity>

    @Query("SELECT COALESCE(SUM(quantityBase), 0) FROM supplier_stock_allocations WHERE reversesAllocationId = :allocationId")
    suspend fun restoredQuantityForAllocation(allocationId: Long): Double

    @Query("SELECT * FROM supplier_stock_allocations WHERE stockMovementId = :stockMovementId ORDER BY id")
    suspend fun allocationsForMovement(stockMovementId: Long): List<SupplierStockAllocationEntity>
}
''',
"app/src/main/java/com/fush/erp/domain/SupplierStockProvenancePlanner.kt": r'''package com.fush.erp.domain

import kotlin.math.min

data class SupplierStockSourceBalance(
    val sourceId: Long,
    val supplierId: Long?,
    val availableQuantityBase: Double,
    val unitCostBase: Double,
    val sourceDate: Long,
    val attributionStatus: String
)

data class SupplierStockTake(
    val sourceId: Long,
    val supplierId: Long?,
    val quantityBase: Double,
    val unitCostBase: Double,
    val attributionStatus: String,
    val reversesAllocationId: Long? = null
)

data class SupplierStockConsumePlan(
    val requestedQtyBase: Double,
    val takes: List<SupplierStockTake>,
    val shortageQtyBase: Double
) {
    val isComplete: Boolean get() = shortageQtyBase <= SupplierStockProvenancePlanner.EPS
}

data class SupplierStockRestorableAllocation(
    val allocationId: Long,
    val sourceId: Long,
    val supplierId: Long?,
    val restorableQuantityBase: Double,
    val unitCostBase: Double
)

object SupplierStockProvenancePlanner {
    const val EPS = 1e-9

    fun consume(requiredQtyBase: Double, sources: List<SupplierStockSourceBalance>): SupplierStockConsumePlan {
        require(requiredQtyBase >= -EPS) { "الكمية المطلوبة غير صالحة" }
        var remaining = requiredQtyBase.coerceAtLeast(0.0)
        val takes = mutableListOf<SupplierStockTake>()
        sources.sortedWith(compareBy<SupplierStockSourceBalance>({ it.sourceDate }, { it.sourceId })).forEach { source ->
            if (remaining <= EPS) return@forEach
            val qty = min(remaining, source.availableQuantityBase.coerceAtLeast(0.0))
            if (qty > EPS) {
                takes += SupplierStockTake(source.sourceId, source.supplierId, qty, source.unitCostBase, source.attributionStatus)
                remaining -= qty
            }
        }
        return SupplierStockConsumePlan(requiredQtyBase, takes, remaining.coerceAtLeast(0.0))
    }

    fun restore(requiredQtyBase: Double, originals: List<SupplierStockRestorableAllocation>): SupplierStockConsumePlan {
        require(requiredQtyBase >= -EPS) { "كمية الاسترجاع غير صالحة" }
        var remaining = requiredQtyBase.coerceAtLeast(0.0)
        val takes = mutableListOf<SupplierStockTake>()
        originals.forEach { original ->
            if (remaining <= EPS) return@forEach
            val qty = min(remaining, original.restorableQuantityBase.coerceAtLeast(0.0))
            if (qty > EPS) {
                takes += SupplierStockTake(
                    sourceId = original.sourceId,
                    supplierId = original.supplierId,
                    quantityBase = qty,
                    unitCostBase = original.unitCostBase,
                    attributionStatus = if (original.supplierId == null) "UNATTRIBUTED" else "EXACT",
                    reversesAllocationId = original.allocationId
                )
                remaining -= qty
            }
        }
        return SupplierStockConsumePlan(requiredQtyBase, takes, remaining.coerceAtLeast(0.0))
    }
}
''',
"app/src/main/java/com/fush/erp/domain/SupplierStockProvenanceService.kt": r'''package com.fush.erp.domain

import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.SupplierStockAllocationEntity
import com.fush.erp.data.entity.SupplierStockSourceEntity
import kotlin.math.abs

class SupplierStockProvenanceService(private val db: FushDatabase) {
    private val dao get() = db.supplierStockProvenanceDao()

    suspend fun recordPurchaseSource(
        stockMovementId: Long, supplierId: Long, purchaseLineId: Long, warehouseId: Long, itemId: Long,
        lotNo: String?, expiryDate: Long?, sourceDate: Long, quantityBase: Double, unitCostBase: Double
    ): Long {
        require(quantityBase > SupplierStockProvenancePlanner.EPS) { "كمية مصدر الشراء يجب أن تكون موجبة" }
        return dao.insertSource(
            SupplierStockSourceEntity(
                supplierId = supplierId, warehouseId = warehouseId, itemId = itemId, lotNo = lotNo,
                expiryDate = expiryDate, sourceDate = sourceDate, sourceType = "PURCHASE",
                sourceMovementId = stockMovementId, sourceReferenceType = "PURCHASE_LINE", sourceReferenceId = purchaseLineId,
                originalQuantityBase = quantityBase, unitCostBase = unitCostBase, attributionStatus = "EXACT"
            )
        )
    }

    suspend fun consumePurchaseReturn(
        stockMovementId: Long, purchaseLineId: Long, purchaseReturnLineId: Long, quantityBase: Double, unitCostBase: Double
    ) {
        val source = requireNotNull(dao.sourceByReference("PURCHASE_LINE", purchaseLineId)) { "مصدر المورد لسطر الشراء غير موجود" }
        val available = dao.availableQuantity(source.id) ?: 0.0
        require(available + SupplierStockProvenancePlanner.EPS >= quantityBase) {
            "كمية مرتجع الشراء تتجاوز الكمية المتبقية من مصدر المورد الأصلي"
        }
        dao.insertAllocation(
            SupplierStockAllocationEntity(
                sourceId = source.id, stockMovementId = stockMovementId, movementType = "PURCHASE_RETURN",
                businessReferenceType = "PURCHASE_RETURN_LINE", businessReferenceId = purchaseReturnLineId,
                quantityBase = -quantityBase, unitCostBase = unitCostBase,
                inventoryValueDeltaBase = -quantityBase * unitCostBase
            )
        )
    }

    suspend fun allocateSale(
        stockMovementId: Long, salesAllocationId: Long, warehouseId: Long, itemId: Long, lotNo: String?, expiryDate: Long?,
        movementDate: Long, quantityBase: Double, movementUnitCostBase: Double
    ) {
        val available = dao.availableSources(warehouseId, itemId, lotNo, expiryDate).map {
            SupplierStockSourceBalance(it.sourceId, it.supplierId, it.availableQuantityBase, it.unitCostBase, it.sourceDate, it.attributionStatus)
        }
        var plan = SupplierStockProvenancePlanner.consume(quantityBase, available)
        if (!plan.isComplete) {
            val fallbackId = dao.insertSource(
                SupplierStockSourceEntity(
                    supplierId = null, warehouseId = warehouseId, itemId = itemId, lotNo = lotNo, expiryDate = expiryDate,
                    sourceDate = movementDate, sourceType = "UNATTRIBUTED_INFLOW", sourceMovementId = null,
                    sourceReferenceType = "PROVENANCE_GAP", sourceReferenceId = stockMovementId,
                    originalQuantityBase = plan.shortageQtyBase, unitCostBase = movementUnitCostBase, attributionStatus = "UNATTRIBUTED"
                )
            )
            val fallback = SupplierStockSourceBalance(fallbackId, null, plan.shortageQtyBase, movementUnitCostBase, movementDate, "UNATTRIBUTED")
            val fallbackPlan = SupplierStockProvenancePlanner.consume(plan.shortageQtyBase, listOf(fallback))
            plan = SupplierStockConsumePlan(plan.requestedQtyBase, plan.takes + fallbackPlan.takes, fallbackPlan.shortageQtyBase)
        }
        require(plan.isComplete) { "تعذر تخصيص كامل كمية البيع إلى مصادر المخزون" }
        plan.takes.forEach { take ->
            dao.insertAllocation(
                SupplierStockAllocationEntity(
                    sourceId = take.sourceId, stockMovementId = stockMovementId, movementType = "SALE",
                    businessReferenceType = "SALES_ALLOCATION", businessReferenceId = salesAllocationId,
                    quantityBase = -take.quantityBase, unitCostBase = movementUnitCostBase,
                    inventoryValueDeltaBase = -take.quantityBase * movementUnitCostBase
                )
            )
        }
        val allocated = dao.allocationsForMovement(stockMovementId).sumOf { abs(it.quantityBase) }
        require(abs(allocated - quantityBase) <= 0.000001) { "تخصيص مصدر المورد لا يساوي كمية حركة البيع" }
    }

    suspend fun restoreSalesReturn(
        stockMovementId: Long, salesReturnAllocationId: Long, originalSalesAllocationId: Long, quantityBase: Double
    ) {
        val originals = dao.allocationsForBusinessReference("SALES_ALLOCATION", originalSalesAllocationId)
            .filter { it.quantityBase < -SupplierStockProvenancePlanner.EPS }
            .map { row ->
                val source = requireNotNull(dao.sourceById(row.sourceId)) { "مصدر المورد الأصلي غير موجود" }
                val restored = dao.restoredQuantityForAllocation(row.id).coerceAtLeast(0.0)
                SupplierStockRestorableAllocation(
                    allocationId = row.id, sourceId = row.sourceId, supplierId = source.supplierId,
                    restorableQuantityBase = (-row.quantityBase - restored).coerceAtLeast(0.0), unitCostBase = row.unitCostBase
                )
            }
        val plan = SupplierStockProvenancePlanner.restore(quantityBase, originals)
        require(plan.isComplete) { "كمية مرتجع البيع تتجاوز تخصيص مصدر المورد الأصلي" }
        plan.takes.forEach { take ->
            dao.insertAllocation(
                SupplierStockAllocationEntity(
                    sourceId = take.sourceId, stockMovementId = stockMovementId, movementType = "SALES_RETURN",
                    businessReferenceType = "SALES_RETURN_ALLOCATION", businessReferenceId = salesReturnAllocationId,
                    quantityBase = take.quantityBase, unitCostBase = take.unitCostBase,
                    inventoryValueDeltaBase = take.quantityBase * take.unitCostBase,
                    reversesAllocationId = take.reversesAllocationId
                )
            )
        }
    }
}
''',
"app/src/main/java/com/fush/erp/data/SupplierStockProvenanceMigration.kt": r'''package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal const val SQL_CREATE_SUPPLIER_STOCK_SOURCES = """
CREATE TABLE IF NOT EXISTS `supplier_stock_sources` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `supplierId` INTEGER, `warehouseId` INTEGER NOT NULL, `itemId` INTEGER NOT NULL, `lotNo` TEXT, `expiryDate` INTEGER, `sourceDate` INTEGER NOT NULL, `sourceType` TEXT NOT NULL, `sourceMovementId` INTEGER, `sourceReferenceType` TEXT NOT NULL, `sourceReferenceId` INTEGER, `originalQuantityBase` REAL NOT NULL, `unitCostBase` REAL NOT NULL, `attributionStatus` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`supplierId`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`warehouseId`) REFERENCES `warehouses`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`sourceMovementId`) REFERENCES `stock_movements`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )
"""
internal const val SQL_CREATE_SUPPLIER_STOCK_ALLOCATIONS = """
CREATE TABLE IF NOT EXISTS `supplier_stock_allocations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceId` INTEGER NOT NULL, `stockMovementId` INTEGER NOT NULL, `movementType` TEXT NOT NULL, `businessReferenceType` TEXT NOT NULL, `businessReferenceId` INTEGER NOT NULL, `quantityBase` REAL NOT NULL, `unitCostBase` REAL NOT NULL, `inventoryValueDeltaBase` REAL NOT NULL, `reversesAllocationId` INTEGER, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`sourceId`) REFERENCES `supplier_stock_sources`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , FOREIGN KEY(`stockMovementId`) REFERENCES `stock_movements`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )
"""
internal const val SQL_INDEX_SOURCES_SUPPLIER = "CREATE INDEX IF NOT EXISTS `index_supplier_stock_sources_supplierId` ON `supplier_stock_sources` (`supplierId`)"
internal const val SQL_INDEX_SOURCES_WAREHOUSE = "CREATE INDEX IF NOT EXISTS `index_supplier_stock_sources_warehouseId` ON `supplier_stock_sources` (`warehouseId`)"
internal const val SQL_INDEX_SOURCES_ITEM = "CREATE INDEX IF NOT EXISTS `index_supplier_stock_sources_itemId` ON `supplier_stock_sources` (`itemId`)"
internal const val SQL_INDEX_SOURCES_MOVEMENT = "CREATE UNIQUE INDEX IF NOT EXISTS `index_supplier_stock_sources_sourceMovementId` ON `supplier_stock_sources` (`sourceMovementId`)"
internal const val SQL_INDEX_SOURCES_REFERENCE = "CREATE INDEX IF NOT EXISTS `index_supplier_stock_sources_sourceReferenceType_sourceReferenceId` ON `supplier_stock_sources` (`sourceReferenceType`, `sourceReferenceId`)"
internal const val SQL_INDEX_SOURCES_LOT = "CREATE INDEX IF NOT EXISTS `index_supplier_stock_sources_warehouseId_itemId_lotNo_expiryDate_sourceDate` ON `supplier_stock_sources` (`warehouseId`, `itemId`, `lotNo`, `expiryDate`, `sourceDate`)"
internal const val SQL_INDEX_ALLOC_SOURCE = "CREATE INDEX IF NOT EXISTS `index_supplier_stock_allocations_sourceId` ON `supplier_stock_allocations` (`sourceId`)"
internal const val SQL_INDEX_ALLOC_MOVEMENT = "CREATE INDEX IF NOT EXISTS `index_supplier_stock_allocations_stockMovementId` ON `supplier_stock_allocations` (`stockMovementId`)"
internal const val SQL_INDEX_ALLOC_REVERSES = "CREATE INDEX IF NOT EXISTS `index_supplier_stock_allocations_reversesAllocationId` ON `supplier_stock_allocations` (`reversesAllocationId`)"
internal const val SQL_INDEX_ALLOC_REFERENCE = "CREATE INDEX IF NOT EXISTS `index_supplier_stock_allocations_businessReferenceType_businessReferenceId` ON `supplier_stock_allocations` (`businessReferenceType`, `businessReferenceId`)"
internal const val SQL_BACKFILL_LEGACY_SOURCES = """
INSERT INTO supplier_stock_sources (supplierId, warehouseId, itemId, lotNo, expiryDate, sourceDate, sourceType, sourceMovementId, sourceReferenceType, sourceReferenceId, originalQuantityBase, unitCostBase, attributionStatus, createdAt)
SELECT NULL, warehouseId, itemId, lotNo, expiryDate, 0, 'LEGACY_OPENING', NULL, 'MIGRATION_52_53', NULL,
       SUM(quantityBase),
       CASE WHEN ABS(SUM(quantityBase)) > 0.000000001 THEN SUM(quantityBase * unitCostBase) / SUM(quantityBase) ELSE 0 END,
       'UNATTRIBUTED', 0
FROM stock_movements
GROUP BY warehouseId, itemId, lotNo, expiryDate
HAVING SUM(quantityBase) > 0.000000001
"""
internal const val SQL_SOURCE_NO_UPDATE = "CREATE TRIGGER IF NOT EXISTS trg_supplier_stock_sources_no_update BEFORE UPDATE ON supplier_stock_sources BEGIN SELECT RAISE(ABORT, 'supplier stock sources are immutable'); END"
internal const val SQL_SOURCE_NO_DELETE = "CREATE TRIGGER IF NOT EXISTS trg_supplier_stock_sources_no_delete BEFORE DELETE ON supplier_stock_sources BEGIN SELECT RAISE(ABORT, 'supplier stock sources are immutable'); END"
internal const val SQL_ALLOC_NO_UPDATE = "CREATE TRIGGER IF NOT EXISTS trg_supplier_stock_allocations_no_update BEFORE UPDATE ON supplier_stock_allocations BEGIN SELECT RAISE(ABORT, 'supplier stock allocations are immutable'); END"
internal const val SQL_ALLOC_NO_DELETE = "CREATE TRIGGER IF NOT EXISTS trg_supplier_stock_allocations_no_delete BEFORE DELETE ON supplier_stock_allocations BEGIN SELECT RAISE(ABORT, 'supplier stock allocations are immutable'); END"

private val SUPPLIER_PROVENANCE_52_53_SQL = listOf(
    SQL_CREATE_SUPPLIER_STOCK_SOURCES, SQL_CREATE_SUPPLIER_STOCK_ALLOCATIONS,
    SQL_INDEX_SOURCES_SUPPLIER, SQL_INDEX_SOURCES_WAREHOUSE, SQL_INDEX_SOURCES_ITEM, SQL_INDEX_SOURCES_MOVEMENT,
    SQL_INDEX_SOURCES_REFERENCE, SQL_INDEX_SOURCES_LOT, SQL_INDEX_ALLOC_SOURCE, SQL_INDEX_ALLOC_MOVEMENT,
    SQL_INDEX_ALLOC_REVERSES, SQL_INDEX_ALLOC_REFERENCE, SQL_BACKFILL_LEGACY_SOURCES,
    SQL_SOURCE_NO_UPDATE, SQL_SOURCE_NO_DELETE, SQL_ALLOC_NO_UPDATE, SQL_ALLOC_NO_DELETE
)

val MIGRATION_52_53_SUPPLIER_PROVENANCE = object : Migration(52, 53) {
    override fun migrate(db: SupportSQLiteDatabase) {
        SUPPLIER_PROVENANCE_52_53_SQL.forEach(db::execSQL)
    }
}
'''
}

for rel, content in files.items():
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")

# Database schema and DAO registration.
p = root / "app/src/main/java/com/fush/erp/data/FushDatabase.kt"
s = p.read_text(encoding="utf-8")
s = s.replace("const val FUSH_DB_SCHEMA_VERSION = 52", "const val FUSH_DB_SCHEMA_VERSION = 53")
anchor = "        SupplierVariantPriceEntity::class\n"
if anchor not in s:
    raise SystemExit("FushDatabase entity anchor missing")
s = s.replace(anchor, "        SupplierVariantPriceEntity::class,\n        SupplierStockSourceEntity::class,\n        SupplierStockAllocationEntity::class\n", 1)
anchor = "    abstract fun productMasterDao(): ProductMasterDao\n"
if anchor not in s:
    raise SystemExit("FushDatabase DAO anchor missing")
s = s.replace(anchor, anchor + "    abstract fun supplierStockProvenanceDao(): SupplierStockProvenanceDao\n", 1)
p.write_text(s, encoding="utf-8")

# Register migration in every Room builder that already contains 51->52.
changed_builders = 0
for p in (root / "app/src/main/java").rglob("*.kt"):
    s = p.read_text(encoding="utf-8")
    if "MIGRATION_51_52_PRODUCT_MASTER" in s and ".addMigrations" in s and "MIGRATION_52_53_SUPPLIER_PROVENANCE" not in s:
        old = "MIGRATION_51_52_PRODUCT_MASTER).build()"
        if old in s:
            s = s.replace(old, "MIGRATION_51_52_PRODUCT_MASTER, MIGRATION_52_53_SUPPLIER_PROVENANCE).build()")
            p.write_text(s, encoding="utf-8")
            changed_builders += 1
if changed_builders < 1:
    raise SystemExit("No Room builder migration anchor patched")

# Purchase hooks.
p = root / "app/src/main/java/com/fush/erp/domain/PurchaseService.kt"
s = p.read_text(encoding="utf-8")
anchor = "    private val numbering = AutoNumberService(db)\n"
if anchor not in s:
    raise SystemExit("PurchaseService numbering anchor missing")
s = s.replace(anchor, anchor + "    private val supplierStockProvenance = SupplierStockProvenanceService(db)\n", 1)
old = '''            db.stockDao().insertMovement(\n                StockMovementEntity(\n                    movementDate = request.invoiceDate,\n                    warehouseId = request.warehouseId,\n                    itemId = line.itemId,\n                    movementType = "PURCHASE",\n                    quantityBase = line.baseQuantity,\n                    unitCostBase = unitCostBase,\n                    referenceType = "PURCHASE_LINE",\n                    referenceId = lineId,\n                    lotNo = line.lotNo,\n                    expiryDate = line.expiryDate\n                )\n            )'''
new = '''            val stockMovementId = db.stockDao().insertMovement(\n                StockMovementEntity(\n                    movementDate = request.invoiceDate,\n                    warehouseId = request.warehouseId,\n                    itemId = line.itemId,\n                    movementType = "PURCHASE",\n                    quantityBase = line.baseQuantity,\n                    unitCostBase = unitCostBase,\n                    referenceType = "PURCHASE_LINE",\n                    referenceId = lineId,\n                    lotNo = line.lotNo,\n                    expiryDate = line.expiryDate\n                )\n            )\n            supplierStockProvenance.recordPurchaseSource(\n                stockMovementId = stockMovementId, supplierId = supplier.id, purchaseLineId = lineId,\n                warehouseId = request.warehouseId, itemId = line.itemId, lotNo = line.lotNo, expiryDate = line.expiryDate,\n                sourceDate = request.invoiceDate, quantityBase = line.baseQuantity, unitCostBase = unitCostBase\n            )'''
if old not in s:
    raise SystemExit("Purchase movement anchor missing")
s = s.replace(old, new, 1)
old = "            db.purchaseDao().insertReturnLine(\n                PurchaseReturnLineEntity("
if old not in s:
    raise SystemExit("Purchase return line anchor missing")
s = s.replace(old, "            val purchaseReturnLineId = db.purchaseDao().insertReturnLine(\n                PurchaseReturnLineEntity(", 1)
old = '''            db.stockDao().insertMovement(\n                StockMovementEntity(\n                    movementDate = request.returnDate,\n                    warehouseId = invoice.warehouseId,\n                    itemId = line.itemId,\n                    movementType = "PURCHASE_RETURN",\n                    quantityBase = -preparedLine.baseQuantity,\n                    unitCostBase = line.unitCostBase,\n                    referenceType = "PURCHASE_RETURN",\n                    referenceId = returnId,\n                    lotNo = line.lotNo,\n                    expiryDate = line.expiryDate\n                )\n            )'''
new = '''            val stockMovementId = db.stockDao().insertMovement(\n                StockMovementEntity(\n                    movementDate = request.returnDate,\n                    warehouseId = invoice.warehouseId,\n                    itemId = line.itemId,\n                    movementType = "PURCHASE_RETURN",\n                    quantityBase = -preparedLine.baseQuantity,\n                    unitCostBase = line.unitCostBase,\n                    referenceType = "PURCHASE_RETURN",\n                    referenceId = returnId,\n                    lotNo = line.lotNo,\n                    expiryDate = line.expiryDate\n                )\n            )\n            supplierStockProvenance.consumePurchaseReturn(\n                stockMovementId = stockMovementId, purchaseLineId = line.id, purchaseReturnLineId = purchaseReturnLineId,\n                quantityBase = preparedLine.baseQuantity, unitCostBase = line.unitCostBase\n            )'''
if old not in s:
    raise SystemExit("Purchase return movement anchor missing")
s = s.replace(old, new, 1)
p.write_text(s, encoding="utf-8")

# Sales hooks.
p = root / "app/src/main/java/com/fush/erp/domain/SalesService.kt"
s = p.read_text(encoding="utf-8")
anchor = "    private val numbering = AutoNumberService(db)\n"
if anchor not in s:
    raise SystemExit("SalesService numbering anchor missing")
s = s.replace(anchor, anchor + "    private val supplierStockProvenance = SupplierStockProvenanceService(db)\n", 1)
old = "            db.salesDao().insertAllocation(\n                SalesAllocationEntity("
if old not in s:
    raise SystemExit("Sales allocation anchor missing")
s = s.replace(old, "            val salesAllocationId = db.salesDao().insertAllocation(\n                SalesAllocationEntity(", 1)
old = '''            db.stockDao().insertMovement(\n                StockMovementEntity(\n                    movementDate = movementDate,\n                    warehouseId = warehouseId,\n                    itemId = itemId,\n                    movementType = "SALE",\n                    quantityBase = -take.quantityBase,\n                    unitCostBase = p.unitCost,\n                    referenceType = "SALES_LINE",\n                    referenceId = salesLineId,\n                    lotNo = lot.lotNo,\n                    expiryDate = lot.expiryDate\n                )\n            )'''
new = '''            val stockMovementId = db.stockDao().insertMovement(\n                StockMovementEntity(\n                    movementDate = movementDate,\n                    warehouseId = warehouseId,\n                    itemId = itemId,\n                    movementType = "SALE",\n                    quantityBase = -take.quantityBase,\n                    unitCostBase = p.unitCost,\n                    referenceType = "SALES_LINE",\n                    referenceId = salesLineId,\n                    lotNo = lot.lotNo,\n                    expiryDate = lot.expiryDate\n                )\n            )\n            supplierStockProvenance.allocateSale(\n                stockMovementId = stockMovementId, salesAllocationId = salesAllocationId, warehouseId = warehouseId,\n                itemId = itemId, lotNo = lot.lotNo, expiryDate = lot.expiryDate, movementDate = movementDate,\n                quantityBase = take.quantityBase, movementUnitCostBase = p.unitCost\n            )'''
if old not in s:
    raise SystemExit("Sale movement anchor missing")
s = s.replace(old, new, 1)
old = "            db.salesDao().insertReturnAllocation(\n                SalesReturnAllocationEntity("
if old not in s:
    raise SystemExit("Sales return allocation anchor missing")
s = s.replace(old, "            val salesReturnAllocationId = db.salesDao().insertReturnAllocation(\n                SalesReturnAllocationEntity(", 1)
old = '''            db.stockDao().insertMovement(\n                StockMovementEntity(\n                    movementDate = returnDate,\n                    warehouseId = invoice.warehouseId,\n                    itemId = allocation.itemId,\n                    movementType = "SALES_RETURN",\n                    quantityBase = qtyBase,\n                    unitCostBase = allocation.unitCostBase,\n                    referenceType = "SALES_RETURN",\n                    referenceId = returnId,\n                    lotNo = allocation.lotNo,\n                    expiryDate = allocation.expiryDate\n                )\n            )'''
new = '''            val stockMovementId = db.stockDao().insertMovement(\n                StockMovementEntity(\n                    movementDate = returnDate,\n                    warehouseId = invoice.warehouseId,\n                    itemId = allocation.itemId,\n                    movementType = "SALES_RETURN",\n                    quantityBase = qtyBase,\n                    unitCostBase = allocation.unitCostBase,\n                    referenceType = "SALES_RETURN",\n                    referenceId = returnId,\n                    lotNo = allocation.lotNo,\n                    expiryDate = allocation.expiryDate\n                )\n            )\n            supplierStockProvenance.restoreSalesReturn(\n                stockMovementId = stockMovementId, salesReturnAllocationId = salesReturnAllocationId,\n                originalSalesAllocationId = allocation.id, quantityBase = qtyBase\n            )'''
if old not in s:
    raise SystemExit("Sales return movement anchor missing")
s = s.replace(old, new, 1)
p.write_text(s, encoding="utf-8")

print(f"GATE1B_PATCH_APPLIED=PASS files={len(files)} room_builders={changed_builders}")
