# FUSH ERP Mobile v104 — P0 Step 3 Period Fail-Closed

Status: IMPLEMENTED / STATIC-VALIDATED / GRADLE+RUNTIME PENDING

## Objective
Prevent any accounting/operational posting from continuing when the posting date is not covered by an OPEN accounting period.

## Changes
- Added `AccountingPeriodDatabaseGuard` and install it on application cold-open.
- POSTED journal INSERT fails when no OPEN period covers `entryDate`.
- Transition/update to POSTED fails when no OPEN period covers `entryDate`.
- Removed the misleading `?: return` from `AccountingService.requirePostingPeriodOpen`; missing period remains fail-closed via `AccountingDao.periodForDate` / `AccountingPeriodPostingPolicy`.
- Added service-level gates before business mutation for:
  - Sales invoice
  - Customer receipt allocation
  - Sales return
  - Purchase invoice
  - Purchase return
  - Supplier payment allocation
  - Opening inventory
  - Production material issue
  - Production issue correction
  - Production batch acceptance
  - Production batch rejection
- Added defense-in-depth gate at the production journal boundary.
- Added `AccountingPeriodFailClosedContractTest`.

## Data safety
- App ID unchanged: `com.fush.erp.recovery`
- Room schema remains 38.
- No migration added.
- No destructive migration fallback added.
- No signing material included.

## Validation performed in assistant environment
- 45 static invariants: PASS.
- SQLite trigger semantic probe: PASS for missing period, CLOSED period, OPEN period, and DRAFT -> POSTED without OPEN period.
- Kotlin syntax compile for the new database guard and period policy: PASS.

## Still required before closing P0-3
Run targeted Gradle tests and assembleDebug on the Windows/Android build environment, then runtime-check a closed/missing-period rejection on the disposable emulator.
