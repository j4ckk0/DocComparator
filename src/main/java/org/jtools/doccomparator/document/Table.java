package org.jtools.doccomparator.document;

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

import java.util.List;

/** A table: its style (null for none), the properties set on it directly, and its rows of cells. */
public record Table(String styleId, Formatting formatting, List<Row> rows) implements Block {

    public Table {
        rows = List.copyOf(rows);
    }

    /** A row: its own properties (w:trPr: height, header repeated on each page…) and its cells. */
    public record Row(Formatting formatting, List<Cell> cells) {

        public Row {
            cells = List.copyOf(cells);
        }

        public Row(List<Cell> cells) {
            this(Formatting.NONE, cells);
        }
    }

    /** A cell holds blocks of its own: paragraphs, and tables within the table. */
    public record Cell(Formatting formatting, List<Block> blocks) {

        public Cell {
            blocks = List.copyOf(blocks);
        }
    }
}
