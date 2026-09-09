package com.fush.erp.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommercialTenantBindingMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FushDatabase::class.java
    )

    @Test
    fun room53To54AddsTenantMarkerWithoutChangingBusinessData() {
        helper.createDatabase(DB_NAME, 53).use { db53 ->
            db53.execSQL(
                "INSERT INTO currencies(code,nameAr,nameEn,symbol,decimals,isBase,isActive) " +
                    "VALUES('V213_KEEP','احتفاظ','Keep','K',2,0,1)"
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 54, true, MIGRATION_53_54_COMMERCIAL_TENANT_BINDING).use { db54 ->
            assertEquals(54, db54.version)
            assertEquals(1L, scalar(db54, "SELECT COUNT(*) FROM currencies WHERE code='V213_KEEP'"))
            assertEquals(1L, scalar(db54, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='cloud_tenant_binding'"))
            assertEquals(0L, scalar(db54, "SELECT COUNT(*) FROM cloud_tenant_binding"))
        }
    }

    private fun scalar(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { c -> check(c.moveToFirst()); c.getLong(0) }

    private companion object { const val DB_NAME = "fush-commercial-tenant-room53-to-54" }
}
