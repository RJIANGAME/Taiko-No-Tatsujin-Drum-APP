[CmdletBinding()]
param(
    [string]$Configuration = "Release",
    [string]$Runtime = "win-x64"
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$project = Join-Path $root "receiver\TaikoDrumReceiver.csproj"
$output = Join-Path $root "receiver\publish\$Runtime"

$sdks = & dotnet --list-sdks
if (-not $sdks) {
    throw "The .NET SDK is not installed. Install the .NET 8 SDK, then rerun this script."
}

& dotnet publish $project -c $Configuration -r $Runtime --self-contained false -o $output
Write-Host "Receiver output: $output"
