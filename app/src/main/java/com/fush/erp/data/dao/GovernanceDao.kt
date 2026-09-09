package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GovernanceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocument(row: ControlledDocumentEntity): Long

    @Update
    suspend fun updateDocument(row: ControlledDocumentEntity)

    @Query("SELECT * FROM controlled_documents ORDER BY createdAt DESC, id DESC")
    fun observeDocuments(): Flow<List<ControlledDocumentEntity>>

    @Query("SELECT COUNT(*) FROM controlled_documents WHERE status = 'EFFECTIVE'")
    fun observeEffectiveDocumentCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChangeRequest(row: ChangeRequestEntity): Long

    @Update
    suspend fun updateChangeRequest(row: ChangeRequestEntity)

    @Query("SELECT * FROM change_requests ORDER BY createdAt DESC, id DESC")
    fun observeChangeRequests(): Flow<List<ChangeRequestEntity>>

    @Query("SELECT COUNT(*) FROM change_requests WHERE status NOT IN ('REJECTED','IMPLEMENTED','CLOSED')")
    fun observeOpenChangeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertApproval(row: ApprovalRequestEntity): Long

    @Update
    suspend fun updateApproval(row: ApprovalRequestEntity)

    @Query("SELECT * FROM approval_requests ORDER BY requestedAt DESC, id DESC")
    fun observeApprovals(): Flow<List<ApprovalRequestEntity>>

    @Query("SELECT COUNT(*) FROM approval_requests WHERE status = 'PENDING'")
    fun observePendingApprovalCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAudit(row: AuditEventEntity): Long

    @Query("SELECT COUNT(*) FROM audit_events WHERE action=:action AND entityType=:entityType AND entityId=:entityId")
    suspend fun auditMarkerCount(action: String, entityType: String, entityId: String): Int

    @Query("SELECT * FROM audit_events ORDER BY eventAt DESC, id DESC LIMIT 200")
    fun observeAuditEvents(): Flow<List<AuditEventEntity>>

    /**
     * Management audit trail projection. Filtering and paging happen in SQLite so the UI never
     * loads the complete immutable audit table into memory. No UPDATE/DELETE API is exposed.
     */
    @Query("""
        WITH audit_read AS (
            SELECT
                ae.id AS id, ae.eventAt AS eventAt, ae.userId AS userId,
                u.displayName AS actorDisplayName, u.username AS actorUsername,
                ae.action AS actionCode, ae.entityType AS entityType, ae.entityId AS entityId,
                ae.oldValue AS oldValue, ae.newValue AS newValue, ae.reason AS reason, ae.deviceInfo AS deviceInfo,
                CASE
                    WHEN ae.entityType IN ('SESSION','PERMISSION','ROLE','SECURITY_POLICY','SYSTEM','USER')
                         OR ae.action IN ('LOGIN_SUCCESS','LOGIN_FAILED','LOGOUT','PASSWORD_ONLY','ACCESS_DENIED','SECURITY_PERMISSION_HARDENING','SECURITY_PERMISSION_UPGRADE') THEN 'SECURITY'
                    WHEN ae.entityType IN ('DOCUMENT','CHANGE_REQUEST','APPROVAL','APPROVAL_REQUEST') THEN 'GOVERNANCE'
                    WHEN ae.entityType IN ('CUSTOMER','SALES_INVOICE','SALE','SALES_DOCUMENT','CUSTOMER_RECEIPT','SALES_RETURN','SALES_REP_CUSTOMER') THEN 'SALES'
                    WHEN ae.entityType IN ('SUPPLIER','PURCHASE_INVOICE','PURCHASE_DOCUMENT','SUPPLIER_PAYMENT','PURCHASE_RETURN') THEN 'PURCHASES'
                    WHEN ae.entityType IN ('JOURNAL_ENTRY','ACCOUNTING_PERIOD','FISCAL_YEAR','ACCOUNTING_FISCAL_YEAR','TREASURY_ACCOUNT','TREASURY_CASH_COUNT','TREASURY_FX_REVALUATION','BANK_STATEMENT','BANK_STATEMENT_LINE','PARTY_VOUCHER','EXPENSE_DIMENSION') THEN 'ACCOUNTING'
                    WHEN ae.entityType LIKE 'INVENTORY%' OR ae.entityType LIKE 'WAREHOUSE%' OR ae.entityType LIKE 'LEGACY_INVENTORY%' THEN 'INVENTORY'
                    WHEN ae.entityType LIKE 'PRODUCTION%' OR ae.entityType LIKE 'QUALITY%' THEN 'PRODUCTION'
                    WHEN ae.entityType IN ('EMPLOYEE','SALES_REP') OR ae.entityType LIKE 'HR_%' THEN 'HR'
                    WHEN ae.entityType IN ('DATABASE_BACKUP','BACKUP','RESTORE','ATTACHMENT_STORAGE') THEN 'BACKUP'
                    WHEN ae.entityType IN ('SUPPORT_TICKET','SUPPORT_SESSION','VENDOR_SUPPORT','VENDOR_SUPPORT_KEY')
                         OR ae.action LIKE 'SUPPORT_%' OR ae.action LIKE 'VENDOR_SUPPORT_%' THEN 'SUPPORT'
                    WHEN ae.entityType IN ('ITEM','MASTER','PRICE','CREDIT_LIMIT','UNIT_FACTOR','PROVINCE_POLICY') THEN 'MASTER_DATA'
                    ELSE 'OTHER'
                END AS sectionCode,
                COALESCE(
                    CASE
                        WHEN ae.entityType='CUSTOMER' THEN (SELECT c.code || ' — ' || c.nameAr FROM customers c WHERE c.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType='SUPPLIER' THEN (SELECT s.code || ' — ' || s.nameAr FROM suppliers s WHERE s.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType='ITEM' THEN (SELECT i.code || ' — ' || i.nameAr FROM items i WHERE i.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType IN ('SALES_INVOICE','SALE','SALES_DOCUMENT') THEN (SELECT si.invoiceNo || ' — ' || c.nameAr FROM sales_invoices si JOIN customers c ON c.id=si.customerId WHERE si.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType='CUSTOMER_RECEIPT' THEN (SELECT cr.receiptNo || ' — ' || c.nameAr FROM customer_receipts cr JOIN customers c ON c.id=cr.customerId WHERE cr.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType='SALES_RETURN' THEN (SELECT sr.returnNo || ' — ' || c.nameAr FROM sales_returns sr JOIN customers c ON c.id=sr.customerId WHERE sr.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType IN ('PURCHASE_INVOICE','PURCHASE_DOCUMENT') THEN (SELECT pi.invoiceNo || ' — ' || s.nameAr FROM purchase_invoices pi JOIN suppliers s ON s.id=pi.supplierId WHERE pi.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType='SUPPLIER_PAYMENT' THEN (SELECT sp.paymentNo || ' — ' || s.nameAr FROM supplier_payments sp JOIN suppliers s ON s.id=sp.supplierId WHERE sp.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType='PURCHASE_RETURN' THEN (SELECT pr.returnNo || ' — ' || s.nameAr FROM purchase_returns pr JOIN suppliers s ON s.id=pr.supplierId WHERE pr.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType='JOURNAL_ENTRY' THEN (SELECT je.entryNo || ' — ' || je.description FROM journal_entries je WHERE je.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType='PARTY_VOUCHER' THEN (SELECT pv.voucherNo || CASE WHEN pv.partyNameSnapshot<>'' THEN ' — ' || pv.partyNameSnapshot ELSE '' END FROM party_vouchers pv WHERE pv.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType='PRODUCTION_ORDER' THEN (SELECT po.orderNo || ' — ' || i.nameAr FROM production_orders po JOIN items i ON i.id=po.productItemId WHERE po.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType='PRODUCTION_BATCH' THEN (SELECT pb.batchNo FROM production_batches pb WHERE pb.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType='WAREHOUSE_TRANSFER' THEN (SELECT wt.transferNo FROM warehouse_transfers wt WHERE wt.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType='SALES_REP' THEN (SELECT sr.code || ' — ' || sr.fullNameAr FROM sales_representatives sr WHERE sr.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType='USER' THEN (SELECT ux.displayName || ' — ' || ux.username FROM users ux WHERE ux.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType='TREASURY_ACCOUNT' THEN (SELECT ta.code || ' — ' || ta.nameAr FROM treasury_accounts ta WHERE ta.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType='TREASURY_CASH_COUNT' THEN (SELECT ta.code || ' — ' || ta.nameAr FROM treasury_cash_counts tc JOIN treasury_accounts ta ON ta.id=tc.treasuryAccountId WHERE tc.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType='DOCUMENT' THEN (SELECT cd.documentCode || ' — ' || cd.titleAr FROM controlled_documents cd WHERE cd.id=CAST(ae.entityId AS INTEGER))
                        WHEN ae.entityType='CHANGE_REQUEST' THEN (SELECT crq.requestNo || ' — ' || crq.subject FROM change_requests crq WHERE crq.id=CAST(ae.entityId AS INTEGER))
                        ELSE NULL
                    END,
                    CASE WHEN ae.entityId <> '' THEN ae.entityType || ' #' || ae.entityId ELSE ae.entityType END
                ) AS referenceDisplay
            FROM audit_events ae
            LEFT JOIN users u ON u.id=ae.userId
        )
        SELECT * FROM audit_read
        WHERE (:fromAt IS NULL OR eventAt >= :fromAt)
          AND (:toAt IS NULL OR eventAt <= :toAt)
          AND (:userId IS NULL OR userId = :userId)
          AND (:section = '' OR sectionCode = :section)
          AND (:eventType = '' OR entityType = :eventType)
          AND (
                :operation = ''
                OR (:operation='CREATE' AND actionCode IN ('CREATE','CREATE_BACKUP'))
                OR (:operation='UPDATE' AND (actionCode='UPDATE' OR actionCode LIKE '%UPDATE%' OR actionCode LIKE '%CORRECT%'))
                OR (:operation='POST' AND actionCode='POST')
                OR (:operation='REVERSE' AND (actionCode='REVERSE' OR actionCode LIKE '%REVERSE%'))
                OR (:operation='DELETE' AND (actionCode='DELETE' OR actionCode LIKE '%DELETE%'))
                OR (:operation='APPROVE' AND actionCode IN ('APPROVE','APPROVED'))
                OR (:operation='REOPEN' AND actionCode='REOPEN')
                OR (:operation='CLOSE' AND actionCode='CLOSE')
                OR (:operation='LOGIN' AND actionCode IN ('LOGIN_SUCCESS','LOGIN_FAILED','LOGOUT','PASSWORD_ONLY'))
                OR (:operation='PERMISSION' AND (entityType IN ('PERMISSION','ROLE','SECURITY_POLICY') OR actionCode LIKE 'SECURITY_PERMISSION%'))
                OR (:operation='SETTINGS' AND (entityType IN ('SYSTEM','SECURITY_POLICY','PROVINCE_POLICY','WAREHOUSE_REORDER_POLICY','INVENTORY_PLANNING_POLICY') OR actionCode LIKE '%SETTING%'))
          )
          AND (
                :search = '' OR
                lower(referenceDisplay) LIKE '%' || lower(:search) || '%' OR
                lower(COALESCE(actorDisplayName,'')) LIKE '%' || lower(:search) || '%' OR
                lower(COALESCE(actorUsername,'')) LIKE '%' || lower(:search) || '%' OR
                lower(entityId) LIKE '%' || lower(:search) || '%' OR
                lower(entityType) LIKE '%' || lower(:search) || '%' OR
                lower(actionCode) LIKE '%' || lower(:search) || '%' OR
                lower(oldValue) LIKE '%' || lower(:search) || '%' OR
                lower(newValue) LIKE '%' || lower(:search) || '%' OR
                lower(reason) LIKE '%' || lower(:search) || '%'
          )
        ORDER BY eventAt DESC, id DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun auditTrailPage(
        fromAt: Long?,
        toAt: Long?,
        userId: Long?,
        section: String,
        operation: String,
        eventType: String,
        search: String,
        limit: Int,
        offset: Int,
    ): List<AuditTrailRow>

    @Query("SELECT * FROM audit_events WHERE entityType=:entityType AND entityId=:entityId ORDER BY eventAt DESC, id DESC LIMIT 200")
    fun observeAuditEventsForEntity(entityType: String, entityId: String): Flow<List<AuditEventEntity>>

    @Query("""
        SELECT ae.* FROM audit_events ae
        WHERE (ae.entityType='CUSTOMER' AND ae.entityId=CAST(:customerId AS TEXT))
           OR (ae.entityType='PARTY_VOUCHER' AND CAST(ae.entityId AS INTEGER) IN
               (SELECT id FROM party_vouchers WHERE customerId=:customerId))
           OR (ae.entityType='CUSTOMER_RECEIPT' AND CAST(ae.entityId AS INTEGER) IN
               (SELECT id FROM customer_receipts WHERE customerId=:customerId))
        ORDER BY ae.eventAt DESC, ae.id DESC LIMIT 300
    """)
    fun observeCustomerAudit(customerId: Long): Flow<List<AuditEventEntity>>

    @Query("""
        SELECT ae.* FROM audit_events ae
        WHERE (ae.entityType='SUPPLIER' AND ae.entityId=CAST(:supplierId AS TEXT))
           OR (ae.entityType='PARTY_VOUCHER' AND CAST(ae.entityId AS INTEGER) IN
               (SELECT id FROM party_vouchers WHERE supplierId=:supplierId))
           OR (ae.entityType='SUPPLIER_PAYMENT' AND CAST(ae.entityId AS INTEGER) IN
               (SELECT id FROM supplier_payments WHERE supplierId=:supplierId))
        ORDER BY ae.eventAt DESC, ae.id DESC LIMIT 300
    """)
    fun observeSupplierAudit(supplierId: Long): Flow<List<AuditEventEntity>>
}
