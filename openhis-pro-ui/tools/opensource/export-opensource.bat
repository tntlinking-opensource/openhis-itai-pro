@echo off
setlocal EnableExtensions
cd /d "%~dp0..\.."

echo.
echo [export-opensource] output: dist\openhis-pro-ui-opensource (without projects/)
echo.

if "%~1"=="" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0export-opensource.ps1" -Force
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0export-opensource.ps1" %*
)

set "EXITCODE=%ERRORLEVEL%"
echo.
if not "%EXITCODE%"=="0" (
    echo failed, exit code: %EXITCODE%
    pause
    exit /b %EXITCODE%
)
echo done. output: dist\openhis-pro-ui-opensource
exit /b 0
