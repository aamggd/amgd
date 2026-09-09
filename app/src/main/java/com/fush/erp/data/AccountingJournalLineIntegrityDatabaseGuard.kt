package com.fush.erp.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** DB-level enforcement of Debit XOR Credit for every journal line. */
object AccountingJournalLineIntegrityDatabaseGuard {
    fun install(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_journal_lines_debit_credit_xor_insert
            BEFORE INSERT ON journal_lines
            WHEN NOT (
                (NEW.debitScaled > 0 AND NEW.creditScaled = 0)
                OR (NEW.creditScaled > 0 AND NEW.debitScaled = 0)
            )
            BEGIN
                SELECT RAISE(ABORT, 'INVALID_JOURNAL_LINE_DEBIT_CREDIT');
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_journal_lines_debit_credit_xor_update
            BEFORE UPDATE OF debitScaled, creditScaled ON journal_lines
            WHEN NOT (
                (NEW.debitScaled > 0 AND NEW.creditScaled = 0)
                OR (NEW.creditScaled > 0 AND NEW.debitScaled = 0)
            )
            BEGIN
                SELECT RAISE(ABORT, 'INVALID_JOURNAL_LINE_DEBIT_CREDIT');
            END
            """.trimIndent()
        )
    }
}
