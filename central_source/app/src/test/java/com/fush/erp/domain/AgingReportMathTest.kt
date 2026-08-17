package com.fush.erp.domain

import com.fush.erp.data.entity.PartyAgingAdjustmentRow
import com.fush.erp.data.entity.PartyAgingInvoiceRow
import org.junit.Assert.assertEquals
import org.junit.Test

class AgingReportMathTest {
    private val day = 86_400_000L
    private val asOf = 200L * day

    @Test
    fun bucketsInvoicesByDueDateAndKeepsUnappliedVoucherAdjustmentSeparate() {
        val rows = AgingReportMath.build(
            invoices = listOf(
                PartyAgingInvoiceRow(1, "عميل أ", asOf + day, 100.0),
                PartyAgingInvoiceRow(1, "عميل أ", asOf - 10 * day, 200.0),
                PartyAgingInvoiceRow(1, "عميل أ", asOf - 45 * day, 300.0),
                PartyAgingInvoiceRow(1, "عميل أ", asOf - 75 * day, 400.0),
                PartyAgingInvoiceRow(1, "عميل أ", asOf - 120 * day, 500.0)
            ),
            adjustments = listOf(PartyAgingAdjustmentRow(1, "عميل أ", -150.0)),
            asOf = asOf
        )

        val row = rows.single()
        assertEquals(100.0, row.currentBase, 0.0001)
        assertEquals(200.0, row.days1To30Base, 0.0001)
        assertEquals(300.0, row.days31To60Base, 0.0001)
        assertEquals(400.0, row.days61To90Base, 0.0001)
        assertEquals(500.0, row.over90Base, 0.0001)
        assertEquals(-150.0, row.unappliedBase, 0.0001)
        assertEquals(1350.0, row.totalBalanceBase, 0.0001)
        assertEquals(1400.0, row.overdueBase, 0.0001)
    }

    @Test
    fun nullDueDateIsCurrentAndPositiveSupplierAdjustmentIncreasesNetBalance() {
        val rows = AgingReportMath.build(
            invoices = listOf(PartyAgingInvoiceRow(7, "مورد ب", null, 800.0)),
            adjustments = listOf(PartyAgingAdjustmentRow(7, "مورد ب", 120.0)),
            asOf = asOf
        )

        val row = rows.single()
        assertEquals(800.0, row.currentBase, 0.0001)
        assertEquals(120.0, row.unappliedBase, 0.0001)
        assertEquals(920.0, row.totalBalanceBase, 0.0001)
    }

    @Test
    fun zeroOpenInvoicesWithUnappliedCreditStillRemainVisible() {
        val rows = AgingReportMath.build(
            invoices = emptyList(),
            adjustments = listOf(PartyAgingAdjustmentRow(9, "عميل دائن", -250.0)),
            asOf = asOf
        )

        assertEquals(1, rows.size)
        assertEquals(-250.0, rows.single().totalBalanceBase, 0.0001)
    }
}
