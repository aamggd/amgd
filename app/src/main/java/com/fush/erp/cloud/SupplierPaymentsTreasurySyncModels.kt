package com.fush.erp.cloud

data class SupplierPaymentsTreasuryConflictDifference(
    val field: String,
    val localValue: String,
    val cloudValue: String,
)

data class SupplierPaymentsTreasuryConflict(
    val documentType: String,
    val documentNo: String,
    val differences: List<SupplierPaymentsTreasuryConflictDifference>,
)

data class SupplierPaymentsTreasurySyncResult(
    val uploadedPayments: Int,
    val downloadedPayments: Int,
    val unchangedPayments: Int,
    val conflicts: Int,
    val skippedLocalPayments: Int,
    val treasuryAccountsReady: Int,
    val bootstrappedCloud: Boolean,
    val completedAtEpochMillis: Long,
    val conflictDetails: List<SupplierPaymentsTreasuryConflict> = emptyList(),
)
