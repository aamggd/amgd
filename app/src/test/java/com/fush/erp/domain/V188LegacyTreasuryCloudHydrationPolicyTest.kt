package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V188LegacyTreasuryCloudHydrationPolicyTest {
    @Test
    fun onlyHistoricalBlockedTreasurySourcesUseCompatibilityHydration() {
        val expected = setOf(
            "TREASURY_TRANSFER",
            "TREASURY_RECEIPT",
            "TREASURY_PAYMENT",
            "TREASURY_EXPENSE",
            "TREASURY_INCOME",
        )
        assertEquals(expected, LegacyTreasuryCloudHydrationPolicy.historicalSourceTypes)
        expected.forEach { assertTrue(LegacyTreasuryCloudHydrationPolicy.requiresCompatibilityHydration(it.lowercase())) }
        assertFalse(LegacyTreasuryCloudHydrationPolicy.requiresCompatibilityHydration("TRANSFER"))
        assertFalse(LegacyTreasuryCloudHydrationPolicy.requiresCompatibilityHydration("CUSTOMER_RECEIPT"))
        assertFalse(LegacyTreasuryCloudHydrationPolicy.requiresCompatibilityHydration("MANUAL"))
    }

    @Test
    fun stagingAliasIsDedicatedAndNotManual() {
        assertEquals("CLOUD_LEGACY_TREASURY_HYDRATION", LegacyTreasuryCloudHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE)
        assertFalse(LegacyTreasuryCloudHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE.equals("MANUAL", ignoreCase = true))
        assertTrue(LegacyTreasuryCloudHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE in AccountingPostingIdempotencyPolicy.registeredSourceTypes)
        assertFalse(LegacyTreasuryCloudHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE in AccountingPostingIdempotencyPolicy.blockedUnstableSourceTypes)
    }

    @Test
    fun historicalCloudSourceIdIsDeterministicAndFallsBackToEntryNo() {
        assertEquals(
            "cloud-legacy:treasury_expense:EV-20260901-120000-ABC123",
            LegacyTreasuryCloudHydrationPolicy.stableHistoricalSourceId(
                " treasury_expense ",
                "ev-20260901-120000-abc123",
                "ignored",
            )
        )
        assertEquals(
            "cloud-legacy:treasury_transfer:TV-20260901-120000-XYZ789",
            LegacyTreasuryCloudHydrationPolicy.stableHistoricalSourceId(
                "TREASURY_TRANSFER",
                "",
                "tv-20260901-120000-xyz789",
            )
        )
    }
}
