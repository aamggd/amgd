package com.fush.erp.domain

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SupportMaintenanceModeContractTest {
    private fun source(path: String): String {
        val f = listOf(File("src/main/java/$path"), File("app/src/main/java/$path")).firstOrNull { it.isFile }
            ?: error("Missing source: $path")
        return f.readText()
    }

    @Test fun supportRoleHasNoOrdinaryOperationalPermissionsByDefault() {
        val catalog = source("com/fush/erp/domain/PermissionCatalog.kt")
        val block = catalog.substringAfter("\"FUSH_SUPPORT\" to setOf(").substringBefore("\n        )")
        assertTrue(block.contains("SUPPORT_VIEW"))
        assertTrue(block.contains("SUPPORT_REPAIR"))
        assertFalse(block.contains("ACCOUNTING_POST"))
        assertFalse(block.contains("SALES_POST"))
        assertFalse(block.contains("INVENTORY_ADJUST"))
    }

    @Test fun serviceRequiresTicketSessionRoleAndPermission() {
        val service = source("com/fush/erp/domain/SupportService.kt")
        assertTrue(service.contains("requireActiveContext"))
        assertTrue(service.contains("user.role == SupportPolicy.SUPPORT_ROLE"))
        assertTrue(service.contains("session.supportUserId == userId"))
        assertTrue(service.contains("SupportPolicy.isActive(session"))
        assertTrue(service.contains("ticket.status != \"CLOSED\""))
        assertTrue(service.contains("hasPermission(user.role, requiredPermission)"))
    }

    @Test fun repairSavesSnapshotAndValidationAndNoGenericDbEditorExists() {
        val service = source("com/fush/erp/domain/SupportService.kt")
        assertTrue(service.contains("insertSnapshot("))
        assertTrue(service.contains("insertValidation("))
        assertTrue(service.contains("insertAudit("))
        assertTrue(service.contains("transitionStagingToPosted"))
        assertTrue(service.contains("لا يوجد تعديل Database عام"))
        // v189+ intentionally contains two narrowly-scoped test-data cleanup commands.
        // Keep the contract focused on absence of a generic DB editor instead of banning
        // every targeted DELETE used by those protected commands.
        assertTrue(service.contains("SupportPermissions.TEST_DATA_DELETE"))
        assertTrue(service.contains("DELETE_TEST_SALES_INVOICE_BUNDLE"))
        assertTrue(service.contains("DELETE_TEST_SHIPMENT_EXPENSE"))
        assertFalse(service.contains("UPDATE journal_lines"))
    }

    @Test fun supportAuditAndSnapshotsAreImmutableAtDatabaseLayer() {
        val migration = source("com/fush/erp/data/SupportMigrations.kt")
        assertTrue(migration.contains("support_audit_log"))
        assertTrue(migration.contains("support_snapshots"))
        assertTrue(migration.contains("BEFORE UPDATE"))
        assertTrue(migration.contains("BEFORE DELETE"))
        val dao = source("com/fush/erp/data/dao/SupportDao.kt")
        assertFalse(dao.contains("updateAudit"))
        assertFalse(dao.contains("deleteAudit"))
        assertFalse(dao.contains("updateSnapshot"))
        assertFalse(dao.contains("deleteSnapshot"))
    }
    @Test fun safeRepairRequiresSemanticExpectedJournalBeforePosting() {
        val service = source("com/fush/erp/domain/SupportService.kt")
        val semantic = source("com/fush/erp/domain/JournalSemanticExpectation.kt")
        val comparator = source("com/fush/erp/domain/JournalSemanticComparisonPolicy.kt")
        assertTrue(service.contains("JournalSemanticExpectation(db).compare"))
        assertTrue(service.indexOf("JournalSemanticExpectation(db).compare") < service.indexOf("transitionStagingToPosted"))
        assertTrue(service.contains("SEMANTIC_SOURCE_MISMATCH"))
        assertTrue(comparator.contains("lineMismatch"))
        assertTrue(comparator.contains("entryDate expected="))
        assertTrue(comparator.contains("currency expected="))
        assertTrue(comparator.contains("exchangeRate expected="))
        assertTrue(semantic.contains("freeBaseQty="))
        assertTrue(semantic.contains("4250"))
        assertTrue(semantic.contains("6750"))
    }

    @Test fun failedRepairAttemptIsPersistedOutsideBusinessMutationTransaction() {
        val service = source("com/fush/erp/domain/SupportService.kt")
        assertTrue(service.contains("recordFailedRepairAttempt"))
        assertTrue(service.contains("BUSINESS_MUTATION_ROLLED_BACK"))
        assertTrue(service.contains("action = \"${'$'}{command}_FAILED\""))
        assertTrue(service.contains("Durable evidence is intentionally outside"))
    }

    @Test fun supportActivationRequiresRecentReauthenticationAndNoManualForeverMode() {
        val service = source("com/fush/erp/domain/SupportService.kt")
        val ui = source("com/fush/erp/ui/screens/SupportCenterScreen.kt")
        assertTrue(service.contains("requireRecentReauthentication(actorUserId, \"SUPPORT_SESSION_ACTIVATE\")"))
        assertTrue(service.contains("durationMinutes in SupportPolicy.quickDurationsMinutes"))
        assertFalse(ui.contains("حتى الإلغاء"))
        assertTrue(ui.contains("ReauthenticationDialog"))
    }

    @Test fun adminTemporaryCleanupDoesNotUnlockGeneralRepairTools() {
        val service = source("com/fush/erp/domain/SupportService.kt")
        val ui = source("com/fush/erp/ui/screens/SupportCenterScreen.kt")
        assertTrue(service.contains("activateAdminTemporaryTestCleanupSession"))
        assertTrue(service.contains("ADMIN_TEMP_TEST_CLEANUP_ACTIVATE"))
        assertTrue(service.contains("requireTestCleanupContext"))
        assertTrue(service.contains("isAdminTemporaryTestCleanupSession"))
        assertTrue(service.contains("SupportPermissions.TEST_DATA_DELETE"))
        assertTrue(ui.contains("تنظيف اختبار محلي — ساعة (لا يحتاج FSP2)"))
        assertTrue(ui.contains("deleteTestSalesInvoiceBundle"))
        assertTrue(ui.contains("deleteTestShipmentExpense"))
        assertFalse(ui.contains("ADMIN مؤقت — Repost"))
    }

    @Test fun localAdminCannotProvisionOrAssignVendorSupportRole() {
        val security = source("com/fush/erp/domain/SecurityService.kt")
        val ui = source("com/fush/erp/ui/screens/SecurityScreens.kt")
        assertTrue(security.contains("Vendor Identity"))
        assertTrue(security.contains("role.code != SupportPolicy.SUPPORT_ROLE"))
        assertTrue(security.contains("target.role != SupportPolicy.SUPPORT_ROLE"))
        assertTrue(ui.contains("it.code != SupportPolicy.SUPPORT_ROLE"))
    }

    @Test fun v137StoresTreasuryProvenanceForExactCashJournalValidation() {
        val migration = source("com/fush/erp/data/SupportMigrations.kt")
        val sales = source("com/fush/erp/data/entity/SalesEntities.kt")
        val purchases = source("com/fush/erp/data/entity/PurchaseEntities.kt")
        assertTrue(migration.contains("MIGRATION_41_42_SUPPORT_JOURNAL_PROVENANCE"))
        assertTrue(migration.contains("ALTER TABLE sales_invoices ADD COLUMN treasuryAccountId"))
        assertTrue(migration.contains("ALTER TABLE customer_receipts ADD COLUMN treasuryAccountId"))
        assertTrue(migration.contains("ALTER TABLE purchase_invoices ADD COLUMN treasuryAccountId"))
        assertTrue(sales.contains("treasuryAccountId: Long? = null"))
        assertTrue(purchases.contains("treasuryAccountId: Long? = null"))
    }

}
