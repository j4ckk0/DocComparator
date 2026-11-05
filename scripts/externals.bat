@echo off
rem
rem Called by the scripts, from the project root: makes sure scripts\setup.bat has been run — everything it
rem deploys is there: external\jdk and maven — offering to run it when
rem something is missing (a prompt with a 30 s timeout, No by default: the CI does not hang). Then puts the
rem externals in front: external\jdk as JAVA_HOME, its java.exe and Maven's mvn.cmd first in PATH. No setlocal:
rem the caller keeps what is set here. Only generic names: the versions are externals.conf's business.
rem
rem Sets JAR, the jar Maven builds.
rem
set "EXT=%CD%\external"
call :missing
if defined MISSING (
  echo external\ is not set up: missing%MISSING% ^(scripts\setup.bat deploys them, see README: Externals^) >&2
  choice /c YN /n /t 30 /d N /m "Run scripts\setup.bat now? [Y/N] "
  if errorlevel 2 exit /b 1
  call "%~dp0setup.bat" || exit /b 1
  call :missing
  if defined MISSING (
    echo still missing%MISSING% >&2
    exit /b 1
  )
)
set "JAVA_HOME=%EXT%\jdk"
set "PATH=%EXT%\jdk\bin;%EXT%\maven\bin;%PATH%"
set "JAR=doccomparator-app\target\doccomparator.jar"
exit /b 0

rem ---- what setup deploys and is not there, in MISSING (undefined when all is there)
:missing
set "MISSING="
if not exist "%EXT%\jdk\bin\java.exe" set "MISSING=%MISSING% jdk\"
if not exist "%EXT%\maven\bin\mvn.cmd" set "MISSING=%MISSING% maven\"
exit /b 0
