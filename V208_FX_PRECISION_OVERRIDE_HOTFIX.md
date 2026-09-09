# FUSH ERP Mobile v208 — Sales FX Precision + Authorized Override Hotfix

- versionCode: 208
- versionName: 0.15.4.159-fx-precision-override-hotfix
- Room schema remains 51; no database migration is required.
- Sales invoice approved historical FX rate is kept at canonical 8-decimal accounting precision in UI state.
- Four-decimal display formatting is no longer fed back into invoice posting.
- A different invoice FX rate is allowed only with EXCHANGE_RATE_OVERRIDE permission and a reason of at least 5 characters.
- Every approved sales FX override is written to the immutable audit trail as SALES_FX_RATE_OVERRIDE.
