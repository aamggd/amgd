# FUSH ERP Mobile — Part 2C Mandatory Post-Merge Central APK Gate

Status: **PREPARED / NOT EXECUTED / PART 2C CANNOT CLOSE WITHOUT THIS GATE**

This gate exists because the pre-merge compatibility build is not the product that users will install. The final audit must test the APK produced from the new integrated Central after Accounting P1 + Treasury P1 + Purchases P1 + Sales P1 have been accepted by the integration branch.

## Required identities before execution

The final integration owner must provide/pin all of the following:

1. final `fush/integration-current` commit SHA;
2. final `central_source` tree SHA;
3. final Room schema version;
4. Central build workflow run ID;
5. Central artifact name;
6. exact APK filename inside the artifact;
7. exact APK SHA-256;
8. expected signing-certificate SHA-256 if the official signed APK is available.

If any identity is missing, ambiguous, or does not match the downloaded artifact, the final gate is **BLOCKED**.

## Automated final APK infrastructure checks

The prepared workflow `.github/workflows/audit-evaluation-p2c-postmerge-central-apk.yml` must:

- verify the supplied final Central SHA is the actual current `fush/integration-current` head;
- verify the exact `central_source` tree;
- export that source and verify `Application ID = com.fush.erp.recovery`;
- verify the supplied Room schema and schema JSON;
- reject destructive migration/fallback patterns;
- compile the audit cross-module contract suite against the exact final source;
- run focused + full Unit regression on the exact final source;
- download the exact Central APK artifact from the exact build run;
- verify the APK SHA-256;
- verify package name with Android build tools;
- verify zip alignment;
- verify the APK already has a valid signature before attempting installation;
- **never create a new signing key and never sign the APK in the audit workflow**;
- install the exact APK on an Android Emulator/device;
- launch `com.fush.erp.recovery` and verify a process starts without an immediate fatal crash;
- retain logs and identity evidence.

If the Central artifact contains only an unsigned APK, installation testing remains **BLOCKED** until the integration/release owner supplies the correctly signed Central APK using the existing approved signing identity.

## Business E2E closure after APK infrastructure gate

APK install/launch PASS is necessary but not sufficient. Part 2C remains open until the business E2E scenarios in `PART2C_P1_CROSS_MODULE_TEST_MATRIX.md` are rerun against the integrated Central behavior, especially:

- credit sale → collection → reversal;
- sales return / `AE-ACC-009` retest;
- credit purchase → supplier payment → reversal;
- purchase return / `AE-ACC-010` retest;
- stable-source duplicate replay rejection;
- closed-period enforcement / `AE-ACC-011` retest;
- customer/supplier party identity isolation;
- Treasury 1300/2100/2200/2300 negative and positive cases;
- supplier profile/aging/statement reconciliation;
- posted-journal immutability and reversal;
- final Trial Balance / AR / AP / Treasury reconciliation.

## Closure states

Allowed states for Part 2C:

- `IN PROGRESS / PRE-MERGE PREPARED`
- `PRE-MERGE COMPATIBILITY TESTED / FINAL APK PENDING`
- `POST-MERGE SOURCE TESTED / APK PENDING`
- `POST-MERGE APK INFRASTRUCTURE TESTED / BUSINESS E2E PENDING`
- `COMPLETE / TESTED / READY FOR HANDOFF` — only after all final integrated source + APK + business E2E evidence passes.

No earlier state may be reported as final.
