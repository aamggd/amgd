# FUSH ERP Mobile v179 — AdditionalCharges + Shipment Cost Allocation

## Shipment tracking
- Shipment header: shipment no/date/from warehouse/destination province/status/transport reference.
- Shipment items: item/base quantity/lot.
- Actual shipment expenses: transport/loading/customs/other, currency/rate, treasury payment and payment voucher.
- One GL account for actual shipment expenses: account 6430. Province and shipment number are analytical dimensions, not separate GL accounts.
- Shipment item ↔ invoice quantity allocation and shipment expense ↔ invoice cost allocation are separate many-to-many tables.
- Actual expense posts once. Invoice allocation has no journal effect and therefore cannot double-post freight.
- Customer-facing AdditionalCharges are separate pricing/accounting decisions and are never derived automatically from actual shipment cost.
- Invoice profitability prefers active shipment expense allocations and only falls back to the legacy invoice_geographic_costs record when no shipment allocation exists.

## Example
Shipment: 10 packs to Al-Hawban, actual transport expense 70,000 base.
- INV-0001 sells 5 linked packs: 35,000 is allocated analytically; 35,000 remains on shipment.
- Later invoice sells remaining 5: remaining 35,000 is allocated.
- When shipment quantities and actual expense are fully allocated, shipment status becomes CLOSED.

## Database
- Room 46→47: AdditionalCharges.
- Room 47→48: shipment tracking/cost allocation.
- No destructive migration.
