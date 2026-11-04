package org.jtools.doccomparator.compare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Two tables compared: their columns side by side, then their rows, each a Slot — the same, added, removed, moved —
 * then each cell where a row and a column meet, a Row of its own: the two cells compared, or the one there alone.
 *
 * @param cells by row slot, then column slot; null where neither side has a cell (a merged one covers it)
 */
public record TableComparison(List<Slot> columns, List<Slot> rows, List<List<Row>> cells) {

    public TableComparison {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
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

    /** How the tables compare, as a paragraph would: their text first, then the rest. */
    Row.Status status(ParagraphDiff properties) {
        boolean textDiffers = false;
        boolean otherwise = !properties.properties().isEmpty();
        for (List<Row> row : cells) {
            for (Row cell : row) {
                if (cell != null) {
                    textDiffers |= cell.status() == Row.Status.CHANGED || cell.status() == Row.Status.LEFT_ONLY
                            || cell.status() == Row.Status.RIGHT_ONLY;
                    otherwise |= cell.status() == Row.Status.SAME_TEXT;
                }
            }
        }
        if (textDiffers) {
            return Row.Status.CHANGED;
        }
        return otherwise || layoutDiffers() ? Row.Status.SAME_TEXT : Row.Status.SAME;
    }
}
