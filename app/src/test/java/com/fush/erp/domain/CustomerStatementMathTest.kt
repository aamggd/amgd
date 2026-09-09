package com.fush.erp.domain

import com.fush.erp.data.entity.CustomerLedgerEventRow
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerStatementMathTest {
    private fun event(date: Long, order: Int, debit: Double, credit: Double) = CustomerLedgerEventRow(
        eventDate = date,
        eventOrder = order,
        eventType = "TEST",
        referenceNo = "R$order",
        invoiceNo = "",
        currencyCode = "YER_NEW",
        amountOriginal = debit + credit,
        debitBase = debit,
        creditBase = credit,
        notes = "",
    )

    @Test
    fun openingAndPeriodTotalsProduceExpectedClosingBalance() {
        val rows = listOf(
            event(1_000L, 1, 1000.0, 0.0),
            event(2_000L, 1, 0.0, 200.0),
            event(3_000L, 1, 400.0, 0.0),
            event(4_000L, 1, 0.0, 100.0),
        )
        val result = CustomerStatementMath.summarize(rows, 2_500L, 4_500L)
        assertEquals(800.0, result.openingBalanceBase, 0.000001)
        assertEquals(400.0, result.debitBase, 0.000001)
        assertEquals(100.0, result.creditBase, 0.000001)
        assertEquals(1100.0, result.closingBalanceBase, 0.000001)
        assertEquals(1100.0, result.currentBalanceBase, 0.000001)
        assertEquals(2, result.movementCount)
    }

    @Test
    fun dateAndEventOrderAreNormalizedBeforeCalculation() {
        val rows = listOf(
            event(3_000L, 2, 0.0, 50.0),
            event(1_000L, 1, 500.0, 0.0),
            event(3_000L, 1, 200.0, 0.0),
        )
        val result = CustomerStatementMath.summarize(rows, 3_000L, 3_000L)
        assertEquals(500.0, result.openingBalanceBase, 0.000001)
        assertEquals(200.0, result.debitBase, 0.000001)
        assertEquals(50.0, result.creditBase, 0.000001)
        assertEquals(650.0, result.closingBalanceBase, 0.000001)
        assertEquals(2, result.movementCount)
    }
}
