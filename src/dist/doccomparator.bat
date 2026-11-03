@echo off
rem Run DocComparator with the bundled JRE, in a console (its messages). Arguments are passed to the program.
rem DocComparator.exe starts the same program without a console.
"%~dp0jre\bin\java.exe" -jar "%~dp0doccomparator.jar" %*
