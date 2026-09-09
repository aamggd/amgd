package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V148RoomIndexHardeningContractTest {
    private val root = File("src/main/java/com/fush/erp")

    @Test
    fun schema45IndexHardeningRemainsRegisteredAfterSchema46() {
        val db = File(root, "data/FushDatabase.kt").readText()
        val migration = File(root, "data/RoomIndexMigrations.kt").readText()
        val container = File(root, "data/AppContainer.kt").readText()
        val bootstrap = File(root, "data/AccountingWaveBRoomBootstrap.kt").readText()

        assertTrue((Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0) >= 46)
        assertTrue(container.contains("MIGRATION_44_45_FOREIGN_KEY_INDEX_HARDENING"))
        assertTrue(bootstrap.contains("MIGRATION_44_45_FOREIGN_KEY_INDEX_HARDENING"))
        assertTrue(container.contains("MIGRATION_45_46_PURCHASE_INVOICE_ADJUSTMENTS"))
        assertTrue(bootstrap.contains("MIGRATION_45_46_PURCHASE_INVOICE_ADJUSTMENTS"))
        assertTrue(migration.contains("Migration(44, 45)"))
        assertEquals(9, Regex("CREATE INDEX IF NOT EXISTS").findAll(migration).count())
        assertFalse(migration.contains("ALTER TABLE", ignoreCase = true))
        assertFalse(migration.contains("DROP TABLE", ignoreCase = true))
        assertFalse(migration.contains("DELETE FROM", ignoreCase = true))
        assertFalse(migration.contains("UPDATE ", ignoreCase = true))
        assertFalse(migration.contains("INSERT INTO", ignoreCase = true))
    }

    @Test
    fun allNineForeignKeyColumnsAreIndexedInEntitiesAndMigration() {
        val purchase = File(root, "data/entity/PurchaseEntities.kt").readText()
        val sales = File(root, "data/entity/SalesEntities.kt").readText()
        val party = File(root, "data/entity/PartyEntities.kt").readText()
        val support = File(root, "data/entity/SupportEntities.kt").readText()
        val migration = File(root, "data/RoomIndexMigrations.kt").readText()

        listOf("Index(\"warehouseId\")", "Index(\"unitId\")").forEach { assertTrue(purchase.contains(it)) }
        listOf("Index(\"warehouseId\")", "Index(\"unitId\")").forEach { assertTrue(sales.contains(it)) }
        assertTrue(party.contains("Index(\"treasuryAccountId\")"))
        assertTrue(party.contains("Index(\"offsetAccountId\")"))
        assertTrue(support.contains("Index(\"activatedBy\")"))
        assertTrue(support.contains("Index(\"supportUserId\")"))

        val expected = listOf(
            "index_purchase_returns_warehouseId",
            "index_purchase_return_lines_unitId",
            "index_sales_returns_warehouseId",
            "index_sales_return_lines_unitId",
            "index_party_vouchers_treasuryAccountId",
            "index_party_vouchers_offsetAccountId",
            "index_support_sessions_activatedBy",
            "index_support_snapshots_supportUserId",
            "index_support_validation_results_supportUserId"
        )
        expected.forEach { assertTrue("missing $it", migration.contains(it)) }
    }
}
