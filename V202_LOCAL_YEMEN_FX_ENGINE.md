# v202 — Local Yemen FX Engine

- Baseline: v201 AdminUnusedShipmentDelete.
- AppID unchanged: `com.fush.erp.recovery`.
- versionCode: 202.
- Room schema: 50, migration 49→50, no destructive migration.
- Primary source adapter: YECES (USD/SAR, Sana'a/Aden, buy/sell) through Supabase Edge Function.
- Comparison: ACAPS YETI for USD sell; SAMA 3.75 USD/SAR peg cross-check for SAR.
- Supabase stores normalized source batches and rate rows before returning them to Android.
- Android stores fetched quote batches separately from approved accounting rates.
- Accountant can refresh and approve; high-conflict/stale approval requires EXCHANGE_RATE_OVERRIDE + reason.
- Approval writes immutable daily `fx_snapshots` plus canonical historical `exchange_rates` for USD/SAR/YER_OLD.
- Posted documents keep their own exchangeRate and are never repriced by later refreshes.
