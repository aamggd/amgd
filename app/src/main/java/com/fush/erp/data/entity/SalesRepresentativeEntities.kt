package com.fush.erp.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sales_representatives",
    indices = [
        Index(value = ["code"], unique = true),
        Index(value = ["employeeId"], unique = true),
        Index("status"),
        Index("territory")
    ],
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class SalesRepresentativeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val employeeId: Long? = null,
    val repType: String = "EXTERNAL",
    val fullNameAr: String,
    val fullNameEn: String = "",
    val phone: String = "",
    val territory: String = "",
    val commissionRatePct: Double = 0.0,
    @ColumnInfo(defaultValue = "0.0") val freeQtyLimitPct: Double = 0.0,
    val status: String = "ACTIVE",
    val notes: String = "",
    val createdBy: Long,
    val createdAt: Long = com.fush.erp.domain.TrustedTimeService.now(),
    val updatedAt: Long = com.fush.erp.domain.TrustedTimeService.now()
)
