package com.fush.erp.domain

import com.fush.erp.data.entity.SupplierAgingRow
import com.fush.erp.data.entity.SupplierLedgerEventRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplierProfileMathTest {
    @Test
    fun reconcilesStatementWithInvoiceAgingAndSupplierAdjustments() {
        val aging = SupplierAgingRow(
            supplierId = 7L,
            supplierName = "مورد",
            currentBase = 300.0,
            days1To30Base = 200.0,
            days31To60Base = 100.0,
            days61To90Base = 100.0,
            over90Base = 50.0,
            totalOutstandingBase = 750.0
        )
        val events = listOf(
            event("INVOICE", credit = 1_000.0),
            event("RETURN", debit = 200.0),
            event("PAYMENT", debit = 100.0)
        )

        val snapshot = SupplierProfileMath.build(
            aging = aging,
            nonInvoiceAdjustmentBase = -50.0,
            events = events
        )

        assertEquals(700.0, snapshot.statementBalanceBase, 0.000001)
        assertEquals(700.0, snapshot.totalLiabilityBase, 0.000001)
        assertEquals(0.0, snapshot.reconciliationDifferenceBase, 0.000001)
        assertTrue(snapshot.isReconciled)
    }

    @Test
    fun detectsAStatementDifferenceInsteadOfHidingGhostActivity() {
        val aging = SupplierAgingRow(
            supplierId = 3L,
            supplierName = "مورد",
            currentBase = 500.0,
            days1To30Base = 0.0,
            days31To60Base = 0.0,
            days61To90Base = 0.0,
            over90Base = 0.0,
            totalOutstandingBase = 500.0
        )

        val snapshot = SupplierProfileMath.build(
            aging = aging,
            nonInvoiceAdjustmentBase = 0.0,
            events = listOf(event("INVOICE", credit = 510.0))
        )

        assertEquals(10.0, snapshot.reconciliationDifferenceBase, 0.000001)
        assertFalse(snapshot.isReconciled)
    }

    @Test
    fun supplierWithOnlyDirectVoucherActivityStillHasAProfileLiability() {
        val snapshot = SupplierProfileMath.build(
            aging = null,
            nonInvoiceAdjustmentBase = 120.0,
            events = listOf(event("VOUCHER_RECEIPT", credit = 120.0))
        )

        assertEquals(0.0, snapshot.invoiceOutstandingBase, 0.000001)
        assertEquals(120.0, snapshot.totalLiabilityBase, 0.000001)
        assertEquals(120.0, snapshot.statementBalanceBase, 0.000001)
        assertTrue(snapshot.isReconciled)
    }

    private fun event(type: String, debit: Double = 0.0, credit: Double = 0.0) =
        SupplierLedgerEventRow(
            eventDate = 1L,
            eventOrder = 1,
            eventType = type,
            referenceNo = type,
            debitBase = debit,
            creditBase = credit,
            notes = ""
        )
}
