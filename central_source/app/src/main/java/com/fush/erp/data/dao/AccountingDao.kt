package com.fush.erp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fush.erp.data.entity.TreasuryAccountEntity
import com.fush.erp.data.entity.AccountingPeriodEntity
import com.fush.erp.data.entity.FiscalYearClosingEntity
import com.fush.erp.data.entity.TreasuryBalanceRow
import com.fush.erp.data.entity.TreasuryCashCountEntity
import com.fush.erp.data.entity.TreasuryCashCountRow
import com.fush.erp.data.entity.BankStatementEntity
import com.fush.erp.data.entity.BankStatementLineEntity
import com.fush.erp.data.entity.BankStatementRow
import com.fush.erp.data.entity.BankBookMovementRow
import com.fush.erp.data.entity.TreasuryFxRevaluationEntity
import com.fush.erp.data.entity.TreasuryFxRevaluationRow
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTreasury(row: TreasuryAccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTreasuryIgnore(row: TreasuryAccountEntity): Long

    @Query("SELECT * FROM treasury_accounts WHERE id = :id LIMIT 1")
    suspend fun treasuryById(id: Long): TreasuryAccountEntity?

    @Query("SELECT * FROM treasury_accounts WHERE accountId = :accountId LIMIT 1")
    suspend fun treasuryByAccountId(accountId: Long): TreasuryAccountEntity?

    @Query("SELECT * FROM treasury_accounts WHERE isActive = 1 ORDER BY kind, nameAr")
    suspend fun allActiveTreasury(): List<TreasuryAccountEntity>

    @Query("SELECT * FROM treasury_accounts ORDER BY kind, nameAr")
    suspend fun allTreasury(): List<TreasuryAccountEntity>

    @Query("SELECT COUNT(*) FROM treasury_accounts")
    suspend fun treasuryCount(): Int


    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPeriod(row: AccountingPeriodEntity): Long

    @Update
    suspend fun updatePeriod(row: AccountingPeriodEntity)

    @Query("SELECT * FROM accounting_periods WHERE id = :id LIMIT 1")
    suspend fun periodById(id: Long): AccountingPeriodEntity?

    @Query("SELECT * FROM accounting_periods WHERE :date BETWEEN startDate AND endDate ORDER BY startDate DESC LIMIT 1")
    suspend fun periodForDate(date: Long): AccountingPeriodEntity?

    @Query("SELECT COUNT(*) FROM accounting_periods WHERE fiscalYear = :fiscalYear")
    suspend fun periodCountForYear(fiscalYear: Int): Int

    @Query("SELECT COUNT(*) FROM accounting_periods WHERE fiscalYear = :fiscalYear AND periodNo < :periodNo AND status = 'OPEN'")
    suspend fun earlierOpenPeriodCount(fiscalYear: Int, periodNo: Int): Int

    @Query("SELECT COUNT(*) FROM accounting_periods WHERE fiscalYear = :fiscalYear AND periodNo > :periodNo AND status = 'CLOSED'")
    suspend fun laterClosedPeriodCount(fiscalYear: Int, periodNo: Int): Int

    @Query("SELECT * FROM accounting_periods ORDER BY fiscalYear DESC, periodNo")
    fun observePeriods(): Flow<List<AccountingPeriodEntity>>

    @Query("SELECT * FROM accounting_periods ORDER BY fiscalYear DESC, periodNo")
    suspend fun allPeriods(): List<AccountingPeriodEntity>

    @Query("SELECT * FROM accounting_periods WHERE fiscalYear = :fiscalYear ORDER BY periodNo")
    suspend fun periodsForYear(fiscalYear: Int): List<AccountingPeriodEntity>

    @Query("SELECT * FROM accounting_periods WHERE fiscalYear = :fiscalYear AND periodNo = :periodNo LIMIT 1")
    suspend fun periodByYearNo(fiscalYear: Int, periodNo: Int): AccountingPeriodEntity?

    @Query("SELECT COUNT(*) FROM accounting_periods WHERE startDate > :afterDate AND status = 'CLOSED'")
    suspend fun laterClosedPeriodAfterDate(afterDate: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFiscalYearClosing(row: FiscalYearClosingEntity): Long

    @Update
    suspend fun updateFiscalYearClosing(row: FiscalYearClosingEntity)

    @Query("SELECT * FROM fiscal_year_closings WHERE fiscalYear = :fiscalYear AND status = 'CLOSED' ORDER BY id DESC LIMIT 1")
    suspend fun latestClosedFiscalYear(fiscalYear: Int): FiscalYearClosingEntity?

    @Query("SELECT * FROM fiscal_year_closings WHERE fiscalYear = :fiscalYear ORDER BY id DESC LIMIT 1")
    suspend fun latestFiscalYearClosing(fiscalYear: Int): FiscalYearClosingEntity?

    @Query("SELECT COUNT(*) FROM fiscal_year_closings WHERE fiscalYear > :fiscalYear AND status = 'CLOSED'")
    suspend fun laterClosedFiscalYearCount(fiscalYear: Int): Int

    @Query("SELECT * FROM fiscal_year_closings ORDER BY fiscalYear DESC, id DESC")
    fun observeFiscalYearClosings(): Flow<List<FiscalYearClosingEntity>>

    @Query("SELECT COALESCE(SUM(quantityBase * unitCostBase), 0) FROM stock_movements WHERE movementDate <= :asOf")
    suspend fun inventorySubledgerValue(asOf: Long): Double

    @Query("""
        SELECT t.id AS id, t.code AS code, t.nameAr AS nameAr, t.kind AS kind,
               t.accountId AS accountId, t.currencyCode AS currencyCode,
               t.bankName AS bankName, t.accountNumber AS accountNumber,
               COALESCE(SUM(CASE WHEN je.status = 'POSTED' THEN jl.debit - jl.credit ELSE 0 END), 0) AS balanceBase,
               COALESCE(SUM(CASE WHEN je.status = 'POSTED' AND je.currencyCode = t.currencyCode AND ABS(je.exchangeRate) > 0.000000001
                    THEN (jl.debit - jl.credit) / je.exchangeRate ELSE 0 END), 0) AS balanceOriginal
        FROM treasury_accounts t
        LEFT JOIN journal_lines jl ON jl.accountId = t.accountId
        LEFT JOIN journal_entries je ON je.id = jl.entryId
        WHERE t.isActive = 1
        GROUP BY t.id, t.code, t.nameAr, t.kind, t.accountId, t.currencyCode, t.bankName, t.accountNumber
        ORDER BY t.kind, t.nameAr
    """)
    fun observeTreasuryBalances(): Flow<List<TreasuryBalanceRow>>


    @Query("SELECT COALESCE(SUM(CASE WHEN je.status = 'POSTED' THEN jl.debit - jl.credit ELSE 0 END), 0) FROM journal_lines jl JOIN journal_entries je ON je.id = jl.entryId WHERE jl.accountId = :accountId AND je.entryDate <= :asOf")
    suspend fun treasuryBookBalance(accountId: Long, asOf: Long): Double

    @Query("""
        SELECT COALESCE(SUM(CASE
            WHEN je.status = 'POSTED' AND je.currencyCode = :currencyCode AND ABS(je.exchangeRate) > 0.000000001
            THEN (jl.debit - jl.credit) / je.exchangeRate ELSE 0 END), 0)
        FROM journal_lines jl
        JOIN journal_entries je ON je.id = jl.entryId
        WHERE jl.accountId = :accountId AND je.entryDate <= :asOf
    """)
    suspend fun treasuryBookOriginalBalance(accountId: Long, currencyCode: String, asOf: Long): Double

    @Query("SELECT COUNT(*) FROM journal_lines jl JOIN journal_entries je ON je.id = jl.entryId WHERE jl.accountId = :accountId AND je.status = 'POSTED' AND je.entryDate BETWEEN :fromDate AND :toDate")
    suspend fun treasuryActivityCount(accountId: Long, fromDate: Long, toDate: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCashCount(row: TreasuryCashCountEntity): Long

    @Update
    suspend fun updateCashCount(row: TreasuryCashCountEntity)

    @Query("SELECT * FROM treasury_cash_counts WHERE id = :id LIMIT 1")
    suspend fun cashCountById(id: Long): TreasuryCashCountEntity?

    @Query("SELECT COUNT(*) FROM treasury_cash_counts WHERE treasuryAccountId = :treasuryAccountId AND countDate BETWEEN :fromDate AND :toDate")
    suspend fun cashCountCountInRange(treasuryAccountId: Long, fromDate: Long, toDate: Long): Int

    @Query("SELECT COUNT(*) FROM treasury_cash_counts WHERE countDate <= :asOf AND status = 'VARIANCE'")
    suspend fun unresolvedCashVarianceCount(asOf: Long): Int

    @Query("SELECT COUNT(*) FROM treasury_cash_counts WHERE treasuryAccountId = :treasuryAccountId AND countDate <= :asOf AND status = 'VARIANCE'")
    suspend fun unresolvedCashVarianceCountForTreasury(treasuryAccountId: Long, asOf: Long): Int

    @Query("""
        SELECT c.id AS id, c.treasuryAccountId AS treasuryAccountId, t.nameAr AS treasuryName,
               t.currencyCode AS currencyCode, c.countDate AS countDate,
               c.expectedBalanceBase AS expectedBalanceBase, c.actualBalanceBase AS actualBalanceBase,
               c.differenceBase AS differenceBase, c.expectedBalanceOriginal AS expectedBalanceOriginal,
               c.actualBalanceOriginal AS actualBalanceOriginal, c.differenceOriginal AS differenceOriginal,
               c.rateToBase AS rateToBase, c.status AS status, c.notes AS notes, c.resolutionEntryId AS resolutionEntryId
        FROM treasury_cash_counts c
        JOIN treasury_accounts t ON t.id = c.treasuryAccountId
        ORDER BY c.countDate DESC, c.id DESC
        LIMIT 100
    """)
    fun observeCashCounts(): Flow<List<TreasuryCashCountRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBankStatement(row: BankStatementEntity): Long

    @Update
    suspend fun updateBankStatement(row: BankStatementEntity)

    @Query("SELECT * FROM bank_statements WHERE id = :id LIMIT 1")
    suspend fun bankStatementById(id: Long): BankStatementEntity?

    @Query("SELECT * FROM bank_statements WHERE treasuryAccountId = :treasuryAccountId AND status = 'RECONCILED' ORDER BY endDate DESC, id DESC LIMIT 1")
    suspend fun latestReconciledBankStatement(treasuryAccountId: Long): BankStatementEntity?

    @Query("SELECT * FROM bank_statements WHERE treasuryAccountId = :treasuryAccountId ORDER BY endDate DESC, id DESC LIMIT 1")
    suspend fun latestBankStatement(treasuryAccountId: Long): BankStatementEntity?

    @Query("SELECT * FROM bank_statements WHERE treasuryAccountId = :treasuryAccountId ORDER BY startDate ASC, id ASC LIMIT 1")
    suspend fun firstBankStatement(treasuryAccountId: Long): BankStatementEntity?

    @Query("SELECT COUNT(*) FROM bank_statements WHERE treasuryAccountId = :treasuryAccountId AND status = 'RECONCILED' AND startDate <= :requiredStart AND endDate >= :requiredEnd")
    suspend fun reconciledBankStatementCovering(treasuryAccountId: Long, requiredStart: Long, requiredEnd: Long): Int

    @Query("""
        SELECT s.id AS id, s.treasuryAccountId AS treasuryAccountId, t.nameAr AS treasuryName,
               s.currencyCode AS currencyCode, s.startDate AS startDate, s.endDate AS endDate,
               s.openingBalanceOriginal AS openingBalanceOriginal, s.closingBalanceOriginal AS closingBalanceOriginal,
               s.openingBalanceBase AS openingBalanceBase, s.closingBalanceBase AS closingBalanceBase,
               s.status AS status, s.notes AS notes
        FROM bank_statements s
        JOIN treasury_accounts t ON t.id = s.treasuryAccountId
        ORDER BY s.endDate DESC, s.id DESC
        LIMIT 100
    """)
    fun observeBankStatements(): Flow<List<BankStatementRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBankStatementLine(row: BankStatementLineEntity): Long

    @Update
    suspend fun updateBankStatementLine(row: BankStatementLineEntity)

    @Query("SELECT * FROM bank_statement_lines WHERE id = :id LIMIT 1")
    suspend fun bankStatementLineById(id: Long): BankStatementLineEntity?

    @Query("SELECT * FROM bank_statement_lines WHERE statementId = :statementId ORDER BY transactionDate, id")
    suspend fun bankStatementLines(statementId: Long): List<BankStatementLineEntity>

    @Query("SELECT COUNT(*) FROM bank_statement_lines WHERE statementId = :statementId AND matchedJournalEntryId IS NULL")
    suspend fun unmatchedBankStatementLineCount(statementId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM bank_statement_lines l
        JOIN bank_statements s ON s.id = l.statementId
        WHERE l.matchedJournalEntryId = :entryId AND s.treasuryAccountId = :treasuryAccountId
    """)
    suspend fun bankMatchCountForJournal(treasuryAccountId: Long, entryId: Long): Int

    @Query("""
        SELECT je.id AS entryId, je.entryNo AS entryNo, je.entryDate AS entryDate,
               je.description AS description, t.currencyCode AS currencyCode, je.exchangeRate AS exchangeRate,
               CASE WHEN je.currencyCode = t.currencyCode AND ABS(je.exchangeRate) > 0.000000001
                    THEN SUM(jl.debit - jl.credit) / je.exchangeRate ELSE 0 END AS amountOriginal,
               SUM(jl.debit - jl.credit) AS amountBase
        FROM journal_entries je
        JOIN journal_lines jl ON jl.entryId = je.id
        JOIN treasury_accounts t ON t.accountId = jl.accountId
        WHERE jl.accountId = :accountId
          AND je.status = 'POSTED'
          AND je.entryDate <= :asOf
          AND je.sourceType NOT IN ('FX_REVALUATION','FX_REVALUATION_REVERSAL')
        GROUP BY je.id, je.entryNo, je.entryDate, je.description, t.currencyCode, je.currencyCode, je.exchangeRate
        HAVING ABS(SUM(jl.debit - jl.credit)) > 0.000001
        ORDER BY je.entryDate DESC, je.id DESC
    """)
    suspend fun bankBookMovements(accountId: Long, asOf: Long): List<BankBookMovementRow>

    @Query("""
        SELECT je.id AS entryId, je.entryNo AS entryNo, je.entryDate AS entryDate,
               je.description AS description, t.currencyCode AS currencyCode, je.exchangeRate AS exchangeRate,
               CASE WHEN je.currencyCode = t.currencyCode AND ABS(je.exchangeRate) > 0.000000001
                    THEN SUM(jl.debit - jl.credit) / je.exchangeRate ELSE 0 END AS amountOriginal,
               SUM(jl.debit - jl.credit) AS amountBase
        FROM journal_entries je
        JOIN journal_lines jl ON jl.entryId = je.id
        JOIN treasury_accounts t ON t.accountId = jl.accountId
        WHERE jl.accountId = :accountId
          AND je.status = 'POSTED'
          AND je.entryDate BETWEEN :fromDate AND :toDate
          AND je.sourceType NOT IN ('FX_REVALUATION','FX_REVALUATION_REVERSAL')
        GROUP BY je.id, je.entryNo, je.entryDate, je.description, t.currencyCode, je.currencyCode, je.exchangeRate
        HAVING ABS(SUM(jl.debit - jl.credit)) > 0.000001
        ORDER BY je.entryDate, je.id
    """)
    suspend fun bankBookMovementsInRange(accountId: Long, fromDate: Long, toDate: Long): List<BankBookMovementRow>

    @Query("""
        SELECT DISTINCT matchedJournalEntryId FROM bank_statement_lines
        WHERE matchedJournalEntryId IS NOT NULL
          AND statementId IN (SELECT id FROM bank_statements WHERE treasuryAccountId = :treasuryAccountId AND endDate <= :asOf)
    """)
    suspend fun matchedJournalIdsThrough(treasuryAccountId: Long, asOf: Long): List<Long?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFxRevaluation(row: TreasuryFxRevaluationEntity): Long

    @Update
    suspend fun updateFxRevaluation(row: TreasuryFxRevaluationEntity)

    @Query("SELECT * FROM treasury_fx_revaluations WHERE id = :id LIMIT 1")
    suspend fun fxRevaluationById(id: Long): TreasuryFxRevaluationEntity?

    @Query("SELECT * FROM treasury_fx_revaluations WHERE treasuryAccountId = :treasuryAccountId AND valuationDate = :valuationDate AND status = 'POSTED' ORDER BY id DESC LIMIT 1")
    suspend fun activeFxRevaluation(treasuryAccountId: Long, valuationDate: Long): TreasuryFxRevaluationEntity?

    @Query("SELECT * FROM treasury_fx_revaluations WHERE valuationDate BETWEEN :fromDate AND :toDate AND status = 'POSTED' ORDER BY valuationDate, id")
    suspend fun activeFxRevaluationsInRange(fromDate: Long, toDate: Long): List<TreasuryFxRevaluationEntity>

    @Query("""
        SELECT r.id AS id, r.treasuryAccountId AS treasuryAccountId, t.nameAr AS treasuryName,
               r.valuationDate AS valuationDate, r.currencyCode AS currencyCode,
               r.originalBalance AS originalBalance, r.carryingBalanceBeforeBase AS carryingBalanceBeforeBase,
               r.rateToBase AS rateToBase, r.targetBalanceBase AS targetBalanceBase,
               r.differenceBase AS differenceBase, r.status AS status,
               r.journalEntryId AS journalEntryId, r.reversalEntryId AS reversalEntryId
        FROM treasury_fx_revaluations r
        JOIN treasury_accounts t ON t.id = r.treasuryAccountId
        ORDER BY r.valuationDate DESC, r.id DESC
        LIMIT 100
    """)
    fun observeFxRevaluations(): Flow<List<TreasuryFxRevaluationRow>>

}
