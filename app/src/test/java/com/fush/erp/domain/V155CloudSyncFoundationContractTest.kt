package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V155CloudSyncFoundationContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun cloudFoundationKeepsRoomSchema46AndNoDestructiveMigration() {
        val db = source("com/fush/erp/data/FushDatabase.kt")
        val container = source("com/fush/erp/data/AppContainer.kt")

        assertTrue((Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0) >= 46)
        assertFalse(container.contains("fallbackToDestructiveMigration"))
        assertTrue(container.contains("CloudSyncRepository"))
    }

    @Test
    fun cloudClientUsesOnlyPublishableConfigurationAndRegistersOwnDevice() {
        val repository = source("com/fush/erp/cloud/CloudSyncRepository.kt")
        val sessionStore = source("com/fush/erp/cloud/CloudSessionStore.kt")
        val build = listOf(File("build.gradle.kts"), File("app/build.gradle.kts"))
            .firstOrNull { it.isFile && it.readText().contains("SUPABASE_PUBLISHABLE_KEY") }
            ?.readText() ?: error("App build.gradle.kts not found")

        assertTrue(build.contains("SUPABASE_PUBLISHABLE_KEY"))
        assertFalse(build.contains("service_role", ignoreCase = true))
        assertFalse(build.contains("sb_secret_", ignoreCase = true))
        assertTrue(repository.contains("/auth/v1/token?grant_type=password"))
        assertTrue(repository.contains("/rest/v1/fush_sync_devices?on_conflict=user_id,device_key"))
        assertTrue(repository.contains("Authorization", ignoreCase = true))
        assertTrue(sessionStore.contains("AndroidKeyStore"))
        assertTrue(sessionStore.contains("AES/GCM/NoPadding"))
    }

    @Test
    fun cloudSyncSelfServiceIsAvailableToEveryAuthenticatedLocalUser() {
        val home = source("com/fush/erp/ui/screens/HomeShell.kt")
        val screen = source("com/fush/erp/ui/screens/CloudSyncScreen.kt")

        assertFalse(home.contains("\"المزامنة السحابية\" to SecurityPermissions.ROLES_MANAGE"))
        assertTrue(home.contains("target == \"المزامنة السحابية\""))
        assertTrue(home.contains("CloudSyncScreen(container, user, modifier)"))
        assertTrue(screen.contains("testAndRegisterDevice"))
        assertTrue(screen.contains("cloud_sync_multi_user_subtitle"))
    }
}
