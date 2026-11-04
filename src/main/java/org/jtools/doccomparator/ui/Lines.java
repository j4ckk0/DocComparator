package org.jtools.doccomparator.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;
import org.jtools.doccomparator.compare.Comparison;
import org.jtools.doccomparator.compare.DocumentComparer;
import org.jtools.doccomparator.compare.FormatSpan;
import org.jtools.doccomparator.compare.Item;
import org.jtools.doccomparator.compare.PropertyDifference;
import org.jtools.doccomparator.compare.Pairing;
import org.jtools.doccomparator.compare.Slot;
import org.jtools.doccomparator.compare.Span;
import org.jtools.doccomparator.compare.StoryComparison;
import org.jtools.doccomparator.compare.TableComparison;
import org.jtools.doccomparator.document.ListItem;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Picture;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.StyleSheet;
import org.jtools.doccomparator.document.Table;
import org.jtools.doccomparator.document.WordDocument;

/**
 * A comparison into the lines of the view: a title for each part, then its pairings. Those with a paragraph on one side
 * only that follow one another share lines, the left ones beside the right ones, as Beyond Compare shows them: what was
 * removed beside what was added in its place — each still its own difference. A table takes a line for its columns,
 * then one for each of its rows.
 */
final class Lines {

    /** The most formatting differences a paragraph lists; the rest are in the tooltip of the text. */
    private static final int LISTED_FORMATTING = 4;

    /**
     * What the lines show: every line, or only those that differ; everything, or only what is comparable — what has its
     * match on the other side, moved or not; the diff view, or the document view.
     */
    record Shown(boolean onlyDifferences, boolean onlyComparable, boolean documentView) {

        /** Every line, in the diff view. */
        static final Shown ALL = new Shown(false, false, false);

        Shown withOnlyDifferences(boolean on) {
            return new Shown(on, onlyComparable, documentView);
        }

        Shown withOnlyComparable(boolean on) {
            return new Shown(onlyDifferences, on, documentView);
        }

        Shown withDocumentView(boolean on) {
            return new Shown(onlyDifferences, onlyComparable, on);
        }
    }

    private final Fonts fonts;
    private final Palette palette;
    private final Shown shown;
    private final CompareOptions options;
    /** The document view: each paragraph as its document's styles lay it out; null for the diff view. */
    private final StyleSheet[] styles;
    /** The document view: where pages begin, by paragraph, on each side (PageMarks). */
    private final List<Map<Paragraph, List<DocumentLook.PageMark>>> pageMarks = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    /** The numbers the lists are at, on each side, counted again in each part. */
    private ListNumbers leftNumbers = new ListNumbers();
    private ListNumbers rightNumbers = new ListNumbers();

    private Lines(Fonts fonts, Palette palette, Shown shown, CompareOptions options, StyleSheet[] styles) {
        this.fonts = fonts;
        this.palette = palette;
        this.shown = shown;
        this.options = options;
        this.styles = styles;
    }

    /**
     * The lines of one document alone, on its side, the other empty: its parts, paragraphs and tables as they are,
     * nothing compared — until the other document is there.
     */
    static List<Line> alone(WordDocument document, boolean onLeft, boolean documentView, Fonts fonts,
                            Palette palette) {
        Comparison itself = DocumentComparer.compare(document, document,
                CompareOptions.TEXT_ONLY.with(Aspect.HEADERS_AND_FOOTERS, true));
        List<Line> lines = new ArrayList<>();
        for (Line line : of(itself, Shown.ALL.withDocumentView(documentView), fonts, palette)) {
            if (line.isTitle()) {
                int dash = line.title.indexOf(" \u2014 ");
                lines.add(Line.title(dash < 0 ? line.title : line.title.substring(0, dash)));
            } else {
                lines.add(onLeft ? Line.of(line.left, null) : Line.of(null, line.right));
            }
        }
        return lines;
    }

    /**
     * The lines of a comparison, as shown: only what is comparable leaves out a paragraph, a table, a row of a table on
     * one side only.
     */
    static List<Line> of(Comparison comparison, Shown shown, Fonts fonts, Palette palette) {
        Lines lines = new Lines(fonts, palette, shown, comparison.options(), shown.documentView()
                ? new StyleSheet[] {comparison.left().styles(), comparison.right().styles()} : null);
        if (shown.documentView()) {
            lines.pageMarks.add(PageMarks.of(comparison.left(), comparison.options().blanks()));
            lines.pageMarks.add(PageMarks.of(comparison.right(), comparison.options().blanks()));
        }
        lines.addDocument(comparison.documentDifferences());
        for (StoryComparison story : comparison.stories()) {
            lines.addStory(story);
        }
        return lines.lines;
    }

    private void addDocument(List<PropertyDifference> differences) {
        if (differences.isEmpty()) {
            return;
        }
        lines.add(Line.title("Document — " + count(differences.size())));
        for (PropertyDifference difference : differences) {
            lines.add(Line.of(property(difference.property(), difference.left()),
                    property(difference.property(), difference.right())));
        }
    }

    private void addStory(StoryComparison story) {
        List<Pairing> pairings = story.pairings();
        if (pairings.isEmpty()) {
            return;
        }
        int titleLine = lines.size();
        lines.add(Line.title(story.title()));
        leftNumbers = new ListNumbers();
        rightNumbers = new ListNumbers();
        Map<Integer, Integer> lineOfPairing = new HashMap<>();
        Map<LineSide, Integer> moved = new HashMap<>();
        int r = 0;
        while (r < pairings.size()) {
            Pairing pairing = pairings.get(r);
            if (pairing.table() != null) {
                addTable(pairing, r, lineOfPairing, moved);
                r++;
                continue;
            }
            if (pairing.left() != null && pairing.right() != null) {
                add(Line.of(side(pairing, true), side(pairing, false)), true, lineOfPairing, moved, r);
                r++;
                continue;
            }
            List<Integer> lefts = new ArrayList<>();
            List<Integer> rights = new ArrayList<>();
            for (; r < pairings.size() && pairings.get(r).table() == null
                    && (pairings.get(r).left() == null || pairings.get(r).right() == null); r++) {
                (pairings.get(r).left() != null ? lefts : rights).add(r);
            }
            for (int k = 0; k < Math.max(lefts.size(), rights.size()); k++) {
                int leftIndex = k < lefts.size() ? lefts.get(k) : -1;
                int rightIndex = k < rights.size() ? rights.get(k) : -1;
                boolean comparable = leftIndex >= 0 && pairings.get(leftIndex).moved()
                        || rightIndex >= 0 && pairings.get(rightIndex).moved();
                if (shown.onlyComparable()) {   // a moved one alone on its line: its neighbour may be one not comparable
                    if (leftIndex >= 0 && pairings.get(leftIndex).moved()) {
                        add(Line.of(side(pairings.get(leftIndex), true), null), true, lineOfPairing, moved, leftIndex);
                    }
                    if (rightIndex >= 0 && pairings.get(rightIndex).moved()) {
                        add(Line.of(null, side(pairings.get(rightIndex), false)), true, lineOfPairing, moved, rightIndex);
                    }
                    continue;
                }
                add(Line.of(leftIndex < 0 ? null : side(pairings.get(leftIndex), true),
                        rightIndex < 0 ? null : side(pairings.get(rightIndex), false)), comparable, lineOfPairing, moved,
                        leftIndex, rightIndex);
            }
        }
        moved.forEach((side, line) -> {
            int target = lineOfPairing.get(side.pairing.counterpart());
            // the way to its match, said unless the text is left out: a move is a matter of text
            boolean said = options.text() && !(side.caption == null && side.columns != null);
            side.linkTo(target, target > line, said ? fonts.small() : null, palette);
        });
        long differences = lines.subList(titleLine, lines.size()).stream().filter(Line::differs).count();
        lines.set(titleLine, Line.title(story.title() + " \u2014 " + count(differences)));
    }

    /**
     * Adds a line, unless only differences are shown and it has none; the pairings given are then on it, unless they
     * are on a line already (a table, on its first).
     */
    private void add(Line line, boolean comparable, Map<Integer, Integer> lineOfPairing, Map<LineSide, Integer> moved,
                     int... pairings) {
        if (shown.onlyDifferences() && !line.differs() || shown.onlyComparable() && !comparable) {
            return;
        }
        int index = lines.size();
        lines.add(line);
        for (int r : pairings) {
            if (r >= 0) {
                lineOfPairing.putIfAbsent(r, index);
            }
        }
        for (LineSide side : new LineSide[] {line.left, line.right}) {
            if (side != null && side.pairing != null && side.pairing.moved()) {
                moved.put(side, index);
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Tables

    /**
     * A table: a first line with its columns, lettered on each side as a spreadsheet does, then a line for each row,
     * its cells under its columns. A side that has not got a column shows a narrow empty place for it.
     */
    private void addTable(Pairing pairing, int index, Map<Integer, Integer> lineOfPairing, Map<LineSide, Integer> moved) {
        TableComparison table = pairing.table();
        LineSide.Look tableLook = look(pairing);
        boolean comparable = pairing.left() != null && pairing.right() != null || pairing.moved();
        add(Line.of(pairing.left() == null ? null : tableHeader(pairing, true, tableLook),
                pairing.right() == null ? null : tableHeader(pairing, false, tableLook)), comparable, lineOfPairing, moved, index);
        for (int r = 0; r < table.rows().size(); r++) {
            Slot slot = table.rows().get(r);
            boolean rowComparable = comparable && (slot.left() >= 0 && slot.right() >= 0 || slot.moved());
            add(Line.of(slot.left() < 0 || pairing.left() == null ? null : tableRow(pairing, r, true),
                    slot.right() < 0 || pairing.right() == null ? null : tableRow(pairing, r, false)), rowComparable,
                    lineOfPairing, moved, index);
        }
    }

    private LineSide tableHeader(Pairing pairing, boolean onLeft, LineSide.Look look) {
        TableComparison table = pairing.table();
        List<LineSide> columns = new ArrayList<>();
        for (int c : shownColumns(table)) {
            Slot slot = table.columns().get(c);
            int column = onLeft ? slot.left() : slot.right();
            if (column < 0) {
                columns.add(null);
                continue;
            }
            LineSide.Look columnLook = look(slot);
            String said = slot.moved() ? " (moved)" : slot.left() < 0 || slot.right() < 0
                    ? (onLeft ? " (removed)" : " (added)") : "";
            columns.add(new LineSide(columnLook, null, null, null,
                    TextBlock.plain(columnName(column) + said, fonts.small(), palette.faint, palette), null, null));
        }
        Item item = onLeft ? pairing.left() : pairing.right();
        String notes = notes(pairing, onLeft, List.of());
        return new LineSide(look, pairing, null, TextBlock.plain(location(item, true), fonts.small(), palette.faint, palette),
                null, columns, notes.isEmpty() ? null : TextBlock.plain(notes, fonts.notes(), palette.faint, palette));
    }

    private LineSide tableRow(Pairing pairing, int r, boolean onLeft) {
        TableComparison table = pairing.table();
        Slot rowSlot = table.rows().get(r);
        LineSide.Look look = look(rowSlot);
        List<LineSide> columns = new ArrayList<>();
        for (int c : shownColumns(table)) {
            Slot columnSlot = table.columns().get(c);
            if ((onLeft ? columnSlot.left() : columnSlot.right()) < 0) {
                columns.add(null);
                continue;
            }
            Pairing cell = table.cell(r, c);
            if (cell == null || (onLeft ? cell.left() : cell.right()) == null) {
                columns.add(new LineSide(LineSide.Look.SAME, null, null, null,
                        TextBlock.plain("", fonts.text(), palette.text, palette), null, null));   // under a merged cell
                continue;
            }
            LineSide.Look cellLook = look(cell).stronger(look(columnSlot)).stronger(look(rowSlot));
            LineSide shown = side(cell, onLeft, cellLook);
            if (styles != null) {
                shown.shading = DocumentLook.shading((onLeft ? cell.left() : cell.right()).cell());
            }
            columns.add(shown);
            look = look.stronger(cellLook);
        }
        int number = (onLeft ? rowSlot.left() : rowSlot.right()) + 1;
        List<PropertyDifference> rowProperties = table.rowProperties().get(r);
        if (!rowProperties.isEmpty()) {
            look = look.stronger(LineSide.Look.OTHERWISE);
        }
        String notes = String.join("  \u00B7  ", rowProperties.stream()
                .map(d -> d.property() + ": " + (onLeft ? d.left() : d.right())).toList());
        return new LineSide(look, pairing, String.valueOf(number), null, null, columns,
                notes.isEmpty() ? null : TextBlock.plain(notes, fonts.notes(), palette.faint, palette));
    }

    /** The columns shown: all; only what is comparable, those with a match on the other side, moved or not. */
    private List<Integer> shownColumns(TableComparison table) {
        List<Integer> columns = new ArrayList<>();
        for (int c = 0; c < table.columns().size(); c++) {
            Slot slot = table.columns().get(c);
            if (!shown.onlyComparable() || slot.left() >= 0 && slot.right() >= 0 || slot.moved()) {
                columns.add(c);
            }
        }
        return columns;
    }

    /** A column by its letters, as a spreadsheet names it: A to Z, then AA… */
    static String columnName(int column) {
        StringBuilder name = new StringBuilder();
        for (int c = column + 1; c > 0; c = (c - 1) / 26) {
            name.insert(0, (char) ('A' + (c - 1) % 26));
        }
        return name.toString();
    }

    private static LineSide.Look look(Pairing pairing) {
        if (pairing.moved() && pairing.status() != Pairing.Status.SAME) {
            return LineSide.Look.MOVED;
        }
        return switch (pairing.status()) {
            case SAME -> LineSide.Look.SAME;
            case MOVED -> LineSide.Look.MOVED;
            case OTHERWISE -> LineSide.Look.OTHERWISE;
            case CHANGED -> LineSide.Look.CHANGED;
            case LEFT_ONLY, RIGHT_ONLY -> LineSide.Look.ORPHAN;
        };
    }

    /**
     * How a row or a column of a table looks: moved, with the table layout counted; on one side only, with the text or
     * the table layout counted (it holds text, it changes the layout); the same otherwise.
     */
    private LineSide.Look look(Slot slot) {
        boolean layout = options.has(Aspect.TABLE_LAYOUT);
        if (slot.moved()) {
            return layout ? LineSide.Look.MOVED : LineSide.Look.SAME;
        }
        if (slot.left() < 0 || slot.right() < 0) {
            return layout || options.text() ? LineSide.Look.ORPHAN : LineSide.Look.SAME;
        }
        return LineSide.Look.SAME;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Paragraphs

    private LineSide side(Pairing pairing, boolean onLeft) {
        return side(pairing, onLeft, look(pairing));
    }

    private LineSide side(Pairing pairing, boolean onLeft, LineSide.Look look) {
        Item item = onLeft ? pairing.left() : pairing.right();
        // alone: nothing on the other side compared with it (a moved row of a table is compared with its match)
        boolean alone = (pairing.left() == null || pairing.right() == null) && !pairing.moved()
                && (onLeft ? pairing.diff().right() : pairing.diff().left()).isEmpty();
        List<Span> spans = alone ? List.of() : onLeft ? pairing.diff().left() : pairing.diff().right();
        List<FormatSpan> formatting = onLeft ? pairing.diff().leftFormatting() : pairing.diff().rightFormatting();
        boolean heading = item.paragraph().headingLevel() > 0;
        boolean inTable = !item.places().isEmpty() && !item.places().getLast().textBox();
        DocumentLook laidOut = styles == null ? null : DocumentLook.of(styles[onLeft ? 0 : 1], item.paragraph());
        String marker = inTable ? null : marker(item, onLeft ? leftNumbers : rightNumbers);
        if (laidOut != null && !pageMarks.isEmpty()) {
            List<DocumentLook.PageMark> pages = pageMarks.get(onLeft ? 0 : 1).get(item.paragraph());
            if (pages != null) {
                laidOut = laidOut.withMarks(pages);
            }
        }
        if (laidOut != null && item.paragraph().listItem() != null && marker != null) {
            laidOut = laidOut.withNumber(marker);   // in the text, as Word shows it, not in the margin
            marker = null;
        }
        TextBlock text = new TextBlock(item.text(), heading && laidOut == null ? fonts.heading() : fonts.text(),
                palette.text, palette, spans, formatting, "(empty paragraph)", laidOut).withPictures(pictures(item));
        String location = inTable ? null : location(item, false);
        TextBlock caption = location == null ? null : TextBlock.plain(location, fonts.small(), palette.faint, palette);
        String notes = notes(pairing, onLeft, formatting);
        return new LineSide(look, inTable ? null : pairing, marker, caption, text, null,
                notes.isEmpty() ? null : TextBlock.plain(notes, fonts.notes(), palette.faint, palette));
    }

    /** A property of the document, its value on one side. */
    private LineSide property(String property, String value) {
        String text = property + ": " + value;
        List<Span> spans = List.of(new Span(0, property.length() + 2, Span.Kind.SAME),
                new Span(property.length() + 2, text.length(), Span.Kind.CHANGED));
        return new LineSide(LineSide.Look.CHANGED, null, null, null,
                new TextBlock(text, fonts.text(), palette.text, palette, spans, List.of(), " "), null, null);
    }

    /** A paragraph's pictures, by their offset in its text. */
    private static Map<Integer, Picture> pictures(Item item) {
        Map<Integer, Picture> pictures = new HashMap<>();
        int offset = 0;
        for (Run run : item.paragraph().runs()) {
            if (run.picture() != null) {
                pictures.put(offset, run.picture());
            }
            offset += run.text().length();
        }
        return pictures;
    }

    /** Where a paragraph or a table is, when the body's flow does not say (or always): "Table 2", "Footer, section 2". */
    private static String location(Item item, boolean always) {
        if (item.story() == Story.Kind.BODY && item.places().isEmpty() && !always) {
            return null;
        }
        String location = item.location();
        if (item.story() == Story.Kind.BODY) {
            location = location.substring(location.indexOf(", ") + 2);
            location = Character.toUpperCase(location.charAt(0)) + location.substring(1);
        }
        return location;
    }

    /** What else differs, this side's values: "heading: heading 2 · formatting (dotted): bold: true → (none)". */
    private static String notes(Pairing pairing, boolean onLeft, List<FormatSpan> formatting) {
        List<String> notes = new ArrayList<>();
        for (PropertyDifference difference : pairing.diff().properties()) {
            notes.add(difference.property() + ": " + (onLeft ? difference.left() : difference.right()));
        }
        Set<PropertyDifference> formats = new LinkedHashSet<>();
        formatting.forEach(span -> formats.addAll(span.differences()));
        if (!formats.isEmpty()) {
            List<String> listed = formats.stream().limit(LISTED_FORMATTING).map(PropertyDifference::toString).toList();
            notes.add("formatting (dotted): " + String.join(", ", listed) + (formats.size() > LISTED_FORMATTING ? "…" : ""));
        }
        return String.join("  ·  ", notes);
    }

    /** The heading level ("H2"), or the mark of a list item as Word shows it; null for neither. */
    private static String marker(Item item, ListNumbers numbers) {
        if (item.paragraph().headingLevel() > 0) {
            return "H" + item.paragraph().headingLevel();
        }
        ListItem list = item.paragraph().listItem();
        return list == null ? null : numbers.next(list);
    }

    private static String count(long differences) {
        return differences == 0 ? "no difference" : differences == 1 ? "1 difference" : differences + " differences";
    }
}
