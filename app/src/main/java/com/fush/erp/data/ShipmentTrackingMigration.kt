package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v179 shipment tracking: shipment header/items, actual expenses and analytical invoice allocations. */
val MIGRATION_47_48_SHIPMENT_COST_ALLOCATION = object : Migration(47, 48) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sales_shipments` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `shipmentNo` TEXT NOT NULL,
              `shipmentDate` INTEGER NOT NULL,
              `fromWarehouseId` INTEGER NOT NULL,
              `destinationProvince` TEXT NOT NULL,
              `status` TEXT NOT NULL,
              `transportReference` TEXT NOT NULL,
              `notes` TEXT NOT NULL,
              `createdBy` INTEGER NOT NULL,
              `createdAt` INTEGER NOT NULL,
              `closedAt` INTEGER,
              `cancelledAt` INTEGER,
              `cancellationReason` TEXT NOT NULL,
              FOREIGN KEY(`fromWarehouseId`) REFERENCES `warehouses`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_shipments_shipmentNo` ON `sales_shipments` (`shipmentNo`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipments_shipmentDate` ON `sales_shipments` (`shipmentDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipments_fromWarehouseId` ON `sales_shipments` (`fromWarehouseId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipments_destinationProvince` ON `sales_shipments` (`destinationProvince`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipments_status` ON `sales_shipments` (`status`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sales_shipment_items` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `shipmentId` INTEGER NOT NULL,
              `itemId` INTEGER NOT NULL,
              `lotNo` TEXT NOT NULL,
              `quantityBase` REAL NOT NULL,
              `createdBy` INTEGER NOT NULL,
              `createdAt` INTEGER NOT NULL,
              FOREIGN KEY(`shipmentId`) REFERENCES `sales_shipments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
              FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipment_items_shipmentId` ON `sales_shipment_items` (`shipmentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipment_items_itemId` ON `sales_shipment_items` (`itemId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipment_items_lotNo` ON `sales_shipment_items` (`lotNo`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_shipment_items_shipmentId_itemId_lotNo` ON `sales_shipment_items` (`shipmentId`,`itemId`,`lotNo`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sales_shipment_expenses` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `shipmentId` INTEGER NOT NULL,
              `expenseType` TEXT NOT NULL,
              `description` TEXT NOT NULL,
              `expenseDate` INTEGER NOT NULL,
              `amountOriginal` REAL NOT NULL,
              `currencyCode` TEXT NOT NULL,
              `exchangeRate` REAL NOT NULL,
              `amountBase` REAL NOT NULL,
              `paymentMethod` TEXT NOT NULL,
              `partyVoucherId` INTEGER NOT NULL,
              `paymentVoucherNo` TEXT NOT NULL,
              `paymentReference` TEXT NOT NULL,
              `status` TEXT NOT NULL,
              `createdBy` INTEGER NOT NULL,
              `createdAt` INTEGER NOT NULL,
              FOREIGN KEY(`shipmentId`) REFERENCES `sales_shipments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
              FOREIGN KEY(`currencyCode`) REFERENCES `currencies`(`code`) ON UPDATE NO ACTION ON DELETE RESTRICT,
              FOREIGN KEY(`partyVoucherId`) REFERENCES `party_vouchers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipment_expenses_shipmentId` ON `sales_shipment_expenses` (`shipmentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipment_expenses_expenseType` ON `sales_shipment_expenses` (`expenseType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipment_expenses_expenseDate` ON `sales_shipment_expenses` (`expenseDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipment_expenses_currencyCode` ON `sales_shipment_expenses` (`currencyCode`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_shipment_expenses_partyVoucherId` ON `sales_shipment_expenses` (`partyVoucherId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipment_expenses_status` ON `sales_shipment_expenses` (`status`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sales_shipment_invoice_item_allocations` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `shipmentItemId` INTEGER NOT NULL,
              `invoiceId` INTEGER NOT NULL,
              `quantityBase` REAL NOT NULL,
              `status` TEXT NOT NULL,
              `createdBy` INTEGER NOT NULL,
              `createdAt` INTEGER NOT NULL,
              `reversedAt` INTEGER,
              `reversalReason` TEXT NOT NULL,
              FOREIGN KEY(`shipmentItemId`) REFERENCES `sales_shipment_items`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
              FOREIGN KEY(`invoiceId`) REFERENCES `sales_invoices`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipment_invoice_item_allocations_shipmentItemId` ON `sales_shipment_invoice_item_allocations` (`shipmentItemId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipment_invoice_item_allocations_invoiceId` ON `sales_shipment_invoice_item_allocations` (`invoiceId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_shipment_invoice_item_allocations_shipmentItemId_invoiceId` ON `sales_shipment_invoice_item_allocations` (`shipmentItemId`,`invoiceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipment_invoice_item_allocations_status` ON `sales_shipment_invoice_item_allocations` (`status`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sales_shipment_expense_invoice_allocations` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `shipmentExpenseId` INTEGER NOT NULL,
              `invoiceId` INTEGER NOT NULL,
              `amountBase` REAL NOT NULL,
              `allocationMethod` TEXT NOT NULL,
              `basisQuantityBase` REAL NOT NULL,
              `status` TEXT NOT NULL,
              `createdBy` INTEGER NOT NULL,
              `createdAt` INTEGER NOT NULL,
              `reversedAt` INTEGER,
              `reversalReason` TEXT NOT NULL,
              FOREIGN KEY(`shipmentExpenseId`) REFERENCES `sales_shipment_expenses`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
              FOREIGN KEY(`invoiceId`) REFERENCES `sales_invoices`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipment_expense_invoice_allocations_shipmentExpenseId` ON `sales_shipment_expense_invoice_allocations` (`shipmentExpenseId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipment_expense_invoice_allocations_invoiceId` ON `sales_shipment_expense_invoice_allocations` (`invoiceId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_shipment_expense_invoice_allocations_shipmentExpenseId_invoiceId` ON `sales_shipment_expense_invoice_allocations` (`shipmentExpenseId`,`invoiceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shipment_expense_invoice_allocations_status` ON `sales_shipment_expense_invoice_allocations` (`status`)")
    }
}
