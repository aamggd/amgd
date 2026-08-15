# Phase 14.5.51 — Core Operational Localization I

Apply after **14.5.50.1** in this exact order:

1. `01_localization_resources.patch`
2. `02_commerce_parties.patch`
3. `03_accounting_inventory.patch`

## Scope
This slice localizes the high-frequency operational surfaces for Sales, Purchases, Customers, Suppliers, Accounting/Journal, Treasury, and Inventory, while preserving internal route/section keys and all persisted business/status codes.

## Resource state
- English/default: `values/strings.xml`
- Arabic: `values-ar/strings.xml`
- 425 matching string keys in each resource set after this phase.
- Format placeholders match between Arabic and English.

## Safety boundary
UI/resources only. No Room schema/migration, DAO, accounting posting/reversal, inventory quantity/cost, sales/purchase calculation, customer/supplier balance, production, compensation/commission, authentication/permission, or backup/restore logic is intentionally changed. No files under `data/` or `domain/` are changed.

## Validation
All three patches pass `git apply --check` sequentially over the verified 14.5.50.1 source and reproduce the 14.5.51 changed files byte-for-byte. XML parses successfully, resource keys/placeholders match, `git diff --check` is clean, new `R.string` references resolve, and basic Kotlin delimiter checks pass. Full Android/Compose compilation was not available because the supplied source has no Gradle wrapper/Android SDK toolchain.

## Branch test identity
- versionCode `91`
- versionName `0.15.4.51-ui-core-operations-localization`

Central integration owns final integrated versioning, migrations, release build and permanent APK signing.
