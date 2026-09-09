# FUSH ERP Mobile v104 — P0 Step 2 Voucher Idempotency

Status: IMPLEMENTED / STATIC + PURE POLICY VALIDATED / GRADLE GATE PENDING

## Scope
Prevent duplicate treasury/accounting postings caused by rapid double-submit or retry of the same user-confirmed voucher operation.

## Changes
- Added `TreasuryVoucherOperationIdentity`.
- `AccountingService.VoucherRequest` now requires a stable `operationId`.
- Journal `sourceId` for `postVoucher()` is now `voucher:<operation UUID>` instead of a fresh UUID generated inside the service.
- A retry of the same operation returns the already-posted journal id after fail-closed payload verification.
- Reuse of the same operation id with a changed payload or different user is rejected.
- Canonical treasury voucher source types are replay-safe at the database guard boundary:
  - CUSTOMER_RECEIPT
  - SUPPLIER_PAYMENT
  - EXPENSE_PAYMENT
  - EMPLOYEE_PAYMENT
  - TRANSFER
  - ADJUSTMENT
- Expense, Treasury voucher, and Sales Rep commission-payment dialogs create one operation id per opened dialog.
- All three posting hosts have a synchronous in-flight guard and disable the confirm action while posting.

## Data / compatibility
- Application ID unchanged: `com.fush.erp.recovery`
- versionCode unchanged: 104
- Room schema unchanged: 38
- No migration added.
- No destructive migration enabled.
- No signing material included.

## Validation completed here
- Pure Kotlin compilation of:
  - AccountingIntegrationContract.kt
  - TreasuryMovementType.kt
  - AccountingPostingIdempotencyPolicy.kt
  - TreasuryVoucherOperationIdentity.kt
- Pure policy assertions PASS.
- Static scan confirms every active `AccountingService.VoucherRequest(...)` constructor supplies `operationId`.
- Static scan confirms `postVoucher()` no longer generates `sourceId = UUID.randomUUID()`.

## Required Gradle gate before P0 Step 3
Run:

`./gradlew :app:testDebugUnitTest --tests "com.fush.erp.domain.AccountingPostingIdempotencyPolicyTest" --tests "com.fush.erp.domain.TreasuryVoucherOperationIdentityTest" --tests "com.fush.erp.domain.TreasuryVoucherDoubleSubmitContractTest" --no-daemon`

Then run:

`./gradlew :app:assembleDebug --no-daemon`

Do not classify Step 2 as COMPLETE until both gates pass.
