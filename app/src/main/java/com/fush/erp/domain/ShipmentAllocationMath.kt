package com.fush.erp.domain

object ShipmentAllocationMath {
    private const val EPS = 0.000000001
    fun proportionalExpenseAllocation(
        expenseTotalBase: Double,
        alreadyAllocatedBase: Double,
        shipmentQuantityBase: Double,
        invoiceShipmentQuantityBase: Double
    ): Double {
        require(expenseTotalBase >= 0.0 && expenseTotalBase.isFinite()) { "إجمالي مصروف الشحنة غير صالح" }
        require(alreadyAllocatedBase >= 0.0 && alreadyAllocatedBase.isFinite()) { "المبلغ الموزع سابقاً غير صالح" }
        require(shipmentQuantityBase > 0.0 && shipmentQuantityBase.isFinite()) { "كمية الشحنة غير صالحة" }
        require(invoiceShipmentQuantityBase >= 0.0 && invoiceShipmentQuantityBase.isFinite()) { "كمية الفاتورة المرتبطة غير صالحة" }
        val remaining = (expenseTotalBase - alreadyAllocatedBase).coerceAtLeast(0.0)
        val ratio = (invoiceShipmentQuantityBase / shipmentQuantityBase).coerceIn(0.0, 1.0)
        return minOf(expenseTotalBase * ratio, remaining)
    }

    /** Ensures the final sale from a shipment consumes any rounding remainder exactly once. */
    fun automaticExpenseAllocation(
        expenseTotalBase: Double,
        alreadyAllocatedBase: Double,
        shipmentQuantityBase: Double,
        invoiceShipmentQuantityBase: Double,
        shipmentRemainingQuantityAfterInvoiceBase: Double
    ): Double {
        val remainingExpense = (expenseTotalBase - alreadyAllocatedBase).coerceAtLeast(0.0)
        if (shipmentRemainingQuantityAfterInvoiceBase <= EPS) return remainingExpense
        return proportionalExpenseAllocation(expenseTotalBase, alreadyAllocatedBase, shipmentQuantityBase, invoiceShipmentQuantityBase)
    }
    /** Pure fail-closed split used by sales shipment planning. Candidate order is already province/FIFO ordered. */
    fun splitQuantityAcrossAvailability(requiredQtyBase: Double, availabilityQtyBase: List<Double>): List<Double> {
        require(requiredQtyBase.isFinite() && requiredQtyBase > EPS) { "كمية البيع المطلوبة من الشحنات غير صالحة" }
        var remaining = requiredQtyBase
        val takes = availabilityQtyBase.map { rawAvailable ->
            require(rawAvailable.isFinite() && rawAvailable >= -EPS) { "رصيد شحنة غير صالح" }
            val available = rawAvailable.coerceAtLeast(0.0)
            val take = if (remaining > EPS) minOf(remaining, available) else 0.0
            remaining -= take
            take
        }
        require(remaining <= EPS) { "رصيد الشحنات المتاح غير كافٍ. العجز: $remaining بالوحدة الأساسية" }
        return takes
    }

}
