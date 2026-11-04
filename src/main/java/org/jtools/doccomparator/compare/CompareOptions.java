package org.jtools.doccomparator.compare;

import java.util.EnumSet;
import java.util.Set;

/**
 * What the comparison takes into account: the text and each kind of layout, each compared or left out on its own. Left
 * out, a kind of difference is neither counted nor shown. The text left out, paragraphs are still paired by it — their
 * layout can only be compared so — but nothing of it is shown as differing: no word marked, no paragraph added,
 * removed or moved.
 */
public record CompareOptions(Set<Aspect> aspects) {

    /** A kind of difference. */
    public enum Aspect {
        TEXT("Text", "The words of paragraphs and cells; paragraphs added, removed or moved"),
        BLANKS("Blanks", "Spaces, tabs, breaks and empty paragraphs"),
        HEADINGS("Headings", "Heading levels: a title's depth"),
        LISTS("Lists", "List items, their level and numbering"),
        SECTIONS("Sections", "Section breaks and their number; paragraphs in text boxes"),
        STYLES("Styles", "Paragraph, character and table styles, by name; the documents' style definitions"),
        CHARACTERS("Characters", "Formatting set on the text: bold, italic, underline, font, size, colour, highlight…"),
        INDENTATION("Indentation", "Paragraphs' indents: left, right, first line, hanging"),
        SPACING("Spacing", "Space before and after paragraphs, line spacing"),
        ALIGNMENT("Alignment", "Paragraphs' alignment: left, centred, right, justified"),
        PARAGRAPHS("Paragraphs", "Other paragraph formatting: borders, shading, tabs, keep with next, page breaks…"),
        TABLE_LAYOUT("Table layout", "Rows and columns added, removed or moved, their numbers; widths and heights"),
        CELLS("Cell formatting", "Cells' and tables' shading, borders, margins, vertical alignment; header rows…"),
        TEMPLATE("Template", "The template attached, and the one named"),
        HEADERS_AND_FOOTERS("Headers and footers", "Their text, as a part of its own");

        private final String label;
        private final String description;

        Aspect(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }
    }

    /** The text, the headers and footers included: the content, the layout left out until asked for. */
    public static final CompareOptions DEFAULT = new CompareOptions(EnumSet.of(Aspect.TEXT, Aspect.HEADERS_AND_FOOTERS));
    /** The text alone, the body's and the notes'. */
    public static final CompareOptions TEXT_ONLY = new CompareOptions(EnumSet.of(Aspect.TEXT));
    /** Everything but the text and the blanks: the layout, the headers and footers included. */
    public static final CompareOptions LAYOUT_ONLY = new CompareOptions(
            EnumSet.complementOf(EnumSet.of(Aspect.TEXT, Aspect.BLANKS)));
    /** Everything. */
    public static final CompareOptions ALL = new CompareOptions(EnumSet.allOf(Aspect.class));

    public CompareOptions {
        aspects = Set.copyOf(aspects);
    }

    public boolean has(Aspect aspect) {
        return aspects.contains(aspect);
    }

    public CompareOptions with(Aspect aspect, boolean on) {
        EnumSet<Aspect> changed = aspects.isEmpty() ? EnumSet.noneOf(Aspect.class) : EnumSet.copyOf(aspects);
        if (on) {
            changed.add(aspect);
        } else {
            changed.remove(aspect);
        }
        return new CompareOptions(changed);
    }

    public boolean blanks() {
        return has(Aspect.BLANKS);
    }

    public boolean text() {
        return has(Aspect.TEXT);
    }
}
