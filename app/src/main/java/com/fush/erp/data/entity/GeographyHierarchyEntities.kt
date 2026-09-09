package com.fush.erp.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Stable Yemen administrative level 1. IDs are portable across devices/cloud sync. */
@Entity(
    tableName = "geo_governorates",
    indices = [Index(value = ["code"], unique = true), Index("nameAr"), Index("isActive")]
)
data class GovernorateEntity(
    @PrimaryKey val id: String,
    val code: String,
    val nameAr: String,
    val nameEn: String = "",
    val sortOrder: Int = 0,
    val source: String = "USER",
    val isOfficialSeed: Boolean = false,
    val isActive: Boolean = true,
    val updatedBy: Long? = null,
    val updatedAt: Long = com.fush.erp.domain.TrustedTimeService.now()
)

/** Stable Yemen administrative level 2. */
@Entity(
    tableName = "geo_districts",
    indices = [
        Index(value = ["code"], unique = true),
        Index("governorateId"),
        Index(value = ["governorateId", "nameAr"]),
        Index("isActive")
    ],
    foreignKeys = [ForeignKey(
        entity = GovernorateEntity::class,
        parentColumns = ["id"], childColumns = ["governorateId"],
        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE
    )]
)
data class DistrictEntity(
    @PrimaryKey val id: String,
    val code: String,
    val governorateId: String,
    val nameAr: String,
    val nameEn: String = "",
    val sortOrder: Int = 0,
    val source: String = "USER",
    val isOfficialSeed: Boolean = false,
    val isActive: Boolean = true,
    val updatedBy: Long? = null,
    val updatedAt: Long = com.fush.erp.domain.TrustedTimeService.now()
)

/** Operational level 3. Official starter rows are Yemen uzaal; users may add local areas later. */
@Entity(
    tableName = "geo_areas",
    indices = [
        Index(value = ["code"], unique = true),
        Index("districtId"),
        Index(value = ["districtId", "nameAr"]),
        Index("isActive")
    ],
    foreignKeys = [ForeignKey(
        entity = DistrictEntity::class,
        parentColumns = ["id"], childColumns = ["districtId"],
        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE
    )]
)
data class AreaEntity(
    @PrimaryKey val id: String,
    val code: String,
    val districtId: String,
    val nameAr: String,
    val nameEn: String = "",
    val sortOrder: Int = 0,
    val source: String = "USER",
    val isOfficialSeed: Boolean = false,
    val isActive: Boolean = true,
    val updatedBy: Long? = null,
    val updatedAt: Long = com.fush.erp.domain.TrustedTimeService.now()
)

/** Legacy free-text aliases used only for safe migration/reconciliation. */
@Entity(
    tableName = "geo_governorate_aliases",
    indices = [Index("governorateId")],
    foreignKeys = [ForeignKey(
        entity = GovernorateEntity::class,
        parentColumns = ["id"], childColumns = ["governorateId"],
        onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE
    )]
)
data class GovernorateAliasEntity(
    @PrimaryKey val normalizedAlias: String,
    val governorateId: String,
    val displayAlias: String = "",
    @ColumnInfo(defaultValue = "'MIGRATION'") val source: String = "MIGRATION"
)
