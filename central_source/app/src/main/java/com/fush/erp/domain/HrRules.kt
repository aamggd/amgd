package com.fush.erp.domain

object HrRules {
    fun trainingIsValid(
        result: String,
        practicalObserved: Boolean,
        requiresPracticalObservation: Boolean,
        completedAt: Long,
        expiresAt: Long?,
        at: Long
    ): Boolean = result == "PASS" &&
        (!requiresPracticalObservation || practicalObserved) &&
        completedAt <= at &&
        (expiresAt == null || expiresAt >= at)

    fun authorizationIsValid(status: String, issuedAt: Long, expiresAt: Long?, at: Long): Boolean =
        status == "ACTIVE" && issuedAt <= at && (expiresAt == null || expiresAt >= at)
}
