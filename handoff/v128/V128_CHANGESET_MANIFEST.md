# FUSH ERP Mobile v128 — Changeset Manifest

Authoritative local source artifact:
`FushERP-Mobile-v128-ReleaseHardening-FINAL-Source.zip`
SHA-256: `0d567621b83a55890c2088a8b66f1bfec5d62aa67976897efaf758080ca73e33`

Baseline artifact:
`FushERP-Mobile-v127-SearchableDropdownKeyboardFix-FINAL-Source.zip`

Exact v127→v128 patch generated from clean archives:
`FushERP-Mobile-v127-to-v128-ReleaseHardening-FINAL.patch`
SHA-256: `0e62d961e6f986a828f1e035f9bfdc7a1b235ec69ff3451dfaa2944e81c58d75`

## Files changed from v127 to v128
- `BUILD_STATUS.md`
- `RELEASE_HANDOFF.md`
- `V128_RELEASE_HARDENING.md`
- `app/build.gradle.kts`
- `app/src/androidTest/java/com/fush/erp/attachments/AttachmentStorageAndroidTest.kt`
- `app/src/androidTest/java/com/fush/erp/ui/export/ReportExportSupportAndroidTest.kt`
- `app/src/main/java/com/fush/erp/FushErpApplication.kt`
- `app/src/main/java/com/fush/erp/attachments/AttachmentBackupMigrator.kt`
- `app/src/main/java/com/fush/erp/attachments/AttachmentStorage.kt`
- `app/src/main/java/com/fush/erp/backup/BackupArchiveCodec.kt`
- `app/src/main/java/com/fush/erp/backup/BackupRestoreManager.kt`
- `app/src/main/java/com/fush/erp/data/dao/ExpenseDao.kt`
- `app/src/main/java/com/fush/erp/data/dao/PartyDao.kt`
- `app/src/main/java/com/fush/erp/data/dao/ReportDao.kt`
- `app/src/main/java/com/fush/erp/data/dao/SalesDaos.kt`
- `app/src/main/java/com/fush/erp/data/dao/SecurityDao.kt`
- `app/src/main/java/com/fush/erp/data/entity/ReportEntities.kt`
- `app/src/main/java/com/fush/erp/domain/BusinessDatePolicy.kt`
- `app/src/main/java/com/fush/erp/domain/BusinessTimeZone.kt`
- `app/src/main/java/com/fush/erp/domain/ExpenseDatePolicy.kt`
- `app/src/main/java/com/fush/erp/domain/InventoryReportMath.kt`
- `app/src/main/java/com/fush/erp/domain/MasterDataService.kt`
- `app/src/main/java/com/fush/erp/domain/NearExpiryPolicy.kt`
- `app/src/main/java/com/fush/erp/domain/PermissionCatalog.kt`
- `app/src/main/java/com/fush/erp/domain/PlanningService.kt`
- `app/src/main/java/com/fush/erp/domain/ReportMath.kt`
- `app/src/main/java/com/fush/erp/domain/SecurityService.kt`
- `app/src/main/java/com/fush/erp/domain/WarehouseTransferMath.kt`
- `app/src/main/java/com/fush/erp/ui/export/ReportExportSupport.kt`
- `app/src/main/java/com/fush/erp/ui/screens/ExpenseScreens.kt`
- `app/src/main/java/com/fush/erp/ui/screens/HomeShell.kt`
- `app/src/main/java/com/fush/erp/ui/screens/PartyScreens.kt`
- `app/src/main/java/com/fush/erp/ui/screens/ReportsScreen.kt`
- `app/src/main/res/xml/file_paths.xml`
- `app/src/test/java/com/fush/erp/backup/BackupArchiveCodecTest.kt`
- `app/src/test/java/com/fush/erp/domain/BusinessTimeZoneTest.kt`
- `app/src/test/java/com/fush/erp/domain/InventoryReportMathTest.kt`
- `app/src/test/java/com/fush/erp/domain/NearExpiryPolicyTest.kt`
- `app/src/test/java/com/fush/erp/domain/PeriodComparisonMathTest.kt`
- `app/src/test/java/com/fush/erp/domain/PermissionCatalogTest.kt`
- `app/src/test/java/com/fush/erp/domain/ReleaseHardeningV128ContractTest.kt`
- `app/src/test/java/com/fush/erp/domain/ReportMathTest.kt`
- `app/src/test/java/com/fush/erp/domain/TransactionChronologyTest.kt`

## Diff summary
43 files changed, 876 insertions, 176 deletions.

This branch is a handoff/snapshot branch only. It is not approval to merge into `master` or any Central/integration branch.
