# v174 — Accounting Purchases Report

- Builds on v173 without redesigning the Reports structure.
- Adds invoice accounting details: invoice/date/supplier/currency/payment type/due date/status, gross, discount, tax placeholder, net, paid and outstanding.
- Adds purchase analysis by item and month/period.
- Separates cash versus credit purchases.
- Adds supplier balances and aging buckets.
- Links purchase returns to the original invoice and settlement type.
- Adds three-way accounting reconciliation:
  1. Operational net purchases vs purchase-source movement on Inventory control account 1200.
  2. Credit purchase document effect vs purchase-source movement on Accounts Payable 2100.
  3. Operational supplier closing balance vs General Ledger Accounts Payable 2100 closing balance.
- Read-only reporting changes only. Room remains schema 46; no destructive migration.
