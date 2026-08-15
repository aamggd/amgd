# Fush ERP Phase 14.5.58 — Non-Blocking Export & Print UX

Base: verified Phase 14.5.57 Management / Planning Print Coverage artifact.
Branch: `fush/reports-printing`.

Professional stability fix:
- PDF and XLSX rendering/saving moves off the main UI thread.
- Share-file preparation moves off the main UI thread; chooser launch remains on main.
- Print PDF rendering moves off the main UI thread; Android print dialog launch remains on main.
- Export controls are temporarily disabled while one export is being prepared to prevent duplicate taps/files.
- A progress indicator and explicit Arabic preparation state are shown.
- Coroutine cancellation is propagated correctly when the screen leaves composition, avoiding stale error toasts.
- Existing PDF/XLSX formatting, current filters, RTL, and accounting data are unchanged.

Regression coverage:
- Existing full unit/Room/Compose suite.
- XLSX action-preparation path is used by the structural XML validity regression test.

Safety:
- Application ID unchanged: `com.fush.erp.recovery`.
- Room schema remains 27.
- No migration.
- No accounting posting changes.

Version:
- versionCode 97
- versionName `0.15.4.58-nonblocking-export`
