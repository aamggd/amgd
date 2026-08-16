package com.fush.erp.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class ControlAccountPolicyTest {
    @Test(expected = IllegalArgumentException::class)
    fun manualPostingToReceivables_isBlocked() {
        ControlAccountPolicy.requireManualPostingAllowed("1300")
    }

    @Test(expected = IllegalArgumentException::class)
    fun manualPostingToPayables_isBlocked() {
        ControlAccountPolicy.requireManualPostingAllowed("2100")
    }

    @Test
    fun ordinaryExpenseAccount_isAllowed() {
        ControlAccountPolicy.requireManualPostingAllowed("6500")
        assertTrue(true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun genericCustomerVoucherToTradeControl_isBlocked() {
        ControlAccountPolicy.requireGenericVoucherAllowed("1300")
    }
}
