# FUSH ERP Mobile — Phase 14.5.35 Sales & Customers UI

Baseline: `0.15.4.34-ui-professionalization-1`
Target: `0.15.4.35-ui-sales-customers`
Branch: `fush/ui-professional-redesign`

## Purpose
Second UI professionalization package focused on Sales and Customers.

## Patch order
1. `01_scope.patch`
2. `02_build_gradle.patch`
3. `03_sales_screen.patch`
4. `04_customer_list.patch`
5. `05_customer_profile.patch`

Apply from the root of a source tree that already contains Phase 14.5.34 UI Professionalization 1:

```bash
cat phase14_5_35_sales_customers_ui/patches/*.patch > /tmp/fush-ui-14.5.35.patch
git apply --check /tmp/fush-ui-14.5.35.patch
git apply /tmp/fush-ui-14.5.35.patch
```

## Safety boundary
Presentation only. No Room schema, DAO query, accounting posting, sales posting, collections, returns, inventory posting, commissions or credit-control business logic is intentionally changed.

## Validation
The package is generated as a direct diff from the Phase 14.5.34 UI source. Both combined and sequential patch application were verified with `git apply --check`, followed by `git diff --check` with no errors. Android build validation is separate because this source package does not include the project Gradle wrapper/Android SDK toolchain.
