@echo off
rem Starts DocComparator with the bundled JRE, without a console. Arguments are passed to the program.
rem It runs javaw.exe, signed by the Eclipse Foundation: no executable of ours, unsigned, that a security policy
rem might refuse (DocComparator.exe does the same, and is not signed).
rem -XX:-UsePerfData: the JVM's performance counters stay in memory, not in a file of %TEMP%;
rem -XX:+UseCompactObjectHeaders: smaller objects, less memory for a document.
start "" "%~dp0jre\bin\javaw.exe" -XX:-UsePerfData -XX:+UseCompactObjectHeaders -jar "%~dp0doccomparator.jar" %*
