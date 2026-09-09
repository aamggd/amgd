# FUSH ERP Mobile v206 — FUSH AI Read-Only Assistant

- Baseline: v205 only.
- App ID unchanged: `com.fush.erp.recovery`.
- versionCode: 206.
- versionName: `0.15.4.157-fush-ai-readonly-assistant`.
- Room schema remains 51; no migration and no destructive database behavior.
- Adds a local, read-only assistant UI named FUSH AI.
- The assistant does not call a public LLM and does not send ERP data to the internet.
- Every data tool enforces the existing role permission before reading data.
- Supported initial intents: sales today/month, customer balance, item stock, treasury balances,
  overdue invoices, monthly purchases, production status, shipment follow-up, and daily business summary.
- No posting, deletion, approval, or update action is exposed by v206.
