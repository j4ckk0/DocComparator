package org.jtools.doccomparator.ui;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.List;
import org.jtools.doccomparator.compare.Row;

/**
 * One side of a line of the view: a paragraph and how it compares, a row of a table, or a property of the document.
 * Laid out top to bottom: where it is (a table, a header…) when that is worth saying; its text, or for a row of a table
 * its cells side by side; what else differs about it; for a moved paragraph or table, the way to its match.
 */
final class Cell {

    /** How the paragraph compares, the colour it is shown in. */
    enum Look {
        SAME, OTHERWISE, MOVED, ORPHAN, CHANGED;

        /** The look that says more of two: a line shows its stronger side. */
        Look stronger(Look other) {
            return other.ordinal() > ordinal() ? other : this;
        }
    }

    static final int PADDING = 5;
    static final int GAP = 2;
    /** The share of a table's width a column has on the side that has not got it. */
    static final float MISSING_COLUMN = 0.3f;

    final Look look;
    /** The row of the comparison it shows (for a row of a table, the table's); null for a property of the document. */
    final Row row;
    /** A short sign in the margin: the heading level ("H2"), a list item's mark, a table row's number. */
    final String marker;
    final TextBlock caption;
    /** Its text; null for a row of a table. */
    final TextBlock text;
    /** For a row of a table, a cell for each column the comparison has, null where this side has none. */
    final List<Cell> columns;
    final TextBlock notes;
    /** The line of its match, for a moved paragraph or table; -1 otherwise. */
    int counterpartLine = -1;
    private TextBlock link;
    private int height;
    private float[] columnX = new float[0];
    private float[] columnWidth = new float[0];
    private int gridHeight;

    Cell(Look look, Row row, String marker, TextBlock caption, TextBlock text, List<Cell> columns, TextBlock notes) {
        this.look = look;
        this.row = row;
        this.marker = marker;
        this.caption = caption;
        this.text = text;
        this.columns = columns;
        this.notes = notes;
    }

    boolean differs() {
        return look != Look.SAME;
    }

    boolean moved() {
        return counterpartLine >= 0;
    }

    /** For a moved paragraph or table: its match is on that line, above or below; said, if said is given. */
    void linkTo(int line, boolean below, Font said) {
        counterpartLine = line;
        if (said != null) {
            link = TextBlock.plain("Moved — its match is " + (below ? "below ↓" : "above ↑")
                    + " (click to go there)", said, Palette.LINK);
        }
    }

    int layout(FontRenderContext context, float width) {
        int h = PADDING;
        if (caption != null) {
            h += caption.layout(context, width) + GAP;
        }
        if (columns != null) {
            h += layoutGrid(context, width);
        } else {
            h += text.layout(context, width);
        }
        for (TextBlock block : new TextBlock[] {notes, link}) {
            if (block != null) {
                h += GAP + block.layout(context, width);
            }
        }
        height = h + PADDING;
        return height;
    }

    private int layoutGrid(FontRenderContext context, float width) {
        int n = columns.size();
        columnX = new float[n];
        columnWidth = new float[n];
        float weights = 0;
        for (Cell column : columns) {
            weights += column == null ? MISSING_COLUMN : 1;
        }
        float x = 0;
        gridHeight = 18;
        for (int c = 0; c < n; c++) {
            Cell column = columns.get(c);
            columnX[c] = x;
            columnWidth[c] = width * (column == null ? MISSING_COLUMN : 1) / Math.max(weights, 1);
            x += columnWidth[c];
            if (column != null) {
                gridHeight = Math.max(gridHeight, column.layout(context, Math.max(10, columnWidth[c] - 2 * PADDING)));
            }
        }
        return gridHeight;
    }

    int height() {
        return height;
    }

    TextBlock link() {
        return link;
    }

    float columnX(int column) {
        return columnX[column];
    }

    float columnWidth(int column) {
        return columnWidth[column];
    }

    int gridHeight() {
        return gridHeight;
    }
}
