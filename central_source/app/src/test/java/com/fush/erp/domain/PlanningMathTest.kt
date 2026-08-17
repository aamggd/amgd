package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlanningMathTest {
    @Test fun baseline_uses_monthly_average_and_clamps_negative_net_months() {
        assertEquals(75.0, PlanningMath.baseline(listOf(100.0, 50.0)), 1e-9)
        assertEquals(50.0, PlanningMath.baseline(listOf(100.0, -20.0)), 1e-9)
        assertEquals(0.0, PlanningMath.baseline(emptyList()), 1e-9)
    }

    @Test fun forecast_applies_configured_seasonality_factor() {
        assertEquals(150.0, PlanningMath.forecast(100.0, 1.5), 1e-9)
        assertEquals(70.0, PlanningMath.forecast(100.0, 0.7), 1e-9)
    }

    @Test fun invalid_month_and_factor_are_rejected() {
        assertThrows(IllegalArgumentException::class.java) { PlanningMath.validateMonth(13) }
        assertThrows(IllegalArgumentException::class.java) { PlanningMath.validateFactor(-0.1) }
        assertThrows(IllegalArgumentException::class.java) { PlanningMath.forecast(100.0, 10.1) }
    }
    @Test
    fun manual_adjustment_is_difference_between_plan_and_system_forecast() {
        assertEquals(25.0, PlanningMath.manualAdjustment(100.0, 125.0), 0.0001)
        assertEquals(-20.0, PlanningMath.manualAdjustment(100.0, 80.0), 0.0001)
    }

    @Test
    fun planned_quantity_rejects_negative_values() {
        assertThrows(IllegalArgumentException::class.java) { PlanningMath.validatePlannedQuantity(-1.0) }
    }

    @Test
    fun weekly_budget_distribution_sums_to_monthly_target_and_follows_days() {
        val august = PlanningMath.distributeMonthlyTarget(310.0, 31)
        assertEquals(5, august.size)
        assertEquals(310.0, august.sum(), 1e-9)
        assertEquals(70.0, august[0], 1e-9)
        assertEquals(30.0, august[4], 1e-9)

        val february = PlanningMath.distributeMonthlyTarget(280.0, 28)
        assertEquals(4, february.size)
        assertEquals(70.0, february[0], 1e-9)
        assertEquals(280.0, february.sum(), 1e-9)
    }

    @Test
    fun weekly_budget_total_must_equal_monthly_target() {
        PlanningMath.validateWeeklyBudget(100.0, listOf(25.0, 25.0, 25.0, 25.0))
        assertThrows(IllegalArgumentException::class.java) {
            PlanningMath.validateWeeklyBudget(100.0, listOf(20.0, 20.0, 20.0, 20.0))
        }
        assertEquals(75.0, PlanningMath.achievementPct(75.0, 100.0), 1e-9)
    }


    @Test
    fun summer_and_winter_analysis_use_operational_month_groups_and_neutral_missing_factors() {
        assertEquals(true, PlanningMath.isSummerMonth(4))
        assertEquals(true, PlanningMath.isSummerMonth(9))
        assertEquals(false, PlanningMath.isSummerMonth(10))
        assertEquals(false, PlanningMath.isSummerMonth(3))

        val values = listOf(4 to 120.0, 5 to 80.0, 10 to 50.0, 1 to -20.0)
        assertEquals(100.0, PlanningMath.seasonAverage(values, summer = true), 1e-9)
        assertEquals(25.0, PlanningMath.seasonAverage(values, summer = false), 1e-9)

        val factors = mapOf(4 to 1.2, 5 to 1.2, 10 to 0.8)
        assertEquals((1.2 + 1.2 + 1.0 + 1.0 + 1.0 + 1.0) / 6.0, PlanningMath.averageSeasonFactor(factors, summer = true), 1e-9)
        assertEquals((0.8 + 1.0 + 1.0 + 1.0 + 1.0 + 1.0) / 6.0, PlanningMath.averageSeasonFactor(factors, summer = false), 1e-9)
    }

    @Test
    fun relative_difference_requires_nonzero_comparison_base() {
        assertEquals(50.0, PlanningMath.relativeDifferencePct(150.0, 100.0)!!, 1e-9)
        assertEquals(null, PlanningMath.relativeDifferencePct(100.0, 0.0))
    }

    @Test
    fun production_plan_rounds_net_need_to_full_batches_and_preserves_safety_stock() {
        val daily = PlanningMath.dailyRequirement(620.0, 31)
        assertEquals(20.0, daily, 1e-9)
        val safety = PlanningMath.safetyStockQty(daily, 3.0)
        assertEquals(60.0, safety, 1e-9)
        val reorder = PlanningMath.reorderPointQty(daily, 5.0, safety)
        assertEquals(160.0, reorder, 1e-9)
        val netNeed = PlanningMath.netProductionNeed(620.0, safety, 200.0)
        assertEquals(480.0, netNeed, 1e-9)
        assertEquals(2, PlanningMath.requiredBatchCount(netNeed, 360.0))
    }

    @Test
    fun component_requirement_includes_expected_loss_and_purchase_covers_required_plus_safety() {
        val required = PlanningMath.componentRequirement(15.0, 10.0, 2)
        assertEquals(33.0, required, 1e-9)
        val daily = PlanningMath.dailyRequirement(required, 30)
        val safety = PlanningMath.safetyStockQty(daily, 3.0)
        assertEquals(3.3, safety, 1e-9)
        assertEquals(26.3, PlanningMath.suggestedPurchaseQty(required, safety, 10.0), 1e-9)
        assertEquals(0.0, PlanningMath.suggestedPurchaseQty(required, safety, 100.0), 1e-9)
    }

    @Test
    fun planning_days_and_batch_output_are_validated() {
        assertThrows(IllegalArgumentException::class.java) { PlanningMath.validatePlanningDays(-1.0) }
        assertThrows(IllegalArgumentException::class.java) { PlanningMath.requiredBatchCount(100.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { PlanningMath.componentRequirement(1.0, -0.1, 1) }
    }

}
