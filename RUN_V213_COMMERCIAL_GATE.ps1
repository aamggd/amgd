$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
python tools/verify_v213_commercial_multitenant.py
if (-not (Test-Path "gradle/wrapper/gradle-wrapper.jar")) { throw "gradle/wrapper/gradle-wrapper.jar is missing" }
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
if (-not $sdk -or -not (Test-Path $sdk)) { throw "Set ANDROID_SDK_ROOT or ANDROID_HOME to Android SDK" }
& .\gradlew.bat --version
& .\gradlew.bat clean test :app:lintVitalRelease :app:assembleRelease
if (-not (Test-Path "app/schemas/com.fush.erp.data.FushDatabase/54.json")) { throw "Room compiler did not export schema 54.json" }
$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) { throw "DEVICE GATE PENDING: adb not found; connectedDebugAndroidTest not run" }
$device = (& adb devices | Select-String "\tdevice$")
if (-not $device) { throw "DEVICE GATE PENDING: no adb device/emulator; connectedDebugAndroidTest not run" }
& .\gradlew.bat :app:connectedDebugAndroidTest
Write-Host "V213 ANDROID COMMERCIAL BUILD GATE PASS" -ForegroundColor Green
