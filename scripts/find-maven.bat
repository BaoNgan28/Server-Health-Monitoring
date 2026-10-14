@echo off
rem ===========================================================================
rem  Locates a Maven launcher and returns it in the MVN_CMD variable.
rem  Maven is often not on PATH on a machine that only has NetBeans installed,
rem  so the bundled copy is used as a fallback.
rem  Called by build.bat and test.bat; not meant to be run on its own.
rem ===========================================================================

set "MVN_CMD="

where mvn >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    set "MVN_CMD=mvn"
    goto :found
)

for %%D in (
    "C:\Program Files\NetBeans-22\netbeans\java\maven\bin\mvn.cmd"
    "C:\Program Files\NetBeans-21\netbeans\java\maven\bin\mvn.cmd"
    "C:\Program Files\NetBeans-20\netbeans\java\maven\bin\mvn.cmd"
    "C:\Program Files\Apache NetBeans\netbeans\java\maven\bin\mvn.cmd"
) do (
    if exist %%D (
        set "MVN_CMD=%%~D"
        goto :found
    )
)

echo [ERROR] Maven could not be found.
echo         Install Apache Maven and put it on PATH, or edit scripts\find-maven.bat
echo         to point at the Maven bundled with your NetBeans installation.
exit /b 1

:found
exit /b 0
