package com.fush.erp.domain

import org.junit.Assert.*
import org.junit.Test

class SecurityPolicyTest {
    @Test
    fun strongPasswordIsAccepted() {
        assertNull(PasswordPolicy.validate("StrongPass#2026A".toCharArray(), "operator"))
    }

    @Test
    fun weakPasswordIsRejected() {
        assertNotNull(PasswordPolicy.validate("short1!A".toCharArray(), "operator"))
        assertNotNull(PasswordPolicy.validate("alllowercase#2026".toCharArray(), "operator"))
        assertNotNull(PasswordPolicy.validate("ALLUPPERCASE#2026".toCharArray(), "operator"))
        assertNotNull(PasswordPolicy.validate("NoDigitsHere#ABC".toCharArray(), "operator"))
    }

    @Test
    fun passwordContainingUsernameIsRejected() {
        assertNotNull(PasswordPolicy.validate("Operator#2026Strong".toCharArray(), "operator"))
    }

    @Test
    fun fifthFailureLocksForFifteenMinutes() {
        val now = 1_000L
        var attempts = 0
        var lockouts = 0
        var decision = LockoutDecision(0, 0, null)
        repeat(5) {
            decision = LoginLockoutPolicy.onFailure(attempts, lockouts, now)
            attempts = decision.failedAttempts
            lockouts = decision.lockoutCount
        }
        assertEquals(now + 15L * 60_000L, decision.lockedUntil)
    }

    @Test
    fun repeatedLockUsesSixtyMinutes() {
        val now = 5_000L
        val decision = LoginLockoutPolicy.onFailure(4, 1, now)
        assertEquals(now + 60L * 60_000L, decision.lockedUntil)
    }

    @Test
    fun automaticSessionLogoutIsDisabledByDefault() {
        val settings = SessionTimeoutSettings()
        assertFalse(settings.automaticLogoutEnabled)
        assertEquals(5L, settings.idleTimeoutMinutes)
        assertEquals(480L, settings.maxSessionMinutes)
    }

    @Test
    fun normalizePreservesDisabledAutomaticLogout() {
        val settings = SessionTimeoutSettings(
            automaticLogoutEnabled = false,
            idleTimeoutMinutes = 30L,
            maxSessionMinutes = 1_000L
        )
        val normalized = SessionPolicy.normalize(settings)
        assertFalse(normalized.automaticLogoutEnabled)
        assertEquals(30L, normalized.idleTimeoutMinutes)
        assertEquals(1_000L, normalized.maxSessionMinutes)
    }

    @Test
    fun disabledAutomaticLogoutDoesNotExpireNormalUserSession() {
        val settings = SessionTimeoutSettings(
            automaticLogoutEnabled = false,
            idleTimeoutMinutes = 1L,
            maxSessionMinutes = 1L
        )
        val effective = SessionPolicy.effective(settings, "ACCOUNTANT")
        assertFalse(effective.automaticLogoutEnabled)
        assertFalse(SessionPolicy.shouldExpire(settings, "ACCOUNTANT", 0L, 0L, 24L * 60L * 60_000L))
    }

    @Test
    fun disabledAutomaticLogoutDoesNotExpireAdminSession() {
        val settings = SessionTimeoutSettings(
            automaticLogoutEnabled = false,
            idleTimeoutMinutes = 1L,
            maxSessionMinutes = 1L
        )
        val effective = SessionPolicy.effective(settings, "ADMIN")
        assertFalse(effective.automaticLogoutEnabled)
        assertFalse(SessionPolicy.shouldExpire(settings, "ADMIN", 0L, 0L, 24L * 60L * 60_000L))
    }

    @Test
    fun adminSessionUsesStricterIdleAndAbsoluteCapsWhenAutomaticLogoutEnabled() {
        val settings = SessionTimeoutSettings(
            automaticLogoutEnabled = true,
            idleTimeoutMinutes = 30L,
            maxSessionMinutes = 1_000L
        )
        val effective = SessionPolicy.effective(settings, "ADMIN")
        assertTrue(effective.automaticLogoutEnabled)
        assertEquals(3L, effective.idleTimeoutMinutes)
        assertEquals(240L, effective.maxSessionMinutes)
        assertFalse(SessionPolicy.shouldExpire(settings, "ADMIN", 0L, 0L, 3L * 60_000L - 1L))
        assertTrue(SessionPolicy.shouldExpire(settings, "ADMIN", 0L, 0L, 3L * 60_000L))
        assertTrue(SessionPolicy.shouldExpire(settings, "ADMIN", 0L, 239L * 60_000L, 240L * 60_000L))
    }

    @Test
    fun administratorCanOnlyTightenTimeoutsWhenAutomaticLogoutEnabled() {
        val settings = SessionTimeoutSettings(
            automaticLogoutEnabled = true,
            idleTimeoutMinutes = 2L,
            maxSessionMinutes = 120L
        )
        val normal = SessionPolicy.effective(settings, "ACCOUNTANT")
        val admin = SessionPolicy.effective(settings, "ADMIN")
        assertEquals(2L, normal.idleTimeoutMinutes)
        assertEquals(120L, normal.maxSessionMinutes)
        assertEquals(2L, admin.idleTimeoutMinutes)
        assertEquals(120L, admin.maxSessionMinutes)
    }

    @Test
    fun criticalReauthenticationWindowIsFiveMinutes() {
        val verifiedAt = 10_000L
        assertTrue(ReauthenticationPolicy.isFresh(verifiedAt, verifiedAt))
        assertTrue(ReauthenticationPolicy.isFresh(verifiedAt, verifiedAt + 5L * 60_000L))
        assertFalse(ReauthenticationPolicy.isFresh(verifiedAt, verifiedAt + 5L * 60_000L + 1L))
        assertFalse(ReauthenticationPolicy.isFresh(null, verifiedAt))
        assertFalse(ReauthenticationPolicy.isFresh(verifiedAt + 1L, verifiedAt))
    }

    @Test
    fun passwordExpiresAfterSixtyDays() {
        val changedAt = 1_000L
        val beforeExpiry = changedAt + (60L * 24L * 60L * 60_000L) - 1L
        val atExpiry = changedAt + 60L * 24L * 60L * 60_000L
        assertFalse(PasswordPolicy.isExpired(changedAt, beforeExpiry))
        assertTrue(PasswordPolicy.isExpired(changedAt, atExpiry))
        assertTrue(PasswordPolicy.isExpired(null, atExpiry))
    }
}
