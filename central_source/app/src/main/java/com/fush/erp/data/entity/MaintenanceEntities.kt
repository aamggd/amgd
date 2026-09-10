package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "assets",
    indices = [Index(value = ["code"], unique = true), Index("status"), Index("assetType")]
)
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val nameAr: String,
    val nameEn: String = "",
    val assetType: String,
    val location: String,
    val serialNo: String = "",
    val status: String = "ACTIVE",
    val criticality: String = "MEDIUM",
    val usageHours: Double = 0.0,
    val usageBatches: Int = 0,
    val calibrationRequired: Boolean = false,
    val inspectionDueAt: Long? = null,
    val calibrationDueAt: Long? = null,
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "maintenance_plans",
    indices = [Index("assetId"), Index("nextDueAt")],
    foreignKeys = [ForeignKey(
        entity = AssetEntity::class,
        parentColumns = ["id"],
        childColumns = ["assetId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class MaintenancePlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val nameAr: String,
    val frequencyType: String,
    val intervalDays: Int? = null,
    val checklist: String = "",
    val lastCompletedAt: Long? = null,
    val nextDueAt: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "maintenance_work_orders",
    indices = [Index(value = ["workOrderNo"], unique = true), Index("assetId"), Index("planId"), Index("status"), Index("dueAt")],
    foreignKeys = [
        ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MaintenancePlanEntity::class, parentColumns = ["id"], childColumns = ["planId"], onDelete = ForeignKey.SET_NULL)
    ]
)
data class MaintenanceWorkOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workOrderNo: String,
    val assetId: Long,
    val planId: Long? = null,
    val workType: String,
    val openedAt: Long,
    val dueAt: Long? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val status: String = "OPEN",
    val problem: String = "",
    val actionTaken: String = "",
    val downtimeMinutes: Int = 0,
    val costBase: Double = 0.0,
    val technician: String = "",
    val returnToServiceApprovedBy: Long? = null,
    val returnToServiceAt: Long? = null,
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "breakdowns",
    indices = [Index(value = ["breakdownNo"], unique = true), Index("assetId"), Index("occurredAt"), Index("workOrderId")],
    foreignKeys = [
        ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MaintenanceWorkOrderEntity::class, parentColumns = ["id"], childColumns = ["workOrderId"], onDelete = ForeignKey.SET_NULL)
    ]
)
data class BreakdownEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val breakdownNo: String,
    val assetId: Long,
    val occurredAt: Long,
    val severity: String,
    val description: String,
    val rootCause: String = "",
    val recurring: Boolean = false,
    val downtimeMinutes: Int = 0,
    val workOrderId: Long? = null,
    val capaReference: String = "",
    val status: String = "OPEN",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "asset_inspections",
    indices = [Index(value = ["inspectionNo"], unique = true), Index("assetId"), Index("inspectionDate"), Index("inspectionType")],
    foreignKeys = [ForeignKey(
        entity = AssetEntity::class,
        parentColumns = ["id"],
        childColumns = ["assetId"],
        onDelete = ForeignKey.RESTRICT
    )]
)
data class AssetInspectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inspectionNo: String,
    val assetId: Long,
    val inspectionType: String,
    val inspectionDate: Long,
    val result: String,
    val checklistResult: String,
    val findings: String = "",
    val correctiveAction: String = "",
    val inspectedBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "calibration_records",
    indices = [Index(value = ["calibrationNo"], unique = true), Index("assetId"), Index("checkedAt"), Index("dueAt")],
    foreignKeys = [ForeignKey(
        entity = AssetEntity::class,
        parentColumns = ["id"],
        childColumns = ["assetId"],
        onDelete = ForeignKey.RESTRICT
    )]
)
data class CalibrationRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val calibrationNo: String,
    val assetId: Long,
    val checkedAt: Long,
    val result: String,
    val referenceStandard: String = "",
    val measuredError: Double? = null,
    val tolerance: Double? = null,
    val dueAt: Long? = null,
    val certificateRef: String = "",
    val notes: String = "",
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "safety_incidents", indices = [Index(value = ["incidentNo"], unique = true), Index("occurredAt"), Index("incidentType"), Index("status")])
data class SafetyIncidentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val incidentNo: String,
    val occurredAt: Long,
    val incidentType: String,
    val area: String,
    val description: String,
    val injuryOrImpact: String = "",
    val immediateAction: String = "",
    val rootCause: String = "",
    val correctiveAction: String = "",
    val preventiveAction: String = "",
    val capaRequired: Boolean = false,
    val status: String = "OPEN",
    val createdBy: Long,
    val closedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "safety_inspections", indices = [Index(value = ["inspectionNo"], unique = true), Index("inspectionDate"), Index("result")])
data class SafetyInspectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inspectionNo: String,
    val inspectionDate: Long,
    val area: String,
    val inspectionType: String,
    val result: String,
    val findings: String = "",
    val correctiveAction: String = "",
    val dueAt: Long? = null,
    val closedAt: Long? = null,
    val inspectedBy: Long,
    val createdAt: Long = System.currentTimeMillis()
)

data class AssetSummary(
    val id: Long,
    val code: String,
    val nameAr: String,
    val assetType: String,
    val location: String,
    val status: String,
    val criticality: String,
    val inspectionDueAt: Long?,
    val calibrationDueAt: Long?,
    val openWorkOrders: Int,
    val overduePlans: Int
)

data class MaintenanceKpiRow(
    val duePreventive: Int,
    val completedPreventiveOnTime: Int,
    val closedWorkOrders: Int,
    val closedWorkOrdersOnTime: Int,
    val overdueEquipmentChecks: Int,
    val unplannedDowntimeMinutes: Int
)
