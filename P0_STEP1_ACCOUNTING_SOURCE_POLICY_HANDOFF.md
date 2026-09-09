# FUSH ERP Mobile v104 — P0 Step 1 Handoff

Status: IMPLEMENTED / STATIC VALIDATED / GRADLE GATE PENDING

## Scope
Fix the accounting source-policy conflict that caused legitimate operational postings to be rejected by `trg_journal_unstable_source_blocked_insert` with `ACCOUNTING_SOURCE_REQUIRES_STABLE_EVENT_ID`.

## Root cause fixed
`STATE_GUARDED` events were incorrectly included in `replaySafeSourceTypes`, so the database unique trigger treated lifecycle/state-guarded events as if `sourceType + sourceId` were an immutable unique event identity. That could both block legitimate postings and falsely deduplicate later valid lifecycle operations.

## Policy after this step
- `STABLE_SOURCE`: database duplicate protection by `sourceType + sourceId`.
- `STATE_GUARDED`: registered and allowed; duplicate/replay protection is owned by the domain lifecycle/state guard, not by the source-key unique trigger.
- `MANUAL_ONLY`: registered and allowed; each confirmed invocation is a distinct event. Cross-invocation retry/double-click hardening is intentionally deferred to P0 Step 2.
- `NEEDS_STABLE_EVENT_ID`: remains fail-closed at the database boundary.

## Reclassified sources
- `YEAR_END_CLOSE` -> `STATE_GUARDED`
- `OPENING_STOCK` -> `MANUAL_ONLY`
- `PROD_ISSUE_CORR` -> `STATE_GUARDED`
- `PROD_REJECT_CORR` -> `STATE_GUARDED`
- `PROD_COST_CORR` -> `STATE_GUARDED`
- `PROD_OUTPUT_CORR` -> `STATE_GUARDED`
- `FIXED_ASSET_ACQUISITION` -> `MANUAL_ONLY`
- `FIXED_ASSET_DISPOSAL` -> `STATE_GUARDED`

## Files changed
- `app/src/main/java/com/fush/erp/domain/AccountingIntegrationContract.kt`
- `app/src/main/java/com/fush/erp/domain/AccountingPostingIdempotencyPolicy.kt`
- `app/src/test/java/com/fush/erp/domain/AccountingIntegrationContractTest.kt`
- `app/src/test/java/com/fush/erp/domain/AccountingPostingIdempotencyPolicyTest.kt`

## Data safety
- Application ID unchanged: `com.fush.erp.recovery`
- Room schema unchanged: `38`
- No migration added.
- No destructive migration enabled.
- No journal posting amounts or debit/credit mappings changed.
- Expense ActivityResult runtime fix remains present in `MainActivity.kt`.

## Static verification performed
1. Kotlin compilation of:
   - `AccountingIntegrationContract.kt`
   - `TreasuryMovementType.kt`
   - `AccountingPostingIdempotencyPolicy.kt`
   Result: PASS.
2. Runtime policy invariant check in a standalone Kotlin JVM runner:
   Result: `STEP1_POLICY_INVARIANTS_PASS`.
3. Confirmed the eight formerly blocked sources are no longer in `blockedUnstableSourceTypes`.
4. Confirmed only `STABLE_SOURCE` enters `replaySafeSourceTypes`.
5. Confirmed unresolved legacy treasury source identities remain fail-closed.

## Required Gradle gate on the user's configured Android build environment
Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.fush.erp.domain.AccountingIntegrationContractTest" --tests "com.fush.erp.domain.AccountingPostingIdempotencyPolicyTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

Do not proceed to P0 Step 2 until this targeted gate passes.

## Explicitly not fixed in this step
- Double-click/retry idempotency for manual operational actions (P0 Step 2).
- Period fail-closed coverage (later P0 step).
- The three legacy `AccountingP1IntegrityPolicyTest` failures (later P0 step).
