# FUSH ERP Mobile v189 — Temporary Test Data Cleanup

- Baseline: v188 Legacy Treasury Cloud Hydration
- Application ID: `com.fush.erp.recovery`
- versionCode: `189`
- versionName: `0.15.4.140-temporary-test-data-cleanup`
- Room schema: `49` (unchanged)
- No destructive migration and no database recreation.

## Scope
Adds one temporary support permission: `SUPPORT_TEST_DATA_DELETE`.
It is available only to the protected `FUSH_SUPPORT` role during an active company-authorized Support Session.
Normal sales-return, transportation-fee and shipment-expense logic is unchanged.

## Typed maintenance commands
1. Delete an explicitly identified test sales invoice bundle, including its linked return, exclusive receipt, commission, stock movements, journal effects, shipment allocations and additional-charge settlements.
2. Delete an explicitly identified test shipment expense together with its party voucher, expense dimensions and accounting journal. Deletion is refused while the expense still has an active invoice allocation.

## Safety
- The reason must explicitly identify the operation as test/fake/demo data.
- Receipts shared with any other invoice make invoice deletion fail closed.
- Posted-journal DELETE guards are relaxed only inside the protected cleanup transaction and are reinstalled in `finally`; all normal insert/update lifecycle guards remain enforced.
- Support audit records are retained and remain immutable.
