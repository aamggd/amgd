package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreasuryMovementTypePolicyTest {
    @Test
    fun `P0 exposes exactly the six approved movement types`() {
        assertEquals(
            setOf(
                "CUSTOMER_RECEIPT",
                "SUPPLIER_PAYMENT",
                "EXPENSE_PAYMENT",
                "EMPLOYEE_PAYMENT",
                "TRANSFER",
                "ADJUSTMENT"
            ),
            TreasuryMovementType.values().map { it.sourceType }.toSet()
        )
    }

    @Test
    fun `voucher business meaning is classified independently from payment label`() {
        assertEquals(TreasuryMovementType.CUSTOMER_RECEIPT, TreasuryMovementTypePolicy.forVoucher("RECEIPT", "CUSTOMER"))
        assertEquals(TreasuryMovementType.SUPPLIER_PAYMENT, TreasuryMovementTypePolicy.forVoucher("PAYMENT", "SUPPLIER"))
        assertEquals(TreasuryMovementType.EXPENSE_PAYMENT, TreasuryMovementTypePolicy.forVoucher("EXPENSE"))
        assertEquals(TreasuryMovementType.EMPLOYEE_PAYMENT, TreasuryMovementTypePolicy.forVoucher("PAYMENT", "EMPLOYEE"))
        assertEquals(TreasuryMovementType.EMPLOYEE_PAYMENT, TreasuryMovementTypePolicy.forVoucher("PAYMENT", "SALES_REP"))
        assertEquals(TreasuryMovementType.TRANSFER, TreasuryMovementTypePolicy.forVoucher("TRANSFER"))
        assertEquals(TreasuryMovementType.ADJUSTMENT, TreasuryMovementTypePolicy.forVoucher("PAYMENT"))
        assertEquals(TreasuryMovementType.ADJUSTMENT, TreasuryMovementTypePolicy.forVoucher("RECEIPT"))
        assertEquals(TreasuryMovementType.ADJUSTMENT, TreasuryMovementTypePolicy.forVoucher("INCOME"))
    }

    @Test
    fun `P0 classification does not introduce P1 party validation`() {
        assertEquals(TreasuryMovementType.TRANSFER, TreasuryMovementTypePolicy.forVoucher("TRANSFER", "CUSTOMER"))
        assertEquals(TreasuryMovementType.ADJUSTMENT, TreasuryMovementTypePolicy.forVoucher("RECEIPT", "SUPPLIER"))
        assertEquals(TreasuryMovementType.ADJUSTMENT, TreasuryMovementTypePolicy.forVoucher("PAYMENT", "CUSTOMER"))
    }

    @Test
    fun `historical treasury source names keep a deterministic classification`() {
        assertEquals(TreasuryMovementType.EXPENSE_PAYMENT, TreasuryMovementTypePolicy.fromJournalSource("TREASURY_EXPENSE"))
        assertEquals(TreasuryMovementType.EMPLOYEE_PAYMENT, TreasuryMovementTypePolicy.fromJournalSource("TREASURY_PAYMENT", "EMPLOYEE"))
        assertEquals(TreasuryMovementType.ADJUSTMENT, TreasuryMovementTypePolicy.fromJournalSource("TREASURY_PAYMENT"))
        assertEquals(TreasuryMovementType.TRANSFER, TreasuryMovementTypePolicy.fromJournalSource("TREASURY_TRANSFER"))
        assertEquals(TreasuryMovementType.ADJUSTMENT, TreasuryMovementTypePolicy.fromJournalSource("TREASURY_RECEIPT"))
    }

    @Test
    fun `transfer detection supports canonical legacy and reversal sources`() {
        assertTrue(TreasuryMovementTypePolicy.isInternalTransferSource("TRANSFER"))
        assertTrue(TreasuryMovementTypePolicy.isInternalTransferSource("TREASURY_TRANSFER"))
        assertTrue(TreasuryMovementTypePolicy.isInternalTransferSource("REVERSAL", "TRANSFER"))
        assertTrue(TreasuryMovementTypePolicy.isInternalTransferSource("REVERSAL", "TREASURY_TRANSFER"))
        assertFalse(TreasuryMovementTypePolicy.isInternalTransferSource("SUPPLIER_PAYMENT"))
    }
}
