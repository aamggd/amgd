package com.fush.erp.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** Single accounting database-integrity initialization point for application cold open. */
object AccountingDatabaseGuardInitializer {
    fun initializeBeforeExposure(db: FushDatabase): FushDatabase {
        installAll(db.openHelper.writableDatabase)
        return db
    }

    fun installAll(db: SupportSQLiteDatabase) {
        AccountingIdempotencyDatabaseGuards.install(db)
        AccountingPeriodDatabaseGuard.install(db)
        AccountingJournalLineIntegrityDatabaseGuard.install(db)
        AccountingJournalApprovalDatabaseGuard.install(db)
        AccountingPostedJournalLifecycleDatabaseGuard.install(db)
        installJournalLineSemanticInsertTrigger(db)
        installInventoryCostLayerSupport(db)
        installSupportImmutableGuards(db)
    }
}
