package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorSupportLifecycleMigrationContractTest {
    private fun migrationSource(): String =
        File("src/main/java/com/fush/erp/data/SupportMigrations.kt").readText()

    @Test
    fun v43To44DropsRenamedTableBeforeRecreatingLifecycleIndexes() {
        val source = migrationSource()
        val migrationStart = source.indexOf("MIGRATION_43_44_VENDOR_SUPPORT_LIFECYCLE")
        val section = source.substring(migrationStart)
        val dropOld = section.indexOf("DROP TABLE vendor_support_identities_v138")
        val createSupportUserIndex = section.indexOf(
            "CREATE INDEX IF NOT EXISTS index_vendor_support_identities_supportUserId ON vendor_support_identities(supportUserId)"
        )
        val createBindingIndex = section.indexOf(
            "CREATE INDEX IF NOT EXISTS index_vendor_support_identities_installationBinding ON vendor_support_identities(installationBinding)"
        )
        val createNonceIndex = section.indexOf(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_vendor_support_identities_packageNonce ON vendor_support_identities(packageNonce)"
        )

        assertTrue("migration section must exist", migrationStart >= 0)
        assertTrue("old v138 table must be dropped", dropOld >= 0)
        assertTrue("supportUserId index must be recreated", createSupportUserIndex > dropOld)
        assertTrue("installationBinding index must be recreated", createBindingIndex > dropOld)
        assertTrue("packageNonce index must be recreated", createNonceIndex > dropOld)
    }
}
