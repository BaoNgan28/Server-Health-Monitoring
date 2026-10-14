@echo off
rem ===========================================================================
rem  Security test: send a valid packet, then send the very same packet again.
rem  Group 10 - Server Health Monitoring System
rem
rem  Expected on the monitoring client:
rem    - the first copy is accepted
rem    - every later copy is rejected as SECURITY_REPLAY_PACKET
rem    - a genuinely newer packet is still accepted afterwards
rem ===========================================================================
setlocal

cd /d "%~dp0.."

set "JAR=simulator-app\target\simulator-app-1.0.0-all.jar"
if not exist "%JAR%" (
    echo [ERROR] %JAR% not found. Run scripts\build.bat first.
    pause
    exit /b 1
)

java -Dmonitoring.home="%CD%" -jar "%JAR%" replay --host=127.0.0.1 --port=6000 --agent=AGENT-01 --secret=GROUP10_DEMO_SECRET_01
pause
