# FUSH ERP Mobile — Central UI Integration Anchor

Status: **APPROVED UI INTEGRATION ANCHOR**

- Integration branch: `fush/integration-ui-14.5.44`
- Validated commit: `7e8dcb4a3e83e8869907370624a4877c38a9cfed`
- Successful GitHub Actions run: `31863406088`
- Artifact: `FushERP-Phase14.5.44-Central-Integrated-UI-Build`
- Artifact ID: `9241326285`
- Artifact digest: `sha256:41addb7e99884d876a37bed45b7d19cf51a3fb32812c9efc04132f17a58dab37`

## Integrated source identity

- Application ID: `com.fush.erp.recovery`
- UI integration versionCode: `83`
- UI integration versionName: `0.15.4.44-ui-final-consistency`
- Room schema: `27` (unchanged)
- Source ZIP SHA-256: `c10c7ddf79c082a58213ec23202372bcea329017af312bbb6907d227d8f16ef1`
- Aligned unsigned APK SHA-256: `80c48bcf630a870c4875b6265523503c6953bfa04d0a0ee93193488a2638f918`

## Validation

- Phase 14.5.43 verified source restored successfully.
- Phase 14.5.44 final handoff applied in the documented 10-patch order.
- One `HomeShell.kt` context mismatch in patch 07 was resolved centrally by preserving the newer verified 14.5.43 alert-list structure and porting only the intended UI-state substitutions through `resolve_home_shell_07.py`.
- UI handoff scope guard: PASS.
- No destructive Room migration fallback: PASS.
- Unit tests: PASS.
- Release build: PASS.
- Application identity and Room schema safety checks: PASS.
- Zipalign: PASS.

## Central integration rule

This anchor is the UI source baseline for subsequent accounting, users/permissions, and bug-fix integration. On conflicts, preserve the professional UI from this anchor while incorporating newer business/security/data logic from the specialized branch. Final integrated versionCode/versionName and any Room migration renumbering remain owned by central integration.
