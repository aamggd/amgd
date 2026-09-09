package com.fush.erp.domain

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AccountEntity
import com.fush.erp.data.entity.AccountingPeriodEntity
import com.fush.erp.data.entity.CurrencyEntity
import com.fush.erp.data.entity.UserEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountingPeriodPostingIntegrationTest {
    private lateinit var db: FushDatabase
    private lateinit var service: AccountingService
    private var adminId: Long = 0L
    private var debitAccountId: Long = 0L
    private var creditAccountId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FushDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        service = AccountingService(db)
        adminId = db.userDao().insert(
            UserEntity(
                username = "qa-acc-006",
                displayName = "QA ACC 006",
                passwordHash = "x",
                salt = "x",
                role = "ADMIN",
                mustChangePassword = false,
                mfaEnabled = true,
                mfaVerifiedSessionVersion = 0L
            )
        )
        db.currencyDao().insertDefaultsIgnore(
            listOf(
                CurrencyEntity(
                    code = "YER_NEW",
                    nameAr = "ريال يمني",
                    nameEn = "Yemeni Rial",
                    symbol = "YER",
                    decimals = 2,
                    isBase = true,
                    isActive = true
                )
            )
        )
        debitAccountId = db.accountDao().insert(
            AccountEntity(code = "1998", nameAr = "اختبار مدين", nameEn = "QA Debit", type = "ASSET")
        )
        creditAccountId = db.accountDao().insert(
            AccountEntity(code = "3998", nameAr = "اختبار دائن", nameEn = "QA Credit", type = "EQUITY")
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun openPeriodAllowsPostingOnFirstAndLastDay() = runBlocking {
        insertPeriod(status = "OPEN")
        post(START)
        post(END)
        assertJournalCounts(entries = 2L, lines = 4L)
    }

    @Test
    fun closedPeriodRejectsBeforeAnyJournalWrite() = runBlocking {
        insertPeriod(status = "CLOSED")
        assertRejected { post(MID) }
        assertJournalCounts(entries = 0L, lines = 0L)
    }

    @Test
    fun missingPeriodRejectsBeforeAnyJournalWrite() = runBlocking {
        assertRejected { post(MID) }
        assertJournalCounts(entries = 0L, lines = 0L)
    }

    @Test
    fun datesBeforeAndAfterConfiguredPeriodAreRejectedWithoutPartialPosting() = runBlocking {
        insertPeriod(status = "OPEN")
        assertRejected { post(START - 1L) }
        assertRejected { post(END + 1L) }
        assertJournalCounts(entries = 0L, lines = 0L)
    }

    @Test
    fun allowedReversalInsideOpenPeriodStillPostsExactReversalJournal() = runBlocking {
        insertPeriod(status = "OPEN")
        val originalId = post(MID)
        val reversalId = service.reverseEntry(
            entryId = originalId,
            reason = "QA allowed reversal",
            createdBy = adminId,
            reversalDate = END
        )
        assertTrue(reversalId > originalId)
        assertEquals("REVERSAL", db.journalDao().byId(reversalId)?.sourceType)
        assertJournalCounts(entries = 2L, lines = 4L)
    }

    private suspend fun insertPeriod(status: String) {
        db.accountingDao().insertPeriod(
            AccountingPeriodEntity(
                fiscalYear = 2026,
                periodNo = 8,
                nameAr = "أغسطس 2026",
                startDate = START,
                endDate = END,
                status = status,
                createdBy = adminId
            )
        )
    }

    private suspend fun post(date: Long): Long = service.postManualJournal(
        description = "AE-ACC-006 QA",
        entryDate = date,
        currencyCode = "YER_NEW",
        exchangeRate = 1.0,
        lines = listOf(
            AccountingService.ManualLine(accountId = debitAccountId, debitOriginal = 10.0),
            AccountingService.ManualLine(accountId = creditAccountId, creditOriginal = 10.0)
        ),
        createdBy = adminId
    )

    private suspend fun assertRejected(block: suspend () -> Unit) {
        val failure = runCatching { block() }.exceptionOrNull()
        assertTrue("Expected accounting-period rejection", failure is IllegalArgumentException)
    }

    private fun assertJournalCounts(entries: Long, lines: Long) {
        assertEquals(entries, scalarLong("SELECT COUNT(*) FROM journal_entries"))
        assertEquals(lines, scalarLong("SELECT COUNT(*) FROM journal_lines"))
    }

    private fun scalarLong(sql: String): Long =
        db.openHelper.readableDatabase.query(sql).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private companion object {
        const val START = 1_775_174_400_000L
        const val END = 1_777_852_799_999L
        const val MID = 1_776_513_600_000L
    }
}
