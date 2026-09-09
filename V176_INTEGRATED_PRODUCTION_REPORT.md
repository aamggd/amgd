# FUSH ERP Mobile v176 — Integrated Production Report

- applicationId: `com.fush.erp.recovery`
- versionCode: `176`
- versionName: `0.15.4.127-integrated-production-report`
- Room schema: `46` (no migration required)

## Report additions
- BOM standard vs actual material issue by production order, quantity variance, variance cost, and variance %.
- Planned vs actual production and production variance.
- Actual manufacturing cost split into materials, labor, directly-linked manufacturing overhead, total cost, and unit cost.
- WIP reconciliation using GL account `1210` with opening, additions, transfers to finished goods, losses/other movements, and closing balance.
- Reject/scrap/rework analysis using recorded production and quality data; fields not recorded by the current schema are explicitly shown as unavailable rather than inferred.
- Product-level production analytics.
- Labor productivity based on recorded operator assignments and labor cost; actual labor hours are reported only when available in recorded data.
- Downtime/maintenance analysis using recorded maintenance/failure data; utilization percentages are not fabricated if actual run-time/capacity is not recorded.
- Raw-material lot to finished-goods lot traceability through production orders.
- Production-order unit-cost comparison with ±15% abnormal-variance alert.
- Final production totals and reconciliation with inventory/accounting sources.
- PDF column-width/wrapping rules improved for long production-order, lot, product, material, reason, and responsible-party values.

## Validation
- Unit tests: 533 passed, 0 failed, 0 errors, 0 skipped.
- `assembleRelease`: PASS.
- `lintVitalRelease`: PASS.
- APK zipalign: PASS.
- APK Signature Scheme v2: PASS.
- APK Signature Scheme v3: PASS.
- Signers: 1.
- Signing certificate SHA-256: `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`.
