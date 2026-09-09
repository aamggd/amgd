package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_35_36_ACCOUNTING_PRECISION = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE journal_entries ADD COLUMN exchangeRateScaled INTEGER NOT NULL DEFAULT 100000000")
        db.execSQL("ALTER TABLE journal_lines ADD COLUMN debitScaled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE journal_lines ADD COLUMN creditScaled INTEGER NOT NULL DEFAULT 0")
        AccountingPrecisionAutoMigration().onPostMigrate(db)
    }
}

val MIGRATION_36_37_JOURNAL_LINE_SEMANTICS = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `journal_line_semantics` (
                `journalLineId` INTEGER NOT NULL,
                `lineNo` INTEGER NOT NULL,
                `functionalDebitScaled` INTEGER NOT NULL,
                `functionalCreditScaled` INTEGER NOT NULL,
                `transactionCurrencyCode` TEXT NOT NULL,
                `transactionDebitScaled` INTEGER NOT NULL,
                `transactionCreditScaled` INTEGER NOT NULL,
                `transactionExchangeRateScaled` INTEGER NOT NULL,
                `branchCode` TEXT NOT NULL,
                `costCenterCode` TEXT NOT NULL,
                `projectCode` TEXT NOT NULL,
                `partyType` TEXT NOT NULL,
                `partyId` INTEGER,
                `sourceReferenceType` TEXT NOT NULL,
                `sourceReferenceId` TEXT NOT NULL,
                PRIMARY KEY(`journalLineId`),
                FOREIGN KEY(`journalLineId`) REFERENCES `journal_lines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_line_semantics_transactionCurrencyCode` ON `journal_line_semantics` (`transactionCurrencyCode`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_line_semantics_branchCode` ON `journal_line_semantics` (`branchCode`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_line_semantics_costCenterCode` ON `journal_line_semantics` (`costCenterCode`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_line_semantics_projectCode` ON `journal_line_semantics` (`projectCode`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_journal_line_semantics_partyType_partyId` ON `journal_line_semantics` (`partyType`, `partyId`)")
        db.execSQL("""
            INSERT OR IGNORE INTO journal_line_semantics(
                journalLineId,lineNo,functionalDebitScaled,functionalCreditScaled,
                transactionCurrencyCode,transactionDebitScaled,transactionCreditScaled,transactionExchangeRateScaled,
                branchCode,costCenterCode,projectCode,partyType,partyId,sourceReferenceType,sourceReferenceId
            )
            SELECT jl.id,
                   (SELECT COUNT(*) FROM journal_lines prior WHERE prior.entryId=jl.entryId AND prior.id<=jl.id),
                   jl.debitScaled,jl.creditScaled,
                   'FUNCTIONAL',jl.debitScaled,jl.creditScaled,100000000,
                   '','','','NONE',NULL,COALESCE(je.sourceType,''),COALESCE(je.sourceId,'')
            FROM journal_lines jl JOIN journal_entries je ON je.id=jl.entryId
        """.trimIndent())
        installJournalLineSemanticInsertTrigger(db)
    }
}

fun installJournalLineSemanticInsertTrigger(db: SupportSQLiteDatabase) {
    db.execSQL("""
        CREATE TRIGGER IF NOT EXISTS trg_journal_line_semantics_autocreate
        AFTER INSERT ON journal_lines
        BEGIN
            INSERT OR IGNORE INTO journal_line_semantics(
                journalLineId,lineNo,functionalDebitScaled,functionalCreditScaled,
                transactionCurrencyCode,transactionDebitScaled,transactionCreditScaled,transactionExchangeRateScaled,
                branchCode,costCenterCode,projectCode,partyType,partyId,sourceReferenceType,sourceReferenceId
            ) VALUES(
                NEW.id,
                (SELECT COUNT(*) FROM journal_lines prior WHERE prior.entryId=NEW.entryId AND prior.id<=NEW.id),
                NEW.debitScaled,NEW.creditScaled,
                'FUNCTIONAL',NEW.debitScaled,NEW.creditScaled,100000000,
                '','','','NONE',NULL,
                COALESCE((SELECT sourceType FROM journal_entries WHERE id=NEW.entryId),''),
                COALESCE((SELECT sourceId FROM journal_entries WHERE id=NEW.entryId),'')
            );
        END
    """.trimIndent())
}
