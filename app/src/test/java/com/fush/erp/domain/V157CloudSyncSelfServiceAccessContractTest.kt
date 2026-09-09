package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V157CloudSyncSelfServiceAccessContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File("src/main/java/$relative"), File("app/src/main/java/$relative"))
        return candidates.firstOrNull { it.isFile }?.readText() ?: error("Source file not found: $relative")
    }

    @Test
    fun cloudSyncNavigationIsSelfServiceWithoutElevatedRolePermission() {
        val home = source("com/fush/erp/ui/screens/HomeShell.kt")
        val cloud = source("com/fush/erp/ui/screens/CloudSyncScreen.kt")
        val security = source("com/fush/erp/ui/screens/SecurityScreens.kt")

        assertFalse(home.contains("\"المزامنة السحابية\" to SecurityPermissions.ROLES_MANAGE"))
        assertTrue(home.contains("target == \"المزامنة السحابية\""))
        assertTrue(home.contains("drawerItems.filter { canOpen(it.target) }"))
        assertTrue(home.contains("modules.filter { canOpen(it.target) }"))
        assertTrue(cloud.contains("fun CloudSyncScreen("))
        assertTrue(cloud.contains("user: UserEntity"))
        assertTrue(cloud.contains("repository.signIn(user, email, password)"))
        assertTrue(cloud.contains("repository.testAndRegisterDevice(user)"))
        // Administrative provisioning remains within the security screen and permission-gated.
        assertTrue(security.contains("can(SecurityPermissions.ROLES_MANAGE)"))
        assertTrue(security.contains("provisionCloudUser"))
    }
}
