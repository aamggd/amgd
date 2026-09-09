package com.fush.erp.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fush.erp.data.dao.*
import com.fush.erp.data.entity.*

const val FUSH_DB_SCHEMA_VERSION = 54

@Database(
    entities = [
        UserEntity::class,
        RoleEntity::class,
        PermissionEntity::class,
        RolePermissionEntity::class,
        UserPasswordHistoryEntity::class,
        MfaRecoveryCodeEntity::class,
        CurrencyEntity::class,
        ExchangeRateEntity::class,
        AccountEntity::class,
        UnitEntity::class,
        WarehouseEntity::class,
        ItemEntity::class,
        JournalEntryEntity::class,
        JournalLineEntity::class,
        JournalLineSemanticEntity::class,
        InventoryCostLayerEntity::class,
        SupplierEntity::class,
        ItemUnitConversionEntity::class,
        PurchaseInvoiceEntity::class,
        PurchaseLineEntity::class,
        PurchaseReturnEntity::class,
        PurchaseReturnLineEntity::class,
        SupplierPaymentEntity::class,
        SupplierPaymentAllocationEntity::class,
        StockMovementEntity::class,
        RecipeEntity::class,
        RecipeComponentEntity::class,
        ProductionOrderEntity::class,
        ProductionMaterialEntity::class,
        ProductionBatchEntity::class,
        ProductionIssueEntity::class,
        QualitySpecificationEntity::class,
        QualityCheckEntity::class,
        QualityCheckSampleEntity::class,
        NonConformanceEntity::class,
        CustomerEntity::class,
        SalesPriceEntity::class,
        SalesInvoiceEntity::class,
        SalesLineEntity::class,
        SalesAllocationEntity::class,
        CustomerReceiptEntity::class,
        CustomerReceiptAllocationEntity::class,
        SalesCommissionEntity::class,
        SalesReturnEntity::class,
        SalesReturnLineEntity::class,
        SalesReturnAllocationEntity::class,
        AdditionalChargeTypeEntity::class,
        SalesAdditionalChargeEntity::class,
        SalesAdditionalChargePaymentEntity::class,
        SalesAdditionalChargeSettlementEntity::class,
        SalesShipmentEntity::class,
        SalesShipmentItemEntity::class,
        SalesShipmentExpenseEntity::class,
        SalesShipmentInvoiceItemAllocationEntity::class,
        SalesShipmentExpenseInvoiceAllocationEntity::class,
        AssetEntity::class,
        MaintenancePlanEntity::class,
        MaintenanceWorkOrderEntity::class,
        BreakdownEntity::class,
        AssetInspectionEntity::class,
        CalibrationRecordEntity::class,
        SafetyIncidentEntity::class,
        SafetyInspectionEntity::class,
        ControlledDocumentEntity::class,
        ChangeRequestEntity::class,
        ApprovalRequestEntity::class,
        AuditEventEntity::class,
        EmployeeEntity::class,
        SalesRepresentativeEntity::class,
        TrainingCourseEntity::class,
        EmployeeTrainingEntity::class,
        EquipmentAuthorizationEntity::class,
        ProductionOperatorAssignmentEntity::class,
        TreasuryAccountEntity::class,
        FxSnapshotEntity::class,
        FxMarketRateEntity::class,
        FxRateSettingsEntity::class,
        GovernorateEntity::class,
        DistrictEntity::class,
        AreaEntity::class,
        GovernorateAliasEntity::class,
        ProvincePolicyEntity::class,
        InvoiceGeographicCostEntity::class,
        InventoryCountEntity::class,
        InventoryCountLineEntity::class,
        InventoryLotControlEntity::class,
        WarehouseReorderPolicyEntity::class,
        WarehouseTransferEntity::class,
        WarehouseTransferLineEntity::class,
        NumberSequenceEntity::class,
        RiskEntity::class,
        InternalControlEntity::class,
        ControlTestEntity::class,
        ControlExceptionEntity::class,
        SegregationRuleEntity::class,
        DemandSeasonalityEntity::class,
        DemandPlanEntity::class,
        SalesBudgetWeekEntity::class,
        InventoryPlanningPolicyEntity::class,
        ProductionPlanEntity::class,
        ProductionPlanMaterialEntity::class,
        PartyVoucherEntity::class,
        PartyAttachmentEntity::class,
        ExpenseDimensionEntity::class,
        ExpenseAttachmentEntity::class,
        AccountingPeriodEntity::class,
        FiscalYearClosingEntity::class,
        TreasuryCashCountEntity::class,
        BankStatementEntity::class,
        BankStatementLineEntity::class,
        TreasuryFxRevaluationEntity::class,
        FixedAssetEntity::class,
        FixedAssetDepreciationEntity::class,
        FixedAssetDisposalEntity::class,
        SupportTicketEntity::class,
        SupportSessionEntity::class,
        SupportSnapshotEntity::class,
        SupportAuditLogEntity::class,
        SupportValidationResultEntity::class,
        VendorSupportIdentityEntity::class,
        VendorSupportKeyEntity::class,
        FushAiDraftEntity::class,
        CloudTenantBindingEntity::class
    ],
    version = FUSH_DB_SCHEMA_VERSION,
    exportSchema = true
)
abstract class FushDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun securityDao(): SecurityDao
    abstract fun currencyDao(): CurrencyDao
    abstract fun accountDao(): AccountDao
    abstract fun unitDao(): UnitDao
    abstract fun warehouseDao(): WarehouseDao
    abstract fun itemDao(): ItemDao
    abstract fun journalDao(): JournalDao
    abstract fun numberSequenceDao(): NumberSequenceDao
    abstract fun supplierDao(): SupplierDao
    abstract fun itemUnitConversionDao(): ItemUnitConversionDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun stockDao(): StockDao
    abstract fun recipeDao(): RecipeDao
    abstract fun productionDao(): ProductionDao
    abstract fun customerDao(): CustomerDao
    abstract fun salesDao(): SalesDao
    abstract fun additionalChargesDao(): AdditionalChargesDao
    abstract fun shipmentDao(): ShipmentDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun governanceDao(): GovernanceDao
    abstract fun employeeDao(): EmployeeDao
    abstract fun salesRepresentativeDao(): SalesRepresentativeDao
    abstract fun accountingDao(): AccountingDao
    abstract fun geographyDao(): GeographyDao
    abstract fun advancedInventoryDao(): AdvancedInventoryDao
    abstract fun reportDao(): ReportDao
    abstract fun riskControlDao(): RiskControlDao
    abstract fun planningDao(): PlanningDao
    abstract fun partyDao(): PartyDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun fixedAssetDao(): FixedAssetDao
    abstract fun supportDao(): SupportDao
    abstract fun fushAiDao(): FushAiDao
}
