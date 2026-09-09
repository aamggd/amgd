# FUSH ERP Mobile v133 — Time / Selection / Backup Hardening

Baseline: v132 Integrated Release Hardening + Trusted Time.

## Closed in v133
- Trusted UTC can be strengthened/recovered from HTTPS when internet is available.
- HTTPS re-anchoring requires consensus from at least two independent origins; the first responder alone is not trusted.
- Offline time continues from `SystemClock.elapsedRealtime()` after a trusted anchor is established.
- First launch with Android automatic time disabled fails closed for sensitive operations until automatic time is restored or HTTPS consensus succeeds.
- Direct no-argument `Date()` / default `Calendar.getInstance()` clock reads were removed from main Kotlin source.
- Customer receipt, sales return, supplier payment and purchase return all reject future business dates at Service level.
- Sales return and purchase return dialogs expose editable return dates.
- `FushSearchableSelectionField.onCleared` is mandatory; core wrappers clear their backing selected object when visible text is edited.
- Near-expiry threshold (`near_expiry_days`) is included in portable backup settings and restored transactionally with rollback protection.
- General release pointers were refreshed so current review does not accidentally rely on obsolete v108/v128 status files.

## Identity / data safety
- applicationId: `com.fush.erp.recovery`
- versionCode: `133`
- versionName: `0.15.4.84-time-selection-backup-hardening1`
- Room schema: `39` (unchanged)
- No migration, no database recreation, no destructive fallback.

## Explicit non-scope
Manufacturing overhead allocation is not silently mixed into this hardening release because it changes inventory valuation, COGS and accounting. It must be implemented as a dedicated cost-accounting release with a non-destructive schema migration and explicit allocation records.

## Merge correction
During final validation, nullable callback type mismatches introduced by searchable-selector hardening were corrected in Expense, Maintenance, and Reports UI before v133 was accepted over v132. These corrections are UI typing/selection-safety fixes only and do not change Room schema or accounting data.
