package com.fush.erp.cloud

data class MasterDataSyncResult(
    val uploaded: Int,
    val downloaded: Int,
    val unchanged: Int,
    val conflicts: Int,
    val skippedLocalOnFirstBaseline: Int,
    val skippedUnauthorized: Int,
    val bootstrappedCloud: Boolean,
    val completedAtEpochMillis: Long,
) {
    val changed: Int get() = uploaded + downloaded
}
