package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PartyStatementMathTest {
    @Test
    fun customerStatementCarriesOpeningAndReducesBalanceWithReceipt() {
        val report = PartyStatementMath.build(
            events = listOf(
                PartyStatementEvent(10, 1, "INVOICE", "INV-1", "فاتورة", 100.0, 0.0),
                PartyStatementEvent(20, 1, "RECEIPT", "RCPT-1", "تحصيل", 0.0, 40.0),
                PartyStatementEvent(30, 1, "RECEIPT", "RCPT-2", "تحصيل", 0.0, 60.0)
            ),
            fromDate = 15,
            toDate = 35,
            customerBalance = true
        )
        assertEquals(100.0, report.openingBalance, 0.0001)
        assertEquals(0.0, report.closingBalance, 0.0001)
        assertEquals(60.0, report.lines.first().runningBalance, 0.0001)
    }

    @Test
    fun supplierStatementUsesCreditMinusDebitAsPayableBalance() {
        val report = PartyStatementMath.build(
            events = listOf(
                PartyStatementEvent(10, 1, "INVOICE", "PINV-1", "فاتورة شراء", 0.0, 200.0),
                PartyStatementEvent(20, 1, "PAYMENT", "PAY-1", "دفعة", 50.0, 0.0)
            ),
            fromDate = 0,
            toDate = 25,
            customerBalance = false
        )
        assertEquals(200.0, report.lines.first().runningBalance, 0.0001)
        assertEquals(150.0, report.closingBalance, 0.0001)
    }
}
