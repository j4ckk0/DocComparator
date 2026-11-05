# Architecture review — DocComparator

Review of 5 November 2026, after the split into three jars (core, ui, app), the Excel and PDF readers, and the
reader and texts services. The previous review (version 1.6) is in the git history; its findings were all done.

Reviewed the 122 classes of `doccomparator-*/src/main/java`, about 17,800 lines, every package: sizes, public types,
dependency edges between packages and modules, method names. Read in full: the four files past 500 lines
(`TextLayout`, `ContentReader`, `PdfFile`, `ComparatorPanel`), the module descriptors, the reader and texts services
(`DocumentReader`, `DocumentReaders`, `I18n`, `I18nTexts`), `DocumentSource`. Not read line by line: the algorithms of
`compare`, `docx` and `xlsx`, unchanged in shape since the last review or reviewed when written. Comments are not
judged here: a review of them was done at the same time (see the comment review's report).

## Verdict

Yes: the shape reads at once. Three modules follow the pipeline — read, compare, show, run — their exports say what
another program may use, and every arrow points down. What is most in the way is one name: the model every format
reads into is still called **`WordDocument`**, while an Excel workbook, a PDF and a text file are read into it too —
and it is about to be published as an API.

## What's working

- **Layering, one way, no cycle**, now enforced by the modules:
  - core (`document`, `reader`, the four readers, `compare`, `report`, `i18n`) needs the JDK alone (`java.xml`);
  - ui needs core and `java.desktop`, no look and feel of its own;
  - the app needs ui and FlatLaf.
- **Exports are the API**: core exports its model, readers, comparison, words and languages; `ooxml` and each jar's
  texts stay inside. Within exported packages, most types are package-private (`pdf`: 2 public of 13; `docx`: 1 of 7).
- **Two extension seams, the same way**: a format is a `DocumentReader` service, a jar's texts an `I18nTexts` service.
  A third-party jar adds both without touching the program.
- **The comparison is data** (`Phrase`, `Pairing`), put into words by `report` — usable without a window.
- **Each module tests itself**, its texts included (`TextsCheck`); `NoNetworkTest` reads all three modules' classes.
- **The readers are parallel in shape**: each format a package, its reader the only public class, a file of its own
  per concern (`SheetReader`, `Styles`, `NumberFormats`; `PdfFile`, `PdfFont`, `CMap`, `ContentReader`).

## Findings

### 1. `WordDocument` names one format; it holds all four
**Status:** Done: `Document`.
**Where:** `doccomparator-core/…/document/WordDocument.java`; 55 references in main sources, more in tests; the
`DocumentReader.read` contract; README.
**Why it costs:** a developer reading `PdfReader.read(...)` returns a `WordDocument` guesses wrong — a conversion to
Word, or a PDF-to-Word export. Once core is on Maven Central, the name is a contract: renaming it later breaks every
program that uses it. The rest of the model keeps Word's vocabulary on purpose (`Story`, `Run`, `Formatting` under
Word's property names), and that is fine: it is the comparison's common language, said once in `Formatting`'s doc.
**Proposed change:** rename `WordDocument` to `Document` (package `document` already says what it is); state in its
doc that every format is read into it, in Word's terms.
**Effort / risk:** medium, mechanical (a rename across three modules and the README); low risk now, high after
publishing. To do before step 3.

### 2. A file that cannot be read has three exception types, one of them hidden
**Status:** Done: `reader.DocumentReadException`, the file named once by `AbstractDocumentReader.read`; `PdfException` and `OoxmlException` say why only, inside their packages.
**Where:** `ooxml/OoxmlException` (in a package core does not export, yet thrown by the public `DocxReader` and
`XlsxReader`), `pdf/PdfException` (exported), `TextReader` (plain `IOException`); the "file: why" message composed in
three places (`OoxmlException`, `PdfFile.error`, `DocumentSource.File.read`).
**Why it costs:** a program using core cannot catch "this file is not readable" by type: one type it cannot name, one
format-specific, one generic. The message prefix, built in three places, is where a doubled "name: name:" creeps in.
**Proposed change:** one public `reader.DocumentReadException extends IOException` (the file's name and why), which
`OoxmlException` and `PdfException` extend — or which replaces them; the readers name the file once, and
`DocumentSource.File` stops wrapping.
**Effort / risk:** small to medium; the messages' tests pin the exact texts.

### 3. `TextLayout` holds four jobs, and one of its predicates changes what it looks at
**Where:** `doccomparator-core/…/pdf/TextLayout.java` (603 lines): glyphs into lines (140–245), running header and
footer (245–295), lines into paragraphs (295–440), paragraphs into runs, list marks and hyphenated words (440–603);
`joinedBySpace` (549) removes the previous line's last glyph while its name promises a yes/no answer.
**Why it costs:** a fix to the header detection means scrolling through paragraph heuristics; a caller of
`joinedBySpace` would not expect it to edit a line.
**Proposed change:** keep `TextLayout.of(pages)` as the entry point; move the sections to `PdfLines` (glyphs into
`Line`s), `RunningLines` (header and footer), `PdfParagraphs` (lines into paragraphs, runs, list marks). Rename
`joinedBySpace` to `join(previous, line)` returning how they join (a space, nothing, the hyphen dropped), the glyph
dropped by the caller.
**Effort / risk:** medium; the PDF tests (`PdfReaderTest`, `PdfSamplesTest`) cover the behaviour.

### 4. `PdfFile` is the object store and the cross-reference parser
**Where:** `doccomparator-core/…/pdf/PdfFile.java` (546 lines): finding objects — tables, streams, `/Prev` chains, the
rebuild by scanning — (305–515) beside resolving, decrypting and decoding them (130–305).
**Why it costs:** the two change for different reasons (a damaged file's layout; a new encryption or filter), and the
reader of one has to skip the other.
**Proposed change:** extract `CrossReferences` (the places of the objects and the trailer, read or rebuilt); `PdfFile`
keeps resolving them.
**Effort / risk:** small to medium; covered by the rebuilt-file, object-stream and encryption tests.

### 5. The colour spaces sit inside the content interpreter
**Where:** `doccomparator-core/…/pdf/ContentReader.java` (562 lines): `color`, `hex`, `channel`, `resolveSpace`,
`components` (417–510), a self-contained subject inside the operator loop.
**Proposed change:** extract `FillColors` (a colour space and its operands to RRGGBB, or none); `ContentReader` keeps
the operators and the text state.
**Effort / risk:** small.

### 6. "A file of no known extension is a Word document" is decided in the UI
**Status:** Done: `DocumentReaders.forFile`.
**Where:** `doccomparator-ui/…/DocumentSource.java:57` (`OTHERWISE = new DocxReader()`), the only reason ui imports
`docx`.
**Why it costs:** a program using core alone gets `DocumentReaders.of(path)` empty for a Word file named `.tmp`,
while the panel opens it: the same question, two answers, depending on the jar.
**Proposed change:** `DocumentReaders.forFile(Path)`: the reader of its extension, else Word's; `DocumentSource.File`
calls it, and the ui → docx edge disappears.
**Effort / risk:** small.

## Patterns

- **Generic method names, 2:** `ContentReader.run(content, …)` (it interprets a content stream: `interpret`),
  `TextLayout.running(…)` (it takes the running lines out of the pages: `takeRunningLines`).
- **One language for the whole JVM:** `I18n` keeps the language in a static field, which core's readers use for
  their messages. Right for the program; for a server comparing for several users, one language at a time. Not a
  change now — a sentence in `I18n`'s doc, so that a library user knows.
- **Three names for the program** — folder `doccomparator-app`, artifact `doccomparator`, module
  `org.jtools.doccomparator` — kept for the Maven Central coordinates already published; the README's module table
  maps them. No change.

## Suggested order

1. **Finding 1, the rename**, before step 3 publishes core: after that, it is a breaking change.
2. **Finding 2, one read exception**, for the same reason: it is part of the public contract.
3. **Finding 6**, small, and it settles a public behaviour of `DocumentReaders`.
4. **Findings 3, 4, 5 and the two method names**: inside the `pdf` package, nothing public; one at a time, the PDF
   tests after each.
