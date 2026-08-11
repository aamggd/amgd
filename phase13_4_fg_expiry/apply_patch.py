from pathlib import Path
root = Path('FushERP_Mobile_Phase5')

# Version bump
p = root/'app/build.gradle.kts'
s = p.read_text()
s = s.replace('versionCode = 21', 'versionCode = 22')
s = s.replace('versionName = "0.13.3-phase13-credit-policy"', 'versionName = "0.13.4-phase13-fg-expiry"')
p.write_text(s)

# Master data: finished goods default to 730-day shelf life and tracking
p = root/'app/src/main/java/com/fush/erp/domain/MasterDataService.kt'
s = p.read_text()
old = '''        val code = numbering.nextItemCode(category)\n        val row = ItemEntity(\n            code = code,\n            nameAr = nameAr.trim(),\n            nameEn = nameEn.trim(),\n            category = category,\n            baseUnitId = baseUnitId,\n            reorderLevel = reorderLevel,\n            shelfLifeDays = shelfLifeDays,\n            lotTracked = lotTracked,\n            expiryTracked = expiryTracked\n        )'''
new = '''        val code = numbering.nextItemCode(category)\n        val effectiveShelfLife = if (category == "FINISHED_GOOD") (shelfLifeDays ?: 730) else shelfLifeDays\n        val row = ItemEntity(\n            code = code,\n            nameAr = nameAr.trim(),\n            nameEn = nameEn.trim(),\n            category = category,\n            baseUnitId = baseUnitId,\n            reorderLevel = reorderLevel,\n            shelfLifeDays = effectiveShelfLife,\n            lotTracked = if (category == "FINISHED_GOOD") true else lotTracked,\n            expiryTracked = if (category == "FINISHED_GOOD") true else expiryTracked\n        )'''
assert old in s
s = s.replace(old,new)
p.write_text(s)

# Production expiry and product-specific batch prefix
p = root/'app/src/main/java/com/fush/erp/domain/ProductionService.kt'
s = p.read_text()
s = s.replace('''        val manufactureDate = System.currentTimeMillis()\n        val expiryDate = manufactureDate + ((product.shelfLifeDays ?: 0).toLong() * 86_400_000L)\n        val batchNo = nextBatchNo(manufactureDate)''','''        val manufactureDate = System.currentTimeMillis()\n        val shelfLifeDays = product.shelfLifeDays ?: 730\n        require(shelfLifeDays > 0) { "يجب تحديد مدة صلاحية صحيحة للمنتج النهائي قبل إنشاء التشغيلة" }\n        val expiryDate = manufactureDate + (shelfLifeDays.toLong() * 86_400_000L)\n        require(expiryDate > manufactureDate) { "تاريخ انتهاء التشغيلة يجب أن يكون بعد تاريخ الإنتاج" }\n        val batchNo = nextBatchNo(product, manufactureDate)''')
old = '''    private suspend fun nextBatchNo(manufactureDate: Long): String {\n        val cal = Calendar.getInstance().apply { timeInMillis = manufactureDate }\n        val start = Calendar.getInstance().apply {\n            timeInMillis = manufactureDate\n            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)\n        }.timeInMillis\n        val end = start + 86_400_000L\n        val seq = db.productionDao().countBatchesInRange(start, end) + 1\n        val date = SimpleDateFormat("yyMMdd", Locale.US).format(Date(manufactureDate))\n        @Suppress("UNUSED_VARIABLE") val ignored = cal\n        return "F60-$date-${seq.toString().padStart(2, '0')}"\n    }'''
new = '''    private suspend fun nextBatchNo(product: ItemEntity, manufactureDate: Long): String {\n        val start = Calendar.getInstance().apply {\n            timeInMillis = manufactureDate\n            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)\n        }.timeInMillis\n        val end = start + 86_400_000L\n        val seq = db.productionDao().countBatchesInRange(start, end) + 1\n        val date = SimpleDateFormat("yyMMdd", Locale.US).format(Date(manufactureDate))\n        val searchable = (product.code + " " + product.nameAr + " " + product.nameEn).uppercase(Locale.US)\n        val prefix = when {\n            searchable.contains("200") -> "F200"\n            searchable.contains("60") -> "F60"\n            else -> "F${product.id}"\n        }\n        return "$prefix-$date-${seq.toString().padStart(2, '0')}"\n    }'''
assert old in s
s = s.replace(old,new)
p.write_text(s)

# Startup legacy data repair
p = root/'app/src/main/java/com/fush/erp/data/AppContainer.kt'
s = p.read_text()
s = s.replace('''            seedPlanningExchangeRate()\n            try { seedInternalControlDefaults() } catch (_: Exception) { }''','''            seedPlanningExchangeRate()\n            repairLegacyFinishedGoods()\n            try { seedInternalControlDefaults() } catch (_: Exception) { }''')
anchor = '''\n\n    private suspend fun seedGeographyPolicies() {'''
repair = r'''

    private fun repairLegacyFinishedGoods() {
        val sql = db.openHelper.writableDatabase
        val twoYearsMs = 730L * 86_400_000L

        sql.execSQL("""
            UPDATE items
            SET shelfLifeDays = 730, lotTracked = 1, expiryTracked = 1
            WHERE category = 'FINISHED_GOOD' AND (shelfLifeDays IS NULL OR shelfLifeDays <= 0)
        """.trimIndent())

        sql.execSQL("""
            UPDATE production_batches
            SET expiryDate = manufactureDate + $twoYearsMs
            WHERE expiryDate <= manufactureDate
              AND orderId IN (
                  SELECT po.id FROM production_orders po
                  JOIN items i ON i.id = po.productItemId
                  WHERE i.category = 'FINISHED_GOOD'
              )
        """.trimIndent())

        sql.execSQL("""
            UPDATE production_batches
            SET batchNo = 'F200-' || substr(batchNo, 5)
            WHERE batchNo LIKE 'F60-%'
              AND orderId IN (
                  SELECT po.id FROM production_orders po
                  JOIN items i ON i.id = po.productItemId
                  WHERE i.category = 'FINISHED_GOOD'
                    AND (upper(i.code) LIKE '%200%' OR upper(i.nameAr) LIKE '%200%' OR upper(i.nameEn) LIKE '%200%')
              )
              AND NOT EXISTS (
                  SELECT 1 FROM production_batches other
                  WHERE other.batchNo = 'F200-' || substr(production_batches.batchNo, 5)
                    AND other.id <> production_batches.id
              )
        """.trimIndent())

        sql.execSQL("""
            UPDATE stock_movements
            SET expiryDate = (
                    SELECT pb.expiryDate FROM production_batches pb
                    WHERE pb.id = stock_movements.referenceId
                ),
                lotNo = (
                    SELECT pb.batchNo FROM production_batches pb
                    WHERE pb.id = stock_movements.referenceId
                )
            WHERE referenceType = 'PRODUCTION_BATCH'
              AND EXISTS (
                  SELECT 1 FROM production_batches pb
                  WHERE pb.id = stock_movements.referenceId
              )
        """.trimIndent())
    }
'''
assert anchor in s
s = s.replace(anchor, repair+anchor)
p.write_text(s)

# Inventory UI: show total vs sellable/expired/controlled on balances
p = root/'app/src/main/java/com/fush/erp/ui/screens/AdvancedInventoryScreens.kt'
s = p.read_text()
s = s.replace('else -> InventoryBalances(balances)', 'else -> InventoryBalances(balances, lots)')
old = '''@Composable\nprivate fun InventoryBalances(rows: List<StockBalanceRow>) {\n    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {\n        item { Text("الأرصدة الحالية", style = MaterialTheme.typography.titleLarge) }\n        items(rows) { row ->\n            ElevatedCard(Modifier.fillMaxWidth()) {\n                ListItem(\n                    headlineContent = { Text(row.nameAr) },\n                    supportingContent = { Text(row.code) },\n                    trailingContent = { Text("${fmtQty(row.quantityBase)} ${row.baseUnitName}") }\n                )\n            }\n        }\n    }\n}'''
new = '''@Composable\nprivate fun InventoryBalances(rows: List<StockBalanceRow>, lots: List<InventoryLotAlertRow>) {\n    val now = System.currentTimeMillis()\n    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {\n        item {\n            Text("الأرصدة الحالية", style = MaterialTheme.typography.titleLarge)\n            Text("للمنتجات ذات التشغيلات يعرض النظام الرصيد الكلي والمتاح للبيع والمنتهي/المحجور.", style = MaterialTheme.typography.bodySmall)\n        }\n        items(rows) { row ->\n            val itemLots = lots.filter { it.itemId == row.itemId && it.quantityBase > 0.000000001 }\n            val sellable = itemLots.filter { (it.expiryDate == null || it.expiryDate >= now) && it.controlStatus == "ACCEPTED" }.sumOf { it.quantityBase }\n            val expired = itemLots.filter { it.expiryDate != null && it.expiryDate < now }.sumOf { it.quantityBase }\n            val controlled = itemLots.filter { it.controlStatus != "ACCEPTED" }.sumOf { it.quantityBase }\n            ElevatedCard(Modifier.fillMaxWidth()) {\n                ListItem(\n                    headlineContent = { Text(row.nameAr) },\n                    supportingContent = {\n                        Column {\n                            Text(row.code)\n                            if (itemLots.isNotEmpty()) {\n                                Text("الكلي ${fmtQty(row.quantityBase)} • متاح للبيع ${fmtQty(sellable)} • منتهي ${fmtQty(expired)} • محجور/موقوف ${fmtQty(controlled)}")\n                            }\n                        }\n                    },\n                    trailingContent = { Text("${fmtQty(row.quantityBase)} ${row.baseUnitName}") }\n                )\n            }\n        }\n    }\n}'''
assert old in s
s = s.replace(old,new)
p.write_text(s)

print('PHASE13_4_PATCH_OK')
