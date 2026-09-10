package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.*

class RiskControlService(private val db: FushDatabase) {
    private val numbering = AutoNumberService(db)

    suspend fun createRisk(
        title: String,
        category: String,
        description: String,
        likelihood: Int,
        impact: Int,
        mitigationPlan: String,
        ownerRole: String,
        dueAt: Long?,
        userId: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.RISK_MANAGE)
        val score = RiskControlMath.score(likelihood, impact)
        val no = numbering.nextDocumentNo("RSK")
        val id = db.riskControlDao().insertRisk(
            RiskEntity(
                riskNo = no,
                title = title.trim(),
                category = category.trim().ifBlank { "OPERATIONAL" },
                description = description.trim(),
                likelihood = likelihood,
                impact = impact,
                inherentScore = score,
                mitigationPlan = mitigationPlan.trim(),
                residualLikelihood = likelihood,
                residualImpact = impact,
                residualScore = score,
                ownerRole = ownerRole.trim().ifBlank { "ADMIN" },
                dueAt = dueAt,
                createdBy = userId
            )
        )
        db.governanceDao().insertAudit(AuditEventEntity(userId=userId, action="CREATE", entityType="RISK", entityId=id.toString(), newValue="$no score=$score", reason=title))
        id
    }

    suspend fun createControl(
        title: String,
        controlType: String,
        frequency: String,
        ownerRole: String,
        relatedRiskId: Long?,
        designDescription: String,
        evidenceRequired: String,
        userId: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.RISK_MANAGE)
        val code = numbering.nextDocumentNo("CTL")
        val id = db.riskControlDao().insertControl(
            InternalControlEntity(
                controlCode = code,
                title = title.trim(),
                controlType = controlType.trim().ifBlank { "PREVENTIVE" },
                frequency = frequency.trim().ifBlank { "MONTHLY" },
                ownerRole = ownerRole.trim().ifBlank { "ADMIN" },
                relatedRiskId = relatedRiskId,
                designDescription = designDescription.trim(),
                evidenceRequired = evidenceRequired.trim()
            )
        )
        db.governanceDao().insertAudit(AuditEventEntity(userId=userId, action="CREATE", entityType="CONTROL", entityId=id.toString(), newValue=code, reason=title))
        id
    }

    suspend fun recordControlTest(
        controlId: Long,
        result: String,
        evidenceRef: String,
        finding: String,
        severity: String,
        nextDueAt: Long?,
        userId: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.RISK_MANAGE)
        val normalized = result.uppercase()
        require(normalized in setOf("PASS", "FAIL")) { "النتيجة يجب أن تكون PASS أو FAIL" }
        val testId = db.riskControlDao().insertTest(
            ControlTestEntity(
                controlId = controlId,
                result = normalized,
                evidenceRef = evidenceRef.trim(),
                finding = finding.trim(),
                testedBy = userId,
                nextDueAt = nextDueAt
            )
        )
        if (normalized == "FAIL") {
            val control = db.riskControlDao().controlById(controlId)
            val no = numbering.nextDocumentNo("EXC")
            db.riskControlDao().insertException(
                ControlExceptionEntity(
                    exceptionNo = no,
                    controlId = controlId,
                    sourceType = "CONTROL_TEST",
                    sourceId = testId.toString(),
                    severity = severity.uppercase().ifBlank { "MEDIUM" },
                    description = finding.trim().ifBlank { "فشل اختبار الرقابة ${control?.controlCode ?: controlId}" },
                    ownerRole = control?.ownerRole ?: "ADMIN",
                    dueAt = nextDueAt,
                    openedBy = userId
                )
            )
        }
        db.governanceDao().insertAudit(AuditEventEntity(userId=userId, action="TEST_$normalized", entityType="CONTROL", entityId=controlId.toString(), newValue="test=$testId", reason=finding))
        testId
    }


    suspend fun createManualException(
        description: String,
        severity: String,
        ownerRole: String,
        dueAt: Long?,
        userId: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.RISK_MANAGE)
        val no = numbering.nextDocumentNo("EXC")
        val id = db.riskControlDao().insertException(
            ControlExceptionEntity(
                exceptionNo = no,
                controlId = null,
                sourceType = "MANUAL",
                sourceId = "",
                severity = severity.uppercase().ifBlank { "MEDIUM" },
                description = description.trim(),
                ownerRole = ownerRole.trim().ifBlank { "ADMIN" },
                dueAt = dueAt,
                openedBy = userId
            )
        )
        db.governanceDao().insertAudit(AuditEventEntity(userId=userId, action="CREATE", entityType="CONTROL_EXCEPTION", entityId=id.toString(), newValue=no, reason=description.trim()))
        id
    }

    suspend fun reviewRisk(
        row: RiskEntity,
        residualLikelihood: Int,
        residualImpact: Int,
        status: String,
        mitigationPlan: String,
        userId: Long
    ) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.RISK_MANAGE)
        val score = RiskControlMath.score(residualLikelihood, residualImpact)
        val now = System.currentTimeMillis()
        db.riskControlDao().updateRisk(
            row.copy(
                residualLikelihood = residualLikelihood,
                residualImpact = residualImpact,
                residualScore = score,
                status = status,
                mitigationPlan = mitigationPlan.trim(),
                reviewedAt = now
            )
        )
        db.governanceDao().insertAudit(AuditEventEntity(userId=userId, action="REVIEW", entityType="RISK", entityId=row.id.toString(), oldValue="${row.residualScore}/${row.status}", newValue="$score/$status", reason=mitigationPlan.trim()))
    }

    suspend fun closeException(row: ControlExceptionEntity, closureNote: String, userId: Long) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.RISK_MANAGE)
        require(row.openedBy != userId) { "فصل المهام: لا يجوز لمن فتح الاستثناء أن يعتمد إغلاقه." }
        require(closureNote.isNotBlank()) { "اكتب ملاحظة الإغلاق." }
        val now = System.currentTimeMillis()
        db.riskControlDao().updateException(row.copy(status="CLOSED", approvedBy=userId, approvedAt=now, closureNote=closureNote.trim(), closedAt=now))
        db.governanceDao().insertAudit(AuditEventEntity(userId=userId, action="CLOSE", entityType="CONTROL_EXCEPTION", entityId=row.id.toString(), oldValue=row.status, newValue="CLOSED", reason=closureNote.trim()))
    }
}
