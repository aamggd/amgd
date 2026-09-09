# FUSH ERP Mobile v188 — Legacy Treasury Cloud Hydration

## Problem fixed
On a second device, accounting cloud hydration could fail with:

`ACCOUNTING_SOURCE_REQUIRES_STABLE_EVENT_ID (code 1811)`

when the cloud contained historical posted journals whose source type was one of the pre-stable-ID
`TREASURY_*` values.

## Fix
- Keeps the database fail-closed guard unchanged for every new local posting.
- During cloud hydration only, an historical blocked treasury journal header is inserted temporarily
  as `MANUAL` + `STAGING` inside the existing Room transaction.
- A deterministic compatibility `sourceId` is derived from the cloud source reference / entry number.
- The original historical `TREASURY_*` source type is restored while still `STAGING`.
- Journal lines are hydrated and the normal exact-balance transition changes the journal to `POSTED`.
- No direct `POSTED` insert, no destructive migration, no database reset, and no replay of treasury
  cash effects outside the imported journal itself.
- The original historical source type remains unchanged after hydration, so the local payload remains
  equal to the existing cloud document on later syncs and does not create an artificial conflict.

## Version
- App ID: `com.fush.erp.recovery`
- versionCode: `188`
- versionName: `0.15.4.139-legacy-treasury-cloud-hydration`
- Room schema: `49` (unchanged)
