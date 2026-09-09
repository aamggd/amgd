# FUSH ERP Mobile — UI Professionalization 14

## Phase
14.5.47 — Professional Forms, Date Selection & User-Facing Status Language

## Scope
- Add a shared Material 3 date-field component with a calendar dialog while preserving the existing `yyyy-MM-dd` string contract used by current screen/domain parsers.
- Add shared decimal, integer and phone fields that request appropriate software keyboards without changing existing validation/calculation logic.
- Replace manual date entry in high-frequency accounting, purchase, expense, inventory, geography and party-voucher workflows with the shared date field.
- Apply numeric/phone keyboards to common sales, purchase, expense, inventory, customer/supplier and representative inputs.
- Replace technical PASS/FAIL and other code-like choices in Risk/Internal Control and Maintenance forms with Arabic labels while retaining the same stored codes.
- Translate remaining employee/representative voucher and commission status pills that exposed internal status codes.

## Safety boundary
Presentation/form-entry only. No Room schema/entity/migration, DAO query, accounting posting/reversal, inventory quantity/costing, purchase/sales calculation, customer/supplier balance, production/quality calculation, employee compensation, representative commission calculation, risk scoring/service rule, maintenance service rule, authentication, permissions or backup logic is intentionally changed.

## Compatibility notes
- Date picker confirmation writes the same `yyyy-MM-dd` text previously entered manually; existing parsers remain the source of truth for posting/report ranges.
- Numeric components only request the IME type. They do not coerce, round or alter values before existing validation.
- Branch version is TEST / BRANCH ONLY. Central integration owns the final integrated versionCode/versionName, migration ordering and signing.

## Validation target
- Ordered patch application over Phase 14.5.46 using `git apply --check`.
- `git diff --check` clean; no conflict markers.
- No Phase 14.5.47 change under `data/` or `domain/`.
- Parser-oriented Kotlin syntax checks on changed files.
- Full Android/Compose compile, date-picker device behavior, keyboard behavior, large-font/RTL testing and release regression remain central integration gates.
