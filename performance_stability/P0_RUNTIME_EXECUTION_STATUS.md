# FUSH ERP Mobile — Performance/Stability P0 Runtime Execution Status

Branch: `fush/performance-stability`

Status: **P0 RUNTIME EXECUTION BLOCKED — NOT COMPLETE**

This document records only P0 measurement execution. No optimization and no P1 work is included.

## Central baseline refresh

Before continuing runtime work, the performance branch was refreshed from the current Central integration branch through PR #3.

- Central branch: `fush/integration-current`
- Central commit merged into this branch: `5bb7f56a55089235168469201dfd81c15e56e489`
- Performance refresh merge commit: `b2111733292187a5634a29e5928b02ff4a3e8b24`
- No merge from this branch to `fush/main` was performed.

## Android runtime environment actually started

A real Android Emulator was created and booted successfully from the P0 runtime kit:

- Android API: 30 / Android 11
- ABI: x86_64
- AVD: `fush_p0`
- Memory: 2048 MB
- vCPU setting: 2 cores
- Graphics: SwiftShader indirect
- KVM: unavailable in the execution container
- Emulator acceleration mode: `-accel off` (TCG software emulation)
- `sys.boot_completed`: `1`
- ADB serial: `emulator-5554`

The environment is therefore capable of running Android and collecting `am start -W`, `dumpsys meminfo`, `dumpsys gfxinfo`, logcat/ANR evidence, and workload wall-clock measurements once a valid installable APK for the current baseline is available.

## Current Central APK preparation evidence

The P0 runtime-kit workflow previously produced an aligned/release unsigned APK from a then-current Central source and validated:

- package: `com.fush.erp.recovery`
- Room schema: 34
- no `fallbackToDestructiveMigration`
- Unit tests: PASS
- Release build: PASS

That runtime-kit APK is intentionally unsigned. It was not modified or test-signed.

## Actual install result on Android Emulator

Installing the unsigned release APK on the booted emulator was attempted and Android rejected it with:

`INSTALL_PARSE_FAILED_NO_CERTIFICATES`

This is expected Android package-manager behavior for an unsigned APK. The branch rules explicitly forbid creating an alternative keystore/certificate, and the P0 execution did not create one.

The official FUSH signing certificate is required to have SHA-256:

`22:D5:E2:A8:BD:48:DD:D2:33:9A:BD:C4:74:86:48:B5:09:E0:2D:04:65:24:D6:E1:18:FB:E0:50:88:15:55:86`

The current execution container does not contain the permanent private signing key, and it is not stored in GitHub. Therefore the current Central APK cannot be legally signed in this execution environment without the separately supplied permanent key.

## Harness verification with existing signed historical APK

An already supplied Phase 14.5.33 APK was checked locally with `apksigner`. It verifies with v2 and v3 and its signer certificate SHA-256 matches the required permanent FUSH certificate exactly. It was installable on the emulator, confirming that the emulator/package-manager path itself works with a valid FUSH-signed APK.

Its runtime numbers are **not** accepted as P0 baseline results because it is an older source stage. No old-stage performance result will be substituted for the latest Central baseline.

## Runtime Measurement Matrix status

| Workload | Required | Current status |
|---|---:|---|
| Cold startup normal | 5 runs | BLOCKED — current Central APK not installable unsigned |
| Cold startup / no pending restore | 5 runs | BLOCKED |
| Warm startup | 5 runs | BLOCKED |
| Home render | 5 runs | BLOCKED |
| Sales render | 5 runs | BLOCKED |
| Purchases render | 5 runs | BLOCKED |
| Inventory render | 5 runs | BLOCKED |
| Accounting render | 5 runs | BLOCKED |
| Reports large-range load | 5 runs | BLOCKED |
| Large PDF export | 3 runs | BLOCKED |
| Large XLSX export | 3 runs | BLOCKED |
| Backup workload | 3 runs | BLOCKED |
| Memory / PSS snapshots | each scenario | BLOCKED |
| Frame/jank statistics | screen scenarios | BLOCKED |
| Crash / ANR evidence | all scenarios | BLOCKED |

No timing, memory, frame, or ANR result is fabricated.

## What is required to close P0

Provide either of the following to the execution session, without committing it to GitHub:

1. the permanent FUSH signing `.p12` file so the latest Central release APK can be signed locally and then immediately measured; or
2. an APK built from the current Central baseline and already signed with the required FUSH certificate.

Once one of those is available, the existing emulator can execute the matrix. The resulting P0 record will include raw samples, min/median/p95 where applicable, memory snapshots, frame statistics, report/export/backup durations, ANR/crash checks, database size and workload row counts.

## Safety / impact

- Optimization changes: **NONE**
- P1 started: **NO**
- Business Logic changed: **NO**
- Room schema changed: **NO**
- Migration added: **NO**
- Accounting changed: **NO**
- Inventory changed: **NO**
- Production changed: **NO**
- Application ID changed: **NO**
- Test signing key created: **NO**
- Signing secret committed/uploaded to GitHub: **NO**

## Completion gate

P0 is deliberately **not** marked `COMPLETE / TESTED / READY FOR HANDOFF` until the Runtime Measurement Matrix is executed against the current Central baseline on the Android runtime environment above.
