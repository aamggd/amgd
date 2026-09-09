package com.fush.erp.domain

enum class AccountingEventDomain {
    ACCOUNTING,
    TREASURY,
    SALES,
    PURCHASES,
    INVENTORY,
    PRODUCTION,
    FIXED_ASSETS
}

enum class AccountingReplayPolicy {
    /** sourceType + sourceId already identifies one business event. */
    STABLE_SOURCE,

    /** Duplicate execution is prevented by an existing business-state/reversal guard, not by a unique source key. */
    STATE_GUARDED,

    /** Current sourceId is generated or can be reused by more than one legitimate event; P1 must replace it with a stable event id. */
    NEEDS_STABLE_EVENT_ID,

    /** Manually initiated journal: every invocation is intentionally a new accounting event. */
    MANUAL_ONLY
}

enum class AccountingReversalPolicy {
    NONE,
    GENERIC_JOURNAL_REVERSAL,
    OPERATIONAL_DOCUMENT_REVERSAL,
    DEDICATED_REVERSAL_EVENT
}

data class AccountingEventSpec(
    val sourceType: String,
    val domain: AccountingEventDomain,
    val sourceReference: String,
    val replayPolicy: AccountingReplayPolicy,
    val reversalPolicy: AccountingReversalPolicy,
    val notes: String = ""
)

/**
 * P0 accounting integration contract.
 *
 * The contract records every application event that is allowed to create a journal entry and the
 * business identity that must be carried in JournalEntryEntity.sourceId.  P1 uses the replayPolicy
 * field to add idempotency only where the reference is actually safe; it must not blindly assume
 * that sourceType + the current sourceId is unique.
 */
object AccountingIntegrationContract {
    val events: List<AccountingEventSpec> = listOf(
        AccountingEventSpec("MANUAL", AccountingEventDomain.ACCOUNTING, "generated journal UUID", AccountingReplayPolicy.MANUAL_ONLY, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("REVERSAL", AccountingEventDomain.ACCOUNTING, "original journal_entries.id", AccountingReplayPolicy.STATE_GUARDED, AccountingReversalPolicy.NONE),
        AccountingEventSpec("YEAR_END_CLOSE", AccountingEventDomain.ACCOUNTING, "fiscal year closing lifecycle is guarded by fiscal_year_closings status; re-close after reversal is a new valid event", AccountingReplayPolicy.STATE_GUARDED, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),

        AccountingEventSpec("TREASURY_TRANSFER", AccountingEventDomain.TREASURY, "generated UUID today; P1 requires immutable voucher/event id", AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("TREASURY_RECEIPT", AccountingEventDomain.TREASURY, "generated UUID today; P1 requires party_vouchers.id or request id", AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("TREASURY_PAYMENT", AccountingEventDomain.TREASURY, "generated UUID today; P1 requires party_vouchers.id or request id", AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("TREASURY_EXPENSE", AccountingEventDomain.TREASURY, "generated UUID today; P1 requires party_vouchers.id or request id", AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("TREASURY_INCOME", AccountingEventDomain.TREASURY, "generated UUID today; P1 requires party_vouchers.id or request id", AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("CASH_COUNT_ADJUSTMENT", AccountingEventDomain.TREASURY, "treasury_cash_counts.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("FX_REVALUATION", AccountingEventDomain.TREASURY, "treasury_fx_revaluations.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.DEDICATED_REVERSAL_EVENT),
        AccountingEventSpec("FX_REVALUATION_REVERSAL", AccountingEventDomain.TREASURY, "treasury_fx_revaluations.id", AccountingReplayPolicy.STATE_GUARDED, AccountingReversalPolicy.NONE),

        AccountingEventSpec("SALE", AccountingEventDomain.SALES, "sales_invoices.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.OPERATIONAL_DOCUMENT_REVERSAL),
        AccountingEventSpec("CUSTOMER_RECEIPT", AccountingEventDomain.SALES, "customer_receipts.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.OPERATIONAL_DOCUMENT_REVERSAL),
        AccountingEventSpec("SALES_RETURN", AccountingEventDomain.SALES, "sales_returns.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.OPERATIONAL_DOCUMENT_REVERSAL),
        AccountingEventSpec("SALES_COMMISSION", AccountingEventDomain.SALES, "sales_commissions.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.DEDICATED_REVERSAL_EVENT),
        AccountingEventSpec("COMMISSION_REVERSAL", AccountingEventDomain.SALES, "sales_returns.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.NONE),
        AccountingEventSpec("RECEIPT_COMMISSION_REVERSAL", AccountingEventDomain.SALES, "reversal customer_receipts.id + sales_invoices.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.NONE),
        AccountingEventSpec("ADDITIONAL_CHARGE", AccountingEventDomain.SALES, "sales_additional_charges.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("ADDITIONAL_CHARGE_PAYMENT", AccountingEventDomain.SALES, "sales_additional_charge_payments.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),

        AccountingEventSpec("PURCHASE", AccountingEventDomain.PURCHASES, "purchase_invoices.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.OPERATIONAL_DOCUMENT_REVERSAL),
        AccountingEventSpec("PURCHASE_RETURN", AccountingEventDomain.PURCHASES, "purchase_returns.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.OPERATIONAL_DOCUMENT_REVERSAL),
        AccountingEventSpec("SUPPLIER_PAYMENT", AccountingEventDomain.PURCHASES, "supplier_payments.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.OPERATIONAL_DOCUMENT_REVERSAL),

        AccountingEventSpec("OPENING_STOCK", AccountingEventDomain.INVENTORY, "user-initiated opening-stock posting; every confirmed invocation is a distinct accounting event", AccountingReplayPolicy.MANUAL_ONLY, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("INVENTORY_COUNT", AccountingEventDomain.INVENTORY, "inventory_counts.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),

        AccountingEventSpec("PRODUCTION_ISSUE", AccountingEventDomain.PRODUCTION, "production_orders.orderNo; exactly one original material issue per order", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("PRODUCTION_LABOR", AccountingEventDomain.PRODUCTION, "production_orders.orderNo; exactly one original labor accrual per order", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("PRODUCTION_RECEIPT", AccountingEventDomain.PRODUCTION, "stock_movements.id for accepted output", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("PRODUCTION_REJECT", AccountingEventDomain.PRODUCTION, "production_batches.batchNo for original rejection", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("PROD_ISSUE_CORR", AccountingEventDomain.PRODUCTION, "production issue correction is guarded by the current issued quantity and correction rows; multiple corrections per order are valid", AccountingReplayPolicy.STATE_GUARDED, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("PROD_REJECT_CORR", AccountingEventDomain.PRODUCTION, "rejected-production correction is guarded by production state and correction rows; multiple corrections per order are valid", AccountingReplayPolicy.STATE_GUARDED, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("PROD_COST_CORR", AccountingEventDomain.PRODUCTION, "accepted-batch cost correction is guarded by current material/batch state; multiple corrections per order are valid", AccountingReplayPolicy.STATE_GUARDED, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("PROD_OUTPUT_CORR", AccountingEventDomain.PRODUCTION, "accepted-output correction is guarded by current material/batch quantities; multiple corrections per order are valid", AccountingReplayPolicy.STATE_GUARDED, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),

        AccountingEventSpec("FIXED_ASSET_ACQUISITION", AccountingEventDomain.FIXED_ASSETS, "user-initiated asset registration; every confirmed invocation creates a distinct asset and acquisition event", AccountingReplayPolicy.MANUAL_ONLY, AccountingReversalPolicy.DEDICATED_REVERSAL_EVENT),
        AccountingEventSpec("FIXED_ASSET_ACQUISITION_REVERSAL", AccountingEventDomain.FIXED_ASSETS, "original acquisition journal_entries.id", AccountingReplayPolicy.STATE_GUARDED, AccountingReversalPolicy.NONE),
        AccountingEventSpec("FIXED_ASSET_DEPRECIATION", AccountingEventDomain.FIXED_ASSETS, "assetId:fiscalYear:periodNo", AccountingReplayPolicy.STATE_GUARDED, AccountingReversalPolicy.DEDICATED_REVERSAL_EVENT),
        AccountingEventSpec("FIXED_ASSET_DEPRECIATION_REVERSAL", AccountingEventDomain.FIXED_ASSETS, "original depreciation journal_entries.id", AccountingReplayPolicy.STATE_GUARDED, AccountingReversalPolicy.NONE),
        AccountingEventSpec("FIXED_ASSET_DISPOSAL", AccountingEventDomain.FIXED_ASSETS, "asset status + active disposal row guard the lifecycle; redisposal after reversal is a distinct valid event", AccountingReplayPolicy.STATE_GUARDED, AccountingReversalPolicy.DEDICATED_REVERSAL_EVENT),
        AccountingEventSpec("FIXED_ASSET_DISPOSAL_REVERSAL", AccountingEventDomain.FIXED_ASSETS, "original disposal journal_entries.id", AccountingReplayPolicy.STATE_GUARDED, AccountingReversalPolicy.NONE)
    )

    private val byType = events.associateBy { it.sourceType }

    init {
        require(byType.size == events.size) { "ACCOUNTING_EVENT_SOURCE_TYPE_DUPLICATE" }
    }

    fun spec(sourceType: String): AccountingEventSpec? = byType[sourceType.trim().uppercase()]

    fun requireRegistered(sourceType: String): AccountingEventSpec =
        requireNotNull(spec(sourceType)) { "ACCOUNTING_EVENT_SOURCE_TYPE_NOT_REGISTERED:$sourceType" }

    fun canonicalEventKey(sourceType: String, sourceId: String): String {
        val spec = requireRegistered(sourceType)
        val normalizedId = sourceId.trim()
        require(normalizedId.isNotEmpty()) { "ACCOUNTING_EVENT_SOURCE_ID_REQUIRED:${spec.sourceType}" }
        return "${spec.sourceType}:$normalizedId"
    }

    /**
     * Historical P1 duplicate-protection snapshot.
     *
     * P1 originally closed the first thirteen stable operational sources.  Later features added
     * additional replay-safe sources (for example the sales-commission lifecycle), but those must
     * not retroactively rewrite the P1 audit contract.  Current runtime replay protection is
     * exposed separately through [currentStableReplayCandidates].
     */
    private val p1HistoricalStableSourceTypes: Set<String> = linkedSetOf(
        "CASH_COUNT_ADJUSTMENT",
        "FX_REVALUATION",
        "SALE",
        "CUSTOMER_RECEIPT",
        "SALES_RETURN",
        "PURCHASE",
        "PURCHASE_RETURN",
        "SUPPLIER_PAYMENT",
        "INVENTORY_COUNT",
        "PRODUCTION_ISSUE",
        "PRODUCTION_LABOR",
        "PRODUCTION_RECEIPT",
        "PRODUCTION_REJECT"
    )

    fun p1StableKeyCandidates(): List<AccountingEventSpec> =
        p1HistoricalStableSourceTypes.map { sourceType ->
            val spec = requireRegistered(sourceType)
            require(spec.replayPolicy == AccountingReplayPolicy.STABLE_SOURCE) {
                "ACCOUNTING_P1_SOURCE_NOT_STABLE:$sourceType"
            }
            require(spec.reversalPolicy != AccountingReversalPolicy.NONE) {
                "ACCOUNTING_P1_SOURCE_WITHOUT_REVERSAL:$sourceType"
            }
            spec
        }

    /** Current runtime sources whose sourceType + sourceId is a stable business-event identity. */
    fun currentStableReplayCandidates(): List<AccountingEventSpec> =
        events.filter { it.replayPolicy == AccountingReplayPolicy.STABLE_SOURCE }

    fun p1ReferenceGaps(): List<AccountingEventSpec> =
        events.filter { it.replayPolicy == AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID }
}
