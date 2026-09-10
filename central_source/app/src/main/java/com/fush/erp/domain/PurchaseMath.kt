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
