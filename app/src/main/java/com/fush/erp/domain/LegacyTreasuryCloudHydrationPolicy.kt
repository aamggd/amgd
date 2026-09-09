package com.fush.erp.domain

/**
 * Compatibility policy for posted treasury journals created before stable voucher operation IDs
 * were introduced.
 *
 * The old TREASURY_* source types are deliberately blocked for new local INSERTs by
 * AccountingIdempotencyDatabaseGuards. Cloud synchronization, however, still needs to hydrate
 * already-posted historical journals that legitimately use those source types.
 *
 * Hydration therefore inserts the header temporarily with an internal cloud-only STAGING alias
 * inside one transaction, writes all lines, and atomically restores the original historical source
 * type in the same UPDATE that transitions STAGING -> POSTED. This lets a second device replicate
 * an already-posted historical fact even when that device has already closed the period, while
 * keeping all new local posting paths fail-closed. This keeps the local posting policy intact while
 * preserving byte-for-byte cloud identity for subsequent sync comparisons.
 */
object LegacyTreasuryCloudHydrationPolicy {
    /**
     * Internal-only transient source used while a legacy cloud journal is still STAGING.
     * It deliberately avoids MANUAL because manual journals are governed by DRAFT -> approval.
     */
    const val STAGING_ALIAS_SOURCE_TYPE = "CLOUD_LEGACY_TREASURY_HYDRATION"

    val historicalSourceTypes: Set<String> = linkedSetOf(
        "TREASURY_TRANSFER",
        "TREASURY_RECEIPT",
        "TREASURY_PAYMENT",
        "TREASURY_EXPENSE",
        "TREASURY_INCOME",
    )

    fun requiresCompatibilityHydration(sourceType: String): Boolean =
        sourceType.trim().uppercase() in historicalSourceTypes

    fun stableHistoricalSourceId(sourceType: String, sourceRef: String, entryNo: String): String {
        val normalizedType = sourceType.trim().uppercase()
        require(normalizedType in historicalSourceTypes) { "NOT_LEGACY_TREASURY_SOURCE:$normalizedType" }
        val stableRef = sourceRef.trim().ifBlank { entryNo.trim() }
        require(stableRef.isNotBlank()) { "LEGACY_TREASURY_CLOUD_SOURCE_REF_REQUIRED" }
        return "cloud-legacy:${normalizedType.lowercase()}:${stableRef.uppercase()}"
    }
}
