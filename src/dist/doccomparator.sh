#!/bin/sh
# Run DocComparator with the bundled JRE. Arguments are passed to the program.
dir=$(dirname "$(readlink -f "$0")")
# -XX:-UsePerfData: the JVM's performance counters stay in memory, not in a file of /tmp;
# -XX:+UseCompactObjectHeaders: smaller objects, less memory for a document
exec "$dir/jre/bin/java" -XX:-UsePerfData -XX:+UseCompactObjectHeaders -jar "$dir/doccomparator.jar" "$@"
