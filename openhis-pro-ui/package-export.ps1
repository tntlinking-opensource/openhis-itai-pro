# 转发到 publish/publish-frontend.ps1（发布产物见 publish/releases/）
#
# Usage:
#   .\package-export.ps1
#   .\package-export.ps1 -Clean -SkipInstall

param(
    [switch]$Clean,
    [switch]$SkipInstall,
    [switch]$SkipPortable,
    [switch]$SkipNginxDownload,
    [string]$ListenPort = '8080',
    [string]$BackendHost = '127.0.0.1',
    [string]$BackendPort = '8081'
)

$ErrorActionPreference = 'Stop'

$PublishScript = Join-Path $PSScriptRoot '..\publish\publish-frontend.ps1'
if (-not (Test-Path $PublishScript)) {
    throw "Missing publish script: $PublishScript"
}

$args = @{}
if ($Clean) { $args['Clean'] = $true }
if ($SkipInstall) { $args['SkipInstall'] = $true }
if ($SkipPortable) { $args['SkipPortable'] = $true }
if ($SkipNginxDownload) { $args['SkipNginxDownload'] = $true }
$args['ListenPort'] = $ListenPort
$args['BackendHost'] = $BackendHost
$args['BackendPort'] = $BackendPort

& $PublishScript @args
