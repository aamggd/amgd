# FUSH ERP Mobile v186 — Direct Stock + Unified Sync + Print Fit

- versionCode: 186
- versionName: 0.15.4.137-direct-stock-unified-sync-print-fit
- Room schema: 49 (preserves v185 migration 48→49)

## Sales shipment choice
- Shipment is optional on a sales line.
- `بدون شحنة — بيع مباشر من المخزن` skips shipment allocations and shipment-cost allocations for that line while normal inventory/lot allocation still applies.
- Choosing a shipment retains v185 line-level shipment tracking and proportional shipment-cost allocation.

## Unified sync
- Primary UI action is `مزامنة الكل الآن`.
- Active organization membership authorizes sync transport; business roles remain operation-authority controls inside domain services.
- Master Data, Sales/Receivables, Purchases, Supplier Payments/Treasury, Inventory/Production, Accounting/Treasury and Sales Auxiliary/Shipments are executed as one company synchronization workflow.
- Legacy/domain-specific sync cards remain available under advanced sync/conflict details.

## PDF/print fit
- Sales invoice uses compact `singlePagePreferred` layout.
- Shared report PDF tables calculate column widths, wrap text, scale font size for wide reports and clip content to cell bounds so text cannot paint outside the page.
- Very large content may still continue to an additional page rather than being truncated.
