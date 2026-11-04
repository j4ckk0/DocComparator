package org.jtools.doccomparator.compare;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.jtools.doccomparator.document.Table;

/**
 * Compares two tables as a reader sees them: their layout, then their content, whatever their styles.
 * <ol>
 * <li>The columns, each the text of its cells top to bottom, paired as paragraphs are (Pairer): added, removed,
 * moved. Columns left over between two pairs are paired in place when their headers are alike: a column whose values
 * were all rewritten under the same header is still the one that was there.</li>
 * <li>The rows, each the text of its cells, paired the same way.</li>
 * <li>Where a row meets a column, the two cells, compared as paragraphs (ParagraphComparer); a cell of a moved row or
 * column with its match, where it is on its side.</li>
 * </ol>
 * Cells are placed on the table's grid (TableGrid): a merged cell where it starts.
 */
final class TableComparer {

    private final CompareOptions options;
    private final ParagraphComparer paragraphs;

    TableComparer(CompareOptions options, ParagraphComparer paragraphs) {
        this.options = options;
        this.paragraphs = paragraphs;
    }

    TableComparison compare(Unit a, Unit b) {
        List<Unit> columnsA = columns(a);
        List<Unit> columnsB = columns(b);
        List<Slot> columns = Slot.align(columnsA.size(), columnsB.size(),
                inPlace(Pairer.pair(columnsA, columnsB), a, b));
        List<Unit> rowsA = rows(a);
        List<Unit> rowsB = rows(b);
        List<Slot> rows = Slot.align(rowsA.size(), rowsB.size(), Pairer.pair(rowsA, rowsB));

        int[] columnOfA = Slot.leftPartners(columns, columnsA.size());
        int[] columnOfB = Slot.rightPartners(columns, columnsB.size());
        int[] rowOfA = Slot.leftPartners(rows, rowsA.size());
        int[] rowOfB = Slot.rightPartners(rows, rowsB.size());
        List<List<Row>> cells = new ArrayList<>();
        for (Slot row : rows) {
            List<Row> line = new ArrayList<>();
            for (Slot column : columns) {
                Unit x = cell(a, row.left(), column.left());
                Unit y = cell(b, row.right(), column.right());
                if (x != null && y != null) {
                    line.add(pair(x, y));
                } else if (x != null) {
                    Unit partner = cell(b, partner(rowOfA, row.left()), partner(columnOfA, column.left()));
                    line.add(partner == null ? cellAlone(x, true) : withoutRight(pair(x, partner)));
                } else if (y != null) {
                    Unit partner = cell(a, partner(rowOfB, row.right()), partner(columnOfB, column.right()));
                    line.add(partner == null ? cellAlone(y, false) : withoutLeft(pair(partner, y)));
                } else {
                    line.add(null);
                }
            }
            cells.add(line);
        }
        return new TableComparison(columns, rows, cells);
    }

    /** The properties of two tables, as the options compare them: their size, their style. */
    ParagraphDiff properties(Unit a, Unit b, TableComparison table) {
        List<PropertyDifference> differences = new ArrayList<>();
        if (options.structure()) {
            add(differences, PropertyDifference.of("rows", String.valueOf(a.cells().length),
                    String.valueOf(b.cells().length)));
            add(differences, PropertyDifference.of("columns", String.valueOf(columns(a).size()),
                    String.valueOf(columns(b).size())));
            add(differences, PropertyDifference.of("place", a.item().placeInStructure(), b.item().placeInStructure()));
        }
        if (options.styles()) {
            Table x = a.item().table();
            Table y = b.item().table();
            add(differences, PropertyDifference.of("table style", paragraphs.leftStyleName(x.styleId()),
                    paragraphs.rightStyleName(y.styleId())));
        }
        return new ParagraphDiff(List.of(), List.of(), List.of(), List.of(), differences);
    }

    /** A table alone, laid out as it is: its rows and columns, each cell alone. */
    static TableComparison alone(Unit table, boolean onLeft) {
        int rowCount = table.cells().length;
        int columnCount = rowCount == 0 ? 0 : table.cells()[0].length;
        List<Slot> columns = new ArrayList<>();
        for (int c = 0; c < columnCount; c++) {
            columns.add(onLeft ? new Slot(c, -1, -1) : new Slot(-1, c, -1));
        }
        List<Slot> rows = new ArrayList<>();
        List<List<Row>> cells = new ArrayList<>();
        for (int r = 0; r < rowCount; r++) {
            rows.add(onLeft ? new Slot(r, -1, -1) : new Slot(-1, r, -1));
            List<Row> line = new ArrayList<>();
            for (int c = 0; c < columnCount; c++) {
                Unit cell = table.cells()[r][c];
                line.add(cell == null ? null : cellAlone(cell, onLeft));
            }
            cells.add(line);
        }
        return new TableComparison(columns, rows, cells);
    }

    private Row pair(Unit x, Unit y) {
        ParagraphDiff diff = paragraphs.compare(x, y);
        return new Row(x.item(), y.item(), Row.statusOf(diff), -1, diff, null);
    }

    private static Row withoutRight(Row pair) {
        return new Row(pair.left(), null, pair.status(), -1, pair.diff(), null);
    }

    private static Row withoutLeft(Row pair) {
        return new Row(null, pair.right(), pair.status(), -1, pair.diff(), null);
    }

    private static Row cellAlone(Unit cell, boolean onLeft) {
        return new Row(onLeft ? cell.item() : null, onLeft ? null : cell.item(),
                onLeft ? Row.Status.LEFT_ONLY : Row.Status.RIGHT_ONLY, -1, ParagraphDiff.alone(cell.text(), onLeft), null);
    }

    private static int partner(int[] partners, int index) {
        return index < 0 ? -1 : partners[index];
    }

    private static Unit cell(Unit table, int row, int column) {
        return row < 0 || column < 0 ? null : table.cells()[row][column];
    }

    /** Each column of a table as one unit: the text of its cells, top to bottom. */
    private static List<Unit> columns(Unit table) {
        Unit[][] cells = table.cells();
        int columnCount = cells.length == 0 ? 0 : cells[0].length;
        List<Unit> columns = new ArrayList<>();
        for (int c = 0; c < columnCount; c++) {
            List<Unit> parts = new ArrayList<>();
            for (Unit[] row : cells) {
                if (row[c] != null) {
                    parts.add(row[c]);
                }
            }
            columns.add(Unit.composite(part(table, 0, c + 1), parts));
        }
        return columns;
    }

    /** Each row of a table as one unit: the text of its cells, left to right. */
    private static List<Unit> rows(Unit table) {
        List<Unit> rows = new ArrayList<>();
        Unit[][] cells = table.cells();
        for (int r = 0; r < cells.length; r++) {
            List<Unit> parts = new ArrayList<>();
            for (Unit cell : cells[r]) {
                if (cell != null) {
                    parts.add(cell);
                }
            }
            rows.add(Unit.composite(part(table, r + 1, 0), parts));
        }
        return rows;
    }

    /** The item standing for a row (column 0) or a column (row 0) of a table: where it is, which pairing prefers. */
    private static Item part(Unit table, int row, int column) {
        Item item = table.item();
        return new Item(item.story(), item.label(), Items.within(item.places(), Item.Place.inCell(0, row, column)),
                item.paragraph(), null);
    }

    /**
     * Besides the pairs found, the columns left over between two pairs whose headers (their first cells) are alike:
     * paired in place, in order. A column whose values were all rewritten under the same header is still that column.
     */
    private static List<Pairer.Match> inPlace(List<Pairer.Match> matches, Unit a, Unit b) {
        int leftLength = columnCount(a);
        int rightLength = columnCount(b);
        boolean[] leftPaired = new boolean[leftLength];
        boolean[] rightPaired = new boolean[rightLength];
        List<Pairer.Match> inOrder = new ArrayList<>();
        for (Pairer.Match match : matches) {
            leftPaired[match.left()] = true;
            rightPaired[match.right()] = true;
            if (!match.moved()) {
                inOrder.add(match);
            }
        }
        inOrder.add(new Pairer.Match(leftLength, rightLength, 0, false));
        List<Pairer.Match> all = new ArrayList<>(matches);
        int previousLeft = -1;
        int previousRight = -1;
        for (Pairer.Match next : inOrder) {
            int j = previousRight + 1;
            for (int i = previousLeft + 1; i < next.left(); i++) {
                if (leftPaired[i]) {
                    continue;
                }
                for (int k = j; k < next.right(); k++) {
                    if (!rightPaired[k] && sameHeader(a, i, b, k)) {
                        all.add(new Pairer.Match(i, k, 0, false));
                        j = k + 1;
                        break;
                    }
                }
            }
            previousLeft = next.left();
            previousRight = next.right();
        }
        all.sort(Comparator.comparing(Pairer.Match::moved).thenComparingInt(Pairer.Match::left));
        return all;
    }

    private static boolean sameHeader(Unit a, int columnA, Unit b, int columnB) {
        Unit x = a.cells().length == 0 ? null : a.cells()[0][columnA];
        Unit y = b.cells().length == 0 ? null : b.cells()[0][columnB];
        return x != null && y != null && !x.isBlank() && Pairer.similarity(x, y) > 0;
    }

    private static int columnCount(Unit table) {
        return table.cells().length == 0 ? 0 : table.cells()[0].length;
    }

    private static void add(List<PropertyDifference> differences, PropertyDifference difference) {
        if (difference != null) {
            differences.add(difference);
        }
    }
}
