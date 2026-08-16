package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AuditEventEntity
import com.fush.erp.data.entity.ExpenseWorkflowRequestEntity
import java.util.Locale
import java.util.UUID

data class ExpensePaymentAuthorizationSnapshot(
    val treasuryAccountId: Long,
    val expenseAccountId: Long,
    val amountOriginal: Double,
    val currencyCode: String,
    val exchangeRate: Double,
    val description: String,
    val voucherReferenceNo: String,
    val expenseDate: Long,
    val employeeId: Long?,
    val salesRepId: Long?,
    val costCenterCode: String,
    val organizationUnit: String,
    val referenceType: String,
    val referenceId: Long?,
    val dimensionReferenceNo: String,
    val referenceLabel: String,
    val customerId: Long?,
    val supplierId: Long?,
    val itemId: Long?
)

object ExpenseLifecyclePolicy {
    const val DRAFT = "DRAFT"
    const val SUBMITTED = "SUBMITTED"
    const val APPROVED = "APPROVED"
    const val REJECTED = "REJECTED"
    const val UNPAID = "UNPAID"
    const val PAID = "PAID"

    fun requireCanSubmit(approvalStatus: String, paymentStatus: String) {
        require(approvalStatus == DRAFT && paymentStatus == UNPAID) { "يمكن إرسال مسودة غير مدفوعة فقط للاعتماد" }
    }

    fun requireCanApprove(approvalStatus: String, paymentStatus: String, requestedBy: Long, actorId: Long) {
        require(approvalStatus == SUBMITTED && paymentStatus == UNPAID) { "يمكن اعتماد طلب مرسل وغير مدفوع فقط" }
        require(requestedBy != actorId) { "لا يجوز لمنشئ طلب المصروف اعتماد طلبه بنفسه" }
    }

    fun requireCanReject(approvalStatus: String, paymentStatus: String, requestedBy: Long, actorId: Long, reason: String) {
        require(approvalStatus == SUBMITTED && paymentStatus == UNPAID) { "يمكن رفض طلب مرسل وغير مدفوع فقط" }
        require(requestedBy != actorId) { "لا يجوز لمنشئ طلب المصروف اتخاذ قرار الاعتماد على طلبه" }
        require(reason.trim().isNotBlank()) { "سبب رفض المصروف مطلوب" }
    }

    fun requireCanPay(approvalStatus: String, paymentStatus: String) {
        require(approvalStatus == APPROVED) { "لا يمكن دفع المصروف قبل اعتماده" }
        require(paymentStatus == UNPAID) { "تم دفع المصروف مسبقاً" }
    }

    fun requirePaymentMatchesApproved(
        approved: ExpensePaymentAuthorizationSnapshot,
        requested: ExpensePaymentAuthorizationSnapshot
    ) {
        require(normalizeSnapshot(approved) == normalizeSnapshot(requested)) {
            "بيانات الدفع لا تطابق نسخة المصروف التي تم اعتمادها"
        }
    }

    fun lifecycleLabel(approvalStatus: String, paymentStatus: String): String = when {
        paymentStatus == PAID -> "PAID"
        approvalStatus == REJECTED -> "REJECTED"
        approvalStatus == APPROVED -> "APPROVED"
        approvalStatus == SUBMITTED -> "SUBMITTED"
        else -> "DRAFT"
    }

    private fun normalizeSnapshot(value: ExpensePaymentAuthorizationSnapshot): ExpensePaymentAuthorizationSnapshot = value.copy(
        currencyCode = value.currencyCode.trim().uppercase(Locale.US),
        description = value.description.trim(),
        voucherReferenceNo = value.voucherReferenceNo.trim(),
        costCenterCode = value.costCenterCode.trim().uppercase(Locale.US),
        organizationUnit = value.organizationUnit.trim(),
        referenceType = value.referenceType.trim().ifBlank { "NONE" }.uppercase(Locale.US),
        dimensionReferenceNo = value.dimensionReferenceNo.trim(),
        referenceLabel = value.referenceLabel.trim()
    )
}

fun ExpenseWorkflowRequestEntity.paymentAuthorizationSnapshot(): ExpensePaymentAuthorizationSnapshot =
    ExpensePaymentAuthorizationSnapshot(
        treasuryAccountId = treasuryAccountId,
        expenseAccountId = expenseAccountId,
        amountOriginal = amountOriginal,
        currencyCode = currencyCode,
        exchangeRate = exchangeRate,
        description = description,
        voucherReferenceNo = referenceNo,
        expenseDate = expenseDate,
        employeeId = employeeId,
        salesRepId = salesRepId,
        costCenterCode = costCenterCode,
        organizationUnit = organizationUnit,
        referenceType = referenceType,
        referenceId = referenceId,
        dimensionReferenceNo = dimensionReferenceNo,
        referenceLabel = referenceLabel,
        customerId = customerId,
        supplierId = supplierId,
        itemId = itemId
    )

fun AccountingService.VoucherRequest.expensePaymentAuthorizationSnapshot(): ExpensePaymentAuthorizationSnapshot {
    val context = requireNotNull(expenseContext) { "بيانات تصنيف المصروف مطلوبة" }
    return ExpensePaymentAuthorizationSnapshot(
        treasuryAccountId = treasuryAccountId,
        expenseAccountId = requireNotNull(offsetAccountId) { "حساب المصروف مطلوب" },
        amountOriginal = amountOriginal,
        currencyCode = currencyCode,
        exchangeRate = exchangeRate,
        description = description,
        voucherReferenceNo = referenceNo,
        expenseDate = voucherDate,
        employeeId = context.employeeId,
        salesRepId = context.salesRepId,
        costCenterCode = context.costCenterCode,
        organizationUnit = context.organizationUnit,
        referenceType = context.referenceType,
        referenceId = context.referenceId,
        dimensionReferenceNo = context.referenceNo,
        referenceLabel = context.referenceLabel,
        customerId = context.customerId,
        supplierId = context.supplierId,
        itemId = context.itemId
    )
}

class ExpenseWorkflowService(private val db: FushDatabase) {
    suspend fun createDraft(request: AccountingService.VoucherRequest, createdBy: Long): Long = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.ACCOUNTING_POST)
        require(request.type == "EXPENSE") { "دورة الاعتماد هذه مخصصة للمصروفات فقط" }
        require(request.approvedExpenseRequestId == null) { "لا يمكن إنشاء مسودة من طلب اعتماد مدفوع" }
        require(request.treasuryAccountId > 0L) { "مصدر الدفع مطلوب" }
        require((request.offsetAccountId ?: 0L) > 0L) { "حساب المصروف مطلوب" }
        require(request.amountOriginal.isFinite() && request.amountOriginal > 0.0) { "المبلغ يجب أن يكون أكبر من صفر" }
        require(request.exchangeRate.isFinite() && request.exchangeRate > 0.0) { "سعر الصرف غير صالح" }
        require(request.currencyCode.trim().isNotBlank()) { "العملة مطلوبة" }
        require(request.description.trim().isNotBlank()) { "بيان المصروف مطلوب" }
        val context = requireNotNull(request.expenseContext) { "بيانات تصنيف المصروف مطلوبة" }
        val now = System.currentTimeMillis()
        val requestNo = "ERQ-${UUID.randomUUID().toString().take(8).uppercase(Locale.US)}"
        val id = db.expenseWorkflowDao().insert(
            ExpenseWorkflowRequestEntity(
                requestNo = requestNo,
                treasuryAccountId = request.treasuryAccountId,
                expenseAccountId = requireNotNull(request.offsetAccountId),
                amountOriginal = request.amountOriginal,
                currencyCode = request.currencyCode.trim().uppercase(Locale.US),
                exchangeRate = request.exchangeRate,
                description = request.description.trim(),
                referenceNo = request.referenceNo.trim(),
                expenseDate = request.voucherDate,
                employeeId = context.employeeId,
                salesRepId = context.salesRepId,
                costCenterCode = context.costCenterCode.trim().uppercase(Locale.US),
                organizationUnit = context.organizationUnit.trim(),
                referenceType = context.referenceType.trim().ifBlank { "NONE" }.uppercase(Locale.US),
                referenceId = context.referenceId,
                dimensionReferenceNo = context.referenceNo.trim(),
                referenceLabel = context.referenceLabel.trim(),
                customerId = context.customerId,
                supplierId = context.supplierId,
                itemId = context.itemId,
                attachmentFileName = context.attachment?.fileName?.trim().orEmpty(),
                attachmentMimeType = context.attachment?.mimeType?.trim().orEmpty(),
                attachmentUri = context.attachment?.uri?.trim().orEmpty(),
                attachmentNotes = context.attachment?.notes?.trim().orEmpty(),
                createdBy = createdBy,
                createdAt = now,
                updatedAt = now
            )
        )
        audit(createdBy, "EXPENSE_DRAFT_CREATED", id, "", "DRAFT|UNPAID", request.description)
        id
    }

    suspend fun submit(requestId: Long, actorId: Long) = db.withTransaction {
        db.requireUserPermission(actorId, SecurityPermissions.ACCOUNTING_POST)
        val row = requireRequest(requestId)
        ExpenseLifecyclePolicy.requireCanSubmit(row.approvalStatus, row.paymentStatus)
        validateForSubmission(row)
        val now = System.currentTimeMillis()
        db.expenseWorkflowDao().update(
            row.copy(
                approvalStatus = ExpenseLifecyclePolicy.SUBMITTED,
                submittedBy = actorId,
                submittedAt = now,
                updatedAt = now
            )
        )
        audit(actorId, "EXPENSE_SUBMITTED", requestId, "DRAFT|UNPAID", "SUBMITTED|UNPAID", row.description)
    }

    suspend fun approve(requestId: Long, actorId: Long) = db.withTransaction {
        db.requireUserPermission(actorId, SecurityPermissions.APPROVAL_DECIDE)
        val row = requireRequest(requestId)
        ExpenseLifecyclePolicy.requireCanApprove(row.approvalStatus, row.paymentStatus, row.createdBy, actorId)
        val now = System.currentTimeMillis()
        db.expenseWorkflowDao().update(
            row.copy(
                approvalStatus = ExpenseLifecyclePolicy.APPROVED,
                approvedBy = actorId,
                approvedAt = now,
                rejectedBy = null,
                rejectedAt = null,
                rejectionReason = "",
                updatedAt = now
            )
        )
        audit(actorId, "EXPENSE_APPROVED", requestId, "SUBMITTED|UNPAID", "APPROVED|UNPAID", row.description)
    }

    suspend fun reject(requestId: Long, reason: String, actorId: Long) = db.withTransaction {
        db.requireUserPermission(actorId, SecurityPermissions.APPROVAL_DECIDE)
        val row = requireRequest(requestId)
        ExpenseLifecyclePolicy.requireCanReject(row.approvalStatus, row.paymentStatus, row.createdBy, actorId, reason)
        val now = System.currentTimeMillis()
        db.expenseWorkflowDao().update(
            row.copy(
                approvalStatus = ExpenseLifecyclePolicy.REJECTED,
                rejectedBy = actorId,
                rejectedAt = now,
                rejectionReason = reason.trim(),
                updatedAt = now
            )
        )
        audit(actorId, "EXPENSE_REJECTED", requestId, "SUBMITTED|UNPAID", "REJECTED|UNPAID", reason)
    }

    suspend fun pay(requestId: Long, actorId: Long): Long {
        db.requireUserPermission(actorId, SecurityPermissions.TREASURY_POST)
        val row = requireRequest(requestId)
        ExpenseLifecyclePolicy.requireCanPay(row.approvalStatus, row.paymentStatus)
        validateForSubmission(row)
        return AccountingService(db).postVoucher(
            AccountingService.VoucherRequest(
                type = "EXPENSE",
                treasuryAccountId = row.treasuryAccountId,
                offsetAccountId = row.expenseAccountId,
                amountOriginal = row.amountOriginal,
                currencyCode = row.currencyCode,
                exchangeRate = row.exchangeRate,
                description = row.description,
                referenceNo = row.referenceNo,
                voucherDate = row.expenseDate,
                createdBy = actorId,
                approvedExpenseRequestId = row.id,
                expenseContext = AccountingService.ExpenseContext(
                    employeeId = row.employeeId,
                    salesRepId = row.salesRepId,
                    costCenterCode = row.costCenterCode,
                    organizationUnit = row.organizationUnit,
                    referenceType = row.referenceType,
                    referenceId = row.referenceId,
                    referenceNo = row.dimensionReferenceNo,
                    referenceLabel = row.referenceLabel,
                    customerId = row.customerId,
                    supplierId = row.supplierId,
                    itemId = row.itemId,
                    attachment = row.attachmentUri.takeIf { it.isNotBlank() }?.let {
                        AccountingService.ExpenseAttachmentInput(
                            fileName = row.attachmentFileName.ifBlank { "مرفق مصروف" },
                            mimeType = row.attachmentMimeType,
                            uri = it,
                            notes = row.attachmentNotes
                        )
                    }
                )
            )
        )
    }

    private suspend fun requireRequest(requestId: Long): ExpenseWorkflowRequestEntity =
        requireNotNull(db.expenseWorkflowDao().byId(requestId)) { "طلب المصروف غير موجود" }

    private fun validateForSubmission(row: ExpenseWorkflowRequestEntity) {
        require(row.treasuryAccountId > 0L) { "مصدر الدفع مطلوب" }
        require(row.expenseAccountId > 0L) { "حساب المصروف مطلوب" }
        require(row.amountOriginal.isFinite() && row.amountOriginal > 0.0) { "المبلغ يجب أن يكون أكبر من صفر" }
        require(row.exchangeRate.isFinite() && row.exchangeRate > 0.0) { "سعر الصرف غير صالح" }
        require(row.currencyCode.isNotBlank()) { "العملة مطلوبة" }
        require(row.description.isNotBlank()) { "بيان المصروف مطلوب" }
        ExpenseClassificationPolicy.validateMandatoryDimensions(
            ExpenseMandatoryDimensions(
                costCenterCode = row.costCenterCode,
                organizationUnit = row.organizationUnit,
                referenceType = row.referenceType,
                referenceId = row.referenceId,
                referenceNo = row.dimensionReferenceNo,
                referenceLabel = row.referenceLabel,
                customerId = row.customerId,
                supplierId = row.supplierId,
                itemId = row.itemId
            )
        )
    }

    private suspend fun audit(userId: Long, action: String, requestId: Long, oldValue: String, newValue: String, reason: String) {
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = userId,
                action = action,
                entityType = "EXPENSE_WORKFLOW",
                entityId = requestId.toString(),
                oldValue = oldValue,
                newValue = newValue,
                reason = reason.trim()
            )
        )
    }
}
