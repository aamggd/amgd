package com.fush.erp.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["code"], unique = true),
        Index("currencyCode"),
        Index("province"),
        Index("governorateId"),
        Index("districtId"),
        Index("areaId"),
        Index("classification"),
        Index("salesRepId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["code"],
            childColumns = ["currencyCode"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = SalesRepresentativeEntity::class,
            parentColumns = ["id"],
            childColumns = ["salesRepId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(entity = GovernorateEntity::class, parentColumns = ["id"], childColumns = ["governorateId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = DistrictEntity::class, parentColumns = ["id"], childColumns = ["districtId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AreaEntity::class, parentColumns = ["id"], childColumns = ["areaId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val nameAr: String,
    val nameEn: String = "",
    val phone: String = "",
    val address: String = "",
    val province: String = "",
    @ColumnInfo(defaultValue = "NULL") val governorateId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val districtId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val areaId: String? = null,
    val channel: String = "RETAIL",
    val classification: String = "C",
    val currencyCode: String = "YER_NEW",
    val creditLimitBase: Double = 0.0,
    val creditDays: Int = 0,
    val allowCredit: Boolean = false,
    val salesRepName: String = "",
    val salesRepId: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now()
)

@Entity(
    tableName = "sales_prices",
    indices = [
        Index("itemId"),
        Index("currencyCode"),
        Index("effectiveTo"),
        Index(value = ["itemId", "channel", "province", "currencyCode", "effectiveFrom"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CurrencyEntity::class, parentColumns = ["code"], childColumns = ["currencyCode"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class SalesPriceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val channel: String,
    val province: String,
    val currencyCode: String,
    val baseUnitPriceOriginal: Double,
    val effectiveFrom: Long,
    val effectiveTo: Long? = null,
    val isActive: Boolean = true,
    val note: String = ""
)

@Entity(
    tableName = "sales_invoices",
    indices = [
        Index(value = ["invoiceNo"], unique = true),
        Index("customerId"),
        Index("warehouseId"),
        Index("invoiceDate"),
        Index("dueDate"),
        Index("governorateId"),
        Index("districtId"),
        Index("areaId"),
        Index("salesRepId")
    ],
    foreignKeys = [
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = WarehouseEntity::class, parentColumns = ["id"], childColumns = ["warehouseId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SalesRepresentativeEntity::class, parentColumns = ["id"], childColumns = ["salesRepId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = GovernorateEntity::class, parentColumns = ["id"], childColumns = ["governorateId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = DistrictEntity::class, parentColumns = ["id"], childColumns = ["districtId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AreaEntity::class, parentColumns = ["id"], childColumns = ["areaId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class SalesInvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNo: String,
    val customerId: Long,
    val invoiceDate: Long,
    val dueDate: Long? = null,
    val warehouseId: Long,
    val currencyCode: String,
    val exchangeRate: Double,
    val paymentType: String,
    val channel: String,
    val province: String,
    @ColumnInfo(defaultValue = "NULL") val governorateId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val districtId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val areaId: String? = null,
    val salesRepId: Long? = null,
    val salesRepNameSnapshot: String = "",
    val salesRepRatePct: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val freeQtyLimitPctSnapshot: Double = 0.0,
    val freeQtyApprovedBy: Long? = null,
    @ColumnInfo(defaultValue = "''") val freeQtyApprovalReason: String = "",
    val discountPct: Double,
    val grossOriginal: Double,
    val discountOriginal: Double,
    val transportOriginal: Double,
    val feesOriginal: Double,
    val riskMarginOriginal: Double,
    val totalOriginal: Double,
    val totalBase: Double,
    @ColumnInfo(defaultValue = "NULL") val treasuryAccountId: Long? = null,
    val status: String = "POSTED",
    val belowFloorApprovedBy: Long? = null,
    val belowFloorReason: String = "",
    val notes: String = "",
    val createdBy: Long,
    val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now()
)

@Entity(
    tableName = "sales_lines",
    indices = [Index("invoiceId"), Index("itemId"), Index("unitId")],
    foreignKeys = [
        ForeignKey(entity = SalesInvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = UnitEntity::class, parentColumns = ["id"], childColumns = ["unitId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class SalesLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val itemId: Long,
    val unitId: Long,
    val quantity: Double,
    @ColumnInfo(defaultValue = "0.0") val freeQuantity: Double = 0.0,
    val factorToBase: Double,
    val baseQuantity: Double,
    @ColumnInfo(defaultValue = "0.0") val freeBaseQuantity: Double = 0.0,
    val unitPriceOriginal: Double,
    val grossOriginal: Double,
    val discountOriginal: Double,
    val netOriginal: Double
)

@Entity(
    tableName = "sales_allocations",
    indices = [Index("salesLineId"), Index("itemId"), Index("lotNo")],
    foreignKeys = [
        ForeignKey(entity = SalesLineEntity::class, parentColumns = ["id"], childColumns = ["salesLineId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class SalesAllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val salesLineId: Long,
    val itemId: Long,
    val lotNo: String?,
    val expiryDate: Long?,
    val quantityBase: Double,
    @ColumnInfo(defaultValue = "0.0") val freeQuantityBase: Double = 0.0,
    val unitCostBase: Double,
    val costBase: Double
)

@Entity(
    tableName = "customer_receipts",
    indices = [
        Index(value = ["receiptNo"], unique = true),
        Index("customerId"),
        Index("receiptDate"),
        Index(value = ["reversalOfReceiptId"], unique = true)
    ],
    foreignKeys = [ForeignKey(
        entity = CustomerEntity::class,
        parentColumns = ["id"],
        childColumns = ["customerId"],
        onDelete = ForeignKey.RESTRICT
    )]
)
data class CustomerReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptNo: String,
    val customerId: Long,
    val receiptDate: Long,
    val currencyCode: String,
    val exchangeRate: Double,
    val amountOriginal: Double,
    val amountBase: Double,
    @ColumnInfo(defaultValue = "0.0") val discountOriginal: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val discountBase: Double = 0.0,
    @ColumnInfo(defaultValue = "''") val discountReason: String = "",
    val notes: String = "",
    @ColumnInfo(defaultValue = "NULL") val treasuryAccountId: Long? = null,
    val reversalOfReceiptId: Long? = null,
    val createdBy: Long,
    val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now()
)

@Entity(
    tableName = "customer_receipt_allocations",
    indices = [Index("receiptId"), Index("invoiceId")],
    foreignKeys = [
        ForeignKey(entity = CustomerReceiptEntity::class, parentColumns = ["id"], childColumns = ["receiptId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SalesInvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class CustomerReceiptAllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val invoiceId: Long,
    val amountBase: Double,
    @ColumnInfo(defaultValue = "0.0") val discountOriginal: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val discountBase: Double = 0.0
)

@Entity(
    tableName = "sales_commissions",
    indices = [
        Index("invoiceId"),
        Index("salesRepId"),
        Index(value = ["receiptAllocationId"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(entity = SalesInvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CustomerReceiptAllocationEntity::class, parentColumns = ["id"], childColumns = ["receiptAllocationId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SalesRepresentativeEntity::class, parentColumns = ["id"], childColumns = ["salesRepId"], onDelete = ForeignKey.SET_NULL)
    ]
)
data class SalesCommissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val receiptAllocationId: Long,
    val salesRepId: Long? = null,
    val beneficiary: String,
    val ratePct: Double = 0.0,
    val earnedBase: Double,
    val reversedBase: Double = 0.0,
    val status: String = "EARNED",
    val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now()
)

@Entity(
    tableName = "sales_returns",
    indices = [Index(value = ["returnNo"], unique = true), Index("salesInvoiceId"), Index("customerId"), Index("warehouseId")],
    foreignKeys = [
        ForeignKey(entity = SalesInvoiceEntity::class, parentColumns = ["id"], childColumns = ["salesInvoiceId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = WarehouseEntity::class, parentColumns = ["id"], childColumns = ["warehouseId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class SalesReturnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val returnNo: String,
    val salesInvoiceId: Long,
    val customerId: Long,
    val returnDate: Long,
    val warehouseId: Long,
    val currencyCode: String,
    val exchangeRate: Double,
    val settlementType: String,
    val totalOriginal: Double,
    val totalBase: Double,
    val totalCostBase: Double,
    @ColumnInfo(defaultValue = "NULL") val treasuryAccountId: Long? = null,
    val reason: String,
    val status: String = "POSTED",
    val createdBy: Long,
    val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now()
)

@Entity(
    tableName = "sales_return_lines",
    indices = [Index("returnId"), Index("salesLineId"), Index("itemId"), Index("unitId")],
    foreignKeys = [
        ForeignKey(entity = SalesReturnEntity::class, parentColumns = ["id"], childColumns = ["returnId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SalesLineEntity::class, parentColumns = ["id"], childColumns = ["salesLineId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = UnitEntity::class, parentColumns = ["id"], childColumns = ["unitId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class SalesReturnLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val returnId: Long,
    val salesLineId: Long,
    val itemId: Long,
    val unitId: Long,
    val quantity: Double,
    @ColumnInfo(defaultValue = "0.0") val freeQuantity: Double = 0.0,
    val factorToBase: Double,
    val baseQuantity: Double,
    @ColumnInfo(defaultValue = "0.0") val freeBaseQuantity: Double = 0.0,
    val unitPriceOriginal: Double,
    val lineNetOriginal: Double,
    val costBase: Double
)

@Entity(
    tableName = "sales_return_allocations",
    indices = [Index("returnLineId"), Index("salesAllocationId"), Index("itemId")],
    foreignKeys = [
        ForeignKey(entity = SalesReturnLineEntity::class, parentColumns = ["id"], childColumns = ["returnLineId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SalesAllocationEntity::class, parentColumns = ["id"], childColumns = ["salesAllocationId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class SalesReturnAllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val returnLineId: Long,
    val salesAllocationId: Long,
    val itemId: Long,
    val lotNo: String?,
    val expiryDate: Long?,
    val quantityBase: Double,
    @ColumnInfo(defaultValue = "0.0") val freeQuantityBase: Double = 0.0,
    val unitCostBase: Double,
    val costBase: Double
)

data class SalesInvoiceSummary(
    val id: Long,
    val invoiceNo: String,
    val invoiceDate: Long,
    val customerName: String,
    val paymentType: String,
    val currencyCode: String,
    val totalOriginal: Double,
    val totalBase: Double,
    val outstandingBase: Double,
    val dueDate: Long?
)

data class CustomerReceivableRow(
    val customerId: Long,
    val customerName: String,
    val province: String,
    val classification: String,
    val creditLimitBase: Double,
    val totalDueBase: Double,
    val paidBase: Double,
    val outstandingBase: Double,
    val overdueBase: Double
)


data class CustomerLedgerEventRow(
    val eventDate: Long,
    val eventOrder: Int,
    val eventType: String,
    val referenceNo: String,
    val invoiceNo: String,
    val currencyCode: String,
    val amountOriginal: Double,
    val debitBase: Double,
    val creditBase: Double,
    val notes: String
)
