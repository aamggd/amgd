package com.fush.erp.audit

import com.fush.erp.data.entity.SupplierAgingRow
import com.fush.erp.data.entity.SupplierLedgerEventRow
import com.fush.erp.domain.AccountingP1IntegrityPolicy
import com.fush.erp.domain.CustomerMovementIdentity
import com.fush.erp.domain.SupplierMovementIdentity
import com.fush.erp.domain.SupplierProfileMath
import com.fush.erp.domain.TreasuryPartyRequirement
import com.fush.erp.domain.TreasuryPartyRequirementPolicy
import com.fush.erp.domain.TreasuryPartySelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit-only cross-module contract suite.
 *
 * This file is copied temporarily into the exact current/final Central source by the audit workflow.
 * It is NOT an application patch and must never be used to claim the final Central APK was tested.
 */
class CrossModuleP1ContractAuditTest {

    @Test
    fun accountingP1ProtectsAllSettlementSourceTypesTouchedBySalesPurchasesAndTreasury() {
        val required = setOf(
            "SALE",
            "CUSTOMER_RECEIPT",
            "SALES_RETURN",
            "PURCHASE",
            "PURCHASE_RETURN",
            "SUPPLIER_PAYMENT"
        )
        required.forEach { sourceType ->
            assertTrue("$sourceType must remain duplicate-protected", AccountingP1IntegrityPolicy.isDuplicateProtected(sourceType))
            assertNotNull(AccountingP1IntegrityPolicy.stableEventKeyOrNull(sourceType, "audit-1"))
        }
    }

    @Test
    fun stableAccountingKeysKeepSourceTypeBoundaries() {
        val sale = AccountingP1IntegrityPolicy.stableEventKeyOrNull("SALE", "42")
        val receipt = AccountingP1IntegrityPolicy.stableEventKeyOrNull("CUSTOMER_RECEIPT", "42")
        val purchase = AccountingP1IntegrityPolicy.stableEventKeyOrNull("PURCHASE", "42")
        assertNotNull(sale)
        assertNotNull(receipt)
        assertNotNull(purchase)
        assertNotEquals(sale, receipt)
        assertNotEquals(sale, purchase)
        assertEquals(sale, AccountingP1IntegrityPolicy.stableEventKeyOrNull(" sale ", "42"))
    }

    @Test
    fun customerIdentityAndTreasuryCustomerControlContractAreCompatible() {
        val customerId = CustomerMovementIdentity.requireId(101L)
        assertEquals(101L, customerId)
        assertEquals(
            TreasuryPartyRequirement.CUSTOMER,
            TreasuryPartyRequirementPolicy.requireValidSelection(
                "1300",
                TreasuryPartySelection(customerId = customerId)
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            TreasuryPartyRequirementPolicy.requireValidSelection(
                "1300",
                TreasuryPartySelection(customerId = customerId, supplierId = 202L)
            )
        }
        assertThrows(IllegalArgumentException::class.java) { CustomerMovementIdentity.requireId(null) }
        assertThrows(IllegalArgumentException::class.java) { CustomerMovementIdentity.requireId(0L) }
    }

    @Test
    fun supplierIdentityAndTreasurySupplierControlContractAreCompatible() {
        val supplierId = SupplierMovementIdentity.requireId(202L)
        assertEquals(202L, supplierId)
        assertEquals(
            TreasuryPartyRequirement.SUPPLIER,
            TreasuryPartyRequirementPolicy.requireValidSelection(
                "2100",
                TreasuryPartySelection(supplierId = supplierId)
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            TreasuryPartyRequirementPolicy.requireValidSelection(
                "2100",
                TreasuryPartySelection(supplierId = supplierId, employeeId = 303L)
            )
        }
        assertThrows(IllegalArgumentException::class.java) { SupplierMovementIdentity.requireId(null) }
        assertThrows(IllegalArgumentException::class.java) { SupplierMovementIdentity.requireId(-1L) }
    }

    @Test
    fun employeeSalesRepAndGeneralTreasuryAccountsRemainIsolated() {
        assertEquals(
            TreasuryPartyRequirement.EMPLOYEE,
            TreasuryPartyRequirementPolicy.requireValidSelection(
                "2200",
                TreasuryPartySelection(employeeId = 303L)
            )
        )
        assertEquals(
            TreasuryPartyRequirement.SALES_REP,
            TreasuryPartyRequirementPolicy.requireValidSelection(
                "2300",
                TreasuryPartySelection(salesRepId = 404L)
            )
        )
        assertEquals(
            TreasuryPartyRequirement.NONE,
            TreasuryPartyRequirementPolicy.requireValidSelection("6100", TreasuryPartySelection())
        )
        assertThrows(IllegalArgumentException::class.java) {
            TreasuryPartyRequirementPolicy.requireValidSelection("2200", TreasuryPartySelection())
        }
        assertThrows(IllegalArgumentException::class.java) {
            TreasuryPartyRequirementPolicy.requireValidSelection("2300", TreasuryPartySelection(customerId = 101L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TreasuryPartyRequirementPolicy.requireValidSelection("6100", TreasuryPartySelection(supplierId = 202L))
        }
    }

    @Test
    fun supplierProfileReconcilesInvoiceLiabilityWithNonInvoiceAdjustmentsAndStatement() {
        val aging = SupplierAgingRow(
            supplierId = 202L,
            supplierName = "Audit Supplier",
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
    fun supplierProfileMustSurfaceMismatchInsteadOfHidingGhostActivity() {
        val aging = SupplierAgingRow(
            supplierId = 202L,
            supplierName = "Audit Supplier",
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

    private fun event(type: String, debit: Double = 0.0, credit: Double = 0.0) =
        SupplierLedgerEventRow(
            eventDate = 1L,
            eventOrder = 1,
            eventType = type,
            referenceNo = type,
            debitBase = debit,
            creditBase = credit,
            notes = "audit"
        )
}
