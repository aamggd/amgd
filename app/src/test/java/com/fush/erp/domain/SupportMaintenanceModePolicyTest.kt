package com.fush.erp.domain

import com.fush.erp.data.entity.SupportSessionEntity
import org.junit.Assert.*
import org.junit.Test

class SupportMaintenanceModePolicyTest {
    @Test fun onlyBoundedSupportDurationsAreAllowed() {
        val start = 1_000_000L
        assertEquals(start + 60L * 60_000L, SupportPolicy.expiresAt(start, 60L))
        assertEquals(start + 360L * 60_000L, SupportPolicy.expiresAt(start, 360L))
        assertEquals(start + 1440L * 60_000L, SupportPolicy.expiresAt(start, 1440L))
        runCatching { SupportPolicy.expiresAt(start, 30L) }.onSuccess { fail("30 minutes must be rejected") }
        runCatching { SupportPolicy.expiresAt(start, 2880L) }.onSuccess { fail("48 hours must be rejected") }
    }

    @Test fun sessionMustBeBoundedActiveUnrevokedUnclosedAndUnexpired() {
        val base = SupportSessionEntity(
            id = 1, ticketId = 1, companyId = "C1", supportUserId = 2, activatedBy = 1,
            startedAt = 1_000L, expiresAt = 1_000L + 60L * 60_000L
        )
        assertTrue(SupportPolicy.isActive(base, 2_000L))
        assertFalse(SupportPolicy.isActive(base, base.expiresAt))
        assertFalse(SupportPolicy.isActive(base.copy(status = "CLOSED", closedAt = 1_500), 2_000L))
        assertFalse(SupportPolicy.isActive(base.copy(status = "REVOKED", revokedAt = 1_500), 2_000L))
        assertFalse(SupportPolicy.isActive(base.copy(expiresAt = Long.MAX_VALUE), 2_000L))
        assertFalse(SupportPolicy.isActive(base.copy(expiresAt = base.startedAt + 1_441L * 60_000L), 2_000L))
    }

    @Test fun adminTemporaryCleanupSessionIsStrictlyOneHourAndSelfActivated() {
        val start = 10_000L
        val session = SupportSessionEntity(
            id = 9, ticketId = 3, companyId = "LOCAL", supportUserId = 7, activatedBy = 7,
            startedAt = start, expiresAt = start + 60L * 60_000L
        )
        assertTrue(SupportPolicy.isAdminTemporaryTestCleanupSession(session, 7, start + 1L))
        assertFalse(SupportPolicy.isAdminTemporaryTestCleanupSession(session, 8, start + 1L))
        assertFalse(SupportPolicy.isAdminTemporaryTestCleanupSession(session.copy(activatedBy = 6), 7, start + 1L))
        assertFalse(SupportPolicy.isAdminTemporaryTestCleanupSession(session.copy(expiresAt = start + 61L * 60_000L), 7, start + 1L))
        assertFalse(SupportPolicy.isAdminTemporaryTestCleanupSession(session, 7, session.expiresAt))
    }

    @Test fun supportPermissionSetIsSeparate() {
        assertEquals(6, SupportPermissions.all.size)
        assertTrue(SupportPermissions.VIEW in SupportPermissions.all)
        assertTrue(SupportPermissions.CORRECT_DATA in SupportPermissions.all)
        assertTrue(SupportPermissions.TEST_DATA_DELETE in SupportPermissions.all)
        assertFalse(SecurityPermissions.ACCOUNTING_POST in SupportPermissions.all)
        assertFalse(SecurityPermissions.INVENTORY_ADJUST in SupportPermissions.all)
    }
}
