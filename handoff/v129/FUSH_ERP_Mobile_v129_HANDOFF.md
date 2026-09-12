# FUSH ERP Mobile v129 — CASH_REFUND Actual-Cash Guard

Branch: `fush/v129-cash-refund-actual-cash-guard`

## Release identity
- AppID: `com.fush.erp.recovery`
- versionCode: `129`
- versionName: `0.15.4.80-cash-refund-actual-cash-guard1`
- Room schema: `39` (unchanged; no migration)

## Fixes persisted in GitHub
1. Sales CASH_REFUND actual-cash/timeline guard and receipt-reversal dependency guard.
2. Purchase CASH_REFUND actual-paid/timeline guard and supplier-payment-reversal dependency guard.
3. Regression tests and v129 release identity.

## Accounting policy
- CASH_REFUND may never exceed actual cash collected/paid for the same invoice as of the event date.
- Prior CASH_REFUND postings reduce the remaining refundable cash ceiling.
- Settlement discounts never count as cash.
- Cash invoices use the original cash invoice amount as actual cash.
- Reversing a receipt/payment is blocked if that reversal would leave any existing CASH_REFUND unsupported at any affected timeline point.

## Local validation completed
- Policy boundary 20,000 collected / 20,000 refund: PASS
- 20,000 collected / 50,000 refund: correctly rejected PASS
- 20,000 collected / 15,000 previous refund leaves 5,000: PASS
- Reversal leaving refund unsupported: correctly rejected PASS
- No `fallbackToDestructiveMigration`, `deleteDatabase()`, or `clearAllTables()` introduced.
- Signing certificate verified locally: SHA-256 `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`.

## Source artifact
`FushERP-Mobile-v129-CashRefundActualCashGuard-FINAL-Source.zip`
SHA-256: `79cde3961d08e816fd7cf1e0587efb88374a0cba0a7f4d91a8bc43a8adaba4c5`

Full v128→v129 patch SHA-256: `dcf802bdee1441681b7b0ba25d758608a7c97fbc27cb28b7c1adfcdb1e34b9f2`

## Build status
A full Android Gradle build was attempted locally, but the runtime has no Gradle 9.4.1 distribution / Android build environment and outbound distribution download is unavailable. Therefore no v129 APK is claimed as built or signed in this handoff. The signing key was not uploaded to GitHub.
