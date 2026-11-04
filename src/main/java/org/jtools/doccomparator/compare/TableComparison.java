package org.jtools.doccomparator.compare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Two tables compared: their columns side by side, then their rows, each a Slot — the same, added, removed, moved —
 * then each cell where a row and a column meet, a Row of its own: the two cells compared, or the one there alone.
 *
 * @param cells         by row slot, then column slot; null where neither side has a cell (a merged one covers it)
 * @param rowProperties by row slot, how the rows' own properties differ (height, header…), as the options compare them
 */
public record TableComparison(List<Slot> columns, List<Slot> rows, List<List<Row>> cells,
                              List<List<PropertyDifference>> rowProperties) {

    public TableComparison {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
        rowProperties = rowProperties.stream().map(List::copyOf).toList();
        List<List<Row>> copy = new ArrayList<>();
        for (List<Row> row : cells) {
            copy.add(Collections.unmodifiableList(new ArrayList<>(row)));   // with its nulls
        }
        cells = Collections.unmodifiableList(copy);
    }

    public Row cell(int row, int column) {
        return cells.get(row).get(column);
    }

    /** Whether a row or a column was added, removed or moved. */
    public boolean layoutDiffers() {
        return columns.stream().anyMatch(slot -> slot.left() < 0 || slot.right() < 0)
                || rows.stream().anyMatch(slot -> slot.left() < 0 || slot.right() < 0);
    }

    /** How the tables compare, as a paragraph would: their text first, then the rest; the layout if it counts. */
    Row.Status status(ParagraphDiff properties, boolean layoutCounts) {
        boolean textDiffers = false;
        boolean otherwise = !properties.properties().isEmpty()
                || rowProperties.stream().anyMatch(row -> !row.isEmpty());
        for (List<Row> row : cells) {
            for (Row cell : row) {
                if (cell != null) {
                    textDiffers |= cell.status() == Row.Status.CHANGED || cell.status() == Row.Status.LEFT_ONLY
                            || cell.status() == Row.Status.RIGHT_ONLY;
                    otherwise |= cell.status() == Row.Status.OTHERWISE;
                }
            }
        }
        if (textDiffers) {
            return Row.Status.CHANGED;
        }
        return otherwise || layoutCounts && layoutDiffers() ? Row.Status.OTHERWISE : Row.Status.SAME;
    }
}
