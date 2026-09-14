package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupRoomMigrationParityContractTest {
    @Test
    fun startupBootstrapRegistersMigrationIntoCurrentSchema() {
        val db = File("src/main/java/com/fush/erp/data/FushDatabase.kt").readText()
        val appContainer = File("src/main/java/com/fush/erp/data/AppContainer.kt").readText()
        val bootstrap = File("src/main/java/com/fush/erp/data/AccountingWaveBRoomBootstrap.kt").readText()

        val current = Regex("""FUSH_DB_SCHEMA_VERSION\s*=\s*(\d+)""")
            .find(db)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        assertTrue("current schema must be positive", current > 1)

        val expected = Regex("""MIGRATION_${current - 1}_${current}(?:_[A-Z0-9_]+)?""")
        assertTrue("AppContainer must register the migration into schema $current", expected.containsMatchIn(appContainer))
        assertTrue("startup bootstrap must register the migration into schema $current", expected.containsMatchIn(bootstrap))
    }
}
