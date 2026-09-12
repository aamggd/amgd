#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])

def write(rel: str, text: str):
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')

write('app/src/main/java/com/fush/erp/data/entity/SupplierItemMovementReportEntities.kt', r'''package com.fush.erp.data.entity

data class SupplierItemMovementReportRow(
    val eventDate: Long,
    val sourceId: Long,
    val supplierId: Long?,
    val supplierCode: String?,
    val supplierName: String,
    val itemId: Long,
    val code: String,
    val itemName: String,
    val baseUnitName: String,
    val warehouseId: Long,
    val warehouseName: String,
    val lotNo: String?,
    val expiryDate: Long?,
    val movementType: String,
    val referenceType: String,
    val referenceId: Long?,
    val documentNo: String,
    val quantityInBase: Double,
    val quantityOutBase: Double,
    val netQuantityBase: Double,
    val unitCostBase: Double,
    val inventoryValueDeltaBase: Double,
    val attributionStatus: String
)

data class SupplierItemMovementSummaryReportRow(
    val supplierId: Long?,
    val supplierCode: String?,
    val supplierName: String,
    val itemId: Long,
    val code: String,
    val itemName: String,
    val baseUnitName: String,
    val openingQtyBase: Double,
    val purchasesQtyBase: Double,
    val purchaseReturnsQtyBase: Double,
    val salesQtyBase: Double,
    val salesReturnsQtyBase: Double,
    val otherNetQtyBase: Double,
    val closingQtyBase: Double,
    val closingValueBase: Double,
    val attributionStatus: String
)
''')

write('app/src/main/java/com/fush/erp/data/dao/SupplierItemMovementReportSql.kt', r'''package com.fush.erp.data.dao

internal const val SQL_SUPPLIER_ITEM_MOVEMENT_DETAILS = """
WITH movement_events AS (
    SELECT s.sourceDate AS eventDate,
           s.id AS sourceId,
           s.supplierId AS supplierId,
           s.warehouseId AS warehouseId,
           s.itemId AS itemId,
           s.lotNo AS lotNo,
           s.expiryDate AS expiryDate,
           s.sourceType AS movementType,
           COALESCE(sm.referenceType, s.sourceReferenceType) AS referenceType,
           COALESCE(sm.referenceId, s.sourceReferenceId) AS referenceId,
           s.originalQuantityBase AS netQuantityBase,
           s.unitCostBase AS unitCostBase,
           s.originalQuantityBase * s.unitCostBase AS inventoryValueDeltaBase,
           s.attributionStatus AS attributionStatus
    FROM supplier_stock_sources s
    LEFT JOIN stock_movements sm ON sm.id = s.sourceMovementId

    UNION ALL

    SELECT sm.movementDate AS eventDate,
           a.sourceId AS sourceId,
           s.supplierId AS supplierId,
           sm.warehouseId AS warehouseId,
           sm.itemId AS itemId,
           sm.lotNo AS lotNo,
           sm.expiryDate AS expiryDate,
           a.movementType AS movementType,
           sm.referenceType AS referenceType,
           sm.referenceId AS referenceId,
           a.quantityBase AS netQuantityBase,
           a.unitCostBase AS unitCostBase,
           a.inventoryValueDeltaBase AS inventoryValueDeltaBase,
           s.attributionStatus AS attributionStatus
    FROM supplier_stock_allocations a
    JOIN supplier_stock_sources s ON s.id = a.sourceId
    JOIN stock_movements sm ON sm.id = a.stockMovementId
)
SELECT e.eventDate AS eventDate,
       e.sourceId AS sourceId,
       e.supplierId AS supplierId,
       sp.code AS supplierCode,
       COALESCE(sp.nameAr, 'غير منسوب') AS supplierName,
       e.itemId AS itemId,
       i.code AS code,
       i.nameAr AS itemName,
       u.nameAr AS baseUnitName,
       e.warehouseId AS warehouseId,
       w.nameAr AS warehouseName,
       e.lotNo AS lotNo,
       e.expiryDate AS expiryDate,
       e.movementType AS movementType,
       e.referenceType AS referenceType,
       e.referenceId AS referenceId,
       CASE e.movementType
           WHEN 'PURCHASE' THEN COALESCE((
               SELECT pi.invoiceNo FROM purchase_lines pl
               JOIN purchase_invoices pi ON pi.id = pl.invoiceId
               WHERE pl.id = e.referenceId LIMIT 1
           ), '')
           WHEN 'PURCHASE_RETURN' THEN COALESCE((
               SELECT pr.returnNo FROM purchase_returns pr WHERE pr.id = e.referenceId LIMIT 1
           ), '')
           WHEN 'SALE' THEN COALESCE((
               SELECT si.invoiceNo FROM sales_lines sl
               JOIN sales_invoices si ON si.id = sl.invoiceId
               WHERE sl.id = e.referenceId LIMIT 1
           ), '')
           WHEN 'SALES_RETURN' THEN COALESCE((
               SELECT sr.returnNo FROM sales_returns sr WHERE sr.id = e.referenceId LIMIT 1
           ), '')
           ELSE ''
       END AS documentNo,
       CASE WHEN e.netQuantityBase > 0 THEN e.netQuantityBase ELSE 0.0 END AS quantityInBase,
       CASE WHEN e.netQuantityBase < 0 THEN -e.netQuantityBase ELSE 0.0 END AS quantityOutBase,
       e.netQuantityBase AS netQuantityBase,
       e.unitCostBase AS unitCostBase,
       e.inventoryValueDeltaBase AS inventoryValueDeltaBase,
       e.attributionStatus AS attributionStatus
FROM movement_events e
JOIN items i ON i.id = e.itemId
JOIN units u ON u.id = i.baseUnitId
JOIN warehouses w ON w.id = e.warehouseId
LEFT JOIN suppliers sp ON sp.id = e.supplierId
WHERE e.eventDate BETWEEN :from AND :to
  AND (:supplierId IS NULL OR e.supplierId = :supplierId)
  AND (:itemId IS NULL OR e.itemId = :itemId)
  AND (:warehouseId IS NULL OR e.warehouseId = :warehouseId)
ORDER BY e.eventDate ASC, e.sourceId ASC, e.movementType ASC, e.referenceId ASC
"""

internal const val SQL_SUPPLIER_ITEM_MOVEMENT_SUMMARIES = """
WITH movement_events AS (
    SELECT s.sourceDate AS eventDate,
           s.supplierId AS supplierId,
           s.warehouseId AS warehouseId,
           s.itemId AS itemId,
           s.sourceType AS movementType,
           s.originalQuantityBase AS netQuantityBase,
           s.originalQuantityBase * s.unitCostBase AS inventoryValueDeltaBase
    FROM supplier_stock_sources s

    UNION ALL

    SELECT sm.movementDate AS eventDate,
           s.supplierId AS supplierId,
           sm.warehouseId AS warehouseId,
           sm.itemId AS itemId,
           a.movementType AS movementType,
           a.quantityBase AS netQuantityBase,
           a.inventoryValueDeltaBase AS inventoryValueDeltaBase
    FROM supplier_stock_allocations a
    JOIN supplier_stock_sources s ON s.id = a.sourceId
    JOIN stock_movements sm ON sm.id = a.stockMovementId
), scoped AS (
    SELECT * FROM movement_events e
    WHERE e.eventDate <= :to
      AND (:supplierId IS NULL OR e.supplierId = :supplierId)
      AND (:itemId IS NULL OR e.itemId = :itemId)
      AND (:warehouseId IS NULL OR e.warehouseId = :warehouseId)
)
SELECT e.supplierId AS supplierId,
       sp.code AS supplierCode,
       COALESCE(sp.nameAr, 'غير منسوب') AS supplierName,
       e.itemId AS itemId,
       i.code AS code,
       i.nameAr AS itemName,
       u.nameAr AS baseUnitName,
       COALESCE(SUM(CASE WHEN e.eventDate < :from THEN e.netQuantityBase ELSE 0.0 END), 0.0) AS openingQtyBase,
       COALESCE(SUM(CASE WHEN e.eventDate BETWEEN :from AND :to AND e.movementType = 'PURCHASE' AND e.netQuantityBase > 0 THEN e.netQuantityBase ELSE 0.0 END), 0.0) AS purchasesQtyBase,
       COALESCE(SUM(CASE WHEN e.eventDate BETWEEN :from AND :to AND e.movementType = 'PURCHASE_RETURN' AND e.netQuantityBase < 0 THEN -e.netQuantityBase ELSE 0.0 END), 0.0) AS purchaseReturnsQtyBase,
       COALESCE(SUM(CASE WHEN e.eventDate BETWEEN :from AND :to AND e.movementType = 'SALE' AND e.netQuantityBase < 0 THEN -e.netQuantityBase ELSE 0.0 END), 0.0) AS salesQtyBase,
       COALESCE(SUM(CASE WHEN e.eventDate BETWEEN :from AND :to AND e.movementType = 'SALES_RETURN' AND e.netQuantityBase > 0 THEN e.netQuantityBase ELSE 0.0 END), 0.0) AS salesReturnsQtyBase,
       COALESCE(SUM(CASE WHEN e.eventDate BETWEEN :from AND :to AND e.movementType NOT IN ('PURCHASE','PURCHASE_RETURN','SALE','SALES_RETURN') THEN e.netQuantityBase ELSE 0.0 END), 0.0) AS otherNetQtyBase,
       COALESCE(SUM(e.netQuantityBase), 0.0) AS closingQtyBase,
       COALESCE(SUM(e.inventoryValueDeltaBase), 0.0) AS closingValueBase,
       CASE WHEN e.supplierId IS NULL THEN 'UNATTRIBUTED' ELSE 'EXACT' END AS attributionStatus
FROM scoped e
JOIN items i ON i.id = e.itemId
JOIN units u ON u.id = i.baseUnitId
LEFT JOIN suppliers sp ON sp.id = e.supplierId
GROUP BY e.supplierId, e.itemId, sp.code, sp.nameAr, i.code, i.nameAr, u.nameAr
ORDER BY CASE WHEN e.supplierId IS NULL THEN 1 ELSE 0 END, supplierName, i.nameAr
"""
''')

write('app/src/main/java/com/fush/erp/domain/SupplierItemMovementReport.kt', r'''package com.fush.erp.domain

import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.SupplierItemMovementReportRow
import com.fush.erp.data.entity.SupplierItemMovementSummaryReportRow
import kotlin.math.abs

data class SupplierItemMovementReportFilter(
    val fromDate: Long,
    val toDate: Long,
    val supplierId: Long? = null,
    val itemId: Long? = null,
    val warehouseId: Long? = null
)

data class SupplierItemMovementReportTotals(
    val openingQtyBase: Double,
    val purchasesQtyBase: Double,
    val purchaseReturnsQtyBase: Double,
    val salesQtyBase: Double,
    val salesReturnsQtyBase: Double,
    val otherNetQtyBase: Double,
    val closingQtyBase: Double,
    val closingValueBase: Double,
    val exactSummaryCount: Int,
    val unattributedSummaryCount: Int,
    val unattributedClosingQtyBase: Double
)

data class SupplierItemMovementReportResult(
    val filter: SupplierItemMovementReportFilter,
    val rows: List<SupplierItemMovementReportRow>,
    val summaries: List<SupplierItemMovementSummaryReportRow>,
    val totals: SupplierItemMovementReportTotals
)

object SupplierItemMovementReportMath {
    private const val EPS = 1e-9

    fun isRelevant(row: SupplierItemMovementSummaryReportRow): Boolean =
        listOf(
            row.openingQtyBase,
            row.purchasesQtyBase,
            row.purchaseReturnsQtyBase,
            row.salesQtyBase,
            row.salesReturnsQtyBase,
            row.otherNetQtyBase,
            row.closingQtyBase,
            row.closingValueBase
        ).any { abs(it) > EPS }

    fun buildTotals(rows: List<SupplierItemMovementSummaryReportRow>): SupplierItemMovementReportTotals =
        SupplierItemMovementReportTotals(
            openingQtyBase = rows.sumOf { it.openingQtyBase },
            purchasesQtyBase = rows.sumOf { it.purchasesQtyBase },
            purchaseReturnsQtyBase = rows.sumOf { it.purchaseReturnsQtyBase },
            salesQtyBase = rows.sumOf { it.salesQtyBase },
            salesReturnsQtyBase = rows.sumOf { it.salesReturnsQtyBase },
            otherNetQtyBase = rows.sumOf { it.otherNetQtyBase },
            closingQtyBase = rows.sumOf { it.closingQtyBase },
            closingValueBase = rows.sumOf { it.closingValueBase },
            exactSummaryCount = rows.count { it.supplierId != null && it.attributionStatus == "EXACT" },
            unattributedSummaryCount = rows.count { it.supplierId == null || it.attributionStatus == "UNATTRIBUTED" },
            unattributedClosingQtyBase = rows.filter { it.supplierId == null || it.attributionStatus == "UNATTRIBUTED" }.sumOf { it.closingQtyBase }
        )
}

class SupplierItemMovementReportService(private val db: FushDatabase) {
    suspend fun load(filter: SupplierItemMovementReportFilter): SupplierItemMovementReportResult {
        require(filter.fromDate <= filter.toDate) { "تاريخ بداية تقرير حركة أصناف المورد يجب ألا يتجاوز تاريخ النهاية" }
        if (filter.supplierId != null) requireNotNull(db.supplierDao().byId(filter.supplierId)) { "المورد غير موجود" }
        if (filter.itemId != null) requireNotNull(db.itemDao().byId(filter.itemId)) { "الصنف غير موجود" }
        if (filter.warehouseId != null) require(db.warehouseDao().allActive().any { it.id == filter.warehouseId }) { "المخزن غير موجود" }

        val dao = db.reportDao()
        val rows = dao.supplierItemMovements(
            from = filter.fromDate,
            to = filter.toDate,
            supplierId = filter.supplierId,
            itemId = filter.itemId,
            warehouseId = filter.warehouseId
        )
        val summaries = dao.supplierItemMovementSummaries(
            from = filter.fromDate,
            to = filter.toDate,
            supplierId = filter.supplierId,
            itemId = filter.itemId,
            warehouseId = filter.warehouseId
        ).filter(SupplierItemMovementReportMath::isRelevant)

        return SupplierItemMovementReportResult(
            filter = filter,
            rows = rows,
            summaries = summaries,
            totals = SupplierItemMovementReportMath.buildTotals(summaries)
        )
    }
}
''')

report_dao = root / 'app/src/main/java/com/fush/erp/data/dao/ReportDao.kt'
text = report_dao.read_text(encoding='utf-8')
anchor = '@Dao\ninterface ReportDao {'
if anchor not in text:
    raise SystemExit('ReportDao anchor not found')
addition = '''@Dao\ninterface ReportDao {\n    @Query(SQL_SUPPLIER_ITEM_MOVEMENT_DETAILS)\n    suspend fun supplierItemMovements(\n        from: Long,\n        to: Long,\n        supplierId: Long?,\n        itemId: Long?,\n        warehouseId: Long?\n    ): List<SupplierItemMovementReportRow>\n\n    @Query(SQL_SUPPLIER_ITEM_MOVEMENT_SUMMARIES)\n    suspend fun supplierItemMovementSummaries(\n        from: Long,\n        to: Long,\n        supplierId: Long?,\n        itemId: Long?,\n        warehouseId: Long?\n    ): List<SupplierItemMovementSummaryReportRow>\n'''
text = text.replace(anchor, addition, 1)
report_dao.write_text(text, encoding='utf-8')

app = root / 'app/src/main/java/com/fush/erp/data/AppContainer.kt'
text = app.read_text(encoding='utf-8')
import_anchor = 'import com.fush.erp.domain.ProductMasterService\n'
if import_anchor not in text:
    raise SystemExit('AppContainer import anchor not found')
text = text.replace(import_anchor, import_anchor + 'import com.fush.erp.domain.SupplierItemMovementReportService\n', 1)
service_anchor = '    val productMasterService = ProductMasterService(db)\n'
if service_anchor not in text:
    raise SystemExit('AppContainer service anchor not found')
text = text.replace(service_anchor, service_anchor + '    val supplierItemMovementReportService = SupplierItemMovementReportService(db)\n', 1)
app.write_text(text, encoding='utf-8')

print('GATE2A_PATCH_APPLIED=PASS')
