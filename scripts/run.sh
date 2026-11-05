#!/usr/bin/env bash
#
# Build (if needed) and run DocComparator.
#
#   scripts/run.sh
#   scripts/run.sh --rebuild                # force a rebuild first
#
# Any other argument is passed to the program.
#
set -euo pipefail

cd "$(dirname "$(readlink -f "$0")")/.."   # the project root
. scripts/externals.sh   # setup done? (offers it otherwise); the externals first
JAR=$jar   # the program's jar (scripts/externals.sh)

rebuild=false
args=()
for a in "$@"; do
  case "$a" in
    --rebuild) rebuild=true ;;
    *) args+=("$a") ;;
  esac
done

# Behind any build in progress, which leaves the jar this looks at; the lock goes before the program starts, as it runs for long.
take_build_lock

# Rebuild when asked, when the jar is missing, or when any source is newer than it.
if $rebuild || [ ! -f "$JAR" ] || [ -n "$(find pom.xml doccomparator-*/pom.xml doccomparator-*/src -newer "$JAR" -print -quit)" ]; then
  echo "== building $JAR"
  mvn -q clean package   # clean: Maven leaves in target/ what the sources no longer have (a removed resource would still be shipped)
fi

# Run a copy of the jar, never the jar itself: a build rewrites $JAR in place, and a program still running from it
# would then read garbage out of its own jar (ZipException: invalid LOC header).
# The copy is written under a name of its own and renamed into place, a new file, so that a copy an earlier run is still
# reading stays whole; a clean build may delete that copy, but a running program keeps the file it has open.
RUN_JAR="$(dirname "$JAR")/doccomparator-run.jar"
cp "$JAR" "$RUN_JAR.$$"
mv -f "$RUN_JAR.$$" "$RUN_JAR"

release_build_lock
exec java -XX:-UsePerfData -XX:+UseCompactObjectHeaders -jar "$RUN_JAR" ${args[@]+"${args[@]}"}
