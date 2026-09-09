package com.fush.erp.domain

import java.util.UUID

/**
 * Stable idempotency identity for one user-confirmed treasury voucher operation.
 *
 * The UI creates one UUID when the dialog is opened and reuses it for every retry of that
 * same confirmation. A newly opened dialog receives a new UUID. Prefixing the persisted sourceId
 * keeps treasury-voucher retries in a namespace that cannot collide with legacy numeric
 * customer_receipts / supplier_payments source ids.
 */
object TreasuryVoucherOperationIdentity {
    private const val PREFIX = "voucher:"

    fun newOperationId(): String = UUID.randomUUID().toString()

    fun sourceId(operationId: String): String {
        val normalized = operationId.trim().lowercase()
        require(normalized.isNotBlank()) { "TREASURY_VOUCHER_OPERATION_ID_REQUIRED" }
        require(runCatching { UUID.fromString(normalized) }.isSuccess) {
            "TREASURY_VOUCHER_OPERATION_ID_INVALID"
        }
        return PREFIX + normalized
    }
}
