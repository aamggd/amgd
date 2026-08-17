# FUSH ERP Mobile — SQLCipher P4 Central35 Candidate Status

Branch: `fush/users-permissions`

Status: **CANDIDATE / NOT DONE — PHYSICAL ANDROID DEVICE GATE PENDING**

## Pinned Central basis

- Central integration identity: `ddae764a236cb1916a46e194ccb2d3c24ca6b919`
- Central source identity: `30b028b75d6463c07afd0419429f53c7937fabb1`
- Application ID: `com.fush.erp.recovery`
- Logical Room schema: `35`

The old Phase 14.5.54 / Room34 P4 patch is **not** an integration input by itself. Its SQLCipher behavior was re-established selectively on the pinned current Central source using `P4_rebase_apply_v2.py`. No full branch merge is performed.

## Current functional-diff boundary

The Central35 P4 functional diff is restricted to these seven paths only:

1. `app/build.gradle.kts`
2. `app/src/androidTest/java/com/fush/erp/backup/DatabaseEncryptionInstrumentedTest.kt`
3. `app/src/main/java/com/fush/erp/backup/BackupRestoreManager.kt`
4. `app/src/main/java/com/fush/erp/backup/DatabaseEncryptionManager.kt`
5. `app/src/main/java/com/fush/erp/backup/DatabaseKeyManager.kt`
6. `app/src/main/java/com/fush/erp/data/AppContainer.kt`
7. `app/src/test/java/com/fush/erp/backup/DatabaseEncryptionManagerTest.kt`

Explicitly excluded from the P4 diff:
- `FushDatabase.kt`
- Room schema JSON files
- migration files
- accounting/inventory/production business logic
- signing configuration/keystore material

The current `34 -> 35` Central migration remains registered and untouched. P4 changes the physical database-at-rest representation, not the logical Room schema.

## Automated validation completed on the Central35 candidate

- Selective Central35 rebase guard: **PASS**
- Application ID invariant: **PASS**
- Logical Room schema remains 35: **PASS**
- Current `34 -> 35` migration retained: **PASS**
- destructive fallback scan: **PASS**
- signing-keystore inclusion scan: **PASS / none added**
- security regression suite: **PASS**
- full Unit tests: **PASS**
- Release build: **PASS**
- SQLCipher native-library packaging check: **PASS**
- Android Emulator plaintext database -> encrypted SQLCipher migration test: **PASS**
- Emulator actual APK install/replace path (`baseline` then `adb install -r candidate`): **PASS**

These automated/emulator results do **not** satisfy the physical-device acceptance gate.

## Mandatory remaining gate

A **real Android device** must perform an in-place upgrade from the actual current Central application version while retaining its existing plaintext database. The test must prove all of the following:

1. the existing application is upgraded without uninstall/clear-data;
2. plaintext DB remains recoverable until encrypted conversion is verified successful;
3. all representative pre-upgrade data remains present after conversion;
4. the encrypted DB opens successfully after app restart;
5. an existing user can still log in;
6. representative read/write operations continue to work;
7. the database is no longer readable as a normal plaintext SQLite file after successful conversion;
8. rollback/safety behavior does not delete the original plaintext DB on conversion failure;
9. the APK used for the in-place test has a signing identity compatible with the actually installed application; no replacement/new signing keystore may be generated.

Until every real-device check passes, P4 remains **CANDIDATE / NOT DONE** and must not be promoted to READY or merged into Central.

## Central control

This status file does not modify `fush/integration-current`, does not merge P4, does not allocate a new Room schema number, and does not create signing material.
