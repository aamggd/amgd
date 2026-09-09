$ErrorActionPreference = "Stop"
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) {
    throw "Gradle is not in PATH. Use the installed Gradle 9.4.1 executable first."
}
gradle wrapper --gradle-version 9.4.1 --distribution-type bin
Write-Host "Gradle wrapper 9.4.1 generated." -ForegroundColor Green
