#!/usr/bin/env bash
#
# Build the self-contained Linux distribution (jar + runtime linked with the program + launcher + AOT cache), into
# releases/doccomparator-<version>-linux.tar.gz. The Windows one is built on Windows (scripts\package.bat): jlink
# links only for the system it runs on.
#
#   scripts/package.sh
#   scripts/package.sh -DskipTests      # extra arguments are passed to mvn
#
# The JDK comes from external/ (scripts/setup.sh deploys it; scripts/externals.sh checks it is there). The AOT cache
# is made by running the program once (doccomparator-app/pom.xml, profile dist): with no display, xvfb-run gives one
# when it is there.
# The releases of the previous versions are removed once the new one is built: releases/ holds the release
# of the version pom.xml names, and whatever else you put there is left alone.
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the project root
. scripts/externals.sh   # setup done? (offers it otherwise); the externals first
running_instance "packaging"
take_build_lock

run=()
if [ -z "${DISPLAY:-}" ] && command -v xvfb-run >/dev/null; then run=(xvfb-run -a); fi
${run[@]+"${run[@]}"} mvn clean package -Pdist "$@"

# The releases of the previous versions go, now that the new one is there (only ours, named after another version).
version=$(sed -n 's:.*<revision>\(.*\)</revision>.*:\1:p' pom.xml | head -1)   # said once, in the pom
for archive in releases/doccomparator-*-windows.zip releases/doccomparator-*-linux.tar.gz; do
  [ -f "$archive" ] || continue
  case "$archive" in *"-$version-"*) continue ;; esac
  rm -f "${archive:?}"
  echo "== $archive removed (previous release)"
done

# The AOT cache, used once the archive is unpacked elsewhere and started by its launcher: the program's classes then
# come from the cache ("shared objects file"), not from the runtime image. Its absence or refusal is said, not fatal:
# the program starts slower, nothing more.
check_aot() {   # the Linux archive
  local archive=$1 unpacked=target/aot-check log=$PWD/target/aot-check.log
  rm -rf "${unpacked:?}" "$log"
  mkdir -p "$unpacked"
  tar -xzf "$archive" -C "$unpacked"
  mv "$unpacked/doccomparator-$version" "$unpacked/moved"
  if [ ! -f "$unpacked/moved/doccomparator.aot" ]; then
    echo "== WARNING: $archive goes without its AOT cache (see the build's output)" >&2
    return 0
  fi
  JAVA_TOOL_OPTIONS="-Ddoccomparator.training=true -Xlog:class+load=info:file=$log" timeout 120 \
    ${run[@]+"${run[@]}"} "$unpacked/moved/doccomparator.sh" > /dev/null 2>&1 || true
  if grep -q 'org.jtools.doccomparator.Launcher source: shared objects file' "$log" 2>/dev/null; then
    echo "== $archive: its AOT cache is used"
  else
    echo "== WARNING: $archive: its AOT cache is not used once unpacked ($log)" >&2
  fi
}

# What each archive must carry: a distribution runs with nothing installed. The entries go to grep whole, never
# through a pipe it may leave early: under pipefail the writer left behind would fail the check.
check() {   # archive, its entries, what it must hold
  local archive=$1 entries=$2 needed
  shift 2
  for needed in "$@"; do
    grep -q "^[^/]*/$needed\$" <<< "$entries" || { echo "$archive: no $needed" >&2; exit 1; }
  done
}
linux=releases/doccomparator-$version-linux.tar.gz
check "$linux" "$(tar tzf "$linux")" doccomparator.jar doccomparator.sh README.md LICENSE NOTICE \
  licenses/FlatLaf-LICENSE.txt jre/bin/java jre/legal/java.base/LICENSE
check_aot "$linux"
echo "== releases/ checked: $linux"
