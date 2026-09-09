# Phase 14.5.20 — Customer Statement and Search

- Added customer search by Arabic/English name, customer code, phone, province, or address.
- Added a detailed customer account statement from each customer card.
- Statement shows posted sales invoices, receipts, sales returns, and cash-refund counterpart movements.
- Running balance is calculated after every event in base currency.
- Cash sales appear as invoice + automatic receipt and therefore settle to zero.
- Cash-refund returns appear as return credit + refund debit so the customer balance stays correct.
- Corrected receivable/outstanding calculations so only CUSTOMER_CREDIT returns reduce accounts receivable; CASH_REFUND affects cash instead.
- Summary shows total debit, total credit, and current customer balance.
- Room schema remains 23; no migration is required.
