package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V176IntegratedProductionReportContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun releaseIdentityAndDatabaseRemainUpdateSafe() {
        val gradle = listOf(File("app/build.gradle.kts"), File("build.gradle.kts")).first { it.isFile }.readText()
        val db = source("com/fush/erp/data/FushDatabase.kt")
        val versionCode = Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toInt() ?: 0
        assertTrue(versionCode >= 176)
        assertTrue(File("../V176_INTEGRATED_PRODUCTION_REPORT.md").isFile || File("V176_INTEGRATED_PRODUCTION_REPORT.md").isFile)
        assertTrue(gradle.contains("applicationId = \"com.fush.erp.recovery\""))
        assertTrue((Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0) >= 46)
        assertTrue(db.contains("version = FUSH_DB_SCHEMA_VERSION"))
        assertFalse(db.contains("fallbackToDestructiveMigration"))
    }

    @Test
    fun productionReportUsesActualOperationalAndAccountingSources() {
        val dao = source("com/fush/erp/data/dao/ReportDao.kt")
        listOf(
            "productionMaterialVariance",
            "productionOrderOverhead",
            "productionOverheadAccounts",
            "productionWip",
            "productionLosses",
            "productionLaborProductivity",
            "productionDowntime",
            "productionLotTrace",
            "productionAccountingReconciliation",
            "production_materials",
            "production_issues",
            "expense_dimensions",
            "journal_lines",
            "production_operator_assignments"
        ).forEach { assertTrue("Missing production report source: $it", dao.contains(it)) }
    }

    @Test
    fun exportKeepsExistingReportAndAddsRequestedProfessionalSections() {
        val ui = source("com/fush/erp/ui/screens/ReportsScreen.kt")
        listOf(
            "استهلاك المواد الفعلي",
            "المعياري مقابل الفعلي للمواد الخام حسب أمر الإنتاج",
            "أوامر الإنتاج — الخطة والناتج والجودة",
            "أوامر الإنتاج — التكلفة الفعلية ومقارنة تكلفة الوحدة",
            "التكاليف الصناعية غير المباشرة الفعلية",
            "الإنتاج تحت التشغيل WIP",
            "تحليل الإنتاج حسب المنتج",
            "الهالك والمرفوض وإعادة التشغيل",
            "تحليل إنتاجية العمالة",
            "التوقفات وكفاءة التشغيل",
            "تتبع المواد الخام إلى تشغيلة المنتج النهائي",
            "المطابقة مع المخزون والأستاذ العام",
            "الإجماليات النهائية",
            "±15%"
        ).forEach { assertTrue("Missing report section: $it", ui.contains(it)) }
        assertTrue(ui.contains("غير مسجلة على مستوى أمر الإنتاج"))
        assertTrue(ui.contains("لا يوزع اعتباطياً"))
    }

    @Test
    fun pdfColumnPolicyProtectsLongProductionIdentifiers() {
        val export = source("com/fush/erp/ui/export/ReportExportSupport.kt")
        listOf("رقم الأمر", "التشغيلة", "المنتج", "المادة", "السبب", "المسؤول").forEach {
            assertTrue("Missing adaptive column rule for $it", export.contains(it))
        }
        assertTrue(export.contains("wrapText"))
    }
}
