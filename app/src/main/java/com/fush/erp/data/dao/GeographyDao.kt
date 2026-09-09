package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fush.erp.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GeographyDao {
    // v204 formal Yemen geography hierarchy. Stable IDs are used by customers, invoices and shipments.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGovernoratesIgnore(rows: List<GovernorateEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDistrictsIgnore(rows: List<DistrictEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAreasIgnore(rows: List<AreaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGovernorate(row: GovernorateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDistrict(row: DistrictEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArea(row: AreaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGovernorateAliases(rows: List<GovernorateAliasEntity>)

    @Query("SELECT * FROM geo_governorates WHERE isActive = 1 ORDER BY sortOrder, nameAr, id")
    fun observeGovernorates(): Flow<List<GovernorateEntity>>

    @Query("SELECT * FROM geo_governorates ORDER BY sortOrder, nameAr, id")
    suspend fun allGovernorates(): List<GovernorateEntity>

    @Query("SELECT * FROM geo_governorates WHERE id = :id LIMIT 1")
    suspend fun governorateById(id: String): GovernorateEntity?

    @Query("SELECT * FROM geo_governorates WHERE code = :code LIMIT 1")
    suspend fun governorateByCode(code: String): GovernorateEntity?

    @Query("SELECT * FROM geo_districts WHERE governorateId = :governorateId AND isActive = 1 ORDER BY sortOrder, nameAr, id")
    fun observeDistricts(governorateId: String): Flow<List<DistrictEntity>>

    @Query("SELECT * FROM geo_districts WHERE governorateId = :governorateId AND isActive = 1 ORDER BY sortOrder, nameAr, id")
    suspend fun districtsForGovernorate(governorateId: String): List<DistrictEntity>

    @Query("SELECT * FROM geo_districts WHERE governorateId = :governorateId ORDER BY sortOrder, nameAr, id")
    suspend fun allDistrictsForGovernorate(governorateId: String): List<DistrictEntity>

    @Query("SELECT * FROM geo_districts WHERE id = :id LIMIT 1")
    suspend fun districtById(id: String): DistrictEntity?

    @Query("SELECT * FROM geo_districts WHERE code = :code LIMIT 1")
    suspend fun districtByCode(code: String): DistrictEntity?

    @Query("SELECT * FROM geo_areas WHERE districtId = :districtId AND isActive = 1 ORDER BY sortOrder, nameAr, id")
    fun observeAreas(districtId: String): Flow<List<AreaEntity>>

    @Query("SELECT * FROM geo_areas WHERE districtId = :districtId AND isActive = 1 ORDER BY sortOrder, nameAr, id")
    suspend fun areasForDistrict(districtId: String): List<AreaEntity>

    @Query("SELECT * FROM geo_areas WHERE districtId = :districtId ORDER BY sortOrder, nameAr, id")
    suspend fun allAreasForDistrict(districtId: String): List<AreaEntity>

    @Query("SELECT * FROM geo_areas WHERE id = :id LIMIT 1")
    suspend fun areaById(id: String): AreaEntity?

    @Query("SELECT * FROM geo_areas WHERE code = :code LIMIT 1")
    suspend fun areaByCode(code: String): AreaEntity?

    @Query("SELECT governorateId FROM geo_governorate_aliases WHERE normalizedAlias = :normalized LIMIT 1")
    suspend fun governorateIdForAlias(normalized: String): String?

    @Query("SELECT COUNT(*) FROM geo_governorates")
    suspend fun governorateCount(): Int

    @Query("SELECT COUNT(*) FROM geo_districts")
    suspend fun districtCount(): Int

    @Query("SELECT COUNT(*) FROM geo_areas")
    suspend fun areaCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFxSnapshot(row: FxSnapshotEntity): Long

    @Query("SELECT * FROM fx_snapshots ORDER BY effectiveAt DESC, id DESC")
    fun observeFxSnapshots(): Flow<List<FxSnapshotEntity>>

    @Query("SELECT * FROM fx_snapshots WHERE effectiveAt <= :at ORDER BY effectiveAt DESC, id DESC LIMIT 1")
    suspend fun latestFxSnapshotAt(at: Long): FxSnapshotEntity?

    @Query("SELECT COUNT(*) FROM fx_snapshots")
    suspend fun fxSnapshotCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFxMarketRates(rows: List<FxMarketRateEntity>)

    @Query("SELECT batchId FROM fx_market_rates ORDER BY fetchedAt DESC, id DESC LIMIT 1")
    suspend fun latestFxMarketBatchId(): String?

    @Query("SELECT * FROM fx_market_rates WHERE batchId = :batchId ORDER BY marketRegion, currencyCode, rateType")
    suspend fun fxMarketRatesForBatch(batchId: String): List<FxMarketRateEntity>

    @Query("SELECT * FROM fx_market_rates WHERE batchId = (SELECT batchId FROM fx_market_rates ORDER BY fetchedAt DESC, id DESC LIMIT 1) ORDER BY marketRegion, currencyCode, rateType")
    fun observeLatestFxMarketRates(): Flow<List<FxMarketRateEntity>>

    @Query("SELECT * FROM fx_market_rates ORDER BY fetchedAt DESC, id DESC LIMIT :limit")
    fun observeFxMarketRateHistory(limit: Int = 80): Flow<List<FxMarketRateEntity>>

    @Query("SELECT * FROM fx_rate_settings WHERE id = 1 LIMIT 1")
    suspend fun fxRateSettings(): FxRateSettingsEntity?

    @Query("SELECT * FROM fx_rate_settings WHERE id = 1 LIMIT 1")
    fun observeFxRateSettings(): Flow<FxRateSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFxRateSettings(row: FxRateSettingsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvincePolicies(rows: List<ProvincePolicyEntity>)

    @Query("SELECT * FROM province_policies WHERE isActive = 1 ORDER BY nameAr, code")
    fun observeProvincePolicies(): Flow<List<ProvincePolicyEntity>>

    @Query("SELECT * FROM province_policies WHERE code = :code LIMIT 1")
    suspend fun provincePolicy(code: String): ProvincePolicyEntity?

    @Query("""
        SELECT name FROM (
            SELECT TRIM(nameAr) AS name FROM province_policies WHERE isActive = 1 AND TRIM(nameAr) <> ''
            UNION
            SELECT TRIM(province) AS name FROM customers WHERE TRIM(province) <> ''
            UNION
            SELECT TRIM(province) AS name FROM sales_invoices WHERE TRIM(province) <> ''
            UNION
            SELECT TRIM(destinationProvince) AS name FROM sales_shipments WHERE TRIM(destinationProvince) <> ''
        )
        ORDER BY name COLLATE NOCASE
    """)
    suspend fun knownProvinceNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvoiceGeographicCost(row: InvoiceGeographicCostEntity): Long

    @Query("SELECT * FROM invoice_geographic_costs WHERE invoiceId = :invoiceId LIMIT 1")
    suspend fun invoiceGeographicCost(invoiceId: Long): InvoiceGeographicCostEntity?

    @Query("""
        SELECT si.id AS invoiceId,
               si.invoiceNo AS invoiceNo,
               si.invoiceDate AS invoiceDate,
               c.id AS customerId,
               c.nameAr AS customerName,
               si.province AS province,
               si.currencyCode AS currencyCode,
               (si.totalBase - COALESCE((SELECT SUM(sr.totalBase)
                    FROM sales_returns sr
                    WHERE sr.salesInvoiceId = si.id AND sr.status = 'POSTED' AND sr.returnDate <= :to), 0)) AS netRevenueBase,
               (COALESCE((SELECT SUM(sa.costBase)
                    FROM sales_allocations sa
                    JOIN sales_lines sl ON sl.id = sa.salesLineId
                    WHERE sl.invoiceId = si.id), 0)
                - COALESCE((SELECT SUM(sr2.totalCostBase)
                    FROM sales_returns sr2
                    WHERE sr2.salesInvoiceId = si.id AND sr2.status = 'POSTED' AND sr2.returnDate <= :to), 0)) AS netCogsBase,
               COALESCE((SELECT SUM(jl.debit - jl.credit)
                    FROM journal_entries je
                    JOIN journal_lines jl ON jl.entryId = je.id
                    JOIN accounts a ON a.id = jl.accountId
                    WHERE je.sourceId = CAST(si.id AS TEXT)
                      AND je.sourceType IN ('SALES_COMMISSION','COMMISSION_REVERSAL')
                      AND je.entryDate <= :to AND a.code = '6400'), 0) AS commissionBase,
               COALESCE(NULLIF((SELECT SUM(seia.amountBase)
                    FROM sales_shipment_expense_invoice_allocations seia
                    JOIN sales_shipment_expenses se ON se.id = seia.shipmentExpenseId
                    WHERE seia.invoiceId = si.id AND seia.status = 'ACTIVE' AND se.status = 'POSTED'), 0),
                    (SELECT igc.transportCostBase + igc.feesCustomsCostBase + igc.otherDirectCostBase
                     FROM invoice_geographic_costs igc WHERE igc.invoiceId = si.id LIMIT 1), 0) AS geographicCostBase,
               ((si.totalBase - COALESCE((SELECT SUM(sr3.totalBase)
                    FROM sales_returns sr3 WHERE sr3.salesInvoiceId = si.id AND sr3.status = 'POSTED' AND sr3.returnDate <= :to), 0))
                - (COALESCE((SELECT SUM(sa2.costBase)
                    FROM sales_allocations sa2 JOIN sales_lines sl2 ON sl2.id = sa2.salesLineId
                    WHERE sl2.invoiceId = si.id), 0)
                   - COALESCE((SELECT SUM(sr4.totalCostBase)
                    FROM sales_returns sr4 WHERE sr4.salesInvoiceId = si.id AND sr4.status = 'POSTED' AND sr4.returnDate <= :to), 0))
                - COALESCE((SELECT SUM(jl2.debit - jl2.credit)
                    FROM journal_entries je2
                    JOIN journal_lines jl2 ON jl2.entryId = je2.id
                    JOIN accounts a2 ON a2.id = jl2.accountId
                    WHERE je2.sourceId = CAST(si.id AS TEXT)
                      AND je2.sourceType IN ('SALES_COMMISSION','COMMISSION_REVERSAL')
                      AND je2.entryDate <= :to AND a2.code = '6400'), 0)
                - COALESCE(NULLIF((SELECT SUM(seia2.amountBase)
                    FROM sales_shipment_expense_invoice_allocations seia2
                    JOIN sales_shipment_expenses se2 ON se2.id = seia2.shipmentExpenseId
                    WHERE seia2.invoiceId = si.id AND seia2.status = 'ACTIVE' AND se2.status = 'POSTED'), 0),
                    (SELECT igc2.transportCostBase + igc2.feesCustomsCostBase + igc2.otherDirectCostBase
                     FROM invoice_geographic_costs igc2 WHERE igc2.invoiceId = si.id LIMIT 1), 0)) AS profitBase
        FROM sales_invoices si
        JOIN customers c ON c.id = si.customerId
        WHERE si.status = 'POSTED' AND si.invoiceDate BETWEEN :from AND :to
        ORDER BY si.invoiceDate DESC, si.id DESC
    """)
    suspend fun invoiceProfitability(from: Long, to: Long): List<InvoiceProfitabilityRow>

    @Query("""
        WITH invoice_profit AS (
            SELECT si.id AS invoiceId,
                   si.province AS province,
                   (si.totalBase - COALESCE((SELECT SUM(sr.totalBase)
                        FROM sales_returns sr WHERE sr.salesInvoiceId = si.id AND sr.status = 'POSTED' AND sr.returnDate <= :to), 0)) AS netRevenueBase,
                   (COALESCE((SELECT SUM(sa.costBase)
                        FROM sales_allocations sa JOIN sales_lines sl ON sl.id = sa.salesLineId
                        WHERE sl.invoiceId = si.id), 0)
                    - COALESCE((SELECT SUM(sr2.totalCostBase)
                        FROM sales_returns sr2 WHERE sr2.salesInvoiceId = si.id AND sr2.status = 'POSTED' AND sr2.returnDate <= :to), 0)) AS netCogsBase,
                   COALESCE((SELECT SUM(jl.debit - jl.credit)
                        FROM journal_entries je
                        JOIN journal_lines jl ON jl.entryId = je.id
                        JOIN accounts a ON a.id = jl.accountId
                        WHERE je.sourceId = CAST(si.id AS TEXT)
                          AND je.sourceType IN ('SALES_COMMISSION','COMMISSION_REVERSAL')
                          AND je.entryDate <= :to AND a.code = '6400'), 0) AS commissionBase,
                   COALESCE(NULLIF((SELECT SUM(seia.amountBase)
                        FROM sales_shipment_expense_invoice_allocations seia
                        JOIN sales_shipment_expenses se ON se.id = seia.shipmentExpenseId
                        WHERE seia.invoiceId = si.id AND seia.status = 'ACTIVE' AND se.status = 'POSTED'), 0),
                        (SELECT igc.transportCostBase + igc.feesCustomsCostBase + igc.otherDirectCostBase
                         FROM invoice_geographic_costs igc WHERE igc.invoiceId = si.id LIMIT 1), 0) AS geographicCostBase
            FROM sales_invoices si
            WHERE si.status = 'POSTED' AND si.invoiceDate BETWEEN :from AND :to
        )
        SELECT province AS province,
               COUNT(*) AS invoiceCount,
               COALESCE(SUM(netRevenueBase), 0) AS netRevenueBase,
               COALESCE(SUM(netCogsBase), 0) AS netCogsBase,
               COALESCE(SUM(commissionBase), 0) AS commissionBase,
               COALESCE(SUM(geographicCostBase), 0) AS geographicCostBase,
               COALESCE(SUM(netRevenueBase - netCogsBase - commissionBase - geographicCostBase), 0) AS profitBase
        FROM invoice_profit
        GROUP BY province
        ORDER BY profitBase DESC, province
    """)
    suspend fun provinceProfitability(from: Long, to: Long): List<ProvinceProfitabilityRow>
}
