package com.fush.erp.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountingWaveBMigrationChainTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FushDatabase::class.java
    )

    @Test
    fun room35To38PreservesRowsAndBuildsWaveBEvidence() {
        helper.createDatabase(DB_NAME, 35).use { db35 ->
            seed35(db35)
            assertEquals(1L, scalar(db35, "SELECT COUNT(*) FROM journal_entries"))
            assertEquals(2L, scalar(db35, "SELECT COUNT(*) FROM journal_lines"))
            assertEquals(1L, scalar(db35, "SELECT COUNT(*) FROM stock_movements"))
        }

        // Execute the complete explicit chain and validate only against the final Room38 schema.
        // The repository intentionally does not carry generated intermediate 36/37 schema files.
        helper.runMigrationsAndValidate(
            DB_NAME,
            38,
            true,
            MIGRATION_35_36_ACCOUNTING_PRECISION,
            MIGRATION_36_37_JOURNAL_LINE_SEMANTICS,
            MIGRATION_37_38_INVENTORY_COST_LAYERS
        ).use { db38 ->
            assertEquals(38, db38.version)

            // 35 -> 36 precision evidence.
            assertEquals(12_344L, scalar(db38, "SELECT debitScaled FROM journal_lines WHERE id=601"))
            assertEquals(12_344L, scalar(db38, "SELECT creditScaled FROM journal_lines WHERE id=602"))
            assertEquals(123_456_788L, scalar(db38, "SELECT exchangeRateScaled FROM journal_entries WHERE id=501"))

            // 36 -> 37 semantic evidence.
            assertEquals(2L, scalar(db38, "SELECT COUNT(*) FROM journal_line_semantics"))
            assertEquals(2L, scalar(db38, "SELECT COUNT(*) FROM journal_line_semantics WHERE transactionCurrencyCode='FUNCTIONAL'"))
            assertEquals(1L, trigger(db38, "trg_journal_line_semantics_autocreate"))

            // 37 -> 38 cost-layer evidence.
            assertEquals(1L, scalar(db38, "SELECT COUNT(*) FROM inventory_cost_layers"))
            assertEquals(-25.0, scalarDouble(db38, "SELECT signedValueBase FROM inventory_cost_layers WHERE stockMovementId=701"), 0.0)
            assertEquals("MOVEMENT_ACTUAL_V1", scalarText(db38, "SELECT costingMethodVersion FROM inventory_cost_layers WHERE stockMovementId=701"))
            assertEquals("NON_GL_INTERNAL", scalarText(db38, "SELECT traceClass FROM inventory_cost_layers WHERE stockMovementId=701"))
            assertEquals(1L, trigger(db38, "trg_inventory_cost_layer_autocreate"))
            assertEquals(1L, trigger(db38, "trg_inventory_cost_layer_no_update"))
            assertEquals(1L, trigger(db38, "trg_inventory_cost_layer_no_delete"))
            assertTrue(scalar(db38, "SELECT COUNT(*) FROM sqlite_master WHERE type='view' AND name='inventory_cost_gl_trace'") == 1L)
        }
    }

    private fun seed35(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO accounts(id,code,nameAr,nameEn,type,parentCode,isPosting,isActive) VALUES(101,'QA-A','A','A','ASSET',NULL,1,1)")
        db.execSQL("INSERT INTO accounts(id,code,nameAr,nameEn,type,parentCode,isPosting,isActive) VALUES(102,'QA-B','B','B','ASSET',NULL,1,1)")
        db.execSQL("INSERT INTO journal_entries(id,entryNo,entryDate,description,currencyCode,exchangeRate,sourceType,sourceId,status,createdBy,createdAt) VALUES(501,'WB-MIG-1',1700000000000,'wave b migration','USD',1.234567885,'MANUAL',NULL,'DRAFT',1,1700000000000)")
        db.execSQL("INSERT INTO journal_lines(id,entryId,accountId,debit,credit,memo) VALUES(601,501,101,1.23445,0.0,'d')")
        db.execSQL("INSERT INTO journal_lines(id,entryId,accountId,debit,credit,memo) VALUES(602,501,102,0.0,1.23445,'c')")

        db.execSQL("INSERT INTO units(id,code,nameAr,nameEn,isActive) VALUES(201,'QA-U','U','U',1)")
        db.execSQL("INSERT INTO warehouses(id,code,nameAr,nameEn,location,isActive) VALUES(301,'QA-W','W','W','',1)")
        db.execSQL("INSERT INTO items(id,code,nameAr,nameEn,category,baseUnitId,reorderLevel,shelfLifeDays,lotTracked,expiryTracked,isActive) VALUES(401,'QA-I','I','I','RAW_MATERIAL',201,0,NULL,0,0,1)")
        db.execSQL("INSERT INTO stock_movements(id,movementDate,warehouseId,itemId,movementType,quantityBase,unitCostBase,referenceType,referenceId,lotNo,expiryDate,createdAt) VALUES(701,1700000000000,301,401,'TRANSFER_OUT',-5.0,5.0,'QA',1,'',NULL,1700000000000)")
    }

    private fun scalar(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Long =
        db.query(sql).use { c -> check(c.moveToFirst()); c.getLong(0) }

    private fun scalarDouble(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Double =
        db.query(sql).use { c -> check(c.moveToFirst()); c.getDouble(0) }

    private fun scalarText(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): String =
        db.query(sql).use { c -> check(c.moveToFirst()); c.getString(0) }

    private fun trigger(db: androidx.sqlite.db.SupportSQLiteDatabase, name: String): Long =
        scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name='$name'")

    private companion object {
        const val DB_NAME = "accounting-wave-b-room35-to-38"
    }
}
