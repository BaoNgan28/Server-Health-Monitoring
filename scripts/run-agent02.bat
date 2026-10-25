@echo off
rem ===========================================================================
rem  Starts Agent 02 using config\agent02.properties.
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

echo Starting AGENT-02 ...
echo.

java -Dmonitoring.home="%CD%" -jar "%JAR%" --config="config\agent02.properties"
set "RESULT=%ERRORLEVEL%"

if %RESULT% NEQ 0 (
    echo.
    echo AGENT-02 exited with code %RESULT%.
    pause
)
exit /b %RESULT%
