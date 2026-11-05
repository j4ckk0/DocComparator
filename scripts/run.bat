@echo off
rem
rem Build (if needed) and run DocComparator on Windows.
rem
rem   scripts\run.bat
rem   scripts\run.bat --rebuild                force a rebuild first
rem
rem Any other argument is passed to the program.
rem
setlocal EnableDelayedExpansion

cd /d "%~dp0.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
rem (setup done? it is offered otherwise; the externals first; sets JAR)

set "REBUILD=0"
set "ARGS="
for %%a in (%*) do (
  if /i "%%~a"=="--rebuild" (
    set "REBUILD=1"
  ) else (
    set "ARGS=!ARGS! %%a"
  )
)

rem Rebuild when asked, when the jar is missing, or when any source is newer than it.
set "NEED_BUILD=%REBUILD%"
if not exist "%JAR%" set "NEED_BUILD=1"
if "%NEED_BUILD%"=="0" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$jar = (Get-Item '%JAR%').LastWriteTimeUtc;" ^
    "$src = @(Get-Item pom.xml, doccomparator-*\pom.xml) + @(Get-ChildItem doccomparator-*\src -Recurse -File);" ^
    "if ($src | Where-Object { $_.LastWriteTimeUtc -gt $jar }) { exit 1 } else { exit 0 }"
  if errorlevel 1 set "NEED_BUILD=1"
)

if "%NEED_BUILD%"=="1" (
  echo == building %JAR%
  call mvn -q clean package || exit /b 1
)

java -XX:-UsePerfData -XX:+UseCompactObjectHeaders -jar "%JAR%" %ARGS%
exit /b %errorlevel%
