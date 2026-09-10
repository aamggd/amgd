package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SupplierApMathTest {
    @Test fun outstanding_never_goes_negative() {
        assertEquals(700.0, SupplierApMath.outstandingBase(1000.0, 100.0, 200.0), 0.000001)
        assertEquals(0.0, SupplierApMath.outstandingBase(1000.0, 400.0, 700.0), 0.000001)
    }

    @Test fun payment_split_preserves_historical_payable_and_fx_difference() {
        val split = SupplierApMath.paymentSplit(10.0, 150.0, 160.0)
        assertEquals(1500.0, split.allocatedBase, 0.000001)
        assertEquals(1600.0, split.cashBase, 0.000001)
        assertEquals(100.0, split.fxDifferenceBase, 0.000001)
    }

    @Test fun aging_buckets_are_stable() {
        val day = 86_400_000L
        val asOf = 100L * day
        assertEquals("CURRENT", SupplierApMath.agingBucket(asOf + day, asOf))
        assertEquals("1_30", SupplierApMath.agingBucket(asOf - 10 * day, asOf))
        assertEquals("31_60", SupplierApMath.agingBucket(asOf - 45 * day, asOf))
        assertEquals("61_90", SupplierApMath.agingBucket(asOf - 75 * day, asOf))
        assertEquals("OVER_90", SupplierApMath.agingBucket(asOf - 120 * day, asOf))
    }
}
