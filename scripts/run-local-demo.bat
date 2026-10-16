@echo off
rem ===========================================================================
rem  Starts the whole local demo: one monitoring client and four agents, each
rem  in its own window, all on this machine.
rem  Group 10 - Server Health Monitoring System
rem
rem  On one machine the four agents need four different TCP ports (5001..5004).
rem  Across four machines they could all use 5000; see docs\MULTI_MACHINE_SETUP.md.
rem ===========================================================================
setlocal

cd /d "%~dp0.."

if not exist "monitor-app\target\monitor-app-1.0.0-all.jar" (
    echo [ERROR] The jars are missing. Run scripts\build.bat first.
    pause
    exit /b 1
)
if not exist "agent-app\target\agent-app-1.0.0-all.jar" (
    echo [ERROR] The jars are missing. Run scripts\build.bat first.
    pause
    exit /b 1
)

echo ============================================================
echo  Local demo: 1 monitoring client + 4 agents
echo ============================================================
echo.
echo Starting the monitoring client ...
start "Monitoring Client" cmd /c ""%~dp0run-monitor.bat""

rem Give the client a moment to bind UDP 6000 before the agents start sending.
timeout /t 6 /nobreak >nul

echo Starting AGENT-01 ...
start "AGENT-01" cmd /c ""%~dp0run-agent01.bat""
timeout /t 2 /nobreak >nul

echo Starting AGENT-02 ...
start "AGENT-02" cmd /c ""%~dp0run-agent02.bat""
timeout /t 2 /nobreak >nul

echo Starting AGENT-03 ...
start "AGENT-03" cmd /c ""%~dp0run-agent03.bat""
timeout /t 2 /nobreak >nul

echo Starting AGENT-04 ...
start "AGENT-04" cmd /c ""%~dp0run-agent04.bat""

echo.
echo ============================================================
echo  All five processes have been started.
echo.
echo  In the monitoring client, add the four agents on the
echo  Dashboard tab if they are not registered yet:
echo.
echo    AGENT-01  127.0.0.1  5001  secret GROUP10_DEMO_SECRET_01
echo    AGENT-02  127.0.0.1  5002  secret GROUP10_DEMO_SECRET_02
echo    AGENT-03  127.0.0.1  5003  secret GROUP10_DEMO_SECRET_03
echo    AGENT-04  127.0.0.1  5004  secret GROUP10_DEMO_SECRET_04
echo.
echo  Close each window to stop that process.
echo ============================================================
pause
