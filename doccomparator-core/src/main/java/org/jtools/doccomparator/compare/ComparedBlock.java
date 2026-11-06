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

import java.util.List;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Table;
import org.jtools.doccomparator.document.Story;

/**
 * A block of a document as the comparison pairs it, one against one, and where it is: a paragraph, or a whole table,
 * compared row by row and column by column (TableComparer). Text boxes are opened into their paragraphs and tables,
 * each knowing where it was. A cell of a table is one too, its paragraphs one text, its paragraph the first's
 * properties.
 *
 * @param story     the story it is in
 * @param places    the text boxes, the table and the cell it is in, the outermost first; empty in the flow of the story
 * @param paragraph the paragraph; for a table, its text alone, cell after cell
 * @param table     the table, null for a paragraph or a cell
 * @param cell      a cell's own properties (w:tcPr); none for a paragraph or a table
 */
public record ComparedBlock(Story.Name story, List<Place> places, Paragraph paragraph, Table table, Formatting cell) {

    public ComparedBlock {
        places = List.copyOf(places);
    }

    public ComparedBlock(Story.Name story, List<Place> places, Paragraph paragraph, Table table) {
        this(story, places, paragraph, table, Formatting.NONE);
    }

    /**
     * A text box, a table (counted in the story, from 1) or a table's cell (its row and its column on the table's grid,
     * from 1).
     */
    public record Place(boolean textBox, int table, int row, int column) {

        static Place inTextBox() {
            return new Place(true, 0, 0, 0);
        }

        static Place inTable(int table) {
            return new Place(false, table, 0, 0);
        }

        static Place inCell(int table, int row, int column) {
            return new Place(false, table, row, column);
        }

        public boolean isTable() {
            return !textBox && row == 0;
        }
    }

    public boolean isTable() {
        return table != null;
    }

    public String text() {
        return paragraph.text();
    }

    /**
     * Where it is, as the structure compares it: in the flow of the text or in a text box — not which table, nor which
     * cell, which an inserted table, row or column would change for all those after it: those are compared as such.
     */
    Phrase placeInStructure() {
        return Phrase.of(places.stream().anyMatch(Place::textBox) ? "value.place.textBox" : "value.place.text");
    }
}
