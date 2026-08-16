package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportMathTest {
    @Test fun percent_is_safe() {
        assertEquals(50.0, ReportMath.percent(5.0, 10.0), 0.0001)
        assertEquals(0.0, ReportMath.percent(5.0, 0.0), 0.0001)
    }

    @Test fun net_and_unit_cost() {
        assertEquals(800.0, ReportMath.net(1000.0, 200.0), 0.0001)
        assertEquals(250.0, ReportMath.unitCost(1000.0, 4.0), 0.0001)
        assertEquals(0.0, ReportMath.unitCost(1000.0, 0.0), 0.0001)
    }

    @Test fun product_volume_classification_supports_current_finished_goods() {
        assertEquals(60, ReportMath.productVolumeMl("FG-FUSH-60", "Fush 60 مل"))
        assertEquals(200, ReportMath.productVolumeMl("FG-000001", "fush 200ml"))
        assertEquals(null, ReportMath.productVolumeMl("PK-BOTTLE-60", "عبوة"))
    }

    @Test fun customer_outstanding_includes_linked_party_vouchers() {
        assertEquals(0.0, ReportMath.customerOutstandingBase(
            creditInvoices = 100_000.0,
            customerCreditReturns = 0.0,
            allocatedReceipts = 0.0,
            partyReceipts = 100_000.0,
            partyPayments = 0.0
        ), 0.0001)

        assertEquals(40_000.0, ReportMath.customerOutstandingBase(
            creditInvoices = 100_000.0,
            customerCreditReturns = 10_000.0,
            allocatedReceipts = 30_000.0,
            partyReceipts = 25_000.0,
            partyPayments = 5_000.0
        ), 0.0001)
    }

    @Test fun unallocated_customer_receipt_reduces_overdue_oldest_first_without_aging_payments() {
        assertEquals(0.0, ReportMath.overdueAfterUnallocatedReceipts(
            overdueAfterAllocatedReceipts = 100_000.0,
            partyReceipts = 100_000.0
        ), 0.0001)
        assertEquals(60_000.0, ReportMath.overdueAfterUnallocatedReceipts(
            overdueAfterAllocatedReceipts = 100_000.0,
            partyReceipts = 40_000.0
        ), 0.0001)
    }
}
