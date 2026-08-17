# Users / Permissions — P1 Session Lifecycle Handoff

Source branch: `fush/users-permissions`

Source handoff commit: `b04efcc95c6ce241610610fac2b639617910751e`

Validated Central Baseline: `Phase 14.5.54 Printing Integrated`

Central source SHA-256: `8adc047bc38703fcb460344b4ddd2c7f18f0c47c3ca1a6c62a51d3ef590450a1`

Validation workflow: `Validate Users Permissions P1 on Central 14.5.54`

Workflow run: `31917946736`

Validation result: **PASS**

## Scope

- timed logout cannot be disabled by a legacy/persisted false setting;
- normal-user idle cap = 5 minutes;
- ADMIN idle cap = 3 minutes;
- normal-user absolute session cap = 480 minutes / 8 hours;
- ADMIN absolute session cap = 240 minutes / 4 hours;
- configured values may tighten but not weaken those caps;
- existing sessionVersion invalidation on disable/role/password/new login is preserved.

## Changed files

1. `app/src/main/java/com/fush/erp/data/SessionSettingsStore.kt`
2. `app/src/main/java/com/fush/erp/domain/SecurityPolicy.kt`
3. `app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt`
4. `app/src/main/java/com/fush/erp/ui/screens/SecurityScreens.kt`
5. `app/src/test/java/com/fush/erp/domain/SecurityPolicyTest.kt`

## Room / migrations

- Room schema delta: **none**
- Migration added: **none**
- Central schema remains `34`
- no destructive migration
- no `fallbackToDestructiveMigration`

## Functional impact

- Accounting: no calculation/business-logic change.
- Inventory: no stock/cost calculation change.
- Production: no manufacturing calculation change.
- Purchases/Sales: no transaction calculation change.
- Security: session expiry policy only.

## Validation

- Patch applies cleanly to exact Phase 14.5.54 source: **PASS**
- Full Unit Tests: **PASS**
- `assembleRelease`: **PASS**
- Application ID `com.fush.erp.recovery`: **PASS / unchanged**
- Room schema `34`: **PASS / unchanged**
- Destructive migration scan: **PASS / none**

## Patch integrity

Payload file: `P1_session_policy.patch.gz.b64`

Payload SHA-256: `dd8d26290ccc43766b56dc66f327ebc26a8bc87cc6cfb2faf5b6e87cd1eb4901`

Decoded patch SHA-256: `6058262a40acfea0e5b516ede92b0e4dd657e1b746a227fcc7ead8ff9e087957`

Apply only after confirming Phase 14.5.54 is still the accepted Central Baseline. If a newer baseline is adopted, rebase this five-file functional change instead of overwriting newer files.

This handoff does **not** merge to `fush/main` and does not alter signing material.
