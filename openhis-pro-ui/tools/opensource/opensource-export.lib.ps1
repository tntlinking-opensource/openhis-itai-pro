# Open-source export helpers (dot-sourced by export-opensource.ps1)

function Import-UiOpenSourceExportManifest {
    param([string]$UiRoot)
    $path = Join-Path $UiRoot 'tools\opensource\opensource-export-manifest.json'
    if (-not (Test-Path $path)) {
        throw "Missing manifest: $path"
    }
    $manifest = Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not ($manifest.PSObject.Properties.Name -contains 'ExcludedRootDirectories')) {
        throw 'opensource-export-manifest.json missing ExcludedRootDirectories'
    }
    return $manifest
}

function Get-UiOpenSourceRobocopyExcludeDirs {
    param($Manifest)
    return @($Manifest.ExcludedRootDirectories)
}

function Remove-UiOpenSourceExcludedPaths {
    param(
        [string]$OutputDir,
        [string[]]$RelativePaths,
        [scriptblock]$RemoveDir
    )
    foreach ($rel in $RelativePaths) {
        $target = Join-Path $OutputDir ($rel -replace '/', '\')
        if (-not (Test-Path $target)) { continue }
        $item = Get-Item -LiteralPath $target -Force
        if ($item.PSIsContainer) {
            & $RemoveDir $target
        } else {
            Remove-Item -LiteralPath $target -Force
        }
        Write-Host "  - removed $rel" -ForegroundColor DarkGray
    }
}

function Assert-UiOpenSourceTreeClean {
    param([string]$OutputDir)
    $projectsDir = Join-Path $OutputDir 'projects'
    if (Test-Path $projectsDir) {
        throw 'Export validation failed: projects/ still present'
    }
}
