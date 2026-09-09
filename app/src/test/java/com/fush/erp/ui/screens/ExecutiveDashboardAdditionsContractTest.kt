package com.fush.erp.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExecutiveDashboardAdditionsContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/$relative"), File("app/src/main/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Cannot locate $relative from ${File(".").absolutePath}")
    }

    @Test
    fun dashboard_places_today_decisions_before_finance_and_system_pulse_last() {
        val home = source("java/com/fush/erp/ui/screens/HomeShell.kt")
        val decisions = home.indexOf("dashboard_decisions_today")
        val finance = home.indexOf("dashboard_finance_profitability")
        val inventory = home.indexOf("dashboard_inventory_materials")
        val modules = home.indexOf("dashboard_system_modules")
        val pulse = home.indexOf("dashboard_system_pulse")
        assertTrue(decisions in 0 until finance)
        assertTrue(finance in 0 until inventory)
        assertTrue(inventory in 0 until modules)
        assertTrue(modules in 0 until pulse)
    }

    @Test
    fun finance_metrics_are_backed_by_actual_period_queries() {
        val home = source("java/com/fush/erp/ui/screens/HomeShell.kt")
        val reportDao = source("java/com/fush/erp/data/dao/ReportDao.kt")
        val accountingDao = source("java/com/fush/erp/data/dao/AccountingDao.kt")
        assertTrue(home.contains("currentMonthStart"))
        assertTrue(home.contains("previousMonthStart"))
        assertTrue(home.contains("accountingService.profitLoss"))
        assertTrue(reportDao.contains("suspend fun salesGrossProfit"))
        assertTrue(reportDao.contains("suspend fun overdueReceivablesOlderThan"))
        assertTrue(accountingDao.contains("suspend fun treasuryTotalBookBalance"))
    }

    @Test
    fun material_coverage_uses_approved_plan_without_hidden_expiry_threshold() {
        val inventoryDao = source("java/com/fush/erp/data/dao/AdvancedInventoryDao.kt")
        val home = source("java/com/fush/erp/ui/screens/HomeShell.kt")
        assertTrue(inventoryDao.contains("pp.status = 'APPROVED'"))
        assertTrue(inventoryDao.contains("dailyUsageQtyBase"))
        assertTrue(home.contains("metric_stagnant_policy_missing"))
        assertTrue(home.contains("metric_material_coverage_missing"))
    }
}
