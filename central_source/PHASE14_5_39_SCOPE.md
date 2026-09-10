# Phase 14.5.39 — Foreign Currency Treasury & Revaluation

## Baseline
This phase is incremental on the **validated Accounting Rebase over official Phase 14.5.38 Professional UI**.

The application identity is intentionally unchanged:
- Application ID: `com.fush.erp.recovery`
- Baseline branch-test versionCode: `77`
- Baseline branch-test versionName: `0.15.4.38-ui-inventory-master-data`
- Starting standalone accounting schema: `31`
- Phase 14.5.39 standalone accounting schema: `32` (**PROVISIONAL / BRANCH ONLY**)

## Objective
Make treasury/cash-count/bank-reconciliation controls correct for foreign currencies by separating:
1. the **original currency quantity** (for example USD 100), and
2. the **YER_NEW carrying value** used by the general ledger.

Period-end FX revaluation must change only the YER_NEW carrying value and must never invent or destroy original-currency quantity.

## Implemented functionality

### Original-currency treasury ledger
- Treasury balance rows expose both `balanceOriginal` and `balanceBase`.
- Original balance is derived only from posted operational journal entries whose currency matches the treasury currency.
- `FX_REVALUATION` and `FX_REVALUATION_REVERSAL` entries are base-currency valuation adjustments and therefore do not change original quantity.
- Database trigger rejects operational journal lines hitting a treasury account when journal currency does not match treasury currency.
- FX revaluation entries are the only explicit exemption.

### Foreign-currency cash count
- Cash count compares expected and physically counted amounts in the treasury's own currency.
- Stores expected/actual/difference in original currency plus the rate used and the YER_NEW effect.
- Cash over/short settlement posts the base effect while preserving the correct original-currency movement.

### Foreign-currency bank reconciliation
- Bank statement opening/closing balances and statement lines are stored in the bank account's original currency.
- Book movements expose both original and base values.
- Matching compares original-currency amounts.
- Outstanding book items and adjusted bank balance are calculated in original currency.
- Reconciliation result reports its currency explicitly.

### Period-end FX revaluation
- New `treasury_fx_revaluations` audit table.
- Revaluation uses the latest exchange rate valid at the valuation date.
- Target carrying value = original balance × period-end rate.
- Gain posts to `4250 — أرباح فروق العملة`.
- Loss posts to `6750 — خسائر فروق العملة`.
- Revaluation journal currency is `YER_NEW`, source type `FX_REVALUATION`.
- Re-running the same date is idempotent while data/rate is unchanged.
- If an existing period-end revaluation becomes stale, it is reversed and replaced rather than silently overwritten.
- Reopening a period/year reverses active FX revaluations in that period with an auditable `FX_REVALUATION_REVERSAL` entry.

### Period-closing control
- A nonzero treasury balance is subject to cash/bank controls even when there was no operational movement during the period.
- A foreign treasury with a balance/activity cannot pass period close unless it has a current revaluation exactly at period end.
- Revaluation is considered stale if original quantity changed afterwards or base carrying value no longer equals the revalued target.

### UI
- Treasury cards show original-currency balance and, for foreign currency, YER_NEW carrying value separately.
- Cash-count fields use selected treasury currency.
- Bank statements/reconciliation use bank currency.
- Accounting period screen provides end-of-period FX revaluation action and recent revaluation history.

## Migration 31 -> 32 (provisional)
Adds:
- original-currency fields to cash counts,
- original-currency/currency fields to bank statements,
- original amount to bank statement lines,
- `treasury_fx_revaluations`,
- treasury-currency consistency INSERT/UPDATE triggers.

No destructive migration or database recreation is used.

## Central integration warning
`31 -> 32` is **not a final project schema number**. The central integration chat must renumber this migration after resolving the users/security migration sequence. SQL semantics and data preservation must remain unchanged.

## Deliberately not included
Cross-currency treasury transfer/exchange (for example USD treasury -> YER treasury) remains blocked. It requires an explicit currency-exchange document with two rates/legs and realized FX treatment and should be implemented as a separate accounting phase rather than hidden inside a normal transfer.
