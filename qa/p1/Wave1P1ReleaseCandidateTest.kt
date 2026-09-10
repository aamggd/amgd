package com.fush.erp.qa

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fush.erp.domain.AccountingP1IntegrityPolicy
import com.fush.erp.domain.CustomerMovementIdentity
import com.fush.erp.domain.SupplierMovementIdentity
import com.fush.erp.domain.TreasuryPartyRequirement
import com.fush.erp.domain.TreasuryPartyRequirementPolicy
import com.fush.erp.domain.TreasuryPartySelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Wave1P1ReleaseCandidateTest {

    @Test
    fun exactTargetPackage_isRecoveryApplication_andHasLaunchableActivity() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.fush.erp.recovery", target.packageName)

        val launchIntent = target.packageManager.getLaunchIntentForPackage(target.packageName)
        assertNotNull("Merged Central APK must expose a launcher activity", launchIntent)
        launchIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        target.startActivity(launchIntent)
    }

    @Test
    fun saleCollectionAndReturn_keepCustomerIdentityTreasuryPartyAndAccountingEventIdentityAligned() {
        val customerId = CustomerMovementIdentity.requireId(101L)
        assertEquals(101L, customerId)

        assertEquals(
            TreasuryPartyRequirement.CUSTOMER,
            TreasuryPartyRequirementPolicy.requireValidSelection(
                accountCode = "1300",
                selection = TreasuryPartySelection(customerId = customerId)
            )
        )

        val saleKey = AccountingP1IntegrityPolicy.stableEventKeyOrNull("SALE", "sale-501")
        val receiptKey = AccountingP1IntegrityPolicy.stableEventKeyOrNull("CUSTOMER_RECEIPT", "receipt-601")
        val returnKey = AccountingP1IntegrityPolicy.stableEventKeyOrNull("SALES_RETURN", "return-701")

        assertNotNull(saleKey)
        assertNotNull(receiptKey)
        assertNotNull(returnKey)
        assertTrue(AccountingP1IntegrityPolicy.isDuplicateProtected("SALE"))
        assertTrue(AccountingP1IntegrityPolicy.isDuplicateProtected("CUSTOMER_RECEIPT"))
        assertTrue(AccountingP1IntegrityPolicy.isDuplicateProtected("SALES_RETURN"))
        assertFalse(setOf(saleKey, receiptKey, returnKey).contains(null))

        assertThrows(IllegalArgumentException::class.java) {
            TreasuryPartyRequirementPolicy.requireValidSelection(
                accountCode = "1300",
                selection = TreasuryPartySelection(supplierId = 202L)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustomerMovementIdentity.requireId(0L)
        }
    }

    @Test
    fun purchasePaymentAndReturn_keepSupplierIdentityTreasuryPartyAndAccountingEventIdentityAligned() {
        val supplierId = SupplierMovementIdentity.requireId(202L)
        assertEquals(202L, supplierId)

        assertEquals(
            TreasuryPartyRequirement.SUPPLIER,
            TreasuryPartyRequirementPolicy.requireValidSelection(
                accountCode = "2100",
                selection = TreasuryPartySelection(supplierId = supplierId)
            )
        )

        val purchaseKey = AccountingP1IntegrityPolicy.stableEventKeyOrNull("PURCHASE", "purchase-801")
        val paymentKey = AccountingP1IntegrityPolicy.stableEventKeyOrNull("SUPPLIER_PAYMENT", "payment-901")
        val returnKey = AccountingP1IntegrityPolicy.stableEventKeyOrNull("PURCHASE_RETURN", "return-1001")

        assertNotNull(purchaseKey)
        assertNotNull(paymentKey)
        assertNotNull(returnKey)
        assertTrue(AccountingP1IntegrityPolicy.isDuplicateProtected("PURCHASE"))
        assertTrue(AccountingP1IntegrityPolicy.isDuplicateProtected("SUPPLIER_PAYMENT"))
        assertTrue(AccountingP1IntegrityPolicy.isDuplicateProtected("PURCHASE_RETURN"))

        assertThrows(IllegalArgumentException::class.java) {
            TreasuryPartyRequirementPolicy.requireValidSelection(
                accountCode = "2100",
                selection = TreasuryPartySelection(customerId = 101L)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SupplierMovementIdentity.requireId(null)
        }
    }

    @Test
    fun generalTreasuryAccount_rejectsOrphanPartyLinkage() {
        assertEquals(
            TreasuryPartyRequirement.NONE,
            TreasuryPartyRequirementPolicy.requireValidSelection(
                accountCode = "6100",
                selection = TreasuryPartySelection()
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            TreasuryPartyRequirementPolicy.requireValidSelection(
                accountCode = "6100",
                selection = TreasuryPartySelection(customerId = 101L)
            )
        }
    }
}
