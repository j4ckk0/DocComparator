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
import java.util.Collections;
import java.util.List;

/**
 * Two tables compared: their columns side by side, then their rows, each a Slot — the same, added, removed, moved —
 * then each cell where a row and a column meet, a Pairing of its own: the two cells compared, or the one there alone.
 *
 * @param cells         by row slot, then column slot; null where neither side has a cell (a merged one covers it)
 * @param rowProperties by row slot, how the rows' own properties differ (height, header…), as the options compare them
 */
public record TableComparison(List<Slot> columns, List<Slot> rows, List<List<Pairing>> cells,
                              List<List<PropertyDifference>> rowProperties) {

    public TableComparison {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
        rowProperties = rowProperties.stream().map(List::copyOf).toList();
        List<List<Pairing>> copy = new ArrayList<>();
        for (List<Pairing> row : cells) {
            copy.add(Collections.unmodifiableList(new ArrayList<>(row)));   // with its nulls
        }
        cells = Collections.unmodifiableList(copy);
    }

    public Pairing cell(int row, int column) {
        return cells.get(row).get(column);
    }

    /** Whether a row or a column was added, removed or moved. */
    public boolean layoutDiffers() {
        return columns.stream().anyMatch(slot -> slot.left() < 0 || slot.right() < 0)
                || rows.stream().anyMatch(slot -> slot.left() < 0 || slot.right() < 0);
    }

    /** How the tables compare, as a paragraph would: their text first, then the rest; the layout if it counts. */
    Pairing.Status status(ParagraphDiff properties, boolean layoutCounts) {
        boolean textDiffers = false;
        boolean otherwise = !properties.properties().isEmpty()
                || rowProperties.stream().anyMatch(row -> !row.isEmpty());
        for (List<Pairing> row : cells) {
            for (Pairing cell : row) {
                if (cell != null) {
                    textDiffers |= cell.status() == Pairing.Status.CHANGED || cell.status() == Pairing.Status.LEFT_ONLY
                            || cell.status() == Pairing.Status.RIGHT_ONLY;
                    otherwise |= cell.status() == Pairing.Status.OTHERWISE;
                }
            }
        }
        if (textDiffers) {
            return Pairing.Status.CHANGED;
        }
        return otherwise || layoutCounts && layoutDiffers() ? Pairing.Status.OTHERWISE : Pairing.Status.SAME;
    }
}
