# QA P1 Wave-1 — Final Central APK Gate

Status: **MANDATORY FINAL GATE — NOT EXECUTED DURING PREPARATION**

This gate is intentionally deferred until the integration conversation produces one Central candidate containing the intended Accounting P1 + Treasury P1 + Purchases P1 + Sales P1 set.

## Evidence identity

Record together before testing:

- Central branch and exact source commit SHA.
- Central source tree SHA.
- Room schema and migration chain.
- Exact APK file SHA-256.
- Build/workflow run and artifact ID if produced by CI.
- Application ID must be `com.fush.erp.recovery`.
- If officially signed, certificate SHA-256 must be `22:D5:E2:A8:BD:48:DD:D2:33:9A:BD:C4:74:86:48:B5:09:E0:2D:04:65:24:D6:E1:18:FB:E0:50:88:15:55:86`.
- If the official signing key is unavailable, the APK is test-only and cannot close the official signing gate.

## Required final test order

1. Confirm all four P1 contracts are present together by running `validate_wave1_contracts.py --require-all` against the exact Central source used to build the APK.
2. Run full Unit tests and `assembleRelease` against that exact source SHA.
3. Run the QA `MigrationTestHelper` suite on emulator/device and verify the complete migration path to the final Central Room schema.
4. Install the previous accepted Central APK and create/retain representative accounting, customer, supplier, sales, purchase and treasury data.
5. Update to the new Central APK using the normal Android update path **without uninstalling**. Do not clear app data.
6. Launch and verify all retained records are present and the database opens without reset.
7. Execute the Wave-1 matrix scenarios: sale, collection, receipt reversal, sales return, purchase, supplier payment, purchase return, employee-payable treasury voucher, sales-rep payable voucher, and blocked generic postings to trade control accounts.
8. Verify each operational event creates at most one intended POSTED stable-source journal and that POSTED journals cannot be edited/deleted directly.
9. Verify customer/supplier statements, aging/outstanding, treasury balances and inventory effects remain reconciled after returns/reversals.
10. Force-stop/restart after representative transactions and verify persisted state remains correct.

## Release-blocking conditions

Any of the following keeps QA P1 open:

- APK digest cannot be tied to the tested Central source SHA.
- Update requires uninstall/data wipe.
- Room migration fails or loses historical data.
- Duplicate stable-source posting succeeds.
- POSTED journal mutation succeeds.
- Cross-customer receipt allocation or cross-supplier payment allocation is accepted.
- Generic treasury voucher bypasses the dedicated customer/supplier control-account workflows.
- Customer/supplier statements or treasury/accounting balances diverge after the tested scenarios.
- Application ID differs from `com.fush.erp.recovery`.
- Destructive migration/reset is introduced.

Only after this exact-APK gate passes can this QA phase be reported **COMPLETE / TESTED / READY FOR HANDOFF**.
