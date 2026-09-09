package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesFreeQuantityMigrationContractTest {
    private fun source(path: String): String = listOf(File("src/main/java/$path"), File("app/src/main/java/$path")).first { it.isFile }.readText()

    @Test fun migrationIsAdditiveAndPreservesHistoricalRows() {
        val s = source("com/fush/erp/data/SalesFreeQuantityMigration.kt")
        assertTrue(s.contains("ALTER TABLE sales_lines ADD COLUMN freeQuantity REAL NOT NULL DEFAULT 0.0"))
        assertTrue(s.contains("ALTER TABLE sales_allocations ADD COLUMN freeQuantityBase REAL NOT NULL DEFAULT 0.0"))
        assertTrue(s.contains("ALTER TABLE sales_return_lines ADD COLUMN freeQuantity REAL NOT NULL DEFAULT 0.0"))
        assertTrue(!s.contains("DROP TABLE"))
        assertTrue(!s.contains("DELETE FROM"))
    }
}
