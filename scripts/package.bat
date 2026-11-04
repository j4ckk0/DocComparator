@echo off
rem
rem Build the self-contained distributions (jar + bundled JRE + launchers; DocComparator.exe without a console), into releases\:
rem   releases\doccomparator-<version>-windows.zip, releases\doccomparator-<version>-linux.tar.gz
rem
rem   scripts\package.bat
rem   scripts\package.bat -DskipTests      extra arguments are passed to mvn
rem
rem The JRE archives come from external\ (scripts\setup.bat deploys them; scripts\externals.bat checks they are there).
rem The releases of the previous versions are removed once the new one is built: releases\ holds the release
rem of the version pom.xml names, and whatever else you put there is left alone.
rem
cd /d "%~dp0.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
rem (setup done? it is offered otherwise; the externals first)
call :running || exit /b 1
rem (DocComparator running from the jar the build replaces: the build would fail deleting it)

call mvn clean package -Pdist %* || exit /b 1

rem The releases of the previous versions go, now that the new one is there (only ours, named after another version).
for /f "delims=" %%l in ('findstr /r /c:"<revision>.*</revision>" pom.xml') do if not defined VERSION_LINE set "VERSION_LINE=%%l"
set "VERSION=%VERSION_LINE:*<revision>=%"
set "VERSION=%VERSION:</revision>=%"
for %%a in (releases\doccomparator-*-windows.zip releases\doccomparator-*-linux.tar.gz) do call :previous "%%a"

rem What each archive must carry: a distribution runs with nothing installed.
set "WINDOWS=releases\doccomparator-%VERSION%-windows.zip"
set "LINUX=releases\doccomparator-%VERSION%-linux.tar.gz"
for %%f in (doccomparator.jar DocComparator.exe doccomparator.bat README.md licenses/FlatLaf-LICENSE.txt jre/bin/java.exe) do call :holds "%WINDOWS%" %%f || exit /b 1
for %%f in (doccomparator.jar doccomparator.sh README.md licenses/FlatLaf-LICENSE.txt jre/bin/java) do call :holds "%LINUX%" %%f || exit /b 1
echo == releases\ checked: %WINDOWS%, %LINUX%
exit /b 0

rem ---- DocComparator running from the jar? the build would fail deleting it, saying nothing of the cause
:running
tasklist /fi "imagename eq java.exe" /fo csv /nh 2>nul | findstr /i "java.exe" >nul || exit /b 0
wmic process where "name='java.exe'" get commandline 2>nul | findstr /i "doccomparator.jar" >nul || exit /b 0
echo DocComparator is running: exit it ^(File ^> Exit^) before packaging, or it holds the jar the build replaces. >&2
exit /b 1

rem ---- an archive must hold a file
:holds
tar -tf "%~1" | findstr /e /c:"/%~2" >nul || ( echo %~1: no %~2 >&2 & exit /b 1 )
exit /b 0

rem ---- an archive of another version: removed
:previous
echo %~nx1 | findstr /c:"-%VERSION%-" >nul && exit /b 0
del /q "%~1"
echo == %~1 removed ^(previous release^)
exit /b 0
