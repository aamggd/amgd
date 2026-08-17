# Phase 14.5.10 — Partial Purchase Returns

## Goal
Redesign purchase returns so one posted return document can contain partial quantities from one or multiple original purchase-invoice lines.

## Delivered
- Quantity input per original invoice line with purchased / already-returned / remaining quantities shown.
- Multiple partial lines can be posted in one purchase-return document.
- Server-side validation prevents duplicate selections and over-returning a line.
- Exact original lot/expiry stock is validated in aggregate before posting, preventing the return from making that lot negative.
- One consistent return timestamp is used for the return header, stock movements, numbering, and journal entry.
- Return reason is mandatory.
- Legacy single-line API remains available and delegates to the new multi-line service.

## Database
Room schema remains 21; existing purchase_return_lines already supports multiple lines per return.
