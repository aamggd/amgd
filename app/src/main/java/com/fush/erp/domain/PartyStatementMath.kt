package com.fush.erp.domain

data class PartyStatementEvent(
    val eventDate: Long,
    val eventOrder: Int,
    val eventType: String,
    val referenceNo: String,
    val description: String,
    val debitBase: Double,
    val creditBase: Double
)

data class PartyStatementLine(
    val eventDate: Long,
    val eventType: String,
    val referenceNo: String,
    val description: String,
    val debitBase: Double,
    val creditBase: Double,
    val runningBalance: Double
)

data class PartyStatementPeriod(
    val openingBalance: Double,
    val totalDebit: Double,
    val totalCredit: Double,
    val closingBalance: Double,
    val lines: List<PartyStatementLine>
)

object PartyStatementMath {
    fun build(
        events: List<PartyStatementEvent>,
        fromDate: Long,
        toDate: Long,
        customerBalance: Boolean
    ): PartyStatementPeriod {
        require(fromDate <= toDate) { "الفترة غير صحيحة" }
        fun delta(event: PartyStatementEvent): Double =
            if (customerBalance) event.debitBase - event.creditBase
            else event.creditBase - event.debitBase

        val ordered = events.sortedWith(
            compareBy<PartyStatementEvent> { it.eventDate }
                .thenBy { it.eventOrder }
                .thenBy { it.referenceNo }
        )
        val opening = ordered.filter { it.eventDate < fromDate }.sumOf(::delta)
        var running = opening
        val lines = ordered.filter { it.eventDate in fromDate..toDate }.map { event ->
            running += delta(event)
            PartyStatementLine(
                eventDate = event.eventDate,
                eventType = event.eventType,
                referenceNo = event.referenceNo,
                description = event.description,
                debitBase = event.debitBase,
                creditBase = event.creditBase,
                runningBalance = running
            )
        }
        return PartyStatementPeriod(
            openingBalance = opening,
            totalDebit = lines.sumOf { it.debitBase },
            totalCredit = lines.sumOf { it.creditBase },
            closingBalance = running,
            lines = lines
        )
    }
}
