# FUSH ERP Mobile — Audit Findings Registry

Branch: `fush/audit-evaluation`

Plan phase: `Part 1 — Initial Technical Audit Formalization`

Audited Central Baseline: **Phase 14.5.54 Printing Integrated**

- Central source/integration branch: `fush/integration-printing-14.5.54`
- Central branch record commit: `5095ba46a676fd6a8e048f2325c433a1f336d05d`
- Validated workflow commit: `36ac48935ecc9d71c899481b0901a1c69b7354be`
- Final integrated source tree: `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`
- Application ID: `com.fush.erp.recovery`
- Baseline Room schema: `34`
- Evaluation rule: **Findings only. No defect is repaired in this branch.**

## Status vocabulary

- `OPEN`: defect/risk is demonstrated and has not been fixed.
- `READY_FOR_OWNER`: evidence and acceptance criteria are sufficient for the owner branch to implement a fix.
- `FIXED_PENDING_RETEST`: owner branch reports a fix, but audit retest is not complete.
- `CLOSED`: audit retest reproduced the original scenario and verified the acceptance criteria.

No finding may move directly from `OPEN` to `CLOSED` without a documented retest.

---

## AE-SEC-001 — Backup archive stores the database without encryption

- **Severity:** HIGH
- **Owner Branch:** `fush/users-permissions` (security ownership; coordinate with backup UI if needed)
- **Status:** READY_FOR_OWNER
- **Impact:** A copied backup archive can expose the complete ERP database to anyone who can extract the ZIP. SHA-256 verifies integrity but does not provide confidentiality.
- **Expected:** Backup archives containing business/accounting data are encrypted at rest using an authenticated encryption design; restore requires the authorized secret/key flow and rejects tampered ciphertext.
- **Actual:** `BackupArchiveCodec.writeArchive()` writes `database/fush_erp.db` directly into a standard ZIP entry and stores only its SHA-256 in the manifest. The Room builder uses the normal Room/SQLite builder without an encrypted SupportFactory.
- **Evidence:**
  - `app/src/main/java/com/fush/erp/backup/BackupArchiveCodec.kt:22-47`
  - `app/src/main/java/com/fush/erp/backup/BackupArchiveCodec.kt:91-112`
  - `app/src/main/java/com/fush/erp/data/AppContainer.kt:26-32`
- **Reproduction:**
  1. Create a backup from the application.
  2. Open the resulting archive using any standard ZIP reader.
  3. Extract `database/fush_erp.db` without supplying an encryption password/key.
  4. Confirm the manifest hash is an integrity field, not encryption.
- **Acceptance Criteria:**
  - Backup database bytes are not recoverable by a standard ZIP extraction without the authorized decryption flow.
  - Encryption is authenticated; modified ciphertext fails restore.
  - Backup/restore compatibility and failure behavior are covered by tests.
  - No key/password is embedded in source, Gradle, workflow, or repository.
  - Existing unencrypted backups have an explicit compatibility/migration policy.
- **Retest:** Required after owner-branch fix.

## AE-DB-002 — Room migration chain lacks real migration instrumentation tests

- **Severity:** HIGH
- **Owner Branch:** `fush/bugfixes-errors` (coordinate final schema numbering with `fush/integration-current`)
- **Status:** READY_FOR_OWNER
- **Impact:** A release can pass unit tests and `assembleRelease` while an actual installed database fails or loses/mis-shapes data during upgrade.
- **Expected:** Every supported upgrade path is verified with Room migration tests using historical schema assets and representative data; destructive fallback remains forbidden.
- **Actual:** The source registers migrations continuously from `1→2` through `31→32`, then `32→33 SECURITY` and `33→34 FIXED_ASSETS`, but there is no `app/src/androidTest` directory and no `MigrationTestHelper` / `runMigrationsAndValidate` coverage. The schema archive contains `12-23`, `25-28`, `31-34`, leaving historical gaps relative to the migration chain.
- **Evidence:**
  - `app/src/main/java/com/fush/erp/data/Migrations.kt` — `MIGRATION_1_2` through `MIGRATION_31_32`
  - `app/src/main/java/com/fush/erp/data/SecurityMigrations.kt` — `MIGRATION_32_33_SECURITY`
  - `app/src/main/java/com/fush/erp/data/Migrations.kt` — `MIGRATION_33_34_FIXED_ASSETS`
  - `app/src/main/java/com/fush/erp/data/AppContainer.kt:28-32`
  - `app/schemas/com.fush.erp.data.FushDatabase/` — present schemas: `12-23`, `25-28`, `31-34`
  - `app/src/androidTest/` — absent in the audited source package
- **Reproduction:**
  1. Inspect the registered migration list in `AppContainer.kt`.
  2. Inspect `app/schemas/...` and compare it to the supported migration chain.
  3. Search test sources for `MigrationTestHelper` or `runMigrationsAndValidate`; none are present.
- **Acceptance Criteria:**
  - Instrumented migration tests run against every supported previous production schema required by policy.
  - Tests insert representative accounting, inventory, production, user/security and fixed-asset data before migration and verify it after migration.
  - Missing historical schema JSON required for supported paths is restored from validated historical sources, not invented.
  - `fallbackToDestructiveMigration` remains absent.
  - Final migration numbering is assigned only by integration control.
- **Retest:** Required after owner-branch fix.

## AE-DATA-003 — Application startup performs silent historical production/inventory data repair

- **Severity:** HIGH
- **Owner Branch:** `fush/production-quality` with `fush/inventory` review
- **Status:** READY_FOR_OWNER
- **Impact:** Opening the application can alter production batches, lot numbers, expiry dates, item tracking flags and stock movement lot/expiry values outside an explicit migration or user-approved correction workflow. This can affect traceability and historical auditability.
- **Expected:** Historical business data changes occur through a versioned migration or an explicit audited correction operation with deterministic eligibility and tests; normal startup does not silently rewrite posted operational history.
- **Actual:** `FushErpApp` calls `container.seedIfNeeded()` at startup. `seedIfNeeded()` calls `repairLegacyFinishedGoods()`, which executes SQL UPDATE statements against `items`, `production_batches`, and `stock_movements`, including shelf-life/tracking flags, expiry dates and batch/lot numbers.
- **Evidence:**
  - `app/src/main/java/com/fush/erp/ui/FushErpApp.kt:40-45`
  - `app/src/main/java/com/fush/erp/data/AppContainer.kt:145-156`
  - `app/src/main/java/com/fush/erp/data/AppContainer.kt:161-215`
- **Reproduction:**
  1. Prepare a database containing a FINISHED_GOOD with missing/invalid shelf life and a matching legacy production batch/stock movement.
  2. Record the values before application startup.
  3. Launch the application and allow startup seed to complete.
  4. Re-read the same rows and observe automatic updates performed by `repairLegacyFinishedGoods()`.
- **Acceptance Criteria:**
  - Normal startup does not perform undocumented historical mutation.
  - Any required one-time repair is versioned, deterministic, idempotent and auditable.
  - Production batch/lot/expiry and stock movement history remain internally consistent after upgrade.
  - Regression tests cover affected legacy and already-correct data.
- **Retest:** Required after owner-branch fix.

## AE-I18N-004 — Localization is incomplete because user-visible Arabic text remains hard-coded in Kotlin

- **Severity:** MEDIUM
- **Owner Branch:** `fush/ui-professional-redesign`
- **Status:** READY_FOR_OWNER
- **Impact:** English mode can show mixed Arabic/English UI and reports, reducing usability and making RTL/LTR behavior inconsistent.
- **Expected:** User-visible strings are resource-backed/localized, with complete Arabic/English coverage and correct RTL/LTR layout behavior.
- **Actual:** The project has `values/strings.xml` and `values-ar/strings.xml`, but static inspection found Arabic characters in 82 Kotlin source files, including data/domain error messages and multiple UI screens. This demonstrates that localization is not resource-complete.
- **Evidence:**
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-ar/strings.xml`
  - Examples: `AppContainer.kt`, `BackupRestoreManager.kt`, `SecurityPolicy.kt`, accounting/inventory/production screen files and report/export code contain Arabic literals.
- **Reproduction:**
  1. Switch the app to English.
  2. Exercise accounting, inventory, production, backup/restore, validation and error paths.
  3. Record Arabic literals that remain visible.
- **Acceptance Criteria:**
  - No user-visible hard-coded Arabic/English string remains in Kotlin except explicitly documented non-localizable data/constants.
  - English mode is English throughout supported screens/reports/errors.
  - Arabic mode preserves correct RTL rendering.
  - Automated resource/lint or equivalent regression checks prevent reintroduction.
- **Retest:** Required after owner-branch fix.

## AE-SEC-005 — Automatic session logout is disabled by default

- **Severity:** MEDIUM
- **Owner Branch:** `fush/users-permissions`
- **Status:** READY_FOR_OWNER
- **Impact:** Fresh installations or users without saved session settings can remain logged in indefinitely with respect to the configured idle/max-session policy because expiration is bypassed while automatic logout is disabled.
- **Expected:** Secure session expiration is enabled by default according to the approved role/session policy, and disabling it is either prohibited or explicitly controlled/audited.
- **Actual:** `SessionTimeoutSettings.automaticLogoutEnabled` defaults to `false`; `SessionSettingsStore` also reads a missing preference as `false`; `SessionPolicy.shouldExpire()` returns `false` immediately when automatic logout is disabled.
- **Evidence:**
  - `app/src/main/java/com/fush/erp/data/SessionSettingsStore.kt:10-15`
  - `app/src/main/java/com/fush/erp/domain/SecurityPolicy.kt:86-96`
  - `app/src/main/java/com/fush/erp/domain/SecurityPolicy.kt:103-114`
- **Reproduction:**
  1. Use a clean installation/no saved session settings.
  2. Authenticate and leave the app idle beyond the configured timeout.
  3. Verify that expiration logic is bypassed because automatic logout defaults to disabled.
- **Acceptance Criteria:**
  - Fresh installs default to the approved secure timeout policy.
  - Privileged role limits meet the central security policy.
  - Unit/integration tests verify idle and maximum-session expiration defaults and persisted settings.
  - Any administrator override is explicit, permission-controlled and auditable.
- **Retest:** Required after owner-branch fix.

## AE-PRINT-006 — PDF table cells truncate body content after four wrapped lines

- **Severity:** MEDIUM
- **Owner Branch:** `fush/reports-printing`
- **Status:** READY_FOR_OWNER
- **Impact:** Long descriptions, party names, account names or references can be omitted from printed/exported PDF evidence. This is especially risky for accounting narratives and audit documentation.
- **Expected:** Report output preserves the complete business value or provides an explicit safe continuation mechanism without silently discarding text.
- **Actual:** `ReportExportSupport.cellLines()` truncates wrapped content to a supplied maximum and appends an ellipsis. Body rows call it with `maxLines = 4`; header rows use 3. Column widths are weighted primarily from header labels.
- **Evidence:**
  - `app/src/main/java/com/fush/erp/ui/export/ReportExportSupport.kt:228-244`
  - `app/src/main/java/com/fush/erp/ui/export/ReportExportSupport.kt:246-299`
- **Reproduction:**
  1. Create/export a report row whose narrative wraps to more than four lines at the chosen column width.
  2. Generate the PDF.
  3. Observe the fourth line ending in `…` and later text absent from the PDF.
- **Acceptance Criteria:**
  - No legally/accountingly relevant cell value is silently lost.
  - Long text either expands safely, continues on another row/page, or is represented by an explicitly documented alternative that preserves the full value.
  - Multi-page RTL rendering remains correct.
  - Automated PDF/render regression covers long Arabic and English values.
- **Retest:** Required after owner-branch fix.

## AE-PRINT-007 — No visual/device-level PDF and printing regression suite

- **Severity:** MEDIUM
- **Owner Branch:** `fush/reports-printing`
- **Status:** READY_FOR_OWNER
- **Impact:** Data/math unit tests may pass while final PDF pagination, RTL shaping, clipping, printer integration or page breaks are visually wrong on Android devices.
- **Expected:** Critical reports have render-level and device/printer-path acceptance tests in addition to data/math tests.
- **Actual:** The audited source contains unit tests such as `AccountingSectionExportTest`, report math tests and spreadsheet value tests, but no `app/src/androidTest` directory and no source tests referencing `PdfDocument`, screenshots/rendering, `PrintManager`, or equivalent visual validation.
- **Evidence:**
  - `app/src/test/java/com/fush/erp/ui/screens/AccountingSectionExportTest.kt`
  - `app/src/test/java/com/fush/erp/ui/export/SpreadsheetCellValueTest.kt`
  - `app/src/androidTest/` — absent
- **Reproduction:**
  1. Inspect the test tree.
  2. Search for PDF render/screenshot/PrintManager instrumentation coverage.
  3. Confirm that current automated coverage validates data structures/math, not final rendered pages on Android.
- **Acceptance Criteria:**
  - Representative accounting, treasury, expense and other critical reports are rendered and visually checked for RTL/LTR, long text and multiple pages.
  - At least one real-device or emulator printing/export smoke flow is documented and repeatable.
  - Regression artifacts make clipping/overlap/page-break failures detectable.
- **Retest:** Required after owner-branch fix.

## AE-BUILD-008 — Distributed source package is not self-contained for reproducible Gradle execution

- **Severity:** MEDIUM
- **Owner Branch:** `fush/bugfixes-errors`
- **Status:** READY_FOR_OWNER
- **Impact:** A recipient cannot execute the canonical Gradle Wrapper command from the delivered source alone; build reproducibility depends on an externally installed Gradle version/workflow environment.
- **Expected:** Source delivery contains the standard Gradle Wrapper launcher and wrapper metadata/jar pinned to the validated Gradle distribution, or an equally deterministic approved build bootstrap.
- **Actual:** The 14.5.54 source package contains `gradle/libs.versions.toml` but does not contain `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, or `gradle/wrapper/gradle-wrapper.properties`. The validated GitHub workflow installs Gradle 9.4.1 externally.
- **Evidence:**
  - Source package root — wrapper launcher files absent.
  - `gradle/` contains `libs.versions.toml` only.
  - `.github/workflows/build-printing-integration-14.5.54.yml` installs Gradle `9.4.1` through `gradle/actions/setup-gradle`.
- **Reproduction:**
  1. Extract the delivered source package on a clean workstation with JDK/Android SDK but no system Gradle.
  2. Attempt the standard `./gradlew :app:testDebugUnitTest` or Windows wrapper command.
  3. Observe that the wrapper executable/metadata is missing.
- **Acceptance Criteria:**
  - Delivered source can run the documented canonical build/test commands on a clean supported environment without relying on an unpinned local Gradle installation.
  - Wrapper distribution is pinned to the validated version and contains no secrets.
  - CI and local build instructions use the same supported toolchain.
- **Retest:** Required after owner-branch fix.

---

## Part 1 finding summary

| ID | Severity | Owner Branch | Status |
|---|---|---|---|
| AE-SEC-001 | HIGH | `fush/users-permissions` | READY_FOR_OWNER |
| AE-DB-002 | HIGH | `fush/bugfixes-errors` | READY_FOR_OWNER |
| AE-DATA-003 | HIGH | `fush/production-quality` + `fush/inventory` review | READY_FOR_OWNER |
| AE-I18N-004 | MEDIUM | `fush/ui-professional-redesign` | READY_FOR_OWNER |
| AE-SEC-005 | MEDIUM | `fush/users-permissions` | READY_FOR_OWNER |
| AE-PRINT-006 | MEDIUM | `fush/reports-printing` | READY_FOR_OWNER |
| AE-PRINT-007 | MEDIUM | `fush/reports-printing` | READY_FOR_OWNER |
| AE-BUILD-008 | MEDIUM | `fush/bugfixes-errors` | READY_FOR_OWNER |

Part 1 does not declare the application production-ready. Parts 2-7 remain required before the master audit can reach final status.
