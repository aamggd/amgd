package com.fush.erp.data

import android.util.Log
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountingP1Migration34To35Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FushDatabase::class.java
    )

    @Test
    fun migrate34To35_preservesHistoricalPostedRows_andEnforcesWave1JournalGuards() {
        Log.i(TAG, "BODY_START:migrate34To35_preservesHistoricalPostedRows_andEnforcesWave1JournalGuards")
        helper.createDatabase(DB_34_35, 34).apply {
            execSQL(
                """INSERT INTO journal_entries
                    (id, entryNo, entryDate, description, currencyCode, exchangeRate, sourceType, sourceId, status, createdBy, createdAt)
                    VALUES (100, 'LEG-POSTED-100', 1000, 'legacy posted sale', 'YER_NEW', 1.0, 'SALE', NULL, 'POSTED', 1, 1000)
                """.trimIndent()
            )
            execSQL(
                """INSERT INTO journal_entries
                    (id, entryNo, entryDate, description, currencyCode, exchangeRate, sourceType, sourceId, status, createdBy, createdAt)
                    VALUES (101, 'LEG-MANUAL-101', 1001, 'legacy manual', 'YER_NEW', 1.0, 'MANUAL', NULL, 'POSTED', 1, 1001)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            DB_34_35,
            35,
            true,
            MIGRATION_34_35_ACCOUNTING_P1
        )
        Log.i(TAG, "MIGRATION_EXECUTED:34->35")

        db.query("SELECT COUNT(*) FROM journal_entries WHERE id IN (100,101)").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }
        db.query("SELECT entryNo, sourceType, sourceId, status FROM journal_entries WHERE id=100").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("LEG-POSTED-100", c.getString(0))
            assertEquals("SALE", c.getString(1))
            assertTrue(c.isNull(2))
            assertEquals("POSTED", c.getString(3))
        }

        assertSqlRejected(db, "ACCOUNTING_STABLE_SOURCE_ID_REQUIRED") {
            db.execSQL(
                """INSERT INTO journal_entries
                    (entryNo, entryDate, description, currencyCode, exchangeRate, sourceType, sourceId, status, createdBy, createdAt)
                    VALUES ('SALE-NO-ID', 2000, 'missing id', 'YER_NEW', 1.0, 'SALE', NULL, 'POSTED', 1, 2000)
                """.trimIndent()
            )
        }

        val stableSources = listOf(
            "SALE",
            "CUSTOMER_RECEIPT",
            "SALES_RETURN",
            "PURCHASE",
            "PURCHASE_RETURN",
            "SUPPLIER_PAYMENT"
        )
        stableSources.forEachIndexed { index, sourceType ->
            val sourceId = "qa-wave1-${sourceType.lowercase()}-$index"
            val firstNo = "QA-$index-A"
            val duplicateNo = "QA-$index-B"
            db.execSQL(
                """INSERT INTO journal_entries
                    (entryNo, entryDate, description, currencyCode, exchangeRate, sourceType, sourceId, status, createdBy, createdAt)
                    VALUES ('$firstNo', ${2100 + index}, 'first wave1 event', 'YER_NEW', 1.0, '$sourceType', '$sourceId', 'POSTED', 1, ${2100 + index})
                """.trimIndent()
            )
            assertSqlRejected(db, "DUPLICATE_ACCOUNTING_POSTING") {
                db.execSQL(
                    """INSERT INTO journal_entries
                        (entryNo, entryDate, description, currencyCode, exchangeRate, sourceType, sourceId, status, createdBy, createdAt)
                        VALUES ('$duplicateNo', ${2200 + index}, 'duplicate wave1 event', 'YER_NEW', 1.0, '$sourceType', '$sourceId', 'POSTED', 1, ${2200 + index})
                    """.trimIndent()
                )
            }
        }

        assertSqlRejected(db, "POSTED_JOURNAL_IMMUTABLE_USE_REVERSAL") {
            db.execSQL("UPDATE journal_entries SET description='mutated' WHERE id=100")
        }
        assertSqlRejected(db, "POSTED_JOURNAL_IMMUTABLE_USE_REVERSAL") {
            db.execSQL("DELETE FROM journal_entries WHERE id=100")
        }

        db.execSQL(
            """INSERT INTO journal_entries
                (entryNo, entryDate, description, currencyCode, exchangeRate, sourceType, sourceId, status, createdBy, createdAt)
                VALUES ('MANUAL-REPEAT-A', 2300, 'manual A', 'YER_NEW', 1.0, 'MANUAL', 'same-manual-id', 'POSTED', 1, 2300)
            """.trimIndent()
        )
        db.execSQL(
            """INSERT INTO journal_entries
                (entryNo, entryDate, description, currencyCode, exchangeRate, sourceType, sourceId, status, createdBy, createdAt)
                VALUES ('MANUAL-REPEAT-B', 2301, 'manual B', 'YER_NEW', 1.0, 'MANUAL', 'same-manual-id', 'POSTED', 1, 2301)
            """.trimIndent()
        )

        assertSqlRejected(db, "INVALID_JOURNAL_LINE") {
            db.execSQL("INSERT INTO journal_lines(entryId, accountId, debit, credit, memo) VALUES (99999, 99999, -1, 0, 'invalid')")
        }
        assertSqlRejected(db, "INVALID_JOURNAL_LINE") {
            db.execSQL("INSERT INTO journal_lines(entryId, accountId, debit, credit, memo) VALUES (99999, 99999, 1, 1, 'invalid')")
        }

        db.close()
        Log.i(TAG, "BODY_PASS:migrate34To35_preservesHistoricalPostedRows_andEnforcesWave1JournalGuards")
    }

    @Test
    fun migrate32To35_validatesCompleteSecurityFixedAssetAccountingChain_withoutDestructiveReset() {
        Log.i(TAG, "BODY_START:migrate32To35_validatesCompleteSecurityFixedAssetAccountingChain_withoutDestructiveReset")
        helper.createDatabase(DB_32_35, 32).close()
        helper.runMigrationsAndValidate(
            DB_32_35,
            35,
            true,
            MIGRATION_32_33_SECURITY,
            MIGRATION_33_34_FIXED_ASSETS,
            MIGRATION_34_35_ACCOUNTING_P1
        ).close()
        Log.i(TAG, "MIGRATION_CHAIN_EXECUTED:32->35")
        Log.i(TAG, "BODY_PASS:migrate32To35_validatesCompleteSecurityFixedAssetAccountingChain_withoutDestructiveReset")
    }

    private fun assertSqlRejected(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        expectedMarker: String,
        block: () -> Unit
    ) {
        try {
            block()
            fail("Expected SQL rejection containing $expectedMarker")
        } catch (t: Throwable) {
            assertTrue(
                "Expected '$expectedMarker' but got: ${t.message}",
                t.message.orEmpty().contains(expectedMarker)
            )
        }
    }

    companion object {
        private const val TAG = "FUSH_QA_MIGRATION"
        private const val DB_34_35 = "qa-accounting-p1-34-35"
        private const val DB_32_35 = "qa-wave1-32-35"
    }
}
