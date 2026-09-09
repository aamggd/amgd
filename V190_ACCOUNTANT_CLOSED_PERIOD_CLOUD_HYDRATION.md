# FUSH ERP Mobile v190 — Accountant Closed-Period Cloud Hydration Hotfix

## Scope
This release is intentionally isolated to the accountant cloud-sync failure `ACCOUNTING_PERIOD_NOT_OPEN` seen while hydrating already-posted historical treasury journals on a second device.

## Behavior
- Historical cloud `TREASURY_*` journals are hydrated as the internal `CLOUD_LEGACY_TREASURY_HYDRATION` STAGING alias.
- Lines are inserted and validated while the header remains STAGING.
- The final transition is one atomic SQLite UPDATE that restores the original `TREASURY_*` source and changes STAGING -> POSTED.
- The accounting-period DB guard exempts only that exact cloud finalization shape, with a stable `cloud-legacy:*` source id.
- Normal local posting into a closed accounting period remains blocked with `ACCOUNTING_PERIOD_NOT_OPEN`.
- The internal alias can never remain POSTED and cannot finalize to non-treasury source types.

## Upgrade safety
- Application ID remains `com.fush.erp.recovery`.
- versionCode: 190.
- versionName: `0.15.4.141-accountant-closed-period-cloud-hydration`.
- Room schema remains 49.
- No destructive migration and no database recreation.
- This hotfix does not add the requested ADMIN maintenance shortcut; that work is deliberately deferred so this sync fix remains isolated.
