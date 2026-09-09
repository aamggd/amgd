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
    val netQtyBase: Double,
    val freeQtyBase: Double,
    val returnedFreeQtyBase: Double,
    val netFreeQtyBase: Double,
    val freeCostBase: Double,
    val returnedFreeCostBase: Double,
    val netFreeCostBase: Double
)


data class SalesInvoiceAccountingReportRow(
    val invoiceId: Long,
    val invoiceNo: String,
    val invoiceDate: Long,
    val dueDate: Long?,
    val customerId: Long,
    val customerName: String,
    val province: String,
    val salesRepName: String,
    val paymentType: String,
    val currencyCode: String,
    val totalOriginal: Double,
    val totalBase: Double,
    val returnsBase: Double,
    val collectedBase: Double,
    val outstandingBase: Double
)

data class ProductProfitabilityReportRow(
    val itemId: Long,
    val itemCode: String,
    val itemName: String,
    val grossRevenueBase: Double,
    val returnedRevenueBase: Double,
    val netRevenueBase: Double,
    val grossCostBase: Double,
    val returnedCostBase: Double,
    val netCostBase: Double,
    val netProfitBase: Double
)

data class SalesRepPerformanceReportRow(
    val salesRepName: String,
    val customerCount: Int,
    val invoiceCount: Int,
    val grossSalesBase: Double,
    val returnsBase: Double,
    val netSalesBase: Double,
    val collectionsBase: Double,
    val outstandingBase: Double,
    val commissionBase: Double
)

data class SalesReturnDetailReportRow(
    val returnNo: String,
    val returnDate: Long,
    val invoiceNo: String,
    val customerName: String,
    val salesRepName: String,
    val itemCode: String,
    val itemName: String,
    val quantityBase: Double,
    val returnValueBase: Double,
    val returnCostBase: Double,
    val reason: String
)

data class SalesMonthlyTrendReportRow(
    val periodKey: String,
    val invoiceCount: Int,
    val grossSalesBase: Double,
    val returnsBase: Double,
    val netSalesBase: Double
)

data class SalesAdditionalChargeReportRow(
    val chargeId: Long,
    val chargeNo: String,
    val chargeDate: Long,
    val customerName: String,
    val chargeTypeName: String,
    val bearer: String,
    val accountingTreatment: String,
    val principalAgentMode: String,
    val paymentStatus: String,
    val paidBy: String,
    val currencyCode: String,
    val amountOriginal: Double,
    val amountBase: Double,
    val paidBase: Double,
    val settledBase: Double,
    val remainingBase: Double,
    val serviceRevenueBase: Double
)

data class SalesShipmentCostAllocationReportRow(
    val shipmentId: Long,
    val shipmentNo: String,
    val shipmentDate: Long,
    val destinationProvince: String,
    val transportReference: String,
    val invoiceId: Long,
    val invoiceNo: String,
    val invoiceDate: Long,
    val customerName: String,
    val expenseType: String,
    val paymentVoucherNo: String,
    val paymentReference: String,
    val expenseAmountBase: Double,
    val allocatedBase: Double,
    val shipmentAllocatedQuantityBase: Double,
    val shipmentTotalExpenseBase: Double,
    val shipmentAllocatedExpenseBase: Double,
    val shipmentRemainingExpenseBase: Double
)

data class SalesReconciliationReportRow(
    val grossSalesBase: Double,
    val salesReturnsBase: Double,
    val netSalesBase: Double,
    val cashSalesBase: Double,
    val creditSalesBase: Double,
    val glSalesRevenueBase: Double,
    val glSalesReturnsBase: Double,
    val glNetSalesBase: Double,
    val companyExpensesBase: Double,
    val glCompanyExpensesBase: Double,
    val serviceRevenueBase: Double,
    val glServiceRevenueBase: Double,
    val recoverableClosingBase: Double,
    val glRecoverableClosingBase: Double,
    val operationalReceivablesClosingBase: Double,
    val glReceivablesClosingBase: Double
)

data class SupplierPurchaseReportRow(
    val supplierId: Long,
    val supplierName: String,
    val invoiceCount: Int,
    val grossPurchasesBase: Double,
    val returnsBase: Double,
    val netPurchasesBase: Double
)

/**
 * Detailed purchase invoice/line row used by the final purchases report.
 * Invoice-level monetary fields are intentionally repeated per line so the
 * export layer can group rows without additional database lookups.
 */
data class PurchaseInvoiceDetailReportRow(
    val invoiceId: Long,
    val invoiceNo: String,
    val supplierInvoiceNo: String,
    val invoiceDate: Long,
    val supplierName: String,
    val currencyCode: String,
    val exchangeRate: Double,
    val paymentType: String,
    val subtotalOriginal: Double,
    val discountOriginal: Double,
    val freightOriginal: Double,
    val customsOriginal: Double,
    val otherChargesOriginal: Double,
    val totalOriginal: Double,
    val totalBase: Double,
    val lineId: Long,
    val itemCode: String,
    val itemName: String,
    val unitName: String,
    val quantity: Double,
    val baseQuantity: Double,
    val unitPriceOriginal: Double,
    val lineTotalOriginal: Double,
    val lotNo: String?,
    val expiryDate: Long?
)


data class PurchaseInvoiceAccountingReportRow(
    val invoiceId: Long,
    val invoiceNo: String,
    val supplierInvoiceNo: String,
    val invoiceDate: Long,
    val dueDate: Long?,
    val supplierId: Long,
    val supplierName: String,
    val currencyCode: String,
    val exchangeRate: Double,
    val paymentType: String,
    val subtotalOriginal: Double,
    val discountOriginal: Double,
    val taxOriginal: Double,
    val chargesOriginal: Double,
    val totalOriginal: Double,
    val totalBase: Double,
    val supplierCreditReturnsBase: Double,
    val paidBase: Double,
    val outstandingBase: Double
)

data class PurchaseReturnReportRow(
    val returnId: Long,
    val returnNo: String,
    val purchaseInvoiceId: Long,
    val invoiceNo: String,
    val supplierName: String,
    val returnDate: Long,
    val currencyCode: String,
    val settlementType: String,
    val originalPaymentType: String,
    val totalOriginal: Double,
    val totalBase: Double
)

data class PurchaseItemAnalysisReportRow(
    val itemId: Long,
    val itemCode: String,
    val itemName: String,
    val baseUnitName: String,
    val grossQtyBase: Double,
    val returnedQtyBase: Double,
    val netQtyBase: Double,
    val grossValueBase: Double,
    val returnedValueBase: Double,
    val netValueBase: Double
)

data class PurchaseReconciliationReportRow(
    val grossPurchasesBase: Double,
    val purchaseReturnsBase: Double,
    val netPurchasesBase: Double,
    val inventoryGlNetBase: Double,
    val creditPurchasesBase: Double,
    val supplierCreditReturnsBase: Double,
    val apDocumentOperationalNetBase: Double,
    val apDocumentGlNetBase: Double,
    val openSupplierInvoicesBase: Double,
    val supplierVoucherAdjustmentBase: Double,
    val operationalPayablesClosingBase: Double,
    val apGlClosingBase: Double
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

// v176 — Read-only analytical rows for the integrated production report.
data class ProductionMaterialVarianceReportRow(
    val orderId: Long,
    val orderNo: String,
    val productCode: String,
    val productName: String,
    val batchNo: String?,
    val itemCode: String,
    val itemName: String,
    val unitName: String,
    val standardQtyBase: Double,
    val actualQtyBase: Double,
    val actualCostBase: Double,
    val averageUnitCostBase: Double,
    val quantityVarianceBase: Double,
    val varianceCostBase: Double,
    val variancePct: Double
)

data class ProductionOrderOverheadReportRow(
    val orderId: Long,
    val orderNo: String,
    val overheadBase: Double
)

data class ProductionOverheadAccountReportRow(
    val accountCode: String,
    val accountName: String,
    val amountBase: Double
)

data class ProductionWipReportRow(
    val openingWipBase: Double,
    val materialAddedBase: Double,
    val laborAddedBase: Double,
    val overheadAddedBase: Double,
    val finishedTransferredBase: Double,
    val rejectedTransferredBase: Double,
    val otherNetMovementBase: Double,
    val closingWipBase: Double
)

data class ProductionLossReportRow(
    val orderId: Long,
    val orderNo: String,
    val productCode: String,
    val productName: String,
    val batchNo: String?,
    val actualQtyBase: Double,
    val rejectedQtyBase: Double,
    val scrapQtyBase: Double,
    val baseProductionCost: Double,
    val reason: String,
    val responsible: String
)

data class ProductionLaborProductivityReportRow(
    val employeeId: Long,
    val employeeName: String,
    val orderCount: Int,
    val acceptedQtyBase: Double,
    val laborCostBase: Double
)

data class ProductionDowntimeReportRow(
    val sourceType: String,
    val sourceNo: String,
    val assetName: String,
    val eventDate: Long,
    val reason: String,
    val downtimeMinutes: Int,
    val status: String,
    val relatedOrders: String
)

data class ProductionLotTraceReportRow(
    val orderId: Long,
    val orderNo: String,
    val productCode: String,
    val productName: String,
    val finishedLotNo: String?,
    val rawItemCode: String,
    val rawItemName: String,
    val rawLotNo: String?,
    val rawExpiryDate: Long?,
    val issuedQtyBase: Double,
    val issueCostBase: Double
)

data class ProductionAccountingReconciliationReportRow(
    val operationalMaterialCostBase: Double,
    val glMaterialToWipBase: Double,
    val operationalLaborCostBase: Double,
    val glLaborToWipBase: Double,
    val operationalFinishedReceiptBase: Double,
    val glFinishedReceiptBase: Double,
    val glProductionLossBase: Double,
    val closingWipGlBase: Double
)
