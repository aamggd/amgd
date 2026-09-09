package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationalDocumentReversalGuardTest {
    @Test
    fun operationalCustomerReceiptCannotBeReversedAsGenericJournal() {
        assertTrue(AccountingService.requiresOperationalDocumentReversal("CUSTOMER_RECEIPT", hasLinkedPartyVoucher = false))
        assertFalse(AccountingService.canReverseEntrySource("CUSTOMER_RECEIPT", hasLinkedPartyVoucher = false))
    }

    @Test
    fun operationalSupplierPaymentCannotBeReversedAsGenericJournal() {
        assertTrue(AccountingService.requiresOperationalDocumentReversal("SUPPLIER_PAYMENT", hasLinkedPartyVoucher = false))
        assertFalse(AccountingService.canReverseEntrySource("SUPPLIER_PAYMENT", hasLinkedPartyVoucher = false))
    }

    @Test
    fun linkedPartyVoucherKeepsItsAtomicReversalLifecycle() {
        assertFalse(AccountingService.requiresOperationalDocumentReversal("CUSTOMER_RECEIPT", hasLinkedPartyVoucher = true))
        assertTrue(AccountingService.canReverseEntrySource("CUSTOMER_RECEIPT", hasLinkedPartyVoucher = true))
        assertFalse(AccountingService.requiresOperationalDocumentReversal("SUPPLIER_PAYMENT", hasLinkedPartyVoucher = true))
        assertTrue(AccountingService.canReverseEntrySource("SUPPLIER_PAYMENT", hasLinkedPartyVoucher = true))
    }

    @Test
    fun manualJournalRemainsGenericallyReversible() {
        assertFalse(AccountingService.requiresOperationalDocumentReversal("MANUAL", hasLinkedPartyVoucher = false))
        assertTrue(AccountingService.canReverseEntrySource("MANUAL", hasLinkedPartyVoucher = false))
    }
}
