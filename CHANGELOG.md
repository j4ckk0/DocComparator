# Changelog

What each version brought, newest first. The GitHub Releases carry the same notes with the downloads and their
checksums.

## 2.1 — 2026-11-05

- **Examples** ([examples/](examples/README.md)): comparing two files without a window, a reader of CSV files of
  one's own, the comparison view in a window of one's own; each a single Java file, compiled at every build.

## 2.0 — 2026-11-05

- **Three jars, for other programs too**: DocComparator is now built as three Java modules, each a jar —
  `doccomparator-core`, the comparison with no window (the readers, the comparison, its words, the languages; the
  JDK alone), `doccomparator-ui`, the comparison view as a Swing component another program can show, and
  `doccomparator`, the program. The distributions carry all three, linked into their runtime; nothing changes in how
  the program is started or used.
- **What another program can build on**: a format is added as a `DocumentReader` service (`AbstractDocumentReader`
  does the extensions and the errors); a jar's texts as an `I18nTexts` service; the panel opens documents from files
  or from the program's own sources (`DocumentSource`) and tells each comparison (`onCompared`). Every format is read
  into one `Document`, and a file that cannot be read is one `DocumentReadException`, naming the file once.
- The window no longer says a PDF's name twice in its error ("report.pdf: report.pdf: …").
- An Excel fraction (`# ?/?`) or condition (`[>100]`) shows the cell's value, no longer half rendered (`13 /`).
- *Open…*'s first filter reads "All documents".

## 1.13 — 2026-11-05

- **PDFs compared**: a `.pdf` file is read as its text, its paragraphs rebuilt from the lines of its pages, and
  compared as a document is. A paragraph goes on across a page break; a list's bullets and numbers are its marks, so
  that a step only renumbered is no difference; the running header and footer are compared once, apart from the body.
  Each character's font, size, bold, italic, colour and superscript go under *Characters* and show in the document
  view. Read with no added library: compressed and damaged files, and encrypted ones that open without a password.
- A PDF is compared up to 2,000,000 characters, about 500 pages. Pictures, and so scanned pages, annotations, form
  fields and tables as tables are not read; a PDF without text, or that asks a password, is refused, saying why.
- *Open…* offers PDFs with the other documents, or apart.

## 1.12 — 2026-11-05

- **Excel workbooks compared**: a `.xlsx`, `.xlsm`, `.xltx` or `.xltm` file is read as a heading for each sheet and a
  table of its cells, compared row by row and column by column like a Word table. Values show as Excel's number
  formats show them, the same on every machine (a `.` for decimals, dates as `2026-11-05`). A changed formula is
  caught even when its result stays the same (*Cells* switch). Fonts, fills, borders, alignment, number formats,
  wrapping, column widths, row heights and hidden rows go under the existing switches and show in the document view.
- A workbook is compared up to 250,000 cells, all its sheets together; a larger one is refused at once, naming the
  sheet. Charts, pictures, comments, conditional formatting and the old `.xls` format are not read.
- *Open…* offers Word documents, Excel workbooks and text files together, or each apart.

## 1.11 — 2026-11-05

- **Text files compared**: a `.txt`, `.text`, `.md` or `.markdown` file, opened, dropped or given at the start, is
  read as text, a paragraph a line, and can face another text file, pasted text or a Word document. Its encoding is
  the one its byte order mark names, or else UTF-8, or else Windows-1252 (a file an older Windows program wrote).
  Markdown is compared as the text it is.
- *Open…* offers Word documents and text files together, or each apart.

## 1.10 — 2026-11-05

- No change to what the program does: the code says, with Java's `_`, every value it is given and does not use.

## 1.9 — 2026-11-05

- **A faster start on Windows too**: the Windows distribution now carries Java's AOT cache of the program, as the
  Linux one does; it holds wherever the archive is unpacked, whatever the time zone.
- **Smaller downloads**: each distribution's runtime is made for the program, with only the parts of Java it uses and
  the program inside it, about half the size of a full runtime: 36 MB for Windows instead of 57, 41 MB for Linux
  instead of 69.

## 1.8 — 2026-11-04

- **Java 25**: the program runs on Eclipse Temurin 25, the runtime both distributions carry.
- **Less memory**: every launcher turns on Java 25's compact object headers, about a tenth less memory for a
  comparison.
- **A faster start on Linux**: the Linux distribution carries Java's AOT cache of the program, `doccomparator.aot`;
  its window opens sooner (0.35 s instead of 0.65 s, measured here). The Windows distribution has none yet.

## 1.7 — 2026-11-04

- No change to what the program does: the code reviewed again (REVIEW.md). Every word of the comparison is now made
  by the view, the comparison keeping only data; the main panel's background work and the layout of the two heads
  have classes of their own; no line of code is over 120 characters.

## 1.6 — 2026-11-04

- **A Help menu**: the program's licence and the embedded Java runtime's, read from the program and its runtime
  (no file to find, no browser), and *About DocComparator*: its version, its home page to copy, the Java it runs on.
- **Apache License 2.0**: `LICENSE` and `NOTICE` at the root and in each distribution, the licence's header in every
  source file, the pom ready for Maven Central (`org.jtools:doccomparator`, see PUBLISHING.md).
- **A language change is immediate**: the comparison shown is said again in the other language at once, without
  reading or comparing the documents again.
- The empty sides name each shortcut's keys in the language in use (*Ctrl+Maj+O* in French).
- The code reviewed for a newcomer's reading (REVIEW.md): one type for a side, the view's drawing apart from its
  state, what the comparison says kept as data until it is shown. The comparison itself is unchanged.

## 1.5 — 2026-11-04

- **Text pasted goes to the side clicked**: Ctrl+V, or *Paste here* in the right-click menu, on the side last clicked;
  *Paste left* and *Paste right* in the File menu. The side chosen shows in blue, its head tinted and underlined.
- **The ribbon** is set apart from the documents by a marked edge, and **collapses** to a strip (its chevron,
  *View ▸ Ribbon*, Ctrl+F1), kept so for the next time.
- The gutter runs down between the sides to the bottom of the view; a click on the side waiting for its document
  selects no line of the other.

## 1.4 — 2026-11-04

- **Text compared without a document**: text pasted or dropped on a side is compared as a document, a paragraph a line.

## 1.3 — 2026-11-04

- **French and English**: every text of the window and of the comparison in one file per language, chosen in the drop
  list on the right of the menus, French by default, kept for the next time.

## 1.2 — 2026-11-04

- The empty window takes the look's colours: dark in a dark look.
- The code reviewed and its comments rewritten; no change to what the program does.

## 1.1 — 2026-11-04

- **FlatLaf's looks**, light and dark, the default; *View ▸ Look* chooses another, *Dark* turns the one in use dark,
  kept beside the program for the next time.

## 1.0 — 2026-11-04

- **Two Word documents compared side by side**: paragraphs paired, even moved, their differences marked word by word
  and character by character; tables row by row and column by column; headers, footers, footnotes and endnotes.
- **A switch for each kind of difference**, the text included: headings, lists, styles, indentation, spacing,
  alignment, tables' layout, cells, template, sections — and *Only differences*, *Only what is comparable*.
- **A document view**, each paragraph as Word lays it out, with list numbers, page and section breaks, and pictures,
  compared by their images.
- **Self-contained and offline**: each distribution carries its Java runtime, and the program never reaches the
  network.
