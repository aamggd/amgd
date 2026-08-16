# FUSH ERP Mobile — Users/Permissions P4 SQLCipher Handoff

Branch: `fush/users-permissions`

Status: **CANDIDATE — EMULATOR VALIDATED / PHYSICAL DEVICE PENDING**

## Baseline

- Accepted Central Baseline: **Phase 14.5.54 Printing Integrated**
- Central source artifact workflow: `31909754750`
- Central source SHA-256: `8adc047bc38703fcb460344b4ddd2c7f18f0c47c3ca1a6c62a51d3ef590450a1`
- P1 is applied first from the already registered users/permissions P1 handoff.
- P4 validation head: `291cff73472224cc2b5cce3670920e550176c4c6`
- P4 validation workflow run: `31918572570`

## P4 patch identity

- Patch SHA-256: `aa2ee3674ac476e00d22fb84595d49d79bf48bd5e43c1b9a9a8bf2acb4caca8e`
- Source patch parts remain pinned in `fush/users-permissions`:
  - `security_central_14_5_54/P4_sqlcipher_parts/part_00.b64`
  - `security_central_14_5_54/P4_sqlcipher_parts/part_01.b64`
  - `security_central_14_5_54/P4_sqlcipher_parts/part_02.b64`
- Combined Base64 SHA-256: `f9e1aed8eea5c10b051a6a68a426d3f9cc659ee0b3daa736ab2cee59110b6ea5`

Reconstruct with:

```bash
cat part_00.b64 part_01.b64 part_02.b64 > P4_sqlcipher.combined.b64
base64 -d P4_sqlcipher.combined.b64 | gzip -dc > P4_sqlcipher.patch
sha256sum P4_sqlcipher.patch
```

## Changed files — exactly 7

1. `app/build.gradle.kts`
2. `app/src/androidTest/java/com/fush/erp/backup/DatabaseEncryptionInstrumentedTest.kt`
3. `app/src/main/java/com/fush/erp/backup/BackupRestoreManager.kt`
4. `app/src/main/java/com/fush/erp/backup/DatabaseEncryptionManager.kt`
5. `app/src/main/java/com/fush/erp/backup/DatabaseKeyManager.kt`
6. `app/src/main/java/com/fush/erp/data/AppContainer.kt`
7. `app/src/test/java/com/fush/erp/backup/DatabaseEncryptionManagerTest.kt`

## Functional change

- Adds `sqlcipher-android:4.17.0` Room open-helper integration.
- Generates a random 256-bit database passphrase.
- Stores only a wrapped passphrase; wrapping key is non-exportable in Android Keystore using AES-GCM.
- Migrates an existing plaintext `fush_erp.db` to SQLCipher before Room opens.
- Verifies database integrity and preserves `PRAGMA user_version` during migration.
- Uses a safety copy for rollback and removes plaintext safety/cache remnants after success.
- Keeps portable backup compatibility by exporting a temporary plaintext snapshot only inside private cache and deleting it after backup packaging.
- Restore remains compatible: the restored database is processed by `ensureEncrypted()` before Room use.

## Room / migration impact

- Room schema change: **NONE**.
- Central schema remains: **34**.
- New Room migration: **NONE**.
- Existing migrations remain unchanged through `33 -> 34`.
- No `fallbackToDestructiveMigration`.

## Business-module impact

- Accounting calculations: **not changed**.
- Inventory calculations: **not changed**.
- Production calculations: **not changed**.
- P4 changes only database file-at-rest handling plus backup/restore file handling.

## Validation

Workflow run `31918572570` on exact Central 14.5.54 + P1:

- Patch SHA gates: **PASS**
- Selective-apply guard: **PASS** — exactly 7 P4 files and no Room/Migration file changed
- Plaintext Central debug APK build: **PASS**
- Full Unit tests: **PASS** — `BUILD SUCCESSFUL`
- P4 Release build: **PASS** — `BUILD SUCCESSFUL`
- Android Emulator SQLCipher migration: **PASS** — 2/2 instrumented tests
- SQLCipher native library verification: **PASS** for `arm64-v8a` and `x86_64`
- Application ID: `com.fush.erp.recovery`

Validation artifact:
- Artifact ID: `9255807712`
- Artifact digest: `sha256:1f36b7633a95661150cf0a230e6702c782ca347e091f23315583821440c07f6d`

APK/patch SHA-256 from that artifact:
- Plaintext baseline debug APK: `9c53618518f927ce38a6980c1c2eac7c5ca385a88f8d0116b73716c0de3f79f8`
- P4 SQLCipher debug APK: `006d4cf7df7bd388761a521d6b65d514213e959461c7fb0d96b8c8561515f1f1`
- P4 release unsigned APK: `e9cfbd6a79dd69f1221fafda1e52d6262f7e5b411f07481e483b8010a8b6cd9d`
- Patch: `aa2ee3674ac476e00d22fb84595d49d79bf48bd5e43c1b9a9a8bf2acb4caca8e`

## Remaining acceptance gate

The branch plan explicitly requires encryption/upgrade testing on both **Emulator and a real device**. Emulator validation is complete. A real-device upgrade test remains mandatory before P4 may be marked `DONE` or applied to the Central application source. See `PHYSICAL_DEVICE_TEST.md` in this handoff directory.

Do not merge directly to `fush/main`. Do not allocate a new Room schema number for this patch. Do not add or create signing keys.
