#!/usr/bin/env bash
#
# Deploys the project's externals into external/ (not under source control), from externals.conf:
#   external/jdk, external/maven                        extracted, their top-level directory stripped
#   external/jre-linux.tar.gz, external/jre-windows.zip  the JRE archives the distributions bundle
#   external/downloads/                                 the archives as downloaded (kept)
#
#   scripts/setup.sh            deploys what is missing
#   scripts/setup.sh --force    downloads and deploys everything again
#
# MIRROR=<url> in externals.conf, or in the environment, fetches <MIRROR>/<file name> instead of the URLs;
# MIRROR_TOKEN (+ MIRROR_TOKEN_HEADER) or MIRROR_USER / MIRROR_PASSWORD authenticate there — from
# externals.local.conf (not under source control) or the environment rather than externals.conf.
# Needs curl, tar and unzip.
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."   # the project root

conf=externals.conf
local_conf=externals.local.conf
ext=external
dl=$ext/downloads
force=false
for arg in "$@"; do
  case "$arg" in
    --force) force=true ;;
    *) echo "unknown option: $arg (try --force)" >&2; exit 1 ;;
  esac
done

for tool in curl tar unzip; do
  command -v "$tool" >/dev/null || { echo "$tool not found: install it, then run setup again" >&2; exit 1; }
done

# externals.local.conf then externals.conf: KEY=VALUE lines; what is set already (the environment, then the
# local file) wins
read_conf() {
  while IFS='=' read -r key value; do
    case "$key" in ''|\#*) continue ;; esac
    if [ -z "${!key:-}" ]; then printf -v "$key" '%s' "$value"; fi
  done < "$1"
}
[ -f "$local_conf" ] && read_conf "$local_conf"
read_conf "$conf"

# The authentication of the mirror, as curl arguments (none for the public URLs).
auth=()
if [ -n "${MIRROR:-}" ]; then
  if [ -n "${MIRROR_TOKEN:-}" ]; then auth=(-H "${MIRROR_TOKEN_HEADER:-Authorization: Bearer} $MIRROR_TOKEN"); fi
  if [ -n "${MIRROR_USER:-}" ]; then auth+=(-u "$MIRROR_USER:${MIRROR_PASSWORD:-}"); fi
fi

# Downloads the archive of a URL into external/downloads/ unless it is there; prints its path.
fetch() {
  local url=$1 name=${1##*/}
  local src=$url
  if [ -n "${MIRROR:-}" ]; then src="${MIRROR%/}/$name"; fi
  if [ ! -s "$dl/$name" ] || $force; then
    echo "== downloading $name from ${src%/*}/" >&2
    # explicit failures: set -e is off inside a function called under || (the callers' "|| exit 1")
    curl -fL --progress-bar ${auth[@]+"${auth[@]}"} -o "$dl/$name.part" "$src" || { echo "download failed: $src" >&2; rm -f "${dl:?}/${name:?}.part"; return 1; }
    mv "$dl/$name.part" "$dl/$name" || return 1
  fi
  [ -s "$dl/$name" ] || { echo "no $dl/$name" >&2; return 1; }
  echo "$dl/$name"
}

# Extracts an archive into a directory, the archive's single top-level directory stripped.
deploy() {
  local file=$1 dir=$2
  rm -rf "${dir:?}"
  mkdir -p "$dir"
  case "$file" in
    *.tar.gz|*.tgz) tar -xzf "$file" -C "$dir" --strip-components 1 ;;
    *.zip)
      local tmp
      tmp=$(mktemp -d "$ext/unzip.XXXXXX")
      unzip -q "$file" -d "$tmp"
      local top
      top=$(find "$tmp" -mindepth 1 -maxdepth 1)
      if [ "$(printf '%s\n' "$top" | wc -l)" = 1 ] && [ -d "$top" ]; then (shopt -s dotglob; mv "$top"/* "$dir"/); else (shopt -s dotglob; mv "$tmp"/* "$dir"/); fi
      rm -rf "${tmp:?}" ;;
    *) echo "$file: not a .tar.gz or .zip" >&2; return 1 ;;
  esac
}

mkdir -p "$dl"

# the tools, under their generic names: present when their launcher is (a failed deployment leaves none)
for spec in "jdk JDK_LINUX bin/java" "maven MAVEN_LINUX bin/mvn"; do
  set -- $spec
  dir=$ext/$1
  if [ -x "$dir/$3" ] && ! $force; then echo "== $dir present"; continue; fi
  file=$(fetch "${!2}") || exit 1   # downloaded before the directory is touched
  deploy "$file" "$dir"
  echo "== $dir deployed"
done

# the JRE archives the distributions bundle, kept as archives under generic names
for spec in "jre-linux.tar.gz JRE_LINUX" "jre-windows.zip JRE_WINDOWS"; do
  set -- $spec
  target=$ext/$1
  if [ -s "$target" ] && ! $force; then echo "== $target present"; continue; fi
  file=$(fetch "${!2}") || exit 1
  cp "$file" "$target"
  echo "== $target deployed"
done

echo "== external/ ready:"
"$ext/jdk/bin/java" -version 2>&1 | head -1
JAVA_HOME="$PWD/$ext/jdk" "$ext/maven/bin/mvn" -v 2>/dev/null | head -1
