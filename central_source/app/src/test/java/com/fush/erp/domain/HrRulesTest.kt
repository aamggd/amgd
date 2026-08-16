package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HrRulesTest {
    @Test fun practical_observation_is_required_when_course_requires_it() {
        val at = 1_000L
        assertFalse(HrRules.trainingIsValid("PASS", false, true, 500L, null, at))
        assertTrue(HrRules.trainingIsValid("PASS", true, true, 500L, null, at))
    }

    @Test fun expired_training_and_authorization_are_rejected() {
        val at = 2_000L
        assertFalse(HrRules.trainingIsValid("PASS", true, true, 500L, 1_999L, at))
        assertFalse(HrRules.authorizationIsValid("ACTIVE", 500L, 1_999L, at))
        assertTrue(HrRules.authorizationIsValid("ACTIVE", 500L, 2_000L, at))
    }

    @Test fun failed_training_or_revoked_authorization_are_rejected() {
        assertFalse(HrRules.trainingIsValid("FAIL", true, true, 500L, null, 1_000L))
        assertFalse(HrRules.authorizationIsValid("REVOKED", 500L, null, 1_000L))
    }

    @Test fun employee_identity_can_be_assigned_or_changed_before_financial_movement() {
        assertTrue(HrRules.employeeIdentityChangeAllowed(null, 10L, false))
        assertTrue(HrRules.employeeIdentityChangeAllowed(10L, 11L, false))
    }

    @Test fun same_employee_identity_remains_valid_after_financial_movement() {
        assertTrue(HrRules.employeeIdentityChangeAllowed(10L, 10L, true))
    }

    @Test fun employee_identity_cannot_move_to_another_employee_after_financial_movement() {
        assertFalse(HrRules.employeeIdentityChangeAllowed(10L, 11L, true))
    }
}
