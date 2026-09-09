package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreasuryVoucherOperationIdentityTest {
    @Test
    fun sameOperationIdProducesSameStableSourceId() {
        val operationId = "123e4567-e89b-12d3-a456-426614174000"
        assertEquals(
            "voucher:123e4567-e89b-12d3-a456-426614174000",
            TreasuryVoucherOperationIdentity.sourceId(operationId)
        )
        assertEquals(
            TreasuryVoucherOperationIdentity.sourceId(operationId),
            TreasuryVoucherOperationIdentity.sourceId("  ${operationId.uppercase()}  ")
        )
    }

    @Test
    fun newDialogsGetDifferentOperationIds() {
        val first = TreasuryVoucherOperationIdentity.newOperationId()
        val second = TreasuryVoucherOperationIdentity.newOperationId()
        assertNotEquals(first, second)
        assertTrue(TreasuryVoucherOperationIdentity.sourceId(first).startsWith("voucher:"))
        assertTrue(TreasuryVoucherOperationIdentity.sourceId(second).startsWith("voucher:"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedOperationIdIsRejectedFailClosed() {
        TreasuryVoucherOperationIdentity.sourceId("not-a-uuid")
    }
}
