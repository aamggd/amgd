package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedTimeContractTest {
    private fun source(path: String): String {
        val f = listOf(File("src/main/java/$path"), File("app/src/main/java/$path")).firstOrNull { it.isFile }
            ?: error("Missing source: $path")
        return f.readText()
    }

    @Test fun securityUsesMonotonicAndTrustedClock() {
        val security = source("com/fush/erp/domain/SecurityService.kt")
        val policy = source("com/fush/erp/domain/SecurityPolicy.kt")
        assertTrue(security.contains("TrustedTimeService.elapsedRealtime()"))
        assertTrue(security.contains("TrustedTimeService.failureReason()"))
        assertTrue(policy.contains("TrustedTimeService.elapsedRealtime()"))
        assertFalse(security.contains("System.currentTimeMillis()"))
    }

    @Test fun futureGuardsRequireTrustedTime() {
        val production = source("com/fush/erp/domain/ProductionService.kt")
        val sales = source("com/fush/erp/domain/SalesService.kt")
        val accounting = source("com/fush/erp/domain/AccountingService.kt")
        assertTrue(production.contains("TrustedTimeService.requireTrustedNow()"))
        assertTrue(sales.contains("TrustedTimeService.requireTrustedNow()"))
        assertTrue(accounting.contains("TrustedTimeService.requireTrustedNow()"))
    }
}
