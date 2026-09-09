package com.fush.erp.domain

/**
 * Identity guard for every business movement that belongs to a customer.
 *
 * Customer names are display snapshots only and must never be used as the
 * transaction identity. A persisted receivable/customer movement must carry
 * a real, positive customer primary key.
 */
object CustomerMovementIdentity {
    fun requireId(customerId: Long?): Long {
        val id = requireNotNull(customerId) { "يجب تحديد العميل وربط الحركة بمعرف العميل" }
        require(id > 0L) { "معرف العميل غير صالح" }
        return id
    }
}
