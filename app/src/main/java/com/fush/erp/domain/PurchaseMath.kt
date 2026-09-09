package com.fush.erp.domain

import kotlin.math.abs

data class PurchaseDraftLine(
    val itemId: Long,
    val unitId: Long,
    val quantity: Double,
    val factorToBase: Double,
    val unitPriceOriginal: Double,
    val lotNo: String? = null,
    val expiryDate: Long? = null
) {
    val baseQuantity: Double get() = quantity * factorToBase
    val lineTotalOriginal: Double get() = quantity * unitPriceOriginal
}

data class PurchaseReturnDraftLine(
    val purchaseLineId: Long,
    val quantity: Double
)

data class PurchasePriceVariance(
    val amount: Double,
    val percent: Double?
)

object PurchaseMath {
    fun validateExchangeRate(rate: Double) {
        require(rate > 0.0 && rate.isFinite()) { "سعر الصرف يجب أن يكون أكبر من صفر" }
    }

    fun validateLine(line: PurchaseDraftLine) {
        require(line.itemId > 0) { "الصنف مطلوب" }
        require(line.unitId > 0) { "الوحدة مطلوبة" }
        require(line.quantity > 0.0 && line.quantity.isFinite()) { "الكمية يجب أن تكون أكبر من صفر" }
        require(line.factorToBase > 0.0 && line.factorToBase.isFinite()) { "عامل التحويل غير صالح" }
        require(line.unitPriceOriginal >= 0.0 && line.unitPriceOriginal.isFinite()) { "سعر الوحدة غير صالح" }
    }

    fun totalOriginal(lines: List<PurchaseDraftLine>): Double {
        require(lines.isNotEmpty()) { "يجب إضافة صنف واحد على الأقل" }
        lines.forEach(::validateLine)
        return lines.sumOf { it.lineTotalOriginal }
    }

    fun toBaseAmount(original: Double, exchangeRate: Double): Double {
        validateExchangeRate(exchangeRate)
        require(original >= 0.0 && original.isFinite()) { "المبلغ غير صالح" }
        return original * exchangeRate
    }

    fun unitCostBase(line: PurchaseDraftLine, exchangeRate: Double): Double {
        validateLine(line)
        validateExchangeRate(exchangeRate)
        return if (abs(line.baseQuantity) < 1e-12) 0.0 else line.lineTotalOriginal * exchangeRate / line.baseQuantity
    }


    fun validateInvoiceAdjustments(
        subtotalOriginal: Double,
        discountOriginal: Double,
        freightOriginal: Double,
        customsOriginal: Double,
        otherChargesOriginal: Double
    ) {
        require(subtotalOriginal >= 0.0 && subtotalOriginal.isFinite()) { "إجمالي الأصناف غير صالح" }
        val values = listOf(
            "خصم المورد" to discountOriginal,
            "رسوم النقل/الشحن" to freightOriginal,
            "الجمارك" to customsOriginal,
            "الرسوم الأخرى" to otherChargesOriginal
        )
        values.forEach { (label, value) ->
            require(value >= 0.0 && value.isFinite()) { "$label يجب أن يكون صفراً أو قيمة موجبة" }
        }
        require(discountOriginal <= subtotalOriginal + 1e-9) { "خصم المورد لا يمكن أن يتجاوز إجمالي الأصناف" }
        val charges = freightOriginal + customsOriginal + otherChargesOriginal
        require(subtotalOriginal > 1e-12 || charges <= 1e-12) {
            "لا يمكن توزيع رسوم شراء على فاتورة إجمالي أصنافها صفر"
        }
        val total = subtotalOriginal - discountOriginal + charges
        require(total >= -1e-9 && total.isFinite()) { "إجمالي فاتورة الشراء بعد الخصم والرسوم غير صالح" }
    }

    fun invoiceTotalOriginal(
        subtotalOriginal: Double,
        discountOriginal: Double,
        freightOriginal: Double,
        customsOriginal: Double,
        otherChargesOriginal: Double
    ): Double {
        validateInvoiceAdjustments(subtotalOriginal, discountOriginal, freightOriginal, customsOriginal, otherChargesOriginal)
        return (subtotalOriginal - discountOriginal + freightOriginal + customsOriginal + otherChargesOriginal).coerceAtLeast(0.0)
    }

    fun allocatedUnitCostBase(
        line: PurchaseDraftLine,
        exchangeRate: Double,
        subtotalOriginal: Double,
        invoiceTotalOriginal: Double
    ): Double {
        validateLine(line)
        validateExchangeRate(exchangeRate)
        require(subtotalOriginal >= 0.0 && subtotalOriginal.isFinite()) { "إجمالي الأصناف غير صالح" }
        require(invoiceTotalOriginal >= 0.0 && invoiceTotalOriginal.isFinite()) { "إجمالي الفاتورة غير صالح" }
        if (abs(line.baseQuantity) < 1e-12 || abs(subtotalOriginal) < 1e-12) return 0.0
        val allocatedOriginal = line.lineTotalOriginal * invoiceTotalOriginal / subtotalOriginal
        return allocatedOriginal * exchangeRate / line.baseQuantity
    }

    fun returnTotalOriginal(baseQuantity: Double, unitCostBase: Double, exchangeRate: Double): Double {
        require(baseQuantity > 0.0 && baseQuantity.isFinite()) { "كمية المرتجع الأساسية غير صالحة" }
        require(unitCostBase >= 0.0 && unitCostBase.isFinite()) { "تكلفة المرتجع غير صالحة" }
        validateExchangeRate(exchangeRate)
        return baseQuantity * unitCostBase / exchangeRate
    }

    fun priceVariance(currentPrice: Double, previousPrice: Double): PurchasePriceVariance {
        require(currentPrice >= 0.0 && currentPrice.isFinite()) { "السعر الحالي غير صالح" }
        require(previousPrice >= 0.0 && previousPrice.isFinite()) { "السعر السابق غير صالح" }
        val amount = currentPrice - previousPrice
        val percent = if (abs(previousPrice) < 1e-12) null else amount / previousPrice * 100.0
        return PurchasePriceVariance(amount = amount, percent = percent)
    }

    fun validateReturn(requestedQuantity: Double, purchasedQuantity: Double, alreadyReturned: Double) {
        require(requestedQuantity > 0.0 && requestedQuantity.isFinite()) { "كمية المرتجع يجب أن تكون أكبر من صفر" }
        require(purchasedQuantity >= 0.0 && purchasedQuantity.isFinite()) { "كمية الشراء الأصلية غير صالحة" }
        require(alreadyReturned >= 0.0 && alreadyReturned.isFinite()) { "كمية المرتجع السابقة غير صالحة" }
        val remaining = (purchasedQuantity - alreadyReturned).coerceAtLeast(0.0)
        require(requestedQuantity <= remaining + 1e-9) { "كمية المرتجع تتجاوز الكمية المتاحة للمرتجع" }
    }

    fun validateReturnDraft(lines: List<PurchaseReturnDraftLine>) {
        require(lines.isNotEmpty()) { "اختر صنفاً واحداً على الأقل للمرتجع" }
        require(lines.map { it.purchaseLineId }.distinct().size == lines.size) { "لا يمكن تكرار نفس سطر الشراء في المرتجع" }
        lines.forEach { line ->
            require(line.purchaseLineId > 0) { "سطر الشراء غير صالح" }
            require(line.quantity > 0.0 && line.quantity.isFinite()) { "كمية المرتجع يجب أن تكون أكبر من صفر" }
        }
    }
}
