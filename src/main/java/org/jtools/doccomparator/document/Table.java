package org.jtools.doccomparator.document;

import java.util.List;

/** A table: its style (null for none), the properties set on it directly, and its rows of cells. */
public record Table(String styleId, Formatting formatting, List<Row> rows) implements Block {

    public Table {
        rows = List.copyOf(rows);
    }

    public record Row(List<Cell> cells) {

        public Row {
            cells = List.copyOf(cells);
        }
    }

    /** A cell holds blocks of its own: paragraphs, and tables within the table. */
    public record Cell(Formatting formatting, List<Block> blocks) {

        public Cell {
            blocks = List.copyOf(blocks);
        }
    }
}
