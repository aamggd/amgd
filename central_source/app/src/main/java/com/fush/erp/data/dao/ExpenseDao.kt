package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fush.erp.data.entity.ExpenseAttachmentEntity
import com.fush.erp.data.entity.ExpenseDimensionEntity
import com.fush.erp.data.entity.ExpenseReportRow
import com.fush.erp.data.entity.SalesRepContributionRow
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDimension(row: ExpenseDimensionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttachment(row: ExpenseAttachmentEntity): Long

    @Query("SELECT * FROM expense_dimensions WHERE partyVoucherId=:voucherId LIMIT 1")
    suspend fun byPartyVoucherId(voucherId: Long): ExpenseDimensionEntity?

    @Query("SELECT * FROM expense_attachments WHERE expenseId=:expenseId ORDER BY createdAt DESC, id DESC")
    fun observeAttachments(expenseId: Long): Flow<List<ExpenseAttachmentEntity>>

    @Query("""
        SELECT ed.id AS expenseId,
               pv.id AS voucherId,
               pv.voucherNo AS voucherNo,
               pv.voucherDate AS voucherDate,
               a.id AS expenseAccountId,
               a.code AS expenseAccountCode,
               a.nameAr AS expenseAccountName,
               pv.amountBase AS amountBase,
               pv.description AS description,
               pv.currencyCode AS currencyCode,
               pv.amountOriginal AS amountOriginal,
               ed.paymentMethodSnapshot AS paymentMethod,
               ed.employeeId AS employeeId,
               ed.employeeNameSnapshot AS employeeName,
               ed.salesRepId AS salesRepId,
               ed.salesRepNameSnapshot AS salesRepName,
               ed.costCenterCode AS costCenterCode,
               ed.costCenterNameSnapshot AS costCenterName,
               ed.organizationUnit AS organizationUnit,
               ed.referenceType AS referenceType,
               ed.referenceId AS referenceId,
               ed.referenceNo AS referenceNo,
               ed.referenceLabelSnapshot AS referenceLabel,
               ed.customerId AS customerId,
               ed.customerNameSnapshot AS customerName,
               ed.supplierId AS supplierId,
               ed.supplierNameSnapshot AS supplierName,
               ed.itemId AS itemId,
               ed.itemNameSnapshot AS itemName,
               (SELECT COUNT(*) FROM expense_attachments ea WHERE ea.expenseId=ed.id) AS attachmentCount
        FROM expense_dimensions ed
        JOIN party_vouchers pv ON pv.id=ed.partyVoucherId
        JOIN accounts a ON a.id=pv.offsetAccountId
        WHERE pv.voucherType='EXPENSE' AND pv.status IN ('POSTED','REVERSED')
          AND pv.voucherDate BETWEEN :fromDate AND :toDate
        UNION ALL
        SELECT -ed.id AS expenseId,
               pv.id AS voucherId,
               'REV-' || pv.voucherNo AS voucherNo,
               COALESCE(pv.reversedAt,0) AS voucherDate,
               a.id AS expenseAccountId,
               a.code AS expenseAccountCode,
               a.nameAr AS expenseAccountName,
               -pv.amountBase AS amountBase,
               'عكس: ' || pv.reversalReason AS description,
               pv.currencyCode AS currencyCode,
               -pv.amountOriginal AS amountOriginal,
               ed.paymentMethodSnapshot AS paymentMethod,
               ed.employeeId AS employeeId,
               ed.employeeNameSnapshot AS employeeName,
               ed.salesRepId AS salesRepId,
               ed.salesRepNameSnapshot AS salesRepName,
               ed.costCenterCode AS costCenterCode,
               ed.costCenterNameSnapshot AS costCenterName,
               ed.organizationUnit AS organizationUnit,
               ed.referenceType AS referenceType,
               ed.referenceId AS referenceId,
               ed.referenceNo AS referenceNo,
               ed.referenceLabelSnapshot AS referenceLabel,
               ed.customerId AS customerId,
               ed.customerNameSnapshot AS customerName,
               ed.supplierId AS supplierId,
               ed.supplierNameSnapshot AS supplierName,
               ed.itemId AS itemId,
               ed.itemNameSnapshot AS itemName,
               0 AS attachmentCount
        FROM expense_dimensions ed
        JOIN party_vouchers pv ON pv.id=ed.partyVoucherId
        JOIN accounts a ON a.id=pv.offsetAccountId
        WHERE pv.voucherType='EXPENSE' AND pv.status='REVERSED'
          AND pv.reversedAt IS NOT NULL
          AND pv.reversedAt BETWEEN :fromDate AND :toDate
        ORDER BY voucherDate DESC, voucherId DESC
    """)
    suspend fun reportRows(fromDate: Long, toDate: Long): List<ExpenseReportRow>

    /**
     * Live historical expense stream used by the expense screen and its exports.
     *
     * A reversed expense remains visible at its original posting date, while a
     * synthetic negative row is emitted at the reversal date. This mirrors
     * [reportRows] so the UI, exports and dated reports share the same audit
     * semantics instead of silently dropping reversed vouchers.
     */
    @Query("""
        SELECT ed.id AS expenseId,
               pv.id AS voucherId,
               pv.voucherNo AS voucherNo,
               pv.voucherDate AS voucherDate,
               a.id AS expenseAccountId,
               a.code AS expenseAccountCode,
               a.nameAr AS expenseAccountName,
               pv.amountBase AS amountBase,
               pv.description AS description,
               pv.currencyCode AS currencyCode,
               pv.amountOriginal AS amountOriginal,
               ed.paymentMethodSnapshot AS paymentMethod,
               ed.employeeId AS employeeId,
               ed.employeeNameSnapshot AS employeeName,
               ed.salesRepId AS salesRepId,
               ed.salesRepNameSnapshot AS salesRepName,
               ed.costCenterCode AS costCenterCode,
               ed.costCenterNameSnapshot AS costCenterName,
               ed.organizationUnit AS organizationUnit,
               ed.referenceType AS referenceType,
               ed.referenceId AS referenceId,
               ed.referenceNo AS referenceNo,
               ed.referenceLabelSnapshot AS referenceLabel,
               ed.customerId AS customerId,
               ed.customerNameSnapshot AS customerName,
               ed.supplierId AS supplierId,
               ed.supplierNameSnapshot AS supplierName,
               ed.itemId AS itemId,
               ed.itemNameSnapshot AS itemName,
               (SELECT COUNT(*) FROM expense_attachments ea WHERE ea.expenseId=ed.id) AS attachmentCount
        FROM expense_dimensions ed
        JOIN party_vouchers pv ON pv.id=ed.partyVoucherId
        JOIN accounts a ON a.id=pv.offsetAccountId
        WHERE pv.voucherType='EXPENSE' AND pv.status IN ('POSTED','REVERSED')
        UNION ALL
        SELECT -ed.id AS expenseId,
               pv.id AS voucherId,
               'REV-' || pv.voucherNo AS voucherNo,
               COALESCE(pv.reversedAt,0) AS voucherDate,
               a.id AS expenseAccountId,
               a.code AS expenseAccountCode,
               a.nameAr AS expenseAccountName,
               -pv.amountBase AS amountBase,
               'عكس: ' || pv.reversalReason AS description,
               pv.currencyCode AS currencyCode,
               -pv.amountOriginal AS amountOriginal,
               ed.paymentMethodSnapshot AS paymentMethod,
               ed.employeeId AS employeeId,
               ed.employeeNameSnapshot AS employeeName,
               ed.salesRepId AS salesRepId,
               ed.salesRepNameSnapshot AS salesRepName,
               ed.costCenterCode AS costCenterCode,
               ed.costCenterNameSnapshot AS costCenterName,
               ed.organizationUnit AS organizationUnit,
               ed.referenceType AS referenceType,
               ed.referenceId AS referenceId,
               ed.referenceNo AS referenceNo,
               ed.referenceLabelSnapshot AS referenceLabel,
               ed.customerId AS customerId,
               ed.customerNameSnapshot AS customerName,
               ed.supplierId AS supplierId,
               ed.supplierNameSnapshot AS supplierName,
               ed.itemId AS itemId,
               ed.itemNameSnapshot AS itemName,
               0 AS attachmentCount
        FROM expense_dimensions ed
        JOIN party_vouchers pv ON pv.id=ed.partyVoucherId
        JOIN accounts a ON a.id=pv.offsetAccountId
        WHERE pv.voucherType='EXPENSE' AND pv.status='REVERSED'
          AND pv.reversedAt IS NOT NULL
        ORDER BY voucherDate DESC, voucherId DESC
    """)
    fun observeReportRows(): Flow<List<ExpenseReportRow>>

    @Query("""
        SELECT sr.id AS salesRepId,
               sr.fullNameAr AS salesRepName,
               COALESCE((SELECT SUM(si.totalBase) FROM sales_invoices si WHERE si.salesRepId=sr.id AND si.status='POSTED'),0) AS grossSalesBase,
               COALESCE((SELECT SUM(sret.totalBase)
                         FROM sales_returns sret
                         JOIN sales_invoices si2 ON si2.id=sret.salesInvoiceId
                         WHERE si2.salesRepId=sr.id AND sret.status='POSTED'),0) AS returnsBase,
               COALESCE((SELECT SUM(si.totalBase) FROM sales_invoices si WHERE si.salesRepId=sr.id AND si.status='POSTED'),0)
                 - COALESCE((SELECT SUM(sret.totalBase)
                             FROM sales_returns sret
                             JOIN sales_invoices si2 ON si2.id=sret.salesInvoiceId
                             WHERE si2.salesRepId=sr.id AND sret.status='POSTED'),0) AS netSalesBase,
               COALESCE((SELECT SUM(sa.costBase)
                         FROM sales_allocations sa
                         JOIN sales_lines sl ON sl.id=sa.salesLineId
                         JOIN sales_invoices si3 ON si3.id=sl.invoiceId
                         WHERE si3.salesRepId=sr.id AND si3.status='POSTED'),0) AS grossCogsBase,
               COALESCE((SELECT SUM(sret.totalCostBase)
                         FROM sales_returns sret
                         JOIN sales_invoices si4 ON si4.id=sret.salesInvoiceId
                         WHERE si4.salesRepId=sr.id AND sret.status='POSTED'),0) AS returnCostBase,
               COALESCE((SELECT SUM(sa.costBase)
                         FROM sales_allocations sa
                         JOIN sales_lines sl ON sl.id=sa.salesLineId
                         JOIN sales_invoices si3 ON si3.id=sl.invoiceId
                         WHERE si3.salesRepId=sr.id AND si3.status='POSTED'),0)
                 - COALESCE((SELECT SUM(sret.totalCostBase)
                             FROM sales_returns sret
                             JOIN sales_invoices si4 ON si4.id=sret.salesInvoiceId
                             WHERE si4.salesRepId=sr.id AND sret.status='POSTED'),0) AS netCogsBase,
               COALESCE((SELECT SUM(pv.amountBase)
                         FROM expense_dimensions ed
                         JOIN party_vouchers pv ON pv.id=ed.partyVoucherId
                         WHERE pv.voucherType='EXPENSE' AND pv.status='POSTED'
                           AND (ed.salesRepId=sr.id OR (ed.salesRepId IS NULL AND sr.employeeId IS NOT NULL AND ed.employeeId=sr.employeeId))),0) AS directExpensesBase,
               (
                 COALESCE((SELECT SUM(si.totalBase) FROM sales_invoices si WHERE si.salesRepId=sr.id AND si.status='POSTED'),0)
                 - COALESCE((SELECT SUM(sret.totalBase)
                             FROM sales_returns sret
                             JOIN sales_invoices si2 ON si2.id=sret.salesInvoiceId
                             WHERE si2.salesRepId=sr.id AND sret.status='POSTED'),0)
                 - (COALESCE((SELECT SUM(sa.costBase)
                              FROM sales_allocations sa
                              JOIN sales_lines sl ON sl.id=sa.salesLineId
                              JOIN sales_invoices si3 ON si3.id=sl.invoiceId
                              WHERE si3.salesRepId=sr.id AND si3.status='POSTED'),0)
                    - COALESCE((SELECT SUM(sret.totalCostBase)
                                FROM sales_returns sret
                                JOIN sales_invoices si4 ON si4.id=sret.salesInvoiceId
                                WHERE si4.salesRepId=sr.id AND sret.status='POSTED'),0))
                 - COALESCE((SELECT SUM(pv.amountBase)
                             FROM expense_dimensions ed
                             JOIN party_vouchers pv ON pv.id=ed.partyVoucherId
                             WHERE pv.voucherType='EXPENSE' AND pv.status='POSTED'
                               AND (ed.salesRepId=sr.id OR (ed.salesRepId IS NULL AND sr.employeeId IS NOT NULL AND ed.employeeId=sr.employeeId))),0)
               ) AS netContributionBase
        FROM sales_representatives sr
        WHERE sr.status='ACTIVE'
        ORDER BY sr.fullNameAr, sr.id
    """)
    fun observeSalesRepContribution(): Flow<List<SalesRepContributionRow>>
}
