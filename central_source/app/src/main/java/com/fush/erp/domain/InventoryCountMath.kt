package com.fush.erp.domain

object InventoryCountMath {
    data class MissingLineInput(
        val countedQtyBase: Double,
        val unitCostBase: Double,
        val lotTracked: Boolean,
        val expiryTracked: Boolean,
        val lotNo: String?,
        val expiryDate: Long?
    )

    fun validateMissingLine(input: MissingLineInput) {
        require(input.countedQtyBase.isFinite() && input.countedQtyBase > InventoryMath.EPS) {
            "الكمية الفعلية للصنف غير الموجود في لقطة الجرد يجب أن تكون أكبر من صفر"
        }
        require(input.unitCostBase.isFinite() && input.unitCostBase > InventoryMath.EPS) {
            "تكلفة الوحدة للصنف المضاف إلى الجرد يجب أن تكون أكبر من صفر"
        }
        if (input.lotTracked) {
            require(!input.lotNo.isNullOrBlank()) { "رقم التشغيلة مطلوب لهذا الصنف" }
        }
        if (input.expiryTracked) {
            require(input.expiryDate != null && input.expiryDate > 0L) { "تاريخ الصلاحية مطلوب لهذا الصنف" }
        }
    }
}
