package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.fush.erp.data.entity.*

@Dao
interface ReportDao {
    @Query("""
        WITH customer_balances AS (
            SELECT c.id AS customerId,
                   MAX(0.0,
                       COALESCE((SELECT SUM(si.totalBase)
                                 FROM sales_invoices si
                                 WHERE si.customerId=c.id AND si.status='POSTED'
                                   AND si.paymentType='CREDIT' AND si.invoiceDate <= :to),0)
                       - COALESCE((SELECT SUM(sr.totalBase)
                                   FROM sales_returns sr
                                   JOIN sales_invoices srx ON srx.id=sr.salesInvoiceId
                                   WHERE sr.customerId=c.id AND sr.status='POSTED'
                                     AND sr.settlementType='CUSTOMER_CREDIT'
                                     AND srx.paymentType='CREDIT' AND sr.returnDate <= :to),0)
                       - COALESCE((SELECT SUM(cra.amountBase)
                                   FROM customer_receipt_allocations cra
                                   JOIN customer_receipts cr ON cr.id=cra.receiptId
                                   JOIN sales_invoices six ON six.id=cra.invoiceId
                                   WHERE six.customerId=c.id AND six.paymentType='CREDIT'
                                     AND cr.receiptDate <= :to),0)
                       + COALESCE((SELECT SUM(CASE
                                   WHEN pv.voucherType='PAYMENT' THEN pv.amountBase
                                   WHEN pv.voucherType='RECEIPT' THEN -pv.amountBase
                                   ELSE 0 END)
                                   FROM party_vouchers pv
                                   WHERE pv.customerId=c.id AND pv.status='POSTED'
                                     AND pv.partyType='CUSTOMER' AND pv.voucherDate <= :to),0)
                   ) AS outstandingBase,
                   MAX(0.0,
                       COALESCE((SELECT SUM(MAX(0.0,
                           si2.totalBase
                           - COALESCE((SELECT SUM(sr2.totalBase)
                                       FROM sales_returns sr2
                                       WHERE sr2.salesInvoiceId=si2.id AND sr2.status='POSTED'
                                         AND sr2.settlementType='CUSTOMER_CREDIT' AND sr2.returnDate <= :to),0)
                           - COALESCE((SELECT SUM(cra2.amountBase)
                                       FROM customer_receipt_allocations cra2
                                       JOIN customer_receipts cr2 ON cr2.id=cra2.receiptId
                                       WHERE cra2.invoiceId=si2.id AND cr2.receiptDate <= :to),0)
                       ))
                       FROM sales_invoices si2
                       WHERE si2.customerId=c.id AND si2.status='POSTED'
                         AND si2.paymentType='CREDIT' AND si2.invoiceDate <= :to
                         AND si2.dueDate IS NOT NULL AND si2.dueDate < :to),0)
                       - MAX(0.0,
                           COALESCE((SELECT SUM(pv2.amountBase)
                               FROM party_vouchers pv2
                               WHERE pv2.customerId=c.id AND pv2.status='POSTED'
                                 AND pv2.partyType='CUSTOMER' AND pv2.voucherType='RECEIPT'
                                 AND pv2.voucherDate <= :to),0)
                       )
                   ) AS overdueBase
            FROM customers c
        )
        SELECT
          COALESCE((SELECT SUM(totalBase) FROM sales_invoices WHERE status='POSTED' AND invoiceDate BETWEEN :from AND :to),0) AS grossSalesBase,
          COALESCE((SELECT SUM(totalBase) FROM sales_returns WHERE status='POSTED' AND returnDate BETWEEN :from AND :to),0) AS salesReturnsBase,
          (COALESCE((SELECT SUM(amountBase) FROM customer_receipts WHERE receiptDate BETWEEN :from AND :to),0)
           + COALESCE((SELECT SUM(amountBase) FROM party_vouchers
                       WHERE status='POSTED' AND partyType='CUSTOMER' AND voucherType='RECEIPT'
                         AND voucherDate BETWEEN :from AND :to),0)
           - COALESCE((SELECT SUM(totalBase) FROM sales_returns
                       WHERE status='POSTED' AND settlementType='CASH_REFUND' AND returnDate BETWEEN :from AND :to),0)
           - COALESCE((SELECT SUM(amountBase) FROM party_vouchers
                       WHERE status='POSTED' AND partyType='CUSTOMER' AND voucherType='PAYMENT'
                         AND voucherDate BETWEEN :from AND :to),0)) AS collectionsBase,
          COALESCE((SELECT SUM(totalBase) FROM purchase_invoices WHERE status='POSTED' AND invoiceDate BETWEEN :from AND :to),0) AS grossPurchasesBase,
          COALESCE((SELECT SUM(totalBase) FROM purchase_returns WHERE status='POSTED' AND returnDate BETWEEN :from AND :to),0) AS purchaseReturnsBase,
          COALESCE((SELECT SUM(quantityBase * unitCostBase) FROM stock_movements WHERE movementDate <= :to),0) AS inventoryValueBase,
          COALESCE((SELECT SUM(outstandingBase) FROM customer_balances),0) AS receivablesBase,
          COALESCE((SELECT SUM(overdueBase) FROM customer_balances),0) AS overdueBase,
          COALESCE((SELECT COUNT(*) FROM production_orders WHERE plannedDate BETWEEN :from AND :to),0) AS productionOrders,
          COALESCE((SELECT SUM(pb.acceptedQtyBase) FROM production_batches pb WHERE pb.manufactureDate BETWEEN :from AND :to),0) AS acceptedQtyBase,
          COALESCE((SELECT SUM(pb.acceptedQtyBase)
                    FROM production_batches pb
                    JOIN production_orders po ON po.id=pb.orderId
                    JOIN items i ON i.id=po.productItemId
                    WHERE pb.manufactureDate BETWEEN :from AND :to
                      AND (LOWER(i.code)='fg-fush-60'
                           OR LOWER(i.nameAr) LIKE '%60%'
                           OR LOWER(i.nameEn) LIKE '%60%')),0) AS accepted60QtyBase,
          COALESCE((SELECT SUM(pb.acceptedQtyBase)
                    FROM production_batches pb
                    JOIN production_orders po ON po.id=pb.orderId
                    JOIN items i ON i.id=po.productItemId
                    WHERE pb.manufactureDate BETWEEN :from AND :to
                      AND (LOWER(i.nameAr) LIKE '%200%'
                           OR LOWER(i.nameEn) LIKE '%200%')),0) AS accepted200QtyBase,
          COALESCE((SELECT SUM(pb.scrapQtyBase) FROM production_batches pb WHERE pb.manufactureDate BETWEEN :from AND :to),0) AS scrapQtyBase,
          COALESCE((SELECT COUNT(*) FROM non_conformances nc WHERE nc.status <> 'CLOSED' AND nc.createdAt <= :to),0) AS openNonConformances,
          COALESCE((SELECT SUM(mwo.costBase) FROM maintenance_work_orders mwo WHERE mwo.openedAt BETWEEN :from AND :to),0) AS maintenanceCostBase
    """)
    suspend fun executive(from: Long, to: Long): ExecutiveReportRow

    @Query("""
        SELECT cr.receiptDate AS eventDate,
               CASE WHEN cr.reversalOfReceiptId IS NULL THEN 'RECEIPT' ELSE 'RECEIPT_REVERSAL' END AS entryType,
               cr.receiptNo AS referenceNo,
               COALESCE((SELECT GROUP_CONCAT(si.invoiceNo, ', ')
                         FROM customer_receipt_allocations cra
                         JOIN sales_invoices si ON si.id = cra.invoiceId
                         WHERE cra.receiptId = cr.id), '') AS invoiceNo,
               c.nameAr AS customerName,
               c.province AS province,
               cr.currencyCode AS currencyCode,
               cr.amountOriginal AS amountOriginal,
               (cr.amountOriginal * cr.exchangeRate) AS amountBase,
               cr.notes AS notes
        FROM customer_receipts cr
        JOIN customers c ON c.id = cr.customerId
        WHERE cr.receiptDate BETWEEN :from AND :to

        UNION ALL

        SELECT sr.returnDate AS eventDate,
               'CASH_REFUND' AS entryType,
               sr.returnNo AS referenceNo,
               si.invoiceNo AS invoiceNo,
               c.nameAr AS customerName,
               c.province AS province,
               sr.currencyCode AS currencyCode,
               -sr.totalOriginal AS amountOriginal,
               -sr.totalBase AS amountBase,
               sr.reason AS notes
        FROM sales_returns sr
        JOIN customers c ON c.id = sr.customerId
        JOIN sales_invoices si ON si.id = sr.salesInvoiceId
        WHERE sr.status = 'POSTED'
          AND sr.settlementType = 'CASH_REFUND'
          AND sr.returnDate BETWEEN :from AND :to

        UNION ALL

        SELECT pv.voucherDate AS eventDate,
               'VOUCHER_RECEIPT' AS entryType,
               pv.voucherNo AS referenceNo,
               '' AS invoiceNo,
               c.nameAr AS customerName,
               c.province AS province,
               pv.currencyCode AS currencyCode,
               pv.amountOriginal AS amountOriginal,
               pv.amountBase AS amountBase,
               CASE WHEN pv.referenceNo <> '' THEN pv.description || ' • مرجع: ' || pv.referenceNo ELSE pv.description END AS notes
        FROM party_vouchers pv
        JOIN customers c ON c.id = pv.customerId
        WHERE pv.status = 'POSTED'
          AND pv.partyType = 'CUSTOMER'
          AND pv.voucherType = 'RECEIPT'
          AND pv.voucherDate BETWEEN :from AND :to

        UNION ALL

        SELECT pv.voucherDate AS eventDate,
               'VOUCHER_PAYMENT' AS entryType,
               pv.voucherNo AS referenceNo,
               '' AS invoiceNo,
               c.nameAr AS customerName,
               c.province AS province,
               pv.currencyCode AS currencyCode,
               -pv.amountOriginal AS amountOriginal,
               -pv.amountBase AS amountBase,
               CASE WHEN pv.referenceNo <> '' THEN pv.description || ' • مرجع: ' || pv.referenceNo ELSE pv.description END AS notes
        FROM party_vouchers pv
        JOIN customers c ON c.id = pv.customerId
        WHERE pv.status = 'POSTED'
          AND pv.partyType = 'CUSTOMER'
          AND pv.voucherType = 'PAYMENT'
          AND pv.voucherDate BETWEEN :from AND :to

        ORDER BY eventDate DESC, referenceNo DESC
    """)
    suspend fun collectionDetails(from: Long, to: Long): List<CollectionDetailRow>

    @Query("""
        WITH period_activity AS (
            SELECT customerId FROM sales_invoices
             WHERE status='POSTED' AND invoiceDate BETWEEN :from AND :to
            UNION
            SELECT customerId FROM sales_returns
             WHERE status='POSTED' AND returnDate BETWEEN :from AND :to
            UNION
            SELECT customerId FROM customer_receipts
             WHERE receiptDate BETWEEN :from AND :to
            UNION
            SELECT customerId FROM party_vouchers
             WHERE customerId IS NOT NULL AND status='POSTED' AND partyType='CUSTOMER'
               AND voucherType IN ('RECEIPT','PAYMENT') AND voucherDate BETWEEN :from AND :to
        ),
        customer_balances AS (
            SELECT c0.id AS customerId,
                   MAX(0.0,
                       COALESCE((SELECT SUM(si0.totalBase)
                                 FROM sales_invoices si0
                                 WHERE si0.customerId=c0.id AND si0.status='POSTED'
                                   AND si0.paymentType='CREDIT' AND si0.invoiceDate <= :to),0)
                       - COALESCE((SELECT SUM(sr0.totalBase)
                                   FROM sales_returns sr0
                                   JOIN sales_invoices srx0 ON srx0.id=sr0.salesInvoiceId
                                   WHERE sr0.customerId=c0.id AND sr0.status='POSTED'
                                     AND sr0.settlementType='CUSTOMER_CREDIT'
                                     AND srx0.paymentType='CREDIT' AND sr0.returnDate <= :to),0)
                       - COALESCE((SELECT SUM(cra0.amountBase)
                                   FROM customer_receipt_allocations cra0
                                   JOIN customer_receipts cr0 ON cr0.id=cra0.receiptId
                                   JOIN sales_invoices six0 ON six0.id=cra0.invoiceId
                                   WHERE six0.customerId=c0.id AND six0.paymentType='CREDIT'
                                     AND cr0.receiptDate <= :to),0)
                       + COALESCE((SELECT SUM(CASE
                                   WHEN pv0.voucherType='PAYMENT' THEN pv0.amountBase
                                   WHEN pv0.voucherType='RECEIPT' THEN -pv0.amountBase
                                   ELSE 0 END)
                                   FROM party_vouchers pv0
                                   WHERE pv0.customerId=c0.id AND pv0.status='POSTED'
                                     AND pv0.partyType='CUSTOMER' AND pv0.voucherDate <= :to),0)
                   ) AS outstandingBase
            FROM customers c0
        )
        SELECT c.id AS customerId, c.nameAr AS customerName, c.province AS province,
               COALESCE((SELECT COUNT(*) FROM sales_invoices si
                          WHERE si.customerId=c.id AND si.status='POSTED' AND si.invoiceDate BETWEEN :from AND :to),0) AS invoiceCount,
               COALESCE((SELECT SUM(si.totalBase) FROM sales_invoices si
                          WHERE si.customerId=c.id AND si.status='POSTED' AND si.invoiceDate BETWEEN :from AND :to),0) AS grossSalesBase,
               COALESCE((SELECT SUM(sr.totalBase) FROM sales_returns sr
                          WHERE sr.customerId=c.id AND sr.status='POSTED' AND sr.returnDate BETWEEN :from AND :to),0) AS returnsBase,
               (COALESCE((SELECT SUM(cr.amountOriginal * cr.exchangeRate) FROM customer_receipts cr
                           WHERE cr.customerId=c.id AND cr.receiptDate BETWEEN :from AND :to),0)
                + COALESCE((SELECT SUM(pvr.amountBase) FROM party_vouchers pvr
                             WHERE pvr.customerId=c.id AND pvr.status='POSTED' AND pvr.partyType='CUSTOMER'
                               AND pvr.voucherType='RECEIPT' AND pvr.voucherDate BETWEEN :from AND :to),0)
                - COALESCE((SELECT SUM(srCash.totalBase) FROM sales_returns srCash
                             WHERE srCash.customerId=c.id AND srCash.status='POSTED'
                               AND srCash.settlementType='CASH_REFUND' AND srCash.returnDate BETWEEN :from AND :to),0)
                - COALESCE((SELECT SUM(pvp.amountBase) FROM party_vouchers pvp
                             WHERE pvp.customerId=c.id AND pvp.status='POSTED' AND pvp.partyType='CUSTOMER'
                               AND pvp.voucherType='PAYMENT' AND pvp.voucherDate BETWEEN :from AND :to),0)) AS collectionsBase,
               COALESCE(cb.outstandingBase,0) AS outstandingBase
        FROM customers c
        LEFT JOIN customer_balances cb ON cb.customerId=c.id
        WHERE c.id IN (SELECT customerId FROM period_activity)
           OR COALESCE(cb.outstandingBase,0) > 0.000001
        ORDER BY grossSalesBase DESC, outstandingBase DESC, c.nameAr
    """)
    suspend fun customerSales(from: Long, to: Long): List<CustomerSalesReportRow>

    @Query("""
        WITH product_movements AS (
            SELECT sl.itemId AS itemId,
                   SUM(sl.baseQuantity) AS grossQtyBase,
                   0.0 AS returnedQtyBase
            FROM sales_lines sl
            JOIN sales_invoices si ON si.id=sl.invoiceId
            WHERE si.status='POSTED' AND si.invoiceDate BETWEEN :from AND :to
            GROUP BY sl.itemId

            UNION ALL

            SELECT srl.itemId AS itemId,
                   0.0 AS grossQtyBase,
                   SUM(srl.baseQuantity) AS returnedQtyBase
            FROM sales_return_lines srl
            JOIN sales_returns sr ON sr.id=srl.returnId
            WHERE sr.status='POSTED' AND sr.returnDate BETWEEN :from AND :to
            GROUP BY srl.itemId
        )
        SELECT i.id AS itemId,
               i.code AS code,
               i.nameAr AS productName,
               COALESCE(SUM(pm.grossQtyBase),0) AS grossQtyBase,
               COALESCE(SUM(pm.returnedQtyBase),0) AS returnedQtyBase,
               COALESCE(SUM(pm.grossQtyBase),0) - COALESCE(SUM(pm.returnedQtyBase),0) AS netQtyBase
        FROM product_movements pm
        JOIN items i ON i.id=pm.itemId
        WHERE i.category='FINISHED_GOOD'
        GROUP BY i.id, i.code, i.nameAr
        ORDER BY netQtyBase DESC, i.nameAr
    """)
    suspend fun salesProductQuantities(from: Long, to: Long): List<ProductSalesQuantityReportRow>

    @Query("""
        WITH period_suppliers AS (
            SELECT supplierId FROM purchase_invoices
             WHERE status='POSTED' AND invoiceDate BETWEEN :from AND :to
            UNION
            SELECT supplierId FROM purchase_returns
             WHERE status='POSTED' AND returnDate BETWEEN :from AND :to
        )
        SELECT s.id AS supplierId, s.nameAr AS supplierName,
               COALESCE((SELECT COUNT(*) FROM purchase_invoices pi
                          WHERE pi.supplierId=s.id AND pi.status='POSTED' AND pi.invoiceDate BETWEEN :from AND :to),0) AS invoiceCount,
               COALESCE((SELECT SUM(pi.totalBase) FROM purchase_invoices pi
                          WHERE pi.supplierId=s.id AND pi.status='POSTED' AND pi.invoiceDate BETWEEN :from AND :to),0) AS grossPurchasesBase,
               COALESCE((SELECT SUM(pr.totalBase) FROM purchase_returns pr
                          WHERE pr.supplierId=s.id AND pr.status='POSTED' AND pr.returnDate BETWEEN :from AND :to),0) AS returnsBase,
               (COALESCE((SELECT SUM(pi2.totalBase) FROM purchase_invoices pi2
                           WHERE pi2.supplierId=s.id AND pi2.status='POSTED' AND pi2.invoiceDate BETWEEN :from AND :to),0)
                - COALESCE((SELECT SUM(pr2.totalBase) FROM purchase_returns pr2
                             WHERE pr2.supplierId=s.id AND pr2.status='POSTED' AND pr2.returnDate BETWEEN :from AND :to),0)) AS netPurchasesBase
        FROM suppliers s
        WHERE s.id IN (SELECT supplierId FROM period_suppliers)
        ORDER BY netPurchasesBase DESC, s.nameAr
    """)
    suspend fun supplierPurchases(from: Long, to: Long): List<SupplierPurchaseReportRow>

    @Query("""
        SELECT i.id AS itemId, i.code AS code, i.nameAr AS itemName, u.nameAr AS baseUnitName,
               COALESCE(SUM(sm.quantityBase),0) AS quantityBase,
               COALESCE(SUM(sm.quantityBase * sm.unitCostBase),0) AS inventoryValueBase,
               i.reorderLevel AS reorderLevel
        FROM items i
        JOIN units u ON u.id=i.baseUnitId
        LEFT JOIN stock_movements sm ON sm.itemId=i.id AND sm.movementDate <= :asOf
        WHERE i.isActive=1
        GROUP BY i.id, i.code, i.nameAr, u.nameAr, i.reorderLevel
        ORDER BY inventoryValueBase DESC, i.nameAr
    """)
    suspend fun inventoryValuation(asOf: Long): List<InventoryValuationReportRow>


    @Query("""
        SELECT i.id AS itemId, i.code AS code, i.nameAr AS itemName, u.nameAr AS baseUnitName,
               COALESCE(SUM(sm.quantityBase),0) AS quantityBase,
               COALESCE(SUM(sm.quantityBase * sm.unitCostBase),0) AS inventoryValueBase,
               MIN(CASE WHEN sm.quantityBase > 0.000000001 THEN sm.movementDate ELSE NULL END) AS firstInboundDate,
               MAX(sm.movementDate) AS lastMovementDate,
               MAX(CASE WHEN sm.quantityBase < -0.000000001 THEN sm.movementDate ELSE NULL END) AS lastOutboundDate
        FROM items i
        JOIN units u ON u.id = i.baseUnitId
        LEFT JOIN stock_movements sm ON sm.itemId = i.id AND sm.movementDate <= :asOf
        WHERE i.isActive = 1
        GROUP BY i.id, i.code, i.nameAr, u.nameAr
        HAVING COALESCE(SUM(sm.quantityBase),0) > 0.000000001
        ORDER BY CASE WHEN lastOutboundDate IS NULL THEN 0 ELSE 1 END, lastOutboundDate, i.nameAr
    """)
    suspend fun inventoryActivity(asOf: Long): List<InventoryActivityReportRow>

    @Query("""
        SELECT w.nameAr AS warehouseName, i.id AS itemId, i.code AS code, i.nameAr AS itemName,
               u.nameAr AS baseUnitName, sm.lotNo AS lotNo, COALESCE(sm.expiryDate,0) AS expiryDate,
               COALESCE(SUM(sm.quantityBase),0) AS quantityBase,
               COALESCE(SUM(sm.quantityBase * sm.unitCostBase),0) AS inventoryValueBase
        FROM stock_movements sm
        JOIN warehouses w ON w.id = sm.warehouseId
        JOIN items i ON i.id = sm.itemId
        JOIN units u ON u.id = i.baseUnitId
        WHERE sm.movementDate <= :asOf AND sm.expiryDate IS NOT NULL
        GROUP BY sm.warehouseId, w.nameAr, sm.itemId, i.code, i.nameAr, u.nameAr, sm.lotNo, sm.expiryDate
        HAVING COALESCE(SUM(sm.quantityBase),0) > 0.000000001
        ORDER BY sm.expiryDate, i.nameAr, w.nameAr, sm.lotNo
    """)
    suspend fun inventoryExpiryLots(asOf: Long): List<InventoryExpiryLotReportRow>

    @Query("""
        SELECT sm.id AS id, sm.movementDate AS movementDate, w.nameAr AS warehouseName,
               i.id AS itemId, i.code AS code, i.nameAr AS itemName, u.nameAr AS baseUnitName,
               sm.movementType AS movementType, sm.quantityBase AS quantityBase,
               sm.unitCostBase AS unitCostBase, (sm.quantityBase * sm.unitCostBase) AS movementValueBase,
               sm.lotNo AS lotNo, sm.expiryDate AS expiryDate,
               sm.referenceType AS referenceType, sm.referenceId AS referenceId
        FROM stock_movements sm
        JOIN warehouses w ON w.id = sm.warehouseId
        JOIN items i ON i.id = sm.itemId
        JOIN units u ON u.id = i.baseUnitId
        WHERE sm.movementDate BETWEEN :from AND :to
        ORDER BY sm.movementDate DESC, sm.id DESC
    """)
    suspend fun inventoryMovementDetails(from: Long, to: Long): List<InventoryMovementDetailReportRow>

    @Query("""
        SELECT po.id AS orderId, po.orderNo AS orderNo, po.plannedDate AS plannedDate,
               pb.manufactureDate AS manufactureDate,
               i.code AS productCode, i.nameAr AS productName, po.status AS status, pb.batchNo AS batchNo,
               po.plannedOutputQtyBase AS plannedQtyBase,
               COALESCE(pb.actualOutputQtyBase,0) AS actualQtyBase,
               COALESCE(pb.acceptedQtyBase,0) AS acceptedQtyBase,
               COALESCE(pb.rejectedQtyBase,0) AS rejectedQtyBase,
               COALESCE(pb.scrapQtyBase,0) AS scrapQtyBase,
               COALESCE((SELECT SUM(pissue.totalCostBase) FROM production_issues pissue WHERE pissue.orderId=po.id),0) AS materialCostBase,
               po.directLaborCostBase AS laborCostBase,
               COALESCE((SELECT SUM(pissue2.totalCostBase) FROM production_issues pissue2 WHERE pissue2.orderId=po.id),0) + po.directLaborCostBase AS actualCostBase
        FROM production_orders po
        JOIN items i ON i.id=po.productItemId
        LEFT JOIN production_batches pb ON pb.orderId=po.id
        WHERE COALESCE(pb.manufactureDate, po.plannedDate) >= :from
          AND COALESCE(pb.manufactureDate, po.plannedDate) <= :to
        ORDER BY COALESCE(pb.manufactureDate, po.plannedDate) DESC, po.id DESC
    """)
    suspend fun productionPerformance(from: Long, to: Long): List<ProductionPerformanceReportRow>

    @Query("""
        SELECT pi.itemId AS itemId, i.code AS code, i.nameAr AS itemName, u.nameAr AS unitName,
               COALESCE(SUM(pi.quantityBase),0) AS issuedQtyBase,
               COALESCE(SUM(pi.totalCostBase),0) AS totalCostBase,
               CASE WHEN COALESCE(SUM(pi.quantityBase),0) > 0
                    THEN COALESCE(SUM(pi.totalCostBase),0) / SUM(pi.quantityBase) ELSE 0 END AS averageUnitCostBase,
               COUNT(DISTINCT pi.orderId) AS orderCount
        FROM production_issues pi
        JOIN production_orders po ON po.id = pi.orderId
        LEFT JOIN production_batches pb ON pb.orderId = po.id
        JOIN items i ON i.id = pi.itemId
        JOIN units u ON u.id = i.baseUnitId
        WHERE COALESCE(pb.manufactureDate, po.plannedDate) >= :from
          AND COALESCE(pb.manufactureDate, po.plannedDate) <= :to
        GROUP BY pi.itemId, i.code, i.nameAr, u.nameAr
        ORDER BY totalCostBase DESC, i.nameAr
    """)
    suspend fun productionMaterialUsage(from: Long, to: Long): List<ProductionMaterialUsageReportRow>

    @Query("""
        SELECT pb.id AS batchId, pb.batchNo AS batchNo, pb.manufactureDate AS manufactureDate,
               pb.status AS batchStatus,
               COALESCE((SELECT COUNT(*) FROM quality_checks qc WHERE qc.batchId=pb.id AND qc.decision='PASS'),0) AS passChecks,
               COALESCE((SELECT COUNT(*) FROM quality_checks qc2 WHERE qc2.batchId=pb.id AND qc2.decision='FAIL'),0) AS failChecks,
               COALESCE((SELECT COUNT(*) FROM non_conformances nc WHERE nc.batchId=pb.id AND nc.status <> 'CLOSED'),0) AS openNonConformances,
               pb.acceptedQtyBase AS acceptedQtyBase, pb.rejectedQtyBase AS rejectedQtyBase, pb.scrapQtyBase AS scrapQtyBase
        FROM production_batches pb
        WHERE pb.manufactureDate BETWEEN :from AND :to
        ORDER BY pb.manufactureDate DESC, pb.id DESC
    """)
    suspend fun quality(from: Long, to: Long): List<QualityReportRow>

    @Query("""
        SELECT sc.beneficiary AS beneficiary,
               COALESCE(SUM(sc.earnedBase),0) AS earnedBase,
               COALESCE(SUM(sc.reversedBase),0) AS reversedBase,
               COALESCE(SUM(sc.earnedBase-sc.reversedBase),0) AS netCommissionBase
        FROM sales_commissions sc
        WHERE sc.createdAt BETWEEN :from AND :to
        GROUP BY sc.beneficiary
        ORDER BY netCommissionBase DESC, sc.beneficiary
    """)
    suspend fun commissions(from: Long, to: Long): List<CommissionReportRow>

    @Query("""
        SELECT COUNT(*) AS workOrderCount,
               COALESCE(SUM(CASE WHEN status='CLOSED' THEN 1 ELSE 0 END),0) AS closedCount,
               COALESCE(SUM(CASE WHEN status<>'CLOSED' THEN 1 ELSE 0 END),0) AS openCount,
               COALESCE(SUM(downtimeMinutes),0) AS downtimeMinutes,
               COALESCE(SUM(costBase),0) AS costBase
        FROM maintenance_work_orders
        WHERE openedAt BETWEEN :from AND :to
    """)
    suspend fun maintenance(from: Long, to: Long): MaintenanceReportRow

    @Query("""
        SELECT c.id AS partyId,
               c.nameAr AS partyName,
               si.dueDate AS dueDate,
               MAX(0.0,
                   si.totalBase
                   - COALESCE((SELECT SUM(sr.totalBase)
                               FROM sales_returns sr
                               WHERE sr.salesInvoiceId=si.id
                                 AND sr.status='POSTED'
                                 AND sr.settlementType='CUSTOMER_CREDIT'
                                 AND sr.returnDate <= :asOf),0)
                   - COALESCE((SELECT SUM(cra.amountBase)
                               FROM customer_receipt_allocations cra
                               JOIN customer_receipts cr ON cr.id=cra.receiptId
                               WHERE cra.invoiceId=si.id
                                 AND cr.receiptDate <= :asOf),0)
               ) AS outstandingBase
        FROM sales_invoices si
        JOIN customers c ON c.id=si.customerId
        WHERE si.status='POSTED'
          AND si.paymentType='CREDIT'
          AND si.invoiceDate <= :asOf
        ORDER BY c.nameAr, COALESCE(si.dueDate, si.invoiceDate), si.invoiceDate, si.id
    """)
    suspend fun customerAgingInvoices(asOf: Long): List<PartyAgingInvoiceRow>

    @Query("""
        SELECT c.id AS partyId,
               c.nameAr AS partyName,
               COALESCE(SUM(
                   CASE WHEN pv.voucherDate <= :asOf THEN
                       CASE WHEN pv.voucherType='PAYMENT' THEN pv.amountBase
                            WHEN pv.voucherType='RECEIPT' THEN -pv.amountBase
                            ELSE 0.0 END
                       ELSE 0.0 END
                   + CASE WHEN pv.status='REVERSED' AND pv.reversedAt IS NOT NULL AND pv.reversedAt <= :asOf THEN
                       CASE WHEN pv.voucherType='PAYMENT' THEN -pv.amountBase
                            WHEN pv.voucherType='RECEIPT' THEN pv.amountBase
                            ELSE 0.0 END
                       ELSE 0.0 END
               ),0) AS adjustmentBase
        FROM customers c
        JOIN party_vouchers pv ON pv.customerId=c.id
        WHERE pv.partyType='CUSTOMER'
          AND pv.voucherType IN ('RECEIPT','PAYMENT')
          AND pv.status IN ('POSTED','REVERSED')
          AND pv.voucherDate <= :asOf
        GROUP BY c.id, c.nameAr
        ORDER BY c.nameAr
    """)
    suspend fun customerAgingAdjustments(asOf: Long): List<PartyAgingAdjustmentRow>

    @Query("""
        SELECT s.id AS partyId,
               s.nameAr AS partyName,
               pi.dueDate AS dueDate,
               MAX(0.0,
                   pi.totalBase
                   - COALESCE((SELECT SUM(pr.totalBase)
                               FROM purchase_returns pr
                               WHERE pr.purchaseInvoiceId=pi.id
                                 AND pr.status='POSTED'
                                 AND pr.settlementType='SUPPLIER_CREDIT'
                                 AND pr.returnDate <= :asOf),0)
                   - COALESCE((SELECT SUM(spa.allocatedBase)
                               FROM supplier_payment_allocations spa
                               JOIN supplier_payments sp ON sp.id=spa.paymentId
                               WHERE spa.invoiceId=pi.id
                                 AND sp.paymentDate <= :asOf),0)
               ) AS outstandingBase
        FROM purchase_invoices pi
        JOIN suppliers s ON s.id=pi.supplierId
        WHERE pi.status='POSTED'
          AND pi.paymentType='CREDIT'
          AND pi.invoiceDate <= :asOf
        ORDER BY s.nameAr, COALESCE(pi.dueDate, pi.invoiceDate), pi.invoiceDate, pi.id
    """)
    suspend fun supplierAgingInvoices(asOf: Long): List<PartyAgingInvoiceRow>

    @Query("""
        SELECT s.id AS partyId,
               s.nameAr AS partyName,
               COALESCE(SUM(
                   CASE WHEN pv.voucherDate <= :asOf THEN
                       CASE WHEN pv.voucherType='RECEIPT' THEN pv.amountBase
                            WHEN pv.voucherType='PAYMENT' THEN -pv.amountBase
                            ELSE 0.0 END
                       ELSE 0.0 END
                   + CASE WHEN pv.status='REVERSED' AND pv.reversedAt IS NOT NULL AND pv.reversedAt <= :asOf THEN
                       CASE WHEN pv.voucherType='RECEIPT' THEN -pv.amountBase
                            WHEN pv.voucherType='PAYMENT' THEN pv.amountBase
                            ELSE 0.0 END
                       ELSE 0.0 END
               ),0) AS adjustmentBase
        FROM suppliers s
        JOIN party_vouchers pv ON pv.supplierId=s.id
        WHERE pv.partyType='SUPPLIER'
          AND pv.voucherType IN ('RECEIPT','PAYMENT')
          AND pv.status IN ('POSTED','REVERSED')
          AND pv.voucherDate <= :asOf
        GROUP BY s.id, s.nameAr
        ORDER BY s.nameAr
    """)
    suspend fun supplierAgingAdjustments(asOf: Long): List<PartyAgingAdjustmentRow>



    @Query("""
        SELECT t.id AS treasuryId, t.code AS treasuryCode, t.nameAr AS treasuryName,
               t.kind AS treasuryKind, t.currencyCode AS currencyCode,
               t.bankName AS bankName, t.accountNumber AS accountNumber,
               je.id AS entryId, je.entryNo AS entryNo, je.entryDate AS entryDate,
               je.description AS description, je.sourceType AS sourceType,
               jl.debit AS debitBase, jl.credit AS creditBase,
               CASE WHEN je.sourceType = 'TREASURY_TRANSFER' OR (
                   je.sourceType = 'REVERSAL' AND EXISTS(
                       SELECT 1 FROM journal_entries original
                       WHERE original.id = CAST(je.sourceId AS INTEGER)
                         AND original.sourceType = 'TREASURY_TRANSFER'
                   )
               ) THEN 1 ELSE 0 END AS isInternalTransfer
        FROM treasury_accounts t
        JOIN journal_lines jl ON jl.accountId = t.accountId
        JOIN journal_entries je ON je.id = jl.entryId
        WHERE je.status = 'POSTED' AND je.entryDate <= :to
        ORDER BY t.kind, t.nameAr, je.entryDate, je.id, jl.id
    """)
    suspend fun treasuryMovementsThrough(to: Long): List<TreasuryMovementReportRow>
}
