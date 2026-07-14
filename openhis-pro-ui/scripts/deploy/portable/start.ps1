param(
    [string]$ConfigFile = (Join-Path $PSScriptRoot 'config.env')
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Read-ConfigEnv($Path) {
    $config = @{
        LISTEN_PORT      = '18080'
        BACKEND_HOST     = '127.0.0.1'
        BACKEND_PORT     = '8081'
        FRONTEND_VERSION = ''
        HTML_ROOT        = 'html'
    }
    if (-not (Test-Path $Path)) { return $config }
    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        $idx = $line.IndexOf('=')
        if ($idx -lt 1) { return }
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        if ($key) { $config[$key] = $val }
    }
    if ($config.FRONTEND_VERSION) {
        $config.HTML_ROOT = "versions/$($config.FRONTEND_VERSION)"
    }
    elseif (-not $config.HTML_ROOT) {
        $config.HTML_ROOT = 'html'
    }
    return $config
}

function Write-ConfigEnv($Path, $Config) {
    if ($Config.FRONTEND_VERSION) {
        $Config.HTML_ROOT = "versions/$($Config.FRONTEND_VERSION)"
    }
    $lines = @(
        '# OpenHIS portable bundle config (edit then run start.bat)',
        '# LISTEN_PORT 由 start.ps1 自动检测；若绑定失败会写入可用端口',
        "LISTEN_PORT=$($Config.LISTEN_PORT)",
        "BACKEND_HOST=$($Config.BACKEND_HOST)",
        "BACKEND_PORT=$($Config.BACKEND_PORT)"
    )
    if ($Config.FRONTEND_VERSION) {
        $lines += "FRONTEND_VERSION=$($Config.FRONTEND_VERSION)"
        $lines += "HTML_ROOT=$($Config.HTML_ROOT)"
    }
    elseif ($Config.HTML_ROOT) {
        $lines += "HTML_ROOT=$($Config.HTML_ROOT)"
    }
    Set-Content -LiteralPath $Path -Value ($lines -join "`r`n") -Encoding ASCII
}

function Get-ExcludedPortRanges {
    $ranges = @()
    try {
        $output = netsh interface ipv4 show excludedportrange protocol=tcp 2>$null
        foreach ($line in $output) {
            if ($line -match '^\s*(\d+)\s+(\d+)\s*$') {
                $start = [int]$Matches[1]
                $end = [int]$Matches[2]
                if ($end -lt $start) { $end = $start }
                $ranges += [pscustomobject]@{ Start = $start; End = $end }
            }
        }
    } catch {
        Write-Warning "无法读取 Windows 保留端口段: $($_.Exception.Message)"
    }
    return $ranges
}

function Test-PortInExcludedRange([int]$Port, $Ranges) {
    foreach ($range in $Ranges) {
        if ($Port -ge $range.Start -and $Port -le $range.End) {
            return $true
        }
    }
    return $false
}

function Test-PortListening([int]$Port) {
    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        return $null -ne $conn
    } catch {
        $line = netstat -ano | Select-String -Pattern "LISTENING" | Select-String -Pattern ":$Port\s"
        return $null -ne $line
    }
}

function Test-PortBindable([int]$Port) {
    $listener = $null
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $Port)
        $listener.Start()
        return $true
    } catch {
        return $false
    } finally {
        if ($null -ne $listener) { $listener.Stop() }
    }
}

function Find-AvailablePort($PreferredPorts, $ExcludedRanges) {
    foreach ($port in $PreferredPorts) {
        if ($port -lt 1 -or $port -gt 65535) { continue }
        if (Test-PortInExcludedRange $port $ExcludedRanges) { continue }
        if (Test-PortListening $port) { continue }
        if (Test-PortBindable $port) { return $port }
    }
    throw '未找到可用 TCP 端口。请以管理员运行 diagnose.bat 查看保留端口段。'
}

function Render-NginxConf($TemplatePath, $OutputPath, $Config) {
    if (-not $Config.HTML_ROOT) { $Config.HTML_ROOT = 'html' }
    $template = Get-Content -LiteralPath $TemplatePath -Raw
    $nginxConf = $template `
        -replace '\{\{LISTEN_PORT\}\}', $Config.LISTEN_PORT `
        -replace '\{\{BACKEND_HOST\}\}', $Config.BACKEND_HOST `
        -replace '\{\{BACKEND_PORT\}\}', $Config.BACKEND_PORT `
        -replace '\{\{HTML_ROOT\}\}', $Config.HTML_ROOT
    Set-Content -LiteralPath $OutputPath -Value $nginxConf -Encoding ASCII
}

function Test-NginxConfig($NginxExe, $Root) {
    & $NginxExe -t -p $Root -c conf/nginx.conf 2>&1 | Out-String
    return ($LASTEXITCODE -eq 0)
}

$config = Read-ConfigEnv $ConfigFile
$templatePath = Join-Path $PSScriptRoot 'conf\nginx.conf.template'
$nginxConfPath = Join-Path $PSScriptRoot 'conf\nginx.conf'
$nginxExe = Join-Path $PSScriptRoot 'nginx.exe'

if (-not (Test-Path $templatePath)) { throw "Missing template: $templatePath" }
if (-not (Test-Path $nginxExe)) { throw "Missing nginx.exe in $PSScriptRoot" }

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Warning '当前未以管理员身份运行。若启动失败，请使用 start-admin.bat。'
}

try {
    Unblock-File -LiteralPath $nginxExe -ErrorAction SilentlyContinue
    Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.exe' -ErrorAction SilentlyContinue | ForEach-Object {
        Unblock-File -LiteralPath $_.FullName -ErrorAction SilentlyContinue
    }
} catch {}

foreach ($dir in @('logs', 'temp', 'html', 'versions', 'conf')) {
    $path = Join-Path $PSScriptRoot $dir
    if (-not (Test-Path $path)) {
        New-Item -ItemType Directory -Path $path | Out-Null
    }
}

# 迁移旧 html / html-a 到 versions/
$versionsRoot = Join-Path $PSScriptRoot 'versions'
$hasVersion = Get-ChildItem $versionsRoot -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path (Join-Path $_.FullName 'index.html') } |
    Select-Object -First 1
if (-not $hasVersion) {
    foreach ($legacyName in @('html', 'html-a', 'html-b')) {
        $legacy = Join-Path $PSScriptRoot $legacyName
        if (Test-Path (Join-Path $legacy 'index.html')) {
            $label = 'migrated-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
            $dest = Join-Path $versionsRoot $label
            New-Item -ItemType Directory -Path $dest -Force | Out-Null
            Copy-Item -Path (Join-Path $legacy '*') -Destination $dest -Recurse -Force
            $config.FRONTEND_VERSION = $label
            $config.HTML_ROOT = "versions/$label"
            Write-ConfigEnv $ConfigFile $config
            Write-Host "Migrated $legacyName/ -> versions/$label/" -ForegroundColor DarkGray
            break
        }
    }
}

$excludedRanges = Get-ExcludedPortRanges
$preferredPorts = @(
    [int]$config.LISTEN_PORT,
    18080, 28080, 38080, 48080, 52080, 58080, 58888, 60080
) | Select-Object -Unique

Write-Host 'Checking listen port ...' -ForegroundColor DarkGray
$listenPort = Find-AvailablePort $preferredPorts $excludedRanges

if ([string]$listenPort -ne [string]$config.LISTEN_PORT) {
    Write-Host "Port $($config.LISTEN_PORT) unavailable, switch to $listenPort" -ForegroundColor Yellow
    $config.LISTEN_PORT = [string]$listenPort
    Write-ConfigEnv $ConfigFile $config
}

Render-NginxConf $templatePath $nginxConfPath $config

if (-not (Test-NginxConfig $nginxExe $PSScriptRoot)) {
    Write-Host 'nginx -t failed on selected port, trying another port ...' -ForegroundColor Yellow
    $fallbackPorts = @(30001, 31001, 32001, 33001, 34001, 35001, 36001, 37001, 38001, 39001, 40001)
    $listenPort = Find-AvailablePort $fallbackPorts $excludedRanges
    $config.LISTEN_PORT = [string]$listenPort
    Write-ConfigEnv $ConfigFile $config
    Render-NginxConf $templatePath $nginxConfPath $config
    if (-not (Test-NginxConfig $nginxExe $PSScriptRoot)) {
        throw @"
nginx config test failed.
请以管理员身份运行 start-admin.bat，或执行 diagnose.bat 查看保留端口段。
若仍失败，请将 E:\whale\openhis-web 加入杀毒软件白名单。
"@
    }
}

& $nginxExe -p $PSScriptRoot -c conf/nginx.conf
if ($LASTEXITCODE -ne 0) { throw "nginx start failed with exit code $LASTEXITCODE" }

Write-Host ''
Write-Host 'OpenHIS Web started.' -ForegroundColor Green
Write-Host "  URL     : http://localhost:$($config.LISTEN_PORT)/" -ForegroundColor Cyan
Write-Host "  LAN     : http://<server-ip>:$($config.LISTEN_PORT)/" -ForegroundColor Cyan
Write-Host "  Backend : http://$($config.BACKEND_HOST):$($config.BACKEND_PORT)" -ForegroundColor DarkGray
if ($config.FRONTEND_VERSION) {
    Write-Host "  Frontend: $($config.FRONTEND_VERSION)" -ForegroundColor DarkGray
}
Write-Host '  Stop    : run stop.bat' -ForegroundColor DarkGray
