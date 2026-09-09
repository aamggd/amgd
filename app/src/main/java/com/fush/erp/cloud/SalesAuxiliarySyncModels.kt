package com.fush.erp.cloud

data class SalesAuxiliaryConflictDifference(
    val field: String,
    val localValue: String,
    val cloudValue: String,
)

data class SalesAuxiliaryConflict(
    val entityType: String,
    val entityKey: String,
    val differences: List<SalesAuxiliaryConflictDifference>,
)

enum class SalesAuxiliaryConflictResolution {
    KEEP_LOCAL,
    USE_CLOUD,
}

data class SalesAuxiliarySyncResult(
    val uploadedDocuments: Int,
    val downloadedDocuments: Int,
    val unchangedDocuments: Int,
    val conflicts: Int,
    val skippedLocalDocuments: Int,
    val completedAtEpochMillis: Long,
    val conflictDetails: List<SalesAuxiliaryConflict> = emptyList(),
)
