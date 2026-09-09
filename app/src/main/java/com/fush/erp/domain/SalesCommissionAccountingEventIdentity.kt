package com.fush.erp.domain

/**
 * Stable accounting source identities for sales commission lifecycle events.
 *
 * These identities are deliberately namespaced. Older app versions stored invoice ids (and the
 * first stable-id hotfix stored bare row ids) in journal_entries.sourceId for the same sourceType.
 * A bare numeric id can therefore collide with an unrelated historical journal on an upgraded
 * database. Prefixing the immutable row identity keeps the event replay-safe without rewriting or
 * deleting any historical accounting rows.
 */
object SalesCommissionAccountingEventIdentity {
    fun commission(commissionId: Long): String =
        "commission:${positiveId("SALES_COMMISSION", commissionId)}"

    fun returnReversal(returnId: Long): String =
        "sales-return:${positiveId("COMMISSION_REVERSAL", returnId)}"

    fun receiptReversal(reversalReceiptId: Long, invoiceId: Long): String =
        "receipt-reversal:${positiveId("RECEIPT_COMMISSION_REVERSAL_RECEIPT", reversalReceiptId)}:invoice:${positiveId("RECEIPT_COMMISSION_REVERSAL_INVOICE", invoiceId)}"

    private fun positiveId(label: String, id: Long): String {
        require(id > 0L) { "${label}_STABLE_EVENT_ID_REQUIRED" }
        return id.toString()
    }
}
