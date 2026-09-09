package com.fush.erp.domain

import java.util.Locale

object ProductionBatchNumberPolicy {
    /**
     * Batch identity is derived from the product master-data code, never from a hardcoded size/name.
     * Example: FG-000123 -> FG000123. This works unchanged for 60ml, 100ml, 200ml, or any future product.
     */
    fun productPrefix(productCode: String, productId: Long): String {
        val normalized = productCode
            .trim()
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]+"), "")
        return normalized.ifBlank { "F$productId" }
    }
}
