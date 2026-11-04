package org.jtools.doccomparator.compare;

import java.util.List;
import org.jtools.doccomparator.document.Formatting;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Table;
import org.jtools.doccomparator.document.Story;
import org.jtools.doccomparator.i18n.I18n;

/**
 * What the comparison pairs, one against one: a paragraph, or a whole table, compared row by row and column by column
 * (TableComparer). Text boxes are opened into their paragraphs and tables, each knowing where it was. A cell of a table
 * is an item too, its paragraphs one text, its paragraph the first's properties.
 *
 * @param story     the kind of story it is in
 * @param label     the story's label ("Body", "Footnote 2")
 * @param places    the text boxes, the table and the cell it is in, the outermost first; empty in the flow of the story
 * @param paragraph the paragraph; for a table, its text alone, cell after cell
 * @param table     the table, null for a paragraph or a cell
 * @param cell      a cell's own properties (w:tcPr); none for a paragraph or a table
 */
public record Item(Story.Kind story, String label, List<Place> places, Paragraph paragraph, Table table,
                   Formatting cell) {

    public Item {
        places = List.copyOf(places);
    }

    /** Between the places of a location: the view takes the body's label off by it ("Body, table 2" → "Table 2"). */
    public static final String BETWEEN_PLACES = ", ";

    public Item(Story.Kind story, String label, List<Place> places, Paragraph paragraph, Table table) {
        this(story, label, places, paragraph, table, Formatting.NONE);
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

        boolean isTable() {
            return !textBox && row == 0;
        }
    }

    public boolean isTable() {
        return table != null;
    }

    public String text() {
        return paragraph.text();
    }

    /** Where it is, for a reader: "Body, table 2", "Body, text box, table 1, row 3, column 1". */
    public String location() {
        StringBuilder location = new StringBuilder(label);
        for (Place place : places) {
            location.append(BETWEEN_PLACES).append(place.textBox() ? I18n.text("location.textBox")
                    : place.isTable() ? I18n.text("location.table", place.table())
                    : I18n.text("location.cell", place.row(), place.column()));
        }
        return location.toString();
    }

    /**
     * Where it is, as the structure compares it: in the flow of the text or in a text box — not which table, nor which
     * cell, which an inserted table, row or column would change for all those after it: those are compared as such.
     */
    String placeInStructure() {
        return I18n.text(places.stream().anyMatch(Place::textBox) ? "value.place.textBox" : "value.place.text");
    }
}
