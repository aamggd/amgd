package com.fush.erp.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V213CommercialMultiTenantFoundationContractTest {
    private fun source(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"), File("../$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Source file not found: $relative")
    }

    @Test
    fun apkNoLongerCompilesAFixedOrganizationId() {
        val gradle = source("build.gradle.kts")
        assertFalse(gradle.contains("FUSH_CLOUD_ORGANIZATION_ID"))
        assertFalse(gradle.contains("f0000000-0000-4000-8000-000000000001"))
        assertTrue(gradle.contains("versionCode = 213"))
        assertTrue(gradle.contains("commercial-multitenant-foundation"))
    }

    @Test
    fun cloudSessionCarriesRuntimeTenantAndRoomPersistsTheTenantBoundary() {
        val models = source("src/main/java/com/fush/erp/cloud/CloudModels.kt")
        val store = source("src/main/java/com/fush/erp/cloud/CloudSessionStore.kt")
        val repo = source("src/main/java/com/fush/erp/cloud/CloudSyncRepository.kt")
        val database = source("src/main/java/com/fush/erp/data/FushDatabase.kt")
        val migration = source("src/main/java/com/fush/erp/data/V213CommercialTenantBindingMigration.kt")
        val entity = source("src/main/java/com/fush/erp/data/entity/CloudTenantBindingEntity.kt")

        assertTrue(models.contains("val organizationId: String? = null"))
        assertTrue(models.contains("requireOrganizationId"))
        assertTrue(store.contains("KEY_ORGANIZATION_ID"))
        assertTrue(repo.contains("?select=organization_id,role,is_active"))
        assertTrue(repo.contains("databaseOrganizationId()"))
        assertTrue(repo.contains("bindDatabaseOrganization"))
        assertTrue(repo.contains("session.copy(organizationId = selected)"))
        assertTrue(repo.contains("INSERT OR IGNORE INTO cloud_tenant_binding"))
        assertTrue(entity.contains("tableName = \"cloud_tenant_binding\""))
        assertTrue(migration.contains("Migration(53, 54)"))
        assertTrue(database.contains("FUSH_DB_SCHEMA_VERSION = 54"))
        assertTrue(database.contains("CloudTenantBindingEntity::class"))
        assertFalse(store.contains("installation_organization_id"))
    }

    @Test
    fun everyAndroidCloudTransportUsesTheRuntimeTenant() {
        val cloudDir = File("src/main/java/com/fush/erp/cloud").takeIf { it.isDirectory }
            ?: File("app/src/main/java/com/fush/erp/cloud")
        val forbidden = cloudDir.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .mapNotNull { file ->
                val text = file.readText()
                if (text.contains("FUSH_CLOUD_ORGANIZATION_ID") ||
                    text.contains("f0000000-0000-4000-8000-000000000001")) file.name else null
            }.toList()
        assertTrue("Fixed tenant references remain in Android cloud code: $forbidden", forbidden.isEmpty())
    }

    @Test
    fun supabaseInstallerCreatesIndependentOrganizationsWithoutVendorTenantSeed() {
        val migration = source("cloud/supabase/010_commercial_multitenant_foundation.sql")
        val foundation = source("cloud/supabase/001_cloud_sync_foundation.sql")
        val installer = source("../FUSH_ERP_Mobile_v213-CommercialMultiTenant-Supabase.sql")
        assertTrue(migration.contains("fush_create_organization"))
        assertTrue(migration.contains("gen_random_uuid()"))
        assertTrue(migration.contains("'OWNER'"))
        assertFalse(migration.contains("f0000000-0000-4000-8000-000000000001"))
        assertFalse(foundation.contains("f0000000-0000-4000-8000-000000000001"))
        assertFalse(installer.contains("f0000000-0000-4000-8000-000000000001"))
        assertTrue(installer.contains("uq_fush_sync_devices_org_user_device"))
        assertTrue(migration.contains("user_id = (select auth.uid()) and is_active = true"))
        assertTrue(migration.contains("public.fush_is_org_member(organization_id)"))
        assertTrue(migration.contains("fush_cloud_binding_select_self_or_owner"))
    }

    @Test
    fun rlsAcceptanceGateCoversReadAndWriteCrossTenantDenial() {
        val rls = source("../V213_MULTI_TENANT_RLS_TEST.sql")
        assertTrue(rls.contains("tenant tables without RLS"))
        assertTrue(rls.contains("user A can see tenant B"))
        assertTrue(rls.contains("user B can see tenant A"))
        assertTrue(rls.contains("user A inserted into tenant B"))
        assertTrue(rls.contains("user B inserted into tenant A"))
        assertTrue(rls.contains("user A deleted tenant B"))
        assertTrue(rls.contains("user B deleted tenant A"))
        assertTrue(rls.contains("where organization_id = $1"))
        assertTrue(rls.contains("c.relname like 'fush_%'"))
    }
}
