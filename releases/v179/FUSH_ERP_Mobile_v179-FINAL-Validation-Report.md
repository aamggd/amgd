# FUSH ERP Mobile v179 — Final Validation Report

## Identity
- Application ID: `com.fush.erp.recovery`
- versionCode: `179`
- versionName: `0.15.4.130-sales-additional-charges-shipment-costs`
- Room schema: `48`
- Upgrade path: `46 -> 47 AdditionalCharges -> 48 Shipment Cost Allocation`
- No destructive migration / no database deletion.

## Sales AdditionalCharges
- Configurable charge types and Principal/Agent policy snapshot.
- Bearer, amount, currency, FX, payment status, paid-by, treasury account/date/reference.
- Accounting treatment: recoverable / company expense / service revenue.
- Independent many-to-many settlement between charges and sales invoices.
- Duplicate/over-allocation guards with remaining amount error.
- Customer-direct payment does not inflate A/R.

## Shipment Cost Allocation
- Shipment header: number/date/from warehouse/destination province/status/transport reference.
- Shipment items: item, base quantity and lot/batch.
- Actual shipment expenses: transport/loading/customs/other + currency/FX + treasury payment voucher/reference.
- Actual shipment expense posts once to account `6430`; shipment number and province are analytical dimensions.
- Shipment item <-> invoice and shipment expense <-> invoice are independent many-to-many allocation tables.
- Invoice allocation is analytical only and never posts a second GL entry.
- Customer-facing AdditionalCharges are independent from actual shipment cost.
- Shipment view shows total cost, allocated cost and remaining cost; invoice view shows linked shipment and allocated actual cost.
- Fully allocated quantity + cost closes the shipment automatically.

## Acceptance example
- Shipment quantity: 10 packs.
- Actual transport cost: 70,000.
- First invoice quantity: 5 packs -> allocation = 35,000.
- Remaining shipment cost = 35,000.
- Second 5 packs -> allocation = 35,000.
- Allocation cannot exceed the remaining actual shipment cost.

## Tests and build
- Unit tests: `552 / 552 PASS`
- Failures: 0
- Errors: 0
- Skipped: 0
- `assembleRelease`: PASS
- `lintVitalRelease`: PASS
- `zipalign`: PASS

## Signing
- Alias: `fush_erp_recovery`
- APK Signature Scheme v2: PASS
- APK Signature Scheme v3: PASS
- Signer count: 1
- Certificate SHA-256: `22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586`
- Same package and signer as v178; versionCode increased 178 -> 179.

## Cloud sync
- v179 includes conflict-safe bidirectional cloud mirror for AdditionalCharges and shipment documents/allocations.
- Supabase SQL is delivered separately and must be applied once to the same FUSH Supabase project before the new auxiliary documents can synchronize between devices.
- Cloud hydration writes document rows directly and does not replay GL/Treasury posting side effects.
