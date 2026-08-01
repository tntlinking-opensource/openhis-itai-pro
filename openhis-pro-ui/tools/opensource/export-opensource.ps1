# Export openhis-pro-ui for public / open-source tree (private repo unchanged).
# Excludes projects/ (hospital customization) and local-only junk.
#
# Default output: ..\openhis-itai-pro\openhis-pro-ui
# Override:  .\export-opensource.ps1 -OutputDir D:\path\to\out
#            .\export-opensource.ps1 -OutputDir dist\openhis-pro-ui-opensource

param(
    [string]$OutputDir,
    [switch]$VerifyBuild,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$UiRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)

Set-Location $UiRoot

. (Join-Path $ScriptDir 'opensource-export.lib.ps1')

$Manifest = Import-UiOpenSourceExportManifest -UiRoot $UiRoot

if (-not $OutputDir) {
    $rel = if ($Manifest.DefaultOutputDir) { $Manifest.DefaultOutputDir } else { '../openhis-itai-pro/openhis-pro-ui' }
    $OutputDir = [System.IO.Path]::GetFullPath((Join-Path $UiRoot ($rel -replace '/', '\')))
} elseif (-not [System.IO.Path]::IsPathRooted($OutputDir)) {
    $OutputDir = [System.IO.Path]::GetFullPath((Join-Path $UiRoot $OutputDir))
}

function Write-Step([string]$Message) {
    Write-Host ''
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Remove-DirIfExists([string]$Path) {
    if (-not (Test-Path $Path)) { return }
    $empty = Join-Path $env:TEMP ("empty-" + [guid]::NewGuid().ToString())
    New-Item -ItemType Directory -Force -Path $empty | Out-Null
    try {
        & robocopy $empty $Path /MIR /NFL /NDL /NJH /NJS /NC /NS | Out-Null
        Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction SilentlyContinue
    } finally {
        Remove-Item -LiteralPath $empty -Recurse -Force -ErrorAction SilentlyContinue
    }
}

function Clear-OutputDirectory([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Force -Path $Path | Out-Null
        return
    }
    Get-ChildItem -LiteralPath $Path -Force | ForEach-Object {
        if ($_.Name -eq '.git') {
            Write-Host '  keep: .git' -ForegroundColor DarkGray
            return
        }
        if ($_.PSIsContainer) {
            Remove-DirIfExists $_.FullName
        } else {
            Remove-Item -LiteralPath $_.FullName -Force
        }
    }
}

$excludeDirs = Get-UiOpenSourceRobocopyExcludeDirs -Manifest $Manifest
$excludeArgs = @()
foreach ($dir in $excludeDirs) {
    $excludeArgs += '/XD'
    $excludeArgs += $dir
}

if ((Test-Path $OutputDir) -and -not $Force) {
    throw "Output directory already exists: $OutputDir. Use -Force to overwrite."
}

Write-Step "Prepare output directory: $OutputDir"
Clear-OutputDirectory $OutputDir

Write-Step 'Copy openhis-pro-ui (exclude projects/ and build artifacts)'
Write-Host ("  exclude dirs: " + ($excludeDirs -join ', ')) -ForegroundColor DarkGray
& robocopy $UiRoot $OutputDir /E @excludeArgs /NFL /NDL /NJH /NJS /NC /NS | Out-Null
if ($LASTEXITCODE -ge 8) {
    throw "robocopy failed ($LASTEXITCODE): $UiRoot -> $OutputDir"
}

if ($Manifest.PSObject.Properties.Name -contains 'ExcludedPaths') {
    Write-Step 'Remove extra excluded paths'
    Remove-UiOpenSourceExcludedPaths -OutputDir $OutputDir -RelativePaths @($Manifest.ExcludedPaths) -RemoveDir ${function:Remove-DirIfExists}
}

Write-Step 'Assert open-source tree is clean (no projects/)'
Assert-UiOpenSourceTreeClean -OutputDir $OutputDir

Write-Step 'Write opensource .gitignore'
$opensourceGitignore = Join-Path $ScriptDir 'opensource-gitignore.txt'
if (-not (Test-Path $opensourceGitignore)) {
    throw 'Missing tools/opensource/opensource-gitignore.txt'
}
Copy-Item -LiteralPath $opensourceGitignore -Destination (Join-Path $OutputDir '.gitignore') -Force

if ($VerifyBuild) {
    Write-Step 'Verify build (product baseline build:ele, no OPENHIS_PROJECT)'
    Push-Location $OutputDir
    try {
        Remove-Item Env:OPENHIS_PROJECT -ErrorAction SilentlyContinue
        Remove-Item Env:VITE_OPENHIS_PROJECT -ErrorAction SilentlyContinue

        & pnpm install --frozen-lockfile
        if ($LASTEXITCODE -ne 0) {
            throw 'VerifyBuild failed: pnpm install'
        }

        $buildCmd = if ($Manifest.VerifyBuildCommand) { $Manifest.VerifyBuildCommand } else { 'build:ele' }
        & pnpm run $buildCmd
        if ($LASTEXITCODE -ne 0) {
            throw "VerifyBuild failed: pnpm run $buildCmd"
        }

        $indexHtml = Join-Path $OutputDir 'apps\web-ele\dist\index.html'
        if (-not (Test-Path $indexHtml)) {
            throw 'VerifyBuild failed: apps/web-ele/dist/index.html missing'
        }
        Write-Host 'VerifyBuild OK' -ForegroundColor Green
    } finally {
        Pop-Location
    }
}

Write-Host ''
Write-Host 'Export done:' -ForegroundColor Green
Write-Host "  $OutputDir" -ForegroundColor Green
Write-Host ''
Write-Host 'Build: pnpm install; pnpm run build:ele' -ForegroundColor Yellow
Write-Host 'Manifest: tools/opensource/opensource-export-manifest.json' -ForegroundColor Yellow
Write-Host ''
