package com.fush.erp.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fush.erp.domain.AccountingPrecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountingPrecisionMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FushDatabase::class.java
    )

    @Test
    fun room35PrecisionPolicySurvivesFullWaveBMigrationTo38() {
        // Produce a real Room35 database through the production 34 -> 35 migration so the
        // Wave A immutability triggers are present before the precision backfill starts.
        helper.createDatabase(DB_NAME, 34).close()
        helper.runMigrationsAndValidate(
            DB_NAME,
            35,
            true,
            MIGRATION_34_35_ACCOUNTING_P1
        ).use { db35 ->
            seedAccounts(db35)
            seedJournal(db35, 501, "QA-024-1", 1.234567885, 601, 602, 1.23445)
            seedJournal(db35, 502, "QA-024-2", 1.234567895, 603, 604, 1.23455)
            seedJournal(db35, 503, "QA-024-3", 1.0, 605, 606, 0.0, "DRAFT")
            assertEquals(3L, scalarLong(db35, "SELECT COUNT(*) FROM journal_entries"))
            assertEquals(6L, scalarLong(db35, "SELECT COUNT(*) FROM journal_lines"))
        }

        // Validate only against the final exported schema. Intermediate 36/37 schemas are not
        // committed artifacts; the explicit migrations are still executed in order here.
        helper.runMigrationsAndValidate(
            DB_NAME,
            38,
            true,
            MIGRATION_35_36_ACCOUNTING_PRECISION,
            MIGRATION_36_37_JOURNAL_LINE_SEMANTICS,
            MIGRATION_37_38_INVENTORY_COST_LAYERS
        ).use { db38 ->
            assertEquals(38, db38.version)
            assertEquals(3L, scalarLong(db38, "SELECT COUNT(*) FROM journal_entries"))
            assertEquals(6L, scalarLong(db38, "SELECT COUNT(*) FROM journal_lines"))
            assertEquals(listOf(501L, 502L, 503L), ids(db38, "journal_entries"))
            assertEquals(listOf(601L, 602L, 603L, 604L, 605L, 606L), ids(db38, "journal_lines"))

            assertEquals(12_344L, lineScaled(db38, 601, "debitScaled"))
            assertEquals(12_344L, lineScaled(db38, 602, "creditScaled"))
            assertEquals(12_346L, lineScaled(db38, 603, "debitScaled"))
            assertEquals(12_346L, lineScaled(db38, 604, "creditScaled"))
            assertEquals(0L, lineScaled(db38, 605, "debitScaled"))
            assertEquals(0L, lineScaled(db38, 606, "creditScaled"))

            assertEquals(123_456_788L, entryScaledRate(db38, 501))
            assertEquals(123_456_790L, entryScaledRate(db38, 502))
            assertEquals(AccountingPrecision.amountToScaled(1.23445), lineScaled(db38, 601, "debitScaled"))
            assertEquals(AccountingPrecision.amountToScaled(1.23455), lineScaled(db38, 603, "debitScaled"))
            assertEquals(AccountingPrecision.rateToScaled(1.234567885), entryScaledRate(db38, 501))
            assertEquals(AccountingPrecision.rateToScaled(1.234567895), entryScaledRate(db38, 502))

            assertEquals(AccountingPrecision.amountToDouble(12_344L), lineReal(db38, 601, "debit"), 0.0)
            assertEquals(AccountingPrecision.amountToDouble(12_346L), lineReal(db38, 604, "credit"), 0.0)
            assertEquals(AccountingPrecision.rateToDouble(123_456_788L), entryRealRate(db38, 501), 0.0)

            db38.query(
                """
                SELECT entryId, SUM(debitScaled), SUM(creditScaled)
                FROM journal_lines GROUP BY entryId ORDER BY entryId
                """.trimIndent()
            ).use { cursor ->
                while (cursor.moveToNext()) assertEquals(cursor.getLong(1), cursor.getLong(2))
            }

            // The 36 -> 37 migration must have produced semantics for every migrated line.
            assertEquals(6L, scalarLong(db38, "SELECT COUNT(*) FROM journal_line_semantics"))
            assertEquals(6L, scalarLong(db38, "SELECT COUNT(*) FROM journal_line_semantics WHERE transactionCurrencyCode='FUNCTIONAL'"))

            val triggerCount = scalarLong(
                db38,
                """
                SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name IN (
                  'trg_posted_journal_no_update',
                  'trg_posted_journal_line_no_update',
                  'trg_journal_line_semantics_autocreate',
                  'trg_inventory_cost_layer_autocreate'
                )
                """.trimIndent()
            )
            assertEquals(4L, triggerCount)
            assertTrue(entryScaledRate(db38, 501) > 0L)
        }
    }

    private fun seedAccounts(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO accounts(id, code, nameAr, nameEn, type, parentCode, isPosting, isActive) VALUES(101, 'QA-CASH', 'نقد', 'Cash', 'ASSET', NULL, 1, 1)")
        db.execSQL("INSERT INTO accounts(id, code, nameAr, nameEn, type, parentCode, isPosting, isActive) VALUES(102, 'QA-OFFSET', 'مقابل', 'Offset', 'ASSET', NULL, 1, 1)")
    }

    private fun seedJournal(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        entryId: Long,
        entryNo: String,
        exchangeRate: Double,
        debitLineId: Long,
        creditLineId: Long,
        amount: Double,
        status: String = "POSTED"
    ) {
        db.execSQL(
            """
            INSERT INTO journal_entries(
                id, entryNo, entryDate, description, currencyCode, exchangeRate,
                sourceType, sourceId, status, createdBy, createdAt
            ) VALUES(?, ?, 1700000000000, 'QA AE-ACC-024', 'USD', ?, 'MANUAL', NULL, ?, 1, 1700000000000)
            """.trimIndent(),
            arrayOf<Any?>(entryId, entryNo, exchangeRate, status)
        )
        db.execSQL(
            "INSERT INTO journal_lines(id, entryId, accountId, debit, credit, memo) VALUES(?, ?, 101, ?, 0.0, 'QA debit')",
            arrayOf<Any?>(debitLineId, entryId, amount)
        )
        db.execSQL(
            "INSERT INTO journal_lines(id, entryId, accountId, debit, credit, memo) VALUES(?, ?, 102, 0.0, ?, 'QA credit')",
            arrayOf<Any?>(creditLineId, entryId, amount)
        )
    }

    private fun scalarLong(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { cursor -> check(cursor.moveToFirst()); cursor.getLong(0) }

    private fun ids(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): List<Long> =
        db.query("SELECT id FROM $table ORDER BY id").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
        }

    private fun lineScaled(db: androidx.sqlite.db.SupportSQLiteDatabase, id: Long, column: String): Long =
        db.query("SELECT $column FROM journal_lines WHERE id = ?", arrayOf(id)).use { cursor ->
            check(cursor.moveToFirst()); cursor.getLong(0)
        }

    private fun lineReal(db: androidx.sqlite.db.SupportSQLiteDatabase, id: Long, column: String): Double =
        db.query("SELECT $column FROM journal_lines WHERE id = ?", arrayOf(id)).use { cursor ->
            check(cursor.moveToFirst()); cursor.getDouble(0)
        }

    private fun entryScaledRate(db: androidx.sqlite.db.SupportSQLiteDatabase, id: Long): Long =
        db.query("SELECT exchangeRateScaled FROM journal_entries WHERE id = ?", arrayOf(id)).use { cursor ->
            check(cursor.moveToFirst()); cursor.getLong(0)
        }

    private fun entryRealRate(db: androidx.sqlite.db.SupportSQLiteDatabase, id: Long): Double =
        db.query("SELECT exchangeRate FROM journal_entries WHERE id = ?", arrayOf(id)).use { cursor ->
            check(cursor.moveToFirst()); cursor.getDouble(0)
        }

    private companion object {
        const val DB_NAME = "ae-acc-024-room35-to-38-test"
    }
}
