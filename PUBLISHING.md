# Publishing DocComparator: its licence, Maven Central, GitHub's releases

The licence the program is published under, and how it reaches Maven Central — the parent and three jars under
`org.jtools` —, the same way as the other jtools projects (XsdViewer). Checked against the tree at version 2.0
(5 November 2026). **Not published yet.**

## 1. The licence

Apache License 2.0, like the other jtools projects, copyright jtools.org:

- `LICENSE` at the root, the licence's full text; `NOTICE` beside it, the third-party terms: FlatLaf (Apache License
  2.0), whose classes the distributions hold, and the runtime linked from Eclipse Temurin (GPLv2 with the Classpath
  Exception) the distributions carry.
- `<licenses>`, `<organization>` and `<developers>` in `pom.xml`, which Maven Central requires and shows.
- The Apache header at the top of every Java source, written by `license-maven-plugin` as the sources are built
  (`update-file-header`, phase `process-sources`): a new source gets it at the next build.
- The "Licence" section of `README.md`.
- Each distribution carries `LICENSE` and `NOTICE` at its root, FlatLaf's licence in `licenses/`, and the runtime's
  own in `jre/legal/`; `scripts/package.sh` and `scripts\package.bat` check they are there.

## 2. Maven Central

What reaches Central, every one with its pom, the version written out (`flatten-maven-plugin`: the poms'
`${revision}` never reaches Central):

| Artifact | What it is | Files |
|---|---|---|
| `org.jtools:doccomparator-parent` | the parent pom: the version, the shared build, the coordinates | the pom |
| `org.jtools:doccomparator-core` | the comparison, no window: readers, comparison, its words, languages | jar, sources, javadoc; `-tests` jar: `TextsCheck`, a check of a jar's texts, and the English-in-tests extension |
| `org.jtools:doccomparator-ui` | the comparison view, a Swing component (`ComparatorPanel`) | jar, sources, javadoc |
| `org.jtools:doccomparator` | the program: its window, its looks (FlatLaf) | jar, sources, javadoc |

Each jar holds its own classes only, the jars below it and FlatLaf named by its pom: the distributions'
`doccomparator.jar`, which holds them all, is not this one (the profile `publish` turns its shading off). The
distributions, with their runtimes, are never uploaded: they are not attached to the build. **This is not part of a
release**: a version reaches Central only when it is asked for on its own — what Central publishes can never be
replaced or withdrawn.

What it takes, once — all already done for XsdViewer, and shared by every `org.jtools` artifact:

1. **The namespace `org.jtools`.** Maven Central hands out a groupId only to who owns the domain it reverses, and
   jtools.org is ours: verified on <https://central.sonatype.com> through a DNS TXT record on `jtools.org`. Once
   verified the namespace is ours for good: nothing to do again.
2. **The portal's user token** (*View Account* ▸ *Generate User Token*), kept as the server `central` in
   `~/.m2/settings.xml` — there already:

       <settings><servers><server>
         <id>central</id><username>…</username><password>…</password>
       </server></servers></settings>

3. **A GPG key**, its public part on a key server the portal reads — there already. `maven-gpg-plugin` signs with
   the default key, or `-Dgpg.keyname=<id>`. Central refuses an unsigned bundle.

Then, for a version, from the root: `mvn -Ppublish clean deploy` runs every module's tests, builds the jars, their
sources and their javadoc, signs them and uploads one bundle — the four artifacts — to the portal, where it is
validated. Nothing is released until **Publish** is pressed there (`autoPublish` is off), so a mistake costs nothing.
The version must not be a SNAPSHOT. `mvn -Ppublish verify -Dgpg.skip` builds the same without signing nor uploading,
to see in each module's `target/` what would be published (checked for 2.0: it builds, javadoc included).

Learnt with XsdViewer (5 September 2026), true here too:

- `gpg --send-keys` reports success here while the key never leaves: the key-server ports are filtered. Upload over
  HTTPS instead — `gpg --armor --export <id> > pub.asc` then
  `curl --data-urlencode "keytext@pub.asc" https://keyserver.ubuntu.com/pks/add`, which answers with the
  fingerprint it inserted.
- A failed build after `Uploaded bundle successfully` does not mean a failed upload: the plugin polls the portal
  afterwards, and an old one throws on a field the portal has since added. Ask the portal itself rather than
  trusting the exit code:

      curl -X POST -H "Authorization: Bearer $(printf '%s:%s' "$USER_TOKEN" "$PASSWORD" | base64 -w0)" \
        "https://central.sonatype.com/api/v1/publisher/status?id=<deploymentId>"

  `deploymentState: VALIDATED` means it is staged and waiting for **Publish**.

These builds use the network (plugins, the upload): the program does not.

## 3. A release on GitHub

What a user downloads: the two self-contained distributions, each with its Java runtime and built on its own system, on
the repository's Releases page, with the version's notes from `CHANGELOG.md` and the archives' SHA-256. A release is
public as soon as it is made: it is made when asked for, never as a side effect of a push.

1. Bump the version in the parent `pom.xml`: `<revision>` (`2.1`) and `<windowsVersion>` (`2.1.0.0`, the four parts
   launch4j wants).
2. Give `CHANGELOG.md` its section, `## 2.1 — <date>`: what the version brings, a bullet list, for a user.
3. Commit, tag and push the tag:

       git tag -a v2.1 -m "2.1"
       git push origin main v2.1

The tag starts `.github/workflows/release.yml` on GitHub: it checks that the tag, the pom and the changelog agree,
then on Linux and on Windows deploys the externals (`scripts/setup.sh`, `scripts\setup.bat`), builds and tests the
distribution (`scripts/package.sh`, `scripts\package.bat`) and checks its launchers use the AOT cache, then creates
the release with the two archives (`gh release create`, with the workflow's own token: no personal token is
involved). Every push of `main` also runs `.github/workflows/build.yml`: the build and the tests, without a screen.

Before a tag, the release workflow can be run by hand as a dry run (*Actions*, *release*, *Run workflow*, on `main`):
the two distributions of the version `pom.xml` names, built and checked on Linux and on Windows, are kept as the
run's artefacts, to download and try; nothing is published, and neither the tag nor the changelog is checked.

By hand, should the workflow be unavailable: `scripts/package.sh` on Linux and `scripts\package.bat` on Windows, the
Windows archive copied into `releases/` beside the Linux one, then `scripts/release.sh 2.1`, which creates the
release from the pushed tag and uploads the archives — with a fine-grained token for this repository only (*Contents:
read and write*), in `$GITHUB_TOKEN` or `~/.config/github/doccomparator-release-token`. `scripts/release.sh --dry-run
2.1` prints the notes and calls nothing.
