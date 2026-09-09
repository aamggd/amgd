package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V211FushAiDraftActionsContractTest {
    private fun source(path: String) = File(path).takeIf { it.exists() } ?: File("../$path")

    @Test
    fun parserRecognizesThreeDraftIntents() {
        assertEquals(FushAiIntent.DRAFT_SALES_INVOICE, FushAiIntentParser.detect("جهز مسودة فاتورة للعميل أحمد 10 قطع فوش"))
        assertEquals(FushAiIntent.DRAFT_TREASURY_VOUCHER, FushAiIntentParser.detect("جهز مسودة سند صرف مبلغ 50000"))
        assertEquals(FushAiIntent.DRAFT_PRODUCTION_ORDER, FushAiIntentParser.detect("انشئ مسودة أمر إنتاج 360 قطعة فوش"))
    }

    @Test
    fun draftToolsRequireTheirRealPostingPermissions() {
        val sales = FushAiToolPolicy.allowedTools("USER", setOf(SecurityPermissions.SALES_POST))
        assertTrue(FushAiToolPolicy.DRAFT_SALES_INVOICE in sales)
        assertFalse(FushAiToolPolicy.DRAFT_TREASURY_VOUCHER in sales)
        assertFalse(FushAiToolPolicy.DRAFT_PRODUCTION_ORDER in sales)

        val treasury = FushAiToolPolicy.allowedTools("ACCOUNTANT", setOf(SecurityPermissions.TREASURY_POST))
        assertTrue(FushAiToolPolicy.DRAFT_TREASURY_VOUCHER in treasury)

        val production = FushAiToolPolicy.allowedTools("PRODUCTION", setOf(SecurityPermissions.PRODUCTION_POST))
        assertTrue(FushAiToolPolicy.DRAFT_PRODUCTION_ORDER in production)
    }

    @Test
    fun draftServiceCannotPostOperationalDocuments() {
        val service = source("app/src/main/java/com/fush/erp/domain/FushAiDraftService.kt").readText()
        assertFalse(service.contains("postSale("))
        assertFalse(service.contains("postVoucher("))
        assertFalse(service.contains("createOrder("))
        assertFalse(service.contains("insertMovement"))
        assertTrue(service.contains("fushAiDao().insert"))
        assertTrue(service.contains("FUSH_AI_DRAFT"))
    }

    @Test
    fun room53MigrationRegisteredInBothOpenPaths() {
        val db = source("app/src/main/java/com/fush/erp/data/FushDatabase.kt").readText()
        val container = source("app/src/main/java/com/fush/erp/data/AppContainer.kt").readText()
        val bootstrap = source("app/src/main/java/com/fush/erp/data/AccountingWaveBRoomBootstrap.kt").readText()
        assertTrue((Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0) >= 53)
        assertTrue(db.contains("FushAiDraftEntity::class"))
        assertTrue(container.contains("MIGRATION_52_53_FUSH_AI_DRAFT_ACTIONS"))
        assertTrue(bootstrap.contains("MIGRATION_52_53_FUSH_AI_DRAFT_ACTIONS"))
    }
}
