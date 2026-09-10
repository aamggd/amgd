package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SalesRepresentativeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: SalesRepresentativeEntity): Long

    @Update
    suspend fun update(row: SalesRepresentativeEntity)

    @Query("SELECT * FROM sales_representatives ORDER BY fullNameAr, id")
    fun observeAll(): Flow<List<SalesRepresentativeEntity>>

    @Query("SELECT * FROM sales_representatives WHERE status='ACTIVE' ORDER BY fullNameAr, id")
    fun observeActive(): Flow<List<SalesRepresentativeEntity>>

    @Query("SELECT * FROM sales_representatives WHERE status='ACTIVE' ORDER BY fullNameAr, id")
    suspend fun active(): List<SalesRepresentativeEntity>

    @Query("SELECT * FROM sales_representatives WHERE id=:id LIMIT 1")
    suspend fun byId(id: Long): SalesRepresentativeEntity?

    @Query("SELECT * FROM sales_representatives WHERE employeeId=:employeeId LIMIT 1")
    suspend fun byEmployeeId(employeeId: Long): SalesRepresentativeEntity?

    @Query("SELECT COUNT(*) FROM sales_representatives WHERE status='ACTIVE'")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT MAX(CAST(SUBSTR(code, 5) AS INTEGER)) FROM sales_representatives WHERE code LIKE 'REP-%'")
    suspend fun maxSequence(): Int?

    @Query("SELECT * FROM customers WHERE salesRepId=:repId ORDER BY nameAr, id")
    fun observeCustomers(repId: Long): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM sales_invoices WHERE salesRepId=:repId ORDER BY invoiceDate DESC, id DESC LIMIT 100")
    fun observeInvoices(repId: Long): Flow<List<SalesInvoiceEntity>>

    @Query("SELECT * FROM sales_commissions WHERE salesRepId=:repId ORDER BY createdAt DESC, id DESC LIMIT 200")
    fun observeCommissions(repId: Long): Flow<List<SalesCommissionEntity>>

    @Query("SELECT COALESCE(SUM(totalBase),0) FROM sales_invoices WHERE salesRepId=:repId AND status='POSTED'")
    fun observeSalesBase(repId: Long): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(cra.amountBase),0)
        FROM customer_receipt_allocations cra
        JOIN sales_invoices si ON si.id=cra.invoiceId
        WHERE si.salesRepId=:repId AND si.status='POSTED'
    """)
    fun observeCollectionsBase(repId: Long): Flow<Double>

    @Query("""
        SELECT COALESCE(SUM(sr.totalBase),0)
        FROM sales_returns sr
        JOIN sales_invoices si ON si.id=sr.salesInvoiceId
        WHERE si.salesRepId=:repId AND sr.status='POSTED'
    """)
    fun observeReturnsBase(repId: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(earnedBase),0) FROM sales_commissions WHERE salesRepId=:repId")
    fun observeCommissionEarnedBase(repId: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(reversedBase),0) FROM sales_commissions WHERE salesRepId=:repId")
    fun observeCommissionReversedBase(repId: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(amountBase),0) FROM party_vouchers WHERE salesRepId=:repId AND voucherType='PAYMENT' AND status='POSTED'")
    fun observeCommissionPaidBase(repId: Long): Flow<Double>

    @Query("SELECT COUNT(*) FROM sales_invoices WHERE customerId=:customerId AND salesRepId IS NULL")
    suspend fun unassignedInvoiceCountForCustomer(customerId: Long): Int

    @Query("SELECT COALESCE(SUM(totalBase),0) FROM sales_invoices WHERE customerId=:customerId AND salesRepId IS NULL AND status='POSTED'")
    suspend fun unassignedPostedSalesBaseForCustomer(customerId: Long): Double

    @Query("""
        UPDATE sales_invoices
        SET salesRepId=:repId, salesRepNameSnapshot=:repName
        WHERE customerId=:customerId AND salesRepId IS NULL
    """)
    suspend fun linkUnassignedInvoicesForCustomer(customerId: Long, repId: Long, repName: String): Int

    @Query("""
        UPDATE sales_commissions
        SET salesRepId=:repId, beneficiary=:repName
        WHERE salesRepId IS NULL
          AND invoiceId IN (
              SELECT id FROM sales_invoices
              WHERE customerId=:customerId AND salesRepId=:repId
          )
    """)
    suspend fun linkUnassignedCommissionsForCustomer(customerId: Long, repId: Long, repName: String): Int
}
