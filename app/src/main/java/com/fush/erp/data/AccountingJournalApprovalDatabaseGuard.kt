package com.fush.erp.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.fush.erp.domain.CloudPostedJournalHydrationPolicy

/**
 * AE-ACC-022 database enforcement for the manual-journal approval lifecycle.
 *
 * Manual journals are the only source type governed here. Operational journals keep their
 * existing posting lifecycle. The database requires DRAFT -> SUBMITTED -> APPROVED -> POSTED,
 * creates one generic approval request at submission, enforces maker != approver, and rechecks
 * the accounting period immediately before POSTED.
 */
object AccountingJournalApprovalDatabaseGuard {
    fun install(db: SupportSQLiteDatabase) {
        val cloudAlias = CloudPostedJournalHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE
        val cloudPrefix = CloudPostedJournalHydrationPolicy.STAGING_SOURCE_ID_PREFIX
        // v196 changes two manual-journal trigger bodies. Rebuild them on every cold open so an
        // update over an existing database cannot retain the v195 IF-NOT-EXISTS definitions.
        listOf(
            "trg_manual_journal_status_transition",
            "trg_manual_journal_open_period_post",
        ).forEach { db.execSQL("DROP TRIGGER IF EXISTS $it") }
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_manual_journal_initial_draft
            BEFORE INSERT ON journal_entries
            WHEN UPPER(TRIM(NEW.sourceType)) = 'MANUAL'
              AND NEW.status <> 'DRAFT'
            BEGIN
                SELECT RAISE(ABORT, 'MANUAL_JOURNAL_MUST_START_DRAFT');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER trg_manual_journal_status_transition
            BEFORE UPDATE OF status ON journal_entries
            WHEN UPPER(TRIM(NEW.sourceType)) = 'MANUAL'
              AND NEW.status <> OLD.status
              AND NOT (
                    UPPER(TRIM(COALESCE(OLD.sourceType, ''))) = '$cloudAlias'
                AND UPPER(TRIM(COALESCE(OLD.status, ''))) = 'STAGING'
                AND LOWER(TRIM(COALESCE(OLD.sourceId, ''))) LIKE '${cloudPrefix.lowercase()}%'
                AND UPPER(TRIM(COALESCE(NEW.status, ''))) = 'POSTED'
                AND NEW.entryDate = OLD.entryDate
              )
              AND NOT (
                    (OLD.status = 'DRAFT' AND NEW.status = 'SUBMITTED')
                 OR (OLD.status = 'SUBMITTED' AND NEW.status = 'APPROVED')
                 OR (OLD.status = 'APPROVED' AND NEW.status = 'POSTED')
                 OR (OLD.status = 'SUBMITTED' AND NEW.status = 'REJECTED')
              )
            BEGIN
                SELECT RAISE(ABORT, 'INVALID_MANUAL_JOURNAL_STATUS_TRANSITION');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_manual_journal_submit
            AFTER INSERT ON journal_entries
            WHEN UPPER(TRIM(NEW.sourceType)) = 'MANUAL'
              AND NEW.status = 'DRAFT'
            BEGIN
                INSERT INTO approval_requests(
                    referenceType, referenceId, title, requestedRole, requestedBy,
                    status, decisionBy, decisionAt, decisionNote, requestedAt
                ) VALUES(
                    'ACCOUNTING_JOURNAL', CAST(NEW.id AS TEXT),
                    'اعتماد قيد محاسبي ' || NEW.entryNo,
                    'ADMIN', NEW.createdBy,
                    'PENDING', NULL, NULL, '', NEW.createdAt
                );

                UPDATE journal_entries
                SET status = 'SUBMITTED'
                WHERE id = NEW.id;

                INSERT INTO audit_events(
                    eventAt, userId, action, entityType, entityId,
                    oldValue, newValue, reason, deviceInfo
                ) VALUES(
                    NEW.createdAt, NEW.createdBy, 'SUBMIT', 'JOURNAL_ENTRY', CAST(NEW.id AS TEXT),
                    'DRAFT', 'SUBMITTED', 'Manual journal approval required', 'ANDROID'
                );
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_accounting_approval_sod
            BEFORE UPDATE OF status, decisionBy ON approval_requests
            WHEN NEW.referenceType = 'ACCOUNTING_JOURNAL'
              AND NEW.status = 'APPROVED'
              AND (NEW.decisionBy IS NULL OR NEW.decisionBy = NEW.requestedBy)
            BEGIN
                SELECT RAISE(ABORT, 'ACCOUNTING_APPROVER_MUST_DIFFER_FROM_MAKER');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_accounting_approval_reference
            BEFORE UPDATE OF status ON approval_requests
            WHEN NEW.referenceType = 'ACCOUNTING_JOURNAL'
              AND NEW.status IN ('APPROVED', 'REJECTED')
              AND NOT EXISTS(
                    SELECT 1
                    FROM journal_entries je
                    WHERE je.id = CAST(NEW.referenceId AS INTEGER)
                      AND UPPER(TRIM(je.sourceType)) = 'MANUAL'
                      AND je.status = 'SUBMITTED'
              )
            BEGIN
                SELECT RAISE(ABORT, 'ACCOUNTING_APPROVAL_JOURNAL_NOT_SUBMITTED');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_accounting_approval_open_period
            BEFORE UPDATE OF status ON approval_requests
            WHEN NEW.referenceType = 'ACCOUNTING_JOURNAL'
              AND OLD.status = 'PENDING'
              AND NEW.status = 'APPROVED'
              AND NOT EXISTS(
                    SELECT 1
                    FROM journal_entries je
                    JOIN accounting_periods ap
                      ON je.entryDate BETWEEN ap.startDate AND ap.endDate
                    WHERE je.id = CAST(NEW.referenceId AS INTEGER)
                      AND ap.status = 'OPEN'
              )
            BEGIN
                SELECT RAISE(ABORT, 'ACCOUNTING_PERIOD_NOT_OPEN');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_manual_journal_approval_evidence
            BEFORE UPDATE OF status ON journal_entries
            WHEN UPPER(TRIM(NEW.sourceType)) = 'MANUAL'
              AND NEW.status = 'APPROVED'
              AND NOT EXISTS(
                    SELECT 1
                    FROM approval_requests ar
                    WHERE ar.referenceType = 'ACCOUNTING_JOURNAL'
                      AND ar.referenceId = CAST(NEW.id AS TEXT)
                      AND ar.status = 'APPROVED'
                      AND ar.decisionBy IS NOT NULL
                      AND ar.decisionBy <> ar.requestedBy
              )
            BEGIN
                SELECT RAISE(ABORT, 'ACCOUNTING_APPROVAL_REQUIRED');
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER trg_manual_journal_open_period_post
            BEFORE UPDATE OF status ON journal_entries
            WHEN UPPER(TRIM(NEW.sourceType)) = 'MANUAL'
              AND NEW.status = 'POSTED'
              AND NOT (
                    UPPER(TRIM(COALESCE(OLD.sourceType, ''))) = '$cloudAlias'
                AND UPPER(TRIM(COALESCE(OLD.status, ''))) = 'STAGING'
                AND LOWER(TRIM(COALESCE(OLD.sourceId, ''))) LIKE '${cloudPrefix.lowercase()}%'
                AND NEW.entryDate = OLD.entryDate
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

        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_accounting_approval_post
            AFTER UPDATE OF status ON approval_requests
            WHEN NEW.referenceType = 'ACCOUNTING_JOURNAL'
              AND OLD.status = 'PENDING'
              AND NEW.status = 'APPROVED'
            BEGIN
                UPDATE journal_entries
                SET status = 'APPROVED'
                WHERE id = CAST(NEW.referenceId AS INTEGER)
                  AND status = 'SUBMITTED';

                UPDATE journal_entries
                SET status = 'POSTED'
                WHERE id = CAST(NEW.referenceId AS INTEGER)
                  AND status = 'APPROVED';

                INSERT INTO audit_events(
                    eventAt, userId, action, entityType, entityId,
                    oldValue, newValue, reason, deviceInfo
                ) VALUES(
                    COALESCE(NEW.decisionAt, NEW.requestedAt), NEW.decisionBy,
                    'APPROVE_POST', 'JOURNAL_ENTRY', NEW.referenceId,
                    'SUBMITTED', 'POSTED', NEW.decisionNote, 'ANDROID'
                );
            END
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_accounting_approval_reject
            AFTER UPDATE OF status ON approval_requests
            WHEN NEW.referenceType = 'ACCOUNTING_JOURNAL'
              AND OLD.status = 'PENDING'
              AND NEW.status = 'REJECTED'
            BEGIN
                UPDATE journal_entries
                SET status = 'REJECTED'
                WHERE id = CAST(NEW.referenceId AS INTEGER)
                  AND status = 'SUBMITTED';

                INSERT INTO audit_events(
                    eventAt, userId, action, entityType, entityId,
                    oldValue, newValue, reason, deviceInfo
                ) VALUES(
                    COALESCE(NEW.decisionAt, NEW.requestedAt), COALESCE(NEW.decisionBy, NEW.requestedBy),
                    'REJECT', 'JOURNAL_ENTRY', NEW.referenceId,
                    'SUBMITTED', 'REJECTED', NEW.decisionNote, 'ANDROID'
                );
            END
            """.trimIndent()
        )
    }
}
