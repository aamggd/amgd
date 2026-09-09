# FUSH ERP Mobile v124 — Sales Discount 100% Guard

Baseline: v123 TransactionChronologyGuards.

## Policy
Standard sales invoices accept discounts in the range `0 <= discountPct < 100`.
A 100% discount is not represented as a zero-value sales invoice.
Free goods / commercial samples must use a separate operational document lifecycle rather than a 100% sales discount.

## Guard layers
- `SalesMath.validateDiscount`: rejects 100% and above.
- `SalesMath.discountOriginal`: rejects 100% and above.
- `SalesMath.effectiveBaseUnitPriceBase`: rejects 100% and above.
- `SalesMath.totalOriginal`: requires gross > 0 and discount amount < gross amount.
- `SalesService.postSale`: requires final original/base totals > 0 before persistence/accounting posting.
- Sales UI blocks posting when discount is 100% or above and shows an explicit message.

## Data / schema
No Room schema change. `FUSH_DB_SCHEMA_VERSION` remains 38 and existing historical invoices are not modified.
