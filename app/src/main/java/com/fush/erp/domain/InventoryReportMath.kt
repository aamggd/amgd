package com.fush.erp.domain

import com.fush.erp.data.entity.InventoryActivityReportRow
import com.fush.erp.data.entity.InventoryExpiryLotReportRow
import com.fush.erp.data.entity.InventoryMovementDetailReportRow
import kotlin.math.abs
import kotlin.math.max

data class InventoryActivityInsight(
    val source: InventoryActivityReportRow,
    val daysSinceLastOutbound: Long?,
    val daysSinceFirstInbound: Long?,
    val status: String
)

data class InventoryExpiryInsight(
    val source: InventoryExpiryLotReportRow,
    val daysToExpiry: Long,
    val status: String
)

data class InventoryMovementSummary(
    val inboundQtyBase: Double,
    val outboundQtyBase: Double,
    val netQtyBase: Double,
    val inboundValueBase: Double,
    val outboundValueBase: Double,
    val movementCount: Int
)

object InventoryReportMath {
    private const val DAY_MS = 86_400_000L

    fun activity(row: InventoryActivityReportRow, asOf: Long): InventoryActivityInsight {
        val outboundDays = row.lastOutboundDate?.let { daysSince(it, asOf) }
        val inboundDays = row.firstInboundDate?.let { daysSince(it, asOf) }
        val status = when {
            outboundDays != null && outboundDays >= 180L -> "راكد 180+ يوم"
            outboundDays != null && outboundDays >= 90L -> "بطيء 90+ يوم"
            outboundDays != null -> "نشط"
            inboundDays != null && inboundDays >= 180L -> "بدون صرف 180+ يوم"
            inboundDays != null && inboundDays >= 90L -> "بدون صرف 90+ يوم"
            else -> "لم يُصرف بعد"
        }
        return InventoryActivityInsight(row, outboundDays, inboundDays, status)
    }

    fun expiry(
        row: InventoryExpiryLotReportRow,
        asOf: Long,
        nearExpiryDays: Int = NearExpiryPolicy.DEFAULT_DAYS
    ): InventoryExpiryInsight {
        val days = (row.expiryDate - asOf) / DAY_MS
        val status = NearExpiryPolicy.status(days, nearExpiryDays)
        return InventoryExpiryInsight(row, days, status)
    }

    fun movementSummary(rows: List<InventoryMovementDetailReportRow>): InventoryMovementSummary {
        var inboundQty = 0.0
        var outboundQty = 0.0
        var inboundValue = 0.0
        var outboundValue = 0.0
        rows.forEach { row ->
            when {
                row.quantityBase > 0.0 -> {
                    inboundQty += row.quantityBase
                    inboundValue += abs(row.movementValueBase)
                }
                row.quantityBase < 0.0 -> {
                    outboundQty += abs(row.quantityBase)
                    outboundValue += abs(row.movementValueBase)
                }
            }
        }
        return InventoryMovementSummary(
            inboundQtyBase = inboundQty,
            outboundQtyBase = outboundQty,
            netQtyBase = inboundQty - outboundQty,
            inboundValueBase = inboundValue,
            outboundValueBase = outboundValue,
            movementCount = rows.size
        )
    }

    private fun daysSince(date: Long, asOf: Long): Long = max(0L, (asOf - date) / DAY_MS)
}
