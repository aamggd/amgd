package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fush.erp.domain.TrustedTimeService

/**
 * v211: AI-generated operational drafts are isolated from accounting/stock documents.
 * A row in this table is never a posted ERP document and has no financial/inventory effect.
 */
@Entity(
    tableName = "fush_ai_drafts",
    indices = [Index("requestedBy"), Index("status"), Index("draftType"), Index("createdAt")],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["requestedBy"],
            onDelete = ForeignKey.RESTRICT,
        )
    ]
)
data class FushAiDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val draftType: String,
    val status: String = "PENDING",
    val title: String,
    val summary: String,
    val payloadJson: String,
    val requestedBy: Long,
    val createdAt: Long = TrustedTimeService.now(),
    val approvedBy: Long? = null,
    val approvedAt: Long? = null,
    val cancelledBy: Long? = null,
    val cancelledAt: Long? = null,
)
