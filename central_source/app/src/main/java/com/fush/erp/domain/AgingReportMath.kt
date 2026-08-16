package com.fush.erp.domain

import com.fush.erp.data.entity.PartyAgingAdjustmentRow
import com.fush.erp.data.entity.PartyAgingInvoiceRow
import kotlin.math.abs

data class PartyAgingReportRow(
    val partyId: Long,
    val partyName: String,
    val currentBase: Double,
    val days1To30Base: Double,
    val days31To60Base: Double,
    val days61To90Base: Double,
    val over90Base: Double,
    val unappliedBase: Double,
    val totalBalanceBase: Double
) {
    val overdueBase: Double
        get() = days1To30Base + days31To60Base + days61To90Base + over90Base
}

object AgingReportMath {
    private const val DAY_MS = 86_400_000L
    private const val EPSILON = 0.000000001

    fun build(
        invoices: List<PartyAgingInvoiceRow>,
        adjustments: List<PartyAgingAdjustmentRow>,
        asOf: Long
    ): List<PartyAgingReportRow> {
        data class MutableAging(
            val partyId: Long,
            var partyName: String,
            var current: Double = 0.0,
            var days1To30: Double = 0.0,
            var days31To60: Double = 0.0,
            var days61To90: Double = 0.0,
            var over90: Double = 0.0,
            var unapplied: Double = 0.0
        )

        val rows = linkedMapOf<Long, MutableAging>()
        fun row(id: Long, name: String): MutableAging = rows.getOrPut(id) { MutableAging(id, name) }.also {
            if (it.partyName.isBlank() && name.isNotBlank()) it.partyName = name
        }

        invoices.forEach { invoice ->
            val amount = invoice.outstandingBase.coerceAtLeast(0.0)
            if (amount <= EPSILON) return@forEach
            val target = row(invoice.partyId, invoice.partyName)
            when (bucket(invoice.dueDate, asOf)) {
                "CURRENT" -> target.current += amount
                "1_30" -> target.days1To30 += amount
                "31_60" -> target.days31To60 += amount
                "61_90" -> target.days61To90 += amount
                else -> target.over90 += amount
            }
        }

        adjustments.forEach { adjustment ->
            if (abs(adjustment.adjustmentBase) <= EPSILON) return@forEach
            row(adjustment.partyId, adjustment.partyName).unapplied += adjustment.adjustmentBase
        }

        return rows.values.map { r ->
            val total = r.current + r.days1To30 + r.days31To60 + r.days61To90 + r.over90 + r.unapplied
            PartyAgingReportRow(
                partyId = r.partyId,
                partyName = r.partyName,
                currentBase = r.current,
                days1To30Base = r.days1To30,
                days31To60Base = r.days31To60,
                days61To90Base = r.days61To90,
                over90Base = r.over90,
                unappliedBase = r.unapplied,
                totalBalanceBase = total
            )
        }.filter { row ->
            abs(row.totalBalanceBase) > EPSILON || row.overdueBase > EPSILON || row.currentBase > EPSILON || abs(row.unappliedBase) > EPSILON
        }.sortedWith(
            compareByDescending<PartyAgingReportRow> { it.over90Base }
                .thenByDescending { it.days61To90Base }
                .thenByDescending { it.overdueBase }
                .thenByDescending { it.totalBalanceBase }
                .thenBy { it.partyName }
        )
    }

    fun bucket(dueDate: Long?, asOf: Long): String {
        if (dueDate == null || dueDate >= asOf) return "CURRENT"
        val days = ((asOf - dueDate) / DAY_MS).coerceAtLeast(0)
        return when {
            days <= 30 -> "1_30"
            days <= 60 -> "31_60"
            days <= 90 -> "61_90"
            else -> "OVER_90"
        }
    }
}
