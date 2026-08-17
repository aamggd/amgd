package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.audit.AuditEventMetadata
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
    suspend fun insertAuditRow(row: AuditEventEntity): Long

    @Query("SELECT sessionVersion FROM users WHERE id=:userId LIMIT 1")
    suspend fun auditSessionVersion(userId: Long): Long?

    suspend fun insertAudit(row: AuditEventEntity): Long {
        val sessionVersion = auditSessionVersion(row.userId)
        return insertAuditRow(AuditEventMetadata.enrich(row, sessionVersion))
    }

    @Query("SELECT * FROM audit_events ORDER BY eventAt DESC, id DESC LIMIT 200")
    fun observeAuditEvents(): Flow<List<AuditEventEntity>>

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
