package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "controlled_documents", indices = [Index(value = ["documentCode"], unique = true), Index("status"), Index("category")])
data class ControlledDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentCode: String,
    val titleAr: String,
    val category: String,
    val versionNo: Int = 1,
    val status: String = "DRAFT",
    val effectiveAt: Long? = null,
    val reviewDueAt: Long? = null,
    val ownerRole: String = "",
    val contentSummary: String = "",
    val createdBy: Long,
    val approvedBy: Long? = null,
    val approvedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "change_requests", indices = [Index(value = ["requestNo"], unique = true), Index("status"), Index("changeType")])
data class ChangeRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestNo: String,
    val changeType: String,
    val subject: String,
    val reason: String,
    val qualityImpact: String = "",
    val financialImpact: String = "",
    val inventoryImpact: String = "",
    val status: String = "SUBMITTED",
    val requestedBy: Long,
    val approvedBy: Long? = null,
    val approvedAt: Long? = null,
    val implementedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "approval_requests", indices = [Index("status"), Index("referenceType"), Index("requestedAt")])
data class ApprovalRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val referenceType: String,
    val referenceId: String,
    val title: String,
    val requestedRole: String,
    val requestedBy: Long,
    val status: String = "PENDING",
    val decisionBy: Long? = null,
    val decisionAt: Long? = null,
    val decisionNote: String = "",
    val requestedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "audit_events", indices = [Index("eventAt"), Index("entityType"), Index("userId")])
data class AuditEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventAt: Long = System.currentTimeMillis(),
    val userId: Long,
    val action: String,
    val entityType: String,
    val entityId: String,
    val oldValue: String = "",
    val newValue: String = "",
    val reason: String = "",
    val deviceInfo: String = "ANDROID"
)
