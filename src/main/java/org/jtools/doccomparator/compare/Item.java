package org.jtools.doccomparator.compare;

import java.util.List;
import org.jtools.doccomparator.document.Paragraph;
import org.jtools.doccomparator.document.Story;

/**
 * A paragraph as the comparison sees it: what is compared, one against one. Tables and text boxes are taken apart into
 * their paragraphs, each knowing where it was — a paragraph may have left a table, or moved into one.
 *
 * @param story  the kind of story it is in
 * @param label  the story's label ("Body", "Footnote 2")
 * @param places the table cells and text boxes it is in, the outermost first; empty in the flow of the story
 */
public record Item(Story.Kind story, String label, List<Place> places, Paragraph paragraph) {

    public Item {
        places = List.copyOf(places);
    }

    /** A table cell (its table counted in the story, its row and cell from 1) or a text box. */
    public record Place(boolean textBox, int table, int row, int cell) {

        static Place inTextBox() {
            return new Place(true, 0, 0, 0);
        }

        static Place inCell(int table, int row, int cell) {
            return new Place(false, table, row, cell);
        }
    }

    public String text() {
        return paragraph.text();
    }

    /** Where it is, for a reader: "Body, table 2, row 3, cell 1". */
    public String location() {
        StringBuilder location = new StringBuilder(label);
        for (Place place : places) {
            location.append(place.textBox() ? ", text box"
                    : ", table " + place.table() + ", row " + place.row() + ", cell " + place.cell());
        }
        return location.toString();
    }

    /**
     * Where it is, as the structure compares it: in the flow of the text, a text box, a cell by its row and cell —
     * not which table, which an inserted table would change for all those after it.
     */
    String placeInStructure() {
        if (places.isEmpty()) {
            return "text";
        }
        StringBuilder place = new StringBuilder();
        for (Place p : places) {
            if (!place.isEmpty()) {
                place.append(", within ");
            }
            place.append(p.textBox() ? "text box" : "table cell (row " + p.row() + ", cell " + p.cell() + ")");
        }
        return place.toString();
    }
}
