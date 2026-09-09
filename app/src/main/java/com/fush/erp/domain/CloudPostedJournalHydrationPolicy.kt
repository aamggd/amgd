package com.fush.erp.domain

/**
 * Internal-only envelope used while reproducing an already-POSTED cloud journal locally.
 *
 * A cloud journal is an immutable accounting fact that was posted on another trusted device.
 * Replaying that fact on a second device must still pass the normal STAGING + balanced-lines
 * lifecycle, but it must not be mistaken for a brand-new local posting merely because the local
 * accounting period has since been closed.
 *
 * The alias is never a business source type and is never allowed to remain POSTED. The engine
 * inserts STAGING under this alias, writes the full balanced line batch, then atomically restores
 * the original source type/source id while transitioning to POSTED.
 */
object CloudPostedJournalHydrationPolicy {
    const val STAGING_ALIAS_SOURCE_TYPE = "CLOUD_POSTED_JOURNAL_HYDRATION"
    const val STAGING_SOURCE_ID_PREFIX = "cloud-hydration:"

    fun stagingSourceId(entryNo: String): String {
        val normalized = entryNo.trim().uppercase()
        require(normalized.isNotBlank()) { "CLOUD_HYDRATION_ENTRY_NO_REQUIRED" }
        return "$STAGING_SOURCE_ID_PREFIX$normalized"
    }
}
