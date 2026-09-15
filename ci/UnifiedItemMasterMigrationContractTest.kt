package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UnifiedItemMasterMigrationContractTest {
    private fun source(path: String): String {
        val candidates = listOf(
            File(path),
            File(System.getProperty("user.dir"), path),
            File(System.getProperty("user.dir"), "../$path")
        )
        return candidates.firstOrNull { it.exists() }?.readText()
            ?: error("Source file not found: $path")
    }

    @Test
    fun schema54RequiresCategoryMasterAndStartupMigrationParity() {
        val db = source("src/main/java/com/fush/erp/data/FushDatabase.kt")
        val app = source("src/main/java/com/fush/erp/data/AppContainer.kt")
        val boot = source("src/main/java/com/fush/erp/data/AccountingWaveBRoomBootstrap.kt")
        val entities = source("src/main/java/com/fush/erp/data/entity/ProductMasterEntities.kt")
        val migrationPath = "src/main/java/com/fush/erp/data/UnifiedItemMasterMigration.kt"
        val migration = runCatching { source(migrationPath) }.getOrDefault("")

        assertTrue(db.contains("FUSH_DB_SCHEMA_VERSION = 54"))
        assertTrue(db.contains("ProductCategoryEntity::class"))
        assertTrue(entities.contains("data class ProductCategoryEntity"))
        assertTrue(entities.contains("val categoryId: Long?"))
        assertTrue(entities.contains("val referencePurchasePrice: Double?"))
        assertTrue(entities.contains("val imageUri: String?"))
        assertTrue(migration.contains("Migration(53, 54)"))
        assertTrue(app.contains("MIGRATION_53_54_UNIFIED_ITEM_MASTER"))
        assertTrue(boot.contains("MIGRATION_53_54_UNIFIED_ITEM_MASTER"))
        assertFalse(app.contains("fallbackToDestructiveMigration"))
        assertFalse(boot.contains("fallbackToDestructiveMigration"))
    }
}
