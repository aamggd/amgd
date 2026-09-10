# P4 Physical Android Device Upgrade Test

Purpose: satisfy the branch-plan acceptance requirement for a **real-device plaintext -> SQLCipher upgrade** without using production data.

Use only a spare/test Android device. Do not run this procedure against the accountant's production installation or irreplaceable data.

## Files

Obtain both APKs from validation artifact ID `9255807712` / workflow run `31918572570`:

1. `FushERP-14.5.54-Plaintext-Baseline-debug.apk`
   - SHA-256 `9c53618518f927ce38a6980c1c2eac7c5ca385a88f8d0116b73716c0de3f79f8`
2. `FushERP-P4-SQLCipher-Central54-debug.apk`
   - SHA-256 `006d4cf7df7bd388761a521d6b65d514213e959461c7fb0d96b8c8561515f1f1`

Both are debug builds produced by the same CI runner configuration so the second APK can upgrade the first during this controlled test.

## Test procedure

1. Remove any old **debug/test** installation of FUSH from the spare device.
2. Install the plaintext baseline debug APK.
3. Launch FUSH and complete the normal first-run setup if requested.
4. Create unmistakable test records in more than one area, for example:
   - one test user/role entry;
   - one master-data record;
   - one harmless test accounting/master record if available without posting production transactions.
5. Close the app normally and reopen it once. Confirm the records are still present.
6. Record screenshots or values of the test records.
7. Install `FushERP-P4-SQLCipher-Central54-debug.apk` **as an update without uninstalling or clearing app data**.
8. Launch the updated app.
9. Acceptance checks:
   - app starts without crash;
   - existing login/user data remains available;
   - all test records from step 4 remain unchanged;
   - normal read/write operations work after upgrade;
   - close/reopen the app and verify the same data again;
   - create one additional test record after encryption and verify persistence after another restart.
10. Exercise FUSH backup creation once after the upgrade and confirm that it completes successfully.
11. If practical, restore that backup only on a disposable/test installation and confirm it opens normally.

## Optional ADB evidence

With USB debugging enabled:

```bash
adb install -r FushERP-14.5.54-Plaintext-Baseline-debug.apk
# create the test data in the app
adb install -r FushERP-P4-SQLCipher-Central54-debug.apk
adb shell am force-stop com.fush.erp.recovery
adb shell monkey -p com.fush.erp.recovery 1
```

Do **not** use `pm clear`, uninstall, or `adb install -r -d` between the baseline and P4 APK; the test is specifically proving in-place preservation of application data.

## Pass criteria

P4 physical-device gate is PASS only when:

- installation is an in-place upgrade;
- no existing user/password/application data is lost;
- app opens after migration and after a subsequent restart;
- read/write functionality remains operational;
- backup creation works after encryption;
- no destructive reset, database recreation, or manual data clear was used.

Record device model, Android version, test date, result, and any logs/screenshots in a short test record. Only after this PASS should the integration registry status be changed from `PHYSICAL DEVICE PENDING` to `READY / VALIDATED HANDOFF`.
