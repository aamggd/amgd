package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expense_workflow_requests",
    indices = [
        Index(value = ["requestNo"], unique = true),
        Index("treasuryAccountId"),
        Index("expenseAccountId"),
        Index("approvalStatus"),
        Index("paymentStatus"),
        Index("expenseDate"),
        Index("createdBy")
    ],
    foreignKeys = [
        ForeignKey(
            entity = TreasuryAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["treasuryAccountId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseAccountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class ExpenseWorkflowRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestNo: String,
    val treasuryAccountId: Long,
    val expenseAccountId: Long,
    val amountOriginal: Double,
    val currencyCode: String,
    val exchangeRate: Double,
    val description: String,
    val referenceNo: String = "",
    val expenseDate: Long,
    val employeeId: Long? = null,
    val salesRepId: Long? = null,
    val costCenterCode: String = "",
    val organizationUnit: String = "",
    val referenceType: String = "NONE",
    val referenceId: Long? = null,
    val referenceLabel: String = "",
    val customerId: Long? = null,
    val supplierId: Long? = null,
    val itemId: Long? = null,
    val attachmentFileName: String = "",
    val attachmentMimeType: String = "",
    val attachmentUri: String = "",
    val attachmentNotes: String = "",
    val approvalStatus: String = "DRAFT",
    val paymentStatus: String = "UNPAID",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val submittedBy: Long? = null,
    val submittedAt: Long? = null,
    val approvedBy: Long? = null,
    val approvedAt: Long? = null,
    val rejectedBy: Long? = null,
    val rejectedAt: Long? = null,
    val rejectionReason: String = "",
    val paidBy: Long? = null,
    val paidAt: Long? = null,
    val journalEntryId: Long? = null,
    val partyVoucherId: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
