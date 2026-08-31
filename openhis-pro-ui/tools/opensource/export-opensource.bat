@echo off
setlocal EnableExtensions
REM 改版本: export-opensource.bat -Force -NewVersion 2.0.6
REM 前后端一起: whale-sever\tools\sync-opensource\sync-to-opensource.bat
cd /d "%~dp0..\.."

echo.
echo [export-opensource] default -^> ..\openhis-itai-pro\openhis-pro-ui (without projects/)
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
echo done.
exit /b 0
