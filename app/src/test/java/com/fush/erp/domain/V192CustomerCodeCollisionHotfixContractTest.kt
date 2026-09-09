package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class V192CustomerCodeCollisionHotfixContractTest {
    private fun projectFile(path: String): String = File(path).readText()

    @Test
    fun customerSequenceReconcilesAgainstHydratedCustomerRows() {
        val dao = projectFile("src/main/java/com/fush/erp/data/dao/SalesDaos.kt")
        val numbering = projectFile("src/main/java/com/fush/erp/domain/AutoNumberService.kt")
        assertTrue(dao.contains("suspend fun maxAutomaticCodeValue(): Long"))
        assertTrue(dao.contains("code GLOB 'CUS-[0-9]*'"))
        assertTrue(numbering.contains("maxOf(sequenceValue, persistedCustomerValue) + 1L"))
        assertTrue(numbering.contains("db.customerDao().byCode(candidate) == null"))
    }

    @Test
    fun connectedPhonesReserveCustomerCodeAtCompanyScope() {
        val service = projectFile("src/main/java/com/fush/erp/domain/SalesService.kt")
        assertTrue(service.contains("allocateCompanySafeCustomerCode(createdBy)"))
        assertTrue(service.contains("documentNumberGuard.reserve(createdBy, \"CUSTOMER_MASTER\", candidate)"))
        assertTrue(service.contains("is DocumentNumberReservationResult.InUse -> Unit"))
    }

    @Test
    fun v192MetadataPreservesUpgradeIdentity() {
        val gradle = projectFile("build.gradle.kts")
        assertTrue(gradle.contains("applicationId = \"com.fush.erp.recovery\""))
        val versionCode = Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toInt() ?: 0
        assertTrue(versionCode >= 192)
        assertTrue(gradle.contains("versionName ="))
    }
}
