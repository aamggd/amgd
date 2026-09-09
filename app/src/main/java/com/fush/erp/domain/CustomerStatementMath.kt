package com.fush.erp.domain

import com.fush.erp.data.entity.CustomerLedgerEventRow

data class CustomerStatementPeriodSummary(
    val openingBalanceBase: Double,
    val debitBase: Double,
    val creditBase: Double,
    val closingBalanceBase: Double,
    val currentBalanceBase: Double,
    val movementCount: Int,
)

object CustomerStatementMath {
    fun summarize(
        events: List<CustomerLedgerEventRow>,
        fromDate: Long,
        toDate: Long,
    ): CustomerStatementPeriodSummary {
        require(fromDate <= toDate) { "fromDate must not exceed toDate" }
        val ordered = events.sortedWith(compareBy<CustomerLedgerEventRow> { it.eventDate }.thenBy { it.eventOrder })
        val opening = ordered.asSequence()
            .filter { it.eventDate < fromDate }
            .sumOf { it.debitBase - it.creditBase }
        val period = ordered.filter { it.eventDate in fromDate..toDate }
        val debit = period.sumOf { it.debitBase }
        val credit = period.sumOf { it.creditBase }
        val current = ordered.sumOf { it.debitBase - it.creditBase }
        return CustomerStatementPeriodSummary(
            openingBalanceBase = opening,
            debitBase = debit,
            creditBase = credit,
            closingBalanceBase = opening + debit - credit,
            currentBalanceBase = current,
            movementCount = period.size,
        )
    }
}
