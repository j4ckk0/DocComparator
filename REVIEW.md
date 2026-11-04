# Architecture review — DocComparator

Final review at version 1.6 (4 November 2026), after the first review's seven findings were done (`Side`,
`SideState`, `LinePainter` and `EmptySide`, the comparison kept as data and said by the view, the literals held once).

Reviewed the 91 classes of `src/main/java`, about 11,400 lines, every package: their sizes, kinds, first doc lines
and dependency edges. Read in full: `ComparatorPanel`, the classes added or changed by the first review (`Side`,
`SideState`, `LinePainter`, `EmptySide`, `ComparisonWords`, `Said`, `Story.Name`, `StoryComparison.Part`) and
`HelpMenu`. Not read line by line: the algorithms of `compare` and the readers of `docx`, unchanged in shape since the
first review.

## Verdict

Yes: a developer arriving cold finds their way. The packages follow the pipeline (read, compare, show), the
dependencies run one way, and a side, its state and its drawing now each have a type. One thing is still in the way:
putting the comparison into words has **two homes**, one in `compare` and one in `ui`, and the one in `compare` has a
name that does not say what kind of object it is.

## What's working

- **Layering, one way, no cycle:**
  - `document` depends on nothing, and holds no translated text: a story is named by data (`Story.Name`).
  - `docx` and `text` read into `document`; `docx` uses `i18n` for its error messages only.
  - `compare` depends on `document`; `ui` on everything below it.
- **A side is one type** (`Side`), its state one record (`SideState`): no more booleans, 0/1/-1, or parallel fields.
- **The view is split by concern:**
  - `ComparisonView` keeps the state, the layout, the mouse and the scrolling (461 lines);
  - `LinePainter` draws the lines, `EmptySide` a side waiting for its document.
- **A language change is a re-show**, not a new comparison: the comparison holds data; a test proves it is immediate.
- **The no-network rule stays structural** (`NoNetworkTest`): even the Help menu's home page is text to copy.
- **The model is immutable records**; the class names say the pipeline (`DocxReader`, `Pairer`, `ParagraphComparer`,
  `TableComparer`, `Lines`, `ComparisonView`).

## Findings

### 1. Putting the comparison into words has two homes — done (`Said` renamed `Phrase`, plain data; `ComparisonWords.say` and `PropertyNames` in `ui`; `compare` imports nothing from `i18n`, its tests read keys and Word's names)

**Where:**
- `compare/Said.java` says itself (`say()`, through `I18n`), and `compare/PropertyNames.java` names Word's properties
  in the language in use.
- `ui/ComparisonWords.java` says the rest: a story's name, a location, a part's title, a count, a property difference.

**Why it costs:** the rule the first review set — the comparison holds data, the view says it — holds for stories and
parts, not for property differences, which say themselves from inside `compare`. A maintainer adding a new phrase must
learn both mechanisms and guess which one applies. And `Said` is a participle: covering its body, a reader cannot tell
it is a data type for deferred text, nor that it belongs with `ComparisonWords`.

**Proposed change:**
- Rename `Said` to `Phrase` ("a phrase of the comparison, kept as data"): 78 references in 8 files.
- Move the saying to the view: `ComparisonWords.say(Phrase)`, a switch over its four records; `PropertyNames` moves to
  `ui` with it.
- `compare` then imports nothing from `i18n`, and every word the user reads is made in `ui`.

**Effort / risk:** medium. A rename and a move, no behaviour change. The tests already assert the English text through
one helper (`Paragraphs.said`), and the 16 screenshots in two languages can check the view pixel for pixel, as they
did for the first review.

### 2. `ComparatorPanel` is the one file over 500 lines (593) — done (`EmptySide.waiting`/`opened`, `HeadsLayout`, `BackgroundWork`; the panel at 482 lines)

**Where:** `ui/ComparatorPanel.java`.

**Why it costs:** besides its core (assembling the panel and its actions, the documents' flow from opening to
comparing), it holds three things a reader does not look for there:
- the empty sides' texts and the naming of a shortcut's keys (`showEmptySides`, `emptySide`, `shortcut`, lines
  412–444);
- the background work's plumbing: cursor, cancelling, failure dialog (`inBackground`, about 35 lines);
- the layout of the two heads over their columns (`HeadsLayout`, lines 559–592).

**Proposed change:**
- `EmptySide.waiting(sideKey, openShortcut, pasteShortcut)` and `EmptySide.opened(name)` build what an empty side
  says, beside how it is drawn; the shortcut naming goes with them.
- `HeadsLayout` gets a file of its own, given the view and the heads.
- `inBackground` becomes a small `BackgroundWork` the panel owns (`start(saying, work, done, failed, title)`,
  `cancel()`).

That brings the panel to about 490 lines, each part findable by its name.

**Effort / risk:** small. Moves only. `ComparatorPanelTest` covers pasting, the chosen side, the waiting side, the
look and the language.

## Patterns

- **Lines over 120 characters (55, in 25 files)** — done: none left, the code and the comments' words unchanged: most in `ui/Lines.java` (9, some from `Side.LEFT` being longer
  than `true`), `OptionControls`, `LinePainter` and `DocumentComparer` (5 each). A formatting matter, not a
  structural one: worth one formatting pass, on its own commit.
- **A test helper in a class named for something else:** `Paragraphs.said(…)` turns property differences into text,
  in a helper class for paragraphs. With finding 1, it would move with the phrases.
- **Comments:** reviewed separately. They state intent and the present. One fixed: `HelpMenu.show` repeated the doc of
  `content`.

## Suggested order

1. **Finding 2:** small moves, and they clear the only oversized file.
2. **Finding 1:** the larger one; checked by the tests and the two-language screenshots, like the first review's
   finding 7.
