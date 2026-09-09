package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class PostedJournalLifecycleIntegrityContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Missing source: $relative")
    }

    @Test
    fun operationalJournalsStartStagingAndPostOnlyAfterLines() {
        val entities = source("com/fush/erp/data/entity/Entities.kt")
        val dao = source("com/fush/erp/data/dao/Daos.kt")
        assertTrue(entities.contains("if (sourceType == \"MANUAL\") \"DRAFT\" else \"STAGING\""))
        assertTrue(dao.contains("insertLinesRaw(rows)"))
        assertTrue(dao.contains("transitionStagingToPosted(entryId)"))
    }

    @Test
    fun databaseBlocksAllPostedLineMutationsAndUnbalancedPosting() {
        val guard = source("com/fush/erp/data/AccountingPostedJournalLifecycleDatabaseGuard.kt")
        assertTrue(guard.contains("trg_posted_journal_line_no_insert"))
        assertTrue(guard.contains("trg_posted_journal_line_no_update"))
        assertTrue(guard.contains("trg_posted_journal_line_no_delete"))
        assertTrue(guard.contains("trg_journal_post_requires_balanced_lines"))
        assertTrue(guard.contains("SUM(jl.debitScaled)"))
        assertTrue(guard.contains("SUM(jl.creditScaled)"))
        assertTrue(guard.contains("NEW.entryId AND je.status = 'POSTED'"))
    }

    @Test
    fun lifecycleGuardIsInstalledOnEveryColdOpen() {
        val initializer = source("com/fush/erp/data/AccountingDatabaseGuardInitializer.kt")
        assertTrue(initializer.contains("AccountingPostedJournalLifecycleDatabaseGuard.install(db)"))
    }
}
