package com.fush.erp.domain

/**
 * Resolves the sourceId used while hydrating a posted journal from cloud storage.
 *
 * Local accounting guards intentionally require every non-MANUAL journal to carry a stable
 * sourceId. Cloud payloads use portable document references, while many local sourceIds are
 * device-local row ids. Resolution can therefore legitimately fail on a second device even when
 * the journal itself is valid. A failed lookup must never be converted to a null sourceId because
 * that turns a recoverable replication case into ACCOUNTING_SOURCE_ID_REQUIRED.
 *
 * The policy prefers the locally resolved business identity. If it is unavailable, it falls back
 * to a deterministic cloud-only identity derived from immutable payload fields. The fallback is
 * namespaced and stable across retries, and does not relax any SQLite accounting guard.
 */
object AccountingCloudHydrationSourceIdentityPolicy {
    const val FALLBACK_PREFIX = "cloud-sync"

    fun sourceIdForHydration(
        sourceType: String,
        sourceRef: String,
        entryNo: String,
        resolvedLocalSourceId: String?,
        reversalEntryNo: String? = null,
    ): String? {
        val normalizedType = sourceType.trim().uppercase().ifBlank { "MANUAL" }

        if (normalizedType == "MANUAL") {
            return resolvedLocalSourceId?.trim()?.takeIf { it.isNotEmpty() }
        }

        if (LegacyTreasuryCloudHydrationPolicy.requiresCompatibilityHydration(normalizedType)) {
            return LegacyTreasuryCloudHydrationPolicy.stableHistoricalSourceId(
                normalizedType,
                sourceRef,
                entryNo,
            )
        }

        resolvedLocalSourceId?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }

        // Non-MANUAL journal identity is mandatory. Prefer a portable source reference, then a
        // reversal reference, and finally the immutable journal number. entryNo is required by
        // the cloud journal contract, so a valid payload always has a deterministic fallback.
        val portableRef = sourceRef.trim()
            .ifBlank { reversalEntryNo?.trim().orEmpty() }
            .ifBlank { entryNo.trim() }
        require(portableRef.isNotBlank()) { "CLOUD_ACCOUNTING_SOURCE_IDENTITY_REQUIRED:$normalizedType" }

        return "$FALLBACK_PREFIX:${normalizedType.lowercase()}:${portableRef.uppercase()}"
    }
}
