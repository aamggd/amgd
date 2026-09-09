package com.fush.erp.domain

import com.fush.erp.cloud.canonicalReferenceSnapshotScalar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class V199CanonicalReferenceHashAndChargeMetadataContractTest {
    private fun source(path: String): String = File("src/main/java/$path").readText()

    @Test
    fun canonicalSnapshotNumbersIgnoreJsonNumericSpelling() {
        assertEquals(canonicalReferenceSnapshotScalar(0), canonicalReferenceSnapshotScalar(0.0))
        assertEquals(canonicalReferenceSnapshotScalar(10), canonicalReferenceSnapshotScalar(10.000))
        assertEquals(canonicalReferenceSnapshotScalar(0.10), canonicalReferenceSnapshotScalar(0.1000))
    }

    @Test
    fun referenceSnapshotUsesVersionedCanonicalHashAndLegacyBridge() {
        val engine = source("com/fush/erp/cloud/AccountingCloudSyncEngine.kt")
        assertTrue(engine.contains("hash_version"))
        assertTrue(engine.contains("REFERENCE_SNAPSHOT_HASH_VERSION = 2"))
        assertTrue(engine.contains("legacyReferenceSnapshotHash"))
        assertTrue(engine.contains("validateReferenceSnapshotShape(snapshot)"))
        assertTrue(engine.contains("BigDecimal(value.toString()).stripTrailingZeros().toPlainString()"))
    }

    @Test
    fun chargeTypeCreatedAtIsMetadataNotBusinessConflict() {
        val engine = source("com/fush/erp/cloud/SalesAuxiliaryCloudSyncEngine.kt")
        assertTrue(engine.contains("CHARGE_TYPE_METADATA_FIELDS=setOf(\"created_at_ms\")"))
        assertTrue(engine.contains("alignChargeTypeMetadataForPublish(localBefore, remoteBefore)"))
        assertTrue(engine.contains("resolveMetadataOnlyChargeTypeConflict"))
        assertTrue(engine.contains("businessDifferences(doc.entityType, localDoc.content, doc.content)"))
        // Keep created_at_ms on actual transactional documents; only CHARGE_TYPE treats it as metadata.
        assertTrue(engine.contains(".put(\"status\", row.status).put(\"notes\", row.notes).put(\"created_at_ms\", row.createdAt)"))
    }

    @Test
    fun v199KeepsUpdateIdentityAndRoomSchema49() {
        val gradle = File("build.gradle.kts").readText()
        val db = source("com/fush/erp/data/FushDatabase.kt")
        assertTrue(gradle.contains("applicationId = \"com.fush.erp.recovery\""))
        val versionCode = Regex("versionCode\\s*=\\s*(\\d+)").find(gradle)?.groupValues?.get(1)?.toInt() ?: 0
        assertTrue(versionCode >= 199)
        assertTrue(gradle.contains("versionName ="))
        assertTrue(Regex("FUSH_DB_SCHEMA_VERSION\\s*=\\s*(\\d+)").find(db)?.groupValues?.get(1)?.toInt() ?: 0 >= 49)
        assertFalse(source("com/fush/erp/data/AppContainer.kt").contains("fallbackToDestructiveMigration"))
    }
}
