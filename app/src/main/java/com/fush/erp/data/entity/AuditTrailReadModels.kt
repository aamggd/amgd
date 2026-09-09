package com.fush.erp.data.entity

import androidx.room.ColumnInfo

/** Read-only projection used by Governance > Audit Trail. It never mutates audit_events. */
data class AuditTrailRow(
    val id: Long,
    val eventAt: Long,
    val userId: Long,
    val actorDisplayName: String?,
    val actorUsername: String?,
    @ColumnInfo(name = "actionCode") val action: String,
    val entityType: String,
    val entityId: String,
    val oldValue: String,
    val newValue: String,
    val reason: String,
    val deviceInfo: String,
    val sectionCode: String,
    val referenceDisplay: String,
)
