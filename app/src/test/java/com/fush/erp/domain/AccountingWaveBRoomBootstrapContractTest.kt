package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingWaveBRoomBootstrapContractTest {
    @Test
    fun startupBootstrapIncludesLatestRoomMigration() {
        val source = File("src/main/java/com/fush/erp/data/AccountingWaveBRoomBootstrap.kt").readText()
        val previous = source.indexOf("MIGRATION_42_43_VENDOR_SUPPORT_PROVISIONING")
        val latest = source.indexOf("MIGRATION_43_44_VENDOR_SUPPORT_LIFECYCLE")
        assertTrue("42->43 migration must be registered", previous >= 0)
        assertTrue("43->44 migration must be registered in startup bootstrap", latest > previous)
        val shipment = source.indexOf("MIGRATION_48_49_SHIPMENT_SALES_LINE_LINK")
        val fx = source.indexOf("MIGRATION_49_50_LOCAL_FX_ENGINE")
        assertTrue("48->49 migration must be registered in startup bootstrap", shipment >= 0)
        assertTrue("49->50 migration must be registered in startup bootstrap before Room opens", fx > shipment)
    }
}
