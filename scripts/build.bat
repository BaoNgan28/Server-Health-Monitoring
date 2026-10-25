@echo off
rem ===========================================================================
rem  Builds every module and produces the executable jars.
rem  Group 10 - Server Health Monitoring System
rem ===========================================================================
setlocal

rem Move to the project root, which is the folder above this one. The %~dp0
rem form keeps working when the path contains spaces, as "F:\Project LTM" does.
cd /d "%~dp0.."

call "%~dp0find-maven.bat"
if %ERRORLEVEL% NEQ 0 (
    pause
    exit /b 1
)

echo ============================================================
echo  Building Server Health Monitoring System
echo  Project root: %CD%
echo  Maven       : %MVN_CMD%
echo ============================================================
echo.

call "%MVN_CMD%" clean package
set "BUILD_RESULT=%ERRORLEVEL%"

echo.
if %BUILD_RESULT% EQU 0 (
    echo ============================================================
    echo  BUILD SUCCESS
    echo ============================================================
    echo  monitor-app\target\monitor-app-1.0.0-all.jar
    echo  agent-app\target\agent-app-1.0.0-all.jar
    echo  simulator-app\target\simulator-app-1.0.0-all.jar
) else (
    echo ============================================================
    echo  BUILD FAILED with exit code %BUILD_RESULT%
    echo  Read the Maven output above for the first [ERROR] line.
    echo ============================================================
)

pause
rem Hand the real exit code back, so a caller can tell success from failure.
exit /b %BUILD_RESULT%
