package org.jtools.doccomparator.compare;

/*-
 * #%L
 * DocComparator
 * %%
 * Copyright (C) 2026 jtools.org
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.ArrayList;
import java.util.List;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Run;
import org.jtools.doccomparator.document.Table;
import org.jtools.doccomparator.document.WordProperties;

/**
 * A table on its grid, as Word lays it out: a cell spanning columns (w:gridSpan) at the first of them, nothing at the
 * others; a cell merged with the one above (w:vMerge) nothing either. Each cell an item, its paragraphs one text — a
 * line break between them — its paragraph the first one's properties.
 */
final class TableGrid {

    /** The cells by row and column; null where none starts. */
    final ComparedBlock[][] cells;
    final int columns;

    private TableGrid(ComparedBlock[][] cells, int columns) {
        this.cells = cells;
        this.columns = columns;
    }

    static TableGrid of(ComparedBlock table) {
        List<Table.Row> rows = table.table().rows();
        int number = table.places().getLast().table();
        List<ComparedBlock[]> grid = new ArrayList<>();
        int columns = 0;
        for (int r = 0; r < rows.size(); r++) {
            List<ComparedBlock> row = new ArrayList<>();
            for (Table.Cell cell : rows.get(r).cells()) {
                int span = span(cell.formatting());
                String merge = cell.formatting().get(WordProperties.VERTICAL_MERGE);
                boolean continued = merge != null && !merge.equals(WordProperties.MERGE_STARTS);
                if (!continued) {
                    row.add(new ComparedBlock(table.story(),
                            ComparedBlocks.within(table.places(),
                                    ComparedBlock.Place.inCell(number, r + 1, row.size() + 1)),
                            paragraph(cell), null, cell.formatting()));
                } else {
                    row.add(null);
                }
                for (int s = 1; s < span; s++) {
                    row.add(null);
                }
            }
            columns = Math.max(columns, row.size());
            grid.add(row.toArray(new ComparedBlock[0]));
        }
        ComparedBlock[][] cells = new ComparedBlock[grid.size()][columns];
        for (int r = 0; r < grid.size(); r++) {
            System.arraycopy(grid.get(r), 0, cells[r], 0, grid.get(r).length);
        }
        return new TableGrid(cells, columns);
    }

    private static int span(Formatting formatting) {
        String span = formatting.get(WordProperties.GRID_SPAN);
        try {
            return Math.max(1, Integer.parseInt(span == null ? "1" : span));
        } catch (NumberFormatException _) {
            return 1;
        }
    }

    /** The cell's paragraphs as one: their runs, a line break between paragraphs, the first one's properties. */
    private static Paragraph paragraph(Table.Cell cell) {
        List<Paragraph> paragraphs = ComparedBlocks.paragraphs(cell.blocks());
        if (paragraphs.isEmpty()) {
            return Paragraph.of(List.of());
        }
        List<Run> runs = new ArrayList<>();
        for (Paragraph paragraph : paragraphs) {
            if (!runs.isEmpty()) {
                runs.add(Run.of("\n"));
            }
            runs.addAll(paragraph.runs());
        }
        Paragraph first = paragraphs.getFirst();
        return new Paragraph(first.styleId(), first.formatting(), first.headingLevel(), first.listItem(), false, runs);
    }
}
