package com.fush.erp.domain

import androidx.room.withTransaction
import com.fush.erp.data.FushDatabase
import com.fush.erp.data.entity.AuditEventEntity
import com.fush.erp.data.entity.SalesRepresentativeEntity
import java.util.Locale

class SalesRepresentativeService(private val db: FushDatabase) {
    data class CustomerHistoryLinkResult(
        val customerId: Long,
        val salesRepId: Long,
        val invoicesLinked: Int,
        val commissionsLinked: Int,
        val salesBaseLinked: Double
    )

    suspend fun create(
        repType: String,
        employeeId: Long?,
        fullNameAr: String,
        fullNameEn: String,
        phone: String,
        territory: String,
        commissionRatePct: Double,
        notes: String,
        createdBy: Long
    ): SalesRepresentativeEntity = db.withTransaction {
        db.requireUserPermission(createdBy, SecurityPermissions.SALES_REPS_MANAGE)
        require(repType in setOf("INTERNAL", "EXTERNAL")) { "نوع المندوب غير صالح" }
        require(commissionRatePct.isFinite() && commissionRatePct in 0.0..100.0) { "نسبة العمولة يجب أن تكون بين 0 و100" }
        val employee = if (repType == "INTERNAL") {
            val id = requireNotNull(employeeId) { "اختر الموظف المرتبط بالمندوب الداخلي" }
            requireNotNull(db.employeeDao().employeeById(id)) { "الموظف غير موجود" }.also {
                require(it.status == "ACTIVE") { "الموظف المحدد غير نشط" }
                require(db.salesRepresentativeDao().byEmployeeId(id) == null) { "هذا الموظف مرتبط بمندوب مبيعات مسبقاً" }
            }
        } else null
        val resolvedName = fullNameAr.trim().ifBlank { employee?.fullNameAr.orEmpty() }
        require(resolvedName.isNotBlank()) { "اسم المندوب مطلوب" }
        val seq = (db.salesRepresentativeDao().maxSequence() ?: 0) + 1
        val code = "REP-%04d".format(Locale.US, seq)
        val row = SalesRepresentativeEntity(
            code = code,
            employeeId = employee?.id,
            repType = repType,
            fullNameAr = resolvedName,
            fullNameEn = fullNameEn.trim(),
            phone = phone.trim().ifBlank { employee?.phone.orEmpty() },
            territory = territory.trim(),
            commissionRatePct = commissionRatePct,
            notes = notes.trim(),
            createdBy = createdBy
        )
        val id = db.salesRepresentativeDao().insert(row)
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = createdBy,
                action = "CREATE",
                entityType = "SALES_REP",
                entityId = id.toString(),
                newValue = "$code|${row.fullNameAr}|${row.repType}|${row.territory}|${row.commissionRatePct}",
                reason = "إضافة مندوب مبيعات"
            )
        )
        row.copy(id = id)
    }

    suspend fun update(
        id: Long,
        fullNameAr: String,
        fullNameEn: String,
        phone: String,
        territory: String,
        commissionRatePct: Double,
        status: String,
        notes: String,
        updatedBy: Long
    ): SalesRepresentativeEntity = db.withTransaction {
        db.requireUserPermission(updatedBy, SecurityPermissions.SALES_REPS_MANAGE)
        require(fullNameAr.trim().isNotBlank()) { "اسم المندوب مطلوب" }
        require(commissionRatePct.isFinite() && commissionRatePct in 0.0..100.0) { "نسبة العمولة يجب أن تكون بين 0 و100" }
        require(status in setOf("ACTIVE", "INACTIVE")) { "حالة المندوب غير صالحة" }
        val old = requireNotNull(db.salesRepresentativeDao().byId(id)) { "المندوب غير موجود" }
        val row = old.copy(
            fullNameAr = fullNameAr.trim(),
            fullNameEn = fullNameEn.trim(),
            phone = phone.trim(),
            territory = territory.trim(),
            commissionRatePct = commissionRatePct,
            status = status,
            notes = notes.trim(),
            updatedAt = System.currentTimeMillis()
        )
        db.salesRepresentativeDao().update(row)
        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = updatedBy,
                action = "UPDATE",
                entityType = "SALES_REP",
                entityId = id.toString(),
                oldValue = "${old.fullNameAr}|${old.territory}|${old.commissionRatePct}|${old.status}",
                newValue = "${row.fullNameAr}|${row.territory}|${row.commissionRatePct}|${row.status}",
                reason = "تعديل مندوب المبيعات"
            )
        )
        row
    }

    suspend fun assignCustomerWithHistory(
        customerId: Long,
        salesRepId: Long,
        updatedBy: Long
    ): CustomerHistoryLinkResult = db.withTransaction {
        db.requireUserPermission(updatedBy, SecurityPermissions.SALES_REPS_MANAGE)
        val rep = requireNotNull(db.salesRepresentativeDao().byId(salesRepId)) { "مندوب المبيعات غير موجود" }
        require(rep.status == "ACTIVE") { "مندوب المبيعات غير نشط" }
        val customer = requireNotNull(db.customerDao().byId(customerId)) { "العميل غير موجود" }

        val invoicesBefore = db.salesRepresentativeDao().unassignedInvoiceCountForCustomer(customerId)
        val salesBaseBefore = db.salesRepresentativeDao().unassignedPostedSalesBaseForCustomer(customerId)

        db.customerDao().update(
            customer.copy(
                salesRepId = rep.id,
                salesRepName = rep.fullNameAr
            )
        )

        val invoicesLinked = db.salesRepresentativeDao().linkUnassignedInvoicesForCustomer(
            customerId = customerId,
            repId = rep.id,
            repName = rep.fullNameAr
        )
        val commissionsLinked = db.salesRepresentativeDao().linkUnassignedCommissionsForCustomer(
            customerId = customerId,
            repId = rep.id,
            repName = rep.fullNameAr
        )

        db.governanceDao().insertAudit(
            AuditEventEntity(
                userId = updatedBy,
                action = "LINK_HISTORY",
                entityType = "SALES_REP_CUSTOMER",
                entityId = "${rep.id}:$customerId",
                oldValue = "customerRep=${customer.salesRepId ?: 0}|unassignedInvoices=$invoicesBefore|unassignedSalesBase=$salesBaseBefore",
                newValue = "customerRep=${rep.id}|linkedInvoices=$invoicesLinked|linkedCommissions=$commissionsLinked|linkedSalesBase=$salesBaseBefore",
                reason = "ربط العميل بالمندوب وربط المبيعات السابقة غير المرتبطة بمندوب"
            )
        )

        CustomerHistoryLinkResult(
            customerId = customerId,
            salesRepId = rep.id,
            invoicesLinked = invoicesLinked,
            commissionsLinked = commissionsLinked,
            salesBaseLinked = salesBaseBefore
        )
    }

}
