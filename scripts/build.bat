@echo off
rem
rem Build the modules (core, ui, the program, the examples) and doccomparator-app\target\doccomparator.jar, the
rem program (compile + tests).
rem
rem   scripts\build.bat
rem   scripts\build.bat -DskipTests        extra arguments are passed to mvn
rem
cd /d "%~dp0.."
rem (the project root)
call "%~dp0externals.bat" || exit /b 1
rem (setup done? it is offered otherwise; the externals first)
call mvn clean package %*
exit /b %errorlevel%
