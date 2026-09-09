package com.fush.erp.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.fush.erp.domain.AccountingPostingIdempotencyPolicy
import com.fush.erp.domain.CloudPostedJournalHydrationPolicy
import com.fush.erp.domain.LegacyTreasuryCloudHydrationPolicy

/** Central SQLite guards for accounting-event identity and duplicate posting. */
object AccountingIdempotencyDatabaseGuards {
    fun install(db: SupportSQLiteDatabase) {
        val registered = sqlStringList(AccountingPostingIdempotencyPolicy.registeredSourceTypes)
        val replaySafe = sqlStringList(AccountingPostingIdempotencyPolicy.replaySafeSourceTypes)
        val unstable = sqlStringList(AccountingPostingIdempotencyPolicy.blockedUnstableSourceTypes)
        val cloudHydrationAlias = CloudPostedJournalHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE
        val cloudHydrationPrefix = CloudPostedJournalHydrationPolicy.STAGING_SOURCE_ID_PREFIX
        val businessRegistered = sqlStringList(
            AccountingPostingIdempotencyPolicy.registeredSourceTypes - setOf(
                cloudHydrationAlias,
                LegacyTreasuryCloudHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE,
            )
        )

        // These trigger bodies embed policy-derived source lists. Rebuild them on every cold open
        // so an application upgrade cannot leave a stale fail-closed policy in an existing DB.
        listOf(
            "trg_journal_source_registered_insert",
            "trg_journal_source_id_required_insert",
            "trg_journal_unstable_source_blocked_insert",
            "trg_journal_replay_safe_unique_insert",
            "trg_journal_replay_safe_unique_update",
            "trg_cloud_legacy_treasury_hydration_insert_shape",
            "trg_cloud_legacy_treasury_hydration_finalize_shape",
            "trg_cloud_legacy_treasury_hydration_never_posted",
            "trg_cloud_posted_journal_hydration_insert_shape",
            "trg_cloud_posted_journal_hydration_finalize_shape",
            "trg_cloud_posted_journal_hydration_never_posted"
        ).forEach { trigger -> db.execSQL("DROP TRIGGER IF EXISTS $trigger") }

        db.execSQL(
            """
            CREATE TRIGGER trg_journal_source_registered_insert
            BEFORE INSERT ON journal_entries
            WHEN UPPER(TRIM(NEW.sourceType)) NOT IN ($registered)
            BEGIN
                SELECT RAISE(ABORT, 'ACCOUNTING_EVENT_SOURCE_TYPE_NOT_REGISTERED');
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER trg_journal_source_id_required_insert
            BEFORE INSERT ON journal_entries
            WHEN UPPER(TRIM(NEW.sourceType)) <> 'MANUAL'
              AND (NEW.sourceId IS NULL OR LENGTH(TRIM(NEW.sourceId)) = 0)
            BEGIN
                SELECT RAISE(ABORT, 'ACCOUNTING_SOURCE_ID_REQUIRED');
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER trg_journal_unstable_source_blocked_insert
            BEFORE INSERT ON journal_entries
            WHEN UPPER(TRIM(NEW.sourceType)) IN ($unstable)
            BEGIN
                SELECT RAISE(ABORT, 'ACCOUNTING_SOURCE_REQUIRES_STABLE_EVENT_ID');
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER trg_cloud_legacy_treasury_hydration_insert_shape
            BEFORE INSERT ON journal_entries
            WHEN UPPER(TRIM(NEW.sourceType)) = 'CLOUD_LEGACY_TREASURY_HYDRATION'
              AND (
                    UPPER(TRIM(NEW.status)) <> 'STAGING'
                 OR NEW.sourceId IS NULL
                 OR LOWER(TRIM(NEW.sourceId)) NOT LIKE 'cloud-legacy:%'
              )
            BEGIN
                SELECT RAISE(ABORT, 'CLOUD_LEGACY_TREASURY_HYDRATION_INVALID');
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER trg_cloud_legacy_treasury_hydration_finalize_shape
            BEFORE UPDATE OF status, sourceType, sourceId ON journal_entries
            WHEN UPPER(TRIM(OLD.sourceType)) = 'CLOUD_LEGACY_TREASURY_HYDRATION'
              AND (
                    UPPER(TRIM(OLD.status)) <> 'STAGING'
                 OR UPPER(TRIM(NEW.status)) <> 'POSTED'
                 OR UPPER(TRIM(NEW.sourceType)) NOT IN (
                        'TREASURY_TRANSFER',
                        'TREASURY_RECEIPT',
                        'TREASURY_PAYMENT',
                        'TREASURY_EXPENSE',
                        'TREASURY_INCOME'
                    )
                 OR NEW.sourceId <> OLD.sourceId
                 OR LOWER(TRIM(COALESCE(NEW.sourceId, ''))) NOT LIKE 'cloud-legacy:%'
              )
            BEGIN
                SELECT RAISE(ABORT, 'CLOUD_LEGACY_TREASURY_HYDRATION_FINALIZE_INVALID');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER trg_cloud_legacy_treasury_hydration_never_posted
            BEFORE UPDATE OF status, sourceType ON journal_entries
            WHEN UPPER(TRIM(NEW.sourceType)) = 'CLOUD_LEGACY_TREASURY_HYDRATION'
              AND UPPER(TRIM(NEW.status)) = 'POSTED'
            BEGIN
                SELECT RAISE(ABORT, 'CLOUD_LEGACY_TREASURY_HYDRATION_ALIAS_MUST_NOT_POST');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER trg_cloud_posted_journal_hydration_insert_shape
            BEFORE INSERT ON journal_entries
            WHEN UPPER(TRIM(NEW.sourceType)) = '$cloudHydrationAlias'
              AND (
                    UPPER(TRIM(NEW.status)) <> 'STAGING'
                 OR NEW.sourceId IS NULL
                 OR LOWER(TRIM(NEW.sourceId)) NOT LIKE '${cloudHydrationPrefix.lowercase()}%'
              )
            BEGIN
                SELECT RAISE(ABORT, 'CLOUD_POSTED_JOURNAL_HYDRATION_INVALID');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER trg_cloud_posted_journal_hydration_finalize_shape
            BEFORE UPDATE OF status, sourceType, sourceId ON journal_entries
            WHEN UPPER(TRIM(OLD.sourceType)) = '$cloudHydrationAlias'
              AND (
                    UPPER(TRIM(OLD.status)) <> 'STAGING'
                 OR UPPER(TRIM(NEW.status)) <> 'POSTED'
                 OR UPPER(TRIM(NEW.sourceType)) NOT IN ($businessRegistered)
                 OR (
                        UPPER(TRIM(NEW.sourceType)) <> 'MANUAL'
                    AND (NEW.sourceId IS NULL OR LENGTH(TRIM(NEW.sourceId)) = 0)
                 )
              )
            BEGIN
                SELECT RAISE(ABORT, 'CLOUD_POSTED_JOURNAL_HYDRATION_FINALIZE_INVALID');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER trg_cloud_posted_journal_hydration_never_posted
            BEFORE UPDATE OF status, sourceType ON journal_entries
            WHEN UPPER(TRIM(NEW.sourceType)) = '$cloudHydrationAlias'
              AND UPPER(TRIM(NEW.status)) = 'POSTED'
            BEGIN
                SELECT RAISE(ABORT, 'CLOUD_POSTED_JOURNAL_HYDRATION_ALIAS_MUST_NOT_POST');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER trg_journal_replay_safe_unique_insert
            BEFORE INSERT ON journal_entries
            WHEN NEW.status = 'POSTED'
              AND UPPER(TRIM(NEW.sourceType)) IN ($replaySafe)
              AND EXISTS (
                  SELECT 1 FROM journal_entries
                  WHERE status = 'POSTED'
                    AND UPPER(TRIM(sourceType)) = UPPER(TRIM(NEW.sourceType))
                    AND TRIM(sourceId) = TRIM(NEW.sourceId)
              )
            BEGIN
                SELECT RAISE(ABORT, 'DUPLICATE_ACCOUNTING_SOURCE');
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TRIGGER trg_journal_replay_safe_unique_update
            BEFORE UPDATE OF status, sourceType, sourceId ON journal_entries
            WHEN NEW.status = 'POSTED'
              AND UPPER(TRIM(NEW.sourceType)) IN ($replaySafe)
              AND EXISTS (
                  SELECT 1 FROM journal_entries
                  WHERE id <> NEW.id
                    AND status = 'POSTED'
                    AND UPPER(TRIM(sourceType)) = UPPER(TRIM(NEW.sourceType))
                    AND TRIM(sourceId) = TRIM(NEW.sourceId)
              )
            BEGIN
                SELECT RAISE(ABORT, 'DUPLICATE_ACCOUNTING_SOURCE');
            END
            """.trimIndent()
        )
    }

    private fun sqlStringList(values: Set<String>): String =
        values.joinToString(",") { "'${it.replace("'", "''")}'" }
}
