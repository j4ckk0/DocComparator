# DocComparator

Compares two Word documents (`.docx`): two versions of one file, or two different documents, one of which took
paragraphs from the other and changed pieces of their sentences. The text comes first; the structure, the blanks, the
template, the headers and footers and the styles can each be compared or left out. Paragraphs that nearly match are
shown side by side, their differences marked within them. *(To come: the window, still empty; the comparison itself
is done — see "How it compares".)*

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

**A document cannot make it reach the network either.** XML can name a DTD or an entity elsewhere, which a parser
fetches unless told not to: `XmlCursor`, the program's only XML parser, supports no DTD, no external entity, no
protocol to fetch them with, and refuses a part that has a DOCTYPE, which Word never writes. The links a document
holds — the template it is attached to, often on a network share, a linked picture, a hyperlink — are read as text
and never followed.

Two checks run with every build, so a change that breaks the rule does not build:

- **`NoNetworkTest`** reads every compiled class of the program and fails on any name of what the JDK reaches the network
  with: the packages `java.net`, `javax.net`, `jdk.net`, `sun.net`, `com.sun.net`, `java.rmi`, `javax.rmi`,
  `javax.naming` and `javax.management.remote`; `java.awt.Desktop`, whose `browse()` and `mail()` start other programs
  that do; `ProcessBuilder` and `Runtime.exec`, since another program would do what this one may not; the JDK's XML
  parsers' factories, as above, but in `XmlCursor`. It reads the
  class files, not the sources, so that a fully qualified name, a method reference or a class named for reflection is
  seen as well as an import. A second test checks that it does find each of these. The exemptions are two, each limited
  to what it names: `NoNetworkResolver` names the resolver types it implements, `XmlCursor` makes its parser.
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
  mapped reads them: something to keep in mind when DocComparator opens files. And a document chosen on a network drive
  is read over the network, by the system: the program reads only the files it is given.

The **build**, not the program, uses the network: `scripts/setup.sh` downloads the externals, and the first Maven build
downloads its plugins into `~/.m2`. After that, `mvn -o` builds offline.

## What is read

`DocxReader` reads a `.docx` (and `.docm`, `.dotx`, `.dotm`, built alike) into a `WordDocument`, with the JDK alone:

- **The stories:** the body, each header and footer the sections refer to ("Header (first page), section 2"), each
  footnote and endnote. Each is a list of blocks — paragraphs, tables whose cells hold blocks of their own, text boxes
  right after the paragraph they are anchored in.
- **The text as Word shows it:** tracked changes accepted (inserted text in, deleted text out; the document is marked as
  having had some), a field's result rather than its code, the text of links, content controls and equations, tabs,
  line and page breaks. What Word writes twice for older versions of itself, text boxes for one, is read once.
- **The structure:** a paragraph's heading level and list item (its numbering's format and text), through its own
  properties or its style's, and the style it is based on; the ends of sections.
- **The formatting:** the styles and the document's defaults; each paragraph's, run's, table's and cell's own properties,
  every one of them under Word's name (`b`, `sz`, `rFonts.ascii`, `pBdr.top.sz`), so that all can be compared. Runs that
  look the same are joined: Word splits them for reasons of its own.
- **The template:** the one the settings attach, as linked, and the one the properties name.

Not read yet: comments, pictures (but the text boxes they hold), the numbers a list shows (its format is read), what an
abstract list borrows from a numbering style.

## How it compares

`DocumentComparer` compares two `WordDocument`s, with the JDK alone, in two steps, then lays out the rows of a
side-by-side view (`Comparison`): the body, the headers and footers, the footnotes and endnotes, each on its own. Tables
and text boxes are opened into their paragraphs, each knowing its place (table, row, cell): a paragraph that left a
table is still found.

**Step one, pairing the paragraphs** (`Pairer`). Two paragraphs go together when their text is the same, or close: their
*similarity* is the share of their words they keep in order, from 0 to 1.

- Pairs that keep the order of both documents come first: of all such sequences, the one that pairs the most text, which
  favours a long paragraph over a short one and the same text over a close one (similarity 0.5 at least).
- Paragraphs left over may have their match elsewhere: **moved**, each shown where it is on its side, its row naming the
  other's. A moved pair needs more: the same text, found once on each side, or a similarity of 0.6 and three words at
  least — so that a "Yes." or a "1" in a table is not linked across the document by chance.
- Last, between two pairs in order, paragraphs left over on both sides are paired with less in common (0.4): in place,
  a paragraph rewritten in good part is still the one that was there.

This covers both cases the program is for: two versions of a document, mostly in order, and two documents of which one
took paragraphs from the other, in any order, and changed pieces of them.

**Step two, what differs within a pair** (`ParagraphComparer`). Word by word first: the fewest words removed and added.
Then, where a word was replaced by one close to it (`colour` → `color`, `bench` → `benches`), character by character;
numbers stay whole (`2025` → `2026`). Each side gets its spans: the same, changed, only on this side, absent here
(where the other side has text), unimportant (blanks, when they are left out).

**The options** (`CompareOptions`): the text is always compared; the rest can each be left out.

| Option | Compared |
|---|---|
| structure | heading levels, lists, a paragraph's place (text, table cell, text box), the number of sections |
| blanks | spaces, tabs and breaks as they are, and empty paragraphs; left out, a difference of blanks alone is shown, but does not count |
| template | the template attached, the one named |
| headers and footers | their paragraphs, as a story of their own |
| styles | paragraph and character styles by name (the same in every language: "heading 1"), the formatting set on paragraphs and text, the documents' style definitions |

## Samples

`samples/` holds three documents to try the program on, written by `samples/make_samples.py` (Python's standard library;
run it again after a change: it writes the same files):

| Document | What it is |
|---|---|
| `procedure-revA.docx` | the reference: a maintenance procedure, revision A |
| `procedure-revB.docx` | its next version: paragraphs added, sentences changed, two sections swapped, a table row added, formatting and heading levels changed, blanks added, tracked changes, another footer and template |
| `training-handbook.docx` | another document, on another template, with other headings and styles, that copied paragraphs from revision A, in another order, and changed pieces of them |

The script says what each change is; `SamplesTest` checks that the comparison finds them.

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
| `…/document/` | what a Word document is to the comparison: `WordDocument`, its `Story`s of `Block`s (`Paragraph` of `Run`s, `Table`, `TextBox`), `Formatting`, `StyleSheet`, `Template` |
| `…/docx/` | `DocxReader` (a .docx into a `WordDocument`), `DocxPackage` (the zip and its relationships), `XmlCursor` (the XML, fetching nothing), `StoryReader`, `StylesReader`, `Numbering`, `FormattingReader` |
| `…/compare/` | `DocumentComparer` (two documents into a `Comparison` of `Row`s), `Pairer` (step one), `ParagraphComparer` (step two), `Diff` (Myers' algorithm), `CompareOptions` |
| `src/main/resources/META-INF/services/` | what installs `NoNetworkResolver` |
| `src/test/java/org/jtools/doccomparator/` | `NoNetworkTest`, `NoNetworkResolverTest` (see "No network"); `docx/DocxReaderTest`, on documents `TestDocx` builds from the XML Word writes; `compare/`: each step, and `SamplesTest` |
| `samples/` | the sample documents, and the script that writes them (see "Samples") |
| `src/dist/`, `src/assembly/` | the launchers of the distributions, and what goes in them |
| `scripts/` | the scripts above |
