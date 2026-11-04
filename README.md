# DocComparator

Compares two Word documents (`.docx`): two versions of one file, or two different documents, one of which took
paragraphs from the other and changed pieces of their sentences. The text comes first; each kind of layout — heading
levels, lists, styles, indentation, spacing, tables' layout, cells' shading and borders… — can be compared as well,
each on its own switch, and the text itself can be left out to compare the layout alone. Paragraphs that nearly match are
shown side by side, their differences marked within them; tables are compared row by row and column by column.

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
  Traced on Linux (`strace`, 2026-11-04, reading, comparing and drawing the samples): no connection, no address bound,
  no packet. What the trace does show, all on the machine itself:
  - three sockets the JRE opens and closes as its networking library loads, to learn whether IPv6 is there — never
    bound, never connected, nothing sent. The library loads as soon as the JDK reads a font file, through file channels
    whose code loads it: in any Java program that draws text, whatever its fonts (tried: with fonts shipped in the JRE
    and a font configuration of our own, the probe is still there), and no option avoids it. On Windows the same
    library is likely to do the same (not traced);
  - a font cache, `~/.java/fonts/<version>/`, written once by the JDK's Linux font code, the only file written outside
    the program's folder. Naming it, that code asks for the machine's name: `NoNetworkResolver` answers, nothing is
    looked up. Shipping fonts of our own would avoid the cache, for some megabytes, and at the price of the system's
    fonts (a document in a script they lack would show empty boxes): not done;
  - the C library asking the local name service cache (`/var/run/nscd/socket`) for the user's name.

  The JVM's performance counters are kept in memory (`-XX:-UsePerfData` in every launcher), not in a file of `/tmp` or
  `%TEMP%`.
- **What runs on Windows.** `doccomparator.bat` starts the program with the bundled `javaw.exe`, signed by the Eclipse
  Foundation, without a console: no executable of ours, unsigned, that a security policy (AppLocker, an antivirus'
  heuristics) might refuse. `DocComparator.exe`, the same without a script, is not signed; signing it takes a
  code-signing certificate, the company's own. Should its `jre\` folder be removed, it falls back to a message offering
  Java's download page; it stays out of the way while the distribution is whole.
- **The system.** Windows may check a program downloaded from the internet (SmartScreen) the first time it is opened;
  a distribution copied by other means is not marked so. The file dialog of *Open left…* and *Open right…* lists the drives
  the system has mapped, and reads those it is shown, network ones included: dropping the files on the window, or
  giving them at the start, opens no dialog at all. And a document chosen on a network drive
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

## The window

```bash
scripts/run.sh                                   # then open the two documents
scripts/run.sh old.docx new.docx                 # or compare them at once
doccomparator.sh old.docx new.docx               # the same, from a distribution (DocComparator.exe old.docx new.docx)
```

Until both are open, the window shows the two sides, each a place framed with dashes saying which document goes there
and how to open it; one document opened, it is shown on its side as it is, nothing compared, the other side waiting. Each side has its head, as in Beyond Compare: the path of its document — type one and press Enter — and *Open…* to
choose it (Ctrl+O on the left, Ctrl+Shift+O on the right, or the File menu); under them, its template and whether it
had tracked changes. Documents dropped on the window open too: two at once, left and right; one, on the side it is
dropped on, its head included. Dropping works from Windows' Explorer and from Linux desktops (which drop files as
`file://` URIs, decoded by `FileUris` without the JDK's networking classes). The two sides scroll as one, each
paragraph beside its match, as high as the taller.

- **Two views**, in the ribbon's *View* or the *View* menu. *Diff*, the text plain and compact, the differences at a
  glance, as Beyond Compare shows them. *Document*, each paragraph as Word lays it out, through its styles: fonts,
  sizes, bold, italic, underline, colours and highlighting; alignment, justified lines included, indents, space before
  and after, line spacing; a paragraph's and a cell's shading — at a scale that fits a page's width in each side. A
  font the machine has not got is shown in one made to take its place (Carlito for Calibri, Liberation for Arial and
  Times New Roman, on Linux), else in Java's own. Paragraphs stay side by side, paired, their differences marked over
  their formatting. List items show their numbers and bullets at the start of their line, the text at the list's indent. Where each
  page begins is drawn across the page, dashed, numbered on each side: the pages the document asks for ("Page break —
  page 3": a page break in the text, a paragraph's "page break before") and those Word's layout gave it when Word last
  saved it ("Page 3": Word keeps a record of them in the file, `w:lastRenderedPageBreak`; a document Word never laid out
  has none); and where sections end. The program does not lay pages out itself: its lines are not quite Word's, so a
  page Word began within a paragraph is shown before the line holding where it began; one begun in a table counts but is
  not shown. What begins in an empty paragraph, left out with the blanks, is shown before the paragraph after it. Word's
  record of its pages is not compared: it is no part of the content, and changes with the printer. Not shown: pictures.
- **Colours**, after Beyond Compare's: red, the text differs, the very words or letters marked darker; purple, on one
  side only; teal, moved; amber, the same text but its structure or style differs. A red bar marks where the other side has text this one has not; a dotted line, text formatted otherwise (its
  tooltip says how). A sign between the sides says the same at a glance.
- **Moved paragraphs** are shown where they are on each side; a click on one goes to its match.
- **Tables** take a line for their columns, lettered A, B, C… on each side, a column added, removed or moved said so,
  then a line for each row, its cells under their columns, an empty narrow place where a side has not got a column.
- **The map** at the right shows every difference at its height in the documents; a click goes there.
- **The ribbon**, as Word has it: groups side by side, each its name under its buttons — *Go*, *Text*, *Structure*,
  *Formatting*, *Document*, *Show*, *Presets*.
- **Previous and next difference**: F7 and Shift+F7, or *Go*. *Only differences* hides the lines that are the
  same; *Only what is comparable* hides what has nothing in common with the other side — paragraphs, tables, rows and
  columns of tables on one side only — and shows the rest: the same paragraphs as they are, the close ones with their
  differences. To see two different documents by what they share.
- **What is compared**: a check box for each kind of difference, in the ribbon's groups *Text*, *Structure*,
  *Formatting* and *Document*; *Presets* sets them all at once (*Text only*, *Layout only*, *All*); the same in the
  *Compare* menu, with *None*. Each change compares again at once; the documents are not read again.
- What is said under a paragraph, a cell or a row: what else differs (heading, style, indent, shading…), on each side
  its own value. A right click copies a side's text.

## How it compares

`DocumentComparer` compares two `WordDocument`s, with the JDK alone, in two steps, then lays out the rows of a
side-by-side view (`Comparison`): the body, the headers and footers, the footnotes and endnotes, each on its own. Text
boxes are opened into their paragraphs and tables; a table is compared as a whole (see "Tables").

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

**Tables** (`TableComparer`): their layout, then their content, whatever their styles.

1. Tables are paired as paragraphs are, by the text of their cells, a table with a table only: added, removed, moved,
   changed.
2. Their **columns** are paired, each as the text of its cells top to bottom: added, removed, moved. Columns left over
   whose header (first cell) is alike are paired in place: a column whose values were all rewritten under the same
   header is still that column.
3. Their **rows** are paired the same way, each as the text of its cells: added, removed, moved, changed.
4. Where a row meets a column, the two **cells** are compared as two paragraphs; a cell of a moved row or column with
   its match.

Cells are placed on the table's grid, as Word lays it out: a cell spanning columns (`gridSpan`) at the first of them, a
cell merged with the one above (`vMerge`) at the top one. A cell's paragraphs are one text, a line break between them;
a table within a cell is read as text. Rows and columns moved count with the table layout only; added or removed, they
hold text, which counts with the text. The tables', rows' and cells' own properties count each with its switch.

**What is compared** (`CompareOptions`): each kind of difference on its own switch. By default, the text and the
headers and footers: the content; the layout is there to switch on when it matters.

| Switch | Compared |
|---|---|
| Text | the words of paragraphs and cells; paragraphs added, removed, moved. Off: paragraphs are still paired by their text, the only way to compare their layout, but nothing of their text is shown as differing |
| Blanks | spaces, tabs and breaks as they are, and empty paragraphs; off, a difference of blanks alone is none |
| Headings | heading levels: a title's depth |
| Lists | list items, their level and numbering |
| Sections | section breaks and their number; paragraphs in text boxes |
| Table layout | rows and columns added, removed or moved, their numbers; tables', columns' and rows' widths and heights, a table's place |
| Styles | paragraph, character and table styles by name (the same in every language: "heading 1"); the documents' style definitions |
| Characters | the formatting set on the text: bold, italic, underline, font, size, colour, highlight… |
| Indentation, Spacing, Alignment | paragraphs' indents, space before and after, line spacing, alignment — as they apply, through the paragraph's style and those it is based on |
| Paragraphs | the rest of a paragraph's own formatting: borders, shading, tabs, keep with next… |
| Cell formatting | cells' and tables' shading, borders, margins, vertical alignment; header rows, rows kept on one page |
| Template | the template attached, the one named |
| Headers and footers | their text, as a part of its own |

Which switch a property is on depends on where it is set: `jc` aligns a paragraph's text (Alignment) but places a table
(Table layout); `shd` shades text (Characters), a paragraph (Paragraphs) or a cell (Cell formatting) — `Aspects` says
which. A property and its attributes are one difference, named as a reader says it: "cell shading: clear, color auto,
fill D9E2F3". Word's `cnfStyle`, the table style's banding flags it writes on rows, cells and paragraphs, is never
compared: Word rewrites it on every row below one inserted.

Not compared yet: the formatting text takes from its styles (Characters compares what is set on the text itself, Styles
the styles). The indentation a list gives its items is compared with the rest, as Word applies it: over the
paragraph's style, under the paragraph's own.

## Samples

`samples/` holds three documents to try the program on, written by `samples/make_samples.py` (Python's standard library;
run it again after a change: it writes the same files):

| Document | What it is |
|---|---|
| `procedure-revA.docx` | the reference: a maintenance procedure, revision A, with page breaks, and Word's record of its pages |
| `procedure-revB.docx` | its next version: paragraphs added, sentences changed, two sections swapped, a table row added, formatting and heading levels changed, a paragraph indented, the table's header row shaded and taller, a cell's border, blanks added, tracked changes, another footer and template |
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
| `doccomparator-<version>-windows.zip` | `doccomparator.bat` (the signed `javaw.exe`, no console), or `DocComparator.exe` (not signed) |

Both are built from either system. Previous versions' archives are removed from `releases/` once the new one is built.

## Layout

| Path | What it holds |
|---|---|
| `src/main/java/org/jtools/doccomparator/` | `Launcher` (the entry point, given two documents or none), `MainWindow` (the window, its menus), `NoNetworkResolver` (see "No network") |
| `…/document/` | what a Word document is to the comparison: `WordDocument`, its `Story`s of `Block`s (`Paragraph` of `Run`s, `Table`, `TextBox`), `Formatting`, `StyleSheet`, `Template` |
| `…/docx/` | `DocxReader` (a .docx into a `WordDocument`), `DocxPackage` (the zip and its relationships), `XmlCursor` (the XML, fetching nothing), `StoryReader`, `StylesReader`, `Numbering`, `FormattingReader` |
| `…/compare/` | `DocumentComparer` (two documents into a `Comparison` of `Row`s), `Pairer` (step one), `ParagraphComparer` (step two), `TableComparer` and `TableGrid` (tables), `Slot` (two sequences side by side), `Diff` (Myers' algorithm), `CompareOptions` (the switches), `Aspects` (which switch a property is on), `Differences`, `PropertyNames` |
| `…/ui/` | `ComparatorPanel` (the ribbon, the sides' heads, the status line), `ComparisonView` (the two sides, drawn line by line), `Lines` (a comparison into lines), `DifferenceMap`, `TextBlock` (a text wrapped, its differences marked; laid out as Word does in the document view), `DocumentLook` (a paragraph's formatting as it applies), `PageMarks` (where pages begin), `FontFamilies`, `FileUris` (files dropped from Linux) |
| `src/main/resources/META-INF/services/` | what installs `NoNetworkResolver` |
| `src/test/java/org/jtools/doccomparator/` | `NoNetworkTest`, `NoNetworkResolverTest` (see "No network"); `docx/DocxReaderTest`, on documents `TestDocx` builds from the XML Word writes; `compare/`: each step, tables, and `SamplesTest`; `ui/LinesTest`, the view, drawn without a screen |
| `samples/` | the sample documents, and the script that writes them (see "Samples") |
| `src/dist/`, `src/assembly/` | the launchers of the distributions, and what goes in them |
| `scripts/` | the scripts above |
