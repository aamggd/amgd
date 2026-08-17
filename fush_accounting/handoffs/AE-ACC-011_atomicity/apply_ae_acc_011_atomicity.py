from pathlib import Path
import sys

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("central_source")
SERVICE = ROOT / "app/src/main/java/com/fush/erp/domain/AccountingService.kt"
TEST = ROOT / "app/src/test/java/com/fush/erp/domain/AccountingPostJournalAtomicityTest.kt"

source = SERVICE.read_text(encoding="utf-8")

class_anchor = "class AccountingService(private val db: FushDatabase) {"
atomicity_gate = '''internal object AccountingPostJournalAtomicity {
    suspend fun <T> validateThenPersistPosted(
        lines: List<DraftJournalLine>,
        persistPosted: suspend () -> T
    ): T {
        AccountingValidator.validate(lines)
        return persistPosted()
    }
}

'''
if source.count(class_anchor) != 1:
    raise SystemExit("AccountingService class anchor mismatch")
source = source.replace(class_anchor, atomicity_gate + class_anchor, 1)

helper_anchor = '''    suspend fun addAccount(
'''
helper = '''    private suspend fun postJournalEntry(
        entry: JournalEntryEntity,
        lines: List<JournalLineEntity>
    ): Long = db.withTransaction {
        val validationLines = lines.map { line ->
            DraftJournalLine(line.accountId, line.debit, line.credit)
        }
        AccountingPostJournalAtomicity.validateThenPersistPosted(validationLines) {
            val entryId = db.journalDao().insertEntry(entry.copy(id = 0, status = "POSTED"))
            db.journalDao().insertLines(lines.map { line -> line.copy(id = 0, entryId = entryId) })
            entryId
        }
    }

'''
if source.count(helper_anchor) != 1:
    raise SystemExit("addAccount anchor mismatch")
source = source.replace(helper_anchor, helper + helper_anchor, 1)

old_manual_post = '''        AccountingValidator.validate(baseLines)
        val entryNo = documentNo("JV")
        val entryId = db.journalDao().insertEntry(
            JournalEntryEntity(
                entryNo = entryNo,
                entryDate = entryDate,
                description = description.trim(),
                currencyCode = currencyCode,
                exchangeRate = exchangeRate,
                sourceType = "MANUAL",
                sourceId = UUID.randomUUID().toString(),
                createdBy = createdBy
            )
        )
        db.journalDao().insertLines(
            lines.mapIndexed { index, line ->
                JournalLineEntity(
                    entryId = entryId,
                    accountId = line.accountId,
                    debit = baseLines[index].debit,
                    credit = baseLines[index].credit,
                    memo = line.memo.trim()
                )
            }
        )
        entryId
'''
new_manual_post = '''        val entryNo = documentNo("JV")
        postJournalEntry(
            entry = JournalEntryEntity(
                entryNo = entryNo,
                entryDate = entryDate,
                description = description.trim(),
                currencyCode = currencyCode,
                exchangeRate = exchangeRate,
                sourceType = "MANUAL",
                sourceId = UUID.randomUUID().toString(),
                createdBy = createdBy
            ),
            lines = lines.mapIndexed { index, line ->
                JournalLineEntity(
                    entryId = 0,
                    accountId = line.accountId,
                    debit = baseLines[index].debit,
                    credit = baseLines[index].credit,
                    memo = line.memo.trim()
                )
            }
        )
'''
if source.count(old_manual_post) != 1:
    raise SystemExit("postManualJournal posting block mismatch")
source = source.replace(old_manual_post, new_manual_post, 1)
SERVICE.write_text(source, encoding="utf-8")

if TEST.exists():
    raise SystemExit(f"Regression test already exists: {TEST}")
TEST.write_text('''package com.fush.erp.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AccountingPostJournalAtomicityTest {
    @Test
    fun aeAcc011_unbalancedJournalCannotPersistPostedStatus() = runBlocking {
        var persistedStatus = "DRAFT"
        var persistCalled = false

        try {
            AccountingPostJournalAtomicity.validateThenPersistPosted(
                listOf(
                    DraftJournalLine(accountId = 1, debit = 100.0, credit = 0.0),
                    DraftJournalLine(accountId = 2, debit = 0.0, credit = 90.0)
                )
            ) {
                persistCalled = true
                persistedStatus = "POSTED"
            }
            fail("AE-ACC-011 regression: unbalanced journal reached POSTED persistence")
        } catch (_: IllegalArgumentException) {
            // Expected: validation aborts before the persistence callback.
        }

        assertFalse(persistCalled)
        assertEquals("DRAFT", persistedStatus)
    }

    @Test
    fun aeAcc011_lineIntegrityFailureCannotPersistPostedStatus() = runBlocking {
        var persistCalled = false

        try {
            AccountingPostJournalAtomicity.validateThenPersistPosted(
                listOf(
                    DraftJournalLine(accountId = 1, debit = 100.0, credit = 100.0),
                    DraftJournalLine(accountId = 2, debit = 0.0, credit = 100.0)
                )
            ) {
                persistCalled = true
            }
            fail("AE-ACC-011 regression: invalid debit/credit line reached POSTED persistence")
        } catch (_: IllegalArgumentException) {
            // Expected: line-integrity validation aborts before persistence.
        }

        assertFalse(persistCalled)
    }

    @Test
    fun balancedJournalMayEnterPostedPersistenceOnlyAfterValidation() = runBlocking {
        var persistCalled = false
        val result = AccountingPostJournalAtomicity.validateThenPersistPosted(
            listOf(
                DraftJournalLine(accountId = 1, debit = 100.0, credit = 0.0),
                DraftJournalLine(accountId = 2, debit = 0.0, credit = 100.0)
            )
        ) {
            persistCalled = true
            42L
        }

        assertTrue(persistCalled)
        assertEquals(42L, result)
    }
}
''', encoding="utf-8")

print("AE_ACC_011_ATOMICITY_FIX_APPLIED")
