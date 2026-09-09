package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE isActive = 1 ORDER BY nameAr")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE isActive = 1 ORDER BY nameAr")
    suspend fun allActive(): List<CustomerEntity>

    @Query("SELECT * FROM customers ORDER BY nameAr")
    suspend fun allCustomers(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE code = :code LIMIT 1")
    suspend fun byCode(code: String): CustomerEntity?

    @Query("""
        SELECT COALESCE(MAX(CAST(substr(code, 5) AS INTEGER)), 0)
        FROM customers
        WHERE code GLOB 'CUS-[0-9]*'
    """)
    suspend fun maxAutomaticCodeValue(): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: CustomerEntity): Long

    @Update
    suspend fun update(row: CustomerEntity)

    @Query("SELECT COUNT(*) FROM customers WHERE isActive = 1")
    fun observeCount(): Flow<Int>
}

@Dao
interface SalesDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPrice(row: SalesPriceEntity): Long

    @Query("SELECT COUNT(*) FROM sales_prices")
    suspend fun priceCount(): Int

    @Query("SELECT * FROM sales_prices WHERE id = :id LIMIT 1")
    suspend fun priceById(id: Long): SalesPriceEntity?

    @Update
    suspend fun updatePrice(row: SalesPriceEntity)

    @Query("""
        SELECT * FROM sales_prices
        WHERE itemId = :itemId AND channel = :channel AND province = :province
          AND currencyCode = :currencyCode AND isActive = 1
          AND effectiveFrom <= :at
          AND (effectiveTo IS NULL OR effectiveTo >= :at)
        ORDER BY effectiveFrom DESC, id DESC LIMIT 1
    """)
    suspend fun latestPrice(itemId: Long, channel: String, province: String, currencyCode: String, at: Long): SalesPriceEntity?

    @Query("""
        SELECT * FROM sales_prices
        WHERE itemId = :itemId AND channel = :channel AND province = :province
          AND currencyCode = :currencyCode AND isActive = 1
          AND effectiveFrom < :before
        ORDER BY effectiveFrom DESC, id DESC LIMIT 1
    """)
    suspend fun latestActivePriceBefore(itemId: Long, channel: String, province: String, currencyCode: String, before: Long): SalesPriceEntity?

    @Query("""
        SELECT COUNT(*) FROM sales_prices
        WHERE itemId = :itemId AND channel = :channel AND province = :province
          AND currencyCode = :currencyCode AND isActive = 1
          AND id != :excludeId
          AND (:effectiveTo IS NULL OR effectiveFrom <= :effectiveTo)
          AND (effectiveTo IS NULL OR effectiveTo >= :effectiveFrom)
    """)
    suspend fun overlappingActivePriceCount(
        itemId: Long,
        channel: String,
        province: String,
        currencyCode: String,
        effectiveFrom: Long,
        effectiveTo: Long?,
        excludeId: Long
    ): Int

    @Query("SELECT * FROM sales_prices WHERE itemId = :itemId ORDER BY province, channel, currencyCode, effectiveFrom DESC, id DESC")
    fun observePrices(itemId: Long): Flow<List<SalesPriceEntity>>

    @Query("SELECT COUNT(*) FROM sales_invoices WHERE invoiceNo = :invoiceNo COLLATE NOCASE")
    suspend fun invoiceNoCount(invoiceNo: String): Int

    @Query("SELECT COUNT(*) FROM customer_receipts WHERE receiptNo = :receiptNo COLLATE NOCASE")
    suspend fun receiptNoCount(receiptNo: String): Int

    @Query("SELECT * FROM sales_invoices ORDER BY invoiceDate, id")
    suspend fun allInvoicesForCloudSync(): List<SalesInvoiceEntity>

    @Query("SELECT * FROM customer_receipts ORDER BY receiptDate, id")
    suspend fun allReceiptsForCloudSync(): List<CustomerReceiptEntity>

    @Query("SELECT * FROM sales_returns ORDER BY returnDate, id")
    suspend fun allReturnsForCloudSync(): List<SalesReturnEntity>

    @Query("SELECT * FROM sales_allocations ORDER BY salesLineId, id")
    suspend fun allAllocationsForCloudSync(): List<SalesAllocationEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInvoice(row: SalesInvoiceEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLine(row: SalesLineEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllocation(row: SalesAllocationEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceipt(row: CustomerReceiptEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReceiptAllocation(row: CustomerReceiptAllocationEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCommission(row: SalesCommissionEntity): Long

    @Update
    suspend fun updateCommission(row: SalesCommissionEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReturn(row: SalesReturnEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReturnLine(row: SalesReturnLineEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReturnAllocation(row: SalesReturnAllocationEntity): Long

    @Update
    suspend fun updateAllocation(row: SalesAllocationEntity)

    @Update
    suspend fun updateReturnAllocation(row: SalesReturnAllocationEntity)

    @Update
    suspend fun updateReturnLine(row: SalesReturnLineEntity)

    @Update
    suspend fun updateReturn(row: SalesReturnEntity)

    @Query("SELECT * FROM sales_allocations WHERE itemId = :itemId AND COALESCE(lotNo, '') = COALESCE(:lotNo, '') ORDER BY id")
    suspend fun allocationsForLot(itemId: Long, lotNo: String?): List<SalesAllocationEntity>

    @Query("SELECT * FROM sales_return_allocations WHERE salesAllocationId = :salesAllocationId ORDER BY id")
    suspend fun returnAllocationsForSalesAllocation(salesAllocationId: Long): List<SalesReturnAllocationEntity>

    @Query("SELECT * FROM sales_return_allocations WHERE returnLineId = :returnLineId ORDER BY id")
    suspend fun returnAllocationsForLine(returnLineId: Long): List<SalesReturnAllocationEntity>

    @Query("SELECT * FROM sales_return_lines WHERE id = :id LIMIT 1")
    suspend fun returnLineById(id: Long): SalesReturnLineEntity?

    @Query("SELECT * FROM sales_return_lines WHERE returnId = :returnId ORDER BY id")
    suspend fun returnLinesForReturn(returnId: Long): List<SalesReturnLineEntity>

    @Query("SELECT * FROM sales_returns WHERE id = :id LIMIT 1")
    suspend fun returnById(id: Long): SalesReturnEntity?

    @Query("SELECT * FROM sales_returns WHERE returnNo = :returnNo COLLATE NOCASE LIMIT 1")
    suspend fun returnByNo(returnNo: String): SalesReturnEntity?

    @Query("SELECT * FROM sales_invoices WHERE id = :id LIMIT 1")
    suspend fun invoiceById(id: Long): SalesInvoiceEntity?

    @Query("SELECT * FROM sales_invoices WHERE invoiceNo = :invoiceNo COLLATE NOCASE LIMIT 1")
    suspend fun invoiceByNo(invoiceNo: String): SalesInvoiceEntity?

    @Query("SELECT * FROM customer_receipts WHERE id = :id LIMIT 1")
    suspend fun receiptById(id: Long): CustomerReceiptEntity?

    @Query("SELECT * FROM customer_receipts WHERE receiptNo = :receiptNo COLLATE NOCASE LIMIT 1")
    suspend fun receiptByNo(receiptNo: String): CustomerReceiptEntity?

    @Query("SELECT * FROM customer_receipt_allocations WHERE receiptId = :receiptId ORDER BY id")
    suspend fun receiptAllocations(receiptId: Long): List<CustomerReceiptAllocationEntity>

    @Query("SELECT * FROM customer_receipts WHERE reversalOfReceiptId = :receiptId LIMIT 1")
    suspend fun reversalForReceipt(receiptId: Long): CustomerReceiptEntity?

    @Query("SELECT * FROM sales_lines WHERE id = :id LIMIT 1")
    suspend fun lineById(id: Long): SalesLineEntity?

    @Query("SELECT * FROM sales_lines WHERE invoiceId = :invoiceId ORDER BY id")
    suspend fun linesForInvoice(invoiceId: Long): List<SalesLineEntity>

    @Query("SELECT * FROM sales_allocations WHERE salesLineId = :salesLineId ORDER BY id")
    suspend fun allocationsForLine(salesLineId: Long): List<SalesAllocationEntity>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM sales_return_lines WHERE salesLineId = :salesLineId")
    suspend fun returnedQuantityForLine(salesLineId: Long): Double

    @Query("SELECT COALESCE(SUM(freeQuantity), 0) FROM sales_return_lines WHERE salesLineId = :salesLineId")
    suspend fun returnedFreeQuantityForLine(salesLineId: Long): Double

    @Query("""
        SELECT COALESCE(SUM(sra.quantityBase), 0)
        FROM sales_return_allocations sra
        WHERE sra.salesAllocationId = :allocationId
    """)
    suspend fun returnedBaseForAllocation(allocationId: Long): Double

    @Query("""
        SELECT COALESCE(SUM(sra.quantityBase - sra.freeQuantityBase), 0)
        FROM sales_return_allocations sra
        WHERE sra.salesAllocationId = :allocationId
    """)
    suspend fun returnedPaidBaseForAllocation(allocationId: Long): Double

    @Query("""
        SELECT COALESCE(SUM(sra.freeQuantityBase), 0)
        FROM sales_return_allocations sra
        WHERE sra.salesAllocationId = :allocationId
    """)
    suspend fun returnedFreeBaseForAllocation(allocationId: Long): Double

    /** Actual cash applied to the invoice. Used for cash collection / commission metrics only. */
    @Query("SELECT COALESCE(SUM(amountBase), 0) FROM customer_receipt_allocations WHERE invoiceId = :invoiceId")
    suspend fun receivedBaseForInvoice(invoiceId: Long): Double

    /** Net actual cash applied up to the requested date; settlement discounts are excluded. */
    @Query("""
        SELECT COALESCE(SUM(cra.amountBase), 0)
        FROM customer_receipt_allocations cra
        JOIN customer_receipts cr ON cr.id = cra.receiptId
        WHERE cra.invoiceId = :invoiceId AND cr.receiptDate <= :asOf
    """)
    suspend fun receivedBaseForInvoiceAsOf(invoiceId: Long, asOf: Long): Double

    @Query("""
        SELECT COALESCE(SUM(totalBase), 0)
        FROM sales_returns
        WHERE salesInvoiceId = :invoiceId AND status = 'POSTED'
          AND settlementType = 'CASH_REFUND' AND returnDate <= :asOf
    """)
    suspend fun cashRefundedBaseForInvoiceAsOf(invoiceId: Long, asOf: Long): Double

    /** Total A/R settlement = cash applied + settlement discount. */
    @Query("SELECT COALESCE(SUM(amountBase + discountBase), 0) FROM customer_receipt_allocations WHERE invoiceId = :invoiceId")
    suspend fun settledBaseForInvoice(invoiceId: Long): Double

    @Query("""
        SELECT COALESCE(SUM(cra.amountBase + cra.discountBase), 0)
        FROM customer_receipt_allocations cra
        JOIN customer_receipts cr ON cr.id = cra.receiptId
        WHERE cra.invoiceId = :invoiceId AND cr.receiptDate <= :asOf
    """)
    suspend fun settledBaseForInvoiceAsOf(invoiceId: Long, asOf: Long): Double

    @Query("SELECT COALESCE(SUM(totalBase), 0) FROM sales_returns WHERE salesInvoiceId = :invoiceId AND status = 'POSTED'")
    suspend fun returnedBaseForInvoice(invoiceId: Long): Double

    @Query("SELECT COALESCE(SUM(totalBase), 0) FROM sales_returns WHERE salesInvoiceId = :invoiceId AND status = 'POSTED' AND settlementType = 'CUSTOMER_CREDIT'")
    suspend fun customerCreditReturnedBaseForInvoice(invoiceId: Long): Double

    @Query("""
        SELECT COALESCE(SUM(totalBase), 0)
        FROM sales_returns
        WHERE salesInvoiceId = :invoiceId AND status = 'POSTED'
          AND settlementType = 'CUSTOMER_CREDIT' AND returnDate <= :asOf
    """)
    suspend fun customerCreditReturnedBaseForInvoiceAsOf(invoiceId: Long, asOf: Long): Double

    @Query("""
        SELECT cr.*
        FROM customer_receipts cr
        JOIN customer_receipt_allocations cra ON cra.receiptId = cr.id
        WHERE cra.invoiceId = :invoiceId
        ORDER BY cr.receiptDate, cr.id
    """)
    suspend fun receiptsForInvoice(invoiceId: Long): List<CustomerReceiptEntity>

    @Query("""
        SELECT * FROM sales_returns
        WHERE salesInvoiceId = :invoiceId AND status = 'POSTED'
        ORDER BY returnDate, id
    """)
    suspend fun returnsForInvoice(invoiceId: Long): List<SalesReturnEntity>

    @Query("""
        SELECT COALESCE(SUM(si.totalBase), 0)
             - COALESCE((SELECT SUM(acs.amountBase)
                         FROM sales_additional_charge_settlements acs
                         JOIN sales_additional_charges ac ON ac.id=acs.chargeId
                         JOIN sales_invoices axi ON axi.id=acs.invoiceId
                         WHERE axi.customerId=:customerId AND axi.paymentType='CREDIT' AND axi.status='POSTED'
                           AND acs.status='ACTIVE' AND ac.status='POSTED' AND ac.bearer='CUSTOMER' AND ac.paidBy='CUSTOMER_DIRECT'),0)
             - COALESCE((SELECT SUM(cra.amountBase + cra.discountBase)
                         FROM customer_receipt_allocations cra
                         JOIN sales_invoices sx ON sx.id = cra.invoiceId
                         WHERE sx.customerId = :customerId AND sx.paymentType = 'CREDIT'), 0)
             - COALESCE((SELECT SUM(sr.totalBase)
                         FROM sales_returns sr
                         JOIN sales_invoices sx2 ON sx2.id = sr.salesInvoiceId
                         WHERE sr.customerId = :customerId AND sr.status = 'POSTED' AND sr.settlementType = 'CUSTOMER_CREDIT' AND sx2.paymentType = 'CREDIT'), 0)
             + COALESCE((SELECT SUM(CASE
                         WHEN pv.voucherType = 'PAYMENT' THEN pv.amountBase
                         WHEN pv.voucherType = 'RECEIPT' THEN -pv.amountBase
                         ELSE 0 END)
                         FROM party_vouchers pv
                         WHERE pv.customerId = :customerId AND pv.status = 'POSTED'), 0)
        FROM sales_invoices si
        WHERE si.customerId = :customerId AND si.status = 'POSTED' AND si.paymentType = 'CREDIT'
    """)
    suspend fun customerOutstandingBase(customerId: Long): Double

    @Query("""
        SELECT COALESCE(SUM(si.totalBase), 0)
             - COALESCE((SELECT SUM(acs.amountBase)
                         FROM sales_additional_charge_settlements acs
                         JOIN sales_additional_charges ac ON ac.id=acs.chargeId
                         JOIN sales_invoices axi ON axi.id=acs.invoiceId
                         WHERE axi.customerId=:customerId AND axi.paymentType='CREDIT' AND axi.status='POSTED' AND axi.invoiceDate<=:asOf
                           AND acs.status='ACTIVE' AND acs.settlementDate<=:asOf AND ac.status='POSTED' AND ac.bearer='CUSTOMER' AND ac.paidBy='CUSTOMER_DIRECT'),0)
             - COALESCE((SELECT SUM(cra.amountBase + cra.discountBase)
                         FROM customer_receipt_allocations cra
                         JOIN customer_receipts cr ON cr.id = cra.receiptId
                         JOIN sales_invoices sx ON sx.id = cra.invoiceId
                         WHERE sx.customerId = :customerId
                           AND sx.paymentType = 'CREDIT'
                           AND sx.invoiceDate <= :asOf
                           AND cr.receiptDate <= :asOf), 0)
             - COALESCE((SELECT SUM(sr.totalBase)
                         FROM sales_returns sr
                         JOIN sales_invoices sx2 ON sx2.id = sr.salesInvoiceId
                         WHERE sr.customerId = :customerId
                           AND sr.status = 'POSTED'
                           AND sr.settlementType = 'CUSTOMER_CREDIT'
                           AND sx2.paymentType = 'CREDIT'
                           AND sx2.invoiceDate <= :asOf
                           AND sr.returnDate <= :asOf), 0)
             + COALESCE((SELECT SUM(CASE
                         WHEN pv.voucherType = 'PAYMENT' THEN pv.amountBase
                         WHEN pv.voucherType = 'RECEIPT' THEN -pv.amountBase
                         ELSE 0 END)
                         FROM party_vouchers pv
                         WHERE pv.customerId = :customerId
                           AND pv.voucherDate <= :asOf
                           AND (pv.status = 'POSTED'
                                OR (pv.status = 'REVERSED' AND (pv.reversedAt IS NULL OR pv.reversedAt > :asOf)))), 0)
        FROM sales_invoices si
        WHERE si.customerId = :customerId
          AND si.status = 'POSTED'
          AND si.paymentType = 'CREDIT'
          AND si.invoiceDate <= :asOf
    """)
    suspend fun customerOutstandingBaseAsOf(customerId: Long, asOf: Long): Double

    @Query("""
        SELECT si.invoiceDate AS eventDate,
               10 AS eventOrder,
               'INVOICE' AS eventType,
               si.invoiceNo AS referenceNo,
               si.invoiceNo AS invoiceNo,
               si.currencyCode AS currencyCode,
               si.totalOriginal AS amountOriginal,
               si.totalBase AS debitBase,
               0.0 AS creditBase,
               si.notes AS notes
        FROM sales_invoices si
        WHERE si.customerId = :customerId AND si.status = 'POSTED'

        UNION ALL

        SELECT acs.settlementDate AS eventDate,
               15 AS eventOrder,
               'ADDITIONAL_CHARGE_DIRECT' AS eventType,
               ac.chargeNo AS referenceNo,
               si.invoiceNo AS invoiceNo,
               si.currencyCode AS currencyCode,
               -acs.amountInvoiceOriginal AS amountOriginal,
               0.0 AS debitBase,
               acs.amountBase AS creditBase,
               'رسم مدفوع مباشرة بواسطة العميل — لا يدخل صندوق أو ذمم الشركة' AS notes
        FROM sales_additional_charge_settlements acs
        JOIN sales_additional_charges ac ON ac.id = acs.chargeId
        JOIN sales_invoices si ON si.id = acs.invoiceId
        WHERE si.customerId = :customerId
          AND si.status = 'POSTED'
          AND acs.status = 'ACTIVE'
          AND ac.status = 'POSTED'
          AND ac.bearer = 'CUSTOMER'
          AND ac.paidBy = 'CUSTOMER_DIRECT'

        UNION ALL

        SELECT sr.returnDate AS eventDate,
               20 AS eventOrder,
               'SALES_RETURN' AS eventType,
               sr.returnNo AS referenceNo,
               si.invoiceNo AS invoiceNo,
               sr.currencyCode AS currencyCode,
               -sr.totalOriginal AS amountOriginal,
               0.0 AS debitBase,
               sr.totalBase AS creditBase,
               sr.reason AS notes
        FROM sales_returns sr
        JOIN sales_invoices si ON si.id = sr.salesInvoiceId
        WHERE sr.customerId = :customerId AND sr.status = 'POSTED'
          AND sr.settlementType = 'CUSTOMER_CREDIT'

        UNION ALL

        SELECT sr.returnDate AS eventDate,
               25 AS eventOrder,
               'CASH_REFUND' AS eventType,
               sr.returnNo AS referenceNo,
               si.invoiceNo AS invoiceNo,
               sr.currencyCode AS currencyCode,
               sr.totalOriginal AS amountOriginal,
               0.0 AS debitBase,
               0.0 AS creditBase,
               sr.reason || CASE WHEN sr.reason <> '' THEN ' • ' ELSE '' END || 'رد نقدي لا يغير ذمة العميل' AS notes
        FROM sales_returns sr
        JOIN sales_invoices si ON si.id = sr.salesInvoiceId
        WHERE sr.customerId = :customerId
          AND sr.status = 'POSTED'
          AND sr.settlementType = 'CASH_REFUND'

        UNION ALL

        SELECT cr.receiptDate AS eventDate,
               CASE WHEN cr.reversalOfReceiptId IS NULL THEN 30 ELSE 35 END AS eventOrder,
               CASE WHEN cr.reversalOfReceiptId IS NULL THEN 'RECEIPT' ELSE 'RECEIPT_REVERSAL' END AS eventType,
               cr.receiptNo AS referenceNo,
               COALESCE((SELECT GROUP_CONCAT(si2.invoiceNo, ', ')
                         FROM customer_receipt_allocations cra
                         JOIN sales_invoices si2 ON si2.id = cra.invoiceId
                         WHERE cra.receiptId = cr.id), '') AS invoiceNo,
               cr.currencyCode AS currencyCode,
               -(cr.amountOriginal + cr.discountOriginal) AS amountOriginal,
               CASE WHEN cr.reversalOfReceiptId IS NULL THEN 0.0 ELSE -(cr.amountBase + cr.discountBase) END AS debitBase,
               CASE WHEN cr.reversalOfReceiptId IS NULL THEN (cr.amountBase + cr.discountBase) ELSE 0.0 END AS creditBase,
               CASE WHEN ABS(cr.discountOriginal) > 0.000000001
                    THEN cr.notes || CASE WHEN cr.notes <> '' THEN ' • ' ELSE '' END || 'خصم تحصيل: ' || ABS(cr.discountOriginal)
                    ELSE cr.notes END AS notes
        FROM customer_receipts cr
        WHERE cr.customerId = :customerId

        UNION ALL

        SELECT pv.voucherDate AS eventDate,
               40 AS eventOrder,
               CASE WHEN pv.voucherType='RECEIPT' THEN 'VOUCHER_RECEIPT' ELSE 'VOUCHER_PAYMENT' END AS eventType,
               pv.voucherNo AS referenceNo,
               pv.referenceNo AS invoiceNo,
               pv.currencyCode AS currencyCode,
               CASE WHEN pv.voucherType='RECEIPT' THEN -pv.amountOriginal ELSE pv.amountOriginal END AS amountOriginal,
               CASE WHEN pv.voucherType='PAYMENT' THEN pv.amountBase ELSE 0.0 END AS debitBase,
               CASE WHEN pv.voucherType='RECEIPT' THEN pv.amountBase ELSE 0.0 END AS creditBase,
               pv.description AS notes
        FROM party_vouchers pv
        WHERE pv.customerId=:customerId AND pv.voucherType IN ('RECEIPT','PAYMENT')

        UNION ALL

        SELECT pv.reversedAt AS eventDate,
               45 AS eventOrder,
               'VOUCHER_REVERSAL' AS eventType,
               'REV-' || pv.voucherNo AS referenceNo,
               pv.referenceNo AS invoiceNo,
               pv.currencyCode AS currencyCode,
               CASE WHEN pv.voucherType='RECEIPT' THEN pv.amountOriginal ELSE -pv.amountOriginal END AS amountOriginal,
               CASE WHEN pv.voucherType='RECEIPT' THEN pv.amountBase ELSE 0.0 END AS debitBase,
               CASE WHEN pv.voucherType='PAYMENT' THEN pv.amountBase ELSE 0.0 END AS creditBase,
               'عكس السند: ' || pv.reversalReason AS notes
        FROM party_vouchers pv
        WHERE pv.customerId=:customerId AND pv.status='REVERSED' AND pv.reversedAt IS NOT NULL

        ORDER BY eventDate, eventOrder, referenceNo
    """)
    suspend fun customerLedgerEvents(customerId: Long): List<CustomerLedgerEventRow>

    @Query("""
        SELECT COUNT(*) FROM sales_invoices
        WHERE customerId = :customerId AND paymentType = 'CASH' AND status = 'POSTED'
    """)
    suspend fun successfulCashSales(customerId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM sales_invoices si
        WHERE si.customerId = :customerId
          AND si.paymentType = 'CREDIT'
          AND si.status = 'POSTED'
          AND si.dueDate IS NOT NULL AND si.dueDate < :now
          AND (si.totalBase
               - COALESCE((SELECT SUM(acs.amountBase) FROM sales_additional_charge_settlements acs JOIN sales_additional_charges ac ON ac.id=acs.chargeId WHERE acs.invoiceId=si.id AND acs.status='ACTIVE' AND ac.status='POSTED' AND ac.bearer='CUSTOMER' AND ac.paidBy='CUSTOMER_DIRECT'), 0)
               - COALESCE((SELECT SUM(cra.amountBase + cra.discountBase) FROM customer_receipt_allocations cra WHERE cra.invoiceId = si.id), 0)
               - COALESCE((SELECT SUM(sr.totalBase) FROM sales_returns sr WHERE sr.salesInvoiceId = si.id AND sr.status = 'POSTED' AND sr.settlementType = 'CUSTOMER_CREDIT'), 0)) > 0.000000001
    """)
    suspend fun overdueInvoiceCount(customerId: Long, now: Long): Int

    @Query("""
        SELECT COUNT(*) FROM sales_invoices si
        WHERE si.customerId = :customerId
          AND si.paymentType = 'CREDIT'
          AND si.status = 'POSTED'
          AND si.invoiceDate <= :asOf
          AND si.dueDate IS NOT NULL AND si.dueDate < :asOf
          AND (si.totalBase
               - COALESCE((SELECT SUM(acs.amountBase)
                           FROM sales_additional_charge_settlements acs
                           JOIN sales_additional_charges ac ON ac.id = acs.chargeId
                           WHERE acs.invoiceId = si.id
                             AND acs.status = 'ACTIVE'
                             AND acs.settlementDate <= :asOf
                             AND ac.status = 'POSTED'
                             AND ac.bearer = 'CUSTOMER'
                             AND ac.paidBy = 'CUSTOMER_DIRECT'), 0)
               - COALESCE((SELECT SUM(cra.amountBase + cra.discountBase)
                           FROM customer_receipt_allocations cra
                           JOIN customer_receipts cr ON cr.id = cra.receiptId
                           WHERE cra.invoiceId = si.id AND cr.receiptDate <= :asOf), 0)
               - COALESCE((SELECT SUM(sr.totalBase)
                           FROM sales_returns sr
                           WHERE sr.salesInvoiceId = si.id
                             AND sr.status = 'POSTED'
                             AND sr.settlementType = 'CUSTOMER_CREDIT'
                             AND sr.returnDate <= :asOf), 0)) > 0.000000001
    """)
    suspend fun overdueInvoiceCountAsOf(customerId: Long, asOf: Long): Int

    @Query("""
        SELECT * FROM sales_commissions WHERE invoiceId = :invoiceId ORDER BY createdAt, id
    """)
    suspend fun commissionsForInvoice(invoiceId: Long): List<SalesCommissionEntity>

    @Query("""
        SELECT COALESCE(SUM(earnedBase - reversedBase), 0)
        FROM sales_commissions WHERE invoiceId = :invoiceId
    """)
    suspend fun netCommissionBaseForInvoice(invoiceId: Long): Double

    @Query("SELECT * FROM sales_invoices WHERE customerId=:customerId AND status='POSTED' ORDER BY invoiceDate DESC, id DESC")
    suspend fun customerInvoices(customerId: Long): List<SalesInvoiceEntity>

    @Query("SELECT * FROM customer_receipts WHERE customerId=:customerId ORDER BY receiptDate DESC, id DESC")
    suspend fun customerReceipts(customerId: Long): List<CustomerReceiptEntity>

    @Query("SELECT * FROM sales_returns WHERE customerId=:customerId AND status='POSTED' ORDER BY returnDate DESC, id DESC")
    suspend fun customerReturns(customerId: Long): List<SalesReturnEntity>

    @Query("""
        SELECT sc.* FROM sales_commissions sc
        JOIN sales_invoices si ON si.id=sc.invoiceId
        WHERE si.customerId=:customerId
        ORDER BY sc.createdAt DESC, sc.id DESC
    """)
    suspend fun customerCommissions(customerId: Long): List<SalesCommissionEntity>

    @Query("SELECT COUNT(*) FROM sales_invoices WHERE status = 'POSTED'")
    fun observeInvoiceCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sales_returns WHERE status = 'POSTED'")
    fun observeReturnCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM customer_receipts")
    fun observeReceiptCount(): Flow<Int>

    @Query("""
        SELECT si.id AS id, si.invoiceNo AS invoiceNo, si.invoiceDate AS invoiceDate,
               c.nameAr AS customerName, si.paymentType AS paymentType,
               si.currencyCode AS currencyCode, si.totalOriginal AS totalOriginal,
               si.totalBase AS totalBase,
               CASE WHEN si.paymentType = 'CREDIT' THEN
                 (si.totalBase
                  - COALESCE((SELECT SUM(acs.amountBase) FROM sales_additional_charge_settlements acs JOIN sales_additional_charges ac ON ac.id=acs.chargeId WHERE acs.invoiceId=si.id AND acs.status='ACTIVE' AND ac.status='POSTED' AND ac.bearer='CUSTOMER' AND ac.paidBy='CUSTOMER_DIRECT'), 0)
               - COALESCE((SELECT SUM(cra.amountBase + cra.discountBase) FROM customer_receipt_allocations cra WHERE cra.invoiceId = si.id), 0)
                  - COALESCE((SELECT SUM(sr.totalBase) FROM sales_returns sr WHERE sr.salesInvoiceId = si.id AND sr.status = 'POSTED' AND sr.settlementType = 'CUSTOMER_CREDIT'), 0))
               ELSE 0 END AS outstandingBase,
               si.dueDate AS dueDate
        FROM sales_invoices si
        JOIN customers c ON c.id = si.customerId
        WHERE si.status = 'POSTED'
        ORDER BY si.invoiceDate DESC, si.id DESC
    """)
    fun observeSummaries(): Flow<List<SalesInvoiceSummary>>

    @Query("""
        SELECT si.id AS id, si.invoiceNo AS invoiceNo, si.invoiceDate AS invoiceDate,
               c.nameAr AS customerName, si.paymentType AS paymentType,
               si.currencyCode AS currencyCode, si.totalOriginal AS totalOriginal,
               si.totalBase AS totalBase,
               CASE WHEN si.paymentType = 'CREDIT' THEN
                 (si.totalBase
                  - COALESCE((SELECT SUM(acs.amountBase) FROM sales_additional_charge_settlements acs JOIN sales_additional_charges ac ON ac.id=acs.chargeId WHERE acs.invoiceId=si.id AND acs.status='ACTIVE' AND ac.status='POSTED' AND ac.bearer='CUSTOMER' AND ac.paidBy='CUSTOMER_DIRECT'), 0)
               - COALESCE((SELECT SUM(cra.amountBase + cra.discountBase) FROM customer_receipt_allocations cra WHERE cra.invoiceId = si.id), 0)
                  - COALESCE((SELECT SUM(sr.totalBase) FROM sales_returns sr WHERE sr.salesInvoiceId = si.id AND sr.status = 'POSTED' AND sr.settlementType = 'CUSTOMER_CREDIT'), 0))
               ELSE 0 END AS outstandingBase,
               si.dueDate AS dueDate
        FROM sales_invoices si
        JOIN customers c ON c.id = si.customerId
        WHERE si.customerId = :customerId AND si.status = 'POSTED' AND si.paymentType = 'CREDIT'
          AND (si.totalBase
               - COALESCE((SELECT SUM(acs.amountBase) FROM sales_additional_charge_settlements acs JOIN sales_additional_charges ac ON ac.id=acs.chargeId WHERE acs.invoiceId=si.id AND acs.status='ACTIVE' AND ac.status='POSTED' AND ac.bearer='CUSTOMER' AND ac.paidBy='CUSTOMER_DIRECT'), 0)
               - COALESCE((SELECT SUM(cra.amountBase + cra.discountBase) FROM customer_receipt_allocations cra WHERE cra.invoiceId = si.id), 0)
               - COALESCE((SELECT SUM(sr.totalBase) FROM sales_returns sr WHERE sr.salesInvoiceId = si.id AND sr.status = 'POSTED' AND sr.settlementType = 'CUSTOMER_CREDIT'), 0)) > 0.000000001
        ORDER BY si.invoiceDate, si.id
    """)
    suspend fun openInvoiceSummaries(customerId: Long): List<SalesInvoiceSummary>

    @Query("""
        SELECT si.id AS id, si.invoiceNo AS invoiceNo, si.invoiceDate AS invoiceDate,
               c.nameAr AS customerName, si.paymentType AS paymentType,
               si.currencyCode AS currencyCode, si.totalOriginal AS totalOriginal,
               si.totalBase AS totalBase,
               CASE WHEN si.paymentType = 'CREDIT' THEN
                 (si.totalBase
                  - COALESCE((SELECT SUM(acs.amountBase)
                              FROM sales_additional_charge_settlements acs
                              JOIN sales_additional_charges ac ON ac.id=acs.chargeId
                              WHERE acs.invoiceId=si.id AND acs.status='ACTIVE' AND acs.settlementDate<=:asOf
                                AND ac.status='POSTED' AND ac.bearer='CUSTOMER' AND ac.paidBy='CUSTOMER_DIRECT'),0)
                  - COALESCE((SELECT SUM(cra.amountBase + cra.discountBase)
                              FROM customer_receipt_allocations cra
                              JOIN customer_receipts cr ON cr.id = cra.receiptId
                              WHERE cra.invoiceId = si.id AND cr.receiptDate <= :asOf), 0)
                  - COALESCE((SELECT SUM(sr.totalBase) FROM sales_returns sr
                              WHERE sr.salesInvoiceId = si.id AND sr.status = 'POSTED'
                                AND sr.settlementType = 'CUSTOMER_CREDIT' AND sr.returnDate <= :asOf), 0))
               ELSE 0 END AS outstandingBase,
               si.dueDate AS dueDate
        FROM sales_invoices si
        JOIN customers c ON c.id = si.customerId
        WHERE si.customerId = :customerId AND si.status = 'POSTED' AND si.paymentType = 'CREDIT'
          AND si.invoiceDate <= :asOf
          AND (si.totalBase
               - COALESCE((SELECT SUM(acs2.amountBase)
                           FROM sales_additional_charge_settlements acs2
                           JOIN sales_additional_charges ac2 ON ac2.id=acs2.chargeId
                           WHERE acs2.invoiceId=si.id AND acs2.status='ACTIVE' AND acs2.settlementDate<=:asOf
                             AND ac2.status='POSTED' AND ac2.bearer='CUSTOMER' AND ac2.paidBy='CUSTOMER_DIRECT'),0)
               - COALESCE((SELECT SUM(cra2.amountBase + cra2.discountBase)
                           FROM customer_receipt_allocations cra2
                           JOIN customer_receipts cr2 ON cr2.id = cra2.receiptId
                           WHERE cra2.invoiceId = si.id AND cr2.receiptDate <= :asOf), 0)
               - COALESCE((SELECT SUM(sr2.totalBase) FROM sales_returns sr2
                           WHERE sr2.salesInvoiceId = si.id AND sr2.status = 'POSTED'
                             AND sr2.settlementType = 'CUSTOMER_CREDIT' AND sr2.returnDate <= :asOf), 0)) > 0.000000001
        ORDER BY si.invoiceDate, si.id
    """)
    suspend fun openInvoiceSummariesAsOf(customerId: Long, asOf: Long): List<SalesInvoiceSummary>

    @Query("""
        SELECT c.id AS customerId, c.nameAr AS customerName, c.province AS province,
               c.classification AS classification, c.creditLimitBase AS creditLimitBase,
               (
                 COALESCE((SELECT SUM(si.totalBase) FROM sales_invoices si
                           WHERE si.customerId=c.id AND si.status='POSTED' AND si.paymentType='CREDIT'),0)
                 - COALESCE((SELECT SUM(acs.amountBase) FROM sales_additional_charge_settlements acs
                             JOIN sales_additional_charges ac ON ac.id=acs.chargeId
                             JOIN sales_invoices ai ON ai.id=acs.invoiceId
                             WHERE ai.customerId=c.id AND ai.status='POSTED' AND ai.paymentType='CREDIT'
                               AND acs.status='ACTIVE' AND ac.status='POSTED' AND ac.bearer='CUSTOMER' AND ac.paidBy='CUSTOMER_DIRECT'),0)
                 - COALESCE((SELECT SUM(sr.totalBase) FROM sales_returns sr
                             JOIN sales_invoices sri ON sri.id=sr.salesInvoiceId
                             WHERE sr.customerId=c.id AND sr.status='POSTED' AND sr.settlementType='CUSTOMER_CREDIT'
                               AND sri.paymentType='CREDIT'),0)
               ) AS totalDueBase,
               (
                 COALESCE((SELECT SUM(cra.amountBase + cra.discountBase) FROM customer_receipt_allocations cra
                           JOIN sales_invoices cri ON cri.id=cra.invoiceId
                           WHERE cri.customerId=c.id AND cri.paymentType='CREDIT'),0)
                 + COALESCE((SELECT SUM(pv.amountBase) FROM party_vouchers pv
                             WHERE pv.customerId=c.id AND pv.status='POSTED' AND pv.voucherType='RECEIPT'),0)
               ) AS paidBase,
               (
                 COALESCE((SELECT SUM(si2.totalBase) FROM sales_invoices si2
                           WHERE si2.customerId=c.id AND si2.status='POSTED' AND si2.paymentType='CREDIT'),0)
                 - COALESCE((SELECT SUM(acs2.amountBase) FROM sales_additional_charge_settlements acs2
                             JOIN sales_additional_charges ac2 ON ac2.id=acs2.chargeId
                             JOIN sales_invoices ai2 ON ai2.id=acs2.invoiceId
                             WHERE ai2.customerId=c.id AND ai2.status='POSTED' AND ai2.paymentType='CREDIT'
                               AND acs2.status='ACTIVE' AND ac2.status='POSTED' AND ac2.bearer='CUSTOMER' AND ac2.paidBy='CUSTOMER_DIRECT'),0)
                 - COALESCE((SELECT SUM(sr2.totalBase) FROM sales_returns sr2
                             JOIN sales_invoices sri2 ON sri2.id=sr2.salesInvoiceId
                             WHERE sr2.customerId=c.id AND sr2.status='POSTED' AND sr2.settlementType='CUSTOMER_CREDIT'
                               AND sri2.paymentType='CREDIT'),0)
                 - COALESCE((SELECT SUM(cra2.amountBase + cra2.discountBase) FROM customer_receipt_allocations cra2
                             JOIN sales_invoices cri2 ON cri2.id=cra2.invoiceId
                             WHERE cri2.customerId=c.id AND cri2.paymentType='CREDIT'),0)
                 + COALESCE((SELECT SUM(CASE WHEN pv2.voucherType='PAYMENT' THEN pv2.amountBase
                                             WHEN pv2.voucherType='RECEIPT' THEN -pv2.amountBase ELSE 0 END)
                             FROM party_vouchers pv2 WHERE pv2.customerId=c.id AND pv2.status='POSTED'),0)
               ) AS outstandingBase,
               COALESCE((SELECT SUM(MAX(0,
                   oi.totalBase
                   - COALESCE((SELECT SUM(oacs.amountBase) FROM sales_additional_charge_settlements oacs JOIN sales_additional_charges oac ON oac.id=oacs.chargeId WHERE oacs.invoiceId=oi.id AND oacs.status='ACTIVE' AND oac.status='POSTED' AND oac.bearer='CUSTOMER' AND oac.paidBy='CUSTOMER_DIRECT'),0)
                   - COALESCE((SELECT SUM(ocra.amountBase + ocra.discountBase) FROM customer_receipt_allocations ocra WHERE ocra.invoiceId=oi.id),0)
                   - COALESCE((SELECT SUM(osr.totalBase) FROM sales_returns osr WHERE osr.salesInvoiceId=oi.id AND osr.status='POSTED' AND osr.settlementType='CUSTOMER_CREDIT'),0)
               )) FROM sales_invoices oi
               WHERE oi.customerId=c.id AND oi.status='POSTED' AND oi.paymentType='CREDIT'
                 AND oi.dueDate IS NOT NULL AND oi.dueDate < :now),0) AS overdueBase
        FROM customers c
        WHERE c.isActive=1
        ORDER BY outstandingBase DESC, c.nameAr
    """)
    fun observeReceivables(now: Long): Flow<List<CustomerReceivableRow>>

    @Query("""
        SELECT COUNT(*) FROM sales_invoices si
        WHERE si.paymentType = 'CREDIT' AND si.status = 'POSTED'
          AND si.dueDate IS NOT NULL AND si.dueDate < :now
          AND (si.totalBase
               - COALESCE((SELECT SUM(acs.amountBase) FROM sales_additional_charge_settlements acs JOIN sales_additional_charges ac ON ac.id=acs.chargeId WHERE acs.invoiceId=si.id AND acs.status='ACTIVE' AND ac.status='POSTED' AND ac.bearer='CUSTOMER' AND ac.paidBy='CUSTOMER_DIRECT'), 0)
               - COALESCE((SELECT SUM(cra.amountBase + cra.discountBase) FROM customer_receipt_allocations cra WHERE cra.invoiceId = si.id), 0)
               - COALESCE((SELECT SUM(sr.totalBase) FROM sales_returns sr WHERE sr.salesInvoiceId = si.id AND sr.status = 'POSTED' AND sr.settlementType = 'CUSTOMER_CREDIT'), 0)) > 0.000000001
    """)
    fun observeOverdueInvoiceCount(now: Long): Flow<Int>

    @Query("""
        SELECT CASE WHEN COUNT(*) = 0 OR COALESCE(SUM(totalBase), 0) <= 0 THEN 0.0
                    ELSE 100.0 * SUM(CASE WHEN paymentType = 'CASH' THEN totalBase ELSE 0 END) / SUM(totalBase)
               END
        FROM sales_invoices
        WHERE status = 'POSTED'
    """)
    fun observeCashSalesPct(): Flow<Double>
}
