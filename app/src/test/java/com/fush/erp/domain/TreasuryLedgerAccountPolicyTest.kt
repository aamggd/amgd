package com.fush.erp.domain

import com.fush.erp.data.entity.AccountEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreasuryLedgerAccountPolicyTest {
    @Test
    fun onlyActivePostingUnlinkedAssetAccountsAreAvailable() {
        val accounts = listOf(
            AccountEntity(id=1, code="1100", nameAr="الصندوق", nameEn="Cash", type="ASSET"),
            AccountEntity(id=2, code="1110", nameAr="صندوق فرع", nameEn="Branch Cash", type="ASSET"),
            AccountEntity(id=3, code="1120", nameAr="غير نشط", nameEn="Inactive", type="ASSET", isActive=false),
            AccountEntity(id=4, code="1000", nameAr="الأصول", nameEn="Assets", type="ASSET", isPosting=false),
            AccountEntity(id=5, code="6100", nameAr="مصروف", nameEn="Expense", type="EXPENSE")
        )
        val available = TreasuryLedgerAccountPolicy.availableAccounts(accounts, setOf(1L))
        assertEquals(listOf(2L), available.map { it.id })
        assertTrue(available.none { it.id == 1L })
    }
}
