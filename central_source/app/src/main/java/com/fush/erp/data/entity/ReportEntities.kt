package com.fush.erp.data.entity

data class ExecutiveReportRow(
    val grossSalesBase: Double,
    val salesReturnsBase: Double,
    val collectionsBase: Double,
    val grossPurchasesBase: Double,
    val purchaseReturnsBase: Double,
    val inventoryValueBase: Double,
    val receivablesBase: Double,
    val overdueBase: Double,
    val productionOrders: Int,
    val acceptedQtyBase: Double,
    val accepted60QtyBase: Double,
    val accepted200QtyBase: Double,
    val scrapQtyBase: Double,
    val openNonConformances: Int,
    val maintenanceCostBase: Double
)

data class CollectionDetailRow(
    val eventDate: Long,
    val entryType: String,
    val referenceNo: String,
    val invoiceNo: String,
    val customerName: String,
    val province: String,
    val currencyCode: String,
    val amountOriginal: Double,
    val amountBase: Double,
    val notes: String
)

data class CustomerSalesReportRow(
    val customerId: Long,
    val customerName: String,
    val province: String,
    val invoiceCount: Int,
    val grossSalesBase: Double,
    val returnsBase: Double,
    val collectionsBase: Double,
    val outstandingBase: Double
)

data class ProductSalesQuantityReportRow(
    val itemId: Long,
    val code: String,
    val productName: String,
    val grossQtyBase: Double,
    val returnedQtyBase: Double,
    val netQtyBase: Double
)

data class SupplierPurchaseReportRow(
    val supplierId: Long,
    val supplierName: String,
    val invoiceCount: Int,
    val grossPurchasesBase: Double,
    val returnsBase: Double,
    val netPurchasesBase: Double
)

data class InventoryValuationReportRow(
    val itemId: Long,
    val code: String,
    val itemName: String,
    val baseUnitName: String,
    val quantityBase: Double,
    val inventoryValueBase: Double,
    val reorderLevel: Double
)

data class ProductionPerformanceReportRow(
    val orderId: Long,
    val orderNo: String,
    val plannedDate: Long,
    val manufactureDate: Long?,
    val productCode: String,
    val productName: String,
    val status: String,
    val batchNo: String?,
    val plannedQtyBase: Double,
    val actualQtyBase: Double,
    val acceptedQtyBase: Double,
    val rejectedQtyBase: Double,
    val scrapQtyBase: Double,
    val materialCostBase: Double,
    val laborCostBase: Double,
    val actualCostBase: Double
)

data class ProductionMaterialUsageReportRow(
    val itemId: Long,
    val code: String,
    val itemName: String,
    val unitName: String,
    val issuedQtyBase: Double,
    val totalCostBase: Double,
    val averageUnitCostBase: Double,
    val orderCount: Int
)

data class QualityReportRow(
    val batchId: Long,
    val batchNo: String,
    val manufactureDate: Long,
    val batchStatus: String,
    val passChecks: Int,
    val failChecks: Int,
    val openNonConformances: Int,
    val acceptedQtyBase: Double,
    val rejectedQtyBase: Double,
    val scrapQtyBase: Double
)

data class CommissionReportRow(
    val beneficiary: String,
    val earnedBase: Double,
    val reversedBase: Double,
    val netCommissionBase: Double
)

data class MaintenanceReportRow(
    val workOrderCount: Int,
    val closedCount: Int,
    val openCount: Int,
    val downtimeMinutes: Int,
    val costBase: Double
)

data class PartyAgingInvoiceRow(
    val partyId: Long,
    val partyName: String,
    val dueDate: Long?,
    val outstandingBase: Double
)

data class PartyAgingAdjustmentRow(
    val partyId: Long,
    val partyName: String,
    val adjustmentBase: Double
)


data class TreasuryMovementReportRow(
    val treasuryId: Long,
    val treasuryCode: String,
    val treasuryName: String,
    val treasuryKind: String,
    val currencyCode: String,
    val bankName: String,
    val accountNumber: String,
    val entryId: Long,
    val entryNo: String,
    val entryDate: Long,
    val description: String,
    val sourceType: String,
    val debitBase: Double,
    val creditBase: Double,
    val isInternalTransfer: Boolean
)

data class InventoryActivityReportRow(
    val itemId: Long,
    val code: String,
    val itemName: String,
    val baseUnitName: String,
    val quantityBase: Double,
    val inventoryValueBase: Double,
    val firstInboundDate: Long?,
    val lastMovementDate: Long?,
    val lastOutboundDate: Long?
)

data class InventoryExpiryLotReportRow(
    val warehouseName: String,
    val itemId: Long,
    val code: String,
    val itemName: String,
    val baseUnitName: String,
    val lotNo: String?,
    val expiryDate: Long,
    val quantityBase: Double,
    val inventoryValueBase: Double
)

data class InventoryMovementDetailReportRow(
    val id: Long,
    val movementDate: Long,
    val warehouseName: String,
    val itemId: Long,
    val code: String,
    val itemName: String,
    val baseUnitName: String,
    val movementType: String,
    val quantityBase: Double,
    val unitCostBase: Double,
    val movementValueBase: Double,
    val lotNo: String?,
    val expiryDate: Long?,
    val referenceType: String,
    val referenceId: Long
)
