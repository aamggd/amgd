# Employees / HR — P1 Entitlement Types

Status: **INITIAL IMPLEMENTATION / NOT FINAL HANDOFF**

Initial Central baseline: `fush/integration-current@2cb8da801fc54aec8c1f0d6a83588f097ca85117`

Central source tree at initial baseline: `7733c6570357eb813f7e05e5093752ea26788749`.

## Scope

P1 defines the canonical employee entitlement type contract only:

- `PRODUCTION` — إنتاج
- `SALARY` — راتب
- `COMMISSION` — عمولة
- `ALLOWANCE` — بدل
- `ADVANCE` — سلفة
- `DEDUCTION` — خصم

Production and commission are explicitly marked as requiring an operational source so later phases can preserve traceability to the originating transaction. Advance and deduction reduce employee payable; production, salary, commission and allowance increase payable.

P1 does **not** implement lifecycle states, approvals, payments, reversals, double-payment controls, or employee statement reconciliation; those belong to later official phases.

## Exact application patch

`employees-hr-p1.patch`

The patch adds exactly two application-source files when applied to the Central source:

- `app/src/main/java/com/fush/erp/domain/EmployeeEntitlementType.kt`
- `app/src/test/java/com/fush/erp/domain/EmployeeEntitlementTypeTest.kt`

## Data / accounting impact

- Room schema: unchanged at `34`.
- Migration: none.
- Accounting posting logic: unchanged.
- Inventory: no impact.
- Production: classification contract only; existing production posting logic is unchanged.
- Application ID must remain `com.fush.erp.recovery`.
- Signing: no keystore or signing secret is introduced.

## Final validation gate

The initial validation is intentionally not the final handoff. After the current Central Wave (Accounting P1 + Treasury P1 + Purchases P1) finishes, this exact patch must be applied again to the newest Central HEAD and rerun through:

1. P1 domain tests.
2. Full unit tests.
3. Release build.
4. Room/schema and migration checks.
5. No-destructive-migration checks.

Only after that gate passes may P1 be declared `COMPLETE / TESTED / READY FOR HANDOFF`.
