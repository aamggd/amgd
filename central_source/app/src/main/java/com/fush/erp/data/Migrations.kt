package com.fush.erp.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS suppliers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                code TEXT NOT NULL,
                nameAr TEXT NOT NULL,
                nameEn TEXT NOT NULL,
                phone TEXT NOT NULL,
                address TEXT NOT NULL,
                currencyCode TEXT NOT NULL,
                paymentTermsDays INTEGER NOT NULL,
                isActive INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(currencyCode) REFERENCES currencies(code) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_suppliers_code ON suppliers(code)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_suppliers_currencyCode ON suppliers(currencyCode)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS item_unit_conversions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                itemId INTEGER NOT NULL,
                unitId INTEGER NOT NULL,
                factorToBase REAL NOT NULL,
                allowPurchase INTEGER NOT NULL,
                allowSale INTEGER NOT NULL,
                barcode TEXT,
                isActive INTEGER NOT NULL,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(unitId) REFERENCES units(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_item_unit_conversions_itemId_unitId ON item_unit_conversions(itemId, unitId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_item_unit_conversions_itemId ON item_unit_conversions(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_item_unit_conversions_unitId ON item_unit_conversions(unitId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS purchase_invoices (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                invoiceNo TEXT NOT NULL,
                supplierInvoiceNo TEXT NOT NULL,
                supplierId INTEGER NOT NULL,
                invoiceDate INTEGER NOT NULL,
                warehouseId INTEGER NOT NULL,
                currencyCode TEXT NOT NULL,
                exchangeRate REAL NOT NULL,
                paymentType TEXT NOT NULL,
                subtotalOriginal REAL NOT NULL,
                totalOriginal REAL NOT NULL,
                totalBase REAL NOT NULL,
                status TEXT NOT NULL,
                notes TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(supplierId) REFERENCES suppliers(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(warehouseId) REFERENCES warehouses(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_invoices_invoiceNo ON purchase_invoices(invoiceNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_invoices_supplierId ON purchase_invoices(supplierId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_invoices_warehouseId ON purchase_invoices(warehouseId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS purchase_lines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                invoiceId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                unitId INTEGER NOT NULL,
                quantity REAL NOT NULL,
                factorToBase REAL NOT NULL,
                baseQuantity REAL NOT NULL,
                unitPriceOriginal REAL NOT NULL,
                lineTotalOriginal REAL NOT NULL,
                unitCostBase REAL NOT NULL,
                lotNo TEXT,
                expiryDate INTEGER,
                FOREIGN KEY(invoiceId) REFERENCES purchase_invoices(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(unitId) REFERENCES units(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_lines_invoiceId ON purchase_lines(invoiceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_lines_itemId ON purchase_lines(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_lines_unitId ON purchase_lines(unitId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS purchase_returns (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                returnNo TEXT NOT NULL,
                purchaseInvoiceId INTEGER NOT NULL,
                supplierId INTEGER NOT NULL,
                returnDate INTEGER NOT NULL,
                warehouseId INTEGER NOT NULL,
                currencyCode TEXT NOT NULL,
                exchangeRate REAL NOT NULL,
                settlementType TEXT NOT NULL,
                totalOriginal REAL NOT NULL,
                totalBase REAL NOT NULL,
                status TEXT NOT NULL,
                reason TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(purchaseInvoiceId) REFERENCES purchase_invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(supplierId) REFERENCES suppliers(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(warehouseId) REFERENCES warehouses(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_returns_returnNo ON purchase_returns(returnNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_returns_purchaseInvoiceId ON purchase_returns(purchaseInvoiceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_returns_supplierId ON purchase_returns(supplierId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS purchase_return_lines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                returnId INTEGER NOT NULL,
                purchaseLineId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                unitId INTEGER NOT NULL,
                quantity REAL NOT NULL,
                factorToBase REAL NOT NULL,
                baseQuantity REAL NOT NULL,
                unitPriceOriginal REAL NOT NULL,
                lineTotalOriginal REAL NOT NULL,
                unitCostBase REAL NOT NULL,
                FOREIGN KEY(returnId) REFERENCES purchase_returns(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(purchaseLineId) REFERENCES purchase_lines(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(unitId) REFERENCES units(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_return_lines_returnId ON purchase_return_lines(returnId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_return_lines_purchaseLineId ON purchase_return_lines(purchaseLineId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_return_lines_itemId ON purchase_return_lines(itemId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS stock_movements (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                movementDate INTEGER NOT NULL,
                warehouseId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                movementType TEXT NOT NULL,
                quantityBase REAL NOT NULL,
                unitCostBase REAL NOT NULL,
                referenceType TEXT NOT NULL,
                referenceId INTEGER NOT NULL,
                lotNo TEXT,
                expiryDate INTEGER,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(warehouseId) REFERENCES warehouses(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_warehouseId ON stock_movements(warehouseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_itemId ON stock_movements(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_referenceType_referenceId ON stock_movements(referenceType, referenceId)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS recipes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                code TEXT NOT NULL,
                productItemId INTEGER NOT NULL,
                versionNo INTEGER NOT NULL,
                effectiveFrom INTEGER NOT NULL,
                targetOutputQtyBase REAL NOT NULL,
                status TEXT NOT NULL,
                notes TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(productItemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recipes_code_versionNo ON recipes(code, versionNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recipes_productItemId ON recipes(productItemId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS recipe_components (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                recipeId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                quantityBase REAL NOT NULL,
                expectedLossPct REAL NOT NULL,
                stage TEXT NOT NULL,
                sequenceNo INTEGER NOT NULL,
                FOREIGN KEY(recipeId) REFERENCES recipes(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_components_recipeId ON recipe_components(recipeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_components_itemId ON recipe_components(itemId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS production_orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                orderNo TEXT NOT NULL,
                recipeId INTEGER NOT NULL,
                productItemId INTEGER NOT NULL,
                plannedOutputQtyBase REAL NOT NULL,
                rawWarehouseId INTEGER NOT NULL,
                finishedWarehouseId INTEGER NOT NULL,
                plannedDate INTEGER NOT NULL,
                status TEXT NOT NULL,
                directLaborCostBase REAL NOT NULL,
                notes TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                closedAt INTEGER,
                FOREIGN KEY(recipeId) REFERENCES recipes(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(productItemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(rawWarehouseId) REFERENCES warehouses(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(finishedWarehouseId) REFERENCES warehouses(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_production_orders_orderNo ON production_orders(orderNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_production_orders_recipeId ON production_orders(recipeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_production_orders_productItemId ON production_orders(productItemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_production_orders_rawWarehouseId ON production_orders(rawWarehouseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_production_orders_finishedWarehouseId ON production_orders(finishedWarehouseId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS production_materials (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                orderId INTEGER NOT NULL,
                recipeComponentId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                standardQtyBase REAL NOT NULL,
                reservedQtyBase REAL NOT NULL,
                issuedQtyBase REAL NOT NULL,
                issueCostBase REAL NOT NULL,
                FOREIGN KEY(orderId) REFERENCES production_orders(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_production_materials_orderId ON production_materials(orderId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_production_materials_itemId ON production_materials(itemId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS production_batches (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                batchNo TEXT NOT NULL,
                orderId INTEGER NOT NULL,
                manufactureDate INTEGER NOT NULL,
                expiryDate INTEGER NOT NULL,
                status TEXT NOT NULL,
                actualOutputQtyBase REAL NOT NULL,
                acceptedQtyBase REAL NOT NULL,
                rejectedQtyBase REAL NOT NULL,
                scrapQtyBase REAL NOT NULL,
                notes TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(orderId) REFERENCES production_orders(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_production_batches_batchNo ON production_batches(batchNo)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_production_batches_orderId ON production_batches(orderId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS production_issues (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                orderId INTEGER NOT NULL,
                materialId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                quantityBase REAL NOT NULL,
                unitCostBase REAL NOT NULL,
                totalCostBase REAL NOT NULL,
                lotNo TEXT,
                expiryDate INTEGER,
                issueDate INTEGER NOT NULL,
                FOREIGN KEY(orderId) REFERENCES production_orders(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(materialId) REFERENCES production_materials(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_production_issues_orderId ON production_issues(orderId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_production_issues_materialId ON production_issues(materialId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_production_issues_itemId ON production_issues(itemId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS quality_checks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                batchId INTEGER NOT NULL,
                stage TEXT NOT NULL,
                checkName TEXT NOT NULL,
                resultValue TEXT NOT NULL,
                decision TEXT NOT NULL,
                notes TEXT NOT NULL,
                checkedBy INTEGER NOT NULL,
                checkedAt INTEGER NOT NULL,
                FOREIGN KEY(batchId) REFERENCES production_batches(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quality_checks_batchId ON quality_checks(batchId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS non_conformances (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                batchId INTEGER NOT NULL,
                code TEXT NOT NULL,
                description TEXT NOT NULL,
                immediateAction TEXT NOT NULL,
                rootCause TEXT NOT NULL,
                correctiveAction TEXT NOT NULL,
                preventiveAction TEXT NOT NULL,
                responsible TEXT NOT NULL,
                dueDate INTEGER,
                status TEXT NOT NULL,
                effectivenessVerified INTEGER NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                closedAt INTEGER,
                FOREIGN KEY(batchId) REFERENCES production_batches(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_non_conformances_batchId ON non_conformances(batchId)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS customers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                code TEXT NOT NULL,
                nameAr TEXT NOT NULL,
                nameEn TEXT NOT NULL,
                phone TEXT NOT NULL,
                address TEXT NOT NULL,
                province TEXT NOT NULL,
                channel TEXT NOT NULL,
                classification TEXT NOT NULL,
                currencyCode TEXT NOT NULL,
                creditLimitBase REAL NOT NULL,
                creditDays INTEGER NOT NULL,
                allowCredit INTEGER NOT NULL,
                salesRepName TEXT NOT NULL,
                isActive INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(currencyCode) REFERENCES currencies(code) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_customers_code ON customers(code)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_currencyCode ON customers(currencyCode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_province ON customers(province)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_classification ON customers(classification)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sales_prices (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                itemId INTEGER NOT NULL,
                channel TEXT NOT NULL,
                province TEXT NOT NULL,
                currencyCode TEXT NOT NULL,
                baseUnitPriceOriginal REAL NOT NULL,
                effectiveFrom INTEGER NOT NULL,
                isActive INTEGER NOT NULL,
                note TEXT NOT NULL,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(currencyCode) REFERENCES currencies(code) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_prices_itemId ON sales_prices(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_prices_currencyCode ON sales_prices(currencyCode)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sales_prices_itemId_channel_province_currencyCode_effectiveFrom ON sales_prices(itemId, channel, province, currencyCode, effectiveFrom)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sales_invoices (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                invoiceNo TEXT NOT NULL,
                customerId INTEGER NOT NULL,
                invoiceDate INTEGER NOT NULL,
                dueDate INTEGER,
                warehouseId INTEGER NOT NULL,
                currencyCode TEXT NOT NULL,
                exchangeRate REAL NOT NULL,
                paymentType TEXT NOT NULL,
                channel TEXT NOT NULL,
                province TEXT NOT NULL,
                discountPct REAL NOT NULL,
                grossOriginal REAL NOT NULL,
                discountOriginal REAL NOT NULL,
                transportOriginal REAL NOT NULL,
                feesOriginal REAL NOT NULL,
                riskMarginOriginal REAL NOT NULL,
                totalOriginal REAL NOT NULL,
                totalBase REAL NOT NULL,
                status TEXT NOT NULL,
                belowFloorApprovedBy INTEGER,
                belowFloorReason TEXT NOT NULL,
                notes TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(warehouseId) REFERENCES warehouses(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sales_invoices_invoiceNo ON sales_invoices(invoiceNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_invoices_customerId ON sales_invoices(customerId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_invoices_warehouseId ON sales_invoices(warehouseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_invoices_invoiceDate ON sales_invoices(invoiceDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_invoices_dueDate ON sales_invoices(dueDate)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sales_lines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                invoiceId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                unitId INTEGER NOT NULL,
                quantity REAL NOT NULL,
                factorToBase REAL NOT NULL,
                baseQuantity REAL NOT NULL,
                unitPriceOriginal REAL NOT NULL,
                grossOriginal REAL NOT NULL,
                discountOriginal REAL NOT NULL,
                netOriginal REAL NOT NULL,
                FOREIGN KEY(invoiceId) REFERENCES sales_invoices(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(unitId) REFERENCES units(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_lines_invoiceId ON sales_lines(invoiceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_lines_itemId ON sales_lines(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_lines_unitId ON sales_lines(unitId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sales_allocations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                salesLineId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                lotNo TEXT,
                expiryDate INTEGER,
                quantityBase REAL NOT NULL,
                unitCostBase REAL NOT NULL,
                costBase REAL NOT NULL,
                FOREIGN KEY(salesLineId) REFERENCES sales_lines(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_allocations_salesLineId ON sales_allocations(salesLineId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_allocations_itemId ON sales_allocations(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_allocations_lotNo ON sales_allocations(lotNo)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS customer_receipts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                receiptNo TEXT NOT NULL,
                customerId INTEGER NOT NULL,
                receiptDate INTEGER NOT NULL,
                currencyCode TEXT NOT NULL,
                exchangeRate REAL NOT NULL,
                amountOriginal REAL NOT NULL,
                amountBase REAL NOT NULL,
                notes TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_customer_receipts_receiptNo ON customer_receipts(receiptNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_receipts_customerId ON customer_receipts(customerId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_receipts_receiptDate ON customer_receipts(receiptDate)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS customer_receipt_allocations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                receiptId INTEGER NOT NULL,
                invoiceId INTEGER NOT NULL,
                amountBase REAL NOT NULL,
                FOREIGN KEY(receiptId) REFERENCES customer_receipts(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(invoiceId) REFERENCES sales_invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_receipt_allocations_receiptId ON customer_receipt_allocations(receiptId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customer_receipt_allocations_invoiceId ON customer_receipt_allocations(invoiceId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sales_commissions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                invoiceId INTEGER NOT NULL,
                receiptAllocationId INTEGER NOT NULL,
                beneficiary TEXT NOT NULL,
                ratePct REAL NOT NULL,
                earnedBase REAL NOT NULL,
                reversedBase REAL NOT NULL,
                status TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(invoiceId) REFERENCES sales_invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(receiptAllocationId) REFERENCES customer_receipt_allocations(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_commissions_invoiceId ON sales_commissions(invoiceId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sales_commissions_receiptAllocationId ON sales_commissions(receiptAllocationId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sales_returns (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                returnNo TEXT NOT NULL,
                salesInvoiceId INTEGER NOT NULL,
                customerId INTEGER NOT NULL,
                returnDate INTEGER NOT NULL,
                warehouseId INTEGER NOT NULL,
                currencyCode TEXT NOT NULL,
                exchangeRate REAL NOT NULL,
                settlementType TEXT NOT NULL,
                totalOriginal REAL NOT NULL,
                totalBase REAL NOT NULL,
                totalCostBase REAL NOT NULL,
                reason TEXT NOT NULL,
                status TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(salesInvoiceId) REFERENCES sales_invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(warehouseId) REFERENCES warehouses(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sales_returns_returnNo ON sales_returns(returnNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_returns_salesInvoiceId ON sales_returns(salesInvoiceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_returns_customerId ON sales_returns(customerId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sales_return_lines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                returnId INTEGER NOT NULL,
                salesLineId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                unitId INTEGER NOT NULL,
                quantity REAL NOT NULL,
                factorToBase REAL NOT NULL,
                baseQuantity REAL NOT NULL,
                unitPriceOriginal REAL NOT NULL,
                lineNetOriginal REAL NOT NULL,
                costBase REAL NOT NULL,
                FOREIGN KEY(returnId) REFERENCES sales_returns(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(salesLineId) REFERENCES sales_lines(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(unitId) REFERENCES units(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_return_lines_returnId ON sales_return_lines(returnId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_return_lines_salesLineId ON sales_return_lines(salesLineId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_return_lines_itemId ON sales_return_lines(itemId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sales_return_allocations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                returnLineId INTEGER NOT NULL,
                salesAllocationId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                lotNo TEXT,
                expiryDate INTEGER,
                quantityBase REAL NOT NULL,
                unitCostBase REAL NOT NULL,
                costBase REAL NOT NULL,
                FOREIGN KEY(returnLineId) REFERENCES sales_return_lines(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(salesAllocationId) REFERENCES sales_allocations(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_return_allocations_returnLineId ON sales_return_allocations(returnLineId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_return_allocations_salesAllocationId ON sales_return_allocations(salesAllocationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_return_allocations_itemId ON sales_return_allocations(itemId)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE production_orders ADD COLUMN primaryAssetId INTEGER")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS assets (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                code TEXT NOT NULL,
                nameAr TEXT NOT NULL,
                nameEn TEXT NOT NULL,
                assetType TEXT NOT NULL,
                location TEXT NOT NULL,
                serialNo TEXT NOT NULL,
                status TEXT NOT NULL,
                criticality TEXT NOT NULL,
                usageHours REAL NOT NULL,
                usageBatches INTEGER NOT NULL,
                calibrationRequired INTEGER NOT NULL,
                inspectionDueAt INTEGER,
                calibrationDueAt INTEGER,
                notes TEXT NOT NULL,
                isActive INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_assets_code ON assets(code)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_assets_status ON assets(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_assets_assetType ON assets(assetType)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS maintenance_plans (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                assetId INTEGER NOT NULL,
                nameAr TEXT NOT NULL,
                frequencyType TEXT NOT NULL,
                intervalDays INTEGER,
                checklist TEXT NOT NULL,
                lastCompletedAt INTEGER,
                nextDueAt INTEGER,
                isActive INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(assetId) REFERENCES assets(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_maintenance_plans_assetId ON maintenance_plans(assetId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_maintenance_plans_nextDueAt ON maintenance_plans(nextDueAt)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS maintenance_work_orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                workOrderNo TEXT NOT NULL,
                assetId INTEGER NOT NULL,
                planId INTEGER,
                workType TEXT NOT NULL,
                openedAt INTEGER NOT NULL,
                dueAt INTEGER,
                startedAt INTEGER,
                completedAt INTEGER,
                status TEXT NOT NULL,
                problem TEXT NOT NULL,
                actionTaken TEXT NOT NULL,
                downtimeMinutes INTEGER NOT NULL,
                costBase REAL NOT NULL,
                technician TEXT NOT NULL,
                returnToServiceApprovedBy INTEGER,
                returnToServiceAt INTEGER,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(assetId) REFERENCES assets(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(planId) REFERENCES maintenance_plans(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_maintenance_work_orders_workOrderNo ON maintenance_work_orders(workOrderNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_maintenance_work_orders_assetId ON maintenance_work_orders(assetId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_maintenance_work_orders_planId ON maintenance_work_orders(planId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_maintenance_work_orders_status ON maintenance_work_orders(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_maintenance_work_orders_dueAt ON maintenance_work_orders(dueAt)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS breakdowns (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                breakdownNo TEXT NOT NULL,
                assetId INTEGER NOT NULL,
                occurredAt INTEGER NOT NULL,
                severity TEXT NOT NULL,
                description TEXT NOT NULL,
                rootCause TEXT NOT NULL,
                recurring INTEGER NOT NULL,
                downtimeMinutes INTEGER NOT NULL,
                workOrderId INTEGER,
                capaReference TEXT NOT NULL,
                status TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(assetId) REFERENCES assets(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(workOrderId) REFERENCES maintenance_work_orders(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_breakdowns_breakdownNo ON breakdowns(breakdownNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_breakdowns_assetId ON breakdowns(assetId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_breakdowns_occurredAt ON breakdowns(occurredAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_breakdowns_workOrderId ON breakdowns(workOrderId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS asset_inspections (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                inspectionNo TEXT NOT NULL,
                assetId INTEGER NOT NULL,
                inspectionType TEXT NOT NULL,
                inspectionDate INTEGER NOT NULL,
                result TEXT NOT NULL,
                checklistResult TEXT NOT NULL,
                findings TEXT NOT NULL,
                correctiveAction TEXT NOT NULL,
                inspectedBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(assetId) REFERENCES assets(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_asset_inspections_inspectionNo ON asset_inspections(inspectionNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_inspections_assetId ON asset_inspections(assetId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_inspections_inspectionDate ON asset_inspections(inspectionDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_inspections_inspectionType ON asset_inspections(inspectionType)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS calibration_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                calibrationNo TEXT NOT NULL,
                assetId INTEGER NOT NULL,
                checkedAt INTEGER NOT NULL,
                result TEXT NOT NULL,
                referenceStandard TEXT NOT NULL,
                measuredError REAL,
                tolerance REAL,
                dueAt INTEGER,
                certificateRef TEXT NOT NULL,
                notes TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(assetId) REFERENCES assets(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_calibration_records_calibrationNo ON calibration_records(calibrationNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_calibration_records_assetId ON calibration_records(assetId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_calibration_records_checkedAt ON calibration_records(checkedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_calibration_records_dueAt ON calibration_records(dueAt)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS safety_incidents (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                incidentNo TEXT NOT NULL,
                occurredAt INTEGER NOT NULL,
                incidentType TEXT NOT NULL,
                area TEXT NOT NULL,
                description TEXT NOT NULL,
                injuryOrImpact TEXT NOT NULL,
                immediateAction TEXT NOT NULL,
                rootCause TEXT NOT NULL,
                correctiveAction TEXT NOT NULL,
                preventiveAction TEXT NOT NULL,
                capaRequired INTEGER NOT NULL,
                status TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                closedAt INTEGER,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_safety_incidents_incidentNo ON safety_incidents(incidentNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_safety_incidents_occurredAt ON safety_incidents(occurredAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_safety_incidents_incidentType ON safety_incidents(incidentType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_safety_incidents_status ON safety_incidents(status)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS safety_inspections (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                inspectionNo TEXT NOT NULL,
                inspectionDate INTEGER NOT NULL,
                area TEXT NOT NULL,
                inspectionType TEXT NOT NULL,
                result TEXT NOT NULL,
                findings TEXT NOT NULL,
                correctiveAction TEXT NOT NULL,
                dueAt INTEGER,
                closedAt INTEGER,
                inspectedBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_safety_inspections_inspectionNo ON safety_inspections(inspectionNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_safety_inspections_inspectionDate ON safety_inspections(inspectionDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_safety_inspections_result ON safety_inspections(result)")
    }
}


val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS controlled_documents (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                documentCode TEXT NOT NULL,
                titleAr TEXT NOT NULL,
                category TEXT NOT NULL,
                versionNo INTEGER NOT NULL,
                status TEXT NOT NULL,
                effectiveAt INTEGER,
                reviewDueAt INTEGER,
                ownerRole TEXT NOT NULL,
                contentSummary TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                approvedBy INTEGER,
                approvedAt INTEGER,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_controlled_documents_documentCode ON controlled_documents(documentCode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_controlled_documents_status ON controlled_documents(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_controlled_documents_category ON controlled_documents(category)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS change_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                requestNo TEXT NOT NULL,
                changeType TEXT NOT NULL,
                subject TEXT NOT NULL,
                reason TEXT NOT NULL,
                qualityImpact TEXT NOT NULL,
                financialImpact TEXT NOT NULL,
                inventoryImpact TEXT NOT NULL,
                status TEXT NOT NULL,
                requestedBy INTEGER NOT NULL,
                approvedBy INTEGER,
                approvedAt INTEGER,
                implementedAt INTEGER,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_change_requests_requestNo ON change_requests(requestNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_change_requests_status ON change_requests(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_change_requests_changeType ON change_requests(changeType)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS approval_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                referenceType TEXT NOT NULL,
                referenceId TEXT NOT NULL,
                title TEXT NOT NULL,
                requestedRole TEXT NOT NULL,
                requestedBy INTEGER NOT NULL,
                status TEXT NOT NULL,
                decisionBy INTEGER,
                decisionAt INTEGER,
                decisionNote TEXT NOT NULL,
                requestedAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_approval_requests_status ON approval_requests(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_approval_requests_referenceType ON approval_requests(referenceType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_approval_requests_requestedAt ON approval_requests(requestedAt)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS audit_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                eventAt INTEGER NOT NULL,
                userId INTEGER NOT NULL,
                action TEXT NOT NULL,
                entityType TEXT NOT NULL,
                entityId TEXT NOT NULL,
                oldValue TEXT NOT NULL,
                newValue TEXT NOT NULL,
                reason TEXT NOT NULL,
                deviceInfo TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_eventAt ON audit_events(eventAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_entityType ON audit_events(entityType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_events_userId ON audit_events(userId)")
    }
}


val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS employees (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                code TEXT NOT NULL,
                fullNameAr TEXT NOT NULL,
                fullNameEn TEXT NOT NULL,
                phone TEXT NOT NULL,
                jobTitle TEXT NOT NULL,
                department TEXT NOT NULL,
                hireDate INTEGER NOT NULL,
                status TEXT NOT NULL,
                notes TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_employees_code ON employees(code)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employees_status ON employees(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employees_department ON employees(department)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS training_courses (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                code TEXT NOT NULL,
                titleAr TEXT NOT NULL,
                category TEXT NOT NULL,
                assetType TEXT,
                description TEXT NOT NULL,
                requiresPracticalObservation INTEGER NOT NULL,
                isActive INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_training_courses_code ON training_courses(code)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_training_courses_category ON training_courses(category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_training_courses_isActive ON training_courses(isActive)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS employee_trainings (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                employeeId INTEGER NOT NULL,
                courseId INTEGER NOT NULL,
                completedAt INTEGER NOT NULL,
                expiresAt INTEGER,
                result TEXT NOT NULL,
                practicalObserved INTEGER NOT NULL,
                trainer TEXT NOT NULL,
                certificateRef TEXT NOT NULL,
                notes TEXT NOT NULL,
                recordedBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(courseId) REFERENCES training_courses(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_trainings_employeeId ON employee_trainings(employeeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_trainings_courseId ON employee_trainings(courseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_trainings_completedAt ON employee_trainings(completedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_employee_trainings_expiresAt ON employee_trainings(expiresAt)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS equipment_authorizations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                authorizationNo TEXT NOT NULL,
                employeeId INTEGER NOT NULL,
                assetId INTEGER NOT NULL,
                courseId INTEGER NOT NULL,
                issuedAt INTEGER NOT NULL,
                expiresAt INTEGER,
                status TEXT NOT NULL,
                notes TEXT NOT NULL,
                authorizedBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(assetId) REFERENCES assets(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(courseId) REFERENCES training_courses(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_equipment_authorizations_authorizationNo ON equipment_authorizations(authorizationNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_equipment_authorizations_employeeId ON equipment_authorizations(employeeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_equipment_authorizations_assetId ON equipment_authorizations(assetId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_equipment_authorizations_courseId ON equipment_authorizations(courseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_equipment_authorizations_status ON equipment_authorizations(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_equipment_authorizations_expiresAt ON equipment_authorizations(expiresAt)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS production_operator_assignments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                orderId INTEGER NOT NULL,
                employeeId INTEGER NOT NULL,
                assignedBy INTEGER NOT NULL,
                assignedAt INTEGER NOT NULL,
                FOREIGN KEY(orderId) REFERENCES production_orders(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_production_operator_assignments_orderId ON production_operator_assignments(orderId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_production_operator_assignments_employeeId ON production_operator_assignments(employeeId)")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE accounts SET isPosting = 0 WHERE code = '3000'")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS treasury_accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                code TEXT NOT NULL,
                nameAr TEXT NOT NULL,
                kind TEXT NOT NULL,
                accountId INTEGER NOT NULL,
                currencyCode TEXT NOT NULL,
                bankName TEXT NOT NULL,
                accountNumber TEXT NOT NULL,
                isActive INTEGER NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(accountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(currencyCode) REFERENCES currencies(code) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_treasury_accounts_code ON treasury_accounts(code)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_treasury_accounts_accountId ON treasury_accounts(accountId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_treasury_accounts_currencyCode ON treasury_accounts(currencyCode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_treasury_accounts_isActive ON treasury_accounts(isActive)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_entries_entryDate_phase8 ON journal_entries(entryDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_entries_sourceType_phase8 ON journal_entries(sourceType)")
        db.execSQL("""
            INSERT OR IGNORE INTO treasury_accounts
            (code, nameAr, kind, accountId, currencyCode, bankName, accountNumber, isActive, createdBy, createdAt)
            SELECT 'CASH-MAIN', 'الصندوق الرئيسي', 'CASH', id, 'YER_NEW', '', '', 1, 1, strftime('%s','now') * 1000
            FROM accounts WHERE code = '1100' LIMIT 1
        """.trimIndent())
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS fx_snapshots (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                effectiveAt INTEGER NOT NULL,
                usdNewYer REAL NOT NULL,
                usdOldYer REAL NOT NULL,
                oldYerToNewYer REAL NOT NULL,
                sourceNote TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fx_snapshots_effectiveAt ON fx_snapshots(effectiveAt)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS province_policies (
                code TEXT NOT NULL PRIMARY KEY,
                nameAr TEXT NOT NULL,
                currencyCode TEXT NOT NULL,
                defaultTransportPerCartonBase REAL NOT NULL,
                requiresDailyFx INTEGER NOT NULL,
                requiresActualTransport INTEGER NOT NULL,
                requiresFeesAndCustoms INTEGER NOT NULL,
                notes TEXT NOT NULL,
                isActive INTEGER NOT NULL,
                FOREIGN KEY(currencyCode) REFERENCES currencies(code) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_province_policies_currencyCode ON province_policies(currencyCode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_province_policies_isActive ON province_policies(isActive)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS invoice_geographic_costs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                invoiceId INTEGER NOT NULL,
                province TEXT NOT NULL,
                cartonsEquivalent REAL NOT NULL,
                transportCostBase REAL NOT NULL,
                feesCustomsCostBase REAL NOT NULL,
                otherDirectCostBase REAL NOT NULL,
                notes TEXT NOT NULL,
                recordedBy INTEGER NOT NULL,
                recordedAt INTEGER NOT NULL,
                FOREIGN KEY(invoiceId) REFERENCES sales_invoices(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_invoice_geographic_costs_invoiceId ON invoice_geographic_costs(invoiceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_geographic_costs_province ON invoice_geographic_costs(province)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_geographic_costs_recordedAt ON invoice_geographic_costs(recordedAt)")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS inventory_counts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                countNo TEXT NOT NULL,
                warehouseId INTEGER NOT NULL,
                countDate INTEGER NOT NULL,
                status TEXT NOT NULL,
                notes TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                postedAt INTEGER,
                FOREIGN KEY(warehouseId) REFERENCES warehouses(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_counts_countNo ON inventory_counts(countNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_counts_warehouseId ON inventory_counts(warehouseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_counts_status ON inventory_counts(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_counts_countDate ON inventory_counts(countDate)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS inventory_count_lines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                countId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                lotNo TEXT,
                expiryDate INTEGER,
                lotKey TEXT NOT NULL,
                expiryKey INTEGER NOT NULL,
                systemQtyBase REAL NOT NULL,
                countedQtyBase REAL,
                varianceQtyBase REAL NOT NULL,
                unitCostBase REAL NOT NULL,
                reason TEXT NOT NULL,
                FOREIGN KEY(countId) REFERENCES inventory_counts(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_count_lines_countId ON inventory_count_lines(countId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_count_lines_itemId ON inventory_count_lines(itemId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_count_lines_countId_itemId_lotKey_expiryKey ON inventory_count_lines(countId,itemId,lotKey,expiryKey)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS inventory_lot_controls (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                warehouseId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                lotNo TEXT,
                expiryDate INTEGER,
                lotKey TEXT NOT NULL,
                expiryKey INTEGER NOT NULL,
                status TEXT NOT NULL,
                reason TEXT NOT NULL,
                changedBy INTEGER NOT NULL,
                changedAt INTEGER NOT NULL,
                FOREIGN KEY(warehouseId) REFERENCES warehouses(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_lot_controls_warehouseId ON inventory_lot_controls(warehouseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_lot_controls_itemId ON inventory_lot_controls(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_lot_controls_status ON inventory_lot_controls(status)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_lot_controls_warehouseId_itemId_lotKey_expiryKey ON inventory_lot_controls(warehouseId,itemId,lotKey,expiryKey)")

        db.execSQL("INSERT OR IGNORE INTO warehouses(code,nameAr,nameEn,location,isActive) VALUES('RM-QC','حجر المواد الخام','Raw Materials Quarantine','',1)")
        db.execSQL("INSERT OR IGNORE INTO warehouses(code,nameAr,nameEn,location,isActive) VALUES('FG-QC','حجر المنتج النهائي','Finished Goods Quarantine','',1)")
        db.execSQL("INSERT OR IGNORE INTO warehouses(code,nameAr,nameEn,location,isActive) VALUES('RET','مخزن المرتجعات','Returns Warehouse','',1)")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS number_sequences (
                sequenceKey TEXT NOT NULL PRIMARY KEY,
                lastValue INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """.trimIndent())

        // Preserve existing records. New automatic patterns start after any matching
        // manually entered codes that already exist in the database.
        db.execSQL("""
            INSERT OR REPLACE INTO number_sequences(sequenceKey,lastValue,updatedAt)
            SELECT 'MASTER:SUP', COALESCE(MAX(CAST(substr(code,5) AS INTEGER)),0), strftime('%s','now') * 1000
            FROM suppliers WHERE code GLOB 'SUP-[0-9]*'
        """.trimIndent())
        db.execSQL("""
            INSERT OR REPLACE INTO number_sequences(sequenceKey,lastValue,updatedAt)
            SELECT 'MASTER:CUS', COALESCE(MAX(CAST(substr(code,5) AS INTEGER)),0), strftime('%s','now') * 1000
            FROM customers WHERE code GLOB 'CUS-[0-9]*'
        """.trimIndent())
        db.execSQL("""
            INSERT OR REPLACE INTO number_sequences(sequenceKey,lastValue,updatedAt)
            SELECT 'MASTER:UNT', COALESCE(MAX(CAST(substr(code,5) AS INTEGER)),0), strftime('%s','now') * 1000
            FROM units WHERE code GLOB 'UNT-[0-9]*'
        """.trimIndent())
        db.execSQL("""
            INSERT OR REPLACE INTO number_sequences(sequenceKey,lastValue,updatedAt)
            SELECT 'MASTER:ITEM:RM', COALESCE(MAX(CAST(substr(code,4) AS INTEGER)),0), strftime('%s','now') * 1000
            FROM items WHERE code GLOB 'RM-[0-9]*'
        """.trimIndent())
        db.execSQL("""
            INSERT OR REPLACE INTO number_sequences(sequenceKey,lastValue,updatedAt)
            SELECT 'MASTER:ITEM:PK', COALESCE(MAX(CAST(substr(code,4) AS INTEGER)),0), strftime('%s','now') * 1000
            FROM items WHERE code GLOB 'PK-[0-9]*'
        """.trimIndent())
        db.execSQL("""
            INSERT OR REPLACE INTO number_sequences(sequenceKey,lastValue,updatedAt)
            SELECT 'MASTER:ITEM:FG', COALESCE(MAX(CAST(substr(code,4) AS INTEGER)),0), strftime('%s','now') * 1000
            FROM items WHERE code GLOB 'FG-[0-9]*'
        """.trimIndent())
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // These tables did not exist before Phase 12. Recreate only them if a prior interrupted
        // Phase 12 attempt left an inconsistent copy. Existing ERP/business tables are untouched.
        db.execSQL("DROP TABLE IF EXISTS control_tests")
        db.execSQL("DROP TABLE IF EXISTS control_exceptions")
        db.execSQL("DROP TABLE IF EXISTS internal_controls")
        db.execSQL("DROP TABLE IF EXISTS segregation_rules")
        db.execSQL("DROP TABLE IF EXISTS risk_register")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS risk_register (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                riskNo TEXT NOT NULL,
                title TEXT NOT NULL,
                category TEXT NOT NULL,
                description TEXT NOT NULL,
                likelihood INTEGER NOT NULL,
                impact INTEGER NOT NULL,
                inherentScore INTEGER NOT NULL,
                mitigationPlan TEXT NOT NULL,
                residualLikelihood INTEGER NOT NULL,
                residualImpact INTEGER NOT NULL,
                residualScore INTEGER NOT NULL,
                ownerRole TEXT NOT NULL,
                status TEXT NOT NULL,
                dueAt INTEGER,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                reviewedAt INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_risk_register_riskNo ON risk_register(riskNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_risk_register_status ON risk_register(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_risk_register_category ON risk_register(category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_risk_register_ownerRole ON risk_register(ownerRole)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS internal_controls (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                controlCode TEXT NOT NULL,
                title TEXT NOT NULL,
                controlType TEXT NOT NULL,
                frequency TEXT NOT NULL,
                ownerRole TEXT NOT NULL,
                relatedRiskId INTEGER,
                designDescription TEXT NOT NULL,
                evidenceRequired TEXT NOT NULL,
                isActive INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(relatedRiskId) REFERENCES risk_register(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_internal_controls_controlCode ON internal_controls(controlCode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_internal_controls_relatedRiskId ON internal_controls(relatedRiskId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_internal_controls_ownerRole ON internal_controls(ownerRole)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_internal_controls_isActive ON internal_controls(isActive)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS control_tests (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                controlId INTEGER NOT NULL,
                testedAt INTEGER NOT NULL,
                result TEXT NOT NULL,
                evidenceRef TEXT NOT NULL,
                finding TEXT NOT NULL,
                testedBy INTEGER NOT NULL,
                nextDueAt INTEGER,
                FOREIGN KEY(controlId) REFERENCES internal_controls(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_control_tests_controlId ON control_tests(controlId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_control_tests_result ON control_tests(result)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_control_tests_testedAt ON control_tests(testedAt)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS control_exceptions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                exceptionNo TEXT NOT NULL,
                controlId INTEGER,
                sourceType TEXT NOT NULL,
                sourceId TEXT NOT NULL,
                severity TEXT NOT NULL,
                description TEXT NOT NULL,
                status TEXT NOT NULL,
                ownerRole TEXT NOT NULL,
                dueAt INTEGER,
                openedBy INTEGER NOT NULL,
                detectedAt INTEGER NOT NULL,
                approvedBy INTEGER,
                approvedAt INTEGER,
                closureNote TEXT NOT NULL,
                closedAt INTEGER,
                FOREIGN KEY(controlId) REFERENCES internal_controls(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_control_exceptions_exceptionNo ON control_exceptions(exceptionNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_control_exceptions_controlId ON control_exceptions(controlId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_control_exceptions_status ON control_exceptions(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_control_exceptions_severity ON control_exceptions(severity)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_control_exceptions_dueAt ON control_exceptions(dueAt)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS segregation_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                ruleCode TEXT NOT NULL,
                actionKey TEXT NOT NULL,
                initiatorRole TEXT NOT NULL,
                approverRole TEXT NOT NULL,
                description TEXT NOT NULL,
                requireDifferentUser INTEGER NOT NULL,
                requireDifferentRole INTEGER NOT NULL,
                isActive INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_segregation_rules_ruleCode ON segregation_rules(ruleCode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_segregation_rules_actionKey ON segregation_rules(actionKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_segregation_rules_isActive ON segregation_rules(isActive)")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS demand_seasonality (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                itemId INTEGER NOT NULL,
                provinceCode TEXT NOT NULL,
                month INTEGER NOT NULL,
                demandFactor REAL NOT NULL,
                note TEXT NOT NULL,
                updatedBy INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_demand_seasonality_itemId ON demand_seasonality(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_demand_seasonality_provinceCode ON demand_seasonality(provinceCode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_demand_seasonality_month ON demand_seasonality(month)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_demand_seasonality_itemId_provinceCode_month ON demand_seasonality(itemId,provinceCode,month)")
    }
}


val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE production_issues ADD COLUMN issueKind TEXT NOT NULL DEFAULT 'ISSUE'")
        db.execSQL("ALTER TABLE production_issues ADD COLUMN correctionOfIssueId INTEGER")
        db.execSQL("ALTER TABLE production_issues ADD COLUMN reason TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE production_issues ADD COLUMN createdBy INTEGER")
    }
}


val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS demand_plans (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                itemId INTEGER NOT NULL,
                provinceCode TEXT NOT NULL,
                planYear INTEGER NOT NULL,
                planMonth INTEGER NOT NULL,
                baselineQtyBase REAL NOT NULL,
                seasonFactor REAL NOT NULL,
                systemForecastQtyBase REAL NOT NULL,
                plannedQtyBase REAL NOT NULL,
                manualAdjustmentQtyBase REAL NOT NULL,
                note TEXT NOT NULL,
                status TEXT NOT NULL,
                revision INTEGER NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedBy INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                approvedBy INTEGER,
                approvedAt INTEGER,
                lastActionReason TEXT NOT NULL,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_demand_plans_itemId ON demand_plans(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_demand_plans_provinceCode ON demand_plans(provinceCode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_demand_plans_planYear ON demand_plans(planYear)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_demand_plans_planMonth ON demand_plans(planMonth)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_demand_plans_status ON demand_plans(status)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_demand_plans_itemId_provinceCode_planYear_planMonth ON demand_plans(itemId,provinceCode,planYear,planMonth)")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sales_budget_weeks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                demandPlanId INTEGER NOT NULL,
                weekNo INTEGER NOT NULL,
                plannedQtyBase REAL NOT NULL,
                note TEXT NOT NULL,
                updatedBy INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(demandPlanId) REFERENCES demand_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_budget_weeks_demandPlanId ON sales_budget_weeks(demandPlanId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_budget_weeks_weekNo ON sales_budget_weeks(weekNo)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sales_budget_weeks_demandPlanId_weekNo ON sales_budget_weeks(demandPlanId,weekNo)")
    }
}



val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS inventory_planning_policies (
                itemId INTEGER NOT NULL PRIMARY KEY,
                safetyStockDays REAL NOT NULL,
                leadTimeDays REAL NOT NULL,
                note TEXT NOT NULL,
                updatedBy INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS production_plans (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                itemId INTEGER NOT NULL,
                planYear INTEGER NOT NULL,
                planMonth INTEGER NOT NULL,
                recipeId INTEGER NOT NULL,
                recipeVersionNo INTEGER NOT NULL,
                recipeTargetOutputQtyBase REAL NOT NULL,
                approvedDemandQtyBase REAL NOT NULL,
                approvedProvinceCount INTEGER NOT NULL,
                finishedStockQtyBase REAL NOT NULL,
                finishedDailyDemandQtyBase REAL NOT NULL,
                finishedSafetyStockQtyBase REAL NOT NULL,
                finishedReorderPointQtyBase REAL NOT NULL,
                netProductionNeedQtyBase REAL NOT NULL,
                plannedBatchCount INTEGER NOT NULL,
                plannedOutputQtyBase REAL NOT NULL,
                projectedEndingFinishedQtyBase REAL NOT NULL,
                status TEXT NOT NULL,
                revision INTEGER NOT NULL,
                generatedBy INTEGER NOT NULL,
                generatedAt INTEGER NOT NULL,
                updatedBy INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                approvedBy INTEGER,
                approvedAt INTEGER,
                lastActionReason TEXT NOT NULL,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_production_plans_itemId ON production_plans(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_production_plans_planYear ON production_plans(planYear)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_production_plans_planMonth ON production_plans(planMonth)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_production_plans_status ON production_plans(status)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_production_plans_itemId_planYear_planMonth ON production_plans(itemId,planYear,planMonth)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS production_plan_materials (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                productionPlanId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                sequenceNo INTEGER NOT NULL,
                perBatchQtyBase REAL NOT NULL,
                expectedLossPct REAL NOT NULL,
                requiredQtyBase REAL NOT NULL,
                currentStockQtyBase REAL NOT NULL,
                dailyUsageQtyBase REAL NOT NULL,
                safetyStockQtyBase REAL NOT NULL,
                reorderPointQtyBase REAL NOT NULL,
                suggestedPurchaseQtyBase REAL NOT NULL,
                projectedEndingQtyBase REAL NOT NULL,
                FOREIGN KEY(productionPlanId) REFERENCES production_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_production_plan_materials_productionPlanId ON production_plan_materials(productionPlanId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_production_plan_materials_itemId ON production_plan_materials(itemId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_production_plan_materials_productionPlanId_sequenceNo ON production_plan_materials(productionPlanId,sequenceNo)")
    }
}


val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE purchase_invoices ADD COLUMN dueDate INTEGER")
        db.execSQL("""
            UPDATE purchase_invoices
            SET dueDate = CASE
                WHEN paymentType='CREDIT' THEN invoiceDate +
                    (COALESCE((SELECT paymentTermsDays FROM suppliers s WHERE s.id=purchase_invoices.supplierId),0) * 86400000)
                ELSE NULL
            END
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS supplier_payments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                paymentNo TEXT NOT NULL,
                supplierId INTEGER NOT NULL,
                treasuryAccountId INTEGER NOT NULL,
                paymentDate INTEGER NOT NULL,
                currencyCode TEXT NOT NULL,
                exchangeRate REAL NOT NULL,
                amountOriginal REAL NOT NULL,
                cashAmountBase REAL NOT NULL,
                notes TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(supplierId) REFERENCES suppliers(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(treasuryAccountId) REFERENCES treasury_accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_supplier_payments_paymentNo ON supplier_payments(paymentNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payments_supplierId ON supplier_payments(supplierId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payments_treasuryAccountId ON supplier_payments(treasuryAccountId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payments_paymentDate ON supplier_payments(paymentDate)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS supplier_payment_allocations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                paymentId INTEGER NOT NULL,
                invoiceId INTEGER NOT NULL,
                amountOriginal REAL NOT NULL,
                allocatedBase REAL NOT NULL,
                FOREIGN KEY(paymentId) REFERENCES supplier_payments(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(invoiceId) REFERENCES purchase_invoices(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payment_allocations_paymentId ON supplier_payment_allocations(paymentId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payment_allocations_invoiceId ON supplier_payment_allocations(invoiceId)")
    }
}


val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS warehouse_transfers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                transferNo TEXT NOT NULL,
                transferDate INTEGER NOT NULL,
                fromWarehouseId INTEGER NOT NULL,
                toWarehouseId INTEGER NOT NULL,
                status TEXT NOT NULL,
                notes TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                postedBy INTEGER,
                postedAt INTEGER,
                cancelReason TEXT NOT NULL,
                FOREIGN KEY(fromWarehouseId) REFERENCES warehouses(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(toWarehouseId) REFERENCES warehouses(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_warehouse_transfers_transferNo ON warehouse_transfers(transferNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_warehouse_transfers_fromWarehouseId ON warehouse_transfers(fromWarehouseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_warehouse_transfers_toWarehouseId ON warehouse_transfers(toWarehouseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_warehouse_transfers_transferDate ON warehouse_transfers(transferDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_warehouse_transfers_status ON warehouse_transfers(status)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS warehouse_transfer_lines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                transferId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                quantityBase REAL NOT NULL,
                unitCostBase REAL NOT NULL,
                lotNo TEXT,
                expiryDate INTEGER,
                lotKey TEXT NOT NULL,
                expiryKey INTEGER NOT NULL,
                FOREIGN KEY(transferId) REFERENCES warehouse_transfers(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_warehouse_transfer_lines_transferId ON warehouse_transfer_lines(transferId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_warehouse_transfer_lines_itemId ON warehouse_transfer_lines(itemId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_warehouse_transfer_lines_transferId_itemId_lotKey_expiryKey ON warehouse_transfer_lines(transferId,itemId,lotKey,expiryKey)")
    }
}


val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS warehouse_reorder_policies (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                warehouseId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                reorderLevel REAL NOT NULL,
                updatedBy INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(warehouseId) REFERENCES warehouses(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_warehouse_reorder_policies_warehouseId ON warehouse_reorder_policies(warehouseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_warehouse_reorder_policies_itemId ON warehouse_reorder_policies(itemId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_warehouse_reorder_policies_warehouseId_itemId ON warehouse_reorder_policies(warehouseId,itemId)")

        // Preserve existing item-level thresholds as operational warehouse defaults only.
        // Quarantine/returns warehouses are deliberately not seeded as available-stock policies.
        db.execSQL("""
            INSERT OR IGNORE INTO warehouse_reorder_policies
                (warehouseId, itemId, reorderLevel, updatedBy, updatedAt)
            SELECT w.id, i.id, i.reorderLevel, 0, 0
            FROM items i
            JOIN warehouses w ON (
                (i.category IN ('RAW_MATERIAL','PACKAGING') AND w.code = 'RM') OR
                (i.category = 'FINISHED_GOOD' AND w.code = 'FG')
            )
            WHERE i.isActive = 1 AND w.isActive = 1 AND i.reorderLevel > 0
        """.trimIndent())
    }
}


val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE warehouse_transfers ADD COLUMN reversalReason TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE warehouse_transfers ADD COLUMN reversedBy INTEGER")
        db.execSQL("ALTER TABLE warehouse_transfers ADD COLUMN reversedAt INTEGER")
    }
}


val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS quality_specifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                productItemId INTEGER NOT NULL,
                stage TEXT NOT NULL,
                parameterName TEXT NOT NULL,
                unit TEXT NOT NULL,
                minValue REAL,
                maxValue REAL,
                targetValue REAL,
                requiredSampleSize INTEGER NOT NULL,
                isRequired INTEGER NOT NULL,
                isActive INTEGER NOT NULL,
                notes TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(productItemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quality_specifications_productItemId ON quality_specifications(productItemId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_quality_specifications_productItemId_stage_parameterName ON quality_specifications(productItemId,stage,parameterName)")
        db.execSQL("ALTER TABLE quality_checks ADD COLUMN specificationId INTEGER")
        db.execSQL("ALTER TABLE quality_checks ADD COLUMN measuredValue REAL")
        db.execSQL("ALTER TABLE quality_checks ADD COLUMN unit TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE quality_checks ADD COLUMN minValue REAL")
        db.execSQL("ALTER TABLE quality_checks ADD COLUMN maxValue REAL")
        db.execSQL("ALTER TABLE quality_checks ADD COLUMN targetValue REAL")
        db.execSQL("ALTER TABLE quality_checks ADD COLUMN sampleSize INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quality_checks_specificationId ON quality_checks(specificationId)")
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sales_prices ADD COLUMN effectiveTo INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_prices_effectiveTo ON sales_prices(effectiveTo)")

        // Preserve the historical meaning of existing price rows by closing each row
        // one millisecond before the next row for the same product/channel/province/currency.
        db.execSQL("""
            UPDATE sales_prices
            SET effectiveTo = (
                SELECT MIN(next_price.effectiveFrom) - 1
                FROM sales_prices AS next_price
                WHERE next_price.itemId = sales_prices.itemId
                  AND next_price.channel = sales_prices.channel
                  AND next_price.province = sales_prices.province
                  AND next_price.currencyCode = sales_prices.currencyCode
                  AND next_price.effectiveFrom > sales_prices.effectiveFrom
            )
            WHERE EXISTS (
                SELECT 1
                FROM sales_prices AS next_price
                WHERE next_price.itemId = sales_prices.itemId
                  AND next_price.channel = sales_prices.channel
                  AND next_price.province = sales_prices.province
                  AND next_price.currencyCode = sales_prices.currencyCode
                  AND next_price.effectiveFrom > sales_prices.effectiveFrom
            )
        """.trimIndent())
    }
}



val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS quality_check_samples (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                checkId INTEGER NOT NULL,
                sequenceNo INTEGER NOT NULL,
                measuredValue REAL NOT NULL,
                FOREIGN KEY(checkId) REFERENCES quality_checks(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quality_check_samples_checkId ON quality_check_samples(checkId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_quality_check_samples_checkId_sequenceNo ON quality_check_samples(checkId, sequenceNo)")

        // Existing quantitative checks are preserved as historical aggregate checks.
        // We intentionally do not invent individual sample readings for legacy records.
    }
}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Schema 24 existed in two compatible branches. IF NOT EXISTS safely converges both:
        // original 14.5.24 already has quality_check_samples; Party preview already has party_*.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS quality_check_samples (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                checkId INTEGER NOT NULL,
                sequenceNo INTEGER NOT NULL,
                measuredValue REAL NOT NULL,
                FOREIGN KEY(checkId) REFERENCES quality_checks(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quality_check_samples_checkId ON quality_check_samples(checkId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_quality_check_samples_checkId_sequenceNo ON quality_check_samples(checkId, sequenceNo)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS party_vouchers (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                voucherNo TEXT NOT NULL,
                voucherType TEXT NOT NULL,
                treasuryAccountId INTEGER NOT NULL,
                offsetAccountId INTEGER NOT NULL,
                customerId INTEGER,
                supplierId INTEGER,
                employeeId INTEGER,
                partyType TEXT NOT NULL,
                partyNameSnapshot TEXT NOT NULL,
                voucherDate INTEGER NOT NULL,
                currencyCode TEXT NOT NULL,
                exchangeRate REAL NOT NULL,
                amountOriginal REAL NOT NULL,
                amountBase REAL NOT NULL,
                description TEXT NOT NULL,
                referenceNo TEXT NOT NULL,
                journalEntryId INTEGER NOT NULL,
                status TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                reversalReason TEXT NOT NULL,
                reversedBy INTEGER,
                reversedAt INTEGER,
                reversalJournalEntryId INTEGER,
                FOREIGN KEY(treasuryAccountId) REFERENCES treasury_accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(offsetAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(supplierId) REFERENCES suppliers(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(journalEntryId) REFERENCES journal_entries(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_party_vouchers_voucherNo ON party_vouchers(voucherNo)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_party_vouchers_journalEntryId ON party_vouchers(journalEntryId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_vouchers_customerId ON party_vouchers(customerId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_vouchers_supplierId ON party_vouchers(supplierId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_vouchers_employeeId ON party_vouchers(employeeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_vouchers_voucherDate ON party_vouchers(voucherDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_vouchers_status ON party_vouchers(status)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS party_attachments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                customerId INTEGER,
                supplierId INTEGER,
                fileName TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                uri TEXT NOT NULL,
                notes TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(supplierId) REFERENCES suppliers(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_attachments_customerId ON party_attachments(customerId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_attachments_supplierId ON party_attachments(supplierId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_attachments_createdAt ON party_attachments(createdAt)")
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS sales_representatives (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                code TEXT NOT NULL,
                employeeId INTEGER,
                repType TEXT NOT NULL,
                fullNameAr TEXT NOT NULL,
                fullNameEn TEXT NOT NULL,
                phone TEXT NOT NULL,
                territory TEXT NOT NULL,
                commissionRatePct REAL NOT NULL,
                status TEXT NOT NULL,
                notes TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sales_representatives_code ON sales_representatives(code)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sales_representatives_employeeId ON sales_representatives(employeeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_representatives_status ON sales_representatives(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_representatives_territory ON sales_representatives(territory)")

        // Do not guess legacy text-only representative names. Existing salesRepName remains
        // as a historical/display snapshot until the user explicitly links a stable rep ID.
        db.execSQL("ALTER TABLE customers ADD COLUMN salesRepId INTEGER REFERENCES sales_representatives(id) ON UPDATE NO ACTION ON DELETE SET NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_salesRepId ON customers(salesRepId)")

        db.execSQL("ALTER TABLE sales_invoices ADD COLUMN salesRepId INTEGER REFERENCES sales_representatives(id) ON UPDATE NO ACTION ON DELETE SET NULL")
        db.execSQL("ALTER TABLE sales_invoices ADD COLUMN salesRepNameSnapshot TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sales_invoices ADD COLUMN salesRepRatePct REAL NOT NULL DEFAULT 10.0")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_invoices_salesRepId ON sales_invoices(salesRepId)")

        db.execSQL("ALTER TABLE sales_commissions ADD COLUMN salesRepId INTEGER REFERENCES sales_representatives(id) ON UPDATE NO ACTION ON DELETE SET NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_commissions_salesRepId ON sales_commissions(salesRepId)")

        db.execSQL("ALTER TABLE party_vouchers ADD COLUMN salesRepId INTEGER REFERENCES sales_representatives(id) ON UPDATE NO ACTION ON DELETE RESTRICT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_party_vouchers_salesRepId ON party_vouchers(salesRepId)")
    }
}


val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS expense_dimensions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                partyVoucherId INTEGER NOT NULL,
                employeeId INTEGER,
                employeeNameSnapshot TEXT NOT NULL,
                salesRepId INTEGER,
                salesRepNameSnapshot TEXT NOT NULL,
                costCenterCode TEXT NOT NULL,
                costCenterNameSnapshot TEXT NOT NULL,
                organizationUnit TEXT NOT NULL,
                referenceType TEXT NOT NULL,
                referenceId INTEGER,
                referenceNo TEXT NOT NULL,
                referenceLabelSnapshot TEXT NOT NULL,
                customerId INTEGER,
                customerNameSnapshot TEXT NOT NULL,
                supplierId INTEGER,
                supplierNameSnapshot TEXT NOT NULL,
                itemId INTEGER,
                itemNameSnapshot TEXT NOT NULL,
                paymentMethodSnapshot TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(partyVoucherId) REFERENCES party_vouchers(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(employeeId) REFERENCES employees(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(salesRepId) REFERENCES sales_representatives(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(customerId) REFERENCES customers(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(supplierId) REFERENCES suppliers(id) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(itemId) REFERENCES items(id) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_expense_dimensions_partyVoucherId ON expense_dimensions(partyVoucherId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_dimensions_employeeId ON expense_dimensions(employeeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_dimensions_salesRepId ON expense_dimensions(salesRepId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_dimensions_customerId ON expense_dimensions(customerId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_dimensions_supplierId ON expense_dimensions(supplierId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_dimensions_itemId ON expense_dimensions(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_dimensions_costCenterCode ON expense_dimensions(costCenterCode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_dimensions_organizationUnit ON expense_dimensions(organizationUnit)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_dimensions_referenceType ON expense_dimensions(referenceType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_dimensions_referenceId ON expense_dimensions(referenceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_dimensions_createdAt ON expense_dimensions(createdAt)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS expense_attachments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                expenseId INTEGER NOT NULL,
                fileName TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                uri TEXT NOT NULL,
                notes TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(expenseId) REFERENCES expense_dimensions(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_attachments_expenseId ON expense_attachments(expenseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_attachments_createdAt ON expense_attachments(createdAt)")
    }
}


val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS accounting_periods (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                fiscalYear INTEGER NOT NULL,
                periodNo INTEGER NOT NULL,
                nameAr TEXT NOT NULL,
                startDate INTEGER NOT NULL,
                endDate INTEGER NOT NULL,
                status TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                closedBy INTEGER,
                closedAt INTEGER,
                closeReason TEXT NOT NULL,
                reopenedBy INTEGER,
                reopenedAt INTEGER,
                reopenReason TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_accounting_periods_fiscalYear_periodNo ON accounting_periods(fiscalYear, periodNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_accounting_periods_startDate ON accounting_periods(startDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_accounting_periods_endDate ON accounting_periods(endDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_accounting_periods_status ON accounting_periods(status)")

        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS trg_journal_entries_closed_period
            BEFORE INSERT ON journal_entries
            WHEN EXISTS (
                SELECT 1
                FROM accounting_periods ap
                WHERE NEW.entryDate BETWEEN ap.startDate AND ap.endDate
                  AND ap.status <> 'OPEN'
            )
            BEGIN
                SELECT RAISE(ABORT, 'الفترة المحاسبية مقفلة');
            END
        """.trimIndent())
    }
}


val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE customer_receipts ADD COLUMN reversalOfReceiptId INTEGER")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_customer_receipts_reversalOfReceiptId ON customer_receipts(reversalOfReceiptId)")
        db.execSQL("ALTER TABLE supplier_payments ADD COLUMN reversalOfPaymentId INTEGER")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_supplier_payments_reversalOfPaymentId ON supplier_payments(reversalOfPaymentId)")
    }
}


val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS fiscal_year_closings (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                fiscalYear INTEGER NOT NULL,
                startDate INTEGER NOT NULL,
                endDate INTEGER NOT NULL,
                closingEntryId INTEGER,
                netIncomeBase REAL NOT NULL,
                retainedEarningsAccountId INTEGER NOT NULL,
                status TEXT NOT NULL,
                closeReason TEXT NOT NULL,
                closedBy INTEGER NOT NULL,
                closedAt INTEGER NOT NULL,
                reversalEntryId INTEGER,
                reopenReason TEXT NOT NULL,
                reopenedBy INTEGER,
                reopenedAt INTEGER
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fiscal_year_closings_fiscalYear ON fiscal_year_closings(fiscalYear)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fiscal_year_closings_status ON fiscal_year_closings(status)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fiscal_year_closings_closingEntryId ON fiscal_year_closings(closingEntryId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fiscal_year_closings_reversalEntryId ON fiscal_year_closings(reversalEntryId)")
    }
}

val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS treasury_cash_counts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                treasuryAccountId INTEGER NOT NULL,
                countDate INTEGER NOT NULL,
                expectedBalanceBase REAL NOT NULL,
                actualBalanceBase REAL NOT NULL,
                differenceBase REAL NOT NULL,
                status TEXT NOT NULL,
                notes TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                resolutionEntryId INTEGER,
                resolutionReason TEXT NOT NULL,
                resolvedBy INTEGER,
                resolvedAt INTEGER,
                FOREIGN KEY(treasuryAccountId) REFERENCES treasury_accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_treasury_cash_counts_treasuryAccountId ON treasury_cash_counts(treasuryAccountId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_treasury_cash_counts_countDate ON treasury_cash_counts(countDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_treasury_cash_counts_status ON treasury_cash_counts(status)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_treasury_cash_counts_resolutionEntryId ON treasury_cash_counts(resolutionEntryId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS bank_statements (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                treasuryAccountId INTEGER NOT NULL,
                startDate INTEGER NOT NULL,
                endDate INTEGER NOT NULL,
                openingBalanceBase REAL NOT NULL,
                closingBalanceBase REAL NOT NULL,
                status TEXT NOT NULL,
                notes TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                reconciledBy INTEGER,
                reconciledAt INTEGER,
                FOREIGN KEY(treasuryAccountId) REFERENCES treasury_accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_statements_treasuryAccountId ON bank_statements(treasuryAccountId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_statements_startDate ON bank_statements(startDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_statements_endDate ON bank_statements(endDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_statements_status ON bank_statements(status)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bank_statements_treasuryAccountId_startDate_endDate ON bank_statements(treasuryAccountId,startDate,endDate)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS bank_statement_lines (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                statementId INTEGER NOT NULL,
                transactionDate INTEGER NOT NULL,
                referenceNo TEXT NOT NULL,
                description TEXT NOT NULL,
                amountBase REAL NOT NULL,
                matchedJournalEntryId INTEGER,
                matchedBy INTEGER,
                matchedAt INTEGER,
                FOREIGN KEY(statementId) REFERENCES bank_statements(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_statement_lines_statementId ON bank_statement_lines(statementId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_statement_lines_transactionDate ON bank_statement_lines(transactionDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_statement_lines_matchedJournalEntryId ON bank_statement_lines(matchedJournalEntryId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_statement_lines_statementId_transactionDate ON bank_statement_lines(statementId,transactionDate)")

        db.execSQL("""
            INSERT OR IGNORE INTO accounts(code, nameAr, nameEn, type, parentCode, isPosting, isActive)
            VALUES('6950', 'فروقات الصندوق', 'Cash Over and Short', 'EXPENSE', NULL, 1, 1)
        """.trimIndent())
    }
}

// Phase 14.5.39 (accounting branch only): original-currency treasury ledger,
// FX revaluation and foreign-currency bank/cash reconciliation.
// Migration number is PROVISIONAL and must be renumbered by central integration if needed.
val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE treasury_cash_counts ADD COLUMN expectedBalanceOriginal REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE treasury_cash_counts ADD COLUMN actualBalanceOriginal REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE treasury_cash_counts ADD COLUMN differenceOriginal REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE treasury_cash_counts ADD COLUMN rateToBase REAL NOT NULL DEFAULT 1")
        db.execSQL("""
            UPDATE treasury_cash_counts
            SET expectedBalanceOriginal = expectedBalanceBase,
                actualBalanceOriginal = actualBalanceBase,
                differenceOriginal = differenceBase,
                rateToBase = 1
            WHERE treasuryAccountId IN (SELECT id FROM treasury_accounts WHERE currencyCode = 'YER_NEW')
        """.trimIndent())

        db.execSQL("ALTER TABLE bank_statements ADD COLUMN currencyCode TEXT NOT NULL DEFAULT 'YER_NEW'")
        db.execSQL("ALTER TABLE bank_statements ADD COLUMN openingBalanceOriginal REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE bank_statements ADD COLUMN closingBalanceOriginal REAL NOT NULL DEFAULT 0")
        db.execSQL("""
            UPDATE bank_statements
            SET currencyCode = COALESCE((SELECT currencyCode FROM treasury_accounts t WHERE t.id = bank_statements.treasuryAccountId), 'YER_NEW'),
                openingBalanceOriginal = openingBalanceBase,
                closingBalanceOriginal = closingBalanceBase
        """.trimIndent())

        db.execSQL("ALTER TABLE bank_statement_lines ADD COLUMN amountOriginal REAL NOT NULL DEFAULT 0")
        db.execSQL("UPDATE bank_statement_lines SET amountOriginal = amountBase")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS treasury_fx_revaluations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                treasuryAccountId INTEGER NOT NULL,
                valuationDate INTEGER NOT NULL,
                currencyCode TEXT NOT NULL,
                originalBalance REAL NOT NULL,
                carryingBalanceBeforeBase REAL NOT NULL,
                rateToBase REAL NOT NULL,
                targetBalanceBase REAL NOT NULL,
                differenceBase REAL NOT NULL,
                status TEXT NOT NULL,
                journalEntryId INTEGER,
                reason TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                reversalEntryId INTEGER,
                reversedBy INTEGER,
                reversedAt INTEGER,
                reversalReason TEXT NOT NULL,
                FOREIGN KEY(treasuryAccountId) REFERENCES treasury_accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_treasury_fx_revaluations_treasuryAccountId ON treasury_fx_revaluations(treasuryAccountId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_treasury_fx_revaluations_valuationDate ON treasury_fx_revaluations(valuationDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_treasury_fx_revaluations_status ON treasury_fx_revaluations(status)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_treasury_fx_revaluations_journalEntryId ON treasury_fx_revaluations(journalEntryId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_treasury_fx_revaluations_reversalEntryId ON treasury_fx_revaluations(reversalEntryId)")

        // Operational postings that hit a treasury account must use that treasury's own currency.
        // FX_REVALUATION entries are explicitly exempt because they change only base carrying value,
        // not the original currency quantity.
        db.execSQL("DROP TRIGGER IF EXISTS trg_treasury_currency_consistency")
        db.execSQL("""
            CREATE TRIGGER trg_treasury_currency_consistency
            BEFORE INSERT ON journal_lines
            WHEN EXISTS (
                SELECT 1
                FROM treasury_accounts t
                JOIN journal_entries je ON je.id = NEW.entryId
                WHERE t.accountId = NEW.accountId
                  AND je.sourceType NOT IN ('FX_REVALUATION','FX_REVALUATION_REVERSAL')
                  AND t.currencyCode <> je.currencyCode
            )
            BEGIN
                SELECT RAISE(ABORT, 'TREASURY_CURRENCY_MISMATCH');
            END
        """.trimIndent())
        db.execSQL("DROP TRIGGER IF EXISTS trg_treasury_currency_consistency_update")
        db.execSQL("""
            CREATE TRIGGER trg_treasury_currency_consistency_update
            BEFORE UPDATE OF entryId, accountId ON journal_lines
            WHEN EXISTS (
                SELECT 1
                FROM treasury_accounts t
                JOIN journal_entries je ON je.id = NEW.entryId
                WHERE t.accountId = NEW.accountId
                  AND je.sourceType NOT IN ('FX_REVALUATION','FX_REVALUATION_REVERSAL')
                  AND t.currencyCode <> je.currencyCode
            )
            BEGIN
                SELECT RAISE(ABORT, 'TREASURY_CURRENCY_MISMATCH');
            END
        """.trimIndent())
    }
}

// Phase 14.5.40 (accounting branch only): fixed-asset financial subledger.
// Migration number is PROVISIONAL and must be renumbered by central integration if needed.
val MIGRATION_33_34_FIXED_ASSETS = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS fixed_assets (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                assetNo TEXT NOT NULL,
                maintenanceAssetId INTEGER,
                nameAr TEXT NOT NULL,
                nameEn TEXT NOT NULL,
                category TEXT NOT NULL,
                acquisitionDate INTEGER NOT NULL,
                inServiceDate INTEGER NOT NULL,
                acquisitionCostBase REAL NOT NULL,
                residualValueBase REAL NOT NULL,
                usefulLifeMonths INTEGER NOT NULL,
                depreciationMethod TEXT NOT NULL,
                assetAccountId INTEGER NOT NULL,
                accumulatedDepreciationAccountId INTEGER NOT NULL,
                depreciationExpenseAccountId INTEGER NOT NULL,
                acquisitionMode TEXT NOT NULL,
                acquisitionJournalEntryId INTEGER,
                acquisitionReversalEntryId INTEGER,
                status TEXT NOT NULL,
                disposalDate INTEGER,
                disposalProceedsBase REAL NOT NULL,
                disposalGainLossBase REAL NOT NULL,
                disposalJournalEntryId INTEGER,
                disposalReason TEXT NOT NULL,
                cancellationReason TEXT NOT NULL,
                cancelledBy INTEGER,
                cancelledAt INTEGER,
                notes TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(maintenanceAssetId) REFERENCES assets(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(assetAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(accumulatedDepreciationAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(depreciationExpenseAccountId) REFERENCES accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(acquisitionJournalEntryId) REFERENCES journal_entries(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(acquisitionReversalEntryId) REFERENCES journal_entries(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(disposalJournalEntryId) REFERENCES journal_entries(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fixed_assets_assetNo ON fixed_assets(assetNo)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fixed_assets_maintenanceAssetId ON fixed_assets(maintenanceAssetId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fixed_assets_status ON fixed_assets(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fixed_assets_inServiceDate ON fixed_assets(inServiceDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fixed_assets_assetAccountId ON fixed_assets(assetAccountId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fixed_assets_accumulatedDepreciationAccountId ON fixed_assets(accumulatedDepreciationAccountId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fixed_assets_depreciationExpenseAccountId ON fixed_assets(depreciationExpenseAccountId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fixed_assets_acquisitionJournalEntryId ON fixed_assets(acquisitionJournalEntryId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fixed_assets_acquisitionReversalEntryId ON fixed_assets(acquisitionReversalEntryId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fixed_assets_disposalJournalEntryId ON fixed_assets(disposalJournalEntryId)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS fixed_asset_depreciations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                assetId INTEGER NOT NULL,
                fiscalYear INTEGER NOT NULL,
                periodNo INTEGER NOT NULL,
                depreciationDate INTEGER NOT NULL,
                amountBase REAL NOT NULL,
                status TEXT NOT NULL,
                journalEntryId INTEGER NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                reversalEntryId INTEGER,
                reversalReason TEXT NOT NULL,
                reversedBy INTEGER,
                reversedAt INTEGER,
                FOREIGN KEY(assetId) REFERENCES fixed_assets(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(journalEntryId) REFERENCES journal_entries(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(reversalEntryId) REFERENCES journal_entries(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fixed_asset_depreciations_assetId ON fixed_asset_depreciations(assetId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fixed_asset_depreciations_depreciationDate ON fixed_asset_depreciations(depreciationDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fixed_asset_depreciations_status ON fixed_asset_depreciations(status)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fixed_asset_depreciations_journalEntryId ON fixed_asset_depreciations(journalEntryId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fixed_asset_depreciations_reversalEntryId ON fixed_asset_depreciations(reversalEntryId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fixed_asset_depreciations_assetId_fiscalYear_periodNo ON fixed_asset_depreciations(assetId,fiscalYear,periodNo)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS fixed_asset_disposals (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                assetId INTEGER NOT NULL,
                disposalDate INTEGER NOT NULL,
                proceedsBase REAL NOT NULL,
                treasuryAccountId INTEGER,
                currencyCode TEXT NOT NULL,
                exchangeRate REAL NOT NULL,
                proceedsOriginal REAL NOT NULL,
                acquisitionCostBase REAL NOT NULL,
                accumulatedDepreciationBase REAL NOT NULL,
                carryingValueBase REAL NOT NULL,
                gainLossBase REAL NOT NULL,
                status TEXT NOT NULL,
                journalEntryId INTEGER NOT NULL,
                reason TEXT NOT NULL,
                createdBy INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                reversalEntryId INTEGER,
                reversalReason TEXT NOT NULL,
                reversedBy INTEGER,
                reversedAt INTEGER,
                FOREIGN KEY(assetId) REFERENCES fixed_assets(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(treasuryAccountId) REFERENCES treasury_accounts(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(journalEntryId) REFERENCES journal_entries(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(reversalEntryId) REFERENCES journal_entries(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fixed_asset_disposals_assetId ON fixed_asset_disposals(assetId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fixed_asset_disposals_disposalDate ON fixed_asset_disposals(disposalDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fixed_asset_disposals_status ON fixed_asset_disposals(status)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fixed_asset_disposals_journalEntryId ON fixed_asset_disposals(journalEntryId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_fixed_asset_disposals_reversalEntryId ON fixed_asset_disposals(reversalEntryId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fixed_asset_disposals_treasuryAccountId ON fixed_asset_disposals(treasuryAccountId)")
    }
}
