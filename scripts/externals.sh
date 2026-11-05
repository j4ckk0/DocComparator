#!/usr/bin/env bash
#
# Sourced by the scripts, from the project root: makes sure scripts/setup.sh has been run — everything it
# deploys is there: external/jdk and maven — offering to run it when something is missing (at a terminal;
# elsewhere, the CI or a hook, it stops with the way out). Then puts the externals in front: external/jdk as
# JAVA_HOME, its java and Maven's mvn first in PATH. Only generic names: the versions are externals.conf's business.
#
# take_build_lock makes one build at a time wait for the other, as each starts by deleting what the other is using.
#
# Sets $jar, the jar Maven builds.
#
ext="$PWD/external"

# What setup deploys; empty when all is there.
missing_externals() {
  local m=
  [ -x "$ext/jdk/bin/java" ] || m="$m jdk/"
  [ -x "$ext/maven/bin/mvn" ] || m="$m maven/"
  echo "$m"
}

missing=$(missing_externals)
if [ -n "$missing" ]; then
  echo "external/ is not set up: missing$missing (scripts/setup.sh deploys them, see README: Externals)" >&2
  if [ -t 0 ] && [ -t 2 ]; then
    read -r -p "Run scripts/setup.sh now? [Y/n] " answer
    case "$answer" in ''|[Yy]*) scripts/setup.sh ;; *) exit 1 ;; esac
    missing=$(missing_externals)
    [ -z "$missing" ] || { echo "still missing$missing" >&2; exit 1; }
  else
    exit 1
  fi
fi

export JAVA_HOME="$ext/jdk"
export PATH="$ext/jdk/bin:$ext/maven/bin:$PATH"
jar=doccomparator-app/target/doccomparator.jar

# Refuses to go on while DocComparator runs from the jar a build is about to replace: Maven would otherwise fail
# deleting it, with an error saying nothing of the cause. $1 is what the caller is about to do.
running_instance() {
  local pids
  # anchored on the process's own java: a shell command line that merely names the jar is not an instance
  pids=$(pgrep -f '^[^[:space:]]*java[^[:space:]]* .*-jar[[:space:]].*doccomparator\.jar' || true)
  [ -n "$pids" ] || return 0
  echo "DocComparator is running (pid$( [ "$(echo "$pids" | wc -l)" -gt 1 ] && echo s) $(echo "$pids" | tr '\n' ' ')): exit it (File ▸ Exit) before ${1:-building}, or it holds the jar the build replaces." >&2
  exit 1
}

# Waits for any other build of this project, then holds the lock on descriptor 9 until the script ends, which the build
# tool it hands over to inherits; a process that dies gives it back by itself. Without flock, builds run unguarded.
take_build_lock() {
  command -v flock >/dev/null || return 0
  exec 9>"$ext/build.lock"
  flock -n 9 && return 0
  echo "== another build of DocComparator is running: waiting for it to end (Ctrl+C to give up)" >&2
  flock 9
}

# For a script that goes on past its build: what it starts next must not keep the others waiting.
release_build_lock() {
  exec 9>&-
}
