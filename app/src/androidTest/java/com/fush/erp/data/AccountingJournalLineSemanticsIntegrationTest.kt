package com.fush.erp.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fush.erp.data.entity.AccountEntity
import com.fush.erp.data.entity.JournalEntryEntity
import com.fush.erp.data.entity.JournalLineEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountingJournalLineSemanticsIntegrationTest {
    private var db: FushDatabase? = null

    @After fun tearDown() { db?.close(); db = null }

    @Test fun freshRoom37CreatesSemanticRowForEveryJournalLine() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FushDatabase::class.java
        ).allowMainThreadQueries().build()
        db = database
        AccountingDatabaseGuardInitializer.initializeBeforeExposure(database)

        val accountId = database.accountDao().insert(AccountEntity(code="QA-011", nameAr="QA", nameEn="QA", type="ASSET"))
        val entryId = database.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo="QA-011-1", entryDate=1L, description="semantic", currencyCode="USD",
                exchangeRate=2.0, sourceType="SALE", sourceId="qa-011-1", createdBy=1L
            )
        )
        database.journalDao().insertLines(listOf(JournalLineEntity(entryId=entryId, accountId=accountId, debit=2.0)))

        val cursor = database.openHelper.readableDatabase.query(
            "SELECT COUNT(*), transactionCurrencyCode, functionalDebitScaled FROM journal_line_semantics"
        )
        cursor.use {
            check(it.moveToFirst())
            assertEquals(1L, it.getLong(0))
            assertEquals("FUNCTIONAL", it.getString(1))
            assertEquals(20_000L, it.getLong(2))
        }
    }
}
