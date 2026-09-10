package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "party_vouchers",
    indices = [
        Index(value = ["voucherNo"], unique = true),
        Index(value = ["journalEntryId"], unique = true),
        Index("customerId"), Index("supplierId"), Index("employeeId"), Index("salesRepId"),
        Index("voucherDate"), Index("status")
    ],
    foreignKeys = [
        ForeignKey(entity = TreasuryAccountEntity::class, parentColumns = ["id"], childColumns = ["treasuryAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["offsetAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SalesRepresentativeEntity::class, parentColumns = ["id"], childColumns = ["salesRepId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["journalEntryId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class PartyVoucherEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val voucherNo: String,
    val voucherType: String,
    val treasuryAccountId: Long,
    val offsetAccountId: Long,
    val customerId: Long? = null,
    val supplierId: Long? = null,
    val employeeId: Long? = null,
    val salesRepId: Long? = null,
    val partyType: String = "NONE",
    val partyNameSnapshot: String = "",
    val voucherDate: Long,
    val currencyCode: String,
    val exchangeRate: Double,
    val amountOriginal: Double,
    val amountBase: Double,
    val description: String,
    val referenceNo: String = "",
    val journalEntryId: Long,
    val status: String = "POSTED",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val reversalReason: String = "",
    val reversedBy: Long? = null,
    val reversedAt: Long? = null,
    val reversalJournalEntryId: Long? = null
)

@Entity(
    tableName = "party_attachments",
    indices = [Index("customerId"), Index("supplierId"), Index("createdAt")],
    foreignKeys = [
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class PartyAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long? = null,
    val supplierId: Long? = null,
    val fileName: String,
    val mimeType: String = "",
    val uri: String,
    val notes: String = "",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)
