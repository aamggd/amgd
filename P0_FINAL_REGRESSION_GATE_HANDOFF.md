# FUSH ERP Mobile v104 — P0 Final Regression Gate

Status: READY FOR EXECUTION

This checkpoint contains cumulative P0 fixes:
- P0-1 Accounting source/idempotency classification
- P0-2 Voucher operation identity / double-submit safety
- P0-3 Accounting period fail-closed enforcement
- P0-4 Accounting contract consistency / legacy P1 snapshot separation

No new functional fix is introduced by this checkpoint.
It exists to prove the cumulative P0 baseline before P1 begins.

Required gate:
1. Targeted P0 accounting tests
2. Full unit regression
3. assembleDebug
4. AppID = com.fush.erp.recovery
5. versionCode = 104
6. Room schema = 38
7. no fallbackToDestructiveMigration
8. debug APK produced

Run on the configured Windows development machine:
    powershell -ExecutionPolicy Bypass -File .\RUN_P0_FINAL_GATE.ps1

P1 must not start if this gate reports FAIL.
