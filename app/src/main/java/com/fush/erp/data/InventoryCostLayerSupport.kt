package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_37_38_INVENTORY_COST_LAYERS = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `inventory_cost_layers` (
                `stockMovementId` INTEGER NOT NULL, `movementDate` INTEGER NOT NULL, `warehouseId` INTEGER NOT NULL,
                `itemId` INTEGER NOT NULL, `movementType` TEXT NOT NULL, `quantityBase` REAL NOT NULL,
                `unitCostBase` REAL NOT NULL, `signedValueBase` REAL NOT NULL, `costingMethodVersion` TEXT NOT NULL,
                `lotNo` TEXT NOT NULL, `sourceReferenceType` TEXT NOT NULL, `sourceReferenceId` INTEGER NOT NULL,
                `glSourceType` TEXT NOT NULL, `glSourceId` TEXT NOT NULL, `traceClass` TEXT NOT NULL,
                PRIMARY KEY(`stockMovementId`),
                FOREIGN KEY(`stockMovementId`) REFERENCES `stock_movements`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_cost_layers_itemId` ON `inventory_cost_layers` (`itemId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_cost_layers_warehouseId` ON `inventory_cost_layers` (`warehouseId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_cost_layers_glSourceType_glSourceId` ON `inventory_cost_layers` (`glSourceType`, `glSourceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_cost_layers_sourceReferenceType_sourceReferenceId` ON `inventory_cost_layers` (`sourceReferenceType`, `sourceReferenceId`)")
        backfillInventoryCostLayers(db)
        installInventoryCostLayerSupport(db)
    }
}

fun installInventoryCostLayerSupport(db: SupportSQLiteDatabase) {
    db.execSQL("""
        CREATE TRIGGER IF NOT EXISTS trg_inventory_cost_layer_autocreate
        AFTER INSERT ON stock_movements
        BEGIN
            INSERT OR IGNORE INTO inventory_cost_layers(
                stockMovementId,movementDate,warehouseId,itemId,movementType,quantityBase,unitCostBase,signedValueBase,
                costingMethodVersion,lotNo,sourceReferenceType,sourceReferenceId,glSourceType,glSourceId,traceClass
            )
            VALUES(
                NEW.id,NEW.movementDate,NEW.warehouseId,NEW.itemId,NEW.movementType,NEW.quantityBase,NEW.unitCostBase,
                NEW.quantityBase * NEW.unitCostBase,'MOVEMENT_ACTUAL_V1',COALESCE(NEW.lotNo,''),NEW.referenceType,NEW.referenceId,
                ${glSourceTypeSql("NEW")},${glSourceIdSql("NEW")},${traceClassSql("NEW")}
            );
        END
    """.trimIndent())
    db.execSQL("""
        CREATE TRIGGER IF NOT EXISTS trg_inventory_cost_layer_no_update
        BEFORE UPDATE ON inventory_cost_layers
        BEGIN SELECT RAISE(ABORT, 'INVENTORY_COST_LAYER_IMMUTABLE'); END
    """.trimIndent())
    db.execSQL("""
        CREATE TRIGGER IF NOT EXISTS trg_inventory_cost_layer_no_delete
        BEFORE DELETE ON inventory_cost_layers
        BEGIN SELECT RAISE(ABORT, 'INVENTORY_COST_LAYER_IMMUTABLE'); END
    """.trimIndent())
    createInventoryCostGlTraceView(db)
}

fun backfillInventoryCostLayers(db: SupportSQLiteDatabase) {
    db.execSQL("""
        INSERT OR IGNORE INTO inventory_cost_layers(
            stockMovementId,movementDate,warehouseId,itemId,movementType,quantityBase,unitCostBase,signedValueBase,
            costingMethodVersion,lotNo,sourceReferenceType,sourceReferenceId,glSourceType,glSourceId,traceClass
        )
        SELECT sm.id,sm.movementDate,sm.warehouseId,sm.itemId,sm.movementType,sm.quantityBase,sm.unitCostBase,
               sm.quantityBase * sm.unitCostBase,'MOVEMENT_ACTUAL_V1',COALESCE(sm.lotNo,''),sm.referenceType,sm.referenceId,
               ${glSourceTypeSql("sm")},${glSourceIdSql("sm")},${traceClassSql("sm")}
        FROM stock_movements sm
    """.trimIndent())
}

private fun glSourceTypeSql(alias: String): String = """CASE
    WHEN $alias.movementType='PURCHASE' THEN 'PURCHASE'
    WHEN $alias.movementType='PURCHASE_RETURN' THEN 'PURCHASE_RETURN'
    WHEN $alias.movementType='SALE' THEN 'SALE'
    WHEN $alias.movementType='SALES_RETURN' THEN 'SALES_RETURN'
    WHEN $alias.movementType='COUNT_ADJUSTMENT' THEN 'INVENTORY_COUNT'
    WHEN $alias.movementType='PRODUCTION_RECEIPT' THEN 'PRODUCTION_RECEIPT'
    WHEN $alias.movementType='PRODUCTION_ISSUE' THEN 'PRODUCTION_ISSUE'
    WHEN $alias.movementType='OPENING' THEN 'OPENING_STOCK'
    ELSE '' END"""

private fun glSourceIdSql(alias: String): String = """CASE
    WHEN $alias.movementType='PURCHASE' THEN COALESCE((SELECT CAST(pl.invoiceId AS TEXT) FROM purchase_lines pl WHERE pl.id=$alias.referenceId),'')
    WHEN $alias.movementType='PURCHASE_RETURN' THEN CAST($alias.referenceId AS TEXT)
    WHEN $alias.movementType='SALE' THEN COALESCE((SELECT CAST(sl.invoiceId AS TEXT) FROM sales_lines sl WHERE sl.id=$alias.referenceId),'')
    WHEN $alias.movementType='SALES_RETURN' THEN CAST($alias.referenceId AS TEXT)
    WHEN $alias.movementType='COUNT_ADJUSTMENT' THEN CAST($alias.referenceId AS TEXT)
    WHEN $alias.movementType='PRODUCTION_RECEIPT' THEN CAST($alias.id AS TEXT)
    WHEN $alias.movementType='PRODUCTION_ISSUE' THEN COALESCE((SELECT po.orderNo FROM production_issues pi JOIN production_orders po ON po.id=pi.orderId WHERE pi.id=$alias.referenceId),'')
    WHEN $alias.movementType='OPENING' THEN COALESCE((SELECT je.sourceId FROM journal_entries je WHERE je.id=$alias.referenceId),'')
    ELSE '' END"""

private fun traceClassSql(alias: String): String = """CASE
    WHEN $alias.movementType IN ('PURCHASE','PURCHASE_RETURN','SALE','SALES_RETURN','COUNT_ADJUSTMENT','PRODUCTION_RECEIPT','PRODUCTION_ISSUE','OPENING') THEN 'GL_MAPPED'
    WHEN $alias.movementType LIKE 'TRANSFER%' OR $alias.movementType LIKE 'LEGACY_LOT_RECLASS%' THEN 'NON_GL_INTERNAL'
    ELSE 'OWNER_MAPPING_REQUIRED' END"""

private fun createInventoryCostGlTraceView(db: SupportSQLiteDatabase) {
    db.execSQL("DROP VIEW IF EXISTS inventory_cost_gl_trace")
    db.execSQL("""
        CREATE VIEW inventory_cost_gl_trace AS
        WITH layers AS (
            SELECT glSourceType, glSourceId, SUM(signedValueBase) AS layerValueBase
            FROM inventory_cost_layers
            WHERE traceClass='GL_MAPPED' AND glSourceType<>'' AND glSourceId<>''
            GROUP BY glSourceType, glSourceId
        ), gl AS (
            SELECT je.sourceType AS glSourceType, je.sourceId AS glSourceId,
                   SUM(CASE WHEN a.code='1200' THEN jl.debit - jl.credit ELSE 0 END) AS glInventoryDeltaBase
            FROM journal_entries je
            JOIN journal_lines jl ON jl.entryId=je.id
            JOIN accounts a ON a.id=jl.accountId
            WHERE je.status='POSTED'
            GROUP BY je.sourceType, je.sourceId
        )
        SELECT layers.glSourceType, layers.glSourceId, layers.layerValueBase,
               COALESCE(gl.glInventoryDeltaBase,0) AS glInventoryDeltaBase,
               layers.layerValueBase - COALESCE(gl.glInventoryDeltaBase,0) AS varianceBase
        FROM layers LEFT JOIN gl
          ON gl.glSourceType=layers.glSourceType AND gl.glSourceId=layers.glSourceId
    """.trimIndent())
}
