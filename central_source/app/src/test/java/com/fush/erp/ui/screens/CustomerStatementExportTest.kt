package com.fush.erp.ui.screens

import com.fush.erp.data.entity.CustomerLedgerEventRow
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerStatementExportTest {
    private fun event(
        date: Long,
        order: Int,
        type: String,
        reference: String,
        debit: Double = 0.0,
        credit: Double = 0.0,
    ) = CustomerLedgerEventRow(
        eventDate = date,
        eventOrder = order,
        eventType = type,
        referenceNo = reference,
        invoiceNo = reference,
        currencyCode = "YER_NEW",
        amountOriginal = debit - credit,
        debitBase = debit,
        creditBase = credit,
        notes = "",
    )

    @Test
    fun runningBalanceMatchesCustomerLedgerAccountingDirection() {
        val snapshot = buildCustomerStatementSnapshot(
            events = listOf(
                event(1_000, 10, "INVOICE", "INV-1", debit = 1_000.0),
                event(2_000, 30, "RECEIPT", "RCPT-1", credit = 200.0),
                event(3_000, 20, "SALES_RETURN", "RET-1", credit = 100.0),
            ),
            fromInclusive = 1_000,
            toInclusive = 3_000,
        )

        assertEquals(0.0, snapshot.openingBalanceBase, 0.000001)
        assertEquals(1_000.0, snapshot.periodDebitBase, 0.000001)
        assertEquals(300.0, snapshot.periodCreditBase, 0.000001)
        assertEquals(700.0, snapshot.closingBalanceBase, 0.000001)
        assertEquals(listOf(1_000.0, 800.0, 700.0), snapshot.lines.map { it.runningBalanceBase })
    }

    @Test
    fun periodCarriesOpeningBalanceFromEarlierMovements() {
        val snapshot = buildCustomerStatementSnapshot(
            events = listOf(
                event(500, 10, "INVOICE", "INV-OLD", debit = 900.0),
                event(1_500, 30, "RECEIPT", "RCPT-1", credit = 250.0),
                event(2_500, 10, "INVOICE", "INV-2", debit = 400.0),
            ),
            fromInclusive = 1_000,
            toInclusive = 3_000,
        )

        assertEquals(900.0, snapshot.openingBalanceBase, 0.000001)
        assertEquals(400.0, snapshot.periodDebitBase, 0.000001)
        assertEquals(250.0, snapshot.periodCreditBase, 0.000001)
        assertEquals(1_050.0, snapshot.closingBalanceBase, 0.000001)
        assertEquals(listOf(650.0, 1_050.0), snapshot.lines.map { it.runningBalanceBase })
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidPeriodFailsClosed() {
        buildCustomerStatementSnapshot(
            events = emptyList(),
            fromInclusive = 2_000,
            toInclusive = 1_000,
        )
    }
}
