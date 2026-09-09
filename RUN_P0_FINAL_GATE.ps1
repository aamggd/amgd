$ErrorActionPreference = "Stop"

$root = Get-Location
$logDir = Join-Path $root "p0-final-gate-results"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Run-Gate {
    param(
        [string]$Name,
        [scriptblock]$Command
    )
    Write-Host ""
    Write-Host "=== $Name ===" -ForegroundColor Cyan
    & $Command 2>&1 | Tee-Object -FilePath (Join-Path $logDir "$Name.txt")
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAIL: $Name" -ForegroundColor Red
        exit $LASTEXITCODE
    }
    Write-Host "PASS: $Name" -ForegroundColor Green
}

if (-not (Test-Path ".\gradlew.bat")) {
    Write-Host "gradlew.bat is missing." -ForegroundColor Yellow
    Write-Host "Generate the wrapper first with:" -ForegroundColor Yellow
    Write-Host "powershell -ExecutionPolicy Bypass -File .\GENERATE_GRADLE_WRAPPER.ps1"
    exit 2
}

# Gate 1: P0 targeted accounting regression
Run-Gate "01-targeted-p0-accounting" {
    .\gradlew.bat :app:testDebugUnitTest `
        --tests "com.fush.erp.domain.AccountingP1IntegrityPolicyTest" `
        --tests "com.fush.erp.domain.AccountingIntegrationContractTest" `
        --tests "com.fush.erp.domain.AccountingPostingIdempotencyPolicyTest" `
        --tests "com.fush.erp.domain.SalesCommissionIdempotencyPolicyTest" `
        --tests "com.fush.erp.domain.AccountingPeriodPostingPolicyTest" `
        --tests "com.fush.erp.domain.AccountingPeriodConsumerContractTest" `
        --tests "com.fush.erp.domain.AccountingPeriodFailClosedContractTest" `
        --tests "com.fush.erp.domain.TreasuryVoucherOperationIdentityTest" `
        --tests "com.fush.erp.domain.TreasuryVoucherDoubleSubmitContractTest" `
        --no-daemon
}

# Gate 2: Full unit regression
Run-Gate "02-full-unit" {
    .\gradlew.bat :app:testDebugUnitTest --no-daemon
}

# Gate 3: Debug build
Run-Gate "03-assemble-debug" {
    .\gradlew.bat :app:assembleDebug --no-daemon
}

# Gate 4: source invariants
$buildGradle = Get-Content ".\app\build.gradle.kts" -Raw
$dbFile = Get-Content ".\app\src\main\java\com\fush\erp\data\FushDatabase.kt" -Raw
$allMain = Get-ChildItem ".\app\src\main" -Recurse -File | Where-Object {
    $_.Extension -in ".kt", ".kts", ".xml"
} | ForEach-Object { Get-Content $_.FullName -Raw }

$failures = @()

if ($buildGradle -notmatch 'applicationId\s*=\s*"com\.fush\.erp\.recovery"') {
    $failures += "AppID changed"
}
if ($buildGradle -notmatch 'versionCode\s*=\s*104') {
    $failures += "versionCode is not 104"
}
if ($dbFile -notmatch 'FUSH_DB_SCHEMA_VERSION\s*=\s*38') {
    $failures += "Room schema is not 38"
}
if (($allMain -join "`n") -match 'fallbackToDestructiveMigration') {
    $failures += "Destructive Room migration fallback detected"
}

$apk = ".\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) {
    $failures += "Debug APK not produced"
}

$summary = Join-Path $logDir "P0_FINAL_GATE_SUMMARY.txt"

if ($failures.Count -gt 0) {
    @(
        "FUSH ERP Mobile v104 — P0 FINAL REGRESSION GATE"
        "RESULT: FAIL"
        ""
        $failures
    ) | Set-Content -Encoding UTF8 $summary
    Get-Content $summary
    exit 3
}

@(
    "FUSH ERP Mobile v104 — P0 FINAL REGRESSION GATE"
    "RESULT: PASS"
    ""
    "Targeted P0 accounting tests: PASS"
    "Full unit regression: PASS"
    "assembleDebug: PASS"
    "AppID: com.fush.erp.recovery"
    "versionCode: 104"
    "Room schema: 38"
    "Destructive migration fallback: NOT FOUND"
    "Debug APK: $apk"
) | Set-Content -Encoding UTF8 $summary

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "P0 FINAL REGRESSION GATE: PASS" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Get-Content $summary
