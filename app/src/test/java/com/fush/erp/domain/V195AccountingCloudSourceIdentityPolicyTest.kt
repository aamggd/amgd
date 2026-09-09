package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V195AccountingCloudSourceIdentityPolicyTest {
    @Test
    fun manualJournalMayRemainWithoutSourceId() {
        assertNull(
            AccountingCloudHydrationSourceIdentityPolicy.sourceIdForHydration(
                sourceType = "MANUAL",
                sourceRef = "",
                entryNo = "JE-MANUAL-1",
                resolvedLocalSourceId = null,
            )
        )
    }

    @Test
    fun resolvedLocalBusinessIdentityAlwaysWinsForNormalSources() {
        assertEquals(
            "42",
            AccountingCloudHydrationSourceIdentityPolicy.sourceIdForHydration(
                sourceType = "SALE",
                sourceRef = "65",
                entryNo = "JE-65",
                resolvedLocalSourceId = "42",
            )
        )
    }

    @Test
    fun unresolvedOperationalSourceNeverBecomesNull() {
        val id = AccountingCloudHydrationSourceIdentityPolicy.sourceIdForHydration(
            sourceType = "SALES_COMMISSION",
            sourceRef = "JE-COM-20260901-0001",
            entryNo = "JE-COM-20260901-0001",
            resolvedLocalSourceId = null,
        )
        assertEquals("cloud-sync:sales_commission:JE-COM-20260901-0001", id)
    }

    @Test
    fun entryNumberIsFinalPortableFallbackWhenCloudSourceRefIsBlank() {
        val id = AccountingCloudHydrationSourceIdentityPolicy.sourceIdForHydration(
            sourceType = "INVENTORY_COUNT",
            sourceRef = "",
            entryNo = "JE-INVCOUNT-20260904-1",
            resolvedLocalSourceId = null,
        )
        assertEquals("cloud-sync:inventory_count:JE-INVCOUNT-20260904-1", id)
    }

    @Test
    fun everyRegisteredNonManualSourceHasANonBlankHydrationIdentityEvenWhenUnresolved() {
        AccountingIntegrationContract.events
            .filterNot { it.sourceType == "MANUAL" }
            .forEach { spec ->
                val id = AccountingCloudHydrationSourceIdentityPolicy.sourceIdForHydration(
                    sourceType = spec.sourceType,
                    sourceRef = "",
                    entryNo = "JE-${spec.sourceType}-TEST",
                    resolvedLocalSourceId = null,
                )
                assertNotNull(spec.sourceType, id)
                assertTrue(spec.sourceType, id!!.isNotBlank())
            }
    }

    @Test
    fun legacyTreasuryKeepsDedicatedCloudLegacyIdentity() {
        val id = AccountingCloudHydrationSourceIdentityPolicy.sourceIdForHydration(
            sourceType = "TREASURY_PAYMENT",
            sourceRef = "PV-20260904-0001",
            entryNo = "JE-PV-20260904-0001",
            resolvedLocalSourceId = null,
        )
        assertEquals("cloud-legacy:treasury_payment:PV-20260904-0001", id)
    }
    @Test
    fun everyDatabaseRegisteredCloudSourceHasANonBlankHydrationIdentity() {
        AccountingPostingIdempotencyPolicy.registeredSourceTypes
            .filterNot { it == "MANUAL" || it == LegacyTreasuryCloudHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE }
            .forEach { sourceType ->
                val id = AccountingCloudHydrationSourceIdentityPolicy.sourceIdForHydration(
                    sourceType = sourceType,
                    sourceRef = "",
                    entryNo = "JE-$sourceType-TEST",
                    resolvedLocalSourceId = null,
                )
                assertNotNull(sourceType, id)
                assertTrue(sourceType, id!!.isNotBlank())
            }
    }

    @Test
    fun v194ProductionBackupSourceFamiliesNeverHydrateWithNullIdentity() {
        val sourceTypes = setOf(
            "SALE", "CUSTOMER_RECEIPT", "SALES_RETURN", "SALES_COMMISSION",
            "COMMISSION_REVERSAL", "RECEIPT_COMMISSION_REVERSAL", "PURCHASE",
            "PURCHASE_RETURN", "OPENING_STOCK", "PRODUCTION_ISSUE",
            "PRODUCTION_LABOR", "PRODUCTION_RECEIPT", "PROD_ISSUE_CORR",
            "PROD_COST_CORR", "PROD_OUTPUT_CORR", "REVERSAL", "ADJUSTMENT",
            "EMPLOYEE_PAYMENT", "EXPENSE_PAYMENT", "TRANSFER", "TREASURY_PAYMENT",
            "TREASURY_RECEIPT", "TREASURY_EXPENSE"
        )
        sourceTypes.forEach { sourceType ->
            assertTrue(sourceType, sourceType in AccountingPostingIdempotencyPolicy.registeredSourceTypes)
            val id = AccountingCloudHydrationSourceIdentityPolicy.sourceIdForHydration(
                sourceType = sourceType,
                sourceRef = "",
                entryNo = "JE-$sourceType-V194",
                resolvedLocalSourceId = null,
            )
            assertNotNull(sourceType, id)
            assertTrue(sourceType, id!!.isNotBlank())
        }
    }

}
