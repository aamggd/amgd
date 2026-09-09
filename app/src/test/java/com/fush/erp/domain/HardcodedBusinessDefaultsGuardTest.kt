package com.fush.erp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HardcodedBusinessDefaultsGuardTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Cannot locate $relative from ${File(".").absolutePath}")
    }

    @Test
    fun startupDoesNotSeedBusinessSpecificFactoryData() {
        val source = source("com/fush/erp/data/AppContainer.kt")
        listOf(
            "seedFactoryItemsAndConversions",
            "seedDefaultRecipe",
            "seedSalesPrices",
            "seedMaintenanceAssetsAndPlans",
            "seedTrainingCourses",
            "seedGeographyPolicies",
            "seedPlanningExchangeRate",
            "repairLegacyFinishedGoods",
            "FG-FUSH-60",
            "RM-BORIC",
            "BOM-FUSH-60",
            "1554.62",
            "10_000.0"
        ).forEach { forbidden -> assertFalse("Found hidden business seed: $forbidden", source.contains(forbidden)) }
    }

    @Test
    fun salesHasNoHiddenPriceCommissionCreditOrAdenTransportDefaults() {
        val math = source("com/fush/erp/domain/SalesMath.kt")
        val service = source("com/fush/erp/domain/SalesService.kt")
        val ui = source("com/fush/erp/ui/screens/SalesScreens.kt")
        listOf("MAX_CREDIT_DAYS", "DEFAULT_COMMISSION_PCT", "FUSH_PRICE_FLOOR_BASE_PER_BOTTLE", "BOTTLES_PER_CARTON")
            .forEach { assertFalse(math.contains(it)) }
        assertFalse(service.contains("FG-FUSH-60"))
        assertFalse(ui.contains("province == \"عدن\""))
        assertFalse(ui.contains("10000.0"))
        assertFalse(ui.contains("/ 480.0"))
    }

    @Test
    fun finishedGoodsRequireExplicitShelfLifeAndGeographyInputsStartBlank() {
        val master = source("com/fush/erp/domain/MasterDataService.kt")
        val production = source("com/fush/erp/domain/ProductionService.kt")
        val geo = source("com/fush/erp/ui/screens/GeographyScreens.kt")
        assertFalse(master.contains("?: 730"))
        assertTrue(production.contains("requireNotNull(product.shelfLifeDays)"))
        assertFalse(geo.contains("1554.62"))
        assertFalse(geo.contains("480000"))
        assertFalse(geo.contains("FG-FUSH-60"))
    }
    @Test
    fun customerAndPlanningDoNotEmbedSpecificProvinceDefaults() {
        val salesEntities = source("com/fush/erp/data/entity/SalesEntities.kt")
        val planningDao = source("com/fush/erp/data/dao/PlanningDao.kt")
        assertFalse(salesEntities.contains("val province: String = \"تعز\""))
        listOf("TAIZ", "ADEN", "SANAA", "%تعز%", "%عدن%", "%صنعاء%").forEach { token ->
            assertFalse("planning DAO still contains fixed province token: $token", planningDao.contains(token))
        }
    }

    @Test
    fun reportsDoNotDependOnFushSpecificItemCode() {
        val reportMath = source("com/fush/erp/domain/ReportMath.kt")
        val reportDao = source("com/fush/erp/data/dao/ReportDao.kt")
        assertFalse(reportMath.lowercase().contains("fg-fush"))
        assertFalse(reportDao.lowercase().contains("fg-fush"))
    }

}
