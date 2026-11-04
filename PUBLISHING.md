# Publishing DocComparator on Maven Central

The licence the program is published under, and how it reaches Maven Central as `org.jtools:doccomparator`, the
same way as the other jtools projects (XsdViewer). Checked against the tree at version 1.5 (4 November 2026).
**Not published yet.**

## 1. The licence

Apache License 2.0, like the other jtools projects, copyright jtools.org:

- `LICENSE` at the root, the licence's full text; `NOTICE` beside it, the third-party terms: FlatLaf (Apache License
  2.0), whose classes the distributions' `doccomparator.jar` holds, and the Eclipse Temurin runtime (GPLv2 with the
  Classpath Exception) the distributions bundle.
- `<licenses>`, `<organization>` and `<developers>` in `pom.xml`, which Maven Central requires and shows.
- The Apache header at the top of every Java source, written by `license-maven-plugin` as the sources are built
  (`update-file-header`, phase `process-sources`): a new source gets it at the next build.
- The "Licence" section of `README.md`.
- Each distribution carries `LICENSE` and `NOTICE` at its root, FlatLaf's licence in `licenses/`, and the runtime's
  own in `jre/legal/`; `scripts/package.sh` checks they are there.

## 2. Maven Central

`org.jtools:doccomparator` is the program's own jar: its classes, FlatLaf a dependency of it (the distributions'
`doccomparator.jar` holds FlatLaf's classes; this one does not), with its sources, its javadoc, and its pom, the
version written out (`flatten-maven-plugin`: the pom's `${revision}` never reaches Central). The distributions, with
their JREs, are never uploaded: they are not attached to the build. **This is not part of a release**: a version
reaches Central only when it is asked for on its own — what Central publishes can never be replaced or withdrawn.

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

Then, for a version: `mvn -Ppublish clean deploy` runs the tests, builds the jar, its sources and its javadoc, signs
them and uploads the bundle to the portal, where it is validated. Nothing is released until **Publish** is pressed
there (`autoPublish` is off), so a mistake costs nothing. The version must not be a SNAPSHOT.
`mvn -Ppublish verify -Dgpg.skip` builds the same without signing nor uploading, to see in `target/` what would be
published.

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
