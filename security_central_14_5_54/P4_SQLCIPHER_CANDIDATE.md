# FUSH ERP Mobile — P4 SQLCipher / At-Rest Encryption Candidate

Branch: `fush/users-permissions`

Base: exact `Phase 14.5.54 Printing Integrated` + validated P1 Session Lifecycle handoff.

Central source SHA-256: `8adc047bc38703fcb460344b4ddd2c7f18f0c47c3ca1a6c62a51d3ef590450a1`

P1 decoded patch SHA-256: `6058262a40acfea0e5b516ede92b0e4dd657e1b746a227fcc7ead8ff9e087957`

P4 payload combined SHA-256: `f9e1aed8eea5c10b051a6a68a426d3f9cc659ee0b3daa736ab2cee59110b6ea5`

P4 decoded patch SHA-256: `aa2ee3674ac476e00d22fb84595d49d79bf48bd5e43c1b9a9a8bf2acb4caca8e`

## P4 scope

- SQLCipher for Android `4.17.0`.
- Room integration through `SupportOpenHelperFactory`.
- Random 256-bit database passphrase.
- Passphrase stored only wrapped by a non-exportable Android Keystore AES-256-GCM key.
- Existing plaintext `fush_erp.db` is migrated before Room opens.
- Source DB integrity and `user_version` are checked before conversion.
- Encrypted temp DB is checked before atomic replacement.
- Rollback safety copy is used on failure and removed after a successful migration/rollback.
- Stale plaintext migration safety copies and interrupted backup plaintext snapshots are cleaned on application start.
- Backup/restore compatibility is preserved by exporting a temporary plaintext SQLite snapshot to private cache only; the snapshot is deleted in `finally` and stale remnants are removed on the next start.
- Restore still applies before `AppContainer`; the restored plaintext DB is encrypted before Room opens it.
- Android Auto Backup remains disabled by the existing Central manifest.

## Changed files — P4 only

1. `app/build.gradle.kts`
2. `app/src/main/java/com/fush/erp/data/AppContainer.kt`
3. `app/src/main/java/com/fush/erp/backup/BackupRestoreManager.kt`
4. `app/src/main/java/com/fush/erp/backup/DatabaseEncryptionManager.kt` (new)
5. `app/src/main/java/com/fush/erp/backup/DatabaseKeyManager.kt` (new)
6. `app/src/test/java/com/fush/erp/backup/DatabaseEncryptionManagerTest.kt` (new)
7. `app/src/androidTest/java/com/fush/erp/backup/DatabaseEncryptionInstrumentedTest.kt` (new)

## Room / migrations

- Room logical schema change: **No**.
- New Room migration: **No**.
- Schema remains Central `34`.
- Existing `32 -> 33 SECURITY` and `33 -> 34 FIXED_ASSETS` remain unchanged.
- No `fallbackToDestructiveMigration`.

Encryption changes the physical representation of the database file only. The instrumented test explicitly preserves and verifies `PRAGMA user_version = 34`.

## Business impact

- Accounting calculations: unchanged.
- Inventory quantities/costing: unchanged.
- Production calculations: unchanged.
- Sales/purchases calculations: unchanged.
- Backup/restore I/O path: updated only to handle encrypted live DB safely.

## Required gates

Before this candidate can be marked `Done` under the new branch plan:

1. P1 dependency applies cleanly to exact Central 14.5.54.
2. P4 patch applies cleanly after P1.
3. Full Unit tests PASS.
4. `assembleRelease` PASS.
5. Emulator migration test PASS using a real plaintext SQLite file with data and `user_version=34`.
6. Room opens the encrypted DB and executes a transaction/read-only transaction.
7. Framework SQLite cannot query the encrypted file.
8. SQLCipher native libraries are present in release APK.
9. **Physical Android device upgrade/encryption test PASS.** This final gate cannot be claimed from a GitHub emulator.

Until gate 9 passes, P4 remains **CANDIDATE / NOT DONE** and must not be registered as a Ready integration handoff.

No signing key or password is stored in this branch.
