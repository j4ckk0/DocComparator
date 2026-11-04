package org.jtools.doccomparator.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jtools.doccomparator.compare.CompareOptions;
import org.jtools.doccomparator.compare.CompareOptions.Aspect;
import org.jtools.doccomparator.compare.Comparison;
import org.jtools.doccomparator.compare.DocumentComparer;
import org.jtools.doccomparator.compare.FormatSpan;
import org.jtools.doccomparator.compare.Item;
import org.jtools.doccomparator.compare.PropertyDifference;
import org.jtools.doccomparator.compare.Row;
import org.jtools.doccomparator.compare.Slot;
import org.jtools.doccomparator.compare.Span;
import org.jtools.doccomparator.compare.StoryComparison;
import org.jtools.doccomparator.compare.TableComparison;
import org.jtools.doccomparator.document.ListItem;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.document.WordDocument;

/**
 * A comparison into the lines of the view: a title for each part, then its rows. Rows with a paragraph on one side
 * only that follow one another share lines, the left ones beside the right ones, as Beyond Compare shows them: what was
 * removed beside what was added in its place — each still its own difference. A table takes a line for its columns,
 * then one for each of its rows.
 */
final class Lines {

    /** The most formatting differences a paragraph lists; the rest are in the tooltip of the text. */
    private static final int LISTED_FORMATTING = 4;

    private final Fonts fonts;
    private final boolean onlyDifferences;
    private final boolean onlyComparable;
    private final CompareOptions options;
    private final List<Line> lines = new ArrayList<>();
    /** The numbers lists are at, on each side: by list, a counter for each level. */
    private final Map<String, int[]> leftNumbers = new HashMap<>();
    private final Map<String, int[]> rightNumbers = new HashMap<>();

    private Lines(Fonts fonts, boolean onlyDifferences, boolean onlyComparable, CompareOptions options) {
        this.fonts = fonts;
        this.onlyDifferences = onlyDifferences;
        this.onlyComparable = onlyComparable;
        this.options = options;
    }

    static List<Line> of(Comparison comparison, boolean onlyDifferences, Fonts fonts) {
        return of(comparison, onlyDifferences, false, fonts);
    }

    /**
     * The lines of one document alone, on its side, the other empty: its parts, paragraphs and tables as they are,
     * nothing compared — until the other document is there.
     */
    static List<Line> alone(WordDocument document, boolean onLeft, Fonts fonts) {
        Comparison itself = DocumentComparer.compare(document, document,
                CompareOptions.TEXT_ONLY.with(Aspect.HEADERS_AND_FOOTERS, true));
        List<Line> lines = new ArrayList<>();
        for (Line line : of(itself, false, fonts)) {
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
     * The lines of a comparison: only those that differ, if asked; only what is comparable, if asked — what has its
     * match on the other side, moved or not: a paragraph, a table, a row of a table on one side only is left out.
     */
    static List<Line> of(Comparison comparison, boolean onlyDifferences, boolean onlyComparable, Fonts fonts) {
        Lines lines = new Lines(fonts, onlyDifferences, onlyComparable, comparison.options());
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
        List<Row> rows = story.rows();
        if (rows.isEmpty()) {
            return;
        }
        int titleLine = lines.size();
        lines.add(Line.title(story.title()));
        leftNumbers.clear();
        rightNumbers.clear();
        Map<Integer, Integer> lineOfRow = new HashMap<>();
        Map<Cell, Integer> moved = new HashMap<>();
        int r = 0;
        while (r < rows.size()) {
            Row row = rows.get(r);
            if (row.table() != null) {
                addTable(row, r, lineOfRow, moved);
                r++;
                continue;
            }
            if (row.left() != null && row.right() != null) {
                add(Line.of(cell(row, true), cell(row, false)), true, lineOfRow, moved, r);
                r++;
                continue;
            }
            List<Integer> lefts = new ArrayList<>();
            List<Integer> rights = new ArrayList<>();
            for (; r < rows.size() && rows.get(r).table() == null
                    && (rows.get(r).left() == null || rows.get(r).right() == null); r++) {
                (rows.get(r).left() != null ? lefts : rights).add(r);
            }
            for (int k = 0; k < Math.max(lefts.size(), rights.size()); k++) {
                int leftRow = k < lefts.size() ? lefts.get(k) : -1;
                int rightRow = k < rights.size() ? rights.get(k) : -1;
                boolean comparable = leftRow >= 0 && rows.get(leftRow).moved()
                        || rightRow >= 0 && rows.get(rightRow).moved();
                if (onlyComparable) {   // a moved one alone on its line: its neighbour may be one not comparable
                    if (leftRow >= 0 && rows.get(leftRow).moved()) {
                        add(Line.of(cell(rows.get(leftRow), true), null), true, lineOfRow, moved, leftRow);
                    }
                    if (rightRow >= 0 && rows.get(rightRow).moved()) {
                        add(Line.of(null, cell(rows.get(rightRow), false)), true, lineOfRow, moved, rightRow);
                    }
                    continue;
                }
                add(Line.of(leftRow < 0 ? null : cell(rows.get(leftRow), true),
                        rightRow < 0 ? null : cell(rows.get(rightRow), false)), comparable, lineOfRow, moved,
                        leftRow, rightRow);
            }
        }
        moved.forEach((cell, line) -> {
            int target = lineOfRow.get(cell.row.counterpart());
            // the way to its match, said unless the text is left out: a move is a matter of text
            boolean said = options.text() && !(cell.caption == null && cell.columns != null);
            cell.linkTo(target, target > line, said ? fonts.small() : null);
        });
        long differences = lines.subList(titleLine, lines.size()).stream().filter(Line::differs).count();
        lines.set(titleLine, Line.title(story.title() + " \u2014 " + count(differences)));
    }

    /**
     * Adds a line, unless only differences are shown and it has none; the rows given are then on it, unless they are on
     * a line already (a table, on its first).
     */
    private void add(Line line, boolean comparable, Map<Integer, Integer> lineOfRow, Map<Cell, Integer> moved,
                     int... rows) {
        if (onlyDifferences && !line.differs() || onlyComparable && !comparable) {
            return;
        }
        int index = lines.size();
        lines.add(line);
        for (int r : rows) {
            if (r >= 0) {
                lineOfRow.putIfAbsent(r, index);
            }
        }
        for (Cell cell : new Cell[] {line.left, line.right}) {
            if (cell != null && cell.row != null && cell.row.moved()) {
                moved.put(cell, index);
            }
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Tables

    /**
     * A table: a first line with its columns, lettered on each side as a spreadsheet does, then a line for each row,
     * its cells under its columns. A side that has not got a column shows a narrow empty place for it.
     */
    private void addTable(Row row, int index, Map<Integer, Integer> lineOfRow, Map<Cell, Integer> moved) {
        TableComparison table = row.table();
        Cell.Look tableLook = look(row);
        boolean comparable = row.left() != null && row.right() != null || row.moved();
        add(Line.of(row.left() == null ? null : tableHeader(row, true, tableLook),
                row.right() == null ? null : tableHeader(row, false, tableLook)), comparable, lineOfRow, moved, index);
        for (int r = 0; r < table.rows().size(); r++) {
            Slot slot = table.rows().get(r);
            boolean rowComparable = comparable && (slot.left() >= 0 && slot.right() >= 0 || slot.moved());
            add(Line.of(slot.left() < 0 || row.left() == null ? null : tableRow(row, r, true),
                    slot.right() < 0 || row.right() == null ? null : tableRow(row, r, false)), rowComparable,
                    lineOfRow, moved, index);
        }
    }

    private Cell tableHeader(Row row, boolean onLeft, Cell.Look look) {
        TableComparison table = row.table();
        List<Cell> columns = new ArrayList<>();
        for (int c : shownColumns(table)) {
            Slot slot = table.columns().get(c);
            int column = onLeft ? slot.left() : slot.right();
            if (column < 0) {
                columns.add(null);
                continue;
            }
            Cell.Look columnLook = look(slot);
            String said = slot.moved() ? " (moved)" : slot.left() < 0 || slot.right() < 0
                    ? (onLeft ? " (removed)" : " (added)") : "";
            columns.add(new Cell(columnLook, null, null, null,
                    TextBlock.plain(columnName(column) + said, fonts.small(), Palette.FAINT), null, null));
        }
        Item item = onLeft ? row.left() : row.right();
        String notes = notes(row, onLeft, List.of());
        return new Cell(look, row, null, TextBlock.plain(location(item, true), fonts.small(), Palette.FAINT), null,
                columns, notes.isEmpty() ? null : TextBlock.plain(notes, fonts.notes(), Palette.FAINT));
    }

    private Cell tableRow(Row row, int r, boolean onLeft) {
        TableComparison table = row.table();
        Slot rowSlot = table.rows().get(r);
        Cell.Look look = look(rowSlot);
        List<Cell> columns = new ArrayList<>();
        for (int c : shownColumns(table)) {
            Slot columnSlot = table.columns().get(c);
            if ((onLeft ? columnSlot.left() : columnSlot.right()) < 0) {
                columns.add(null);
                continue;
            }
            Row cell = table.cell(r, c);
            if (cell == null || (onLeft ? cell.left() : cell.right()) == null) {
                columns.add(new Cell(Cell.Look.SAME, null, null, null,
                        TextBlock.plain("", fonts.text(), Palette.TEXT), null, null));   // covered by a merged cell
                continue;
            }
            Cell.Look cellLook = look(cell).stronger(look(columnSlot)).stronger(look(rowSlot));
            Cell shown = cell(cell, onLeft, cellLook);
            columns.add(shown);
            look = look.stronger(cellLook);
        }
        int number = (onLeft ? rowSlot.left() : rowSlot.right()) + 1;
        List<PropertyDifference> rowProperties = table.rowProperties().get(r);
        if (!rowProperties.isEmpty()) {
            look = look.stronger(Cell.Look.OTHERWISE);
        }
        String notes = String.join("  \u00B7  ", rowProperties.stream()
                .map(d -> d.property() + ": " + (onLeft ? d.left() : d.right())).toList());
        return new Cell(look, row, String.valueOf(number), null, null, columns,
                notes.isEmpty() ? null : TextBlock.plain(notes, fonts.notes(), Palette.FAINT));
    }

    /** The columns shown: all; only what is comparable, those with a match on the other side, moved or not. */
    private List<Integer> shownColumns(TableComparison table) {
        List<Integer> shown = new ArrayList<>();
        for (int c = 0; c < table.columns().size(); c++) {
            Slot slot = table.columns().get(c);
            if (!onlyComparable || slot.left() >= 0 && slot.right() >= 0 || slot.moved()) {
                shown.add(c);
            }
        }
        return shown;
    }

    /** A column by its letters, as a spreadsheet names it: A to Z, then AA… */
    static String columnName(int column) {
        StringBuilder name = new StringBuilder();
        for (int c = column + 1; c > 0; c = (c - 1) / 26) {
            name.insert(0, (char) ('A' + (c - 1) % 26));
        }
        return name.toString();
    }

    private static Cell.Look look(Row row) {
        if (row.moved() && row.status() != Row.Status.SAME) {
            return Cell.Look.MOVED;
        }
        return switch (row.status()) {
            case SAME -> Cell.Look.SAME;
            case MOVED -> Cell.Look.MOVED;
            case OTHERWISE -> Cell.Look.OTHERWISE;
            case CHANGED -> Cell.Look.CHANGED;
            case LEFT_ONLY, RIGHT_ONLY -> Cell.Look.ORPHAN;
        };
    }

    /**
     * How a row or a column of a table looks: moved, with the table layout counted; on one side only, with the text or
     * the table layout counted (it holds text, it changes the layout); the same otherwise.
     */
    private Cell.Look look(Slot slot) {
        boolean layout = options.has(Aspect.TABLE_LAYOUT);
        if (slot.moved()) {
            return layout ? Cell.Look.MOVED : Cell.Look.SAME;
        }
        if (slot.left() < 0 || slot.right() < 0) {
            return layout || options.text() ? Cell.Look.ORPHAN : Cell.Look.SAME;
        }
        return Cell.Look.SAME;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Paragraphs

    private Cell cell(Row row, boolean onLeft) {
        return cell(row, onLeft, look(row));
    }

    private Cell cell(Row row, boolean onLeft, Cell.Look look) {
        Item item = onLeft ? row.left() : row.right();
        // alone: nothing on the other side compared with it (a moved row of a table is compared with its match)
        boolean alone = (row.left() == null || row.right() == null) && !row.moved()
                && (onLeft ? row.diff().right() : row.diff().left()).isEmpty();
        List<Span> spans = alone ? List.of() : onLeft ? row.diff().left() : row.diff().right();
        List<FormatSpan> formatting = onLeft ? row.diff().leftFormatting() : row.diff().rightFormatting();
        boolean heading = item.paragraph().headingLevel() > 0;
        TextBlock text = new TextBlock(item.text(), heading ? fonts.heading() : fonts.text(), Palette.TEXT, spans,
                formatting, "(empty paragraph)");
        boolean inTable = !item.places().isEmpty() && !item.places().getLast().textBox();
        String location = inTable ? null : location(item, false);
        TextBlock caption = location == null ? null : TextBlock.plain(location, fonts.small(), Palette.FAINT);
        String notes = notes(row, onLeft, formatting);
        return new Cell(look, inTable ? null : row, inTable ? null : marker(item, onLeft ? leftNumbers : rightNumbers),
                caption, text, null,
                notes.isEmpty() ? null : TextBlock.plain(notes, fonts.notes(), Palette.FAINT));
    }

    /** A property of the document, its value on one side. */
    private Cell property(String property, String value) {
        String text = property + ": " + value;
        List<Span> spans = List.of(new Span(0, property.length() + 2, Span.Kind.SAME),
                new Span(property.length() + 2, text.length(), Span.Kind.CHANGED));
        return new Cell(Cell.Look.CHANGED, null, null, null,
                new TextBlock(text, fonts.text(), Palette.TEXT, spans, List.of(), " "), null, null);
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

    /** What else differs, this side's values: "heading: heading 2 · formatting: b: true → (none)". */
    private static String notes(Row row, boolean onLeft, List<FormatSpan> formatting) {
        List<String> notes = new ArrayList<>();
        for (PropertyDifference difference : row.diff().properties()) {
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

    /**
     * The heading level, or the mark of a list item as Word shows it: its level's text ("%1.", "%1.%2"), each number
     * counted in the order of the side's paragraphs; a bullet as such.
     */
    private static String marker(Item item, Map<String, int[]> numbers) {
        if (item.paragraph().headingLevel() > 0) {
            return "H" + item.paragraph().headingLevel();
        }
        ListItem list = item.paragraph().listItem();
        if (list == null) {
            return null;
        }
        String format = list.numberFormat() == null ? "decimal" : list.numberFormat();
        if (format.equals("bullet")) {
            return "\u2022";
        }
        int level = Math.clamp(list.level(), 0, 8);
        int[] counters = numbers.computeIfAbsent(list.listId(), id -> new int[9]);
        counters[level]++;
        for (int deeper = level + 1; deeper < counters.length; deeper++) {
            counters[deeper] = 0;
        }
        String text = list.levelText() == null ? "%" + (level + 1) + "." : list.levelText();
        for (int l = 0; l <= level; l++) {
            text = text.replace("%" + (l + 1), number(Math.max(1, counters[l]), l == level ? format : "decimal"));
        }
        return text.length() > 6 ? text.substring(0, 6) : text;
    }

    private static String number(int n, String format) {
        return switch (format) {
            case "lowerLetter" -> String.valueOf((char) ('a' + (n - 1) % 26));
            case "upperLetter" -> String.valueOf((char) ('A' + (n - 1) % 26));
            case "lowerRoman" -> roman(n).toLowerCase(Locale.ROOT);
            case "upperRoman" -> roman(n);
            default -> String.valueOf(n);
        };
    }

    private static String roman(int n) {
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        StringBuilder roman = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            for (; n >= values[i]; n -= values[i]) {
                roman.append(symbols[i]);
            }
        }
        return roman.toString();
    }

    private static String count(long differences) {
        return differences == 0 ? "no difference" : differences == 1 ? "1 difference" : differences + " differences";
    }
}
