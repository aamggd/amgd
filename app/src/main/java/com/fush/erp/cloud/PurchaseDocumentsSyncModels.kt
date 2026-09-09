package com.fush.erp.cloud

data class PurchaseConflictDifference(
    val field: String,
    val localValue: String,
    val cloudValue: String,
)

data class PurchaseDocumentsConflict(
    val documentType: String,
    val documentNo: String,
    val differences: List<PurchaseConflictDifference>,
)

data class PurchaseDocumentsSyncResult(
    val uploadedDocuments: Int,
    val downloadedDocuments: Int,
    val unchangedDocuments: Int,
    val conflicts: Int,
    val skippedLocalDocuments: Int,
    val bootstrappedCloud: Boolean,
    val completedAtEpochMillis: Long,
    val conflictDetails: List<PurchaseDocumentsConflict> = emptyList(),
)
