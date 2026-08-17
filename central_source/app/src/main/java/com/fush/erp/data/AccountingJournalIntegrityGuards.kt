package com.fush.erp.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * AE-ACC-011: one idempotent journal-integrity guard set for both fresh Room 35
 * databases and databases upgraded through 34 -> 35.
 *
 * This installer is DDL-only: it creates triggers and never mutates user rows.
 */
internal object AccountingJournalIntegrityGuards {
    private val stableSourcesSql = """
        'CASH_COUNT_ADJUSTMENT',
        'FX_REVALUATION',
        'SALE',
        'CUSTOMER_RECEIPT',
        'SALES_RETURN',
        'PURCHASE',
        'PURCHASE_RETURN',
        'SUPPLIER_PAYMENT',
        'INVENTORY_COUNT',
        'PRODUCTION_ISSUE',
        'PRODUCTION_LABOR',
        'PRODUCTION_RECEIPT',
        'PRODUCTION_REJECT'
    """.trimIndent()

    internal val triggerSql: List<String>
        get() = listOf(
            """
                CREATE TRIGGER IF NOT EXISTS trg_journal_entries_closed_period
                BEFORE INSERT ON journal_entries
                WHEN EXISTS (
                    SELECT 1
                    FROM accounting_periods ap
                    WHERE NEW.entryDate BETWEEN ap.startDate AND ap.endDate
                      AND ap.status <> 'OPEN'
                )
                BEGIN
                    SELECT RAISE(ABORT, 'الفترة المحاسبية مقفلة');
                END
            """.trimIndent(),
            """
                CREATE TRIGGER IF NOT EXISTS trg_journal_stable_source_id_required_insert
                BEFORE INSERT ON journal_entries
                WHEN NEW.status = 'POSTED'
                  AND NEW.sourceType IN ($stableSourcesSql)
                  AND (NEW.sourceId IS NULL OR TRIM(NEW.sourceId) = '')
                BEGIN
                    SELECT RAISE(ABORT, 'ACCOUNTING_STABLE_SOURCE_ID_REQUIRED');
                END
            """.trimIndent(),
            """
                CREATE TRIGGER IF NOT EXISTS trg_journal_no_duplicate_stable_source_insert
                BEFORE INSERT ON journal_entries
                WHEN NEW.status = 'POSTED'
                  AND NEW.sourceType IN ($stableSourcesSql)
                  AND NEW.sourceId IS NOT NULL
                  AND TRIM(NEW.sourceId) <> ''
                  AND EXISTS (
                      SELECT 1 FROM journal_entries existing
                      WHERE existing.status = 'POSTED'
                        AND existing.sourceType = NEW.sourceType
                        AND existing.sourceId = NEW.sourceId
                  )
                BEGIN
                    SELECT RAISE(ABORT, 'DUPLICATE_ACCOUNTING_POSTING');
                END
            """.trimIndent(),
            """
                CREATE TRIGGER IF NOT EXISTS trg_journal_no_duplicate_stable_source_update
                BEFORE UPDATE OF status, sourceType, sourceId ON journal_entries
                WHEN OLD.status <> 'POSTED'
                  AND NEW.status = 'POSTED'
                  AND NEW.sourceType IN ($stableSourcesSql)
                  AND NEW.sourceId IS NOT NULL
                  AND TRIM(NEW.sourceId) <> ''
                  AND EXISTS (
                      SELECT 1 FROM journal_entries existing
                      WHERE existing.id <> NEW.id
                        AND existing.status = 'POSTED'
                        AND existing.sourceType = NEW.sourceType
                        AND existing.sourceId = NEW.sourceId
                  )
                BEGIN
                    SELECT RAISE(ABORT, 'DUPLICATE_ACCOUNTING_POSTING');
                END
            """.trimIndent(),
            """
                CREATE TRIGGER IF NOT EXISTS trg_posted_journal_no_update
                BEFORE UPDATE ON journal_entries
                WHEN OLD.status = 'POSTED'
                BEGIN
                    SELECT RAISE(ABORT, 'POSTED_JOURNAL_IMMUTABLE_USE_REVERSAL');
                END
            """.trimIndent(),
            """
                CREATE TRIGGER IF NOT EXISTS trg_posted_journal_no_delete
                BEFORE DELETE ON journal_entries
                WHEN OLD.status = 'POSTED'
                BEGIN
                    SELECT RAISE(ABORT, 'POSTED_JOURNAL_IMMUTABLE_USE_REVERSAL');
                END
            """.trimIndent(),
            """
                CREATE TRIGGER IF NOT EXISTS trg_journal_line_sanity_insert
                BEFORE INSERT ON journal_lines
                WHEN NEW.debit < 0 OR NEW.credit < 0 OR (NEW.debit > 0 AND NEW.credit > 0)
                BEGIN
                    SELECT RAISE(ABORT, 'INVALID_JOURNAL_LINE');
                END
            """.trimIndent(),
            """
                CREATE TRIGGER IF NOT EXISTS trg_posted_journal_line_no_update
                BEFORE UPDATE ON journal_lines
                WHEN EXISTS (
                    SELECT 1 FROM journal_entries je
                    WHERE je.id = OLD.entryId AND je.status = 'POSTED'
                )
                BEGIN
                    SELECT RAISE(ABORT, 'POSTED_JOURNAL_LINE_IMMUTABLE_USE_REVERSAL');
                END
            """.trimIndent(),
            """
                CREATE TRIGGER IF NOT EXISTS trg_posted_journal_line_no_delete
                BEFORE DELETE ON journal_lines
                WHEN EXISTS (
                    SELECT 1 FROM journal_entries je
                    WHERE je.id = OLD.entryId AND je.status = 'POSTED'
                )
                BEGIN
                    SELECT RAISE(ABORT, 'POSTED_JOURNAL_LINE_IMMUTABLE_USE_REVERSAL');
                END
            """.trimIndent()
        )

    fun install(db: SupportSQLiteDatabase) {
        triggerSql.forEach(db::execSQL)
    }

    val callback: RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            install(db)
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            install(db)
        }
    }
}
