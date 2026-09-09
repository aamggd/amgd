package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V158MasterDataCloudSyncContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    private fun projectFile(relative: String): String {
        val candidates = listOf(File(relative), File("../$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Project file not found: $relative")
    }

    @Test
    fun v158KeepsRoom46AndNeverAddsDestructiveMigration() {
        val db = source("com/fush/erp/data/FushDatabase.kt")
        val container = source("com/fush/erp/data/AppContainer.kt")
        assertTrue((Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0) >= 46)
        assertFalse(container.contains("fallbackToDestructiveMigration"))
    }

    @Test
    fun masterDataSyncCoversCoreCompanyReferenceData() {
        val engine = source("com/fush/erp/cloud/MasterDataCloudSyncEngine.kt")
        listOf(
            "fush_md_currencies",
            "fush_md_units",
            "fush_md_warehouses",
            "fush_md_items",
            "fush_md_item_unit_conversions",
            "fush_md_customers",
            "fush_md_suppliers",
        ).forEach { assertTrue("Missing cloud scope $it", engine.contains(it)) }
        assertTrue(engine.contains("skippedBaseline"))
        assertTrue(engine.contains("conflicts"))
        assertTrue(engine.contains("canWrite = true"))
        assertFalse(engine.contains("role in setOf(\"OWNER\", \"ADMIN\")"))
        assertTrue(engine.contains("previous != null && localHash == previous"))
        assertTrue(engine.contains("optNullableString(\"barcode\")"))
    }

    @Test
    fun foregroundAutoSyncAndManualSyncAreBothPresent() {
        val home = source("com/fush/erp/ui/screens/HomeShell.kt")
        val screen = source("com/fush/erp/ui/screens/CloudSyncScreen.kt")
        assertTrue(home.contains("syncMasterData(user)"))
        assertTrue(home.contains("delay(60_000L)"))
        assertTrue(screen.contains("cloud_master_data_sync_now"))
        assertTrue(screen.contains("syncMasterData(user)"))
    }

    @Test
    fun cloudSqlUsesRlsAndNoHardDeleteGrant() {
        val sql = projectFile("cloud/supabase/003_master_data_sync.sql")
        assertTrue(sql.contains("enable row level security"))
        assertTrue(sql.contains("fush_is_org_member"))
        assertTrue(sql.contains("fush_has_org_role"))
        assertFalse(sql.contains("grant select, insert, update, delete", ignoreCase = true))
    }
}
