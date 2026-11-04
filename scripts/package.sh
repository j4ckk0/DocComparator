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

# Java's AOT cache in the Linux distribution, doccomparator.aot, for a faster start: made by running the program once,
# as it ships (its launcher, its JRE, its flags), on the two sample procedures, then packed with it and checked as
# unpacked elsewhere. With no display, xvfb-run gives one. Should that fail, the archive goes without the cache: the
# program starts slower, nothing more. The Windows distribution has none: its cache would have to be made on Windows.
aot_cache() {   # the Linux archive
  local archive=$1 top=doccomparator-$version train=target/aot-training check=target/aot-check
  local run=()
  if [ -z "${DISPLAY:-}" ]; then
    if ! command -v xvfb-run >/dev/null; then
      echo "== WARNING: no display and no xvfb-run: $archive goes without its AOT cache" >&2
      return 0
    fi
    run=(xvfb-run -a)
  fi
  rm -rf "${train:?}" "${check:?}"
  mkdir -p "$train" "$check"
  tar -xzf "$archive" -C "$train"
  local dist=$PWD/$train/$top
  if ! JAVA_TOOL_OPTIONS="-XX:AOTCacheOutput=$dist/doccomparator.aot -D$training=true" timeout 300 \
      ${run[@]+"${run[@]}"} "$dist/doccomparator.sh" \
      "$PWD/samples/procedure-revA.docx" "$PWD/samples/procedure-revB.docx" \
      > "$train.log" 2>&1 || [ ! -s "$dist/doccomparator.aot" ]; then
    tail -5 "$train.log" >&2
    echo "== WARNING: the training run failed ($train.log): $archive goes without its AOT cache" >&2
    return 0
  fi
  tar -czf "$archive.part" -C "$train" "$top"
  # unpacked under another name, the cache must be used: AOTMode=on fails rather than pass it over
  tar -xzf "$archive.part" -C "$check"
  mv "$check/$top" "$check/moved"
  if ! timeout 120 ${run[@]+"${run[@]}"} "$check/moved/jre/bin/java" -XX:-UsePerfData -XX:+UseCompactObjectHeaders \
      -XX:AOTMode=on -XX:AOTCache="$check/moved/doccomparator.aot" -D$training=true \
      -jar "$check/moved/doccomparator.jar" \
      > "$check.log" 2>&1; then
    rm -f "$archive.part"
    tail -5 "$check.log" >&2
    echo "== WARNING: the AOT cache is not used once unpacked ($check.log): $archive goes without it" >&2
    return 0
  fi
  mv -f "$archive.part" "$archive"
  echo "== $archive: AOT cache added ($(du -h "$dist/doccomparator.aot" | cut -f1))"
}
training=doccomparator.training   # Launcher.TRAINING

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
aot_cache "$linux"
check "$windows" "$(unzip -Z1 "$windows")" doccomparator.jar DocComparator.exe doccomparator.bat README.md LICENSE NOTICE licenses/FlatLaf-LICENSE.txt jre/bin/java.exe
check "$linux" "$(tar tzf "$linux")" doccomparator.jar doccomparator.sh README.md LICENSE NOTICE licenses/FlatLaf-LICENSE.txt jre/bin/java
echo "== releases/ checked: $windows, $linux"
