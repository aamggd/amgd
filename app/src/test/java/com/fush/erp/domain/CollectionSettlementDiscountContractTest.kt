package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionSettlementDiscountContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun receiptPersistenceAndMigrationStoreDiscountExplicitly() {
        val entities = source("com/fush/erp/data/entity/SalesEntities.kt")
        val migration = source("com/fush/erp/data/CustomerSettlementDiscountMigration.kt")
        val database = source("com/fush/erp/data/FushDatabase.kt")
        assertTrue(entities.contains("val discountOriginal: Double = 0.0"))
        assertTrue(entities.contains("val discountBase: Double = 0.0"))
        assertTrue(entities.contains("val discountReason: String = \"\""))
        assertTrue(migration.contains("Migration(38, 39)"))
        assertTrue(migration.contains("ALTER TABLE customer_receipts ADD COLUMN discountOriginal"))
        assertTrue(migration.contains("ALTER TABLE customer_receipt_allocations ADD COLUMN discountBase"))
        val schemaVersion = Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)")
            .find(database)?.groupValues?.get(1)?.toInt() ?: error("Database schema version not found")
        assertTrue(schemaVersion >= 40)
    }

    @Test
    fun outstandingUsesCashPlusDiscountWhileCommissionUsesCashOnly() {
        val dao = source("com/fush/erp/data/dao/SalesDaos.kt")
        val service = source("com/fush/erp/domain/SalesService.kt")
        assertTrue(dao.contains("SUM(amountBase + discountBase)"))
        assertTrue(dao.contains("SUM(cra.amountBase + cra.discountBase)"))
        assertTrue(service.contains("settledBaseForInvoice(invoiceId)"))
        assertTrue(service.contains("val totalCollected = db.salesDao().receivedBaseForInvoice(invoice.id)"))
        assertTrue(service.contains("discountBase is intentionally excluded"))
    }

    @Test
    fun collectionJournalSeparatesCashDiscountReceivableAndCashOnlyFx() {
        val service = source("com/fush/erp/domain/SalesService.kt")
        assertTrue(service.contains("DraftJournalLine(treasuryAccount.id, treasuryCashBase, 0.0)"))
        assertTrue(service.contains("db.accountDao().byCode(\"4110\")"))
        assertTrue(service.contains("DraftJournalLine(settlementDiscount.id, discountBase, 0.0)"))
        assertTrue(service.contains("DraftJournalLine(receivables.id, 0.0, settledReceivableBase)"))
        assertTrue(service.contains("val fxDifference = treasuryCashBase - cashReceivableBase"))
    }

    @Test
    fun discountRequiresDedicatedPermissionReasonAndAudit() {
        val service = source("com/fush/erp/domain/SalesService.kt")
        val permissions = source("com/fush/erp/domain/SecurityPolicy.kt")
        assertTrue(permissions.contains("COLLECTION_DISCOUNT_POST"))
        assertTrue(service.contains("SecurityPermissions.COLLECTION_DISCOUNT_POST"))
        assertTrue(service.contains("سبب خصم التحصيل مطلوب"))
        assertTrue(service.contains("CUSTOMER_COLLECTION_DISCOUNT"))
    }

    @Test
    fun reversalRestoresCashAndDiscountSettlement() {
        val service = source("com/fush/erp/domain/SalesService.kt")
        assertTrue(service.contains("discountOriginal = -original.discountOriginal"))
        assertTrue(service.contains("discountBase = -original.discountBase"))
        assertTrue(service.contains("restoredReceivableBase = allocations.sumOf { it.amountBase + it.discountBase }"))
    }
}
