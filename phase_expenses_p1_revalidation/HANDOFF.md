# FUSH ERP Mobile — Expenses P1 Wave 1 Revalidation Handoff

Status: **RUNTIME DEFECT FOUND / HANDOFF BLOCKED**

## Freeze reason

A real Android runtime crash was reported after the prior CI-only handoff: opening Expenses succeeds, but tapping **تسجيل مصروف جديد** exits/crashes the application.

The prior Unit/Release/zipalign SUCCESS is therefore not sufficient for integration readiness. Expenses P1 is frozen until runtime reproduction, adb logcat root-cause capture, minimal fix, Android runtime smoke coverage, and full revalidation are complete.

## Exact Central baseline

- Branch: `fush/integration-current`
- Central HEAD: `ddae764a236cb1916a46e194ccb2d3c24ca6b919`
- Central Source Tree: `30b028b75d6463c07afd0419429f53c7937fabb1`
- Application ID: `com.fush.erp.recovery`
- Central Room schema: `35`
- Expenses branch-only provisional schema after applying P1: `36`
- Provisional migration: `MIGRATION_35_36_EXPENSE_WORKFLOW_PROVISIONAL`

## Previously validated patch identity — now frozen pending runtime repair

- Exact Expenses P1 Patch SHA-256: `b74c2d5766381a4c2d14ecb664b72baed6260bc4ba6863614530ee2197972a54`
- Previous CI run: `31981705972` — SUCCESS
- Previous branch HEAD: `abcce572053f17a0c922558ab36de850677d69a2`

## Required runtime closure gates

Before READY can be restored, the branch must provide all of the following:

- Reproduction on Exact Central APK and Exact Expenses P1 APK.
- Full `adb logcat` FATAL EXCEPTION stacktrace and first frame inside `com.fush.erp`.
- Root cause identified before code modification.
- Minimal P1-scoped fix only.
- Regression test for the root cause.
- Android UI/runtime smoke gate that opens the actual Add Expense dialog without crashing.
- Runtime verification of account, treasury, currency, and cost-center selectors; cancel/reopen; valid test expense posting on Test DB; journal and treasury verification.
- Targeted tests, Full Unit, Release, Room/migration preservation, Application ID, no destructive migration, and zipalign all PASS.
- Final repaired patch SHA and APK SHA.

## Restrictions

- P2 must not start.
- Do not merge to `fush/integration-current` or `fush/main`.
- Room `36` remains **PROVISIONAL / BRANCH ONLY**; do not renumber it in this repair.
- Do not use destructive migration or database reset as a repair.

This handoff is intentionally blocked until the runtime defect is closed with Android evidence.
