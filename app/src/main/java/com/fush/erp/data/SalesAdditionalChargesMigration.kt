package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v179: configurable sales additional charges + payments + many-to-many invoice settlement. */
val MIGRATION_46_47_SALES_ADDITIONAL_CHARGES = object : Migration(46, 47) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sales_additional_charge_types` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `code` TEXT NOT NULL,
                `nameAr` TEXT NOT NULL,
                `nameEn` TEXT NOT NULL,
                `defaultBearer` TEXT NOT NULL,
                `defaultAccountingTreatment` TEXT NOT NULL,
                `principalAgentMode` TEXT NOT NULL,
                `recoverableAccountId` INTEGER NOT NULL,
                `expenseAccountId` INTEGER NOT NULL,
                `revenueAccountId` INTEGER NOT NULL,
                `payableAccountId` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`recoverableAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`expenseAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`revenueAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`payableAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_additional_charge_types_code` ON `sales_additional_charge_types` (`code`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_additional_charge_types_isActive` ON `sales_additional_charge_types` (`isActive`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_additional_charge_types_recoverableAccountId` ON `sales_additional_charge_types` (`recoverableAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_additional_charge_types_expenseAccountId` ON `sales_additional_charge_types` (`expenseAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_additional_charge_types_revenueAccountId` ON `sales_additional_charge_types` (`revenueAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_additional_charge_types_payableAccountId` ON `sales_additional_charge_types` (`payableAccountId`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sales_additional_charges` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `chargeNo` TEXT NOT NULL,
                `customerId` INTEGER NOT NULL,
                `chargeTypeId` INTEGER NOT NULL,
                `chargeDate` INTEGER NOT NULL,
                `description` TEXT NOT NULL,
                `amountOriginal` REAL NOT NULL,
                `currencyCode` TEXT NOT NULL,
                `exchangeRate` REAL NOT NULL,
                `amountBase` REAL NOT NULL,
                `bearer` TEXT NOT NULL,
                `paymentStatus` TEXT NOT NULL,
                `paidBy` TEXT NOT NULL,
                `accountingTreatment` TEXT NOT NULL,
                `principalAgentModeSnapshot` TEXT NOT NULL,
                `recoverableAccountId` INTEGER NOT NULL,
                `expenseAccountId` INTEGER NOT NULL,
                `revenueAccountId` INTEGER NOT NULL,
                `payableAccountId` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `createdBy` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `cancelledBy` INTEGER,
                `cancelledAt` INTEGER,
                `cancellationReason` TEXT NOT NULL,
                FOREIGN KEY(`customerId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`chargeTypeId`) REFERENCES `sales_additional_charge_types`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`currencyCode`) REFERENCES `currencies`(`code`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`recoverableAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`expenseAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`revenueAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`payableAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        listOf("customerId","chargeTypeId","chargeDate","status","currencyCode","recoverableAccountId","expenseAccountId","revenueAccountId","payableAccountId").forEach {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_additional_charges_${it}` ON `sales_additional_charges` (`${it}`)")
        }
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_additional_charges_chargeNo` ON `sales_additional_charges` (`chargeNo`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sales_additional_charge_payments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `paymentNo` TEXT NOT NULL,
                `chargeId` INTEGER NOT NULL,
                `paymentDate` INTEGER NOT NULL,
                `amountOriginal` REAL NOT NULL,
                `currencyCode` TEXT NOT NULL,
                `exchangeRate` REAL NOT NULL,
                `amountBase` REAL NOT NULL,
                `paidBy` TEXT NOT NULL,
                `treasuryAccountId` INTEGER,
                `paymentReference` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `reversalOfPaymentId` INTEGER,
                `createdBy` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`chargeId`) REFERENCES `sales_additional_charges`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`treasuryAccountId`) REFERENCES `treasury_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`reversalOfPaymentId`) REFERENCES `sales_additional_charge_payments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_additional_charge_payments_paymentNo` ON `sales_additional_charge_payments` (`paymentNo`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_additional_charge_payments_chargeId` ON `sales_additional_charge_payments` (`chargeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_additional_charge_payments_paymentDate` ON `sales_additional_charge_payments` (`paymentDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_additional_charge_payments_treasuryAccountId` ON `sales_additional_charge_payments` (`treasuryAccountId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_additional_charge_payments_reversalOfPaymentId` ON `sales_additional_charge_payments` (`reversalOfPaymentId`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `sales_additional_charge_settlements` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `chargeId` INTEGER NOT NULL,
                `invoiceId` INTEGER NOT NULL,
                `amountChargeOriginal` REAL NOT NULL,
                `amountInvoiceOriginal` REAL NOT NULL,
                `amountBase` REAL NOT NULL,
                `settlementDate` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `createdBy` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `reversedBy` INTEGER,
                `reversedAt` INTEGER,
                `reversalReason` TEXT NOT NULL,
                FOREIGN KEY(`chargeId`) REFERENCES `sales_additional_charges`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`invoiceId`) REFERENCES `sales_invoices`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_additional_charge_settlements_chargeId` ON `sales_additional_charge_settlements` (`chargeId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_additional_charge_settlements_invoiceId` ON `sales_additional_charge_settlements` (`invoiceId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_additional_charge_settlements_chargeId_invoiceId` ON `sales_additional_charge_settlements` (`chargeId`,`invoiceId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_additional_charge_settlements_status` ON `sales_additional_charge_settlements` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_additional_charge_settlements_settlementDate` ON `sales_additional_charge_settlements` (`settlementDate`)")
    }
}
