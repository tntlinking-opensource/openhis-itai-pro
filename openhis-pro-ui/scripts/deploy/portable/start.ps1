param(
    [string]$ConfigFile = (Join-Path $PSScriptRoot 'config.env')
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Read-ConfigEnv($Path) {
    $config = @{
        LISTEN_PORT  = '8080'
        BACKEND_HOST = '127.0.0.1'
        BACKEND_PORT = '8081'
    }
    if (-not (Test-Path $Path)) {
        return $config
    }
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        $idx = $line.IndexOf('=')
        if ($idx -lt 1) { return }
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        if ($key) { $config[$key] = $val }
    }
    return $config
}

$config = Read-ConfigEnv $ConfigFile
$templatePath = Join-Path $PSScriptRoot 'conf\nginx.conf.template'
$nginxConfPath = Join-Path $PSScriptRoot 'conf\nginx.conf'

if (-not (Test-Path $templatePath)) {
    throw "Missing template: $templatePath"
}

$template = Get-Content -LiteralPath $templatePath -Raw
$nginxConf = $template `
    -replace '\{\{LISTEN_PORT\}\}', $config.LISTEN_PORT `
    -replace '\{\{BACKEND_HOST\}\}', $config.BACKEND_HOST `
    -replace '\{\{BACKEND_PORT\}\}', $config.BACKEND_PORT

Set-Content -LiteralPath $nginxConfPath -Value $nginxConf -Encoding ASCII

$nginxExe = Join-Path $PSScriptRoot 'nginx.exe'
if (-not (Test-Path $nginxExe)) {
    throw "Missing nginx.exe in $PSScriptRoot"
}

foreach ($dir in @('logs', 'temp', 'html')) {
    $path = Join-Path $PSScriptRoot $dir
    if (-not (Test-Path $path)) {
        New-Item -ItemType Directory -Path $path | Out-Null
    }
}

& $nginxExe -t -p $PSScriptRoot -c conf/nginx.conf
if ($LASTEXITCODE -ne 0) {
    throw "nginx config test failed"
}

& $nginxExe -p $PSScriptRoot -c conf/nginx.conf
if ($LASTEXITCODE -ne 0) {
    throw "nginx start failed with exit code $LASTEXITCODE"
}

Write-Host ''
Write-Host "OpenHIS Web started." -ForegroundColor Green
Write-Host "  URL     : http://localhost:$($config.LISTEN_PORT)/" -ForegroundColor Cyan
Write-Host "  Backend : http://$($config.BACKEND_HOST):$($config.BACKEND_PORT)" -ForegroundColor DarkGray
Write-Host "  Stop    : run stop.bat" -ForegroundColor DarkGray
