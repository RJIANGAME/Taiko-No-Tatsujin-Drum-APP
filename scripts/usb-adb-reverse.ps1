[CmdletBinding()]
param(
    [int]$Port = 27183,
    [string]$AdbPath = ""
)

$ErrorActionPreference = "Stop"

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

if ($Port -lt 1 -or $Port -gt 65535) {
    throw "Port must be between 1 and 65535."
}

$adb = Resolve-Adb -RequestedPath $AdbPath
Write-Host "Using adb: $adb"

& $adb start-server | Out-Host
$devices = & $adb devices
if (-not ($devices | Select-String "`tdevice")) {
    $devices | Out-Host
    throw "No authorized Android device found. Connect USB, enable USB debugging, and accept the phone prompt."
}

& $adb reverse "tcp:$Port" "tcp:$Port" | Out-Host
Write-Host "ADB reverse ready: Android 127.0.0.1:$Port -> PC 127.0.0.1:$Port"
