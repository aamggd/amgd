package com.fush.erp.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * v213 commercial tenant safety marker.
 *
 * The marker is stored inside the Room database (and therefore inside portable backups),
 * so a database restored from Company A cannot later be synchronized into Company B by
 * merely changing the cloud login on the device.
 */
@Entity(
    tableName = "cloud_tenant_binding",
    indices = [Index(value = ["organization_id"], unique = true)],
)
data class CloudTenantBindingEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    @ColumnInfo(name = "organization_id") val organizationId: String,
    @ColumnInfo(name = "bound_at") val boundAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
