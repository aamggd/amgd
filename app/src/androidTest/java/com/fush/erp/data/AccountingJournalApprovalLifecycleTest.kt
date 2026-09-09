package com.fush.erp.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fush.erp.data.entity.AccountingPeriodEntity
import com.fush.erp.data.entity.JournalEntryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountingJournalApprovalLifecycleTest {
    private var db: FushDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        db = null
    }

    @Test
    fun manualJournalRequiresDifferentApproverBeforePosting() = runBlocking {
        val database = newDatabase()
        database.accountingDao().insertPeriod(
            AccountingPeriodEntity(
                fiscalYear = 2026,
                periodNo = 8,
                nameAr = "QA",
                startDate = 1L,
                endDate = 100L,
                status = "OPEN",
                createdBy = 99L
            )
        )

        val draft = JournalEntryEntity(
            entryNo = "QA-022-1",
            entryDate = 50L,
            description = "manual approval",
            currencyCode = "YER_NEW",
            exchangeRate = 1.0,
            sourceType = "MANUAL",
            sourceId = "qa-022-1",
            createdBy = 11L
        )
        assertEquals("DRAFT", draft.status)

        val entryId = database.journalDao().insertEntry(draft)
        assertEquals("SUBMITTED", database.journalDao().byId(entryId)?.status)
        assertEquals(1L, scalar(database, "SELECT COUNT(*) FROM approval_requests WHERE referenceType='ACCOUNTING_JOURNAL' AND referenceId='$entryId' AND status='PENDING'"))

        val approvalId = scalar(database, "SELECT id FROM approval_requests WHERE referenceType='ACCOUNTING_JOURNAL' AND referenceId='$entryId'")
        val sameMakerFailure = runCatching {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE approval_requests SET status='APPROVED', decisionBy=11, decisionAt=60, decisionNote='self' WHERE id=$approvalId"
            )
        }.exceptionOrNull()
        assertNotNull(sameMakerFailure)
        assertEquals("SUBMITTED", database.journalDao().byId(entryId)?.status)

        database.openHelper.writableDatabase.execSQL(
            "UPDATE approval_requests SET status='APPROVED', decisionBy=22, decisionAt=60, decisionNote='approved' WHERE id=$approvalId"
        )
        assertEquals("POSTED", database.journalDao().byId(entryId)?.status)
        assertEquals(1L, scalar(database, "SELECT COUNT(*) FROM audit_events WHERE entityType='JOURNAL_ENTRY' AND entityId='$entryId' AND action='APPROVE_POST'"))
    }

    @Test
    fun coldDatabaseRejectsDirectManualPostedInsertAndStatusSkip() = runBlocking {
        val database = newDatabase()
        database.accountingDao().insertPeriod(
            AccountingPeriodEntity(
                fiscalYear = 2026,
                periodNo = 8,
                nameAr = "QA",
                startDate = 1L,
                endDate = 100L,
                status = "OPEN",
                createdBy = 99L
            )
        )
        val sqlite = database.openHelper.writableDatabase

        val directFailure = runCatching {
            sqlite.execSQL(
                """
                INSERT INTO journal_entries(
                    entryNo, entryDate, description, currencyCode, exchangeRate, exchangeRateScaled,
                    sourceType, sourceId, status, createdBy, createdAt
                ) VALUES('QA-022-RAW',50,'raw','YER_NEW',1.0,100000000,'MANUAL','raw-022','POSTED',11,1)
                """.trimIndent()
            )
        }.exceptionOrNull()
        assertNotNull(directFailure)

        val entryId = database.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = "QA-022-2",
                entryDate = 50L,
                description = "skip",
                currencyCode = "YER_NEW",
                exchangeRate = 1.0,
                sourceType = "MANUAL",
                sourceId = "qa-022-2",
                createdBy = 11L
            )
        )
        val skipFailure = runCatching {
            sqlite.execSQL("UPDATE journal_entries SET status='POSTED' WHERE id=$entryId")
        }.exceptionOrNull()
        assertNotNull(skipFailure)
        assertEquals("SUBMITTED", database.journalDao().byId(entryId)?.status)
    }

    @Test
    fun approvalRechecksOpenPeriodBeforePosting() = runBlocking {
        val database = newDatabase()
        val entryId = database.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = "QA-022-3",
                entryDate = 500L,
                description = "missing period",
                currencyCode = "YER_NEW",
                exchangeRate = 1.0,
                sourceType = "MANUAL",
                sourceId = "qa-022-3",
                createdBy = 11L
            )
        )
        val approvalId = scalar(database, "SELECT id FROM approval_requests WHERE referenceId='$entryId'")
        val failure = runCatching {
            database.openHelper.writableDatabase.execSQL(
                "UPDATE approval_requests SET status='APPROVED', decisionBy=22, decisionAt=600, decisionNote='approved' WHERE id=$approvalId"
            )
        }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals("SUBMITTED", database.journalDao().byId(entryId)?.status)
    }

    private fun newDatabase(): FushDatabase {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            FushDatabase::class.java
        ).allowMainThreadQueries().build()
        db = database
        AccountingDatabaseGuardInitializer.initializeBeforeExposure(database)
        val triggerCount = scalar(
            database,
            "SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name LIKE 'trg_%journal%'"
        )
        assertTrue(triggerCount > 0L)
        return database
    }

    private fun scalar(database: FushDatabase, sql: String): Long =
        database.openHelper.readableDatabase.query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }
}
