# v169 — Customer Receipt Print

- Adds a branded printable customer receipt document for customer collections.
- Opens Android print preview immediately after a successful collection post.
- Adds “طباعة السند” to historical collection rows in sales invoice details.
- Receipt includes customer, receipt number/date, cash amount, collection discount/reason, settlement total, exchange rate, treasury/bank, allocations to invoices, notes, and signature lines.
- Uses the v168 FUSH branded PDF header.
- No Room schema change; remains schema 46.
- No destructive migration.
