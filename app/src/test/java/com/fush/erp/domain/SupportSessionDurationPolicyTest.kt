package com.fush.erp.domain

import org.junit.Assert.*
import org.junit.Test

class SupportSessionDurationPolicyTest {
    private val settings = SessionTimeoutSettings(
        automaticLogoutEnabled = true,
        idleTimeoutMinutes = 60L,
        maxSessionMinutes = 60L,
    )

    @Test fun activeSupportGrantIsNotTruncatedByGenericSessionTimeout() {
        val twoHours = 120L * 60_000L
        assertFalse(
            SessionPolicy.shouldExpireSupportShell(
                settings = settings,
                hasActiveSupportSession = true,
                lastActivityAt = 0L,
                now = twoHours,
            )
        )
    }

    @Test fun lockedSupportShellUsesIdleTimeoutAfterGrantEnds() {
        assertFalse(SessionPolicy.shouldExpireSupportShell(settings, false, lastActivityAt = 10_000L, now = 10_000L + 59L * 60_000L))
        assertTrue(SessionPolicy.shouldExpireSupportShell(settings, false, lastActivityAt = 10_000L, now = 10_000L + 60L * 60_000L))
    }

    @Test fun supportGrantBackendStillExpiresAtExactTrustedExpiry() {
        val start = 1_000_000L
        val session = com.fush.erp.data.entity.SupportSessionEntity(
            ticketId = 1L,
            companyId = "LOCAL",
            supportUserId = 2L,
            activatedBy = 1L,
            startedAt = start,
            expiresAt = start + 360L * 60_000L,
        )
        assertTrue(SupportPolicy.isActive(session, session.expiresAt - 1L))
        assertFalse(SupportPolicy.isActive(session, session.expiresAt))
    }

    @Test fun allAuthorizedSupportDurationsExpireExactlyAtSelectedTime() {
        val start = 5_000_000L
        for (minutes in SupportPolicy.quickDurationsMinutes) {
            val end = SupportPolicy.expiresAt(start, minutes)
            assertEquals(start + minutes * 60_000L, end)
            val session = com.fush.erp.data.entity.SupportSessionEntity(
                ticketId = 1L, companyId = "LOCAL", supportUserId = 2L, activatedBy = 1L,
                startedAt = start, expiresAt = end,
            )
            assertTrue("$minutes minute grant must still be active immediately before expiry", SupportPolicy.isActive(session, end - 1L))
            assertFalse("$minutes minute grant must expire exactly at expiry", SupportPolicy.isActive(session, end))
        }
    }
}
