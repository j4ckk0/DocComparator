#!/bin/sh
# Run DocComparator with its runtime. Arguments are passed to the program.
dir=$(dirname "$(readlink -f "$0")")
# -XX:-UsePerfData: the JVM's performance counters stay in memory, not in a file of /tmp;
# -XX:+UseCompactObjectHeaders: smaller objects, less memory for a document
# doccomparator.aot, Java's AOT cache of the program (pom.xml makes it), for a faster start; read only, and
# silently passed over should it not match the runtime.
aot="$dir/doccomparator.aot"
if [ -f "$aot" ]; then
  exec "$dir/jre/bin/java" -XX:-UsePerfData -XX:+UseCompactObjectHeaders \
    -XX:AOTCache="$aot" -Xlog:aot=off -Xlog:cds=off -jar "$dir/doccomparator.jar" "$@"
fi
exec "$dir/jre/bin/java" -XX:-UsePerfData -XX:+UseCompactObjectHeaders -jar "$dir/doccomparator.jar" "$@"
