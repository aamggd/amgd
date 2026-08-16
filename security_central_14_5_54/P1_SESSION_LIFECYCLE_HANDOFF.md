# FUSH ERP Mobile — P1 Session Lifecycle Handoff

Branch: `fush/users-permissions`

Central baseline used: `Phase 14.5.54 Printing Integrated`

Central source artifact: `FushERP-Mobile-Phase14.5.54-Printing-Integrated-Source.zip`

Central source SHA-256: `8adc047bc38703fcb460344b4ddd2c7f18f0c47c3ca1a6c62a51d3ef590450a1`

## Scope

This is a selective security change built on the current Central Baseline. It does not copy old 14.5.38 application files over 14.5.54.

P1 fixes the insecure session-policy default while retaining current session settings compatibility:

- automatic timed logout is always enabled;
- persisted legacy `automaticLogoutEnabled=false` cannot weaken the effective policy;
- normal-user idle timeout is capped at 5 minutes;
- ADMIN idle timeout is capped at 3 minutes;
- normal-user absolute session duration is capped at 480 minutes (8 hours);
- ADMIN absolute session duration is capped at 240 minutes (4 hours);
- configured values may tighten these caps but may not weaken them;
- existing `sessionVersion` invalidation on user disable / role change / password change / new login remains unchanged.

## Changed files

1. `app/src/main/java/com/fush/erp/data/SessionSettingsStore.kt`
2. `app/src/main/java/com/fush/erp/domain/SecurityPolicy.kt`
3. `app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt`
4. `app/src/main/java/com/fush/erp/ui/screens/SecurityScreens.kt`
5. `app/src/test/java/com/fush/erp/domain/SecurityPolicyTest.kt`

## Room / migrations

- Room schema change: **No**
- Migration added: **No**
- Central Room schema remains `34`.
- `MIGRATION_32_33_SECURITY` and `MIGRATION_33_34_FIXED_ASSETS` are left untouched.
- No `fallbackToDestructiveMigration`.

## Business impact

- Accounting logic: no change.
- Inventory logic: no change.
- Production logic: no change.
- Sales / purchases calculations: no change.
- Security session expiration only.

## Patch

Payload: `P1_session_policy.patch.gz.b64`

Combined payload SHA-256: `dd8d26290ccc43766b56dc66f327ebc26a8bc87cc6cfb2faf5b6e87cd1eb4901`

Decoded patch SHA-256: `6058262a40acfea0e5b516ede92b0e4dd657e1b746a227fcc7ead8ff9e087957`

Decode/apply:

```bash
base64 -d P1_session_policy.patch.gz.b64 | gzip -dc > P1_session_policy.patch
echo '6058262a40acfea0e5b516ede92b0e4dd657e1b746a227fcc7ead8ff9e087957  P1_session_policy.patch' | sha256sum -c -
git apply --check P1_session_policy.patch
git apply P1_session_policy.patch
```

## Acceptance gate

P1 is ready for handoff only after the dedicated Central-14.5.54 workflow passes:

- full Unit tests;
- `assembleRelease`;
- Application ID remains `com.fush.erp.recovery`;
- Room schema remains `34`;
- no destructive migration;
- security-policy regression tests pass.

Do not merge directly to `fush/main`.
