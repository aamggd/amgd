package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class EmployeeService(private val db: FushDatabase) {

    suspend fun createEmployee(
        code: String,
        fullNameAr: String,
        jobTitle: String,
        department: String,
        phone: String,
        notes: String,
        createdBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.EMPLOYEES_MANAGE)
        require(fullNameAr.isNotBlank()) { "اسم الموظف مطلوب" }
        require(jobTitle.isNotBlank()) { "المسمى الوظيفي مطلوب" }
        require(department.isNotBlank()) { "القسم مطلوب" }
        val resolvedCode = code.trim().uppercase(Locale.US).ifBlank {
            val seq = (db.employeeDao().maxEmployeeSequence() ?: 0) + 1
            "EMP-%04d".format(Locale.US, seq)
        }
        val id = db.employeeDao().insertEmployee(
            EmployeeEntity(
                code = resolvedCode,
                fullNameAr = fullNameAr.trim(),
                phone = phone.trim(),
                jobTitle = jobTitle.trim(),
                department = department.trim(),
                notes = notes.trim(),
                createdBy = createdBy
            )
        )
        audit(createdBy, "CREATE", "EMPLOYEE", id.toString(), "", "$resolvedCode|$fullNameAr|$jobTitle", "إضافة موظف")
        id
    }

    suspend fun updateEmployee(
        employeeId: Long,
        fullNameAr: String,
        fullNameEn: String,
        jobTitle: String,
        department: String,
        phone: String,
        status: String,
        notes: String,
        updatedBy: Long
    ): EmployeeEntity = db.withTransaction {
        db.requireUserPermission(updatedBy, SecurityPermissions.EMPLOYEES_MANAGE)
        require(fullNameAr.trim().isNotBlank()) { "اسم الموظف مطلوب" }
        require(jobTitle.trim().isNotBlank()) { "المسمى الوظيفي مطلوب" }
        require(department.trim().isNotBlank()) { "القسم مطلوب" }
        require(status in setOf("ACTIVE", "INACTIVE")) { "حالة الموظف غير صالحة" }
        val old = requireNotNull(db.employeeDao().employeeById(employeeId)) { "الموظف غير موجود" }
        val row = old.copy(
            fullNameAr = fullNameAr.trim(),
            fullNameEn = fullNameEn.trim(),
            jobTitle = jobTitle.trim(),
            department = department.trim(),
            phone = phone.trim(),
            status = status,
            notes = notes.trim()
        )
        db.employeeDao().updateEmployee(row)
        audit(
            updatedBy, "UPDATE", "EMPLOYEE", employeeId.toString(),
            "${old.fullNameAr}|${old.jobTitle}|${old.department}|${old.status}",
            "${row.fullNameAr}|${row.jobTitle}|${row.department}|${row.status}",
            "تعديل بيانات الموظف"
        )
        row
    }

    suspend fun createCourse(
        code: String,
        titleAr: String,
        category: String,
        assetType: String?,
        description: String,
        requiresPracticalObservation: Boolean,
        createdBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.EMPLOYEES_MANAGE)
        require(code.isNotBlank() && titleAr.isNotBlank()) { "كود وعنوان التدريب مطلوبان" }
        require(category in setOf("SAFETY", "PROCESS", "EQUIPMENT", "QUALITY", "SOP", "OTHER")) { "فئة التدريب غير صالحة" }
        val id = db.employeeDao().insertCourse(
            TrainingCourseEntity(
                code = code.trim().uppercase(Locale.US),
                titleAr = titleAr.trim(),
                category = category,
                assetType = assetType,
                description = description.trim(),
                requiresPracticalObservation = requiresPracticalObservation
            )
        )
        audit(createdBy, "CREATE", "TRAINING_COURSE", id.toString(), "", "$code|$titleAr", "إضافة برنامج تدريب")
        id
    }

    suspend fun recordTraining(
        employeeId: Long,
        courseId: Long,
        result: String,
        practicalObserved: Boolean,
        validityDays: Int?,
        trainer: String,
        certificateRef: String,
        notes: String,
        recordedBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(recordedBy, SecurityPermissions.EMPLOYEES_MANAGE)
        val employee = requireNotNull(db.employeeDao().employeeById(employeeId)) { "الموظف غير موجود" }
        require(employee.status == "ACTIVE") { "الموظف غير نشط" }
        val course = requireNotNull(db.employeeDao().courseById(courseId)) { "برنامج التدريب غير موجود" }
        require(course.isActive) { "برنامج التدريب غير نشط" }
        require(result in setOf("PASS", "FAIL")) { "نتيجة التدريب غير صالحة" }
        if (result == "PASS" && course.requiresPracticalObservation) {
            require(practicalObserved) { "هذا التدريب يتطلب اجتياز الملاحظة العملية قبل العمل المستقل" }
        }
        if (validityDays != null) require(validityDays > 0) { "مدة الصلاحية يجب أن تكون أكبر من صفر" }
        val completedAt = System.currentTimeMillis()
        val expiresAt = validityDays?.let { completedAt + it.toLong() * DAY_MS }
        val id = db.employeeDao().insertTraining(
            EmployeeTrainingEntity(
                employeeId = employeeId,
                courseId = courseId,
                completedAt = completedAt,
                expiresAt = expiresAt,
                result = result,
                practicalObserved = practicalObserved,
                trainer = trainer.trim(),
                certificateRef = certificateRef.trim(),
                notes = notes.trim(),
                recordedBy = recordedBy
            )
        )
        audit(recordedBy, "RECORD", "EMPLOYEE_TRAINING", id.toString(), "", "employee=$employeeId course=$courseId result=$result expires=$expiresAt", "تسجيل تدريب وكفاءة")
        id
    }

    suspend fun authorizeEquipment(
        employeeId: Long,
        assetId: Long,
        courseId: Long,
        validityDays: Int?,
        notes: String,
        authorizedBy: Long
    ): Long = db.withTransaction {
        db.requireUserPermission(authorizedBy, SecurityPermissions.EMPLOYEES_MANAGE)
        val now = System.currentTimeMillis()
        val employee = requireNotNull(db.employeeDao().employeeById(employeeId)) { "الموظف غير موجود" }
        require(employee.status == "ACTIVE") { "الموظف غير نشط" }
        val asset = requireNotNull(db.maintenanceDao().assetById(assetId)) { "المعدة غير موجودة" }
        require(asset.status == "ACTIVE") { "لا يمكن إصدار تصريح لمعدة غير نشطة" }
        val course = requireNotNull(db.employeeDao().courseById(courseId)) { "برنامج التدريب غير موجود" }
        require(course.assetType == asset.assetType) { "برنامج التدريب المختار غير مخصص لنوع هذه المعدة (${asset.assetType})" }
        require(db.employeeDao().validTrainingCount(employeeId, courseId, now) > 0) {
            "لا يمكن إصدار تصريح تشغيل قبل وجود تدريب ناجح وساري واجتياز الملاحظة العملية المطلوبة"
        }
        if (validityDays != null) require(validityDays > 0) { "مدة التصريح يجب أن تكون أكبر من صفر" }
        val expiresAt = validityDays?.let { now + it.toLong() * DAY_MS }
        val id = db.employeeDao().insertAuthorization(
            EquipmentAuthorizationEntity(
                authorizationNo = documentNo("AUTH"),
                employeeId = employeeId,
                assetId = assetId,
                courseId = courseId,
                issuedAt = now,
                expiresAt = expiresAt,
                notes = notes.trim(),
                authorizedBy = authorizedBy
            )
        )
        audit(authorizedBy, "AUTHORIZE", "EQUIPMENT_AUTHORIZATION", id.toString(), "", "employee=$employeeId asset=$assetId course=$courseId expires=$expiresAt", "تصريح تشغيل معدة")
        id
    }

    suspend fun revokeAuthorization(authorizationId: Long, reason: String, userId: Long) = db.withTransaction {
        db.requireUserPermission(userId, SecurityPermissions.EMPLOYEES_MANAGE)
        require(reason.isNotBlank()) { "سبب إلغاء التصريح مطلوب" }
        val row = requireNotNull(db.employeeDao().authorizationById(authorizationId)) { "التصريح غير موجود" }
        if (row.status == "REVOKED") return@withTransaction
        db.employeeDao().updateAuthorization(row.copy(status = "REVOKED", notes = listOf(row.notes, "إلغاء: ${reason.trim()}").filter { it.isNotBlank() }.joinToString(" | ")))
        audit(userId, "REVOKE", "EQUIPMENT_AUTHORIZATION", authorizationId.toString(), row.status, "REVOKED", reason.trim())
    }

    suspend fun assignOperator(orderId: Long, employeeId: Long, assignedBy: Long) = db.withTransaction {
        db.requireUserPermission(assignedBy, SecurityPermissions.EMPLOYEES_MANAGE)
        val order = requireNotNull(db.productionDao().orderById(orderId)) { "أمر الإنتاج غير موجود" }
        val employee = requireNotNull(db.employeeDao().employeeById(employeeId)) { "موظف الإنتاج غير موجود" }
        require(employee.status == "ACTIVE") { "موظف الإنتاج غير نشط" }
        order.primaryAssetId?.let { assetId -> assertEmployeeCanOperateAsset(employeeId, assetId) }
        val current = db.employeeDao().operatorAssignment(orderId)
        if (current == null) {
            db.employeeDao().insertOperatorAssignment(ProductionOperatorAssignmentEntity(orderId = orderId, employeeId = employeeId, assignedBy = assignedBy))
        } else {
            db.employeeDao().updateOperatorAssignment(current.copy(employeeId = employeeId, assignedBy = assignedBy, assignedAt = System.currentTimeMillis()))
        }
        audit(assignedBy, "ASSIGN", "PRODUCTION_OPERATOR", orderId.toString(), current?.employeeId?.toString() ?: "", employeeId.toString(), "تعيين موظف إنتاج/مشغل وربط استحقاق الأجور بأمر الإنتاج")
    }

    suspend fun assertEmployeeCanOperateAsset(employeeId: Long, assetId: Long, at: Long = System.currentTimeMillis()) {
        val employee = requireNotNull(db.employeeDao().employeeById(employeeId)) { "المشغل غير موجود" }
        require(employee.status == "ACTIVE") { "المشغل غير نشط" }
        val authorization = requireNotNull(db.employeeDao().activeAuthorization(employeeId, assetId, at)) {
            "المشغل ${employee.fullNameAr} لا يملك تصريح تشغيل ساري لهذه المعدة"
        }
        val course = requireNotNull(db.employeeDao().courseById(authorization.courseId)) { "برنامج التدريب المرتبط بالتصريح غير موجود" }
        require(course.isActive) { "برنامج التدريب المرتبط بالتصريح غير نشط" }
        require(db.employeeDao().validTrainingCount(employeeId, authorization.courseId, at) > 0) {
            "تدريب المشغل ${employee.fullNameAr} غير ساري أو لم يجتز المتطلبات العملية"
        }
    }

    private suspend fun audit(userId: Long, action: String, entityType: String, entityId: String, oldValue: String, newValue: String, reason: String) {
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = action,
                entityType = entityType,
                entityId = entityId,
                oldValue = oldValue,
                newValue = newValue,
                reason = reason
            )
        )
    }

    private fun documentNo(prefix: String): String {
        val date = SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date())
        return "$prefix-$date-${UUID.randomUUID().toString().take(4).uppercase(Locale.US)}"
    }

    companion object { private const val DAY_MS = 86_400_000L }
}
