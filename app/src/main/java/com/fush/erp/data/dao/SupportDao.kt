package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SupportDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTicket(row: SupportTicketEntity): Long

    @Update
    suspend fun updateTicket(row: SupportTicketEntity)

    @Query("SELECT * FROM support_tickets ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeTickets(limit: Int = 200): Flow<List<SupportTicketEntity>>

    @Query("SELECT * FROM support_tickets WHERE id=:id LIMIT 1")
    suspend fun ticketById(id: Long): SupportTicketEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(row: SupportSessionEntity): Long

    @Update
    suspend fun updateSession(row: SupportSessionEntity)

    @Query("SELECT * FROM support_sessions WHERE id=:id LIMIT 1")
    suspend fun sessionById(id: Long): SupportSessionEntity?

    @Query("""
        SELECT ss.id, ss.ticketId, ss.companyId, ss.branchId, ss.supportUserId,
               u.displayName AS supportDisplayName, u.username AS supportUsername,
               ss.activatedBy, ss.status, ss.startedAt, ss.expiresAt, ss.revokedAt, ss.closedAt, ss.closeReason
        FROM support_sessions ss
        JOIN users u ON u.id=ss.supportUserId
        ORDER BY ss.startedAt DESC, ss.id DESC
        LIMIT :limit
    """)
    fun observeSessions(limit: Int = 200): Flow<List<SupportSessionViewRow>>

    @Query("""
        SELECT * FROM support_sessions
        WHERE supportUserId=:supportUserId AND status='ACTIVE' AND revokedAt IS NULL AND closedAt IS NULL
          AND startedAt <= :now AND expiresAt > :now
        ORDER BY expiresAt DESC, id DESC
    """)
    suspend fun activeSessionsForSupport(supportUserId: Long, now: Long): List<SupportSessionEntity>

    @Query("""
        SELECT COUNT(*) FROM support_sessions
        WHERE ticketId=:ticketId AND status='ACTIVE' AND revokedAt IS NULL AND closedAt IS NULL AND expiresAt > :now
    """)
    suspend fun activeSessionCountForTicket(ticketId: Long, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSnapshot(row: SupportSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAudit(row: SupportAuditLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertValidation(row: SupportValidationResultEntity): Long

    @Query("SELECT * FROM support_audit_log WHERE sessionId=:sessionId ORDER BY eventAt DESC, id DESC LIMIT :limit")
    fun observeAuditForSession(sessionId: Long, limit: Int = 300): Flow<List<SupportAuditLogEntity>>

    @Query("SELECT * FROM support_validation_results WHERE sessionId=:sessionId ORDER BY eventAt DESC, id DESC LIMIT :limit")
    fun observeValidationForSession(sessionId: Long, limit: Int = 100): Flow<List<SupportValidationResultEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVendorSupportIdentity(row: VendorSupportIdentityEntity): Long

    @Query("SELECT * FROM vendor_support_identities ORDER BY credentialVersion DESC, id DESC LIMIT 1")
    suspend fun latestVendorIdentity(): VendorSupportIdentityEntity?

    @Query("SELECT * FROM vendor_support_identities ORDER BY credentialVersion DESC, id DESC LIMIT 1")
    fun observeLatestVendorIdentity(): Flow<VendorSupportIdentityEntity?>

    @Query("SELECT * FROM vendor_support_identities WHERE supportUserId=:supportUserId ORDER BY credentialVersion DESC, id DESC LIMIT 1")
    suspend fun vendorIdentityBySupportUser(supportUserId: Long): VendorSupportIdentityEntity?

    @Query("SELECT COUNT(*) FROM vendor_support_identities")
    suspend fun vendorIdentityCount(): Int

    @Query("""
        SELECT * FROM vendor_support_identities vsi
        WHERE vsi.installationBinding=:installationBinding
          AND vsi.id=(SELECT id FROM vendor_support_identities ORDER BY credentialVersion DESC, id DESC LIMIT 1)
        LIMIT 1
    """)
    suspend fun vendorIdentityForInstallation(installationBinding: String): VendorSupportIdentityEntity?

    @Query("""
        SELECT * FROM vendor_support_identities vsi
        WHERE vsi.installationBinding=:installationBinding
          AND vsi.id=(SELECT id FROM vendor_support_identities ORDER BY credentialVersion DESC, id DESC LIMIT 1)
        LIMIT 1
    """)
    fun observeVendorIdentity(installationBinding: String): Flow<VendorSupportIdentityEntity?>

    @Query("""
        SELECT u.* FROM users u
        JOIN vendor_support_identities vsi ON vsi.supportUserId=u.id
        WHERE u.role='FUSH_SUPPORT' AND u.isActive=1
          AND vsi.installationBinding=:installationBinding
          AND vsi.id=(SELECT id FROM vendor_support_identities ORDER BY credentialVersion DESC, id DESC LIMIT 1)
        ORDER BY u.displayName, u.username
    """)
    fun observeSupportUsers(installationBinding: String): Flow<List<UserEntity>>

    @Query("""
        SELECT u.* FROM users u
        WHERE u.role='FUSH_SUPPORT'
          AND NOT EXISTS (SELECT 1 FROM vendor_support_identities vsi WHERE vsi.supportUserId=u.id)
        ORDER BY u.id
    """)
    suspend fun legacySupportUsers(): List<UserEntity>

    @Query("""
        SELECT u.* FROM users u
        WHERE u.role='FUSH_SUPPORT'
          AND NOT EXISTS (SELECT 1 FROM vendor_support_identities vsi WHERE vsi.supportUserId=u.id)
        ORDER BY u.id
    """)
    fun observeLegacySupportUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVendorSupportKey(row: VendorSupportKeyEntity): Long

    @Query("SELECT * FROM vendor_support_keys ORDER BY id DESC LIMIT 1")
    suspend fun latestVendorSupportKey(): VendorSupportKeyEntity?

    @Query("SELECT * FROM vendor_support_keys ORDER BY id DESC LIMIT 1")
    fun observeLatestVendorSupportKey(): Flow<VendorSupportKeyEntity?>

    @Query("SELECT COUNT(*) FROM vendor_support_keys WHERE keyId=:keyId")
    suspend fun vendorSupportKeyCount(keyId: String): Int

    // Cross-module validation used after typed financial repair commands.
    @Query("SELECT COUNT(*) FROM sales_invoices WHERE id=:invoiceId AND status='POSTED'")
    suspend fun postedSalesInvoiceCount(invoiceId: Long): Int

    @Query("SELECT paymentType FROM sales_invoices WHERE id=:invoiceId LIMIT 1")
    suspend fun salesInvoicePaymentType(invoiceId: Long): String?

    @Query("""
        SELECT COUNT(*)
        FROM sales_lines sl
        WHERE sl.invoiceId=:invoiceId
          AND NOT EXISTS (
              SELECT 1 FROM stock_movements sm
              WHERE sm.referenceType='SALES_LINE' AND sm.referenceId=sl.id
          )
    """)
    suspend fun salesLinesMissingStockMovement(invoiceId: Long): Int

    @Query("""
        SELECT COUNT(*)
        FROM journal_lines jl
        JOIN treasury_accounts ta ON ta.accountId=jl.accountId
        WHERE jl.entryId=:entryId AND jl.debit > 0
    """)
    suspend fun treasuryDebitLineCount(entryId: Long): Int

    @Query("SELECT COUNT(*) FROM purchase_invoices WHERE id=:invoiceId AND status='POSTED'")
    suspend fun postedPurchaseInvoiceCount(invoiceId: Long): Int

    @Query("SELECT paymentType FROM purchase_invoices WHERE id=:invoiceId LIMIT 1")
    suspend fun purchaseInvoicePaymentType(invoiceId: Long): String?

    @Query("""
        SELECT COUNT(*)
        FROM purchase_lines pl
        WHERE pl.invoiceId=:invoiceId
          AND NOT EXISTS (
              SELECT 1 FROM stock_movements sm
              WHERE sm.referenceType='PURCHASE_LINE' AND sm.referenceId=pl.id
          )
    """)
    suspend fun purchaseLinesMissingStockMovement(invoiceId: Long): Int

    @Query("""
        SELECT COUNT(*)
        FROM journal_lines jl
        JOIN treasury_accounts ta ON ta.accountId=jl.accountId
        WHERE jl.entryId=:entryId AND jl.credit > 0
    """)
    suspend fun treasuryCreditLineCount(entryId: Long): Int

    @Query("SELECT COUNT(*) FROM customer_receipts WHERE id=:receiptId")
    suspend fun customerReceiptCount(receiptId: Long): Int

    @Query("SELECT COUNT(*) FROM supplier_payments WHERE id=:paymentId")
    suspend fun supplierPaymentCount(paymentId: Long): Int

    @Query("SELECT settlementType FROM sales_returns WHERE id=:returnId AND status='POSTED' LIMIT 1")
    suspend fun postedSalesReturnSettlementType(returnId: Long): String?

    @Query("""
        SELECT COUNT(*)
        FROM (SELECT DISTINCT itemId FROM sales_return_lines WHERE returnId=:returnId) x
        WHERE NOT EXISTS (
            SELECT 1 FROM stock_movements sm
            WHERE sm.referenceType='SALES_RETURN' AND sm.referenceId=:returnId AND sm.itemId=x.itemId
        )
    """)
    suspend fun salesReturnLinesMissingStockMovement(returnId: Long): Int

    @Query("SELECT settlementType FROM purchase_returns WHERE id=:returnId AND status='POSTED' LIMIT 1")
    suspend fun postedPurchaseReturnSettlementType(returnId: Long): String?

    @Query("""
        SELECT COUNT(*)
        FROM (SELECT DISTINCT itemId FROM purchase_return_lines WHERE returnId=:returnId) x
        WHERE NOT EXISTS (
            SELECT 1 FROM stock_movements sm
            WHERE sm.referenceType='PURCHASE_RETURN' AND sm.referenceId=:returnId AND sm.itemId=x.itemId
        )
    """)
    suspend fun purchaseReturnLinesMissingStockMovement(returnId: Long): Int

    @Query("""
        SELECT 'ERROR' AS severity, 'ACCOUNTING' AS module, 'JOURNAL_ENTRY' AS recordType,
               CAST(je.id AS TEXT) AS recordId, je.entryNo AS reference,
               'debit=' || printf('%.2f', COALESCE(SUM(jl.debit),0)) || '; credit=' || printf('%.2f', COALESCE(SUM(jl.credit),0)) AS details
        FROM journal_entries je
        LEFT JOIN journal_lines jl ON jl.entryId=je.id
        WHERE je.status IN ('POSTED','STAGING')
        GROUP BY je.id
        HAVING ABS(COALESCE(SUM(jl.debit),0)-COALESCE(SUM(jl.credit),0)) > 0.0001
        ORDER BY je.entryDate DESC, je.id DESC
        LIMIT :limit
    """)
    suspend fun findUnbalancedJournals(limit: Int = 100): List<SupportFindingRow>

    @Query("""
        SELECT 'WARN' AS severity, 'ACCOUNTING' AS module, 'JOURNAL_ENTRY' AS recordType,
               CAST(je.id AS TEXT) AS recordId, je.entryNo AS reference,
               'status=' || je.status || '; source=' || je.sourceType || ':' || COALESCE(je.sourceId,'') AS details
        FROM journal_entries je
        WHERE je.status='STAGING'
        UNION ALL
        SELECT 'ERROR','SALES','SALES_INVOICE',CAST(si.id AS TEXT),si.invoiceNo,'POSTED invoice missing SALE journal'
        FROM sales_invoices si
        WHERE si.status='POSTED' AND NOT EXISTS (
            SELECT 1 FROM journal_entries je WHERE je.sourceType='SALE' AND je.sourceId=CAST(si.id AS TEXT) AND je.status='POSTED'
        )
        UNION ALL
        SELECT 'ERROR','PURCHASES','PURCHASE_INVOICE',CAST(pi.id AS TEXT),pi.invoiceNo,'POSTED invoice missing PURCHASE journal'
        FROM purchase_invoices pi
        WHERE pi.status='POSTED' AND NOT EXISTS (
            SELECT 1 FROM journal_entries je WHERE je.sourceType='PURCHASE' AND je.sourceId=CAST(pi.id AS TEXT) AND je.status='POSTED'
        )
        LIMIT :limit
    """)
    suspend fun findIncompleteTransactions(limit: Int = 100): List<SupportFindingRow>

    @Query("""
        SELECT 'INFO' AS severity, 'SALES' AS module, 'SALES_INVOICE' AS recordType,
               CAST(si.id AS TEXT) AS recordId, si.invoiceNo AS reference,
               'status=' || si.status || '; customer=' || c.code || ' — ' || c.nameAr ||
               '; totalBase=' || printf('%.2f',si.totalBase) ||
               '; lines=' || (SELECT COUNT(*) FROM sales_lines sl WHERE sl.invoiceId=si.id) ||
               '; journal=' || COALESCE((SELECT je.entryNo FROM journal_entries je WHERE je.sourceType='SALE' AND je.sourceId=CAST(si.id AS TEXT) ORDER BY je.id DESC LIMIT 1),'MISSING') AS details
        FROM sales_invoices si JOIN customers c ON c.id=si.customerId
        WHERE si.id=:invoiceId
        UNION ALL
        SELECT 'INFO','PURCHASES','PURCHASE_INVOICE',CAST(pi.id AS TEXT),pi.invoiceNo,
               'status=' || pi.status || '; supplier=' || s.code || ' — ' || s.nameAr ||
               '; totalBase=' || printf('%.2f',pi.totalBase) ||
               '; lines=' || (SELECT COUNT(*) FROM purchase_lines pl WHERE pl.invoiceId=pi.id) ||
               '; journal=' || COALESCE((SELECT je.entryNo FROM journal_entries je WHERE je.sourceType='PURCHASE' AND je.sourceId=CAST(pi.id AS TEXT) ORDER BY je.id DESC LIMIT 1),'MISSING')
        FROM purchase_invoices pi JOIN suppliers s ON s.id=pi.supplierId
        WHERE pi.id=:invoiceId
    """)
    suspend fun diagnoseInvoice(invoiceId: Long): List<SupportFindingRow>

    @Query("""
        SELECT 'INFO' AS severity, 'SALES' AS module, 'CUSTOMER' AS recordType,
               CAST(c.id AS TEXT) AS recordId, c.code || ' — ' || c.nameAr AS reference,
               'openItemBalance=' || printf('%.2f', MAX(0.0,
                   COALESCE((SELECT SUM(si.totalBase) FROM sales_invoices si
                             WHERE si.customerId=c.id AND si.status='POSTED' AND si.paymentType='CREDIT'),0)
                   - COALESCE((SELECT SUM(sr.totalBase) FROM sales_returns sr
                               JOIN sales_invoices six ON six.id=sr.salesInvoiceId
                               WHERE sr.customerId=c.id AND sr.status='POSTED' AND sr.settlementType='CUSTOMER_CREDIT'
                                 AND six.paymentType='CREDIT'),0)
                   - COALESCE((SELECT SUM(cra.amountBase + cra.discountBase) FROM customer_receipt_allocations cra
                               JOIN sales_invoices six2 ON six2.id=cra.invoiceId
                               WHERE six2.customerId=c.id AND six2.paymentType='CREDIT'),0)
                   + COALESCE((SELECT SUM(CASE WHEN pv.voucherType='PAYMENT' THEN pv.amountBase WHEN pv.voucherType='RECEIPT' THEN -pv.amountBase ELSE 0 END)
                               FROM party_vouchers pv WHERE pv.customerId=c.id AND pv.status='POSTED' AND pv.partyType='CUSTOMER'),0)
               )) AS details
        FROM customers c
        WHERE c.id=:customerId
    """)
    suspend fun checkCustomerBalance(customerId: Long): List<SupportFindingRow>

    @Query("""
        SELECT 'INFO' AS severity, 'PURCHASES' AS module, 'SUPPLIER' AS recordType,
               CAST(s.id AS TEXT) AS recordId, s.code || ' — ' || s.nameAr AS reference,
               'openItemBalance=' || printf('%.2f', MAX(0.0,
                   COALESCE((SELECT SUM(pi.totalBase) FROM purchase_invoices pi
                             WHERE pi.supplierId=s.id AND pi.status='POSTED' AND pi.paymentType='CREDIT'),0)
                   - COALESCE((SELECT SUM(pr.totalBase) FROM purchase_returns pr
                               JOIN purchase_invoices pix ON pix.id=pr.purchaseInvoiceId
                               WHERE pr.supplierId=s.id AND pr.status='POSTED' AND pr.settlementType='SUPPLIER_CREDIT'
                                 AND pix.paymentType='CREDIT'),0)
                   - COALESCE((SELECT SUM(spa.allocatedBase) FROM supplier_payment_allocations spa
                               JOIN supplier_payments sp ON sp.id=spa.paymentId
                               JOIN purchase_invoices pi2 ON pi2.id=spa.invoiceId
                               WHERE sp.supplierId=s.id AND pi2.paymentType='CREDIT'),0)
                   + COALESCE((SELECT SUM(CASE WHEN pv.voucherType='RECEIPT' THEN pv.amountBase WHEN pv.voucherType='PAYMENT' THEN -pv.amountBase ELSE 0 END)
                               FROM party_vouchers pv WHERE pv.supplierId=s.id AND pv.status='POSTED' AND pv.partyType='SUPPLIER'),0)
               )) AS details
        FROM suppliers s
        WHERE s.id=:supplierId
    """)
    suspend fun checkSupplierBalance(supplierId: Long): List<SupportFindingRow>

    @Query("""
        SELECT 'INFO' AS severity, 'ACCOUNTING' AS module, 'TREASURY_ACCOUNT' AS recordType,
               CAST(ta.id AS TEXT) AS recordId, ta.code || ' — ' || ta.nameAr AS reference,
               'GL balance=' || printf('%.2f', COALESCE(SUM(CASE WHEN je.status='POSTED' THEN jl.debit-jl.credit ELSE 0 END),0)) AS details
        FROM treasury_accounts ta
        JOIN accounts a ON a.id=ta.accountId
        LEFT JOIN journal_lines jl ON jl.accountId=a.id
        LEFT JOIN journal_entries je ON je.id=jl.entryId
        WHERE ta.id=:treasuryId
        GROUP BY ta.id
    """)
    suspend fun checkTreasuryBalance(treasuryId: Long): List<SupportFindingRow>

    @Query("""
        SELECT 'INFO' AS severity, 'INVENTORY' AS module, 'ITEM_WAREHOUSE' AS recordType,
               CAST(:itemId AS TEXT) AS recordId,
               w.code || ' / ' || i.code AS reference,
               'quantity=' || printf('%.6f', COALESCE(SUM(sm.quantityBase),0)) ||
               '; weightedInputCost=' || printf('%.6f', CASE WHEN SUM(CASE WHEN sm.quantityBase>0 THEN sm.quantityBase ELSE 0 END)=0 THEN 0 ELSE
                    SUM(CASE WHEN sm.quantityBase>0 THEN sm.quantityBase*sm.unitCostBase ELSE 0 END) /
                    SUM(CASE WHEN sm.quantityBase>0 THEN sm.quantityBase ELSE 0 END) END) AS details
        FROM warehouses w CROSS JOIN items i
        LEFT JOIN stock_movements sm ON sm.warehouseId=w.id AND sm.itemId=i.id AND sm.movementDate<=:asOf
        WHERE w.id=:warehouseId AND i.id=:itemId
        GROUP BY w.id,i.id
    """)
    suspend fun recalculateInventory(warehouseId: Long, itemId: Long, asOf: Long): List<SupportFindingRow>

    @Query("""
        SELECT 'INFO' AS severity, 'INVENTORY' AS module, 'ITEM' AS recordType,
               CAST(i.id AS TEXT) AS recordId, i.code || ' — ' || i.nameAr AS reference,
               'netQty=' || printf('%.6f', COALESCE(SUM(icl.quantityBase),0)) ||
               '; netValue=' || printf('%.6f', COALESCE(SUM(icl.signedValueBase),0)) ||
               '; averageCost=' || printf('%.6f', CASE WHEN ABS(COALESCE(SUM(icl.quantityBase),0)) < 0.0000001 THEN 0 ELSE SUM(icl.signedValueBase)/SUM(icl.quantityBase) END) AS details
        FROM items i
        LEFT JOIN inventory_cost_layers icl ON icl.itemId=i.id
        WHERE i.id=:itemId
        GROUP BY i.id
    """)
    suspend fun recalculateAverageCost(itemId: Long): List<SupportFindingRow>
}
