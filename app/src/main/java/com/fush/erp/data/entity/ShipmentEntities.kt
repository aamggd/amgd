package com.fush.erp.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sales_shipments",
    indices = [
        Index(value = ["shipmentNo"], unique = true),
        Index("shipmentDate"), Index("fromWarehouseId"), Index("destinationProvince"), Index("destinationGovernorateId"), Index("destinationDistrictId"), Index("destinationAreaId"), Index("status")
    ],
    foreignKeys = [
        ForeignKey(entity = WarehouseEntity::class, parentColumns = ["id"], childColumns = ["fromWarehouseId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = GovernorateEntity::class, parentColumns = ["id"], childColumns = ["destinationGovernorateId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = DistrictEntity::class, parentColumns = ["id"], childColumns = ["destinationDistrictId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AreaEntity::class, parentColumns = ["id"], childColumns = ["destinationAreaId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class SalesShipmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipmentNo: String,
    val shipmentDate: Long,
    val fromWarehouseId: Long,
    val destinationProvince: String,
    @ColumnInfo(defaultValue = "NULL") val destinationGovernorateId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val destinationDistrictId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val destinationAreaId: String? = null,
    val status: String = "DRAFT", // DRAFT, IN_TRANSIT, DELIVERED, CLOSED, CANCELLED
    val transportReference: String = "",
    val notes: String = "",
    val createdBy: Long,
    val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now(),
    val closedAt: Long? = null,
    val cancelledAt: Long? = null,
    val cancellationReason: String = ""
)

@Entity(
    tableName = "sales_shipment_items",
    indices = [Index("shipmentId"), Index("itemId"), Index("lotNo"), Index(value=["shipmentId","itemId","lotNo"], unique = true)],
    foreignKeys = [
        ForeignKey(entity = SalesShipmentEntity::class, parentColumns = ["id"], childColumns = ["shipmentId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class SalesShipmentItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipmentId: Long,
    val itemId: Long,
    val lotNo: String = "",
    val quantityBase: Double,
    val createdBy: Long,
    val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now()
)

@Entity(
    tableName = "sales_shipment_expenses",
    indices = [
        Index("shipmentId"), Index("expenseType"), Index("expenseDate"), Index("currencyCode"),
        Index(value=["partyVoucherId"], unique = true), Index("status")
    ],
    foreignKeys = [
        ForeignKey(entity = SalesShipmentEntity::class, parentColumns = ["id"], childColumns = ["shipmentId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = CurrencyEntity::class, parentColumns = ["code"], childColumns = ["currencyCode"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = PartyVoucherEntity::class, parentColumns = ["id"], childColumns = ["partyVoucherId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class SalesShipmentExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipmentId: Long,
    val expenseType: String, // TRANSPORT, LOADING, CUSTOMS, OTHER
    val description: String = "",
    val expenseDate: Long,
    val amountOriginal: Double,
    val currencyCode: String,
    val exchangeRate: Double,
    val amountBase: Double,
    /** COMPANY or CUSTOMER. CUSTOMER means the allocated actual cost is recoverable on the sales invoice. */
    val bearer: String = "COMPANY",
    val paymentMethod: String, // CASH / BANK snapshot for reporting
    val partyVoucherId: Long,
    val paymentVoucherNo: String,
    val paymentReference: String = "",
    val status: String = "POSTED",
    val createdBy: Long,
    val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now()
)

/** Quantity trace from shipment item/lot to sales invoice. No GL effect. */
@Entity(
    tableName = "sales_shipment_invoice_item_allocations",
    indices = [
        Index("shipmentItemId"), Index("invoiceId"), Index("salesLineId"),
        Index(value=["shipmentItemId","salesLineId"], unique = true), Index("status")
    ],
    foreignKeys = [
        ForeignKey(entity = SalesShipmentItemEntity::class, parentColumns = ["id"], childColumns = ["shipmentItemId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SalesInvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class SalesShipmentInvoiceItemAllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipmentItemId: Long,
    val invoiceId: Long,
    /** v185: line-level shipment trace. Null is retained only for allocations migrated from v179-v184. */
    val salesLineId: Long? = null,
    val quantityBase: Double,
    val status: String = "ACTIVE",
    val createdBy: Long,
    val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now(),
    val reversedAt: Long? = null,
    val reversalReason: String = ""
)

/** Actual shipment-cost allocation to invoice. Analytical only: never posts a second journal. */
@Entity(
    tableName = "sales_shipment_expense_invoice_allocations",
    indices = [
        Index("shipmentExpenseId"), Index("invoiceId"),
        Index(value=["shipmentExpenseId","invoiceId"], unique = true), Index("status")
    ],
    foreignKeys = [
        ForeignKey(entity = SalesShipmentExpenseEntity::class, parentColumns = ["id"], childColumns = ["shipmentExpenseId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SalesInvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class SalesShipmentExpenseInvoiceAllocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipmentExpenseId: Long,
    val invoiceId: Long,
    val amountBase: Double,
    /** Customer-facing amount generated from this exact actual-cost allocation. Zero when company bears it. */
    val customerChargeBase: Double = 0.0,
    val allocationMethod: String = "MANUAL", // MANUAL / QUANTITY
    val basisQuantityBase: Double = 0.0,
    val status: String = "ACTIVE",
    val createdBy: Long,
    val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now(),
    val reversedAt: Long? = null,
    val reversalReason: String = ""
)

data class ShipmentSummaryRow(
    val id: Long,
    val shipmentNo: String,
    val shipmentDate: Long,
    val warehouseName: String,
    val destinationProvince: String,
    val status: String,
    val transportReference: String,
    val itemLineCount: Int,
    val totalExpenseBase: Double,
    val allocatedExpenseBase: Double,
    val remainingExpenseBase: Double
)

data class ShipmentInvoiceCostRow(
    val shipmentId: Long,
    val shipmentNo: String,
    val shipmentDate: Long,
    val destinationProvince: String,
    val transportReference: String,
    val expenseType: String,
    val expenseId: Long,
    val expenseAmountBase: Double,
    val allocatedBase: Double,
    val bearer: String,
    val customerChargeBase: Double,
    val paymentVoucherNo: String,
    val paymentReference: String
)

data class ShipmentItemAllocationRow(
    val shipmentItemId: Long,
    val itemId: Long,
    val itemCode: String,
    val itemName: String,
    val lotNo: String,
    val shippedQtyBase: Double,
    val allocatedQtyBase: Double,
    val remainingQtyBase: Double
)


data class ShipmentInvoiceLinkRow(
    val invoiceId: Long,
    val invoiceNo: String,
    val invoiceDate: Long,
    val customerName: String,
    val allocatedQuantityBase: Double,
    val allocatedCostBase: Double
)

/** Available shipment for a sales line. Remaining quantity is derived only from ACTIVE allocations. */
data class ShipmentSaleOptionRow(
    val shipmentId: Long,
    val shipmentNo: String,
    val shipmentDate: Long,
    val destinationProvince: String,
    val itemId: Long,
    val remainingQtyBase: Double
)

/** Line-level trace shown inside sales invoice details. */
data class SalesLineShipmentAllocationRow(
    val salesLineId: Long,
    val shipmentId: Long,
    val shipmentNo: String,
    val destinationProvince: String,
    val quantityBase: Double
)
