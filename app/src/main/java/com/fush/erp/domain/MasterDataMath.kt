package com.fush.erp.domain

import kotlin.math.abs

object MasterDataMath {
    private const val EPSILON = 0.000000001

    fun validateConversionFactor(factorToBase: Double, isBaseUnit: Boolean): Double {
        require(factorToBase.isFinite() && factorToBase > 0.0) { "عامل التحويل يجب أن يكون رقمًا موجبًا" }
        if (isBaseUnit) {
            require(abs(factorToBase - 1.0) <= EPSILON) { "الوحدة الأساسية يجب أن يكون معاملها 1" }
        }
        return factorToBase
    }

    fun normalizeBarcode(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    fun requireEmptyBalanceForDeactivation(balance: Double, label: String) {
        require(balance.isFinite()) { "رصيد $label غير صالح" }
        require(abs(balance) <= EPSILON) { "لا يمكن إيقاف $label ويوجد له رصيد مخزني قائم" }
    }
}
