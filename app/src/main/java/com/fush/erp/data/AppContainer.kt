package com.fush.erp.data

import android.content.Context
import com.fush.erp.cloud.CloudSyncRepository
import com.fush.erp.cloud.FxRateRemoteService
import androidx.room.Room
import androidx.room.withTransaction
import com.fush.erp.data.entity.*
import com.fush.erp.domain.InventoryService
import com.fush.erp.domain.PurchaseService
import com.fush.erp.domain.ProductionService
import com.fush.erp.domain.SalesService
import com.fush.erp.domain.AdditionalChargesService
import com.fush.erp.domain.MaintenanceService
import com.fush.erp.domain.EmployeeService
import com.fush.erp.domain.SalesRepresentativeService
import com.fush.erp.domain.AccountingService
import com.fush.erp.domain.GeographyService
import com.fush.erp.domain.FxRateService
import com.fush.erp.domain.AdvancedInventoryService
import com.fush.erp.domain.MasterDataService
import com.fush.erp.domain.RiskControlService
import com.fush.erp.domain.PlanningService
import com.fush.erp.domain.PermissionCatalog
import com.fush.erp.domain.SecurityService
import com.fush.erp.domain.FixedAssetService
import com.fush.erp.domain.SupportService
import com.fush.erp.domain.VendorInstallationIdentityStore
import com.fush.erp.domain.VendorSupportProvisioningManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val sessionSettings = SessionSettingsStore(context.applicationContext)
    val db: FushDatabase = Room.databaseBuilder(
        context.applicationContext,
        FushDatabase::class.java,
        "fush_erp.db"
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33_SECURITY, MIGRATION_33_34_FIXED_ASSETS, MIGRATION_34_35_ACCOUNTING_P1, MIGRATION_35_36_ACCOUNTING_PRECISION, MIGRATION_36_37_JOURNAL_LINE_SEMANTICS, MIGRATION_37_38_INVENTORY_COST_LAYERS, MIGRATION_38_39_CUSTOMER_SETTLEMENT_DISCOUNT, MIGRATION_39_40_SALES_FREE_QUANTITY, MIGRATION_40_41_SUPPORT_MAINTENANCE_MODE, MIGRATION_41_42_SUPPORT_JOURNAL_PROVENANCE, MIGRATION_42_43_VENDOR_SUPPORT_PROVISIONING, MIGRATION_43_44_VENDOR_SUPPORT_LIFECYCLE, MIGRATION_44_45_FOREIGN_KEY_INDEX_HARDENING, MIGRATION_45_46_PURCHASE_INVOICE_ADJUSTMENTS, MIGRATION_46_47_SALES_ADDITIONAL_CHARGES, MIGRATION_47_48_SHIPMENT_COST_ALLOCATION, MIGRATION_48_49_SHIPMENT_SALES_LINE_LINK, MIGRATION_49_50_LOCAL_FX_ENGINE, MIGRATION_50_51_GEOGRAPHY_HIERARCHY, MIGRATION_51_52_MULTI_CURRENCY_TREASURY, MIGRATION_52_53_FUSH_AI_DRAFT_ACTIONS, MIGRATION_53_54_COMMERCIAL_TENANT_BINDING).build()

    val cloudSyncRepository = CloudSyncRepository(context.applicationContext, db)

    val purchaseService = PurchaseService(db, cloudSyncRepository)
    val inventoryService = InventoryService(db)
    val productionService = ProductionService(db)
    val salesService = SalesService(db, cloudSyncRepository)
    val additionalChargesService = AdditionalChargesService(db)
    val maintenanceService = MaintenanceService(db)
    val employeeService = EmployeeService(db)
    val salesRepresentativeService = SalesRepresentativeService(db)
    val accountingService = AccountingService(db)
    val geographyService = GeographyService(db)
    val fxRateService = FxRateService(db, FxRateRemoteService())
    val advancedInventoryService = AdvancedInventoryService(db)
    val masterDataService = MasterDataService(db)
    val riskControlService = RiskControlService(db)
    val planningService = PlanningService(db)
    val vendorInstallationIdentityStore = VendorInstallationIdentityStore(context.applicationContext)
    val vendorSupportProvisioningManager = VendorSupportProvisioningManager(db, vendorInstallationIdentityStore)
    val securityService = SecurityService(db, vendorSupportProvisioningManager)
    val fixedAssetService = FixedAssetService(db)
    val supportService = SupportService(db, securityService, cloudSyncRepository)

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
        installSupportImmutableGuards(sqlite)
    }

    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        ensureImmutableAuditLog()
        db.withTransaction {
            securityService.seedDefaults()
            YemenGeographyHierarchy.ensureOfficialSeed(appContext, db)
            db.currencyDao().insertDefaultsIgnore(
                listOf(
                    CurrencyEntity("YER_NEW", "ريال يمني - طبعة جديدة", "Yemeni Rial - New", "ر.ي", 2, isBase = true),
                    CurrencyEntity("YER_OLD", "ريال يمني - طبعة قديمة", "Yemeni Rial - Old", "ر.ي قديم", 2),
                    CurrencyEntity("USD", "دولار أمريكي", "US Dollar", "$", 2),
                    CurrencyEntity("SAR", "ريال سعودي", "Saudi Riyal", "ر.س", 2)
                )
            )

            db.accountDao().insertAll(
                listOf(
                    AccountEntity(code="1000", nameAr="الأصول", nameEn="Assets", type="ASSET", isPosting=false),
                    AccountEntity(code="1100", nameAr="الصندوق", nameEn="Cash", type="ASSET"),
                    AccountEntity(code="1200", nameAr="المخزون", nameEn="Inventory", type="ASSET"),
                    AccountEntity(code="1210", nameAr="إنتاج تحت التشغيل", nameEn="Work in Process", type="ASSET"),
                    AccountEntity(code="1300", nameAr="العملاء", nameEn="Accounts Receivable", type="ASSET"),
                    AccountEntity(code="1310", nameAr="مبالغ قابلة للاسترداد من العملاء", nameEn="Recoverable Amounts from Customers", type="ASSET"),
                    AccountEntity(code="2000", nameAr="الالتزامات", nameEn="Liabilities", type="LIABILITY", isPosting=false),
                    AccountEntity(code="2100", nameAr="الموردون", nameEn="Accounts Payable", type="LIABILITY"),
                    AccountEntity(code="2200", nameAr="أجور إنتاج مستحقة", nameEn="Accrued Production Labor", type="LIABILITY"),
                    AccountEntity(code="2300", nameAr="عمولات بيع مستحقة", nameEn="Sales Commissions Payable", type="LIABILITY"),
                    AccountEntity(code="3000", nameAr="حقوق الملكية", nameEn="Equity", type="EQUITY", isPosting=false),
                    AccountEntity(code="3100", nameAr="الرصيد الافتتاحي", nameEn="Opening Balance", type="EQUITY"),
                    AccountEntity(code="4000", nameAr="المبيعات", nameEn="Sales", type="REVENUE"),
                    AccountEntity(code="4010", nameAr="إيرادات خدمات ورسوم إضافية", nameEn="Additional Service Revenue", type="REVENUE"),
                    AccountEntity(code="4100", nameAr="مردودات المبيعات", nameEn="Sales Returns", type="REVENUE"),
                    AccountEntity(code="4110", nameAr="خصومات تسوية العملاء", nameEn="Customer Settlement Discounts", type="REVENUE"),
                    AccountEntity(code="5000", nameAr="تكلفة المبيعات", nameEn="Cost of Goods Sold", type="EXPENSE"),
                    AccountEntity(code="6100", nameAr="مصروف الإيجار", nameEn="Rent Expense", type="EXPENSE"),
                    AccountEntity(code="6200", nameAr="مصروف الكهرباء والماء والغاز", nameEn="Utilities Expense", type="EXPENSE"),
                    AccountEntity(code="6300", nameAr="خسائر إنتاج وجودة", nameEn="Production and Quality Loss", type="EXPENSE"),
                    AccountEntity(code="6400", nameAr="مصروف عمولات البيع", nameEn="Sales Commission Expense", type="EXPENSE"),
                    AccountEntity(code="1150", nameAr="الحسابات البنكية", nameEn="Bank Accounts", type="ASSET"),
                    AccountEntity(code="1500", nameAr="الأصول الثابتة", nameEn="Fixed Assets", type="ASSET"),
                    AccountEntity(code="1590", nameAr="مجمع الإهلاك", nameEn="Accumulated Depreciation", type="ASSET"),
                    AccountEntity(code="2400", nameAr="التزامات أخرى", nameEn="Other Payables", type="LIABILITY"),
                    AccountEntity(code="2410", nameAr="رسوم إضافية مستحقة الدفع", nameEn="Additional Charges Payable", type="LIABILITY"),
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

            val recoverableAccount = requireNotNull(db.accountDao().byCode("1310")) { "حساب المبالغ القابلة للاسترداد 1310 غير موجود" }
            val additionalPayableAccount = requireNotNull(db.accountDao().byCode("2410")) { "حساب الرسوم الإضافية المستحقة 2410 غير موجود" }
            val serviceRevenueAccount = requireNotNull(db.accountDao().byCode("4010")) { "حساب إيرادات الخدمات الإضافية 4010 غير موجود" }
            val transportExpenseAccount = requireNotNull(db.accountDao().byCode("6430")) { "حساب مصاريف النقل 6430 غير موجود" }
            val otherExpenseAccount = requireNotNull(db.accountDao().byCode("6900")) { "حساب المصاريف الأخرى 6900 غير موجود" }
            db.additionalChargesDao().insertTypesIgnore(
                listOf(
                    AdditionalChargeTypeEntity(code="TRANSPORT", nameAr="نقل", nameEn="Transport", defaultBearer="CUSTOMER", defaultAccountingTreatment="RECOVERABLE", principalAgentMode="REVIEW", recoverableAccountId=recoverableAccount.id, expenseAccountId=transportExpenseAccount.id, revenueAccountId=serviceRevenueAccount.id, payableAccountId=additionalPayableAccount.id),
                    AdditionalChargeTypeEntity(code="CUSTOMS", nameAr="جمارك", nameEn="Customs", defaultBearer="CUSTOMER", defaultAccountingTreatment="RECOVERABLE", principalAgentMode="REVIEW", recoverableAccountId=recoverableAccount.id, expenseAccountId=otherExpenseAccount.id, revenueAccountId=serviceRevenueAccount.id, payableAccountId=additionalPayableAccount.id),
                    AdditionalChargeTypeEntity(code="LOADING", nameAr="تحميل وتنزيل", nameEn="Loading / Unloading", defaultBearer="CUSTOMER", defaultAccountingTreatment="RECOVERABLE", principalAgentMode="REVIEW", recoverableAccountId=recoverableAccount.id, expenseAccountId=transportExpenseAccount.id, revenueAccountId=serviceRevenueAccount.id, payableAccountId=additionalPayableAccount.id),
                    AdditionalChargeTypeEntity(code="OTHER", nameAr="رسوم أخرى", nameEn="Other Charges", defaultBearer="CUSTOMER", defaultAccountingTreatment="RECOVERABLE", principalAgentMode="REVIEW", recoverableAccountId=recoverableAccount.id, expenseAccountId=otherExpenseAccount.id, revenueAccountId=serviceRevenueAccount.id, payableAccountId=additionalPayableAccount.id)
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
                    WarehouseEntity(code="RET", nameAr="مخزن المرتجعات", nameEn="Returns Warehouse"),
                    WarehouseEntity(
                        code = "SHIP-CUSTODY",
                        nameAr = "عهدة الشحنات (نظام)",
                        nameEn = "Shipment Custody (System)",
                        location = "SYSTEM",
                        isActive = false
                    )
                )
            )

            seedTreasuryAccounts()
            try {
                seedInternalControlDefaults()
            } catch (e: Exception) {
                android.util.Log.e("FushERP", "Failed to seed internal control defaults", e)
            }
        }
    }


    private suspend fun seedTreasuryAccounts() {
        val cash = requireNotNull(db.accountDao().byCode("1100"))
        db.accountingDao().insertTreasuryIgnore(
            TreasuryAccountEntity(
                code = "CASH-MAIN",
                groupCode = "CASH-MAIN",
                nameAr = "الصندوق الرئيسي",
                kind = "CASH",
                accountId = cash.id,
                currencyCode = "YER_NEW",
                createdBy = 1L
            )
        )
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
