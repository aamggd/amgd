# v137 — Safe Support Repair Hardening

Baseline: v136 SupportMaintenanceMode FINAL only.

## High-severity repair hardening

`rebuildAccountingEntry()` and `repostTransaction()` no longer treat a balanced STAGING journal as sufficient evidence of correctness.

Before any `STAGING -> POSTED` transition, `JournalSemanticExpectation` reconstructs the journal expected from the original typed source and compares it to the current STAGING journal by:

- source document identity;
- entry number;
- business date;
- currency;
- exchange rate;
- exact journal-line multiset: account ID + debit + credit;
- expected total debit/credit and actual total debit/credit.

Supported typed sources remain intentionally narrow:

- SALE
- PURCHASE
- CUSTOMER_RECEIPT
- SUPPLIER_PAYMENT
- SALES_RETURN
- PURCHASE_RETURN

If source reconstruction cannot be proven, repair fails closed. It never invents a balancing account and never repairs a manual journal by this command.

### Source-derived accounting

The expected journal is rebuilt from source documents and their allocations/cost allocations, including where applicable:

- customer / supplier or treasury leg;
- sales / purchase inventory leg;
- COGS and inventory cost;
- collection settlement discount;
- realized FX gain/loss on receipts/payments;
- sales/purchase return settlement;
- free-quantity cost through the original sales cost allocations.

The comparator does not trust the existing STAGING journal to choose its accounts or amounts.

## Treasury provenance and historical fail-closed behavior

Room 41 -> 42 non-destructively adds nullable `treasuryAccountId` provenance to:

- sales_invoices
- customer_receipts
- sales_returns
- purchase_invoices
- purchase_returns

New cash documents persist the exact selected treasury source. Historical rows remain NULL. A historical cash document without provable treasury provenance is not guessed from the journal: automated Safe Repair is rejected and requires a reviewed typed command.

Supplier payments already carried treasury provenance and did not need a new column.

## Durable failed-repair evidence

The before snapshot is persisted before the business mutation transaction.

If semantic preflight fails, or if the later business mutation / post-validation fails:

- business mutation is rolled back;
- a FAIL validation result is written outside the rolled-back business transaction;
- an immutable `${command}_FAILED` Support Audit row is retained;
- the original before snapshot remains retained.

Thus failed Support attempts remain auditable even though business data is not changed.

## Session activation hardening

Opening a Support Session now requires recent reauthentication inside `SupportService.activateSession()` itself using:

`requireRecentReauthentication(actorUserId, "SUPPORT_SESSION_ACTIVATE")`

The UI prompts for reauthentication when needed, but backend enforcement is authoritative.

Only bounded lifetimes are accepted:

- 1 hour
- 6 hours
- 24 hours

The previous indefinite / until-manual-cancel option is removed. Legacy sessions with `Long.MAX_VALUE` or a lifetime beyond 24 hours are treated as inactive by the v137 Support policy.

## FUSH_SUPPORT local-role hardening

Company-local user administration can no longer:

- create a user with role FUSH_SUPPORT;
- assign FUSH_SUPPORT to a normal user;
- assign another role to a FUSH_SUPPORT identity;
- reset the password of a FUSH_SUPPORT identity;
- enable/disable that identity through normal company user management;
- edit FUSH_SUPPORT permissions.

The normal Security UI also hides FUSH_SUPPORT from company user/role-management lists.

This prevents a company ADMIN from manufacturing a new local Support identity. Existing Support identities are preserved through upgrade.

**Important limitation:** v137 does not add cryptographic vendor provisioning or remote support. A future FUSH Support Backend / device-pairing architecture is still required to cryptographically verify vendor identity and to enable remote support from another device.

## Scope isolation note

The current Android ERP database is effectively local-company scoped. Support Ticket/Session carries companyId/branchId and backend commands validate that Ticket/Session scope matches. Full row-level multi-company/multi-branch isolation is not claimed for future shared-database deployments; that requires tenant/branch keys on business tables and scoped DAO queries.

## Identity

- applicationId: com.fush.erp.recovery
- versionCode: 137
- versionName: 0.15.4.88-safe-support-repair1
- Room: 42
- Migration: 41 -> 42 additive/non-destructive
