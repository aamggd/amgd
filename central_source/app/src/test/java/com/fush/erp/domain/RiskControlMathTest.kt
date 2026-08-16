package com.fush.erp.domain

import org.junit.Assert.*
import org.junit.Test

class RiskControlMathTest {
    @Test fun score_and_band_are_consistent() {
        assertEquals(25, RiskControlMath.score(5, 5))
        assertEquals("CRITICAL", RiskControlMath.band(25))
        assertEquals("HIGH", RiskControlMath.band(15))
        assertEquals("MEDIUM", RiskControlMath.band(8))
        assertEquals("LOW", RiskControlMath.band(4))
    }

    @Test fun high_risk_requires_escalation() {
        assertTrue(RiskControlMath.needsEscalation(15))
        assertFalse(RiskControlMath.needsEscalation(14))
    }

    @Test fun maker_cannot_approve_own_request() {
        assertFalse(RiskControlMath.canApprove(7, 7))
        assertTrue(RiskControlMath.canApprove(7, 8))
    }
}
