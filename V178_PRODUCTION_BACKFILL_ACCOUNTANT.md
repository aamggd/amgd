# FUSH ERP Mobile v178 — Production Backfill + Accountant Bidirectional Production

- Baseline: v177 Accounting Bidirectional Sync only.
- versionCode: 178
- versionName: 0.15.4.129-production-backfill-accountant
- App ID unchanged: com.fush.erp.recovery
- Room schema unchanged: 46
- ACCOUNTANT may publish CLOSED production documents, but is not added to inventory publishing roles.
- CLOSED local production orders missing from cloud are backfilled regardless of outbound cursor / closedAt age.
- Existing cloud order numbers are compared first: identical = unchanged, different = explicit production conflict.
- Cloud download hydrates production document rows directly and never re-runs ProductionService posting/stock/GL effects.
- v177 General Ledger, Treasury and accounting conflict sync are preserved.
