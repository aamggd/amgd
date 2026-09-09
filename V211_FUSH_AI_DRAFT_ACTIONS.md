# FUSH ERP Mobile v211 — FUSH AI Draft Actions

## Scope
v211 extends v210 with isolated AI-generated operational drafts. The assistant can propose and store drafts for:
- Sales invoice
- Treasury voucher
- Production order

## Safety boundary
- AI drafts are stored only in `fush_ai_drafts`.
- Creating, approving, or cancelling an AI draft does **not** call `SalesService.postSale`, `AccountingService.postVoucher`, or `ProductionService.createOrder`.
- Drafts do not create stock movements, GL journals, treasury movements, receivables, commissions, shipment allocations, or production material issues.
- Approval means "approved draft", not "posted ERP document".
- Each draft action is permission-gated using the same real posting permission that would be required by the target module.
- Audit events are written for CREATE / APPROVE / CANCEL of `FUSH_AI_DRAFT`.

## Permissions
- Sales invoice draft: `SALES_POST`
- Treasury voucher draft: `TREASURY_POST`
- Production order draft: `PRODUCTION_POST`

## Room
- Schema 52 -> 53
- Non-destructive migration `MIGRATION_52_53_FUSH_AI_DRAFT_ACTIONS`
- Existing operational tables are not altered.

## Supabase FUSH AI gateway
The `fush-ai` Edge Function v2 advertises the three draft tools to a future configured LLM provider. The gateway prompt explicitly forbids operational execution, deletion, posting, and accounting approval. The Android app independently rechecks tool permissions and creates only local draft rows.

## UI
FUSH AI now shows quick actions for the three draft types and displays draft ID/status. A user can approve or cancel a pending draft from the chat. The UI states that approval has no financial or inventory effect.

## Next gate
A future execution gate may convert an APPROVED draft into the existing module's final form and require a separate explicit confirmation before any operational service is invoked.
