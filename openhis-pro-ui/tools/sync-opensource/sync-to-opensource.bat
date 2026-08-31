@echo off
setlocal EnableExtensions
REM 与后端 tools/sync-opensource 对称：把非院点前端同步到 openhis-itai-pro\openhis-pro-ui
REM 改版本示例：sync-to-opensource.bat -Force -NewVersion 2.0.6
REM 前后端一起：请用 whale-sever\tools\sync-opensource\sync-to-opensource.bat
cd /d "%~dp0..\.."

echo.
echo [sync-to-opensource] openhis-pro-ui -^> ..\openhis-itai-pro\openhis-pro-ui
echo.

if "%~1"=="" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\opensource\export-opensource.ps1" -Force
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\opensource\export-opensource.ps1" %*
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
