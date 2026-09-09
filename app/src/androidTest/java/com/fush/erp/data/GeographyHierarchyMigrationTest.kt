package com.fush.erp.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeographyHierarchyMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FushDatabase::class.java
    )

    @Test
    fun room50To51PreservesLegacyTextAndBackfillsStableGovernorateIds() {
        helper.createDatabase(DB_NAME, 50).use { db50 ->
            db50.execSQL("INSERT INTO currencies(code,nameAr,nameEn,symbol,decimals,isBase,isActive) VALUES('YER_NEW','ريال','YER','ر.ي',2,1,1)")
            db50.execSQL("INSERT INTO warehouses(id,code,nameAr,nameEn,location,isActive) VALUES(1,'MAIN','الرئيسي','Main','',1)")
            db50.execSQL("""
                INSERT INTO customers(id,code,nameAr,nameEn,phone,address,province,channel,classification,currencyCode,creditLimitBase,creditDays,allowCredit,salesRepName,salesRepId,isActive,createdAt)
                VALUES
                  (1,'C001','عميل تعز','','','','تعز/بيرباشا','RETAIL','C','YER_NEW',0,0,0,'',NULL,1,1),
                  (2,'C002','عميل عدن','','','','عدن','RETAIL','C','YER_NEW',0,0,0,'',NULL,1,1),
                  (3,'C003','عميل غير معروف','','','','مكان خاص','RETAIL','C','YER_NEW',0,0,0,'',NULL,1,1)
            """.trimIndent())
            db50.execSQL("""
                INSERT INTO sales_invoices(
                    id,invoiceNo,customerId,invoiceDate,dueDate,warehouseId,currencyCode,exchangeRate,paymentType,channel,province,
                    salesRepId,salesRepNameSnapshot,salesRepRatePct,freeQtyLimitPctSnapshot,freeQtyApprovedBy,freeQtyApprovalReason,
                    discountPct,grossOriginal,discountOriginal,transportOriginal,feesOriginal,riskMarginOriginal,totalOriginal,totalBase,
                    treasuryAccountId,status,belowFloorApprovedBy,belowFloorReason,notes,createdBy,createdAt
                ) VALUES(1,'INV-1',1,1,NULL,1,'YER_NEW',1,'CREDIT','RETAIL','تعز / المسبح',NULL,'',0,0,NULL,'',0,100,0,0,0,0,100,100,NULL,'POSTED',NULL,'','',1,1)
            """.trimIndent())
            db50.execSQL("""
                INSERT INTO sales_shipments(id,shipmentNo,shipmentDate,fromWarehouseId,destinationProvince,status,transportReference,notes,createdBy,createdAt,closedAt,cancelledAt,cancellationReason)
                VALUES(1,'SHP-1',1,1,'الحوبان','IN_TRANSIT','','',1,1,NULL,NULL,'')
            """.trimIndent())
        }

        helper.runMigrationsAndValidate(
            DB_NAME,
            51,
            true,
            MIGRATION_50_51_GEOGRAPHY_HIERARCHY
        ).use { db51 ->
            assertEquals(51, db51.version)
            assertEquals(22L, scalar(db51, "SELECT COUNT(*) FROM geo_governorates"))
            assertEquals("YE15", textOrNull(db51, "SELECT governorateId FROM customers WHERE id=1"))
            assertEquals("تعز/بيرباشا", textOrNull(db51, "SELECT province FROM customers WHERE id=1"))
            assertEquals("YE24", textOrNull(db51, "SELECT governorateId FROM customers WHERE id=2"))
            assertNull(textOrNull(db51, "SELECT governorateId FROM customers WHERE id=3"))
            assertEquals("مكان خاص", textOrNull(db51, "SELECT province FROM customers WHERE id=3"))
            assertEquals("YE15", textOrNull(db51, "SELECT governorateId FROM sales_invoices WHERE id=1"))
            assertEquals("تعز / المسبح", textOrNull(db51, "SELECT province FROM sales_invoices WHERE id=1"))
            assertEquals("YE15", textOrNull(db51, "SELECT destinationGovernorateId FROM sales_shipments WHERE id=1"))
            assertEquals("الحوبان", textOrNull(db51, "SELECT destinationProvince FROM sales_shipments WHERE id=1"))
            assertEquals(0L, scalar(db51, "SELECT COUNT(*) FROM geo_districts"))
            assertEquals(0L, scalar(db51, "SELECT COUNT(*) FROM geo_areas"))
            assertEquals(1L, scalar(db51, "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_customers_governorateId'"))
            assertEquals(1L, scalar(db51, "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_sales_shipments_destinationGovernorateId'"))
        }
    }

    private fun scalar(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { c -> check(c.moveToFirst()); c.getLong(0) }

    private fun textOrNull(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): String? =
        db.query(sql).use { c -> check(c.moveToFirst()); if (c.isNull(0)) null else c.getString(0) }

    private companion object {
        const val DB_NAME = "formal-geography-room50-to-51"
    }
}
