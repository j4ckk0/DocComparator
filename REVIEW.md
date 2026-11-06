# Architecture review — DocComparator

Review of 6 November 2026, after 2.0 (three jars, ready for Maven Central) and 2.1 (the examples, now a module of
the build). The previous review (5 November) is in the git history; its six findings were all done.

Measured: the 128 classes of `doccomparator-*/src/main/java` and `examples/src/main/java`, 18,538 lines — sizes,
public types per package, the import edges between packages, inbound references within `compare` and `ui`, method
names. Read in full: `ComparatorPanel`'s fields and public methods, `compare`'s public records (`Item`, `Pairing`,
`ParagraphDiff`, `Span`, `FormatSpan`, `Slot`, `PropertyDifference`, `Comparison`, `StoryComparison`,
`TableComparison`), `document`'s `Paragraph`, `Document`, `Story`, `Formatting`, and `Palette`, `I18nLabels`. Read by
their method lists only: `StoryReader`, `NumberFormats`, `SheetReader`, `ParagraphComparer`. Not read: the bodies of
`ui`'s drawing classes (`TextBlock`, `Lines`, `ComparisonView`, `OptionControls`, `LinePainter`), the `pdf` package
(reviewed yesterday, unchanged since), the app's six classes beyond their imports, the tests. Comments are not judged
here.

## Verdict

Yes: the shape still reads at once. Read, compare, say, show, run, each a package or a module with every arrow
pointing down, no file past 535 lines, method names that say what they do. What is most in the way is now the
**public surface a newcomer meets first**, the one about to go to Maven Central: building a `Document` from a reader of
one's own takes positional nulls, and the first type of the comparison an API user holds is called `Item`.

## What's working

- **Layering, one way, no cycle**: `document` depends on nothing; the readers (`docx`, `xlsx`, `pdf`, `text`) on
  `document`, `ooxml` and `reader`; `compare` on `document` alone; `report` on `compare`; ui on core; the app on ui.
  The module descriptors enforce it.
- **No god object**: the most referenced class in `ui`, `Palette` (14 of 34 files), holds colours and nothing else; in
  `compare`, `PropertyDifference` (8 of 24), a record. Dependencies spread out instead of converging.
- **`compare` reads as its pipeline**: `Items` (stories into what is paired), `Unit` (ready to compare), `Pairer`,
  `ParagraphComparer` and `TableComparer` (steps one and two), `Comparison` (the result); the public types are records
  of the result, the algorithms package-private.
- **`ComparatorPanel`**, the one file past 500 lines, is one thing: the panel's public face, each method handing off
  to a part (`OptionControls`, `DocumentHeader`, `BackgroundWork`, `StatusLine`). No split to propose.
- **Method names**: a search for `handle`, `process`, `run`, `manage`, `…And…`, and `get`/`is`/`find` with side
  effects finds none, but one (Patterns).
- **The examples are a module**, compiled and run at every build: the public API's first users are checked.

## Findings

### 1. A reader of one's own builds its `Document` from positional nulls
**Status:** Done: `Paragraph.of(runs)`, `Paragraph.of(text)` (no run for an empty line), `Run.of(text)`,
`Document.of(fileName, body)`; used by `TextReader`, `XlsxReader`, `ComparedBlocks`, `TableGrid`, `CsvReader` and three
tests. `PdfParagraphs` and `PdfReader` keep their constructors: one gives the pages' starts, the other headers.
**Where:** `document/Paragraph.java`, `document/Document.java`. A plain paragraph is
`new Paragraph(null, Formatting.NONE, 0, null, false, runs)` in `text/TextReader.java:120`,
`compare/Items.java:64`, `compare/TableGrid.java:90`, and in
`examples/…/CsvReader.java:98`; a document of one body is
`new Document(name, new Story(Story.Name.BODY, blocks), List.of(), List.of(), StyleSheet.NONE, Template.NONE, false)`
in `text/TextReader.java:122`, `xlsx/XlsxReader.java:133`, `examples/…/CsvReader.java:92`, and nearly in
`pdf/PdfReader.java:117`.
**Why it costs:** a format of one's own is the seam the README and the examples advertise, and its author's first
lines are five arguments whose meaning they must look up (`0` a heading level, `false` "ends a section", the last
`false` "has tracked changes"). Every reader that is not Word writes the same filler, and a new field on `Document`
breaks all of them.
**Proposed change:** two factories, additive: `Paragraph.of(List<Run> runs)` (no style, no formatting, not a heading,
no list item) and `Document.of(String fileName, List<Block> body)` (no headers, notes, styles or template, no tracked
change); the seven call sites above use them. A `Run.of(String text)` too if wanted (the same pattern, once in core).
**Effort / risk:** small · none: new methods, the constructors stay.

### 2. `compare`'s public vocabulary: `Item`, `Span`, and "diff" beside "difference"
**Status:** Done, all of it: `ComparedBlock` (and `ComparedBlock.Place`), `ComparedBlocks`, `TextSpan`,
`ParagraphDifference`, `Pairing.difference()`, `EditScript` (and `EditScriptTest`); README's Layout names them.
**Where:** `compare/Item.java` (51 references in 14 files: `Pairing`, `Unit`, `Items`, `Pairer`, `TableGrid`,
`TableComparer`, `ParagraphComparer`, `DocumentComparer`, `ComparisonWords`, ui's `Lines`…); `compare/Items.java`;
`compare/Span.java` beside `FormatSpan.java`; `ParagraphDiff` and `Pairing.diff()` beside `PropertyDifference` and
`StoryComparison.differences()`.
**Why it costs:** `Pairing(Item left, Item right, …)` is the first thing an API user holds
(`pairing.left().text()` in `CompareFiles`), and `Item` says nothing of what it is: a paragraph, a table or a cell,
with where it is (`places`). The package that builds them from stories, `Items`, differs from it by one letter, and
`document` already has a `ListItem`. Next to it, `Span` and `FormatSpan` are two halves of one diff — the text's
stretches and the formatting's — but only one says what it spans. And the same idea has two words across the public
types: a reader looks for `ParagraphDifference` and finds `ParagraphDiff`.
**Proposed change:** before the first upload to Central (PUBLISHING.md: "Not published yet"), after which each is a
breaking change:
- `Item` → `ComparedBlock` (a block of a document as the comparison pairs it, and where it is), `Item.Place` →
  `ComparedBlock.Place`; `Items` → `ComparedBlocks`;
- `Span` → `TextSpan`, to pair with `FormatSpan`;
- optional, the larger one: `ParagraphDiff` → `ParagraphDifference` and `Pairing.diff()` → `difference()`, so that the
  public API says "difference" throughout. The internal `Diff` (Myers' edit script) may keep its name, or become
  `EditScript`, which says what `Diff.of` returns.
**Effort / risk:** medium · mechanical, the compiler finds every site; public API, so now or at a major version. No
README names them (checked); the examples do (`CompareFiles`).

### 3. The model's formatting speaks Word's XML, in string literals the readers repeat
**Status:** Done: `document.WordProperties`, the names the Excel and PDF readers set, each with its values and unit
(`cellBorder(side, part)` for the borders); used by `Styles`, `SheetReader`, `XlsxReader`, `PdfParagraphs`, and by
`TableGrid`, which reads `gridSpan` and `vMerge` from any reader. `WordPropertiesTest` fails on a name the report cannot
say (checked: `"szz"` fails it). Not `Aspects`: it, `PropertyNames`, the Word reader and the view's `DocumentLook` read
Word's whole vocabulary, most of it without a constant, and keep Word's names as Word writes them (the class's doc says
so). Corrected below: a name no switch claims is compared all the same.
**Where:** `document/Formatting.java` (its doc: "under Word's own names"); the names written as literals by each
reader — `xlsx/SheetReader.java:147-364` (`"trHeight.val"`, `"tcW.w"` with `"dxa"`, `"gridSpan"`, `"vMerge"`),
`xlsx/Styles.java:158` (`"u"`) — and read as literals by `compare/Aspects.java` (`"ind"`, `"jc"`, `"spacing"`…).
**Why it costs:** the choice is sound and written down — one vocabulary, Word's, so that every format compares
the same way. But it is learnt by reading `SheetReader`: a reader of one's own whose cells should compare their width
must know that it is `tcW.w` in twentieths of a point with `tcW.type` `dxa`. A typo in one of these strings is not a
compile error: the property is still compared (`Aspects.of` gives a name no switch claims its source's default —
paragraphs, characters or cells), but under that switch rather than its own, and said under its raw name.
**Proposed change:** name the vocabulary once: the property names a reader may set, as constants grouped by where they
apply (paragraph, run, table, row, cell), in `document` beside `Formatting` (a `WordProperties` class, or nested in
`Formatting`), each with its unit; `SheetReader`, `Styles` and `Aspects` use them; `examples/README.md`'s "A format of
your own" points to it. The model itself does not change.
**Effort / risk:** medium · behaviour unchanged if each literal is replaced by its constant; the docx reader, which
copies Word's names as it reads them, needs none.

## Patterns

- **One flat package for the view, 34 classes** (`doccomparator-ui/…/ui/`): the panel, the sides' heads, the drawn
  view, the controls, the labels. A newcomer finds their way through README's Layout table, which groups them, rather
  than through the folders. Kept: subpackages would make public, and so exported, classes that are package-private
  today. No change; keep the Layout table up to date when a class is added.
- **One public verb with no object** (done: `relabel()`): `I18nLabels.apply()` — what it does is set every label
  bound again, in the language now in use: `relabel()` says it. Public in ui, used by the app (`MainWindow`) and
  `ComparatorPanel`; with finding 2 if done.
- **A nested type named after a JDK class**: `DocumentSource.File`, which takes a `Path`, in a package where
  `java.io.File` is imported too (`ComparatorPanel`). Clear enough written in full (`DocumentSource.File`); worth
  knowing before it is published, not a change on its own.
- **`Story.Name` is an identifier** (kind, pages, section, number), not a name; documented as "what tells a story
  from the others". No change.

## Suggested order

1. **Finding 1**: additive, nothing can break, and it shows in the example a reader author copies.
2. **Finding 2**, before the first `mvn -Ppublish deploy`: one rename a commit (`Item`, then `Span`, then the
   "difference" pass if wanted), the build after each.
3. **Finding 3**: inside core, no public name changes but the new constants'; after 1, so that `CsvReader` shows both.
4. `I18nLabels.apply()` with the renames of 2, being public too.
