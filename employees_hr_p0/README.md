# Employees HR - P0

Baseline: Phase 14.5.54 Printing Integrated (validated central candidate).

Scope: stable employee identity for employee-linked production compensation. The patch exposes `employeeId` in the compensation projection and blocks moving a production order to another employee after a posted `PRODUCTION_LABOR` accrual exists. Display names remain presentation data, not identity keys.

Room schema: unchanged (34). No migration. Application ID remains `com.fush.erp.recovery`. No signing secret is used by branch CI.
