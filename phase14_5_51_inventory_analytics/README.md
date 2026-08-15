# Fush ERP Phase 14.5.51 — Professional Inventory Analytics

Base: verified Phase 14.5.50 Period Comparison artifact.
Branch: `fush/reports-printing`.

## Scope
- Upgrade the existing `المخزون` report instead of adding another navigation tab.
- Add period movement analysis from real `stock_movements` data.
- Add slow/dormant inventory analysis based on the last actual outbound movement.
- Keep stock that has never been issued as a separate, explicit state instead of inventing an outbound date.
- Add expiry/lot reporting only where a real `expiryDate` exists and the lot has positive stock as of the report date.
- Show a lighter last-100 movement list on-screen while PDF/XLSX includes all period movements.
- Export valuation, stock age/activity, expiry lots, and detailed movement tables through the professional export engine.
- Keep `com.fush.erp.recovery` and Room schema 27 unchanged.

## Classification rules
- Slow-moving: 90+ days since last actual outbound movement.
- Dormant: 180+ days since last actual outbound movement.
- Never issued: shown separately; if first inbound is 90+/180+ days old it is flagged as `بدون صرف` rather than given a fake outbound date.
- Expiry: expired, <=30 days, 31–90 days, >90 days.
