@echo off
rem
rem Deploys the project's externals into external\ (not under source control), from externals.conf:
rem   external\jdk, external\maven                         extracted, their top-level directory stripped
rem   external\jre-linux.tar.gz, external\jre-windows.zip  the JRE archives the distributions bundle
rem   external\downloads\                                  the archives as downloaded (kept)
rem
rem   scripts\setup.bat            deploys what is missing
rem   scripts\setup.bat --force    downloads and deploys everything again
rem
rem MIRROR=<url> in externals.conf, or in the environment, fetches <MIRROR>/<file name> instead of the URLs;
rem MIRROR_TOKEN (+ MIRROR_TOKEN_HEADER) or MIRROR_USER / MIRROR_PASSWORD authenticate there — from
rem externals.local.conf (not under source control) or the environment rather than externals.conf.
rem Needs curl and tar, both in Windows 10 (1803) and later.
rem
setlocal EnableDelayedExpansion
cd /d "%~dp0.."
rem (the project root)

set "CONF=externals.conf"
set "LOCAL_CONF=externals.local.conf"
set "EXT=external"
set "DL=%EXT%\downloads"
set "FORCE=0"
if /i "%~1"=="--force" set "FORCE=1"

for %%t in (curl tar) do where %%t >nul 2>nul || (
  echo %%t not found ^(Windows 10 1803 and later have it^): install it, then run setup again >&2
  exit /b 1
)

rem externals.local.conf then externals.conf: KEY=VALUE lines, # comments; what is set already (the environment,
rem then the local file) wins
if exist "%LOCAL_CONF%" for /f "usebackq eol=# tokens=1* delims==" %%a in ("%LOCAL_CONF%") do if not defined %%a set "%%a=%%b"
for /f "usebackq eol=# tokens=1* delims==" %%a in ("%CONF%") do if not defined %%a set "%%a=%%b"

rem the authentication of the mirror, as curl arguments (none for the public URLs)
set "AUTH="
if defined MIRROR (
  if defined MIRROR_TOKEN (
    if not defined MIRROR_TOKEN_HEADER set "MIRROR_TOKEN_HEADER=Authorization: Bearer"
    set "AUTH=-H "!MIRROR_TOKEN_HEADER! !MIRROR_TOKEN!""
  )
  if defined MIRROR_USER set "AUTH=!AUTH! -u "!MIRROR_USER!:!MIRROR_PASSWORD!""
)

if not exist "%DL%" mkdir "%DL%"

call :tool jdk JDK_WINDOWS bin\java.exe || exit /b 1
call :tool maven MAVEN_WINDOWS bin\mvn.cmd || exit /b 1
call :archive jre-linux.tar.gz JRE_LINUX || exit /b 1
call :archive jre-windows.zip JRE_WINDOWS || exit /b 1

echo == external\ ready:
"%EXT%\jdk\bin\java.exe" -version 2>&1 | findstr /i "version"
set "JAVA_HOME=%CD%\%EXT%\jdk"
call "%EXT%\maven\bin\mvn.cmd" -v 2>nul | findstr /i "Apache Maven"
exit /b 0

rem ---- a tool: extracted into external\<name>, the archive's top-level directory stripped; present when its
rem      launcher (%3) is — a failed deployment leaves none
:tool
set "DIR=%EXT%\%~1"
if exist "%DIR%\%~3" if "%FORCE%"=="0" (
  echo == %DIR% present
  exit /b 0
)
call :fetch %~2 || exit /b 1
if exist "%DIR%" rmdir /s /q "%DIR%"
mkdir "%DIR%"
tar -xf "!FILE!" -C "%DIR%" --strip-components 1 || exit /b 1
echo == %DIR% deployed
exit /b 0

rem ---- a JRE archive: copied as it is under its generic name
:archive
set "TARGET=%EXT%\%~1"
if exist "%TARGET%" if "%FORCE%"=="0" (
  echo == %TARGET% present
  exit /b 0
)
call :fetch %~2 || exit /b 1
copy /y "!FILE!" "%TARGET%" >nul || exit /b 1
echo == %TARGET% deployed
exit /b 0

rem ---- the archive of the URL held by the variable named %1, into external\downloads\ unless it is there; sets FILE
:fetch
set "URL=!%~1!"
for %%f in ("!URL!") do set "NAME=%%~nxf"
set "SRC=!URL!"
if defined MIRROR set "SRC=!MIRROR!/!NAME!"
set "FILE=%DL%\!NAME!"
if exist "!FILE!" if "%FORCE%"=="0" exit /b 0
echo == downloading !NAME! from !SRC!
curl -fL !AUTH! -o "!FILE!.part" "!SRC!" || exit /b 1
move /y "!FILE!.part" "!FILE!" >nul
exit /b 0
