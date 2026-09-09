package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditTrailRoomQueryContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relative")
    }

    @Test fun auditTrailCteAvoidsReservedActionAlias() {
        val dao = source("com/fush/erp/data/dao/GovernanceDao.kt")
        assertTrue(dao.contains("ae.action AS actionCode"))
        assertTrue(dao.contains("lower(actionCode)"))
        assertFalse(dao.contains("ae.action AS action,"))
    }

    @Test fun readModelMapsActionCodeBackToPublicActionProperty() {
        val model = source("com/fush/erp/data/entity/AuditTrailReadModels.kt")
        assertTrue(model.contains("@ColumnInfo(name = \"actionCode\") val action: String"))
    }
}
