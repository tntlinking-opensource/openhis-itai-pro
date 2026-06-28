$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$nginxExe = Join-Path $PSScriptRoot 'nginx.exe'
if (-not (Test-Path $nginxExe)) {
    throw "Missing nginx.exe in $PSScriptRoot"
}

& $nginxExe -s stop -p $PSScriptRoot -c conf/nginx.conf
Write-Host 'OpenHIS Web stopped.' -ForegroundColor Green
