package com.fush.erp.domain

/**
 * Identity guard for every business movement that belongs to a supplier.
 *
 * Supplier names are display snapshots only and must never be used as the
 * transaction identity. A persisted supplier movement must carry a real,
 * positive supplier primary key.
 */
object SupplierMovementIdentity {
    fun requireId(supplierId: Long?): Long {
        val id = requireNotNull(supplierId) { "يجب تحديد المورد وربط الحركة بمعرف المورد" }
        require(id > 0L) { "معرف المورد غير صالح" }
        return id
    }
}
