package com.fush.erp.domain

object WarehouseReorderMath {
    data class Lot(
        val quantityBase: Double,
        val expiryDate: Long? = null,
        val controlStatus: String = "ACCEPTED"
    )

    fun validateLevel(level: Double): Double {
        require(level.isFinite() && level >= 0.0) { "حد إعادة الطلب يجب أن يكون صفراً أو رقماً موجباً" }
        return level
    }

    fun usableQuantity(lots: List<Lot>, at: Long): Double = lots
        .filter { it.controlStatus == "ACCEPTED" }
        .filter { it.expiryDate == null || it.expiryDate >= at }
        .sumOf { it.quantityBase }

    fun needsReorder(usableQuantityBase: Double, reorderLevel: Double): Boolean {
        validateLevel(reorderLevel)
        return usableQuantityBase <= reorderLevel + InventoryMath.EPS
    }
}
