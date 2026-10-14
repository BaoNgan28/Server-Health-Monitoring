@echo off
rem ===========================================================================
rem  Starts Agent 03 using config\agent03.properties.
rem  Group 10 - Server Health Monitoring System
rem ===========================================================================
setlocal

cd /d "%~dp0.."

set "JAR=agent-app\target\agent-app-1.0.0-all.jar"
if not exist "%JAR%" (
    echo [ERROR] %JAR% not found.
    echo         Run scripts\build.bat first.
    pause
    exit /b 1
)

echo Starting AGENT-03 ...
echo.

java -Dmonitoring.home="%CD%" -jar "%JAR%" --config="config\agent03.properties"
set "RESULT=%ERRORLEVEL%"

if %RESULT% NEQ 0 (
    echo.
    echo AGENT-03 exited with code %RESULT%.
    pause
)
exit /b %RESULT%
