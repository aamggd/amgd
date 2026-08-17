package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers WHERE isActive = 1 ORDER BY nameAr")
    fun observeAll(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE isActive = 1 ORDER BY nameAr")
    suspend fun allActive(): List<SupplierEntity>

    @Query("SELECT * FROM suppliers ORDER BY nameAr")
    suspend fun allSuppliers(): List<SupplierEntity>

    @Query("SELECT * FROM suppliers WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE code = :code LIMIT 1")
    suspend fun byCode(code: String): SupplierEntity?

    @Query("SELECT COUNT(*) FROM suppliers")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM suppliers")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: SupplierEntity): Long
}

@Dao
interface ItemUnitConversionDao {
    @Query("SELECT * FROM item_unit_conversions WHERE itemId = :itemId AND isActive = 1 ORDER BY factorToBase")
    suspend fun forItem(itemId: Long): List<ItemUnitConversionEntity>

    @Query("SELECT * FROM item_unit_conversions ORDER BY itemId, isActive DESC, factorToBase")
    fun observeAllIncludingInactive(): Flow<List<ItemUnitConversionEntity>>

    @Query("SELECT * FROM item_unit_conversions WHERE itemId = :itemId AND unitId = :unitId LIMIT 1")
    suspend fun byItemAndUnitAny(itemId: Long, unitId: Long): ItemUnitConversionEntity?

    @Query("SELECT * FROM item_unit_conversions WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): ItemUnitConversionEntity?

    @Query("SELECT * FROM item_unit_conversions WHERE itemId = :itemId AND unitId = :unitId AND isActive = 1 LIMIT 1")
    suspend fun byItemAndUnit(itemId: Long, unitId: Long): ItemUnitConversionEntity?

    @Query("SELECT COUNT(*) FROM item_unit_conversions WHERE unitId = :unitId AND isActive = 1")
    suspend fun activeCountForUnit(unitId: Long): Int

    @Query("SELECT COUNT(*) FROM item_unit_conversions WHERE TRIM(COALESCE(barcode, '')) = :barcode AND id != :excludeId")
    suspend fun barcodeConflictCount(barcode: String, excludeId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: ItemUnitConversionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ItemUnitConversionEntity): Long

    @Update
    suspend fun update(row: ItemUnitConversionEntity)
}

@Dao
interface PurchaseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInvoice(row: PurchaseInvoiceEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLine(row: PurchaseLineEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReturn(row: PurchaseReturnEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReturnLine(row: PurchaseReturnLineEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSupplierPayment(row: SupplierPaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSupplierPaymentAllocation(row: SupplierPaymentAllocationEntity): Long

    @Query("SELECT * FROM purchase_invoices WHERE id = :id LIMIT 1")
    suspend fun invoiceById(id: Long): PurchaseInvoiceEntity?

    @Query("SELECT * FROM supplier_payments WHERE id = :id LIMIT 1")
    suspend fun supplierPaymentById(id: Long): SupplierPaymentEntity?

    @Query("SELECT * FROM supplier_payment_allocations WHERE paymentId = :paymentId ORDER BY id")
    suspend fun supplierPaymentAllocations(paymentId: Long): List<SupplierPaymentAllocationEntity>

    @Query("SELECT * FROM supplier_payments WHERE reversalOfPaymentId = :paymentId LIMIT 1")
    suspend fun reversalForSupplierPayment(paymentId: Long): SupplierPaymentEntity?

    @Query("SELECT * FROM purchase_lines WHERE id = :id LIMIT 1")
    suspend fun lineById(id: Long): PurchaseLineEntity?

    @Query("SELECT * FROM purchase_lines WHERE invoiceId = :invoiceId ORDER BY id")
    suspend fun linesForInvoice(invoiceId: Long): List<PurchaseLineEntity>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM purchase_return_lines WHERE purchaseLineId = :purchaseLineId")
    suspend fun returnedQuantityForLine(purchaseLineId: Long): Double

    @Query("""
        SELECT pl.unitPriceOriginal AS unitPriceOriginal,
               p.currencyCode AS currencyCode,
               p.invoiceDate AS invoiceDate,
               p.invoiceNo AS invoiceNo,
               s.nameAr AS supplierName
        FROM purchase_lines pl
        JOIN purchase_invoices p ON p.id = pl.invoiceId
        JOIN suppliers s ON s.id = p.supplierId
        WHERE pl.itemId = :itemId
          AND pl.unitId = :unitId
          AND p.currencyCode = :currencyCode
          AND p.status = 'POSTED'
        ORDER BY p.invoiceDate DESC, p.id DESC, pl.id DESC
        LIMIT 1
    """)
    suspend fun lastPurchasePrice(itemId: Long, unitId: Long, currencyCode: String): LastPurchasePriceRow?

    @Query("""
        SELECT * FROM purchase_returns
        WHERE purchaseInvoiceId = :invoiceId AND status = 'POSTED'
        ORDER BY returnDate, id
    """)
    suspend fun returnsForInvoice(invoiceId: Long): List<PurchaseReturnEntity>


    @Query("SELECT COALESCE(SUM(allocatedBase), 0) FROM supplier_payment_allocations WHERE invoiceId = :invoiceId")
    suspend fun paidBaseForInvoice(invoiceId: Long): Double

    @Query("""
        SELECT p.id AS invoiceId, p.invoiceNo AS invoiceNo, p.invoiceDate AS invoiceDate,
               p.dueDate AS dueDate, p.currencyCode AS currencyCode,
               p.exchangeRate AS invoiceExchangeRate, p.totalBase AS totalBase,
               COALESCE((SELECT SUM(pr.totalBase) FROM purchase_returns pr
                         WHERE pr.purchaseInvoiceId=p.id AND pr.status='POSTED' AND pr.settlementType='SUPPLIER_CREDIT'),0) AS supplierCreditReturnsBase,
               COALESCE((SELECT SUM(spa.allocatedBase) FROM supplier_payment_allocations spa
                         WHERE spa.invoiceId=p.id),0) AS paidBase,
               MAX(0, p.totalBase
                    - COALESCE((SELECT SUM(pr2.totalBase) FROM purchase_returns pr2
                                WHERE pr2.purchaseInvoiceId=p.id AND pr2.status='POSTED' AND pr2.settlementType='SUPPLIER_CREDIT'),0)
                    - COALESCE((SELECT SUM(spa2.allocatedBase) FROM supplier_payment_allocations spa2
                                WHERE spa2.invoiceId=p.id),0)) AS outstandingBase
        FROM purchase_invoices p
        WHERE p.supplierId=:supplierId AND p.status='POSTED' AND p.paymentType='CREDIT'
          AND (p.totalBase
               - COALESCE((SELECT SUM(pr3.totalBase) FROM purchase_returns pr3
                           WHERE pr3.purchaseInvoiceId=p.id AND pr3.status='POSTED' AND pr3.settlementType='SUPPLIER_CREDIT'),0)
               - COALESCE((SELECT SUM(spa3.allocatedBase) FROM supplier_payment_allocations spa3
                           WHERE spa3.invoiceId=p.id),0)) > 0.000000001
        ORDER BY COALESCE(p.dueDate,p.invoiceDate), p.invoiceDate, p.id
    """)
    suspend fun openSupplierInvoices(supplierId: Long): List<SupplierInvoicePayableRow>

    @Query("""
        SELECT sp.id AS paymentId, sp.paymentNo AS paymentNo, sp.paymentDate AS paymentDate,
               sp.currencyCode AS currencyCode, sp.amountOriginal AS amountOriginal,
               sp.cashAmountBase AS cashAmountBase, spa.allocatedBase AS allocatedBase,
               p.invoiceNo AS invoiceNo, t.nameAr AS treasuryName,
               sp.reversalOfPaymentId AS reversalOfPaymentId
        FROM supplier_payments sp
        JOIN supplier_payment_allocations spa ON spa.paymentId=sp.id
        JOIN purchase_invoices p ON p.id=spa.invoiceId
        JOIN treasury_accounts t ON t.id=sp.treasuryAccountId
        WHERE sp.supplierId=:supplierId
        ORDER BY sp.paymentDate DESC, sp.id DESC
    """)
    suspend fun supplierPayments(supplierId: Long): List<SupplierPaymentDetailRow>

    @Query("""
        SELECT sp.id AS paymentId, sp.paymentNo AS paymentNo, sp.paymentDate AS paymentDate,
               sp.currencyCode AS currencyCode, sp.amountOriginal AS amountOriginal,
               sp.cashAmountBase AS cashAmountBase, spa.allocatedBase AS allocatedBase,
               p.invoiceNo AS invoiceNo, t.nameAr AS treasuryName,
               sp.reversalOfPaymentId AS reversalOfPaymentId
        FROM supplier_payments sp
        JOIN supplier_payment_allocations spa ON spa.paymentId=sp.id
        JOIN purchase_invoices p ON p.id=spa.invoiceId
        JOIN treasury_accounts t ON t.id=sp.treasuryAccountId
        WHERE spa.invoiceId=:invoiceId
        ORDER BY sp.paymentDate, sp.id
    """)
    suspend fun supplierPaymentsForInvoice(invoiceId: Long): List<SupplierPaymentDetailRow>

    @Query("""
        SELECT s.id AS supplierId, s.nameAr AS supplierName, s.currencyCode AS currencyCode,
               MAX(0,
                   COALESCE((SELECT SUM(pi.totalBase) FROM purchase_invoices pi
                             WHERE pi.supplierId=s.id AND pi.status='POSTED' AND pi.paymentType='CREDIT'),0)
                   - COALESCE((SELECT SUM(pr.totalBase) FROM purchase_returns pr
                               JOIN purchase_invoices pi2 ON pi2.id=pr.purchaseInvoiceId
                               WHERE pr.supplierId=s.id AND pr.status='POSTED' AND pr.settlementType='SUPPLIER_CREDIT'
                                 AND pi2.paymentType='CREDIT'),0)
               )
               + COALESCE((SELECT SUM(pvDue.amountBase) FROM party_vouchers pvDue
                           WHERE pvDue.supplierId=s.id AND pvDue.status='POSTED' AND pvDue.voucherType='RECEIPT'),0) AS totalDueBase,
               COALESCE((SELECT SUM(spa.allocatedBase) FROM supplier_payment_allocations spa
                         JOIN purchase_invoices pi3 ON pi3.id=spa.invoiceId WHERE pi3.supplierId=s.id),0)
               + COALESCE((SELECT SUM(pvPay.amountBase) FROM party_vouchers pvPay
                           WHERE pvPay.supplierId=s.id AND pvPay.status='POSTED' AND pvPay.voucherType='PAYMENT'),0) AS paidBase,
               (
               MAX(0,
                   COALESCE((SELECT SUM(pi4.totalBase) FROM purchase_invoices pi4
                             WHERE pi4.supplierId=s.id AND pi4.status='POSTED' AND pi4.paymentType='CREDIT'),0)
                   - COALESCE((SELECT SUM(pr4.totalBase) FROM purchase_returns pr4
                               JOIN purchase_invoices pi5 ON pi5.id=pr4.purchaseInvoiceId
                               WHERE pr4.supplierId=s.id AND pr4.status='POSTED' AND pr4.settlementType='SUPPLIER_CREDIT'
                                 AND pi5.paymentType='CREDIT'),0)
                   - COALESCE((SELECT SUM(spa4.allocatedBase) FROM supplier_payment_allocations spa4
                               JOIN purchase_invoices pi6 ON pi6.id=spa4.invoiceId WHERE pi6.supplierId=s.id),0)
               )
               + COALESCE((SELECT SUM(CASE WHEN pvBal.voucherType='RECEIPT' THEN pvBal.amountBase
                                            WHEN pvBal.voucherType='PAYMENT' THEN -pvBal.amountBase ELSE 0 END)
                           FROM party_vouchers pvBal WHERE pvBal.supplierId=s.id AND pvBal.status='POSTED'),0)
               ) AS outstandingBase,
               COALESCE((SELECT SUM(MAX(0,
                    pi7.totalBase
                    - COALESCE((SELECT SUM(pr7.totalBase) FROM purchase_returns pr7 WHERE pr7.purchaseInvoiceId=pi7.id AND pr7.status='POSTED' AND pr7.settlementType='SUPPLIER_CREDIT'),0)
                    - COALESCE((SELECT SUM(spa7.allocatedBase) FROM supplier_payment_allocations spa7 WHERE spa7.invoiceId=pi7.id),0)))
                  FROM purchase_invoices pi7
                  WHERE pi7.supplierId=s.id AND pi7.status='POSTED' AND pi7.paymentType='CREDIT'
                    AND pi7.dueDate IS NOT NULL AND pi7.dueDate < :now),0) AS overdueBase
        FROM suppliers s WHERE s.isActive=1 ORDER BY s.nameAr
    """)
    fun observeSupplierBalances(now: Long): Flow<List<SupplierBalanceRow>>

    @Query("SELECT * FROM purchase_invoices WHERE supplierId=:supplierId AND status='POSTED' ORDER BY invoiceDate DESC, id DESC")
    suspend fun supplierInvoices(supplierId: Long): List<PurchaseInvoiceEntity>

    @Query("SELECT * FROM purchase_returns WHERE supplierId=:supplierId AND status='POSTED' ORDER BY returnDate DESC, id DESC")
    suspend fun supplierReturns(supplierId: Long): List<PurchaseReturnEntity>

    @Query("""
        SELECT eventDate, eventOrder, eventType, referenceNo, debitBase, creditBase, notes FROM (
            SELECT pi.invoiceDate AS eventDate, 10 AS eventOrder, 'INVOICE' AS eventType,
                   pi.invoiceNo AS referenceNo, 0.0 AS debitBase, pi.totalBase AS creditBase,
                   'فاتورة شراء آجلة' AS notes
            FROM purchase_invoices pi
            WHERE pi.supplierId=:supplierId AND pi.status='POSTED' AND pi.paymentType='CREDIT' AND pi.invoiceDate <= :toDate
            UNION ALL
            SELECT pr.returnDate AS eventDate, 20 AS eventOrder, 'RETURN' AS eventType,
                   pr.returnNo AS referenceNo, pr.totalBase AS debitBase, 0.0 AS creditBase,
                   CASE WHEN pr.reason='' THEN 'مرتجع مشتريات - تخفيض رصيد المورد' ELSE pr.reason END AS notes
            FROM purchase_returns pr
            JOIN purchase_invoices pir ON pir.id=pr.purchaseInvoiceId
            WHERE pr.supplierId=:supplierId AND pr.status='POSTED' AND pr.settlementType='SUPPLIER_CREDIT'
              AND pir.paymentType='CREDIT' AND pr.returnDate <= :toDate
            UNION ALL
            SELECT sp.paymentDate AS eventDate,
                   CASE WHEN sp.reversalOfPaymentId IS NULL THEN 30 ELSE 35 END AS eventOrder,
                   CASE WHEN sp.reversalOfPaymentId IS NULL THEN 'PAYMENT' ELSE 'PAYMENT_REVERSAL' END AS eventType,
                   sp.paymentNo AS referenceNo,
                   CASE WHEN sp.reversalOfPaymentId IS NULL THEN spa.allocatedBase ELSE 0.0 END AS debitBase,
                   CASE WHEN sp.reversalOfPaymentId IS NULL THEN 0.0 ELSE -spa.allocatedBase END AS creditBase,
                   CASE WHEN sp.notes='' THEN 'دفعة مورد' ELSE sp.notes END AS notes
            FROM supplier_payments sp
            JOIN supplier_payment_allocations spa ON spa.paymentId=sp.id
            JOIN purchase_invoices pip ON pip.id=spa.invoiceId
            WHERE sp.supplierId=:supplierId AND pip.paymentType='CREDIT' AND sp.paymentDate <= :toDate
            UNION ALL
            SELECT pv.voucherDate AS eventDate, 40 AS eventOrder,
                   CASE WHEN pv.voucherType='PAYMENT' THEN 'VOUCHER_PAYMENT' ELSE 'VOUCHER_RECEIPT' END AS eventType,
                   pv.voucherNo AS referenceNo,
                   CASE WHEN pv.voucherType='PAYMENT' THEN pv.amountBase ELSE 0.0 END AS debitBase,
                   CASE WHEN pv.voucherType='RECEIPT' THEN pv.amountBase ELSE 0.0 END AS creditBase,
                   pv.description AS notes
            FROM party_vouchers pv
            WHERE pv.supplierId=:supplierId AND pv.voucherDate <= :toDate AND pv.voucherType IN ('PAYMENT','RECEIPT')
            UNION ALL
            SELECT pv.reversedAt AS eventDate, 45 AS eventOrder, 'VOUCHER_REVERSAL' AS eventType,
                   'REV-' || pv.voucherNo AS referenceNo,
                   CASE WHEN pv.voucherType='RECEIPT' THEN pv.amountBase ELSE 0.0 END AS debitBase,
                   CASE WHEN pv.voucherType='PAYMENT' THEN pv.amountBase ELSE 0.0 END AS creditBase,
                   'عكس السند: ' || pv.reversalReason AS notes
            FROM party_vouchers pv
            WHERE pv.supplierId=:supplierId AND pv.status='REVERSED' AND pv.reversedAt IS NOT NULL AND pv.reversedAt <= :toDate
        ) ORDER BY eventDate, eventOrder, referenceNo
    """)
    suspend fun supplierLedgerEvents(supplierId: Long, toDate: Long): List<SupplierLedgerEventRow>

    @Query("""
        SELECT s.id AS supplierId, s.nameAr AS supplierName,
               COALESCE(SUM(CASE WHEN x.outstandingBase > 0 AND (x.dueDate IS NULL OR x.dueDate >= :asOf) THEN x.outstandingBase ELSE 0 END),0) AS currentBase,
               COALESCE(SUM(CASE WHEN x.outstandingBase > 0 AND x.dueDate < :asOf AND (:asOf-x.dueDate)/86400000 BETWEEN 0 AND 30 THEN x.outstandingBase ELSE 0 END),0) AS days1To30Base,
               COALESCE(SUM(CASE WHEN x.outstandingBase > 0 AND (:asOf-x.dueDate)/86400000 BETWEEN 31 AND 60 THEN x.outstandingBase ELSE 0 END),0) AS days31To60Base,
               COALESCE(SUM(CASE WHEN x.outstandingBase > 0 AND (:asOf-x.dueDate)/86400000 BETWEEN 61 AND 90 THEN x.outstandingBase ELSE 0 END),0) AS days61To90Base,
               COALESCE(SUM(CASE WHEN x.outstandingBase > 0 AND (:asOf-x.dueDate)/86400000 > 90 THEN x.outstandingBase ELSE 0 END),0) AS over90Base,
               COALESCE(SUM(CASE WHEN x.outstandingBase > 0 THEN x.outstandingBase ELSE 0 END),0) AS totalOutstandingBase
        FROM suppliers s
        LEFT JOIN (
            SELECT pi.id, pi.supplierId, pi.dueDate,
                   MAX(0, pi.totalBase
                     - COALESCE((SELECT SUM(pr.totalBase) FROM purchase_returns pr
                                 WHERE pr.purchaseInvoiceId=pi.id AND pr.status='POSTED' AND pr.settlementType='SUPPLIER_CREDIT' AND pr.returnDate <= :asOf),0)
                     - COALESCE((SELECT SUM(spa.allocatedBase) FROM supplier_payment_allocations spa
                                 JOIN supplier_payments sp ON sp.id=spa.paymentId
                                 WHERE spa.invoiceId=pi.id AND sp.paymentDate <= :asOf),0)) AS outstandingBase
            FROM purchase_invoices pi
            WHERE pi.status='POSTED' AND pi.paymentType='CREDIT' AND pi.invoiceDate <= :asOf
        ) x ON x.supplierId=s.id
        WHERE s.isActive=1
        GROUP BY s.id, s.nameAr
        HAVING totalOutstandingBase > 0.000000001
        ORDER BY over90Base DESC, days61To90Base DESC, totalOutstandingBase DESC, s.nameAr
    """)
    suspend fun supplierAging(asOf: Long): List<SupplierAgingRow>

    @Query("SELECT COUNT(*) FROM purchase_invoices WHERE status = 'POSTED'")
    fun observeInvoiceCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM purchase_returns WHERE status = 'POSTED'")
    fun observeReturnCount(): Flow<Int>

    @Query("""
        SELECT p.id AS id, p.invoiceNo AS invoiceNo, p.invoiceDate AS invoiceDate,
               s.nameAr AS supplierName, p.currencyCode AS currencyCode,
               p.totalOriginal AS totalOriginal, p.totalBase AS totalBase, p.paymentType AS paymentType
        FROM purchase_invoices p
        JOIN suppliers s ON s.id = p.supplierId
        WHERE p.status = 'POSTED'
        ORDER BY p.invoiceDate DESC, p.id DESC
    """)
    fun observeSummaries(): Flow<List<PurchaseInvoiceSummary>>
}

@Dao
interface StockDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMovement(row: StockMovementEntity): Long

    @Query("SELECT COALESCE(SUM(quantityBase), 0) FROM stock_movements WHERE warehouseId = :warehouseId AND itemId = :itemId")
    suspend fun balance(warehouseId: Long, itemId: Long): Double

    /** Canonical historical ledger balance: sum of persisted movements up to and including asOf. */
    @Query("""
        SELECT COALESCE(SUM(quantityBase), 0)
        FROM stock_movements
        WHERE warehouseId = :warehouseId AND itemId = :itemId AND movementDate <= :asOf
    """)
    suspend fun balanceAt(warehouseId: Long, itemId: Long, asOf: Long): Double

    @Query("SELECT COALESCE(SUM(ABS(balance)), 0) FROM (SELECT SUM(quantityBase) AS balance FROM stock_movements WHERE warehouseId = :warehouseId GROUP BY itemId)")
    suspend fun absoluteWarehouseBalance(warehouseId: Long): Double

    @Query("SELECT COALESCE(SUM(ABS(balance)), 0) FROM (SELECT SUM(quantityBase) AS balance FROM stock_movements WHERE itemId = :itemId GROUP BY warehouseId)")
    suspend fun absoluteItemBalance(itemId: Long): Double

    @Query("""
        SELECT itemId AS itemId, lotNo AS lotNo, expiryDate AS expiryDate,
               COALESCE(SUM(quantityBase), 0) AS quantityBase,
               COALESCE(SUM(quantityBase * unitCostBase), 0) AS inventoryValueBase
        FROM stock_movements
        WHERE warehouseId = :warehouseId AND itemId = :itemId
        GROUP BY itemId, lotNo, expiryDate
        HAVING COALESCE(SUM(quantityBase), 0) > 0.000000001
        ORDER BY CASE WHEN expiryDate IS NULL THEN 1 ELSE 0 END, expiryDate, lotNo
    """)
    suspend fun lotBalances(warehouseId: Long, itemId: Long): List<LotBalanceRow>

    @Query("""
        SELECT itemId AS itemId, lotNo AS lotNo, expiryDate AS expiryDate,
               COALESCE(SUM(quantityBase), 0) AS quantityBase,
               COALESCE(SUM(quantityBase * unitCostBase), 0) AS inventoryValueBase
        FROM stock_movements
        WHERE warehouseId = :warehouseId AND itemId = :itemId AND movementDate <= :asOf
        GROUP BY itemId, lotNo, expiryDate
        HAVING COALESCE(SUM(quantityBase), 0) > 0.000000001
        ORDER BY CASE WHEN expiryDate IS NULL THEN 1 ELSE 0 END, expiryDate, lotNo
    """)
    suspend fun lotBalancesAt(warehouseId: Long, itemId: Long, asOf: Long): List<LotBalanceRow>

    @Query("""
        SELECT * FROM stock_movements
        WHERE warehouseId = :warehouseId AND itemId = :itemId
          AND TRIM(COALESCE(lotNo, '')) = :lotKey
          AND COALESCE(expiryDate, -1) = :expiryKey
        ORDER BY movementDate ASC, id ASC
    """)
    suspend fun lotMovementTimeline(
        warehouseId: Long,
        itemId: Long,
        lotKey: String,
        expiryKey: Long
    ): List<StockMovementEntity>

    @Query("""
        SELECT DISTINCT movementType FROM stock_movements
        WHERE warehouseId = :warehouseId AND itemId = :itemId
          AND COALESCE(lotNo, '') = COALESCE(:lotNo, '')
        ORDER BY movementType
    """)
    suspend fun movementTypesForLot(warehouseId: Long, itemId: Long, lotNo: String?): List<String>

    @Query("""
        SELECT CASE WHEN ABS(COALESCE(SUM(quantityBase), 0)) < 0.000000001 THEN 0
                    ELSE COALESCE(SUM(quantityBase * unitCostBase), 0) / SUM(quantityBase) END
        FROM stock_movements
        WHERE warehouseId = :warehouseId AND itemId = :itemId
    """)
    suspend fun averageUnitCost(warehouseId: Long, itemId: Long): Double

    @Query("""
        SELECT i.id AS itemId, i.code AS code, i.nameAr AS nameAr,
               COALESCE(SUM(m.quantityBase), 0) AS quantityBase,
               u.nameAr AS baseUnitName
        FROM items i
        JOIN units u ON u.id = i.baseUnitId
        LEFT JOIN stock_movements m ON m.itemId = i.id
        WHERE i.isActive = 1
        GROUP BY i.id, i.code, i.nameAr, u.nameAr
        ORDER BY i.nameAr
    """)
    fun observeBalances(): Flow<List<StockBalanceRow>>

    @Query("""
        SELECT w.id AS warehouseId, w.code AS warehouseCode, w.nameAr AS warehouseName,
               i.id AS itemId, i.code AS code, i.nameAr AS nameAr, i.category AS category,
               COALESCE(SUM(m.quantityBase), 0) AS quantityBase, u.nameAr AS baseUnitName
        FROM stock_movements m
        JOIN warehouses w ON w.id = m.warehouseId
        JOIN items i ON i.id = m.itemId
        JOIN units u ON u.id = i.baseUnitId
        WHERE w.isActive = 1 AND i.isActive = 1
        GROUP BY w.id, w.code, w.nameAr, i.id, i.code, i.nameAr, i.category, u.nameAr
        HAVING ABS(COALESCE(SUM(m.quantityBase), 0)) > 0.000000001
        ORDER BY w.nameAr, i.nameAr
    """)
    fun observeWarehouseBalances(): Flow<List<WarehouseStockBalanceRow>>
}
