package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "suppliers",
    indices = [Index(value = ["code"], unique = true), Index("currencyCode")],
    foreignKeys = [ForeignKey(
        entity = CurrencyEntity::class,
        parentColumns = ["code"],
        childColumns = ["currencyCode"],
        onDelete = ForeignKey.RESTRICT
    )]
)
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val nameAr: String,
    val nameEn: String = "",
    val phone: String = "",
    val address: String = "",
    val currencyCode: String = "YER_NEW",
    val paymentTermsDays: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "item_unit_conversions",
    indices = [
        Index(value = ["itemId", "unitId"], unique = true),
        Index("itemId"),
        Index("unitId")
    ],
    foreignKeys = [
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = UnitEntity::class, parentColumns = ["id"], childColumns = ["unitId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class ItemUnitConversionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val unitId: Long,
    val factorToBase: Double,
    val allowPurchase: Boolean = true,
    val allowSale: Boolean = false,
    val barcode: String? = null,
    val isActive: Boolean = true
)

@Entity(
    tableName = "purchase_invoices",
    indices = [Index(value = ["invoiceNo"], unique = true), Index("supplierId"), Index("warehouseId")],
    foreignKeys = [
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = WarehouseEntity::class, parentColumns = ["id"], childColumns = ["warehouseId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class PurchaseInvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNo: String,
    val supplierInvoiceNo: String = "",
    val supplierId: Long,
    val invoiceDate: Long,
    val dueDate: Long? = null,
    val warehouseId: Long,
    val currencyCode: String,
    val exchangeRate: Double,
    val paymentType: String,
    val subtotalOriginal: Double,
    val totalOriginal: Double,
    val totalBase: Double,
    val status: String = "POSTED",
    val notes: String = "",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "purchase_lines",
    indices = [Index("invoiceId"), Index("itemId"), Index("unitId")],
    foreignKeys = [
        ForeignKey(entity = PurchaseInvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = UnitEntity::class, parentColumns = ["id"], childColumns = ["unitId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class PurchaseLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val itemId: Long,
    val unitId: Long,
    val quantity: Double,
    val factorToBase: Double,
    val baseQuantity: Double,
    val unitPriceOriginal: Double,
    val lineTotalOriginal: Double,
    val unitCostBase: Double,
    val lotNo: String? = null,
    val expiryDate: Long? = null
)

@Entity(
    tableName = "purchase_returns",
    indices = [Index(value = ["returnNo"], unique = true), Index("purchaseInvoiceId"), Index("supplierId")],
    foreignKeys = [
        ForeignKey(entity = PurchaseInvoiceEntity::class, parentColumns = ["id"], childColumns = ["purchaseInvoiceId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = WarehouseEntity::class, parentColumns = ["id"], childColumns = ["warehouseId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class PurchaseReturnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val returnNo: String,
    val purchaseInvoiceId: Long,
    val supplierId: Long,
    val returnDate: Long,
    val warehouseId: Long,
    val currencyCode: String,
    val exchangeRate: Double,
    val settlementType: String,
    val totalOriginal: Double,
    val totalBase: Double,
    val status: String = "POSTED",
    val reason: String = "",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "purchase_return_lines",
    indices = [Index("returnId"), Index("purchaseLineId"), Index("itemId")],
    foreignKeys = [
        ForeignKey(entity = PurchaseReturnEntity::class, parentColumns = ["id"], childColumns = ["returnId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PurchaseLineEntity::class, parentColumns = ["id"], childColumns = ["purchaseLineId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = UnitEntity::class, parentColumns = ["id"], childColumns = ["unitId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class PurchaseReturnLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val returnId: Long,
    val purchaseLineId: Long,
    val itemId: Long,
    val unitId: Long,
    val quantity: Double,
    val factorToBase: Double,
    val baseQuantity: Double,
    val unitPriceOriginal: Double,
    val lineTotalOriginal: Double,
    val unitCostBase: Double
)

@Entity(
    tableName = "supplier_payments",
    indices = [
        Index(value = ["paymentNo"], unique = true),
        Index("supplierId"),
        Index("treasuryAccountId"),
        Index("paymentDate"),
        Index(value = ["reversalOfPaymentId"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TreasuryAccountEntity::class, parentColumns = ["id"], childColumns = ["treasuryAccountId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class SupplierPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentNo: String,
    val supplierId: Long,
    val treasuryAccountId: Long,
    val paymentDate: Long,
    val currencyCode: String,
    val exchangeRate: Double,
    val amountOriginal: Double,
    val cashAmountBase: Double,
    val notes: String = "",
    val reversalOfPaymentId: Long? = null,
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "supplier_payment_allocations",
    indices = [Index("paymentId"), Index("invoiceId")],
    foreignKeys = [
        ForeignKey(entity = SupplierPaymentEntity::class, parentColumns = ["id"], childColumns = ["paymentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PurchaseInvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class SupplierPaymentAllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentId: Long,
    val invoiceId: Long,
    val amountOriginal: Double,
    val allocatedBase: Double
)

@Entity(
    tableName = "stock_movements",
    indices = [Index("warehouseId"), Index("itemId"), Index(value = ["referenceType", "referenceId"])],
    foreignKeys = [
        ForeignKey(entity = WarehouseEntity::class, parentColumns = ["id"], childColumns = ["warehouseId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val movementDate: Long,
    val warehouseId: Long,
    val itemId: Long,
    val movementType: String,
    val quantityBase: Double,
    val unitCostBase: Double,
    val referenceType: String,
    val referenceId: Long,
    val lotNo: String? = null,
    val expiryDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class StockBalanceRow(
    val itemId: Long,
    val code: String,
    val nameAr: String,
    val quantityBase: Double,
    val baseUnitName: String
)

data class WarehouseStockBalanceRow(
    val warehouseId: Long,
    val warehouseCode: String,
    val warehouseName: String,
    val itemId: Long,
    val code: String,
    val nameAr: String,
    val category: String,
    val quantityBase: Double,
    val baseUnitName: String
)

data class PurchaseInvoiceSummary(
    val id: Long,
    val invoiceNo: String,
    val invoiceDate: Long,
    val supplierName: String,
    val currencyCode: String,
    val totalOriginal: Double,
    val totalBase: Double,
    val paymentType: String
)

data class LastPurchasePriceRow(
    val unitPriceOriginal: Double,
    val currencyCode: String,
    val invoiceDate: Long,
    val invoiceNo: String,
    val supplierName: String
)


data class SupplierBalanceRow(
    val supplierId: Long,
    val supplierName: String,
    val currencyCode: String,
    val totalDueBase: Double,
    val paidBase: Double,
    val outstandingBase: Double,
    val overdueBase: Double
)

data class SupplierInvoicePayableRow(
    val invoiceId: Long,
    val invoiceNo: String,
    val invoiceDate: Long,
    val dueDate: Long?,
    val currencyCode: String,
    val invoiceExchangeRate: Double,
    val totalBase: Double,
    val supplierCreditReturnsBase: Double,
    val paidBase: Double,
    val outstandingBase: Double
)

data class SupplierPaymentDetailRow(
    val paymentId: Long,
    val paymentNo: String,
    val paymentDate: Long,
    val currencyCode: String,
    val amountOriginal: Double,
    val cashAmountBase: Double,
    val allocatedBase: Double,
    val invoiceNo: String,
    val treasuryName: String,
    val reversalOfPaymentId: Long?
)

data class SupplierLedgerEventRow(
    val eventDate: Long,
    val eventOrder: Int,
    val eventType: String,
    val referenceNo: String,
    val debitBase: Double,
    val creditBase: Double,
    val notes: String
)

data class SupplierAgingRow(
    val supplierId: Long,
    val supplierName: String,
    val currentBase: Double,
    val days1To30Base: Double,
    val days31To60Base: Double,
    val days61To90Base: Double,
    val over90Base: Double,
    val totalOutstandingBase: Double
)
