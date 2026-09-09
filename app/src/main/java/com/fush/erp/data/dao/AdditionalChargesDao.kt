package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AdditionalChargesDao {
    @Query("SELECT * FROM sales_additional_charge_types WHERE isActive = 1 ORDER BY nameAr, id")
    fun observeActiveTypes(): Flow<List<AdditionalChargeTypeEntity>>

    @Query("SELECT * FROM sales_additional_charge_types ORDER BY isActive DESC, nameAr, id")
    suspend fun allTypes(): List<AdditionalChargeTypeEntity>

    @Query("SELECT * FROM sales_additional_charge_types WHERE id = :id LIMIT 1")
    suspend fun typeById(id: Long): AdditionalChargeTypeEntity?

    @Query("SELECT * FROM sales_additional_charge_types WHERE code = :code LIMIT 1")
    suspend fun typeByCode(code: String): AdditionalChargeTypeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTypesIgnore(rows: List<AdditionalChargeTypeEntity>)

    @Update
    suspend fun updateType(row: AdditionalChargeTypeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCharge(row: SalesAdditionalChargeEntity): Long

    @Update
    suspend fun updateCharge(row: SalesAdditionalChargeEntity)

    @Query("SELECT * FROM sales_additional_charges WHERE id = :id LIMIT 1")
    suspend fun chargeById(id: Long): SalesAdditionalChargeEntity?

    @Query("SELECT * FROM sales_additional_charges WHERE chargeNo = :chargeNo LIMIT 1")
    suspend fun chargeByNo(chargeNo: String): SalesAdditionalChargeEntity?

    @Query("SELECT COUNT(*) FROM sales_additional_charges WHERE chargeNo = :chargeNo")
    suspend fun chargeNoCount(chargeNo: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(row: SalesAdditionalChargePaymentEntity): Long

    @Query("SELECT * FROM sales_additional_charge_payments WHERE id = :id LIMIT 1")
    suspend fun paymentById(id: Long): SalesAdditionalChargePaymentEntity?

    @Query("SELECT * FROM sales_additional_charge_payments WHERE paymentNo = :paymentNo LIMIT 1")
    suspend fun paymentByNo(paymentNo: String): SalesAdditionalChargePaymentEntity?

    @Query("SELECT * FROM sales_additional_charge_payments WHERE chargeId = :chargeId ORDER BY paymentDate, id")
    suspend fun paymentsForCharge(chargeId: Long): List<SalesAdditionalChargePaymentEntity>

    @Query("SELECT COUNT(*) FROM sales_additional_charge_payments WHERE paymentNo = :paymentNo")
    suspend fun paymentNoCount(paymentNo: String): Int

    @Query("""
        SELECT COALESCE(SUM(amountOriginal), 0)
        FROM sales_additional_charge_payments
        WHERE chargeId = :chargeId
    """)
    suspend fun paidOriginalForCharge(chargeId: Long): Double

    @Query("""
        SELECT COALESCE(SUM(amountBase), 0)
        FROM sales_additional_charge_payments
        WHERE chargeId = :chargeId
    """)
    suspend fun paidBaseForCharge(chargeId: Long): Double

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSettlement(row: SalesAdditionalChargeSettlementEntity): Long

    @Update
    suspend fun updateSettlement(row: SalesAdditionalChargeSettlementEntity)

    @Query("SELECT * FROM sales_additional_charge_settlements WHERE id = :id LIMIT 1")
    suspend fun settlementById(id: Long): SalesAdditionalChargeSettlementEntity?

    @Query("SELECT * FROM sales_additional_charge_settlements WHERE chargeId=:chargeId AND invoiceId=:invoiceId LIMIT 1")
    suspend fun settlementByLink(chargeId: Long, invoiceId: Long): SalesAdditionalChargeSettlementEntity?

    @Query("SELECT * FROM sales_additional_charge_settlements WHERE invoiceId = :invoiceId ORDER BY id")
    suspend fun settlementsForInvoice(invoiceId: Long): List<SalesAdditionalChargeSettlementEntity>

    @Query("SELECT * FROM sales_additional_charge_settlements WHERE chargeId = :chargeId ORDER BY id")
    suspend fun settlementsForCharge(chargeId: Long): List<SalesAdditionalChargeSettlementEntity>

    @Query("SELECT COUNT(*) FROM sales_additional_charge_settlements WHERE chargeId=:chargeId AND invoiceId=:invoiceId")
    suspend fun linkCount(chargeId: Long, invoiceId: Long): Int

    @Query("SELECT COALESCE(SUM(amountBase),0) FROM sales_additional_charge_settlements WHERE chargeId=:chargeId AND status='ACTIVE'")
    suspend fun settledBaseForCharge(chargeId: Long): Double

    @Query("SELECT COALESCE(SUM(amountChargeOriginal),0) FROM sales_additional_charge_settlements WHERE chargeId=:chargeId AND status='ACTIVE'")
    suspend fun settledOriginalForCharge(chargeId: Long): Double

    @Query("""
        SELECT COALESCE(SUM(s.amountBase), 0)
        FROM sales_additional_charge_settlements s
        JOIN sales_additional_charges c ON c.id=s.chargeId
        WHERE s.invoiceId=:invoiceId AND s.status='ACTIVE'
          AND c.status='POSTED' AND c.bearer='CUSTOMER' AND c.paidBy='CUSTOMER_DIRECT'
    """)
    suspend fun externallySettledBaseForInvoice(invoiceId: Long): Double

    @Query("""
        SELECT COALESCE(SUM(s.amountBase), 0)
        FROM sales_additional_charge_settlements s
        JOIN sales_additional_charges c ON c.id=s.chargeId
        WHERE s.invoiceId=:invoiceId AND s.status='ACTIVE'
          AND s.settlementDate <= :asOf
          AND c.status='POSTED' AND c.bearer='CUSTOMER' AND c.paidBy='CUSTOMER_DIRECT'
    """)
    suspend fun externallySettledBaseForInvoiceAsOf(invoiceId: Long, asOf: Long): Double

    @Query("""
        SELECT c.id AS id, c.chargeNo AS chargeNo, c.chargeDate AS chargeDate,
               c.customerId AS customerId, c.chargeTypeId AS chargeTypeId,
               t.nameAr AS chargeTypeName,
               c.amountOriginal AS amountOriginal, c.currencyCode AS currencyCode,
               c.exchangeRate AS exchangeRate, c.amountBase AS amountBase,
               c.bearer AS bearer, c.paymentStatus AS paymentStatus, c.paidBy AS paidBy,
               c.accountingTreatment AS accountingTreatment,
               COALESCE((SELECT SUM(s.amountBase) FROM sales_additional_charge_settlements s WHERE s.chargeId=c.id AND s.status='ACTIVE'),0) AS settledBase,
               MAX(0, c.amountBase - COALESCE((SELECT SUM(s2.amountBase) FROM sales_additional_charge_settlements s2 WHERE s2.chargeId=c.id AND s2.status='ACTIVE'),0)) AS remainingBase
        FROM sales_additional_charges c
        JOIN sales_additional_charge_types t ON t.id=c.chargeTypeId
        WHERE c.customerId=:customerId AND c.status='POSTED'
          AND c.amountBase - COALESCE((SELECT SUM(s3.amountBase) FROM sales_additional_charge_settlements s3 WHERE s3.chargeId=c.id AND s3.status='ACTIVE'),0) > 0.00000001
        ORDER BY c.chargeDate, c.id
    """)
    suspend fun availableCustomerCharges(customerId: Long): List<AdditionalChargeAvailableRow>

    @Query("""
        SELECT s.id AS settlementId, c.id AS chargeId, c.chargeNo AS chargeNo,
               t.nameAr AS chargeTypeName, c.bearer AS bearer,
               c.accountingTreatment AS accountingTreatment, c.paidBy AS paidBy,
               c.paymentStatus AS paymentStatus, c.currencyCode AS currencyCode,
               c.amountOriginal AS chargeAmountOriginal,
               s.amountChargeOriginal AS allocatedChargeOriginal,
               s.amountInvoiceOriginal AS allocatedInvoiceOriginal,
               s.amountBase AS allocatedBase,
               (SELECT ta.nameAr FROM sales_additional_charge_payments p JOIN treasury_accounts ta ON ta.id=p.treasuryAccountId
                  WHERE p.chargeId=c.id AND p.treasuryAccountId IS NOT NULL ORDER BY p.paymentDate DESC,p.id DESC LIMIT 1) AS paymentAccountName,
               (SELECT p2.paymentDate FROM sales_additional_charge_payments p2 WHERE p2.chargeId=c.id ORDER BY p2.paymentDate DESC,p2.id DESC LIMIT 1) AS lastPaymentDate,
               (SELECT p3.paymentReference FROM sales_additional_charge_payments p3 WHERE p3.chargeId=c.id ORDER BY p3.paymentDate DESC,p3.id DESC LIMIT 1) AS lastPaymentReference
        FROM sales_additional_charge_settlements s
        JOIN sales_additional_charges c ON c.id=s.chargeId
        JOIN sales_additional_charge_types t ON t.id=c.chargeTypeId
        WHERE s.invoiceId=:invoiceId AND s.status='ACTIVE'
        ORDER BY s.id
    """)
    suspend fun invoiceChargeDetails(invoiceId: Long): List<AdditionalChargeInvoiceDetailRow>

    @Query("SELECT * FROM sales_additional_charges WHERE status='POSTED' ORDER BY chargeDate DESC,id DESC")
    fun observeAllCharges(): Flow<List<SalesAdditionalChargeEntity>>

    // v179 cloud hydration/export. These DAO calls deliberately do not invoke accounting services.
    @Query("SELECT * FROM sales_additional_charges ORDER BY chargeDate,id")
    suspend fun allChargesForCloudSync(): List<SalesAdditionalChargeEntity>

    @Query("SELECT * FROM sales_additional_charge_payments ORDER BY paymentDate,id")
    suspend fun allPaymentsForCloudSync(): List<SalesAdditionalChargePaymentEntity>

    @Query("SELECT * FROM sales_additional_charge_settlements ORDER BY settlementDate,id")
    suspend fun allSettlementsForCloudSync(): List<SalesAdditionalChargeSettlementEntity>

    @Update
    suspend fun updatePayment(row: SalesAdditionalChargePaymentEntity)
}
