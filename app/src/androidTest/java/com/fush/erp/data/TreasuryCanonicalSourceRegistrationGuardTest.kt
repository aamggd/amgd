package com.fush.erp.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fush.erp.data.entity.JournalEntryEntity
import com.fush.erp.domain.AccountingPostingIdempotencyPolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TreasuryCanonicalSourceRegistrationGuardTest {
    private var db: FushDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        db = null
    }

    @Test
    fun upgradedDatabaseRebuildsRegistrationTriggerForCanonicalTreasuryVoucherSources() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FushDatabase::class.java
        ).allowMainThreadQueries().build()
        db = database

        // Simulate a phone database whose registration trigger predates the canonical treasury
        // movement source names. The application upgrade must rebuild this trigger from policy.
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS trg_journal_source_registered_insert")
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER trg_journal_source_registered_insert
            BEFORE INSERT ON journal_entries
            WHEN UPPER(TRIM(NEW.sourceType)) NOT IN ('MANUAL','TREASURY_TRANSFER','TREASURY_INCOME')
            BEGIN SELECT RAISE(ABORT, 'ACCOUNTING_EVENT_SOURCE_TYPE_NOT_REGISTERED'); END
            """.trimIndent()
        )

        AccountingDatabaseGuardInitializer.initializeBeforeExposure(database)

        AccountingPostingIdempotencyPolicy.treasuryVoucherReplaySafeSourceTypes.forEachIndexed { index, sourceType ->
            database.journalDao().insertEntry(
                JournalEntryEntity(
                    entryNo = "QA-TREASURY-$index",
                    entryDate = 1L,
                    description = "canonical treasury source $sourceType",
                    currencyCode = "YER_NEW",
                    exchangeRate = 1.0,
                    sourceType = sourceType,
                    sourceId = "qa-${sourceType.lowercase()}-$index",
                    createdBy = 1L
                )
            )
        }

        val unknown = runCatching {
            database.journalDao().insertEntry(
                JournalEntryEntity(
                    entryNo = "QA-UNKNOWN",
                    entryDate = 1L,
                    description = "unknown source must remain blocked",
                    currencyCode = "YER_NEW",
                    exchangeRate = 1.0,
                    sourceType = "TREASURY_NOT_REAL",
                    sourceId = "qa-unknown",
                    createdBy = 1L
                )
            )
        }.exceptionOrNull()
        assertNotNull(unknown)
    }
}
