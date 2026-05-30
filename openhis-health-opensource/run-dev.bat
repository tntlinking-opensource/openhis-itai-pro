
@echo off

setlocal EnableExtensions

cd /d "%~dp0"

echo.

echo [run-dev] mvn spring-boot:run (profile: binary-framework)

echo Requires PostgreSQL at localhost:5432/whale

echo.

call mvn -Pbinary-framework -pl whale-health/health-app spring-boot:run -DskipTests

echo.

pause

exit /b %ERRORLEVEL%
