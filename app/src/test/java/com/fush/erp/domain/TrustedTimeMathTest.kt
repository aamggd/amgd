package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedTimeMathTest {
    @Test fun monotonicWallIgnoresDeviceWallChanges() {
        assertEquals(1_000_030_000L, TrustedTimeMath.monotonicWall(1_000_000_000L, 50_000L, 80_000L))
    }

    @Test fun wallDriftBeyondToleranceIsDetected() {
        val trusted = 1_000_000_000L
        assertFalse(TrustedTimeMath.isWallDriftSuspicious(trusted + 60_000L, trusted, 120_000L))
        assertTrue(TrustedTimeMath.isWallDriftSuspicious(trusted + 180_001L, trusted, 120_000L))
        assertTrue(TrustedTimeMath.isWallDriftSuspicious(trusted - 180_001L, trusted, 120_000L))
    }
}
