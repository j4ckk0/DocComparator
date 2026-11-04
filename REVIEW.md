# Architecture review — DocComparator

Reviewed the 84 classes of `src/main/java` (about 9,000 lines, every package) with their sizes, kinds, first doc lines
and dependency edges; read in full the largest and most-connected ones: `ComparatorPanel`, `ComparisonView`,
`OptionControls`, `MainWindow`, `DropHandler`, `DocumentHeader`. Not read line by line: the algorithms of `compare`
(`Pairer`, `ParagraphComparer`, `Diff`) and the readers of `docx`, judged here by their names, their contracts and
their edges only; the 24 test classes, which were read for their comments only. 4 November 2026, version 1.5.

## Verdict

Yes. A developer arriving cold finds their way: the packages follow the program's pipeline (read, compare, show), the
dependencies run one way, and the class names say what each one is. What most gets in the way is in `ui`: **a side**
— left or right — has no type of its own. It is written four different ways, and the two sides' state is kept in
parallel fields. Every feature that touches "a side" (paste, the chosen side, the side waiting) has to learn all of
them.

## What's working

- **Layering, strictly one way, no cycle:** `document` depends on nothing; `docx` and `text` read into `document`;
  `compare` depends on `document`; `ui` on all of them; the entry point on `ui`. Keep it so.
- **The model is immutable records** (`WordDocument`, `Paragraph`, `Pairing`, `Slot`, `ParagraphDiff`…). Whoever
  holds one cannot change it.
- **Names that tell the pipeline:** `DocxReader` → `WordDocument`; `DocumentComparer` drives `Pairer` (step one),
  then `ParagraphComparer` and `TableComparer` (step two) into a `Comparison`. `Lines` turns that into the view's
  `Line`s of `LineSide`s, and `ComparisonView` draws them. Earlier renames (`Pairing`, `LineSide`, `Marking`) removed
  the clashes between "row", "cell" and "look"; the vocabulary is consistent now.
- **One concern, one class**, at a sensible size: no class over 650 lines. Small single-purpose classes
  (`FileUris`, `ListNumbers`, `PageMarks`, `Vocabulary`, `RibbonEdge`) are not over-split: each is used from more than
  one place, or hides a real rule.
- **The no-network rule is structural:** `XmlCursor` is the only XML parser, `NoNetworkResolver` the name service,
  and `NoNetworkTest` checks the compiled classes. A developer cannot break the rule by accident.
- **i18n through `I18nLabels`:** each part of the window binds its labels once; a language change is one `apply()`.

## Findings

### 1. A side has four representations — done (in `ui`; `compare` keeps its booleans)

**Where:**
- `boolean onLeft` in `ComparatorPanel`, `DocumentOpener`, `DocumentHeader`, `DropHandler`, `Lines`, `TableComparer`,
  `ParagraphDiff` (about 20 signatures).
- `Boolean side` in `DropHandler` (null: "by the half dropped on").
- `int waitingSide` / `int side` in `ComparisonView` (0 left, 1 right, -1 none: `side == 0` four times).
- `boolean leftChosen` in `ComparisonView`, with `!leftChosen` for the right in `ComparatorPanel.showChosenSide`.

**Why it costs:** a reader must translate at every boundary: `setWaitingSide(onLeft ? 1 : 0)`, `(side == 0) ==
leftChosen`. A call like `open(true, path)` or `new DropHandler(opener, area, null)` does not say which side it means.
The mistakes are silent: swap 0 and 1 and it still compiles.

**Proposed change:** an `enum Side { LEFT, RIGHT }` in `ui`, with `other()`. The changes:
- `onLeft` parameters become `Side side`;
- `waitingSide` becomes a `Side` (null for none);
- `leftChosen` becomes `Side chosenSide()`;
- `DropHandler`'s `Boolean` becomes a `Side`, null meaning "where it is dropped".

`compare` (`TableComparer`, `ParagraphDiff.alone`) can keep its booleans, or take the same enum moved to `compare`. I
would start with `ui` only.

**Effort / risk:** medium. About 40 call sites in `ui` and their tests. It is a mechanical change with no behaviour
change, and the compiler finds every site.

### 2. `ComparatorPanel` keeps the two sides in parallel fields — done (`SideState`, the heads kept apart so that a swap leaves them in place)

**Where:** `ComparatorPanel.java`.
- Six fields come in left/right pairs: `left`/`right`, `leftDocument`/`rightDocument`, `leftHeader`/`rightHeader`.
- An `if (onLeft) … else …` repeats in `put`, `swap`, `showAlone` and `show`, and the ternaries in `showEmptySides`
  do the same.

**Why it costs:** "what a side has" (its source, its document once read, its head) has no name. Adding one thing per
side means touching every branch, and one branch forgotten leaves the two sides out of step (e.g. a document not
forgotten when its source changes).

**Proposed change:** a small `SideState` (or `OpenedSide`) holding `DocumentSource source`, `WordDocument read` and
`DocumentHeader header`, with `open(source)` (which forgets `read`) and `show(read)`. Two instances replace the six
fields, indexed by `Side` (finding 1); `swap()` exchanges the two instances. Then `ComparatorPanel` drops to around
480 lines and reads as "the panel's layout, the background work, the actions".

**Effort / risk:** small to medium, after finding 1. `ComparatorPanelTest` covers paste, swap, the chosen side and
the waiting side.

### 3. The window's title is passed through a string replace — done

**Where:**
- `ComparatorPanel.title()` builds `"a.docx ↔ b.docx — DocComparator"` with the name written as a literal.
- `MainWindow` then calls `title.replace(APP_NAME, version)` to put the version in.

**Why it costs:** the panel and the window are coupled through a string, not a contract. Renaming the program in
`MainWindow.APP_NAME` alone breaks the versioned title, silently. A document named "DocComparator notes.docx" gets
its own name rewritten too.

**Proposed change:** the panel says only what it knows, the documents' names (`onDocumentsNamed(…)`, or `title()`
returning `"a.docx ↔ b.docx"`, empty with none). `MainWindow` composes `names — DocComparator 1.5`. The literal in
`ComparatorPanel` goes away.

**Effort / risk:** small. Two classes, one test (`pastedTextsAreCompared` checks the title).

### 4. The dark mode is the one choice not kept by `OptionControls` — done

**Where:**
- `OptionControls` keeps every other choice of the View and Compare menus in step with the ribbon, through one rule
  ("every check box showing the same choice is kept in step").
- The dark mode is split: its ribbon box is in `OptionControls`, but its View ▸ Dark menu item lives in
  `MainWindow.menuBar()`.
- The two are synchronised by hand in `MainWindow.applyLook` (`darkMode.setSelected`, `panel.showDarkMode`).

**Why it costs:** a developer looking for "where is View ▸ Dark kept in step" looks in `OptionControls`, as for
every other item, and finds half of it.

**Proposed change:** `OptionControls.viewMenu()` adds the Dark item itself, kept in step with its box as the ribbon
action is. `MainWindow` keeps only the look: `onDarkMode` and `showDarkMode`, as today.

**Effort / risk:** small. `everyButtonDoesSomething` and the dark-look tests cover it.

### 5. `ComparisonView` paints, lays out, hit-tests and holds the selection (640 lines) — done (`LinePainter`, `EmptySide`; the view at 461 lines)

**Where:** `ComparisonView.java`.

**Why it costs:** it is the largest file, and it holds four things:
- the state: lines, current line, chosen and waiting side, plus `difference`/`differenceCount`;
- layout and hit-testing: `layoutLines`, `lineAt`, `sideAt`, `onWaitingSide`;
- painting, about 240 lines: lines, gutter, grid, hatching, empty sides;
- tooltips.

To change how the gutter looks, a reader wades through the selection logic, and the reverse.

**Proposed change:** move the painting of a line (`paintLine`, `paintLineSide`, `paintContent`, `paintGrid`,
`hatch`, `paintGutter`, `paintBlankGutter`, `arrow`) to a `LinePainter`, given the palette, the fonts and the
columns' geometry. Move `paintSide` with `EmptySide` to an `EmptySidePainter`. The view keeps its state, its layout,
the mouse and the scrolling, at around 380 lines.

**Effort / risk:** medium. Painting has no assertion beyond "draws without a screen" (`LinesTest`), so check it by
eye on the samples, light and dark, in both views.

### 6. A second rule for where pasted text goes, which no code path of the program reaches — done

**Where:**
- `DropHandler.leftHalf`: when pasted, not dropped, and no side was given, the text goes to "the side not open yet,
  the left first" (`!opener.leftOpened()`).
- `DocumentOpener.leftOpened()` exists for this branch only.

**Why it costs:** the program's rule is "text pasted goes to the side last clicked" (`ComparisonView.leftChosen`).
A reader finds two rules and has to work out that the second does not happen: every paste in the program goes through
`ComparatorPanel.paste(side)`, which gives the side, and nothing binds Swing's own paste action on the view (a
`JComponent` has none by default). Only `DropHandlerTest` reaches this branch.

**Proposed change:** your call, since it is behaviour. Either:
- remove the branch and `leftOpened()` (a paste without a side then goes to the chosen side, through a
  `DocumentOpener.chosenSide()`), and adapt the test;
- or keep it and say in `DropHandler` that it is a fallback the panel does not use.

**Effort / risk:** small.

### 7. The comparison's results are already in the user's language

**Where:** the text is produced in `compare` and `docx`, not in `ui`:
- `Story.label` ("Body", "Footnote 3") in `docx`;
- `PropertyNames`, `PropertyDifference.none()`, `Item.location()` and `StoryComparison.title` in `compare`.

As a result, `ComparatorPanel.languageChanged()` has to read both documents again and compare them again. `Lines`
takes "Body" off a location by string surgery (`Item.BETWEEN_PLACES`).

**Why it costs:** a maintainer expects a comparison's result to be data and the words to be the view's. Here a
change of language is a new comparison, and a test of `compare` depends on the language in use. That is why
`I18nEnglishInTests` exists.

**Proposed change:** keep kinds and numbers in the model (`Story.Kind` and the numbers are already there; property
names stay Word's own), and translate in `ui` (`Lines`). This is large. I would not do it unless re-comparing on a
language change becomes slow on big documents. It is listed so the choice is a known one.

**Effort / risk:** large. It touches the readers, the comparison, the view and many tests' expected texts.

## Patterns

- **Literals that repeat a value held elsewhere (5)** — done:
  - `"DocComparator"` appears in `ComparatorPanel.title()` and as `MainWindow.APP_NAME`.
  - The shortcut texts `"Ctrl+O"`, `"Ctrl+Shift+O"` and `"Ctrl+V"` in `ComparatorPanel.showEmptySides` are written
    next to the `KeyStroke`s they describe, so the two can drift apart; deriving them from the action's
    `ACCELERATOR_KEY` would keep them in step.
  - The extensions `"docx", "docm", "dotx", "dotm"` are in the file chooser, and the same list is written in
    `DocxReader`'s doc; one constant in `DocxReader` would be the single source.
  - The client property `"look"` in `MainWindow.lookMenu`.
- **Wording helpers in the line builder (2)** — done, moved to `ComparisonWords` with finding 7: `Lines.count()` and `Lines.said()` are called by `ComparatorPanel`
  (the status line) and `ComparisonView` (tooltips). They are how the program says a difference, not how it builds
  lines. Leave them unless finding 7 is done; they would then go together into the view's wording.
- **Comments:** reviewed separately (the comments review). They state intent and the present. Two stale ones were
  fixed: `Settings` did not list the ribbon setting, and `OptionControls.ribbon` did not list the presets group.

## Suggested order

1. **Finding 3, the title:** smallest, and it removes a silent coupling.
2. **Finding 4, the dark item into `OptionControls`:** small, and it makes the class's one rule true everywhere.
3. **Finding 6, the paste rule:** once you have chosen which way.
4. **Finding 1, `Side`:** mechanical, compiler-checked, and it makes 2 and 5 simpler.
5. **Finding 2, `SideState`:** builds on 1.
6. **Finding 5, splitting `ComparisonView`'s painting:** last of the structural ones, checked by eye.
7. **Finding 7:** only if language switching or testing makes it worth it.

Each is one commit, tests run after each.
