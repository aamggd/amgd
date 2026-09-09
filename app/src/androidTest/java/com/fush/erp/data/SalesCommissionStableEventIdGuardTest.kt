package com.fush.erp.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fush.erp.data.entity.JournalEntryEntity
import com.fush.erp.domain.SalesCommissionAccountingEventIdentity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalesCommissionStableEventIdGuardTest {
    private var db: FushDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        db = null
    }

    @Test
    fun upgradedDatabaseAllowsNamespacedIdsBesideLegacyIdsAndStillRejectsTrueDuplicates() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FushDatabase::class.java
        ).allowMainThreadQueries().build()
        db = database

        // Simulate the stale trigger body that exists in databases opened by the previous build.
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER trg_journal_unstable_source_blocked_insert
            BEFORE INSERT ON journal_entries
            WHEN UPPER(TRIM(NEW.sourceType)) = 'SALES_COMMISSION'
            BEGIN SELECT RAISE(ABORT, 'ACCOUNTING_SOURCE_REQUIRES_STABLE_EVENT_ID'); END
            """.trimIndent()
        )
        AccountingDatabaseGuardInitializer.initializeBeforeExposure(database)

        data class Case(val sourceType: String, val legacySourceId: String, val newSourceId: String)
        val cases = listOf(
            Case("SALES_COMMISSION", "101", SalesCommissionAccountingEventIdentity.commission(101L)),
            Case("COMMISSION_REVERSAL", "202", SalesCommissionAccountingEventIdentity.returnReversal(202L)),
            Case(
                "RECEIPT_COMMISSION_REVERSAL",
                "303:404",
                SalesCommissionAccountingEventIdentity.receiptReversal(303L, 404L)
            )
        )

        cases.forEachIndexed { index, case ->
            // Existing phones can already contain these legacy ids. They must remain untouched.
            database.journalDao().insertEntry(
                JournalEntryEntity(
                    entryNo = "QA-LEGACY-$index",
                    entryDate = 1L,
                    description = "legacy ${case.sourceType}",
                    currencyCode = "YER_NEW",
                    exchangeRate = 1.0,
                    sourceType = case.sourceType,
                    sourceId = case.legacySourceId,
                    createdBy = 1L
                )
            )

            // The new event is different even when its underlying row id numerically matches legacy data.
            database.journalDao().insertEntry(
                JournalEntryEntity(
                    entryNo = "QA-NEW-$index",
                    entryDate = 1L,
                    description = case.sourceType,
                    currencyCode = "YER_NEW",
                    exchangeRate = 1.0,
                    sourceType = case.sourceType,
                    sourceId = case.newSourceId,
                    createdBy = 1L
                )
            )

            // True replay of the exact new event must still be rejected.
            val duplicate = runCatching {
                database.journalDao().insertEntry(
                    JournalEntryEntity(
                        entryNo = "QA-DUP-$index",
                        entryDate = 1L,
                        description = "duplicate ${case.sourceType}",
                        currencyCode = "YER_NEW",
                        exchangeRate = 1.0,
                        sourceType = case.sourceType,
                        sourceId = case.newSourceId,
                        createdBy = 1L
                    )
                )
            }.exceptionOrNull()
            assertNotNull(duplicate)
        }
    }
}
