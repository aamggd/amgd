# v187 — Cloud Journal STAGING Hydration Hotfix

- Baseline: v186.
- versionCode: 187.
- versionName: 0.15.4.138-cloud-journal-staging-hydration.
- Room schema remains 49; no destructive migration.
- Fixes accountant-device cloud hydration failure `OPERATIONAL_JOURNAL_MUST_START_STAGING`.
- Cloud journal hydration now inserts/updates a STAGING header, writes the complete scaled debit/credit line batch, then transitions to POSTED through the existing database balance guard.
- Existing POSTED journals remain immutable; differing cloud truth must use explicit conflict/reversal handling rather than in-place mutation.
