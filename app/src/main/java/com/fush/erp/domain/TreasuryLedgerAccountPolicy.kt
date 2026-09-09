package com.fush.erp.domain

import com.fush.erp.data.entity.AccountEntity

object TreasuryLedgerAccountPolicy {
    fun availableAccounts(accounts: List<AccountEntity>, linkedAccountIds: Set<Long>): List<AccountEntity> =
        accounts.filter { account ->
            account.isActive &&
                account.isPosting &&
                account.type == "ASSET" &&
                account.id !in linkedAccountIds
        }
}
