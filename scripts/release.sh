#!/usr/bin/env bash
#
# Publish a version on GitHub: creates the Release from the tag and attaches the archives of releases/.
#
#   scripts/release.sh <version>                           # e.g. scripts/release.sh 1.6: the notes are CHANGELOG.md's section
#   scripts/release.sh <version> <whats-new.md>            # the notes from a file instead
#   scripts/release.sh --draft <version> [<whats-new.md>]  # a draft, to check on GitHub before publishing
#   scripts/release.sh --dry-run <version> [<whats-new.md>]  # prints the notes, calls nothing
#   scripts/release.sh --body <version> [<whats-new.md>]     # prints the notes alone (the release workflow uses them)
#
# Before: bump the version in pom.xml (<revision> and <windowsVersion>), write the version's section in
# CHANGELOG.md, commit, tag vX.Y, push the tag, run scripts/package.sh (see PUBLISHING.md, section 3).
# The notes hold the "What's new" part only (Markdown, a bullet list); the script adds the intro, the
# downloads table and the SHA-256 checksums. The release workflow on GitHub does the same on a pushed
# tag: this script is the way by hand.
#
# Token: $GITHUB_TOKEN, else ~/.config/github/doccomparator-release-token (one line, mode 600) — a
# fine-grained token for this repository only, with Contents: read and write. Never echoed.
#
set -euo pipefail
cd "$(dirname "$(readlink -f "$0")")/.."

REPO=j4ckk0/DocComparator
TOKEN_FILE=$HOME/.config/github/doccomparator-release-token
draft=false; dry=false; body_only=false
while [ $# -gt 0 ]; do
  case "$1" in
    --draft) draft=true; shift ;;
    --dry-run) dry=true; shift ;;
    --body) body_only=true; dry=true; shift ;;
    -*) echo "unknown option $1" >&2; exit 2 ;;
    *) break ;;
  esac
done
[ $# -eq 1 ] || [ $# -eq 2 ] || { sed -n '3,17p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
version=$1; tag=v$version
if [ $# -eq 2 ]; then
  notes=$2
  [ -r "$notes" ] || { echo "notes file not found: $notes" >&2; exit 1; }
else
  # the version's section of CHANGELOG.md, its heading replaced by "What's new"
  notes=$(mktemp)
  trap 'rm -f "$notes"' EXIT
  scripts/changelog-section.py "$version" > "$notes" || exit 1
fi
for tool in curl python3 sha256sum git; do command -v "$tool" >/dev/null || { echo "$tool not found in PATH" >&2; exit 1; }; done

# What is attached: the two archives of scripts/package.sh, for this version.
zip=doccomparator-$version-windows.zip; tgz=doccomparator-$version-linux.tar.gz
for f in "$zip" "$tgz"; do
  [ -f "releases/$f" ] || { echo "releases/$f missing - run scripts/package.sh after the version bump" >&2; exit 1; }
done
grep -q "<revision>$version</revision>" pom.xml || { echo "pom.xml is not at version $version" >&2; exit 1; }
git rev-parse -q --verify "refs/tags/$tag" >/dev/null || { echo "tag $tag does not exist - git tag -a $tag -m \"$version\"" >&2; exit 1; }
if ! $dry && ! git ls-remote --exit-code --tags origin "refs/tags/$tag" >/dev/null 2>&1; then
  echo "tag $tag is not on GitHub - git push origin $tag" >&2; exit 1
fi

# The bundled runtime's version, from the JRE archive externals.conf names (…_25.0.5_8.tar.gz → 25.0.5).
jre_version=$(sed -n 's/^JRE_LINUX=.*_\([0-9][0-9.]*\)_[0-9]*\.tar\.gz$/\1/p' externals.conf)
[ -n "$jre_version" ] || jre_version=25

body=$(
  echo "DocComparator $version — compare two Word documents (\`.docx\`) side by side: paragraphs paired even when moved, their differences marked word by word, tables row by row and column by column, each kind of layout on its own switch. Self-contained, and it never reaches the network."
  echo
  cat "$notes"
  echo
  echo "## Downloads"
  echo
  echo "| File | For |"
  echo "|---|---|"
  echo "| \`$zip\` | Windows x64, no Java needed — unzip anywhere and start \`doccomparator.bat\` (the signed \`javaw.exe\`) or \`DocComparator.exe\` |"
  echo "| \`$tgz\` | Linux x64, no Java needed — untar anywhere and run \`doccomparator.sh\` |"
  echo
  echo "Each archive bundles an Eclipse Temurin $jre_version runtime, redistributed under the GPLv2 with Classpath Exception (notices in \`jre/legal\`), and FlatLaf (Apache 2.0, in \`licenses/\`). DocComparator itself is Apache 2.0."
  echo
  echo "## Checksums (SHA-256)"
  echo
  echo '```'
  (cd releases && sha256sum "$zip" "$tgz")
  echo '```'
)

if $body_only; then echo "$body"; exit 0; fi
if $dry; then echo "$body"; echo; echo "(dry run: release $tag not created)"; exit 0; fi

if [ -z "${GITHUB_TOKEN:-}" ] && [ -r "$TOKEN_FILE" ]; then GITHUB_TOKEN=$(tr -d '[:space:]' < "$TOKEN_FILE"); fi
: "${GITHUB_TOKEN:?no token: set GITHUB_TOKEN or write it to $TOKEN_FILE}"
auth=(-H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28")

json=$(python3 -c 'import json, sys
print(json.dumps({"tag_name": sys.argv[1], "name": "DocComparator " + sys.argv[2], "body": sys.stdin.read(),
                  "draft": sys.argv[3] == "true", "prerelease": False, "make_latest": "true"}))' "$tag" "$version" "$draft" <<<"$body")
resp=$(curl -sS "${auth[@]}" -X POST "https://api.github.com/repos/$REPO/releases" -d "$json")
id=$(python3 -c 'import json, sys
r = json.loads(sys.stdin.read())
if "id" not in r: sys.exit("release not created: " + r.get("message", str(r)))
print(r["id"])' <<<"$resp")
echo "release $tag created (id $id$($draft && echo ', draft'))"

# An upload of a large archive sometimes answers nothing (the connection dropped): tried again, a few times.
for f in "$zip" "$tgz"; do
  case $f in *.zip) type=application/zip ;; *) type=application/gzip ;; esac
  for attempt in 1 2 3; do
    if curl -sS "${auth[@]}" -H "Content-Type: $type" --data-binary @"releases/$f" \
        "https://uploads.github.com/repos/$REPO/releases/$id/assets?name=$f" \
        | python3 -c 'import json, sys
try:
    r = json.load(sys.stdin)
except ValueError:
    sys.exit("no answer")
if r.get("state") != "uploaded": sys.exit("upload failed: " + r.get("message", str(r)))
print("uploaded", r["name"], r["size"], "bytes")'; then break; fi
    [ "$attempt" -lt 3 ] || { echo "$f: giving up - attach it by hand on the release page" >&2; exit 1; }
    echo "$f: upload attempt $attempt failed, retrying" >&2
    # a half-uploaded asset of that name would block the retry: remove it
    curl -sS "${auth[@]}" "https://api.github.com/repos/$REPO/releases/$id/assets" | python3 -c 'import json, sys
for a in json.load(sys.stdin):
    if a["name"] == sys.argv[1]: print(a["id"])' "$f" | while read -r aid; do
      curl -sS "${auth[@]}" -X DELETE "https://api.github.com/repos/$REPO/releases/assets/$aid" >/dev/null
    done
  done
done
echo "https://github.com/$REPO/releases/tag/$tag"
