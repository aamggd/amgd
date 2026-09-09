package com.fush.erp.domain

import org.junit.Assert.assertThrows
import org.junit.Test

class OpenItemLedgerMathTest {
    @Test fun normalOpenItemIsAccepted() {
        OpenItemLedgerMath.requireNoNegativeOpenItems(listOf(AccountingOpenItem("AR",1,"S1",2,"USD",10.0,1500.0)))
    }
    @Test fun negativeOpenItemIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            OpenItemLedgerMath.requireNoNegativeOpenItems(listOf(AccountingOpenItem("AP",1,"P1",2,"USD",-2.0,-300.0)))
        }
    }
    @Test fun exactControlReconciliationPasses() {
        OpenItemLedgerMath.requireControlReconciled(OpenItemControlReconciliation("AR",100.0,100.0,0.0))
    }
    @Test fun controlMismatchRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            OpenItemLedgerMath.requireControlReconciled(OpenItemControlReconciliation("AP",100.0,90.0,10.0))
        }
    }
}
