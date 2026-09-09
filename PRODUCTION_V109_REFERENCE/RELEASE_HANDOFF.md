# FUSH ERP Mobile v109 — Accounting Authorization Hardening

## Production baseline
The production APK for this release was merged over the known-good signed v108 runtime hotfix, not rebuilt as a wholesale replacement from the historical source tree.

- Baseline APK: FUSH_ERP_Mobile_v108-UsersPermissions-RuntimeHotfix-FUSHIcon-SIGNED.apk
- Baseline SHA-256: a4c010b5f49560b3fcc85af2b2060f428e383bcc9060c5c87a698592d8097e8f
- Final App ID: com.fush.erp.recovery
- Final versionCode: 109
- Final versionName: 0.15.4.60-accounting-authorization-hardening1
- Room schema: 38 (unchanged)
- Database migration: none

## Merged functional delta
Only the Accounting Authorization Hardening delta was ported into the production v108 baseline:
- GovernanceDao / generated GovernanceDao implementation
- AccountingService
- FixedAssetService
- PermissionCatalog
- SecurityPolicy permission constants/session-compatible policy classes
- SecurityService one-time compatibility authorization upgrade
- AccountingScreens UI permission gates

## New specialized permissions
- CASH_COUNT_POST
- BANK_RECONCILIATION_POST
- FIXED_ASSET_POST
- FX_REVALUATION_POST
- ACCOUNTING_PERIOD_MANAGE
- ACCOUNTING_YEAR_CLOSE

## Preserved production behavior
- Portable Backup v3 and legacy key alias fush_backup_master_v2
- Customer statement/report integration
- FUSH launcher identity/icon
- No active MFA runtime flow
- Room 38 and existing user data

## Validation
- Source compiles: PASS
- Targeted PermissionCatalogTest: PASS
- Targeted SecurityPolicyTest: PASS
- Targeted BackupArchiveCodecTest: PASS
- assembleDebug: PASS
- Full source suite has one pre-existing unrelated TreasuryVoucherDoubleSubmitContractTest failure from the historical source tree; this release avoids importing that module by using the v108 production APK as baseline.
- Production authorization service guards found: 25
- Six specialized permission constants: PASS
- Room 38: PASS
- APK signature v2/v3: PASS
- 16KB zip alignment: PASS
- Signer certificate SHA-256: 22d5e2a8bd48ddd2339abdc4748648b509e02d046524d6e118fbe05088155586

Signing key and password are intentionally not included.
