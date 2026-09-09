package com.fush.erp.cloud

data class AccountingSyncConflictDifference(
    val field: String,
    val localValue: String,
    val cloudValue: String,
)

data class AccountingSyncConflict(
    val entityType: String,
    val entityKey: String,
    val differences: List<AccountingSyncConflictDifference>,
)

enum class AccountingConflictResolution {
    KEEP_LOCAL,
    USE_CLOUD,
}

data class AccountingCloudSyncResult(
    val uploadedJournals: Int,
    val downloadedJournals: Int,
    val unchangedJournals: Int,
    val uploadedTreasuryVouchers: Int,
    val downloadedTreasuryVouchers: Int,
    val unchangedTreasuryVouchers: Int,
    val skippedLocalJournals: Int,
    val skippedLocalTreasuryVouchers: Int,
    val conflicts: Int,
    val treasuryAccountsChecked: Int,
    val treasuryBalanceDifferenceBase: Double,
    val bootstrappedCloud: Boolean,
    val completedAtEpochMillis: Long,
    val conflictDetails: List<AccountingSyncConflict> = emptyList(),
)

data class AccountingReferenceSyncResult(
    val publishedSnapshot: Boolean,
    val downloadedAccounts: Int,
    val downloadedTreasuries: Int,
    val downloadedEmployees: Int,
    val downloadedSalesRepresentatives: Int,
    val snapshotHash: String?,
    val completedAtEpochMillis: Long,
) {
    val downloadedRows: Int
        get() = downloadedAccounts + downloadedTreasuries + downloadedEmployees + downloadedSalesRepresentatives
}
