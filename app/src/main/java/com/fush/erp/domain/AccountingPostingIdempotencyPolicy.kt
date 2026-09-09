package com.fush.erp.domain

object AccountingPostingIdempotencyPolicy {
    /**
     * Every source that can be emitted by AccountingService.postVoucher().
     *
     * P0 Step 2 gives each user-confirmed voucher one stable VoucherRequest.operationId and stores
     * it as a namespaced journal sourceId. The same request can therefore be retried safely while
     * a newly opened voucher dialog receives a new operation id and remains a legitimate new event.
     */
    val treasuryVoucherReplaySafeSourceTypes: Set<String> =
        TreasuryMovementType.entries.mapTo(linkedSetOf()) { it.sourceType }

    /**
     * Source types for which sourceType + sourceId is the immutable business-event identity.
     *
     * STATE_GUARDED events are intentionally excluded: their duplicate prevention comes from
     * domain state (for example disposal/reversal status), and the same sourceId may legitimately
     * be reused by a later lifecycle event after a reversal.
     */
    /**
     * Historical databases created by pre-v197 production correction flows may contain more than
     * one legitimate PRODUCTION_ISSUE journal for the same production order.  The source id was
     * the order number, so sourceType + sourceId is not a safe unique key for those rows.  Keep
     * them outside the SQLite duplicate-source trigger for backward-compatible hydration.
     * v197 also stops creating new correction rows with PRODUCTION_ISSUE and uses
     * PROD_ISSUE_CORR for additional material issues instead.
     */
    private val legacyMultiEventSourceTypes: Set<String> = setOf("PRODUCTION_ISSUE")

    val replaySafeSourceTypes: Set<String>
        get() = AccountingIntegrationContract.currentStableReplayCandidates()
            .asSequence()
            .map { it.sourceType }
            .filterNot { it in legacyMultiEventSourceTypes }
            .toCollection(linkedSetOf())
            .apply { addAll(treasuryVoucherReplaySafeSourceTypes) }

    val blockedUnstableSourceTypes: Set<String>
        get() = AccountingIntegrationContract.events
            .filter { it.replayPolicy == AccountingReplayPolicy.NEEDS_STABLE_EVENT_ID }
            .mapTo(linkedSetOf()) { it.sourceType }

    val registeredSourceTypes: Set<String>
        get() = AccountingIntegrationContract.events
            .mapTo(linkedSetOf()) { it.sourceType }
            .apply {
                addAll(treasuryVoucherReplaySafeSourceTypes)
                // Internal STAGING-only cloud aliases. They are intentionally not business
                // events in AccountingIntegrationContract and are never allowed to remain POSTED.
                add(LegacyTreasuryCloudHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE)
                add(CloudPostedJournalHydrationPolicy.STAGING_ALIAS_SOURCE_TYPE)
            }
}
