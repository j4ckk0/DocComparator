#!/usr/bin/env bash
#
# Build the self-contained distributions (jar + bundled JRE + launchers; DocComparator.exe without a console), into releases/:
#   releases/doccomparator-<version>-windows.zip, releases/doccomparator-<version>-linux.tar.gz
#
#   scripts/package.sh
#   scripts/package.sh -DskipTests      # extra arguments are passed to mvn
#
# The JRE archives come from external/ (scripts/setup.sh deploys them; scripts/externals.sh checks they are there).
# The releases of the previous versions are removed once the new one is built: releases/ holds the release
# of the version pom.xml names, and whatever else you put there is left alone.
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the project root
. scripts/externals.sh   # setup done? (offers it otherwise); the externals first
running_instance "packaging"
take_build_lock

mvn clean package -Pdist "$@"

# The releases of the previous versions go, now that the new one is there (only ours, named after another version).
version=$(sed -n 's:.*<revision>\(.*\)</revision>.*:\1:p' pom.xml | head -1)   # said once, in the pom
for archive in releases/doccomparator-*-windows.zip releases/doccomparator-*-linux.tar.gz; do
  [ -f "$archive" ] || continue
  case "$archive" in *"-$version-"*) continue ;; esac
  rm -f "${archive:?}"
  echo "== $archive removed (previous release)"
done

# What each archive must carry: a distribution runs with nothing installed. The entries go to grep whole, never
# through a pipe it may leave early: under pipefail the writer left behind would fail the check.
check() {   # archive, its entries, what it must hold
  local archive=$1 entries=$2 needed
  shift 2
  for needed in "$@"; do
    grep -q "^[^/]*/$needed\$" <<< "$entries" || { echo "$archive: no $needed" >&2; exit 1; }
  done
}
windows=releases/doccomparator-$version-windows.zip
linux=releases/doccomparator-$version-linux.tar.gz
check "$windows" "$(unzip -Z1 "$windows")" doccomparator.jar DocComparator.exe doccomparator.bat README.md jre/bin/java.exe
check "$linux" "$(tar tzf "$linux")" doccomparator.jar doccomparator.sh README.md jre/bin/java
echo "== releases/ checked: $windows, $linux"
