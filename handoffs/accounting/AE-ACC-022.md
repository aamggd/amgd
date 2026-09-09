# Accounting Handoff — AE-ACC-022

- Defect ID: AE-ACC-022
- Severity: HIGH
- Development Baseline: `00c24f858870419392668e70e90eb76b239ae2aa` (`ACCOUNTING WAVE B DEVELOPMENT BASELINE — NOT CENTRAL`).
- Root Cause: Manual journals were constructed with `status=POSTED` and the accounting engine did not bind the existing generic approval framework to the journal lifecycle. Maker/approver separation and an approval-time period recheck were therefore not mandatory.
- Fix: Manual `JournalEntryEntity` convenience construction now starts at `DRAFT`; the database guard creates an `ACCOUNTING_JOURNAL` approval request and advances to `SUBMITTED`. Approval requires `decisionBy != requestedBy`, a still-open accounting period, and valid submitted-journal evidence. An approved request drives `SUBMITTED -> APPROVED -> POSTED`; rejected requests drive `SUBMITTED -> REJECTED`. Direct raw `MANUAL/POSTED` insert and lifecycle skips are rejected.
- Existing Framework Reused: `approval_requests`, `audit_events`, `SecurityPermissions.APPROVAL_DECIDE` and the governance approval UI/service permission check. No second approval subsystem was introduced.
- Changed Files:
  - `app/src/main/java/com/fush/erp/data/entity/Entities.kt`
  - `app/src/main/java/com/fush/erp/data/AccountingJournalApprovalDatabaseGuard.kt`
  - `app/src/main/java/com/fush/erp/data/AccountingDatabaseGuardInitializer.kt`
  - `app/src/androidTest/java/com/fush/erp/data/AccountingJournalApprovalLifecycleTest.kt`
- Database/Room Impact: No schema change. Room remains 36. Triggers are installed from the existing cold-open accounting guard initializer. No destructive migration and no data deletion.
- Tests: DRAFT default for MANUAL; automatic SUBMITTED + pending approval; maker self-approval rejection; different approver posts; direct MANUAL/POSTED raw insert rejection; status-skip rejection; approval rechecks Open Period; audit evidence after approve/post.
- Cross-module Impact: Operational journals remain POSTED by default and are not governed by this manual-journal trigger family. Governance approval processing is reused; QA should verify the Accounting screen message/UI reflects that a manual journal is submitted for approval rather than immediately posted.
- Status: FIXED / READY FOR PRE-INTEGRATION QA (Android instrumentation execution required on QA SDK/device).
