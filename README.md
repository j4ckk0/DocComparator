# DocComparator

Compares two Word documents (`.docx`): two versions of one file, or two different documents, one of which took
paragraphs from the other and changed pieces of their sentences. The text comes first; the structure, the blanks, the
template, the headers and footers and the styles can each be compared or left out. Paragraphs that nearly match are
shown side by side, their differences marked within them. *(To come: the window is still empty.)*

DocComparator is a desktop program that runs on its own: each distribution carries its Java runtime, so nothing needs
installing where it is unpacked, and it **never reaches the network** (see "No network").

## No network

The program opens a window in its own process, and that is all: no server, no port, not even on `127.0.0.1`; no update
check, no browser, no mail client, no other program started.

**No name lookup either, not even the JDK's own.** On Linux, Swing's fonts look up the machine's name the first time
text is drawn, to name their cache file in `~/.java/fonts/`: a DNS query, which the search domain may carry beyond the
machine. `NoNetworkResolver` takes the place of Java's name service (through
`META-INF/services/java.net.spi.InetAddressResolverProvider`) and answers every lookup as an unknown host, which the JDK
expects: the font cache is then named after `localhost`. It covers every lookup Java would make, whoever makes it.

Two checks run with every build, so a change that breaks the rule does not build:

- **`NoNetworkTest`** reads every compiled class of the program and fails on any name of what the JDK reaches the network
  with: the packages `java.net`, `javax.net`, `jdk.net`, `sun.net`, `com.sun.net`, `java.rmi`, `javax.rmi`,
  `javax.naming` and `javax.management.remote`; `java.awt.Desktop`, whose `browse()` and `mail()` start other programs
  that do; `ProcessBuilder` and `Runtime.exec`, since another program would do what this one may not. It reads the
  class files, not the sources, so that a fully qualified name, a method reference or a class named for reflection is
  seen as well as an import. A second test checks that it does find each of these. `NoNetworkResolver` alone may name
  the resolver types it implements.
- **The enforcer rule of `pom.xml`** fails the build when a library would ship with the program: the test reads only the
  program's own classes. Adding one is a decision to take knowingly, checking what that library does, before lifting
  the rule for it.

What stays outside these checks:

- **The JRE.** The bundled Java runtime holds the JDK's networking code like any other; nothing in the program calls it.
  Traced on Linux (`strace`, 2026-11-04), the packaged program opens the display's local socket and nothing towards
  the network: the one TCP socket the JRE creates as its networking library loads, to learn whether IPv6 is there, is
  closed unused — no address, no packet.
- **`DocComparator.exe`**, should its `jre\` folder be removed, falls back to a message offering Java's download page.
  It stays out of the way while the distribution is whole.
- **The system.** Windows may check a program downloaded from the internet (SmartScreen) the first time it is opened;
  a distribution copied by other means is not marked so. A file dialog that lists the network drives the system has
  mapped reads them: something to keep in mind when DocComparator opens files.

The **build**, not the program, uses the network: `scripts/setup.sh` downloads the externals, and the first Maven build
downloads its plugins into `~/.m2`. After that, `mvn -o` builds offline.

## Build and run

Nothing to install: `scripts/setup.sh` deploys the JDK and Maven the scripts use (see "Externals").

```bash
scripts/setup.sh    # once: deploys the externals (JDK, Maven…) into external/
scripts/run.sh      # builds target/doccomparator.jar if needed, then starts the program
scripts\setup.bat, scripts\run.bat    # same, on Windows
```

| Script | What it does |
|---|---|
| `scripts/setup.sh` / `.bat` | deploys the externals; `--force` downloads and deploys everything again |
| `scripts/build.sh` / `.bat` | builds the jar and runs the tests; what follows goes to Maven (`-DskipTests`) |
| `scripts/run.sh` / `.bat` | builds the jar if a source is newer, then runs it; `--rebuild` forces the build, the other arguments go to the program |
| `scripts/package.sh` / `.bat` | builds the distributions into `releases/` (see "Packaging") |
| `scripts/ci.sh` | a clean build with every test, run by `.githooks/pre-push` |

On Linux, the scripts run one build at a time: `run.sh`, `build.sh`, `ci.sh`, `package.sh` and a push wait for a build in
progress and say so, as each starts by deleting what the other is using. A running program holds nothing up.

Every script uses the externals of `external/` and nothing installed on the machine. The coordinates and the version
live in `pom.xml`, the version once, as `<revision>`.

The pre-push hook is enabled once per clone with `git config core.hooksPath .githooks`.

## Externals

The software the build and the distributions need is not under source control. `externals.conf` names each one and its
version, and `scripts/setup.sh` deploys them under generic names into `external/`: `jdk`, `maven`, `jre-linux.tar.gz`,
`jre-windows.zip`. The archives stay in `external/downloads/`, so a second run downloads nothing.

To fetch from a local mirror, set `MIRROR` in `externals.conf`. Keep its credentials in `externals.local.conf`, which
is not under source control, or in the environment: `externals.conf` documents them.

## Packaging

`scripts/package.sh` builds two self-contained distributions into `releases/`, each with its own JRE, so nothing needs
installing where they are unpacked:

| Archive | Start it with |
|---|---|
| `doccomparator-<version>-linux.tar.gz` | `doccomparator.sh` |
| `doccomparator-<version>-windows.zip` | `DocComparator.exe` (no console) or `doccomparator.bat` (console) |

Both are built from either system. Previous versions' archives are removed from `releases/` once the new one is built.

## Layout

| Path | What it holds |
|---|---|
| `src/main/java/org/jtools/doccomparator/` | `Launcher` (the entry point), `MainWindow` (the window), `NoNetworkResolver` (see "No network") |
| `src/main/resources/META-INF/services/` | what installs `NoNetworkResolver` |
| `src/test/java/org/jtools/doccomparator/` | `NoNetworkTest`, `NoNetworkResolverTest` (see "No network") |
| `src/dist/`, `src/assembly/` | the launchers of the distributions, and what goes in them |
| `scripts/` | the scripts above |
