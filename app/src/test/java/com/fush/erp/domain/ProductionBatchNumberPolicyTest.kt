package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductionBatchNumberPolicyTest {
    @Test fun productCodeDrivesPrefixForAnyProductSize() {
        assertEquals("FG000060", ProductionBatchNumberPolicy.productPrefix("FG-000060", 60))
        assertEquals("FG000100", ProductionBatchNumberPolicy.productPrefix("FG-000100", 100))
        assertEquals("FG000200", ProductionBatchNumberPolicy.productPrefix("FG-000200", 200))
    }

    @Test fun prefixDoesNotDependOnMlNamingConvention() {
        assertEquals("FUSHGELX", ProductionBatchNumberPolicy.productPrefix("FUSH-GEL-X", 999))
    }

    @Test fun blankCodeHasStableIdFallback() {
        assertEquals("F42", ProductionBatchNumberPolicy.productPrefix("   ", 42))
    }
}
