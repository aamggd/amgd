package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fixed_assets",
    indices = [
        Index(value = ["assetNo"], unique = true),
        Index(value = ["maintenanceAssetId"], unique = true),
        Index("status"),
        Index("inServiceDate"),
        Index("assetAccountId"),
        Index("accumulatedDepreciationAccountId"),
        Index("depreciationExpenseAccountId"),
        Index(value = ["acquisitionJournalEntryId"], unique = true),
        Index(value = ["acquisitionReversalEntryId"], unique = true),
        Index(value = ["disposalJournalEntryId"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["maintenanceAssetId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["assetAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accumulatedDepreciationAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["depreciationExpenseAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["acquisitionJournalEntryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["acquisitionReversalEntryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["disposalJournalEntryId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class FixedAssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetNo: String,
    val maintenanceAssetId: Long? = null,
    val nameAr: String,
    val nameEn: String = "",
    val category: String = "OTHER",
    val acquisitionDate: Long,
    val inServiceDate: Long,
    val acquisitionCostBase: Double,
    val residualValueBase: Double = 0.0,
    val usefulLifeMonths: Int,
    val depreciationMethod: String = "STRAIGHT_LINE_MONTHLY",
    val assetAccountId: Long,
    val accumulatedDepreciationAccountId: Long,
    val depreciationExpenseAccountId: Long,
    val acquisitionMode: String,
    val acquisitionJournalEntryId: Long? = null,
    val acquisitionReversalEntryId: Long? = null,
    val status: String = "ACTIVE",
    val disposalDate: Long? = null,
    val disposalProceedsBase: Double = 0.0,
    val disposalGainLossBase: Double = 0.0,
    val disposalJournalEntryId: Long? = null,
    val disposalReason: String = "",
    val cancellationReason: String = "",
    val cancelledBy: Long? = null,
    val cancelledAt: Long? = null,
    val notes: String = "",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "fixed_asset_depreciations",
    indices = [
        Index("assetId"),
        Index("depreciationDate"),
        Index("status"),
        Index(value = ["journalEntryId"], unique = true),
        Index(value = ["reversalEntryId"], unique = true),
        Index(value = ["assetId", "fiscalYear", "periodNo"])
    ],
    foreignKeys = [
        ForeignKey(entity = FixedAssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["journalEntryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["reversalEntryId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class FixedAssetDepreciationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val fiscalYear: Int,
    val periodNo: Int,
    val depreciationDate: Long,
    val amountBase: Double,
    val status: String = "POSTED",
    val journalEntryId: Long,
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val reversalEntryId: Long? = null,
    val reversalReason: String = "",
    val reversedBy: Long? = null,
    val reversedAt: Long? = null
)

@Entity(
    tableName = "fixed_asset_disposals",
    indices = [
        Index("assetId"),
        Index("disposalDate"),
        Index("status"),
        Index(value = ["journalEntryId"], unique = true),
        Index(value = ["reversalEntryId"], unique = true),
        Index("treasuryAccountId")
    ],
    foreignKeys = [
        ForeignKey(entity = FixedAssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = TreasuryAccountEntity::class, parentColumns = ["id"], childColumns = ["treasuryAccountId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["journalEntryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = JournalEntryEntity::class, parentColumns = ["id"], childColumns = ["reversalEntryId"], onDelete = ForeignKey.RESTRICT)
    ]
)
data class FixedAssetDisposalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val disposalDate: Long,
    val proceedsBase: Double,
    val treasuryAccountId: Long? = null,
    val currencyCode: String = "YER_NEW",
    val exchangeRate: Double = 1.0,
    val proceedsOriginal: Double = 0.0,
    val acquisitionCostBase: Double,
    val accumulatedDepreciationBase: Double,
    val carryingValueBase: Double,
    val gainLossBase: Double,
    val status: String = "POSTED",
    val journalEntryId: Long,
    val reason: String,
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val reversalEntryId: Long? = null,
    val reversalReason: String = "",
    val reversedBy: Long? = null,
    val reversedAt: Long? = null
)

data class FixedAssetRegisterRow(
    val id: Long,
    val assetNo: String,
    val maintenanceAssetId: Long?,
    val nameAr: String,
    val category: String,
    val acquisitionDate: Long,
    val inServiceDate: Long,
    val acquisitionCostBase: Double,
    val residualValueBase: Double,
    val usefulLifeMonths: Int,
    val depreciationMethod: String,
    val status: String,
    val accumulatedDepreciationBase: Double,
    val netBookValueBase: Double,
    val disposalDate: Long?,
    val disposalProceedsBase: Double,
    val disposalGainLossBase: Double
)
