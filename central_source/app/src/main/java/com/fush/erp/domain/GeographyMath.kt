package com.fush.erp.domain

import com.fush.erp.data.entity.ProvincePolicyEntity

data class GeographicQuoteResult(
    val currencyCode: String,
    val exchangeRateToBase: Double,
    val productOriginal: Double,
    val transportOriginal: Double,
    val feesOriginal: Double,
    val riskMarginOriginal: Double,
    val totalOriginal: Double,
    val totalBaseEquivalent: Double
)

object GeographyMath {
    const val ADEN_TRANSPORT_PER_CARTON_BASE = 10_000.0

    fun oldYerToNewYerRate(usdNewYer: Double, usdOldYer: Double): Double {
        require(usdNewYer > 0.0 && usdNewYer.isFinite()) { "سعر الدولار بالريال الجديد غير صالح" }
        require(usdOldYer > 0.0 && usdOldYer.isFinite()) { "سعر الدولار بالريال القديم غير صالح" }
        return usdNewYer / usdOldYer
    }

    fun newYerToOldYer(amountNew: Double, usdNewYer: Double, usdOldYer: Double): Double {
        require(amountNew >= 0.0 && amountNew.isFinite()) { "المبلغ بالريال الجديد غير صالح" }
        return amountNew / usdNewYer * usdOldYer
    }

    fun quote(
        policy: ProvincePolicyEntity,
        cartons: Double,
        productAmountNewBase: Double,
        usdNewYer: Double? = null,
        usdOldYer: Double? = null,
        transportOverrideOriginal: Double? = null,
        feesOriginal: Double = 0.0,
        riskMarginOriginal: Double = 0.0
    ): GeographicQuoteResult {
        require(cartons >= 0.0 && cartons.isFinite()) { "عدد الكراتين غير صالح" }
        require(productAmountNewBase >= 0.0 && productAmountNewBase.isFinite()) { "قيمة المنتج غير صالحة" }
        require(feesOriginal >= 0.0 && feesOriginal.isFinite()) { "الرسوم غير صالحة" }
        require(riskMarginOriginal >= 0.0 && riskMarginOriginal.isFinite()) { "هامش المخاطر غير صالح" }

        val rate = if (policy.currencyCode == "YER_OLD" || policy.requiresDailyFx) {
            oldYerToNewYerRate(
                requireNotNull(usdNewYer) { "أدخل سعر الدولار بالطبعة الجديدة" },
                requireNotNull(usdOldYer) { "أدخل سعر الدولار بالطبعة القديمة" }
            )
        } else 1.0

        val productOriginal = if (policy.currencyCode == "YER_OLD") productAmountNewBase / rate else productAmountNewBase
        val defaultTransportBase = policy.defaultTransportPerCartonBase * cartons
        val defaultTransportOriginal = if (policy.currencyCode == "YER_OLD") defaultTransportBase / rate else defaultTransportBase
        val transport = transportOverrideOriginal ?: defaultTransportOriginal
        require(transport >= 0.0 && transport.isFinite()) { "تكلفة النقل غير صالحة" }
        val totalOriginal = productOriginal + transport + feesOriginal + riskMarginOriginal
        return GeographicQuoteResult(
            currencyCode = policy.currencyCode,
            exchangeRateToBase = rate,
            productOriginal = productOriginal,
            transportOriginal = transport,
            feesOriginal = feesOriginal,
            riskMarginOriginal = riskMarginOriginal,
            totalOriginal = totalOriginal,
            totalBaseEquivalent = totalOriginal * rate
        )
    }
}
