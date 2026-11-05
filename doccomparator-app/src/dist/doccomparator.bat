@echo off
rem Starts DocComparator with its runtime, without a console. Arguments are passed to the program.
rem It runs javaw.exe, signed by the Eclipse Foundation: no executable of ours, unsigned, that a security policy
rem might refuse (DocComparator.exe does the same, and is not signed).
rem -XX:-UsePerfData: the JVM's performance counters stay in memory, not in a file of %TEMP%;
rem -XX:+UseCompactObjectHeaders: smaller objects, less memory for a document.
rem doccomparator.aot, Java's AOT cache of the program (pom.xml makes it), for a faster start; read only, and
rem silently passed over should it not match the runtime. No ( ) block: a path's ")" would close it.
if not exist "%~dp0doccomparator.aot" goto without
start "" "%~dp0jre\bin\javaw.exe" -XX:-UsePerfData -XX:+UseCompactObjectHeaders "-XX:AOTCache=%~dp0doccomparator.aot" ^
  -Xlog:aot=off -Xlog:cds=off -jar "%~dp0doccomparator.jar" %*
exit /b 0
:without
start "" "%~dp0jre\bin\javaw.exe" -XX:-UsePerfData -XX:+UseCompactObjectHeaders -jar "%~dp0doccomparator.jar" %*
