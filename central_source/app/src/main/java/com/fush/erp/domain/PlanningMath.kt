package com.fush.erp.domain

import kotlin.math.max

object PlanningMath {
    fun baseline(monthlyNetQtyBase: List<Double>): Double {
        if (monthlyNetQtyBase.isEmpty()) return 0.0
        val normalized = monthlyNetQtyBase.map { max(0.0, it) }
        return normalized.average()
    }

    fun forecast(baselineQtyBase: Double, seasonFactor: Double): Double {
        require(baselineQtyBase >= 0.0) { "خط الأساس لا يمكن أن يكون سالبًا" }
        require(seasonFactor in 0.0..10.0) { "معامل الموسمية يجب أن يكون بين 0 و10" }
        return baselineQtyBase * seasonFactor
    }

    fun validateMonth(month: Int) {
        require(month in 1..12) { "الشهر يجب أن يكون بين 1 و12" }
    }

    fun validateFactor(factor: Double) {
        require(factor in 0.0..10.0) { "معامل الموسمية يجب أن يكون بين 0 و10" }
    }

    fun validatePlannedQuantity(quantity: Double) {
        require(quantity.isFinite() && quantity >= 0.0) { "كمية خطة الطلب يجب أن تكون رقمًا غير سالب" }
    }


    fun activeBudgetWeeks(daysInMonth: Int): Int {
        require(daysInMonth in 28..31) { "عدد أيام الشهر غير صحيح" }
        return if (daysInMonth > 28) 5 else 4
    }

    fun distributeMonthlyTarget(monthlyTarget: Double, daysInMonth: Int): List<Double> {
        validatePlannedQuantity(monthlyTarget)
        val weeks = activeBudgetWeeks(daysInMonth)
        val dayCounts = (1..weeks).map { week ->
            when (week) {
                1, 2, 3, 4 -> 7
                else -> daysInMonth - 28
            }
        }
        var allocated = 0.0
        return dayCounts.mapIndexed { index, days ->
            if (index == dayCounts.lastIndex) {
                monthlyTarget - allocated
            } else {
                val value = monthlyTarget * days.toDouble() / daysInMonth.toDouble()
                allocated += value
                value
            }
        }
    }

    fun validateWeeklyBudget(monthlyTarget: Double, weeklyTargets: List<Double>, tolerance: Double = 0.1) {
        validatePlannedQuantity(monthlyTarget)
        require(weeklyTargets.isNotEmpty()) { "أدخل الموازنة الأسبوعية" }
        weeklyTargets.forEach { validatePlannedQuantity(it) }
        require(kotlin.math.abs(weeklyTargets.sum() - monthlyTarget) <= tolerance) {
            "مجموع الموازنة الأسبوعية يجب أن يساوي الموازنة الشهرية"
        }
    }

    fun achievementPct(actual: Double, target: Double): Double =
        if (target <= 0.0) 0.0 else (actual / target) * 100.0

    val summerMonths: Set<Int> = setOf(4, 5, 6, 7, 8, 9)
    val winterMonths: Set<Int> = setOf(10, 11, 12, 1, 2, 3)

    fun isSummerMonth(month: Int): Boolean {
        validateMonth(month)
        return month in summerMonths
    }

    fun seasonAverage(values: List<Pair<Int, Double>>, summer: Boolean): Double {
        val months = if (summer) summerMonths else winterMonths
        val selected = values.filter { it.first in months }.map { max(0.0, it.second) }
        return if (selected.isEmpty()) 0.0 else selected.average()
    }

    fun averageSeasonFactor(factorsByMonth: Map<Int, Double>, summer: Boolean): Double {
        val months = if (summer) summerMonths else winterMonths
        return months.map { month ->
            val factor = factorsByMonth[month] ?: 1.0
            validateFactor(factor)
            factor
        }.average()
    }

    fun relativeDifferencePct(high: Double, low: Double): Double? =
        if (low <= 0.0) null else ((high - low) / low) * 100.0



    fun validatePlanningDays(days: Double) {
        require(days.isFinite() && days >= 0.0) { "أيام مخزون الأمان وزمن التوريد يجب أن تكون أرقامًا غير سالبة" }
    }

    fun dailyRequirement(monthlyQtyBase: Double, daysInMonth: Int): Double {
        validatePlannedQuantity(monthlyQtyBase)
        require(daysInMonth in 28..31) { "عدد أيام الشهر غير صحيح" }
        return monthlyQtyBase / daysInMonth.toDouble()
    }

    fun safetyStockQty(dailyRequirementQtyBase: Double, safetyStockDays: Double): Double {
        validatePlannedQuantity(dailyRequirementQtyBase)
        validatePlanningDays(safetyStockDays)
        return dailyRequirementQtyBase * safetyStockDays
    }

    fun reorderPointQty(dailyRequirementQtyBase: Double, leadTimeDays: Double, safetyStockQtyBase: Double): Double {
        validatePlannedQuantity(dailyRequirementQtyBase)
        validatePlanningDays(leadTimeDays)
        validatePlannedQuantity(safetyStockQtyBase)
        return dailyRequirementQtyBase * leadTimeDays + safetyStockQtyBase
    }

    fun netProductionNeed(approvedDemandQtyBase: Double, safetyStockQtyBase: Double, currentFinishedStockQtyBase: Double): Double {
        validatePlannedQuantity(approvedDemandQtyBase)
        validatePlannedQuantity(safetyStockQtyBase)
        require(currentFinishedStockQtyBase.isFinite()) { "رصيد المنتج النهائي غير صالح" }
        return max(0.0, approvedDemandQtyBase + safetyStockQtyBase - max(0.0, currentFinishedStockQtyBase))
    }

    fun requiredBatchCount(netNeedQtyBase: Double, batchOutputQtyBase: Double): Int {
        validatePlannedQuantity(netNeedQtyBase)
        require(batchOutputQtyBase.isFinite() && batchOutputQtyBase > 0.0) { "الناتج القياسي للطرحة يجب أن يكون أكبر من صفر" }
        if (netNeedQtyBase <= 0.000000001) return 0
        return kotlin.math.ceil(netNeedQtyBase / batchOutputQtyBase).toInt()
    }

    fun componentRequirement(perBatchQtyBase: Double, expectedLossPct: Double, batchCount: Int): Double {
        validatePlannedQuantity(perBatchQtyBase)
        require(expectedLossPct.isFinite() && expectedLossPct >= 0.0) { "نسبة الفاقد المتوقعة غير صالحة" }
        require(batchCount >= 0) { "عدد الطرحات لا يمكن أن يكون سالبًا" }
        return perBatchQtyBase * batchCount.toDouble() * (1.0 + expectedLossPct / 100.0)
    }

    fun suggestedPurchaseQty(requiredQtyBase: Double, safetyStockQtyBase: Double, currentStockQtyBase: Double): Double {
        validatePlannedQuantity(requiredQtyBase)
        validatePlannedQuantity(safetyStockQtyBase)
        require(currentStockQtyBase.isFinite()) { "رصيد المادة غير صالح" }
        return max(0.0, requiredQtyBase + safetyStockQtyBase - max(0.0, currentStockQtyBase))
    }

    fun manualAdjustment(systemForecastQtyBase: Double, plannedQtyBase: Double): Double {
        validatePlannedQuantity(systemForecastQtyBase)
        validatePlannedQuantity(plannedQtyBase)
        return plannedQtyBase - systemForecastQtyBase
    }
}
