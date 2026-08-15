# Fush ERP Phase 14.5.50 — Period Comparison & Variance

Base: verified Phase 14.5.49 Treasury Reports artifact.
Branch: `fush/reports-printing`.

## Scope
- Add a central Reports tab: `مقارنة الفترات`.
- Compare the selected current period with the immediately preceding equivalent period.
- Show current value, previous value, absolute difference, and percentage change for key financial indicators.
- Use existing report/accounting sources for sales, purchases, collections, P&L revenue/expenses/net profit, inventory, receivables, overdue receivables, and maintenance cost.
- Do not assign automatic positive/negative meaning to an increase or decrease.
- When the previous value is zero, show no percentage instead of an infinite/misleading ratio.
- `كل الفترة` intentionally has no previous comparison period.
- Export through the existing professional PDF/XLSX engine.
- Keep `com.fush.erp.recovery` and Room schema 27 unchanged.
