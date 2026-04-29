[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ReceiverArgs
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$project = Join-Path $root "receiver\TaikoDrumReceiver.csproj"
$fallback = Join-Path $root "receiver\TaikoDrumReceiver.ps1"

$dotnet = Get-Command dotnet -ErrorAction SilentlyContinue
$sdks = if ($dotnet) { & dotnet --list-sdks } else { @() }
if ($sdks) {
    & dotnet run --project $project -- @ReceiverArgs
} else {
    Write-Host "The .NET SDK is not installed. Using PowerShell receiver fallback."
    Write-Host ""
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $fallback @ReceiverArgs
}
