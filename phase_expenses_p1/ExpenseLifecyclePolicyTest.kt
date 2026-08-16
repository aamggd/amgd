package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExpenseLifecyclePolicyTest {
    @Test
    fun draft_can_only_be_submitted_while_unpaid() {
        ExpenseLifecyclePolicy.requireCanSubmit(ExpenseLifecyclePolicy.DRAFT, ExpenseLifecyclePolicy.UNPAID)
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseLifecyclePolicy.requireCanSubmit(ExpenseLifecyclePolicy.SUBMITTED, ExpenseLifecyclePolicy.UNPAID)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseLifecyclePolicy.requireCanSubmit(ExpenseLifecyclePolicy.DRAFT, ExpenseLifecyclePolicy.PAID)
        }
    }

    @Test
    fun creator_cannot_approve_or_reject_own_request() {
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseLifecyclePolicy.requireCanApprove(ExpenseLifecyclePolicy.SUBMITTED, ExpenseLifecyclePolicy.UNPAID, 7L, 7L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseLifecyclePolicy.requireCanReject(ExpenseLifecyclePolicy.SUBMITTED, ExpenseLifecyclePolicy.UNPAID, 7L, 7L, "رفض")
        }
    }

    @Test
    fun submitted_request_can_be_approved_by_different_actor() {
        ExpenseLifecyclePolicy.requireCanApprove(ExpenseLifecyclePolicy.SUBMITTED, ExpenseLifecyclePolicy.UNPAID, 7L, 8L)
    }

    @Test
    fun rejection_requires_a_reason() {
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseLifecyclePolicy.requireCanReject(ExpenseLifecyclePolicy.SUBMITTED, ExpenseLifecyclePolicy.UNPAID, 7L, 8L, "  ")
        }
        ExpenseLifecyclePolicy.requireCanReject(ExpenseLifecyclePolicy.SUBMITTED, ExpenseLifecyclePolicy.UNPAID, 7L, 8L, "خارج السياسة")
    }

    @Test
    fun payment_requires_approved_and_unpaid() {
        ExpenseLifecyclePolicy.requireCanPay(ExpenseLifecyclePolicy.APPROVED, ExpenseLifecyclePolicy.UNPAID)
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseLifecyclePolicy.requireCanPay(ExpenseLifecyclePolicy.SUBMITTED, ExpenseLifecyclePolicy.UNPAID)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseLifecyclePolicy.requireCanPay(ExpenseLifecyclePolicy.APPROVED, ExpenseLifecyclePolicy.PAID)
        }
    }

    @Test
    fun payment_must_match_the_exact_approved_snapshot() {
        val approved = snapshot()
        ExpenseLifecyclePolicy.requirePaymentMatchesApproved(approved, approved.copy(currencyCode = " yer_new ", costCenterCode = "admin"))
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseLifecyclePolicy.requirePaymentMatchesApproved(approved, approved.copy(amountOriginal = 101.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseLifecyclePolicy.requirePaymentMatchesApproved(approved, approved.copy(expenseAccountId = 99L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ExpenseLifecyclePolicy.requirePaymentMatchesApproved(approved, approved.copy(dimensionReferenceNo = "INV-OTHER"))
        }
    }

    @Test
    fun lifecycle_label_keeps_approval_and_payment_separate() {
        assertEquals("DRAFT", ExpenseLifecyclePolicy.lifecycleLabel("DRAFT", "UNPAID"))
        assertEquals("SUBMITTED", ExpenseLifecyclePolicy.lifecycleLabel("SUBMITTED", "UNPAID"))
        assertEquals("APPROVED", ExpenseLifecyclePolicy.lifecycleLabel("APPROVED", "UNPAID"))
        assertEquals("REJECTED", ExpenseLifecyclePolicy.lifecycleLabel("REJECTED", "UNPAID"))
        assertEquals("PAID", ExpenseLifecyclePolicy.lifecycleLabel("APPROVED", "PAID"))
    }

    private fun snapshot() = ExpensePaymentAuthorizationSnapshot(
        treasuryAccountId = 10L,
        expenseAccountId = 20L,
        amountOriginal = 100.0,
        currencyCode = "YER_NEW",
        exchangeRate = 1.0,
        description = "مواصلات",
        voucherReferenceNo = "DOC-1",
        expenseDate = 123L,
        employeeId = null,
        salesRepId = null,
        costCenterCode = "ADMIN",
        organizationUnit = "HQ",
        referenceType = "OTHER",
        referenceId = null,
        dimensionReferenceNo = "INV-1",
        referenceLabel = "فاتورة داخلية",
        customerId = null,
        supplierId = null,
        itemId = null
    )
}
