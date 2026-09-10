package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fx_snapshots",
    indices = [Index(value = ["effectiveAt"], unique = true)]
)
data class FxSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val effectiveAt: Long,
    val usdNewYer: Double,
    val usdOldYer: Double,
    val oldYerToNewYer: Double,
    val sourceNote: String = "",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "province_policies",
    foreignKeys = [ForeignKey(
        entity = CurrencyEntity::class,
        parentColumns = ["code"],
        childColumns = ["currencyCode"],
        onDelete = ForeignKey.RESTRICT
    )],
    indices = [Index("currencyCode"), Index("isActive")]
)
data class ProvincePolicyEntity(
    @PrimaryKey val code: String,
    val nameAr: String,
    val currencyCode: String,
    val defaultTransportPerCartonBase: Double = 0.0,
    val requiresDailyFx: Boolean = false,
    val requiresActualTransport: Boolean = false,
    val requiresFeesAndCustoms: Boolean = false,
    val notes: String = "",
    val isActive: Boolean = true
)

@Entity(
    tableName = "invoice_geographic_costs",
    foreignKeys = [ForeignKey(
        entity = SalesInvoiceEntity::class,
        parentColumns = ["id"],
        childColumns = ["invoiceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["invoiceId"], unique = true), Index("province"), Index("recordedAt")]
)
data class InvoiceGeographicCostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val province: String,
    val cartonsEquivalent: Double,
    val transportCostBase: Double,
    val feesCustomsCostBase: Double,
    val otherDirectCostBase: Double,
    val notes: String = "",
    val recordedBy: Long,
    val recordedAt: Long = System.currentTimeMillis()
)

data class InvoiceProfitabilityRow(
    val invoiceId: Long,
    val invoiceNo: String,
    val invoiceDate: Long,
    val customerId: Long,
    val customerName: String,
    val province: String,
    val currencyCode: String,
    val netRevenueBase: Double,
    val netCogsBase: Double,
    val commissionBase: Double,
    val geographicCostBase: Double,
    val profitBase: Double
)

data class ProvinceProfitabilityRow(
    val province: String,
    val invoiceCount: Int,
    val netRevenueBase: Double,
    val netCogsBase: Double,
    val commissionBase: Double,
    val geographicCostBase: Double,
    val profitBase: Double
)
