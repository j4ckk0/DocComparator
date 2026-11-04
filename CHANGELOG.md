# Changelog

What each version brought, newest first. The GitHub Releases carry the same notes with the downloads and their
checksums.

## 1.6 — not released yet

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
