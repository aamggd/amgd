#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(sys.argv[1]).resolve()

def p(rel): return ROOT / rel

def replace(rel, old, new, expected=1):
    path = p(rel)
    text = path.read_text()
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{rel}: expected {expected} occurrences, found {count}: {old[:100]!r}")
    path.write_text(text.replace(old, new))

# --- Room entity / schema ---
replace(
    "app/src/main/java/com/fush/erp/data/entity/PurchaseEntities.kt",
    "import androidx.room.Entity\n",
    "import androidx.room.ColumnInfo\nimport androidx.room.Entity\n",
)
replace(
    "app/src/main/java/com/fush/erp/data/entity/PurchaseEntities.kt",
    'indices = [Index("warehouseId"), Index("itemId"), Index(value = ["referenceType", "referenceId"])],',
    'indices = [\n'
    '        Index("warehouseId"),\n'
    '        Index("itemId"),\n'
    '        Index(value = ["referenceType", "referenceId"]),\n'
    '        Index(value = ["sourceKey"], unique = true)\n'
    '    ],',
)
replace(
    "app/src/main/java/com/fush/erp/data/entity/PurchaseEntities.kt",
    "    val expiryDate: Long? = null,\n    val createdAt: Long = System.currentTimeMillis()\n)",
    "    val expiryDate: Long? = null,\n"
    "    @ColumnInfo(defaultValue = \"''\") val sourceKey: String = \"\",\n"
    "    val createdAt: Long = System.currentTimeMillis()\n)",
)

replace(
    "app/src/main/java/com/fush/erp/data/FushDatabase.kt",
    "const val FUSH_DB_SCHEMA_VERSION = 35",
    "// Inventory P1 branch-only provisional schema. Final numbering is assigned by Central integration.\n"
    "const val FUSH_DB_SCHEMA_VERSION = 36",
)

migration = r'''

// Inventory P1 — PROVISIONAL / BRANCH ONLY.
// Adds a durable source identity for every new stock movement without rewriting any historical
// movement facts. Legacy rows receive a unique LEGACY:<id> key only; quantity, cost, date, lot,
// reference fields and primary keys are preserved exactly.
val MIGRATION_35_36_INVENTORY_P1_PROVISIONAL = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE stock_movements ADD COLUMN sourceKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE stock_movements SET sourceKey = 'LEGACY:' || id WHERE sourceKey = ''")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_stock_movements_sourceKey ON stock_movements(sourceKey)")

        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trg_stock_movement_source_key_required_insert
            BEFORE INSERT ON stock_movements
            WHEN TRIM(NEW.sourceKey) = ''
            BEGIN
                SELECT RAISE(ABORT, 'STOCK_MOVEMENT_SOURCE_KEY_REQUIRED');
            END
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trg_stock_movement_contract_insert
            BEFORE INSERT ON stock_movements
            WHEN NOT (
                (NEW.movementType = 'OPENING' AND NEW.referenceType = 'JOURNAL_ENTRY' AND NEW.quantityBase > 0) OR
                (NEW.movementType = 'PURCHASE' AND NEW.referenceType = 'PURCHASE_LINE' AND NEW.quantityBase > 0) OR
                (NEW.movementType = 'PURCHASE_RETURN' AND NEW.referenceType = 'PURCHASE_RETURN_LINE' AND NEW.quantityBase < 0) OR
                (NEW.movementType = 'SALE' AND NEW.referenceType = 'SALES_ALLOCATION' AND NEW.quantityBase < 0) OR
                (NEW.movementType = 'SALES_RETURN' AND NEW.referenceType = 'SALES_RETURN_ALLOCATION' AND NEW.quantityBase > 0) OR
                (NEW.movementType = 'PRODUCTION_ISSUE' AND NEW.referenceType = 'PRODUCTION_ISSUE' AND NEW.quantityBase < 0) OR
                (NEW.movementType = 'PRODUCTION_ISSUE_RETURN' AND NEW.referenceType = 'PRODUCTION_ISSUE' AND NEW.quantityBase > 0) OR
                (NEW.movementType = 'PRODUCTION_ISSUE_CORRECTION' AND NEW.referenceType = 'PRODUCTION_ISSUE' AND NEW.quantityBase < 0) OR
                (NEW.movementType = 'PRODUCTION_RECEIPT' AND NEW.referenceType = 'PRODUCTION_BATCH' AND NEW.quantityBase > 0) OR
                (NEW.movementType = 'PRODUCTION_RECEIPT_CORRECTION' AND NEW.referenceType = 'PRODUCTION_BATCH' AND NEW.quantityBase > 0) OR
                (NEW.movementType = 'PRODUCTION_COST_REVALUE_OUT' AND NEW.referenceType = 'PRODUCTION_BATCH' AND NEW.quantityBase < 0) OR
                (NEW.movementType = 'PRODUCTION_COST_REVALUE_IN' AND NEW.referenceType = 'PRODUCTION_BATCH' AND NEW.quantityBase > 0) OR
                (NEW.movementType = 'COUNT_ADJUSTMENT' AND NEW.referenceType = 'INVENTORY_COUNT_LINE' AND NEW.quantityBase <> 0) OR
                (NEW.movementType = 'LEGACY_LOT_RECLASS_OUT' AND NEW.referenceType = 'AUDIT_EVENT' AND NEW.quantityBase < 0) OR
                (NEW.movementType = 'LEGACY_LOT_RECLASS_IN' AND NEW.referenceType = 'AUDIT_EVENT' AND NEW.quantityBase > 0) OR
                (NEW.movementType = 'TRANSFER_OUT' AND NEW.referenceType = 'WAREHOUSE_TRANSFER_LINE' AND NEW.quantityBase < 0) OR
                (NEW.movementType = 'TRANSFER_IN' AND NEW.referenceType = 'WAREHOUSE_TRANSFER_LINE' AND NEW.quantityBase > 0) OR
                (NEW.movementType = 'TRANSFER_REVERSAL_OUT' AND NEW.referenceType = 'WAREHOUSE_TRANSFER_LINE' AND NEW.quantityBase < 0) OR
                (NEW.movementType = 'TRANSFER_REVERSAL_IN' AND NEW.referenceType = 'WAREHOUSE_TRANSFER_LINE' AND NEW.quantityBase > 0)
            )
            BEGIN
                SELECT RAISE(ABORT, 'INVALID_STOCK_MOVEMENT_CONTRACT');
            END
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trg_stock_movement_source_exists_insert
            BEFORE INSERT ON stock_movements
            WHEN
                (NEW.referenceType = 'JOURNAL_ENTRY' AND NOT EXISTS (SELECT 1 FROM journal_entries WHERE id = NEW.referenceId)) OR
                (NEW.referenceType = 'PURCHASE_LINE' AND NOT EXISTS (SELECT 1 FROM purchase_lines WHERE id = NEW.referenceId)) OR
                (NEW.referenceType = 'PURCHASE_RETURN_LINE' AND NOT EXISTS (SELECT 1 FROM purchase_return_lines WHERE id = NEW.referenceId)) OR
                (NEW.referenceType = 'SALES_ALLOCATION' AND NOT EXISTS (SELECT 1 FROM sales_allocations WHERE id = NEW.referenceId)) OR
                (NEW.referenceType = 'SALES_RETURN_ALLOCATION' AND NOT EXISTS (SELECT 1 FROM sales_return_allocations WHERE id = NEW.referenceId)) OR
                (NEW.referenceType = 'PRODUCTION_ISSUE' AND NOT EXISTS (SELECT 1 FROM production_issues WHERE id = NEW.referenceId)) OR
                (NEW.referenceType = 'PRODUCTION_BATCH' AND NOT EXISTS (SELECT 1 FROM production_batches WHERE id = NEW.referenceId)) OR
                (NEW.referenceType = 'INVENTORY_COUNT_LINE' AND NOT EXISTS (SELECT 1 FROM inventory_count_lines WHERE id = NEW.referenceId)) OR
                (NEW.referenceType = 'WAREHOUSE_TRANSFER_LINE' AND NOT EXISTS (SELECT 1 FROM warehouse_transfer_lines WHERE id = NEW.referenceId)) OR
                (NEW.referenceType = 'AUDIT_EVENT' AND NOT EXISTS (SELECT 1 FROM audit_events WHERE id = NEW.referenceId)) OR
                NEW.referenceType NOT IN (
                    'JOURNAL_ENTRY', 'PURCHASE_LINE', 'PURCHASE_RETURN_LINE',
                    'SALES_ALLOCATION', 'SALES_RETURN_ALLOCATION',
                    'PRODUCTION_ISSUE', 'PRODUCTION_BATCH',
                    'INVENTORY_COUNT_LINE', 'WAREHOUSE_TRANSFER_LINE', 'AUDIT_EVENT'
                )
            BEGIN
                SELECT RAISE(ABORT, 'ORPHAN_STOCK_MOVEMENT_SOURCE');
            END
        """.trimIndent())
    }
}
'''
migrations_path = p("app/src/main/java/com/fush/erp/data/Migrations.kt")
migrations_text = migrations_path.read_text()
if "MIGRATION_35_36_INVENTORY_P1_PROVISIONAL" in migrations_text:
    raise SystemExit("Inventory P1 migration already present")
migrations_path.write_text(migrations_text.rstrip() + migration + "\n")

replace(
    "app/src/main/java/com/fush/erp/data/AppContainer.kt",
    "MIGRATION_33_34_FIXED_ASSETS, MIGRATION_34_35_ACCOUNTING_P1).build()",
    "MIGRATION_33_34_FIXED_ASSETS, MIGRATION_34_35_ACCOUNTING_P1, MIGRATION_35_36_INVENTORY_P1_PROVISIONAL).build()",
)

# --- DAO: unchecked insert + polymorphic source existence lookup ---
replace(
    "app/src/main/java/com/fush/erp/data/dao/PurchaseDaos.kt",
    "    suspend fun insertMovement(row: StockMovementEntity): Long\n",
    "    /** P1 low-level insert. Application mutation paths must use StockMovementWriter. */\n"
    "    suspend fun insertMovementUnchecked(row: StockMovementEntity): Long\n\n"
    "    @Query(\"\"\"\n"
    "        SELECT CASE\n"
    "            WHEN :referenceType = 'JOURNAL_ENTRY' THEN EXISTS(SELECT 1 FROM journal_entries WHERE id = :referenceId)\n"
    "            WHEN :referenceType = 'PURCHASE_LINE' THEN EXISTS(SELECT 1 FROM purchase_lines WHERE id = :referenceId)\n"
    "            WHEN :referenceType = 'PURCHASE_RETURN_LINE' THEN EXISTS(SELECT 1 FROM purchase_return_lines WHERE id = :referenceId)\n"
    "            WHEN :referenceType = 'SALES_ALLOCATION' THEN EXISTS(SELECT 1 FROM sales_allocations WHERE id = :referenceId)\n"
    "            WHEN :referenceType = 'SALES_RETURN_ALLOCATION' THEN EXISTS(SELECT 1 FROM sales_return_allocations WHERE id = :referenceId)\n"
    "            WHEN :referenceType = 'PRODUCTION_ISSUE' THEN EXISTS(SELECT 1 FROM production_issues WHERE id = :referenceId)\n"
    "            WHEN :referenceType = 'PRODUCTION_BATCH' THEN EXISTS(SELECT 1 FROM production_batches WHERE id = :referenceId)\n"
    "            WHEN :referenceType = 'INVENTORY_COUNT_LINE' THEN EXISTS(SELECT 1 FROM inventory_count_lines WHERE id = :referenceId)\n"
    "            WHEN :referenceType = 'WAREHOUSE_TRANSFER_LINE' THEN EXISTS(SELECT 1 FROM warehouse_transfer_lines WHERE id = :referenceId)\n"
    "            WHEN :referenceType = 'AUDIT_EVENT' THEN EXISTS(SELECT 1 FROM audit_events WHERE id = :referenceId)\n"
    "            ELSE 0\n"
    "        END\n"
    "    \"\"\")\n"
    "    suspend fun stockMovementSourceExists(referenceType: String, referenceId: Long): Boolean\n",
)

# --- New canonical policy + writer ---
policy = r'''package com.fush.erp.domain

import kotlin.math.abs

object StockMovementType {
    const val OPENING = "OPENING"
    const val PURCHASE = "PURCHASE"
    const val PURCHASE_RETURN = "PURCHASE_RETURN"
    const val SALE = "SALE"
    const val SALES_RETURN = "SALES_RETURN"
    const val PRODUCTION_ISSUE = "PRODUCTION_ISSUE"
    const val PRODUCTION_ISSUE_RETURN = "PRODUCTION_ISSUE_RETURN"
    const val PRODUCTION_ISSUE_CORRECTION = "PRODUCTION_ISSUE_CORRECTION"
    const val PRODUCTION_RECEIPT = "PRODUCTION_RECEIPT"
    const val PRODUCTION_RECEIPT_CORRECTION = "PRODUCTION_RECEIPT_CORRECTION"
    const val PRODUCTION_COST_REVALUE_OUT = "PRODUCTION_COST_REVALUE_OUT"
    const val PRODUCTION_COST_REVALUE_IN = "PRODUCTION_COST_REVALUE_IN"
    const val COUNT_ADJUSTMENT = "COUNT_ADJUSTMENT"
    const val LEGACY_LOT_RECLASS_OUT = "LEGACY_LOT_RECLASS_OUT"
    const val LEGACY_LOT_RECLASS_IN = "LEGACY_LOT_RECLASS_IN"
    const val TRANSFER_OUT = "TRANSFER_OUT"
    const val TRANSFER_IN = "TRANSFER_IN"
    const val TRANSFER_REVERSAL_OUT = "TRANSFER_REVERSAL_OUT"
    const val TRANSFER_REVERSAL_IN = "TRANSFER_REVERSAL_IN"
}

object StockMovementReferenceType {
    const val JOURNAL_ENTRY = "JOURNAL_ENTRY"
    const val PURCHASE_LINE = "PURCHASE_LINE"
    const val PURCHASE_RETURN_LINE = "PURCHASE_RETURN_LINE"
    const val SALES_ALLOCATION = "SALES_ALLOCATION"
    const val SALES_RETURN_ALLOCATION = "SALES_RETURN_ALLOCATION"
    const val PRODUCTION_ISSUE = "PRODUCTION_ISSUE"
    const val PRODUCTION_BATCH = "PRODUCTION_BATCH"
    const val INVENTORY_COUNT_LINE = "INVENTORY_COUNT_LINE"
    const val WAREHOUSE_TRANSFER_LINE = "WAREHOUSE_TRANSFER_LINE"
    const val AUDIT_EVENT = "AUDIT_EVENT"
}

object StockMovementPolicy {
    private enum class Direction { IN, OUT, EITHER }
    private data class Contract(val referenceType: String, val direction: Direction)

    private val contracts = mapOf(
        StockMovementType.OPENING to Contract(StockMovementReferenceType.JOURNAL_ENTRY, Direction.IN),
        StockMovementType.PURCHASE to Contract(StockMovementReferenceType.PURCHASE_LINE, Direction.IN),
        StockMovementType.PURCHASE_RETURN to Contract(StockMovementReferenceType.PURCHASE_RETURN_LINE, Direction.OUT),
        StockMovementType.SALE to Contract(StockMovementReferenceType.SALES_ALLOCATION, Direction.OUT),
        StockMovementType.SALES_RETURN to Contract(StockMovementReferenceType.SALES_RETURN_ALLOCATION, Direction.IN),
        StockMovementType.PRODUCTION_ISSUE to Contract(StockMovementReferenceType.PRODUCTION_ISSUE, Direction.OUT),
        StockMovementType.PRODUCTION_ISSUE_RETURN to Contract(StockMovementReferenceType.PRODUCTION_ISSUE, Direction.IN),
        StockMovementType.PRODUCTION_ISSUE_CORRECTION to Contract(StockMovementReferenceType.PRODUCTION_ISSUE, Direction.OUT),
        StockMovementType.PRODUCTION_RECEIPT to Contract(StockMovementReferenceType.PRODUCTION_BATCH, Direction.IN),
        StockMovementType.PRODUCTION_RECEIPT_CORRECTION to Contract(StockMovementReferenceType.PRODUCTION_BATCH, Direction.IN),
        StockMovementType.PRODUCTION_COST_REVALUE_OUT to Contract(StockMovementReferenceType.PRODUCTION_BATCH, Direction.OUT),
        StockMovementType.PRODUCTION_COST_REVALUE_IN to Contract(StockMovementReferenceType.PRODUCTION_BATCH, Direction.IN),
        StockMovementType.COUNT_ADJUSTMENT to Contract(StockMovementReferenceType.INVENTORY_COUNT_LINE, Direction.EITHER),
        StockMovementType.LEGACY_LOT_RECLASS_OUT to Contract(StockMovementReferenceType.AUDIT_EVENT, Direction.OUT),
        StockMovementType.LEGACY_LOT_RECLASS_IN to Contract(StockMovementReferenceType.AUDIT_EVENT, Direction.IN),
        StockMovementType.TRANSFER_OUT to Contract(StockMovementReferenceType.WAREHOUSE_TRANSFER_LINE, Direction.OUT),
        StockMovementType.TRANSFER_IN to Contract(StockMovementReferenceType.WAREHOUSE_TRANSFER_LINE, Direction.IN),
        StockMovementType.TRANSFER_REVERSAL_OUT to Contract(StockMovementReferenceType.WAREHOUSE_TRANSFER_LINE, Direction.OUT),
        StockMovementType.TRANSFER_REVERSAL_IN to Contract(StockMovementReferenceType.WAREHOUSE_TRANSFER_LINE, Direction.IN),
    )

    fun expectedReferenceType(movementType: String): String =
        requireNotNull(contracts[movementType]) { "نوع حركة مخزون غير معتمد: $movementType" }.referenceType

    fun sourceKey(
        movementType: String,
        referenceType: String,
        referenceId: Long,
        sourceDiscriminator: String = "",
    ): String {
        validateReference(movementType, referenceType, referenceId)
        val normalizedDiscriminator = sourceDiscriminator.trim()
        require(normalizedDiscriminator.none { it == ':' || it.isWhitespace() }) {
            "مميز مصدر حركة المخزون غير صالح"
        }
        val base = "P1:$referenceType:$referenceId:$movementType"
        return if (normalizedDiscriminator.isBlank()) base else "$base:$normalizedDiscriminator"
    }

    fun validate(
        movementType: String,
        quantityBase: Double,
        unitCostBase: Double,
        referenceType: String,
        referenceId: Long,
        sourceKey: String,
    ) {
        validateReference(movementType, referenceType, referenceId)
        require(quantityBase.isFinite() && abs(quantityBase) > 1e-12) { "كمية حركة المخزون يجب أن تكون غير صفرية ومحدودة" }
        require(unitCostBase.isFinite() && unitCostBase >= 0.0) { "تكلفة حركة المخزون غير صالحة" }
        when (contracts.getValue(movementType).direction) {
            Direction.IN -> require(quantityBase > 0.0) { "اتجاه كمية حركة المخزون لا يطابق نوع الحركة" }
            Direction.OUT -> require(quantityBase < 0.0) { "اتجاه كمية حركة المخزون لا يطابق نوع الحركة" }
            Direction.EITHER -> Unit
        }
        val prefix = "P1:$referenceType:$referenceId:$movementType"
        require(sourceKey == prefix || sourceKey.startsWith("$prefix:")) { "مفتاح مصدر حركة المخزون لا يطابق المرجع" }
    }

    private fun validateReference(movementType: String, referenceType: String, referenceId: Long) {
        val contract = requireNotNull(contracts[movementType]) { "نوع حركة مخزون غير معتمد: $movementType" }
        require(referenceType == contract.referenceType) {
            "مرجع حركة المخزون لا يطابق النوع $movementType: المتوقع ${contract.referenceType}"
        }
        require(referenceId > 0L) { "معرف مصدر حركة المخزون مطلوب" }
    }
}
'''
p("app/src/main/java/com/fush/erp/domain/StockMovementPolicy.kt").write_text(policy)

writer = r'''package com.fush.erp.domain

import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.StockMovementEntity

/** Single P1 application path for persisting new stock movements. */
class StockMovementWriter(private val db: FushDatabase) {
    suspend fun insert(row: StockMovementEntity, sourceDiscriminator: String = ""): Long {
        val sourceKey = StockMovementPolicy.sourceKey(
            movementType = row.movementType,
            referenceType = row.referenceType,
            referenceId = row.referenceId,
            sourceDiscriminator = sourceDiscriminator,
        )
        StockMovementPolicy.validate(
            movementType = row.movementType,
            quantityBase = row.quantityBase,
            unitCostBase = row.unitCostBase,
            referenceType = row.referenceType,
            referenceId = row.referenceId,
            sourceKey = sourceKey,
        )
        require(db.stockDao().stockMovementSourceExists(row.referenceType, row.referenceId)) {
            "مصدر حركة المخزون غير موجود: ${row.referenceType}#${row.referenceId}"
        }
        return db.stockDao().insertMovementUnchecked(row.copy(sourceKey = sourceKey))
    }
}
'''
p("app/src/main/java/com/fush/erp/domain/StockMovementWriter.kt").write_text(writer)

test = r'''package com.fush.erp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StockMovementPolicyTest {
    @Test
    fun canonical_reference_type_is_fixed_per_movement_type() {
        assertEquals(StockMovementReferenceType.PURCHASE_LINE, StockMovementPolicy.expectedReferenceType(StockMovementType.PURCHASE))
        assertEquals(StockMovementReferenceType.SALES_ALLOCATION, StockMovementPolicy.expectedReferenceType(StockMovementType.SALE))
        assertEquals(StockMovementReferenceType.WAREHOUSE_TRANSFER_LINE, StockMovementPolicy.expectedReferenceType(StockMovementType.TRANSFER_IN))
    }

    @Test
    fun stable_source_keys_prevent_duplicate_source_leg_but_allow_paired_transfer_legs() {
        val out = StockMovementPolicy.sourceKey(StockMovementType.TRANSFER_OUT, StockMovementReferenceType.WAREHOUSE_TRANSFER_LINE, 77L)
        val outAgain = StockMovementPolicy.sourceKey(StockMovementType.TRANSFER_OUT, StockMovementReferenceType.WAREHOUSE_TRANSFER_LINE, 77L)
        val incoming = StockMovementPolicy.sourceKey(StockMovementType.TRANSFER_IN, StockMovementReferenceType.WAREHOUSE_TRANSFER_LINE, 77L)
        assertEquals(out, outAgain)
        assertNotEquals(out, incoming)
    }

    @Test
    fun direction_is_part_of_the_canonical_contract() {
        val key = StockMovementPolicy.sourceKey(StockMovementType.PURCHASE, StockMovementReferenceType.PURCHASE_LINE, 9L)
        StockMovementPolicy.validate(StockMovementType.PURCHASE, 5.0, 2.0, StockMovementReferenceType.PURCHASE_LINE, 9L, key)
        assertThrows(IllegalArgumentException::class.java) {
            StockMovementPolicy.validate(StockMovementType.PURCHASE, -5.0, 2.0, StockMovementReferenceType.PURCHASE_LINE, 9L, key)
        }
    }

    @Test
    fun correction_events_can_use_explicit_discriminator_without_losing_source_reference() {
        val a = StockMovementPolicy.sourceKey(
            StockMovementType.PRODUCTION_COST_REVALUE_OUT,
            StockMovementReferenceType.PRODUCTION_BATCH,
            12L,
            "1000"
        )
        val b = StockMovementPolicy.sourceKey(
            StockMovementType.PRODUCTION_COST_REVALUE_OUT,
            StockMovementReferenceType.PRODUCTION_BATCH,
            12L,
            "2000"
        )
        assertNotEquals(a, b)
    }

    @Test
    fun unknown_types_mismatched_references_and_invalid_ids_are_rejected() {
        assertThrows(IllegalArgumentException::class.java) {
            StockMovementPolicy.sourceKey(StockMovementType.SALE, StockMovementReferenceType.PURCHASE_LINE, 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StockMovementPolicy.sourceKey(
                StockMovementType.SALE,
                StockMovementReferenceType.SALES_ALLOCATION,
                0L
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            StockMovementPolicy.expectedReferenceType("UNKNOWN")
        }
    }
}
'''
p("app/src/test/java/com/fush/erp/domain/StockMovementPolicyTest.kt").write_text(test)

# --- Service routing to the single writer ---
for rel in [
    "app/src/main/java/com/fush/erp/domain/InventoryService.kt",
    "app/src/main/java/com/fush/erp/domain/PurchaseService.kt",
    "app/src/main/java/com/fush/erp/domain/SalesService.kt",
    "app/src/main/java/com/fush/erp/domain/ProductionService.kt",
    "app/src/main/java/com/fush/erp/domain/AdvancedInventoryService.kt",
]:
    path = p(rel)
    text = path.read_text()
    class_name = path.stem
    class_anchor = f"class {class_name}(private val db: FushDatabase) {{\n"
    if class_anchor not in text:
        raise SystemExit(f"{rel}: class anchor missing")
    text = text.replace(class_anchor, class_anchor + "    private val stockWriter = StockMovementWriter(db)\n", 1)
    text = text.replace("db.stockDao().insertMovement(", "stockWriter.insert(")
    path.write_text(text)

# Purchase return: movement points at persisted return line.
replace("app/src/main/java/com/fush/erp/domain/PurchaseService.kt", "            db.purchaseDao().insertReturnLine(\n", "            val returnLineId = db.purchaseDao().insertReturnLine(\n")
replace("app/src/main/java/com/fush/erp/domain/PurchaseService.kt", '                    referenceType = "PURCHASE_RETURN",\n                    referenceId = returnId,', '                    referenceType = StockMovementReferenceType.PURCHASE_RETURN_LINE,\n                    referenceId = returnLineId,')
replace("app/src/main/java/com/fush/erp/domain/PurchaseService.kt", '                    movementType = "PURCHASE",', '                    movementType = StockMovementType.PURCHASE,')
replace("app/src/main/java/com/fush/erp/domain/PurchaseService.kt", '                    referenceType = "PURCHASE_LINE",', '                    referenceType = StockMovementReferenceType.PURCHASE_LINE,')
replace("app/src/main/java/com/fush/erp/domain/PurchaseService.kt", '                    movementType = "PURCHASE_RETURN",', '                    movementType = StockMovementType.PURCHASE_RETURN,')

# Sales: allocation-level identity.
replace("app/src/main/java/com/fush/erp/domain/SalesService.kt", "            db.salesDao().insertReturnAllocation(\n", "            val returnAllocationId = db.salesDao().insertReturnAllocation(\n")
replace("app/src/main/java/com/fush/erp/domain/SalesService.kt", '                    movementType = "SALES_RETURN",', '                    movementType = StockMovementType.SALES_RETURN,')
replace("app/src/main/java/com/fush/erp/domain/SalesService.kt", '                    referenceType = "SALES_RETURN",\n                    referenceId = returnId,', '                    referenceType = StockMovementReferenceType.SALES_RETURN_ALLOCATION,\n                    referenceId = returnAllocationId,')
replace("app/src/main/java/com/fush/erp/domain/SalesService.kt", "            db.salesDao().insertAllocation(\n", "            val allocationId = db.salesDao().insertAllocation(\n")
replace("app/src/main/java/com/fush/erp/domain/SalesService.kt", '                    movementType = "SALE",', '                    movementType = StockMovementType.SALE,')
replace("app/src/main/java/com/fush/erp/domain/SalesService.kt", '                    referenceType = "SALES_LINE",\n                    referenceId = salesLineId,', '                    referenceType = StockMovementReferenceType.SALES_ALLOCATION,\n                    referenceId = allocationId,')

# Inventory opening.
replace("app/src/main/java/com/fush/erp/domain/InventoryService.kt", '                movementType = "OPENING",', '                movementType = StockMovementType.OPENING,')
replace("app/src/main/java/com/fush/erp/domain/InventoryService.kt", '                referenceType = "JOURNAL_ENTRY",', '                referenceType = StockMovementReferenceType.JOURNAL_ENTRY,')

# Advanced inventory.
replace("app/src/main/java/com/fush/erp/domain/AdvancedInventoryService.kt", 'movementType = "COUNT_ADJUSTMENT",', 'movementType = StockMovementType.COUNT_ADJUSTMENT,')
replace("app/src/main/java/com/fush/erp/domain/AdvancedInventoryService.kt", 'referenceType = "INVENTORY_COUNT",\n                    referenceId = countId,', 'referenceType = StockMovementReferenceType.INVENTORY_COUNT_LINE,\n                    referenceId = line.id,')
replace("app/src/main/java/com/fush/erp/domain/AdvancedInventoryService.kt", 'movementType = "LEGACY_LOT_RECLASS_OUT",', 'movementType = StockMovementType.LEGACY_LOT_RECLASS_OUT,')
replace("app/src/main/java/com/fush/erp/domain/AdvancedInventoryService.kt", 'movementType = "LEGACY_LOT_RECLASS_IN",', 'movementType = StockMovementType.LEGACY_LOT_RECLASS_IN,')
replace("app/src/main/java/com/fush/erp/domain/AdvancedInventoryService.kt", 'referenceType = "LEGACY_LOT_ASSIGNMENT",', 'referenceType = StockMovementReferenceType.AUDIT_EVENT,', expected=2)
for mtype in ["TRANSFER_OUT","TRANSFER_IN","TRANSFER_REVERSAL_OUT","TRANSFER_REVERSAL_IN"]:
    replace("app/src/main/java/com/fush/erp/domain/AdvancedInventoryService.kt", f'                    movementType = "{mtype}",', f'                    movementType = StockMovementType.{mtype},')
replace("app/src/main/java/com/fush/erp/domain/AdvancedInventoryService.kt", '                    referenceType = "WAREHOUSE_TRANSFER",\n                    referenceId = transferId,', '                    referenceType = StockMovementReferenceType.WAREHOUSE_TRANSFER_LINE,\n                    referenceId = line.id,', expected=2)
replace("app/src/main/java/com/fush/erp/domain/AdvancedInventoryService.kt", '                    referenceType = "WAREHOUSE_TRANSFER_REVERSAL",\n                    referenceId = transferId,', '                    referenceType = StockMovementReferenceType.WAREHOUSE_TRANSFER_LINE,\n                    referenceId = line.id,', expected=2)

# Production.
prod = "app/src/main/java/com/fush/erp/domain/ProductionService.kt"
for mtype in ["PRODUCTION_ISSUE", "PRODUCTION_ISSUE_RETURN", "PRODUCTION_ISSUE_CORRECTION", "PRODUCTION_RECEIPT", "PRODUCTION_RECEIPT_CORRECTION", "PRODUCTION_COST_REVALUE_OUT", "PRODUCTION_COST_REVALUE_IN"]:
    path = p(prod); text = path.read_text(); old = f'movementType = "{mtype}",'
    if old not in text: raise SystemExit(f"{prod}: missing movement type {mtype}")
    path.write_text(text.replace(old, f'movementType = StockMovementType.{mtype},'))
replace(prod, 'referenceType = "PRODUCTION_ISSUE",', 'referenceType = StockMovementReferenceType.PRODUCTION_ISSUE,')
replace(prod, 'referenceType = "PRODUCTION_ISSUE_CORRECTION",', 'referenceType = StockMovementReferenceType.PRODUCTION_ISSUE,', expected=2)
replace(prod, 'referenceType = "PRODUCTION_BATCH",', 'referenceType = StockMovementReferenceType.PRODUCTION_BATCH,')
replace(prod, 'referenceType = "PRODUCTION_COST_CORRECTION",\n                            referenceId = order.id,', 'referenceType = StockMovementReferenceType.PRODUCTION_BATCH,\n                            referenceId = batch.id,', expected=2)
replace(prod, 'referenceType = "PRODUCTION_OUTPUT_CORRECTION",\n                        referenceId = batch.id,', 'referenceType = StockMovementReferenceType.PRODUCTION_BATCH,\n                        referenceId = batch.id,')
replace(prod, 'referenceType = "PRODUCTION_OUTPUT_CORRECTION",\n                            referenceId = batch.id,', 'referenceType = StockMovementReferenceType.PRODUCTION_BATCH,\n                            referenceId = batch.id,', expected=2)

# Add a discriminator to repeatable production correction/revaluation movements.
path = p(prod); text = path.read_text()
# Match each stockWriter call conservatively by balancing on the common 'StockMovementEntity(...))' layout.
lines = text.splitlines()
out = []
i = 0
injected = 0
while i < len(lines):
    if lines[i].strip() == "stockWriter.insert(":
        start = i
        j = i + 1
        depth = 1
        movement = None
        while j < len(lines):
            s = lines[j]
            if "movementType = StockMovementType." in s:
                movement = s.split("movementType = StockMovementType.",1)[1].split(",",1)[0].strip()
            depth += s.count("(") - s.count(")")
            if depth == 0:
                break
            j += 1
        block = lines[start:j+1]
        if movement in {"PRODUCTION_RECEIPT_CORRECTION","PRODUCTION_COST_REVALUE_OUT","PRODUCTION_COST_REVALUE_IN"}:
            # The final ')' closes writer.insert; insert named arg before it, after the entity arg.
            indent = block[-1][:-len(block[-1].lstrip())]
            if len(block) < 2 or block[-2].strip() != ")":
                raise SystemExit(f"Unexpected writer block shape for {movement}")
            block[-2] = block[-2] + ","
            block.insert(-1, indent + "    sourceDiscriminator = now.toString()")
            injected += 1
        out.extend(block)
        i = j + 1
    else:
        out.append(lines[i]); i += 1
if injected < 5:
    raise SystemExit(f"{prod}: expected >=5 correction discriminator injections, got {injected}")
path.write_text("\n".join(out) + "\n")

# All application writes must route through StockMovementWriter.
direct = []
for file in p("app/src/main/java").rglob("*.kt"):
    txt = file.read_text()
    if "insertMovementUnchecked(" in txt and file.name not in {"PurchaseDaos.kt", "StockMovementWriter.kt"}:
        direct.append(str(file.relative_to(ROOT)))
    if ".insertMovement(" in txt:
        direct.append(str(file.relative_to(ROOT)))
if direct:
    raise SystemExit("Unsafe stock movement writes remain: " + ", ".join(sorted(set(direct))))

print(f"Inventory P1 transformations applied; repeatable correction keys={injected}")
