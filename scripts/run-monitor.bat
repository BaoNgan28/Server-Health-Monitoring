@echo off
rem ===========================================================================
rem  Starts the Monitoring Client.
rem  Group 10 - Server Health Monitoring System
rem ===========================================================================
setlocal

cd /d "%~dp0.."

set "JAR=monitor-app\target\monitor-app-1.0.0-all.jar"
if not exist "%JAR%" (
    echo [ERROR] %JAR% not found.
    echo         Run scripts\build.bat first.
    pause
    exit /b 1
)

echo Starting the Monitoring Client ...
echo Project root: %CD%
echo.

rem -Dmonitoring.home pins the config, data and logs folders to the project
rem root, so the client works no matter where it was started from.
java -Dmonitoring.home="%CD%" -jar "%JAR%" --config="config\monitor.properties"
set "RESULT=%ERRORLEVEL%"

if %RESULT% NEQ 0 (
    echo.
    echo The monitoring client exited with code %RESULT%.
    pause
)
exit /b %RESULT%
