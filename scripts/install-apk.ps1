[CmdletBinding()]
param(
    [switch]$BuildFirst,
    [string]$AdbPath = ""
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")

if ($BuildFirst) {
    & (Join-Path $PSScriptRoot "build-android.ps1")
}

function Resolve-Adb {
    param([string]$RequestedPath)

    if ($RequestedPath -and (Test-Path -LiteralPath $RequestedPath)) {
        return (Resolve-Path -LiteralPath $RequestedPath).Path
    }

    $fromPath = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($fromPath) {
        return $fromPath.Source
    }

    $sdkAdb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (Test-Path -LiteralPath $sdkAdb) {
        return $sdkAdb
    }

    throw "adb.exe was not found. Install Android platform-tools or pass -AdbPath."
}

$apkCandidates = @(
    (Join-Path $root "Taiko-Phone-Drum-debug.apk"),
    (Join-Path $root "app-debug.apk"),
    (Join-Path $root "app\build\outputs\apk\debug\app-debug.apk")
)

$apk = $apkCandidates | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $apk) {
    throw "APK not found. Put Taiko-Phone-Drum-debug.apk next to Taiko-Drum-Menu.bat, or run scripts\build-android.ps1 first."
}

$adb = Resolve-Adb -RequestedPath $AdbPath
& $adb install -r $apk
