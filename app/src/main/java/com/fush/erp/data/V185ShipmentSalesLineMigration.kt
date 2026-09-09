package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v185: preserves all v179-v184 shipment allocations and adds line-level sales trace. */
val MIGRATION_48_49_SHIPMENT_SALES_LINE_LINK = object : Migration(48, 49) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sales_shipment_invoice_item_allocations` ADD COLUMN `salesLineId` INTEGER")
        db.execSQL("ALTER TABLE `sales_shipment_expenses` ADD COLUMN `bearer` TEXT NOT NULL DEFAULT 'COMPANY'")
        db.execSQL("ALTER TABLE `sales_shipment_expense_invoice_allocations` ADD COLUMN `customerChargeBase` REAL NOT NULL DEFAULT 0.0")
        db.execSQL("DROP INDEX IF EXISTS `index_sales_shipment_invoice_item_allocations_shipmentItemId_invoiceId`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipment_invoice_item_allocations_salesLineId` ON `sales_shipment_invoice_item_allocations` (`salesLineId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_shipment_invoice_item_allocations_shipmentItemId_salesLineId` ON `sales_shipment_invoice_item_allocations` (`shipmentItemId`,`salesLineId`)")
    }
}
