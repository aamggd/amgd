# FUSH ERP Mobile v144 — Date Integrity Hardening

## Identity
- applicationId: `com.fush.erp.recovery`
- versionCode: `144`
- versionName: `0.15.4.95-date-integrity-hardening`
- Room schema: `44` (unchanged; no database migration required)

## Confirmed fixes
- Purchase invoices now use the central future-document-date guard before posting.
- Inventory count start now exposes an explicit count date.
- Inventory count snapshot is calculated as-of the selected business day.
- Inventory count adjustments and their journal entry use the count effective date; `postedAt` remains the actual posting/audit timestamp.
- Backdated negative count variances use the historical lot outflow safety guard.
- Warehouse-transfer UI and service use the same end-of-business-day balance semantics.
- Future-dated warehouse transfers are rejected by the service.
- Internal-control default seeding failures are no longer swallowed silently; they are logged.

## Regression gates
- Unit tests: 466 PASS / 0 failures / 0 errors / 0 skipped.
- lintVitalRelease: PASS.
- assembleRelease: PASS.
- zipalign: PASS.
- APK signature v2: PASS.
- APK signature v3: PASS.

## Full Lint status
Full `lintRelease` still reports exactly 3 errors in `ExpenseFlowSafety.kt` plus warnings. The expense source was deliberately left byte-for-byte unchanged from v143 per the owner's explicit no-expense-modification constraint. No new Full Lint errors were introduced by v144.

## Expense no-change verification
`ExpenseFlowSafety.kt` and `ExpenseScreens.kt` are byte-for-byte identical to v143.
