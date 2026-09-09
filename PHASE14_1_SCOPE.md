# Phase 14.1 — Seasonality and Demand Forecast Foundation

- New planning module: **التخطيط والموسمية**.
- Uses actual posted sales and posted sales returns as the historical demand source.
- Separates planning by finished product and governorate policy: Taiz, Aden, Sana'a/old-note areas, and Other.
- Stores one configurable demand seasonality factor for each product × governorate × calendar month.
- Neutral default is `1.00`; the application does **not** invent which months are strong or weak.
- Shows the latest 12 calendar months, including zero-demand months.
- Forecasts the next month with the transparent formula:
  `12-month average net demand × configured seasonality factor`.
- Negative monthly net demand caused by returns is clamped to zero for the forecasting baseline, while the historical row still shows the actual negative net value.
- Database migration 12 → 13 preserves all existing ERP data and adds only `demand_seasonality`.
- Version: `0.14.1-phase14-seasonality-demand` / versionCode `30`.

## Planned continuation of Phase 14

- 14.2: demand-plan approval and manual overrides.
- 14.3: production plan and batch/material requirements.
- 14.4: safety stock and replenishment planning.
- 14.5: governorate expansion/readiness planning.
- 14.6: planning dashboard, alerts, reports, PDF/XLSX/print.
