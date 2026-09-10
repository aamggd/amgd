# FUSH ERP Mobile — Users/Permissions P4 SQLCipher Handoff

Source branch: `fush/users-permissions`

Status: **CANDIDATE — EMULATOR VALIDATED / REAL DEVICE UPGRADE TEST PENDING**

> This is an independent phase handoff. Do **not** merge the whole `fush/users-permissions` branch. Apply only the payload in this directory, and only after P1 has been accepted/applied in sequence.

## Baseline and dependency

- Accepted Central Baseline used for validation: **Phase 14.5.54 Printing Integrated**
- Central source workflow: `31909754750`
- Central source SHA-256: `8adc047bc38703fcb460344b4ddd2c7f18f0c47c3ca1a6c62a51d3ef590450a1`
- Required predecessor: `integration_current/handoffs/users-permissions/P1_session_lifecycle/`
- P4 source/validation head: `291cff73472224cc2b5cce3670920e550176c4c6`
- P4 validation workflow run: `31918572570`

If a newer Central Baseline is adopted before integration, re-establish the same functional changes on that baseline. Do not overwrite newer files with Phase 14.5.54 copies.

## Self-contained patch payload

Payload in this directory:

- `P4_sqlcipher.patch.gz.b64`
- Payload SHA-256: `c228030e244c913dcd08693f353a764f0b2bead76736d7d270998e819151ca68`
- Decoded patch SHA-256: `aa2ee3674ac476e00d22fb84595d49d79bf48bd5e43c1b9a9a8bf2acb4caca8e`

Reconstruct:

```bash
sha256sum P4_sqlcipher.patch.gz.b64
base64 -d P4_sqlcipher.patch.gz.b64 | gzip -dc > P4_sqlcipher.patch
sha256sum P4_sqlcipher.patch
```

Expected decoded patch SHA-256:

`aa2ee3674ac476e00d22fb84595d49d79bf48bd5e43c1b9a9a8bf2acb4caca8e`

`SHA256SUMS.txt` in this directory records the APK and patch checksums from the validated CI artifact.

## Changed files — exactly 7

1. `app/build.gradle.kts`
2. `app/src/androidTest/java/com/fush/erp/backup/DatabaseEncryptionInstrumentedTest.kt`
3. `app/src/main/java/com/fush/erp/backup/BackupRestoreManager.kt`
4. `app/src/main/java/com/fush/erp/backup/DatabaseEncryptionManager.kt`
5. `app/src/main/java/com/fush/erp/backup/DatabaseKeyManager.kt`
6. `app/src/main/java/com/fush/erp/data/AppContainer.kt`
7. `app/src/test/java/com/fush/erp/backup/DatabaseEncryptionManagerTest.kt`

## Functional change

- Adds `net.zetetic:sqlcipher-android:4.17.0` Room open-helper integration.
- Generates a random 256-bit database passphrase.
- Stores only a wrapped passphrase; the wrapping key is non-exportable in Android Keystore and uses AES-GCM.
- Migrates an existing plaintext `fush_erp.db` to SQLCipher before Room opens it.
- Verifies database integrity and preserves `PRAGMA user_version` during conversion.
- Uses a temporary safety copy for rollback and removes plaintext safety/cache remnants after successful migration or successful rollback.
- Keeps portable backup behavior by creating a temporary plaintext snapshot only in private cache for backup packaging, then deleting it.
- Restored databases pass through `ensureEncrypted()` before Room opens them.

## Room / migrations

- Room schema change: **NONE**.
- Central schema remains `34` for this validated candidate.
- New Room migration: **NONE**.
- Existing Room migrations remain unchanged through `33 -> 34`.
- No destructive migration.
- No `fallbackToDestructiveMigration`.
- This phase changes the physical database representation at rest, not the logical Room schema.

## Business-module impact

- Accounting calculations: **not changed**.
- Inventory calculations: **not changed**.
- Production calculations: **not changed**.
- Purchases/Sales calculations: **not changed**.
- Scope is database-at-rest handling plus backup/restore file handling only.

## CI validation completed

Workflow run `31918572570` on exact Central 14.5.54 + registered P1:

- Baseline SHA verification: **PASS**
- P1 and P4 payload SHA gates: **PASS**
- Selective-apply guard: **PASS** — exactly 7 P4 files
- Guard confirming no Room/Migration/schema file changed: **PASS**
- Plaintext Central debug APK build: **PASS**
- Full Unit Tests: **PASS**
- P4 Release build: **PASS**
- Android Emulator plaintext -> SQLCipher migration: **PASS — 2/2 instrumented tests**
- Native SQLCipher verification: **PASS** for `arm64-v8a` and `x86_64`
- Application ID: `com.fush.erp.recovery` unchanged

Validation artifact:

- Artifact ID: `9255807712`
- Artifact digest: `sha256:1f36b7633a95661150cf0a230e6702c782ca347e091f23315583821440c07f6d`

Artifact checksums:

- Plaintext baseline debug APK: `9c53618518f927ce38a6980c1c2eac7c5ca385a88f8d0116b73716c0de3f79f8`
- P4 SQLCipher debug APK: `006d4cf7df7bd388761a521d6b65d514213e959461c7fb0d96b8c8561515f1f1`
- P4 release unsigned APK: `e9cfbd6a79dd69f1221fafda1e52d6262f7e5b411f07481e483b8010a8b6cd9d`
- P4 patch: `aa2ee3674ac476e00d22fb84595d49d79bf48bd5e43c1b9a9a8bf2acb4caca8e`

## Remaining acceptance gate — mandatory

P4 remains **CANDIDATE**. It must not be marked READY or applied to the Central application source until the **Real Device Upgrade Test** is completed successfully.

Use `PHYSICAL_DEVICE_TEST.md` in this directory. The intended test is an in-place upgrade on a real Android device from the supplied/validated plaintext baseline debug build to the P4 SQLCipher debug build, preserving existing data and verifying that the database is encrypted after upgrade.

Until that gate passes:

- do not promote P4 to READY;
- do not integrate P4 into the Central app source;
- do not merge the whole specialized branch;
- do not change Room schema numbering for P4;
- do not create or replace any signing key.

No P5 or later users/permissions phase should start until a new Central Baseline/integration instruction is received.
