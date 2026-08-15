# FUSH ERP Mobile — Phase 14.5.52.1 Calendar-Only Dates

Apply after **Phase 14.5.52** in this order:

1. `01_scope.patch`
2. `02_build_gradle.patch`
3. `03_calendar_only_date_field.patch`

## What changes
- Makes the shared `FushDateField` read-only.
- Users can no longer type or paste a date manually into date fields.
- Dates are selected through the existing Material 3 calendar picker.
- Optional dates retain the existing Clear action.
- The underlying value remains `yyyy-MM-dd`; business parsing and stored values are unchanged.

## Coverage
Because operational date inputs were previously migrated to `FushDateField`, this applies to accounting/treasury, expenses, purchases, inventory, party vouchers, geography/currency pricing, and report date ranges that use the shared date component.

## Safety boundary
UI input behavior only. No Room schema/migration, DAO, accounting posting/reversal, stock quantity/costing, production, employee compensation, sales-rep commissions, permissions/authentication, or backup changes.

## Branch test identity
- versionCode `94`
- versionName `0.15.4.52.1-ui-calendar-only-dates`
