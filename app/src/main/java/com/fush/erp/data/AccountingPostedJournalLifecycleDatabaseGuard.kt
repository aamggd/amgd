package com.fush.erp.data

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Final DB boundary for journal lifecycle and posted-line immutability.
 * Operational journals must be built as STAGING, receive their complete line batch,
 * pass exact scaled debit/credit balance, then transition to POSTED.
 */
object AccountingPostedJournalLifecycleDatabaseGuard {
    fun install(db: SupportSQLiteDatabase) {
        listOf(
            "trg_operational_journal_initial_staging",
            "trg_operational_journal_status_transition",
            "trg_journal_post_requires_balanced_lines",
            "trg_posted_journal_line_no_insert",
            "trg_posted_journal_line_no_update",
            "trg_posted_journal_line_no_delete",
            "trg_posted_journal_no_update",
            "trg_posted_journal_no_delete"
        ).forEach { db.execSQL("DROP TRIGGER IF EXISTS $it") }

        db.execSQL(
            """
            CREATE TRIGGER trg_operational_journal_initial_staging
            BEFORE INSERT ON journal_entries
            WHEN UPPER(TRIM(NEW.sourceType)) <> 'MANUAL'
              AND UPPER(TRIM(NEW.status)) <> 'STAGING'
            BEGIN
                SELECT RAISE(ABORT, 'OPERATIONAL_JOURNAL_MUST_START_STAGING');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER trg_operational_journal_status_transition
            BEFORE UPDATE OF status ON journal_entries
            WHEN UPPER(TRIM(OLD.sourceType)) <> 'MANUAL'
              AND NEW.status <> OLD.status
              AND NOT (OLD.status = 'STAGING' AND NEW.status = 'POSTED')
            BEGIN
                SELECT RAISE(ABORT, 'INVALID_OPERATIONAL_JOURNAL_STATUS_TRANSITION');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER trg_journal_post_requires_balanced_lines
            BEFORE UPDATE OF status ON journal_entries
            WHEN OLD.status <> 'POSTED' AND NEW.status = 'POSTED'
              AND (
                    (SELECT COUNT(*) FROM journal_lines jl WHERE jl.entryId = NEW.id) < 2
                 OR COALESCE((SELECT SUM(jl.debitScaled) FROM journal_lines jl WHERE jl.entryId = NEW.id), 0) <= 0
                 OR COALESCE((SELECT SUM(jl.creditScaled) FROM journal_lines jl WHERE jl.entryId = NEW.id), 0) <= 0
                 OR COALESCE((SELECT SUM(jl.debitScaled) FROM journal_lines jl WHERE jl.entryId = NEW.id), 0)
                    <> COALESCE((SELECT SUM(jl.creditScaled) FROM journal_lines jl WHERE jl.entryId = NEW.id), 0)
              )
            BEGIN
                SELECT RAISE(ABORT, 'JOURNAL_NOT_BALANCED_CANNOT_POST');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER trg_posted_journal_line_no_insert
            BEFORE INSERT ON journal_lines
            WHEN EXISTS (
                SELECT 1 FROM journal_entries je
                WHERE je.id = NEW.entryId AND je.status = 'POSTED'
            )
            BEGIN
                SELECT RAISE(ABORT, 'POSTED_JOURNAL_LINE_IMMUTABLE_USE_REVERSAL');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER trg_posted_journal_line_no_update
            BEFORE UPDATE ON journal_lines
            WHEN EXISTS (SELECT 1 FROM journal_entries je WHERE je.id = OLD.entryId AND je.status = 'POSTED')
               OR EXISTS (SELECT 1 FROM journal_entries je WHERE je.id = NEW.entryId AND je.status = 'POSTED')
            BEGIN
                SELECT RAISE(ABORT, 'POSTED_JOURNAL_LINE_IMMUTABLE_USE_REVERSAL');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER trg_posted_journal_line_no_delete
            BEFORE DELETE ON journal_lines
            WHEN EXISTS (SELECT 1 FROM journal_entries je WHERE je.id = OLD.entryId AND je.status = 'POSTED')
            BEGIN
                SELECT RAISE(ABORT, 'POSTED_JOURNAL_LINE_IMMUTABLE_USE_REVERSAL');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER trg_posted_journal_no_update
            BEFORE UPDATE ON journal_entries
            WHEN OLD.status = 'POSTED'
            BEGIN
                SELECT RAISE(ABORT, 'POSTED_JOURNAL_IMMUTABLE_USE_REVERSAL');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER trg_posted_journal_no_delete
            BEFORE DELETE ON journal_entries
            WHEN OLD.status = 'POSTED'
            BEGIN
                SELECT RAISE(ABORT, 'POSTED_JOURNAL_IMMUTABLE_USE_REVERSAL');
            END
            """.trimIndent()
        )
    }
}
