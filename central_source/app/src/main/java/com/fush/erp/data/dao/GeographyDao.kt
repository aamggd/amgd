package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fush.erp.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GeographyDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFxSnapshot(row: FxSnapshotEntity): Long

    @Query("SELECT * FROM fx_snapshots ORDER BY effectiveAt DESC, id DESC")
    fun observeFxSnapshots(): Flow<List<FxSnapshotEntity>>

    @Query("SELECT * FROM fx_snapshots WHERE effectiveAt <= :at ORDER BY effectiveAt DESC, id DESC LIMIT 1")
    suspend fun latestFxSnapshotAt(at: Long): FxSnapshotEntity?

    @Query("SELECT COUNT(*) FROM fx_snapshots")
    suspend fun fxSnapshotCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvincePolicies(rows: List<ProvincePolicyEntity>)

    @Query("SELECT * FROM province_policies WHERE isActive = 1 ORDER BY CASE code WHEN 'TAIZ' THEN 1 WHEN 'ADEN' THEN 2 WHEN 'SANAA' THEN 3 ELSE 4 END, nameAr")
    fun observeProvincePolicies(): Flow<List<ProvincePolicyEntity>>

    @Query("SELECT * FROM province_policies WHERE code = :code LIMIT 1")
    suspend fun provincePolicy(code: String): ProvincePolicyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInvoiceGeographicCost(row: InvoiceGeographicCostEntity): Long

    @Query("SELECT * FROM invoice_geographic_costs WHERE invoiceId = :invoiceId LIMIT 1")
    suspend fun invoiceGeographicCost(invoiceId: Long): InvoiceGeographicCostEntity?

    @Query("SELECT COALESCE(SUM(baseQuantity), 0) / 480.0 FROM sales_lines WHERE invoiceId = :invoiceId")
    suspend fun invoiceCartonsEquivalent(invoiceId: Long): Double

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
               COALESCE((SELECT igc.transportCostBase + igc.feesCustomsCostBase + igc.otherDirectCostBase
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
                - COALESCE((SELECT igc2.transportCostBase + igc2.feesCustomsCostBase + igc2.otherDirectCostBase
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
                   COALESCE((SELECT igc.transportCostBase + igc.feesCustomsCostBase + igc.otherDirectCostBase
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
