package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceMathTest {
    @Test fun preventiveComplianceTargetIsCalculated() {
        assertEquals(95.0, MaintenanceMath.preventiveCompliancePct(20, 19), 0.00001)
    }

    @Test fun operationRequiresAllMaintenanceControls() {
        assertTrue(MaintenanceMath.assetCanOperate("ACTIVE", 0, false, false, true))
        assertFalse(MaintenanceMath.assetCanOperate("OUT_OF_SERVICE", 0, false, false, true))
        assertFalse(MaintenanceMath.assetCanOperate("ACTIVE", 1, false, false, true))
        assertFalse(MaintenanceMath.assetCanOperate("ACTIVE", 0, true, false, true))
        assertFalse(MaintenanceMath.assetCanOperate("ACTIVE", 0, false, true, true))
        assertFalse(MaintenanceMath.assetCanOperate("ACTIVE", 0, false, false, false))
    }

    @Test fun recurringBreakdownIsFlaggedAfterPriorOccurrence() {
        assertFalse(MaintenanceMath.recurringBreakdown(0))
        assertTrue(MaintenanceMath.recurringBreakdown(1))
    }
}
