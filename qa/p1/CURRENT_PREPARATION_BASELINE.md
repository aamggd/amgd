# QA P1 Wave-1 — Current Preparation Baseline

Status: **PREPARATION / NOT FINAL**

Branch: `fush/testing-qa`

Re-established from current Central:

- Central branch: `fush/integration-current`
- Central HEAD: `bd39c9fdce444da865460539ea80058f02770d4a`
- Central source tree: `2c8f39d515e627d9d7d6ba1eac3e065a1d17f245`
- Application ID: `com.fush.erp.recovery`
- Room schema: `35`
- Accounting P1: integrated and Central-validated
- Treasury/Banking P1: integrated and Central-validated
- Purchases/Suppliers P1: Central integration job running at the time of this checkpoint
- Sales/Customers P1: not yet present in this Central checkpoint

The QA workflow always re-reads the live `fush/integration-current` ref at run start. This checkpoint therefore records ancestry, while each evidence bundle records the exact Central SHA it actually tested.

No QA phase is final at this checkpoint. Final acceptance remains blocked until Accounting P1 + Treasury P1 + Purchases P1 + Sales P1 coexist in one merged Central source and the exact resulting Central APK is retested according to `FINAL_CENTRAL_APK_GATE.md`.
