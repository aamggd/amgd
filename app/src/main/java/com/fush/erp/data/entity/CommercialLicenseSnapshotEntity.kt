package com.fush.erp.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * v214 server-authoritative commercial license cache.
 *
 * The row is only a signed-in/offline decision cache; Supabase remains the authority.  No raw
 * activation code is stored locally.  Keeping the last verified snapshot in Room lets a customer
 * work during an explicitly granted offline grace period without weakening tenant isolation.
 */
@Entity(
    tableName = "commercial_license_snapshot",
    indices = [Index(value = ["organization_id"], unique = true)]
)
data class CommercialLicenseSnapshotEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "license_id") val licenseId: String,
    @ColumnInfo(name = "device_entitlement_id") val deviceEntitlementId: String?,
    @ColumnInfo(name = "plan_code") val planCode: String,
    val status: String,
    @ColumnInfo(name = "is_trial") val isTrial: Boolean,
    @ColumnInfo(name = "starts_at") val startsAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    @ColumnInfo(name = "offline_grace_until") val offlineGraceUntil: Long,
    @ColumnInfo(name = "max_devices") val maxDevices: Int,
    @ColumnInfo(name = "verified_at") val verifiedAt: Long,
    @ColumnInfo(name = "server_time") val serverTime: Long,
)
