# v203 — Room 49→50 startup migration hotfix

## Root cause
The v202 production database bootstrap (`AccountingWaveBRoomBootstrap`) opens `fush_erp.db` before `AppContainer` and registered migrations only through 48→49. Although `AppContainer` already registered `MIGRATION_49_50_LOCAL_FX_ENGINE`, Room failed earlier during startup with "A migration from 49 to 50 was required but not found".

## Fix
- Register `MIGRATION_49_50_LOCAL_FX_ENGINE` in `AccountingWaveBRoomBootstrap`.
- Keep Room schema at 50; no new schema change is introduced by this hotfix.
- Keep all existing explicit migrations and data-preserving behavior.
- No destructive migration, database deletion, or database recreation.
- Add contract coverage that the startup bootstrap includes 48→49 then 49→50 in order.

## Release identity
- App ID: `com.fush.erp.recovery`
- versionCode: 203
- versionName: `0.15.4.154-local-yemen-fx-migration-hotfix`
- Room schema: 50
