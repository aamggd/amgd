# FUSH ERP Mobile — Phase 14.5.36 Purchases & Suppliers UI

Baseline: `0.15.4.35-ui-sales-customers`
Target: `0.15.4.36-ui-purchases-suppliers`
Branch: `fush/ui-professional-redesign`

## Purpose
Third UI professionalization package focused on Purchases and Suppliers.

## Patch order
1. `01_scope.patch`
2. `02_build_gradle.patch`
3. `03_purchases_screen.patch`
4. `04_supplier_list.patch`
5. `05_supplier_profile.patch`

Apply from the root of a source tree that already contains Phase 14.5.35:

```bash
cat phase14_5_36_purchases_suppliers_ui/patches/*.patch > /tmp/fush-ui-14.5.36.patch
git apply --check /tmp/fush-ui-14.5.36.patch
git apply /tmp/fush-ui-14.5.36.patch
```

## UI scope
- Purchases KPIs for invoice volume, supplier payables, overdue balances and credit purchases.
- Purchase invoice search and clearer invoice cards.
- Prominent supplier-aging access.
- Supplier KPIs and risk-aware payable cards.
- Professional supplier profile identity, payment-term status and activity summary.
- Improved supplier information and unified ledger presentation.

## Safety boundary
Presentation only. No Room schema, DAO query, purchase posting, inventory costing, supplier payment posting, purchase returns, accounting posting or other business logic is intentionally changed.

## Validation
The package is generated as a direct diff from the Phase 14.5.35 UI source. Sequential patch application was verified with `git apply --check`, followed by `git diff --check` with no errors. Android build validation remains separate because the supplied source package does not include the project Gradle wrapper/Android SDK toolchain in this environment.
