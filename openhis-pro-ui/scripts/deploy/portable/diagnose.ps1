$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot

Write-Host '=== OpenHIS Web diagnose ===' -ForegroundColor Cyan
Write-Host ''

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
Write-Host "Administrator: $isAdmin"

Write-Host ''
Write-Host '--- config.env ---'
if (Test-Path '.\config.env') { Get-Content '.\config.env' } else { Write-Host 'missing' }

Write-Host ''
Write-Host '--- nginx.conf listen ---'
if (Test-Path '.\conf\nginx.conf') {
    Select-String -Path '.\conf\nginx.conf' -Pattern 'listen'
} else {
    Write-Host 'missing'
}

Write-Host ''
Write-Host '--- excluded port ranges (Hyper-V/WSL) ---'
netsh interface ipv4 show excludedportrange protocol=tcp

Write-Host ''
Write-Host '--- listening ports (8080/8081/18080/sample) ---'
netstat -ano | findstr "LISTENING" | findstr ":8080 :8081 :18080 :28080 :52080"

Write-Host ''
Write-Host '--- nginx.exe blocked? ---'
Get-Item '.\nginx.exe' -Stream * -ErrorAction SilentlyContinue | Format-Table Stream, Length

Write-Host ''
Write-Host '--- recent error.log ---'
if (Test-Path '.\logs\error.log') { Get-Content '.\logs\error.log' -Tail 20 } else { Write-Host 'no error.log' }

Write-Host ''
Write-Host 'If many ports fail with 10013, try:' -ForegroundColor Yellow
Write-Host '  1. start-admin.bat (run as administrator)'
Write-Host '  2. Unblock nginx.exe in file properties'
Write-Host '  3. Add this folder to antivirus exclusions'
Write-Host '  4. net stop winnat && net start winnat  (admin, may free reserved ports)'
