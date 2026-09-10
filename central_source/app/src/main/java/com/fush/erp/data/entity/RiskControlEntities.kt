package com.fush.erp.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "risk_register",
    indices = [
        Index(value = ["riskNo"], unique = true),
        Index("status"),
        Index("category"),
        Index("ownerRole")
    ]
)
data class RiskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val riskNo: String,
    val title: String,
    val category: String,
    val description: String,
    val likelihood: Int,
    val impact: Int,
    val inherentScore: Int,
    val mitigationPlan: String = "",
    val residualLikelihood: Int = likelihood,
    val residualImpact: Int = impact,
    val residualScore: Int = inherentScore,
    val ownerRole: String = "ADMIN",
    val status: String = "OPEN",
    val dueAt: Long? = null,
    val createdBy: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val reviewedAt: Long? = null
)

@Entity(
    tableName = "internal_controls",
    foreignKeys = [ForeignKey(
        entity = RiskEntity::class,
        parentColumns = ["id"],
        childColumns = ["relatedRiskId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [
        Index(value = ["controlCode"], unique = true),
        Index("relatedRiskId"),
        Index("ownerRole"),
        Index("isActive")
    ]
)
data class InternalControlEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val controlCode: String,
    val title: String,
    val controlType: String,
    val frequency: String,
    val ownerRole: String,
    val relatedRiskId: Long? = null,
    val designDescription: String = "",
    val evidenceRequired: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "control_tests",
    foreignKeys = [ForeignKey(
        entity = InternalControlEntity::class,
        parentColumns = ["id"],
        childColumns = ["controlId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("controlId"), Index("result"), Index("testedAt")]
)
data class ControlTestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val controlId: Long,
    val testedAt: Long = System.currentTimeMillis(),
    val result: String,
    val evidenceRef: String = "",
    val finding: String = "",
    val testedBy: Long,
    val nextDueAt: Long? = null
)

@Entity(
    tableName = "control_exceptions",
    foreignKeys = [ForeignKey(
        entity = InternalControlEntity::class,
        parentColumns = ["id"],
        childColumns = ["controlId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [
        Index(value = ["exceptionNo"], unique = true),
        Index("controlId"),
        Index("status"),
        Index("severity"),
        Index("dueAt")
    ]
)
data class ControlExceptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exceptionNo: String,
    val controlId: Long? = null,
    val sourceType: String = "CONTROL_TEST",
    val sourceId: String = "",
    val severity: String = "MEDIUM",
    val description: String,
    val status: String = "OPEN",
    val ownerRole: String = "ADMIN",
    val dueAt: Long? = null,
    val openedBy: Long,
    val detectedAt: Long = System.currentTimeMillis(),
    val approvedBy: Long? = null,
    val approvedAt: Long? = null,
    val closureNote: String = "",
    val closedAt: Long? = null
)

@Entity(
    tableName = "segregation_rules",
    indices = [Index(value = ["ruleCode"], unique = true), Index("actionKey"), Index("isActive")]
)
data class SegregationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleCode: String,
    val actionKey: String,
    val initiatorRole: String,
    val approverRole: String,
    val description: String,
    val requireDifferentUser: Boolean = true,
    val requireDifferentRole: Boolean = false,
    val isActive: Boolean = true
)
