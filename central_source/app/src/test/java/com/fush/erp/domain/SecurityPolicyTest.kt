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
        assertFalse(
            SessionPolicy.shouldExpire(
                settings = settings,
                sessionStartedAt = 0L,
                lastActivityAt = 0L,
                now = 10L * 24L * 60L * 60_000L
            )
        )
    }

    @Test
    fun customSessionTimeoutCanBeEnabled() {
        val settings = SessionTimeoutSettings(
            automaticLogoutEnabled = true,
            idleTimeoutMinutes = 30L,
            maxSessionMinutes = 480L
        )
        assertFalse(SessionPolicy.shouldExpire(settings, 0L, 29L * 60_000L, 30L * 60_000L - 1L))
        assertTrue(SessionPolicy.shouldExpire(settings, 0L, 0L, 30L * 60_000L))
        assertTrue(SessionPolicy.shouldExpire(settings, 0L, 479L * 60_000L, 480L * 60_000L))
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
