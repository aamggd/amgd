# v129 Cash Refund Actual-Cash Guard

- Sales CASH_REFUND is capped by net actual cash collected for the invoice.
- Purchase CASH_REFUND is capped by net actual cash paid for the invoice.
- Prior cash refunds reduce the remaining ceiling.
- Receipt reversal is blocked if it would leave an existing sales cash refund unsupported at any affected timeline point.
- Supplier-payment reversal is blocked by the symmetric rule.
- Cash invoices treat the original cash invoice amount as actual cash paid/collected.
- Settlement discounts never count as cash.
- Room schema remains 39; no migration.
