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
        AccountingEventSpec("YEAR_END_CLOSE", AccountingEventDomain.ACCOUNTING, "fiscal year today; P1 must use immutable closing-cycle id", AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),

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
        AccountingEventSpec("SALES_COMMISSION", AccountingEventDomain.SALES, "invoice id today; multiple collection allocations may earn commission", AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID, AccountingReversalPolicy.DEDICATED_REVERSAL_EVENT),
        AccountingEventSpec("COMMISSION_REVERSAL", AccountingEventDomain.SALES, "invoice id today; multiple sales returns may reverse commission", AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID, AccountingReversalPolicy.NONE),
        AccountingEventSpec("RECEIPT_COMMISSION_REVERSAL", AccountingEventDomain.SALES, "invoice id today; multiple receipt reversals are possible", AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID, AccountingReversalPolicy.NONE),

        AccountingEventSpec("PURCHASE", AccountingEventDomain.PURCHASES, "purchase_invoices.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.OPERATIONAL_DOCUMENT_REVERSAL),
        AccountingEventSpec("PURCHASE_RETURN", AccountingEventDomain.PURCHASES, "purchase_returns.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.OPERATIONAL_DOCUMENT_REVERSAL),
        AccountingEventSpec("SUPPLIER_PAYMENT", AccountingEventDomain.PURCHASES, "supplier_payments.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.OPERATIONAL_DOCUMENT_REVERSAL),

        AccountingEventSpec("OPENING_STOCK", AccountingEventDomain.INVENTORY, "generated opening document number today; P1 requires immutable opening event id", AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("INVENTORY_COUNT", AccountingEventDomain.INVENTORY, "inventory_counts.id", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),

        AccountingEventSpec("PRODUCTION_ISSUE", AccountingEventDomain.PRODUCTION, "production_orders.orderNo; exactly one original material issue per order", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("PRODUCTION_LABOR", AccountingEventDomain.PRODUCTION, "production_orders.orderNo; exactly one original labor accrual per order", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("PRODUCTION_RECEIPT", AccountingEventDomain.PRODUCTION, "stock_movements.id for accepted output", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("PRODUCTION_REJECT", AccountingEventDomain.PRODUCTION, "production_batches.batchNo for original rejection", AccountingReplayPolicy.STABLE_SOURCE, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("PROD_ISSUE_CORR", AccountingEventDomain.PRODUCTION, "orderNo today; multiple corrections can exist", AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("PROD_REJECT_CORR", AccountingEventDomain.PRODUCTION, "orderNo today; multiple corrections can exist", AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("PROD_COST_CORR", AccountingEventDomain.PRODUCTION, "orderNo today; multiple corrections can exist", AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),
        AccountingEventSpec("PROD_OUTPUT_CORR", AccountingEventDomain.PRODUCTION, "orderNo today; multiple corrections can exist", AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID, AccountingReversalPolicy.GENERIC_JOURNAL_REVERSAL),

        AccountingEventSpec("FIXED_ASSET_ACQUISITION", AccountingEventDomain.FIXED_ASSETS, "generated assetNo today; P1 requires immutable acquisition request/event id", AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID, AccountingReversalPolicy.DEDICATED_REVERSAL_EVENT),
        AccountingEventSpec("FIXED_ASSET_ACQUISITION_REVERSAL", AccountingEventDomain.FIXED_ASSETS, "original acquisition journal_entries.id", AccountingReplayPolicy.STATE_GUARDED, AccountingReversalPolicy.NONE),
        AccountingEventSpec("FIXED_ASSET_DEPRECIATION", AccountingEventDomain.FIXED_ASSETS, "assetId:fiscalYear:periodNo", AccountingReplayPolicy.STATE_GUARDED, AccountingReversalPolicy.DEDICATED_REVERSAL_EVENT),
        AccountingEventSpec("FIXED_ASSET_DEPRECIATION_REVERSAL", AccountingEventDomain.FIXED_ASSETS, "original depreciation journal_entries.id", AccountingReplayPolicy.STATE_GUARDED, AccountingReversalPolicy.NONE),
        AccountingEventSpec("FIXED_ASSET_DISPOSAL", AccountingEventDomain.FIXED_ASSETS, "asset id today; redisposal after reversal is possible", AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID, AccountingReversalPolicy.DEDICATED_REVERSAL_EVENT),
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

    fun p1StableKeyCandidates(): List<AccountingEventSpec> =
        events.filter { it.replayPolicy == AccountingReplayPolicy.STABLE_SOURCE }

    fun p1ReferenceGaps(): List<AccountingEventSpec> =
        events.filter { it.replayPolicy == AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID }
}
