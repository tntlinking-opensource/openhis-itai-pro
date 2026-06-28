# Deprecated: use publish/lib/assemble-portable-bundle.ps1

param(
    [Parameter(Mandatory = $true)]
    [string]$DistDir,
    [Parameter(Mandatory = $true)]
    [string]$OutputZip,
    [string]$NginxVersion = '1.26.3',
    [string]$ListenPort = '8080',
    [string]$BackendHost = '127.0.0.1',
    [string]$BackendPort = '8081',
    [switch]$SkipNginxDownload
)

$ErrorActionPreference = 'Stop'
$Target = Join-Path $PSScriptRoot '..\..\..\publish\lib\assemble-portable-bundle.ps1'
if (-not (Test-Path $Target)) {
    throw "Missing publish assemble script: $Target"
}
& $Target @PSBoundParameters
