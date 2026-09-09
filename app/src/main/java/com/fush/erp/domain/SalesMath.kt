package com.fush.erp.domain

import kotlin.math.abs

data class SalesDraftLine(
    val itemId: Long,
    val unitId: Long,
    val quantity: Double,
    val factorToBase: Double,
    val unitPriceOriginal: Double,
    val freeQuantity: Double = 0.0,
    /** Preferred/starting shipment selected by the user. If it cannot cover the full physical quantity,
     * posting continues automatically from the next oldest eligible shipments. */
    val preferredShipmentId: Long? = null,
    /** v186: false means a direct warehouse sale with no shipment trace/cost allocation. */
    val useShipmentTracking: Boolean = true
) {
    val baseQuantity: Double get() = quantity * factorToBase
    val freeBaseQuantity: Double get() = freeQuantity * factorToBase
    val totalQuantity: Double get() = quantity + freeQuantity
    val totalBaseQuantity: Double get() = baseQuantity + freeBaseQuantity
    val grossOriginal: Double get() = quantity * unitPriceOriginal
}

object SalesDocumentNumberPolicy {
    fun manualOrNull(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank()) return null
        require(value.length <= 80) { "رقم المستند طويل جداً" }
        require(value.none { it == '\n' || it == '\r' || it == '\t' }) { "رقم المستند يحتوي محارف غير صالحة" }
        return value
    }

    fun requireManual(raw: String, label: String): String =
        requireNotNull(manualOrNull(raw)) { "$label مطلوب ولا يمكن إنشاء المستند بدونه" }
}

object SalesMath {

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
        require(line.quantity > 0.0 && line.quantity.isFinite()) { "الكمية المباعة يجب أن تكون أكبر من صفر" }
        require(line.freeQuantity >= 0.0 && line.freeQuantity.isFinite()) { "الكمية المجانية لا يمكن أن تكون سالبة" }
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

    fun totalFreeBaseQuantity(lines: List<SalesDraftLine>): Double {
        lines.forEach(::validateLine)
        return lines.sumOf { it.freeBaseQuantity }
    }

    fun totalPhysicalBaseQuantity(lines: List<SalesDraftLine>): Double {
        lines.forEach(::validateLine)
        return lines.sumOf { it.totalBaseQuantity }
    }

    fun validateDiscount(paymentType: String, totalBaseQty: Double, discountPct: Double) {
        require(paymentType in setOf("CASH", "CREDIT")) { "نوع البيع غير صالح" }
        require(totalBaseQty >= 0.0 && totalBaseQty.isFinite()) { "الكمية الإجمالية غير صالحة" }
        require(discountPct >= 0.0 && discountPct < 100.0 && discountPct.isFinite()) { "نسبة الخصم يجب أن تكون من 0 وأقل من 100%. البضاعة المجانية لا تسجل كخصم 100%" }
    }

    fun discountOriginal(grossOriginal: Double, discountPct: Double): Double {
        require(grossOriginal >= 0.0 && grossOriginal.isFinite()) { "إجمالي البيع غير صالح" }
        require(discountPct >= 0.0 && discountPct < 100.0 && discountPct.isFinite()) { "نسبة الخصم غير صالحة؛ يجب أن تكون أقل من 100%" }
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
        require(grossOriginal > 0.0) { "إجمالي الأصناف يجب أن يكون أكبر من صفر" }
        require(discountOriginal < grossOriginal) { "قيمة الخصم يجب أن تكون أقل من إجمالي الأصناف؛ الخصم الكامل 100% غير مسموح في فاتورة البيع" }
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
        require(discountPct >= 0.0 && discountPct < 100.0 && discountPct.isFinite()) { "نسبة الخصم غير صالحة؛ يجب أن تكون أقل من 100%" }
        val netOriginal = line.grossOriginal * (1.0 - discountPct / 100.0)
        return if (abs(line.baseQuantity) < 1e-12) 0.0 else netOriginal * exchangeRate / line.baseQuantity
    }

    fun validateCreditDays(days: Int) {
        require(days > 0) { "مدة الائتمان يجب أن تكون أكبر من صفر" }
    }

    fun validateReturnComponent(
        requestedQuantity: Double,
        soldQuantity: Double,
        alreadyReturned: Double,
        label: String
    ) {
        require(requestedQuantity >= 0.0 && requestedQuantity.isFinite()) { "$label غير صالحة" }
        require(soldQuantity >= 0.0 && soldQuantity.isFinite()) { "الكمية الأصلية غير صالحة" }
        require(alreadyReturned >= 0.0 && alreadyReturned.isFinite()) { "الكمية المرتجعة سابقاً غير صالحة" }
        val remaining = (soldQuantity - alreadyReturned).coerceAtLeast(0.0)
        require(requestedQuantity <= remaining + 1e-9) { "$label تتجاوز الكمية المتاحة للمرتجع" }
    }

    fun validateReturn(requestedQuantity: Double, soldQuantity: Double, alreadyReturned: Double) {
        require(requestedQuantity > 0.0 && requestedQuantity.isFinite()) { "كمية المرتجع يجب أن تكون أكبر من صفر" }
        validateReturnComponent(requestedQuantity, soldQuantity, alreadyReturned, "كمية المرتجع")
    }

    fun commissionBase(collectedBase: Double, ratePct: Double): Double {
        require(collectedBase >= 0.0 && collectedBase.isFinite()) { "مبلغ التحصيل غير صالح" }
        require(ratePct >= 0.0 && ratePct <= 100.0 && ratePct.isFinite()) { "نسبة العمولة غير صالحة" }
        return collectedBase * ratePct / 100.0
    }

    fun commissionReversalBase(returnedSalesBase: Double, ratePct: Double): Double {
        return commissionBase(returnedSalesBase, ratePct)
    }

}
