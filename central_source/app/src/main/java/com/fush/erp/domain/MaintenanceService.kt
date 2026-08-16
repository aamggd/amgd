package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MaintenanceService(private val db: FushDatabase) {

    data class Kpis(
        val preventiveCompliancePct: Double,
        val workOrdersClosedOnTimePct: Double,
        val overdueEquipmentChecks: Int,
        val unplannedDowntimeMinutes: Int
    )

    suspend fun createAsset(
        code: String,
        nameAr: String,
        assetType: String,
        location: String,
        serialNo: String = "",
        criticality: String = "MEDIUM",
        calibrationRequired: Boolean = false,
        inspectionDueAt: Long? = null,
        calibrationDueAt: Long? = null,
        notes: String = "",
        createdBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.MAINTENANCE_MANAGE)
        require(code.isNotBlank()) { "كود الأصل مطلوب" }
        require(nameAr.isNotBlank()) { "اسم الأصل مطلوب" }
        require(assetType in setOf("FILLING_MACHINE", "BURNER", "VESSEL", "MEASURING_TOOL", "SAFETY_EQUIPMENT", "OTHER")) { "نوع الأصل غير صالح" }
        require(criticality in setOf("LOW", "MEDIUM", "HIGH", "CRITICAL")) { "درجة أهمية الأصل غير صالحة" }
        db.maintenanceDao().insertAsset(
            AssetEntity(
                code = code.trim(),
                nameAr = nameAr.trim(),
                assetType = assetType,
                location = location.trim(),
                serialNo = serialNo.trim(),
                criticality = criticality,
                calibrationRequired = calibrationRequired,
                inspectionDueAt = inspectionDueAt,
                calibrationDueAt = calibrationDueAt,
                notes = notes.trim()
            )
        )
    }

    suspend fun createPlan(
        assetId: Long,
        nameAr: String,
        frequencyType: String,
        intervalDays: Int?,
        checklist: String,
        firstDueAt: Long? = null,
        createdBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.MAINTENANCE_MANAGE)
        requireNotNull(db.maintenanceDao().assetById(assetId)) { "الأصل غير موجود" }
        require(nameAr.isNotBlank()) { "اسم خطة الصيانة مطلوب" }
        require(frequencyType in setOf("BEFORE_EACH_RUN", "AFTER_EACH_BATCH", "WEEKLY", "MONTHLY", "QUARTERLY", "CUSTOM")) { "تكرار الصيانة غير صالح" }
        if (frequencyType in setOf("WEEKLY", "MONTHLY", "QUARTERLY", "CUSTOM")) {
            require(intervalDays != null && intervalDays > 0) { "حدد فترة الخطة بالأيام" }
        }
        db.maintenanceDao().insertPlan(
            MaintenancePlanEntity(
                assetId = assetId,
                nameAr = nameAr.trim(),
                frequencyType = frequencyType,
                intervalDays = intervalDays,
                checklist = checklist.trim(),
                nextDueAt = firstDueAt
            )
        )
    }

    suspend fun openPreventiveWorkOrder(planId: Long, dueAt: Long?, createdBy: Long): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.MAINTENANCE_MANAGE)
        val plan = requireNotNull(db.maintenanceDao().planById(planId)) { "خطة الصيانة غير موجودة" }
        val asset = requireNotNull(db.maintenanceDao().assetById(plan.assetId)) { "الأصل غير موجود" }
        db.maintenanceDao().insertWorkOrder(
            MaintenanceWorkOrderEntity(
                workOrderNo = documentNo("MWO"),
                assetId = asset.id,
                planId = plan.id,
                workType = "PREVENTIVE",
                openedAt = System.currentTimeMillis(),
                dueAt = dueAt ?: plan.nextDueAt,
                problem = plan.nameAr,
                createdBy = createdBy
            )
        )
    }

    suspend fun reportBreakdown(
        assetId: Long,
        severity: String,
        description: String,
        downtimeMinutes: Int,
        createdBy: Long,
        occurredAt: Long = System.currentTimeMillis()
    ): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.MAINTENANCE_MANAGE)
        require(description.isNotBlank()) { "وصف العطل مطلوب" }
        require(severity in setOf("LOW", "MEDIUM", "HIGH", "CRITICAL")) { "درجة العطل غير صالحة" }
        require(downtimeMinutes >= 0) { "زمن التوقف غير صالح" }
        val asset = requireNotNull(db.maintenanceDao().assetById(assetId)) { "الأصل غير موجود" }
        val ninetyDaysAgo = occurredAt - 90L * 86_400_000L
        val recurring = MaintenanceMath.recurringBreakdown(db.maintenanceDao().recentBreakdownCount(assetId, ninetyDaysAgo))
        val workOrderId = db.maintenanceDao().insertWorkOrder(
            MaintenanceWorkOrderEntity(
                workOrderNo = documentNo("CM"),
                assetId = asset.id,
                workType = "CORRECTIVE",
                openedAt = occurredAt,
                problem = description.trim(),
                downtimeMinutes = downtimeMinutes,
                createdBy = createdBy
            )
        )
        val breakdownId = db.maintenanceDao().insertBreakdown(
            BreakdownEntity(
                breakdownNo = documentNo("BRK"),
                assetId = asset.id,
                occurredAt = occurredAt,
                severity = severity,
                description = description.trim(),
                recurring = recurring,
                downtimeMinutes = downtimeMinutes,
                workOrderId = workOrderId,
                createdBy = createdBy
            )
        )
        if (severity in setOf("HIGH", "CRITICAL")) {
            db.maintenanceDao().updateAsset(asset.copy(status = "OUT_OF_SERVICE"))
        }
        breakdownId
    }

    suspend fun completeWorkOrder(
        workOrderId: Long,
        actionTaken: String,
        technician: String,
        costBase: Double,
        downtimeMinutes: Int,
        approveReturnToService: Boolean,
        approvedBy: Long?,
        performedBy: Long
    ) = db.withTransaction {
        db.requireUserPermission(performedBy, SecurityPermissions.MAINTENANCE_MANAGE)
        require(actionTaken.isNotBlank()) { "الإجراء المنفذ مطلوب" }
        require(costBase >= 0 && costBase.isFinite()) { "تكلفة الصيانة غير صالحة" }
        require(downtimeMinutes >= 0) { "زمن التوقف غير صالح" }
        if (approveReturnToService) require(approvedBy != null) { "اعتماد إعادة التشغيل مطلوب" }
        val row = requireNotNull(db.maintenanceDao().workOrderById(workOrderId)) { "أمر الصيانة غير موجود" }
        require(row.status !in setOf("COMPLETED", "CANCELLED")) { "أمر الصيانة مغلق" }
        val now = System.currentTimeMillis()
        db.maintenanceDao().updateWorkOrder(
            row.copy(
                status = "COMPLETED",
                startedAt = row.startedAt ?: now,
                completedAt = now,
                actionTaken = actionTaken.trim(),
                technician = technician.trim(),
                costBase = costBase,
                downtimeMinutes = downtimeMinutes,
                returnToServiceApprovedBy = if (approveReturnToService) approvedBy else null,
                returnToServiceAt = if (approveReturnToService) now else null
            )
        )
        row.planId?.let { planId ->
            db.maintenanceDao().planById(planId)?.let { plan ->
                db.maintenanceDao().updatePlan(
                    plan.copy(
                        lastCompletedAt = now,
                        nextDueAt = MaintenanceMath.nextDue(now, plan.intervalDays)
                    )
                )
            }
        }
        if (approveReturnToService) {
            db.maintenanceDao().assetById(row.assetId)?.let { asset ->
                db.maintenanceDao().updateAsset(asset.copy(status = "ACTIVE"))
            }
        }
    }

    suspend fun recordAssetInspection(
        assetId: Long,
        inspectionType: String,
        result: String,
        checklistResult: String,
        findings: String,
        correctiveAction: String,
        inspectedBy: Long,
        nextInspectionDueAt: Long? = null
    ): Long = db.withTransaction {
        db.requireUserPermission(inspectedBy, SecurityPermissions.MAINTENANCE_MANAGE)
        require(inspectionType in setOf("PRE_START", "POST_BATCH", "WEEKLY", "MONTHLY", "SAFETY")) { "نوع الفحص غير صالح" }
        require(result in setOf("PASS", "FAIL")) { "نتيجة الفحص غير صالحة" }
        require(checklistResult.isNotBlank()) { "نتيجة قائمة الفحص مطلوبة" }
        val asset = requireNotNull(db.maintenanceDao().assetById(assetId)) { "الأصل غير موجود" }
        val now = System.currentTimeMillis()
        val id = db.maintenanceDao().insertAssetInspection(
            AssetInspectionEntity(
                inspectionNo = documentNo("INS"),
                assetId = assetId,
                inspectionType = inspectionType,
                inspectionDate = now,
                result = result,
                checklistResult = checklistResult.trim(),
                findings = findings.trim(),
                correctiveAction = correctiveAction.trim(),
                inspectedBy = inspectedBy
            )
        )
        if (result == "FAIL") {
            db.maintenanceDao().updateAsset(asset.copy(status = "OUT_OF_SERVICE", inspectionDueAt = nextInspectionDueAt))
        } else if (nextInspectionDueAt != null) {
            db.maintenanceDao().updateAsset(asset.copy(inspectionDueAt = nextInspectionDueAt))
        }
        id
    }

    suspend fun recordCalibration(
        assetId: Long,
        result: String,
        referenceStandard: String,
        measuredError: Double?,
        tolerance: Double?,
        dueAt: Long?,
        certificateRef: String,
        notes: String,
        createdBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.MAINTENANCE_MANAGE)
        require(result in setOf("PASS", "FAIL")) { "نتيجة المعايرة غير صالحة" }
        val asset = requireNotNull(db.maintenanceDao().assetById(assetId)) { "الأصل غير موجود" }
        require(asset.calibrationRequired || asset.assetType == "MEASURING_TOOL") { "هذا الأصل غير معرف كأداة تحتاج معايرة" }
        val now = System.currentTimeMillis()
        val id = db.maintenanceDao().insertCalibration(
            CalibrationRecordEntity(
                calibrationNo = documentNo("CAL"),
                assetId = assetId,
                checkedAt = now,
                result = result,
                referenceStandard = referenceStandard.trim(),
                measuredError = measuredError,
                tolerance = tolerance,
                dueAt = dueAt,
                certificateRef = certificateRef.trim(),
                notes = notes.trim(),
                createdBy = createdBy
            )
        )
        db.maintenanceDao().updateAsset(
            asset.copy(
                calibrationDueAt = dueAt,
                status = if (result == "FAIL") "OUT_OF_SERVICE" else asset.status
            )
        )
        id
    }

    suspend fun reportSafetyIncident(
        incidentType: String,
        area: String,
        description: String,
        injuryOrImpact: String,
        immediateAction: String,
        capaRequired: Boolean,
        createdBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.MAINTENANCE_MANAGE)
        require(incidentType in setOf("ACCIDENT", "NEAR_MISS", "SPILL", "FIRE", "GAS_LEAK", "OTHER")) { "نوع الحادث غير صالح" }
        require(area.isNotBlank() && description.isNotBlank()) { "الموقع ووصف الحادث مطلوبان" }
        db.maintenanceDao().insertSafetyIncident(
            SafetyIncidentEntity(
                incidentNo = documentNo("SAFE"),
                occurredAt = System.currentTimeMillis(),
                incidentType = incidentType,
                area = area.trim(),
                description = description.trim(),
                injuryOrImpact = injuryOrImpact.trim(),
                immediateAction = immediateAction.trim(),
                capaRequired = capaRequired,
                createdBy = createdBy
            )
        )
    }

    suspend fun closeSafetyIncident(id: Long, rootCause: String, correctiveAction: String, preventiveAction: String, userId: Long) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.MAINTENANCE_MANAGE)
        require(rootCause.isNotBlank()) { "السبب الجذري مطلوب" }
        require(correctiveAction.isNotBlank()) { "الإجراء التصحيحي مطلوب" }
        val row = requireNotNull(db.maintenanceDao().safetyIncidentById(id)) { "الحادث غير موجود" }
        db.maintenanceDao().updateSafetyIncident(
            row.copy(
                rootCause = rootCause.trim(),
                correctiveAction = correctiveAction.trim(),
                preventiveAction = preventiveAction.trim(),
                status = "CLOSED",
                closedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordSafetyInspection(
        area: String,
        inspectionType: String,
        result: String,
        findings: String,
        correctiveAction: String,
        dueAt: Long?,
        inspectedBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(inspectedBy, SecurityPermissions.MAINTENANCE_MANAGE)
        require(area.isNotBlank()) { "منطقة الفحص مطلوبة" }
        require(result in setOf("PASS", "FAIL")) { "نتيجة الفحص غير صالحة" }
        db.maintenanceDao().insertSafetyInspection(
            SafetyInspectionEntity(
                inspectionNo = documentNo("SINS"),
                inspectionDate = System.currentTimeMillis(),
                area = area.trim(),
                inspectionType = inspectionType.trim(),
                result = result,
                findings = findings.trim(),
                correctiveAction = correctiveAction.trim(),
                dueAt = dueAt,
                closedAt = if (result == "PASS") System.currentTimeMillis() else null,
                inspectedBy = inspectedBy
            )
        )
    }

    suspend fun assertAssetCanOperate(assetId: Long, now: Long = System.currentTimeMillis()) {
        val asset = requireNotNull(db.maintenanceDao().assetById(assetId)) { "الأصل غير موجود" }
        val overduePlans = db.maintenanceDao().overduePlanCount(assetId, now)
        val inspectionOverdue = MaintenanceMath.isOverdue(asset.inspectionDueAt, now)
        val calibrationOverdue = asset.calibrationRequired && MaintenanceMath.isOverdue(asset.calibrationDueAt, now)
        val latest = db.maintenanceDao().latestAssetInspection(assetId, "PRE_START")
        val latestPassed = latest != null && latest.result == "PASS" && (now - latest.inspectionDate) <= 12L * 60L * 60L * 1000L
        require(MaintenanceMath.assetCanOperate(asset.status, overduePlans, inspectionOverdue, calibrationOverdue, latestPassed)) {
            "لا يمكن تشغيل المعدة: تحقق من الحالة والصيانة المستحقة والمعايرة وفحص ما قبل التشغيل"
        }
    }

    suspend fun kpis(from: Long, to: Long): Kpis {
        val due = db.maintenanceDao().preventiveDueCount(from, to)
        val done = db.maintenanceDao().preventiveCompletedOnTimeCount(from, to)
        val closed = db.maintenanceDao().closedWorkOrderCount(from, to)
        val closedOnTime = db.maintenanceDao().closedOnTimeCount(from, to)
        val overdueChecks = db.maintenanceDao().operationalAssets().count { asset ->
            MaintenanceMath.isOverdue(asset.inspectionDueAt, to) || (asset.calibrationRequired && MaintenanceMath.isOverdue(asset.calibrationDueAt, to))
        }
        return Kpis(
            preventiveCompliancePct = MaintenanceMath.preventiveCompliancePct(due, done),
            workOrdersClosedOnTimePct = MaintenanceMath.workOrdersClosedOnTimePct(closed, closedOnTime),
            overdueEquipmentChecks = overdueChecks,
            unplannedDowntimeMinutes = db.maintenanceDao().downtimeMinutes(from, to)
        )
    }

    private fun documentNo(prefix: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "$prefix-$stamp-${UUID.randomUUID().toString().take(6)}"
    }
}
