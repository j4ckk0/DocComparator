#!/bin/sh
# Run DocComparator with the bundled JRE. Arguments are passed to the program.
dir=$(dirname "$(readlink -f "$0")")
exec "$dir/jre/bin/java" -jar "$dir/doccomparator.jar" "$@"
