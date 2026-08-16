# FUSH Reports/Printing — Current Central Room-34 Handoff

Status: IN VALIDATION

Exact base:
- Branch: `fush/integration-current`
- HEAD: `2cb8da801fc54aec8c1f0d6a83588f097ca85117`
- Room schema: `34`
- Application ID: `com.fush.erp.recovery`

This handoff does **not** merge the historical Phase 14.5.58 branch/source. Historical successful artifacts are used only as functional references to generate narrow deltas. The validation workflow applies only selected Reports/Printing changes onto the exact current Central source, preserves current Central screen changes by 3-way patching, runs full unit tests, release build, Room/application safety checks, and Android Print Framework emulator smoke testing.

Selected functional scope:
1. PDF long-text/hard-wrap/dynamic-row fixes and professional layout/XLSX hardening.
2. Direct print/export coverage for the previously completed operational/management sections, transplanted as narrow hunks rather than whole-file replacement.
3. Non-blocking PDF/XLSX/share/print preparation with busy-state protection; chooser/PrintManager launch remains on main thread.
4. Printing-specific regression tests plus an Android Print Framework emulator smoke test.

Explicit exclusions:
- no Room entity/schema change
- no migration change
- no accounting posting change
- no inventory transaction change
- no production transaction change
- no versionCode/versionName ownership change
- no signing key/configuration change
- no merge to `fush/main`

The workflow produces a new exact patch against the current Central source. That generated patch, not the historical branch history, is the handoff artifact for review.
