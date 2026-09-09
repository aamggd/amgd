# FUSH ERP Mobile v160 — Sales Conflict Inspector

- Baseline: v159 Sales & Receivables Cloud Mirror.
- versionCode: 160.
- Room schema remains 46; no migration added.
- Adds persisted, per-user conflict inspection for sales invoices, customer receipts and sales returns.
- Compares material business header fields plus invoice/receipt/return child rows with tolerant numeric comparison.
- Technical `createdAt` metadata is not considered a business conflict, avoiding false positives caused only by import timestamps.
- The UI lists the document number and the exact local/cloud field values that differ.
- Safety: no automatic overwrite or destructive conflict resolution is performed. Both financial copies remain preserved for review.
- No Supabase SQL migration is required for v160.
