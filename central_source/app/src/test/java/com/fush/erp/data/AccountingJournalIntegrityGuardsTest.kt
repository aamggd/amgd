package com.fush.erp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingJournalIntegrityGuardsTest {
    @Test
    fun freshAndUpgradeInstallerContainsCompleteWave1ProtectionSet() {
        val sql = AccountingJournalIntegrityGuards.triggerSql.joinToString("\n")
        val requiredTriggers = setOf(
            "trg_journal_entries_closed_period",
            "trg_journal_stable_source_id_required_insert",
            "trg_journal_no_duplicate_stable_source_insert",
            "trg_journal_no_duplicate_stable_source_update",
            "trg_posted_journal_no_update",
            "trg_posted_journal_no_delete",
            "trg_journal_line_sanity_insert",
            "trg_posted_journal_line_no_update",
            "trg_posted_journal_line_no_delete"
        )
        requiredTriggers.forEach { assertTrue("Missing $it", sql.contains(it)) }
        assertEquals(9, AccountingJournalIntegrityGuards.triggerSql.size)
    }

    @Test
    fun stableOperationalSourcesAreProtected() {
        val sql = AccountingJournalIntegrityGuards.triggerSql.joinToString("\n")
        setOf("SALE", "CUSTOMER_RECEIPT", "SALES_RETURN", "PURCHASE", "SUPPLIER_PAYMENT", "PURCHASE_RETURN")
            .forEach { assertTrue("Missing stable source $it", sql.contains("'$it'")) }
    }

    @Test
    fun installerIsDdlOnlyAndCannotDeleteExistingUserData() {
        AccountingJournalIntegrityGuards.triggerSql.forEach { statement ->
            val normalized = statement.trimStart().uppercase()
            assertTrue(normalized.startsWith("CREATE TRIGGER IF NOT EXISTS"))
            assertFalse(normalized.startsWith("DELETE "))
            assertFalse(normalized.startsWith("UPDATE "))
            assertFalse(normalized.startsWith("DROP "))
            assertFalse(normalized.startsWith("ALTER "))
        }
    }
}
