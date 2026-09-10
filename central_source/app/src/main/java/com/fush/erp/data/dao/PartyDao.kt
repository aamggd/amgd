package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.PartyAttachmentEntity
import com.fush.erp.data.entity.PartyVoucherEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartyDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVoucher(row: PartyVoucherEntity): Long

    @Update
    suspend fun updateVoucher(row: PartyVoucherEntity)

    @Query("SELECT * FROM party_vouchers WHERE journalEntryId=:entryId LIMIT 1")
    suspend fun voucherByJournalEntryId(entryId: Long): PartyVoucherEntity?

    @Query("SELECT * FROM party_vouchers WHERE customerId=:customerId ORDER BY voucherDate DESC, id DESC")
    fun observeCustomerVouchers(customerId: Long): Flow<List<PartyVoucherEntity>>

    @Query("SELECT * FROM party_vouchers WHERE supplierId=:supplierId ORDER BY voucherDate DESC, id DESC")
    fun observeSupplierVouchers(supplierId: Long): Flow<List<PartyVoucherEntity>>

    @Query("SELECT * FROM party_vouchers WHERE customerId=:customerId ORDER BY voucherDate, id")
    suspend fun customerVouchers(customerId: Long): List<PartyVoucherEntity>

    @Query("SELECT * FROM party_vouchers WHERE supplierId=:supplierId ORDER BY voucherDate, id")
    suspend fun supplierVouchers(supplierId: Long): List<PartyVoucherEntity>

    @Query("SELECT * FROM party_vouchers WHERE employeeId=:employeeId ORDER BY voucherDate DESC, id DESC")
    fun observeEmployeeVouchers(employeeId: Long): Flow<List<PartyVoucherEntity>>

    @Query("SELECT * FROM party_vouchers WHERE salesRepId=:salesRepId ORDER BY voucherDate DESC, id DESC")
    fun observeSalesRepVouchers(salesRepId: Long): Flow<List<PartyVoucherEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAttachment(row: PartyAttachmentEntity): Long

    @Query("SELECT * FROM party_attachments WHERE customerId=:customerId ORDER BY createdAt DESC, id DESC")
    fun observeCustomerAttachments(customerId: Long): Flow<List<PartyAttachmentEntity>>

    @Query("SELECT * FROM party_attachments WHERE supplierId=:supplierId ORDER BY createdAt DESC, id DESC")
    fun observeSupplierAttachments(supplierId: Long): Flow<List<PartyAttachmentEntity>>
}
