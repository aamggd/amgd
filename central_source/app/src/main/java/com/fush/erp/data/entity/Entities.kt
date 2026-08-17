package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users", indices = [Index(value = ["username"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val displayName: String,
    val passwordHash: String,
    val salt: String,
    val role: String = "ADMIN",
    val isActive: Boolean = true,
    val mustChangePassword: Boolean = true,
    val failedLoginAttempts: Int = 0,
    val lockoutCount: Int = 0,
    val lockedUntil: Long? = null,
    val lastLoginAt: Long? = null,
    val passwordChangedAt: Long? = null,
    val sessionVersion: Long = 0,
    val mfaEnabled: Boolean = false,
    val mfaSecretCiphertext: String? = null,
    val mfaConfirmedAt: Long? = null,
    val mfaVerifiedSessionVersion: Long = -1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "currencies", indices = [Index(value = ["code"], unique = true)])
data class CurrencyEntity(
    @PrimaryKey val code: String,
    val nameAr: String,
    val nameEn: String,
    val symbol: String,
    val decimals: Int = 2,
    val isBase: Boolean = false,
    val isActive: Boolean = true
)

@Entity(
    tableName = "exchange_rates",
    primaryKeys = ["currencyCode", "effectiveAt"],
    foreignKeys = [ForeignKey(
        entity = CurrencyEntity::class,
        parentColumns = ["code"],
        childColumns = ["currencyCode"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("currencyCode")]
)
data class ExchangeRateEntity(
    val currencyCode: String,
    val effectiveAt: Long,
    val rateToBase: Double,
    val sourceNote: String = ""
)

@Entity(tableName = "accounts", indices = [Index(value = ["code"], unique = true)])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val nameAr: String,
    val nameEn: String,
    val type: String,
    val parentCode: String? = null,
    val isPosting: Boolean = true,
    val isActive: Boolean = true
)

@Entity(tableName = "units", indices = [Index(value = ["code"], unique = true)])
data class UnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val nameAr: String,
    val nameEn: String,
    val isActive: Boolean = true
)

@Entity(tableName = "warehouses", indices = [Index(value = ["code"], unique = true)])
data class WarehouseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val nameAr: String,
    val nameEn: String,
    val location: String = "",
    val isActive: Boolean = true
)

@Entity(
    tableName = "items",
    indices = [Index(value = ["code"], unique = true), Index("baseUnitId")],
    foreignKeys = [ForeignKey(
        entity = UnitEntity::class,
        parentColumns = ["id"],
        childColumns = ["baseUnitId"],
        onDelete = ForeignKey.RESTRICT
    )]
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val nameAr: String,
    val nameEn: String,
    val category: String,
    val baseUnitId: Long,
    val reorderLevel: Double = 0.0,
    val shelfLifeDays: Int? = null,
    val lotTracked: Boolean = false,
    val expiryTracked: Boolean = false,
    val isActive: Boolean = true
)


@Entity(tableName = "number_sequences")
data class NumberSequenceEntity(
    @PrimaryKey val sequenceKey: String,
    val lastValue: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "journal_entries", indices = [Index(value = ["entryNo"], unique = true)])
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryNo: String,
    val entryDate: Long,
    val description: String,
    val currencyCode: String,
    val exchangeRate: Double = 1.0,
    val sourceType: String = "MANUAL",
    val sourceId: String? = null,
    val status: String = "POSTED",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "journal_lines",
    foreignKeys = [
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("entryId"), Index("accountId")]
)
data class JournalLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val accountId: Long,
    val debit: Double = 0.0,
    val credit: Double = 0.0,
    val memo: String = ""
)
