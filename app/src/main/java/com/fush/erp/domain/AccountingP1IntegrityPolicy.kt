package com.fush.erp.domain

/**
 * Immutable executable view of the historical P1 duplicate-protection gate.
 *
 * Do not grow this set when later features become replay-safe.  Current runtime idempotency is
 * owned by [AccountingPostingIdempotencyPolicy]; keeping P1 frozen preserves audit traceability
 * and prevents later feature work from silently changing the meaning of the original P1 gate.
 */
object AccountingP1IntegrityPolicy {
    val duplicateProtectedSourceTypes: Set<String> by lazy {
        AccountingIntegrationContract
            .p1StableKeyCandidates()
            .map { it.sourceType }
            .toSet()
    }

    fun isDuplicateProtected(sourceType: String): Boolean =
        sourceType.trim().uppercase() in duplicateProtectedSourceTypes

    fun stableEventKeyOrNull(sourceType: String, sourceId: String?): String? =
        if (isDuplicateProtected(sourceType) && !sourceId.isNullOrBlank())
            AccountingIntegrationContract.canonicalEventKey(sourceType, sourceId)
        else null
}
