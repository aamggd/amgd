# FUSH ERP Mobile v210 — FUSH AI Tool-Calling Assistant

## Goal
Upgrade the v206 deterministic read-only assistant into a permission-gated language-model orchestration layer while retaining an offline/local safe fallback.

## Security architecture
- The language model has no SQL/Room/Supabase ERP table access.
- Planning phase receives only the user question, short chat history, and names of tools already allowed by the local ERP permissions.
- Android validates every requested tool again before execution.
- All ERP reads execute locally against the existing DAO/service layer.
- Answer phase receives only the permission-gated tool summaries needed to phrase the answer.
- Maximum four tool calls per question.
- v210 exposes no create/post/update/delete/reverse/approve tool.
- If the private model gateway is unavailable or not configured, v206 local deterministic behavior remains available.

## Read-only tools
- `sales_summary`
- `customer_balance`
- `stock_item`
- `treasury_balances`
- `overdue_invoices`
- `purchases_summary`
- `production_status`
- `shipments_status`
- `business_summary`

## Cloud gateway
Supabase Edge Function: `fush-ai`

Authentication is validated inside the function using the caller's Supabase access token plus active membership in the requested FUSH organization.

Supported private model backends:
1. OpenAI-compatible HTTPS endpoint through server-side environment variables:
   - `FUSH_AI_BASE_URL`
   - `FUSH_AI_MODEL`
   - `FUSH_AI_API_KEY` (optional for private endpoints with network-level auth)
2. Self-managed Ollama/Llamafile through Supabase AI using `AI_INFERENCE_API_HOST` and `FUSH_AI_MODEL`.

No provider secret is embedded in the Android APK or source tree.

## UI
Each assistant response labels its engine:
- Private language model + local ERP tools
- ERP tools + local wording fallback
- Local safe mode

## Database
No Room schema change. v210 remains schema 52.
