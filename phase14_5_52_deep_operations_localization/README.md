# FUSH ERP Mobile — Phase 14.5.52 Deep Core Operations Localization

Apply after **Phase 14.5.51.1** in this order:
1. `01_build_gradle.patch`
2. `02_deep_resources_en.patch`
3. `03_deep_resources_ar.patch`
4. `04_sales_deep_localization.patch`
5. `05_purchases_deep_localization.patch`
6. `06_parties_deep_localization.patch`
7. `07_accounting_deep_localization.patch`
8. `08_inventory_deep_localization.patch`

## Scope
- Deep bilingual UI pass for Sales, Purchases, Customer/Supplier profiles, Accounting/Treasury, and Inventory.
- Adds 432 paired English/Arabic resource entries in `deep_operations.xml`.
- Converts deep dialogs, secondary tabs, statements, forms, confirmations, empty states, and operational labels to localized resources.
- Internal enum values, route keys, account codes, warehouse codes, and stored DB values stay unchanged.
- Preserves the in-app Arabic/English and Light/Dark controls from Phase 14.5.51.1.

## Safety boundary
Presentation/resources only. No Room schema/migration, DAO, posting, costing, stock quantity, production, commissions/payroll, permissions/authentication, or backup behavior changes.

## Validation
- English and Arabic deep resource files: 432 keys each, exact key-set match.
- XML parsing passed.
- Kotlin parser-oriented check found no syntax/unclosed/unexpected-token markers.
- All 8 patches re-applied sequentially over the reconstructed Phase 14.5.51.1 target.
- `git diff --check` passed.

## Branch test identity
- versionCode `93`
- versionName `0.15.4.52-ui-deep-core-localization`

Central integration owns final version/schema, full Android build, device regression, and official APK signing.
