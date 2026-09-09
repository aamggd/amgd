package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fush.erp.domain.TrustedTimeService

@Entity(
    tableName = "support_tickets",
    indices = [Index("companyId"), Index("branchId"), Index("requestedBy"), Index("status"), Index("createdAt")],
    foreignKeys = [ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["requestedBy"], onDelete = ForeignKey.RESTRICT)]
)
data class SupportTicketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val companyId: String,
    val branchId: String? = null,
    val requestedBy: Long,
    val problemDescription: String,
    val status: String = "OPEN",
    val createdAt: Long = TrustedTimeService.now(),
    val closedAt: Long? = null,
)

@Entity(
    tableName = "support_sessions",
    indices = [Index("ticketId"), Index("companyId"), Index("supportUserId"), Index("activatedBy"), Index("status"), Index("expiresAt")],
    foreignKeys = [
        ForeignKey(entity = SupportTicketEntity::class, parentColumns = ["id"], childColumns = ["ticketId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["supportUserId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["activatedBy"], onDelete = ForeignKey.RESTRICT),
    ]
)
data class SupportSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticketId: Long,
    val companyId: String,
    val branchId: String? = null,
    val supportUserId: Long,
    val activatedBy: Long,
    val status: String = "ACTIVE",
    val startedAt: Long = TrustedTimeService.now(),
    val expiresAt: Long,
    val revokedAt: Long? = null,
    val closedAt: Long? = null,
    val closeReason: String = "",
)

@Entity(
    tableName = "support_snapshots",
    indices = [Index("ticketId"), Index("sessionId"), Index("supportUserId"), Index("recordTable", "recordId"), Index("createdAt")],
    foreignKeys = [
        ForeignKey(entity = SupportTicketEntity::class, parentColumns = ["id"], childColumns = ["ticketId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SupportSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["supportUserId"], onDelete = ForeignKey.RESTRICT),
    ]
)
data class SupportSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supportUserId: Long,
    val companyId: String,
    val ticketId: Long,
    val sessionId: Long,
    val module: String,
    val recordTable: String,
    val recordId: String,
    val beforeValue: String,
    val reason: String,
    val createdAt: Long = TrustedTimeService.now(),
)

@Entity(
    tableName = "support_audit_log",
    indices = [Index("supportUserId"), Index("companyId"), Index("ticketId"), Index("sessionId"), Index("eventAt"), Index("recordTable", "recordId")],
    foreignKeys = [
        ForeignKey(entity = SupportTicketEntity::class, parentColumns = ["id"], childColumns = ["ticketId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SupportSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["supportUserId"], onDelete = ForeignKey.RESTRICT),
    ]
)
data class SupportAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supportUserId: Long,
    val companyId: String,
    val ticketId: Long,
    val sessionId: Long,
    val snapshotId: Long? = null,
    val action: String,
    val module: String,
    val recordTable: String,
    val recordId: String,
    val beforeValue: String = "",
    val afterValue: String = "",
    val reason: String,
    val validationSummary: String = "",
    val eventAt: Long = TrustedTimeService.now(),
    val ipAddress: String = "",
    val deviceInfo: String = "ANDROID",
)

@Entity(
    tableName = "support_validation_results",
    indices = [Index("ticketId"), Index("sessionId"), Index("supportUserId"), Index("eventAt")],
    foreignKeys = [
        ForeignKey(entity = SupportTicketEntity::class, parentColumns = ["id"], childColumns = ["ticketId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SupportSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["supportUserId"], onDelete = ForeignKey.RESTRICT),
    ]
)
data class SupportValidationResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supportUserId: Long,
    val companyId: String,
    val ticketId: Long,
    val sessionId: Long,
    val command: String,
    val status: String,
    val summary: String,
    val eventAt: Long = TrustedTimeService.now(),
)

@Entity(
    tableName = "vendor_support_identities",
    indices = [
        Index(value = ["supportUserId"]),
        Index(value = ["installationBinding"]),
        Index(value = ["packageNonce"], unique = true),
        Index(value = ["provisionedBy"]),
        Index(value = ["credentialVersion"]),
        Index(value = ["supersedesIdentityId"]),
    ],
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["supportUserId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["provisionedBy"], onDelete = ForeignKey.RESTRICT),
    ]
)
data class VendorSupportIdentityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supportUserId: Long,
    val installationBinding: String,
    val keyId: String,
    val packageNonce: String,
    val packageFingerprint: String,
    val provisionedBy: Long,
    val provisionedAt: Long = TrustedTimeService.now(),
    val lifecycleAction: String = "PROVISION",
    val credentialVersion: Long = 1,
    val supersedesIdentityId: Long? = null,
    val previousPackageFingerprint: String = "",
)

@Entity(
    tableName = "vendor_support_keys",
    indices = [
        Index(value = ["keyId"], unique = true),
        Index(value = ["rotatedBy"]),
        Index(value = ["activatedAt"]),
    ],
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["rotatedBy"], onDelete = ForeignKey.RESTRICT),
    ]
)
data class VendorSupportKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyId: String,
    val publicKeyDerBase64: String,
    val supersedesKeyId: String,
    val packageFingerprint: String,
    val rotatedBy: Long,
    val activatedAt: Long = TrustedTimeService.now(),
)

data class SupportFindingRow(
    val severity: String,
    val module: String,
    val recordType: String,
    val recordId: String,
    val reference: String,
    val details: String,
)

data class SupportSessionViewRow(
    val id: Long,
    val ticketId: Long,
    val companyId: String,
    val branchId: String?,
    val supportUserId: Long,
    val supportDisplayName: String,
    val supportUsername: String,
    val activatedBy: Long,
    val status: String,
    val startedAt: Long,
    val expiresAt: Long,
    val revokedAt: Long?,
    val closedAt: Long?,
    val closeReason: String,
)
