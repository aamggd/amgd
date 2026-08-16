package com.fush.erp.domain

import com.fush.erp.data.entity.ExecutiveReportRow
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PeriodComparisonMetric(
    val label: String,
    val currentBase: Double,
    val previousBase: Double,
    val differenceBase: Double,
    val percentChange: Double?
)

data class PeriodComparisonReport(
    val currentFrom: Long,
    val currentTo: Long,
    val previousFrom: Long?,
    val previousTo: Long?,
    val metrics: List<PeriodComparisonMetric>
) {
    val hasComparablePeriod: Boolean get() = previousFrom != null && previousTo != null
}

object PeriodComparisonMath {
    fun previousRange(periodLabel: String, currentFrom: Long, currentTo: Long): Pair<Long, Long>? {
        if (periodLabel == "كل الفترة" || currentFrom <= 0L || currentTo < currentFrom) return null
        val elapsed = currentTo - currentFrom

        fun alignedPrevious(calendarField: Int): Pair<Long, Long> {
            val startCalendar = Calendar.getInstance().apply {
                timeInMillis = currentFrom
                add(calendarField, -1)
            }
            val previousStart = startCalendar.timeInMillis
            val periodEndCalendar = startCalendar.clone() as Calendar
            periodEndCalendar.add(calendarField, 1)
            val previousPeriodEnd = periodEndCalendar.timeInMillis - 1L
            return previousStart to min(previousPeriodEnd, previousStart + elapsed)
        }

        return when (periodLabel) {
            "اليوم" -> alignedPrevious(Calendar.DAY_OF_MONTH)
            "هذا الشهر" -> alignedPrevious(Calendar.MONTH)
            "هذه السنة" -> alignedPrevious(Calendar.YEAR)
            else -> {
                val previousTo = currentFrom - 1L
                max(0L, previousTo - elapsed) to previousTo
            }
        }
    }

    fun percentChange(current: Double, previous: Double): Double? {
        if (abs(previous) < 0.0000001) return if (abs(current) < 0.0000001) 0.0 else null
        return ((current - previous) / abs(previous)) * 100.0
    }

    fun build(
        currentExecutive: ExecutiveReportRow,
        previousExecutive: ExecutiveReportRow,
        currentProfitLoss: ProfitLossReport,
        previousProfitLoss: ProfitLossReport,
        currentFrom: Long,
        currentTo: Long,
        previousFrom: Long,
        previousTo: Long
    ): PeriodComparisonReport {
        fun metric(label: String, current: Double, previous: Double): PeriodComparisonMetric =
            PeriodComparisonMetric(
                label = label,
                currentBase = current,
                previousBase = previous,
                differenceBase = current - previous,
                percentChange = percentChange(current, previous)
            )

        return PeriodComparisonReport(
            currentFrom = currentFrom,
            currentTo = currentTo,
            previousFrom = previousFrom,
            previousTo = previousTo,
            metrics = listOf(
                metric(
                    "صافي المبيعات",
                    currentExecutive.grossSalesBase - currentExecutive.salesReturnsBase,
                    previousExecutive.grossSalesBase - previousExecutive.salesReturnsBase
                ),
                metric("صافي التحصيل", currentExecutive.collectionsBase, previousExecutive.collectionsBase),
                metric(
                    "صافي المشتريات",
                    currentExecutive.grossPurchasesBase - currentExecutive.purchaseReturnsBase,
                    previousExecutive.grossPurchasesBase - previousExecutive.purchaseReturnsBase
                ),
                metric("إيرادات قائمة الدخل", currentProfitLoss.revenue, previousProfitLoss.revenue),
                metric("مصروفات قائمة الدخل", currentProfitLoss.expenses, previousProfitLoss.expenses),
                metric("صافي الربح", currentProfitLoss.netProfit, previousProfitLoss.netProfit),
                metric("قيمة المخزون", currentExecutive.inventoryValueBase, previousExecutive.inventoryValueBase),
                metric("الذمم المدينة", currentExecutive.receivablesBase, previousExecutive.receivablesBase),
                metric("الذمم المتأخرة", currentExecutive.overdueBase, previousExecutive.overdueBase),
                metric("تكلفة الصيانة", currentExecutive.maintenanceCostBase, previousExecutive.maintenanceCostBase)
            )
        )
    }

    fun unavailable(currentFrom: Long, currentTo: Long): PeriodComparisonReport = PeriodComparisonReport(
        currentFrom = currentFrom,
        currentTo = currentTo,
        previousFrom = null,
        previousTo = null,
        metrics = emptyList()
    )
}
