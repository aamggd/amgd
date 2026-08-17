package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "treasury_accounts",
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["accountId"], unique = true),
        Index("currencyCode"),
        Index("isActive")
    ],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["code"],
            childColumns = ["currencyCode"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class TreasuryAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val nameAr: String,
    val kind: String,
    val accountId: Long,
    val currencyCode: String = "YER_NEW",
    val bankName: String = "",
    val accountNumber: String = "",
    val isActive: Boolean = true,
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)

data class TreasuryBalanceRow(
    val id: Long,
    val code: String,
    val nameAr: String,
    val kind: String,
    val accountId: Long,
    val currencyCode: String,
    val bankName: String,
    val accountNumber: String,
    val balanceBase: Double,
    val balanceOriginal: Double
)

data class JournalHeaderRow(
    val id: Long,
    val entryNo: String,
    val entryDate: Long,
    val description: String,
    val currencyCode: String,
    val exchangeRate: Double,
    val sourceType: String,
    val sourceId: String?,
    val createdBy: Long,
    val debitTotal: Double,
    val creditTotal: Double,
    val isReversed: Boolean
)

data class JournalDetailRow(
    val entryId: Long,
    val entryNo: String,
    val entryDate: Long,
    val description: String,
    val sourceType: String,
    val accountId: Long,
    val accountCode: String,
    val accountNameAr: String,
    val accountType: String,
    val debit: Double,
    val credit: Double,
    val memo: String
)


@Entity(
    tableName = "accounting_periods",
    indices = [
        Index(value = ["fiscalYear", "periodNo"], unique = true),
        Index("startDate"),
        Index("endDate"),
        Index("status")
    ]
)
data class AccountingPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fiscalYear: Int,
    val periodNo: Int,
    val nameAr: String,
    val startDate: Long,
    val endDate: Long,
    val status: String = "OPEN",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val closedBy: Long? = null,
    val closedAt: Long? = null,
    val closeReason: String = "",
    val reopenedBy: Long? = null,
    val reopenedAt: Long? = null,
    val reopenReason: String = ""
)


@Entity(
    tableName = "fiscal_year_closings",
    indices = [
        Index("fiscalYear"),
        Index("status"),
        Index(value = ["closingEntryId"], unique = true),
        Index(value = ["reversalEntryId"], unique = true)
    ]
)
data class FiscalYearClosingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fiscalYear: Int,
    val startDate: Long,
    val endDate: Long,
    val closingEntryId: Long? = null,
    val netIncomeBase: Double = 0.0,
    val retainedEarningsAccountId: Long,
    val status: String = "CLOSED",
    val closeReason: String,
    val closedBy: Long,
    val closedAt: Long = System.currentTimeMillis(),
    val reversalEntryId: Long? = null,
    val reopenReason: String = "",
    val reopenedBy: Long? = null,
    val reopenedAt: Long? = null
)

@Entity(
    tableName = "treasury_cash_counts",
    indices = [
        Index("treasuryAccountId"),
        Index("countDate"),
        Index("status"),
        Index(value = ["resolutionEntryId"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = TreasuryAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["treasuryAccountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class TreasuryCashCountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treasuryAccountId: Long,
    val countDate: Long,
    val expectedBalanceBase: Double,
    val actualBalanceBase: Double,
    val differenceBase: Double,
    val expectedBalanceOriginal: Double = 0.0,
    val actualBalanceOriginal: Double = 0.0,
    val differenceOriginal: Double = 0.0,
    val rateToBase: Double = 1.0,
    val status: String,
    val notes: String = "",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val resolutionEntryId: Long? = null,
    val resolutionReason: String = "",
    val resolvedBy: Long? = null,
    val resolvedAt: Long? = null
)

@Entity(
    tableName = "bank_statements",
    indices = [
        Index("treasuryAccountId"),
        Index("startDate"),
        Index("endDate"),
        Index("status"),
        Index(value = ["treasuryAccountId", "startDate", "endDate"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = TreasuryAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["treasuryAccountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class BankStatementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treasuryAccountId: Long,
    val startDate: Long,
    val endDate: Long,
    val currencyCode: String = "YER_NEW",
    val openingBalanceOriginal: Double = 0.0,
    val closingBalanceOriginal: Double = 0.0,
    val openingBalanceBase: Double,
    val closingBalanceBase: Double,
    val status: String = "DRAFT",
    val notes: String = "",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val reconciledBy: Long? = null,
    val reconciledAt: Long? = null
)

@Entity(
    tableName = "bank_statement_lines",
    indices = [
        Index("statementId"),
        Index("transactionDate"),
        Index("matchedJournalEntryId"),
        Index(value = ["statementId", "transactionDate"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = BankStatementEntity::class,
            parentColumns = ["id"],
            childColumns = ["statementId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BankStatementLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val statementId: Long,
    val transactionDate: Long,
    val referenceNo: String = "",
    val description: String = "",
    val amountOriginal: Double = 0.0,
    val amountBase: Double,
    val matchedJournalEntryId: Long? = null,
    val matchedBy: Long? = null,
    val matchedAt: Long? = null
)

data class TreasuryCashCountRow(
    val id: Long,
    val treasuryAccountId: Long,
    val treasuryName: String,
    val currencyCode: String,
    val countDate: Long,
    val expectedBalanceBase: Double,
    val actualBalanceBase: Double,
    val differenceBase: Double,
    val expectedBalanceOriginal: Double,
    val actualBalanceOriginal: Double,
    val differenceOriginal: Double,
    val rateToBase: Double,
    val status: String,
    val notes: String,
    val resolutionEntryId: Long?
)

data class BankStatementRow(
    val id: Long,
    val treasuryAccountId: Long,
    val treasuryName: String,
    val currencyCode: String,
    val startDate: Long,
    val endDate: Long,
    val openingBalanceOriginal: Double,
    val closingBalanceOriginal: Double,
    val openingBalanceBase: Double,
    val closingBalanceBase: Double,
    val status: String,
    val notes: String
)

data class BankBookMovementRow(
    val entryId: Long,
    val entryNo: String,
    val entryDate: Long,
    val description: String,
    val currencyCode: String,
    val exchangeRate: Double,
    val amountOriginal: Double,
    val amountBase: Double
)


@Entity(
    tableName = "treasury_fx_revaluations",
    indices = [
        Index("treasuryAccountId"),
        Index("valuationDate"),
        Index("status"),
        Index(value = ["journalEntryId"], unique = true),
        Index(value = ["reversalEntryId"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = TreasuryAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["treasuryAccountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class TreasuryFxRevaluationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val treasuryAccountId: Long,
    val valuationDate: Long,
    val currencyCode: String,
    val originalBalance: Double,
    val carryingBalanceBeforeBase: Double,
    val rateToBase: Double,
    val targetBalanceBase: Double,
    val differenceBase: Double,
    val status: String = "POSTED",
    val journalEntryId: Long? = null,
    val reason: String = "",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val reversalEntryId: Long? = null,
    val reversedBy: Long? = null,
    val reversedAt: Long? = null,
    val reversalReason: String = ""
)

data class TreasuryFxRevaluationRow(
    val id: Long,
    val treasuryAccountId: Long,
    val treasuryName: String,
    val valuationDate: Long,
    val currencyCode: String,
    val originalBalance: Double,
    val carryingBalanceBeforeBase: Double,
    val rateToBase: Double,
    val targetBalanceBase: Double,
    val differenceBase: Double,
    val status: String,
    val journalEntryId: Long?,
    val reversalEntryId: Long?
)
