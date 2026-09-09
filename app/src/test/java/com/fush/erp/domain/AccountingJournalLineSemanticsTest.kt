package com.fush.erp.domain

import org.junit.Assert.assertThrows
import org.junit.Test

class AccountingJournalLineSemanticsTest {
    private val usdDr = AccountingSemanticLine(20000,0,"USD",10000,0,200000000,"A")
    private val eurCr = AccountingSemanticLine(0,20000,"EUR",0,16000,125000000,"B")

    @Test fun mixedTransactionCurrenciesUseFunctionalBalanceOnly() {
        AccountingJournalLineSemantics.validateFunctionalBalance(listOf(usdDr, eurCr))
    }
    @Test fun functionalImbalanceRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountingJournalLineSemantics.validateFunctionalBalance(listOf(usdDr, eurCr.copy(functionalCreditScaled=19999)))
        }
    }
    @Test fun nonBalancingDimensionDoesNotClaimTrialBalance() {
        AccountingJournalLineSemantics.validateDimensionBalance(listOf(usdDr, eurCr), DimensionBalanceMode.NON_BALANCING)
    }
    @Test fun balancingDimensionRequiresPerKeyBalance() {
        assertThrows(IllegalArgumentException::class.java) {
            AccountingJournalLineSemantics.validateDimensionBalance(listOf(usdDr, eurCr), DimensionBalanceMode.BALANCING)
        }
    }
}
