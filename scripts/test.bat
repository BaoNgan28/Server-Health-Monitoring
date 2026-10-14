@echo off
rem ===========================================================================
rem  Runs every unit and integration test.
rem  Group 10 - Server Health Monitoring System
rem ===========================================================================
setlocal

cd /d "%~dp0.."

call "%~dp0find-maven.bat"
if %ERRORLEVEL% NEQ 0 (
    pause
    exit /b 1
)

echo ============================================================
echo  Running the test suite
echo  Project root: %CD%
echo ============================================================
echo.

call "%MVN_CMD%" clean test
set "TEST_RESULT=%ERRORLEVEL%"

echo.
if %TEST_RESULT% EQU 0 (
    echo ============================================================
    echo  ALL TESTS PASSED
    echo ============================================================
) else (
    echo ============================================================
    echo  TESTS FAILED with exit code %TEST_RESULT%
    echo  Per-test detail: ^<module^>\target\surefire-reports
    echo ============================================================
)

pause
exit /b %TEST_RESULT%
