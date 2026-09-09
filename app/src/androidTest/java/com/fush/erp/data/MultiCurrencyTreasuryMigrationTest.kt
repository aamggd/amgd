package com.fush.erp.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiCurrencyTreasuryMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FushDatabase::class.java
    )

    @Test
    fun room51To52PreservesTreasuryAndBackfillsGroupCode() {
        helper.createDatabase(DB_NAME, 51).use { db51 ->
            db51.execSQL("INSERT INTO currencies(code,nameAr,nameEn,symbol,decimals,isBase,isActive) VALUES('YER_NEW','ريال جديد','YER New','YER',2,1,1)")
            db51.execSQL("INSERT INTO accounts(id,code,nameAr,nameEn,type,parentCode,isPosting,isActive) VALUES(1,'1000','الأصول','Assets','ASSET',NULL,0,1)")
            db51.execSQL("INSERT INTO accounts(id,code,nameAr,nameEn,type,parentCode,isPosting,isActive) VALUES(2,'1100','الصندوق','Cash','ASSET','1000',1,1)")
            db51.execSQL("INSERT INTO treasury_accounts(id,code,nameAr,kind,accountId,currencyCode,bankName,accountNumber,isActive,createdBy,createdAt) VALUES(1,'CASH-MAIN','الصندوق الرئيسي','CASH',2,'YER_NEW','','',1,1,1)")
        }
        helper.runMigrationsAndValidate(DB_NAME, 52, true, MIGRATION_51_52_MULTI_CURRENCY_TREASURY).use { db52 ->
            assertEquals(52, db52.version)
            assertEquals("CASH-MAIN", text(db52, "SELECT groupCode FROM treasury_accounts WHERE id=1"))
            assertEquals(1L, scalar(db52, "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_treasury_accounts_groupCode_currencyCode'"))
        }
    }

    private fun scalar(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { c -> check(c.moveToFirst()); c.getLong(0) }

    private fun text(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): String =
        db.query(sql).use { c -> check(c.moveToFirst()); c.getString(0) }

    private companion object { const val DB_NAME = "multi-currency-treasury-room51-to-52" }
}
