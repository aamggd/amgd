package com.fush.erp.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.fush.erp.domain.CloudPostedJournalHydrationPolicy

/**
 * Fail-closed accounting-period enforcement for locally created/posted journals.
 *
 * An already-POSTED cloud journal is an immutable accounting fact created on another trusted
 * device. Replicating it to a second device must not be treated as a new local posting merely
 * because that device has already closed the historical period. Cloud hydration therefore uses
 * one internal STAGING-only alias and is exempted only while that exact envelope is inserted and
 * atomically finalized. Every ordinary local insert/post remains blocked in a closed period.
 *
 * IMPORTANT: old installations also carry trg_journal_entries_closed_period from migration 27.
 * That legacy trigger blocks *all* inserts in closed periods, including STAGING. It must be rebuilt
 * on every cold open together with the newer open-period guards, otherwise cloud hydration can
 * never reach the finalization step on upgraded databases.
 */
object AccountingPeriodDatabaseGuard {
    private const val LEGACY_CLOUD_ALIAS = "CLOUD_LEGACY_TREASURY_HYDRATION"
    private const val LEGACY_CLOUD_PREFIX = "cloud-legacy:"
    private const val CLOUD_POSTED_ALIAS = CloudPostedJournalHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE
    private const val CLOUD_POSTED_PREFIX = CloudPostedJournalHydrationPolicy.STAGING_SOURCE_ID_PREFIX

    fun install(db: SupportSQLiteDatabase) {
        // Rebuild all period-sensitive journal triggers on every cold open. IF NOT EXISTS would
        // otherwise preserve stale bodies from earlier APKs after an in-place app update.
        listOf(
            "trg_journal_entries_closed_period",
            "trg_journal_entries_open_period_insert",
            "trg_journal_entries_open_period_update",
        ).forEach { db.execSQL("DROP TRIGGER IF EXISTS $it") }

        // Historical migration guard: keep its strict behavior for every local journal, but allow
        // the two internal cloud STAGING envelopes to be materialized before atomic finalization.
        db.execSQL(
            """
            CREATE TRIGGER trg_journal_entries_closed_period
            BEFORE INSERT ON journal_entries
            WHEN EXISTS (
                    SELECT 1
                    FROM accounting_periods ap
                    WHERE NEW.entryDate BETWEEN ap.startDate AND ap.endDate
                      AND ap.status <> 'OPEN'
              )
              AND NOT (
                    UPPER(TRIM(COALESCE(NEW.status, ''))) = 'STAGING'
                AND UPPER(TRIM(COALESCE(NEW.sourceType, ''))) = '$CLOUD_POSTED_ALIAS'
                AND LOWER(TRIM(COALESCE(NEW.sourceId, ''))) LIKE '${CLOUD_POSTED_PREFIX.lowercase()}%'
              )
              AND NOT (
                    UPPER(TRIM(COALESCE(NEW.status, ''))) = 'STAGING'
                AND UPPER(TRIM(COALESCE(NEW.sourceType, ''))) = '$LEGACY_CLOUD_ALIAS'
                AND LOWER(TRIM(COALESCE(NEW.sourceId, ''))) LIKE '${LEGACY_CLOUD_PREFIX.lowercase()}%'
              )
            BEGIN
                SELECT RAISE(ABORT, 'الفترة المحاسبية مقفلة');
            END
            """.trimIndent()
        )

        // Direct POSTED inserts are never a valid cloud hydration path. Cloud rows must start as
        // STAGING under the internal envelope and receive their complete balanced line batch.
        db.execSQL(
            """
            CREATE TRIGGER trg_journal_entries_open_period_insert
            BEFORE INSERT ON journal_entries
            WHEN UPPER(TRIM(COALESCE(NEW.status, 'POSTED'))) = 'POSTED'
              AND NOT EXISTS(
                    SELECT 1
                    FROM accounting_periods ap
                    WHERE NEW.entryDate BETWEEN ap.startDate AND ap.endDate
                      AND ap.status = 'OPEN'
              )
            BEGIN
                SELECT RAISE(ABORT, 'ACCOUNTING_PERIOD_NOT_OPEN');
            END
            """.trimIndent()
        )

        // The sole generic closed-period posting exception is the atomic transition of the
        // internal cloud envelope from STAGING to POSTED while restoring the original source.
        // The legacy TREASURY envelope remains accepted only for upgrade compatibility with a
        // partially hydrated row left by v188-v195.
        db.execSQL(
            """
            CREATE TRIGGER trg_journal_entries_open_period_update
            BEFORE UPDATE OF status, entryDate, sourceType, sourceId ON journal_entries
            WHEN UPPER(TRIM(COALESCE(NEW.status, ''))) = 'POSTED'
              AND (
                    UPPER(TRIM(COALESCE(OLD.status, ''))) <> 'POSTED'
                 OR NEW.entryDate <> OLD.entryDate
              )
              AND NOT (
                    UPPER(TRIM(COALESCE(OLD.status, ''))) = 'STAGING'
                AND UPPER(TRIM(COALESCE(OLD.sourceType, ''))) = '$CLOUD_POSTED_ALIAS'
                AND LOWER(TRIM(COALESCE(OLD.sourceId, ''))) LIKE '${CLOUD_POSTED_PREFIX.lowercase()}%'
                AND UPPER(TRIM(COALESCE(NEW.status, ''))) = 'POSTED'
                AND UPPER(TRIM(COALESCE(NEW.sourceType, ''))) <> '$CLOUD_POSTED_ALIAS'
                AND NEW.entryDate = OLD.entryDate
              )
              AND NOT (
                    UPPER(TRIM(COALESCE(OLD.status, ''))) = 'STAGING'
                AND UPPER(TRIM(COALESCE(OLD.sourceType, ''))) = '$LEGACY_CLOUD_ALIAS'
                AND UPPER(TRIM(COALESCE(NEW.sourceType, ''))) IN (
                    'TREASURY_TRANSFER',
                    'TREASURY_RECEIPT',
                    'TREASURY_PAYMENT',
                    'TREASURY_EXPENSE',
                    'TREASURY_INCOME'
                )
                AND NEW.sourceId = OLD.sourceId
                AND LOWER(TRIM(COALESCE(NEW.sourceId, ''))) LIKE '${LEGACY_CLOUD_PREFIX.lowercase()}%'
              )
              AND NOT EXISTS(
                    SELECT 1
                    FROM accounting_periods ap
                    WHERE NEW.entryDate BETWEEN ap.startDate AND ap.endDate
                      AND ap.status = 'OPEN'
              )
            BEGIN
                SELECT RAISE(ABORT, 'ACCOUNTING_PERIOD_NOT_OPEN');
            END
            """.trimIndent()
        )
    }
}
