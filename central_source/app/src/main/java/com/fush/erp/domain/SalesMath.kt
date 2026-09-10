package com.fush.erp.domain

import kotlin.math.abs

data class SalesDraftLine(
    val itemId: Long,
    val unitId: Long,
    val quantity: Double,
    val factorToBase: Double,
    val unitPriceOriginal: Double
) {
    val baseQuantity: Double get() = quantity * factorToBase
    val grossOriginal: Double get() = quantity * unitPriceOriginal
}

object SalesMath {
    const val MAX_CREDIT_DAYS = 30
    const val DEFAULT_COMMISSION_PCT = 10.0
    const val FUSH_PRICE_FLOOR_BASE_PER_BOTTLE = 750.0
    const val BOTTLES_PER_CARTON = 480.0

    fun validateExchangeRate(rate: Double) {
        require(rate > 0.0 && rate.isFinite()) { "سعر الصرف يجب أن يكون أكبر من صفر" }
    }

    fun validatePricePeriod(effectiveFrom: Long, effectiveTo: Long?) {
        require(effectiveFrom >= 0L) { "تاريخ بداية قائمة السعر غير صالح" }
        require(effectiveTo == null || effectiveTo >= effectiveFrom) { "تاريخ انتهاء قائمة السعر يجب ألا يسبق تاريخ البداية" }
    }

    fun isPriceValidAt(effectiveFrom: Long, effectiveTo: Long?, isActive: Boolean, at: Long): Boolean {
        validatePricePeriod(effectiveFrom, effectiveTo)
        return isActive && at >= effectiveFrom && (effectiveTo == null || at <= effectiveTo)
    }

    fun configuredUnitPrice(baseUnitPriceOriginal: Double, factorToBase: Double): Double {
        require(baseUnitPriceOriginal > 0.0 && baseUnitPriceOriginal.isFinite()) { "سعر قائمة البيع غير صالح" }
        require(factorToBase > 0.0 && factorToBase.isFinite()) { "عامل التحويل غير صالح" }
        return baseUnitPriceOriginal * factorToBase
    }

    fun validateConfiguredUnitPrice(
        requestedUnitPriceOriginal: Double,
        baseUnitPriceOriginal: Double,
        factorToBase: Double
    ) {
        require(requestedUnitPriceOriginal > 0.0 && requestedUnitPriceOriginal.isFinite()) { "سعر البيع غير صالح" }
        val expected = configuredUnitPrice(baseUnitPriceOriginal, factorToBase)
        val tolerance = maxOf(1e-6, kotlin.math.abs(expected) * 1e-9)
        require(kotlin.math.abs(requestedUnitPriceOriginal - expected) <= tolerance) {
            "سعر السطر لا يطابق قائمة الأسعار السارية؛ أعد تحميل السعر"
        }
    }

    fun validateLine(line: SalesDraftLine) {
        require(line.itemId > 0) { "الصنف مطلوب" }
        require(line.unitId > 0) { "الوحدة مطلوبة" }
        require(line.quantity > 0.0 && line.quantity.isFinite()) { "الكمية يجب أن تكون أكبر من صفر" }
        require(line.factorToBase > 0.0 && line.factorToBase.isFinite()) { "عامل التحويل غير صالح" }
        require(line.unitPriceOriginal > 0.0 && line.unitPriceOriginal.isFinite()) { "سعر البيع يجب أن يكون أكبر من صفر" }
    }

    fun grossOriginal(lines: List<SalesDraftLine>): Double {
        require(lines.isNotEmpty()) { "يجب إضافة صنف واحد على الأقل" }
        lines.forEach(::validateLine)
        return lines.sumOf { it.grossOriginal }
    }

    fun totalBaseQuantity(lines: List<SalesDraftLine>): Double {
        lines.forEach(::validateLine)
        return lines.sumOf { it.baseQuantity }
    }

    fun maxAllowedDiscountPct(paymentType: String, totalBaseQty: Double): Double {
        require(paymentType in setOf("CASH", "CREDIT")) { "نوع البيع غير صالح" }
        require(totalBaseQty >= 0.0 && totalBaseQty.isFinite()) { "الكمية الإجمالية غير صالحة" }
        val isBulk = totalBaseQty + 1e-9 >= 5.0 * BOTTLES_PER_CARTON
        return when (paymentType) {
            "CREDIT" -> if (isBulk) 1.0 else 0.0
            else -> if (isBulk) 3.0 else 2.0
        }
    }

    fun validateDiscount(paymentType: String, totalBaseQty: Double, discountPct: Double) {
        require(discountPct >= 0.0 && discountPct.isFinite()) { "نسبة الخصم غير صالحة" }
        val max = maxAllowedDiscountPct(paymentType, totalBaseQty)
        require(discountPct <= max + 1e-9) { "الخصم يتجاوز الحد المسموح لهذه العملية (${formatPct(max)})" }
    }

    fun discountOriginal(grossOriginal: Double, discountPct: Double): Double {
        require(grossOriginal >= 0.0 && grossOriginal.isFinite()) { "إجمالي البيع غير صالح" }
        require(discountPct in 0.0..100.0 && discountPct.isFinite()) { "نسبة الخصم غير صالحة" }
        return grossOriginal * discountPct / 100.0
    }

    fun totalOriginal(
        grossOriginal: Double,
        discountOriginal: Double,
        transportOriginal: Double,
        feesOriginal: Double,
        riskMarginOriginal: Double
    ): Double {
        listOf(grossOriginal, discountOriginal, transportOriginal, feesOriginal, riskMarginOriginal).forEach {
            require(it >= 0.0 && it.isFinite()) { "أحد مبالغ الفاتورة غير صالح" }
        }
        require(discountOriginal <= grossOriginal + 1e-9) { "قيمة الخصم تتجاوز إجمالي الأصناف" }
        return grossOriginal - discountOriginal + transportOriginal + feesOriginal + riskMarginOriginal
    }

    fun toBaseAmount(original: Double, exchangeRate: Double): Double {
        validateExchangeRate(exchangeRate)
        require(original >= 0.0 && original.isFinite()) { "المبلغ غير صالح" }
        return original * exchangeRate
    }

    fun effectiveBaseUnitPriceBase(line: SalesDraftLine, discountPct: Double, exchangeRate: Double): Double {
        validateLine(line)
        validateExchangeRate(exchangeRate)
        require(discountPct in 0.0..100.0) { "نسبة الخصم غير صالحة" }
        val netOriginal = line.grossOriginal * (1.0 - discountPct / 100.0)
        return if (abs(line.baseQuantity) < 1e-12) 0.0 else netOriginal * exchangeRate / line.baseQuantity
    }

    fun validateCreditDays(days: Int) {
        require(days in 1..MAX_CREDIT_DAYS) { "مدة الائتمان القصوى 30 يوماً" }
    }

    fun validateReturn(requestedQuantity: Double, soldQuantity: Double, alreadyReturned: Double) {
        require(requestedQuantity > 0.0 && requestedQuantity.isFinite()) { "كمية المرتجع يجب أن تكون أكبر من صفر" }
        val remaining = soldQuantity - alreadyReturned
        require(requestedQuantity <= remaining + 1e-9) { "كمية المرتجع تتجاوز الكمية المتاحة للمرتجع" }
    }

    fun commissionBase(collectedBase: Double, ratePct: Double = DEFAULT_COMMISSION_PCT): Double {
        require(collectedBase >= 0.0 && collectedBase.isFinite()) { "مبلغ التحصيل غير صالح" }
        require(ratePct >= 0.0 && ratePct <= 100.0 && ratePct.isFinite()) { "نسبة العمولة غير صالحة" }
        return collectedBase * ratePct / 100.0
    }

    fun commissionReversalBase(returnedSalesBase: Double, ratePct: Double = DEFAULT_COMMISSION_PCT): Double {
        return commissionBase(returnedSalesBase, ratePct)
    }

    private fun formatPct(value: Double): String = if (abs(value - value.toInt()) < 1e-9) "${value.toInt()}%" else "$value%"
}
