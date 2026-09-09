package com.fush.erp.data

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fush.erp.domain.AccountingPrecision

/**
 * Non-destructive Room 35 -> 36 backfill for canonical fixed-decimal journal storage.
 *
 * IMPORTANT: Migration conversion must use the exact same AccountingPrecision policy as new writes.
 * SQLite ROUND() is intentionally not used because it rounds ties differently from HALF_EVEN.
 */
class AccountingPrecisionAutoMigration : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        // Room 35 may contain the P1 immutability triggers from MIGRATION_34_35_ACCOUNTING_P1.
        // They must be suspended only for this controlled schema backfill, then restored before
        // the migration transaction completes. If migration fails, Room rolls the transaction back.
        dropPostedUpdateGuards(db)
        try {
            backfillExchangeRates(db)
            backfillJournalAmounts(db)
        } finally {
            restorePostedUpdateGuards(db)
        }
    }

    private fun backfillExchangeRates(db: SupportSQLiteDatabase) {
        var lastId = Long.MIN_VALUE
        while (true) {
            val rows = mutableListOf<Pair<Long, Double>>()
            db.query(
                "SELECT id, exchangeRate FROM journal_entries WHERE id > ? ORDER BY id LIMIT ?",
                arrayOf<Any?>(lastId, BATCH_SIZE)
            ).use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow("id")
                val rateIndex = cursor.getColumnIndexOrThrow("exchangeRate")
                while (cursor.moveToNext()) {
                    rows += cursor.getLong(idIndex) to cursor.getDouble(rateIndex)
                }
            }
            if (rows.isEmpty()) break

            rows.forEach { (id, legacyRate) ->
                val scaled = AccountingPrecision.rateToScaled(legacyRate)
                val mirror = AccountingPrecision.rateToDouble(scaled)
                db.execSQL(
                    "UPDATE journal_entries SET exchangeRateScaled = ?, exchangeRate = ? WHERE id = ?",
                    arrayOf<Any?>(scaled, mirror, id)
                )
            }
            lastId = rows.last().first
        }
    }

    private fun backfillJournalAmounts(db: SupportSQLiteDatabase) {
        var lastId = Long.MIN_VALUE
        while (true) {
            val rows = mutableListOf<LegacyJournalLine>()
            db.query(
                "SELECT id, debit, credit FROM journal_lines WHERE id > ? ORDER BY id LIMIT ?",
                arrayOf<Any?>(lastId, BATCH_SIZE)
            ).use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow("id")
                val debitIndex = cursor.getColumnIndexOrThrow("debit")
                val creditIndex = cursor.getColumnIndexOrThrow("credit")
                while (cursor.moveToNext()) {
                    rows += LegacyJournalLine(
                        id = cursor.getLong(idIndex),
                        debit = cursor.getDouble(debitIndex),
                        credit = cursor.getDouble(creditIndex)
                    )
                }
            }
            if (rows.isEmpty()) break

            rows.forEach { row ->
                val debitScaled = AccountingPrecision.amountToScaled(row.debit)
                val creditScaled = AccountingPrecision.amountToScaled(row.credit)
                db.execSQL(
                    """
                    UPDATE journal_lines
                    SET debitScaled = ?, creditScaled = ?, debit = ?, credit = ?
                    WHERE id = ?
                    """.trimIndent(),
                    arrayOf<Any?>(
                        debitScaled,
                        creditScaled,
                        AccountingPrecision.amountToDouble(debitScaled),
                        AccountingPrecision.amountToDouble(creditScaled),
                        row.id
                    )
                )
            }
            lastId = rows.last().id
        }
    }

    private fun dropPostedUpdateGuards(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TRIGGER IF EXISTS trg_posted_journal_no_update")
        db.execSQL("DROP TRIGGER IF EXISTS trg_posted_journal_line_no_update")
    }

    private fun restorePostedUpdateGuards(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_posted_journal_no_update
            BEFORE UPDATE ON journal_entries
            WHEN OLD.status = 'POSTED'
            BEGIN
                SELECT RAISE(ABORT, 'POSTED_JOURNAL_IMMUTABLE_USE_REVERSAL');
            END
            """.trimIndent()
        )
        db.execSQL(
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
            """.trimIndent()
        )
    }

    private data class LegacyJournalLine(
        val id: Long,
        val debit: Double,
        val credit: Double
    )

    private companion object {
        const val BATCH_SIZE = 500
    }
}
