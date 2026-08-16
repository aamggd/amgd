# FUSH ERP Mobile — Audit Controls P0 Mandatory Event Catalog

Branch: `fush/audit-controls`  
Phase: `P0 — Mandatory event inventory`  
Baseline: Central Phase 14.5.54 Printing Integrated record `5095ba46a676fd6a8e048f2325c433a1f336d05d`  
Central source tree: `1b6af7bcaa86138ae75ea3d905db4bdba0fe04ff`

## P0 boundary

This phase defines the mandatory audit-event contract only. It does **not** add actor/session/device/source capture (P1), tamper protection (P2), persistence links for reversals/approvals (P3), monitoring reports (P4), or retention/export behavior (P5).

No accounting, inventory, production, posting, costing, approval, or Room behavior is changed by P0.

## Mandatory sections and event families

| Section | Mandatory event families |
|---|---|
| Security | successful/failed login, logout/session termination, MFA failure, permission denial, permission/role/security-policy changes |
| Governance & approvals | submit, approve, reject, revoke, change-request creation/decision |
| Master data | create/update/deactivate/reactivate; price, credit-limit and unit-factor changes |
| Sales / AR | document create/post/cancel/reverse, sales return, customer receipt/reversal, price/discount overrides |
| Purchases / AP | document create/post/cancel/reverse, purchase return, supplier payment/reversal, price override |
| Accounting / treasury | manual journal post/reverse/correct, backdated posting, period/year close/reopen, vouchers, cash count/resolution, bank import/match/unmatch/reconcile, FX revaluation/reversal |
| Expenses | create/update/approve/post/reverse and dimension changes |
| Inventory | adjustments, counts, warehouse transfers, lot reclassification and cost override |
| Production | order create/release/cancel/delete, material issue/return, output receipt and issue/receipt corrections |
| HR | employee create/update/status, compensation change, payroll post/reversal |
| Fixed assets | create/update/capitalize, depreciation post/reversal and disposal |
| Planning | create/update/approve/reopen |
| Risk / controls | risk create/review, control create/test, exception create/close |
| Backup / restore | backup create/export and restore start/success/failure |

The executable catalog is `AuditEventCatalog.kt`; it carries metadata indicating whether a future implementation must capture a reason, before/after values, and/or a link to the originating event/document.

## P0 invariants

1. Every listed application section has at least one mandatory event.
2. Event codes are unique and definitions are non-empty.
3. Reversal/cancellation/correction-style events are explicitly marked for reason and original-link requirements where applicable.
4. Sensitive configuration changes are marked for before/after capture.
5. The catalog never defines password/secret/token payload fields.
6. Audit is a trace/control layer only; it is never a replacement for accounting ledger or stock ledger records.

## Baseline observations

The central baseline already contains an `audit_events` Room entity and DAO insert/read paths, plus audit calls in several accounting, inventory, production, governance, security, risk-control and backup flows. P0 does not claim coverage is complete. P1 will map the mandatory catalog against actual call sites and add the required actor/session/device/time/entity/action/source/reason envelope without changing accounting or stock ledgers.

## Room / migration

- Room schema changed: **No**.
- Migration added: **None**.
- Schema remains baseline `34` in P0.
- `fallbackToDestructiveMigration`: prohibited and must remain absent.

## Integration note

This catalog is intentionally re-applicable over a newer central baseline. If the main integration conversation publishes a newer baseline before P1, P1 must be rebased/re-established on that newer baseline rather than replacing newer source files.
