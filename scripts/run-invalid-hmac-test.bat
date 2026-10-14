@echo off
rem ===========================================================================
rem  Security test: send forged packets that the monitoring client must reject.
rem  Group 10 - Server Health Monitoring System
rem
rem  Expected on the monitoring client:
rem    - the dashboard keeps showing the real CPU figure, not 100%
rem    - no new row is written to metric_raw for these packets
rem    - no CPU_HIGH alert is raised by them
rem    - SECURITY_INVALID_HMAC appears in logs\monitor-0.log and in the Alerts tab
rem ===========================================================================
setlocal

cd /d "%~dp0.."

set "JAR=simulator-app\target\simulator-app-1.0.0-all.jar"
if not exist "%JAR%" (
    echo [ERROR] %JAR% not found. Run scripts\build.bat first.
    pause
    exit /b 1
)

java -Dmonitoring.home="%CD%" -jar "%JAR%" invalid-hmac --host=127.0.0.1 --port=6000 --agent=AGENT-01
pause
