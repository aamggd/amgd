package com.fush.erp.cloud

data class SalesConflictDifference(
    val field: String,
    val localValue: String,
    val cloudValue: String,
)

data class SalesReceivablesConflict(
    val documentType: String,
    val documentNo: String,
    val differences: List<SalesConflictDifference>,
)

data class SalesReceivablesSyncResult(
    val uploadedDocuments: Int,
    val downloadedDocuments: Int,
    val unchangedDocuments: Int,
    val conflicts: Int,
    val skippedLocalDocuments: Int,
    val bootstrappedCloud: Boolean,
    val completedAtEpochMillis: Long,
    val conflictDetails: List<SalesReceivablesConflict> = emptyList(),
)
