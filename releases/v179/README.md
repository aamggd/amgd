# FUSH ERP Mobile v179 — Sales AdditionalCharges + Shipment Costs

Canonical project checkpoint for `com.fush.erp.recovery`.

- versionCode: `179`
- versionName: `0.15.4.130-sales-additional-charges-shipment-costs`
- Room schema: `48`
- Upgrade path: Room `46 -> 47 -> 48`, no destructive migration
- Unit tests: `552 / 552 PASS`
- `assembleRelease`: PASS
- `lintVitalRelease`: PASS
- `zipalign`: PASS
- APK signature: v2 + v3, one signer
- Certificate SHA-256: `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`

## Implemented in v179

1. Sales `AdditionalCharges` with configurable Principal/Agent accounting policy.
2. Many-to-many charge settlement against sales invoices with duplicate/over-allocation guards.
3. Recoverable customer-paid-on-behalf amounts separated from company expense and service revenue.
4. Shipment header, items/lots, actual shipment expenses, payment voucher/reference.
5. Shipment item ↔ invoice quantity allocations and shipment expense ↔ invoice cost allocations as independent many-to-many relations.
6. Actual shipment expense posts once to GL account `6430`; invoice allocation is analytical only and never posts a second freight journal.
7. Customer-facing freight/additional charge remains a separate pricing decision from actual shipment cost.
8. Shipment view shows total / allocated / remaining cost; invoice view shows linked shipment and allocated actual cost.
9. Fully allocated shipment quantity + cost closes the shipment automatically.
10. Conflict-safe bidirectional cloud mirror for AdditionalCharges and shipment documents/allocations.

## Acceptance example

Shipment quantity 10 packs, actual transport cost 70,000. First invoice links 5 packs: 35,000 allocation, 35,000 remains. Second invoice links remaining 5 packs: remaining 35,000 allocation and shipment can close. Allocation is capped by the remaining actual shipment expense.

## Source preservation

The v179 source delta, Supabase SQL, validation report, checksums and implementation contract are stored in this directory/branch. The private FUSH signing key and password are intentionally **not** stored in GitHub.

Canonical local artifact hashes are recorded in `FUSH_ERP_Mobile_v179-FINAL-SHA256.txt` and `SOURCE_PROVENANCE.md`.