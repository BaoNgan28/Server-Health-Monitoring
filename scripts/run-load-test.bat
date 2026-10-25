@echo off
rem ===========================================================================
rem  Load test: drive 20 agent identities for 60 seconds.
rem  Group 10 - Server Health Monitoring System
rem
rem  The identities are not registered on the monitoring client, so every packet
rem  is refused as UNKNOWN_AGENT. That is the point: what is being checked is
rem  that the receiver keeps up, stays responsive and does not grow threads or
rem  memory while refusing a flood.
rem ===========================================================================
setlocal

cd /d "%~dp0.."

set "JAR=simulator-app\target\simulator-app-1.0.0-all.jar"
if not exist "%JAR%" (
    echo [ERROR] %JAR% not found. Run scripts\build.bat first.
    pause
    exit /b 1
)

java -Dmonitoring.home="%CD%" -jar "%JAR%" load --host=127.0.0.1 --port=6000 --agents=20 --duration=60
pause
