# FUSH ERP Mobile — Phase 14.5.47 Professional Forms

Apply after Phase 14.5.46 in this order:
1. `01_scope.patch`
2. `02_build_gradle.patch`
3. `03_shared_form_components.patch`
4. `04_accounting_expense_forms.patch`
5. `05_sales_purchase_forms.patch`
6. `06_inventory_geography_forms.patch`
7. `07_party_people_forms.patch`
8. `08_risk_control_forms.patch`
9. `09_maintenance_forms.patch`

Highlights:
- Shared Material 3 date field with calendar dialog while keeping the existing `yyyy-MM-dd` text contract.
- Shared decimal, integer and phone fields that request appropriate IME/keyboards without changing existing calculations or validation contracts.
- Calendar selection added to accounting/report ranges, purchase/payment/expiry dates, expense dates, inventory transfer/expiry dates, geography effective/pricing dates and party vouchers.
- Numeric/phone keyboard improvements across common sales, purchase, expense, inventory and representative forms.
- Risk/Internal Control forms no longer expose PASS/FAIL and status/severity codes as editable English technical values; Arabic labels map to the same stored codes.
- Maintenance inspection/result/type/criticality selections are presented in Arabic while preserving stored codes.
- Remaining employee/representative commission and voucher status pills are translated for the operator.

Safety boundary: UI/form-entry only. No Room schema/migration, DAO, accounting posting, stock/costing, sales/purchase math, balances, production/quality calculations, compensation/commission calculations, authentication or service logic is intentionally changed.

Branch version: 86 / `0.15.4.47-ui-professional-forms` — TEST / BRANCH ONLY. Central integration owns the official integrated version, migration order, build/signing and APK release.

Validation:
- All 9 ordered patches pass `git apply --check` and apply sequentially over the verified Phase 14.5.46 source.
- Resulting changed files match the Phase 14.5.47 working source byte-for-byte.
- `git diff --check` clean, no conflict markers.
- No Phase 14.5.47 changes under `data/` or `domain/`.
- Parser-oriented Kotlin checks found no syntax-token errors in the new shared form component and the sampled changed screens; full Android/Compose compilation remains a central integration gate.
