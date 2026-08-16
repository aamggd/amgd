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

    /**
     * P0 employee identity rule: once an employee-linked financial movement exists,
     * the stable employee id behind that movement cannot be replaced by another employee.
     * Display names remain editable snapshots/presentation data and are not identity keys.
     */
    fun employeeIdentityChangeAllowed(
        currentEmployeeId: Long?,
        requestedEmployeeId: Long,
        financialMovementExists: Boolean
    ): Boolean = currentEmployeeId == null ||
        currentEmployeeId == requestedEmployeeId ||
        !financialMovementExists
}
