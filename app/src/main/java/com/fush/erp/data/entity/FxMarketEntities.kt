package com.fush.erp.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Normalized online market quote cached locally after it is fetched through the Supabase edge function.
 * rateYer is expressed in the local YER note family for marketRegion:
 *  - ADEN  -> YER_NEW
 *  - SANAA -> YER_OLD
 */
@Entity(
    tableName = "fx_market_rates",
    indices = [
        Index("batchId"),
        Index("fetchedAt"),
        Index(value = ["batchId", "marketRegion", "currencyCode", "rateType"], unique = true)
    ]
)
data class FxMarketRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: String,
    val marketRegion: String,
    val currencyCode: String,
    val rateType: String,
    val rateYer: Double,
    val primarySource: String,
    val primarySourceUrl: String,
    val sourcePublishedAt: Long,
    @ColumnInfo(defaultValue = "NULL") val comparisonRateYer: Double? = null,
    @ColumnInfo(defaultValue = "''") val comparisonSource: String = "",
    @ColumnInfo(defaultValue = "''") val comparisonSourceUrl: String = "",
    @ColumnInfo(defaultValue = "NULL") val comparisonPublishedAt: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val variancePercent: Double? = null,
    @ColumnInfo(defaultValue = "'PRIMARY_ONLY'") val sourceStatus: String = "PRIMARY_ONLY",
    val fetchedAt: Long,
    @ColumnInfo(defaultValue = "''") val rawHash: String = ""
)

@Entity(tableName = "fx_rate_settings")
data class FxRateSettingsEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(defaultValue = "'ADEN'") val defaultMarketRegion: String = "ADEN",
    @ColumnInfo(defaultValue = "'SELL'") val defaultRateType: String = "SELL",
    @ColumnInfo(defaultValue = "72") val staleAfterHours: Int = 72,
    @ColumnInfo(defaultValue = "2.0") val warningVariancePct: Double = 2.0,
    @ColumnInfo(defaultValue = "4.0") val highVariancePct: Double = 4.0,
    @ColumnInfo(defaultValue = "NULL") val updatedBy: Long? = null,
    val updatedAt: Long = com.fush.erp.domain.TrustedTimeService.now()
)
