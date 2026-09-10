$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$EvidenceDir = Join-Path $env:GITHUB_WORKSPACE 'evidence'
$WorkSource = Join-Path $env:GITHUB_WORKSPACE 'work-source'
$Candidate = Join-Path $env:GITHUB_WORKSPACE ("exact-central-candidate\" + $env:CENTRAL_CANDIDATE_APK)
$InstrumentTimeoutSeconds = 300
New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null

function Write-TextFile {
    param([string]$Path, [string]$Text)
    [System.IO.File]::WriteAllText($Path, $Text, [System.Text.UTF8Encoding]::new($false))
}

function Capture-Diagnostics {
    param([Parameter(Mandatory=$true)][string]$Prefix)

    $serial = $env:ANDROID_SERIAL
    $targets = @(
        @{ Args = @('-s',$serial,'logcat','-d','-v','threadtime'); File = "$Prefix-logcat.txt" },
        @{ Args = @('-s',$serial,'shell','dumpsys','activity','processes'); File = "$Prefix-activity-processes.txt" },
        @{ Args = @('-s',$serial,'shell','dumpsys','activity','instrumentation'); File = "$Prefix-activity-instrumentation.txt" },
        @{ Args = @('-s',$serial,'shell','dumpsys','package',$env:APP_ID); File = "$Prefix-target-package.txt" },
        @{ Args = @('-s',$serial,'shell','dumpsys','package','com.fush.erp.recovery.test'); File = "$Prefix-test-package.txt" }
    )

    foreach ($target in $targets) {
        try {
            $text = (& adb @($target.Args) 2>&1 | Out-String)
            Write-TextFile (Join-Path $EvidenceDir $target.File) $text
        } catch {
            Write-TextFile (Join-Path $EvidenceDir $target.File) ("DIAGNOSTIC_CAPTURE_ERROR: " + $_.Exception.Message + [Environment]::NewLine)
        }
    }
}

function Assert-DoesNotContain {
    param([string]$Text, [string]$Pattern, [string]$Label)
    if ($Text.Contains($Pattern)) {
        throw "FAIL: $Label found '$Pattern'"
    }
}

function Assert-Contains {
    param([string]$Text, [string]$Pattern, [string]$Label)
    if (-not $Text.Contains($Pattern)) {
        throw "FAIL: $Label missing '$Pattern'"
    }
}

function Validate-InstrumentationOutput {
    param(
        [string]$Output,
        [int]$ExpectedCount,
        [string[]]$ExpectedMethods
    )

    Assert-DoesNotContain $Output 'FAILURES!!!' 'JUnit reported failures'
    Assert-DoesNotContain $Output 'INSTRUMENTATION_FAILED' 'instrumentation framework failure'
    Assert-DoesNotContain $Output 'shortMsg=Process crashed' 'instrumentation process crash'
    Assert-Contains $Output "OK ($ExpectedCount tests)" 'exact JUnit summary'
    Assert-Contains $Output 'INSTRUMENTATION_CODE: -1' 'successful terminal instrumentation code'

    $lines = $Output -split "`r?`n"
    $numTests = @()
    $methods = @()
    $finished = 0

    foreach ($line in $lines) {
        if ($line -match '^INSTRUMENTATION_STATUS: numtests=(\d+)$') {
            $numTests += [int]$Matches[1]
        } elseif ($line -match '^INSTRUMENTATION_STATUS: test=(.+)$') {
            $methods += $Matches[1]
        } elseif ($line.Trim() -eq 'INSTRUMENTATION_STATUS_CODE: 0') {
            $finished++
        }
    }

    if ($numTests.Count -eq 0) {
        throw 'FAIL: no INSTRUMENTATION_STATUS numtests values were emitted'
    }
    foreach ($value in $numTests) {
        if ($value -ne $ExpectedCount) {
            throw "FAIL: expected numtests=$ExpectedCount, observed $($numTests -join ',')"
        }
    }
    if ($finished -ne $ExpectedCount) {
        throw "FAIL: expected $ExpectedCount completed tests, observed $finished"
    }

    $uniqueMethods = @($methods | Select-Object -Unique)
    if ($uniqueMethods.Count -ne $ExpectedCount) {
        throw "FAIL: expected $ExpectedCount unique test methods, observed $($uniqueMethods -join ',')"
    }

    $expectedSorted = @($ExpectedMethods | Sort-Object)
    $actualSorted = @($uniqueMethods | Sort-Object)
    if (($expectedSorted -join "`n") -ne ($actualSorted -join "`n")) {
        throw "FAIL: expected methods [$($expectedSorted -join ',')], observed [$($actualSorted -join ',')]"
    }
}

function Invoke-InstrumentationClass {
    param(
        [Parameter(Mandatory=$true)][string]$ClassName,
        [Parameter(Mandatory=$true)][int]$ExpectedCount,
        [Parameter(Mandatory=$true)][string]$EvidenceName,
        [Parameter(Mandatory=$true)][string[]]$ExpectedMethods
    )

    $serial = $env:ANDROID_SERIAL
    & adb -s $serial logcat -c | Out-Null

    $stdoutPath = Join-Path $EvidenceDir "$EvidenceName-stdout.txt"
    $stderrPath = Join-Path $EvidenceDir "$EvidenceName-stderr.txt"
    $combinedPath = Join-Path $EvidenceDir "$EvidenceName-junit.txt"
    $rcPath = Join-Path $EvidenceDir "$EvidenceName-am-instrument-rc.txt"

    $adbExe = (Get-Command adb -ErrorAction Stop).Source
    $args = @(
        '-s', $serial,
        'shell', 'am', 'instrument', '-w', '-r',
        '-e', 'class', $ClassName,
        'com.fush.erp.recovery.test/androidx.test.runner.AndroidJUnitRunner'
    )

    $process = Start-Process -FilePath $adbExe -ArgumentList $args -PassThru -NoNewWindow `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath

    $completed = $process.WaitForExit($InstrumentTimeoutSeconds * 1000)
    if (-not $completed) {
        try { $process.Kill($true) } catch { try { $process.Kill() } catch {} }
        Capture-Diagnostics $EvidenceName
        Write-TextFile (Join-Path $EvidenceDir "$EvidenceName-timeout-status.txt") @"
STATUS=QA HARNESS TIMEOUT / AM INSTRUMENT
TEST_CLASS=$ClassName
TIMEOUT_SECONDS=$InstrumentTimeoutSeconds
"@
        throw "FAIL: am instrument timeout after $InstrumentTimeoutSeconds seconds for $ClassName"
    }

    $process.WaitForExit()
    $stdout = if (Test-Path $stdoutPath) { Get-Content -Raw $stdoutPath } else { '' }
    $stderr = if (Test-Path $stderrPath) { Get-Content -Raw $stderrPath } else { '' }
    $output = $stdout + [Environment]::NewLine + $stderr
    Write-TextFile $combinedPath $output
    Write-TextFile $rcPath ($process.ExitCode.ToString() + [Environment]::NewLine)

    Capture-Diagnostics $EvidenceName

    if ($process.ExitCode -ne 0) {
        throw "FAIL: am instrument returned non-zero exit code $($process.ExitCode) for $ClassName"
    }

    Validate-InstrumentationOutput -Output $output -ExpectedCount $ExpectedCount -ExpectedMethods $ExpectedMethods
}

Set-Location $WorkSource

gradle --no-daemon :app:assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) { throw 'FAIL: assembleDebugAndroidTest failed' }

gradle --no-daemon :app:dependencyInsight --configuration debugAndroidTestRuntimeClasspath --dependency kotlinx-serialization-core |
    Tee-Object -FilePath (Join-Path $EvidenceDir 'dependency-kotlinx-serialization-core.txt')
if ($LASTEXITCODE -ne 0) { throw 'FAIL: dependencyInsight serialization core failed' }

gradle --no-daemon :app:dependencyInsight --configuration debugAndroidTestRuntimeClasspath --dependency kotlinx-serialization-json |
    Tee-Object -FilePath (Join-Path $EvidenceDir 'dependency-kotlinx-serialization-json.txt')
if ($LASTEXITCODE -ne 0) { throw 'FAIL: dependencyInsight serialization json failed' }

$coreInsight = Get-Content -Raw (Join-Path $EvidenceDir 'dependency-kotlinx-serialization-core.txt')
$jsonInsight = Get-Content -Raw (Join-Path $EvidenceDir 'dependency-kotlinx-serialization-json.txt')
Assert-Contains $coreInsight 'org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3' 'serialization core pin'
Assert-Contains $jsonInsight 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3' 'serialization json pin'

$testApk = Join-Path $WorkSource 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
if (-not (Test-Path $Candidate)) { throw "FAIL: exact candidate APK not found: $Candidate" }
if (-not (Test-Path $testApk)) { throw "FAIL: instrumentation APK not found: $testApk" }

$candidateHash = (Get-FileHash -Algorithm SHA256 $Candidate).Hash.ToLowerInvariant()
if ($candidateHash -ne $env:CENTRAL_CANDIDATE_SHA256.ToLowerInvariant()) {
    throw "FAIL: candidate SHA256 mismatch: $candidateHash"
}

$buildToolsDir = Join-Path $env:ANDROID_HOME 'build-tools\36.0.0'
$apkSigner = Join-Path $buildToolsDir 'apksigner.bat'
if (-not (Test-Path $apkSigner)) { throw "FAIL: apksigner not found: $apkSigner" }

$debugKeystore = Join-Path $env:USERPROFILE '.android\debug.keystore'
$signedTarget = Join-Path $EvidenceDir 'FushERP-qualification-target-signed.apk'
$signedTest = Join-Path $EvidenceDir 'FushERP-qualification-test-signed.apk'
Remove-Item -Force -ErrorAction SilentlyContinue $signedTarget, $signedTest

& $apkSigner sign --ks $debugKeystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $signedTarget $Candidate
if ($LASTEXITCODE -ne 0) { throw 'FAIL: target APK QA signing failed' }
& $apkSigner sign --ks $debugKeystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $signedTest $testApk
if ($LASTEXITCODE -ne 0) { throw 'FAIL: instrumentation APK QA signing failed' }

$targetCert = (& $apkSigner verify --print-certs $signedTarget 2>&1 | Out-String)
$testCert = (& $apkSigner verify --print-certs $signedTest 2>&1 | Out-String)
Write-TextFile (Join-Path $EvidenceDir 'target-cert.txt') $targetCert
Write-TextFile (Join-Path $EvidenceDir 'test-cert.txt') $testCert

$targetDigestMatch = [regex]::Match($targetCert, 'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)')
$testDigestMatch = [regex]::Match($testCert, 'Signer #1 certificate SHA-256 digest:\s*([0-9a-fA-F]+)')
if (-not $targetDigestMatch.Success -or -not $testDigestMatch.Success) {
    throw 'FAIL: unable to extract QA signer digest'
}
if ($targetDigestMatch.Groups[1].Value -ne $testDigestMatch.Groups[1].Value) {
    throw 'FAIL: target and instrumentation APK signatures do not match'
}

$serial = $env:ANDROID_SERIAL
& adb -s $serial uninstall com.fush.erp.recovery.test 2>&1 | Tee-Object -FilePath (Join-Path $EvidenceDir 'test-uninstall.txt') | Out-Null

$targetInstall = (& adb -s $serial install -r $signedTarget 2>&1 | Out-String)
Write-TextFile (Join-Path $EvidenceDir 'target-install.txt') $targetInstall
if ($LASTEXITCODE -ne 0 -or -not $targetInstall.Contains('Success')) {
    throw 'FAIL: target APK install failed. Existing differently-signed target package is not removed automatically; use a dedicated QA device or prepare it manually.'
}

$testInstall = (& adb -s $serial install -r $signedTest 2>&1 | Out-String)
Write-TextFile (Join-Path $EvidenceDir 'test-install.txt') $testInstall
if ($LASTEXITCODE -ne 0 -or -not $testInstall.Contains('Success')) {
    throw 'FAIL: instrumentation APK install failed'
}

$instrumentationList = (& adb -s $serial shell pm list instrumentation 2>&1 | Out-String)
Write-TextFile (Join-Path $EvidenceDir 'instrumentation-list.txt') $instrumentationList
Assert-Contains $instrumentationList 'com.fush.erp.recovery.test/androidx.test.runner.AndroidJUnitRunner' 'instrumentation registration'

Invoke-InstrumentationClass `
    -ClassName 'com.fush.erp.qa.Wave1P1ReleaseCandidateTest' `
    -ExpectedCount 4 `
    -EvidenceName 'release-candidate' `
    -ExpectedMethods @(
        'exactTargetPackage_isRecoveryApplication_andHasLaunchableActivity',
        'saleCollectionAndReturn_keepCustomerIdentityTreasuryPartyAndAccountingEventIdentityAligned',
        'purchasePaymentAndReturn_keepSupplierIdentityTreasuryPartyAndAccountingEventIdentityAligned',
        'generalTreasuryAccount_rejectsOrphanPartyLinkage'
    )

Invoke-InstrumentationClass `
    -ClassName 'com.fush.erp.data.AccountingP1Migration34To35Test' `
    -ExpectedCount 2 `
    -EvidenceName 'migration-34-35' `
    -ExpectedMethods @(
        'migrate34To35_preservesHistoricalPostedRows_andEnforcesWave1JournalGuards',
        'migrate32To35_validatesCompleteSecurityFixedAssetAccountingChain_withoutDestructiveReset'
    )

$migrationLogPath = Join-Path $EvidenceDir 'migration-34-35-logcat.txt'
$migrationLog = Get-Content -Raw $migrationLogPath
$markers = @(
    'BODY_START:migrate34To35_preservesHistoricalPostedRows_andEnforcesWave1JournalGuards',
    'MIGRATION_EXECUTED:34->35',
    'BODY_PASS:migrate34To35_preservesHistoricalPostedRows_andEnforcesWave1JournalGuards',
    'BODY_START:migrate32To35_validatesCompleteSecurityFixedAssetAccountingChain_withoutDestructiveReset',
    'MIGRATION_CHAIN_EXECUTED:32->35',
    'BODY_PASS:migrate32To35_validatesCompleteSecurityFixedAssetAccountingChain_withoutDestructiveReset'
)
foreach ($marker in $markers) {
    Assert-Contains $migrationLog $marker 'migration body evidence marker'
}

Capture-Diagnostics 'qualification-final'

Write-TextFile (Join-Path $EvidenceDir 'LIGHTWEIGHT-PHYSICAL-DEVICE-QUALIFICATION-STATUS.txt') @"
STATUS=QA HARNESS LIGHTWEIGHT QUALIFICATION — PHYSICAL ANDROID DEVICE PASS
Exact Central HEAD=$($env:CENTRAL_SHA)
Exact Central source tree=$($env:CENTRAL_SOURCE_TREE)
Room schema=$($env:ROOM_SCHEMA)
Application ID=$($env:APP_ID)
Android serial=$serial
Wave1P1ReleaseCandidateTest=4/4 PASS
AccountingP1Migration34To35Test=2/2 PASS
Migration test body execution markers=PASS
am instrument non-zero enforcement=ACTIVE
FAILURES!!! rejection=ACTIVE
Expected test-count validation=ACTIVE
Per-instrument timeout=${InstrumentTimeoutSeconds}s
kotlinx-serialization test runtime=1.7.3
Business Logic changes by QA=NONE
Full Final QA=NOT RUN
Final Part2C=NOT RUN
"@

Get-Content (Join-Path $EvidenceDir 'LIGHTWEIGHT-PHYSICAL-DEVICE-QUALIFICATION-STATUS.txt')
