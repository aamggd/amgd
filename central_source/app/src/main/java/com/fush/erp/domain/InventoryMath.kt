package com.fush.erp.domain

object InventoryMath {
    const val EPS = 1e-9
    const val DEFAULT_EXPIRY_ALERT_DAYS = 30
    const val DEFAULT_STALE_DAYS = 90

    fun variance(systemQty: Double, countedQty: Double): Double {
        require(systemQty.isFinite() && countedQty.isFinite()) { "الكميات غير صالحة" }
        require(countedQty >= 0.0) { "الكمية الفعلية لا يمكن أن تكون سالبة" }
        return countedQty - systemQty
    }

    fun varianceValue(varianceQty: Double, unitCost: Double): Double {
        require(varianceQty.isFinite() && unitCost.isFinite() && unitCost >= 0.0) { "قيمة فرق الجرد غير صالحة" }
        return varianceQty * unitCost
    }

    fun needsReorder(onHand: Double, reorderLevel: Double): Boolean = reorderLevel > 0.0 && onHand <= reorderLevel + EPS

    fun isExpired(expiryDate: Long?, at: Long): Boolean = expiryDate != null && expiryDate < at

    fun isNearExpiry(expiryDate: Long?, at: Long, days: Int = DEFAULT_EXPIRY_ALERT_DAYS): Boolean {
        if (expiryDate == null || days < 0) return false
        val until = at + days.toLong() * 86_400_000L
        return expiryDate in at..until
    }

    fun lotKey(lotNo: String?): String = lotNo?.trim().orEmpty()
    fun expiryKey(expiryDate: Long?): Long = expiryDate ?: -1L
}
