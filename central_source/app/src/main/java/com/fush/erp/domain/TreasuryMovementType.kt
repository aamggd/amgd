package com.fush.erp.domain

/**
 * Canonical business classification for every movement that touches a cash/bank treasury account.
 *
 * These codes describe the business meaning of the cash movement. They are intentionally separate
 * from UI voucher labels (RECEIPT/PAYMENT/EXPENSE/INCOME) so a payment voucher is never implicitly
 * treated as an expense.
 */
enum class TreasuryMovementType(
    val sourceType: String,
    val labelAr: String
) {
    CUSTOMER_RECEIPT("CUSTOMER_RECEIPT", "تحصيل عميل"),
    SUPPLIER_PAYMENT("SUPPLIER_PAYMENT", "دفعة مورد"),
    EXPENSE_PAYMENT("EXPENSE_PAYMENT", "دفع مصروف"),
    EMPLOYEE_PAYMENT("EMPLOYEE_PAYMENT", "دفعة موظف"),
    TRANSFER("TRANSFER", "تحويل داخلي"),
    ADJUSTMENT("ADJUSTMENT", "تسوية/تعديل")
}

/** Pure rules used by posting and reports. Kept free of Room dependencies for direct unit testing. */
object TreasuryMovementTypePolicy {
    private val workforceParties = setOf("EMPLOYEE", "SALES_REP")

    fun forVoucher(voucherType: String, partyType: String = "NONE"): TreasuryMovementType {
        val voucher = voucherType.trim().uppercase()
        val party = partyType.trim().uppercase().ifBlank { "NONE" }

        return when {
            voucher == "TRANSFER" -> TreasuryMovementType.TRANSFER
            voucher == "EXPENSE" -> TreasuryMovementType.EXPENSE_PAYMENT
            voucher == "RECEIPT" && party == "CUSTOMER" -> TreasuryMovementType.CUSTOMER_RECEIPT
            voucher == "PAYMENT" && party == "SUPPLIER" -> TreasuryMovementType.SUPPLIER_PAYMENT
            voucher == "PAYMENT" && party in workforceParties -> TreasuryMovementType.EMPLOYEE_PAYMENT
            else -> TreasuryMovementType.ADJUSTMENT
        }
    }

    /**
     * Classifies both new canonical journal sources and historical Phase 14.5.x source names.
     * [originalSourceType] lets a reversal retain the business classification of the entry it reverses.
     */
    fun fromJournalSource(
        sourceType: String,
        partyType: String = "NONE",
        originalSourceType: String? = null
    ): TreasuryMovementType {
        val source = sourceType.trim().uppercase()
        val party = partyType.trim().uppercase().ifBlank { "NONE" }
        if (source == "REVERSAL" && !originalSourceType.isNullOrBlank()) {
            return fromJournalSource(originalSourceType, party)
        }
        return when (source) {
            "CUSTOMER_RECEIPT" -> TreasuryMovementType.CUSTOMER_RECEIPT
            "SUPPLIER_PAYMENT" -> TreasuryMovementType.SUPPLIER_PAYMENT
            "EXPENSE_PAYMENT", "TREASURY_EXPENSE" -> TreasuryMovementType.EXPENSE_PAYMENT
            "EMPLOYEE_PAYMENT" -> TreasuryMovementType.EMPLOYEE_PAYMENT
            "TRANSFER", "TREASURY_TRANSFER" -> TreasuryMovementType.TRANSFER
            "ADJUSTMENT", "TREASURY_RECEIPT", "TREASURY_INCOME" -> TreasuryMovementType.ADJUSTMENT
            "TREASURY_PAYMENT" -> if (party in workforceParties) TreasuryMovementType.EMPLOYEE_PAYMENT else TreasuryMovementType.ADJUSTMENT
            else -> TreasuryMovementType.ADJUSTMENT
        }
    }

    fun isInternalTransferSource(sourceType: String, originalSourceType: String? = null): Boolean =
        fromJournalSource(sourceType, originalSourceType = originalSourceType) == TreasuryMovementType.TRANSFER
}
