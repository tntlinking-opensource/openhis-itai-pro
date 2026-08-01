@echo off
setlocal EnableExtensions
cd /d "%~dp0"

echo.
echo [导出开源源码树] 默认同步到 ..\openhis-itai-pro\openhis-pro-ui（不含 projects/ 院点代码）
echo.

if "%~1"=="" (
    call "%~dp0export-opensource.bat"
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0export-opensource.ps1" %*
)

exit /b %ERRORLEVEL%
