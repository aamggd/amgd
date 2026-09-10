package com.fush.erp.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.fush.erp.data.entity.*
import com.fush.erp.domain.InventoryService
import com.fush.erp.domain.PurchaseService
import com.fush.erp.domain.ProductionService
import com.fush.erp.domain.SalesService
import com.fush.erp.domain.MaintenanceService
import com.fush.erp.domain.EmployeeService
import com.fush.erp.domain.SalesRepresentativeService
import com.fush.erp.domain.AccountingService
import com.fush.erp.domain.GeographyService
import com.fush.erp.domain.AdvancedInventoryService
import com.fush.erp.domain.MasterDataService
import com.fush.erp.domain.RiskControlService
import com.fush.erp.domain.PlanningService
import com.fush.erp.domain.PermissionCatalog
import com.fush.erp.domain.SecurityService
import com.fush.erp.domain.FixedAssetService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppContainer(context: Context) {
    val sessionSettings = SessionSettingsStore(context.applicationContext)
    val db: FushDatabase = Room.databaseBuilder(
        context.applicationContext,
        FushDatabase::class.java,
        "fush_erp.db"
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33_SECURITY, MIGRATION_33_34_FIXED_ASSETS, MIGRATION_34_35_ACCOUNTING_P1).build()

    val purchaseService = PurchaseService(db)
    val inventoryService = InventoryService(db)
    val productionService = ProductionService(db)
    val salesService = SalesService(db)
    val maintenanceService = MaintenanceService(db)
    val employeeService = EmployeeService(db)
    val salesRepresentativeService = SalesRepresentativeService(db)
    val accountingService = AccountingService(db)
    val geographyService = GeographyService(db)
    val advancedInventoryService = AdvancedInventoryService(db)
    val masterDataService = MasterDataService(db)
    val riskControlService = RiskControlService(db)
    val planningService = PlanningService(db)
    val securityService = SecurityService(db)
    val fixedAssetService = FixedAssetService(db)

    private fun ensureImmutableAuditLog() {
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_audit_events_no_update
            BEFORE UPDATE ON audit_events
            BEGIN
                SELECT RAISE(ABORT, 'audit_events are immutable');
            END
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS trg_audit_events_no_delete
            BEFORE DELETE ON audit_events
            BEGIN
                SELECT RAISE(ABORT, 'audit_events are immutable');
            END
            """.trimIndent()
        )
    }

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        ensureImmutableAuditLog()
        db.withTransaction {
            securityService.seedDefaults()
            db.currencyDao().insertDefaultsIgnore(
                listOf(
                    CurrencyEntity("YER_NEW", "ريال يمني - طبعة جديدة", "Yemeni Rial - New", "ر.ي", 2, isBase = true),
                    CurrencyEntity("YER_OLD", "ريال يمني - طبعة قديمة", "Yemeni Rial - Old", "ر.ي قديم", 2),
                    CurrencyEntity("USD", "دولار أمريكي", "US Dollar", "$", 2)
                )
            )

            db.accountDao().insertAll(
                listOf(
                    AccountEntity(code="1000", nameAr="الأصول", nameEn="Assets", type="ASSET", isPosting=false),
                    AccountEntity(code="1100", nameAr="الصندوق", nameEn="Cash", type="ASSET"),
                    AccountEntity(code="1200", nameAr="المخزون", nameEn="Inventory", type="ASSET"),
                    AccountEntity(code="1210", nameAr="إنتاج تحت التشغيل", nameEn="Work in Process", type="ASSET"),
                    AccountEntity(code="1300", nameAr="العملاء", nameEn="Accounts Receivable", type="ASSET"),
                    AccountEntity(code="2000", nameAr="الالتزامات", nameEn="Liabilities", type="LIABILITY", isPosting=false),
                    AccountEntity(code="2100", nameAr="الموردون", nameEn="Accounts Payable", type="LIABILITY"),
                    AccountEntity(code="2200", nameAr="أجور إنتاج مستحقة", nameEn="Accrued Production Labor", type="LIABILITY"),
                    AccountEntity(code="2300", nameAr="عمولات بيع مستحقة", nameEn="Sales Commissions Payable", type="LIABILITY"),
                    AccountEntity(code="3000", nameAr="حقوق الملكية", nameEn="Equity", type="EQUITY", isPosting=false),
                    AccountEntity(code="3100", nameAr="الرصيد الافتتاحي", nameEn="Opening Balance", type="EQUITY"),
                    AccountEntity(code="4000", nameAr="المبيعات", nameEn="Sales", type="REVENUE"),
                    AccountEntity(code="4100", nameAr="مردودات المبيعات", nameEn="Sales Returns", type="REVENUE"),
                    AccountEntity(code="5000", nameAr="تكلفة المبيعات", nameEn="Cost of Goods Sold", type="EXPENSE"),
                    AccountEntity(code="6100", nameAr="مصروف الإيجار", nameEn="Rent Expense", type="EXPENSE"),
                    AccountEntity(code="6200", nameAr="مصروف الكهرباء والماء والغاز", nameEn="Utilities Expense", type="EXPENSE"),
                    AccountEntity(code="6300", nameAr="خسائر إنتاج وجودة", nameEn="Production and Quality Loss", type="EXPENSE"),
                    AccountEntity(code="6400", nameAr="مصروف عمولات البيع", nameEn="Sales Commission Expense", type="EXPENSE"),
                    AccountEntity(code="1150", nameAr="الحسابات البنكية", nameEn="Bank Accounts", type="ASSET"),
                    AccountEntity(code="1500", nameAr="الأصول الثابتة", nameEn="Fixed Assets", type="ASSET"),
                    AccountEntity(code="1590", nameAr="مجمع الإهلاك", nameEn="Accumulated Depreciation", type="ASSET"),
                    AccountEntity(code="2400", nameAr="التزامات أخرى", nameEn="Other Payables", type="LIABILITY"),
                    AccountEntity(code="3200", nameAr="مسحوبات المالك", nameEn="Owner Drawings", type="EQUITY"),
                    AccountEntity(code="3300", nameAr="أرباح محتجزة", nameEn="Retained Earnings", type="EQUITY"),
                    AccountEntity(code="4200", nameAr="إيرادات أخرى", nameEn="Other Income", type="REVENUE"),
                    AccountEntity(code="4250", nameAr="أرباح فروق العملة", nameEn="Foreign Exchange Gain", type="REVENUE"),
                    AccountEntity(code="6500", nameAr="مصروفات عامة وإدارية", nameEn="General and Administrative Expense", type="EXPENSE"),
                    AccountEntity(code="6600", nameAr="مصروف صيانة", nameEn="Maintenance Expense", type="EXPENSE"),
                    AccountEntity(code="6700", nameAr="مصروفات ورسوم بنكية", nameEn="Bank Charges", type="EXPENSE"),
                    AccountEntity(code="6750", nameAr="خسائر فروق العملة", nameEn="Foreign Exchange Loss", type="EXPENSE"),
                    AccountEntity(code="6800", nameAr="مصروف إهلاك", nameEn="Depreciation Expense", type="EXPENSE"),
                    AccountEntity(code="6410", nameAr="مصاريف بيع وتوزيع", nameEn="Selling and Distribution Expense", type="EXPENSE"),
                    AccountEntity(code="6420", nameAr="مصاريف مشتريات", nameEn="Purchasing Expense", type="EXPENSE"),
                    AccountEntity(code="6430", nameAr="مصاريف نقل ومواصلات", nameEn="Transport and Travel Expense", type="EXPENSE"),
                    AccountEntity(code="6440", nameAr="مصاريف تسويق", nameEn="Marketing Expense", type="EXPENSE"),
                    AccountEntity(code="6450", nameAr="مصاريف تشغيل", nameEn="Operating Expense", type="EXPENSE"),
                    AccountEntity(code="6900", nameAr="مصاريف أخرى", nameEn="Other Expense", type="EXPENSE"),
                    AccountEntity(code="6950", nameAr="فروقات الصندوق", nameEn="Cash Over and Short", type="EXPENSE")
                )
            )

            db.unitDao().insertAll(
                listOf(
                    UnitEntity(code="PCS", nameAr="قطعة", nameEn="Piece"),
                    UnitEntity(code="KG", nameAr="كجم", nameEn="Kilogram"),
                    UnitEntity(code="L", nameAr="لتر", nameEn="Liter"),
                    UnitEntity(code="PACK", nameAr="باكيت", nameEn="Pack"),
                    UnitEntity(code="CTN", nameAr="كرتون", nameEn="Carton")
                )
            )

            db.warehouseDao().insertAll(
                listOf(
                    WarehouseEntity(code="RM", nameAr="مخزن المواد الخام", nameEn="Raw Materials"),
                    WarehouseEntity(code="FG", nameAr="مخزن المنتج النهائي", nameEn="Finished Goods"),
                    WarehouseEntity(code="RM-QC", nameAr="حجر المواد الخام", nameEn="Raw Materials Quarantine"),
                    WarehouseEntity(code="FG-QC", nameAr="حجر المنتج النهائي", nameEn="Finished Goods Quarantine"),
                    WarehouseEntity(code="RET", nameAr="مخزن المرتجعات", nameEn="Returns Warehouse")
                )
            )

            seedFactoryItemsAndConversions()
            seedDefaultRecipe()
            seedSalesPrices()
            seedMaintenanceAssetsAndPlans()
            seedTrainingCourses()
            seedTreasuryAccounts()
            seedGeographyPolicies()
            seedPlanningExchangeRate()
            repairLegacyFinishedGoods()
            try { seedInternalControlDefaults() } catch (_: Exception) { }
        }
    }


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


    private suspend fun seedGeographyPolicies() {
        db.geographyDao().upsertProvincePolicies(
            listOf(
                ProvincePolicyEntity(
                    code = "TAIZ",
                    nameAr = "تعز",
                    currencyCode = "YER_NEW",
                    defaultTransportPerCartonBase = 0.0,
                    notes = "سعر القناة؛ العمولة تشمل النقل داخل المحافظة والتوصيل والتحصيل."
                ),
                ProvincePolicyEntity(
                    code = "ADEN",
                    nameAr = "عدن",
                    currencyCode = "YER_NEW",
                    defaultTransportPerCartonBase = 10_000.0,
                    notes = "إضافة 10,000 ريال جديد نقل لكل كرتون فوق سعر القناة."
                ),
                ProvincePolicyEntity(
                    code = "SANAA",
                    nameAr = "صنعاء ومناطق الطبعة القديمة",
                    currencyCode = "YER_OLD",
                    requiresDailyFx = true,
                    requiresActualTransport = true,
                    requiresFeesAndCustoms = true,
                    notes = "تحويل يومي عبر سعر الدولار ثم النقل والرسوم والجمارك وهامش المخاطر."
                ),
                ProvincePolicyEntity(
                    code = "OTHER",
                    nameAr = "بقية المحافظات",
                    currencyCode = "YER_NEW",
                    requiresActualTransport = true,
                    requiresFeesAndCustoms = true,
                    notes = "سعر المصنع + النقل الفعلي + أي رسوم + هامش المخاطر."
                )
            )
        )
    }

    private suspend fun seedPlanningExchangeRate() {
        val studyDate = 1_785_888_000_000L // 2026-08-05 UTC
        if (db.currencyDao().latestRateAt("USD", studyDate) == null) {
            db.currencyDao().upsertRate(
                ExchangeRateEntity(
                    currencyCode = "USD",
                    effectiveAt = studyDate,
                    rateToBase = 1554.62,
                    sourceNote = "سعر تخطيطي من دراسة Fush بتاريخ 2026-08-05"
                )
            )
        }
    }

    private suspend fun seedTreasuryAccounts() {
        val cash = requireNotNull(db.accountDao().byCode("1100"))
        db.accountingDao().insertTreasuryIgnore(
            TreasuryAccountEntity(
                code = "CASH-MAIN",
                nameAr = "الصندوق الرئيسي",
                kind = "CASH",
                accountId = cash.id,
                currencyCode = "YER_NEW",
                createdBy = 1L
            )
        )
    }

    private suspend fun seedFactoryItemsAndConversions() {
        val pcs = requireNotNull(db.unitDao().byCode("PCS"))
        val kg = requireNotNull(db.unitDao().byCode("KG"))
        val liter = requireNotNull(db.unitDao().byCode("L"))
        val pack = requireNotNull(db.unitDao().byCode("PACK"))
        val carton = requireNotNull(db.unitDao().byCode("CTN"))

        suspend fun ensureItem(code: String, ar: String, en: String, category: String, unitId: Long, reorder: Double = 0.0, shelf: Int? = null, lot: Boolean = false, expiry: Boolean = false): ItemEntity {
            db.itemDao().byCode(code)?.let { return it }
            val id = db.itemDao().insert(ItemEntity(code=code, nameAr=ar, nameEn=en, category=category, baseUnitId=unitId, reorderLevel=reorder, shelfLifeDays=shelf, lotTracked=lot, expiryTracked=expiry))
            return requireNotNull(db.itemDao().byCode(code)).copy(id = id)
        }

        suspend fun conversion(item: ItemEntity, unitId: Long, factor: Double, purchase: Boolean = true, sale: Boolean = false) {
            db.itemUnitConversionDao().upsert(ItemUnitConversionEntity(itemId=item.id, unitId=unitId, factorToBase=factor, allowPurchase=purchase, allowSale=sale))
        }

        val boric = ensureItem("RM-BORIC", "حمض البوريك", "Boric Acid", "RAW_MATERIAL", kg.id, reorder=25.0, lot=true, expiry=true)
        conversion(boric, kg.id, 1.0)

        val potato = ensureItem("RM-POTATO", "البطاطس", "Potatoes", "RAW_MATERIAL", kg.id, reorder=75.0)
        conversion(potato, kg.id, 1.0)

        val egg = ensureItem("RM-EGG", "البيض", "Eggs", "RAW_MATERIAL", pcs.id, reorder=375.0, expiry=true)
        conversion(egg, pcs.id, 1.0)
        conversion(egg, carton.id, 360.0)

        val water = ensureItem("RM-WATER", "الماء", "Water", "RAW_MATERIAL", liter.id)
        conversion(water, liter.id, 1.0)

        val dye = ensureItem("RM-DYE", "الصبغة", "Dye", "RAW_MATERIAL", liter.id, reorder=5.0)
        conversion(dye, liter.id, 1.0)

        val bottle = ensureItem("PK-BOTTLE-60", "عبوة صغيرة 60 مل", "60ml Bottle", "PACKAGING", pcs.id, reorder=1800.0)
        conversion(bottle, pcs.id, 1.0)

        val bottleLabel = ensureItem("PK-LABEL-60", "ملصق العبوة الصغيرة", "60ml Bottle Label", "PACKAGING", pcs.id, reorder=1800.0)
        conversion(bottleLabel, pcs.id, 1.0)

        val packMaterial = ensureItem("PK-PACK", "باكيت التغليف", "Packaging Pack", "PACKAGING", pcs.id, reorder=75.0)
        conversion(packMaterial, pcs.id, 1.0)

        val packLabel = ensureItem("PK-PACK-LABEL", "ملصق الباكيت", "Pack Label", "PACKAGING", pcs.id, reorder=75.0)
        conversion(packLabel, pcs.id, 1.0)

        val outerCarton = ensureItem("PK-OUTER-CTN", "كرتون خارجي", "Outer Carton", "PACKAGING", pcs.id, reorder=4.0)
        conversion(outerCarton, pcs.id, 1.0)

        val product = ensureItem("FG-FUSH-60", "Fush 60 مل", "Fush 60ml", "FINISHED_GOOD", pcs.id, shelf=730, lot=true, expiry=true)
        conversion(product, pcs.id, 1.0, purchase=false, sale=true)
        conversion(product, pack.id, 24.0, purchase=false, sale=true)
        conversion(product, carton.id, 480.0, purchase=false, sale=true)
    }

    private suspend fun seedSalesPrices() {
        if (db.salesDao().priceCount() > 0) return
        val product = requireNotNull(db.itemDao().byCode("FG-FUSH-60"))
        val now = System.currentTimeMillis()
        val rows = listOf(
            SalesPriceEntity(itemId=product.id, channel="DIRECT", province="تعز", currencyCode="YER_NEW", baseUnitPriceOriginal=1000.0, effectiveFrom=now, note="بيع مباشر للمستهلك"),
            SalesPriceEntity(itemId=product.id, channel="RETAIL", province="تعز", currencyCode="YER_NEW", baseUnitPriceOriginal=900.0, effectiveFrom=now, note="متجر/تجزئة"),
            SalesPriceEntity(itemId=product.id, channel="DISTRIBUTOR_CASH", province="تعز", currencyCode="YER_NEW", baseUnitPriceOriginal=800.0, effectiveFrom=now, note="موزع نقدي"),
            SalesPriceEntity(itemId=product.id, channel="DISTRIBUTOR_CREDIT", province="تعز", currencyCode="YER_NEW", baseUnitPriceOriginal=850.0, effectiveFrom=now, note="موزع آجل حتى 30 يوماً")
        )
        rows.forEach { db.salesDao().insertPrice(it) }
    }

    private suspend fun seedDefaultRecipe() {
        if (db.recipeDao().count() > 0) return
        val product = requireNotNull(db.itemDao().byCode("FG-FUSH-60"))
        val components = listOf(
            "RM-BORIC" to 2.5,
            "RM-POTATO" to 15.0,
            "RM-EGG" to 75.0,
            "RM-WATER" to 1.0,
            "RM-DYE" to 1.0,
            "PK-BOTTLE-60" to 360.0,
            "PK-LABEL-60" to 360.0,
            "PK-PACK" to 15.0,
            "PK-PACK-LABEL" to 15.0,
            "PK-OUTER-CTN" to 0.75
        )
        val recipeId = db.recipeDao().insertRecipe(
            RecipeEntity(
                code = "BOM-FUSH-60",
                productItemId = product.id,
                versionNo = 1,
                effectiveFrom = System.currentTimeMillis(),
                targetOutputQtyBase = 360.0,
                status = "ACTIVE",
                notes = "الوصفة التشغيلية الأساسية من دراسة Fush؛ أي تعديل لاحق ينشأ كإصدار جديد."
            )
        )
        db.recipeDao().insertComponents(
            components.mapIndexed { index, (code, qty) ->
                val item = requireNotNull(db.itemDao().byCode(code)) { "الصنف $code غير موجود" }
                RecipeComponentEntity(
                    recipeId = recipeId,
                    itemId = item.id,
                    quantityBase = qty,
                    stage = when (code) {
                        "PK-BOTTLE-60", "PK-LABEL-60", "PK-PACK", "PK-PACK-LABEL", "PK-OUTER-CTN" -> "FILLING"
                        else -> "PREPARATION"
                    },
                    sequenceNo = index + 1
                )
            }
        )
    }
    private suspend fun seedMaintenanceAssetsAndPlans() {
        if (db.maintenanceDao().assetByCode("FILL-01") != null) return
        val now = System.currentTimeMillis()
        suspend fun addAsset(row: AssetEntity): Long = db.maintenanceDao().insertAsset(row)
        suspend fun addPlan(assetId: Long, name: String, frequency: String, days: Int?, checklist: String) {
            db.maintenanceDao().insertPlan(MaintenancePlanEntity(
                assetId = assetId,
                nameAr = name,
                frequencyType = frequency,
                intervalDays = days,
                checklist = checklist,
                nextDueAt = days?.let { now + it.toLong() * 86_400_000L }
            ))
        }

        val filler = addAsset(AssetEntity(code="FILL-01", nameAr="آلة التعبئة", assetType="FILLING_MACHINE", location="غرفة التعبئة", criticality="CRITICAL", inspectionDueAt=now + 30L*86_400_000L))
        val burner1 = addAsset(AssetEntity(code="BURNER-01", nameAr="الشعلة 1", assetType="BURNER", location="غرفة الخلط", criticality="HIGH", inspectionDueAt=now + 30L*86_400_000L))
        val burner2 = addAsset(AssetEntity(code="BURNER-02", nameAr="الشعلة 2", assetType="BURNER", location="غرفة الخلط", criticality="HIGH", inspectionDueAt=now + 30L*86_400_000L))
        val scale = addAsset(AssetEntity(code="SCALE-01", nameAr="ميزان الإنتاج", assetType="MEASURING_TOOL", location="غرفة التحضير", criticality="HIGH", calibrationRequired=true, inspectionDueAt=now + 30L*86_400_000L, calibrationDueAt=now + 30L*86_400_000L))
        val extinguisher = addAsset(AssetEntity(code="FIRE-01", nameAr="طفاية الحريق الرئيسية", assetType="SAFETY_EQUIPMENT", location="منطقة الإنتاج", criticality="CRITICAL", inspectionDueAt=now + 30L*86_400_000L))

        addPlan(filler, "فحص ما قبل تشغيل آلة التعبئة", "BEFORE_EACH_RUN", null, "فحص بصري، نظافة، كهرباء، اختبار التشغيل")
        addPlan(filler, "تنظيف وفحص بعد الدفعة", "AFTER_EACH_BATCH", null, "تنظيف، تسجيل التوقفات، فحص الأجزاء الملامسة للمنتج")
        addPlan(filler, "فحص الخراطيم والوصلات ودقة التعبئة", "WEEKLY", 7, "الخراطيم، الوصلات، دقة التعبئة، تنظيف عميق")
        addPlan(filler, "الصيانة الشهرية لآلة التعبئة", "MONTHLY", 30, "صيانة آلة التعبئة والكهرباء")
        addPlan(filler, "الصيانة الشاملة وتحليل الأعطال", "QUARTERLY", 90, "تحليل الأعطال والحاجة للاستبدال")
        addPlan(burner1, "فحص الشعلة والتسرب", "MONTHLY", 30, "التوصيلات، منظم الغاز، اختبار التسرب")
        addPlan(burner2, "فحص الشعلة والتسرب", "MONTHLY", 30, "التوصيلات، منظم الغاز، اختبار التسرب")
        addPlan(scale, "تحقق ومعايرة الميزان", "MONTHLY", 30, "التحقق من الدقة وتسجيل نتيجة المعايرة")
        addPlan(extinguisher, "فحص طفاية الحريق", "MONTHLY", 30, "الموقع، الختم، الضغط، تاريخ الصلاحية")
    }

    private suspend fun seedTrainingCourses() {
        if (db.employeeDao().courseCount() > 0) return
        val rows = listOf(
            TrainingCourseEntity(code="TRN-SAFE-GEN", titleAr="السلامة العامة وبطاقات المخاطر", category="SAFETY", description="التعريف بالمخاطر وبطاقات السلامة ومعدات الوقاية", requiresPracticalObservation=false),
            TrainingCourseEntity(code="TRN-PREP", titleAr="الوزن والتحضير ومنع التلوث", category="PROCESS", description="الوزن والتحضير ومنع التلوث قبل العمل المستقل", requiresPracticalObservation=true),
            TrainingCourseEntity(code="TRN-FILL", titleAr="تشغيل وتنظيف آلة التعبئة", category="EQUIPMENT", assetType="FILLING_MACHINE", description="تشغيل آلة التعبئة والتنظيف وفحص ما قبل التشغيل", requiresPracticalObservation=true),
            TrainingCourseEntity(code="TRN-BURNER", titleAr="تشغيل الشعلة وفحص الغاز", category="EQUIPMENT", assetType="BURNER", description="تشغيل الشعلة وفحص التوصيلات والتسرب الآمن", requiresPracticalObservation=true),
            TrainingCourseEntity(code="TRN-MEASURE", titleAr="استخدام أدوات القياس والميزان", category="EQUIPMENT", assetType="MEASURING_TOOL", description="الاستخدام الصحيح والتحقق من الدقة والمعايرة", requiresPracticalObservation=true),
            TrainingCourseEntity(code="TRN-FIRE-SPILL", titleAr="الانسكاب والحريق والاستجابة للطوارئ", category="SAFETY", assetType="SAFETY_EQUIPMENT", description="الاستجابة للانسكاب والحريق والتصرف الآمن", requiresPracticalObservation=true),
            TrainingCourseEntity(code="TRN-QUALITY", titleAr="الجودة وعدم المطابقة وCAPA", category="QUALITY", description="الفحص وعدم المطابقة والإجراء التصحيحي والوقائي", requiresPracticalObservation=true)
        )
        rows.forEach { db.employeeDao().insertCourse(it) }
    }


    private suspend fun seedInternalControlDefaults() {
        db.riskControlDao().insertSegregationRules(
            listOf(
                SegregationRuleEntity(ruleCode="SOD-001", actionKey="PURCHASE_APPROVAL", initiatorRole="PURCHASING", approverRole="ADMIN", description="منشئ فاتورة/طلب الشراء لا يعتمد نفس المستند.", requireDifferentUser=true),
                SegregationRuleEntity(ruleCode="SOD-002", actionKey="PAYMENT_APPROVAL", initiatorRole="ACCOUNTING", approverRole="ADMIN", description="منشئ الصرف أو الدفع لا يعتمد نفس الصرف.", requireDifferentUser=true),
                SegregationRuleEntity(ruleCode="SOD-003", actionKey="INVENTORY_ADJUSTMENT", initiatorRole="INVENTORY", approverRole="ADMIN", description="منفذ الجرد أو التسوية لا يعتمد فرق الجرد بنفسه.", requireDifferentUser=true),
                SegregationRuleEntity(ruleCode="SOD-004", actionKey="CREDIT_OVERRIDE", initiatorRole="SALES", approverRole="ADMIN", description="استثناء الائتمان أو تجاوز الحد يحتاج اعتماد مستخدم مختلف.", requireDifferentUser=true),
                SegregationRuleEntity(ruleCode="SOD-005", actionKey="CHANGE_REQUEST", initiatorRole="ANY", approverRole="ADMIN", description="مقدم طلب التغيير لا يعتمد طلبه بنفسه.", requireDifferentUser=true),
                SegregationRuleEntity(ruleCode="SOD-006", actionKey="CONTROL_EXCEPTION_CLOSE", initiatorRole="ANY", approverRole="ADMIN", description="من فتح الاستثناء الرقابي لا يعتمد إغلاقه.", requireDifferentUser=true)
            )
        )
        db.riskControlDao().insertControls(
            listOf(
                InternalControlEntity(controlCode="CTL-BASE-001", title="مطابقة الصندوق", controlType="DETECTIVE", frequency="DAILY", ownerRole="ACCOUNTING", designDescription="مطابقة الرصيد الفعلي مع رصيد النظام يومياً.", evidenceRequired="محضر مطابقة/كشف الصندوق"),
                InternalControlEntity(controlCode="CTL-BASE-002", title="مراجعة فروق الجرد", controlType="DETECTIVE", frequency="EACH_COUNT", ownerRole="INVENTORY", designDescription="مراجعة فروق الجرد قبل الترحيل واعتمادها من مستخدم مختلف.", evidenceRequired="تقرير الجرد والتسوية"),
                InternalControlEntity(controlCode="CTL-BASE-003", title="مراجعة الذمم المتأخرة", controlType="PREVENTIVE", frequency="WEEKLY", ownerRole="SALES", designDescription="مراجعة العملاء المتأخرين وإيقاف الآجل عند تجاوز السياسة.", evidenceRequired="كشف أعمار الذمم"),
                InternalControlEntity(controlCode="CTL-BASE-004", title="مراجعة الصلاحية والحجر", controlType="PREVENTIVE", frequency="WEEKLY", ownerRole="QUALITY", designDescription="مراجعة المواد والمنتجات القريبة من الانتهاء أو المحجورة.", evidenceRequired="تقرير الصلاحية والحجر")
            )
        )
    }

}
