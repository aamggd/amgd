package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TreasuryPartyRequirementPolicyTest {
    @Test
    fun `P1 maps protected party accounts to their required party identity`() {
        assertEquals(TreasuryPartyRequirement.CUSTOMER, TreasuryPartyRequirementPolicy.requirementForAccount("1300"))
        assertEquals(TreasuryPartyRequirement.SUPPLIER, TreasuryPartyRequirementPolicy.requirementForAccount("2100"))
        assertEquals(TreasuryPartyRequirement.EMPLOYEE, TreasuryPartyRequirementPolicy.requirementForAccount("2200"))
        assertEquals(TreasuryPartyRequirement.SALES_REP, TreasuryPartyRequirementPolicy.requirementForAccount("2300"))
        assertEquals(TreasuryPartyRequirement.NONE, TreasuryPartyRequirementPolicy.requirementForAccount("6100"))
    }

    @Test
    fun `customer control account requires exactly a customer`() {
        assertEquals(
            TreasuryPartyRequirement.CUSTOMER,
            TreasuryPartyRequirementPolicy.requireValidSelection(
                "1300",
                TreasuryPartySelection(customerId = 11L)
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            TreasuryPartyRequirementPolicy.requireValidSelection("1300", TreasuryPartySelection())
        }
        assertThrows(IllegalArgumentException::class.java) {
            TreasuryPartyRequirementPolicy.requireValidSelection(
                "1300",
                TreasuryPartySelection(customerId = 11L, supplierId = 22L)
            )
        }
    }

    @Test
    fun `supplier control account requires exactly a supplier`() {
        assertEquals(
            TreasuryPartyRequirement.SUPPLIER,
            TreasuryPartyRequirementPolicy.requireValidSelection(
                "2100",
                TreasuryPartySelection(supplierId = 22L)
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            TreasuryPartyRequirementPolicy.requireValidSelection("2100", TreasuryPartySelection())
        }
        assertThrows(IllegalArgumentException::class.java) {
            TreasuryPartyRequirementPolicy.requireValidSelection(
                "2100",
                TreasuryPartySelection(supplierId = 22L, employeeId = 33L)
            )
        }
    }

    @Test
    fun `employee payable account requires exactly an employee`() {
        assertEquals(
            TreasuryPartyRequirement.EMPLOYEE,
            TreasuryPartyRequirementPolicy.requireValidSelection(
                "2200",
                TreasuryPartySelection(employeeId = 33L)
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            TreasuryPartyRequirementPolicy.requireValidSelection("2200", TreasuryPartySelection())
        }
        assertThrows(IllegalArgumentException::class.java) {
            TreasuryPartyRequirementPolicy.requireValidSelection(
                "2200",
                TreasuryPartySelection(employeeId = 33L, customerId = 11L)
            )
        }
    }

    @Test
    fun `sales rep payable remains protected by the inherited party contract`() {
        assertEquals(
            TreasuryPartyRequirement.SALES_REP,
            TreasuryPartyRequirementPolicy.requireValidSelection(
                "2300",
                TreasuryPartySelection(salesRepId = 44L)
            )
        )
    }

    @Test
    fun `general account rejects orphan party linkage`() {
        assertEquals(
            TreasuryPartyRequirement.NONE,
            TreasuryPartyRequirementPolicy.requireValidSelection("6100", TreasuryPartySelection())
        )
        assertThrows(IllegalArgumentException::class.java) {
            TreasuryPartyRequirementPolicy.requireValidSelection(
                "6100",
                TreasuryPartySelection(customerId = 11L)
            )
        }
    }
}
