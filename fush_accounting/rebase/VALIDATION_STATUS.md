# Accounting Rebase Validation Status

Status: **VALIDATED / READY FOR CENTRAL INTEGRATION REVIEW**

## Baseline
- Official Phase 14.5.38 Professional UI GitHub Actions artifact.
- Application ID: `com.fush.erp.recovery`.
- Baseline versionCode retained for branch validation: `77`.
- Baseline versionName retained: `0.15.4.38-ui-inventory-master-data`.
- Baseline Room Schema: `27`.

## Validated rebase
- Branch: `fush/accounting`.
- Validation workflow: `.github/workflows/build-accounting-rebase-14.5.38.yml`.
- Successful run ID: `31863175468`.
- Validated head SHA: `d15ddd966599aa80ef954f0e091d64e94c48b92c`.
- Artifact ID: `9241279022`.
- Artifact name: `FushERP-Accounting-Rebase-14.5.38-Validated-Build`.
- GitHub artifact digest: `sha256:aa4417376762702d271a2d58bc36e3f6e10f0feeae3482ebbc348c93441d25a0`.

## Validation results
- Reassembled patch chunk size checks: PASS.
- SHA-256 checks for all patch chunks: PASS.
- GZIP integrity test: PASS.
- Clean patch SHA-256 verification: PASS.
- `git apply --check` against official 14.5.38 baseline: PASS.
- Application ID check: PASS.
- Baseline version identity check: PASS.
- No destructive migration token found: PASS.
- Unit Tests: PASS.
- Release Build: PASS.
- Room Schema 31 generation for standalone accounting branch: PASS.
- Zipalign: PASS.
- Artifact upload: PASS.

## Validated output hashes
- Clean integration patch: `3c1984f84f0f48c90f1adf5b1683185e62520f90512689befe0bed4ce1060dd6`.
- Full source ZIP: `1e4c3f376b8a9fcd091c3ce6b6aa895042cc5bf3c7acb94460063df61a7f086b`.
- Aligned unsigned APK: `6b20df19874c2b5932a0b4fa06890083653b49ee1aa54e1b8b3bd7a999a6e6d1`.

## Migration warning for central integration
Standalone accounting rebase currently reaches Schema 31 using provisional sequence:
`27 -> 28 -> 29 -> 30 -> 31`.

These numbers are **not final integrated project schema numbers**. If Users/Permissions occupies `28 -> 29`, central integration must renumber accounting migrations after 28 while preserving migration SQL and data. Expected integrated ordering may become:
- `27 -> 28`: accounting periods/reconciliation.
- `28 -> 29`: users/security.
- `29 -> 30`: operational reversals.
- `30 -> 31`: fiscal-year closing.
- `31 -> 32`: treasury/bank reconciliation.

## Legacy patch status
Historical files under `fush_accounting/patches/` are **SUPERSEDED / DO NOT DIRECTLY INTEGRATE**. The clean validated rebase artifact/patch above is the only accounting integration source of truth.

## Signing
No permanent signing key or password is stored in GitHub or this repository. CI intentionally produces an unsigned aligned APK only. Final signing and v2/v3/certificate verification remain part of the central release gate.
