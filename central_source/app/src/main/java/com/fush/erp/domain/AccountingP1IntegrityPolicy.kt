package com.fush.erp.domain

/**
 * Executable P1 view of the P0 contract.
 *
 * Only source identities classified STABLE_SOURCE are eligible for DB-level duplicate-posting
 * protection. Manual/repeatable/state-guarded events must not be falsely collapsed into one event.
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
