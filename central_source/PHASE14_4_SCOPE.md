# Phase 14.4 — Advanced seasonality and summer/winter forecasting

- Keeps the existing monthly seasonality factor independently for every product + province.
- Adds a 24-month actual seasonal analysis with zero-demand months included.
- Operational default season calendar used only for analysis: summer = Apr-Sep, winter = Oct-Mar.
- Does not invent seasonality percentages: missing monthly factors remain neutral at 1.00 and can be edited by the user.
- Summer/winter forecast uses the last-12-month planning baseline multiplied by the average configured monthly factors for each season.
- Adds cross-province comparison for the selected finished product.
- Adds audit events whenever a monthly seasonality factor is created or changed.
- No Room schema change; database stays at schema 16.
- Build version: versionCode 38 / 0.15.3-phase14.4-seasonality-advanced.
