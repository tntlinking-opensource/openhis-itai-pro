@echo off
setlocal EnableExtensions
cd /d "%~dp0"

echo.
echo [导出开源源码树] 输出到 dist\openhis-pro-ui-opensource（不含 projects/ 院点代码）
echo.

if "%~1"=="" (
    call "%~dp0export-opensource.bat"
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0export-opensource.ps1" %*
)

exit /b %ERRORLEVEL%
