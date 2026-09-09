package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V149StartupBackupHardeningContractTest {
    @Test
    fun applicationStartupFailsClosedWithoutCrashOnlyLateinitContainer() {
        val app = File("src/main/java/com/fush/erp/FushErpApplication.kt").readText()
        val activity = File("src/main/java/com/fush/erp/MainActivity.kt").readText()

        assertTrue(app.contains("var container: AppContainer? = null"))
        assertTrue(app.contains("var startupFailure: Throwable? = null"))
        assertTrue(app.contains("catch (t: Throwable)"))
        assertTrue(app.contains("container = null"))
        assertTrue(app.contains("Fatal startup initialization failure"))
        assertTrue(activity.contains("if (appContainer == null)"))
        assertTrue(activity.contains("startup_database_error_title"))
        assertFalse(activity.contains("(application as FushErpApplication).container"))
    }

    @Test
    fun android12BackupAndAabLanguageSplitsAreHardened() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val rules = File("src/main/res/xml/data_extraction_rules.xml").readText()
        val gradle = File("build.gradle.kts").readText()

        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        listOf("root", "file", "database", "sharedpref", "external").forEach { domain ->
            assertTrue(rules.contains("domain=\"$domain\""))
        }
        assertTrue(rules.contains("<cloud-backup>"))
        assertTrue(rules.contains("<device-transfer>"))
        assertTrue(gradle.contains("enableSplit = false"))
    }
}
