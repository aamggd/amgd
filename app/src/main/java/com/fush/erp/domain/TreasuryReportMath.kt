package com.fush.erp.domain

import com.fush.erp.data.entity.TreasuryAccountEntity
import com.fush.erp.data.entity.TreasuryMovementReportRow
import kotlin.math.abs

data class TreasuryAccountReportRow(
    val treasuryId: Long,
    val code: String,
    val nameAr: String,
    val kind: String,
    val currencyCode: String,
    val bankName: String,
    val accountNumber: String,
    val openingBase: Double,
    val externalInBase: Double,
    val externalOutBase: Double,
    val transferInBase: Double,
    val transferOutBase: Double,
    val closingBase: Double
) {
    val netExternalBase: Double get() = externalInBase - externalOutBase
    val netTransfersBase: Double get() = transferInBase - transferOutBase
}

data class TreasuryPeriodReport(
    val accounts: List<TreasuryAccountReportRow>,
    val movements: List<TreasuryMovementReportRow>,
    val openingBase: Double,
    val externalInBase: Double,
    val externalOutBase: Double,
    val transferInBase: Double,
    val transferOutBase: Double,
    val closingBase: Double
)

object TreasuryReportMath {
    private const val EPSILON = 0.000000001

    fun build(
        treasuries: List<TreasuryAccountEntity>,
        movementsThroughEnd: List<TreasuryMovementReportRow>,
        fromDate: Long,
        toDate: Long
    ): TreasuryPeriodReport {
        require(fromDate <= toDate) { "الفترة غير صحيحة" }
        val throughEnd = movementsThroughEnd.filter { it.entryDate <= toDate }
        val period = throughEnd.filter { it.entryDate in fromDate..toDate }
        val summaries = treasuries.map { treasury ->
            val all = throughEnd.filter { it.treasuryId == treasury.id }
            val current = period.filter { it.treasuryId == treasury.id }
            val opening = all.filter { it.entryDate < fromDate }.sumOf { it.debitBase - it.creditBase }
            val external = current.filterNot { it.isInternalTransfer }
            val internal = current.filter { it.isInternalTransfer }
            TreasuryAccountReportRow(
                treasuryId = treasury.id,
                code = treasury.code,
                nameAr = treasury.nameAr,
                kind = treasury.kind,
                currencyCode = treasury.currencyCode,
                bankName = treasury.bankName,
                accountNumber = treasury.accountNumber,
                openingBase = opening,
                externalInBase = external.sumOf { it.debitBase },
                externalOutBase = external.sumOf { it.creditBase },
                transferInBase = internal.sumOf { it.debitBase },
                transferOutBase = internal.sumOf { it.creditBase },
                closingBase = opening + current.sumOf { it.debitBase - it.creditBase }
            )
        }.filter { row ->
            listOf(row.openingBase, row.externalInBase, row.externalOutBase, row.transferInBase, row.transferOutBase, row.closingBase)
                .any { abs(it) > EPSILON } || period.any { it.treasuryId == row.treasuryId }
        }
        return TreasuryPeriodReport(
            accounts = summaries,
            movements = period,
            openingBase = summaries.sumOf { it.openingBase },
            externalInBase = summaries.sumOf { it.externalInBase },
            externalOutBase = summaries.sumOf { it.externalOutBase },
            transferInBase = summaries.sumOf { it.transferInBase },
            transferOutBase = summaries.sumOf { it.transferOutBase },
            closingBase = summaries.sumOf { it.closingBase }
        )
    }
}
