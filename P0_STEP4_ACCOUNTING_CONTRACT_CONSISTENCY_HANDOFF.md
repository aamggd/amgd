# FUSH ERP Mobile v104 — P0 Step 4 Accounting Contract Consistency

Status: IMPLEMENTED / STATICALLY VALIDATED / GRADLE TARGETED TESTS PENDING

## Purpose
Resolve the three legacy AccountingP1IntegrityPolicyTest failures without weakening current runtime idempotency.

## Root cause
The historical P1 audit helper `p1StableKeyCandidates()` had drifted into an alias for every current `STABLE_SOURCE`. Later commission lifecycle sources are correctly replay-safe today, but they were not part of the original P1 snapshot. Terminal reversal sources are replay-safe yet intentionally have `reversalPolicy = NONE`, so treating every current replay-safe source as an original forward P1 event made the legacy reversal assertion invalid.

## Fix
- Freeze `p1StableKeyCandidates()` to the original 13 P1 stable forward events.
- Add `currentStableReplayCandidates()` as the current runtime STABLE_SOURCE view.
- Keep AccountingPostingIdempotencyPolicy on the current runtime view, including commission lifecycle sources.
- Preserve terminal reversal semantics (`reversalPolicy = NONE`); no fake second reversal path was invented.
- Update current contract tests to consume the current runtime API and add an explicit historical P1 snapshot test.

## Invariants
- No journal posting amounts/accounts changed.
- No source type was removed from the current replay-safe runtime set.
- App ID unchanged: `com.fush.erp.recovery`.
- Room schema unchanged: 38.
- No migration added.
- No destructive migration fallback added.
