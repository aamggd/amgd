# Central Integration — Sales / Customers P1 Customer Movement Identity
Source Handoff SHA: f7e6263f2c675704913fb6c1fb675f7ade36fc37
Handoff applicator blob: 05b0d9b438061384bab717f0b6c8e8c2242a0d55
Original validation run: 31977975716
Original validated generated patch SHA-256: 410b2e742cee956351ff9e51745353bc0f3feb98bf856636a4ef1cfeb03a921f
Handoff README/request claimed patch SHA-256: 5edb9fff2c533556c4c5520c853c2cf6dcbd023af3f3561440678cb2845ae7c7 (metadata mismatch; not the patch emitted by successful validation run)
Independent audit pre-merge validation run: 31979547378
Independently validated Central-35 candidate source tree: 30b028b75d6463c07afd0419429f53c7937fabb1
Central-35 evolved functional diff SHA-256: 44cafe442452b0cfdda85dee22b5a94ac5fc47091d4b9ebe4cd133ef90d8cb9b
Integration Commit SHA: 0b2ce59ec3349b46f68acb1edb89c4a832c62a84
Previous Central HEAD: ac1b93bbb7117ec1ec3a8aa6a35ba79b9fd6d922
Intervening Central change from ccbe1273ba5648b1e546eb5838aa4b33fcdb5d48: audit/test evidence record only; central_source unchanged
Central source tree after integration: 30b028b75d6463c07afd0419429f53c7937fabb1
Application ID: com.fush.erp.recovery
Room schema: 35
Migration: none (MIGRATION_34_35_ACCOUNTING_P1 preserved)
Targeted Sales/Customer identity tests: PASS
Accounting P1 regression: PASS
Treasury party-identity regression: PASS
Purchases/Suppliers regression: PASS
Full Unit Tests: PASS
assembleRelease: PASS
Application ID verification: PASS
Room 35 + migration chain through 34 -> 35: PASS
Data/schema preservation: PASS
Destructive migration/reset guards: PASS
zipalign: PASS
Conflict resolution: AccountingService overlap with Treasury P1 resolved by applying the exact validated Sales transformations to evolved Central; both TreasuryPartyRequirementPolicy and CustomerMovementIdentity guards preserved.
Result: SUCCESS
