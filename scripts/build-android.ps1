[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
& (Join-Path $root "gradlew.bat") ":app:assembleDebug"
Write-Host "APK: $(Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk')"
