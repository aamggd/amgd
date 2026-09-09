package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditTrailReadContractTest {
    private fun source(path: String): String = listOf(File("src/main/java/$path"), File("app/src/main/java/$path")).first { it.isFile }.readText()

    @Test fun auditTrailUsesDbFilteringUserJoinNewestFirstAndPagination() {
        val s = source("com/fush/erp/data/dao/GovernanceDao.kt")
        assertTrue(s.contains("LEFT JOIN users u ON u.id=ae.userId"))
        assertTrue(s.contains("ORDER BY eventAt DESC, id DESC"))
        assertTrue(s.contains("LIMIT :limit OFFSET :offset"))
        assertTrue(s.contains(":userId IS NULL OR userId = :userId"))
        assertTrue(s.contains(":fromAt IS NULL OR eventAt >= :fromAt"))
        assertTrue(s.contains(":toAt IS NULL OR eventAt <= :toAt"))
        assertTrue(s.contains("lower(referenceDisplay) LIKE"))
    }

    @Test fun auditEntityHasNoUpdateOrDeleteApiAndDatabaseTriggersRejectMutation() {
        val s = source("com/fush/erp/data/dao/GovernanceDao.kt")
        val auditPart = s.substringAfter("suspend fun insertAudit(row: AuditEventEntity)")
        assertFalse(auditPart.contains("updateAudit("))
        assertFalse(auditPart.contains("deleteAudit("))
        val container = source("com/fush/erp/data/AppContainer.kt")
        assertTrue(container.contains("trg_audit_events_no_update"))
        assertTrue(container.contains("trg_audit_events_no_delete"))
        assertTrue(container.contains("RAISE(ABORT"))
    }

    @Test fun freeQuantityMigrationDoesNotRewriteHistoricalAuditEvents() {
        val migration = source("com/fush/erp/data/SalesFreeQuantityMigration.kt")
        assertFalse(migration.contains("audit_events"))
    }
}
