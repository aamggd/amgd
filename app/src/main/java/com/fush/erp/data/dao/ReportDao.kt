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
                       - COALESCE((SELECT SUM(cra.amountBase + cra.discountBase)
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
                           - COALESCE((SELECT SUM(cra2.amountBase + cra2.discountBase)
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
          COALESCE((SELECT SUM(pb.acceptedQtyBase)
                    FROM production_batches pb
                    WHERE EXISTS (
                        SELECT 1 FROM stock_movements sm
                        WHERE sm.referenceType='PRODUCTION_BATCH'
                          AND sm.referenceId=pb.id
                          AND sm.movementType='PRODUCTION_RECEIPT'
                          AND sm.movementDate BETWEEN :from AND :to
                    )),0) AS acceptedQtyBase,
          COALESCE((SELECT SUM(pb.scrapQtyBase) FROM production_batches pb WHERE pb.manufactureDate BETWEEN :from AND :to),0) AS scrapQtyBase,
          COALESCE((SELECT COUNT(*) FROM non_conformances nc WHERE nc.status <> 'CLOSED' AND nc.createdAt <= :to),0) AS openNonConformances,
          COALESCE((SELECT SUM(mwo.costBase) FROM maintenance_work_orders mwo WHERE mwo.openedAt BETWEEN :from AND :to),0) AS maintenanceCostBase
    """)
    suspend fun executive(from: Long, to: Long): ExecutiveReportRow


    @Query("""
        SELECT (
            COALESCE((SELECT SUM(si.totalBase)
                      FROM sales_invoices si
                      WHERE si.status='POSTED' AND si.invoiceDate BETWEEN :from AND :to), 0)
            - COALESCE((SELECT SUM(sr.totalBase)
                        FROM sales_returns sr
                        WHERE sr.status='POSTED' AND sr.returnDate BETWEEN :from AND :to), 0)
        ) - (
            COALESCE((SELECT SUM(sa.costBase)
                      FROM sales_allocations sa
                      JOIN sales_lines sl ON sl.id = sa.salesLineId
                      JOIN sales_invoices si2 ON si2.id = sl.invoiceId
                      WHERE si2.status='POSTED' AND si2.invoiceDate BETWEEN :from AND :to), 0)
            - COALESCE((SELECT SUM(sr2.totalCostBase)
                        FROM sales_returns sr2
                        WHERE sr2.status='POSTED' AND sr2.returnDate BETWEEN :from AND :to), 0)
        )
    """)
    suspend fun salesGrossProfit(from: Long, to: Long): Double

    @Query("""
        SELECT COALESCE(SUM(
            MAX(0.0,
                COALESCE((
                    SELECT SUM(MAX(0.0,
                        si.totalBase
                        - COALESCE((SELECT SUM(sr.totalBase)
                                    FROM sales_returns sr
                                    WHERE sr.salesInvoiceId = si.id
                                      AND sr.status='POSTED'
                                      AND sr.settlementType='CUSTOMER_CREDIT'
                                      AND sr.returnDate <= :asOf), 0)
                        - COALESCE((SELECT SUM(cra.amountBase + cra.discountBase)
                                    FROM customer_receipt_allocations cra
                                    JOIN customer_receipts cr ON cr.id = cra.receiptId
                                    WHERE cra.invoiceId = si.id
                                      AND cr.receiptDate <= :asOf), 0)
                    ))
                    FROM sales_invoices si
                    WHERE si.customerId = c.id
                      AND si.status='POSTED'
                      AND si.paymentType='CREDIT'
                      AND si.invoiceDate <= :asOf
                      AND si.dueDate IS NOT NULL
                      AND si.dueDate < :cutoff
                ), 0)
                - MAX(0.0, COALESCE((
                    SELECT SUM(pv.amountBase)
                    FROM party_vouchers pv
                    WHERE pv.customerId = c.id
                      AND pv.status='POSTED'
                      AND pv.partyType='CUSTOMER'
                      AND pv.voucherType='RECEIPT'
                      AND pv.voucherDate <= :asOf
                ), 0))
            )
        ), 0)
        FROM customers c
    """)
    suspend fun overdueReceivablesOlderThan(cutoff: Long, asOf: Long): Double

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
               CASE WHEN ABS(cr.discountOriginal) > 0.000000001
                    THEN cr.notes || CASE WHEN cr.notes <> '' THEN ' • ' ELSE '' END || 'خصم تحصيل: ' || ABS(cr.discountOriginal) || ' ' || cr.currencyCode
                    ELSE cr.notes END AS notes
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
                       - COALESCE((SELECT SUM(acs0.amountBase)
                                   FROM sales_additional_charge_settlements acs0
                                   JOIN sales_additional_charges ac0 ON ac0.id=acs0.chargeId
                                   JOIN sales_invoices asi0 ON asi0.id=acs0.invoiceId
                                   WHERE asi0.customerId=c0.id AND asi0.status='POSTED' AND asi0.paymentType='CREDIT'
                                     AND asi0.invoiceDate <= :to AND acs0.status='ACTIVE' AND acs0.settlementDate <= :to
                                     AND ac0.status='POSTED' AND ac0.bearer='CUSTOMER' AND ac0.paidBy='CUSTOMER_DIRECT'),0)
                       - COALESCE((SELECT SUM(sr0.totalBase)
                                   FROM sales_returns sr0
                                   JOIN sales_invoices srx0 ON srx0.id=sr0.salesInvoiceId
                                   WHERE sr0.customerId=c0.id AND sr0.status='POSTED'
                                     AND sr0.settlementType='CUSTOMER_CREDIT'
                                     AND srx0.paymentType='CREDIT' AND sr0.returnDate <= :to),0)
                       - COALESCE((SELECT SUM(cra0.amountBase + cra0.discountBase)
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
               COALESCE((SELECT SUM((si.grossOriginal-si.discountOriginal+si.transportOriginal+si.feesOriginal+si.riskMarginOriginal)*si.exchangeRate) FROM sales_invoices si
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
                   0.0 AS returnedQtyBase,
                   SUM(sl.freeBaseQuantity) AS freeQtyBase,
                   0.0 AS returnedFreeQtyBase
            FROM sales_lines sl
            JOIN sales_invoices si ON si.id=sl.invoiceId
            WHERE si.status='POSTED' AND si.invoiceDate BETWEEN :from AND :to
            GROUP BY sl.itemId

            UNION ALL

            SELECT srl.itemId AS itemId,
                   0.0 AS grossQtyBase,
                   SUM(srl.baseQuantity) AS returnedQtyBase,
                   0.0 AS freeQtyBase,
                   SUM(srl.freeBaseQuantity) AS returnedFreeQtyBase
            FROM sales_return_lines srl
            JOIN sales_returns sr ON sr.id=srl.returnId
            WHERE sr.status='POSTED' AND sr.returnDate BETWEEN :from AND :to
            GROUP BY srl.itemId
        ),
        free_sale_cost AS (
            SELECT sl.itemId AS itemId,
                   SUM(sa.freeQuantityBase * sa.unitCostBase) AS freeCostBase
            FROM sales_allocations sa
            JOIN sales_lines sl ON sl.id=sa.salesLineId
            JOIN sales_invoices si ON si.id=sl.invoiceId
            WHERE si.status='POSTED' AND si.invoiceDate BETWEEN :from AND :to
            GROUP BY sl.itemId
        ),
        free_return_cost AS (
            SELECT srl.itemId AS itemId,
                   SUM(sra.freeQuantityBase * sra.unitCostBase) AS returnedFreeCostBase
            FROM sales_return_allocations sra
            JOIN sales_return_lines srl ON srl.id=sra.returnLineId
            JOIN sales_returns sr ON sr.id=srl.returnId
            WHERE sr.status='POSTED' AND sr.returnDate BETWEEN :from AND :to
            GROUP BY srl.itemId
        )
        SELECT i.id AS itemId,
               i.code AS code,
               i.nameAr AS productName,
               COALESCE(SUM(pm.grossQtyBase),0) AS grossQtyBase,
               COALESCE(SUM(pm.returnedQtyBase),0) AS returnedQtyBase,
               COALESCE(SUM(pm.grossQtyBase),0) - COALESCE(SUM(pm.returnedQtyBase),0) AS netQtyBase,
               COALESCE(SUM(pm.freeQtyBase),0) AS freeQtyBase,
               COALESCE(SUM(pm.returnedFreeQtyBase),0) AS returnedFreeQtyBase,
               COALESCE(SUM(pm.freeQtyBase),0) - COALESCE(SUM(pm.returnedFreeQtyBase),0) AS netFreeQtyBase,
               COALESCE(MAX(fsc.freeCostBase),0) AS freeCostBase,
               COALESCE(MAX(frc.returnedFreeCostBase),0) AS returnedFreeCostBase,
               COALESCE(MAX(fsc.freeCostBase),0) - COALESCE(MAX(frc.returnedFreeCostBase),0) AS netFreeCostBase
        FROM product_movements pm
        JOIN items i ON i.id=pm.itemId
        LEFT JOIN free_sale_cost fsc ON fsc.itemId=pm.itemId
        LEFT JOIN free_return_cost frc ON frc.itemId=pm.itemId
        WHERE i.category='FINISHED_GOOD'
        GROUP BY i.id, i.code, i.nameAr
        ORDER BY netQtyBase DESC, i.nameAr
    """)
    suspend fun salesProductQuantities(from: Long, to: Long): List<ProductSalesQuantityReportRow>

    @Query("""
        SELECT si.id AS invoiceId,
               si.invoiceNo AS invoiceNo,
               si.invoiceDate AS invoiceDate,
               si.dueDate AS dueDate,
               c.id AS customerId,
               c.nameAr AS customerName,
               si.province AS province,
               COALESCE(NULLIF(si.salesRepNameSnapshot,''), srp.fullNameAr, c.salesRepName, '') AS salesRepName,
               si.paymentType AS paymentType,
               si.currencyCode AS currencyCode,
               si.totalOriginal AS totalOriginal,
               si.totalBase AS totalBase,
               COALESCE((SELECT SUM(sr.totalBase) FROM sales_returns sr
                         WHERE sr.salesInvoiceId=si.id AND sr.status='POSTED' AND sr.returnDate <= :asOf),0) AS returnsBase,
               CASE WHEN si.paymentType='CASH' THEN
                   MAX(0.0, si.totalBase - COALESCE((SELECT SUM(acs.amountBase)
                       FROM sales_additional_charge_settlements acs JOIN sales_additional_charges ac ON ac.id=acs.chargeId
                       WHERE acs.invoiceId=si.id AND acs.status='ACTIVE' AND acs.settlementDate <= :asOf
                         AND ac.status='POSTED' AND ac.bearer='CUSTOMER' AND ac.paidBy='CUSTOMER_DIRECT'),0))
               ELSE COALESCE((SELECT SUM(cra.amountBase + cra.discountBase)
                             FROM customer_receipt_allocations cra
                             JOIN customer_receipts cr ON cr.id=cra.receiptId
                             WHERE cra.invoiceId=si.id AND cr.receiptDate <= :asOf),0)
               END AS collectedBase,
               CASE WHEN si.paymentType='CASH' THEN 0.0 ELSE
                   MAX(0.0,
                       si.totalBase
                       - COALESCE((SELECT SUM(acs2.amountBase)
                                   FROM sales_additional_charge_settlements acs2 JOIN sales_additional_charges ac2 ON ac2.id=acs2.chargeId
                                   WHERE acs2.invoiceId=si.id AND acs2.status='ACTIVE' AND acs2.settlementDate <= :asOf
                                     AND ac2.status='POSTED' AND ac2.bearer='CUSTOMER' AND ac2.paidBy='CUSTOMER_DIRECT'),0)
                       - COALESCE((SELECT SUM(sr2.totalBase) FROM sales_returns sr2
                                   WHERE sr2.salesInvoiceId=si.id AND sr2.status='POSTED'
                                     AND sr2.settlementType='CUSTOMER_CREDIT' AND sr2.returnDate <= :asOf),0)
                       - COALESCE((SELECT SUM(cra2.amountBase + cra2.discountBase)
                                   FROM customer_receipt_allocations cra2
                                   JOIN customer_receipts cr2 ON cr2.id=cra2.receiptId
                                   WHERE cra2.invoiceId=si.id AND cr2.receiptDate <= :asOf),0)
                   )
               END AS outstandingBase
        FROM sales_invoices si
        JOIN customers c ON c.id=si.customerId
        LEFT JOIN sales_representatives srp ON srp.id=si.salesRepId
        WHERE si.status='POSTED' AND si.invoiceDate BETWEEN :from AND :to
        ORDER BY si.invoiceDate, si.id
    """)
    suspend fun salesInvoiceAccounting(from: Long, to: Long, asOf: Long): List<SalesInvoiceAccountingReportRow>

    @Query("""
        WITH sold AS (
            SELECT sl.itemId AS itemId,
                   SUM(sl.netOriginal * si.exchangeRate) AS revenueBase,
                   COALESCE((SELECT SUM(sa.costBase + sa.freeQuantityBase * sa.unitCostBase)
                             FROM sales_allocations sa
                             JOIN sales_lines sl2 ON sl2.id=sa.salesLineId
                             JOIN sales_invoices si2 ON si2.id=sl2.invoiceId
                             WHERE sl2.itemId=sl.itemId AND si2.status='POSTED'
                               AND si2.invoiceDate BETWEEN :from AND :to),0) AS costBase
            FROM sales_lines sl JOIN sales_invoices si ON si.id=sl.invoiceId
            WHERE si.status='POSTED' AND si.invoiceDate BETWEEN :from AND :to
            GROUP BY sl.itemId
        ), ret AS (
            SELECT srl.itemId AS itemId,
                   SUM(srl.lineNetOriginal * sr.exchangeRate) AS revenueBase,
                   SUM(srl.costBase + COALESCE((SELECT SUM(sra.freeQuantityBase * sra.unitCostBase)
                                                FROM sales_return_allocations sra
                                                WHERE sra.returnLineId=srl.id),0)) AS costBase
            FROM sales_return_lines srl JOIN sales_returns sr ON sr.id=srl.returnId
            WHERE sr.status='POSTED' AND sr.returnDate BETWEEN :from AND :to
            GROUP BY srl.itemId
        ), ids AS (SELECT itemId FROM sold UNION SELECT itemId FROM ret)
        SELECT i.id AS itemId, i.code AS itemCode, i.nameAr AS itemName,
               COALESCE(sold.revenueBase,0) AS grossRevenueBase,
               COALESCE(ret.revenueBase,0) AS returnedRevenueBase,
               COALESCE(sold.revenueBase,0)-COALESCE(ret.revenueBase,0) AS netRevenueBase,
               COALESCE(sold.costBase,0) AS grossCostBase,
               COALESCE(ret.costBase,0) AS returnedCostBase,
               COALESCE(sold.costBase,0)-COALESCE(ret.costBase,0) AS netCostBase,
               (COALESCE(sold.revenueBase,0)-COALESCE(ret.revenueBase,0)) -
               (COALESCE(sold.costBase,0)-COALESCE(ret.costBase,0)) AS netProfitBase
        FROM ids x JOIN items i ON i.id=x.itemId
        LEFT JOIN sold ON sold.itemId=i.id LEFT JOIN ret ON ret.itemId=i.id
        ORDER BY netRevenueBase DESC, i.nameAr
    """)
    suspend fun productProfitability(from: Long, to: Long): List<ProductProfitabilityReportRow>

    @Query("""
        WITH reps AS (
            SELECT COALESCE(NULLIF(si.salesRepNameSnapshot,''), srp.fullNameAr, c.salesRepName, 'غير محدد') AS repName,
                   COUNT(DISTINCT si.customerId) AS customerCount,
                   COUNT(*) AS invoiceCount,
                   SUM((si.grossOriginal-si.discountOriginal+si.transportOriginal+si.feesOriginal+si.riskMarginOriginal)*si.exchangeRate) AS grossSalesBase,
                   SUM(COALESCE((SELECT SUM(sr.totalBase) FROM sales_returns sr WHERE sr.salesInvoiceId=si.id AND sr.status='POSTED' AND sr.returnDate BETWEEN :from AND :to),0)) AS returnsBase,
                   SUM(CASE WHEN si.paymentType='CASH' THEN MAX(0.0, si.totalBase
                       - COALESCE((SELECT SUM(acs.amountBase) FROM sales_additional_charge_settlements acs JOIN sales_additional_charges ac ON ac.id=acs.chargeId
                                   WHERE acs.invoiceId=si.id AND acs.status='ACTIVE' AND ac.status='POSTED' AND ac.bearer='CUSTOMER' AND ac.paidBy='CUSTOMER_DIRECT'),0)) ELSE
                       COALESCE((SELECT SUM(cra.amountBase + cra.discountBase)
                                 FROM customer_receipt_allocations cra JOIN customer_receipts cr ON cr.id=cra.receiptId
                                 WHERE cra.invoiceId=si.id AND cr.receiptDate BETWEEN :from AND :to),0) END) AS collectionsBase,
                   SUM(CASE WHEN si.paymentType='CREDIT' THEN MAX(0.0,
                       si.totalBase
                       - COALESCE((SELECT SUM(acs2.amountBase) FROM sales_additional_charge_settlements acs2 JOIN sales_additional_charges ac2 ON ac2.id=acs2.chargeId WHERE acs2.invoiceId=si.id AND acs2.status='ACTIVE' AND acs2.settlementDate <= :to AND ac2.status='POSTED' AND ac2.bearer='CUSTOMER' AND ac2.paidBy='CUSTOMER_DIRECT'),0)
                       - COALESCE((SELECT SUM(sr2.totalBase) FROM sales_returns sr2 WHERE sr2.salesInvoiceId=si.id AND sr2.status='POSTED' AND sr2.settlementType='CUSTOMER_CREDIT' AND sr2.returnDate <= :to),0)
                       - COALESCE((SELECT SUM(cra2.amountBase + cra2.discountBase) FROM customer_receipt_allocations cra2 JOIN customer_receipts cr2 ON cr2.id=cra2.receiptId WHERE cra2.invoiceId=si.id AND cr2.receiptDate <= :to),0)) ELSE 0 END) AS outstandingBase,
                   SUM(COALESCE((SELECT SUM(sc.earnedBase-sc.reversedBase) FROM sales_commissions sc WHERE sc.invoiceId=si.id),0)) AS commissionBase
            FROM sales_invoices si JOIN customers c ON c.id=si.customerId
            LEFT JOIN sales_representatives srp ON srp.id=si.salesRepId
            WHERE si.status='POSTED' AND si.invoiceDate BETWEEN :from AND :to
            GROUP BY repName
        )
        SELECT repName AS salesRepName, customerCount, invoiceCount, grossSalesBase, returnsBase,
               grossSalesBase-returnsBase AS netSalesBase, collectionsBase, outstandingBase, commissionBase
        FROM reps ORDER BY netSalesBase DESC, repName
    """)
    suspend fun salesRepPerformance(from: Long, to: Long): List<SalesRepPerformanceReportRow>

    @Query("""
        SELECT sr.returnNo AS returnNo, sr.returnDate AS returnDate, si.invoiceNo AS invoiceNo,
               c.nameAr AS customerName,
               COALESCE(NULLIF(si.salesRepNameSnapshot,''), rep.fullNameAr, c.salesRepName, 'غير محدد') AS salesRepName,
               i.code AS itemCode, i.nameAr AS itemName, srl.baseQuantity AS quantityBase,
               (srl.lineNetOriginal * sr.exchangeRate) AS returnValueBase,
               srl.costBase AS returnCostBase, sr.reason AS reason
        FROM sales_returns sr
        JOIN sales_invoices si ON si.id=sr.salesInvoiceId
        JOIN customers c ON c.id=sr.customerId
        LEFT JOIN sales_representatives rep ON rep.id=si.salesRepId
        JOIN sales_return_lines srl ON srl.returnId=sr.id
        JOIN items i ON i.id=srl.itemId
        WHERE sr.status='POSTED' AND sr.returnDate BETWEEN :from AND :to
        ORDER BY sr.returnDate, sr.id, srl.id
    """)
    suspend fun salesReturnDetails(from: Long, to: Long): List<SalesReturnDetailReportRow>

    @Query("""
        WITH months AS (
            SELECT strftime('%Y-%m', si.invoiceDate/1000, 'unixepoch') AS periodKey,
                   COUNT(*) AS invoiceCount, SUM((si.grossOriginal-si.discountOriginal+si.transportOriginal+si.feesOriginal+si.riskMarginOriginal)*si.exchangeRate) AS grossSalesBase, 0.0 AS returnsBase
            FROM sales_invoices si WHERE si.status='POSTED' AND si.invoiceDate BETWEEN :from AND :to GROUP BY periodKey
            UNION ALL
            SELECT strftime('%Y-%m', sr.returnDate/1000, 'unixepoch') AS periodKey,
                   0 AS invoiceCount, 0.0 AS grossSalesBase, SUM(sr.totalBase) AS returnsBase
            FROM sales_returns sr WHERE sr.status='POSTED' AND sr.returnDate BETWEEN :from AND :to GROUP BY periodKey
        )
        SELECT periodKey, SUM(invoiceCount) AS invoiceCount, SUM(grossSalesBase) AS grossSalesBase,
               SUM(returnsBase) AS returnsBase, SUM(grossSalesBase)-SUM(returnsBase) AS netSalesBase
        FROM months GROUP BY periodKey ORDER BY periodKey
    """)
    suspend fun salesMonthlyTrend(from: Long, to: Long): List<SalesMonthlyTrendReportRow>

    @Query("""
        SELECT
          COALESCE((SELECT SUM((si.grossOriginal-si.discountOriginal+si.transportOriginal+si.feesOriginal+si.riskMarginOriginal)*si.exchangeRate) FROM sales_invoices si WHERE si.status='POSTED' AND si.invoiceDate BETWEEN :from AND :to),0) AS grossSalesBase,
          COALESCE((SELECT SUM(sr.totalBase) FROM sales_returns sr WHERE sr.status='POSTED' AND sr.returnDate BETWEEN :from AND :to),0) AS salesReturnsBase,
          COALESCE((SELECT SUM((si.grossOriginal-si.discountOriginal+si.transportOriginal+si.feesOriginal+si.riskMarginOriginal)*si.exchangeRate) FROM sales_invoices si WHERE si.status='POSTED' AND si.invoiceDate BETWEEN :from AND :to),0)
            - COALESCE((SELECT SUM(sr.totalBase) FROM sales_returns sr WHERE sr.status='POSTED' AND sr.returnDate BETWEEN :from AND :to),0) AS netSalesBase,
          COALESCE((SELECT SUM((si.grossOriginal-si.discountOriginal+si.transportOriginal+si.feesOriginal+si.riskMarginOriginal)*si.exchangeRate) FROM sales_invoices si WHERE si.status='POSTED' AND si.paymentType='CASH' AND si.invoiceDate BETWEEN :from AND :to),0) AS cashSalesBase,
          COALESCE((SELECT SUM((si.grossOriginal-si.discountOriginal+si.transportOriginal+si.feesOriginal+si.riskMarginOriginal)*si.exchangeRate) FROM sales_invoices si WHERE si.status='POSTED' AND si.paymentType='CREDIT' AND si.invoiceDate BETWEEN :from AND :to),0) AS creditSalesBase,
          COALESCE((SELECT SUM(jl.credit-jl.debit) FROM journal_lines jl JOIN journal_entries je ON je.id=jl.entryId JOIN accounts a ON a.id=jl.accountId
                    WHERE je.status='POSTED' AND je.sourceType='SALE' AND je.entryDate BETWEEN :from AND :to AND a.code='4000'),0) AS glSalesRevenueBase,
          COALESCE((SELECT SUM(jl.debit-jl.credit) FROM journal_lines jl JOIN journal_entries je ON je.id=jl.entryId JOIN accounts a ON a.id=jl.accountId
                    WHERE je.status='POSTED' AND je.sourceType='SALES_RETURN' AND je.entryDate BETWEEN :from AND :to AND a.code='4100'),0) AS glSalesReturnsBase,
          COALESCE((SELECT SUM(jl.credit-jl.debit) FROM journal_lines jl JOIN journal_entries je ON je.id=jl.entryId JOIN accounts a ON a.id=jl.accountId
                    WHERE je.status='POSTED' AND je.sourceType='SALE' AND je.entryDate BETWEEN :from AND :to AND a.code='4000'),0)
            - COALESCE((SELECT SUM(jl.debit-jl.credit) FROM journal_lines jl JOIN journal_entries je ON je.id=jl.entryId JOIN accounts a ON a.id=jl.accountId
                    WHERE je.status='POSTED' AND je.sourceType='SALES_RETURN' AND je.entryDate BETWEEN :from AND :to AND a.code='4100'),0) AS glNetSalesBase,
          COALESCE((SELECT SUM(ac.amountBase) FROM sales_additional_charges ac
                    WHERE ac.status='POSTED' AND ac.accountingTreatment='COMPANY_EXPENSE' AND ac.chargeDate BETWEEN :from AND :to),0) AS companyExpensesBase,
          COALESCE((SELECT SUM(jl.debit-jl.credit) FROM journal_lines jl JOIN journal_entries je ON je.id=jl.entryId JOIN accounts a ON a.id=jl.accountId
                    WHERE je.status='POSTED' AND je.sourceType='ADDITIONAL_CHARGE' AND je.entryDate BETWEEN :from AND :to AND a.type='EXPENSE'),0) AS glCompanyExpensesBase,
          COALESCE((SELECT SUM(s.amountBase) FROM sales_additional_charge_settlements s
                    JOIN sales_additional_charges ac ON ac.id=s.chargeId
                    WHERE s.status='ACTIVE' AND ac.status='POSTED' AND ac.accountingTreatment='SERVICE_REVENUE' AND s.settlementDate BETWEEN :from AND :to),0) AS serviceRevenueBase,
          COALESCE((SELECT SUM(jl.credit-jl.debit) FROM journal_lines jl
                    JOIN journal_entries je ON je.id=jl.entryId
                    WHERE je.status='POSTED' AND je.sourceType='SALE' AND je.entryDate BETWEEN :from AND :to
                      AND jl.accountId IN (SELECT DISTINCT revenueAccountId FROM sales_additional_charge_types)),0) AS glServiceRevenueBase,
          COALESCE((SELECT SUM(MAX(0.0, ac.amountBase - COALESCE((SELECT SUM(s.amountBase) FROM sales_additional_charge_settlements s
                    WHERE s.chargeId=ac.id AND s.status='ACTIVE' AND s.settlementDate <= :asOf),0)))
                    FROM sales_additional_charges ac
                    WHERE ac.status='POSTED' AND ac.accountingTreatment='RECOVERABLE' AND ac.paidBy!='CUSTOMER_DIRECT' AND ac.chargeDate <= :asOf),0) AS recoverableClosingBase,
          COALESCE((SELECT SUM(jl.debit-jl.credit) FROM journal_lines jl JOIN journal_entries je ON je.id=jl.entryId JOIN accounts a ON a.id=jl.accountId
                    WHERE je.status='POSTED' AND je.entryDate <= :asOf AND a.code='1310'),0) AS glRecoverableClosingBase,
          COALESCE((SELECT SUM(MAX(0.0, si.totalBase
                    - COALESCE((SELECT SUM(acs.amountBase) FROM sales_additional_charge_settlements acs JOIN sales_additional_charges ac ON ac.id=acs.chargeId WHERE acs.invoiceId=si.id AND acs.status='ACTIVE' AND acs.settlementDate <= :asOf AND ac.status='POSTED' AND ac.bearer='CUSTOMER' AND ac.paidBy='CUSTOMER_DIRECT'),0)
                    - COALESCE((SELECT SUM(sr2.totalBase) FROM sales_returns sr2 WHERE sr2.salesInvoiceId=si.id AND sr2.status='POSTED' AND sr2.settlementType='CUSTOMER_CREDIT' AND sr2.returnDate <= :asOf),0)
                    - COALESCE((SELECT SUM(cra.amountBase+cra.discountBase) FROM customer_receipt_allocations cra JOIN customer_receipts cr ON cr.id=cra.receiptId WHERE cra.invoiceId=si.id AND cr.receiptDate <= :asOf),0)))
                    FROM sales_invoices si WHERE si.status='POSTED' AND si.paymentType='CREDIT' AND si.invoiceDate <= :asOf),0)
            + COALESCE((SELECT SUM(CASE WHEN pv.voucherType='PAYMENT' THEN pv.amountBase WHEN pv.voucherType='RECEIPT' THEN -pv.amountBase ELSE 0 END)
                        FROM party_vouchers pv WHERE pv.status='POSTED' AND pv.partyType='CUSTOMER' AND pv.voucherDate <= :asOf),0) AS operationalReceivablesClosingBase,
          COALESCE((SELECT SUM(jl.debit-jl.credit) FROM journal_lines jl JOIN journal_entries je ON je.id=jl.entryId JOIN accounts a ON a.id=jl.accountId
                    WHERE je.status='POSTED' AND je.entryDate <= :asOf AND a.code='1300'),0) AS glReceivablesClosingBase
    """)
    suspend fun salesReconciliation(from: Long, to: Long, asOf: Long): SalesReconciliationReportRow

    @Query("""
        SELECT s.id AS shipmentId,
               s.shipmentNo AS shipmentNo,
               s.shipmentDate AS shipmentDate,
               s.destinationProvince AS destinationProvince,
               s.transportReference AS transportReference,
               si.id AS invoiceId,
               si.invoiceNo AS invoiceNo,
               si.invoiceDate AS invoiceDate,
               c.nameAr AS customerName,
               e.expenseType AS expenseType,
               e.paymentVoucherNo AS paymentVoucherNo,
               e.paymentReference AS paymentReference,
               e.amountBase AS expenseAmountBase,
               a.amountBase AS allocatedBase,
               COALESCE((SELECT SUM(ia.quantityBase)
                         FROM sales_shipment_invoice_item_allocations ia
                         JOIN sales_shipment_items shi ON shi.id=ia.shipmentItemId
                         WHERE shi.shipmentId=s.id AND ia.invoiceId=si.id AND ia.status='ACTIVE'),0) AS shipmentAllocatedQuantityBase,
               COALESCE((SELECT SUM(e2.amountBase) FROM sales_shipment_expenses e2 WHERE e2.shipmentId=s.id AND e2.status='POSTED'),0) AS shipmentTotalExpenseBase,
               COALESCE((SELECT SUM(a2.amountBase) FROM sales_shipment_expense_invoice_allocations a2
                         JOIN sales_shipment_expenses e3 ON e3.id=a2.shipmentExpenseId
                         WHERE e3.shipmentId=s.id AND e3.status='POSTED' AND a2.status='ACTIVE'),0) AS shipmentAllocatedExpenseBase,
               COALESCE((SELECT SUM(e4.amountBase) FROM sales_shipment_expenses e4 WHERE e4.shipmentId=s.id AND e4.status='POSTED'),0)
                 - COALESCE((SELECT SUM(a3.amountBase) FROM sales_shipment_expense_invoice_allocations a3
                             JOIN sales_shipment_expenses e5 ON e5.id=a3.shipmentExpenseId
                             WHERE e5.shipmentId=s.id AND e5.status='POSTED' AND a3.status='ACTIVE'),0) AS shipmentRemainingExpenseBase
        FROM sales_shipment_expense_invoice_allocations a
        JOIN sales_shipment_expenses e ON e.id=a.shipmentExpenseId
        JOIN sales_shipments s ON s.id=e.shipmentId
        JOIN sales_invoices si ON si.id=a.invoiceId
        JOIN customers c ON c.id=si.customerId
        WHERE a.status='ACTIVE' AND e.status='POSTED' AND si.status='POSTED' AND si.invoiceDate BETWEEN :from AND :to
        ORDER BY s.shipmentDate,s.id,si.invoiceDate,si.id,e.id
    """)
    suspend fun salesShipmentCostAllocations(from: Long, to: Long): List<SalesShipmentCostAllocationReportRow>

    @Query("""
        SELECT ac.id AS chargeId,
               ac.chargeNo AS chargeNo,
               ac.chargeDate AS chargeDate,
               c.nameAr AS customerName,
               t.nameAr AS chargeTypeName,
               ac.bearer AS bearer,
               ac.accountingTreatment AS accountingTreatment,
               ac.principalAgentModeSnapshot AS principalAgentMode,
               ac.paymentStatus AS paymentStatus,
               ac.paidBy AS paidBy,
               ac.currencyCode AS currencyCode,
               ac.amountOriginal AS amountOriginal,
               ac.amountBase AS amountBase,
               COALESCE((SELECT SUM(p.amountBase) FROM sales_additional_charge_payments p
                         WHERE p.chargeId=ac.id AND p.paymentDate <= :to),0) AS paidBase,
               COALESCE((SELECT SUM(s.amountBase) FROM sales_additional_charge_settlements s
                         WHERE s.chargeId=ac.id AND s.status='ACTIVE' AND s.settlementDate <= :to),0) AS settledBase,
               MAX(0.0, ac.amountBase - COALESCE((SELECT SUM(s2.amountBase) FROM sales_additional_charge_settlements s2
                         WHERE s2.chargeId=ac.id AND s2.status='ACTIVE' AND s2.settlementDate <= :to),0)) AS remainingBase,
               CASE WHEN ac.accountingTreatment='SERVICE_REVENUE' THEN
                    COALESCE((SELECT SUM(s3.amountBase) FROM sales_additional_charge_settlements s3
                              WHERE s3.chargeId=ac.id AND s3.status='ACTIVE' AND s3.settlementDate BETWEEN :from AND :to),0)
                    ELSE 0 END AS serviceRevenueBase
        FROM sales_additional_charges ac
        JOIN customers c ON c.id=ac.customerId
        JOIN sales_additional_charge_types t ON t.id=ac.chargeTypeId
        WHERE ac.status='POSTED' AND ac.chargeDate <= :to
          AND (ac.chargeDate BETWEEN :from AND :to
               OR ac.amountBase - COALESCE((SELECT SUM(s4.amountBase) FROM sales_additional_charge_settlements s4
                                            WHERE s4.chargeId=ac.id AND s4.status='ACTIVE' AND s4.settlementDate <= :to),0) > 0.000001
               OR EXISTS(SELECT 1 FROM sales_additional_charge_settlements sx
                         WHERE sx.chargeId=ac.id AND sx.status='ACTIVE' AND sx.settlementDate BETWEEN :from AND :to))
        ORDER BY ac.chargeDate, ac.id
    """)
    suspend fun salesAdditionalCharges(from: Long, to: Long): List<SalesAdditionalChargeReportRow>

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
        SELECT pi.id AS invoiceId,
               pi.invoiceNo AS invoiceNo,
               pi.supplierInvoiceNo AS supplierInvoiceNo,
               pi.invoiceDate AS invoiceDate,
               s.nameAr AS supplierName,
               pi.currencyCode AS currencyCode,
               pi.exchangeRate AS exchangeRate,
               pi.paymentType AS paymentType,
               pi.subtotalOriginal AS subtotalOriginal,
               pi.discountOriginal AS discountOriginal,
               pi.freightOriginal AS freightOriginal,
               pi.customsOriginal AS customsOriginal,
               pi.otherChargesOriginal AS otherChargesOriginal,
               pi.totalOriginal AS totalOriginal,
               pi.totalBase AS totalBase,
               pl.id AS lineId,
               i.code AS itemCode,
               i.nameAr AS itemName,
               u.nameAr AS unitName,
               pl.quantity AS quantity,
               pl.baseQuantity AS baseQuantity,
               pl.unitPriceOriginal AS unitPriceOriginal,
               pl.lineTotalOriginal AS lineTotalOriginal,
               pl.lotNo AS lotNo,
               pl.expiryDate AS expiryDate
        FROM purchase_invoices pi
        JOIN suppliers s ON s.id=pi.supplierId
        JOIN purchase_lines pl ON pl.invoiceId=pi.id
        JOIN items i ON i.id=pl.itemId
        JOIN units u ON u.id=pl.unitId
        WHERE pi.status='POSTED' AND pi.invoiceDate BETWEEN :from AND :to
        ORDER BY pi.invoiceDate, pi.id, pl.id
    """)
    suspend fun purchaseInvoiceDetails(from: Long, to: Long): List<PurchaseInvoiceDetailReportRow>


    @Query("""
        SELECT pi.id AS invoiceId,
               pi.invoiceNo AS invoiceNo,
               pi.supplierInvoiceNo AS supplierInvoiceNo,
               pi.invoiceDate AS invoiceDate,
               pi.dueDate AS dueDate,
               s.id AS supplierId,
               s.nameAr AS supplierName,
               pi.currencyCode AS currencyCode,
               pi.exchangeRate AS exchangeRate,
               pi.paymentType AS paymentType,
               pi.subtotalOriginal AS subtotalOriginal,
               pi.discountOriginal AS discountOriginal,
               0.0 AS taxOriginal,
               (pi.freightOriginal + pi.customsOriginal + pi.otherChargesOriginal) AS chargesOriginal,
               pi.totalOriginal AS totalOriginal,
               pi.totalBase AS totalBase,
               CASE WHEN pi.paymentType='CREDIT' THEN
                   COALESCE((SELECT SUM(pr.totalBase)
                             FROM purchase_returns pr
                             WHERE pr.purchaseInvoiceId=pi.id
                               AND pr.status='POSTED'
                               AND pr.settlementType='SUPPLIER_CREDIT'
                               AND pr.returnDate <= :asOf),0)
                   ELSE 0 END AS supplierCreditReturnsBase,
               CASE WHEN pi.paymentType='CASH' THEN pi.totalBase ELSE
                   COALESCE((SELECT SUM(spa.allocatedBase)
                             FROM supplier_payment_allocations spa
                             JOIN supplier_payments sp ON sp.id=spa.paymentId
                             WHERE spa.invoiceId=pi.id
                               AND sp.paymentDate <= :asOf),0)
                   END AS paidBase,
               CASE WHEN pi.paymentType='CASH' THEN 0.0 ELSE
                   MAX(0,
                       pi.totalBase
                       - COALESCE((SELECT SUM(pr2.totalBase)
                                   FROM purchase_returns pr2
                                   WHERE pr2.purchaseInvoiceId=pi.id
                                     AND pr2.status='POSTED'
                                     AND pr2.settlementType='SUPPLIER_CREDIT'
                                     AND pr2.returnDate <= :asOf),0)
                       - COALESCE((SELECT SUM(spa2.allocatedBase)
                                   FROM supplier_payment_allocations spa2
                                   JOIN supplier_payments sp2 ON sp2.id=spa2.paymentId
                                   WHERE spa2.invoiceId=pi.id
                                     AND sp2.paymentDate <= :asOf),0)
                   )
                   END AS outstandingBase
        FROM purchase_invoices pi
        JOIN suppliers s ON s.id=pi.supplierId
        WHERE pi.status='POSTED'
          AND pi.invoiceDate BETWEEN :from AND :to
        ORDER BY pi.invoiceDate, pi.id
    """)
    suspend fun purchaseInvoiceAccounting(from: Long, to: Long, asOf: Long): List<PurchaseInvoiceAccountingReportRow>

    @Query("""
        SELECT pr.id AS returnId,
               pr.returnNo AS returnNo,
               pr.purchaseInvoiceId AS purchaseInvoiceId,
               pi.invoiceNo AS invoiceNo,
               s.nameAr AS supplierName,
               pr.returnDate AS returnDate,
               pr.currencyCode AS currencyCode,
               pr.settlementType AS settlementType,
               pi.paymentType AS originalPaymentType,
               pr.totalOriginal AS totalOriginal,
               pr.totalBase AS totalBase
        FROM purchase_returns pr
        JOIN purchase_invoices pi ON pi.id=pr.purchaseInvoiceId
        JOIN suppliers s ON s.id=pr.supplierId
        WHERE pr.status='POSTED'
          AND pr.returnDate BETWEEN :from AND :to
        ORDER BY pr.returnDate, pr.id
    """)
    suspend fun purchaseReturnsForReport(from: Long, to: Long): List<PurchaseReturnReportRow>

    @Query("""
        WITH p AS (
            SELECT pl.itemId AS itemId,
                   SUM(pl.baseQuantity) AS grossQtyBase,
                   SUM(pl.baseQuantity * pl.unitCostBase) AS grossValueBase
            FROM purchase_lines pl
            JOIN purchase_invoices pi ON pi.id=pl.invoiceId
            WHERE pi.status='POSTED' AND pi.invoiceDate BETWEEN :from AND :to
            GROUP BY pl.itemId
        ),
        r AS (
            SELECT prl.itemId AS itemId,
                   SUM(prl.baseQuantity) AS returnedQtyBase,
                   SUM(prl.baseQuantity * prl.unitCostBase) AS returnedValueBase
            FROM purchase_return_lines prl
            JOIN purchase_returns pr ON pr.id=prl.returnId
            WHERE pr.status='POSTED' AND pr.returnDate BETWEEN :from AND :to
            GROUP BY prl.itemId
        ),
        ids AS (
            SELECT itemId FROM p
            UNION
            SELECT itemId FROM r
        )
        SELECT i.id AS itemId,
               i.code AS itemCode,
               i.nameAr AS itemName,
               u.nameAr AS baseUnitName,
               COALESCE(p.grossQtyBase,0) AS grossQtyBase,
               COALESCE(r.returnedQtyBase,0) AS returnedQtyBase,
               COALESCE(p.grossQtyBase,0) - COALESCE(r.returnedQtyBase,0) AS netQtyBase,
               COALESCE(p.grossValueBase,0) AS grossValueBase,
               COALESCE(r.returnedValueBase,0) AS returnedValueBase,
               COALESCE(p.grossValueBase,0) - COALESCE(r.returnedValueBase,0) AS netValueBase
        FROM ids x
        JOIN items i ON i.id=x.itemId
        JOIN units u ON u.id=i.baseUnitId
        LEFT JOIN p ON p.itemId=i.id
        LEFT JOIN r ON r.itemId=i.id
        ORDER BY netValueBase DESC, i.nameAr
    """)
    suspend fun purchaseItemAnalysis(from: Long, to: Long): List<PurchaseItemAnalysisReportRow>

    @Query("""
        SELECT
          COALESCE((SELECT SUM(pi.totalBase)
                    FROM purchase_invoices pi
                    WHERE pi.status='POSTED' AND pi.invoiceDate BETWEEN :from AND :to),0) AS grossPurchasesBase,

          COALESCE((SELECT SUM(pr.totalBase)
                    FROM purchase_returns pr
                    WHERE pr.status='POSTED' AND pr.returnDate BETWEEN :from AND :to),0) AS purchaseReturnsBase,

          COALESCE((SELECT SUM(pi.totalBase)
                    FROM purchase_invoices pi
                    WHERE pi.status='POSTED' AND pi.invoiceDate BETWEEN :from AND :to),0)
          - COALESCE((SELECT SUM(pr.totalBase)
                      FROM purchase_returns pr
                      WHERE pr.status='POSTED' AND pr.returnDate BETWEEN :from AND :to),0) AS netPurchasesBase,

          COALESCE((SELECT SUM(CAST(jl.debitScaled AS REAL) - CAST(jl.creditScaled AS REAL)) / 10000.0
                    FROM journal_lines jl
                    JOIN journal_entries je ON je.id=jl.entryId
                    JOIN accounts a ON a.id=jl.accountId
                    WHERE je.status='POSTED'
                      AND a.code='1200'
                      AND je.sourceType IN ('PURCHASE','PURCHASE_RETURN')
                      AND je.entryDate BETWEEN :from AND :to),0) AS inventoryGlNetBase,

          COALESCE((SELECT SUM(pi.totalBase)
                    FROM purchase_invoices pi
                    WHERE pi.status='POSTED'
                      AND pi.paymentType='CREDIT'
                      AND pi.invoiceDate BETWEEN :from AND :to),0) AS creditPurchasesBase,

          COALESCE((SELECT SUM(pr.totalBase)
                    FROM purchase_returns pr
                    JOIN purchase_invoices pi ON pi.id=pr.purchaseInvoiceId
                    WHERE pr.status='POSTED'
                      AND pr.settlementType='SUPPLIER_CREDIT'
                      AND pi.paymentType='CREDIT'
                      AND pr.returnDate BETWEEN :from AND :to),0) AS supplierCreditReturnsBase,

          COALESCE((SELECT SUM(pi.totalBase)
                    FROM purchase_invoices pi
                    WHERE pi.status='POSTED'
                      AND pi.paymentType='CREDIT'
                      AND pi.invoiceDate BETWEEN :from AND :to),0)
          - COALESCE((SELECT SUM(pr.totalBase)
                      FROM purchase_returns pr
                      JOIN purchase_invoices pi ON pi.id=pr.purchaseInvoiceId
                      WHERE pr.status='POSTED'
                        AND pr.settlementType='SUPPLIER_CREDIT'
                        AND pi.paymentType='CREDIT'
                        AND pr.returnDate BETWEEN :from AND :to),0) AS apDocumentOperationalNetBase,

          COALESCE((SELECT SUM(CAST(jl.creditScaled AS REAL) - CAST(jl.debitScaled AS REAL)) / 10000.0
                    FROM journal_lines jl
                    JOIN journal_entries je ON je.id=jl.entryId
                    JOIN accounts a ON a.id=jl.accountId
                    WHERE je.status='POSTED'
                      AND a.code='2100'
                      AND je.sourceType IN ('PURCHASE','PURCHASE_RETURN')
                      AND je.entryDate BETWEEN :from AND :to),0) AS apDocumentGlNetBase,

          (
            COALESCE((SELECT SUM(pi.totalBase)
                      FROM purchase_invoices pi
                      WHERE pi.status='POSTED'
                        AND pi.paymentType='CREDIT'
                        AND pi.invoiceDate <= :asOf),0)
            - COALESCE((SELECT SUM(pr.totalBase)
                        FROM purchase_returns pr
                        JOIN purchase_invoices pi ON pi.id=pr.purchaseInvoiceId
                        WHERE pr.status='POSTED'
                          AND pr.settlementType='SUPPLIER_CREDIT'
                          AND pi.paymentType='CREDIT'
                          AND pr.returnDate <= :asOf),0)
            - COALESCE((SELECT SUM(spa.allocatedBase)
                        FROM supplier_payment_allocations spa
                        JOIN supplier_payments sp ON sp.id=spa.paymentId
                        JOIN purchase_invoices pi ON pi.id=spa.invoiceId
                        WHERE pi.paymentType='CREDIT'
                          AND sp.paymentDate <= :asOf),0)
          ) AS openSupplierInvoicesBase,

          COALESCE((SELECT SUM(
              CASE WHEN pv.voucherDate <= :asOf
                   THEN CASE WHEN pv.voucherType='RECEIPT' THEN pv.amountBase
                             WHEN pv.voucherType='PAYMENT' THEN -pv.amountBase ELSE 0 END
                   ELSE 0 END
              +
              CASE WHEN pv.status='REVERSED' AND pv.reversedAt IS NOT NULL AND pv.reversedAt <= :asOf
                   THEN CASE WHEN pv.voucherType='RECEIPT' THEN -pv.amountBase
                             WHEN pv.voucherType='PAYMENT' THEN pv.amountBase ELSE 0 END
                   ELSE 0 END
          )
          FROM party_vouchers pv
          WHERE pv.supplierId IS NOT NULL
            AND pv.voucherType IN ('RECEIPT','PAYMENT')),0) AS supplierVoucherAdjustmentBase,

          (
            (
              COALESCE((SELECT SUM(pi.totalBase)
                        FROM purchase_invoices pi
                        WHERE pi.status='POSTED'
                          AND pi.paymentType='CREDIT'
                          AND pi.invoiceDate <= :asOf),0)
              - COALESCE((SELECT SUM(pr.totalBase)
                          FROM purchase_returns pr
                          JOIN purchase_invoices pi ON pi.id=pr.purchaseInvoiceId
                          WHERE pr.status='POSTED'
                            AND pr.settlementType='SUPPLIER_CREDIT'
                            AND pi.paymentType='CREDIT'
                            AND pr.returnDate <= :asOf),0)
              - COALESCE((SELECT SUM(spa.allocatedBase)
                          FROM supplier_payment_allocations spa
                          JOIN supplier_payments sp ON sp.id=spa.paymentId
                          JOIN purchase_invoices pi ON pi.id=spa.invoiceId
                          WHERE pi.paymentType='CREDIT'
                            AND sp.paymentDate <= :asOf),0)
            )
            +
            COALESCE((SELECT SUM(
                CASE WHEN pv.voucherDate <= :asOf
                     THEN CASE WHEN pv.voucherType='RECEIPT' THEN pv.amountBase
                               WHEN pv.voucherType='PAYMENT' THEN -pv.amountBase ELSE 0 END
                     ELSE 0 END
                +
                CASE WHEN pv.status='REVERSED' AND pv.reversedAt IS NOT NULL AND pv.reversedAt <= :asOf
                     THEN CASE WHEN pv.voucherType='RECEIPT' THEN -pv.amountBase
                               WHEN pv.voucherType='PAYMENT' THEN pv.amountBase ELSE 0 END
                     ELSE 0 END
            )
            FROM party_vouchers pv
            WHERE pv.supplierId IS NOT NULL
              AND pv.voucherType IN ('RECEIPT','PAYMENT')),0)
          ) AS operationalPayablesClosingBase,

          COALESCE((SELECT SUM(CAST(jl.creditScaled AS REAL) - CAST(jl.debitScaled AS REAL)) / 10000.0
                    FROM journal_lines jl
                    JOIN journal_entries je ON je.id=jl.entryId
                    JOIN accounts a ON a.id=jl.accountId
                    WHERE je.status='POSTED'
                      AND a.code='2100'
                      AND je.entryDate <= :asOf),0) AS apGlClosingBase
    """)
    suspend fun purchaseReconciliation(from: Long, to: Long, asOf: Long): PurchaseReconciliationReportRow

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
                   - COALESCE((SELECT SUM(cra.amountBase + cra.discountBase)
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
               CASE WHEN je.sourceType IN ('TRANSFER', 'TREASURY_TRANSFER') OR (
                   je.sourceType = 'REVERSAL' AND EXISTS(
                       SELECT 1 FROM journal_entries original
                       WHERE original.id = CAST(je.sourceId AS INTEGER)
                         AND original.sourceType IN ('TRANSFER', 'TREASURY_TRANSFER')
                   )
               ) THEN 1 ELSE 0 END AS isInternalTransfer
        FROM treasury_accounts t
        JOIN journal_lines jl ON jl.accountId = t.accountId
        JOIN journal_entries je ON je.id = jl.entryId
        WHERE je.status = 'POSTED' AND je.entryDate <= :to
        ORDER BY t.kind, t.nameAr, je.entryDate, je.id, jl.id
    """)
    suspend fun treasuryMovementsThrough(to: Long): List<TreasuryMovementReportRow>


    @Query("""
        SELECT po.id AS orderId, po.orderNo AS orderNo,
               prod.code AS productCode, prod.nameAr AS productName, pb.batchNo AS batchNo,
               item.code AS itemCode, item.nameAr AS itemName, u.nameAr AS unitName,
               pm.standardQtyBase AS standardQtyBase,
               pm.issuedQtyBase AS actualQtyBase,
               pm.issueCostBase AS actualCostBase,
               CASE WHEN ABS(pm.issuedQtyBase) > 0.000000001 THEN pm.issueCostBase / pm.issuedQtyBase ELSE 0 END AS averageUnitCostBase,
               (pm.issuedQtyBase - pm.standardQtyBase) AS quantityVarianceBase,
               (pm.issuedQtyBase - pm.standardQtyBase) *
                   CASE WHEN ABS(pm.issuedQtyBase) > 0.000000001 THEN pm.issueCostBase / pm.issuedQtyBase ELSE 0 END AS varianceCostBase,
               CASE WHEN ABS(pm.standardQtyBase) > 0.000000001
                    THEN ((pm.issuedQtyBase - pm.standardQtyBase) * 100.0 / pm.standardQtyBase)
                    ELSE 0 END AS variancePct
        FROM production_materials pm
        JOIN production_orders po ON po.id=pm.orderId
        JOIN items prod ON prod.id=po.productItemId
        JOIN items item ON item.id=pm.itemId
        JOIN units u ON u.id=item.baseUnitId
        LEFT JOIN production_batches pb ON pb.orderId=po.id
        WHERE COALESCE(pb.manufactureDate, po.plannedDate) BETWEEN :from AND :to
        ORDER BY po.orderNo, item.nameAr
    """)
    suspend fun productionMaterialVariance(from: Long, to: Long): List<ProductionMaterialVarianceReportRow>

    @Query("""
        SELECT po.id AS orderId, po.orderNo AS orderNo,
               COALESCE(SUM(
                   CASE WHEN pv.voucherDate BETWEEN :from AND :to THEN pv.amountBase ELSE 0 END
                   + CASE WHEN pv.status='REVERSED' AND pv.reversedAt IS NOT NULL AND pv.reversedAt BETWEEN :from AND :to
                          THEN -pv.amountBase ELSE 0 END
               ),0) AS overheadBase
        FROM production_orders po
        JOIN expense_dimensions ed ON ed.referenceType='PRODUCTION_ORDER' AND ed.referenceId=po.id AND ed.costCenterCode='PRODUCTION'
        JOIN party_vouchers pv ON pv.id=ed.partyVoucherId
        WHERE pv.voucherType='EXPENSE' AND pv.status IN ('POSTED','REVERSED')
          AND (pv.voucherDate BETWEEN :from AND :to OR (pv.reversedAt IS NOT NULL AND pv.reversedAt BETWEEN :from AND :to))
        GROUP BY po.id, po.orderNo
        ORDER BY po.orderNo
    """)
    suspend fun productionOrderOverhead(from: Long, to: Long): List<ProductionOrderOverheadReportRow>

    @Query("""
        SELECT a.code AS accountCode, a.nameAr AS accountName,
               COALESCE(SUM(
                   CASE WHEN pv.voucherDate BETWEEN :from AND :to THEN pv.amountBase ELSE 0 END
                   + CASE WHEN pv.status='REVERSED' AND pv.reversedAt IS NOT NULL AND pv.reversedAt BETWEEN :from AND :to
                          THEN -pv.amountBase ELSE 0 END
               ),0) AS amountBase
        FROM expense_dimensions ed
        JOIN party_vouchers pv ON pv.id=ed.partyVoucherId
        JOIN accounts a ON a.id=pv.offsetAccountId
        WHERE ed.costCenterCode='PRODUCTION'
          AND pv.voucherType='EXPENSE' AND pv.status IN ('POSTED','REVERSED')
          AND (pv.voucherDate BETWEEN :from AND :to OR (pv.reversedAt IS NOT NULL AND pv.reversedAt BETWEEN :from AND :to))
        GROUP BY a.id, a.code, a.nameAr
        HAVING ABS(amountBase) > 0.000000001
        ORDER BY ABS(amountBase) DESC, a.code
    """)
    suspend fun productionOverheadAccounts(from: Long, to: Long): List<ProductionOverheadAccountReportRow>

    @Query("""
        SELECT
          COALESCE(SUM(CASE WHEN je.entryDate < :from THEN (CAST(jl.debitScaled AS REAL)-CAST(jl.creditScaled AS REAL))/10000.0 ELSE 0 END),0) AS openingWipBase,
          COALESCE(SUM(CASE WHEN je.entryDate BETWEEN :from AND :to AND je.sourceType='PRODUCTION_ISSUE' THEN CAST(jl.debitScaled AS REAL)/10000.0 ELSE 0 END),0) AS materialAddedBase,
          COALESCE(SUM(CASE WHEN je.entryDate BETWEEN :from AND :to AND je.sourceType='PRODUCTION_LABOR' THEN CAST(jl.debitScaled AS REAL)/10000.0 ELSE 0 END),0) AS laborAddedBase,
          COALESCE(SUM(CASE WHEN je.entryDate BETWEEN :from AND :to AND je.sourceType='PRODUCTION_OVERHEAD' THEN CAST(jl.debitScaled AS REAL)/10000.0 ELSE 0 END),0) AS overheadAddedBase,
          COALESCE(SUM(CASE WHEN je.entryDate BETWEEN :from AND :to AND je.sourceType='PRODUCTION_RECEIPT' THEN CAST(jl.creditScaled AS REAL)/10000.0 ELSE 0 END),0) AS finishedTransferredBase,
          COALESCE(SUM(CASE WHEN je.entryDate BETWEEN :from AND :to AND je.sourceType='PRODUCTION_REJECT' THEN CAST(jl.creditScaled AS REAL)/10000.0 ELSE 0 END),0) AS rejectedTransferredBase,
          COALESCE(SUM(CASE WHEN je.entryDate BETWEEN :from AND :to AND je.sourceType NOT IN ('PRODUCTION_ISSUE','PRODUCTION_LABOR','PRODUCTION_OVERHEAD','PRODUCTION_RECEIPT','PRODUCTION_REJECT')
                            THEN (CAST(jl.debitScaled AS REAL)-CAST(jl.creditScaled AS REAL))/10000.0 ELSE 0 END),0) AS otherNetMovementBase,
          COALESCE(SUM(CASE WHEN je.entryDate <= :to THEN (CAST(jl.debitScaled AS REAL)-CAST(jl.creditScaled AS REAL))/10000.0 ELSE 0 END),0) AS closingWipBase
        FROM journal_lines jl
        JOIN journal_entries je ON je.id=jl.entryId
        JOIN accounts a ON a.id=jl.accountId
        WHERE je.status='POSTED' AND a.code='1210' AND je.entryDate <= :to
    """)
    suspend fun productionWip(from: Long, to: Long): ProductionWipReportRow

    @Query("""
        SELECT po.id AS orderId, po.orderNo AS orderNo, prod.code AS productCode, prod.nameAr AS productName,
               pb.batchNo AS batchNo, COALESCE(pb.actualOutputQtyBase,0) AS actualQtyBase,
               COALESCE(pb.rejectedQtyBase,0) AS rejectedQtyBase, COALESCE(pb.scrapQtyBase,0) AS scrapQtyBase,
               COALESCE((SELECT SUM(pi.totalCostBase) FROM production_issues pi WHERE pi.orderId=po.id),0) + po.directLaborCostBase AS baseProductionCost,
               COALESCE(
                   (SELECT CASE WHEN TRIM(COALESCE(nc.rootCause,''))<>'' THEN nc.rootCause ELSE nc.description END
                    FROM non_conformances nc WHERE nc.batchId=pb.id ORDER BY nc.createdAt DESC, nc.id DESC LIMIT 1),
                   CASE WHEN TRIM(COALESCE(pb.notes,''))<>'' THEN pb.notes ELSE 'غير مسجل' END
               ) AS reason,
               COALESCE(
                   (SELECT NULLIF(TRIM(nc2.responsible),'') FROM non_conformances nc2 WHERE nc2.batchId=pb.id ORDER BY nc2.createdAt DESC, nc2.id DESC LIMIT 1),
                   e.fullNameAr,
                   'أمر ' || po.orderNo
               ) AS responsible
        FROM production_orders po
        JOIN items prod ON prod.id=po.productItemId
        JOIN production_batches pb ON pb.orderId=po.id
        LEFT JOIN production_operator_assignments pa ON pa.orderId=po.id
        LEFT JOIN employees e ON e.id=pa.employeeId
        WHERE pb.manufactureDate BETWEEN :from AND :to
          AND (COALESCE(pb.rejectedQtyBase,0) > 0.000000001 OR COALESCE(pb.scrapQtyBase,0) > 0.000000001)
        ORDER BY pb.manufactureDate DESC, po.id DESC
    """)
    suspend fun productionLosses(from: Long, to: Long): List<ProductionLossReportRow>

    @Query("""
        SELECT e.id AS employeeId, e.fullNameAr AS employeeName,
               COUNT(DISTINCT po.id) AS orderCount,
               COALESCE(SUM(pb.acceptedQtyBase),0) AS acceptedQtyBase,
               COALESCE(SUM(po.directLaborCostBase),0) AS laborCostBase
        FROM production_operator_assignments pa
        JOIN employees e ON e.id=pa.employeeId
        JOIN production_orders po ON po.id=pa.orderId
        LEFT JOIN production_batches pb ON pb.orderId=po.id
        WHERE COALESCE(pb.manufactureDate,po.plannedDate) BETWEEN :from AND :to
        GROUP BY e.id, e.fullNameAr
        ORDER BY acceptedQtyBase DESC, e.fullNameAr
    """)
    suspend fun productionLaborProductivity(from: Long, to: Long): List<ProductionLaborProductivityReportRow>

    @Query("""
        SELECT 'أمر صيانة' AS sourceType, mwo.workOrderNo AS sourceNo, a.nameAr AS assetName,
               mwo.openedAt AS eventDate,
               CASE WHEN TRIM(COALESCE(mwo.problem,''))<>'' THEN mwo.problem ELSE mwo.actionTaken END AS reason,
               mwo.downtimeMinutes AS downtimeMinutes, mwo.status AS status,
               COALESCE((SELECT GROUP_CONCAT(po2.orderNo, '، ')
                         FROM production_orders po2 LEFT JOIN production_batches pb2 ON pb2.orderId=po2.id
                         WHERE po2.primaryAssetId=a.id AND COALESCE(pb2.manufactureDate,po2.plannedDate) BETWEEN :from AND :to),'') AS relatedOrders
        FROM maintenance_work_orders mwo JOIN assets a ON a.id=mwo.assetId
        WHERE mwo.openedAt BETWEEN :from AND :to
        UNION ALL
        SELECT 'عطل' AS sourceType, b.breakdownNo AS sourceNo, a2.nameAr AS assetName,
               b.occurredAt AS eventDate, b.description AS reason,
               b.downtimeMinutes AS downtimeMinutes, b.status AS status,
               COALESCE((SELECT GROUP_CONCAT(po3.orderNo, '، ')
                         FROM production_orders po3 LEFT JOIN production_batches pb3 ON pb3.orderId=po3.id
                         WHERE po3.primaryAssetId=a2.id AND COALESCE(pb3.manufactureDate,po3.plannedDate) BETWEEN :from AND :to),'') AS relatedOrders
        FROM breakdowns b JOIN assets a2 ON a2.id=b.assetId
        WHERE b.occurredAt BETWEEN :from AND :to AND b.workOrderId IS NULL
        ORDER BY eventDate DESC
    """)
    suspend fun productionDowntime(from: Long, to: Long): List<ProductionDowntimeReportRow>

    @Query("""
        SELECT po.id AS orderId, po.orderNo AS orderNo, prod.code AS productCode, prod.nameAr AS productName,
               pb.batchNo AS finishedLotNo, raw.code AS rawItemCode, raw.nameAr AS rawItemName,
               pi.lotNo AS rawLotNo, pi.expiryDate AS rawExpiryDate,
               COALESCE(SUM(pi.quantityBase),0) AS issuedQtyBase,
               COALESCE(SUM(pi.totalCostBase),0) AS issueCostBase
        FROM production_issues pi
        JOIN production_orders po ON po.id=pi.orderId
        JOIN items prod ON prod.id=po.productItemId
        JOIN items raw ON raw.id=pi.itemId
        LEFT JOIN production_batches pb ON pb.orderId=po.id
        WHERE COALESCE(pb.manufactureDate,po.plannedDate) BETWEEN :from AND :to
        GROUP BY po.id, po.orderNo, prod.code, prod.nameAr, pb.batchNo, raw.id, raw.code, raw.nameAr, pi.lotNo, pi.expiryDate
        HAVING COALESCE(SUM(pi.quantityBase),0) > 0.000000001
        ORDER BY po.orderNo, raw.nameAr, pi.lotNo
    """)
    suspend fun productionLotTrace(from: Long, to: Long): List<ProductionLotTraceReportRow>

    @Query("""
        SELECT
          COALESCE((SELECT SUM(pi.totalCostBase)
                    FROM production_issues pi JOIN production_orders po ON po.id=pi.orderId
                    LEFT JOIN production_batches pb ON pb.orderId=po.id
                    WHERE COALESCE(pb.manufactureDate,po.plannedDate) BETWEEN :from AND :to),0) AS operationalMaterialCostBase,
          COALESCE((SELECT SUM(CAST(jl.debitScaled AS REAL))/10000.0
                    FROM journal_lines jl JOIN journal_entries je ON je.id=jl.entryId JOIN accounts a ON a.id=jl.accountId
                    WHERE je.status='POSTED' AND a.code='1210' AND je.sourceType='PRODUCTION_ISSUE' AND je.entryDate BETWEEN :from AND :to),0) AS glMaterialToWipBase,
          COALESCE((SELECT SUM(po.directLaborCostBase) FROM production_orders po LEFT JOIN production_batches pb ON pb.orderId=po.id
                    WHERE COALESCE(pb.manufactureDate,po.plannedDate) BETWEEN :from AND :to),0) AS operationalLaborCostBase,
          COALESCE((SELECT SUM(CAST(jl2.debitScaled AS REAL))/10000.0
                    FROM journal_lines jl2 JOIN journal_entries je2 ON je2.id=jl2.entryId JOIN accounts a2 ON a2.id=jl2.accountId
                    WHERE je2.status='POSTED' AND a2.code='1210' AND je2.sourceType='PRODUCTION_LABOR' AND je2.entryDate BETWEEN :from AND :to),0) AS glLaborToWipBase,
          COALESCE((SELECT SUM(sm.quantityBase*sm.unitCostBase) FROM stock_movements sm
                    WHERE sm.movementType='PRODUCTION_RECEIPT' AND sm.movementDate BETWEEN :from AND :to),0) AS operationalFinishedReceiptBase,
          COALESCE((SELECT SUM(CAST(jl3.debitScaled AS REAL))/10000.0
                    FROM journal_lines jl3 JOIN journal_entries je3 ON je3.id=jl3.entryId JOIN accounts a3 ON a3.id=jl3.accountId
                    WHERE je3.status='POSTED' AND a3.code='1200' AND je3.sourceType='PRODUCTION_RECEIPT' AND je3.entryDate BETWEEN :from AND :to),0) AS glFinishedReceiptBase,
          COALESCE((SELECT SUM(CAST(jl4.debitScaled AS REAL))/10000.0
                    FROM journal_lines jl4 JOIN journal_entries je4 ON je4.id=jl4.entryId JOIN accounts a4 ON a4.id=jl4.accountId
                    WHERE je4.status='POSTED' AND a4.code='6300' AND je4.sourceType='PRODUCTION_REJECT' AND je4.entryDate BETWEEN :from AND :to),0) AS glProductionLossBase,
          COALESCE((SELECT SUM(CAST(jl5.debitScaled AS REAL)-CAST(jl5.creditScaled AS REAL))/10000.0
                    FROM journal_lines jl5 JOIN journal_entries je5 ON je5.id=jl5.entryId JOIN accounts a5 ON a5.id=jl5.accountId
                    WHERE je5.status='POSTED' AND a5.code='1210' AND je5.entryDate <= :to),0) AS closingWipGlBase
    """)
    suspend fun productionAccountingReconciliation(from: Long, to: Long): ProductionAccountingReconciliationReportRow

}
