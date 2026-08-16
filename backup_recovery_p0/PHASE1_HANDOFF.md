# FUSH ERP Mobile - Backup/Recovery Phase P0 Handoff

- Branch: fush/backup-recovery
- Baseline source: validated Phase 14.5.54 printing-integrated artifact, workflow run 31909754750.
- Baseline source SHA-256: 8adc047bc38703fcb460344b4ddd2c7f18f0c47c3ca1a6c62a51d3ef590450a1.
- Baseline source tree: 1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff.
- Application ID: com.fush.erp.recovery.
- Room schema: 34 unchanged; no migration added.
- Phase scope: P0 only - new backups encrypt the entire ZIP payload with AES-256-GCM. SHA-256 database verification remains inside the encrypted manifest. Legacy plaintext format v1 remains read-only compatible for upgrade/recovery continuity.
- Key handling prerequisite: a non-exportable Android Keystore AES key is used so P0 is operational without placing key bytes in the backup or repository. Cross-device/key-loss recovery strategy is intentionally NOT solved here; that is P1.
- Accounting/inventory/production business logic: unchanged. Backup transport/storage only.
- Branch versionCode/versionName: unchanged from baseline; no official version allocated here.
- Signing: no key/certificate created or stored. CI builds unsigned release APK only.
- Patch SHA-256: c9b2eb29a64517ac59c8d25fd995a0ec963a9ac25e0d6dfe44899b7f95e6133a.

## Changed application files
- app/src/main/java/com/fush/erp/backup/BackupArchiveCodec.kt
- app/src/main/java/com/fush/erp/backup/BackupEncryptionKeyProvider.kt (new)
- app/src/main/java/com/fush/erp/backup/BackupRestoreManager.kt
- app/src/test/java/com/fush/erp/backup/BackupArchiveCodecTest.kt

## Required validation
- Unit: encrypted round trip; raw archive does not expose SQLite header/known DB marker; wrong key failure; ciphertext tamper failure; legacy plaintext read compatibility; failed extraction deletes partial destination.
- Regression: full existing unit suite.
- Release: :app:assembleRelease; Application ID verification; schema 34 verification; destructive migration scan.

## Known issue intentionally left for P1
A backup encrypted by the device-local Keystore key cannot be recovered after key loss/reinstall or on another device. P1 must define and implement the approved recovery/wrapping strategy before cross-device recovery is accepted.
