package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expense_dimensions",
    indices = [
        Index(value = ["partyVoucherId"], unique = true),
        Index("employeeId"), Index("salesRepId"), Index("customerId"), Index("supplierId"), Index("itemId"),
        Index("costCenterCode"), Index("organizationUnit"), Index("referenceType"), Index("referenceId"), Index("createdAt")
    ],
    foreignKeys = [
        ForeignKey(entity = PartyVoucherEntity::class, parentColumns = ["id"], childColumns = ["partyVoucherId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EmployeeEntity::class, parentColumns = ["id"], childColumns = ["employeeId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = SalesRepresentativeEntity::class, parentColumns = ["id"], childColumns = ["salesRepId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.SET_NULL)
    ]
)
data class ExpenseDimensionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partyVoucherId: Long,
    val employeeId: Long? = null,
    val employeeNameSnapshot: String = "",
    val salesRepId: Long? = null,
    val salesRepNameSnapshot: String = "",
    val costCenterCode: String = "OTHER",
    val costCenterNameSnapshot: String = "أخرى",
    val organizationUnit: String = "",
    val referenceType: String = "NONE",
    val referenceId: Long? = null,
    val referenceNo: String = "",
    val referenceLabelSnapshot: String = "",
    val customerId: Long? = null,
    val customerNameSnapshot: String = "",
    val supplierId: Long? = null,
    val supplierNameSnapshot: String = "",
    val itemId: Long? = null,
    val itemNameSnapshot: String = "",
    val paymentMethodSnapshot: String = "",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "expense_attachments",
    indices = [Index("expenseId"), Index("createdAt")],
    foreignKeys = [
        ForeignKey(entity = ExpenseDimensionEntity::class, parentColumns = ["id"], childColumns = ["expenseId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class ExpenseAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val fileName: String,
    val mimeType: String = "",
    val uri: String,
    val notes: String = "",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)

data class ExpenseReportRow(
    val expenseId: Long,
    val voucherId: Long,
    val voucherNo: String,
    val voucherDate: Long,
    val expenseAccountId: Long,
    val expenseAccountCode: String,
    val expenseAccountName: String,
    val amountBase: Double,
    val description: String,
    val currencyCode: String,
    val amountOriginal: Double,
    val paymentMethod: String,
    val employeeId: Long?,
    val employeeName: String,
    val salesRepId: Long?,
    val salesRepName: String,
    val costCenterCode: String,
    val costCenterName: String,
    val organizationUnit: String,
    val referenceType: String,
    val referenceId: Long?,
    val referenceNo: String,
    val referenceLabel: String,
    val customerId: Long?,
    val customerName: String,
    val supplierId: Long?,
    val supplierName: String,
    val itemId: Long?,
    val itemName: String,
    val attachmentCount: Int
)

data class SalesRepContributionRow(
    val salesRepId: Long,
    val salesRepName: String,
    val grossSalesBase: Double,
    val returnsBase: Double,
    val netSalesBase: Double,
    val grossCogsBase: Double,
    val returnCostBase: Double,
    val netCogsBase: Double,
    val directExpensesBase: Double,
    val netContributionBase: Double
)
